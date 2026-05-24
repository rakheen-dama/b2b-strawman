package io.b2mash.b2b.b2bstrawman.compliance;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ComplianceAuditReportRepository
    extends JpaRepository<ComplianceAuditReport, UUID> {

  Page<ComplianceAuditReport> findByStatusOrderByCreatedAtDesc(String status, Pageable pageable);

  Optional<ComplianceAuditReport> findByExecutionId(UUID executionId);
}
