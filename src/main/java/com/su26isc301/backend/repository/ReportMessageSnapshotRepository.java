package com.su26isc301.backend.repository;

import com.su26isc301.backend.entity.ReportMessageSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReportMessageSnapshotRepository extends JpaRepository<ReportMessageSnapshot, Long> {
    List<ReportMessageSnapshot> findByReportIdOrderByMessageCreatedAtAsc(Long reportId);
}
