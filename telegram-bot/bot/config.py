import os
from dataclasses import dataclass


@dataclass(frozen=True)
class Settings:
    token: str
    document_service_url: str
    brain_url: str
    max_file_mb: int = 15
    index_timeout_s: int = 180
    analysis_timeout_s: int = 300

    @staticmethod
    def from_env() -> "Settings":
        token = os.environ.get("TELEGRAM_BOT_TOKEN", "").strip()
        if not token:
            raise SystemExit("TELEGRAM_BOT_TOKEN is not set")
        return Settings(
            token=token,
            document_service_url=os.environ.get("DOCUMENT_SERVICE_URL", "http://document-service:8093").rstrip("/"),
            brain_url=os.environ.get("BRAIN_URL", "http://llm-brain-service:8085").rstrip("/"),
            max_file_mb=int(os.environ.get("BOT_MAX_FILE_MB", "15")),
            index_timeout_s=int(os.environ.get("BOT_INDEX_TIMEOUT_S", "180")),
            analysis_timeout_s=int(os.environ.get("BOT_ANALYSIS_TIMEOUT_S", "300")),
        )
