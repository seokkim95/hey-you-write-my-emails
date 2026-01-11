package com.vibe.emailagent;

import com.vibe.emailagent.config.EmailAgentRunnerProperties;
import com.vibe.emailagent.config.GmailProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({GmailProperties.class, EmailAgentRunnerProperties.class})
public class EmailagentApplication {

    public static void main(String[] args) {
        // Batch-style application: no embedded web server (see spring.main.web-application-type=none).
        SpringApplication.run(EmailagentApplication.class, args);
    }

}
