use std::{net::SocketAddr, sync::Arc};

use axum::{
    extract::{Query, State},
    routing::get,
    Json, Router,
};
use serde::Deserialize;
use tokio::{net::TcpListener, sync::RwLock};
use tower_http::cors::{Any, CorsLayer};
use tracing_subscriber::EnvFilter;

use crate::data::{
    fetcher::AdiletFetcher,
    graph::GraphBuilder,
    model::{DocumentId, GraphData},
};

const DEFAULT_SEED_IDS: &[&str] = &[
    "K950001000_", // Гражданский кодекс РК
    "K990000409_", // Гражданский кодекс РК
    "K010000155_", // Уголовный кодекс РК
    "K140000226_", // Кодекс об административных правонарушениях
    "Z050000045_", // Закон о нормативных правовых актах
];

struct AppState {
    builder: GraphBuilder<AdiletFetcher>,
    cache: RwLock<Option<GraphData>>,
    default_seeds: Vec<DocumentId>,
}

impl AppState {
    fn new() -> Result<Self, reqwest::Error> {
        Ok(Self {
            builder: GraphBuilder::new(AdiletFetcher::new()?)
                .with_ml_service("http://localhost:8000"),
            cache: RwLock::new(None),
            default_seeds: DEFAULT_SEED_IDS
                .iter()
                .map(|&s| DocumentId::new(s))
                .collect(),
        })
    }

    async fn get_or_build(&self, seeds: Vec<DocumentId>, depth: usize) -> GraphData {
        let is_default = seeds == self.default_seeds;

        if is_default {
            if let Some(cached) = self.cache.read().await.as_ref() {
                return cached.clone();
            }
        }

        tracing::info!("Building graph: {} seeds, depth={}", seeds.len(), depth);
        let graph = self.builder.build(seeds, depth).await;

        if is_default {
            *self.cache.write().await = Some(graph.clone());
        }

        graph
    }

    async fn invalidate_cache(&self) {
        *self.cache.write().await = None;
    }
}

type SharedState = Arc<AppState>;

// ── Handlers ──────────────────────────────────────────────────────────────────

#[derive(Deserialize)]
struct GraphQuery {
    seeds: Option<String>,
    depth: Option<usize>,
}

async fn get_graph(
    Query(params): Query<GraphQuery>,
    State(state): State<SharedState>,
) -> Json<GraphData> {
    let depth = params.depth.unwrap_or(1);
    let seeds: Vec<DocumentId> = params
        .seeds
        .map(|s| s.split(',').map(|x| DocumentId::new(x.trim())).collect())
        .unwrap_or_else(|| state.default_seeds.clone());

    Json(state.get_or_build(seeds, depth).await)
}

async fn refresh_graph(State(state): State<SharedState>) -> Json<serde_json::Value> {
    state.invalidate_cache().await;

    tracing::info!("Cache cleared, rebuilding default graph...");
    let graph = state.get_or_build(state.default_seeds.clone(), 1).await;

    Json(serde_json::json!({
        "status": "ok",
        "nodes": graph.nodes.len(),
        "links": graph.links.len(),
    }))
}

// ── Server ────────────────────────────────────────────────────────────────────

pub struct Server {
    state: SharedState,
    addr: SocketAddr,
}

impl Server {
    pub fn new(addr: SocketAddr) -> Result<Self, reqwest::Error> {
        Self::init_tracing();
        Ok(Self {
            state: Arc::new(AppState::new()?),
            addr,
        })
    }

    pub async fn run(self) {
        let app = Self::build_router(self.state);
        let listener = TcpListener::bind(self.addr).await.unwrap();
        tracing::info!("Server running on http://{}", self.addr);
        axum::serve(listener, app).await.unwrap();
    }

    fn build_router(state: SharedState) -> Router {
        let cors = CorsLayer::new()
            .allow_origin(Any)
            .allow_methods(Any)
            .allow_headers(Any);

        Router::new()
            .route("/api/graph", get(get_graph))
            .route("/api/graph/refresh", get(refresh_graph))
            .with_state(state)
            .layer(cors)
    }

    fn init_tracing() {
        tracing_subscriber::fmt()
            .with_env_filter(
                EnvFilter::from_default_env().add_directive("legis_entropy=info".parse().unwrap()),
            )
            .init();
    }
}
