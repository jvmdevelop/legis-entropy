package com.jvmd.llmbrainservice.service.citation;

import com.jvmd.llmbrainservice.service.citation.ArticleReferenceExtractor.ArticleReference;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ArticleReferenceExtractorTest {

    private final ArticleReferenceExtractor extractor = new ArticleReferenceExtractor();
    private final RepealedActRegistry repealed = new RepealedActRegistry();

    private static final String CONTRACT = """
            3.1. В соответствии со статьёй 49 Трудового кодекса Республики Казахстан Стороны устанавливают испытательный срок.
            4.3. Перерыв не включается в рабочее время в соответствии со статьёй 95 Трудового кодекса Республики Казахстан.
            5.3. Оплата сверхурочной работы производится в размере, установленном ст. 114 ТК РК.
            7.1. Обязанности сторон определены статьями 22 и 23 ТК РК.
            9.1. Трудовой договор может быть расторгнут по основаниям подпункта 1) пункта 1 статьи 52 настоящего Кодекса.
            10.1. Вопросы разрешаются в соответствии с Законом Республики Казахстан от 10 декабря 1999 года № 493-I «О труде в Республике Казахстан».
            10.2. Работник вправе обратиться в суд в порядке, установленном Гражданским процессуальным кодексом Республики Казахстан.
            10.3. Споры рассматриваются в порядке статьи 172 Гражданского кодекса Республики Казахстан (Особенная часть).
            10.5. Отчисления — согласно статье 5 Закона Республики Казахстан «Об обязательном социальном страховании» № 405-II.
            """;

    @Test
    void resolvesCodesFromFullNamesAndAbbreviations() {
        List<ArticleReference> refs = extractor.extract(CONTRACT);

        assertThat(refs).extracting(ArticleReference::lawCode, ArticleReference::articleNumber)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("ТК РК", "49"),
                        org.assertj.core.groups.Tuple.tuple("ТК РК", "95"),
                        org.assertj.core.groups.Tuple.tuple("ТК РК", "114"),
                        org.assertj.core.groups.Tuple.tuple("ТК РК", "22"),
                        org.assertj.core.groups.Tuple.tuple("ТК РК", "23"),
                        org.assertj.core.groups.Tuple.tuple("ГК РК", "172"));
    }

    @Test
    void keepsClauseTextForEachReference() {
        ArticleReference first = extractor.extract(CONTRACT).getFirst();
        assertThat(first.clause()).startsWith("3.1.").contains("испытательный срок");
    }

    @Test
    void skipsSelfReferencesAndOtherActs() {
        assertThat(extractor.extract("согласно статье 52 настоящего Кодекса")).isEmpty();
        assertThat(extractor.extract("согласно статье 5 Закона РК «О персональных данных»")).isEmpty();
    }

    @Test
    void proceduralCodeWinsOverSubstantive() {
        assertThat(extractor.extract("ст. 160 Гражданского процессуального кодекса РК"))
                .extracting(ArticleReference::lawCode).containsExactly("ГПК РК");
    }

    @Test
    void findsRepealedActsByRegistrationNumber() {
        assertThat(repealed.find(CONTRACT))
                .extracting(m -> m.act().number())
                .containsExactly("493-I", "405-II");
        assertThat(repealed.find("Закон от 20.06.1997 № 263-І")).hasSize(1);
        assertThat(repealed.find("Трудовой кодекс от 23.11.2015 № 414-V")).isEmpty();
    }
}
