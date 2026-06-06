package com.jvmd.lawservice.model;

import lombok.Getter;

@Getter
public enum LawCategory {
    CONSTITUTIONAL("Конституционное право"),
    CIVIL("Гражданское право"),
    CRIMINAL("Уголовное право"),
    ADMINISTRATIVE("Административное право"),
    TAX("Налоговое право"),
    LABOR("Трудовое право"),
    FAMILY("Семейное право"),
    ENVIRONMENTAL("Экологическое право"),
    INTELLECTUAL_PROPERTY("Интеллектуальная собственность"),
    BUSINESS("Корпоративное право"),
    INTERNATIONAL("Международное право"),
    LAND("Земельное право"),
    HOUSING("Жилищное право"),
    SOCIAL("Социальное право"),
    HEALTH("Медицинское право"),
    EDUCATION("Образовательное право"),
    MILITARY("Военное право"),
    CUSTOMS("Таможенное право"),
    UNKNOWN("Неопределено");

    private final String description;

    LawCategory(String description) {
        this.description = description;
    }

    public static LawCategory fromTitle(String title) {
        if (title == null) return UNKNOWN;

        String lowerTitle = title.toLowerCase();

        if (lowerTitle.contains("конституц") || lowerTitle.contains("основной закон")) {
            return CONSTITUTIONAL;
        } else if (lowerTitle.contains("гражданск") || lowerTitle.contains("гк")) {
            return CIVIL;
        } else if (lowerTitle.contains("уголовн") || lowerTitle.contains("ук")) {
            return CRIMINAL;
        } else if (lowerTitle.contains("административ") || lowerTitle.contains("кодекс об административных")) {
            return ADMINISTRATIVE;
        } else if (lowerTitle.contains("налог") || lowerTitle.contains("нк")) {
            return TAX;
        } else if (lowerTitle.contains("труд") || lowerTitle.contains("тк")) {
            return LABOR;
        } else if (lowerTitle.contains("семейн") || lowerTitle.contains("ск")) {
            return FAMILY;
        } else if (lowerTitle.contains("эколог") || lowerTitle.contains("охрана окружающей")) {
            return ENVIRONMENTAL;
        } else if (lowerTitle.contains("интеллектуал") || lowerTitle.contains("авторск")) {
            return INTELLECTUAL_PROPERTY;
        } else if (lowerTitle.contains("корпоратив") || lowerTitle.contains("обществ") || lowerTitle.contains("юридическ")) {
            return BUSINESS;
        } else if (lowerTitle.contains("международ") || lowerTitle.contains("конвенц")) {
            return INTERNATIONAL;
        } else if (lowerTitle.contains("земельн") || lowerTitle.contains("зк")) {
            return LAND;
        } else if (lowerTitle.contains("жилищ") || lowerTitle.contains("жк")) {
            return HOUSING;
        } else if (lowerTitle.contains("социаль") || lowerTitle.contains("пенсион")) {
            return SOCIAL;
        } else if (lowerTitle.contains("медицин") || lowerTitle.contains("здравоохранен")) {
            return HEALTH;
        } else if (lowerTitle.contains("образован") || lowerTitle.contains("школ")) {
            return EDUCATION;
        } else if (lowerTitle.contains("военн") || lowerTitle.contains("оборона")) {
            return MILITARY;
        } else if (lowerTitle.contains("таможен") || lowerTitle.contains("тк тс")) {
            return CUSTOMS;
        }

        return UNKNOWN;
    }
}
