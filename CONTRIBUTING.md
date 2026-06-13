# Contributing

Thanks for your interest in contributing to Legis Entropy!

## Getting Started

1. Fork the repository
2. Clone your fork
3. Create a feature branch from `master`
4. Make your changes
5. Open a pull request

## Development Setup

### Backend (Java 21 + Spring Boot 4)

```bash
cd backend
./gradlew build
```

### Frontend (React + Vite + TypeScript)

```bash
cd frontend
npm ci
npm run dev
```

### Infrastructure

```bash
docker compose up -d postgres-user postgres-chat postgres-documents postgres-workspace neo4j elasticsearch redis minio
```

## Pull Request Guidelines

- Target the `master` branch
- Keep PRs focused — one feature or fix per PR
- Backend changes must pass `./gradlew build test`
- Frontend changes must pass `npm run lint` and `npm run build`
- CI must be green before merge

## Code Style

- Java: follow existing project conventions (no Javadoc required, keep code self-documenting)
- TypeScript/React: ESLint rules in the project

## Reporting Issues

Use GitHub Issues. Include steps to reproduce, expected vs actual behavior, and relevant logs.
