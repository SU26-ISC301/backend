package com.su26isc301.backend.repository;

import com.su26isc301.backend.entity.ReportEvidence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReportEvidenceRepository extends JpaRepository<ReportEvidence, Long> {
    List<ReportEvidence> findByReportId(Long reportId);
}
