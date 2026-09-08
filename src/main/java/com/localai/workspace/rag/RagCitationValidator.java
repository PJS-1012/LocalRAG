package com.localai.workspace.rag;

import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
public class RagCitationValidator {

    private static final Pattern CITATION_PATTERN = Pattern.compile("\\[((?:K\\d+-)?S\\d+)]");

    public CitationValidationResult validate(String answer, List<RagContextSource> sources) {
        Set<String> availableIds = sources.stream()
                .map(RagContextSource::citationId)
                .collect(Collectors.toSet());
        return validateAvailableSourceIds(answer, availableIds);
    }

    public CitationValidationResult validateAvailableSourceIds(String answer, Collection<String> availableIds) {
        Set<String> usedIds = new LinkedHashSet<>();
        Set<String> invalidIds = new LinkedHashSet<>();

        Matcher matcher = CITATION_PATTERN.matcher(answer == null ? "" : answer);
        while (matcher.find()) {
            String sourceId = matcher.group(1);
            usedIds.add(sourceId);
            if (!availableIds.contains(sourceId)) {
                invalidIds.add(sourceId);
            }
        }

        return new CitationValidationResult(
                List.copyOf(usedIds),
                List.copyOf(invalidIds),
                !availableIds.isEmpty() && usedIds.isEmpty()
        );
    }
}
