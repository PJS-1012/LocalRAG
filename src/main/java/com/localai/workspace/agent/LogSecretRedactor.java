package com.localai.workspace.agent;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

@Component
class LogSecretRedactor {

    private static final List<Replacement> REPLACEMENTS = List.of(
            replacement("(?i)(authorization\\s*[:=]\\s*bearer\\s+)[^\\s,;]+", "$1****"),
            replacement("(?i)(authorization\\s*[:=]\\s*basic\\s+)[^\\s,;]+", "$1****"),
            replacement("(?i)(authorization\\s*[:=]\\s*)(?!bearer\\s+|basic\\s+)[^\\s,;]+", "$1****"),
            replacement("(?i)(cookie\\s*[:=]\\s*)[^\\r\\n]+", "$1****"),
            replacement("(?i)((?:password|passwd|pwd|api[_-]?key|token|secret)\\s*[:=]\\s*)"
                    + "(?:\\\"[^\\\"]*\\\"|'[^']*'|[^\\s,;]+)", "$1****"),
            replacement("(?i)jdbc:[^\\s]+", "jdbc:****"),
            replacement("\\beyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\b", "****"),
            replacement("\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b", "[REDACTED_EMAIL]",
                    Pattern.CASE_INSENSITIVE)
    );

    String redact(String value) {
        String sanitized = value;
        for (Replacement replacement : REPLACEMENTS) {
            sanitized = replacement.pattern().matcher(sanitized).replaceAll(replacement.value());
        }
        return sanitized;
    }

    private static Replacement replacement(String regex, String value) {
        return replacement(regex, value, 0);
    }

    private static Replacement replacement(String regex, String value, int flags) {
        return new Replacement(Pattern.compile(regex, flags), value);
    }

    private record Replacement(Pattern pattern, String value) {
    }
}
