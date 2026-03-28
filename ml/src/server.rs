//! Axum HTTP server.
//!
//! All routes are non-blocking: they read from SQLite and return immediately.
//! The background worker fills the DB independently over time.

use std::{net::SocketAddr, sync::Arc};

use axum::{extract::State, http::StatusCode, routing::get, Json, Router};
use tower_http::cors::{Any, CorsLayer};

use crate::{
    data::{analyzer::DocumentAnalyzer, graph::assemble_graph, ml_client::MlClient, model::GraphData},
    db::{Database, WorkerStatus},
};

// ── Application state ─────────────────────────────────────────────────────────

#[derive(Clone)]
struct AppState {
    db: Arc<Database>,
    ml: Arc<MlClient>,
    analyzer: Arc<DocumentAnalyzer>,
    seeds: &'static [&'static str],
}

// ── Entry point ───────────────────────────────────────────────────────────────

pub async fn run(
    db: Arc<Database>,
    addr: SocketAddr,
    ml_url: String,
    seeds: &'static [&'static str],
) -> anyhow::Result<()> {
    let state = AppState {
        db,
        ml: Arc::new(MlClient::new(ml_url)),
        analyzer: Arc::new(DocumentAnalyzer::new()),
        seeds,
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
async fn graph_handler(
    State(state): State<AppState>,
) -> Result<Json<GraphData>, StatusCode> {
    let docs = state.db.get_fetched_docs().await.map_err(|e| {
        tracing::error!("get_fetched_docs: {e}");
        StatusCode::INTERNAL_SERVER_ERROR
    })?;

    let structural_issues = state.analyzer.analyze(&docs);
    let ml_issues = state.ml.analyze(&docs).await;
    let all_issues: Vec<_> = structural_issues.into_iter().chain(ml_issues).collect();

    Ok(Json(assemble_graph(docs, all_issues)))
}

/// Return worker / DB statistics.
async fn status_handler(
    State(state): State<AppState>,
) -> Json<WorkerStatus> {
    let status = state.db.get_status().await.unwrap_or(WorkerStatus {
        total_docs: 0,
        fetched_docs: 0,
        queued: 0,
        captcha_blocked: 0,
    });
    Json(status)
}

/// Re-queue all seeds so the worker re-fetches them (and discovers new refs).
async fn refresh_handler(
    State(state): State<AppState>,
) -> Json<serde_json::Value> {
    match state.db.enqueue_seeds(state.seeds).await {
        Ok(()) => serde_json::json!({ "ok": true, "queued": state.seeds.len() }),
        Err(e) => serde_json::json!({ "ok": false, "error": e.to_string() }),
    }
    .into()
}
