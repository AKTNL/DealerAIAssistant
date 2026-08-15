package com.brand.agentpoc.observability.infrastructure.health;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationInfoService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.actuate.health.Status;
import org.springframework.stereotype.Component;

@Component
public class MigrationHealthIndicator implements HealthIndicator {

    private final ObjectProvider<Flyway> flywayProvider;

    public MigrationHealthIndicator(ObjectProvider<Flyway> flywayProvider) {
        this.flywayProvider = flywayProvider;
    }

    @Override
    public Health health() {
        Flyway flyway = flywayProvider.getIfAvailable();
        if (flyway == null) {
            return Health.up().withDetail("state", "disabled").build();
        }
        try {
            MigrationInfoService info = flyway.info();
            MigrationInfo[] pending = info.pending();
            MigrationInfo current = info.current();
            String currentVersion = current == null || current.getVersion() == null
                    ? "none"
                    : current.getVersion().getVersion();
            if (pending.length > 0) {
                return Health.status(Status.OUT_OF_SERVICE)
                        .withDetail("state", "pending")
                        .withDetail("pendingCount", pending.length)
                        .withDetail("currentVersion", currentVersion)
                        .build();
            }
            return Health.up()
                    .withDetail("state", "current")
                    .withDetail("currentVersion", currentVersion)
                    .build();
        } catch (RuntimeException exception) {
            return Health.down()
                    .withDetail("state", "check_failed")
                    .withDetail("reason", exception.getClass().getSimpleName())
                    .build();
        }
    }
}
