package io.b2mash.b2b.b2bstrawman.takeon;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Take-on reports (Phase 93 657C): one per committed trust take-on, never updated. */
public interface TakeOnReportRepository extends JpaRepository<TakeOnReport, UUID> {

  Optional<TakeOnReport> findByBatchId(UUID batchId);

  List<TakeOnReport> findByTrustAccountIdOrderByCutoverDateDesc(UUID trustAccountId);

  List<TakeOnReport> findAllByOrderByGeneratedAtDesc();
}
