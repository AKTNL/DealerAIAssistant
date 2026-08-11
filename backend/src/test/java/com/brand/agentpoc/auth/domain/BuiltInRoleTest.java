package com.brand.agentpoc.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;

class BuiltInRoleTest {

    @Test
    void definesTheApprovedBuiltInPermissionMatrix() {
        assertThat(BuiltInRole.ADMIN.permissions()).containsExactlyInAnyOrder(PermissionKey.values());
        assertThat(BuiltInRole.ANALYST.permissions()).containsExactlyInAnyOrder(
                PermissionKey.DASHBOARD_READ,
                PermissionKey.DATA_READ,
                PermissionKey.CHAT_USE,
                PermissionKey.KNOWLEDGE_QUERY,
                PermissionKey.REPORT_READ,
                PermissionKey.REPORT_GENERATE,
                PermissionKey.MODEL_CONFIG_TEST
        );
        assertThat(BuiltInRole.VIEWER.permissions()).containsExactlyInAnyOrder(
                PermissionKey.DASHBOARD_READ,
                PermissionKey.DATA_READ,
                PermissionKey.REPORT_READ
        );
        assertThat(BuiltInRole.ANALYST.permissions()).doesNotContainAnyElementsOf(Set.of(
                PermissionKey.USER_MANAGE,
                PermissionKey.ROLE_MANAGE
        ));
    }
}
