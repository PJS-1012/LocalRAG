package com.localai.workspace.agent;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "localrag.agent.git")
public record GitAgentProperties(
        int maxRecentCommits,
        Duration commandTimeout,
        int maxOutputCharacters
) {
    public GitAgentProperties {
        maxRecentCommits = maxRecentCommits < 1 ? 20 : Math.min(maxRecentCommits, 100);
        if (commandTimeout == null || commandTimeout.isNegative() || commandTimeout.isZero()) {
            commandTimeout = Duration.ofSeconds(5);
        }
        maxOutputCharacters = maxOutputCharacters < 1
                ? 65_536
                : Math.min(maxOutputCharacters, 262_144);
    }
}
