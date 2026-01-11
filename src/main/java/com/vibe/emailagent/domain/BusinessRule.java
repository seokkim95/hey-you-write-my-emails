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
 * 최신 비즈니스 규칙(가격/정책/공지 등)을 저장하는 엔티티.
 *
 * RAG에서 가장 중요한 원칙(요구사항)
 * - 과거 이메일 히스토리(학습/기억)와 충돌할 경우 "최신 규칙"이 항상 우선합니다.
 *
 * 현재 스키마는 단순화를 위해 key -> content 형태(최신 값 1개)로 구성했습니다.
 * - 더 정교한 버전 관리(유효 기간, 버전, 변경 사유 등)는 추후 business_rules(버전 테이블)로 확장 가능
 */
@Entity
@Table(name = "business_rule")
public class BusinessRule {

    /**
     * 내부 식별자(PK).
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 규칙의 키(식별자).
     * 예:
     * - "pricing.current"
     * - "policy.refund"
     * - "support.sla"
     *
     * unique=true 이므로 테이블에는 key당 1개 row만 존재.
     */
    @Column(name = "rule_key", nullable = false, unique = true)
    private String ruleKey;

    /**
     * 규칙의 내용(최신값).
     * - 텍스트/마크다운/JSON 등 자유 형식으로 저장 가능
     * - LLM 프롬프트에 바로 넣기 쉬운 형태로 유지하는 것이 실무적으로 유리합니다.
     */
    @Column(name = "rule_content", nullable = false, columnDefinition = "text")
    private String ruleContent;

    /**
     * 마지막 갱신 시각.
     * - 규칙 충돌 해결 시 "최신" 여부를 판단하거나
     * - 감사/운영 측면에서 변경 시점을 추적할 때 사용합니다.
     */
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    /**
     * JPA 기본 생성자.
     */
    protected BusinessRule() {
    }

    public BusinessRule(String ruleKey, String ruleContent) {
        this.ruleKey = ruleKey;
        this.ruleContent = ruleContent;
    }

    /**
     * INSERT 직전에 updatedAt을 자동 설정합니다.
     * - DB default(now())로도 커버되지만, 애플리케이션 계층에서 즉시 일관된 값을 갖게 하기 위해 설정했습니다.
     */
    @PrePersist
    void onCreate() {
        this.updatedAt = OffsetDateTime.now();
    }

    /**
     * UPDATE 직전에 updatedAt을 자동 갱신합니다.
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
     * ruleKey는 비즈니스 식별자이므로 일반적으로 변경하지 않는 것을 권장합니다.
     * - 내용만 바꾸는 사용 패턴을 가정
     */
    public void setRuleContent(String ruleContent) {
        this.ruleContent = ruleContent;
    }
}
