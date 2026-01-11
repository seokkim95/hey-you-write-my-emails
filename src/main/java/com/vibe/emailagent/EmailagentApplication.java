package com.vibe.emailagent;

import com.vibe.emailagent.config.GmailProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(GmailProperties.class)
public class EmailagentApplication {

    public static void main(String[] args) {
        SpringApplication.run(EmailagentApplication.class, args);
    }

}
