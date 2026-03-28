FROM rust:latest AS builder

WORKDIR /app

# build-essential needed for rusqlite bundled (compiles sqlite3 from C source)
RUN apt-get update && apt-get install -y --no-install-recommends \
    pkg-config libssl-dev build-essential \
    && rm -rf /var/lib/apt/lists/*

# Pre-build dependencies for layer caching
COPY Cargo.toml Cargo.lock ./
RUN mkdir src && echo 'fn main(){}' > src/main.rs \
    && cargo build --release \
    && rm -rf src

# Build the real binary
COPY src ./src
RUN touch src/main.rs && cargo build --release

# ── Runtime image ─────────────────────────────────────────────────────────────
FROM debian:bookworm-slim

RUN apt-get update && apt-get install -y --no-install-recommends \
    libssl3 ca-certificates curl \
    && rm -rf /var/lib/apt/lists/*

COPY --from=builder /app/target/release/legis-entropy /usr/local/bin/legis-entropy

# Data directory for SQLite DB (mount a volume here)
RUN mkdir -p /data
VOLUME ["/data"]

EXPOSE 3001

CMD ["legis-entropy"]
