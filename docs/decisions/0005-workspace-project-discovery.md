# 0005. Workspace project discovery and file scan boundary

## Status

Accepted

## Problem

LocalRAG must find heterogeneous projects without scanning the whole machine, guessing a framework from Git alone, or ingesting generated, sensitive, binary, and oversized files.

## Decision

Use `C:/workspace` as the configurable Workspace root and consider only its direct child directories as Project candidates.

Detect a Project type from multiple structural hints:

- Unity requires `Assets`, `ProjectSettings`, and `Packages/manifest.json`.
- Java requires a Gradle or Maven build file and `src/main/java`; build-file content may refine it to Spring Boot.
- Node.js, .NET, and Python use their own manifest and source-layout hints.
- A Git directory is metadata only and does not imply a framework.

Before reading content, scan metadata and classify every regular file. Apply common directory, path, binary, secret, and extension exclusions. Apply Unity generated-directory exclusions only to Unity projects. The initial supported-file size limit is configurable and defaults to 5MB.

The configured relative exclusion `logs/archive` matches only that path from the Project root. A different directory named `archive` remains eligible, preventing an overly broad name-based exclusion.

## Consequence

Project discovery cannot escape the configured Workspace root or descend into nested repositories as separate Projects. The scan reports supported, excluded, too-large, and metadata-failed counts plus details for oversized files, but does not yet read, parse, chunk, embed, or persist file content.

Actual Phase 5 verification detected:

- `Local_Ai_Work` as Java with Spring Boot.
- `DungeonMerchant` as Unity.
- Unity-generated directories including `Library`, `Logs`, and `UserSettings` were excluded.
