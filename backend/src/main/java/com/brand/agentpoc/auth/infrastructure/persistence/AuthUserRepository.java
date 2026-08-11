package com.brand.agentpoc.auth.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface AuthUserRepository extends JpaRepository<AuthUserEntity, Long> {

    @EntityGraph(attributePaths = {"roles", "roles.permissions"})
    List<AuthUserEntity> findByUsernameIgnoreCase(String username);

    @Override
    @EntityGraph(attributePaths = {"roles", "roles.permissions"})
    List<AuthUserEntity> findAll();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select user from AuthUserEntity user order by user.id")
    List<AuthUserEntity> lockAllForAdministrationUpdate();
}
