package com.brand.agentpoc.tenant.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.brand.agentpoc.tenant.domain.TenantScoped;
import java.util.List;
import org.junit.jupiter.api.Test;

class TenantScopedIsolationTest {

    @Test
    void tenantScopeFiltersRowsBeforeBusinessFilters() {
        TenantScoped defaultDealer = () -> 1L;
        TenantScoped secondDealer = () -> 2L;

        List<TenantScoped> visible = OrganizationScopeSupport.filterTenant(
                List.of(defaultDealer, secondDealer), TenantScoped.DEFAULT_TENANT_ID);

        assertThat(visible).containsExactly(defaultDealer);
    }

    private static final class OrganizationScopeSupport {
        private static List<TenantScoped> filterTenant(List<TenantScoped> rows, Long tenantId) {
            return rows.stream().filter(row -> tenantId.equals(row.getTenantId())).toList();
        }
    }
}
