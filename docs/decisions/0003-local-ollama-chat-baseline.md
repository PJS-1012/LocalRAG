# 0003. Local Ollama chat baseline

## Status

Accepted

## Decision

- Use Spring AI 1.1.x with Spring Boot 3.5.x and Java 17.
- Connect to the local Ollama API through the Spring AI Ollama starter.
- Use `qwen3:8b` as the default chat model, while allowing environment-variable overrides.
- Keep startup-time model pulling disabled. Models are downloaded and verified explicitly before application startup.
- Expose a stateless `POST /api/chat` endpoint in Phase 3. Retrieval, chat memory, and tools are deferred to later phases.

## Rationale

This phase verifies the model boundary independently from RAG. Explicit model installation avoids hidden multi-gigabyte downloads during application startup and makes failures easier to diagnose.
