package com.localai.workspace.chunk;

import java.util.ArrayList;
import java.util.List;

final class CharacterChunkSplitter {

    enum BoundaryStyle {
        TEXT,
        SOURCE
    }

    private CharacterChunkSplitter() {
    }

    static List<ChunkSlice> split(
            String content,
            int maxCharacters,
            int overlapCharacters,
            BoundaryStyle style
    ) {
        if (content == null || content.isBlank()) {
            return List.of();
        }

        List<ChunkSlice> chunks = new ArrayList<>();
        int start = 0;
        while (start < content.length()) {
            int limit = Math.min(start + maxCharacters, content.length());
            int end = limit == content.length()
                    ? limit
                    : boundary(content, start, limit, style);
            if (end <= start) {
                end = limit;
            }

            String chunkContent = content.substring(start, end);
            if (!chunkContent.isBlank()) {
                chunks.add(new ChunkSlice(
                        chunkContent,
                        start,
                        end,
                        lineAt(content, start),
                        lineAt(content, end - 1)
                ));
            }

            if (end == content.length()) {
                break;
            }
            start = Math.max(end - overlapCharacters, start + 1);
        }
        return List.copyOf(chunks);
    }

    private static int boundary(String content, int start, int limit, BoundaryStyle style) {
        int minimum = start + ((limit - start) / 2);
        int structural = style == BoundaryStyle.SOURCE
                ? sourceBoundary(content, minimum, limit)
                : textBoundary(content, minimum, limit);
        if (structural > start) {
            return structural;
        }

        int line = content.lastIndexOf('\n', limit - 1);
        if (line >= minimum) {
            return line + 1;
        }
        for (int index = limit - 1; index >= minimum; index--) {
            if (Character.isWhitespace(content.charAt(index))) {
                return index + 1;
            }
        }
        return limit;
    }

    private static int textBoundary(String content, int minimum, int limit) {
        int paragraph = lastBoundary(content, "\n\n", minimum, limit, 2);
        int heading = lastHeading(content, minimum, limit);
        return Math.max(paragraph, heading);
    }

    private static int sourceBoundary(String content, int minimum, int limit) {
        int closedBlock = lastSourceBlockBoundary(content, minimum, limit);
        int paragraph = lastBoundary(content, "\n\n", minimum, limit, 2);
        return Math.max(closedBlock, paragraph);
    }

    private static int lastSourceBlockBoundary(String content, int minimum, int limit) {
        int lineEnd = content.lastIndexOf('\n', limit - 1);
        while (lineEnd >= minimum) {
            int previousLineEnd = content.lastIndexOf('\n', lineEnd - 1);
            String line = content.substring(previousLineEnd + 1, lineEnd).trim();
            if (line.equals("}") || line.equals("};")) {
                return lineEnd + 1;
            }
            lineEnd = previousLineEnd;
        }
        return -1;
    }

    private static int lastBoundary(
            String content,
            String marker,
            int minimum,
            int limit,
            int markerLength
    ) {
        int index = content.lastIndexOf(marker, limit - 1);
        int boundary = index < 0 ? -1 : index + markerLength;
        return boundary >= minimum && boundary <= limit ? boundary : -1;
    }

    private static int lastHeading(String content, int minimum, int limit) {
        int searchFrom = limit - 1;
        while (searchFrom >= minimum) {
            int newline = content.lastIndexOf('\n', searchFrom);
            if (newline < minimum - 1) {
                return -1;
            }
            int headingStart = newline + 1;
            if (headingStart < content.length() && content.charAt(headingStart) == '#') {
                return headingStart;
            }
            searchFrom = newline - 1;
        }
        return -1;
    }

    private static int lineAt(String content, int offset) {
        int line = 1;
        int cappedOffset = Math.min(Math.max(offset, 0), content.length() - 1);
        for (int index = 0; index < cappedOffset; index++) {
            if (content.charAt(index) == '\n') {
                line++;
            }
        }
        return line;
    }
}
