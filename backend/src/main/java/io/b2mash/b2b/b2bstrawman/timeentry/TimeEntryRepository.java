package io.b2mash.b2b.b2bstrawman.timeentry;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TimeEntryRepository extends JpaRepository<TimeEntry, UUID> {

  /**
   * JPQL-based findById that respects Hibernate @Filter (unlike JpaRepository.findById which uses
   * EntityManager.find and bypasses @Filter). Required for shared-schema tenant isolation.
   */
  @Query("SELECT te FROM TimeEntry te WHERE te.id = :id")
  Optional<TimeEntry> findOneById(@Param("id") UUID id);

  @Query(
      "SELECT te FROM TimeEntry te WHERE te.taskId = :taskId ORDER BY te.date DESC, te.createdAt"
          + " DESC")
  List<TimeEntry> findByTaskId(@Param("taskId") UUID taskId);

  @Query(
      """
      SELECT te FROM TimeEntry te
      WHERE te.memberId = :memberId
        AND te.date >= :from
        AND te.date <= :to
      ORDER BY te.date DESC, te.createdAt DESC
      """)
  List<TimeEntry> findByMemberIdAndDateBetween(
      @Param("memberId") UUID memberId, @Param("from") LocalDate from, @Param("to") LocalDate to);
}
