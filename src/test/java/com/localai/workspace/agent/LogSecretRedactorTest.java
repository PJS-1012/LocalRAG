package com.localai.workspace.agent;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LogSecretRedactorTest {

    @Test
    void redactsQuotedCredentialsAndPrivateKeys() {
        String input = "{\"password\":\"quoted secret\",\"Authorization\":\"Bearer abcSecret\"} "
                + "-----BEGIN PRIVATE KEY-----\nprivateContent\n-----END PRIVATE KEY----- "
                + "AKIA1234567890123456 ghp_123456789012345678901234";
        assertThat(new LogSecretRedactor().redact(input)).doesNotContain(
                "quoted secret", "abcSecret", "privateContent", "AKIA1234567890123456", "ghp_");
    }

    @Test
    void redactsCommonCredentialsAndIdentifiers() {
        String input = "Authorization: Bearer abcdef password=my-secret apiKey=test-key "
                + "token=token-value Cookie: session=abc\nuser@example.com "
                + "jdbc:postgresql://db/app?user=a&password=b\nAuthorization: Basic dXNlcjpwYXNz";

        String sanitized = new LogSecretRedactor().redact(input);

        assertThat(sanitized)
                .contains("Authorization: Bearer ****")
                .contains("Authorization: Basic ****")
                .contains("password=****")
                .contains("apiKey=****")
                .contains("token=****")
                .contains("Cookie: ****")
                .contains("[REDACTED_EMAIL]")
                .contains("jdbc:****")
                .doesNotContain("abcdef", "my-secret", "test-key", "token-value", "user@example.com");
    }
}
