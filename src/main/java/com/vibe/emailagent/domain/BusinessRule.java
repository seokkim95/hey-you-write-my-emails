package com.vibe.emailagent.domain;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/**
 * Entity that stores the latest business rules (pricing/policies/notices, etc.).
 *
 * The most important principles (requirements) in RAG:
 * - In case of conflict with past email history (learning/memory), the "latest rule" always takes precedence.
 *
 * The current schema is simplified to a key -> content format (1 latest value) for simplicity.
 * - More sophisticated versioning (validity period, version, reason for change, etc.) can be extended later in the business_rules (version table).
 */
@Entity
@Table(name = "business_rule")
public class BusinessRule {

    /**
     * Internal identifier (PK).
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Rule key (identifier).
     * Example:
     * - "pricing.current"
     * - "policy.refund"
     * - "support.sla"
     *
     * Since unique=true, there is only 1 row per key in the table.
     */
    @Column(name = "rule_key", nullable = false, unique = true)
    private String ruleKey;

    /**
     * Content of the rule (latest value).
     * - Can be stored in free format such as text/markdown/JSON, etc.
     * - Practically, it is advantageous to keep it in a form that is easy to put directly into the LLM prompt.
     */
    @Column(name = "rule_content", nullable = false, columnDefinition = "text")
    private String ruleContent;

    /**
     * Last updated timestamp.
     * - Used to determine "latest" status when resolving rule conflicts, or
     * - To track change points from an audit/operations perspective.
     */
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    /**
     * JPA default constructor.
     */
    protected BusinessRule() {
    }

    public BusinessRule(String ruleKey, String ruleContent) {
        this.ruleKey = ruleKey;
        this.ruleContent = ruleContent;
    }

    /**
     * Automatically sets updatedAt before INSERT.
     * - DB default(now()) can cover this too, but setting it in the application layer makes the value
     *   immediately available and consistent.
     */
    @PrePersist
    void onCreate() {
        this.updatedAt = OffsetDateTime.now();
    }

    /**
     * Automatically updates updatedAt before UPDATE.
     */
    @PreUpdate
    void onUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getRuleKey() {
        return ruleKey;
    }

    public String getRuleContent() {
        return ruleContent;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    /**
     * In this model, ruleKey is the business identifier and should generally be immutable.
     * Only ruleContent is expected to change.
     */
    public void setRuleContent(String ruleContent) {
        this.ruleContent = ruleContent;
    }
}
