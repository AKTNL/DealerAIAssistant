package com.brand.agentpoc.organization.infrastructure;

import com.brand.agentpoc.auth.domain.BuiltInRole;
import com.brand.agentpoc.auth.infrastructure.persistence.AuthAuditEventEntity;
import com.brand.agentpoc.auth.infrastructure.persistence.AuthAuditEventRepository;
import com.brand.agentpoc.auth.infrastructure.persistence.AuthRoleEntity;
import com.brand.agentpoc.auth.infrastructure.persistence.AuthRoleRepository;
import com.brand.agentpoc.entity.Dealer;
import com.brand.agentpoc.organization.domain.OrganizationNodeType;
import com.brand.agentpoc.organization.infrastructure.persistence.OrganizationDealerMappingEntity;
import com.brand.agentpoc.organization.infrastructure.persistence.OrganizationDealerMappingRepository;
import com.brand.agentpoc.organization.infrastructure.persistence.OrganizationNodeEntity;
import com.brand.agentpoc.organization.infrastructure.persistence.OrganizationNodeRepository;
import com.brand.agentpoc.organization.infrastructure.persistence.OrganizationRoleGrantEntity;
import com.brand.agentpoc.organization.infrastructure.persistence.OrganizationRoleGrantRepository;
import com.brand.agentpoc.repository.DealerRepository;
import com.brand.agentpoc.service.ImportBatchService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class OrganizationBootstrap {

    public static final String ROOT_NODE_KEY = "GLOBAL_ROOT";
    private static final Logger log = LoggerFactory.getLogger(OrganizationBootstrap.class);

    private final OrganizationNodeRepository nodeRepository;
    private final OrganizationDealerMappingRepository mappingRepository;
    private final OrganizationRoleGrantRepository roleGrantRepository;
    private final AuthRoleRepository roleRepository;
    private final DealerRepository dealerRepository;
    private final ImportBatchService importBatchService;
    private final AuthAuditEventRepository auditRepository;
    private final Clock clock;

    public OrganizationBootstrap(
            OrganizationNodeRepository nodeRepository,
            OrganizationDealerMappingRepository mappingRepository,
            OrganizationRoleGrantRepository roleGrantRepository,
            AuthRoleRepository roleRepository,
            DealerRepository dealerRepository,
            ImportBatchService importBatchService,
            AuthAuditEventRepository auditRepository,
            Clock clock
    ) {
        this.nodeRepository = nodeRepository;
        this.mappingRepository = mappingRepository;
        this.roleGrantRepository = roleGrantRepository;
        this.roleRepository = roleRepository;
        this.dealerRepository = dealerRepository;
        this.importBatchService = importBatchService;
        this.auditRepository = auditRepository;
        this.clock = clock;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void initializeOrganizationScope() {
        Instant now = Instant.now(clock);
        OrganizationNodeEntity root = ensureNode(
                ROOT_NODE_KEY,
                "Global Dealer Organization",
                OrganizationNodeType.GROUP,
                null,
                now
        );
        List<Dealer> dealers = importBatchService.filterActive(dealerRepository.findAll());
        for (Dealer dealer : dealers) {
            String groupName = normalizedLabel(dealer.getDealerGroupName(), "Unassigned Group");
            OrganizationNodeEntity region = ensureNode(
                    stableKey("REGION", groupName),
                    groupName,
                    OrganizationNodeType.REGION,
                    root,
                    now
            );
            String cityName = normalizedLabel(dealer.getCity(), "Unassigned City");
            OrganizationNodeEntity city = ensureNode(
                    stableKey("CITY", groupName + "|" + cityName),
                    cityName,
                    OrganizationNodeType.CITY,
                    region,
                    now
            );
            OrganizationNodeEntity dealerNode = ensureNode(
                    stableKey("DEALER", dealer.getDealerCode()),
                    normalizedLabel(dealer.getDealerName(), dealer.getDealerCode()),
                    OrganizationNodeType.DEALER,
                    city,
                    now
            );
            ensureDealerMapping(dealerNode, dealer.getDealerCode(), now);
        }
        ensureAdministratorRootGrant(root, now);
        auditRepository.save(new AuthAuditEventEntity(
                null,
                "ORG_BOOTSTRAP",
                "ORGANIZATION_NODE",
                String.valueOf(root.getId()),
                "SUCCESS",
                "startup",
                "organization_scope_initialized",
                now
        ));
        log.info("Organization scope initialized: activeDealerCount={}", dealers.size());
    }

    private OrganizationNodeEntity ensureNode(
            String nodeKey,
            String displayName,
            OrganizationNodeType nodeType,
            OrganizationNodeEntity parent,
            Instant now
    ) {
        List<OrganizationNodeEntity> matches = nodeRepository.findByNodeKeyIgnoreCase(nodeKey);
        if (matches.size() > 1) {
            throw new IllegalStateException("Organization node key is not unique: " + nodeKey);
        }
        if (matches.isEmpty()) {
            return nodeRepository.save(new OrganizationNodeEntity(
                    nodeKey,
                    displayName,
                    nodeType,
                    parent,
                    true,
                    now
            ));
        }
        OrganizationNodeEntity existing = matches.getFirst();
        Long existingParentId = existing.getParent() == null ? null : existing.getParent().getId();
        Long expectedParentId = parent == null ? null : parent.getId();
        if (existing.getNodeType() != nodeType || !java.util.Objects.equals(existingParentId, expectedParentId)) {
            throw new IllegalStateException("Bootstrapped organization hierarchy drifted: " + nodeKey);
        }
        return existing;
    }

    private void ensureDealerMapping(OrganizationNodeEntity dealerNode, String dealerCode, Instant now) {
        List<OrganizationDealerMappingEntity> matches = mappingRepository.findByDealerCodeIgnoreCase(dealerCode);
        if (matches.size() > 1) {
            throw new IllegalStateException("Dealer organization mapping is not unique: " + dealerCode);
        }
        if (matches.isEmpty()) {
            mappingRepository.save(new OrganizationDealerMappingEntity(
                    dealerNode,
                    dealerCode.trim().toUpperCase(Locale.ROOT),
                    now
            ));
        }
    }

    private void ensureAdministratorRootGrant(OrganizationNodeEntity root, Instant now) {
        List<AuthRoleEntity> adminRoles = roleRepository.findByRoleKeyIgnoreCase(BuiltInRole.ADMIN.roleKey());
        if (adminRoles.size() != 1) {
            throw new IllegalStateException("Built-in administrator role is unavailable for organization bootstrap.");
        }
        AuthRoleEntity adminRole = adminRoles.getFirst();
        List<OrganizationRoleGrantEntity> existing = roleGrantRepository.findByRoleId(adminRole.getId());
        boolean rootAssigned = existing.stream()
                .anyMatch(grant -> root.getId().equals(grant.getOrganizationNode().getId()));
        if (!rootAssigned) {
            roleGrantRepository.save(new OrganizationRoleGrantEntity(adminRole.getId(), root, true, now));
        }
    }

    private String stableKey(String prefix, String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(value.trim().toUpperCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
            return prefix + ":" + HexFormat.of().formatHex(hash, 0, 12).toUpperCase(Locale.ROOT);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    private String normalizedLabel(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
