use std::collections::{HashMap, HashSet};

use super::{
    analyzer::DocumentAnalyzer,
    error::FetchError,
    fetcher::DocumentFetcher,
    ml_client::MlClient,
    model::{DocumentId, DocumentMeta, GraphData, GraphLink, GraphNode},
    parser::DocumentParser,
};

/// Builds a `GraphData` by BFS-crawling documents through a `DocumentFetcher`.
///
/// Analysis is two-layered:
/// - Structural checks (outdated refs, cycles) always run locally.
/// - Semantic checks (duplications, contradictions) run via `MlClient` when
///   a service URL is configured; falls back gracefully if unavailable.
pub struct GraphBuilder<F: DocumentFetcher> {
    fetcher: F,
    parser: DocumentParser,
    analyzer: DocumentAnalyzer,
    ml_client: Option<MlClient>,
}

impl<F: DocumentFetcher> GraphBuilder<F> {
    pub fn new(fetcher: F) -> Self {
        Self {
            fetcher,
            parser: DocumentParser,
            analyzer: DocumentAnalyzer::new(),
            ml_client: None,
        }
    }

    /// Attach a ML service for semantic analysis.
    pub fn with_ml_service(mut self, base_url: impl Into<String>) -> Self {
        self.ml_client = Some(MlClient::new(base_url));
        self
    }

    pub async fn build(&self, seeds: Vec<DocumentId>, depth: usize) -> GraphData {
        let metas = self.crawl(seeds, depth).await;
        self.assemble(metas).await
    }

    // ── BFS crawl ─────────────────────────────────────────────────────────────

    async fn crawl(&self, seeds: Vec<DocumentId>, depth: usize) -> Vec<DocumentMeta> {
        let mut visited: HashSet<DocumentId> = HashSet::new();
        let mut collected: Vec<DocumentMeta> = Vec::new();
        let mut queue: Vec<DocumentId> = seeds;

        for current_depth in 0..=depth {
            let batch: Vec<DocumentId> = queue
                .drain(..)
                .filter(|id| !visited.contains(id))
                .collect();

            let results = self.fetch_batch(&batch).await;

            for (id, result) in batch.into_iter().zip(results) {
                visited.insert(id.clone());

                let meta = result.unwrap_or_else(|e| {
                    tracing::warn!("Failed to fetch {id}: {e}");
                    DocumentMeta::placeholder(id.clone(), self.fetcher.doc_url(&id))
                });

                if current_depth < depth {
                    for ref_id in &meta.references {
                        if !visited.contains(ref_id) {
                            queue.push(ref_id.clone());
                        }
                    }
                }

                collected.push(meta);
            }
        }

        collected
    }

    // ── Assembly: build graph + run analysis ──────────────────────────────────

    async fn assemble(&self, metas: Vec<DocumentMeta>) -> GraphData {
        // Run structural and semantic analysis concurrently.
        let structural = self.analyzer.analyze(&metas);
        let semantic = match &self.ml_client {
            Some(client) => client.analyze(&metas).await,
            None => vec![],
        };

        let issues: Vec<_> = structural.into_iter().chain(semantic).collect();
        let issues = issues;

        // Count issues per document for node colouring in the frontend
        let mut issue_counts: HashMap<&str, usize> = HashMap::new();
        for issue in &issues {
            for id in &issue.document_ids {
                *issue_counts.entry(id.as_str()).or_default() += 1;
            }
        }

        // Build links
        let mut links: Vec<GraphLink> = Vec::new();
        let all_ids: HashSet<&str> = metas.iter().map(|m| m.id.as_str()).collect();

        for meta in &metas {
            for ref_id in &meta.references {
                if all_ids.contains(ref_id.as_str()) {
                    links.push(GraphLink {
                        source: meta.id.as_str().to_owned(),
                        target: ref_id.as_str().to_owned(),
                    });
                }
            }
        }

        let nodes = metas
            .iter()
            .map(|meta| {
                let count = issue_counts.get(meta.id.as_str()).copied().unwrap_or(0);
                GraphNode::from_meta(meta, count)
            })
            .collect();

        GraphData { nodes, links, issues }
    }

    // ── Fetch helpers ─────────────────────────────────────────────────────────

    async fn fetch_batch(&self, ids: &[DocumentId]) -> Vec<Result<DocumentMeta, FetchError>> {
        let futures: Vec<_> = ids.iter().map(|id| self.fetch_one(id)).collect();
        futures::future::join_all(futures).await
    }

    async fn fetch_one(&self, id: &DocumentId) -> Result<DocumentMeta, FetchError> {
        let url = self.fetcher.doc_url(id);
        let html = self.fetcher.fetch_html(&url).await?;
        Ok(self.parser.parse(&html, id.clone(), url))
    }
}
