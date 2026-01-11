package com.vibe.emailagent.domain;

import com.pgvector.PGvector;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 과거 이메일/대화 히스토리를 저장하는 엔티티.
 *
 * RAG 관점에서 이 테이블은 "우리 시스템의 장기 기억" 역할을 합니다.
 * - threadId 기준으로 특정 스레드의 누적 대화를 불러오고
 * - embedding(vector) 컬럼을 통해 유사도 검색(semantic search)을 수행합니다.
 *
 * 설계 메모
 * - 실제 Gmail 메시지를 그대로 저장하기보다, 검색/생성에 필요한 최소 데이터(snippet, fullContent 등)로 시작했습니다.
 * - 추후 messageId, sender, subject, createdAt, metadata(JSON) 등을 추가해도 좋습니다.
 */
@Entity
@Table(name = "email_history")
public class EmailHistory {

    /**
     * 내부 식별자(서로게이트 키).
     * - threadId는 비즈니스 키지만, 변경/중복 가능성을 고려해 PK로 쓰지 않습니다.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Gmail thread id.
     * - 한 이메일 스레드(왕복 대화)를 식별합니다.
     * - 같은 threadId로 여러 레코드(메시지/스냅샷)가 쌓일 수 있다고 가정합니다.
     */
    @Column(name = "thread_id", nullable = false)
    private String threadId;

    /**
     * 원문 전체가 길 때 빠르게 미리 보기/후보 컨텍스트에 사용되는 짧은 요약 텍스트.
     * - Gmail API의 snippet과 유사한 개념
     */
    @Column(name = "snippet", columnDefinition = "text")
    private String snippet;

    /**
     * 이메일/대화 원문 전체 텍스트.
     * - RAG에서 실제 프롬프트 컨텍스트로 들어갈 수 있는 본문
     * - HTML -> text 정제/PII 마스킹 등은 추후 ingestion 단계에서 처리하는 것을 권장
     */
    @Column(name = "full_content", columnDefinition = "text")
    private String fullContent;

    /**
     * pgvector 컬럼.
     *
     * 목적
     * - fullContent(또는 chunk)의 임베딩 벡터를 저장하여, pgvector index를 통한 유사도 검색을 수행합니다.
     *
     * 구현 메모
     * - com.pgvector:pgvector 라이브러리의 PGvector 타입을 사용
     * - Hibernate 6/Boot 3.x 환경에서 vector는 표준 JDBC 타입이 아니므로 SqlTypes.OTHER로 매핑
     * - columnDefinition에 vector(차원)을 명시해야 PostgreSQL(pgvector)이 올바르게 인식합니다.
     *
     * 주의
     * - dimensions는 embedding 모델과 반드시 일치해야 합니다. (예: 1536)
     * - null 허용: ingestion이 아직 안 된 레코드는 embedding이 null일 수 있습니다.
     */
    @JdbcTypeCode(SqlTypes.OTHER)
    @Column(name = "embedding", columnDefinition = "vector(1536)")
    private PGvector embedding;

    /**
     * JPA용 protected 기본 생성자.
     */
    protected EmailHistory() {
    }

    /**
     * 애플리케이션 코드에서 사용하는 생성자.
     *
     * @param threadId Gmail thread id
     * @param snippet  짧은 미리보기
     * @param fullContent 원문 전체
     * @param embedding 임베딩(없다면 null 가능)
     */
    public EmailHistory(String threadId, String snippet, String fullContent, PGvector embedding) {
        this.threadId = threadId;
        this.snippet = snippet;
        this.fullContent = fullContent;
        this.embedding = embedding;
    }

    public Long getId() {
        return id;
    }

    public String getThreadId() {
        return threadId;
    }

    public String getSnippet() {
        return snippet;
    }

    public String getFullContent() {
        return fullContent;
    }

    public PGvector getEmbedding() {
        return embedding;
    }

    public void setSnippet(String snippet) {
        this.snippet = snippet;
    }

    public void setFullContent(String fullContent) {
        this.fullContent = fullContent;
    }

    public void setEmbedding(PGvector embedding) {
        this.embedding = embedding;
    }
}
