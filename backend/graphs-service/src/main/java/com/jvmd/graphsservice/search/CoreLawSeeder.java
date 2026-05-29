package com.jvmd.graphsservice.search;

import com.jvmd.graphsservice.model.Law;
import com.jvmd.graphsservice.repository.LawRepository;
import java.util.List;
import java.util.concurrent.BlockingDeque;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@Order(10)
public class CoreLawSeeder {

    private final LawRepository lawRepository;
    private final LawIndexService lawIndexService;
    private final BlockingDeque<String> pathQueue;

    public CoreLawSeeder(
        LawRepository lawRepository,
        LawIndexService lawIndexService,
        @Qualifier("graphKzPathQueue") BlockingDeque<String> pathQueue
    ) {
        this.lawRepository = lawRepository;
        this.lawIndexService = lawIndexService;
        this.pathQueue = pathQueue;
    }

    private sealed interface SeedUrl permits ZanGovUrl, AdiletUrl {
        String toUrl();
    }

    private record ZanGovUrl(int id) implements SeedUrl {
        public String toUrl() {
            return "https://zan.gov.kz/client/#!/doc/" + id + "/rus";
        }
    }

    private record AdiletUrl(String code) implements SeedUrl {
        public String toUrl() {
            return "https://adilet.zan.kz/rus/docs/" + code;
        }
    }

    private record Seed(
        String code,
        String citationCode,
        String title,
        String type,
        String subjectArea,
        String legalForce,
        SeedUrl url
    ) {}

    private static final List<Seed> CORE_LAWS = List.of(
        new Seed(
            "ТК РК",
            "ТК РК",
            "Трудовой кодекс Республики Казахстан",
            "Кодекс",
            "трудовой договор работник работодатель увольнение расторжение трудового договора " +
                "заработная плата зарплата задержка выплаты отпуск больничный сверхурочная работа " +
                "переработка премия выходное пособие дисциплинарное взыскание безработный " +
                "испытательный срок перевод на другую должность",
            "Кодекс",
            new ZanGovUrl(95666)
        ),
        new Seed(
            "ГК РК",
            "ГК РК",
            "Гражданский кодекс Республики Казахстан",
            "Кодекс",
            "договор обязательство сделка купля-продажа аренда займ кредит поставка подряд " +
                "возмездное оказание услуг неустойка пени исковая давность возмещение ущерба " +
                "собственность право собственности наследование дарение залог поручительство",
            "Кодекс",
            new ZanGovUrl(879)
        ),
        new Seed(
            "УК РК",
            "УК РК",
            "Уголовный кодекс Республики Казахстан",
            "Кодекс",
            "уголовное преступление наказание лишение свободы штраф угроза вымогательство " +
                "мошенничество кража грабёж разбой побои насилие шантаж принуждение клевета " +
                "оскорбление взятка коррупция хулиганство",
            "Кодекс",
            new ZanGovUrl(81097)
        ),
        new Seed(
            "ГПК РК",
            "ГПК РК",
            "Гражданский процессуальный кодекс Республики Казахстан",
            "Кодекс",
            "судопроизводство иск исковое заявление ходатайство отложение судебного заседания " +
                "перенос заседания СМЭС специализированный межрайонный экономический суд подсудность " +
                "апелляция кассация подача иска истец ответчик представитель судебное решение " +
                "обеспечительные меры судебный приказ мировое соглашение обжалование",
            "Кодекс",
            new ZanGovUrl(95109)
        ),
        new Seed(
            "КоАП РК",
            "КоАП РК",
            "Кодекс Республики Казахстан об административных правонарушениях",
            "Кодекс",
            "административное правонарушение административный штраф предупреждение " +
                "административный арест лишение прав мелкое хулиганство нарушение ПДД " +
                "нарушение трудового законодательства санитарные нормы",
            "Кодекс",
            new ZanGovUrl(81245)
        ),
        new Seed(
            "УПК РК",
            "УПК РК",
            "Уголовно-процессуальный кодекс Республики Казахстан",
            "Кодекс",
            "уголовное дело следствие дознание подозреваемый обвиняемый арест задержание " +
                "прокурор защитник адвокат следователь допрос обыск выемка " +
                "предварительное следствие судебное разбирательство приговор",
            "Кодекс",
            new ZanGovUrl(81164)
        ),
        new Seed(
            "НК РК",
            "НК РК",
            "Кодекс Республики Казахстан о налогах и других обязательных платежах в бюджет (Налоговый кодекс)",
            "Кодекс",
            "налог НДС КПН ИПН налогообложение налоговая декларация налоговая проверка " +
                "уклонение от уплаты налогов налоговый агент социальный налог акциз",
            "Кодекс",
            new ZanGovUrl(212543)
        ),
        new Seed(
            "СК РК",
            "СК РК",
            "Кодекс Республики Казахстан «О браке (супружестве) и семье»",
            "Кодекс",
            "брак развод алименты дети усыновление опека попечительство раздел имущества " +
                "супруги семья родители родительские права лишение родительских прав",
            "Кодекс",
            new ZanGovUrl(62568)
        ),
        new Seed(
            "ЗК РК",
            "ЗК РК",
            "Земельный кодекс Республики Казахстан",
            "Кодекс",
            "земля земельный участок право на землю аренда земли покупка земли " +
                "сельскохозяйственные угодья изъятие земельного участка",
            "Кодекс",
            new ZanGovUrl(15924)
        ),
        new Seed(
            "ПК РК",
            "ПК РК",
            "Предпринимательский кодекс Республики Казахстан",
            "Кодекс",
            "предпринимательство бизнес лицензирование регулирование проверки " +
                "малый средний крупный бизнес государственный контроль",
            "Кодекс",
            new ZanGovUrl(94981)
        ),
        new Seed(
            "ЭК РК",
            "ЭК РК",
            "Экологический кодекс Республики Казахстан",
            "Кодекс",
            "экология охрана природы природопользование экологический ущерб " +
                "загрязнение отходы экологическая экспертиза",
            "Кодекс",
            new ZanGovUrl(151312)
        ),
        new Seed(
            "ВК РК",
            "ВК РК",
            "Водный кодекс Республики Казахстан",
            "Кодекс",
            "вода водные ресурсы водопользование водный объект загрязнение воды",
            "Кодекс",
            new ZanGovUrl(16225)
        ),
        new Seed(
            "Конституция РК",
            "Конституция РК",
            "Конституция Республики Казахстан",
            "Конституция",
            "конституционные права свободы граждан право на жизнь право на труд " +
                "право на образование право на жильё право на справедливый суд",
            "Конституция",
            new ZanGovUrl(1)
        ),
        new Seed(
            "ЖК РК",
            "ЖК РК",
            "Закон Республики Казахстан «О жилищных отношениях»",
            "Закон",
            "жильё квартира аренда жилья выселение КСК управление домом " +
                "коммунальные услуги приватизация жилищные права",
            "Закон",
            new AdiletUrl("Z970000094_")
        ),
        new Seed(
            "Закон РК о потребителях",
            "Закон РК о потребителях",
            "Закон Республики Казахстан «О защите прав потребителей»",
            "Закон",
            "защита прав потребителей претензия возврат товара ненадлежащее качество гарантия",
            "Закон",
            new AdiletUrl("Z100000274_")
        ),
        new Seed(
            "Закон РК о персданных",
            "Закон РК о персданных",
            "Закон Республики Казахстан «О персональных данных и их защите»",
            "Закон",
            "персональные данные конфиденциальность обработка данных согласие утечка данных",
            "Закон",
            new AdiletUrl("Z1300000094")
        ),
        new Seed(
            "Закон РК о ТОО",
            "Закон РК о ТОО",
            "Закон Республики Казахстан «О товариществах с ограниченной и дополнительной ответственностью»",
            "Закон",
            "ТОО товарищество уставный капитал участники директор ликвидация реорганизация",
            "Закон",
            new AdiletUrl("Z980000220_")
        ),
        new Seed(
            "Закон РК об АО",
            "Закон РК об АО",
            "Закон Республики Казахстан «Об акционерных обществах»",
            "Закон",
            "акционерное общество акции акционеры совет директоров дивиденды IPO",
            "Закон",
            new AdiletUrl("Z030000415_")
        ),
        new Seed(
            "Закон РК о банкротстве",
            "Закон РК о банкротстве",
            "Закон Республики Казахстан «О реабилитации и банкротстве»",
            "Закон",
            "банкротство реабилитация несостоятельность должник кредитор конкурсная масса",
            "Закон",
            new AdiletUrl("Z1400000176")
        )
    );

    @EventListener(ApplicationReadyEvent.class)
    public void seed() {
        int seeded = 0;
        int queued = 0;

        for (Seed s : CORE_LAWS) {
            Law law = lawRepository
                .findByCodeAndCountry(s.code(), "RK")
                .orElseGet(Law::new);
            law.setCode(s.code());
            law.setCitationCode(s.citationCode());
            law.setTitle(s.title());
            law.setType(s.type());
            law.setSubjectArea(s.subjectArea());
            law.setLegalForce(s.legalForce());
            law.setCountry("RK");
            law.setStatus("ACTIVE");
            law.setSource(
                com.jvmd.graphsservice.model.ProvenanceSource.USER_MANUAL
            );
            law.setExtractedBy("core-seeder");
            law.setRecordedAt(java.time.LocalDateTime.now());
            Law saved = lawRepository.save(law);
            lawIndexService.index(saved);
            seeded++;

            String url = s.url().toUrl();
            if (!pathQueue.contains(url)) {
                pathQueue.addFirst(url);
                queued++;
                log.debug("Queued (priority) {}: {}", s.code(), url);
            }
        }

        String gkSpecialUrl = new ZanGovUrl(3559).toUrl();
        if (!pathQueue.contains(gkSpecialUrl)) {
            pathQueue.addFirst(gkSpecialUrl);
            queued++;
        }

        log.info(
            "CoreLawSeeder: seeded {} law stubs, queued {} URLs at front of parser queue",
            seeded,
            queued
        );
    }
}
