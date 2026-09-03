# 0011. Character-based structural chunking baseline

## Status

Accepted

## Context

Phase 6 needs to convert safe `WorkspaceDocument` values into retrieval units without introducing embedding,
vector persistence, or RAG. The current Spring AI 1.1.8 dependency set does not provide a Qwen3-compatible exact
token counter. Adding a model-specific tokenizer now would increase dependency and model coupling before retrieval
quality has been measured.

## Decision

- Use character counts behind a dedicated Chunker abstraction for the MVP.
- Limit general text and Markdown chunks to 2,000 characters with 200 characters of overlap.
- Limit source-code chunks to 2,400 characters with 200 characters of overlap.
- Prefer Markdown heading, paragraph, line, and whitespace boundaries for general text.
- Prefer completed block, blank-line, line, and whitespace boundaries for source code.
- Do not introduce language-specific AST parsers yet.
- Route known source extensions explicitly to `SourceCodeDocumentChunker`; use `TextDocumentChunker` as the
  fallback for other safe text formats.
- Treat blank Documents as `EMPTY` rather than manufacturing empty Chunk content.
- Isolate a Chunking failure to its source Document and continue the Project.
- Keep processing sequential until measurements show a need for concurrency.

## Chunk metadata

Each `DocumentChunk` keeps:

- deterministic `chunkId`
- `projectId`
- Workspace-relative source path and source file name
- extension and zero-based Chunk index
- content
- start/end character offsets, where end is exclusive
- one-based start/end line numbers
- source content fingerprint and source modification time

The relative path supports source display without leaking an absolute Workspace path. Offsets and lines support
citations and ordering. The fingerprint groups a generated set with one exact source version.

## Deterministic identifier

`chunkId` is the SHA-256 hash of:

```text
projectId
relative source path
source content SHA-256
chunk index
```

The same source version produces the same IDs. A changed source produces a new set of IDs. Future persistence must
replace Chunks by `projectId + source path` as one unit so stale IDs are removed safely.

## Baseline

Measured against the working `Local_Ai_Work` Project:

- source Documents: 90
- successfully chunked Documents: 90
- failed Documents: 0
- generated Chunks: 131
- average Chunk size: 1,318.95 characters
- minimum Chunk size: 30 characters
- maximum Chunk size: 2,396 characters
- total processing time, including the existing Project read: 114 ms

Verified file types include `README.md`, Java source,
`src/main/resources/application.yml`, and `gradle/wrapper/gradle-wrapper.properties`. Short files remained one
Chunk, while longer Markdown, Java, and YAML files produced multiple Chunks.

These figures are local reference values. Filesystem cache, the working tree, and newly added source/test files can
change counts and timing.

## Consequences

Character limits do not precisely predict Qwen3 token counts, especially across Korean, English, and source code.
The `DocumentChunker` boundary allows a future token-based implementation without changing Chunk consumers.
Structural splitting is heuristic: it preserves useful boundaries when they occur near the target size but does not
guarantee complete class or method boundaries. AST-based splitting remains an evaluation-driven future option.
