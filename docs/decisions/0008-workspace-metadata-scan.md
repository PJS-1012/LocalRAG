# 0008. Sequential Workspace metadata scan

## Status

Accepted

## Problem

Phase 5 Step 8 needs one aggregate view of every direct-child Project under the default Workspace without reading file content. A scan failure in one Project must remain visible without preventing later Projects from being scanned.

## Decision

Discover Projects from an explicit Workspace `Path`, then call the existing `ProjectFileScanner` sequentially for each Project. Catch runtime scan failures at the Project boundary and emit a failed Project result containing its identity, detection hints, elapsed time, and failure reason.

Each successful Project result contains type, detected framework, Git flag, detection hints, file counts, oversized-file details, and duration. `WorkspaceScanSummary` sums successful Project metadata and separately reports successful and failed Project counts. No `ProjectDocumentReader` is called.

Keep the existing 5MB limit. Keep sequential execution and do not introduce caching, parallel streams, or Virtual Threads until measurements show a need.

## Baseline

The final `C:/workspace` scan found 10 Projects and 107,009 files. It included 8,614 files, excluded 98,394, recorded 0 metadata failures, and recorded 1 oversized file. All 10 Project scans completed successfully in 2,472ms total.

The oversized file was `Toy_Sports_Day/toy_sports_day/Library/Bee/1900b0aE.dag.json` at 16,280,177 bytes.

## Open detection issue

Seven direct-child folders were UNKNOWN. `dragonball`, `Room_Reservation`, `Toy_Sports_Day`, and `ValueSwap` were Git repositories.

`Room_Reservation` is a container folder with two nested Spring Boot projects (`RoomReservation` and `RoomReservationBackendPractice`) plus a frontend Project. `Toy_Sports_Day` contains a nested Unity Project at `toy_sports_day`. Because the direct child is UNKNOWN, Unity's `Library` exclusion was not applied and generated files dominated its scan.

Do not change detection automatically in Step 8. Before Phase 5 ends, decide how one-level wrapper folders and multi-project containers should be represented; blindly inheriting a nested type would be wrong for mixed containers such as `Room_Reservation`.
