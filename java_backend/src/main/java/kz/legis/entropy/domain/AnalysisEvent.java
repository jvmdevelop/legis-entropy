package kz.legis.entropy.domain;

public record AnalysisEvent(String stage, String message, int progress, Object data) {

    public static AnalysisEvent started() {
        return new AnalysisEvent("started", "Загрузка графа документов...", 5, null);
    }

    public static AnalysisEvent graphLoaded(int nodeCount, int linkCount) {
        return new AnalysisEvent(
                "graph_loaded",
                "Граф загружен: %d документов, %d связей".formatted(nodeCount, linkCount),
                35,
                null
        );
    }

    public static AnalysisEvent analyzing(int docCount) {
        return new AnalysisEvent(
                "analyzing",
                "Семантический анализ %d документов (BERT)...".formatted(docCount),
                60,
                null
        );
    }

    public static AnalysisEvent complete(GraphData graph, CorpusStats stats) {
        record CompleteData(GraphData graph, CorpusStats stats) {}
        return new AnalysisEvent("complete", "Анализ завершён", 100, new CompleteData(graph, stats));
    }

    public static AnalysisEvent error(String message) {
        return new AnalysisEvent("error", message, 0, null);
    }
}
