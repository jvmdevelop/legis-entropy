use scraper::{Html, Selector};

use super::model::{DocumentId, DocumentMeta, DocumentStatus};

/// Body text is capped so memory stays bounded on low-end hardware.
const TEXT_LIMIT: usize = 8_000;

const TITLE_SELECTORS: &[&str] = &[
    "h1",
    ".container_alpha.slogan, .container_alpha .slogan",
    "article h1, article h2, article .post_header",
];

const STATUS_SELECTORS: &[&str] = &[
    "span.status",
    ".doc_status",
    ".status_label",
    "span.label",
];

/// Parses raw HTML into a `DocumentMeta`. Has no I/O dependencies.
pub struct DocumentParser;

impl DocumentParser {
    pub fn parse(&self, html: &str, id: DocumentId, url: String) -> DocumentMeta {
        let doc = Html::parse_document(html);
        DocumentMeta {
            id,
            title: self.extract_title(&doc),
            url,
            status: self.extract_status(&doc),
            references: self.extract_references(&doc),
            text: self.extract_text(&doc),
        }
    }

    fn extract_text(&self, doc: &Html) -> String {
        let Ok(sel) = Selector::parse("article") else { return String::new() };
        let full: String = doc
            .select(&sel)
            .flat_map(|el| el.text())
            .map(str::trim)
            .filter(|t| !t.is_empty())
            .collect::<Vec<_>>()
            .join(" ");
        full.chars().take(TEXT_LIMIT).collect()
    }

    fn extract_title(&self, doc: &Html) -> String {
        TITLE_SELECTORS
            .iter()
            .find_map(|sel| self.first_text(doc, sel))
            .unwrap_or_else(|| "Без названия".to_owned())
    }

    fn extract_status(&self, doc: &Html) -> DocumentStatus {
        STATUS_SELECTORS
            .iter()
            .find_map(|sel| {
                let text = self.first_text(doc, sel)?.to_lowercase();
                if text.contains("утратил") || text.contains("недействующ") {
                    Some(DocumentStatus::Outdated)
                } else if text.contains("действующ") || text.contains("актуальн") {
                    Some(DocumentStatus::Active)
                } else {
                    None
                }
            })
            .unwrap_or(DocumentStatus::Unknown)
    }

    fn extract_references(&self, doc: &Html) -> Vec<DocumentId> {
        let Ok(sel) = Selector::parse("article a[href]") else {
            return vec![];
        };

        let mut refs: Vec<DocumentId> = doc
            .select(&sel)
            .filter_map(|el| el.value().attr("href"))
            .filter(|href| href.contains("/docs/"))
            .filter_map(|href| {
                let id = href.trim_end_matches('/').split('/').last()?;
                (id.len() > 3).then(|| DocumentId::new(id))
            })
            .collect();

        refs.sort();
        refs.dedup();
        refs
    }

    /// Returns the trimmed text content of the first element matching `selector`,
    /// or `None` if no match or the text is empty / too long to be a title.
    fn first_text(&self, doc: &Html, selector: &str) -> Option<String> {
        let sel = Selector::parse(selector).ok()?;
        let text = doc.select(&sel).next()?.text().collect::<String>();
        let trimmed = text.trim().to_owned();
        (!trimmed.is_empty() && trimmed.len() < 500).then_some(trimmed)
    }
}
