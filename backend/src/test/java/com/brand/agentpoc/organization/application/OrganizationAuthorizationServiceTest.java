package com.brand.agentpoc.organization.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.brand.agentpoc.auth.domain.AuthPrincipal;
import com.brand.agentpoc.auth.infrastructure.persistence.AuthUserEntity;
import com.brand.agentpoc.auth.infrastructure.persistence.AuthUserRepository;
import com.brand.agentpoc.organization.domain.OrganizationDataScope;
import com.brand.agentpoc.organization.domain.OrganizationNodeType;
import com.brand.agentpoc.organization.infrastructure.persistence.OrganizationDealerMappingEntity;
import com.brand.agentpoc.organization.infrastructure.persistence.OrganizationDealerMappingRepository;
import com.brand.agentpoc.organization.infrastructure.persistence.OrganizationNodeEntity;
import com.brand.agentpoc.organization.infrastructure.persistence.OrganizationNodeRepository;
import com.brand.agentpoc.organization.infrastructure.persistence.OrganizationRoleGrantRepository;
import com.brand.agentpoc.organization.infrastructure.persistence.OrganizationUserGrantRepository;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;

class OrganizationAuthorizationServiceTest {

    private AuthUserRepository userRepository;
    private OrganizationAuthorizationService service;

    @BeforeEach
    void setUp() {
        userRepository = mock(AuthUserRepository.class);
        service = new OrganizationAuthorizationService(
                userRepository,
                mock(OrganizationNodeRepository.class),
                mock(OrganizationDealerMappingRepository.class),
                mock(OrganizationUserGrantRepository.class),
                mock(OrganizationRoleGrantRepository.class)
        );
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void resolvesTheCurrentAuthenticatedPrincipalFromTheSecurityContext() {
        AuthPrincipal principal = new AuthPrincipal(
                1L, 2L, "family", "analyst", "Analyst", true, false, Set.of(), Set.of());
        AuthUserEntity user = mock(AuthUserEntity.class);
        when(user.getEnabled()).thenReturn(true);
        when(user.getRoles()).thenReturn(Set.of());
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, "token", List.of())
        );

        assertThat(service.resolveCurrent().principal()).isEqualTo(principal);
    }

    @Test
    void rejectsARequestWithoutAnAuthenticatedApplicationPrincipal() {
        assertThatThrownBy(service::resolveCurrent)
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Authenticated principal is required.");

        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated("anonymous", "", List.of())
        );
        assertThatThrownBy(service::resolveCurrent)
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Authenticated principal is required.");
    }

    @Test
    void expandsDescendantsAndMergesMultipleGrantsWithoutCrossRegionLeakage() {
        OrganizationNodeEntity root = node(1L, OrganizationNodeType.GROUP, null, true);
        OrganizationNodeEntity north = node(2L, OrganizationNodeType.REGION, root, true);
        OrganizationNodeEntity northCity = node(3L, OrganizationNodeType.CITY, north, true);
        OrganizationNodeEntity northDealer = node(4L, OrganizationNodeType.DEALER, northCity, true);
        OrganizationNodeEntity south = node(5L, OrganizationNodeType.REGION, root, true);
        OrganizationNodeEntity southCity = node(6L, OrganizationNodeType.CITY, south, true);
        OrganizationNodeEntity southDealer = node(7L, OrganizationNodeType.DEALER, southCity, true);

        OrganizationDataScope northOnly = service.resolveDataScope(
                List.of(new OrganizationAuthorizationService.GrantSeed(2L, true)),
                List.of(root, north, northCity, northDealer, south, southCity, southDealer),
                List.of(mapping(northDealer, "NORTH-1"), mapping(southDealer, "SOUTH-1"))
        );
        assertThat(northOnly.dealerCodes()).containsExactly("NORTH-1");
        assertThat(northOnly.organizationNodeIds()).containsExactlyInAnyOrder(2L, 3L, 4L);

        OrganizationDataScope merged = service.resolveDataScope(
                List.of(
                        new OrganizationAuthorizationService.GrantSeed(2L, true),
                        new OrganizationAuthorizationService.GrantSeed(7L, false)
                ),
                List.of(root, north, northCity, northDealer, south, southCity, southDealer),
                List.of(mapping(northDealer, "NORTH-1"), mapping(southDealer, "SOUTH-1"))
        );
        assertThat(merged.dealerCodes()).containsExactlyInAnyOrder("NORTH-1", "SOUTH-1");
    }

    @Test
    void includeDescendantsFalseDoesNotReachMappedChildDealer() {
        OrganizationNodeEntity root = node(1L, OrganizationNodeType.GROUP, null, true);
        OrganizationNodeEntity region = node(2L, OrganizationNodeType.REGION, root, true);
        OrganizationNodeEntity city = node(3L, OrganizationNodeType.CITY, region, true);
        OrganizationNodeEntity dealer = node(4L, OrganizationNodeType.DEALER, city, true);

        OrganizationDataScope scope = service.resolveDataScope(
                List.of(new OrganizationAuthorizationService.GrantSeed(3L, false)),
                List.of(root, region, city, dealer),
                List.of(mapping(dealer, "CHILD-1"))
        );

        assertThat(scope.organizationNodeIds()).containsExactly(3L);
        assertThat(scope.dealerCodes()).isEmpty();
        assertThat(scope.hasDataAccess()).isFalse();
    }

    @Test
    void rejectsUnknownAndDisabledGrantNodes() {
        OrganizationNodeEntity disabled = node(9L, OrganizationNodeType.DEALER, null, false);

        assertThatThrownBy(() -> service.resolveDataScope(
                List.of(new OrganizationAuthorizationService.GrantSeed(99L, false)),
                List.of(disabled),
                List.of()
        )).isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("unknown or disabled");

        assertThatThrownBy(() -> service.resolveDataScope(
                List.of(new OrganizationAuthorizationService.GrantSeed(9L, false)),
                List.of(disabled),
                List.of()
        )).isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("unknown or disabled");
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

    private OrganizationDealerMappingEntity mapping(OrganizationNodeEntity node, String dealerCode) {
        OrganizationDealerMappingEntity mapping = mock(OrganizationDealerMappingEntity.class);
        when(mapping.getOrganizationNode()).thenReturn(node);
        when(mapping.getDealerCode()).thenReturn(dealerCode);
        return mapping;
    }
}
