package com.brand.agentpoc.auth.domain;

import java.util.EnumSet;
import java.util.Set;

public enum BuiltInRole {
    ADMIN("Administrator", EnumSet.allOf(PermissionKey.class)),
    ANALYST("Analyst", EnumSet.of(
            PermissionKey.DASHBOARD_READ,
            PermissionKey.DATA_READ,
            PermissionKey.CHAT_USE,
            PermissionKey.KNOWLEDGE_QUERY,
            PermissionKey.REPORT_READ,
            PermissionKey.REPORT_GENERATE,
            PermissionKey.MODEL_CONFIG_TEST
    )),
    VIEWER("Viewer", EnumSet.of(
            PermissionKey.DASHBOARD_READ,
            PermissionKey.DATA_READ,
            PermissionKey.REPORT_READ
    ));

    private final String displayName;
    private final Set<PermissionKey> permissions;

    BuiltInRole(String displayName, Set<PermissionKey> permissions) {
        this.displayName = displayName;
        this.permissions = Set.copyOf(permissions);
    }

    public String roleKey() {
        return name();
    }

    public String displayName() {
        return displayName;
    }

    public Set<PermissionKey> permissions() {
        return permissions;
    }
}
