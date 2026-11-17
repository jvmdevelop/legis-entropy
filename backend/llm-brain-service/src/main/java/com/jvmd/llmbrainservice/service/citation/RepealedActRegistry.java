package com.jvmd.llmbrainservice.service.citation;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class RepealedActRegistry {

    public record RepealedAct(String number, String title, String status) {}

    public record RepealedMention(RepealedAct act, String clause) {}

    private static final Map<String, RepealedAct> ACTS = new LinkedHashMap<>();

    static {
        add("493-I", "Закон РК от 10 декабря 1999 года «О труде в Республике Казахстан»",
                "утратил силу с принятием Трудового кодекса РК 2007 года; сейчас действует Трудовой кодекс РК от 23.11.2015 № 414-V");
        add("251-III", "Трудовой кодекс РК от 15 мая 2007 года",
                "утратил силу; действует Трудовой кодекс РК от 23.11.2015 № 414-V");
        add("263-I", "Закон РК от 20 июня 1997 года «О пенсионном обеспечении в Республике Казахстан»",
                "утратил силу (заменён Законом РК от 21.06.2013 № 105-V, который, в свою очередь, заменён Социальным кодексом РК от 20.04.2023 № 224-VII)");
        add("105-V", "Закон РК от 21 июня 2013 года «О пенсионном обеспечении в Республике Казахстан»",
                "утратил силу с 1 июля 2023 года в связи с принятием Социального кодекса РК от 20.04.2023 № 224-VII");
        add("405-II", "Закон РК от 25 апреля 2003 года «Об обязательном социальном страховании»",
                "утратил силу с 1 июля 2023 года в связи с принятием Социального кодекса РК от 20.04.2023 № 224-VII");
    }

    private static void add(String number, String title, String status) {
        ACTS.put(normalize(number), new RepealedAct(number, title, status));
    }

    private static final Pattern ACT_NUMBER = Pattern.compile("(?iu)(?:№|N)\\s*(\\d+\\s*-\\s*[IVXІ]+)(?![\\p{L}])");

    public List<RepealedMention> find(String text) {
        if (text == null || text.isBlank()) return List.of();
        Map<String, RepealedMention> found = new LinkedHashMap<>();
        Matcher m = ACT_NUMBER.matcher(text);
        while (m.find()) {
            RepealedAct act = ACTS.get(normalize(m.group(1)));
            if (act != null) {
                found.putIfAbsent(act.number(), new RepealedMention(act, lineAround(text, m.start())));
            }
        }
        return new ArrayList<>(found.values());
    }

    private static String normalize(String number) {
        return number.replaceAll("\\s", "").replace('І', 'I').toUpperCase(Locale.ROOT);
    }

    private static String lineAround(String text, int pos) {
        int start = text.lastIndexOf('\n', pos) + 1;
        int end = text.indexOf('\n', pos);
        if (end < 0) end = text.length();
        String line = text.substring(start, end).strip();
        return line.length() <= 400 ? line : line.substring(0, 400) + "…";
    }
}
