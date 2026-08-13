package com.brand.agentpoc.tenant.infrastructure.persistence;

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

@Entity
@Table(
        name = "tenant_membership_roles",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_tenant_membership_roles",
                columnNames = {"membership_id", "role_id"}
        )
)
public class TenantMembershipRoleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "membership_id", nullable = false)
    private TenantMembershipEntity membership;

    @Column(name = "role_id", nullable = false)
    private Long roleId;

    protected TenantMembershipRoleEntity() {
    }

    public TenantMembershipRoleEntity(TenantMembershipEntity membership, Long roleId) {
        this.membership = membership;
        this.roleId = roleId;
    }

    public Long getId() {
        return id;
    }

    public TenantMembershipEntity getMembership() {
        return membership;
    }

    public Long getRoleId() {
        return roleId;
    }
}
