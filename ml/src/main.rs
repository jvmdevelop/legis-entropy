use std::{net::SocketAddr, sync::Arc};

mod data;
mod db;
mod server;
mod worker;

const SEEDS: &[&str] = &[
    "K950001000_", // Гражданский кодекс РК (часть 1)
    "K990000409_", // Гражданский кодекс РК (часть 2)
    "K010000155_", // Уголовный кодекс РК
    "K140000226_", // Кодекс об административных правонарушениях
    "Z050000045_", // Закон о нормативных правовых актах
];

#[tokio::main]
async fn main() -> anyhow::Result<()> {
    // ── Logging ───────────────────────────────────────────────────────────────
    tracing_subscriber::fmt()
        .with_env_filter(
            tracing_subscriber::EnvFilter::from_default_env()
                .add_directive("legis_entropy=info".parse().unwrap()),
        )
        .init();

    // ── Database ──────────────────────────────────────────────────────────────
    let data_dir = std::env::var("DATA_DIR").unwrap_or_else(|_| "/data".to_owned());
    let db_path = format!("{data_dir}/legis_entropy.db");
    tracing::info!("Opening database at {db_path}");

    let db = Arc::new(db::Database::open(&db_path).await?);
    db.seed_if_empty(SEEDS).await?;
    db.reset_seeds(SEEDS).await?;

    // ── Background worker ─────────────────────────────────────────────────────
    let worker_db = db.clone();
    tokio::spawn(async move {
        worker::run(worker_db).await;
    });

    // ── HTTP server ───────────────────────────────────────────────────────────
    let addr = SocketAddr::from(([0, 0, 0, 0], 3001));
    tracing::info!("Listening on {addr}");
    server::run(db, addr).await
}
