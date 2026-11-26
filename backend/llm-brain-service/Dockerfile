FROM python:3.12-slim
WORKDIR /app
ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1 PIP_NO_CACHE_DIR=1 \
    HF_HOME=/models
ARG ML_EXTRAS=""
RUN apt-get update && apt-get install -y --no-install-recommends wget && rm -rf /var/lib/apt/lists/*
COPY pyproject.toml .
RUN mkdir app && touch app/__init__.py \
    && if [ -n "$ML_EXTRAS" ]; then \
         pip install --index-url https://download.pytorch.org/whl/cpu torch==2.5.1 \
         && pip install ".[$ML_EXTRAS]"; \
       else pip install .; fi
COPY app/ app/
RUN useradd --create-home brain && mkdir -p /models && chown brain /models
USER brain
EXPOSE 8085
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8085"]
