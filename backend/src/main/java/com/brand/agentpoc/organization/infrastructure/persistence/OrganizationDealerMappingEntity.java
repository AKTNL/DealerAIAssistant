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
                name = "uq_organization_dealer_mappings_code",
                columnNames = "dealer_code"
        )
)
public class OrganizationDealerMappingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

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
        this.organizationNode = organizationNode;
        this.dealerCode = dealerCode;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
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
