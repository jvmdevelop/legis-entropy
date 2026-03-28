//! Background BFS fetch worker.
//!
//! Runs as a long-lived Tokio task. Continuously pops items from the
//! persistent queue in SQLite, fetches them with rate limiting and CAPTCHA
//! detection, saves results, and enqueues discovered references.

use std::{sync::Arc, time::Duration};

use rand::Rng;
use tokio::sync::Semaphore;
use tracing::{error, info, warn};

use crate::{
    data::{fetcher::AdiletFetcher, parser::DocumentParser},
    db::Database,
};

// ── Tuning constants ──────────────────────────────────────────────────────────

/// Maximum simultaneous HTTP fetches.
const MAX_CONCURRENT: usize = 2;

/// Random delay range between requests (milliseconds).
const DELAY_MIN_MS: u64 = 1_500;
const DELAY_MAX_MS: u64 = 3_500;

/// How long to back off after a CAPTCHA (seconds the doc is delayed in queue).
const CAPTCHA_RETRY_DELAY_SECS: i64 = 3_600; // 1 hour

/// Extra sleep for the worker thread itself after detecting CAPTCHA,
/// so we don't hammer the next queued document immediately.
const CAPTCHA_WORKER_PAUSE_SECS: u64 = 30;

/// Maximum BFS depth to follow from seeds.
const MAX_DEPTH: usize = 3;

/// How many days before a successfully fetched doc is considered stale.
const STALE_DAYS: i64 = 7;

/// Sleep duration when the queue is empty.
const IDLE_SLEEP_SECS: u64 = 30;

// ── Entry point ───────────────────────────────────────────────────────────────

pub async fn run(db: Arc<Database>) {
    let fetcher = match AdiletFetcher::new() {
        Ok(f) => Arc::new(f),
        Err(e) => {
            error!("Cannot create fetcher: {e}");
            return;
        }
    };
    let parser = Arc::new(DocumentParser);
    let sem = Arc::new(Semaphore::new(MAX_CONCURRENT));

    info!("Worker started (max_depth={MAX_DEPTH}, max_concurrent={MAX_CONCURRENT})");

    loop {
        // ── Dequeue next ready item ───────────────────────────────────────────
        let item = match db.dequeue().await {
            Ok(Some(i)) => i,
            Ok(None) => {
                tokio::time::sleep(Duration::from_secs(IDLE_SLEEP_SECS)).await;
                continue;
            }
            Err(e) => {
                error!("Dequeue failed: {e}");
                tokio::time::sleep(Duration::from_secs(5)).await;
                continue;
            }
        };

        // ── Skip if still fresh ───────────────────────────────────────────────
        match db.is_fresh(&item.id, STALE_DAYS).await {
            Ok(true) => {
                info!("Skip (fresh): {}", item.id);
                continue;
            }
            Err(e) => {
                warn!("is_fresh check failed for {}: {e}", item.id);
            }
            _ => {}
        }

        // ── Rate limiting: random jitter delay ────────────────────────────────
        let delay = rand::thread_rng().gen_range(DELAY_MIN_MS..DELAY_MAX_MS);
        tokio::time::sleep(Duration::from_millis(delay)).await;

        // ── Acquire concurrency permit ────────────────────────────────────────
        let permit = sem.clone().acquire_owned().await.unwrap();

        let db2 = db.clone();
        let fetcher2 = fetcher.clone();
        let parser2 = parser.clone();
        let id = item.id.clone();
        let depth = item.depth;

        tokio::spawn(async move {
            let _permit = permit; // released when this task completes

            process_one(&db2, &fetcher2, &parser2, &id, depth).await;
        });
    }
}

// ── Per-document logic ────────────────────────────────────────────────────────

async fn process_one(
    db: &Database,
    fetcher: &AdiletFetcher,
    parser: &DocumentParser,
    id: &str,
    depth: usize,
) {
    let url = format!("https://adilet.zan.kz/rus/docs/{id}");
    info!("Fetching {id} (depth={depth})");

    // ── HTTP fetch ────────────────────────────────────────────────────────────
    let html = match fetcher.fetch_html(&url).await {
        Ok(h) => h,
        Err(e) => {
            warn!("Fetch error {id}: {e}");
            if let Err(e2) = db.mark_failed(id).await {
                error!("mark_failed {id}: {e2}");
            }
            return;
        }
    };

    // ── CAPTCHA detection ─────────────────────────────────────────────────────
    if parser.is_captcha(&html) {
        warn!(
            "CAPTCHA for {id} — scheduling retry in {}s",
            CAPTCHA_RETRY_DELAY_SECS
        );
        if let Err(e) = db.mark_captcha(id, CAPTCHA_RETRY_DELAY_SECS).await {
            error!("mark_captcha {id}: {e}");
        }
        // Let the worker loop sleep a bit before processing the next item
        tokio::time::sleep(Duration::from_secs(CAPTCHA_WORKER_PAUSE_SECS)).await;
        return;
    }

    // ── Parse ─────────────────────────────────────────────────────────────────
    let doc = parser.parse(
        &html,
        crate::data::model::DocumentId::new(id),
        url,
    );

    let title_preview = &doc.title[..doc.title.len().min(50)];
    info!(
        "Parsed {id}: '{title_preview}', {} refs, status={:?}",
        doc.references.len(),
        doc.status
    );

    // ── Persist ───────────────────────────────────────────────────────────────
    let refs = doc.references.clone();
    if let Err(e) = db.save_document(&doc).await {
        error!("save_document {id}: {e}");
        return;
    }

    // ── Enqueue discovered references ─────────────────────────────────────────
    if depth < MAX_DEPTH {
        for ref_id in &refs {
            if let Err(e) = db.enqueue(ref_id.as_str(), depth + 1).await {
                warn!("enqueue {} -> {}: {e}", id, ref_id.as_str());
            }
        }
        info!(
            "Enqueued {} refs from {id} at depth {}",
            refs.len(),
            depth + 1
        );
    }
}
