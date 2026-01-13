package com.vibe.emailagent.gmail;

/**
 * Utilities for cleaning Gmail message bodies before ingestion.
 *
 * Goal
 * - Make the text more embedding-friendly by removing noisy artifacts:
 *   - HTML tags
 *   - excessive whitespace
 *   - common reply/forward separators (best-effort)
 *
 * Notes
 * - Email clients vary a lot. This is intentionally conservative and best-effort.
 * - Keep this deterministic (no LLM-based cleanup) to control cost.
 */
public final class GmailTextCleaner {

    private GmailTextCleaner() {
    }

    /**
     * Cleans raw body text.
     *
     * @param raw raw body extracted from Gmail payload (may still contain HTML-ish artifacts)
     */
    public static String clean(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }

        String text = raw;

        // If the body still looks like HTML, remove tags and normalize.
        if (looksLikeHtml(text)) {
            text = htmlToText(text);
        }

        // Remove common quoted-reply separators.
        text = stripQuotedReply(text);

        // Normalize whitespace a bit.
        text = text.replace('\r', '\n');
        text = text.replaceAll("\n{3,}", "\n\n");
        text = text.replaceAll("[ \t]{2,}", " ");

        return text.strip();
    }

    private static boolean looksLikeHtml(String s) {
        String lower = s.toLowerCase();
        return lower.contains("<html") || lower.contains("<body") || lower.contains("<div") || lower.contains("<br")
                || (lower.contains("<") && lower.contains(">") && lower.contains("</"));
    }

    /**
     * Very small HTML-to-text conversion.
     *
     * This is intentionally minimal to avoid introducing extra dependencies.
     * It won't be perfect, but it removes the most common tag noise.
     */
    private static String htmlToText(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }

        String text = html;

        // Remove non-content blocks early (best-effort).
        // - Some emails inline huge CSS in <style> blocks.
        // - Some templates include script tags.
        text = text.replaceAll("(?is)<style[^>]*>.*?</style>", " ");
        text = text.replaceAll("(?is)<script[^>]*>.*?</script>", " ");
        text = text.replaceAll("(?is)<head[^>]*>.*?</head>", " ");

        // Preserve line breaks for common tags before stripping.
        text = text.replaceAll("(?i)<br\\s*/?>", "\n");
        text = text.replaceAll("(?i)</p>", "\n");
        text = text.replaceAll("(?i)</div>", "\n");
        text = text.replaceAll("(?i)</li>", "\n");
        text = text.replaceAll("(?i)</tr>", "\n");
        text = text.replaceAll("(?i)</h[1-6]>", "\n");

        // Remove the remaining tags.
        text = text.replaceAll("<[^>]+>", " ");

        // Decode a few common HTML entities.
        text = text.replace("&nbsp;", " ")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&#39;", "'");

        // Some templates accidentally leak CSS into text nodes.
        // Best-effort removal of obvious CSS-like patterns.
        text = stripCssLikeNoise(text);

        // Collapse whitespace.
        text = text.replaceAll("[ \t]{2,}", " ");
        text = text.replaceAll("\n{3,}", "\n\n");

        return text.strip();
    }

    /**
     * Removes typical CSS rules that sometimes appear in HTML email bodies.
     */
    private static String stripCssLikeNoise(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }

        String out = text;

        // Remove @media blocks (can be huge). Non-greedy to avoid wiping too much.
        out = out.replaceAll("(?is)@media[^\\{]*\\{.*?\\}", " ");

        // Remove common selector{...} patterns.
        out = out.replaceAll("(?s)(?:\\.|#)[A-Za-z0-9_-]+\\s*\\{[^}]*\\}", " ");

        // Very generic rule blocks like: body { margin:0; }
        out = out.replaceAll("(?s)\\b[A-Za-z][A-Za-z0-9_-]*\\b\\s*\\{[^}]*\\}", " ");

        // Remove leftover long runs of CSS punctuation.
        out = out.replaceAll("[;:{}]{3,}", " ");

        return out;
    }

    /**
     * Best-effort removal of quoted replies / forwards.
     */
    private static String stripQuotedReply(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }

        String out = text;

        // 1) Typical quoted line starts with ">".
        int quoted = indexOfLineStartingWith(out, ">");
        if (quoted >= 0) {
            out = out.substring(0, quoted);
        }

        // 2) "On ... wrote:" style (Gmail/Web).
        // Example:
        //   On Dec 31, 2025, at 2:11 PM, Lauren K <...> wrote:
        //   On Tue, Dec 31, 2025 at 2:11 PM Lauren K <...> wrote:
        int onWroteLine = indexOfRegexLine(out, "(?i)^\\s*on\\s+.+?wrote:\\s*$");
        if (onWroteLine >= 0) {
            out = out.substring(0, onWroteLine);
        }

        // 3) Apple Mail style (your example):
        //   On Dec 31, 2025, at 2:11 PM, Lauren K <...> wrote:
        // Sometimes there is no leading newline for this separator in ingestion.
        int onWroteAnywhere = indexOfRegex(out, "(?is)\\n\\s*On\\s+.+?wrote:\\s*\\n");
        if (onWroteAnywhere >= 0) {
            out = out.substring(0, onWroteAnywhere);
        }

        // 4) Outlook-ish forwarded/original message blocks.
        int original = indexOfIgnoreCase(out, "-----Original Message-----");
        if (original >= 0) {
            out = out.substring(0, original);
        }

        int forwarded = indexOfIgnoreCase(out, "-----Forwarded message-----");
        if (forwarded >= 0) {
            out = out.substring(0, forwarded);
        }

        // 5) Header-style quoted blocks (common in Outlook and some clients):
        //    From: ...
        //    Sent: ...
        //    To: ...
        //    Subject: ...
        // We cut from the first strongly-indicative header line.
        int headerBlock = indexOfRegexLine(out, "(?i)^\\s*(from|sent|to|subject):\\s+.+$");
        if (headerBlock >= 0) {
            out = out.substring(0, headerBlock);
        }

        return out.strip();
    }

    /**
     * Finds the character offset of the first line matching the regex (Pattern.MULTILINE assumed).
     */
    private static int indexOfRegexLine(String text, String lineRegex) {
        if (text == null || text.isEmpty()) {
            return -1;
        }

        java.util.regex.Pattern p = java.util.regex.Pattern.compile(lineRegex, java.util.regex.Pattern.MULTILINE);
        java.util.regex.Matcher m = p.matcher(text);
        return m.find() ? m.start() : -1;
    }

    /**
     * Finds the character offset of the first match for the given regex.
     */
    private static int indexOfRegex(String text, String regex) {
        if (text == null || text.isEmpty()) {
            return -1;
        }

        java.util.regex.Pattern p = java.util.regex.Pattern.compile(regex);
        java.util.regex.Matcher m = p.matcher(text);
        return m.find() ? m.start() : -1;
    }

    private static int indexOfLineStartingWith(String text, String prefix) {
        int idx = 0;
        while (idx < text.length()) {
            int lineEnd = text.indexOf('\n', idx);
            if (lineEnd < 0) lineEnd = text.length();
            String line = text.substring(idx, lineEnd);
            if (line.startsWith(prefix)) {
                return idx;
            }
            idx = lineEnd + 1;
        }
        return -1;
    }

    private static int indexOfIgnoreCase(String haystack, String needle) {
        return haystack.toLowerCase().indexOf(needle.toLowerCase());
    }
}
