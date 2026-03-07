package io.b2mash.b2b.b2bstrawman.capacity;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LeaveBlockRepository extends JpaRepository<LeaveBlock, UUID> {

  List<LeaveBlock> findByMemberIdOrderByStartDateDesc(UUID memberId);

  @Query(
      """
      SELECT lb FROM LeaveBlock lb
      WHERE lb.memberId = :memberId
        AND lb.startDate <= :end
        AND lb.endDate >= :start
      """)
  List<LeaveBlock> findByMemberIdAndOverlapping(
      @Param("memberId") UUID memberId,
      @Param("start") LocalDate start,
      @Param("end") LocalDate end);

  @Query(
      """
      SELECT lb FROM LeaveBlock lb
      WHERE lb.startDate <= :end
        AND lb.endDate >= :start
      """)
  List<LeaveBlock> findAllOverlapping(@Param("start") LocalDate start, @Param("end") LocalDate end);
}
