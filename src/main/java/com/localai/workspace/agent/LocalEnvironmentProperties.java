package com.localai.workspace.agent;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "localrag.agent.environment")
public record LocalEnvironmentProperties(
        Duration commandTimeout,
        Duration httpTimeout,
        int maxOutputCharacters,
        int maxModels
) {
    public LocalEnvironmentProperties {
        commandTimeout = valid(commandTimeout) ? commandTimeout : Duration.ofSeconds(5);
        httpTimeout = valid(httpTimeout) ? httpTimeout : Duration.ofSeconds(3);
        maxOutputCharacters = maxOutputCharacters < 1 ? 65_536 : Math.min(maxOutputCharacters, 262_144);
        maxModels = maxModels < 1 ? 100 : Math.min(maxModels, 500);
    }

    private static boolean valid(Duration duration) {
        return duration != null && !duration.isNegative() && !duration.isZero();
    }
}
