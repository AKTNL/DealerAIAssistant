package com.brand.agentpoc.organization.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

@Entity
@Table(
        name = "organization_role_grants",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_organization_role_grants",
                columnNames = {"role_id", "organization_node_id"}
        )
)
public class OrganizationRoleGrantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "role_id", nullable = false)
    private Long roleId;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "organization_node_id", nullable = false)
    private OrganizationNodeEntity organizationNode;

    @Column(name = "include_descendants", nullable = false)
    private Boolean includeDescendants;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected OrganizationRoleGrantEntity() {
    }

    public OrganizationRoleGrantEntity(
            Long roleId,
            OrganizationNodeEntity organizationNode,
            boolean includeDescendants,
            Instant createdAt
    ) {
        this.roleId = roleId;
        this.organizationNode = organizationNode;
        this.includeDescendants = includeDescendants;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public Long getRoleId() {
        return roleId;
    }

    public OrganizationNodeEntity getOrganizationNode() {
        return organizationNode;
    }

    public Boolean getIncludeDescendants() {
        return includeDescendants;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
