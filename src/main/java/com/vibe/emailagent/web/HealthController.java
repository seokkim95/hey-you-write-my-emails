package com.vibe.emailagent.web;

import java.time.OffsetDateTime;
import java.util.Map;

import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("web")
public class HealthController {

    @GetMapping("/api/health")
    public Map<String, Object> health() {
        return Map.of(
                "status", "ok",
                "service", "emailagent",
                "time", OffsetDateTime.now().toString()
        );
    }
}
