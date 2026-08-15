package com.brand.agentpoc.reporting.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ReportCollaborationStatusTest {

    @Test
    void allowsOnlyForwardWorkflowTransitions() {
        assertThat(ReportCollaborationStatus.OPEN.canTransitionTo(ReportCollaborationStatus.IN_PROGRESS)).isTrue();
        assertThat(ReportCollaborationStatus.OPEN.canTransitionTo(ReportCollaborationStatus.CLOSED)).isTrue();
        assertThat(ReportCollaborationStatus.IN_PROGRESS.canTransitionTo(ReportCollaborationStatus.RESOLVED)).isTrue();
        assertThat(ReportCollaborationStatus.IN_PROGRESS.canTransitionTo(ReportCollaborationStatus.CLOSED)).isTrue();

        assertThat(ReportCollaborationStatus.IN_PROGRESS.canTransitionTo(ReportCollaborationStatus.OPEN)).isFalse();
        assertThat(ReportCollaborationStatus.RESOLVED.canTransitionTo(ReportCollaborationStatus.IN_PROGRESS)).isFalse();
        assertThat(ReportCollaborationStatus.CLOSED.canTransitionTo(ReportCollaborationStatus.OPEN)).isFalse();
    }

    @Test
    void rejectsUnknownStatusValues() {
        assertThatThrownBy(() -> ReportCollaborationStatus.parse("waiting"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported");
    }
}
