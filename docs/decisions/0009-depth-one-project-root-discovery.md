# 0009. Depth-1 Project root discovery

## Status

Accepted

## Problem

Treating every direct child of the Workspace as a Project misclassifies wrapper folders such as
`Room_Reservation` and `Toy_Sports_Day`. Their actual Project roots are one level lower, so framework-specific
scan rules were not applied to the correct root.

## Decision

Run the existing Project type detector on each direct Workspace child. A child with clear markers is a Project root
and is not searched for more Projects. When it remains UNKNOWN, inspect only its immediate child directories and
keep only candidates with clear Project markers.

If at least one marked child exists, represent the direct folder as a separate `DetectedContainer` and return its
marked children as independent `DetectedProject` roots. A Container is structural metadata and is never a scan or
RAG target. If no marked child exists, retain the direct folder as UNKNOWN. Do not search below this one additional
level.

Keep Container separate from `ProjectType`. Adding CONTAINER to the enum would mix a non-Project structural folder
into APIs whose enum values describe scannable Project technologies.

Scan a discovered Project through its resolved `DetectedProject.rootPath`. This avoids rebuilding the direct-child
assumption inside the scanner and lets the configured Workspace remain only the discovery boundary/default source.

## Common and framework-specific exclusions

The existing common exclusions already apply to every Project type, including UNKNOWN: `.git`, `.gradle`,
`.idea`, `.vscode`, `node_modules`, `target`, `build`, `obj`, and `bin`.

Keep `Library` Unity-specific because that directory name can be legitimate in a non-Unity codebase. Correct
Project-root discovery makes the nested `toy_sports_day` root UNITY, so its `Library` receives the Unity policy
without weakening the boundary between common and framework-specific rules.

## Actual Workspace verification

The Workspace produced 2 Containers and 12 scannable Project roots.

- `Room_Reservation`: `room-reservation-front` (NODE), `RoomReservation` (SPRING_BOOT), and
  `RoomReservationBackendPractice` (SPRING_BOOT).
- `Toy_Sports_Day`: `toy_sports_day` (UNITY).
- Direct roots `Local_Ai_Work` (SPRING_BOOT) and `DungeonMerchant` (UNITY) stayed unchanged and were not searched
  recursively.

The metadata baseline changed from 10 Projects, 107,009 total files, 8,614 included files, 98,394 excluded files,
and 1 oversized file to 12 Project roots, 106,990 total files, 680 included files, 106,310 excluded files, and no
oversized files.

The nested Unity scan excluded `Library`, `Logs`, `Temp`, and `UserSettings`. The previous
`Library/Bee/1900b0aE.dag.json` oversized entry disappeared from the scan candidates.

## Remaining UNKNOWN folders

`awsd`, `dragonball`, `Dungeon_Shop`, `untitled`, and `ValueSwap` remain UNKNOWN after the bounded
depth-1 check. No framework is inferred from Git alone.
