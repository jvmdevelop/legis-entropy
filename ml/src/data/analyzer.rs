//! Structural document analysis — pure Rust, no ML service needed.
//!
//! Detects:
//!  - outdated references (active doc → outdated doc)
//!  - circular reference cycles (Tarjan SCC)
//!  - content duplicates (TF-IDF cosine similarity)
//!  - amendment acts ("О внесении изменений…")

use std::collections::{HashMap, HashSet};

use petgraph::{algo::tarjan_scc, graph::NodeIndex, Graph};

use super::{
    model::{is_amendment_title, DocumentId, DocumentMeta, DocumentStatus, Issue, IssueKind, Severity},
    tfidf,
};

/// Cosine-similarity threshold above which two documents are flagged as duplicates.
const DUPLICATION_THRESHOLD: f32 = 0.70;

// ── Public interface ──────────────────────────────────────────────────────────

pub struct DocumentAnalyzer;

impl DocumentAnalyzer {
    pub fn new() -> Self {
        Self
    }

    /// Run all checks and return a de-duplicated list of issues.
    pub fn analyze(&self, docs: &[DocumentMeta]) -> Vec<Issue> {
        let status_map: HashMap<&DocumentId, &DocumentStatus> =
            docs.iter().map(|d| (&d.id, &d.status)).collect();

        let mut issues = Vec::new();
        issues.extend(outdated_references(docs, &status_map));
        issues.extend(circular_references(docs));
        issues.extend(duplications(docs));
        issues.extend(amendments(docs));
        issues
    }
}

// ── Check: outdated references ────────────────────────────────────────────────

fn outdated_references(
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

// ── Check: circular references (Tarjan SCC) ───────────────────────────────────

fn circular_references(docs: &[DocumentMeta]) -> Vec<Issue> {
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
            let ids: Vec<String> = scc.iter().map(|&nx| graph[nx].to_owned()).collect();
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

// ── Check: structural duplication via TF-IDF cosine similarity ────────────────

fn duplications(docs: &[DocumentMeta]) -> Vec<Issue> {
    let active: Vec<&DocumentMeta> = docs
        .iter()
        .filter(|d| d.status == DocumentStatus::Active && !d.text.is_empty())
        .collect();

    if active.len() < 2 {
        return vec![];
    }

    let texts: Vec<&str> = active.iter().map(|d| d.text.as_str()).collect();
    let vectors = tfidf::vectorize(&texts);

    let mut issues = Vec::new();
    let mut seen: HashSet<(usize, usize)> = HashSet::new();

    for i in 0..active.len() {
        for j in (i + 1)..active.len() {
            let key = (i, j); // i < j always, so no need for min/max
            if seen.contains(&key) {
                continue;
            }
            let sim = tfidf::cosine_similarity(&vectors[i], &vectors[j]);
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

// ── Check: amendment acts ("О внесении изменений…") ──────────────────────────

fn amendments(docs: &[DocumentMeta]) -> Vec<Issue> {
    docs.iter()
        .filter(|d| is_amendment_title(&d.title))
        .map(|doc| {
            let amended_count = doc.references.len();
            let sample_str = doc.references.iter().take(3).map(|r| r.as_str()).collect::<Vec<_>>().join(", ");
            let explanation = if amended_count > 0 {
                format!(
                    "«{}» является актом внесения изменений, затрагивает {} документ(а/ов) \
                     (в т.ч. {}{}). Базовые акты могут содержать устаревший текст без учёта поправок.",
                    doc.title,
                    amended_count,
                    sample_str,
                    if amended_count > 3 { " и др." } else { "" }
                )
            } else {
                format!(
                    "«{}» — акт внесения изменений; изменяемые документы не определены из ссылок.",
                    doc.title
                )
            };
            Issue {
                kind: IssueKind::Amendment,
                severity: Severity::Low,
                document_ids: std::iter::once(doc.id.as_str().to_owned())
                    .chain(doc.references.iter().map(|r| r.as_str().to_owned()))
                    .collect(),
                explanation,
            }
        })
        .collect()
}
