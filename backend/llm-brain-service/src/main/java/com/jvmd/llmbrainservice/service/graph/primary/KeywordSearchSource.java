package com.jvmd.llmbrainservice.service.graph.primary;

import com.jvmd.llmbrainservice.client.GraphServiceClient;
import com.jvmd.llmbrainservice.client.GraphServiceClient.LawInfo;
import com.jvmd.llmbrainservice.service.graph.LawSignalFilter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class KeywordSearchSource implements PrimaryLawSource {

    private final GraphServiceClient graphServiceClient;

    @Override
    public int order() {
        return 20;
    }

    @Override
    public String name() {
        return "keyword-search";
    }

    @Override
    public boolean shouldRun(PrimaryLawContext ctx) {
        return ctx.already().isEmpty();
    }

    @Override
    public List<LawInfo> collect(PrimaryLawContext ctx) {
        List<LawInfo> out = new ArrayList<>();
        for (String q : buildSearchQueries(ctx.docText())) {
            try {
                for (LawInfo law : graphServiceClient.searchLaws(
                    q,
                    ctx.country()
                )) {
                    if (law.getCode() == null) continue;
                    if (LawSignalFilter.isLowSignal(law)) continue;
                    out.add(law);
                }
            } catch (Exception e) {
                log.debug("searchLaws('{}') failed: {}", q, e.getMessage());
            }
        }
        return out;
    }

    private static List<String> buildSearchQueries(String text) {
        LinkedHashSet<String> queries = new LinkedHashSet<>();
        if (text == null || text.isBlank()) return List.of("договор");
        String t = text.toLowerCase(Locale.ROOT);

        String[][] markers = {
            { "ходатайств", "ГПК РК отложение судебного заседания" },
            { "смэс", "ГПК РК экономический суд" },
            { "заседани", "ГПК РК судебное заседание" },
            { "отложен", "ГПК РК отложение заседания" },
            { "перенос", "ГПК РК отложение заседания" },
            { "иск", "ГПК РК исковое заявление" },
            { "подсудност", "подсудность" },
            { "арбитраж", "арбитраж" },
            { "апелляц", "ГПК РК апелляция" },
            { "кассац", "ГПК РК кассация" },
            { "возмездного оказания услуг", "возмездное оказание услуг" },
            { "оказани", "оказание услуг" },
            { "договор", "договор" },
            { "поставк", "поставка" },
            { "аренд", "аренда" },
            { "подряд", "подряд" },
            { "купли-продаж", "купля-продажа" },
            { "займ", "займ" },
            { "кредит", "кредит" },
            { "акционерн", "акционерное общество" },
            { "товарищество", "товарищество" },
            { "страхов", "страхование" },
            { "наслед", "наследование" },
            { "брак", "семейное право" },
            { "исковая давность", "исковая давность" },
            { "неустойк", "неустойка" },
            { "пени", "пени" },
            { "спор", "разрешение споров" },
            { "претензи", "претензия" },
            { "подсудност", "подсудность" },
            { "арбитраж", "арбитраж" },
            { "конфиденциальн", "конфиденциальность" },
            { "персональн", "персональные данные" },
            { "интеллектуальн", "интеллектуальная собственность" },
            { "авторск", "авторское право" },
            { "товарн", "товарный знак" },
            { "программ", "программное обеспечение" },
            { "налог", "налог" },
            { "трудово", "трудовой кодекс" },
            { "работник", "трудовой кодекс" },
            { "работодател", "трудовой кодекс" },
            { "увол", "трудовой кодекс увольнение" },
            { "безработн", "трудовой кодекс" },
            { "работать бесплатно", "трудовой кодекс оплата труда" },
            { "бесплатно", "трудовой кодекс оплата труда" },
            { "зарплат", "трудовой кодекс заработная плата" },
            { "заработн", "трудовой кодекс заработная плата" },
            { "премии", "трудовой кодекс премии" },
            { "переработк", "трудовой кодекс сверхурочная" },
            { "выходн", "трудовой кодекс отдых" },
            { "отпуск", "трудовой кодекс отпуск" },
            { "уголовн", "уголовный кодекс" },
            { "гражданск", "гражданский кодекс" },
            { "угроз", "уголовный кодекс угроза" },
            { "вымогат", "уголовный кодекс вымогательство" },
            { "шантаж", "уголовный кодекс вымогательство" },
            { "принужд", "уголовный кодекс принуждение" },
            { "оскорб", "административный кодекс оскорбление" },
            { "клевет", "защита чести и достоинства" },
            { "мошенн", "уголовный кодекс мошенничество" },
            { "обман", "уголовный кодекс мошенничество" },
            { "побои", "уголовный кодекс побои" },
            { "насили", "уголовный кодекс насилие" },
            { "штраф", "административный штраф" },
        };
        for (String[] m : markers) {
            if (t.contains(m[0])) queries.add(m[1]);
            if (queries.size() >= 12) break;
        }
        if (queries.isEmpty()) queries.add("ГК РК");
        return new ArrayList<>(queries);
    }
}
