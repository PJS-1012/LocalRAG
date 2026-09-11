package com.localai.workspace.agent;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

@Component
public class LogSecretRedactor {

    private static final List<Replacement> REPLACEMENTS = List.of(
            replacement("(?i)((?:\"|')?(?:authorization|cookie|password|passwd|pwd|api[_-]?key|token|secret)(?:\"|')?\\s*[:=]\\s*)(?:\"[^\"]*\"|'[^']*')", "$1****"),
            replacement("(?s)-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----.*?-----END (?:RSA |EC |OPENSSH )?PRIVATE KEY-----", "****"),
            replacement("\\b(?:AKIA[0-9A-Z]{16}|ghp_[A-Za-z0-9]{20,}|sk-[A-Za-z0-9_-]{20,})\\b", "****"),
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

    public String redact(String value) {
        if (value == null) return null;
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
