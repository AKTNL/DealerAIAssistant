package com.brand.agentpoc.observability.infrastructure.health;

import com.brand.agentpoc.config.AppProperties;
import com.brand.agentpoc.knowledge.application.KnowledgeIndex;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.actuate.health.Status;
import org.springframework.stereotype.Component;

@Component
public class KnowledgeHealthIndicator implements HealthIndicator {

    private final KnowledgeIndex knowledgeIndex;
    private final AppProperties appProperties;

    public KnowledgeHealthIndicator(KnowledgeIndex knowledgeIndex, AppProperties appProperties) {
        this.knowledgeIndex = knowledgeIndex;
        this.appProperties = appProperties;
    }

    @Override
    public Health health() {
        try {
            if (!knowledgeIndex.isAvailable()) {
                return Health.status(Status.OUT_OF_SERVICE)
                        .withDetail("state", "not_initialized")
                        .withDetail("store", appProperties.getKnowledge().getVectorStore())
                        .build();
            }
            return Health.up()
                    .withDetail("state", "available")
                    .withDetail("store", appProperties.getKnowledge().getVectorStore())
                    .build();
        } catch (RuntimeException exception) {
            return Health.down()
                    .withDetail("state", "check_failed")
                    .withDetail("reason", exception.getClass().getSimpleName())
                    .build();
        }
    }
}
