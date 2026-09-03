# 0007. Sequential Project document batch read

## Status

Accepted

## Problem

Phase 5 Step 7 must read every allowed text file in one Project while preserving success, failure, and skip outcomes. One bad file must not stop later files, and the implementation must remain replaceable when Workspace roots come from a database instead of application configuration.

## Decision

`ProjectFileScanner` produces an internal `ProjectScanPlan` containing its existing summary plus one `FileScanEntry` per detected file. Only entries marked `SUPPORTED` are passed to the existing `ProjectDocumentReader`. All other scan statuses are converted directly into per-file read results without opening content.

`ProjectDocumentReadResult` contains Project-level counts, successful Documents, per-file status and reason, total UTF-8 text bytes, and elapsed milliseconds. Successful content is stored only in `documents`; `fileResults` keeps paths and operational status without duplicating content in the API response.

Read files sequentially. Measure elapsed time from before scanning through completion of all reads. Do not add parallel streams, Virtual Threads, chunking, embedding, or persistence.

The discovery, scan, single-file read, and batch-read services expose overloads that accept a Workspace `Path`. Their existing no-Path methods delegate using `WorkspaceProperties` only as the current default supplier. A future `WorkspaceService` can therefore pass roots loaded from a database without replacing the filesystem logic.

## Consequence

The scan policy remains the single source of Include/Exclude decisions and is also reapplied by the single-file reader immediately before content access. Metadata for skipped files is retained, while sensitive file content is never read.

The final `Local_Ai_Work` baseline read detected 416 files, read 57 Documents and 90,026 text bytes, skipped 359 files, failed 0 files, and completed in 69ms on the development machine.
