package com.brand.agentpoc.modelusage.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.brand.agentpoc.modelusage.domain.ModelBudgetExceededException;
import com.brand.agentpoc.modelusage.domain.ModelUsageContext;
import com.brand.agentpoc.modelusage.domain.ModelUsageScenario;
import com.brand.agentpoc.modelusage.infrastructure.persistence.ModelBudgetPolicyEntity;
import com.brand.agentpoc.modelusage.infrastructure.persistence.ModelBudgetPolicyRepository;
import com.brand.agentpoc.modelusage.infrastructure.persistence.ModelBudgetReservationRepository;
import com.brand.agentpoc.modelusage.infrastructure.persistence.ModelUsageEventRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
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
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@DataJpaTest
@Import({ModelBudgetAdmissionService.class, ModelBudgetAdmissionPersistenceTest.FixedClockConfiguration.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ModelBudgetAdmissionPersistenceTest {

    private static final Instant NOW = Instant.parse("2026-08-15T02:00:00Z");

    @Autowired
    private ModelBudgetAdmissionService service;

    @Autowired
    private ModelBudgetPolicyRepository policyRepository;

    @Autowired
    private ModelBudgetReservationRepository reservationRepository;

    @Autowired
    private ModelUsageEventRepository eventRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private ExecutorService executor;

    @BeforeEach
    void setUp() {
        executor = Executors.newFixedThreadPool(2);
        new TransactionTemplate(transactionManager).executeWithoutResult(ignored -> {
            reservationRepository.deleteAll();
            eventRepository.deleteAll();
            policyRepository.deleteAll();
        });
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    @Test
    void allowsTheExactBoundaryAndRejectsTheNextReservation() {
        savePolicy("10.00000000", "5.00000000", true);

        assertThat(service.admit("call-1", context()).reservationId()).isNotNull();
        assertThat(service.admit("call-2", context()).reservationId()).isNotNull();
        assertThatThrownBy(() -> service.admit("call-3", context()))
                .isInstanceOf(ModelBudgetExceededException.class);
        assertThat(reservationRepository.count()).isEqualTo(2);
    }

    @Test
    void serializesConcurrentAdmissionsAtTheBudgetBoundary() throws Exception {
        savePolicy("10.00000000", "6.00000000", true);
        CountDownLatch start = new CountDownLatch(1);
        Future<Boolean> first = executor.submit(() -> admit("call-a", start));
        Future<Boolean> second = executor.submit(() -> admit("call-b", start));

        start.countDown();

        assertThat(List.of(first.get(), second.get())).containsExactlyInAnyOrder(true, false);
        assertThat(reservationRepository.count()).isEqualTo(1);
    }

    @Test
    void softBudgetNeverCreatesAReservationOrRejects() {
        savePolicy("1.00000000", "1.00000000", false);

        assertThat(service.admit("call-1", context()).reservationId()).isNull();
        assertThat(reservationRepository.count()).isZero();
    }

    private boolean admit(String callKey, CountDownLatch start) throws InterruptedException {
        start.await();
        try {
            service.admit(callKey, context());
            return true;
        } catch (ModelBudgetExceededException exception) {
            return false;
        }
    }

    private void savePolicy(String limit, String reservation, boolean hardLimit) {
        new TransactionTemplate(transactionManager).executeWithoutResult(ignored -> policyRepository.saveAndFlush(
                new ModelBudgetPolicyEntity(
                        7L, new BigDecimal(limit), 80, hardLimit, true,
                        new BigDecimal(reservation), "USD", NOW)));
    }

    private ModelUsageContext context() {
        return new ModelUsageContext(7L, 2L, ModelUsageScenario.CHAT,
                "openai-compatible", "gpt-test", "trace-1", false);
    }

    @TestConfiguration
    static class FixedClockConfiguration {
        @Bean
        Clock clock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }
}
