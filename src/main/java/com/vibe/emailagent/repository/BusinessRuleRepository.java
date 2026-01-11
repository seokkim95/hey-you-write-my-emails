package com.vibe.emailagent.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.vibe.emailagent.domain.BusinessRule;

/**
 * BusinessRule JPA Repository.
 *
 * 역할
 * - 답변 생성 시 항상 포함되어야 하는 "최신 비즈니스 지식"을 가져옵니다.
 *
 * 주의/확장
 * - 현재는 key당 1 row(최신값) 모델이므로 findAll()이 단순하고 안전합니다.
 * - 규칙 수가 많아지면 캐싱(Caffeine/Redis) 또는 ruleKey prefix별 조회 등을 고려하세요.
 */
public interface BusinessRuleRepository extends JpaRepository<BusinessRule, Long> {

    /**
     * 특정 ruleKey의 규칙을 조회합니다.
     * - 예: "pricing.current"만 필요한 경우
     */
    Optional<BusinessRule> findByRuleKey(String ruleKey);
}
