
# Requirements: 
### 1. when u starting develop u need create certificates in certs with openSSL command to start local developing. 
```shell
sudo openssl req -x509 -nodes -days 365 -newkey rsa:2048 -keyout ip.key -out ip.crt
```

### 2. almost u need setup .env file to local development. U have to choose docker-compose.prod.yml and docker-compose.dev.yml
```shell
cp .env.example .env
# Put your paid Gemini API key into LLM_API_KEY in .env.
docker compose -f docker-compose.dev.yml up -d
```

By default the laptop/dev setup uses Gemini through the OpenAI-compatible endpoint:
`https://generativelanguage.googleapis.com/v1beta/openai/`.
If you want local Ollama instead, set `COMPOSE_PROFILES=local` and
`LOCAL_LLM_ENABLED=true` in `.env`.

### 3. almost u need setup application properties to local developpment. 
