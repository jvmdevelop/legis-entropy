package com.jvmd.lawservice.importer;

import com.jvmd.lawservice.model.kz.*;
import com.jvmd.lawservice.parser.KzLawParser;
import com.jvmd.lawservice.repository.KzLegislativeDocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Slf4j
@Component
@RequiredArgsConstructor
public class KzLawImporter {

    private final KzLegislativeDocumentRepository lawRepository;
    private final KzLawParser kzLawParser;

    @Transactional
    public void importSampleKzLaws() {
        log.info("Starting import of sample KZ laws...");

        Constitution constitution = importConstitution();
        log.info("Constitution imported: {}", constitution.getTitle());

        importCode("УК", "Уголовный Кодекс Республики Казахстан", "141-VI ЗРК");
        importCode("ГК", "Гражданский Кодекс Республики Казахстан", "997-I ЗРК");
        importCode("ПК", "Процессуальный Кодекс Республики Казахстан", "206-IV ЗРК");
        importCode("НК", "Налоговый Кодекс Республики Казахстан", "227-IV ЗРК");
        importCode("ТК", "Трудовой Кодекс Республики Казахстан", "251-V ЗРК");

        log.info("All 5 Codes imported");

        importSampleLaws();

        long totalCount = lawRepository.count();
        log.info("Import completed. Total legislative documents: {}", totalCount);
    }

    private Constitution importConstitution() {
        Constitution constitution = new Constitution();
        constitution.setTitle("Конституция Республики Казахстан");
        constitution.setCode("КРК");
        constitution.setDocumentNumber("3-П");
        constitution.setIssuedBy("Парламент Республики Казахстан");
        constitution.setDatePublished(LocalDate.of(1995, 10, 30));
        constitution.setIsActive(true);
        constitution.setVersion(4);

        String constText = """
                Статья 1. Республика Казахстан утверждается унитарным, светским, правовым государством с президентской формой правления.

                Статья 2. Суверенитет Республики распространяется на всю ее территорию.

                Статья 3. Государственная власть в Республике осуществляется на основе разделения на Президента, Парламент и Правительство.

                Статья 7. Государственным языком Республики Казахстан является казахский язык.

                Статья 14. Государство гарантирует каждому право на образование.
                """;

        kzLawParser.parseArticlesAndClauses(constitution, constText);

        return lawRepository.save(constitution);
    }

    private Code importCode(String code, String title, String number) {
        Code codeEntity = new Code();
        codeEntity.setTitle(title);
        codeEntity.setCode(code);
        codeEntity.setDocumentNumber(number);
        codeEntity.setIssuedBy("Парламент Республики Казахстан");
        codeEntity.setDatePublished(LocalDate.of(2000, 7, 16));
        codeEntity.setIsActive(true);
        codeEntity.setVersion(1);

        String codeText = generateCodeContent(code);
        kzLawParser.parseArticlesAndClauses(codeEntity, codeText);

        return lawRepository.save(codeEntity);
    }

    private void importSampleLaws() {
        SimpleLaw law1 = new SimpleLaw();
        law1.setTitle("Закон Республики Казахстан О защите прав потребителей");
        law1.setDocumentNumber("2-II ЗРК");
        law1.setIssuedBy("Парламент Республики Казахстан");
        law1.setDatePublished(LocalDate.of(1997, 7, 4));
        law1.setIsActive(true);

        String law1Text = """
                Статья 1. Основные положения
                1. Настоящий Закон регулирует отношения между потребителем и изготовителем товара.

                Статья 2. Права потребителя
                1. Потребитель имеет право на безопасность товара.
                2. Потребитель имеет право требовать полной информации о товаре.

                Статья 3. Ответственность изготовителя
                1. Изготовитель несет ответственность за вред, причиненный товаром.
                """;

        kzLawParser.parseArticlesAndClauses(law1, law1Text);
        lawRepository.save(law1);

        SimpleLaw law2 = new SimpleLaw();
        law2.setTitle("Закон Республики Казахстан О акционерных обществах");
        law2.setDocumentNumber("1-II ЗРК");
        law2.setIssuedBy("Парламент Республики Казахстан");
        law2.setDatePublished(LocalDate.of(2003, 10, 13));
        law2.setIsActive(true);

        String law2Text = """
                Статья 1. Основные положения
                1. Акционерное общество - это юридическое лицо, уставный капитал которого разделен на акции.

                Статья 2. Уставный капитал
                1. Минимальный уставный капитал устанавливается законодательством.

                Статья 3. Собрание акционеров
                1. Высшим органом управления является собрание акционеров.
                """;

        kzLawParser.parseArticlesAndClauses(law2, law2Text);
        lawRepository.save(law2);

        Regulation decree = new Regulation(Regulation.RegulationType.PRESIDENTIAL_DECREE);
        decree.setTitle("Указ Президента Республики Казахстан О мерах по реализации реформы");
        decree.setDocumentNumber("567");
        decree.setIssuedBy("Президент Республики Казахстан");
        decree.setDatePublished(LocalDate.of(2023, 6, 15));
        decree.setIsActive(true);

        String decreeText = """
                Статья 1. Цели и задачи
                1. Настоящий Указ направлен на реализацию важных реформ.

                Статья 2. Ответственные органы
                1. За реализацию несут ответственность министерства.
                """;

        kzLawParser.parseArticlesAndClauses(decree, decreeText);
        lawRepository.save(decree);

        log.info("Imported 3 sample laws and regulations");
    }

    private String generateCodeContent(String codeType) {
        return switch (codeType) {
            case "УК" -> """
                    Статья 1. Основные положения Уголовного Кодекса
                    1. Уголовное законодательство Республики Казахстан состоит из настоящего Кодекса.

                    Статья 2. Задачи Уголовного Кодекса
                    1. Задачи - охрана прав и свобод человека и гражданина от преступных посягательств.

                    Статья 145. Кража
                    1. Кража - тайное хищение чужого имущества.
                    2. Наказание - штраф до 5 млн тенге.

                    Статья 157. Грабеж
                    1. Грабеж - открытое хищение чужого имущества.
                    """;
            case "ГК" -> """
                    Статья 1. Основные положения
                    1. Гражданское законодательство основано на принципах равенства участников.

                    Статья 2. Физические и юридические лица
                    1. Участниками гражданских правоотношений могут быть физические и юридические лица.

                    Статья 389. Нотариальное удостоверение сделок
                    1. Сделки могут быть нотариально удостоверены.
                    2. Нотариальное удостоверение обязательно для сделок с недвижимостью.
                    """;
            case "ПК" -> """
                    Статья 1. Общие положения Процессуального Кодекса
                    1. Гражданское судопроизводство основано на принципе равенства сторон.

                    Статья 2. Суды
                    1. Судебная власть осуществляется судами общей юрисдикции и специализированными судами.
                    """;
            case "НК" -> """
                    Статья 1. Основные положения Налогового Кодекса
                    1. Налоговое законодательство регулирует отношения в области налогообложения.

                    Статья 2. Налоговые ставки
                    1. Налоговые ставки устанавливаются законодательством.
                    """;
            case "ТК" -> """
                    Статья 1. Основные положения Трудового Кодекса
                    1. Трудовое законодательство регулирует трудовые отношения.

                    Статья 2. Трудовой договор
                    1. Трудовой договор - соглашение между работником и работодателем.

                    Статья 15. Минимальный размер оплаты труда
                    1. Минимальный размер оплаты труда устанавливается государством.
                    """;
            default -> "Статья 1. Введение";
        };
    }
}
