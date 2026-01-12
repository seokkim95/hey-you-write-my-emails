package com.vibe.emailagent.service;

import java.util.ArrayList;
import java.util.List;

/**
 * Very small, dependency-free text chunker.
 *
 * Why this exists
 * - Embedding providers enforce max input token limits.
 * - Some emails can be long (threads, forwarded content, signatures).
 * - Chunking is the standard approach for RAG ingestion.
 *
 * Notes
 * - This is intentionally character-based to keep it provider-agnostic.
 * - If needed later, we can replace this with a token-based chunker using a tokenizer.
 */
public final class TextChunker {

    private TextChunker() {
    }

    /**
     * Splits text into overlapping chunks.
     *
     * @param text input text
     * @param chunkSize maximum length of each chunk (characters)
     * @param overlap number of characters to overlap between chunks
     */
    public static List<String> chunk(String text, int chunkSize, int overlap) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        int size = Math.max(1, chunkSize);
        int ov = Math.max(0, overlap);
        if (ov >= size) {
            ov = Math.max(0, size / 4);
        }

        String normalized = text.strip();
        int n = normalized.length();

        List<String> out = new ArrayList<>();
        int start = 0;
        while (start < n) {
            int end = Math.min(n, start + size);
            String part = normalized.substring(start, end).strip();
            if (!part.isBlank()) {
                out.add(part);
            }
            if (end == n) {
                break;
            }
            start = Math.max(0, end - ov);
        }

        return out;
    }
}

