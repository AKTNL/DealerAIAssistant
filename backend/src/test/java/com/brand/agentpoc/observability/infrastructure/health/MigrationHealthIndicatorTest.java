package com.brand.agentpoc.observability.infrastructure.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationInfoService;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.actuate.health.Status;

class MigrationHealthIndicatorTest {

    @Test
    void reportsUpWhenFlywayIsDisabled() {
        ObjectProvider<Flyway> provider = provider(null);

        assertThat(new MigrationHealthIndicator(provider).health().getStatus()).isEqualTo(Status.UP);
    }

    @Test
    void reportsOutOfServiceWhenMigrationsArePending() {
        Flyway flyway = mock(Flyway.class);
        MigrationInfoService info = mock(MigrationInfoService.class);
        MigrationInfo current = mock(MigrationInfo.class);
        when(flyway.info()).thenReturn(info);
        when(info.current()).thenReturn(current);
        when(current.getVersion()).thenReturn(MigrationVersion.fromVersion("11"));
        when(info.pending()).thenReturn(new MigrationInfo[]{mock(MigrationInfo.class)});

        var health = new MigrationHealthIndicator(provider(flyway)).health();

        assertThat(health.getStatus()).isEqualTo(Status.OUT_OF_SERVICE);
        assertThat(health.getDetails()).containsEntry("pendingCount", 1);
    }

    @Test
    void reportsUpWhenMigrationsAreCurrent() {
        Flyway flyway = mock(Flyway.class);
        MigrationInfoService info = mock(MigrationInfoService.class);
        when(flyway.info()).thenReturn(info);
        when(info.pending()).thenReturn(new MigrationInfo[0]);

        assertThat(new MigrationHealthIndicator(provider(flyway)).health().getStatus()).isEqualTo(Status.UP);
    }

    @Test
    void reportsDownWhenMigrationInspectionFails() {
        Flyway flyway = mock(Flyway.class);
        when(flyway.info()).thenThrow(new IllegalStateException("migration metadata unavailable"));

        var health = new MigrationHealthIndicator(provider(flyway)).health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails())
                .containsEntry("state", "check_failed")
                .containsEntry("reason", "IllegalStateException");
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<Flyway> provider(Flyway flyway) {
        ObjectProvider<Flyway> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(flyway);
        return provider;
    }
}
