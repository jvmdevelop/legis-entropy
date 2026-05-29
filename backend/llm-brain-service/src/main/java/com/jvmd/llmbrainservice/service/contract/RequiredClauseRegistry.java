package com.jvmd.llmbrainservice.service.contract;

import java.util.List;
import java.util.Map;

final class RequiredClauseRegistry {

    record RequiredClause(String name, String lawBasis, List<String> keywords) {}

    private RequiredClauseRegistry() {}

    private static final Map<String, List<RequiredClause>> CLAUSES = Map.of(
            "трудовой", List.of(
                    new RequiredClause("Должность / трудовая функция", "ст. 28 ТК РК",
                            List.of("должност", "функц", "обязанност")),
                    new RequiredClause("Размер и порядок выплаты заработной платы", "ст. 28 ТК РК",
                            List.of("заработн", "оклад", "зарплат", "вознаграждени")),
                    new RequiredClause("Место работы", "ст. 28 ТК РК",
                            List.of("место работ", "адрес", "офис", "предприятие")),
                    new RequiredClause("Дата начала работы", "ст. 28 ТК РК",
                            List.of("дата начал", "приступить", "с «", "с \"")),
                    new RequiredClause("Режим рабочего времени", "ст. 68 ТК РК",
                            List.of("режим", "рабочее время", "рабочий день", "график"))),
            "аренды", List.of(
                    new RequiredClause("Предмет аренды (описание имущества)", "ст. 541 ГК РК",
                            List.of("предмет", "имущество", "объект", "помещени")),
                    new RequiredClause("Размер арендной платы", "ст. 546 ГК РК",
                            List.of("арендн", "плата", "оплата", "сумма")),
                    new RequiredClause("Срок аренды", "ст. 541 ГК РК",
                            List.of("срок", "период", "продолжительност")),
                    new RequiredClause("Порядок передачи имущества", "ст. 541 ГК РК",
                            List.of("передач", "акт приема", "акт-приема"))),
            "услуг", List.of(
                    new RequiredClause("Предмет договора (перечень услуг)", "ст. 683 ГК РК",
                            List.of("предмет", "перечень услуг", "виды услуг", "оказывает")),
                    new RequiredClause("Стоимость услуг и порядок оплаты", "ст. 685 ГК РК",
                            List.of("стоимост", "цена", "оплат", "вознаграждени")),
                    new RequiredClause("Сроки оказания услуг", "ст. 683 ГК РК",
                            List.of("срок", "период", "в течени")),
                    new RequiredClause("Порядок приёмки услуг", "ст. 686 ГК РК",
                            List.of("приемк", "сдач", "акт выполн"))),
            "поставки", List.of(
                    new RequiredClause("Наименование и количество товара", "ст. 406 ГК РК",
                            List.of("наименовани", "количество", "объем", "товар")),
                    new RequiredClause("Цена и порядок оплаты", "ст. 406 ГК РК",
                            List.of("цена", "стоимост", "оплат")),
                    new RequiredClause("Срок поставки", "ст. 406 ГК РК",
                            List.of("срок поставк", "срок отгрузк", "дата доставк")),
                    new RequiredClause("Условия о качестве товара", "ст. 422 ГК РК",
                            List.of("качество", "стандарт", "гост", "технические условия")))
    );

    private static final List<RequiredClause> DEFAULT = List.of(
            new RequiredClause("Предмет договора", "ст. 393 ГК РК",
                    List.of("предмет", "цель договора")),
            new RequiredClause("Права и обязанности сторон", "ст. 393 ГК РК",
                    List.of("права", "обязанност", "стороны обязуются")),
            new RequiredClause("Порядок расторжения договора", "ст. 401 ГК РК",
                    List.of("расторжени", "прекращени", "отказ от договора"))
    );

    static List<RequiredClause> forType(String contractType) {
        return CLAUSES.getOrDefault(contractType, DEFAULT);
    }
}
