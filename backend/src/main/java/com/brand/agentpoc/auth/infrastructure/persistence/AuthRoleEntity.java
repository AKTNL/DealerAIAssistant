package com.brand.agentpoc.auth.infrastructure.persistence;

import com.brand.agentpoc.auth.domain.PermissionKey;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(
        name = "auth_roles",
        uniqueConstraints = @UniqueConstraint(name = "uq_auth_roles_role_key", columnNames = "role_key")
)
public class AuthRoleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "role_key", nullable = false, length = 64)
    private String roleKey;

    @Column(name = "display_name", nullable = false, length = 128)
    private String displayName;

    @Column(name = "built_in", nullable = false)
    private Boolean builtIn;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "auth_role_permissions", joinColumns = @JoinColumn(name = "role_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "permission_key", nullable = false, length = 64)
    private Set<PermissionKey> permissions;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    protected AuthRoleEntity() {
        permissions = new LinkedHashSet<>();
    }

    public AuthRoleEntity(
            String roleKey,
            String displayName,
            boolean builtIn,
            Set<PermissionKey> permissions,
            Instant createdAt
    ) {
        this.roleKey = roleKey;
        this.displayName = displayName;
        this.builtIn = builtIn;
        this.permissions = new LinkedHashSet<>(permissions);
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getRoleKey() {
        return roleKey;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Boolean getBuiltIn() {
        return builtIn;
    }

    public Set<PermissionKey> getPermissions() {
        return Set.copyOf(permissions);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Long getVersion() {
        return version;
    }

    public void replacePermissions(Set<PermissionKey> permissions, Instant now) {
        this.permissions = new LinkedHashSet<>(permissions);
        this.updatedAt = now;
    }
}
