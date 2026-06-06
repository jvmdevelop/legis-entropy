package com.jvmd.llmbrainservice.service.graph.link;

import com.jvmd.llmbrainservice.client.GraphServiceClient;
import com.jvmd.llmbrainservice.dto.LawInfo;
import com.jvmd.llmbrainservice.service.graph.LawSignalFilter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class SubjectAwareLinkService {

    private final GraphServiceClient graphServiceClient;
    private final ChatClient legalQueryClient;

    private static final Pattern JSON_ARRAY_PATTERN = Pattern.compile(
            "\\[\\s*(\"[^\"]*\"(?:\\s*,\\s*\"[^\"]*\")*)\\s*\\]"
    );

    public SubjectAwareLinkService(
            GraphServiceClient graphServiceClient,
            ChatClient.Builder chatClientBuilder
    ) {
        this.graphServiceClient = graphServiceClient;
        this.legalQueryClient = chatClientBuilder.build();
    }

    public record LinkResult(
            int linked,
            String report,
            List<String> lawCodes
    ) {
    }

    public LinkResult linkSubjectToLaws(
            SubjectLinker linker,
            LinkableSubject subject,
            String graphId,
            int limit,
            String country
    ) {
        int safeLimit = Math.max(1, Math.min(limit, 10));
        String ctry = country == null || country.isBlank() ? "RK" : country;
        String label = linker.displayName();

        log.info(
                "SubjectAwareLinkService: linking {} {} to laws in graph={}, limit={}",
                label,
                subject.id(),
                graphId,
                safeLimit
        );

        String text = linker.fetchSearchableText(subject, graphId);
        if (text == null || text.isBlank()) {
            return new LinkResult(
                    0,
                    label +
                            " найден, но текст ещё не извлечён — попробуйте ещё раз через несколько секунд.",
                    List.of()
            );
        }

        List<String> queries = buildSmartSearchQueries(text);

        LinkedHashMap<String, LawInfo> hits =
                new LinkedHashMap<>();
        for (String q : queries) {
            try {
                for (var law : graphServiceClient.searchLaws(q, ctry)) {
                    if (law.code() == null) continue;
                    if (LawSignalFilter.isLowSignal(law)) continue;
                    hits.putIfAbsent(law.code(), law);
                }
            } catch (Exception e) {
                log.debug("searchLaws('{}') failed: {}", q, e.getMessage());
            }
            if (hits.size() >= safeLimit * 3) break;
        }

        if (hits.isEmpty()) {
            return new LinkResult(
                    0,
                    "В базе не найдено законов, релевантных " +
                            linker.displayNameAccusative() +
                            ". " +
                            "Связи не созданы.",
                    List.of()
            );
        }

        LinkedHashMap<String, LawInfo> filtered;
        if (hits.size() > safeLimit) {
            List<String> relevantCodes = filterRelevantLaws(text, hits);
            filtered = new LinkedHashMap<>();
            for (String code : relevantCodes) {
                if (hits.containsKey(code)) filtered.put(code, hits.get(code));
            }
            if (filtered.isEmpty()) {
                log.debug(
                        "LLM relevance filter returned no matches — using raw search hits as fallback"
                );
                filtered.putAll(hits);
            }
        } else {
            filtered = hits;
        }

        int linked = 0;
        StringBuilder report = new StringBuilder().append("**").append(label);
        if (subject.label() != null && !subject.label().isBlank()) {
            report.append(" «").append(subject.label()).append("»");
        }
        report.append("** связан со следующими нормами:\n");

        List<String> codes = new ArrayList<>();
        for (LawInfo law : filtered.values().stream().limit(safeLimit).toList()) {
            boolean added;
            try {
                graphServiceClient.addLawToUserGraph(
                        graphId,
                        law.code(),
                        ctry
                );
                added = true;
            } catch (Exception e) {
                log.error(e.getMessage());
                added = false;
            }

            boolean ok = linker.linkToLaw(
                    graphId,
                    subject,
                    law.code(),
                    ctry
            );
            if (added && ok) {
                linked++;
                codes.add(law.code());
                report
                        .append("- **")
                        .append(law.code())
                        .append("**")
                        .append(law.type() == null ? "" : ": " + law.title())
                        .append("\n");
            }
        }
        if (linked == 0) {
            return new LinkResult(
                    0,
                    "Найдены релевантные законы, но ни один не удалось добавить в граф (см. логи graphs-service).",
                    codes
            );
        }
        report.append(
                "\n_Связи установлены с применением LLM-анализа правового контекста. " +
                        "Источник: LLM_INFERENCE (semantic)._"
        );
        return new LinkResult(linked, report.toString(), codes);
    }

    private List<String> buildSmartSearchQueries(String text) {
        try {
            String snippet =
                    text.length() > 700 ? text.substring(0, 700) : text;
            String prompt = """
                    Ты — правовой аналитик Республики Казахстан. На основе описания ситуации ниже \
                    сформируй 3–5 поисковых запросов для базы нормативных актов РК.
                    
                    Правила:
                    - Каждый запрос — короткий правовой термин, название кодекса или тема (например: "ГПК", "отложение судебного заседания", "уголовный кодекс", "трудовой договор").
                    - Если ситуация процессуальная (ходатайство, перенос, отвод, иск) — первым запросом ставь соответствующий кодекс ("ГПК РК", "ГК РК", "УК РК", "КоАП РК" и т.д.).
                    - Не добавляй лишних слов — только JSON-массив строк.
                    
                    Ситуация: %s
                    
                    Ответ (только JSON):""".formatted(snippet);
            String response = legalQueryClient
                    .prompt()
                    .user(prompt)
                    .call()
                    .content();
            List<String> parsed = parseJsonStringArray(response);
            if (!parsed.isEmpty()) {
                log.info(
                        "LLM generated {} legal search queries: {}",
                        parsed.size(),
                        parsed
                );
                return parsed;
            }
        } catch (Exception e) {
            log.debug(
                    "LLM smart query generation failed, falling back to keyword heuristics: {}",
                    e.getMessage()
            );
        }
        return buildKeywordQueries(text);
    }

    private List<String> filterRelevantLaws(
            String text,
            LinkedHashMap<String, LawInfo> candidates
    ) {
        if (candidates.isEmpty()) return List.of();
        try {
            String snippet =
                    text.length() > 500 ? text.substring(0, 500) : text;
            StringBuilder lawList = new StringBuilder();
            for (var law : candidates.values()) {
                lawList
                        .append("- ")
                        .append(law.code())
                        .append(": ")
                        .append(law.title());
                if (law.summary() != null && !law.summary().isBlank()) {
                    String sum =
                            law.summary().length() > 120
                                    ? law.summary().substring(0, 120) + "…"
                                    : law.summary();
                    lawList.append(" | ").append(sum);
                }
                lawList.append("\n");
            }
            String prompt = """
                    Ты — правовой аналитик РК. Определи, какие из перечисленных нормативных актов \
                    ДЕЙСТВИТЕЛЬНО применимы к следующей ситуации. Выведи ТОЛЬКО JSON-массив кодов применимых законов.
                    Если ни один не подходит — выведи [].
                    Не добавляй пояснений.
                    
                    Ситуация: %s
                    
                    Нормативные акты:
                    %s
                    
                    Ответ (только JSON):""".formatted(snippet, lawList);
            String response = legalQueryClient
                    .prompt()
                    .user(prompt)
                    .call()
                    .content();
            List<String> relevant = parseJsonStringArray(response);
            log.info(
                    "LLM relevance filter: {} candidates → {} relevant",
                    candidates.size(),
                    relevant.size()
            );
            return relevant;
        } catch (Exception e) {
            log.debug(
                    "LLM relevance filter failed, skipping filter: {}",
                    e.getMessage()
            );
            return candidates.keySet().stream().toList();
        }
    }

    private static List<String> parseJsonStringArray(String response) {
        if (response == null || response.isBlank()) return List.of();
        Matcher m = JSON_ARRAY_PATTERN.matcher(response);
        if (!m.find()) {
            int start = response.indexOf('[');
            int end = response.lastIndexOf(']');
            if (start < 0 || end <= start) return List.of();
            response = response.substring(start, end + 1);
        } else {
            response = m.group();
        }
        List<String> result = new ArrayList<>();
        Pattern item = Pattern.compile("\"([^\"]+)\"");
        Matcher im = item.matcher(response);
        while (im.find()) {
            String s = im.group(1).trim();
            if (!s.isBlank()) result.add(s);
        }
        return result;
    }

    private static List<String> buildKeywordQueries(String text) {
        LinkedHashSet<String> queries = new LinkedHashSet<>();
        String t = text.toLowerCase(Locale.ROOT);

        String[][] markers = {
                {"опьянени", "состояние опьянения"},
                {"алкогол", "алкогольное опьянение"},
                {"нетрезв", "нетрезвое состояние"},
                {"халатност", "халатность"},
                {"неисполнен", "неисполнение обязанностей"},
                {"безопасност", "безопасность"},
                {"телесн", "телесные повреждения"},
                {"вред здоров", "причинение вреда здоровью"},
                {"ушиб", "телесные повреждения"},
                {"травм", "телесные повреждения"},
                {"увеч", "телесные повреждения"},
                {"несовершеннолет", "несовершеннолетний"},
                {"ребен", "защита ребенка"},
                {"малолет", "малолетний"},
                {"общественн", "общественный порядок"},
                {"мелк", "мелкое хулиганство"},
                {"хулиганств", "хулиганство"},
                {"угрожа", "угроза"},
                {"вымогат", "вымогательство"},
                {"мошен", "мошенничество"},
                {"оскорб", "оскорбление"},
                {"клевет", "клевета"},
                {"ходатайств", "ГПК отложение заседания"},
                {"отложен", "отложение судебного заседания"},
                {"перенос заседан", "отложение судебного заседания"},
                {"суд заседан", "ГПК судебное заседание"},
                {"подсудност", "подсудность"},
                {"арбитраж", "арбитраж"},
                {"исковое заявлен", "ГПК исковое заявление"},
                {"смэс", "ГПК экономический суд"},
                {"потребител", "защита прав потребителей"},
                {"возмещен", "возмещение вреда"},
                {"моральн", "моральный вред"},
                {"неустойк", "неустойка"},
                {"трудово", "трудовой договор"},
                {"работник", "труд"},
                {"командировк", "трудовой кодекс командировка"},
                {"оказани", "оказание услуг"},
                {"поставк", "поставка товаров"},
                {"аренд", "аренда"},
                {"подряд", "подряд"},
                {"купли-продаж", "купля-продажа"},
                {"займ", "займ"},
                {"кредит", "кредит"},
                {"налог", "налог"},
                {"акционерн", "акционерное общество"},
                {"товарищество", "товарищество"},
                {"страхов", "страхование"},
                {"наслед", "наследование"},
                {"брак", "семейное право"},
                {"уголовн", "уголовный кодекс"},
                {"гражданск", "гражданский кодекс"},
                {"исковая давность", "исковая давность"},
                {"договор", "договор"},
                {"штраф", "штраф"},
                {"пени", "пени"},
                {"спор", "разрешение споров"},
                {"претензи", "претензия"},
                {"конфиденциальн", "конфиденциальность"},
                {"персональн", "персональные данные"},
                {"интеллектуальн", "интеллектуальная собственность"},
        };
        for (String[] m : markers) {
            if (t.contains(m[0])) queries.add(m[1]);
            if (queries.size() >= 12) break;
        }

        String[] lines = text.split("\\R+", 5);
        for (String ln : lines) {
            String s = ln.strip();
            if (
                    s.length() >= 8 &&
                            s.length() <= 80 &&
                            !s.matches("(?i).*\\d+.*")
            ) {
                queries.add(s);
                break;
            }
        }

        if (queries.isEmpty()) queries.add("ГК РК");
        return new ArrayList<>(queries);
    }
}
