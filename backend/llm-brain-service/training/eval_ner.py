import argparse
import asyncio
import json
import sys
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT))
from app.ml.ner import CompositeNer, GlinerNer, RegexNer  # noqa: E402

LABELS = ["law", "article", "date", "sum", "org", "term"]


def overlap(a, b) -> bool:
    return a["start"] < b["end"] and b["start"] < a["end"]


async def score(model, rows) -> dict:
    tp_g, fn, tp_p, fp = Counter(), Counter(), Counter(), Counter()
    for r in rows:
        pred = [{"label": e.label, "start": e.start, "end": e.end} for e in await model.extract(r["text"])]
        pred = [p for p in pred if p["label"] in LABELS]
        for g in r["entities"]:
            (tp_g if any(p["label"] == g["label"] and overlap(p, g) for p in pred) else fn)[g["label"]] += 1
        for p in pred:
            (tp_p if any(g["label"] == p["label"] and overlap(p, g) for g in r["entities"]) else fp)[p["label"]] += 1
    out = {}
    for lab in LABELS + ["ALL"]:
        pick = (lambda c: sum(c.values())) if lab == "ALL" else (lambda c: c[lab])
        rec = pick(tp_g) / max(1, pick(tp_g) + pick(fn))
        prec = pick(tp_p) / max(1, pick(tp_p) + pick(fp))
        out[lab] = {"p": round(prec, 2), "r": round(rec, 2), "f1": round(2 * prec * rec / max(1e-9, prec + rec), 2)}
    return out


async def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--model", default="urchade/gliner_multi-v2.1")
    ap.add_argument("--device", default="cuda")
    ap.add_argument("--thresholds", type=float, nargs="+", default=[0.3, 0.5])
    a = ap.parse_args()
    rows = [json.loads(l) for l in (ROOT / "training/eval/ner.jsonl").read_text().splitlines() if l.strip()]
    results = {"regex": await score(RegexNer(), rows)}
    for t in a.thresholds:
        g = GlinerNer(a.model, a.device, threshold=t)
        results[f"gliner@{t}"] = await score(g, rows)
        results[f"regex+gliner@{t}"] = await score(CompositeNer(RegexNer(), g), rows)
    print(f"{'model':<20}" + "".join(f"{l:>15}" for l in LABELS + ["ALL"]))
    for name, res in results.items():
        print(f"{name:<20}" + "".join(f"{res[l]['p']:.2f}/{res[l]['r']:.2f}".rjust(15) for l in LABELS + ["ALL"]))
    print("(precision/recall)")


if __name__ == "__main__":
    asyncio.run(main())
