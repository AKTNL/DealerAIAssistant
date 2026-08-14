package com.brand.agentpoc.reporting.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.brand.agentpoc.reporting.domain.ReportDeliveryStatus;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest
class ReportDeliveryPersistenceTest {

    private static final Instant NOW = Instant.parse("2026-08-14T02:00:00Z");

    @Autowired
    private ReportDeliveryRepository repository;

    @Test
    void uniqueJobChannelRecipientPreventsDuplicateMaterialization() {
        repository.saveAndFlush(delivery("delivery-key-1"));

        assertThatThrownBy(() -> repository.saveAndFlush(delivery("delivery-key-2")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void lockedClaimPersistsLeaseAndAttempt() {
        Long id = repository.saveAndFlush(delivery("delivery-key-1")).getId();
        ReportDeliveryEntity locked = repository.findByIdForUpdate(id).orElseThrow();

        locked.claim("worker-a", NOW, NOW.plusSeconds(300));
        repository.saveAndFlush(locked);

        ReportDeliveryEntity stored = repository.findById(id).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(ReportDeliveryStatus.SENDING);
        assertThat(stored.getAttempt()).isEqualTo(1);
        assertThat(stored.getLeaseOwner()).isEqualTo("worker-a");
    }

    private ReportDeliveryEntity delivery(String key) {
        return new ReportDeliveryEntity(
                11L, 9L, 7L, 2L, "draft-1", 3L, "email", key, NOW);
    }
}
