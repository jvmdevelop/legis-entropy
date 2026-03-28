use serde::{Deserialize, Serialize};
use std::{fmt, str::FromStr};

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

#[derive(Debug, Clone, Default, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "lowercase")]
pub enum DocumentStatus {
    Active,
    Outdated,
    #[default]
    Unknown,
}

impl DocumentStatus {
    pub fn as_str(&self) -> &'static str {
        match self {
            Self::Active => "active",
            Self::Outdated => "outdated",
            Self::Unknown => "unknown",
        }
    }
}

impl fmt::Display for DocumentStatus {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str(self.as_str())
    }
}

impl FromStr for DocumentStatus {
    type Err = std::convert::Infallible;
    fn from_str(s: &str) -> Result<Self, Self::Err> {
        Ok(match s {
            "active" => Self::Active,
            "outdated" => Self::Outdated,
            _ => Self::Unknown,
        })
    }
}

#[derive(Debug, Clone)]
pub struct DocumentMeta {
    pub id: DocumentId,
    pub title: String,
    pub url: String,
    pub status: DocumentStatus,
    pub references: Vec<DocumentId>,
    pub text: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum IssueKind {
    Duplication,
    Contradiction,
    OutdatedReference,
    CircularReference,
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
    pub document_ids: Vec<String>,
    pub explanation: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct GraphNode {
    pub id: String,
    pub title: String,
    pub url: String,
    pub status: DocumentStatus,
    pub ref_count: usize,
    pub issue_count: usize,
    pub article_count: usize,
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
