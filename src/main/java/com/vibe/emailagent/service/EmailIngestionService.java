package com.vibe.emailagent.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.vibe.emailagent.config.IngestionProperties;
import com.vibe.emailagent.gmail.GmailClient;
import com.vibe.emailagent.gmail.GmailMessageContent;
import com.vibe.emailagent.gmail.GmailMessagePage;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ingestion pipeline: Gmail -> (Embedding via VectorStore) -> email_embeddings.
 *
 * Single source of truth
 * - We store historical content only in the VectorStore table (email_embeddings).
 * - De-duplication is performed by checking metadata.message_id (Gmail messageId).
 */
@Service
@Transactional
@Profile({"ingest"})
public class EmailIngestionService {

    private static final Logger log = LoggerFactory.getLogger(EmailIngestionService.class);

    private final GmailClient gmailClient;
    private final VectorStore vectorStore;
    private final IngestionProperties ingestionProperties;
    private final JdbcClient jdbcClient;

    public EmailIngestionService(GmailClient gmailClient,
                                VectorStore vectorStore,
                                IngestionProperties ingestionProperties,
                                JdbcClient jdbcClient) {
        this.gmailClient = gmailClient;
        this.vectorStore = vectorStore;
        this.ingestionProperties = ingestionProperties;
        this.jdbcClient = jdbcClient;
    }

    @PostConstruct
    public void init() {
        printRunConfig();
    }

    private void printRunConfig() {
        log.info("Ingestion Configuration:");
        log.info("Lookback Hours: {}", ingestionProperties.lookbackHours());
        log.info("Max Messages: {}", ingestionProperties.maxMessages());
        log.info("Page Size: {}", ingestionProperties.pageSize());
    }

    /**
     * Runs ingestion based on configured parameters.
     *
     * @return summary statistics
     */
    public IngestionResult ingest() {
        int lookbackHours = ingestionProperties.lookbackHours();
        int maxMessages = Math.max(1, ingestionProperties.maxMessages());
        int pageSize = Math.max(1, ingestionProperties.pageSize());

        int chunkSize = Math.max(1, ingestionProperties.chunkSize());
        int chunkOverlap = Math.max(0, ingestionProperties.chunkOverlap());

        String query = buildQuery(lookbackHours);
        log.info("[Ingestion] query='{}', lookbackHours={}, maxMessages={}, pageSize={}, chunkSize={}, chunkOverlap={}",
                query, lookbackHours, maxMessages, pageSize, chunkSize, chunkOverlap);

        int processed = 0;
        int inserted = 0;
        int skipped = 0;

        String pageToken = null;

        while (processed < maxMessages) {
            long batchSize = Math.min(pageSize, maxMessages - processed);
            GmailMessagePage page = gmailClient.listMessages(query, batchSize, pageToken);

            if (page.messages() == null || page.messages().isEmpty()) {
                break;
            }

            for (var summary : page.messages()) {
                if (processed >= maxMessages) break;
                processed++;

                String messageId = summary.messageId();
                if (messageId == null || messageId.isBlank()) {
                    skipped++;
                    continue;
                }

                // De-duplication against email_embeddings (by original Gmail message id)
                if (existsInEmbeddings(messageId)) {
                    skipped++;
                    continue;
                }

                GmailMessageContent content = gmailClient.fetchMessageContent(messageId);

                String body = content.plainTextBody();
                if (body == null || body.isBlank()) {
                    skipped++;
                    continue;
                }

                List<String> chunks = TextChunker.chunk(body, chunkSize, chunkOverlap);
                if (chunks.isEmpty()) {
                    skipped++;
                    continue;
                }

                int totalChunks = chunks.size();

                for (int i = 0; i < totalChunks; i++) {
                    String chunk = chunks.get(i);
                    if (chunk == null || chunk.isBlank()) {
                        continue;
                    }

                    Map<String, Object> metadata = new HashMap<>();
                    metadata.put("message_id", content.messageId());
                    metadata.put("thread_id", content.threadId());
                    metadata.put("subject", content.subject());
                    metadata.put("from", content.from());
                    metadata.put("received_at", content.receivedAt() != null ? content.receivedAt().toString() : null);
                    metadata.put("snippet", content.snippet());
                    metadata.put("chunk_index", i);
                    metadata.put("total_chunks", totalChunks);

                    String docId = UUID.randomUUID().toString();
                    Document doc = new Document(docId, chunk, metadata);
                    vectorStore.add(List.of(doc));
                    inserted++;
                }
            }

            pageToken = page.nextPageToken();
            if (pageToken == null || pageToken.isBlank()) {
                break;
            }
        }

        return new IngestionResult(processed, inserted, skipped);
    }

    private boolean existsInEmbeddings(String messageId) {
        Integer found = jdbcClient.sql("""
                        SELECT 1
                        FROM email_embeddings
                        WHERE metadata ->> 'message_id' = ?
                        LIMIT 1
                        """)
                .param(messageId)
                .query(Integer.class)
                .optional()
                .orElse(null);
        return found != null;
    }

    private String buildQuery(int lookbackHours) {
        String base = "in:inbox -from:me";

        if (lookbackHours <= 0) {
            return base;
        }

        return base + " newer_than:" + lookbackHours + "h";
    }

    public record IngestionResult(int processed, int inserted, int skipped) {
    }
}
