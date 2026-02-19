package io.b2mash.b2b.b2bstrawman.schedule;

import java.time.LocalDate;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScheduleExecutionRepository extends JpaRepository<ScheduleExecution, UUID> {
  boolean existsByScheduleIdAndPeriodStart(UUID scheduleId, LocalDate periodStart);

  Page<ScheduleExecution> findByScheduleIdOrderByPeriodStartDesc(
      UUID scheduleId, Pageable pageable);
}
