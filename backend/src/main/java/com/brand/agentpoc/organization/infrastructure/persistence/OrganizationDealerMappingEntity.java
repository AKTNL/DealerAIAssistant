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
        name = "organization_dealer_mappings",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_organization_mappings_tenant_code",
                columnNames = {"tenant_id", "dealer_code"}
        ),
        indexes = @jakarta.persistence.Index(
                name = "idx_organization_mappings_tenant_node",
                columnList = "tenant_id,organization_node_id,dealer_code"
        )
)
public class OrganizationDealerMappingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "organization_node_id", nullable = false)
    private OrganizationNodeEntity organizationNode;

    @Column(name = "dealer_code", nullable = false, length = 64)
    private String dealerCode;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected OrganizationDealerMappingEntity() {
    }

    public OrganizationDealerMappingEntity(
            OrganizationNodeEntity organizationNode,
            String dealerCode,
            Instant createdAt
    ) {
        this(organizationNode.getTenantId(), organizationNode, dealerCode, createdAt);
    }

    public OrganizationDealerMappingEntity(
            Long tenantId,
            OrganizationNodeEntity organizationNode,
            String dealerCode,
            Instant createdAt
    ) {
        if (tenantId == null || organizationNode == null || !tenantId.equals(organizationNode.getTenantId())) {
            throw new IllegalArgumentException("organization mapping tenant is invalid.");
        }
        this.organizationNode = organizationNode;
        this.tenantId = tenantId;
        this.dealerCode = dealerCode;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public OrganizationNodeEntity getOrganizationNode() {
        return organizationNode;
    }

    public String getDealerCode() {
        return dealerCode;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
