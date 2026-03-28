//! HTML → `DocumentMeta` parser with CAPTCHA detection.

use scraper::{Html, Selector};

use super::model::{DocumentId, DocumentMeta, DocumentStatus};

/// Body text is capped so memory stays bounded.
const TEXT_LIMIT: usize = 8_000;

const TITLE_SELECTORS: &[&str] = &[
    "h1",
    ".container_alpha.slogan",
    ".container_alpha .slogan",
    "article h1",
    "article h2",
    "article .post_header",
];

const STATUS_SELECTORS: &[&str] = &[
    "span.status",
    ".doc_status",
    ".status_label",
    "span.label",
];

/// Substrings that indicate the page is a CAPTCHA / bot-check wall.
/// Checked case-insensitively against the raw HTML.
/// Keep these specific — generic words like "captcha" or "cloudflare" appear
/// in normal pages (ad scripts, CDN references) and cause false positives.
const CAPTCHA_MARKERS: &[&str] = &[
    "вы не робот",
    "g-recaptcha",
    "cf-browser-verification",
    "ddos-guard",
    "verify you are human",
    "please complete the security check",
];

// ── Public interface ──────────────────────────────────────────────────────────

pub struct DocumentParser;

impl DocumentParser {
    /// Returns `true` if the HTML looks like a CAPTCHA / anti-bot page.
    ///
    /// This is a fast string scan — no DOM parsing needed.
    pub fn is_captcha(&self, html: &str) -> bool {
        let lower = html.to_lowercase();
        CAPTCHA_MARKERS.iter().any(|marker| lower.contains(marker))
    }

    /// Parse raw HTML into a `DocumentMeta`.
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
}

// ── Private extraction logic ──────────────────────────────────────────────────

impl DocumentParser {
    fn extract_text(&self, doc: &Html) -> String {
        let Ok(sel) = Selector::parse("article") else {
            return String::new();
        };
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
        // Try CSS selectors first
        let from_selectors = STATUS_SELECTORS.iter().find_map(|sel| {
            let text = self.first_text(doc, sel)?.to_lowercase();
            if text.contains("утратил") || text.contains("недействующ") {
                Some(DocumentStatus::Outdated)
            } else if text.contains("действующ") || text.contains("актуальн") {
                Some(DocumentStatus::Active)
            } else {
                None
            }
        });
        if let Some(s) = from_selectors {
            return s;
        }

        // Fallback: scan the first 2 000 chars of body text for status cues.
        // adilet.zan.kz embeds status notes like "Сноска. Утратил силу …"
        // or "Действующим правом …" inside the article body.
        let body = self.extract_text_short(doc, 2_000).to_lowercase();
        // Outdated markers are more specific — check them first.
        if body.contains("утратил силу")
            || body.contains("утратила силу")
            || body.contains("утратило силу")
            || body.contains("утратили силу")
            || body.contains("недействующ")
        {
            return DocumentStatus::Outdated;
        }
        // Active markers
        if body.contains("действующ") || body.contains("актуальн") {
            return DocumentStatus::Active;
        }

        DocumentStatus::Unknown
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
                let raw = href.trim_end_matches('/').split('/').last()?;
                // Strip intra-document anchor fragments (#z123) — these are article
                // sections within the same document, not separate documents.
                let id = raw.split('#').next().unwrap_or(raw);
                (id.len() > 3).then(|| DocumentId::new(id))
            })
            .collect();

        refs.sort();
        refs.dedup();
        refs
    }

    /// Returns up to `limit` *chars* of body text (faster than full extract_text).
    fn extract_text_short(&self, doc: &Html, limit: usize) -> String {
        let Ok(sel) = Selector::parse("article") else { return String::new() };
        let mut out = String::with_capacity(limit * 3); // UTF-8: up to 3 bytes/char
        let mut char_count = 0usize;
        'outer: for el in doc.select(&sel) {
            for chunk in el.text() {
                let chunk = chunk.trim();
                if chunk.is_empty() { continue; }
                for ch in chunk.chars() {
                    if char_count >= limit { break 'outer; }
                    out.push(ch);
                    char_count += 1;
                }
                if char_count < limit { out.push(' '); char_count += 1; }
            }
        }
        out
    }

    fn first_text(&self, doc: &Html, selector: &str) -> Option<String> {
        let sel = Selector::parse(selector).ok()?;
        let text = doc.select(&sel).next()?.text().collect::<String>();
        let trimmed = text.trim().to_owned();
        (!trimmed.is_empty() && trimmed.len() < 500).then_some(trimmed)
    }
}
