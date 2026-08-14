package com.brand.agentpoc.reporting.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.brand.agentpoc.reporting.domain.ReportGenerationJobStatus;
import com.brand.agentpoc.reporting.domain.ReportScope;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ReportGenerationJobPersistenceTest {

    private static final Instant SCHEDULED_AT = Instant.parse("2026-08-14T01:00:00Z");

    @Autowired
    private ReportGenerationJobRepository repository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private ExecutorService executor;

    @BeforeEach
    void setUp() {
        executor = Executors.newFixedThreadPool(2);
        new TransactionTemplate(transactionManager).executeWithoutResult(ignored -> repository.deleteAll());
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    @Test
    void concurrentWindowMaterializationPersistsExactlyOneJob() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        Future<Boolean> first = executor.submit(() -> persistWindow(start));
        Future<Boolean> second = executor.submit(() -> persistWindow(start));

        start.countDown();
        List<Boolean> outcomes = List.of(first.get(), second.get());

        assertThat(outcomes).containsExactlyInAnyOrder(true, false);
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void lockedClaimRoundTripsLeaseAndAttempt() {
        TransactionTemplate transactions = new TransactionTemplate(transactionManager);
        Long id = transactions.execute(ignored -> repository.saveAndFlush(job()).getId());

        transactions.executeWithoutResult(ignored -> {
            ReportGenerationJobEntity locked = repository.findByIdForUpdate(id).orElseThrow();
            locked.claim("worker-a", SCHEDULED_AT, SCHEDULED_AT.plusSeconds(300));
            repository.saveAndFlush(locked);
        });

        ReportGenerationJobEntity stored = repository.findById(id).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(ReportGenerationJobStatus.RUNNING);
        assertThat(stored.getAttempt()).isEqualTo(1);
        assertThat(stored.getLeaseOwner()).isEqualTo("worker-a");
    }

    private boolean persistWindow(CountDownLatch start) throws InterruptedException {
        start.await();
        try {
            new TransactionTemplate(transactionManager)
                    .executeWithoutResult(ignored -> repository.saveAndFlush(job()));
            return true;
        } catch (DataIntegrityViolationException duplicate) {
            return false;
        }
    }

    private ReportGenerationJobEntity job() {
        return new ReportGenerationJobEntity(
                9L, 7L, 2L, SCHEDULED_AT, "9:" + SCHEDULED_AT,
                "daily", new ReportScope("ORGANIZATION", "10"), "en", "",
                ReportGenerationJobStatus.READY, "trace-job", SCHEDULED_AT);
    }
}
