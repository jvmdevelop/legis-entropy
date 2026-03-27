use std::collections::{HashMap, HashSet};

use petgraph::{Graph, algo::tarjan_scc, graph::NodeIndex};

use super::model::{DocumentId, DocumentMeta, DocumentStatus, Issue, IssueKind, Severity};

// ── Tuning constants ──────────────────────────────────────────────────────────

/// Cosine similarity threshold above which two documents are flagged as duplicates.
const DUPLICATION_THRESHOLD: f32 = 0.70;

/// Words ignored during TF-IDF tokenization (Russian function words).
const STOPWORDS: &[&str] = &[
    "и", "в", "на", "с", "к", "о", "по", "из", "за", "не", "до", "от",
    "при", "для", "или", "что", "как", "так", "это", "но", "а", "же", "ли",
    "то", "бы", "был", "была", "были", "быть", "есть", "всё", "все", "он",
    "она", "они", "его", "её", "их", "мы", "вы", "им", "нам", "вам",
];

// ── Public interface ──────────────────────────────────────────────────────────

pub struct DocumentAnalyzer;

impl DocumentAnalyzer {
    pub fn new() -> Self {
        Self
    }

    /// Runs all checks and returns a de-duplicated list of issues.
    pub fn analyze(&self, docs: &[DocumentMeta]) -> Vec<Issue> {
        let status_map: HashMap<&DocumentId, &DocumentStatus> =
            docs.iter().map(|d| (&d.id, &d.status)).collect();

        let mut issues = Vec::new();
        issues.extend(self.outdated_references(docs, &status_map));
        issues.extend(self.circular_references(docs));
        issues.extend(self.duplications(docs));
        issues
    }
}

// ── Check: outdated references ────────────────────────────────────────────────

impl DocumentAnalyzer {
    fn outdated_references(
        &self,
        docs: &[DocumentMeta],
        status_map: &HashMap<&DocumentId, &DocumentStatus>,
    ) -> Vec<Issue> {
        docs.iter()
            .filter(|d| d.status == DocumentStatus::Active)
            .flat_map(|doc| {
                doc.references
                    .iter()
                    .filter(|ref_id| {
                        status_map
                            .get(ref_id)
                            .map(|s| **s == DocumentStatus::Outdated)
                            .unwrap_or(false)
                    })
                    .map(|ref_id| Issue {
                        kind: IssueKind::OutdatedReference,
                        severity: Severity::High,
                        document_ids: vec![
                            doc.id.as_str().to_owned(),
                            ref_id.as_str().to_owned(),
                        ],
                        explanation: format!(
                            "«{}» ссылается на утративший силу документ {}",
                            doc.title, ref_id
                        ),
                    })
                    .collect::<Vec<_>>()
            })
            .collect()
    }
}

// ── Check: circular references (Tarjan SCC) ───────────────────────────────────

impl DocumentAnalyzer {
    fn circular_references(&self, docs: &[DocumentMeta]) -> Vec<Issue> {
        // Build an index: DocumentId → petgraph NodeIndex
        let mut graph: Graph<&str, ()> = Graph::new();
        let mut index: HashMap<&DocumentId, NodeIndex> = HashMap::new();

        for doc in docs {
            let node = graph.add_node(doc.id.as_str());
            index.insert(&doc.id, node);
        }

        for doc in docs {
            let Some(&src) = index.get(&doc.id) else { continue };
            for ref_id in &doc.references {
                let Some(&dst) = index.get(ref_id) else { continue };
                graph.add_edge(src, dst, ());
            }
        }

        tarjan_scc(&graph)
            .into_iter()
            .filter(|scc| scc.len() > 1)
            .map(|scc| {
                let ids: Vec<String> = scc
                    .iter()
                    .map(|&nx| graph[nx].to_owned())
                    .collect();
                Issue {
                    kind: IssueKind::CircularReference,
                    severity: Severity::Medium,
                    explanation: format!(
                        "Циклические ссылки между {} документами: {}",
                        ids.len(),
                        ids.join(", ")
                    ),
                    document_ids: ids,
                }
            })
            .collect()
    }
}

// ── Check: duplication via TF-IDF cosine similarity ───────────────────────────

impl DocumentAnalyzer {
    fn duplications(&self, docs: &[DocumentMeta]) -> Vec<Issue> {
        let active: Vec<&DocumentMeta> = docs
            .iter()
            .filter(|d| d.status == DocumentStatus::Active && !d.text.is_empty())
            .collect();

        if active.len() < 2 {
            return vec![];
        }

        let texts: Vec<&str> = active.iter().map(|d| d.text.as_str()).collect();
        let tfidf = tfidf_vectors(&texts);

        let mut issues = Vec::new();
        let mut seen: HashSet<(usize, usize)> = HashSet::new();

        for i in 0..active.len() {
            for j in (i + 1)..active.len() {
                let key = (i.min(j), i.max(j));
                if seen.contains(&key) {
                    continue;
                }
                let sim = cosine_similarity(&tfidf[i], &tfidf[j]);
                if sim >= DUPLICATION_THRESHOLD {
                    seen.insert(key);
                    let pct = (sim * 100.0).round() as u8;
                    let severity = if sim >= 0.90 {
                        Severity::High
                    } else if sim >= 0.80 {
                        Severity::Medium
                    } else {
                        Severity::Low
                    };
                    issues.push(Issue {
                        kind: IssueKind::Duplication,
                        severity,
                        document_ids: vec![
                            active[i].id.as_str().to_owned(),
                            active[j].id.as_str().to_owned(),
                        ],
                        explanation: format!(
                            "«{}» и «{}» перекрываются на {}% по содержанию",
                            active[i].title, active[j].title, pct
                        ),
                    });
                }
            }
        }

        issues
    }
}

// ── TF-IDF helpers ────────────────────────────────────────────────────────────

/// Builds normalised TF-IDF vectors for every document in `texts`.
fn tfidf_vectors(texts: &[&str]) -> Vec<HashMap<String, f32>> {
    let token_lists: Vec<Vec<String>> = texts.iter().map(|t| tokenize(t)).collect();
    let n = texts.len() as f32;

    // Document frequency: how many docs contain each term
    let mut df: HashMap<&str, usize> = HashMap::new();
    for tokens in &token_lists {
        let unique: HashSet<&str> = tokens.iter().map(String::as_str).collect();
        for term in unique {
            *df.entry(term).or_default() += 1;
        }
    }

    token_lists
        .iter()
        .map(|tokens| {
            let total = tokens.len() as f32;
            if total == 0.0 {
                return HashMap::new();
            }

            // Term frequency
            let mut tf: HashMap<&str, f32> = HashMap::new();
            for term in tokens {
                *tf.entry(term.as_str()).or_default() += 1.0;
            }

            // TF-IDF with smoothed IDF
            let mut vec: HashMap<String, f32> = tf
                .iter()
                .map(|(&term, &count)| {
                    let idf = ((n + 1.0) / (*df.get(term).unwrap_or(&1) as f32 + 1.0)).ln() + 1.0;
                    (term.to_owned(), (count / total) * idf)
                })
                .collect();

            // L2 normalisation
            let norm: f32 = vec.values().map(|v| v * v).sum::<f32>().sqrt();
            if norm > 0.0 {
                vec.values_mut().for_each(|v| *v /= norm);
            }

            vec
        })
        .collect()
}

fn cosine_similarity(a: &HashMap<String, f32>, b: &HashMap<String, f32>) -> f32 {
    // Iterate over the smaller map for efficiency
    let (small, large) = if a.len() <= b.len() { (a, b) } else { (b, a) };
    small
        .iter()
        .filter_map(|(term, &va)| large.get(term).map(|&vb| va * vb))
        .sum()
}

fn tokenize(text: &str) -> Vec<String> {
    let stops: HashSet<&str> = STOPWORDS.iter().copied().collect();
    text.split(|c: char| !c.is_alphanumeric())
        .map(str::trim)
        .filter(|t| t.len() >= 3)
        .map(|t| t.to_lowercase())
        .filter(|t| !stops.contains(t.as_str()))
        .collect()
}
