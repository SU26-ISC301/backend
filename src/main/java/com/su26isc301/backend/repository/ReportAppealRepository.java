package com.su26isc301.backend.repository;

import com.su26isc301.backend.entity.ReportAppeal;
import com.su26isc301.backend.enums.AppealStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ReportAppealRepository extends JpaRepository<ReportAppeal, Long> {
    List<ReportAppeal> findByReportId(Long reportId);
    Optional<ReportAppeal> findFirstByReportIdAndStatusIn(Long reportId, Collection<AppealStatus> statuses);
}
