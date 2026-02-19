package io.b2mash.b2b.b2bstrawman.schedule;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecurringScheduleRepository extends JpaRepository<RecurringSchedule, UUID> {
  List<RecurringSchedule> findByStatusAndNextExecutionDateLessThanEqual(
      String status, LocalDate date);

  List<RecurringSchedule> findByTemplateId(UUID templateId);

  List<RecurringSchedule> findByCustomerId(UUID customerId);

  boolean existsByTemplateId(UUID templateId);

  boolean existsByTemplateIdAndStatus(UUID templateId, String status);
}
