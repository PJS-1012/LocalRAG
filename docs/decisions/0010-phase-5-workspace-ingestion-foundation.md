# 0010. Phase 5 Workspace ingestion foundation

## Status

Accepted

## Problem

A local RAG system must not treat every file on the PC as an ingestion target. It needs an explicit Workspace
boundary, reliable Project roots, metadata-first filtering, and safe text reads before chunking or vector storage.

## Design

```text
Workspace
  -> Container / Project Discovery
  -> Project Type Detection
  -> Metadata Scan
  -> Policy Filtering
  -> Safe Single / Project Document Read
```

Phase 5 stops at safe in-memory text Documents. It does not chunk, embed, persist vectors, search semantically,
run RAG, or introduce an Agent.

## Decisions

- Use `C:/workspace` as the initial development Workspace, supplied by `WorkspaceProperties`.
- Keep actual discovery and scan operations callable with an explicit `Path` so a future Workspace service can
  supply roots from DB records.
- Detect Java/Spring Boot, Unity, Node, .NET, and Python from explicit root markers; Git alone never determines type.
- Separate structural Containers from scannable Projects.
- Search one additional directory level only when a direct Workspace child remains UNKNOWN.
- Do not search inside an already recognized Project root.
- Use the normalized Workspace-relative path with `/` separators as the temporary external `projectId`.
- Reject absolute IDs, empty segments, `.`, `..`, and any resolved path outside the Workspace.
- Keep the ID parsing in the discovery boundary so it can later be replaced by DB-backed internal IDs.
- Apply common generated-directory exclusions to every type and keep Unity `Library`, `Temp`, `Logs`,
  `UserSettings`, and related directories Unity-specific.
- Block sensitive file-name patterns before reading content.
- Keep the temporary maximum file size at 5MB.
- Decode text as strict UTF-8; malformed input becomes an isolated read failure.
- Resolve real paths before reading so symbolic links cannot escape a Project root.
- Continue after one file read failure and after one Project metadata-scan failure.
- Read Projects sequentially; do not add parallelism, Virtual Threads, or caching without measured need.

## Relative Project identifier

Examples are `Local_Ai_Work`, `Room_Reservation/RoomReservation`,
`Room_Reservation/room-reservation-front`, and `Toy_Sports_Day/toy_sports_day`.

The relative ID is readable, portable across the configured Workspace root, and avoids exposing an absolute path.
Its tradeoff is that moving or renaming a Project changes the ID. A future DB model may replace it with a stable
internal ID without changing the current path validation boundary.

Canonical single-Project APIs accept the ID as a query parameter because encoded slash handling in URL path
variables differs across servers and proxies. Existing direct-child name URLs remain compatible.

## Failure isolation and security regression

The regression suite covers Workspace boundary rejection, Project and file `..` traversal rejection, actual
symbolic-link escape rejection, sensitive-file blocking, excluded directories, the 5MB limit, malformed UTF-8,
per-file failure isolation, per-Project scan failure isolation, Container exclusion, depth-1 discovery, the
depth-2 limit, and Unity generated-directory exclusion.

## Final baseline

- Workspace metadata scan: 3,978 ms
- Project roots: 12
- Containers: 2
- Total detected files: 107,086
- Included files: 686
- Excluded files: 106,400
- Oversized files: 0
- Metadata-failed files: 0
- Failed Project scans: 0
- `Local_Ai_Work` full Document read: 73 documents, 0 failures, 134,727 text bytes, 82 ms

These figures are a local baseline, not a performance target. Filesystem cache state, Git/build artifacts, and other
concurrent disk activity can change both counts and elapsed time between runs.

## Portfolio troubleshooting candidate

Symptom: `Toy_Sports_Day` was classified UNKNOWN because the real Unity root was one level lower. Without the
Unity type, `Library` generated files entered the supported-file evaluation and
`Library/Bee/1900b0aE.dag.json` appeared as a 16,280,177-byte `SKIPPED_TOO_LARGE` file.

Investigation: the original policy assumed every direct Workspace child was a Project root.

Resolution: add bounded depth-1 Project-root discovery, classify `Toy_Sports_Day` as a Container, detect
`Toy_Sports_Day/toy_sports_day` as UNITY, and apply the Unity exclusions at the correct boundary.

Result: `SKIPPED_TOO_LARGE` changed from 1 to 0 and included files dropped from 8,614 to 686 while retaining
metadata visibility for excluded files.

This is a useful portfolio example because it connects a real data-quality/performance symptom to root-cause
analysis, a bounded architecture change, regression tests, and a measurable before/after result.

## Next phase boundary

Phase 6 may consume only successful `WorkspaceDocument` values from this foundation. Chunking policy, chunk IDs,
metadata inheritance, code-aware boundaries, re-index behavior, and token/character sizing remain undecided.
