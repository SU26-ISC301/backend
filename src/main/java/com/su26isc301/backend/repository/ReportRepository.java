package com.su26isc301.backend.repository;

import com.su26isc301.backend.entity.Report;
import com.su26isc301.backend.enums.ReportReasonCode;
import com.su26isc301.backend.enums.ReportStatus;
import com.su26isc301.backend.enums.ReportTargetType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReportRepository extends JpaRepository<Report, Long>, JpaSpecificationExecutor<Report> {

    Optional<Report> findFirstByReporterUserIdAndTargetTypeAndTargetIdAndReasonCodeAndCreatedAtAfter(
            UUID reporterUserId,
            ReportTargetType targetType,
            Long targetId,
            ReportReasonCode reasonCode,
            ZonedDateTime createdAt
    );

    Page<Report> findByReporterUserId(UUID reporterUserId, Pageable pageable);

    Page<Report> findByReporterUserIdAndStatus(UUID reporterUserId, ReportStatus status, Pageable pageable);

    Page<Report> findByVendorId(Long vendorId, Pageable pageable);

    Page<Report> findByVendorIdAndStatus(Long vendorId, ReportStatus status, Pageable pageable);

    @Query("SELECT COUNT(r) FROM Report r WHERE r.vendor.id = :vendorId AND r.status = :status AND r.createdAt >= :since")
    long countValidReportsForVendorSince(
            @Param("vendorId") Long vendorId,
            @Param("status") ReportStatus status,
            @Param("since") ZonedDateTime since
    );

    @Query("SELECT COUNT(r) FROM Report r WHERE r.vendor.id = :vendorId AND r.createdAt >= :since")
    long countReportsForVendorSince(
            @Param("vendorId") Long vendorId,
            @Param("since") ZonedDateTime since
    );

    long countByVendorId(Long vendorId);

    long countByStatus(ReportStatus status);

    long countByPriority(com.su26isc301.backend.enums.ReportPriority priority);

    long countByCreatedAtAfter(ZonedDateTime since);

    @Query("SELECT r.reasonCode, COUNT(r) FROM Report r GROUP BY r.reasonCode")
    List<Object[]> countReportsGroupByReasonCode();
}
