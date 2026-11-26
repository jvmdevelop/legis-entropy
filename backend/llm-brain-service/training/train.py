import argparse
import json
import random
import sys
from pathlib import Path

import numpy as np
import torch
from sklearn.metrics import classification_report, f1_score
from sklearn.model_selection import train_test_split
from transformers import (AutoModelForSequenceClassification, AutoTokenizer, DataCollatorWithPadding, Trainer,
                          TrainingArguments)

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT))
from app.analysis.risk_text import model_input  # noqa: E402  

DATA, EVAL, MODELS = ROOT / "training" / "data", ROOT / "training" / "eval", ROOT / "models"

TASKS = {
    "intent": {"labels": ["DOCUMENT_AUDIT", "DOCUMENT_QA", "SUMMARY", "LAW_SEARCH", "GENERAL_LEGAL_ADVICE",
                          "DOCUMENT_DRAFT", "GRAPH_ADD_LAWS", "GRAPH_LINK_DOCUMENT", "GRAPH_DEEP_ANALYSIS"],
               "multi": False, "max_len": 128},
    "profession": {"labels": ["FAMILY_LAWYER", "REAL_ESTATE_LAWYER", "CRIMINAL_LAWYER", "CORPORATE_LAWYER",
                              "LABOR_LAWYER", "GENERAL_LAWYER"], "multi": True, "max_len": 128},
    "risk": {"labels": ["VIOLATION", "WARNING", "COMPLIANT"], "multi": False, "max_len": 192},
}
MULTI_THRESHOLD = 0.5


def load(path: Path, task: str) -> list[dict]:
    rows = [json.loads(line) for line in path.read_text().splitlines() if line.strip()] if path.exists() else []
    if any("verified" in r for r in rows):
        rows = [r for r in rows if r.get("verified")]
    for r in rows:
        r["input"] = model_input(r["contract_type"], r["text"]) if task == "risk" else r["text"]
    return rows


def targets(rows: list[dict], cfg: dict) -> list:
    idx = {l: i for i, l in enumerate(cfg["labels"])}
    if cfg["multi"]:
        out = []
        for r in rows:
            v = [0.0] * len(idx)
            for l in r["labels"]:
                v[idx[l]] = 1.0
            out.append(v)
        return out
    return [idx[r["label"]] for r in rows]


class Ds(torch.utils.data.Dataset):
    def __init__(self, enc, y):
        self.enc, self.y = enc, y

    def __len__(self):
        return len(self.y)

    def __getitem__(self, i):
        item = {k: v[i] for k, v in self.enc.items()}
        item["labels"] = torch.tensor(self.y[i], dtype=torch.float if isinstance(self.y[i], list) else torch.long)
        return item


class Collator:
    def __init__(self, tok):
        self.pad = DataCollatorWithPadding(tok)

    def __call__(self, feats: list[dict]) -> dict:
        labels = torch.stack([f.pop("labels") for f in feats])
        batch = self.pad(feats)
        batch["labels"] = labels
        return batch


def predict(model, tok, texts: list[str], cfg: dict, device) -> np.ndarray:
    model.eval()
    out = []
    with torch.no_grad():
        for i in range(0, len(texts), 64):
            enc = tok(texts[i:i + 64], truncation=True, max_length=cfg["max_len"], padding=True,
                      return_tensors="pt").to(device)
            logits = model(**enc).logits
            out.append((torch.sigmoid(logits) if cfg["multi"] else torch.softmax(logits, -1)).cpu().numpy())
    return np.concatenate(out) if out else np.zeros((0, len(cfg["labels"])))


def report(probs: np.ndarray, y, cfg: dict) -> dict:
    labels = cfg["labels"]
    if cfg["multi"]:
        pred = (probs >= MULTI_THRESHOLD).astype(int)
        y = np.array(y).astype(int)
        return {"macro_f1": float(f1_score(y, pred, average="macro", zero_division=0)),
                "micro_f1": float(f1_score(y, pred, average="micro", zero_division=0)),
                "per_label": classification_report(y, pred, target_names=labels, output_dict=True, zero_division=0)}
    pred = probs.argmax(-1)
    return {"macro_f1": float(f1_score(y, pred, average="macro", zero_division=0)),
            "accuracy": float((pred == np.array(y)).mean()),
            "per_label": classification_report(y, pred, labels=list(range(len(labels))), target_names=labels,
                                               output_dict=True, zero_division=0)}


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("task", choices=list(TASKS))
    ap.add_argument("--base", default="cointegrated/rubert-tiny2")
    ap.add_argument("--epochs", type=int, default=8)
    ap.add_argument("--lr", type=float, default=6e-5)
    ap.add_argument("--batch", type=int, default=32)
    ap.add_argument("--seed", type=int, default=42)
    a = ap.parse_args()
    cfg = TASKS[a.task]
    random.seed(a.seed), np.random.seed(a.seed), torch.manual_seed(a.seed)

    rows = load(DATA / f"{a.task}.jsonl", a.task)
    if len(rows) < 50:
        sys.exit(f"not enough data for {a.task}: {len(rows)} rows")
    y = targets(rows, cfg)
    strat = None if cfg["multi"] else y
    tr_rows, va_rows, y_tr, y_va = train_test_split(rows, y, test_size=0.15, random_state=a.seed, stratify=strat)
    eval_rows = load(EVAL / f"{a.task}.jsonl", a.task)
    y_ev = targets(eval_rows, cfg)
    print(f"{a.task}: train={len(tr_rows)} val={len(va_rows)} eval(hand)={len(eval_rows)}")

    tok = AutoTokenizer.from_pretrained(a.base)
    model = AutoModelForSequenceClassification.from_pretrained(
        a.base, num_labels=len(cfg["labels"]),
        id2label=dict(enumerate(cfg["labels"])), label2id={l: i for i, l in enumerate(cfg["labels"])},
        problem_type="multi_label_classification" if cfg["multi"] else "single_label_classification")
    enc = lambda rs: tok([r["input"] for r in rs], truncation=True, max_length=cfg["max_len"])  # noqa: E731
    out_dir = MODELS / a.task

    def metrics(p):
        logits, labels = p
        probs = (1 / (1 + np.exp(-logits))) if cfg["multi"] else torch.softmax(torch.tensor(logits), -1).numpy()
        return {"macro_f1": report(probs, labels if not cfg["multi"] else labels.tolist(), cfg)["macro_f1"]}

    args = TrainingArguments(
        output_dir=str(ROOT / "training" / ".runs" / a.task), num_train_epochs=a.epochs, learning_rate=a.lr,
        per_device_train_batch_size=a.batch, per_device_eval_batch_size=64, weight_decay=0.01, warmup_ratio=0.1,
        eval_strategy="epoch", save_strategy="epoch", save_total_limit=1, load_best_model_at_end=True,
        metric_for_best_model="macro_f1", greater_is_better=True, logging_steps=50, report_to=[],
        fp16=torch.cuda.is_available(), seed=a.seed)
    trainer = Trainer(model=model, args=args, train_dataset=Ds(enc(tr_rows), y_tr), eval_dataset=Ds(enc(va_rows), y_va),
                      data_collator=Collator(tok), compute_metrics=metrics)
    trainer.train()

    device = trainer.model.device
    res = {"base": a.base, "train": len(tr_rows),
           "val": report(predict(trainer.model, tok, [r["input"] for r in va_rows], cfg, device), y_va, cfg),
           "eval_hand": report(predict(trainer.model, tok, [r["input"] for r in eval_rows], cfg, device), y_ev, cfg)
           if eval_rows else None}
    trainer.model.save_pretrained(out_dir)
    tok.save_pretrained(out_dir)
    (out_dir / "metrics.json").write_text(json.dumps(res, ensure_ascii=False, indent=2))
    for f in out_dir.iterdir():
        f.chmod(0o644)
    print(json.dumps({"val_macro_f1": res["val"]["macro_f1"],
                      "eval_hand_macro_f1": res["eval_hand"]["macro_f1"] if res["eval_hand"] else None}, indent=2))
    if eval_rows and not cfg["multi"]:
        probs = predict(trainer.model, tok, [r["input"] for r in eval_rows], cfg, device)
        for r, p in zip(eval_rows, probs):
            pl = cfg["labels"][int(p.argmax())]
            if pl != r["label"]:
                print(f"  MISS {r['label']:>22} -> {pl:<22} {p.max():.2f}  {r['text'][:70]}")


if __name__ == "__main__":
    main()
