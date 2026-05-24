package io.b2mash.b2b.b2bstrawman.compliance;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ComplianceAuditFindingRepository
    extends JpaRepository<ComplianceAuditFinding, UUID> {

  Page<ComplianceAuditFinding> findByReportIdOrderBySeverityAsc(UUID reportId, Pageable pageable);

  Page<ComplianceAuditFinding> findByReportIdAndSeverityIn(
      UUID reportId, List<String> severities, Pageable pageable);

  Page<ComplianceAuditFinding> findByReportIdAndCategoryIn(
      UUID reportId, List<String> categories, Pageable pageable);

  Page<ComplianceAuditFinding> findByReportIdAndStatusIn(
      UUID reportId, List<String> statuses, Pageable pageable);

  @Query(
      "SELECT f FROM ComplianceAuditFinding f WHERE f.report.id = :reportId"
          + " AND (:severities IS NULL OR f.severity IN :severities)"
          + " AND (:categories IS NULL OR f.category IN :categories)"
          + " AND (:statuses IS NULL OR f.status IN :statuses)"
          + " ORDER BY f.severity ASC")
  Page<ComplianceAuditFinding> findByReportIdFiltered(
      @Param("reportId") UUID reportId,
      @Param("severities") List<String> severities,
      @Param("categories") List<String> categories,
      @Param("statuses") List<String> statuses,
      Pageable pageable);
}
