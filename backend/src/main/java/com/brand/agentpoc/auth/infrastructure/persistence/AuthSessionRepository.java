package com.brand.agentpoc.auth.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuthSessionRepository extends JpaRepository<AuthSessionEntity, Long> {

    @EntityGraph(attributePaths = {"user", "user.roles", "user.roles.permissions"})
    List<AuthSessionEntity> findByAccessTokenHash(String accessTokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select session from AuthSessionEntity session where session.refreshTokenHash = :refreshTokenHash")
    List<AuthSessionEntity> findRefreshTokenForUpdate(@Param("refreshTokenHash") String refreshTokenHash);

    List<AuthSessionEntity> findByFamilyKey(String familyKey);

    List<AuthSessionEntity> findByUserId(Long userId);
}
