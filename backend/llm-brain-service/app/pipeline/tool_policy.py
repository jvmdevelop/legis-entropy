from app.pipeline.types import ContextPlan, TaskType
from app.schemas import BrainRequest

SEARCH = ["searchLaws", "searchArticles", "findArticle"]
GRAPH_READ = ["getLawGraph", "findRelatedLaws"]
GRAPH_WRITE = ["addLawToUserGraph", "findAndAddRelatedLawsToGraph"]
DOC_GRAPH_WRITE = ["linkDocumentClauseToArticle", "flagDocumentArticleConflict", "linkDocumentToLawsInGraph"]

BY_TASK: dict[TaskType, list[str]] = {
    TaskType.LAW_SEARCH: SEARCH + GRAPH_READ,
    TaskType.GENERAL_LEGAL_ADVICE: ["searchLaws", "findArticle"],
    TaskType.DOCUMENT_AUDIT: ["findArticle", "searchArticles", "searchUserDocuments"],
    TaskType.DOCUMENT_QA: ["searchUserDocuments", "findArticle"],
    TaskType.SUMMARY: [],
}
DEFAULT = ["searchLaws", "findArticle"]


def tools_for(req: BrainRequest, plan: ContextPlan) -> list[str]:
    names = list(BY_TASK.get(plan.task, DEFAULT))
    if not names:
        return []
    if req.has_graph and plan.task in (TaskType.LAW_SEARCH, TaskType.DOCUMENT_AUDIT):
        names += GRAPH_WRITE + (["flagArticleConflict"] if plan.task == TaskType.LAW_SEARCH else [])
        if req.has_document:
            names += DOC_GRAPH_WRITE
    if not req.has_document:
        names = [n for n in names if n != "searchUserDocuments"]
    return list(dict.fromkeys(names))
