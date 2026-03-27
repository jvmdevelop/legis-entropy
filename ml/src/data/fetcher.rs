use async_trait::async_trait;
use reqwest::Client;

use super::{error::FetchError, model::DocumentId};

/// Abstracts document retrieval so the graph builder is not coupled to HTTP.
#[async_trait]
pub trait DocumentFetcher: Send + Sync {
    async fn fetch_html(&self, url: &str) -> Result<String, FetchError>;
    fn doc_url(&self, id: &DocumentId) -> String;
}

// ── Әділет implementation ─────────────────────────────────────────────────────

pub struct AdiletFetcher {
    client: Client,
    base_url: String,
}

impl AdiletFetcher {
    pub fn new() -> Result<Self, reqwest::Error> {
        let client = Client::builder()
            .danger_accept_invalid_certs(true)
            .danger_accept_invalid_hostnames(true)
            .user_agent("Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36")
            .build()?;

        Ok(Self {
            client,
            base_url: "https://adilet.zan.kz/rus/docs/".to_owned(),
        })
    }
}

#[async_trait]
impl DocumentFetcher for AdiletFetcher {
    async fn fetch_html(&self, url: &str) -> Result<String, FetchError> {
        let resp = self.client.get(url).send().await?;
        if !resp.status().is_success() {
            return Err(FetchError::Status {
                status: resp.status().as_u16(),
                url: url.to_owned(),
            });
        }
        Ok(resp.text().await?)
    }

    fn doc_url(&self, id: &DocumentId) -> String {
        format!("{}{}", self.base_url, id)
    }
}
