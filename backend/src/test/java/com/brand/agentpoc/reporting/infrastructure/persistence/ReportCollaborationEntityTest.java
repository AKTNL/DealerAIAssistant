package com.brand.agentpoc.reporting.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.brand.agentpoc.reporting.domain.ReportCollaborationStatus;
import com.brand.agentpoc.reporting.domain.ReportDraft;
import com.brand.agentpoc.reporting.domain.ReportScope;
import com.brand.agentpoc.reporting.domain.ReportType;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ReportCollaborationEntityTest {

    @Test
    void recordsForwardStatusAssignmentAndActivity() {
        ReportCollaborationEntity collaboration = new ReportCollaborationEntity(draft());
        Instant changedAt = Instant.parse("2026-08-15T01:00:00Z");

        collaboration.assign(8L, "owner", "Report Owner", changedAt);
        collaboration.changeStatus(ReportCollaborationStatus.IN_PROGRESS, changedAt.plusSeconds(1));
        collaboration.addCommentActivity(changedAt.plusSeconds(2));

        assertThat(collaboration.getAssigneeUserId()).isEqualTo(8L);
        assertThat(collaboration.getStatus()).isEqualTo(ReportCollaborationStatus.IN_PROGRESS);
        assertThat(collaboration.getActivityCount()).isEqualTo(3L);
        assertThat(collaboration.getUpdatedAt()).isEqualTo(changedAt.plusSeconds(2));
    }

    @Test
    void terminalStateRejectsEveryNewCollaborationMutation() {
        ReportCollaborationEntity collaboration = new ReportCollaborationEntity(draft());
        collaboration.changeStatus(ReportCollaborationStatus.CLOSED, Instant.parse("2026-08-15T01:00:00Z"));

        assertThatThrownBy(() -> collaboration.assign(8L, "owner", "Owner", Instant.now()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("terminal");
        assertThatThrownBy(() -> collaboration.addCommentActivity(Instant.now()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("terminal");
        assertThatThrownBy(() -> collaboration.changeStatus(ReportCollaborationStatus.IN_PROGRESS, Instant.now()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not allowed");
    }

    private ReportDraft draft() {
        return new ReportDraft(
                "report-1", ReportType.DAILY, "Daily", "en", "# Daily",
                Instant.parse("2026-08-15T00:00:00Z"), "batch-1",
                new ReportScope("ORGANIZATION", "10"), "deterministic", "v1", 7L);
    }
}
