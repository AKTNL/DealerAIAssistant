package com.brand.agentpoc.reporting.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.brand.agentpoc.reporting.domain.ReportGenerationJobStatus;
import com.brand.agentpoc.reporting.domain.ReportScope;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ReportGenerationJobEntityTest {

    private static final Instant START = Instant.parse("2026-08-14T01:00:00Z");

    @Test
    void claimsRetriesAndCompletesWithBoundedAttemptCount() {
        ReportGenerationJobEntity job = readyJob();

        job.claim("worker-a", START, START.plusSeconds(300));
        assertThat(job.getStatus()).isEqualTo(ReportGenerationJobStatus.RUNNING);
        assertThat(job.getAttempt()).isEqualTo(1);
        assertThat(job.ownedBy("worker-a")).isTrue();

        job.markRetry("transient_failure", START.plusSeconds(300), START);
        assertThat(job.getStatus()).isEqualTo(ReportGenerationJobStatus.RETRY_WAIT);
        assertThat(job.getErrorCode()).isEqualTo("TRANSIENT_FAILURE");

        job.claim("worker-b", START.plusSeconds(300), START.plusSeconds(600));
        job.markSucceeded("draft-1", START.plusSeconds(301));
        assertThat(job.getStatus()).isEqualTo(ReportGenerationJobStatus.SUCCEEDED);
        assertThat(job.getReportDraftId()).isEqualTo("draft-1");
        assertThat(job.getLeaseOwner()).isNull();
    }

    @Test
    void expiredLeaseCanBeRecoveredAndManuallyReplayedAfterFailure() {
        ReportGenerationJobEntity job = readyJob();
        job.claim("worker-a", START, START.plusSeconds(300));
        job.recoverExpiredLease(START.plusSeconds(301));

        assertThat(job.getStatus()).isEqualTo(ReportGenerationJobStatus.READY);
        assertThat(job.getLeaseOwner()).isNull();
        assertThat(job.isClaimable(START.plusSeconds(301))).isTrue();

        job.claim("worker-b", START.plusSeconds(302), START.plusSeconds(602));
        job.markPermanentFailure("organization_scope_revoked", START.plusSeconds(303));
        job.manualRetry(START.plusSeconds(304), "replay-trace");

        assertThat(job.getStatus()).isEqualTo(ReportGenerationJobStatus.READY);
        assertThat(job.getAttempt()).isZero();
        assertThat(job.getErrorCode()).isNull();
        assertThat(job.getTraceId()).isEqualTo("replay-trace");
    }

    @Test
    void rejectsInvalidStateTransitionsAndSanitizesErrorCodes() {
        ReportGenerationJobEntity job = readyJob();

        assertThatThrownBy(() -> job.markSucceeded("draft-1", START))
                .isInstanceOf(IllegalStateException.class);

        job.claim("worker-a", START, START.plusSeconds(300));
        job.markPermanentFailure("contains spaces", START.plusSeconds(1));

        assertThat(job.getErrorCode()).isEqualTo("UNKNOWN_FAILURE");
        assertThatCode(() -> job.manualRetry(START, "trace"))
                .doesNotThrowAnyException();
    }

    private ReportGenerationJobEntity readyJob() {
        return new ReportGenerationJobEntity(
                9L, 7L, 2L, START, "9:" + START,
                "daily", ReportScope.organization(java.util.Set.of(10L)), "en", "",
                ReportGenerationJobStatus.READY, "trace-1", START);
    }
}
