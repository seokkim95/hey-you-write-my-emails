package com.vibe.emailagent.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.vibe.emailagent.domain.BusinessRule;

/**
 * BusinessRule JPA repository.
 *
 * Responsibility
 * - Loads the latest business rules that must always be included when drafting.
 *
 * Notes
 * - Current model is "single latest value per key", so findAll() is safe and simple.
 * - If rule count grows, consider caching (Caffeine/Redis) or key-prefix queries.
 */
public interface BusinessRuleRepository extends JpaRepository<BusinessRule, Long> {

    /**
     * Lookup a rule by key (e.g., "pricing.current").
     */
    Optional<BusinessRule> findByRuleKey(String ruleKey);
}
