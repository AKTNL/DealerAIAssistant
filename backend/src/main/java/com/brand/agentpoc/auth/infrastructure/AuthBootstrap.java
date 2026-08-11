package com.brand.agentpoc.auth.infrastructure;

import com.brand.agentpoc.auth.application.IdentityInputPolicy;
import com.brand.agentpoc.auth.domain.BuiltInRole;
import com.brand.agentpoc.auth.infrastructure.persistence.AuthAuditEventEntity;
import com.brand.agentpoc.auth.infrastructure.persistence.AuthAuditEventRepository;
import com.brand.agentpoc.auth.infrastructure.persistence.AuthRoleEntity;
import com.brand.agentpoc.auth.infrastructure.persistence.AuthRoleRepository;
import com.brand.agentpoc.auth.infrastructure.persistence.AuthUserEntity;
import com.brand.agentpoc.auth.infrastructure.persistence.AuthUserRepository;
import com.brand.agentpoc.config.AppProperties;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AuthBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AuthBootstrap.class);
    private static final String STARTUP_TRACE_ID = "startup";

    private final AuthRoleRepository roleRepository;
    private final AuthUserRepository userRepository;
    private final AuthAuditEventRepository auditEventRepository;
    private final PasswordEncoder passwordEncoder;
    private final IdentityInputPolicy inputPolicy;
    private final AppProperties appProperties;
    private final Clock clock;

    public AuthBootstrap(
            AuthRoleRepository roleRepository,
            AuthUserRepository userRepository,
            AuthAuditEventRepository auditEventRepository,
            PasswordEncoder passwordEncoder,
            IdentityInputPolicy inputPolicy,
            AppProperties appProperties,
            Clock clock
    ) {
        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
        this.auditEventRepository = auditEventRepository;
        this.passwordEncoder = passwordEncoder;
        this.inputPolicy = inputPolicy;
        this.appProperties = appProperties;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        Instant now = Instant.now(clock);
        Map<BuiltInRole, AuthRoleEntity> roles = ensureBuiltInRoles(now);
        ensureInitialAdministrator(roles.get(BuiltInRole.ADMIN), now);
    }

    private Map<BuiltInRole, AuthRoleEntity> ensureBuiltInRoles(Instant now) {
        Map<BuiltInRole, AuthRoleEntity> resolved = new EnumMap<>(BuiltInRole.class);
        for (BuiltInRole definition : BuiltInRole.values()) {
            List<AuthRoleEntity> matches = roleRepository.findByRoleKeyIgnoreCase(definition.roleKey());
            AuthRoleEntity role = matches.isEmpty()
                    ? roleRepository.save(new AuthRoleEntity(
                            definition.roleKey(),
                            definition.displayName(),
                            true,
                            definition.permissions(),
                            now
                    ))
                    : requireCanonicalBuiltInRole(matches, definition);
            resolved.put(definition, role);
        }
        log.info("Authentication roles initialized: builtInRoleCount={}", resolved.size());
        return resolved;
    }

    private AuthRoleEntity requireCanonicalBuiltInRole(
            List<AuthRoleEntity> matches,
            BuiltInRole definition
    ) {
        if (matches.size() != 1) {
            throw new IllegalStateException("Built-in role key is not unique: " + definition.roleKey());
        }
        AuthRoleEntity role = matches.getFirst();
        boolean canonical = Boolean.TRUE.equals(role.getBuiltIn())
                && definition.displayName().equals(role.getDisplayName())
                && definition.permissions().equals(role.getPermissions());
        if (!canonical) {
            throw new IllegalStateException("Built-in role definition drifted: " + definition.roleKey());
        }
        return role;
    }

    private void ensureInitialAdministrator(AuthRoleEntity adminRole, Instant now) {
        if (userRepository.count() > 0) {
            log.info("Authentication bootstrap skipped: existingUsers=true");
            return;
        }

        AppProperties.Auth.Bootstrap bootstrap = appProperties.getAuth().getBootstrap();
        boolean hasUsername = bootstrap.getUsername() != null && !bootstrap.getUsername().isBlank();
        boolean hasPassword = bootstrap.getPassword() != null && !bootstrap.getPassword().isBlank();
        if (!hasUsername && !hasPassword && !bootstrap.isRequired()) {
            log.warn("Authentication bootstrap pending: existingUsers=false, credentialsConfigured=false");
            return;
        }
        if (!hasUsername || !hasPassword) {
            throw new IllegalStateException("Administrator bootstrap username and password must be configured together.");
        }

        String username = inputPolicy.normalizeUsername(bootstrap.getUsername());
        inputPolicy.validatePassword(bootstrap.getPassword());
        String displayName = inputPolicy.normalizeDisplayName(bootstrap.getDisplayName(), username);
        AuthUserEntity administrator = userRepository.save(new AuthUserEntity(
                username,
                displayName,
                passwordEncoder.encode(bootstrap.getPassword()),
                true,
                true,
                Set.of(adminRole),
                now
        ));
        auditEventRepository.save(new AuthAuditEventEntity(
                null,
                "USER_BOOTSTRAP",
                "USER",
                String.valueOf(administrator.getId()),
                "SUCCESS",
                STARTUP_TRACE_ID,
                "initial_administrator_created",
                now
        ));
        log.info("Initial administrator created: userId={}", administrator.getId());
    }
}
