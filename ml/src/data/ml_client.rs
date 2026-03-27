use serde::{Deserialize, Serialize};

use super::model::{DocumentMeta, DocumentStatus, Issue, IssueKind, Severity};

// ── Wire types (mirror ml_service/models.py) ──────────────────────────────────

#[derive(Serialize)]
struct MlDocument<'a> {
    id: &'a str,
    title: &'a str,
    text: &'a str,
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

    /// Sends documents to the ML service and returns semantic issues.
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
                    status: status_str(&d.status),
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
                Ok(r) => r.issues.into_iter().filter_map(from_wire).collect(),
            },
        }
    }
}

// ── Conversion helpers ────────────────────────────────────────────────────────

fn status_str(s: &DocumentStatus) -> &'static str {
    match s {
        DocumentStatus::Active => "active",
        DocumentStatus::Outdated => "outdated",
        DocumentStatus::Unknown => "unknown",
    }
}

fn from_wire(w: MlIssue) -> Option<Issue> {
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
