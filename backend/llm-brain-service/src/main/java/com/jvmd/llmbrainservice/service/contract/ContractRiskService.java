package com.jvmd.llmbrainservice.service.contract;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jvmd.llmbrainservice.client.LawClient;
import com.jvmd.llmbrainservice.dto.ClauseRiskResult;
import com.jvmd.llmbrainservice.dto.MissingClauseResult;
import com.jvmd.llmbrainservice.dto.RiskScanResponse;
import com.jvmd.llmbrainservice.model.RetrievalChunkResponse;
import com.jvmd.llmbrainservice.service.contract.detector.ContractTypeDetector;
import com.jvmd.llmbrainservice.service.llm.BrainModelClient;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class ContractRiskService {

    private static final Pattern JSON_ARRAY = Pattern.compile(
        "\\[\\s*\\{.*?\\}\\s*\\]",
        Pattern.DOTALL
    );
    private static final int BATCH_SIZE = 15;

    private final ClauseExtractor clauseExtractor;
    private final LawClient lawClient;
    private final BrainModelClient modelClient;
    private final List<ContractTypeDetector> contractTypeDetectors;
    private final ObjectMapper objectMapper;

    public RiskScanResponse scan(String text, String country) {
        if (country == null || country.isBlank()) country = "RK";

        List<ClauseExtractor.Clause> clauses = clauseExtractor.extract(text);
        String contractType = detectType(text);

        if (clauses.isEmpty()) {
            log.warn(
                "No clauses extracted from contract text ({} chars)",
                text.length()
            );
            return RiskScanResponse.builder()
                .contractType(contractType)
                .clauses(List.of())
                .missingClauses(checkMissing(text, contractType))
                .build();
        }

        String articlesCtx = buildArticlesContext(contractType, country);

        List<ClauseRiskResult> allResults = new ArrayList<>();
        for (int i = 0; i < clauses.size(); i += BATCH_SIZE) {
            List<ClauseExtractor.Clause> batch = clauses.subList(
                i,
                Math.min(i + BATCH_SIZE, clauses.size())
            );
            allResults.addAll(analyzeBatch(batch, articlesCtx, contractType));
        }

        List<MissingClauseResult> missing = checkMissing(text, contractType);

        int violations = 0,
            warnings = 0,
            compliant = 0;
        for (ClauseRiskResult r : allResults) {
            switch (r.getRiskLevel()) {
                case "VIOLATION" -> violations++;
                case "WARNING" -> warnings++;
                default -> compliant++;
            }
        }

        return RiskScanResponse.builder()
            .contractType(contractType)
            .clauses(allResults)
            .missingClauses(missing)
            .totalViolations(violations)
            .totalWarnings(warnings)
            .totalCompliant(compliant)
            .build();
    }

    private String detectType(String text) {
        return contractTypeDetectors
            .stream()
            .map(d -> d.detect(text))
            .filter(java.util.Optional::isPresent)
            .map(java.util.Optional::get)
            .findFirst()
            .orElse("иной");
    }

    private String buildArticlesContext(String contractType, String country) {
        List<String> queries = searchQueriesFor(contractType);
        List<RetrievalChunkResponse> articles = new ArrayList<>();
        for (String q : queries) {
            try {
                List<RetrievalChunkResponse> found =
                    lawClient.searchLawsByCountry(country, q);
                for (RetrievalChunkResponse r : found) {
                    if (articles.size() >= 20) break;
                    articles.add(r);
                }
            } catch (Exception e) {
                log.warn("DMS search failed for '{}': {}", q, e.getMessage());
            }
        }

        if (articles.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        for (RetrievalChunkResponse chunk : articles) {
            sb.append(formatChunk(chunk)).append("\n\n");
        }
        return sb.toString();
    }

    private List<String> searchQueriesFor(String contractType) {
        return ContractSearchQueries.forType(contractType);
    }

    private String formatChunk(RetrievalChunkResponse chunk) {
        StringBuilder sb = new StringBuilder();
        if (chunk.metadata() != null) {
            Object lawCode = chunk.metadata().get("lawCode");
            Object artNum = chunk.metadata().get("articleNumber");
            if (lawCode != null) {
                sb.append("[");
                if (artNum != null) sb.append("ст. ")
                    .append(artNum)
                    .append(" ");
                sb.append(lawCode).append("] ");
            }
        }
        String text = chunk.text();
        if (text != null && text.length() > 300) text =
            text.substring(0, 300) + "…";
        sb.append(text);
        return sb.toString();
    }

    private List<ClauseRiskResult> analyzeBatch(
        List<ClauseExtractor.Clause> batch,
        String articlesCtx,
        String contractType
    ) {
        String clausesBlock = buildClausesBlock(batch);
        String prompt = buildPrompt(contractType, articlesCtx, clausesBlock);

        String raw;
        try {
            raw = modelClient.answer(prompt, List.of()).content();
        } catch (Exception e) {
            log.error("LLM risk analysis failed: {}", e.getMessage());
            return fallbackResults(batch);
        }

        List<ClauseRiskResult> parsed = parseJsonResponse(raw, batch);
        if (parsed == null) {
            log.warn("Failed to parse LLM JSON response, using fallback");
            return fallbackResults(batch);
        }
        return parsed;
    }

    private String buildClausesBlock(List<ClauseExtractor.Clause> clauses) {
        StringBuilder sb = new StringBuilder();
        for (ClauseExtractor.Clause c : clauses) {
            sb.append("[")
                .append(c.id())
                .append("] ")
                .append(c.text())
                .append("\n");
        }
        return sb.toString();
    }

    private String buildPrompt(
        String contractType,
        String articlesCtx,
        String clausesBlock
    ) {
        String normsSection = articlesCtx.isBlank()
            ? "(нормативная база не найдена — используй общие знания законодательства РК)\n"
            : "ПРИМЕНИМЫЕ НОРМЫ ИЗ БАЗЫ ЗАКОНОВ:\n" + articlesCtx + "\n";

        return """
        Ты — эксперт по законодательству Республики Казахстан.
        Проанализируй пункты договора %s на соответствие нормам РК.

        %s
        ПУНКТЫ ДОГОВОРА (формат [номер] текст):
        %s

        Верни ТОЛЬКО JSON-массив (без markdown-обёртки и без пояснений):
        [{"clauseId":"1.1","riskLevel":"COMPLIANT","lawCitation":null,"reason":"..."},...]

        Правила классификации:
        - COMPLIANT — соответствует нормам или не регулируется законом
        - WARNING — потенциально невыгодный для клиента пункт, но закон не нарушен
        - VIOLATION — прямо нарушает обязательную норму закона

        Для VIOLATION и WARNING обязательно укажи lawCitation ("ст. X Закона") и reason.
        Для COMPLIANT оставь lawCitation: null, reason может быть null.
        """.formatted(contractType, normsSection, clausesBlock);
    }

    @SuppressWarnings("unchecked")
    private List<ClauseRiskResult> parseJsonResponse(
        String raw,
        List<ClauseExtractor.Clause> batch
    ) {
        try {
            String clean = raw.trim();
            if (clean.startsWith("```")) {
                clean = clean
                    .replaceFirst("```[a-z]*\n?", "")
                    .replaceAll("```$", "")
                    .trim();
            }

            Matcher m = JSON_ARRAY.matcher(clean);
            if (m.find()) clean = m.group();

            List<Map<String, Object>> list = objectMapper.readValue(
                clean,
                new TypeReference<>() {}
            );
            List<ClauseRiskResult> results = new ArrayList<>(list.size());

            Map<String, String> idToText = new java.util.HashMap<>();
            for (ClauseExtractor.Clause c : batch)
                idToText.put(c.id(), c.text());

            for (Map<String, Object> item : list) {
                String id = str(item, "clauseId");
                String level = normalizeLevel(str(item, "riskLevel"));
                String citation = str(item, "lawCitation");
                String reason = str(item, "reason");
                results.add(
                    ClauseRiskResult.builder()
                        .clauseId(id)
                        .clauseText(idToText.getOrDefault(id, ""))
                        .riskLevel(level)
                        .lawCitation(citation)
                        .reason(reason)
                        .build()
                );
            }
            return results;
        } catch (Exception e) {
            log.debug("JSON parse error: {}", e.getMessage());
            return null;
        }
    }

    private String normalizeLevel(String raw) {
        if (raw == null) return "COMPLIANT";
        return switch (raw.toUpperCase(Locale.ROOT)) {
            case "VIOLATION", "НАРУШЕНИЕ" -> "VIOLATION";
            case "WARNING", "ПРЕДУПРЕЖДЕНИЕ", "WARN" -> "WARNING";
            default -> "COMPLIANT";
        };
    }

    private List<ClauseRiskResult> fallbackResults(
        List<ClauseExtractor.Clause> batch
    ) {
        return batch
            .stream()
            .map(c ->
                ClauseRiskResult.builder()
                    .clauseId(c.id())
                    .clauseText(c.text())
                    .riskLevel("COMPLIANT")
                    .build()
            )
            .toList();
    }

    private List<MissingClauseResult> checkMissing(
        String text,
        String contractType
    ) {
        String lower = text.toLowerCase(Locale.ROOT);
        List<MissingClauseResult> missing = new ArrayList<>();

        List<RequiredClauseRegistry.RequiredClause> required = requiredClauses(
            contractType
        );
        for (RequiredClauseRegistry.RequiredClause rc : required) {
            boolean found = rc.keywords().stream().anyMatch(lower::contains);
            if (!found) {
                missing.add(new MissingClauseResult(rc.name(), rc.lawBasis()));
            }
        }
        return missing;
    }

    private List<RequiredClauseRegistry.RequiredClause> requiredClauses(
        String contractType
    ) {
        return RequiredClauseRegistry.forType(contractType);
    }

    private static String str(Map<String, Object> map, String key) {
        Object v = map.get(key);
        if (v == null || "null".equals(v)) return null;
        String s = v.toString().trim();
        return s.isEmpty() ? null : s;
    }
}
