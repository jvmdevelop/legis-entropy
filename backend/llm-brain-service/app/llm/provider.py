import logging
from dataclasses import dataclass
from typing import Protocol

from openai import AsyncOpenAI
from prometheus_client import Counter

from app.config import Settings
from app.llm.retry import RateLimitRetry

log = logging.getLogger(__name__)

LLM_CALLS = Counter("brain_llm_calls_total", "LLM API calls", ["provider", "mode"])
LLM_TOKENS = Counter("brain_llm_tokens_total", "LLM tokens reported by the provider", ["provider", "type"])


@dataclass(frozen=True)
class Provider:
    name: str
    base_url: str
    api_key: str
    model: str
    disable_thinking: bool = False


def select_provider(s: Settings) -> Provider:
    if s.local_enabled:
        return Provider("local", s.local_base_url, "ollama", s.local_model)
    if s.nvidia_enabled or s.nvidia_api_key:
        return Provider("nvidia", s.nvidia_base_url, s.nvidia_api_key, s.nvidia_model, disable_thinking=True)
    return Provider("default", s.llm_base_url, s.llm_api_key or "none", s.llm_model)


class ChatBackend(Protocol):
    provider: Provider

    async def create(self, messages: list[dict], *, structured: bool = False, **kw): ...


class OpenAiBackend:
    def __init__(self, provider: Provider, temperature: float, timeout_s: float, max_retries: int,
                 retry: RateLimitRetry | None = None):
        self.provider = provider
        self.temperature = temperature
        self.retry = retry or RateLimitRetry()
        self._client = AsyncOpenAI(base_url=provider.base_url, api_key=provider.api_key,
                                   timeout=timeout_s, max_retries=max_retries)
        log.info("LLM provider: %s model=%s", provider.name, provider.model)

    @classmethod
    def from_settings(cls, s: Settings) -> "OpenAiBackend":
        return cls(select_provider(s), s.llm_temperature, s.llm_timeout_s, s.llm_max_retries)

    async def create(self, messages: list[dict], *, structured: bool = False, **kw):
        if structured and self.provider.disable_thinking:
            kw["extra_body"] = {"chat_template_kwargs": {"enable_thinking": False}}
        mode = "stream" if kw.get("stream") else ("structured" if structured else "chat")
        LLM_CALLS.labels(self.provider.name, mode).inc()
        if kw.get("stream"):
            kw.setdefault("stream_options", {"include_usage": True})
        resp = await self.retry.run(lambda: self._client.chat.completions.create(
            model=self.provider.model, temperature=self.temperature, messages=messages, **kw))
        if not kw.get("stream"):
            record_usage(self.provider.name, getattr(resp, "usage", None))
        return resp


def record_usage(provider: str, usage) -> None:
    if usage is None:
        return
    LLM_TOKENS.labels(provider, "prompt").inc(usage.prompt_tokens or 0)
    LLM_TOKENS.labels(provider, "completion").inc(usage.completion_tokens or 0)
