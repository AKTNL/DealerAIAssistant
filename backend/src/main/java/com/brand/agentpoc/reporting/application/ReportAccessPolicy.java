package com.brand.agentpoc.reporting.application;

import com.brand.agentpoc.organization.domain.OrganizationDataScope;
import com.brand.agentpoc.reporting.domain.ReportDraft;
import com.brand.agentpoc.reporting.domain.ReportScope;

public final class ReportAccessPolicy {

    private ReportAccessPolicy() {
    }

    public static boolean canRead(ReportDraft draft, OrganizationDataScope dataScope) {
        if (draft == null || dataScope == null || !draft.tenantId().equals(dataScope.tenantId())) {
            return false;
        }
        return scopeCovered(draft.scope(), dataScope);
    }

    public static boolean scopeCovered(ReportScope scope, OrganizationDataScope dataScope) {
        if (scope == null || dataScope == null) {
            return false;
        }
        if ("GLOBAL".equals(scope.type())) {
            return dataScope.unrestricted() || dataScope.rootCoverage();
        }
        return dataScope.containsAllNodes(scope.organizationNodeIds());
    }
}
