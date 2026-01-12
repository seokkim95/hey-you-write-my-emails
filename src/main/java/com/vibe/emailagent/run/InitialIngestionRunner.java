package com.vibe.emailagent.run;

import com.vibe.emailagent.service.EmailIngestionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * One-off runner for ingestion mode (initial + incremental ingestion).
 */
@Component
@Profile({"ingest"})
public class InitialIngestionRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(InitialIngestionRunner.class);

    private final EmailIngestionService ingestionService;
    private final ConfigurableApplicationContext applicationContext;

    public InitialIngestionRunner(EmailIngestionService ingestionService,
                                 ConfigurableApplicationContext applicationContext) {
        this.ingestionService = ingestionService;
        this.applicationContext = applicationContext;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            log.info("[Ingestion] Job started.");

            EmailIngestionService.IngestionResult result = ingestionService.ingest();
            log.info("[Ingestion] Job finished. processed={}, inserted={}, skipped={}",
                    result.processed(), result.inserted(), result.skipped());
        } finally {
            applicationContext.close();
        }
    }
}
