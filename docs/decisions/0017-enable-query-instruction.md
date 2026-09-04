# Decision 0017: Enable Query instruction by default

## Status

Accepted for Phase 6 Step 4.3.

## Decision

- Enable the Query instruction evaluated in Decision 0016 by default.
- Configure the behavior under `localrag.search.query-instruction.enabled` and
  `localrag.search.query-instruction.template`; do not hardcode the text in the service.
- Require the template to contain `<USER_QUERY>` and replace that placeholder with the normalized user Query.
- Allow a nullable request field, `instructionEnabled`, to override the configured default. Omitting it uses the
  configured default; `false` selects raw-query embedding.
- Return `instructionEnabled` in every search response so callers can verify the mode used.
- Preserve the raw user Query in the response and apply the instruction only to the text sent to the embedding model.
- Keep Top-K 5, threshold 0.45, the corpus, Chunking, and pgvector SQL unchanged.

## Default template

```text
Instruct: Retrieve the most relevant source code or project documentation for the software project query.
Query: <USER_QUERY>
```

## Verification

Six representative Queries were sent through the actual API in default and raw modes against the unchanged
208-Chunk index.

| Query | Default instructed | Default results | Raw results | Default Top result |
|---|---|---:|---:|---|
| Project type discovery | true | 5 | 5 | `ProjectType.java` |
| Sensitive-file exclusion | true | 5 | 0 | `ProjectFileScanner.java` |
| Text to Vector | true | 5 | 5 | vector extension migration |
| Missing Kafka configuration | true | 0 | 0 | none |
| Workspace boundary protection | true | 5 | 0 | Phase 5 security decision |
| Nested Project discovery | true | 5 | 5 | depth-one discovery decision |

Every default response reported Top-K 5, threshold 0.45, and `instructionEnabled=true`. Every override response
reported `instructionEnabled=false`. The two previously missing relevant Queries remained recovered, and the absent
Kafka Query remained empty.

## Consequences

Retrieval now benefits from the evaluated Query-side instruction without requiring callers to understand model
prompting. Raw mode remains available for regression diagnosis and rollback. Known ranking limitations remain: the
Text-to-Vector Query still ranks the vector migration above `EmbeddingService`, and some documentation or tests can
outrank production code. Those issues are not addressed by this configuration change.
