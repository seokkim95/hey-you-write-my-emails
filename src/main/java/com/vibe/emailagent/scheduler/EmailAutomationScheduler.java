package com.vibe.emailagent.scheduler;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.vibe.emailagent.gmail.GmailClient;
import com.vibe.emailagent.gmail.GmailMessageSummary;
import com.vibe.emailagent.service.EmailAgentService;
import com.vibe.emailagent.service.EmailDraft;

/**
 * Phase 4: 크론잡(스케줄러) + Gmail API 통합.
 *
 * 요구사항
 * 1) 1시간마다 실행
 * 2) 최근 1시간 내 도착했고 아직 "응답하지 않은" 메시지를 조회
 * 3) EmailAgentService로 답장 초안을 생성
 * 4) Gmail Drafts(users.drafts.create)로 임시 저장함에 저장
 *
 * 설계 포인트
 * - 실제 Gmail OAuth 인증/토큰 갱신은 GmailAuthProvider로 분리 (이번 Phase에서는 뼈대만)
 * - Gmail API 호출은 GmailClient 인터페이스로 캡슐화 (테스트/교체 용이)
 *
 * 운영 고려(추후)
 * - 중복 실행 방지(분산락: ShedLock 등)
 * - 실패 재시도/백오프
 * - idempotency(같은 메시지에 draft 중복 생성 방지)
 * - 메일 본문/제목 추출 및 threadId/headers(In-Reply-To) 정확한 구성
 */
@Component
public class EmailAutomationScheduler {

    private static final Logger log = LoggerFactory.getLogger(EmailAutomationScheduler.class);

    private final GmailClient gmailClient;
    private final EmailAgentService emailAgentService;

    public EmailAutomationScheduler(GmailClient gmailClient, EmailAgentService emailAgentService) {
        this.gmailClient = gmailClient;
        this.emailAgentService = emailAgentService;
    }

    /**
     * 1시간 마다 실행.
     *
     * fixedDelay 사용 이유
     * - 실행이 오래 걸리면 다음 실행은 "끝난 시점 기준"으로 1시간 뒤
     * - 운영에서는 cron 표현식으로 특정 정각 실행도 고려 가능
     */
    @Scheduled(fixedDelayString = "${emailagent.scheduler.fixed-delay-ms:3600000}")
    public void runHourly() {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime since = now.minus(1, ChronoUnit.HOURS);

        log.info("[Scheduler] Checking unreplied messages since {}", since);

        List<GmailMessageSummary> candidates = gmailClient.findUnrepliedMessagesSince(since);
        if (candidates.isEmpty()) {
            log.info("[Scheduler] No unreplied messages found.");
            return;
        }

        for (GmailMessageSummary msg : candidates) {
            try {
                // Phase 4 skeleton: currentQuestion을 snippet으로 대체
                // - 다음 단계에서 message.get payload/본문을 파싱해 더 정확한 질문 추출 필요
                String currentQuestion = msg.snippet();

                // 제목은 GmailMessageSummary에 있는 값 사용(현재는 빈 문자열 placeholder일 수 있음)
                EmailDraft draft = emailAgentService.generateDraft(msg.threadId(), currentQuestion, msg.subject());

                String draftId = gmailClient.createReplyDraft(msg.messageId(), msg.threadId(), draft.subject(), draft.body());

                log.info("[Scheduler] Draft created. messageId={}, threadId={}, draftId={}", msg.messageId(), msg.threadId(), draftId);
            } catch (Exception e) {
                // 운영에서는 특정 에러(429/5xx)만 재시도, 알림 연동 등 필요
                log.warn("[Scheduler] Failed to process messageId={}, threadId={}: {}", msg.messageId(), msg.threadId(), e.getMessage(), e);
            }
        }
    }
}

