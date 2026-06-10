package com.su26isc301.backend.repository;

import com.su26isc301.backend.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    @Query("SELECT a FROM AuditLog a WHERE " +
           "(cast(:query as string) IS NULL OR LOWER(a.userEmail) LIKE LOWER(CONCAT('%', cast(:query as string), '%')) " +
           " OR LOWER(a.action) LIKE LOWER(CONCAT('%', cast(:query as string), '%')) " +
           " OR LOWER(a.ipAddress) LIKE LOWER(CONCAT('%', cast(:query as string), '%')) " +
           " OR LOWER(a.payloadSnapshot) LIKE LOWER(CONCAT('%', cast(:query as string), '%'))) " +
           "AND (cast(:action as string) IS NULL OR a.action = cast(:action as string)) " +
           "ORDER BY a.createdAt DESC")
    Page<AuditLog> findWithFilters(
            @Param("query") String query,
            @Param("action") String action,
            Pageable pageable
    );

    @Query("SELECT a FROM AuditLog a WHERE a.userId = :userId AND " +
           "(cast(:query as string) IS NULL OR LOWER(a.action) LIKE LOWER(CONCAT('%', cast(:query as string), '%')) " +
           " OR LOWER(a.ipAddress) LIKE LOWER(CONCAT('%', cast(:query as string), '%')) " +
           " OR LOWER(a.payloadSnapshot) LIKE LOWER(CONCAT('%', cast(:query as string), '%'))) " +
           "AND (cast(:action as string) IS NULL OR a.action = cast(:action as string)) " +
           "ORDER BY a.createdAt DESC")
    Page<AuditLog> findByUserIdWithFilters(
            @Param("userId") UUID userId,
            @Param("query") String query,
            @Param("action") String action,
            Pageable pageable
    );

    @Query("SELECT DISTINCT a.action FROM AuditLog a ORDER BY a.action ASC")
    List<String> findDistinctActions();

    @Query("SELECT DISTINCT a.action FROM AuditLog a WHERE a.userId = :userId ORDER BY a.action ASC")
    List<String> findDistinctActionsByUserId(@Param("userId") UUID userId);
}
