package com.jvmd.llmbrainservice.service.graph.primary;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jvmd.llmbrainservice.dto.LawInfo;
import com.jvmd.llmbrainservice.util.RateLimitRetry;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class LlmFallbackSource implements PrimaryLawSource {

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    public LlmFallbackSource(
        ChatClient.Builder chatClientBuilder,
        ObjectMapper objectMapper
    ) {
        this.chatClient = chatClientBuilder.build();
        this.objectMapper = objectMapper;
    }

    @Override
    public int order() {
        return 5;
    }

    @Override
    public String name() {
        return "llm-primary";
    }

    @Override
    public boolean shouldRun(PrimaryLawContext ctx) {
        return true;
    }

    @Override
    public List<LawInfo> collect(PrimaryLawContext ctx) {
        String docText = ctx.docText();
        if (docText == null || docText.isBlank()) return List.of();
        String snippet =
            docText.length() > 2000 ? docText.substring(0, 2000) : docText;
        String subjectGenitive = ctx.linker().displayNameGenitive();

        String prompt = """
            Ты — юрист Республики Казахстан. ОТВЕЧАЙ ТОЛЬКО по законам РК.
            Запрещено ссылаться на ТК РФ / УК РФ / ГК РФ / законы РСФСР / СССР / ЕС
            и любые другие юрисдикции — только РК.

            Прочитай содержимое %s (может быть разговорной речью, угрозами,
            трудовыми конфликтами, нецензурной лексикой) и определи 3–6
            нормативных актов РК, применимых к описанной ситуации.

            Используй СТРОГО официальные сокращения кодексов РК:
              "УК РК"   — Уголовный кодекс
              "ТК РК"   — Трудовой кодекс
              "ГК РК"   — Гражданский кодекс
              "КоАП РК" — Кодекс об административных правонарушениях
              "НК РК"   — Налоговый кодекс
              "СК РК"   — Кодекс «О браке (супружестве) и семье»
              или "Закон РК О …" — для отдельных законов РК.

            Верни СТРОГО валидный JSON-массив строк, без какого-либо текста
            до или после, без markdown-fences. Если ничего применимого нет — верни [].

            Пример валидного ответа:
            ["УК РК","ТК РК","КоАП РК"]

            Содержимое:
            %s
            """.formatted(subjectGenitive, snippet);

        try {
            String content = RateLimitRetry.call(
                () -> chatClient.prompt().user(prompt).call().content(),
                20,
                "LlmFallbackSource"
            );
            List<String> codes = parseLlmCodeArray(content);
            if (codes.isEmpty()) return List.of();
            log.info(
                "LlmFallbackSource produced {} code(s): {}",
                codes.size(),
                codes
            );
            List<LawInfo> out = new ArrayList<>(codes.size());
            for (String code : codes) {
                LawInfo law = new LawInfo();
                law.code(code);
                law.title(code);
                out.add(law);
            }
            return out;
        } catch (Exception e) {
            log.warn("LlmFallbackSource failed: {}", e.getMessage());
            return List.of();
        }
    }

    List<String> parseLlmCodeArray(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        String s = raw.trim();
        if (s.startsWith("```")) {
            int firstNl = s.indexOf('\n');
            if (firstNl > 0) s = s.substring(firstNl + 1);
            int end = s.lastIndexOf("```");
            if (end > 0) s = s.substring(0, end);
            s = s.trim();
        }
        int bracket = s.indexOf('[');
        int brace = s.indexOf('{');
        int start = (bracket == -1)
            ? brace
            : (brace == -1 ? bracket : Math.min(bracket, brace));
        if (start > 0) s = s.substring(start);
        try {
            JsonNode node = objectMapper.readTree(s);
            JsonNode arr = node.isArray() ? node : node.get("codes");
            if (arr == null || !arr.isArray()) return List.of();
            LinkedHashSet<String> dedup = new LinkedHashSet<>();
            List<String> out = new ArrayList<>();
            for (JsonNode e : arr) {
                if (!e.isTextual()) continue;
                String v = e.asText().trim();
                if (v.isEmpty()) continue;
                if (dedup.add(v.toUpperCase(Locale.ROOT))) out.add(v);
            }
            return out;
        } catch (Exception e) {
            log.debug("Failed to parse LLM code array: {}", e.getMessage());
            return List.of();
        }
    }
}
