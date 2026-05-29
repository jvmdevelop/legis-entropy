package com.jvmd.dms.voice.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class VoiceAnalysisService {

    public record Classification(
        String kind,
        String severity,
        String summary,
        List<String> quotes,
        List<SuggestedArticle> articles
    ) {}

    public record SuggestedArticle(
        String lawCode,
        String number,
        String reason
    ) {}

    private static final Map<Pattern, BucketRule> RULES = bucketRules();

    public Classification classify(
        String transcript,
        List<String> speakerLabels
    ) {
        String text =
            transcript == null ? "" : transcript.toLowerCase(Locale.ROOT);
        List<String> quotes = new ArrayList<>();
        List<SuggestedArticle> articles = new ArrayList<>();
        String kind = "OTHER";
        String severity = "LOW";

        for (Map.Entry<Pattern, BucketRule> e : RULES.entrySet()) {
            Matcher m = e.getKey().matcher(text);
            while (m.find()) {
                int start = Math.max(0, m.start() - 40);
                int end = Math.min(text.length(), m.end() + 80);
                quotes.add(text.substring(start, end).trim());
                if (
                    severityRank(e.getValue().severity()) >
                    severityRank(severity)
                ) {
                    severity = e.getValue().severity();
                    kind = e.getValue().kind();
                }
                articles.addAll(e.getValue().articles());
            }
        }

        if (quotes.isEmpty()) {
            log.debug(
                "Voice analysis: no rule matched, marking transcript as OTHER"
            );
        }

        String summary = quotes.isEmpty()
            ? "Запись не содержит явных юридически значимых триггеров. Рекомендован ручной разбор."
            : "Обнаружено " +
              quotes.size() +
              " триггер(ов) категории " +
              kind +
              (speakerLabels == null || speakerLabels.isEmpty()
                  ? ""
                  : ". Спикеры: " + String.join(", ", speakerLabels));

        return new Classification(kind, severity, summary, quotes, articles);
    }

    private static int severityRank(String s) {
        return switch (s == null ? "" : s.toUpperCase(Locale.ROOT)) {
            case "CRITICAL" -> 4;
            case "HIGH" -> 3;
            case "MEDIUM" -> 2;
            case "LOW" -> 1;
            default -> 0;
        };
    }

    private record BucketRule(
        String kind,
        String severity,
        List<SuggestedArticle> articles
    ) {}

    private static Map<Pattern, BucketRule> bucketRules() {
        Map<Pattern, BucketRule> r = new LinkedHashMap<>();
        r.put(
            Pattern.compile(
                "(убь[ёе]м|убью|зарежу|порешу|сожгу|пристрелю|сломаю|изобью|порешим|закопаю)",
                Pattern.UNICODE_CHARACTER_CLASS
            ),
            new BucketRule(
                "THREAT",
                "CRITICAL",
                List.of(
                    new SuggestedArticle(
                        "УК РК",
                        "115",
                        "Прямая угроза жизни/здоровью — ст. 115 УК РК (Угроза)"
                    )
                )
            )
        );

        r.put(
            Pattern.compile(
                "(угрожаю|если не отдашь|если не заплатишь|иначе будет хуже|" +
                    "иначе пожалеешь|иначе уволю|или уволим|потребую|выколочу|" +
                    "должен будешь|вернёшь с процентами)",
                Pattern.UNICODE_CHARACTER_CLASS
            ),
            new BucketRule(
                "EXTORTION",
                "HIGH",
                List.of(
                    new SuggestedArticle(
                        "УК РК",
                        "194",
                        "Условная угроза с требованием — ст. 194 УК РК (Вымогательство)"
                    ),
                    new SuggestedArticle(
                        "УК РК",
                        "115",
                        "Параллельно может квалифицироваться как угроза"
                    )
                )
            )
        );

        r.put(
            Pattern.compile(
                "(твоя жена|твой муж|твои дети|твоего ребёнк|твоего ребенк|" +
                    "семь[ёе] не поздоровится|до семьи доберусь)",
                Pattern.UNICODE_CHARACTER_CLASS
            ),
            new BucketRule(
                "THREAT",
                "HIGH",
                List.of(
                    new SuggestedArticle(
                        "УК РК",
                        "115",
                        "Угроза в адрес близких — ст. 115 УК РК"
                    ),
                    new SuggestedArticle(
                        "УК РК",
                        "112",
                        "Принуждение — ст. 112 УК РК (если есть требование)"
                    )
                )
            )
        );

        r.put(
            Pattern.compile(
                "(увол(ю|им|ить|ен)|выгоню|выгоним|" +
                    "работать бесплатно|без зарплаты|не плач(у|им)|" +
                    "лишу премии|задержу зарплат|задерж(а|и)т зарплат|" +
                    "штраф из зарплаты|вычту из зарплаты|" +
                    "безработн|без работы останешься|сидеть без денег)",
                Pattern.UNICODE_CHARACTER_CLASS
            ),
            new BucketRule(
                "LABOR_ABUSE",
                "HIGH",
                List.of(
                    new SuggestedArticle(
                        "ТК РК",
                        "23",
                        "Основные права и обязанности работодателя — ст. 23 ТК РК"
                    ),
                    new SuggestedArticle(
                        "ТК РК",
                        "52",
                        "Основания прекращения трудового договора — ст. 52 ТК РК"
                    ),
                    new SuggestedArticle(
                        "ТК РК",
                        "113",
                        "Сроки и порядок выплаты заработной платы — ст. 113 ТК РК"
                    ),
                    new SuggestedArticle(
                        "УК РК",
                        "152",
                        "Нарушение законодательства о труде — ст. 152 УК РК"
                    )
                )
            )
        );

        r.put(
            Pattern.compile(
                "(обман|обманул(и|а)?|развод(или)?|кинул(и|а)?|" +
                    "не вернули деньги|не выполнил договор|схема)",
                Pattern.UNICODE_CHARACTER_CLASS
            ),
            new BucketRule(
                "FRAUD",
                "MEDIUM",
                List.of(
                    new SuggestedArticle(
                        "УК РК",
                        "190",
                        "Признаки мошеннических действий — ст. 190 УК РК (Мошенничество)"
                    )
                )
            )
        );

        r.put(
            Pattern.compile(
                "(оскорб|унижени(е|я)|нецензур|оскорбительн|матерится|обозвал)",
                Pattern.UNICODE_CHARACTER_CLASS
            ),
            new BucketRule(
                "INSULT",
                "MEDIUM",
                List.of(
                    new SuggestedArticle(
                        "КоАП РК",
                        "434",
                        "Возможное мелкое хулиганство/оскорбление — ст. 434 КоАП РК"
                    )
                )
            )
        );

        r.put(
            Pattern.compile(
                "(клевет|очерн|порочит|распускает слухи|распускают слухи)",
                Pattern.UNICODE_CHARACTER_CLASS
            ),
            new BucketRule(
                "DEFAMATION",
                "MEDIUM",
                List.of(
                    new SuggestedArticle(
                        "ГК РК",
                        "143",
                        "Защита чести/достоинства — ст. 143 ГК РК"
                    )
                )
            )
        );
        return r;
    }
}
