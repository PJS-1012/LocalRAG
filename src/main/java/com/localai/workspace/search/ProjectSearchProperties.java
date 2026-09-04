package com.localai.workspace.search;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "localrag.search")
public record ProjectSearchProperties(
        int defaultTopK,
        double defaultThreshold,
        int maxTopK,
        QueryInstruction queryInstruction
) {
    public ProjectSearchProperties {
        if (defaultTopK < 1 || maxTopK < defaultTopK) {
            throw new IllegalArgumentException("Search Top-K configuration is invalid");
        }
        if (defaultThreshold < 0.0 || defaultThreshold > 1.0) {
            throw new IllegalArgumentException("Search threshold must be between 0 and 1");
        }
        if (queryInstruction == null) {
            throw new IllegalArgumentException("Query instruction configuration is required");
        }
    }

    public record QueryInstruction(boolean enabled, String template) {

        private static final String QUERY_PLACEHOLDER = "<USER_QUERY>";

        public QueryInstruction {
            if (template == null || template.isBlank()) {
                throw new IllegalArgumentException("Query instruction template must not be blank");
            }
            if (!template.contains(QUERY_PLACEHOLDER)) {
                throw new IllegalArgumentException(
                        "Query instruction template must contain " + QUERY_PLACEHOLDER);
            }
        }

        public String apply(String query) {
            return template.replace(QUERY_PLACEHOLDER, query);
        }
    }
}
