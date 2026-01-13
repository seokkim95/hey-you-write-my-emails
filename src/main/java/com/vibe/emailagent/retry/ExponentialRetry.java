package com.vibe.emailagent.retry;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as retryable with exponential backoff.
 *
 * Intended use
 * - Wrap external I/O calls that can fail transiently (e.g., OpenAI API requests).
 *
 * Defaults
 * - initialBackoffMillis: 3000 (3 seconds)
 * - maxAttempts: 3
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ExponentialRetry {

    /**
     * Total number of attempts (initial try + retries).
     */
    int maxAttempts() default 3;

    /**
     * Initial backoff in milliseconds.
     */
    long initialBackoffMillis() default 1000;
}

