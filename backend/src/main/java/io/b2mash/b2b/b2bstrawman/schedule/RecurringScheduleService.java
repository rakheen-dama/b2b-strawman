package io.b2mash.b2b.b2bstrawman.schedule;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceConflictException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.member.MemberRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.projecttemplate.PeriodCalculator;
import io.b2mash.b2b.b2bstrawman.projecttemplate.ProjectTemplateRepository;
import io.b2mash.b2b.b2bstrawman.schedule.dto.CreateScheduleRequest;
import io.b2mash.b2b.b2bstrawman.schedule.dto.ScheduleResponse;
import io.b2mash.b2b.b2bstrawman.schedule.dto.UpdateScheduleRequest;
import io.b2mash.b2b.b2bstrawman.schedule.event.SchedulePausedEvent;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RecurringScheduleService {

  private static final Logger log = LoggerFactory.getLogger(RecurringScheduleService.class);

  private final RecurringScheduleRepository scheduleRepository;
  private final ProjectTemplateRepository templateRepository;
  private final CustomerRepository customerRepository;
  private final MemberRepository memberRepository;
  private final PeriodCalculator periodCalculator;
  private final AuditService auditService;
  private final ApplicationEventPublisher eventPublisher;

  public RecurringScheduleService(
      RecurringScheduleRepository scheduleRepository,
      ProjectTemplateRepository templateRepository,
      CustomerRepository customerRepository,
      MemberRepository memberRepository,
      PeriodCalculator periodCalculator,
      AuditService auditService,
      ApplicationEventPublisher eventPublisher) {
    this.scheduleRepository = scheduleRepository;
    this.templateRepository = templateRepository;
    this.customerRepository = customerRepository;
    this.memberRepository = memberRepository;
    this.periodCalculator = periodCalculator;
    this.auditService = auditService;
    this.eventPublisher = eventPublisher;
  }

  @Transactional
  public ScheduleResponse create(CreateScheduleRequest request, UUID memberId) {
    var template =
        templateRepository
            .findById(request.templateId())
            .orElseThrow(
                () -> new ResourceNotFoundException("ProjectTemplate", request.templateId()));

    if (!template.isActive()) {
      throw new InvalidStateException(
          "Template inactive", "Template must be active to create a schedule.");
    }

    customerRepository
        .findById(request.customerId())
        .orElseThrow(() -> new ResourceNotFoundException("Customer", request.customerId()));

    if (request.projectLeadMemberId() != null) {
      memberRepository
          .findById(request.projectLeadMemberId())
          .orElseThrow(
              () -> new ResourceNotFoundException("Member", request.projectLeadMemberId()));
    }

    var schedule =
        new RecurringSchedule(
            request.templateId(),
            request.customerId(),
            request.nameOverride(),
            request.frequency(),
            request.startDate(),
            request.endDate(),
            request.leadTimeDays(),
            request.projectLeadMemberId(),
            memberId);

    // Calculate initial nextExecutionDate
    var period = periodCalculator.calculateNextPeriod(request.startDate(), request.frequency(), 0);
    LocalDate nextExecution =
        periodCalculator.calculateNextExecutionDate(period.start(), request.leadTimeDays());
    schedule.setNextExecutionDate(nextExecution);

    try {
      schedule = scheduleRepository.saveAndFlush(schedule);
    } catch (DataIntegrityViolationException ex) {
      throw new ResourceConflictException(
          "Duplicate schedule",
          "A schedule already exists for this template, customer, and frequency combination.");
    }

    log.info("Created recurring schedule {}", schedule.getId());

    String templateName = template.getName();
    String customerName = resolveCustomerName(request.customerId());

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("schedule.created")
            .entityType("recurring_schedule")
            .entityId(schedule.getId())
            .details(
                Map.of(
                    "template_name", templateName,
                    "customer_name", customerName,
                    "frequency", schedule.getFrequency()))
            .build());

    return buildResponse(schedule);
  }

  @Transactional
  public ScheduleResponse update(UUID id, UpdateScheduleRequest request) {
    var schedule =
        scheduleRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("RecurringSchedule", id));

    if ("COMPLETED".equals(schedule.getStatus())) {
      throw new InvalidStateException(
          "Cannot update completed schedule", "Cannot update a completed schedule");
    }

    if (request.projectLeadMemberId() != null) {
      memberRepository
          .findById(request.projectLeadMemberId())
          .orElseThrow(
              () -> new ResourceNotFoundException("Member", request.projectLeadMemberId()));
    }

    schedule.updateMutableFields(
        request.nameOverride(),
        request.endDate(),
        request.leadTimeDays(),
        request.projectLeadMemberId());

    schedule = scheduleRepository.save(schedule);

    log.info("Updated recurring schedule {}", id);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("schedule.updated")
            .entityType("recurring_schedule")
            .entityId(id)
            .details(Map.of("lead_time_days", String.valueOf(request.leadTimeDays())))
            .build());

    return buildResponse(schedule);
  }

  @Transactional
  public void delete(UUID id) {
    var schedule =
        scheduleRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("RecurringSchedule", id));

    if ("ACTIVE".equals(schedule.getStatus())) {
      throw new ResourceConflictException(
          "Cannot delete active schedule", "Active schedule must be paused before deletion.");
    }

    scheduleRepository.deleteById(id);

    log.info("Deleted recurring schedule {}", id);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("schedule.deleted")
            .entityType("recurring_schedule")
            .entityId(id)
            .details(Map.of("status", schedule.getStatus()))
            .build());
  }

  @Transactional(readOnly = true)
  public List<ScheduleResponse> list(String status, UUID customerId, UUID templateId) {
    List<RecurringSchedule> schedules;

    if (status != null && customerId != null && templateId != null) {
      schedules =
          scheduleRepository.findByStatusAndCustomerIdAndTemplateIdOrderByCreatedAtDesc(
              status, customerId, templateId);
    } else if (status != null && customerId != null) {
      schedules =
          scheduleRepository.findByStatusAndCustomerIdOrderByCreatedAtDesc(status, customerId);
    } else if (status != null && templateId != null) {
      schedules =
          scheduleRepository.findByStatusAndTemplateIdOrderByCreatedAtDesc(status, templateId);
    } else if (customerId != null && templateId != null) {
      schedules =
          scheduleRepository.findByCustomerIdAndTemplateIdOrderByCreatedAtDesc(
              customerId, templateId);
    } else if (status != null) {
      schedules = scheduleRepository.findByStatusOrderByCreatedAtDesc(status);
    } else if (customerId != null) {
      schedules = scheduleRepository.findByCustomerIdOrderByCreatedAtDesc(customerId);
    } else if (templateId != null) {
      schedules = scheduleRepository.findByTemplateIdOrderByCreatedAtDesc(templateId);
    } else {
      schedules = scheduleRepository.findAllByOrderByCreatedAtDesc();
    }

    return schedules.stream().map(this::buildResponse).toList();
  }

  @Transactional(readOnly = true)
  public ScheduleResponse get(UUID id) {
    var schedule =
        scheduleRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("RecurringSchedule", id));
    return buildResponse(schedule);
  }

  @Transactional
  public ScheduleResponse pause(UUID id) {
    var schedule =
        scheduleRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("RecurringSchedule", id));

    if (!"ACTIVE".equals(schedule.getStatus())) {
      throw new InvalidStateException(
          "Invalid state transition",
          "Schedule must be ACTIVE to pause. Current status: " + schedule.getStatus());
    }

    schedule.setStatus("PAUSED");
    schedule = scheduleRepository.save(schedule);

    log.info("Paused recurring schedule {}", id);

    String templateName = resolveTemplateName(schedule.getTemplateId());
    String customerName = resolveCustomerName(schedule.getCustomerId());

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("schedule.paused")
            .entityType("recurring_schedule")
            .entityId(id)
            .details(
                Map.of(
                    "template_name", templateName,
                    "customer_name", customerName))
            .build());

    publishSchedulePausedEvent(schedule, templateName, customerName);

    return buildResponse(schedule);
  }

  @Transactional
  public ScheduleResponse resume(UUID id) {
    var schedule =
        scheduleRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("RecurringSchedule", id));

    if (!"PAUSED".equals(schedule.getStatus())) {
      throw new InvalidStateException(
          "Invalid state transition",
          "Schedule must be PAUSED to resume. Current status: " + schedule.getStatus());
    }

    schedule.setStatus("ACTIVE");
    periodCalculator.recalculateNextExecutionOnResume(schedule);
    schedule = scheduleRepository.save(schedule);

    log.info("Resumed recurring schedule {}", id);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("schedule.resumed")
            .entityType("recurring_schedule")
            .entityId(id)
            .details(Map.of("next_execution_date", schedule.getNextExecutionDate().toString()))
            .build());

    return buildResponse(schedule);
  }

  @Transactional
  public ScheduleResponse complete(UUID id) {
    var schedule =
        scheduleRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("RecurringSchedule", id));

    if ("COMPLETED".equals(schedule.getStatus())) {
      throw new InvalidStateException("Invalid state transition", "Schedule is already completed.");
    }

    schedule.setStatus("COMPLETED");
    schedule = scheduleRepository.save(schedule);

    log.info("Completed recurring schedule {}", id);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("schedule.completed")
            .entityType("recurring_schedule")
            .entityId(id)
            .details(Map.of("execution_count", String.valueOf(schedule.getExecutionCount())))
            .build());

    return buildResponse(schedule);
  }

  private ScheduleResponse buildResponse(RecurringSchedule schedule) {
    String templateName = resolveTemplateName(schedule.getTemplateId());
    String customerName = resolveCustomerName(schedule.getCustomerId());
    String projectLeadName = resolveMemberName(schedule.getProjectLeadMemberId());

    return new ScheduleResponse(
        schedule.getId(),
        schedule.getTemplateId(),
        templateName,
        schedule.getCustomerId(),
        customerName,
        schedule.getFrequency(),
        schedule.getStartDate(),
        schedule.getEndDate(),
        schedule.getLeadTimeDays(),
        schedule.getStatus(),
        schedule.getNextExecutionDate(),
        schedule.getLastExecutedAt(),
        schedule.getExecutionCount(),
        schedule.getProjectLeadMemberId(),
        projectLeadName,
        schedule.getNameOverride(),
        schedule.getCreatedBy(),
        schedule.getCreatedAt(),
        schedule.getUpdatedAt());
  }

  private String resolveTemplateName(UUID templateId) {
    return templateRepository.findById(templateId).map(t -> t.getName()).orElse(null);
  }

  private String resolveCustomerName(UUID customerId) {
    return customerRepository.findById(customerId).map(c -> c.getName()).orElse(null);
  }

  private String resolveMemberName(UUID memberId) {
    if (memberId == null) {
      return null;
    }
    return memberRepository.findById(memberId).map(m -> m.getName()).orElse(null);
  }

  private void publishSchedulePausedEvent(
      RecurringSchedule schedule, String templateName, String customerName) {
    String tenantId = RequestScopes.TENANT_ID.isBound() ? RequestScopes.TENANT_ID.get() : null;
    String orgId = RequestScopes.ORG_ID.isBound() ? RequestScopes.ORG_ID.get() : null;
    UUID actorMemberId = RequestScopes.MEMBER_ID.isBound() ? RequestScopes.MEMBER_ID.get() : null;
    eventPublisher.publishEvent(
        new SchedulePausedEvent(
            schedule.getId(),
            templateName,
            customerName,
            actorMemberId,
            tenantId,
            orgId,
            Instant.now()));
  }
}
