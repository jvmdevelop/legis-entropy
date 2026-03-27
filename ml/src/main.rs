use std::net::SocketAddr;

mod data;
mod server;

#[tokio::main]
async fn main() {
    let addr = SocketAddr::from(([0, 0, 0, 0], 3001));
    server::Server::new(addr)
        .expect("Failed to initialize server")
        .run()
        .await;
}
