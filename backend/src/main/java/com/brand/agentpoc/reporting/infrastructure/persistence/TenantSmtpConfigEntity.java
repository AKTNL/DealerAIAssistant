package com.brand.agentpoc.reporting.infrastructure.persistence;

import com.brand.agentpoc.reporting.domain.SmtpSecurityMode;
import com.brand.agentpoc.tenant.domain.TenantScoped;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.Instant;

@Entity
@Table(
        name = "tenant_smtp_configs",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_tenant_smtp_configs_tenant",
                columnNames = "tenant_id"
        ),
        indexes = @Index(name = "idx_tenant_smtp_configs_updated", columnList = "tenant_id,updated_at")
)
public class TenantSmtpConfigEntity implements TenantScoped {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(nullable = false, length = 255)
    private String host;

    @Column(nullable = false)
    private Integer port;

    @Column(name = "security_mode", nullable = false, length = 16)
    private String securityMode;

    @Column(nullable = false, length = 254)
    private String username;

    @Column(name = "password_ciphertext", nullable = false, length = 2048)
    private String passwordCiphertext;

    @Column(name = "secret_version", nullable = false)
    private Integer secretVersion;

    @Column(name = "from_address", nullable = false, length = 254)
    private String fromAddress;

    @Column(name = "from_display_name", length = 128)
    private String fromDisplayName;

    @Column(nullable = false)
    private Boolean enabled;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    protected TenantSmtpConfigEntity() {
    }

    public TenantSmtpConfigEntity(
            Long tenantId,
            String host,
            int port,
            SmtpSecurityMode securityMode,
            String username,
            String passwordCiphertext,
            int secretVersion,
            String fromAddress,
            String fromDisplayName,
            boolean enabled,
            Instant now
    ) {
        this.tenantId = requireTenant(tenantId);
        this.host = required(host, "host");
        this.port = port;
        this.securityMode = securityMode.name();
        this.username = required(username, "username");
        this.passwordCiphertext = required(passwordCiphertext, "passwordCiphertext");
        this.secretVersion = secretVersion;
        this.fromAddress = required(fromAddress, "fromAddress");
        this.fromDisplayName = blankToNull(fromDisplayName);
        this.enabled = enabled;
        this.createdAt = now == null ? Instant.now() : now;
        this.updatedAt = this.createdAt;
    }

    public void update(
            String host,
            int port,
            SmtpSecurityMode securityMode,
            String username,
            String passwordCiphertext,
            int secretVersion,
            String fromAddress,
            String fromDisplayName,
            boolean enabled,
            Instant now
    ) {
        this.host = required(host, "host");
        this.port = port;
        this.securityMode = securityMode.name();
        this.username = required(username, "username");
        this.passwordCiphertext = required(passwordCiphertext, "passwordCiphertext");
        this.secretVersion = secretVersion;
        this.fromAddress = required(fromAddress, "fromAddress");
        this.fromDisplayName = blankToNull(fromDisplayName);
        this.enabled = enabled;
        this.updatedAt = now == null ? Instant.now() : now;
    }

    public Long getId() { return id; }
    public Long getTenantId() { return tenantId; }
    public String getHost() { return host; }
    public Integer getPort() { return port; }
    public SmtpSecurityMode getSecurityMode() { return SmtpSecurityMode.valueOf(securityMode); }
    public String getUsername() { return username; }
    public String getPasswordCiphertext() { return passwordCiphertext; }
    public Integer getSecretVersion() { return secretVersion; }
    public String getFromAddress() { return fromAddress; }
    public String getFromDisplayName() { return fromDisplayName; }
    public Boolean getEnabled() { return enabled; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Long getVersion() { return version; }

    private static Long requireTenant(Long tenantId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId is required.");
        }
        return tenantId;
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required.");
        }
        return value.trim();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
