package com.brand.agentpoc.organization.infrastructure.persistence;

import com.brand.agentpoc.organization.domain.OrganizationNodeType;
import com.brand.agentpoc.tenant.domain.TenantScoped;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
        name = "organization_nodes",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_organization_nodes_tenant_key",
                columnNames = {"tenant_id", "node_key"}
        ),
        indexes = {
            @Index(name = "idx_organization_nodes_parent", columnList = "tenant_id,parent_id,id"),
            @Index(name = "idx_organization_nodes_tenant", columnList = "tenant_id,id")
        }
)
public class OrganizationNodeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "node_key", nullable = false, length = 128)
    private String nodeKey;

    @Column(name = "display_name", nullable = false, length = 128)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(name = "node_type", nullable = false, length = 32)
    private OrganizationNodeType nodeType;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "parent_id")
    private OrganizationNodeEntity parent;

    @Column(nullable = false)
    private Boolean enabled;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    protected OrganizationNodeEntity() {
    }

    public OrganizationNodeEntity(
            String nodeKey,
            String displayName,
            OrganizationNodeType nodeType,
            OrganizationNodeEntity parent,
            boolean enabled,
            Instant createdAt
    ) {
        this(TenantScoped.DEFAULT_TENANT_ID, nodeKey, displayName, nodeType, parent, enabled, createdAt);
    }

    public OrganizationNodeEntity(
            Long tenantId,
            String nodeKey,
            String displayName,
            OrganizationNodeType nodeType,
            OrganizationNodeEntity parent,
            boolean enabled,
            Instant createdAt
    ) {
        if (tenantId == null || (parent != null && !tenantId.equals(parent.getTenantId()))) {
            throw new IllegalArgumentException("organization node tenant is invalid.");
        }
        this.nodeKey = nodeKey;
        this.tenantId = tenantId;
        this.displayName = displayName;
        this.nodeType = nodeType;
        this.parent = parent;
        this.enabled = enabled;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public String getNodeKey() {
        return nodeKey;
    }

    public String getDisplayName() {
        return displayName;
    }

    public OrganizationNodeType getNodeType() {
        return nodeType;
    }

    public OrganizationNodeEntity getParent() {
        return parent;
    }

    public Boolean getEnabled() {
        return enabled;
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

    public void update(
            String displayName,
            OrganizationNodeType nodeType,
            OrganizationNodeEntity parent,
            boolean enabled,
            Instant now
    ) {
        if (parent != null && !tenantId.equals(parent.getTenantId())) {
            throw new IllegalArgumentException("organization node tenant is invalid.");
        }
        this.displayName = displayName;
        this.nodeType = nodeType;
        this.parent = parent;
        this.enabled = enabled;
        this.updatedAt = now;
    }
}
