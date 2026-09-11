package com.localai.workspace.errors;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

final class ErrorHistoryEmbeddingText {
    private ErrorHistoryEmbeddingText() { }

    static String from(ErrorHistory history) {
        StringBuilder text = new StringBuilder();
        append(text, "Error type", history.errorType);
        append(text, "Error message", history.errorMessage);
        append(text, "Symptom", history.symptom);
        if (history.status != ErrorStatus.UNVERIFIED) {
            append(text, "Verified root cause", history.rootCause);
            append(text, "Verified solution", history.solution);
        }
        return text.toString().strip();
    }

    static String from(String safeType, String safeMessage, String safeSymptom) {
        StringBuilder text = new StringBuilder();
        append(text, "Error type", safeType);
        append(text, "Error message", safeMessage);
        append(text, "Symptom", safeSymptom);
        return text.toString().strip();
    }

    static String fingerprint(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static void append(StringBuilder target, String label, String value) {
        if (value != null && !value.isBlank()) {
            if (!target.isEmpty()) target.append('\n');
            target.append(label).append(": ").append(value.strip());
        }
    }
}
