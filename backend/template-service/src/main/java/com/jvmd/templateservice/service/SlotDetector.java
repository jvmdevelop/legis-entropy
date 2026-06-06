package com.jvmd.templateservice.service;

import com.jvmd.templateservice.model.Slot;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class SlotDetector {

    public record DetectionResult(String canonicalMarkdown, List<Slot> slots) {}

    private static final Pattern[] PATTERNS = {
        Pattern.compile("\\{\\{\\s*([a-zA-Z0-9_]+)\\s*}}"),
        Pattern.compile("\\[(?<label>[^\\[\\]\\n]{2,80})]"),
        Pattern.compile("<(?<label>[^<>\\n]{2,80})>"),
        Pattern.compile("_{5,}"),
        Pattern.compile("«\\s*_*\\s*»"),
    };

    private static final int CONTEXT_RADIUS = 120;

    public DetectionResult detect(String body) {
        if (body == null || body.isBlank()) return new DetectionResult("", List.of());

        Map<String, Slot> bySlotId = new LinkedHashMap<>();
        StringBuilder out = new StringBuilder(body.length() + 64);
        int cursor = 0;
        int sequence = 0;

        while (cursor < body.length()) {
            Match next = nextMatch(body, cursor);
            if (next == null) { out.append(body, cursor, body.length()); break; }
            out.append(body, cursor, next.start);

            String token;
            if (next.patternIndex == 0) {
                String id = next.matcher.group(1);
                token = "{{" + id + "}}";
                if (!bySlotId.containsKey(id)) {
                    bySlotId.put(id, buildSlot(id, humanise(id), "", body, next.start, next.end, sequence));
                }
            } else {
                String label = labelFromMatch(next);
                String id = ensureUniqueId(slugify(label, ++sequence), bySlotId);
                token = "{{" + id + "}}";
                bySlotId.put(id, buildSlot(id, label, inferContext(body, next.start, next.end), body, next.start, next.end, sequence));
            }
            out.append(token);
            cursor = next.end;
        }
        return new DetectionResult(out.toString(), new ArrayList<>(bySlotId.values()));
    }

    private record Match(int patternIndex, int start, int end, Matcher matcher) {}

    private Match nextMatch(String body, int from) {
        Match best = null;
        for (int i = 0; i < PATTERNS.length; i++) {
            Matcher m = PATTERNS[i].matcher(body);
            if (m.find(from)) {
                if (best == null || m.start() < best.start) best = new Match(i, m.start(), m.end(), m);
            }
        }
        return best;
    }

    private static String labelFromMatch(Match m) {
        try { return m.matcher.group("label").trim(); } catch (IllegalArgumentException ignored) { return "поле"; }
    }

    private Slot buildSlot(String id, String label, String contextText, String body, int start, int end, int order) {
        int pStart = Math.max(0, start - CONTEXT_RADIUS);
        int pEnd = Math.min(body.length(), end + CONTEXT_RADIUS);
        String preceding = body.substring(pStart, start).trim();
        String following = body.substring(end, pEnd).trim();
        Slot.Placement placement = Slot.Placement.builder().preceding(preceding).following(following).build();
        String type = inferType(label, preceding, following).name();
        boolean multiline = "MULTILINE".equals(type) || label.toLowerCase(Locale.ROOT).contains("обстоятельств")
                || label.toLowerCase(Locale.ROOT).contains("факт");
        return Slot.builder().id(id).label(label.isBlank() ? "Поле " + order : label)
                .context(contextText).placement(placement).type(type)
                .aiHint(defaultHint(type)).required(false).multiline(multiline).build();
    }

    private static String inferContext(String body, int start, int end) {
        return body.substring(Math.max(0, start - 40), Math.min(body.length(), end + 40)).replace('\n', ' ').strip();
    }

    private static Slot.SemanticType inferType(String label, String preceding, String following) {
        String lbl = label == null ? "" : label.toLowerCase(Locale.ROOT);
        if (lbl.contains("адрес") || lbl.contains("проживающ")) return Slot.SemanticType.ADDRESS;
        if (lbl.contains("тел.") || lbl.contains("телефон")) return Slot.SemanticType.PHONE;
        if (lbl.contains("e-mail") || lbl.contains("email") || lbl.contains("эл. почт")) return Slot.SemanticType.EMAIL;
        if (lbl.contains("дата") || lbl.contains("число")) return Slot.SemanticType.DATE;
        if (lbl.contains("сумма") || lbl.contains("тенге") || lbl.contains("руб") || lbl.contains("неустойк")) return Slot.SemanticType.AMOUNT;
        if (lbl.contains("статья") || lbl.contains("ук рк") || lbl.contains("гк рк") || lbl.contains("ст. ")) return Slot.SemanticType.ARTICLE_REF;
        if (lbl.contains("обстоятельств") || lbl.contains("факт") || lbl.contains("опишите") || lbl.contains("подробно") || lbl.contains("нарушение")) return Slot.SemanticType.MULTILINE;
        if (lbl.contains("фио") || lbl.contains("имя") || lbl.contains("заявител") || lbl.contains("истец") || lbl.contains("ответчик")) return Slot.SemanticType.NAME;
        String hay = (preceding + " " + following).toLowerCase(Locale.ROOT);
        if (hay.contains("20__") || hay.contains("«___»")) return Slot.SemanticType.DATE;
        if (hay.contains("тенге") || hay.contains("сумма")) return Slot.SemanticType.AMOUNT;
        return Slot.SemanticType.TEXT;
    }

    private static String defaultHint(String type) {
        return switch (type) {
            case "NAME" -> "Полное ФИО в именительном падеже";
            case "DATE" -> "Дата в формате ДД.ММ.ГГГГ";
            case "ADDRESS" -> "Полный адрес: индекс, страна, город, улица, дом";
            case "PHONE" -> "Контактный телефон в международном формате";
            case "AMOUNT" -> "Сумма прописью + цифрами + валюта";
            case "EMAIL" -> "Адрес электронной почты";
            case "ARTICLE_REF" -> "Ссылка на статью законодательства";
            case "MULTILINE" -> "Развёрнутое описание из 2–4 предложений";
            default -> "Кратко, по существу";
        };
    }

    private static String humanise(String id) {
        if (id == null || id.isBlank()) return "Поле";
        String[] parts = id.replace('_', ' ').split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (parts[i].isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            if (i == 0) { sb.append(Character.toUpperCase(parts[i].charAt(0))); if (parts[i].length() > 1) sb.append(parts[i].substring(1)); }
            else sb.append(parts[i]);
        }
        return sb.toString();
    }

    private static String slugify(String label, int seq) {
        if (label == null || label.isBlank()) return "field_" + seq;
        String base = label.toLowerCase(Locale.ROOT).replace('ё', 'е').replaceAll("[^a-zа-я0-9]+", "_").replaceAll("^_+|_+$", "");
        if (base.isEmpty()) return "field_" + seq;
        if (base.length() > 40) base = base.substring(0, 40);
        return base;
    }

    private static String ensureUniqueId(String base, Map<String, Slot> taken) {
        if (!taken.containsKey(base)) return base;
        int n = 2;
        while (taken.containsKey(base + "_" + n)) n++;
        return base + "_" + n;
    }
}
