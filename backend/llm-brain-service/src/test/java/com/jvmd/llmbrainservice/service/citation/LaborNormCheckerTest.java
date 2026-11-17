package com.jvmd.llmbrainservice.service.citation;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LaborNormCheckerTest {

    private final LaborNormChecker checker = new LaborNormChecker();

    @Test
    void findsShortVacationWrittenInWordsOnlyInLabourContracts() {
        String contract = """
                ТРУДОВОЙ ДОГОВОР
                3.1. Стороны устанавливают испытательный срок продолжительностью два (2) месяца.
                4.2. В соответствии со статьёй 93 ТК РК продолжительность рабочей недели не превышает сорока часов.
                6.1. В соответствии со статьёй 87 ТК РК Работнику предоставляется отпуск продолжительностью не менее двадцати одного календарного дня.
                """;
        List<LaborNormChecker.Violation> v = checker.check(contract);

        assertThat(v).singleElement().satisfies(x -> {
            assertThat(x.clause()).isEqualTo("6.1");
            assertThat(x.citedArticle()).isEqualTo("87");
            assertThat(x.correctArticle()).isEqualTo("88");
            assertThat(x.explanation()).contains("указано 21", "24 календарных");
        });
        assertThat(checker.check("Договор аренды: отпуск 10 календарных дней")).isEmpty();
    }

    @Test
    void flagsLongProbationAndLongWeek() {
        String contract = """
                Трудовой договор
                3.1. Испытательный срок — шесть месяцев.
                4.2. Продолжительность рабочей недели — 48 часов.
                """;
        assertThat(checker.check(contract)).extracting(LaborNormChecker.Violation::correctArticle)
                .containsExactly("36", "68");
    }

    @Test
    void parsesRussianNumerals() {
        assertThat(LaborNormChecker.lastNumber("не менее двадцати одного")).isEqualTo(21);
        assertThat(LaborNormChecker.lastNumber("двух (2)")).isEqualTo(2);
        assertThat(LaborNormChecker.lastNumber("восьми")).isEqualTo(8);
        assertThat(LaborNormChecker.lastNumber("сорока")).isEqualTo(40);
        assertThat(LaborNormChecker.lastNumber("трудовой договор")).isNull();
    }
}
