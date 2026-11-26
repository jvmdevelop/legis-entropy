from dataclasses import dataclass
from functools import lru_cache

from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict


@dataclass(frozen=True)
class RankingSettings:
    candidates: int
    top_k: int


@dataclass(frozen=True)
class ThresholdSettings:
    intent_confidence: float
    profession: float
    nli_entailment: float
    clause_similarity: float
    law_link: float


@dataclass(frozen=True)
class DeepAnalysisSettings:
    max_laws_total: int = 25
    max_articles_total: int = 75
    max_conflicts: int = 10
    primary_laws: int = 5
    articles_per_law: int = 3
    related_hop2: int = 3
    related_hop3: int = 2


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore", populate_by_name=True)

    app_name: str = "llm-brain-service"
    port: int = Field(8085, alias="SERVER_PORT")

    eureka_url: str = Field("http://localhost:8761/eureka/", alias="EUREKA_CLIENT_SERVICEURL_DEFAULTZONE")
    eureka_enabled: bool = Field(True, alias="EUREKA_ENABLED")
    instance_host: str | None = Field(None, alias="INSTANCE_HOST")

    llm_api_key: str = Field("", alias="LLM_API_KEY")
    llm_base_url: str = Field("https://generativelanguage.googleapis.com/v1beta/openai/", alias="LLM_BASE_URL")
    llm_model: str = Field("gemini-3.8-flash", alias="LLM_MODEL")
    llm_temperature: float = Field(0.2, alias="LLM_TEMPERATURE")
    llm_timeout_s: float = Field(300.0, alias="LLM_TIMEOUT_SECONDS")
    llm_max_retries: int = Field(1, alias="LLM_MAX_RETRIES")

    nvidia_enabled: bool = Field(False, alias="NVIDIA_LLM_ENABLED")
    nvidia_api_key: str = Field("", alias="NVIDIA_API_KEY")
    nvidia_base_url: str = Field("https://integrate.api.nvidia.com/v1/", alias="NVIDIA_LLM_BASE_URL")
    nvidia_model: str = Field("meta/llama-3.1-8b-instruct", alias="NVIDIA_LLM_MODEL")

    local_enabled: bool = Field(False, alias="LOCAL_LLM_ENABLED")
    local_base_url: str = Field("http://localhost:11434/v1", alias="LOCAL_LLM_BASE_URL")
    local_model: str = Field("llama3.2:3b", alias="LOCAL_LLM_MODEL")

    ml_enabled: bool = Field(False, alias="ML_ENABLED")
    ml_device: str = Field("cpu", alias="ML_DEVICE")
    embedding_model: str = Field("BAAI/bge-m3", alias="EMBEDDING_MODEL")
    reranker_model: str = Field("BAAI/bge-reranker-v2-m3", alias="RERANKER_MODEL")
    nli_model: str = Field("MoritzLaurer/mDeBERTa-v3-base-xnli-multilingual-nli-2mil7", alias="NLI_MODEL")
    ner_model: str = Field("urchade/gliner_multi-v2.1", alias="NER_MODEL")
    intent_model: str = Field("", alias="INTENT_MODEL")
    profession_model: str = Field("", alias="PROFESSION_MODEL")
    risk_model: str = Field("", alias="RISK_MODEL")
    risk_confidence: float = Field(0.6, alias="RISK_CONFIDENCE")

    intent_confidence: float = 0.7
    profession_threshold: float = 0.5
    nli_entailment: float = 0.8
    clause_similarity: float = 0.6
    law_link_threshold: float = Field(0.75, alias="LAW_LINK_THRESHOLD")
    rerank_candidates: int = 50
    rerank_top_k: int = 8

    graph_service_url: str = Field("http://localhost:8086", alias="GRAPH_SERVICE_URL")
    http_timeout_s: float = Field(120.0, alias="HTTP_TIMEOUT_SECONDS")

    deep_max_laws_total: int = Field(25, alias="DEEP_ANALYSIS_MAX_LAWS_TOTAL")
    deep_max_articles_total: int = Field(75, alias="DEEP_ANALYSIS_MAX_ARTICLES_TOTAL")
    deep_max_conflicts: int = Field(10, alias="DEEP_ANALYSIS_MAX_CONFLICTS")
    deep_primary_laws: int = Field(5, alias="DEEP_ANALYSIS_PRIMARY_LAWS")
    deep_articles_per_law: int = Field(3, alias="DEEP_ANALYSIS_ARTICLES_PER_LAW")
    deep_related_hop2: int = Field(3, alias="DEEP_ANALYSIS_RELATED_PER_LAW_HOP2")
    deep_related_hop3: int = Field(2, alias="DEEP_ANALYSIS_RELATED_PER_LAW_HOP3")


    @property
    def ranking(self) -> RankingSettings:
        return RankingSettings(self.rerank_candidates, self.rerank_top_k)

    @property
    def thresholds(self) -> ThresholdSettings:
        return ThresholdSettings(self.intent_confidence, self.profession_threshold, self.nli_entailment,
                                 self.clause_similarity, self.law_link_threshold)

    @property
    def deep(self) -> DeepAnalysisSettings:
        return DeepAnalysisSettings(self.deep_max_laws_total, self.deep_max_articles_total, self.deep_max_conflicts,
                                    self.deep_primary_laws, self.deep_articles_per_law, self.deep_related_hop2,
                                    self.deep_related_hop3)


@lru_cache
def get_settings() -> Settings:
    return Settings()
