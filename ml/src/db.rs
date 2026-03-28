//! SQLite persistence layer.
//!
//! All methods are async wrappers around blocking `rusqlite` calls via
//! `tokio::task::spawn_blocking`. The `Database` handle is cheaply cloneable
//! (`Arc` inside) and safe to share across tasks.

use std::sync::{Arc, Mutex};

use anyhow::{Context, Result};
use chrono::Utc;
use rusqlite::{params, Connection};

use crate::data::model::{DocumentId, DocumentMeta, DocumentStatus};

// ── Schema ────────────────────────────────────────────────────────────────────

const MIGRATIONS: &str = "
PRAGMA journal_mode = WAL;
PRAGMA foreign_keys = ON;

CREATE TABLE IF NOT EXISTS documents (
    id               TEXT PRIMARY KEY,
    title            TEXT NOT NULL DEFAULT 'Загрузка...',
    url              TEXT NOT NULL DEFAULT '',
    status           TEXT NOT NULL DEFAULT 'unknown',
    body_text        TEXT NOT NULL DEFAULT '',
    fetched_at       INTEGER,          -- unix seconds; NULL = never fetched
    captcha_blocked  INTEGER NOT NULL DEFAULT 0,
    fetch_attempts   INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS doc_refs (
    source_id  TEXT NOT NULL,
    target_id  TEXT NOT NULL,
    PRIMARY KEY (source_id, target_id)
);

-- BFS fetch queue.
-- attempt_after = 0 means 'process immediately'.
-- Set to future unix timestamp to delay retry (e.g. after CAPTCHA).
CREATE TABLE IF NOT EXISTS fetch_queue (
    id             TEXT PRIMARY KEY,
    depth          INTEGER NOT NULL DEFAULT 0,
    added_at       INTEGER NOT NULL,
    attempt_after  INTEGER NOT NULL DEFAULT 0
);
";

// ── Handle ────────────────────────────────────────────────────────────────────

#[derive(Clone)]
pub struct Database {
    conn: Arc<Mutex<Connection>>,
}

// Public data types returned by DB queries
pub struct QueueItem {
    pub id: String,
    pub depth: usize,
}

pub struct WorkerStatus {
    pub total_docs: i64,
    pub fetched_docs: i64,
    pub queued: i64,
    pub captcha_blocked: i64,
}

// ── Constructor ───────────────────────────────────────────────────────────────

impl Database {
    pub async fn open(path: &str) -> Result<Self> {
        let path = path.to_owned();
        let conn = tokio::task::spawn_blocking(move || -> Result<Connection> {
            let conn = Connection::open(&path)
                .with_context(|| format!("opening SQLite at {path}"))?;
            conn.execute_batch(MIGRATIONS).context("running migrations")?;
            Ok(conn)
        })
        .await
        .context("spawn_blocking panicked")??;

        Ok(Self {
            conn: Arc::new(Mutex::new(conn)),
        })
    }
}

// ── Seeding ───────────────────────────────────────────────────────────────────

impl Database {
    /// Insert seed documents + queue them if the DB is completely empty.
    pub async fn seed_if_empty(&self, seeds: &[&str]) -> Result<()> {
        let seeds: Vec<String> = seeds.iter().map(|&s| s.to_owned()).collect();
        let conn = self.conn.clone();

        tokio::task::spawn_blocking(move || -> Result<()> {
            let conn = conn.lock().unwrap();
            let count: i64 =
                conn.query_row("SELECT COUNT(*) FROM documents", [], |r| r.get(0))?;
            if count > 0 {
                return Ok(());
            }

            let now = Utc::now().timestamp();
            for id in &seeds {
                let url = format!("https://adilet.zan.kz/rus/docs/{id}");
                conn.execute(
                    "INSERT OR IGNORE INTO documents (id, url) VALUES (?1, ?2)",
                    params![id, url],
                )?;
                conn.execute(
                    "INSERT OR IGNORE INTO fetch_queue (id, depth, added_at, attempt_after)
                     VALUES (?1, 0, ?2, 0)",
                    params![id, now],
                )?;
            }
            tracing::info!("Seeded {} documents", seeds.len());
            Ok(())
        })
        .await
        .context("spawn_blocking panicked")?
    }
}

// ── Queue operations ──────────────────────────────────────────────────────────

impl Database {
    /// Pop the next ready item from the queue (lowest depth, oldest added_at,
    /// attempt_after in the past). Returns `None` if the queue is empty or all
    /// items are delayed.
    pub async fn dequeue(&self) -> Result<Option<QueueItem>> {
        let conn = self.conn.clone();
        tokio::task::spawn_blocking(move || -> Result<Option<QueueItem>> {
            let conn = conn.lock().unwrap();
            let now = Utc::now().timestamp();

            let item = conn
                .query_row(
                    "SELECT id, depth FROM fetch_queue
                     WHERE attempt_after <= ?1
                     ORDER BY depth ASC, added_at ASC
                     LIMIT 1",
                    params![now],
                    |r| {
                        Ok(QueueItem {
                            id: r.get(0)?,
                            depth: r.get::<_, usize>(1)?,
                        })
                    },
                )
                .ok();

            if let Some(ref item) = item {
                conn.execute(
                    "DELETE FROM fetch_queue WHERE id = ?1",
                    params![item.id],
                )?;
            }
            Ok(item)
        })
        .await
        .context("spawn_blocking panicked")?
    }

    /// Enqueue a document (insert stub if unknown), scheduling it for immediate
    /// processing. Silently ignored if already in the queue.
    pub async fn enqueue(&self, id: &str, depth: usize) -> Result<()> {
        let id = id.to_owned();
        let url = format!("https://adilet.zan.kz/rus/docs/{id}");
        let conn = self.conn.clone();

        tokio::task::spawn_blocking(move || -> Result<()> {
            let conn = conn.lock().unwrap();
            let now = Utc::now().timestamp();
            conn.execute(
                "INSERT OR IGNORE INTO documents (id, url) VALUES (?1, ?2)",
                params![id, url],
            )?;
            conn.execute(
                "INSERT OR IGNORE INTO fetch_queue (id, depth, added_at, attempt_after)
                 VALUES (?1, ?2, ?3, 0)",
                params![id, depth as i64, now],
            )?;
            Ok(())
        })
        .await
        .context("spawn_blocking panicked")?
    }

    /// Re-enqueue seeds (called by the refresh endpoint).
    pub async fn enqueue_seeds(&self, seeds: &[&str]) -> Result<()> {
        let seeds: Vec<String> = seeds.iter().map(|&s| s.to_owned()).collect();
        let conn = self.conn.clone();

        tokio::task::spawn_blocking(move || -> Result<()> {
            let conn = conn.lock().unwrap();
            let now = Utc::now().timestamp();
            for id in &seeds {
                conn.execute(
                    "INSERT OR IGNORE INTO fetch_queue (id, depth, added_at, attempt_after)
                     VALUES (?1, 0, ?2, 0)",
                    params![id, now],
                )?;
            }
            Ok(())
        })
        .await
        .context("spawn_blocking panicked")?
    }
}

// ── Freshness ─────────────────────────────────────────────────────────────────

impl Database {
    /// Returns `true` if the document was successfully fetched within `days`.
    pub async fn is_fresh(&self, id: &str, days: i64) -> Result<bool> {
        let id = id.to_owned();
        let conn = self.conn.clone();

        tokio::task::spawn_blocking(move || -> Result<bool> {
            let conn = conn.lock().unwrap();
            let row: Option<(Option<i64>, i64)> = conn
                .query_row(
                    "SELECT fetched_at, captcha_blocked FROM documents WHERE id = ?1",
                    params![id],
                    |r| Ok((r.get(0)?, r.get(1)?)),
                )
                .ok();

            match row {
                Some((Some(ts), 0)) => {
                    let age_secs = Utc::now().timestamp() - ts;
                    Ok(age_secs < days * 86_400)
                }
                _ => Ok(false),
            }
        })
        .await
        .context("spawn_blocking panicked")?
    }
}

// ── Document writes ───────────────────────────────────────────────────────────

impl Database {
    /// Persist a successfully fetched document and replace its references.
    pub async fn save_document(&self, doc: &DocumentMeta) -> Result<()> {
        let id = doc.id.as_str().to_owned();
        let title = doc.title.clone();
        let url = doc.url.clone();
        let status = status_to_str(&doc.status).to_owned();
        let text = doc.text.clone();
        let refs: Vec<String> = doc.references.iter().map(|r| r.as_str().to_owned()).collect();
        let conn = self.conn.clone();

        tokio::task::spawn_blocking(move || -> Result<()> {
            let conn = conn.lock().unwrap();
            let now = Utc::now().timestamp();

            conn.execute(
                "INSERT INTO documents (id, title, url, status, body_text, fetched_at,
                                        captcha_blocked, fetch_attempts)
                 VALUES (?1, ?2, ?3, ?4, ?5, ?6, 0, 0)
                 ON CONFLICT(id) DO UPDATE SET
                     title           = excluded.title,
                     url             = excluded.url,
                     status          = excluded.status,
                     body_text       = excluded.body_text,
                     fetched_at      = excluded.fetched_at,
                     captcha_blocked = 0,
                     fetch_attempts  = 0",
                params![id, title, url, status, text, now],
            )?;

            // Replace outgoing references
            conn.execute("DELETE FROM doc_refs WHERE source_id = ?1", params![id])?;
            for ref_id in &refs {
                conn.execute(
                    "INSERT OR IGNORE INTO doc_refs (source_id, target_id) VALUES (?1, ?2)",
                    params![id, ref_id],
                )?;
            }
            Ok(())
        })
        .await
        .context("spawn_blocking panicked")?
    }

    /// Mark a document as CAPTCHA-blocked and schedule a retry in `retry_after_secs`.
    pub async fn mark_captcha(&self, id: &str, retry_after_secs: i64) -> Result<()> {
        let id = id.to_owned();
        let conn = self.conn.clone();

        tokio::task::spawn_blocking(move || -> Result<()> {
            let conn = conn.lock().unwrap();
            let now = Utc::now().timestamp();
            conn.execute(
                "UPDATE documents
                 SET captcha_blocked = 1, fetch_attempts = fetch_attempts + 1
                 WHERE id = ?1",
                params![id],
            )?;
            // Re-enqueue with delay
            conn.execute(
                "INSERT OR REPLACE INTO fetch_queue (id, depth, added_at, attempt_after)
                 VALUES (?1,
                         COALESCE((SELECT depth FROM fetch_queue WHERE id = ?1), 0),
                         ?2,
                         ?3)",
                params![id, now, now + retry_after_secs],
            )?;
            Ok(())
        })
        .await
        .context("spawn_blocking panicked")?
    }

    /// Increment fetch_attempts for a network/parsing error (non-CAPTCHA).
    pub async fn mark_failed(&self, id: &str) -> Result<()> {
        let id = id.to_owned();
        let conn = self.conn.clone();

        tokio::task::spawn_blocking(move || -> Result<()> {
            let conn = conn.lock().unwrap();
            conn.execute(
                "UPDATE documents SET fetch_attempts = fetch_attempts + 1 WHERE id = ?1",
                params![id],
            )?;
            Ok(())
        })
        .await
        .context("spawn_blocking panicked")?
    }
}

// ── Document reads ────────────────────────────────────────────────────────────

impl Database {
    /// Return all successfully fetched, non-CAPTCHA-blocked documents with their refs.
    pub async fn get_fetched_docs(&self) -> Result<Vec<DocumentMeta>> {
        let conn = self.conn.clone();

        tokio::task::spawn_blocking(move || -> Result<Vec<DocumentMeta>> {
            let conn = conn.lock().unwrap();

            // Load documents
            let mut stmt = conn.prepare(
                "SELECT id, title, url, status, body_text
                 FROM documents
                 WHERE fetched_at IS NOT NULL AND captcha_blocked = 0",
            )?;

            let rows: Vec<(String, String, String, String, String)> = stmt
                .query_map([], |r| {
                    Ok((r.get(0)?, r.get(1)?, r.get(2)?, r.get(3)?, r.get(4)?))
                })?
                .collect::<std::result::Result<_, _>>()?;

            // Load refs for each doc
            let mut ref_stmt = conn.prepare(
                "SELECT target_id FROM doc_refs WHERE source_id = ?1",
            )?;

            let mut docs = Vec::with_capacity(rows.len());
            for (id, title, url, status_str, text) in rows {
                let status = str_to_status(&status_str);
                let refs: Vec<DocumentId> = ref_stmt
                    .query_map(params![&id], |r| r.get::<_, String>(0))?
                    .filter_map(|r| r.ok())
                    .map(|s| DocumentId::new(s))
                    .collect();

                docs.push(DocumentMeta {
                    id: DocumentId::new(id),
                    title,
                    url,
                    status,
                    references: refs,
                    text,
                });
            }
            Ok(docs)
        })
        .await
        .context("spawn_blocking panicked")?
    }

    /// Return aggregate counts for the status endpoint.
    pub async fn get_status(&self) -> Result<WorkerStatus> {
        let conn = self.conn.clone();

        tokio::task::spawn_blocking(move || -> Result<WorkerStatus> {
            let conn = conn.lock().unwrap();
            let total_docs: i64 =
                conn.query_row("SELECT COUNT(*) FROM documents", [], |r| r.get(0))?;
            let fetched_docs: i64 = conn.query_row(
                "SELECT COUNT(*) FROM documents WHERE fetched_at IS NOT NULL",
                [],
                |r| r.get(0),
            )?;
            let queued: i64 =
                conn.query_row("SELECT COUNT(*) FROM fetch_queue", [], |r| r.get(0))?;
            let captcha_blocked: i64 = conn.query_row(
                "SELECT COUNT(*) FROM documents WHERE captcha_blocked = 1",
                [],
                |r| r.get(0),
            )?;
            Ok(WorkerStatus {
                total_docs,
                fetched_docs,
                queued,
                captcha_blocked,
            })
        })
        .await
        .context("spawn_blocking panicked")?
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

fn status_to_str(s: &DocumentStatus) -> &'static str {
    match s {
        DocumentStatus::Active => "active",
        DocumentStatus::Outdated => "outdated",
        DocumentStatus::Unknown => "unknown",
    }
}

fn str_to_status(s: &str) -> DocumentStatus {
    match s {
        "active" => DocumentStatus::Active,
        "outdated" => DocumentStatus::Outdated,
        _ => DocumentStatus::Unknown,
    }
}
