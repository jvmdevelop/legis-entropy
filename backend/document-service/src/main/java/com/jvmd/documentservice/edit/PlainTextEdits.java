package com.jvmd.documentservice.edit;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class PlainTextEdits {
    public record Span(int index, int start, int end, TextEdit edit) {}

    private PlainTextEdits() {}

    public static int nthIndexOf(String text, String fragment, int n) {
        int pos = -1;
        for (int i = 0; i <= n; i++) {
            pos = text.indexOf(fragment, pos + 1);
            if (pos < 0) return -1;
        }
        return pos;
    }

    public static List<Span> resolve(String text, List<TextEdit> edits, List<EditResult.Skipped> skipped) {
        List<Span> spans = new ArrayList<>();
        for (int i = 0; i < edits.size(); i++) {
            TextEdit e = edits.get(i);
            int start = nthIndexOf(text, e.original(), e.occurrence());
            if (start < 0) {
                skipped.add(new EditResult.Skipped(i, "Фрагмент не найден в документе."));
                continue;
            }
            int end = start + e.original().length();
            boolean overlaps = spans.stream().anyMatch(s -> s.start() < end && start < s.end());
            if (overlaps) {
                skipped.add(new EditResult.Skipped(i, "Пересекается с другой правкой."));
                continue;
            }
            spans.add(new Span(i, start, end, e));
        }
        spans.sort(Comparator.comparingInt(Span::start));
        return spans;
    }

    public static String apply(String text, List<Span> spans) {
        StringBuilder sb = new StringBuilder(text);
        for (int k = spans.size() - 1; k >= 0; k--) {
            Span s = spans.get(k);
            sb.replace(s.start(), s.end(), s.edit().replacement());
        }
        return sb.toString();
    }
}