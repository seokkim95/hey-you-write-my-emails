package com.vibe.emailagent.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.vibe.emailagent.domain.EmailHistory;

/**
 * EmailHistory JPA Repository.
 *
 * 이 레이어는 "DB에서 thread 기반 대화 내역"을 안전하게 가져오는 책임을 갖습니다.
 *
 * 팁
 * - 대화가 매우 길어질 수 있으므로, 추후에는 createdAt 추가 + 기간 필터 + page 처리로 확장하는 것을 권장합니다.
 */
public interface EmailHistoryRepository extends JpaRepository<EmailHistory, Long> {

    /**
     * 특정 threadId에 대한 히스토리를 모두 가져옵니다.
     *
     * 정렬(ORDER BY id ASC) 이유
     * - 현재 스키마에는 createdAt이 없어서, 일단 PK 증가 순서를 "대략적인 입력 순서"로 가정합니다.
     * - 실무적으로는 createdAt(메일 수신 시각)을 컬럼으로 추가하고 그 기준으로 정렬하는 것이 더 안전합니다.
     */
    List<EmailHistory> findAllByThreadIdOrderByIdAsc(String threadId);
}
