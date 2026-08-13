package com.brand.agentpoc.organization.application;

import com.brand.agentpoc.auth.domain.AuthPrincipal;
import com.brand.agentpoc.organization.domain.OrganizationAuthorizationContext;
import com.brand.agentpoc.organization.domain.OrganizationDataScope;
import com.brand.agentpoc.organization.infrastructure.persistence.OrganizationDealerMappingEntity;
import com.brand.agentpoc.organization.infrastructure.persistence.OrganizationDealerMappingRepository;
import com.brand.agentpoc.organization.infrastructure.persistence.OrganizationNodeEntity;
import com.brand.agentpoc.organization.infrastructure.persistence.OrganizationNodeRepository;
import com.brand.agentpoc.organization.infrastructure.persistence.OrganizationRoleGrantEntity;
import com.brand.agentpoc.organization.infrastructure.persistence.OrganizationRoleGrantRepository;
import com.brand.agentpoc.organization.infrastructure.persistence.OrganizationUserGrantEntity;
import com.brand.agentpoc.organization.infrastructure.persistence.OrganizationUserGrantRepository;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class OrganizationAuthorizationService {

    private final OrganizationNodeRepository nodeRepository;
    private final OrganizationDealerMappingRepository mappingRepository;
    private final OrganizationUserGrantRepository userGrantRepository;
    private final OrganizationRoleGrantRepository roleGrantRepository;

    public OrganizationAuthorizationService(
            OrganizationNodeRepository nodeRepository,
            OrganizationDealerMappingRepository mappingRepository,
            OrganizationUserGrantRepository userGrantRepository,
            OrganizationRoleGrantRepository roleGrantRepository
    ) {
        this.nodeRepository = nodeRepository;
        this.mappingRepository = mappingRepository;
        this.userGrantRepository = userGrantRepository;
        this.roleGrantRepository = roleGrantRepository;
    }

    public OrganizationAuthorizationContext resolve(AuthPrincipal principal) {
        if (principal == null) {
            throw new AccessDeniedException("Authenticated principal is required.");
        }
        if (!principal.hasTenantContext()) {
            throw new AccessDeniedException("Tenant context is required.");
        }
        Long tenantId = principal.tenantId();

        List<GrantSeed> grants = new ArrayList<>();
        userGrantRepository.findByTenantIdAndUserId(tenantId, principal.userId()).stream()
                .map(this::toSeed)
                .forEach(grants::add);
        if (!principal.roleIds().isEmpty()) {
            roleGrantRepository.findByTenantIdAndRoleIdIn(tenantId, principal.roleIds()).stream()
                    .map(this::toSeed)
                    .forEach(grants::add);
        }
        OrganizationDataScope dataScope = resolveDataScope(
                principal.tenantId(),
                principal.tenantKey(),
                grants,
                nodeRepository.findByTenantId(tenantId),
                mappingRepository.findByTenantId(tenantId)
        );
        return new OrganizationAuthorizationContext(principal, dataScope);
    }

    public OrganizationAuthorizationContext resolveCurrent() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof AuthPrincipal principal)) {
            throw new AccessDeniedException("Authenticated principal is required.");
        }
        return resolve(principal);
    }

    OrganizationDataScope resolveDataScope(
            Collection<GrantSeed> grants,
            List<OrganizationNodeEntity> nodes,
            List<OrganizationDealerMappingEntity> mappings
    ) {
        return resolveDataScope(
                com.brand.agentpoc.tenant.domain.TenantScoped.DEFAULT_TENANT_ID,
                "default",
                grants,
                nodes,
                mappings
        );
    }

    OrganizationDataScope resolveDataScope(
            Long tenantId,
            String tenantKey,
            Collection<GrantSeed> grants,
            List<OrganizationNodeEntity> nodes,
            List<OrganizationDealerMappingEntity> mappings
    ) {
        if (grants == null || grants.isEmpty()) {
            return OrganizationDataScope.tenantScope(
                    tenantId, tenantKey, Set.of(), Set.of(), Set.of(), false);
        }

        Map<Long, OrganizationNodeEntity> nodesById = new HashMap<>();
        Map<Long, List<OrganizationNodeEntity>> childrenByParent = new HashMap<>();
        for (OrganizationNodeEntity node : nodes) {
            nodesById.put(node.getId(), node);
            if (node.getParent() != null) {
                childrenByParent.computeIfAbsent(node.getParent().getId(), ignored -> new ArrayList<>()).add(node);
            }
        }

        Set<Long> allowedNodeIds = new LinkedHashSet<>();
        Set<Long> grantNodeIds = new LinkedHashSet<>();
        boolean rootCoverage = false;
        for (GrantSeed grant : grants) {
            OrganizationNodeEntity grantedNode = nodesById.get(grant.nodeId());
            if (grantedNode == null || !Boolean.TRUE.equals(grantedNode.getEnabled())) {
                throw new AccessDeniedException("Organization grant references an unknown or disabled node.");
            }
            grantNodeIds.add(grantedNode.getId());
            allowedNodeIds.add(grantedNode.getId());
            if (grant.includeDescendants()) {
                addEnabledDescendants(grantedNode, childrenByParent, allowedNodeIds);
                rootCoverage |= grantedNode.getParent() == null;
            }
        }

        Set<String> dealerCodes = mappings.stream()
                .filter(mapping -> mapping.getOrganizationNode() != null)
                .filter(mapping -> Boolean.TRUE.equals(mapping.getOrganizationNode().getEnabled()))
                .filter(mapping -> allowedNodeIds.contains(mapping.getOrganizationNode().getId()))
                .map(OrganizationDealerMappingEntity::getDealerCode)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        return OrganizationDataScope.tenantScope(
                tenantId, tenantKey, allowedNodeIds, grantNodeIds, dealerCodes, rootCoverage);
    }

    private void addEnabledDescendants(
            OrganizationNodeEntity root,
            Map<Long, List<OrganizationNodeEntity>> childrenByParent,
            Set<Long> allowedNodeIds
    ) {
        ArrayDeque<OrganizationNodeEntity> pending = new ArrayDeque<>();
        pending.add(root);
        Set<Long> visited = new HashSet<>();
        while (!pending.isEmpty()) {
            OrganizationNodeEntity current = pending.removeFirst();
            if (!visited.add(current.getId())) {
                throw new AccessDeniedException("Organization hierarchy contains a cycle.");
            }
            for (OrganizationNodeEntity child : childrenByParent.getOrDefault(current.getId(), List.of())) {
                if (Boolean.TRUE.equals(child.getEnabled())) {
                    allowedNodeIds.add(child.getId());
                    pending.addLast(child);
                }
            }
        }
    }

    private GrantSeed toSeed(OrganizationUserGrantEntity grant) {
        return new GrantSeed(grant.getOrganizationNode().getId(), Boolean.TRUE.equals(grant.getIncludeDescendants()));
    }

    private GrantSeed toSeed(OrganizationRoleGrantEntity grant) {
        return new GrantSeed(grant.getOrganizationNode().getId(), Boolean.TRUE.equals(grant.getIncludeDescendants()));
    }

    record GrantSeed(Long nodeId, boolean includeDescendants) {
    }
}
