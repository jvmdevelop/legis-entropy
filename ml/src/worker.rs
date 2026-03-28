use std::{sync::Arc, time::Duration};

use rand::Rng;
use tokio::sync::Semaphore;
use tracing::{error, info, warn};

use crate::{
    data::{fetcher::AdiletFetcher, parser::DocumentParser},
    db::Database,
};

const MAX_CONCURRENT: usize = 2;

const DELAY_MIN_MS: u64 = 1_500;
const DELAY_MAX_MS: u64 = 3_500;

const CAPTCHA_RETRY_DELAY_SECS: i64 = 60;

const CAPTCHA_SLOT_HOLD_SECS: u64 = 5;

const MAX_DEPTH: usize = 3;

const STALE_DAYS: i64 = 7;

const IDLE_SLEEP_SECS: u64 = 30;

const DEQUEUE_ERROR_SLEEP_SECS: u64 = 5;

struct FetchContext {
    fetcher: AdiletFetcher,
    parser: DocumentParser,
}

pub async fn run(db: Arc<Database>) {
    let ctx = match AdiletFetcher::new() {
        Ok(fetcher) => Arc::new(FetchContext {
            fetcher,
            parser: DocumentParser,
        }),
        Err(e) => {
            error!("Cannot create fetcher: {e}");
            return;
        }
    };

    let sem = Arc::new(Semaphore::new(MAX_CONCURRENT));
    info!("Worker started (max_depth={MAX_DEPTH}, max_concurrent={MAX_CONCURRENT})");

    loop {
        let item = match db.dequeue().await {
            Ok(Some(i)) => i,
            Ok(None) => {
                tokio::time::sleep(Duration::from_secs(IDLE_SLEEP_SECS)).await;
                continue;
            }
            Err(e) => {
                error!("Dequeue failed: {e}");
                tokio::time::sleep(Duration::from_secs(DEQUEUE_ERROR_SLEEP_SECS)).await;
                continue;
            }
        };

        match db.is_fresh(&item.id, STALE_DAYS).await {
            Ok(true) => {
                info!("Skip (fresh): {}", item.id);
                continue;
            }
            Err(e) => warn!("is_fresh check failed for {}: {e}", item.id),
            _ => {}
        }

        let delay = rand::thread_rng().gen_range(DELAY_MIN_MS..DELAY_MAX_MS);
        tokio::time::sleep(Duration::from_millis(delay)).await;

        let permit = sem.clone().acquire_owned().await.unwrap();
        let db2 = db.clone();
        let ctx2 = ctx.clone();
        let id = item.id.clone();
        let depth = item.depth;

        tokio::spawn(async move {
            let _permit = permit;
            process_one(&db2, &ctx2, &id, depth).await;
        });
    }
}

async fn process_one(db: &Database, ctx: &FetchContext, id: &str, depth: usize) {
    let url = format!("https://adilet.zan.kz/rus/docs/{id}");
    info!("Fetching {id} (depth={depth})");

    let html = match ctx.fetcher.fetch_html(&url).await {
        Ok(h) => h,
        Err(e) => {
            warn!("Fetch error {id}: {e}");
            if let Err(e2) = db.mark_failed(id).await {
                error!("mark_failed {id}: {e2}");
            }
            return;
        }
    };

    if ctx.parser.is_captcha(&html) {
        warn!("CAPTCHA for {id} — scheduling retry in {CAPTCHA_RETRY_DELAY_SECS}s");
        if let Err(e) = db.mark_captcha(id, CAPTCHA_RETRY_DELAY_SECS).await {
            error!("mark_captcha {id}: {e}");
        }
        tokio::time::sleep(Duration::from_secs(CAPTCHA_SLOT_HOLD_SECS)).await;
        return;
    }

    let doc = ctx
        .parser
        .parse(&html, crate::data::model::DocumentId::new(id), url);
    let title_preview: String = doc.title.chars().take(50).collect();
    info!(
        "Parsed {id}: '{title_preview}', {} refs, status={:?}",
        doc.references.len(),
        doc.status
    );

    let refs = doc.references.clone();
    if let Err(e) = db.save_document(&doc).await {
        error!("save_document {id}: {e}");
        return;
    }

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
