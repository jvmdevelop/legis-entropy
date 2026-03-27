use thiserror::Error;

#[derive(Debug, Error)]
pub enum FetchError {
    #[error("HTTP request failed: {0}")]
    Http(#[from] reqwest::Error),

    #[error("Unexpected HTTP {status} for {url}")]
    Status { status: u16, url: String },
}
