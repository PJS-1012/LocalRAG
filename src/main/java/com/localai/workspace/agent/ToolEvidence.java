package com.localai.workspace.agent;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;

/** Sanitized callback output, never a model-generated statement. */
public record ToolEvidence(int sequence, String toolName, Instant observedAt, JsonNode result) { }
