package io.b2mash.b2b.b2bstrawman.schedule;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecurringScheduleRepository extends JpaRepository<RecurringSchedule, UUID> {
  List<RecurringSchedule> findByStatusAndNextExecutionDateLessThanEqual(
      String status, LocalDate date);

  List<RecurringSchedule> findByTemplateIdOrderByCreatedAtDesc(UUID templateId);

  List<RecurringSchedule> findByCustomerIdOrderByCreatedAtDesc(UUID customerId);

  boolean existsByTemplateId(UUID templateId);

  List<RecurringSchedule> findByStatusOrderByCreatedAtDesc(String status);

  List<RecurringSchedule> findByStatusAndCustomerIdOrderByCreatedAtDesc(
      String status, UUID customerId);

  List<RecurringSchedule> findByStatusAndTemplateIdOrderByCreatedAtDesc(
      String status, UUID templateId);

  List<RecurringSchedule> findByCustomerIdAndTemplateIdOrderByCreatedAtDesc(
      UUID customerId, UUID templateId);

  List<RecurringSchedule> findByStatusAndCustomerIdAndTemplateIdOrderByCreatedAtDesc(
      String status, UUID customerId, UUID templateId);

  List<RecurringSchedule> findAllByOrderByCreatedAtDesc();
}
