package io.b2mash.b2b.b2bstrawman.takeon;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** The render attempts of the take-on reports (Phase 93 657D). */
public interface TakeOnReportRenderRepository extends JpaRepository<TakeOnReportRender, UUID> {

  /** The report's render row, created at zero attempts if absent (two drains may race to it). */
  @Modifying
  @Query(
      value =
          "INSERT INTO take_on_report_renders (report_id, attempts) VALUES (:id, 0)"
              + " ON CONFLICT (report_id) DO NOTHING",
      nativeQuery = true)
  int ensureExists(@Param("id") UUID reportId);

  /**
   * The render claim: the report's render row, locked, or empty when another drain is rendering it
   * — two drains never render one report at once.
   */
  @Query(
      value = "SELECT * FROM take_on_report_renders WHERE report_id = :id FOR UPDATE SKIP LOCKED",
      nativeQuery = true)
  Optional<TakeOnReportRender> claimSkipLocked(@Param("id") UUID reportId);

  List<TakeOnReportRender> findByReportIdIn(Collection<UUID> reportIds);
}
