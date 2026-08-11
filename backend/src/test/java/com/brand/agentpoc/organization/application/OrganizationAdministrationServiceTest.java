package com.brand.agentpoc.organization.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.brand.agentpoc.auth.application.AuthAuditService;
import com.brand.agentpoc.auth.domain.AuthPrincipal;
import com.brand.agentpoc.auth.infrastructure.persistence.AuthRoleRepository;
import com.brand.agentpoc.auth.infrastructure.persistence.AuthUserRepository;
import com.brand.agentpoc.organization.domain.OrganizationNodeType;
import com.brand.agentpoc.organization.infrastructure.persistence.OrganizationDealerMappingRepository;
import com.brand.agentpoc.organization.infrastructure.persistence.OrganizationNodeEntity;
import com.brand.agentpoc.organization.infrastructure.persistence.OrganizationNodeRepository;
import com.brand.agentpoc.organization.infrastructure.persistence.OrganizationRoleGrantRepository;
import com.brand.agentpoc.organization.infrastructure.persistence.OrganizationUserGrantRepository;
import com.brand.agentpoc.repository.DealerRepository;
import com.brand.agentpoc.service.ImportBatchService;
import java.time.Clock;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OrganizationAdministrationServiceTest {

    private OrganizationNodeRepository nodeRepository;
    private OrganizationAdministrationService service;

    @BeforeEach
    void setUp() {
        nodeRepository = mock(OrganizationNodeRepository.class);
        service = new OrganizationAdministrationService(
                nodeRepository,
                mock(OrganizationDealerMappingRepository.class),
                mock(OrganizationUserGrantRepository.class),
                mock(OrganizationRoleGrantRepository.class),
                mock(AuthUserRepository.class),
                mock(AuthRoleRepository.class),
                mock(DealerRepository.class),
                mock(ImportBatchService.class),
                mock(AuthAuditService.class),
                Clock.systemUTC()
        );
    }

    @Test
    void rejectsCyclesAndMovesAcrossRoots() {
        OrganizationNodeEntity rootA = node(1L, OrganizationNodeType.GROUP, null, true);
        OrganizationNodeEntity rootB = node(2L, OrganizationNodeType.GROUP, null, true);
        OrganizationNodeEntity regionA = node(3L, OrganizationNodeType.REGION, rootA, true);
        OrganizationNodeEntity regionB = node(4L, OrganizationNodeType.REGION, rootB, true);
        OrganizationNodeEntity city = node(5L, OrganizationNodeType.CITY, regionA, true);

        when(nodeRepository.findById(5L)).thenReturn(Optional.of(city));
        when(nodeRepository.findById(4L)).thenReturn(Optional.of(regionB));
        assertThatThrownBy(() -> service.updateNode(
                actor(), 5L, "City", OrganizationNodeType.CITY, 4L, true, "trace"
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("across roots");

        OrganizationNodeEntity descendantRegion = node(6L, OrganizationNodeType.REGION, city, true);
        when(nodeRepository.findById(6L)).thenReturn(Optional.of(descendantRegion));
        assertThatThrownBy(() -> service.updateNode(
                actor(), 5L, "City", OrganizationNodeType.CITY, 6L, true, "trace"
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cycle");
    }

    private AuthPrincipal actor() {
        return new AuthPrincipal(1L, 1L, "family", "admin", "Admin", true, false, Set.of(), Set.of());
    }

    private OrganizationNodeEntity node(
            Long id,
            OrganizationNodeType type,
            OrganizationNodeEntity parent,
            boolean enabled
    ) {
        OrganizationNodeEntity node = mock(OrganizationNodeEntity.class);
        when(node.getId()).thenReturn(id);
        when(node.getNodeType()).thenReturn(type);
        when(node.getParent()).thenReturn(parent);
        when(node.getEnabled()).thenReturn(enabled);
        return node;
    }
}
