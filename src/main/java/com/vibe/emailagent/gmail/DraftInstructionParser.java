package com.vibe.emailagent.gmail;

/**
 * Parses a human-written trigger draft.
 *
 * New expected format
 * - Line 1: write
 * - Line 2 (optional): a single free-form note/instruction line. This line has the highest precedence.
 * - Line 3+: ignored
 *
 * Examples
 * write
 * don't schedule on the inquiry day; only 12-2pm
 * (anything below is ignored)
 */
public final class DraftInstructionParser {

    private DraftInstructionParser() {
    }

    public static ParsedDraft parse(String plainTextBody) {
        if (plainTextBody == null) {
            return new ParsedDraft(false, null);
        }

        String normalized = plainTextBody.replace("\r\n", "\n");
        String trimmedLeft = ltrim(normalized);

        // Must start with "write" marker (case-insensitive)
        if (!trimmedLeft.toLowerCase().startsWith("write")) {
            return new ParsedDraft(false, null);
        }

        String[] lines = trimmedLeft.split("\n", -1);

        // Line 2 is optional. If missing/blank, note is null.
        String note = null;
        if (lines.length >= 2) {
            String candidate = lines[1];
            if (candidate != null && !candidate.isBlank()) {
                note = candidate.strip();
            }
        }

        return new ParsedDraft(true, note);
    }

    private static String ltrim(String s) {
        int i = 0;
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
            i++;
        }
        return s.substring(i);
    }

    /**
     * @param isWriteDraft whether this draft is a write-trigger draft
     * @param note optional single-line instruction right below 'write'
     */
    public record ParsedDraft(boolean isWriteDraft, String note) {
    }
}
