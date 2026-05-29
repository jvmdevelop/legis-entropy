package com.jvmd.llmbrainservice.service.pipeline;

import com.jvmd.llmbrainservice.util.TextMatcher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
public class ProfessionClassifier {

    private static final List<String> FAMILY_TERMS = List.of(
            "развод", "алименты", "брак", "ребенок", "дети", "опека",
            "усыновление", "супруг", "родительские права", "забота", "отцовство",
            "материнство", "семейное право", "наследство", "опекун", "попечитель",
            "разрешение на брак", "раздел имущества", "свидание с ребенком"
    );

    private static final List<String> REAL_ESTATE_TERMS = List.of(
            "аренда", "недвижимость", "квартира", "земля", "ипотека",
            "договор купли-продажи", "дду", "регистрация права", "сервитут",
            "жилищное право", "собственность", "арендатор", "арендодатель",
            "залог", "наложение ареста", "земельный участок", "коммерческое помещение",
            "жилой фонд", "рента", "пожизненная рента", "безвозмездное пользование"
    );

    private static final List<String> CRIMINAL_TERMS = List.of(
            "уголовн", "преступление", "следствие", "обвинение", "приговор",
            "задержание", "арест", "кража", "мошенничество", "убийство",
            "изнасилование", "нападение", "грабеж", "вымогательство", "подделка",
            "дознание", "прокуратура", "адвокат", "защита", "судебное разбирательство",
            "штраф уголовный", "лишение свободы", "условное осуждение"
    );

    private static final List<String> CORPORATE_TERMS = List.of(
            "компания", "юридическое лицо", "ооо", "ао", "акционерное общество",
            "уставный капитал", "учредитель", "директор", "совет директоров",
            "устав", "ликвидация", "реорганизация", "слияние", "преобразование",
            "индивидуальный предприниматель", "ип", "корпоративное право",
            "выплата дивидендов", "рейдерство", "корпоративный конфликт"
    );

    private static final List<String> LABOR_TERMS = List.of(
            "трудовой договор", "трудовое право", "увольнение", "зарплата",
            "работодатель", "работник", "отпуск", "дисциплинарн", "трудовой кодекс",
            "трудовые споры", "коллективный договор", "охрана труда", "рабочий день",
            "сверхурочные", "переводы", "увольнение без причины", "страховка",
            "профсоюз", "трудовая инспекция", "спор о восстановлении на работе"
    );

    public ProfessionProfile classify(String message) {
        try {
            String normalized = TextMatcher.normalize(message);

            if (TextMatcher.containsAny(normalized, FAMILY_TERMS)) {
                return ProfessionProfile.FAMILY_LAWYER;
            }
            if (TextMatcher.containsAny(normalized, REAL_ESTATE_TERMS)) {
                return ProfessionProfile.REAL_ESTATE_LAWYER;
            }
            if (TextMatcher.containsAny(normalized, CRIMINAL_TERMS)) {
                return ProfessionProfile.CRIMINAL_LAWYER;
            }
            if (TextMatcher.containsAny(normalized, CORPORATE_TERMS)) {
                return ProfessionProfile.CORPORATE_LAWYER;
            }
            if (TextMatcher.containsAny(normalized, LABOR_TERMS)) {
                return ProfessionProfile.LABOR_LAWYER;
            }

            return ProfessionProfile.GENERAL_LAWYER;
        } catch (Exception e) {
            log.warn("Profession classification failed, using GENERAL: {}", e.getMessage());
            return ProfessionProfile.GENERAL_LAWYER;
        }
    }

}
