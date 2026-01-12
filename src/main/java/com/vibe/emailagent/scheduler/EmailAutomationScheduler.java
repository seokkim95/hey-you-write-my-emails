package com.vibe.emailagent.scheduler;

/**
 * (Deprecated)
 *
 * Earlier versions tried to run the workflow via @Scheduled every hour.
 * The current design runs as a one-off batch job triggered by an external scheduler (e.g., AWS EventBridge).
 *
 * Actual processing is handled by ApplicationRunner implementations:
 * - com.vibe.emailagent.run.EmailAutomationRunner (default mode)
 * - com.vibe.emailagent.run.InitialIngestionRunner (ingest profile)
 *
 * TODO: You can delete this class after the design is fully stabilized.
 */
public final class EmailAutomationScheduler {
    private EmailAutomationScheduler() {
    }
}
