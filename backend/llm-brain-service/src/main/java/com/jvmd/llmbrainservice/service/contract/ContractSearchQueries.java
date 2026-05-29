package com.jvmd.llmbrainservice.service.contract;

import java.util.List;
import java.util.Map;

final class ContractSearchQueries {

    private ContractSearchQueries() {}

    private static final Map<String, List<String>> QUERIES = Map.of(
            "трудовой", List.of(
                    "трудовой договор условия найма зарплата испытательный срок",
                    "права и обязанности работника работодателя увольнение"),
            "аренды", List.of(
                    "договор аренды имущества арендная плата права арендатора",
                    "обязанности арендодателя состояние имущества расторжение аренды"),
            "поставки", List.of(
                    "договор поставки товара покупатель продавец цена оплата",
                    "условия поставки качество товара ответственность сторон"),
            "услуг", List.of(
                    "договор возмездного оказания услуг исполнитель заказчик оплата",
                    "приемка услуг ответственность за качество услуг"),
            "подряда", List.of(
                    "договор подряда подрядчик заказчик выполнение работ смета",
                    "приемка работ гарантии качества подрядные отношения")
    );

    private static final List<String> DEFAULT = List.of(
            "договор права и обязанности сторон ответственность расторжение",
            "гражданский кодекс договорные отношения обязательства"
    );

    static List<String> forType(String contractType) {
        return QUERIES.getOrDefault(contractType, DEFAULT);
    }
}
