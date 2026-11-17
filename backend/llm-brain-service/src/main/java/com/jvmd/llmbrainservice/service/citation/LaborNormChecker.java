package com.jvmd.llmbrainservice.service.citation;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class LaborNormChecker {

    public record Violation(String clause, String citedArticle, String correctArticle, String explanation) {}

    private record Rule(Pattern line, Pattern unit, boolean minimum, int limit, String article, String what) {}

    private static final List<Rule> RULES = List.of(
            new Rule(Pattern.compile("(?iu)отпуск"), Pattern.compile("(?iu)календарн"), true, 24, "88",
                    "основной оплачиваемый ежегодный отпуск по ст. 88 ТК РК — не менее 24 календарных дней"),
            new Rule(Pattern.compile("(?iu)испытательн"), Pattern.compile("(?iu)месяц"), false, 3, "36",
                    "испытательный срок по ст. 36 ТК РК — не более трёх месяцев (для руководителей — до шести)"),
            new Rule(Pattern.compile("(?iu)рабоч\\p{L}*\\s+(?:недел|времен)"), Pattern.compile("(?iu)час"), false, 40, "68",
                    "нормальная продолжительность рабочего времени по ст. 68 ТК РК — не более 40 часов в неделю")
    );

    private static final Pattern CLAUSE_NUMBER = Pattern.compile("^\\s*(\\d+(?:\\.\\d+)+)");
    private static final Pattern CITED = Pattern.compile("(?iu)(?:стать\\p{L}*|ст\\.)\\s*(\\d+)");

    public List<Violation> check(String text) {
        if (text == null || !text.toLowerCase(Locale.ROOT).contains("трудов")) return List.of();
        List<Violation> found = new ArrayList<>();
        for (String line : text.split("\\R")) {
            for (Rule rule : RULES) {
                if (!rule.line().matcher(line).find()) continue;
                Matcher unit = rule.unit().matcher(line);
                if (!unit.find()) continue;
                Integer value = lastNumber(line.substring(Math.max(0, unit.start() - 45), unit.start()));
                if (value == null) continue;
                boolean broken = rule.minimum() ? value < rule.limit() : value > rule.limit();
                if (!broken) continue;
                Matcher clause = CLAUSE_NUMBER.matcher(line);
                Matcher cited = CITED.matcher(line);
                found.add(new Violation(
                        clause.find() ? clause.group(1) : "",
                        cited.find() ? cited.group(1) : "",
                        rule.article(),
                        "В договоре указано " + value + ", а " + rule.what() + "."));
            }
        }
        return found;
    }


    private static final String[][] TENS = {{"двадцат", "20"}, {"тридцат", "30"}, {"сорок", "40"}, {"пятьдесят", "50"}};
    private static final String[][] TEENS = {
            {"одиннадцат", "11"}, {"двенадцат", "12"}, {"тринадцат", "13"}, {"четырнадцат", "14"}, {"пятнадцат", "15"},
            {"шестнадцат", "16"}, {"семнадцат", "17"}, {"восемнадцат", "18"}, {"девятнадцат", "19"}, {"десят", "10"}};
    private static final java.util.Map<String, Integer> UNIT_WORDS = new java.util.HashMap<>();
    static {
        String[][] forms = {
                {"1", "один одна одно одного одной одному одним одну"}, {"2", "два две двух двум двумя"},
                {"3", "три трех трем тремя"}, {"4", "четыре четырех четырем"}, {"5", "пять пяти"},
                {"6", "шесть шести"}, {"7", "семь семи"}, {"8", "восемь восьми"}, {"9", "девять девяти"}};
        for (String[] f : forms) for (String w : f[1].split(" ")) UNIT_WORDS.put(w, Integer.parseInt(f[0]));
    }
    private static final Pattern TOKEN = Pattern.compile("\\d+|\\p{L}+");

    static Integer lastNumber(String window) {
        Integer last = null;
        int run = -1;
        Matcher m = TOKEN.matcher(window.toLowerCase(Locale.ROOT).replace('ё', 'е'));
        while (m.find()) {
            String token = m.group();
            if (Character.isDigit(token.charAt(0))) {
                last = Integer.parseInt(token);
                run = -1;
                continue;
            }
            Integer word = wordValue(token);
            if (word == null) {
                run = -1;
                continue;
            }
            run = (run >= 20 && run % 10 == 0 && word < 10) ? run + word : word;
            last = run;
        }
        return last;
    }

    private static Integer wordValue(String token) {
        for (String[][] table : new String[][][]{TENS, TEENS}) {
            for (String[] e : table) if (token.startsWith(e[0])) return Integer.parseInt(e[1]);
        }
        return UNIT_WORDS.get(token);
    }
}
