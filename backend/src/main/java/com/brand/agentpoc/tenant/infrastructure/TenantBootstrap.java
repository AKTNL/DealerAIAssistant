package com.brand.agentpoc.tenant.infrastructure;

import com.brand.agentpoc.auth.infrastructure.persistence.AuthUserEntity;
import com.brand.agentpoc.auth.infrastructure.persistence.AuthUserRepository;
import com.brand.agentpoc.tenant.domain.TenantScoped;
import com.brand.agentpoc.tenant.infrastructure.persistence.TenantEntity;
import com.brand.agentpoc.tenant.infrastructure.persistence.TenantMembershipEntity;
import com.brand.agentpoc.tenant.infrastructure.persistence.TenantMembershipRepository;
import com.brand.agentpoc.tenant.infrastructure.persistence.TenantMembershipRoleEntity;
import com.brand.agentpoc.tenant.infrastructure.persistence.TenantMembershipRoleRepository;
import com.brand.agentpoc.tenant.infrastructure.persistence.TenantRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 100)
public class TenantBootstrap implements ApplicationRunner {

    public static final String DEFAULT_TENANT_KEY = "default";
    private static final Logger log = LoggerFactory.getLogger(TenantBootstrap.class);

    private final TenantRepository tenantRepository;
    private final TenantMembershipRepository membershipRepository;
    private final TenantMembershipRoleRepository membershipRoleRepository;
    private final AuthUserRepository userRepository;
    private final Clock clock;

    public TenantBootstrap(
            TenantRepository tenantRepository,
            TenantMembershipRepository membershipRepository,
            TenantMembershipRoleRepository membershipRoleRepository,
            AuthUserRepository userRepository,
            Clock clock
    ) {
        this.tenantRepository = tenantRepository;
        this.membershipRepository = membershipRepository;
        this.membershipRoleRepository = membershipRoleRepository;
        this.userRepository = userRepository;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        Instant now = Instant.now(clock);
        TenantEntity tenant = ensureDefaultTenant(now);
        List<AuthUserEntity> users = userRepository.findAll();
        for (AuthUserEntity user : users) {
            TenantMembershipEntity membership = ensureMembership(tenant, user, now);
            ensureMembershipRoles(membership, user);
        }
        log.info("Default tenant initialized: tenantId={}, membershipCount={}", tenant.getId(), users.size());
    }

    private TenantEntity ensureDefaultTenant(Instant now) {
        List<TenantEntity> matches = tenantRepository.findByTenantKeyIgnoreCase(DEFAULT_TENANT_KEY);
        if (matches.size() > 1) {
            throw new IllegalStateException("Default tenant key is not unique.");
        }
        TenantEntity tenant = matches.isEmpty()
                ? tenantRepository.save(new TenantEntity(DEFAULT_TENANT_KEY, "Default Tenant", true, now))
                : matches.getFirst();
        if (tenant.getId() == null || tenant.getId() != TenantScoped.DEFAULT_TENANT_ID) {
            throw new IllegalStateException("Default tenant id must remain stable.");
        }
        return tenant;
    }

    private TenantMembershipEntity ensureMembership(
            TenantEntity tenant,
            AuthUserEntity user,
            Instant now
    ) {
        List<TenantMembershipEntity> matches = membershipRepository.findByTenantIdAndUserId(
                tenant.getId(), user.getId());
        if (matches.size() > 1) {
            throw new IllegalStateException("Default tenant membership is not unique.");
        }
        if (matches.isEmpty()) {
            return membershipRepository.save(new TenantMembershipEntity(
                    tenant,
                    user.getId(),
                    Boolean.TRUE.equals(user.getEnabled()),
                    now
            ));
        }
        return matches.getFirst();
    }

    private void ensureMembershipRoles(TenantMembershipEntity membership, AuthUserEntity user) {
        Set<Long> existingRoleIds = new HashSet<>();
        for (TenantMembershipRoleEntity assignment : membershipRoleRepository.findByMembershipId(membership.getId())) {
            existingRoleIds.add(assignment.getRoleId());
        }
        user.getRoles().stream()
                .map(role -> role.getId())
                .filter(roleId -> !existingRoleIds.contains(roleId))
                .forEach(roleId -> membershipRoleRepository.save(
                        new TenantMembershipRoleEntity(membership, roleId)));
    }
}
