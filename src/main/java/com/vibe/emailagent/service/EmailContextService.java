package com.vibe.emailagent.service;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;

import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vibe.emailagent.domain.BusinessRule;
import com.vibe.emailagent.repository.BusinessRuleRepository;

/**
 * Collects all context needed for RAG-based email drafting.
 *
 * Single source of truth
 * - Thread reconstruction is performed by querying the email_embeddings table directly.
 * - Similarity search is performed via Spring AI VectorStore (pgvector).
 */
@Service
@Profile("automation")
public class EmailContextService {

    private final BusinessRuleRepository businessRuleRepository;
    private final VectorStore vectorStore;
    private final JdbcClient jdbcClient;

    public EmailContextService(BusinessRuleRepository businessRuleRepository,
                              VectorStore vectorStore,
                              JdbcClient jdbcClient) {
        this.businessRuleRepository = businessRuleRepository;
        this.vectorStore = vectorStore;
        this.jdbcClient = jdbcClient;
    }

    @Transactional(readOnly = true)
    public EmailContext collectContext(String threadId, String currentQuestion) {
        List<EmailMessage> threadConversation = loadThreadConversation(threadId);
        List<EmailMessage> similarHistory = loadSimilarHistory(currentQuestion);
        List<BusinessRule> businessRules = businessRuleRepository.findAll();

        return new EmailContext(threadId, currentQuestion, threadConversation, similarHistory, businessRules);
    }

    /**
     * Deterministically reconstructs a thread from email_embeddings.
     */
    List<EmailMessage> loadThreadConversation(String threadId) {
        return jdbcClient.sql("""
                        SELECT
                          id,
                          created_at,
                          content,
                          metadata
                        FROM email_embeddings
                        WHERE metadata ->> 'thread_id' = ?
                        ORDER BY created_at ASC
                        """)
                .param(threadId)
                .query((rs, rowNum) -> {
                    String docId = rs.getString("id");
                    OffsetDateTime createdAt = rs.getObject("created_at", OffsetDateTime.class);
                    String content = rs.getString("content");

                    String subject = null;
                    String from = null;

                    // If you need subject/from reliably as columns, we can denormalize into columns later.
                    // For now, thread reconstruction uses metadata.

                    return new EmailMessage(
                            docId,
                            threadId,
                            subject,
                            from,
                            createdAt,
                            null,
                            content,
                            null
                    );
                })
                .list();
    }

    List<EmailMessage> loadSimilarHistory(String query) {
        var docs = vectorStore.similaritySearch(query);
        if (docs == null) {
            docs = Collections.emptyList();
        }

        return docs.stream()
                .map(d -> new EmailMessage(
                        d.getId(),
                        (String) d.getMetadata().getOrDefault("thread_id", ""),
                        (String) d.getMetadata().getOrDefault("subject", ""),
                        (String) d.getMetadata().getOrDefault("from", ""),
                        null,
                        (String) d.getMetadata().getOrDefault("snippet", null),
                        d.getText(),
                        d.getMetadata()
                ))
                .toList();
    }
}
