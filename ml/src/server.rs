//! Axum HTTP server.
//!
//! All routes are non-blocking: they read from the persistent DB and return
//! immediately. The background worker independently fills the DB over time.

use std::{net::SocketAddr, sync::Arc};

use axum::{extract::State, routing::get, Json, Router};
use serde::Serialize;
use tower_http::cors::{Any, CorsLayer};

use crate::{
    data::{analyzer::DocumentAnalyzer, graph::assemble_graph, ml_client::MlClient},
    db::Database,
};

const SEEDS: &[&str] = &[
    "K950001000_",
    "K990000409_",
    "K010000155_",
    "K140000226_",
    "Z050000045_",
];

// ── State ─────────────────────────────────────────────────────────────────────

#[derive(Clone)]
struct AppState {
    db: Arc<Database>,
    ml: Arc<MlClient>,
    analyzer: Arc<DocumentAnalyzer>,
}

// ── Entry point ───────────────────────────────────────────────────────────────

pub async fn run(db: Arc<Database>, addr: SocketAddr) -> anyhow::Result<()> {
    let ml_url = std::env::var("ML_SERVICE_URL")
        .unwrap_or_else(|_| "http://localhost:8000".to_owned());

    let state = AppState {
        db,
        ml: Arc::new(MlClient::new(ml_url)),
        analyzer: Arc::new(DocumentAnalyzer::new()),
    };

    let cors = CorsLayer::new()
        .allow_origin(Any)
        .allow_methods(Any)
        .allow_headers(Any);

    let app = Router::new()
        .route("/api/graph", get(graph_handler))
        .route("/api/graph/status", get(status_handler))
        .route("/api/graph/refresh", get(refresh_handler))
        .layer(cors)
        .with_state(state);

    let listener = tokio::net::TcpListener::bind(addr).await?;
    axum::serve(listener, app).await?;
    Ok(())
}

// ── Handlers ──────────────────────────────────────────────────────────────────

/// Build and return `GraphData` from the current DB contents.
///
/// Always fast — reads from SQLite, no network I/O.
async fn graph_handler(State(state): State<AppState>) -> Json<serde_json::Value> {
    let docs = match state.db.get_fetched_docs().await {
        Ok(d) => d,
        Err(e) => {
            tracing::error!("get_fetched_docs: {e}");
            return Json(serde_json::json!({ "nodes": [], "links": [], "issues": [] }));
        }
    };

    // Structural analysis (fast, in-process)
    let structural_issues = state.analyzer.analyze(&docs);

    // Semantic analysis (optional ML service, may be empty if unavailable)
    let ml_issues = state.ml.analyze(&docs).await;

    let all_issues: Vec<_> = structural_issues.into_iter().chain(ml_issues).collect();
    let graph = assemble_graph(docs, all_issues);

    Json(serde_json::to_value(graph).unwrap_or_default())
}

/// Return worker / DB statistics.
#[derive(Serialize)]
struct StatusResponse {
    total_docs: i64,
    fetched_docs: i64,
    queued: i64,
    captcha_blocked: i64,
}

async fn status_handler(State(state): State<AppState>) -> Json<StatusResponse> {
    let s = state
        .db
        .get_status()
        .await
        .unwrap_or(crate::db::WorkerStatus {
            total_docs: 0,
            fetched_docs: 0,
            queued: 0,
            captcha_blocked: 0,
        });

    Json(StatusResponse {
        total_docs: s.total_docs,
        fetched_docs: s.fetched_docs,
        queued: s.queued,
        captcha_blocked: s.captcha_blocked,
    })
}

/// Re-queue all seeds so the worker re-fetches them (and discovers new refs).
async fn refresh_handler(State(state): State<AppState>) -> Json<serde_json::Value> {
    match state.db.enqueue_seeds(SEEDS).await {
        Ok(()) => Json(serde_json::json!({ "ok": true, "queued": SEEDS.len() })),
        Err(e) => Json(serde_json::json!({ "ok": false, "error": e.to_string() })),
    }
}
