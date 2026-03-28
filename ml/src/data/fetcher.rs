//! HTTP fetcher with UA rotation and realistic browser headers.

use rand::seq::SliceRandom;
use reqwest::Client;

use super::error::FetchError;

/// A pool of real browser user-agent strings to rotate through.
const USER_AGENTS: &[&str] = &[
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36",
    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:109.0) Gecko/20100101 Firefox/121.0",
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.15; rv:109.0) Gecko/20100101 Firefox/121.0",
    "Mozilla/5.0 (X11; Linux x86_64; rv:109.0) Gecko/20100101 Firefox/121.0",
];

pub struct AdiletFetcher {
    client: Client,
}

impl AdiletFetcher {
    pub fn new() -> Result<Self, reqwest::Error> {
        let client = Client::builder()
            .danger_accept_invalid_certs(true)
            .danger_accept_invalid_hostnames(true)
            .timeout(std::time::Duration::from_secs(30))
            .gzip(true)
            .build()?;

        Ok(Self { client })
    }

    /// Fetch raw HTML from `url` with a random UA and realistic browser headers.
    pub async fn fetch_html(&self, url: &str) -> Result<String, FetchError> {
        let ua = USER_AGENTS
            .choose(&mut rand::thread_rng())
            .copied()
            .unwrap_or(USER_AGENTS[0]);

        let resp = self
            .client
            .get(url)
            .header("User-Agent", ua)
            .header(
                "Accept",
                "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
            )
            .header("Accept-Language", "ru-RU,ru;q=0.9,kk;q=0.8,en;q=0.5")
            .header("Accept-Encoding", "gzip, deflate, br")
            .header("Connection", "keep-alive")
            .header("Upgrade-Insecure-Requests", "1")
            .send()
            .await?;

        if !resp.status().is_success() {
            return Err(FetchError::Status {
                status: resp.status().as_u16(),
                url: url.to_owned(),
            });
        }

        Ok(resp.text().await?)
    }
}
