import logging
from dataclasses import dataclass, field

from fastapi import APIRouter

from app.analysis.audit import ReferenceAuditService
from app.analysis.citation_verifier import CitationVerifier
from app.analysis.clauses import ClauseSegmenter
from app.analysis.compare import ClauseAligner, CompareService
from app.analysis.contract_risk import ContractRiskService, LlmRiskClassifier, RiskClassifier
from app.analysis.contract_types import ContractTypeService
from app.analysis.labor import LaborNormChecker
from app.analysis.repealed import RepealedActChecker
from app.analysis.required_clauses import RequiredClauseChecker
from app.analysis.verifier import ArticleReferenceVerifier
from app.edits.service import DocumentEditService
from app.playbooks.service import PlaybookChecker, PlaybookStore
from app.structure.service import StructureService
from app.api import compare, contracts, documents, playbooks
from app.container import Container

log = logging.getLogger(__name__)


@dataclass
class AnalysisFeature:
    contract_risk: ContractRiskService
    verifier: ArticleReferenceVerifier
    audit: ReferenceAuditService
    compare: CompareService
    routers: list[APIRouter] = field(default_factory=list)
    edits: DocumentEditService | None = None


def _risk_classifier(c: Container) -> RiskClassifier:
    llm_risk = LlmRiskClassifier(c.llm)
    s = c.settings
    if s.ml_enabled and s.risk_model:
        try:
            from app.analysis.risk_model import FineTunedRiskClassifier
            model = FineTunedRiskClassifier(s.risk_model, s.ml_device, llm_risk, s.risk_confidence)
            log.info("ML risk: fine-tuned %s", s.risk_model)
            return model
        except Exception as e:
            log.warning("ML risk model unavailable (%s) -> LLM", e)
    return llm_risk


def wire(c: Container, risk: RiskClassifier | None = None) -> AnalysisFeature:
    t = c.settings.thresholds
    contract_risk = ContractRiskService(
        laws=c.laws, segmenter=ClauseSegmenter(c.llm), types=ContractTypeService(c.classifier),
        risk=risk or _risk_classifier(c), required=RequiredClauseChecker(c.embedder, t.clause_similarity))
    verifier = ArticleReferenceVerifier(
        c.laws, embedder=c.embedder, nli=c.nli, ner=c.ner, repealed=RepealedActChecker(c.graph),
        labor=LaborNormChecker(c.llm), similarity=t.clause_similarity, nli_threshold=t.nli_entailment)
    audit = ReferenceAuditService(verifier, c.llm, CitationVerifier(c.laws, c.llm, t.clause_similarity))
    edits = DocumentEditService(audit, c.documents, c.llm)
    structure = StructureService(c.documents, c.llm)
    playbook_router = playbooks.build_router(PlaybookStore(c.settings.redis_url), PlaybookChecker(c.llm), c.documents,
                                             structure)
    cmp = CompareService(c.documents, c.graph, c.llm, ClauseAligner(c.embedder, t.clause_similarity))
    return AnalysisFeature(contract_risk, verifier, audit, cmp, edits=edits, routers=[
        contracts.build_router(contract_risk), documents.build_router(c.documents, audit, edits, structure),
        playbook_router, compare.build_router(cmp)])
