package io.b2mash.b2b.b2bstrawman.verticals.legal.conflictcheck;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConflictCheckRepository extends JpaRepository<ConflictCheck, UUID> {

  @Query(
      """
      SELECT c FROM ConflictCheck c
      WHERE (:result IS NULL OR c.result = :result)
        AND (:checkType IS NULL OR c.checkType = :checkType)
        AND (CAST(:checkedBy AS uuid) IS NULL OR c.checkedBy = :checkedBy)
        AND (CAST(:dateFrom AS timestamp) IS NULL OR c.checkedAt >= :dateFrom)
        AND (CAST(:dateTo AS timestamp) IS NULL OR c.checkedAt <= :dateTo)
      ORDER BY c.checkedAt DESC
      """)
  Page<ConflictCheck> findByFilters(
      @Param("result") String result,
      @Param("checkType") String checkType,
      @Param("checkedBy") UUID checkedBy,
      @Param("dateFrom") Instant dateFrom,
      @Param("dateTo") Instant dateTo,
      Pageable pageable);
}
