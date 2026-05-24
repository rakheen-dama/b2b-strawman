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

  @Query(
      "SELECT f FROM ComplianceAuditFinding f WHERE f.report.id = :reportId"
          + " ORDER BY CASE f.severity"
          + " WHEN 'CRITICAL' THEN 1 WHEN 'HIGH' THEN 2"
          + " WHEN 'MEDIUM' THEN 3 WHEN 'LOW' THEN 4 WHEN 'INFO' THEN 5"
          + " ELSE 6 END ASC")
  Page<ComplianceAuditFinding> findByReportIdOrderBySeverity(
      @Param("reportId") UUID reportId, Pageable pageable);

  @Query(
      "SELECT f.severity, COUNT(f) FROM ComplianceAuditFinding f"
          + " WHERE f.report.id = :reportId GROUP BY f.severity")
  List<Object[]> countByReportIdGroupedBySeverity(@Param("reportId") UUID reportId);

  @Query(
      "SELECT f FROM ComplianceAuditFinding f WHERE f.report.id = :reportId"
          + " AND (:severities IS NULL OR f.severity IN :severities)"
          + " AND (:categories IS NULL OR f.category IN :categories)"
          + " AND (:statuses IS NULL OR f.status IN :statuses)"
          + " ORDER BY CASE f.severity"
          + " WHEN 'CRITICAL' THEN 1 WHEN 'HIGH' THEN 2"
          + " WHEN 'MEDIUM' THEN 3 WHEN 'LOW' THEN 4 WHEN 'INFO' THEN 5"
          + " ELSE 6 END ASC")
  Page<ComplianceAuditFinding> findByReportIdFiltered(
      @Param("reportId") UUID reportId,
      @Param("severities") List<String> severities,
      @Param("categories") List<String> categories,
      @Param("statuses") List<String> statuses,
      Pageable pageable);
}
