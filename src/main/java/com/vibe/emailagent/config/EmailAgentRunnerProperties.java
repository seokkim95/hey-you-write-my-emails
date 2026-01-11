package com.vibe.emailagent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 1회 실행 배치(Runner) 모드 설정.
 *
 * emailagent.runner.*
 */
@ConfigurationProperties(prefix = "emailagent.runner")
public record EmailAgentRunnerProperties(
        int lookbackHours
) {
}

