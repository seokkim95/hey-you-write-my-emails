package com.vibe.emailagent.run;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.vibe.emailagent.gmail.GmailClient;
import com.vibe.emailagent.gmail.GmailMessageContent;
import com.vibe.emailagent.gmail.GmailMessagePage;
import com.vibe.emailagent.service.TextChunker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Extended gmail-test runner: fetch a fixed number of recent messages and write them to the
 * VectorStore backing table (email_embeddings).
 *
 * Goal
 * - Prove end-to-end ingestion works:
 *   - Gmail API read
 *   - Embedding generation via Spring AI
 *   - Persistence into Postgres/pgvector (email_embeddings)
 *
 * Notes
 * - This runner requires a working DB connection (unlike GmailFetchTestRunner).
 * - This runner is intentionally deterministic and limited.
 * - To avoid embedding token limits, long emails are chunked.
 */
@Component
@Profile("gmail-test")
public class GmailTestIngestRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(GmailTestIngestRunner.class);

    private static final int DEFAULT_MAX_MESSAGES = 1000;
    private static final int DEFAULT_PAGE_SIZE = 20;

    // Chunking defaults (character-based)
    private static final int DEFAULT_CHUNK_SIZE = 10000;
    private static final int DEFAULT_CHUNK_OVERLAP = 200;

    private final GmailClient gmailClient;
    private final VectorStore vectorStore;
    private final JdbcClient jdbcClient;
    private final ConfigurableApplicationContext applicationContext;

    public GmailTestIngestRunner(GmailClient gmailClient,
                                VectorStore vectorStore,
                                JdbcClient jdbcClient,
                                ConfigurableApplicationContext applicationContext) {
        this.gmailClient = gmailClient;
        this.vectorStore = vectorStore;
        this.jdbcClient = jdbcClient;
        this.applicationContext = applicationContext;
    }

    private void printApplicationArgs(ApplicationArguments args) {
        log.info("Application arguments:");
        for (String name : args.getOptionNames()) {
            log.info(" - {}: {}", name, args.getOptionValues(name));
        }
    }

    @Override
    public void run(ApplicationArguments args) {

        printApplicationArgs(args);

        int maxMessages = argInt(args, "gmailTestMaxMessages", DEFAULT_MAX_MESSAGES);
        int pageSize = argInt(args, "gmailTestPageSize", DEFAULT_PAGE_SIZE);

        int chunkSize = argInt(args, "gmailTestChunkSize", DEFAULT_CHUNK_SIZE);
        int chunkOverlap = argInt(args, "gmailTestChunkOverlap", DEFAULT_CHUNK_OVERLAP);

        boolean includeSent = !args.containsOption("gmailTestExcludeSent");

        // Default: ingest inbox + sent.
        // If you want only inbound mail, pass: --gmailTestExcludeSent=true
        String query = includeSent ? "(in:inbox OR in:sent)" : "in:inbox -from:me";

        int processed = 0;
        int insertedMessage = 0;
        int insertedChunks = 0;
        int skipped = 0;

        String pageToken = null;

        try {
            log.info("[GmailTestIngest] Start. query='{}', maxMessages={}, pageSize={}, chunkSize={}, chunkOverlap={}",
                    query, maxMessages, pageSize, chunkSize, chunkOverlap);

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
                    log.info("[GmailTestIngest] Processing messageId={}", messageId);

                    if (messageId == null || messageId.isBlank()) {
                        log.info("[GmailTestIngest] Skipping due to missing messageId");
                        skipped++;
                        continue;
                    }

                    // De-duplication must be based on the stable Gmail message id.
                    // PgVectorStore uses UUID for its internal document id.
                    if (existsByMessageId(messageId)) {
                        log.info("[GmailTestIngest] Skipping messageId={} (already ingested)", messageId);
                        skipped++;
                        continue;
                    }

                    GmailMessageContent content = gmailClient.fetchMessageContent(messageId);
                    String body = content.plainTextBody();
                    if (body == null || body.isBlank()) {
                        log.info("[GmailTestIngest] Skipping messageId={} due to empty body", messageId);
                        skipped++;
                        continue;
                    }

                    List<String> chunks = TextChunker.chunk(body, chunkSize, chunkOverlap);
                    if (chunks.isEmpty()) {
                        log.info("[GmailTestIngest] Skipping messageId={} due to empty chunks", messageId);
                        skipped++;
                        continue;
                    }

                    int totalChunks = chunks.size();

                    // Build all chunk documents first, then store in one batch.
                    List<Document> docs = new java.util.ArrayList<>(totalChunks);

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
                        docs.add(new Document(docId, chunk, metadata));
                    }

                    if (docs.isEmpty()) {
                        skipped++;
                        continue;
                    }

                    vectorStore.add(docs);
                    insertedMessage++;
                    insertedChunks += docs.size();

                    if (processed % 10 == 0) {
                        log.info("[GmailTestIngest] Progress: processed={}, insertedMessages={}, insertedChunks={}, skipped={}",
                                processed, insertedMessage, insertedChunks, skipped);
                    }
                }

                pageToken = page.nextPageToken();
                if (pageToken == null || pageToken.isBlank()) {
                    break;
                }
            }

            log.info("[GmailTestIngest] Completed. processed={}, insertedMessages={}, insertedChunks={}, skipped={}",
                    processed, insertedMessage, insertedChunks, skipped);
        } finally {
            applicationContext.close();
        }
    }

    /**
     * Checks if the Gmail message has already been ingested.
     *
     * We store Gmail's message id in metadata.message_id.
     */
    private boolean existsByMessageId(String messageId) {
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

    private static int argInt(ApplicationArguments args, String key, int defaultValue) {
        if (!args.containsOption(key)) {
            return defaultValue;
        }
        try {
            List<String> values = args.getOptionValues(key);
            if (values == null || values.isEmpty()) {
                return defaultValue;
            }
            return Integer.parseInt(values.get(0));
        } catch (Exception e) {
            return defaultValue;
        }
    }
}
