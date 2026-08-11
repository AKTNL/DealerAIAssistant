package com.brand.agentpoc.auth.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(
        name = "auth_users",
        uniqueConstraints = @UniqueConstraint(name = "uq_auth_users_username", columnNames = "username")
)
public class AuthUserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String username;

    @Column(name = "display_name", nullable = false, length = 128)
    private String displayName;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(nullable = false)
    private Boolean enabled;

    @Column(name = "must_change_password", nullable = false)
    private Boolean mustChangePassword;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "auth_user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    private Set<AuthRoleEntity> roles;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    protected AuthUserEntity() {
        roles = new LinkedHashSet<>();
    }

    public AuthUserEntity(
            String username,
            String displayName,
            String passwordHash,
            boolean enabled,
            boolean mustChangePassword,
            Set<AuthRoleEntity> roles,
            Instant createdAt
    ) {
        this.username = username;
        this.displayName = displayName;
        this.passwordHash = passwordHash;
        this.enabled = enabled;
        this.mustChangePassword = mustChangePassword;
        this.roles = new LinkedHashSet<>(roles);
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public Boolean getMustChangePassword() {
        return mustChangePassword;
    }

    public Set<AuthRoleEntity> getRoles() {
        return Set.copyOf(roles);
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

    public void changePassword(String encodedPassword, boolean mustChangePassword, Instant now) {
        this.passwordHash = encodedPassword;
        this.mustChangePassword = mustChangePassword;
        this.updatedAt = now;
    }

    public void changeEnabled(boolean enabled, Instant now) {
        this.enabled = enabled;
        this.updatedAt = now;
    }

    public void replaceRoles(Set<AuthRoleEntity> roles, Instant now) {
        this.roles = new LinkedHashSet<>(roles);
        this.updatedAt = now;
    }
}
