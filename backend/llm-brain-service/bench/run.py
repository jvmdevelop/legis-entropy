import argparse
import asyncio
import json
import logging
import sys
from dataclasses import dataclass, field
from pathlib import Path
from types import SimpleNamespace

from app.analysis import wiring
from app.analysis.required_clauses import RequiredClauseChecker
from app.config import Settings
from app.ml.classifier import PrototypeClassifier
from app.ml.embeddings import TrigramEmbedder
from app.ml.ner import RegexNer

ROOT = Path(__file__).resolve().parent
REPO = ROOT.parents[2]
PROBLEM = {"WRONG_ARTICLE", "NOT_FOUND"}


class _Offline:
    def __getattr__(self, name):
        async def fail(*a, **k):
            raise RuntimeError(f"offline: {name}")
        return fail


@dataclass
class Metric:
    tp: int = 0
    fp: int = 0
    fn: int = 0
    misses: list[str] = field(default_factory=list)

    @property
    def precision(self) -> float | None:
        return self.tp / (self.tp + self.fp) if self.tp + self.fp else None

    @property
    def recall(self) -> float | None:
        return self.tp / (self.tp + self.fn) if self.tp + self.fn else None

    def row(self) -> str:
        f = lambda v: "  -  " if v is None else f"{v:.2f}"  # noqa: E731
        return f"P {f(self.precision)}  R {f(self.recall)}  (tp {self.tp}, fp {self.fp}, fn {self.fn})"


URLS = {"law": "http://localhost:8087", "graph": "http://localhost:8086"}


def _container(live: bool):
    s = Settings()
    if live:
        from app.clients.base import ServiceClient
        from app.clients.services import GraphClient, LawClient
        from app.container import build_container
        c = build_container(s)
        c.laws = LawClient(ServiceClient("law-service", c.http, URLS["law"], resolver=lambda n, f: f))
        c.graph = GraphClient(ServiceClient("graphs-service", c.http, URLS["graph"], resolver=lambda n, f: f))
        return wiring.wire(c), s
    off = _Offline()
    emb = TrigramEmbedder()
    c = SimpleNamespace(settings=s, llm=off, laws=off, graph=off, documents=off, embedder=emb,
                        classifier=PrototypeClassifier(emb), nli=None, ner=RegexNer())
    return wiring.wire(c), s


def gold_imprecise(case: dict) -> set[tuple[str, str]]:
    return {(x["clause"], x["cited"]) for x in case.get("imprecise", [])}


def score(case: dict, findings, missing: list[str]) -> dict[str, Metric]:
    by_ref = {}
    for f in findings:
        by_ref.setdefault((f.clause or "", f.cited_article or ""), []).append(f)
    m = {k: Metric() for k in ("reference_errors", "correct_article", "repealed", "violations",
                              "missing_clauses", "requisites", "imprecise")}
    for g in case["references"]:
        got = [f for f in by_ref.get((g["clause"], g["cited"]), []) if f.verdict in PROBLEM]
        tag = f"п. {g['clause']} ст. {g['cited']}"
        if g["verdict"] == "IMPRECISE":
            continue
        if g["verdict"] == "OK":
            if got:
                m["reference_errors"].fp += 1
                m["reference_errors"].misses.append(f"ложная ошибка: {tag} -> {got[0].verdict}")
            continue
        if not got:
            m["reference_errors"].fn += 1
            m["reference_errors"].misses.append(f"пропущена: {tag}")
            continue
        m["reference_errors"].tp += 1
        if g["correct"]:
            answers = {f.correct_article for f in got if f.correct_article}
            if answers == {g["correct"]}:
                m["correct_article"].tp += 1
            elif answers:
                m["correct_article"].fp += 1
                m["correct_article"].misses.append(f"{tag}: дано {sorted(answers)}, верно {g['correct']}")
            else:
                m["correct_article"].fn += 1
                m["correct_article"].misses.append(f"{tag}: правильная статья не названа (верно {g['correct']})")

    repealed = {f.law_code for f in findings if f.verdict == "REPEALED"}
    gold = {r["act"] for r in case["repealed"]}
    m["repealed"].tp = len(repealed & gold)
    m["repealed"].fn = len(gold - repealed)
    m["repealed"].fp = len(repealed & set(case.get("valid_acts", [])))
    m["repealed"].misses = [f"пропущен {a}" for a in gold - repealed] + \
                           [f"ложно: {a}" for a in repealed & set(case.get("valid_acts", []))]

    viol = {(f.clause, f.correct_article) for f in findings if f.verdict == "VIOLATION"}
    gold_v = {(v["clause"], v["article"]) for v in case["violations"]}
    m["violations"].tp, m["violations"].fn = len(viol & gold_v), len(gold_v - viol)
    m["violations"].fp = len(viol - gold_v)
    m["violations"].misses = [f"пропущено п. {c} ст. {a}" for c, a in gold_v - viol] + \
                             [f"лишнее п. {c} ст. {a}" for c, a in viol - gold_v]

    gold_m = case["missing_clauses"]
    hit = [g for g in gold_m if any(x.startswith(g) for x in missing)]
    m["missing_clauses"].tp, m["missing_clauses"].fn = len(hit), len(gold_m) - len(hit)
    m["missing_clauses"].fp = sum(not any(x.startswith(g) for g in gold_m) for x in missing)
    m["missing_clauses"].misses = [f"не найден пропуск: {g}" for g in gold_m if g not in hit] + \
                                  [f"лишний пропуск: {x}" for x in missing if not any(x.startswith(g) for g in gold_m)]

    imp = {(f.clause, f.cited_article) for f in findings
           if f.verdict == "IMPRECISE" or (f.verdict == "WRONG_ARTICLE"
                                           and (f.clause, f.cited_article) in gold_imprecise(case))}
    gold_i = {(x["clause"], x["cited"]) for x in case.get("imprecise", [])}
    m["imprecise"].tp, m["imprecise"].fn, m["imprecise"].fp = len(imp & gold_i), len(gold_i - imp), len(imp - gold_i)
    m["imprecise"].misses = [f"не найдена неточность п. {c} ст. {a}" for c, a in gold_i - imp] + \
                            [f"ложная неточность п. {c} ст. {a}" for c, a in imp - gold_i]

    req = [f for f in findings if f.verdict == "WRONG_REQUISITES"]
    gold_r = case.get("requisites", [])
    m["requisites"].tp = sum(any(r["number"] in f.explanation for f in req) for r in gold_r)
    m["requisites"].fn = len(gold_r) - m["requisites"].tp
    m["requisites"].fp = len(req) - m["requisites"].tp
    m["requisites"].misses = [f"лишнее: {f.explanation}" for f in req][:m["requisites"].fp]
    return m


async def run_case(path: Path, live: bool) -> dict:
    case = json.loads(path.read_text())
    text = (REPO / case["document"]).read_text()
    feature, s = _container(live)
    audit = await feature.audit.audit(text)
    missing = [r.required_clause for r in
               await RequiredClauseChecker(TrigramEmbedder() if not live else None,
                                           s.thresholds.clause_similarity).missing(text, case["contract_type"])]
    metrics = score(case, audit.findings, missing)
    print(f"\n== {case['name']} ({'live' if live else 'offline'})")
    for k, v in metrics.items():
        print(f"{k:<17} {v.row()}")
        for miss in v.misses:
            print(f"    · {miss}")
    return {k: {"precision": v.precision, "recall": v.recall, "tp": v.tp, "fp": v.fp, "fn": v.fn,
                "misses": v.misses} for k, v in metrics.items()}


async def main() -> None:
    ap = argparse.ArgumentParser()
    mode = ap.add_mutually_exclusive_group(required=True)
    mode.add_argument("--offline", action="store_true")
    mode.add_argument("--live", action="store_true")
    ap.add_argument("--case", default="*")
    ap.add_argument("--json")
    ap.add_argument("--law-url", default=URLS["law"])
    ap.add_argument("--graph-url", default=URLS["graph"])
    a = ap.parse_args()
    URLS.update(law=a.law_url, graph=a.graph_url)
    logging.basicConfig(level=logging.ERROR)
    cases = sorted((ROOT / "cases").glob(f"{a.case}.json"))
    if not cases:
        sys.exit("no cases")
    res = {p.stem: await run_case(p, a.live) for p in cases}
    if a.json:
        Path(a.json).write_text(json.dumps(res, ensure_ascii=False, indent=1))


if __name__ == "__main__":
    asyncio.run(main())
