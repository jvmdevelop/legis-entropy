"""LLMService — thread-safe wrapper around a small causal language model."""

import threading
from typing import Optional

from config import LLM_MODEL_NAME

_SYSTEM_PROMPT = (
    "Ты — аналитик законодательства Республики Казахстан. "
    "Даёшь краткий профессиональный вывод на русском языке. "
    "Не повторяй исходные данные. Максимум 3–4 предложения."
)

_NOT_READY_MSG = "Модель анализа загружается, повторите через минуту."


class LLMService:
    """Loads a causal LM in a background thread and exposes thread-safe text generation."""

    def __init__(self) -> None:
        self._model = None
        self._tokenizer = None
        self._ready = False
        self._lock = threading.Lock()

    @property
    def ready(self) -> bool:
        with self._lock:
            return self._ready

    def load_in_background(self) -> None:
        threading.Thread(target=self._load, daemon=True).start()

    def _load(self) -> None:
        try:
            import torch
            from transformers import AutoModelForCausalLM, AutoTokenizer

            print(f"[LLM] Loading {LLM_MODEL_NAME}…")
            tok = AutoTokenizer.from_pretrained(LLM_MODEL_NAME)
            mdl = AutoModelForCausalLM.from_pretrained(LLM_MODEL_NAME, torch_dtype=torch.float32)
            mdl.eval()
            with self._lock:
                self._tokenizer = tok
                self._model = mdl
                self._ready = True
            print("[LLM] Model ready.")
        except Exception as e:
            print(f"[LLM] Failed to load: {e}")

    def generate(self, prompt: str, max_new_tokens: int = 180) -> tuple[str, bool]:
        """Generate a review text. Returns (text, model_ready)."""
        with self._lock:
            if not self._ready:
                return _NOT_READY_MSG, False
            tok = self._tokenizer
            mdl = self._model

        import torch

        messages = [
            {"role": "system", "content": _SYSTEM_PROMPT},
            {"role": "user", "content": prompt},
        ]
        text = tok.apply_chat_template(messages, tokenize=False, add_generation_prompt=True)
        inputs = tok([text], return_tensors="pt")
        with torch.no_grad():
            out = mdl.generate(
                **inputs,
                max_new_tokens=max_new_tokens,
                temperature=0.7,
                do_sample=True,
                pad_token_id=tok.eos_token_id,
            )
        generated = out[0][inputs["input_ids"].shape[1]:]
        return tok.decode(generated, skip_special_tokens=True).strip(), True
