//! Async client for the Python ML service (`/analyze` endpoint).
//!
//! Returns an empty vec on any error so the server degrades gracefully when
//! the ML service is unavailable or still warming up.

use serde::{Deserialize, Serialize};

use super::model::{DocumentMeta, Issue, IssueKind, Severity};

// ── Wire types (mirror ml_service/models.py) ──────────────────────────────────

#[derive(Serialize)]
struct MlDocument<'a> {
    id: &'a str,
    title: &'a str,
    text: &'a str,
    /// Serialised via `DocumentStatus::as_str()` — always a static string.
    status: &'static str,
}

#[derive(Serialize)]
struct MlRequest<'a> {
    documents: Vec<MlDocument<'a>>,
}

#[derive(Deserialize)]
struct MlIssue {
    kind: String,
    severity: String,
    document_ids: Vec<String>,
    explanation: String,
    #[allow(dead_code)]
    score: f32,
}

#[derive(Deserialize)]
struct MlResponse {
    issues: Vec<MlIssue>,
}

// ── Client ────────────────────────────────────────────────────────────────────

pub struct MlClient {
    client: reqwest::Client,
    analyze_url: String,
}

impl MlClient {
    pub fn new(base_url: impl Into<String>) -> Self {
        Self {
            client: reqwest::Client::new(),
            analyze_url: format!("{}/analyze", base_url.into()),
        }
    }

    /// Send documents to the ML service and return semantic issues.
    ///
    /// Returns an empty vec (with a warning) if the service is unavailable.
    pub async fn analyze(&self, docs: &[DocumentMeta]) -> Vec<Issue> {
        let payload = MlRequest {
            documents: docs
                .iter()
                .filter(|d| !d.text.is_empty())
                .map(|d| MlDocument {
                    id: d.id.as_str(),
                    title: &d.title,
                    text: &d.text,
                    status: d.status.as_str(),
                })
                .collect(),
        };

        if payload.documents.is_empty() {
            return vec![];
        }

        match self.client.post(&self.analyze_url).json(&payload).send().await {
            Err(e) => {
                tracing::warn!("ML service unreachable, skipping semantic analysis: {e}");
                vec![]
            }
            Ok(resp) => match resp.json::<MlResponse>().await {
                Err(e) => {
                    tracing::warn!("ML service returned invalid JSON: {e}");
                    vec![]
                }
                Ok(r) => r.issues.into_iter().filter_map(issue_from_wire).collect(),
            },
        }
    }
}

// ── Wire → domain conversion ──────────────────────────────────────────────────

fn issue_from_wire(w: MlIssue) -> Option<Issue> {
    let kind = match w.kind.as_str() {
        "duplication" => IssueKind::Duplication,
        "contradiction" => IssueKind::Contradiction,
        other => {
            tracing::warn!("Unknown ML issue kind: {other}");
            return None;
        }
    };

    let severity = match w.severity.as_str() {
        "high" => Severity::High,
        "medium" => Severity::Medium,
        _ => Severity::Low,
    };

    Some(Issue {
        kind,
        severity,
        document_ids: w.document_ids,
        explanation: w.explanation,
    })
}
