package com.jvmd.templateservice.service;

import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class TemplateRenderer {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\s*([a-zA-Z0-9_]+)\\s*}}");

    public RenderResult render(String body, Map<String, String> variables) {
        if (body == null || body.isEmpty()) return new RenderResult("", Set.of());
        Map<String, String> vars = variables == null ? Map.of() : variables;
        Matcher matcher = PLACEHOLDER.matcher(body);
        StringBuffer out = new StringBuffer();
        Set<String> unresolved = new HashSet<>();
        while (matcher.find()) {
            String name = matcher.group(1);
            String value = vars.get(name);
            if (value == null || value.isBlank()) {
                unresolved.add(name);
                matcher.appendReplacement(out, Matcher.quoteReplacement(matcher.group()));
            } else {
                matcher.appendReplacement(out, Matcher.quoteReplacement(value));
            }
        }
        matcher.appendTail(out);
        return new RenderResult(out.toString(), unresolved);
    }

    public record RenderResult(String body, Set<String> unresolvedPlaceholders) {}
}
