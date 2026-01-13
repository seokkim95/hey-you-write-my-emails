package com.vibe.emailagent.retry;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * AOP implementation for {@link ExponentialRetry}.
 *
 * Why AOP
 * - Lets us add retry behavior without coupling business logic to retry mechanics.
 * - Keeps the OpenAI call site clean.
 */
@Aspect
@Component
public class ExponentialRetryAspect {

    private static final Logger log = LoggerFactory.getLogger(ExponentialRetryAspect.class);

    @Around("@annotation(exponentialRetry)")
    public Object around(ProceedingJoinPoint pjp, ExponentialRetry exponentialRetry) throws Throwable {
        int maxAttempts = Math.max(1, exponentialRetry.maxAttempts());
        long initialBackoffMillis = Math.max(0, exponentialRetry.initialBackoffMillis());

        Throwable last = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return pjp.proceed();
            } catch (Throwable t) {
                last = t;

                boolean shouldRetry = attempt < maxAttempts && isRetryable(t);
                if (!shouldRetry) {
                    throw t;
                }

                long sleepMillis = calculateBackoff(initialBackoffMillis, attempt);

                log.warn("[Retry] {} attempt {}/{} failed: {}. Retrying in {}ms",
                        pjp.getSignature().toShortString(), attempt, maxAttempts, safeMessage(t), sleepMillis);

                try {
                    Thread.sleep(sleepMillis);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw t;
                }
            }
        }

        // Should never happen because we either return or throw above.
        throw last;
    }

    /**
     * Backoff strategy: initial * 2^(attempt-1)
     * - attempt=1 -> initial
     * - attempt=2 -> initial*2
     * - attempt=3 -> initial*4
     */
    private static long calculateBackoff(long initial, int attempt) {
        int exponent = Math.max(0, attempt - 1);
        long multiplier = 1L << Math.min(exponent, 20); // cap to avoid overflow
        return initial * multiplier;
    }

    /**
     * Conservative retry filter.
     *
     * We retry only when it looks like a transient network/transport error.
     */
    private static boolean isRetryable(Throwable t) {
        String m = safeMessage(t).toLowerCase();

        // Common transient network markers
        if (m.contains("goaway")
                || m.contains("i/o error")
                || m.contains("connection reset")
                || m.contains("connection closed")
                || m.contains("timeout")
                || m.contains("timed out")
                || m.contains("502")
                || m.contains("503")
                || m.contains("504")) {
            return true;
        }

        // Walk the cause chain as well.
        Throwable cause = t.getCause();
        if (cause != null && cause != t) {
            return isRetryable(cause);
        }

        return false;
    }

    private static String safeMessage(Throwable t) {
        if (t == null) return "";
        String msg = t.getMessage();
        return msg == null ? t.getClass().getSimpleName() : msg;
    }
}

