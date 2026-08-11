package com.brand.agentpoc.auth.infrastructure.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthRoleRepository extends JpaRepository<AuthRoleEntity, Long> {

    @EntityGraph(attributePaths = "permissions")
    List<AuthRoleEntity> findByRoleKeyIgnoreCase(String roleKey);

    @Override
    @EntityGraph(attributePaths = "permissions")
    List<AuthRoleEntity> findAll();
}
