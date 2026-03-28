use std::fmt;
use serde::{Deserialize, Serialize};

/// Newtype for document identifiers to prevent mixing with arbitrary strings.
#[derive(Debug, Clone, PartialEq, Eq, PartialOrd, Ord, Hash, Serialize, Deserialize)]
pub struct DocumentId(pub String);

impl DocumentId {
    pub fn new(s: impl Into<String>) -> Self {
        Self(s.into())
    }

    pub fn as_str(&self) -> &str {
        &self.0
    }
}

impl fmt::Display for DocumentId {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str(&self.0)
    }
}

impl From<&str> for DocumentId {
    fn from(s: &str) -> Self {
        Self(s.to_owned())
    }
}

impl From<String> for DocumentId {
    fn from(s: String) -> Self {
        Self(s)
    }
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "lowercase")]
pub enum DocumentStatus {
    Active,
    Outdated,
    Unknown,
}

/// Parsed document — used by both the graph builder and the analyzer.
#[derive(Debug, Clone)]
pub struct DocumentMeta {
    pub id: DocumentId,
    pub title: String,
    pub url: String,
    pub status: DocumentStatus,
    pub references: Vec<DocumentId>,
    /// Body text, truncated to `TEXT_LIMIT` chars to keep memory bounded.
    pub text: String,
}

impl DocumentMeta {
    pub fn placeholder(id: DocumentId, url: String) -> Self {
        Self {
            id,
            url,
            title: "Недоступен".to_owned(),
            status: DocumentStatus::Unknown,
            references: vec![],
            text: String::new(),
        }
    }
}

// ── Analysis types ────────────────────────────────────────────────────────────

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum IssueKind {
    /// Two active documents with heavily overlapping content (BERT similarity).
    Duplication,
    /// Two documents with similar topic but opposing normative prescriptions (BERT + keywords).
    Contradiction,
    /// An active document references a document that has lost legal force.
    OutdatedReference,
    /// A group of documents reference each other in a cycle.
    CircularReference,
    /// Document is an amendment act ("О внесении изменений") modifying another document.
    Amendment,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "lowercase")]
pub enum Severity {
    Low,
    Medium,
    High,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Issue {
    pub kind: IssueKind,
    pub severity: Severity,
    /// IDs of the documents involved in this issue.
    pub document_ids: Vec<String>,
    pub explanation: String,
}

// ── Graph serialization types ─────────────────────────────────────────────────

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct GraphNode {
    pub id: String,
    pub title: String,
    pub url: String,
    pub status: DocumentStatus,
    pub ref_count: usize,
    pub issue_count: usize,
    /// Number of articles ("Статья N") found in the document body.
    pub article_count: usize,
    /// True if this document is an amendment act ("О внесении изменений…").
    pub is_amendment: bool,
}

impl GraphNode {
    pub fn from_meta(meta: &DocumentMeta, issue_count: usize) -> Self {
        Self {
            id: meta.id.as_str().to_owned(),
            title: meta.title.clone(),
            url: meta.url.clone(),
            status: meta.status.clone(),
            ref_count: meta.references.len(),
            issue_count,
            article_count: count_articles(&meta.text),
            is_amendment: is_amendment_title(&meta.title),
        }
    }
}

fn count_articles(text: &str) -> usize {
    // Count "статья " occurrences (case-insensitive). Use match_indices for
    // safety — avoids manual byte-offset arithmetic on multi-byte Cyrillic chars.
    text.to_lowercase().match_indices("статья ").count()
}

pub fn is_amendment_title(title: &str) -> bool {
    let lower = title.to_lowercase();
    lower.starts_with("о внесении изменений")
        || lower.starts_with("о внесении дополнений")
        || lower.starts_with("о внесении поправок")
        || lower.starts_with("об изменении")
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct GraphLink {
    pub source: String,
    pub target: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct GraphData {
    pub nodes: Vec<GraphNode>,
    pub links: Vec<GraphLink>,
    pub issues: Vec<Issue>,
}
