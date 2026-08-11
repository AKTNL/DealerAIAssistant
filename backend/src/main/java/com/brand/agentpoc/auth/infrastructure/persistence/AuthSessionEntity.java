package com.brand.agentpoc.auth.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.Instant;

@Entity
@Table(
        name = "auth_sessions",
        uniqueConstraints = {
            @UniqueConstraint(name = "uq_auth_sessions_access_hash", columnNames = "access_token_hash"),
            @UniqueConstraint(name = "uq_auth_sessions_refresh_hash", columnNames = "refresh_token_hash")
        },
        indexes = {
            @Index(name = "idx_auth_sessions_family", columnList = "family_key,issued_at"),
            @Index(name = "idx_auth_sessions_user", columnList = "user_id,revoked_at,refresh_expires_at")
        }
)
public class AuthSessionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "family_key", nullable = false, length = 64)
    private String familyKey;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AuthUserEntity user;

    @Column(name = "access_token_hash", nullable = false, length = 64)
    private String accessTokenHash;

    @Column(name = "refresh_token_hash", nullable = false, length = 64)
    private String refreshTokenHash;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "access_expires_at", nullable = false)
    private Instant accessExpiresAt;

    @Column(name = "refresh_expires_at", nullable = false)
    private Instant refreshExpiresAt;

    @Column(name = "rotated_at")
    private Instant rotatedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "revocation_reason", length = 64)
    private String revocationReason;

    @Version
    @Column(nullable = false)
    private Long version;

    protected AuthSessionEntity() {
    }

    public AuthSessionEntity(
            String familyKey,
            AuthUserEntity user,
            String accessTokenHash,
            String refreshTokenHash,
            Instant issuedAt,
            Instant accessExpiresAt,
            Instant refreshExpiresAt
    ) {
        this.familyKey = familyKey;
        this.user = user;
        this.accessTokenHash = accessTokenHash;
        this.refreshTokenHash = refreshTokenHash;
        this.issuedAt = issuedAt;
        this.accessExpiresAt = accessExpiresAt;
        this.refreshExpiresAt = refreshExpiresAt;
    }

    public Long getId() {
        return id;
    }

    public String getFamilyKey() {
        return familyKey;
    }

    public AuthUserEntity getUser() {
        return user;
    }

    public String getAccessTokenHash() {
        return accessTokenHash;
    }

    public String getRefreshTokenHash() {
        return refreshTokenHash;
    }

    public Instant getIssuedAt() {
        return issuedAt;
    }

    public Instant getAccessExpiresAt() {
        return accessExpiresAt;
    }

    public Instant getRefreshExpiresAt() {
        return refreshExpiresAt;
    }

    public Instant getRotatedAt() {
        return rotatedAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public String getRevocationReason() {
        return revocationReason;
    }

    public Long getVersion() {
        return version;
    }

    public void markRotated(Instant now) {
        this.rotatedAt = now;
    }

    public void revoke(Instant now, String reason) {
        if (revokedAt == null) {
            this.revokedAt = now;
            this.revocationReason = reason;
        }
    }
}
