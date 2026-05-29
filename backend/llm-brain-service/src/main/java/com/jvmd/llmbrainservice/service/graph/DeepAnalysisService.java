package com.jvmd.llmbrainservice.service.graph;

import com.jvmd.llmbrainservice.client.GraphServiceClient;
import com.jvmd.llmbrainservice.client.GraphServiceClient.ArticleInfo;
import com.jvmd.llmbrainservice.client.GraphServiceClient.LawInfo;
import com.jvmd.llmbrainservice.service.graph.link.LinkableSubject;
import com.jvmd.llmbrainservice.service.graph.link.SubjectLinker;
import com.jvmd.llmbrainservice.service.graph.primary.PrimaryLawContext;
import com.jvmd.llmbrainservice.service.graph.primary.PrimaryLawResolver;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeepAnalysisService {

    private final GraphServiceClient graphServiceClient;
    private final PrimaryLawResolver primaryLawResolver;

    @Value("${deep-analysis.max-laws-total:25}")
    private int maxLawsTotal;

    @Value("${deep-analysis.max-articles-total:75}")
    private int maxArticlesTotal;

    @Value("${deep-analysis.max-conflicts:10}")
    private int maxConflicts;

    @Value("${deep-analysis.primary-laws:5}")
    private int primaryLaws;

    @Value("${deep-analysis.articles-per-law:3}")
    private int articlesPerLaw;

    @Value("${deep-analysis.related-per-law-hop2:3}")
    private int relatedPerLawHop2;

    @Value("${deep-analysis.related-per-law-hop3:2}")
    private int relatedPerLawHop3;

    public record ProgressEvent(
        String phase,
        int done,
        int total,
        String message
    ) {}

    public record DeepAnalysisResult(
        int primaryLaws,
        int secondaryLaws,
        int tertiaryLaws,
        int articlesLinked,
        int conflictsFound,
        String report
    ) {}

    public DeepAnalysisResult run(
        String graphId,
        SubjectLinker linker,
        LinkableSubject subject,
        String country,
        int depth,
        Consumer<ProgressEvent> progress
    ) {
        String ctry = country == null || country.isBlank() ? "RK" : country;
        int safeDepth = Math.max(1, Math.min(depth, 3));

        emit(
            progress,
            "start",
            0,
            0,
            "Запускаю глубокий анализ " +
                linker.displayNameGenitive() +
                " (depth=" +
                safeDepth +
                ")…"
        );

        String docText;
        try {
            docText = linker.fetchSearchableText(subject, graphId);
        } catch (Exception e) {
            log.warn(
                "DeepAnalysis: fetchSearchableText failed for {} {}: {}",
                linker.kind(),
                subject.id(),
                e.getMessage()
            );
            docText = "";
        }
        if (docText == null || docText.isBlank()) {
            return new DeepAnalysisResult(
                0,
                0,
                0,
                0,
                0,
                "Не удалось получить текст " +
                    linker.displayNameGenitive() +
                    " — попробуйте ещё раз через несколько секунд."
            );
        }

        emit(
            progress,
            "primary_laws",
            0,
            primaryLaws,
            "Ищу прямые применимые нормы…"
        );
        PrimaryLawContext primaryCtx = new PrimaryLawContext(
            docText,
            ctry,
            linker,
            subject,
            graphId,
            primaryLaws,
            java.util.Collections.emptyMap()
        );
        LinkedHashMap<String, LawInfo> primaryHits = primaryLawResolver.resolve(
            primaryCtx
        );
        if (primaryHits.isEmpty()) {
            return new DeepAnalysisResult(
                0,
                0,
                0,
                0,
                0,
                "В базе не найдено законов, релевантных содержимому " +
                    linker.displayNameGenitive() +
                    "."
            );
        }

        Set<String> visitedLaws = new LinkedHashSet<>();
        List<LawInfo> primaryAttached = new ArrayList<>();
        for (LawInfo law : primaryHits
            .values()
            .stream()
            .limit(primaryLaws)
            .toList()) {
            if (visitedLaws.size() >= maxLawsTotal) break;
            boolean added = graphServiceClient.addLawToUserGraph(
                graphId,
                law.getCode(),
                ctry
            );
            boolean linked = linker.linkToLaw(
                graphId,
                subject,
                law.getCode(),
                ctry
            );
            if (added && linked) {
                visitedLaws.add(law.getCode());
                primaryAttached.add(law);
                emit(
                    progress,
                    "primary_laws",
                    primaryAttached.size(),
                    primaryLaws,
                    "Добавлен закон " + law.getCode()
                );
            }
        }
        if (primaryAttached.isEmpty()) {
            return new DeepAnalysisResult(
                0,
                0,
                0,
                0,
                0,
                "Найдены релевантные законы, но ни один не удалось привязать к графу."
            );
        }

        int articlesLinked = 0;
        Map<String, List<RankedArticle>> articlesByLaw = new LinkedHashMap<>();
        if (safeDepth >= 2) {
            emit(
                progress,
                "articles",
                0,
                primaryAttached.size() * articlesPerLaw,
                "Подбираю наиболее релевантные статьи…"
            );
            for (LawInfo law : primaryAttached) {
                List<ArticleInfo> all = graphServiceClient.listArticlesByLaw(
                    law.getCode(),
                    ctry
                );
                if (all.isEmpty()) continue;
                List<RankedArticle> ranked = rankArticles(
                    all,
                    docText,
                    articlesPerLaw
                );
                articlesByLaw.put(law.getCode(), ranked);
                for (RankedArticle ra : ranked) {
                    if (articlesLinked >= maxArticlesTotal) break;
                    String docSnippet = bestDocSnippet(docText, ra.article());
                    String articleSnippet = snippet(
                        ra.article().getBody(),
                        220
                    );
                    boolean ok = linker.linkClauseToArticle(
                        graphId,
                        subject,
                        law.getCode(),
                        ctry,
                        ra.article().getNumber(),
                        "auto",
                        docSnippet,
                        articleSnippet,
                        ra.score()
                    );
                    if (ok) {
                        articlesLinked++;
                        emit(
                            progress,
                            "articles",
                            articlesLinked,
                            primaryAttached.size() * articlesPerLaw,
                            "Связана ст. " +
                                ra.article().getNumber() +
                                " " +
                                law.getCode()
                        );
                    }
                }
                if (articlesLinked >= maxArticlesTotal) break;
            }
        }

        List<LawInfo> secondaryAttached = new ArrayList<>();
        if (safeDepth >= 2) {
            int targetSecondary = Math.min(
                maxLawsTotal - visitedLaws.size(),
                primaryAttached.size() * relatedPerLawHop2
            );
            emit(
                progress,
                "related_laws_2",
                0,
                targetSecondary,
                "Расширяю графа связанными нормами…"
            );
            for (LawInfo primary : primaryAttached) {
                if (visitedLaws.size() >= maxLawsTotal) break;
                List<LawInfo> related = graphServiceClient.findRelatedLaws(
                    primary.getCode(),
                    ctry
                );
                int addedForThisPrimary = 0;
                for (LawInfo rel : related) {
                    if (
                        rel.getCode() == null ||
                        visitedLaws.contains(rel.getCode())
                    ) continue;
                    if (addedForThisPrimary >= relatedPerLawHop2) break;
                    if (visitedLaws.size() >= maxLawsTotal) break;
                    if (
                        graphServiceClient.addLawToUserGraph(
                            graphId,
                            rel.getCode(),
                            ctry
                        )
                    ) {
                        visitedLaws.add(rel.getCode());
                        secondaryAttached.add(rel);
                        addedForThisPrimary++;
                        emit(
                            progress,
                            "related_laws_2",
                            secondaryAttached.size(),
                            targetSecondary,
                            "Добавлен связанный закон " + rel.getCode()
                        );
                    }
                }
            }
        }

        List<LawInfo> tertiaryAttached = new ArrayList<>();
        if (safeDepth >= 3 && !secondaryAttached.isEmpty()) {
            int targetTertiary = Math.min(
                maxLawsTotal - visitedLaws.size(),
                secondaryAttached.size() * relatedPerLawHop3
            );
            emit(
                progress,
                "related_laws_3",
                0,
                targetTertiary,
                "Расширяю граф до 3-го уровня…"
            );
            for (LawInfo secondary : secondaryAttached) {
                if (visitedLaws.size() >= maxLawsTotal) break;
                List<LawInfo> related = graphServiceClient.findRelatedLaws(
                    secondary.getCode(),
                    ctry
                );
                int addedForThisSecondary = 0;
                for (LawInfo rel : related) {
                    if (
                        rel.getCode() == null ||
                        visitedLaws.contains(rel.getCode())
                    ) continue;
                    if (addedForThisSecondary >= relatedPerLawHop3) break;
                    if (visitedLaws.size() >= maxLawsTotal) break;
                    if (
                        graphServiceClient.addLawToUserGraph(
                            graphId,
                            rel.getCode(),
                            ctry
                        )
                    ) {
                        visitedLaws.add(rel.getCode());
                        tertiaryAttached.add(rel);
                        addedForThisSecondary++;
                        emit(
                            progress,
                            "related_laws_3",
                            tertiaryAttached.size(),
                            targetTertiary,
                            "Добавлен закон 3-го уровня " + rel.getCode()
                        );
                    }
                }
            }
        }

        int conflictsFlagged = 0;
        if (safeDepth >= 3) {
            emit(
                progress,
                "conflicts",
                0,
                maxConflicts,
                "Проверяю противоречия в нормах…"
            );
            conflictsFlagged = scanConflicts(
                graphId,
                linker,
                subject,
                ctry,
                primaryAttached,
                articlesByLaw,
                docText,
                progress
            );
        }

        String report = buildReport(
            primaryAttached,
            secondaryAttached,
            tertiaryAttached,
            articlesByLaw,
            articlesLinked,
            conflictsFlagged,
            safeDepth,
            linker.displayNameGenitive()
        );

        emit(progress, "persist", 0, 1, "Сохраняю комментарий в граф…");
        persistAnalysisComment(
            graphId,
            linker,
            subject,
            report,
            primaryAttached,
            secondaryAttached,
            tertiaryAttached,
            articlesByLaw,
            ctry
        );

        emit(progress, "done", 1, 1, "Готово.");

        return new DeepAnalysisResult(
            primaryAttached.size(),
            secondaryAttached.size(),
            tertiaryAttached.size(),
            articlesLinked,
            conflictsFlagged,
            report
        );
    }

    private static String neo4jLabelFor(
        com.jvmd.llmbrainservice.service.graph.link.SubjectKind kind
    ) {
        return switch (kind) {
            case DOCUMENT -> "UserDocument";
            case SITUATION -> "Situation";
            case VOICE_EVIDENCE -> "VoiceEvidence";
        };
    }

    private void persistAnalysisComment(
        String graphId,
        SubjectLinker linker,
        LinkableSubject subject,
        String report,
        List<LawInfo> primaryAttached,
        List<LawInfo> secondaryAttached,
        List<LawInfo> tertiaryAttached,
        Map<String, List<RankedArticle>> articlesByLaw,
        String country
    ) {
        try {
            LinkedHashSet<String> lawCodes = new LinkedHashSet<>();
            for (LawInfo l : primaryAttached)
                if (l.getCode() != null) lawCodes.add(l.getCode());
            for (LawInfo l : secondaryAttached)
                if (l.getCode() != null) lawCodes.add(l.getCode());
            for (LawInfo l : tertiaryAttached)
                if (l.getCode() != null) lawCodes.add(l.getCode());

            List<GraphServiceClient.CommentArticleRef> articleRefs =
                new ArrayList<>();
            articlesByLaw.forEach((lawCode, ranked) -> {
                for (RankedArticle ra : ranked) {
                    if (ra.article().getNumber() == null) continue;
                    articleRefs.add(
                        new GraphServiceClient.CommentArticleRef(
                            lawCode,
                            ra.article().getNumber(),
                            country,
                            "Подобрана как релевантная статья (score=" +
                                String.format(Locale.ROOT, "%.2f", ra.score()) +
                                ")"
                        )
                    );
                }
            });

            String title =
                "Анализ " +
                linker.displayNameGenitive() +
                (subject.label() != null && !subject.label().isBlank()
                    ? " · " + subject.label()
                    : "");

            String subjectLabel = neo4jLabelFor(linker.kind());

            GraphServiceClient.CreateCommentRequest req =
                GraphServiceClient.CreateCommentRequest.builder()
                    .title(title)
                    .body(report)
                    .kind("DEEP_ANALYSIS")
                    .subjectKind(subjectLabel)
                    .subjectId(subject.id())
                    .referencedLawCodes(new ArrayList<>(lawCodes))
                    .referencedArticles(articleRefs)
                    .build();
            graphServiceClient.createComment(graphId, req);
        } catch (Exception e) {
            log.warn(
                "Failed to persist analysis comment for graph {}: {}",
                graphId,
                e.getMessage()
            );
        }
    }

    private record RankedArticle(ArticleInfo article, double score) {}

    private List<RankedArticle> rankArticles(
        List<ArticleInfo> articles,
        String docText,
        int topN
    ) {
        Set<String> docTokens = tokenize(docText);
        List<RankedArticle> ranked = new ArrayList<>();
        for (ArticleInfo art : articles) {
            String hay =
                (art.getTitle() == null ? "" : art.getTitle()) +
                " " +
                (art.getBody() == null ? "" : art.getBody());
            if (hay.isBlank()) continue;
            Set<String> artTokens = tokenize(hay);
            if (artTokens.isEmpty()) continue;
            int overlap = 0;
            for (String t : artTokens) if (docTokens.contains(t)) overlap++;
            double score = (double) overlap / Math.max(1, artTokens.size());
            if (score > 0) ranked.add(
                new RankedArticle(art, Math.min(0.95, 0.5 + score))
            );
        }
        ranked.sort((a, b) -> Double.compare(b.score(), a.score()));
        return ranked.stream().limit(topN).toList();
    }

    private static final Pattern TOKEN_PATTERN = Pattern.compile(
        "[\\p{L}]{4,}"
    );

    private static final Set<String> STOPWORDS = Set.of(
        "этот",
        "эта",
        "это",
        "эти",
        "тот",
        "тем",
        "тех",
        "который",
        "которая",
        "которое",
        "которые",
        "статья",
        "пункт",
        "часть",
        "глава",
        "раздел",
        "также",
        "более",
        "менее",
        "если",
        "когда",
        "будет",
        "может",
        "должн",
        "обязан",
        "вправе",
        "иной",
        "иные",
        "иная",
        "иных"
    );

    private static Set<String> tokenize(String text) {
        Set<String> out = new LinkedHashSet<>();
        Matcher m = TOKEN_PATTERN.matcher(
            text.toLowerCase(Locale.ROOT).replace('ё', 'е')
        );
        while (m.find()) {
            String tok = m.group();
            if (STOPWORDS.contains(tok)) continue;
            out.add(tok);
        }
        return out;
    }

    private String bestDocSnippet(String docText, ArticleInfo article) {
        String title = article.getTitle();
        if (title == null || title.isBlank()) return snippet(docText, 220);
        Set<String> titleTokens = tokenize(title);
        for (String sentence : docText.split("(?<=[.!?])\\s+")) {
            Set<String> stoks = tokenize(sentence);
            for (String t : titleTokens) {
                if (stoks.contains(t)) {
                    return snippet(sentence, 220);
                }
            }
        }
        return snippet(docText, 220);
    }

    private static String snippet(String s, int max) {
        if (s == null) return "";
        String trimmed = s.strip();
        if (trimmed.length() <= max) return trimmed;
        return trimmed.substring(0, max) + "…";
    }

    private int scanConflicts(
        String graphId,
        SubjectLinker linker,
        LinkableSubject subject,
        String country,
        List<LawInfo> primaryAttached,
        Map<String, List<RankedArticle>> articlesByLaw,
        String docText,
        Consumer<ProgressEvent> progress
    ) {
        int flagged = 0;
        String doc = docText.toLowerCase(Locale.ROOT);
        boolean docSaysObligated =
            doc.contains("обязан") ||
            doc.contains("должен") ||
            doc.contains("обязуется");
        boolean docSaysCannotRefuse =
            doc.contains("не вправе") ||
            doc.contains("не может") ||
            doc.contains("запрещается");
        boolean docHasPenalty =
            doc.contains("неустойк") ||
            doc.contains("штраф") ||
            doc.contains("пени");

        for (LawInfo law : primaryAttached) {
            List<RankedArticle> ranked = articlesByLaw.get(law.getCode());
            if (ranked == null) continue;
            for (RankedArticle ra : ranked) {
                if (flagged >= maxConflicts) break;
                String body = ra.article().getBody();
                if (body == null) continue;
                String b = body.toLowerCase(Locale.ROOT);
                String reason = null;
                if (
                    docSaysObligated &&
                    (b.contains("вправе отказаться") || b.contains("не обязан"))
                ) {
                    reason =
                        "Документ обязывает сторону, тогда как " +
                        law.getCode() +
                        " ст. " +
                        ra.article().getNumber() +
                        " допускает отказ";
                } else if (docSaysCannotRefuse && b.contains("вправе")) {
                    reason =
                        "Документ запрещает сторону, тогда как " +
                        law.getCode() +
                        " ст. " +
                        ra.article().getNumber() +
                        " наделяет правом";
                } else if (
                    docHasPenalty &&
                    (b.contains("без неустойки") || b.contains("без штраф"))
                ) {
                    reason =
                        "Документ предусматривает санкцию, тогда как " +
                        law.getCode() +
                        " ст. " +
                        ra.article().getNumber() +
                        " её исключает";
                }
                if (reason != null) {
                    boolean ok = linker.flagConflictOnArticle(
                        graphId,
                        subject,
                        law.getCode(),
                        country,
                        ra.article().getNumber(),
                        "auto",
                        reason,
                        0.55
                    );
                    if (ok) {
                        flagged++;
                        emit(
                            progress,
                            "conflicts",
                            flagged,
                            maxConflicts,
                            "Возможное противоречие: ст. " +
                                ra.article().getNumber() +
                                " " +
                                law.getCode()
                        );
                    }
                }
            }
            if (flagged >= maxConflicts) break;
        }
        return flagged;
    }

    private String buildReport(
        List<LawInfo> primary,
        List<LawInfo> secondary,
        List<LawInfo> tertiary,
        Map<String, List<RankedArticle>> articlesByLaw,
        int articlesLinked,
        int conflicts,
        int depth,
        String subjectLabel
    ) {
        StringBuilder r = new StringBuilder();
        r.append("## Глубокий анализ ")
            .append(subjectLabel == null ? "субъекта" : subjectLabel)
            .append("\n");
        r.append("_Уровень: ")
            .append(
                depth == 3
                    ? "идеальный (3 хопа + проверка конфликтов)"
                    : "глубокий (2 хопа)"
            )
            .append("_\n\n");

        r.append("### Прямые применимые нормы (")
            .append(primary.size())
            .append(")\n");
        for (LawInfo law : primary) {
            r.append("- **").append(law.getCode()).append("**");
            if (law.getTitle() != null && !law.getTitle().isBlank()) {
                r.append(": ").append(law.getTitle());
            }
            r.append("\n");
            List<RankedArticle> arts = articlesByLaw.get(law.getCode());
            if (arts != null) {
                for (RankedArticle ra : arts) {
                    r.append("  - Ст. ").append(ra.article().getNumber());
                    if (
                        ra.article().getTitle() != null &&
                        !ra.article().getTitle().isBlank()
                    ) {
                        r.append(" — ").append(ra.article().getTitle());
                    }
                    r.append(" _(score ")
                        .append(String.format(Locale.ROOT, "%.2f", ra.score()))
                        .append(")_\n");
                }
            }
        }

        if (!secondary.isEmpty()) {
            r.append("\n### Связанные нормы 2-го уровня (")
                .append(secondary.size())
                .append(")\n");
            for (LawInfo law : secondary) {
                r.append("- **").append(law.getCode()).append("**");
                if (law.getTitle() != null && !law.getTitle().isBlank()) {
                    r.append(": ").append(law.getTitle());
                }
                r.append("\n");
            }
        }

        if (!tertiary.isEmpty()) {
            r.append("\n### Связанные нормы 3-го уровня (")
                .append(tertiary.size())
                .append(")\n");
            for (LawInfo law : tertiary) {
                r.append("- **").append(law.getCode()).append("**");
                if (law.getTitle() != null && !law.getTitle().isBlank()) {
                    r.append(": ").append(law.getTitle());
                }
                r.append("\n");
            }
        }

        if (conflicts > 0) {
            r.append("\n### ⚠ Обнаружено возможных противоречий: ")
                .append(conflicts)
                .append("\n");
            r.append(
                "_Помечены в графе как CONFLICTS_WITH; требуют проверки юристом._\n"
            );
        }

        r.append("\n### Сводка\n");
        r.append("- Прямых норм: ").append(primary.size()).append("\n");
        r.append("- Статей привязано к документу: ")
            .append(articlesLinked)
            .append("\n");
        r.append("- 2-й уровень: ").append(secondary.size()).append("\n");
        if (depth >= 3) r.append("- 3-й уровень: ")
            .append(tertiary.size())
            .append("\n");
        r.append("- Конфликтов: ").append(conflicts).append("\n");
        r.append(
            "\n_Источник: PrimaryLawResolver (keyword-search → subject-seed → llm-fallback) + RELATES_TO BFS из graph-service. " +
                "Provenance: LLM_INFERENCE (extractedBy=deep-analysis)._"
        );
        return r.toString();
    }

    private void emit(
        Consumer<ProgressEvent> progress,
        String phase,
        int done,
        int total,
        String message
    ) {
        if (progress == null) return;
        try {
            progress.accept(new ProgressEvent(phase, done, total, message));
        } catch (Exception ignored) {

        }
    }
}
