# legis-entropy Telegram bot

Python service (aiogram 3, long polling — no public URL needed). Send a contract (PDF/DOCX/TXT) →
the bot uploads it to `document-service`, waits for indexing and asks `llm-brain-service` for the same
analysis the web chat gives: wrong article references, violations of the Labour Code, repealed acts.
Plain text messages are questions about the last document (or general questions without one).

## Run

1. Create a bot with [@BotFather](https://t.me/BotFather) and put the token into `.env`:
   `TELEGRAM_BOT_TOKEN=...`
2. `docker compose -f docker-compose.dev.yml --profile bot up -d --build telegram-bot`
3. Logs: `docker logs -f telegram-bot`

The service is in the `bot` profile, so plain `docker compose up` does not start it.

Each Telegram user gets their own backend user id `tg-<telegram id>`; the last document per user is
kept in memory (lost on restart — just send the file again). Commands: `/start`, `/help`, `/new`.

## Tests

```
docker run --rm -v "$PWD":/app -w /app python:3.12-slim \
  sh -c "pip install -q -r requirements-dev.txt && python -m pytest -q"
```
