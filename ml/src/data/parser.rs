use reqwest::Client;
use scraper::{Html, Selector};

struct DocumentData {
    title: String,
    text: String,
    status: Option<String>,
}

pub async fn parse_raw_document(doc_url: &str) -> Result<DocumentData, Box<dyn std::error::Error>> {
    let client = Client::builder()
        .danger_accept_invalid_certs(true)
        .danger_accept_invalid_hostnames(true)
        .build()?;

    let body = client
        .get(doc_url)
        .header("User-Agent", "Mozilla/5.0")
        .send()
        .await?
        .text()
        .await?;

    let document = Html::parse_document(&body);

    let title = parse_title(&document);

    let status_selector = Selector::parse("span.status").ok();
    let status = status_selector
        .as_ref()
        .and_then(|sel| document.select(sel).next())
        .map(|el| el.text().collect::<String>().trim().to_string())
        .filter(|s| !s.is_empty());

    let content_selector = Selector::parse("article").unwrap();

    let text: String = document
        .select(&content_selector)
        .flat_map(|el| el.text())
        .map(|t| t.trim())
        .filter(|t| !t.is_empty())
        .collect::<Vec<_>>()
        .join("\n");

    Ok(DocumentData {
        title,
        text,
        status,
    })
}

fn parse_title(document: &Html) -> String {
    if let Some(selector) = Selector::parse("h1").ok() {
        if let Some(el) = document.select(&selector).next() {
            let title = el.text().collect::<String>().trim().to_string();
            if !title.is_empty() && title.len() < 500 {
                return title;
            }
        }
    }

    if let Some(selector) =
        Selector::parse(".container_alpha.slogan, .container_alpha .slogan").ok()
    {
        if let Some(el) = document.select(&selector).next() {
            let title = el.text().collect::<String>().trim().to_string();
            if !title.is_empty() && title.len() < 500 {
                return title;
            }
        }
    }

    if let Some(selector) = Selector::parse("article h1, article h2, article .post_header").ok() {
        if let Some(el) = document.select(&selector).next() {
            let title = el.text().collect::<String>().trim().to_string();
            if !title.is_empty() && title.len() < 500 {
                return title;
            }
        }
    }

    "Без названия".to_string()
}
