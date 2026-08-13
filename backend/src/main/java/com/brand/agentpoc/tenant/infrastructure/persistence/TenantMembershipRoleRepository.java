package com.brand.agentpoc.tenant.infrastructure.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantMembershipRoleRepository extends JpaRepository<TenantMembershipRoleEntity, Long> {

    List<TenantMembershipRoleEntity> findByMembershipId(Long membershipId);

    List<TenantMembershipRoleEntity> findByRoleId(Long roleId);

    void deleteByMembershipId(Long membershipId);
}
