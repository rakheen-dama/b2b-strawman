package io.b2mash.b2b.b2bstrawman.takeon;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Take-on reports (Phase 93 657C): one per committed trust take-on, never updated. */
public interface TakeOnReportRepository extends JpaRepository<TakeOnReport, UUID> {

  Optional<TakeOnReport> findByBatchId(UUID batchId);

  List<TakeOnReport> findByTrustAccountIdOrderByCutoverDateDesc(UUID trustAccountId);

  List<TakeOnReport> findAllByOrderByGeneratedAtDesc();

  /**
   * 657D (§17.3.12): the reports missing their PDF or their CSV whose render has not failed for
   * good — oldest first. What the drain renders; the sweep's report-file clause asks the same.
   */
  @Query(
      value =
          """
          SELECT r.id FROM take_on_reports r
          WHERE (NOT EXISTS (SELECT 1 FROM take_on_report_files f
                              WHERE f.report_id = r.id AND f.format = 'PDF')
                 OR NOT EXISTS (SELECT 1 FROM take_on_report_files f
                                 WHERE f.report_id = r.id AND f.format = 'CSV'))
            AND NOT EXISTS (SELECT 1 FROM take_on_report_renders x
                             WHERE x.report_id = r.id AND x.failed_at IS NOT NULL)
          ORDER BY r.generated_at ASC, r.id ASC
          """,
      nativeQuery = true)
  List<UUID> findIdsNeedingRender();
}
