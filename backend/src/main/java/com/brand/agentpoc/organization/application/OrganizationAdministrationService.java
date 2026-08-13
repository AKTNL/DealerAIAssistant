package com.brand.agentpoc.organization.application;

import com.brand.agentpoc.auth.application.AuthAuditService;
import com.brand.agentpoc.auth.domain.AuthPrincipal;
import com.brand.agentpoc.auth.infrastructure.persistence.AuthRoleRepository;
import com.brand.agentpoc.auth.infrastructure.persistence.AuthUserRepository;
import com.brand.agentpoc.entity.Dealer;
import com.brand.agentpoc.organization.domain.OrganizationNodeType;
import com.brand.agentpoc.organization.infrastructure.persistence.OrganizationDealerMappingEntity;
import com.brand.agentpoc.organization.infrastructure.persistence.OrganizationDealerMappingRepository;
import com.brand.agentpoc.organization.infrastructure.persistence.OrganizationNodeEntity;
import com.brand.agentpoc.organization.infrastructure.persistence.OrganizationNodeRepository;
import com.brand.agentpoc.organization.infrastructure.persistence.OrganizationRoleGrantEntity;
import com.brand.agentpoc.organization.infrastructure.persistence.OrganizationRoleGrantRepository;
import com.brand.agentpoc.organization.infrastructure.persistence.OrganizationUserGrantEntity;
import com.brand.agentpoc.organization.infrastructure.persistence.OrganizationUserGrantRepository;
import com.brand.agentpoc.repository.DealerRepository;
import com.brand.agentpoc.service.ImportBatchService;
import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrganizationAdministrationService {

    private static final Pattern NODE_KEY_PATTERN = Pattern.compile("[A-Z0-9:_-]{2,128}");

    private final OrganizationNodeRepository nodeRepository;
    private final OrganizationDealerMappingRepository mappingRepository;
    private final OrganizationUserGrantRepository userGrantRepository;
    private final OrganizationRoleGrantRepository roleGrantRepository;
    private final AuthUserRepository userRepository;
    private final AuthRoleRepository roleRepository;
    private final DealerRepository dealerRepository;
    private final ImportBatchService importBatchService;
    private final AuthAuditService auditService;
    private final Clock clock;

    public OrganizationAdministrationService(
            OrganizationNodeRepository nodeRepository,
            OrganizationDealerMappingRepository mappingRepository,
            OrganizationUserGrantRepository userGrantRepository,
            OrganizationRoleGrantRepository roleGrantRepository,
            AuthUserRepository userRepository,
            AuthRoleRepository roleRepository,
            DealerRepository dealerRepository,
            ImportBatchService importBatchService,
            AuthAuditService auditService,
            Clock clock
    ) {
        this.nodeRepository = nodeRepository;
        this.mappingRepository = mappingRepository;
        this.userGrantRepository = userGrantRepository;
        this.roleGrantRepository = roleGrantRepository;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.dealerRepository = dealerRepository;
        this.importBatchService = importBatchService;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<NodeView> listNodes(AuthPrincipal actor) {
        return nodeRepository.findByTenantId(requireTenantId(actor)).stream()
                .sorted(java.util.Comparator.comparing(OrganizationNodeEntity::getNodeKey))
                .map(NodeView::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DealerMappingView> listDealerMappings(AuthPrincipal actor) {
        return mappingRepository.findByTenantId(requireTenantId(actor)).stream()
                .sorted(java.util.Comparator.comparing(OrganizationDealerMappingEntity::getDealerCode))
                .map(DealerMappingView::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<GrantView> listUserGrants(AuthPrincipal actor, Long userId) {
        if (userId == null || !userRepository.existsById(userId)) {
            throw new IllegalArgumentException("user was not found.");
        }
        return userGrantRepository.findByTenantIdAndUserId(requireTenantId(actor), userId).stream()
                .map(GrantView::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<GrantView> listRoleGrants(AuthPrincipal actor, Long roleId) {
        if (roleId == null || !roleRepository.existsById(roleId)) {
            throw new IllegalArgumentException("role was not found.");
        }
        return roleGrantRepository.findByTenantIdAndRoleId(requireTenantId(actor), roleId).stream()
                .map(GrantView::from)
                .toList();
    }

    @Transactional
    public NodeView createNode(
            AuthPrincipal actor,
            String nodeKey,
            String displayName,
            OrganizationNodeType nodeType,
            Long parentId,
            boolean enabled,
            String traceId
    ) {
        Long tenantId = requireTenantId(actor);
        String normalizedKey = normalizeNodeKey(nodeKey);
        if (!nodeRepository.findByTenantIdAndNodeKeyIgnoreCase(tenantId, normalizedKey).isEmpty()) {
            throw new IllegalArgumentException("organization node key already exists.");
        }
        OrganizationNodeEntity parent = parentId == null ? null : requireEnabledNode(tenantId, parentId);
        validateHierarchy(nodeType, parent);
        Instant now = Instant.now(clock);
        OrganizationNodeEntity saved = nodeRepository.saveAndFlush(new OrganizationNodeEntity(
                tenantId,
                normalizedKey,
                normalizeDisplayName(displayName),
                requireType(nodeType),
                parent,
                enabled,
                now
        ));
        auditService.record(actor.userId(), "ORG_NODE_CREATE", "ORGANIZATION_NODE", String.valueOf(saved.getId()),
                "SUCCESS", traceId, "organization_node_created");
        return NodeView.from(saved);
    }

    @Transactional
    public NodeView updateNode(
            AuthPrincipal actor,
            Long nodeId,
            String displayName,
            OrganizationNodeType nodeType,
            Long parentId,
            boolean enabled,
            Long expectedVersion,
            String traceId
    ) {
        Long tenantId = requireTenantId(actor);
        OrganizationNodeEntity node = requireNode(tenantId, nodeId);
        requireVersion(node.getVersion(), expectedVersion);
        OrganizationNodeEntity parent = parentId == null ? null : requireEnabledNode(tenantId, parentId);
        validateHierarchy(nodeType, parent);
        validateReparent(node, parent);
        validateChildren(tenantId, node, nodeType);
        node.update(normalizeDisplayName(displayName), requireType(nodeType), parent, enabled, Instant.now(clock));
        OrganizationNodeEntity saved = nodeRepository.saveAndFlush(node);
        auditService.record(actor.userId(), "ORG_NODE_UPDATE", "ORGANIZATION_NODE", String.valueOf(saved.getId()),
                "SUCCESS", traceId, enabled ? "organization_node_updated" : "organization_node_disabled");
        return NodeView.from(saved);
    }

    @Transactional
    public DealerMappingView mapDealer(
            AuthPrincipal actor,
            String dealerCode,
            Long organizationNodeId,
            String traceId
    ) {
        Long tenantId = requireTenantId(actor);
        String normalizedDealerCode = normalizeDealerCode(dealerCode);
        OrganizationNodeEntity node = requireEnabledNode(tenantId, organizationNodeId);
        if (node.getNodeType() != OrganizationNodeType.DEALER) {
            throw new IllegalArgumentException("Dealer mappings require a DEALER organization node.");
        }
        boolean knownDealer = importBatchService.filterActive(
                        dealerRepository.findByTenantIdAndDealerCodeIgnoreCase(tenantId, normalizedDealerCode),
                        tenantId)
                .stream()
                .map(Dealer::getDealerCode)
                .anyMatch(normalizedDealerCode::equalsIgnoreCase);
        if (!knownDealer) {
            throw new IllegalArgumentException("dealer code was not found in the active data batch.");
        }
        mappingRepository.deleteAll(mappingRepository.findByTenantIdAndDealerCodeIgnoreCase(
                tenantId, normalizedDealerCode));
        mappingRepository.flush();
        OrganizationDealerMappingEntity saved = mappingRepository.save(new OrganizationDealerMappingEntity(
                tenantId,
                node,
                normalizedDealerCode,
                Instant.now(clock)
        ));
        auditService.record(actor.userId(), "ORG_DEALER_MAP", "DEALER", normalizedDealerCode,
                "SUCCESS", traceId, "dealer_mapping_replaced");
        return DealerMappingView.from(saved);
    }

    @Transactional
    public List<GrantView> replaceUserGrants(
            AuthPrincipal actor,
            Long userId,
            Set<GrantInput> grants,
            String traceId
    ) {
        Long tenantId = requireTenantId(actor);
        if (!userRepository.existsById(userId)) {
            throw new IllegalArgumentException("user was not found.");
        }
        Set<GrantInput> normalized = validateGrants(tenantId, grants);
        userGrantRepository.deleteByTenantIdAndUserId(tenantId, userId);
        userGrantRepository.flush();
        Instant now = Instant.now(clock);
        List<OrganizationUserGrantEntity> saved = userGrantRepository.saveAll(normalized.stream()
                .map(grant -> new OrganizationUserGrantEntity(
                        tenantId,
                        userId,
                        requireEnabledNode(tenantId, grant.organizationNodeId()),
                        grant.includeDescendants(),
                        now
                ))
                .toList());
        auditService.record(actor.userId(), "ORG_USER_GRANTS_REPLACE", "USER", String.valueOf(userId),
                "SUCCESS", traceId, "organization_grants_replaced");
        return saved.stream().map(GrantView::from).toList();
    }

    @Transactional
    public List<GrantView> replaceRoleGrants(
            AuthPrincipal actor,
            Long roleId,
            Set<GrantInput> grants,
            String traceId
    ) {
        Long tenantId = requireTenantId(actor);
        if (!roleRepository.existsById(roleId)) {
            throw new IllegalArgumentException("role was not found.");
        }
        Set<GrantInput> normalized = validateGrants(tenantId, grants);
        roleGrantRepository.deleteByTenantIdAndRoleId(tenantId, roleId);
        roleGrantRepository.flush();
        Instant now = Instant.now(clock);
        List<OrganizationRoleGrantEntity> saved = roleGrantRepository.saveAll(normalized.stream()
                .map(grant -> new OrganizationRoleGrantEntity(
                        tenantId,
                        roleId,
                        requireEnabledNode(tenantId, grant.organizationNodeId()),
                        grant.includeDescendants(),
                        now
                ))
                .toList());
        auditService.record(actor.userId(), "ORG_ROLE_GRANTS_REPLACE", "ROLE", String.valueOf(roleId),
                "SUCCESS", traceId, "organization_grants_replaced");
        return saved.stream().map(GrantView::from).toList();
    }

    private Set<GrantInput> validateGrants(Long tenantId, Set<GrantInput> grants) {
        Set<GrantInput> normalized = grants == null ? Set.of() : Set.copyOf(grants);
        Set<Long> nodeIds = new HashSet<>();
        for (GrantInput grant : normalized) {
            if (grant == null || grant.organizationNodeId() == null) {
                throw new IllegalArgumentException("organization node id is required for every grant.");
            }
            if (!nodeIds.add(grant.organizationNodeId())) {
                throw new IllegalArgumentException("duplicate organization node grant.");
            }
            requireEnabledNode(tenantId, grant.organizationNodeId());
        }
        return normalized;
    }

    private void validateHierarchy(OrganizationNodeType nodeType, OrganizationNodeEntity parent) {
        OrganizationNodeType requiredType = requireType(nodeType);
        if (parent == null) {
            if (requiredType != OrganizationNodeType.GROUP) {
                throw new IllegalArgumentException("Only GROUP nodes may be organization roots.");
            }
            return;
        }
        if (!parent.getNodeType().acceptsChild(requiredType)) {
            throw new IllegalArgumentException("organization node type is invalid for the selected parent.");
        }
    }

    private void validateReparent(OrganizationNodeEntity node, OrganizationNodeEntity nextParent) {
        if (node.getParent() == null && nextParent != null) {
            throw new IllegalArgumentException("organization roots cannot be reparented.");
        }
        if (node.getParent() != null && nextParent == null) {
            throw new IllegalArgumentException("non-root organization nodes require a parent.");
        }
        if (nextParent == null) {
            return;
        }
        if (node.getId().equals(nextParent.getId()) || isAncestor(node, nextParent)) {
            throw new IllegalArgumentException("organization hierarchy cannot contain a cycle.");
        }
        if (!rootOf(node).getId().equals(rootOf(nextParent).getId())) {
            throw new IllegalArgumentException("organization nodes cannot be moved across roots.");
        }
    }

    private void validateChildren(Long tenantId, OrganizationNodeEntity node, OrganizationNodeType nextType) {
        boolean invalidChild = nodeRepository.findByTenantIdAndParentId(tenantId, node.getId()).stream()
                .anyMatch(child -> !nextType.acceptsChild(child.getNodeType()));
        if (invalidChild) {
            throw new IllegalArgumentException("organization node type is incompatible with existing children.");
        }
    }

    private boolean isAncestor(OrganizationNodeEntity candidateAncestor, OrganizationNodeEntity node) {
        Set<Long> visited = new HashSet<>();
        OrganizationNodeEntity current = node;
        while (current != null) {
            if (!visited.add(current.getId())) {
                throw new IllegalArgumentException("organization hierarchy contains a cycle.");
            }
            if (candidateAncestor.getId().equals(current.getId())) {
                return true;
            }
            current = current.getParent();
        }
        return false;
    }

    private OrganizationNodeEntity rootOf(OrganizationNodeEntity node) {
        Set<Long> visited = new HashSet<>();
        OrganizationNodeEntity current = node;
        while (current.getParent() != null) {
            if (!visited.add(current.getId())) {
                throw new IllegalArgumentException("organization hierarchy contains a cycle.");
            }
            current = current.getParent();
        }
        return current;
    }

    private OrganizationNodeEntity requireNode(Long tenantId, Long id) {
        if (id == null) {
            throw new IllegalArgumentException("organization node id is required.");
        }
        return nodeRepository.findByTenantIdAndId(tenantId, id).stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("organization node was not found."));
    }

    private OrganizationNodeEntity requireEnabledNode(Long tenantId, Long id) {
        OrganizationNodeEntity node = requireNode(tenantId, id);
        if (!Boolean.TRUE.equals(node.getEnabled())) {
            throw new IllegalArgumentException("organization node is disabled.");
        }
        return node;
    }

    private Long requireTenantId(AuthPrincipal actor) {
        if (actor == null || !actor.hasTenantContext()) {
            throw new org.springframework.security.access.AccessDeniedException("Tenant context is required.");
        }
        return actor.tenantId();
    }

    private String normalizeNodeKey(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!NODE_KEY_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("organization node key is invalid.");
        }
        return normalized;
    }

    private String normalizeDisplayName(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty() || normalized.length() > 128) {
            throw new IllegalArgumentException("organization display name is invalid.");
        }
        return normalized;
    }

    private String normalizeDealerCode(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (normalized.isEmpty() || normalized.length() > 64) {
            throw new IllegalArgumentException("dealer code is invalid.");
        }
        return normalized;
    }

    private OrganizationNodeType requireType(OrganizationNodeType nodeType) {
        if (nodeType == null) {
            throw new IllegalArgumentException("organization node type is required.");
        }
        return nodeType;
    }

    private void requireVersion(Long currentVersion, Long expectedVersion) {
        if (expectedVersion != null && !expectedVersion.equals(currentVersion)) {
            throw new IllegalStateException("The resource changed since it was loaded.");
        }
    }

    public record GrantInput(Long organizationNodeId, boolean includeDescendants) {
    }

    public record NodeView(
            Long id,
            String nodeKey,
            String displayName,
            OrganizationNodeType nodeType,
            Long parentId,
            boolean enabled,
            Long version
    ) {
        static NodeView from(OrganizationNodeEntity node) {
            return new NodeView(
                    node.getId(),
                    node.getNodeKey(),
                    node.getDisplayName(),
                    node.getNodeType(),
                    node.getParent() == null ? null : node.getParent().getId(),
                    Boolean.TRUE.equals(node.getEnabled()),
                    node.getVersion()
            );
        }
    }

    public record DealerMappingView(Long id, Long organizationNodeId, String dealerCode) {
        static DealerMappingView from(OrganizationDealerMappingEntity mapping) {
            return new DealerMappingView(
                    mapping.getId(),
                    mapping.getOrganizationNode().getId(),
                    mapping.getDealerCode()
            );
        }
    }

    public record GrantView(Long id, Long organizationNodeId, boolean includeDescendants) {
        static GrantView from(OrganizationUserGrantEntity grant) {
            return new GrantView(
                    grant.getId(),
                    grant.getOrganizationNode().getId(),
                    Boolean.TRUE.equals(grant.getIncludeDescendants())
            );
        }

        static GrantView from(OrganizationRoleGrantEntity grant) {
            return new GrantView(
                    grant.getId(),
                    grant.getOrganizationNode().getId(),
                    Boolean.TRUE.equals(grant.getIncludeDescendants())
            );
        }
    }
}
