package com.brand.agentpoc.tenant.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.brand.agentpoc.auth.infrastructure.persistence.AuthRoleRepository;
import com.brand.agentpoc.auth.infrastructure.persistence.AuthUserEntity;
import com.brand.agentpoc.auth.infrastructure.persistence.AuthUserRepository;
import com.brand.agentpoc.tenant.infrastructure.persistence.TenantEntity;
import com.brand.agentpoc.tenant.infrastructure.persistence.TenantMembershipEntity;
import com.brand.agentpoc.tenant.infrastructure.persistence.TenantMembershipRepository;
import com.brand.agentpoc.tenant.infrastructure.persistence.TenantMembershipRoleRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TenantMemberDirectoryTest {

    @Test
    void recipientListingSkipsDisabledUsersInsteadOfFailingTheWholeTenant() {
        TenantMembershipRepository membershipRepository = mock(TenantMembershipRepository.class);
        TenantMembershipRoleRepository membershipRoleRepository = mock(TenantMembershipRoleRepository.class);
        AuthRoleRepository roleRepository = mock(AuthRoleRepository.class);
        AuthUserRepository userRepository = mock(AuthUserRepository.class);
        TenantMemberDirectory directory = new TenantMemberDirectory(
                membershipRepository, membershipRoleRepository, roleRepository, userRepository);

        TenantEntity tenant = mock(TenantEntity.class);
        TenantMembershipEntity membership = mock(TenantMembershipEntity.class);
        AuthUserEntity disabledUser = mock(AuthUserEntity.class);
        when(tenant.getEnabled()).thenReturn(true);
        when(membership.getTenant()).thenReturn(tenant);
        when(membership.getUserId()).thenReturn(20L);
        when(disabledUser.getEnabled()).thenReturn(false);
        when(membershipRepository.findByTenantIdAndEnabledTrue(7L)).thenReturn(List.of(membership));
        when(userRepository.findById(20L)).thenReturn(Optional.of(disabledUser));

        assertThat(directory.listReportRecipients(7L)).isEmpty();
    }
}
