# 0006. Safe single text file read

## Status

Accepted

## Problem

Phase 5 Step 6 needs to turn exactly one selected text file into an internal Document without allowing the read operation to bypass the scan boundary, exclusion, sensitivity, file-type, or size policies.

## Decision

Accept only a Project name and a Project-relative file path. Resolve the real Project and file paths before reading and reject paths that escape the selected Project, including traversal and symbolic-link escapes.

Reapply the existing scan policy in this order:

1. Excluded parent directory
2. Sensitive filename
3. Excluded or unsupported file type
4. Regular-file metadata and configured size limit
5. Bounded content read

Decode content as strict UTF-8. Malformed or unmappable input produces `READ_FAILED`; Step 6 does not guess a fallback encoding.

The successful `WorkspaceDocument` contains only `projectName`, `fileName`, `filePath`, `extension`, `size`, `modifiedAt`, and `content`. Read status and failure reason live in a separate `DocumentReadResult`, because they describe the operation rather than the document.

## Consequence

One inaccessible or invalid file produces a result without throwing from the public reader API. The bounded read checks the size again while reading, reducing the risk from a file that grows after its metadata was checked. Project-wide reading, parsing variants, chunking, embedding, and persistence remain out of scope.
