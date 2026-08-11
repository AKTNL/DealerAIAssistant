package com.brand.agentpoc.organization.infrastructure.persistence;

import com.brand.agentpoc.organization.domain.OrganizationNodeType;
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
        uniqueConstraints = @UniqueConstraint(name = "uq_organization_nodes_key", columnNames = "node_key"),
        indexes = @Index(name = "idx_organization_nodes_parent", columnList = "parent_id,id")
)
public class OrganizationNodeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

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
        this.nodeKey = nodeKey;
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
        this.displayName = displayName;
        this.nodeType = nodeType;
        this.parent = parent;
        this.enabled = enabled;
        this.updatedAt = now;
    }
}
