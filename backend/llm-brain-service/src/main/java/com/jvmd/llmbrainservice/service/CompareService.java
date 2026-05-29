package com.jvmd.llmbrainservice.service;

import com.jvmd.llmbrainservice.client.DmsClient;
import com.jvmd.llmbrainservice.client.GraphServiceClient;
import com.jvmd.llmbrainservice.model.BrainResponse;
import com.jvmd.llmbrainservice.model.CompareRequest;
import com.jvmd.llmbrainservice.service.llm.BrainModelClient;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class CompareService {

    private static final int MAX_CHARS_PER_SIDE = 6000;

    private final DmsClient dmsClient;
    private final GraphServiceClient graphServiceClient;
    private final BrainModelClient brainModelClient;

    public BrainResponse compare(CompareRequest req) {
        String leftText = resolveText(
            req.leftKind(),
            req.leftId(),
            req.userId(),
            req.leftLabel()
        );
        String rightText = resolveText(
            req.rightKind(),
            req.rightId(),
            req.userId(),
            req.rightLabel()
        );

        String leftHeader = headerFor(
            req.leftKind(),
            req.leftLabel(),
            req.leftId()
        );
        String rightHeader = headerFor(
            req.rightKind(),
            req.rightLabel(),
            req.rightId()
        );

        String prompt = """
            Ты — юридический ассистент. Сравни два документа с точки зрения юриста, работающего с законодательством Казахстана.

            === ДОКУМЕНТ A: %s ===
            %s

            === ДОКУМЕНТ B: %s ===
            %s

            Выдай структурированный анализ в формате Markdown со следующими разделами:

            ## Краткая суть
            Одно-два предложения о том, что это за документы и как они соотносятся.

            ## Общие положения
            Маркированный список пересекающихся норм, целей, областей регулирования.

            ## Существенные различия
            Маркированный список различий с указанием стороны (A/B).

            ## Возможные противоречия
            Если есть конфликты или коллизии норм — перечисли их. Если нет — напиши «не выявлено».

            ## Вывод для юриста
            Краткий практический вывод: на что обратить внимание при работе с обоими документами.

            Не выдумывай факты. Если текст одного из документов не предоставлен или обрезан, явно укажи это в выводе.
            """.formatted(leftHeader, leftText, rightHeader, rightText);

        log.info(
            "Compare: A={} ({} chars) vs B={} ({} chars)",
            leftHeader,
            leftText.length(),
            rightHeader,
            rightText.length()
        );
        return brainModelClient.answer(prompt, List.of());
    }

    private String resolveText(
        String kind,
        String id,
        String userId,
        String label
    ) {
        if (id == null || id.isBlank()) return fallback(label, "ID не указан");
        try {
            if ("LAW".equalsIgnoreCase(kind)) {
                var law = lookupLaw(id);
                if (law != null) {
                    StringBuilder sb = new StringBuilder();
                    if (law.getTitle() != null) sb.append(
                        law.getTitle()
                    ).append("\n\n");
                    if (law.getSummary() != null) sb.append(law.getSummary());
                    if (sb.length() == 0 && label != null) sb.append(label);
                    return truncate(sb.toString());
                }
                return fallback(label, "закон не найден в графе");
            }
            if ("DOCUMENT".equalsIgnoreCase(kind)) {
                Map<String, String> res = dmsClient.getDocumentText(id, userId);
                if (res == null) return fallback(label, "документ не найден");
                String text = res.getOrDefault(
                    "text",
                    res.getOrDefault("content", "")
                );
                if (text == null || text.isBlank()) return fallback(
                    label,
                    "текст документа пуст"
                );
                return truncate(text);
            }
            return fallback(label, "неизвестный тип " + kind);
        } catch (Exception e) {
            log.warn(
                "Failed to load text for {}:{}: {}",
                kind,
                id,
                e.getMessage()
            );
            return fallback(label, "ошибка загрузки: " + e.getMessage());
        }
    }

    private GraphServiceClient.LawInfo lookupLaw(String code) {
        try {
            var graph = graphServiceClient.getLawWithRelationships(code, "RK");
            if (graph.isPresent() && graph.get().getLaw() != null) return graph
                .get()
                .getLaw();
        } catch (Exception e) {
            log.warn(
                "getLawWithRelationships failed for {}: {} — falling back to search",
                code,
                e.getMessage()
            );
        }
        try {
            var matches = graphServiceClient.searchLaws(code, "RK");
            if (!matches.isEmpty()) return matches.get(0);
        } catch (Exception e) {
            log.warn(
                "searchLaws fallback also failed for {}: {}",
                code,
                e.getMessage()
            );
        }
        return null;
    }

    private static String headerFor(String kind, String label, String id) {
        String l = label == null || label.isBlank() ? id : label;
        return ("LAW".equalsIgnoreCase(kind) ? "закон " : "документ ") + l;
    }

    private static String fallback(String label, String reason) {
        return (
            "[Текст недоступен" +
            (label == null ? "" : " для «" + label + "»") +
            ": " +
            reason +
            "]"
        );
    }

    private static String truncate(String s) {
        if (s == null) return "";
        if (s.length() <= MAX_CHARS_PER_SIDE) return s;
        return s.substring(0, MAX_CHARS_PER_SIDE) + "\n\n[...текст обрезан]";
    }
}
