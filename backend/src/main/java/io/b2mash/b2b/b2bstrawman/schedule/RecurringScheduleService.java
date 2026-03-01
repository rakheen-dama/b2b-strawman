package io.b2mash.b2b.b2bstrawman.schedule;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.customer.Customer;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.customer.LifecycleStatus;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceConflictException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.member.MemberNameResolver;
import io.b2mash.b2b.b2bstrawman.member.MemberRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.notification.NotificationService;
import io.b2mash.b2b.b2bstrawman.prerequisite.PrerequisiteService;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.projecttemplate.NameTokenResolver;
import io.b2mash.b2b.b2bstrawman.projecttemplate.PeriodCalculator;
import io.b2mash.b2b.b2bstrawman.projecttemplate.ProjectTemplateRepository;
import io.b2mash.b2b.b2bstrawman.projecttemplate.ProjectTemplateService;
import io.b2mash.b2b.b2bstrawman.schedule.dto.CreateScheduleRequest;
import io.b2mash.b2b.b2bstrawman.schedule.dto.ScheduleExecutionResponse;
import io.b2mash.b2b.b2bstrawman.schedule.dto.ScheduleResponse;
import io.b2mash.b2b.b2bstrawman.schedule.dto.UpdateScheduleRequest;
import io.b2mash.b2b.b2bstrawman.schedule.event.RecurringProjectCreatedEvent;
import io.b2mash.b2b.b2bstrawman.schedule.event.ScheduleCompletedEvent;
import io.b2mash.b2b.b2bstrawman.schedule.event.SchedulePausedEvent;
import io.b2mash.b2b.b2bstrawman.schedule.event.ScheduleSkippedEvent;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RecurringScheduleService {

  private static final Logger log = LoggerFactory.getLogger(RecurringScheduleService.class);

  private final RecurringScheduleRepository scheduleRepository;
  private final ProjectTemplateRepository templateRepository;
  private final CustomerRepository customerRepository;
  private final MemberRepository memberRepository;
  private final MemberNameResolver memberNameResolver;
  private final PeriodCalculator periodCalculator;
  private final AuditService auditService;
  private final ApplicationEventPublisher eventPublisher;
  private final ScheduleExecutionRepository executionRepository;
  private final ProjectRepository projectRepository;
  private final ProjectTemplateService projectTemplateService;
  private final NameTokenResolver nameTokenResolver;
  private final PrerequisiteService prerequisiteService;
  private final NotificationService notificationService;

  public RecurringScheduleService(
      RecurringScheduleRepository scheduleRepository,
      ProjectTemplateRepository templateRepository,
      CustomerRepository customerRepository,
      MemberRepository memberRepository,
      MemberNameResolver memberNameResolver,
      PeriodCalculator periodCalculator,
      AuditService auditService,
      ApplicationEventPublisher eventPublisher,
      ScheduleExecutionRepository executionRepository,
      ProjectRepository projectRepository,
      ProjectTemplateService projectTemplateService,
      NameTokenResolver nameTokenResolver,
      PrerequisiteService prerequisiteService,
      NotificationService notificationService) {
    this.scheduleRepository = scheduleRepository;
    this.templateRepository = templateRepository;
    this.customerRepository = customerRepository;
    this.memberRepository = memberRepository;
    this.memberNameResolver = memberNameResolver;
    this.periodCalculator = periodCalculator;
    this.auditService = auditService;
    this.eventPublisher = eventPublisher;
    this.executionRepository = executionRepository;
    this.projectRepository = projectRepository;
    this.projectTemplateService = projectTemplateService;
    this.nameTokenResolver = nameTokenResolver;
    this.prerequisiteService = prerequisiteService;
    this.notificationService = notificationService;
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

    var templateNames = resolveTemplateNames(schedules);
    var customerNames = resolveCustomerNames(schedules);
    var memberNames = resolveMemberNames(schedules);

    return schedules.stream()
        .map(s -> buildResponse(s, templateNames, customerNames, memberNames))
        .toList();
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

  @Transactional(readOnly = true)
  public List<ScheduleExecutionResponse> listExecutions(UUID scheduleId) {
    scheduleRepository
        .findById(scheduleId)
        .orElseThrow(() -> new ResourceNotFoundException("RecurringSchedule", scheduleId));

    var page =
        executionRepository.findByScheduleIdOrderByPeriodStartDesc(
            scheduleId, PageRequest.of(0, 50));

    var executions = page.getContent();
    var projectNames = resolveProjectNames(executions);

    return executions.stream()
        .map(
            e ->
                new ScheduleExecutionResponse(
                    e.getId(),
                    e.getProjectId(),
                    e.getProjectId() != null
                        ? projectNames.getOrDefault(e.getProjectId(), "")
                        : null,
                    e.getPeriodStart(),
                    e.getPeriodEnd(),
                    e.getExecutedAt()))
        .toList();
  }

  // --- Scheduler execution methods ---

  /**
   * Finds all due ACTIVE schedules for the current tenant. Called by {@link
   * RecurringScheduleExecutor} with tenant ScopedValues already bound.
   */
  @Transactional(readOnly = true)
  public List<RecurringSchedule> findDueSchedules() {
    LocalDate today = LocalDate.now();
    return scheduleRepository.findByStatusAndNextExecutionDateLessThanEqual("ACTIVE", today);
  }

  /**
   * Executes a single recurring schedule: checks customer lifecycle, calculates period, creates
   * project from template, records execution, and advances to next period.
   *
   * @return true if a project was created, false if skipped (idempotency, lifecycle)
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public boolean executeSingleSchedule(RecurringSchedule detachedSchedule) {
    // Re-load schedule in this REQUIRES_NEW transaction to avoid detached entity issues
    var schedule =
        scheduleRepository
            .findById(detachedSchedule.getId())
            .orElseThrow(
                () -> new ResourceNotFoundException("RecurringSchedule", detachedSchedule.getId()));

    // 1. Load customer and check lifecycle
    var customer =
        customerRepository
            .findById(schedule.getCustomerId())
            .orElseThrow(() -> new ResourceNotFoundException("Customer", schedule.getCustomerId()));

    var template =
        templateRepository
            .findById(schedule.getTemplateId())
            .orElseThrow(
                () -> new ResourceNotFoundException("ProjectTemplate", schedule.getTemplateId()));

    String templateName = template.getName();
    String customerName = customer.getName();

    if (isInactiveLifecycle(customer)) {
      log.warn(
          "Skipping schedule {} — customer {} has lifecycle status {}",
          schedule.getId(),
          customerName,
          customer.getLifecycleStatus());

      publishScheduleSkippedEvent(schedule, templateName, customerName, customer);

      auditService.log(
          AuditEventBuilder.builder()
              .eventType("schedule_execution.skipped")
              .entityType("recurring_schedule")
              .entityId(schedule.getId())
              .details(
                  Map.of(
                      "reason",
                      "Customer lifecycle: " + customer.getLifecycleStatus(),
                      "customer_name",
                      customerName,
                      "customer_lifecycle_status",
                      customer.getLifecycleStatus().toString()))
              .build());

      // Record execution to permanently skip this period (increments executionCount)
      schedule.recordExecution(Instant.now());
      advanceToNextPeriod(schedule, schedule.getExecutionCount());
      scheduleRepository.save(schedule);

      // Check auto-completion after advancing
      checkAutoCompletion(schedule, templateName, customerName);

      return false;
    }

    // 2. Period calculation
    var period =
        periodCalculator.calculateNextPeriod(
            schedule.getStartDate(), schedule.getFrequency(), schedule.getExecutionCount());

    // 3. Idempotency check
    if (executionRepository.existsByScheduleIdAndPeriodStart(schedule.getId(), period.start())) {
      log.info(
          "Schedule {} already executed for period starting {}", schedule.getId(), period.start());
      advanceToNextPeriod(schedule, schedule.getExecutionCount());
      scheduleRepository.save(schedule);
      return false;
    }

    // 4. Resolve project name
    String namePattern =
        schedule.getNameOverride() != null ? schedule.getNameOverride() : template.getNamePattern();
    String projectName =
        nameTokenResolver.resolveNameTokens(
            namePattern, customer, period.start(), period.start(), period.end());

    // 5. Create project from template
    UUID actingMemberId =
        schedule.getProjectLeadMemberId() != null
            ? schedule.getProjectLeadMemberId()
            : schedule.getCreatedBy();
    Project project =
        projectTemplateService.instantiateFromTemplate(
            template, projectName, customer, schedule.getProjectLeadMemberId(), actingMemberId);

    // 5a. Check engagement prerequisites and notify if not met
    var prereqCheck =
        prerequisiteService.checkEngagementPrerequisites(customer.getId(), template.getId());
    if (!prereqCheck.passed()) {
      String fieldList =
          prereqCheck.violations().stream()
              .map(v -> v.fieldSlug())
              .collect(Collectors.joining(", "));
      String notifTitle =
          "Customer %s is missing fields required for %s: %s"
              .formatted(customerName, templateName, fieldList);
      try {
        notificationService.notifyAdminsAndOwners(
            "PREREQUISITE_BLOCKED_ACTIVATION", notifTitle, null, "PROJECT", project.getId());
      } catch (Exception e) {
        log.warn("Failed to send prerequisite notification: {}", e.getMessage());
      }
    }

    // 6. Record execution
    executionRepository.save(
        new ScheduleExecution(
            schedule.getId(), project.getId(), period.start(), period.end(), Instant.now()));

    // 7. Advance schedule — recordExecution increments executionCount
    schedule.recordExecution(Instant.now());
    advanceToNextPeriod(schedule, schedule.getExecutionCount());
    scheduleRepository.save(schedule);

    // 8. Auto-completion check
    checkAutoCompletion(schedule, templateName, customerName);

    // 9. Audit log
    auditService.log(
        AuditEventBuilder.builder()
            .eventType("recurring_project.created")
            .entityType("recurring_schedule")
            .entityId(schedule.getId())
            .details(
                Map.of(
                    "template_name", templateName,
                    "project_name", projectName,
                    "customer_name", customerName,
                    "period_start", period.start().toString(),
                    "period_end", period.end().toString()))
            .build());

    // 10. Publish event
    publishRecurringProjectCreatedEvent(schedule, project, projectName, customerName, templateName);

    return true;
  }

  private boolean isInactiveLifecycle(Customer customer) {
    var status = customer.getLifecycleStatus();
    return status == LifecycleStatus.OFFBOARDED || status == LifecycleStatus.PROSPECT;
  }

  /**
   * Advances the schedule's nextExecutionDate to the period after the given periodIndex. This
   * ensures the next execution date always moves forward regardless of whether the current period
   * was executed or skipped.
   */
  private void advanceToNextPeriod(RecurringSchedule schedule, int nextPeriodIndex) {
    var nextPeriod =
        periodCalculator.calculateNextPeriod(
            schedule.getStartDate(), schedule.getFrequency(), nextPeriodIndex);
    LocalDate nextExec =
        periodCalculator.calculateNextExecutionDate(nextPeriod.start(), schedule.getLeadTimeDays());
    schedule.setNextExecutionDate(nextExec);
  }

  private void checkAutoCompletion(
      RecurringSchedule schedule, String templateName, String customerName) {
    if (schedule.getEndDate() != null
        && schedule.getNextExecutionDate() != null
        && schedule.getNextExecutionDate().isAfter(schedule.getEndDate())) {
      schedule.setStatus("COMPLETED");
      scheduleRepository.save(schedule);
      publishScheduleCompletedEvent(schedule, templateName, customerName);
      auditService.log(
          AuditEventBuilder.builder()
              .eventType("schedule.completed")
              .entityType("recurring_schedule")
              .entityId(schedule.getId())
              .details(Map.of("execution_count", String.valueOf(schedule.getExecutionCount())))
              .build());
    }
  }

  private void publishRecurringProjectCreatedEvent(
      RecurringSchedule schedule,
      Project project,
      String projectName,
      String customerName,
      String templateName) {
    String tenantId = RequestScopes.getTenantIdOrNull();
    String orgId = RequestScopes.getOrgIdOrNull();
    eventPublisher.publishEvent(
        new RecurringProjectCreatedEvent(
            schedule.getId(),
            project.getId(),
            projectName,
            customerName,
            templateName,
            schedule.getProjectLeadMemberId(),
            null,
            "Scheduler",
            tenantId,
            orgId,
            Instant.now()));
  }

  private void publishScheduleSkippedEvent(
      RecurringSchedule schedule, String templateName, String customerName, Customer customer) {
    String tenantId = RequestScopes.getTenantIdOrNull();
    String orgId = RequestScopes.getOrgIdOrNull();
    eventPublisher.publishEvent(
        new ScheduleSkippedEvent(
            schedule.getId(),
            templateName,
            customerName,
            customer.getLifecycleStatus().toString(),
            "Customer lifecycle: " + customer.getLifecycleStatus(),
            tenantId,
            orgId,
            Instant.now()));
  }

  private void publishScheduleCompletedEvent(
      RecurringSchedule schedule, String templateName, String customerName) {
    String tenantId = RequestScopes.getTenantIdOrNull();
    String orgId = RequestScopes.getOrgIdOrNull();
    eventPublisher.publishEvent(
        new ScheduleCompletedEvent(
            schedule.getId(),
            templateName,
            customerName,
            schedule.getExecutionCount(),
            tenantId,
            orgId,
            Instant.now()));
  }

  private ScheduleResponse buildResponse(RecurringSchedule schedule) {
    String templateName = resolveTemplateName(schedule.getTemplateId());
    String customerName = resolveCustomerName(schedule.getCustomerId());
    String projectLeadName = resolveMemberName(schedule.getProjectLeadMemberId());
    String createdByName = resolveMemberName(schedule.getCreatedBy());

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
        createdByName,
        schedule.getCreatedAt(),
        schedule.getUpdatedAt());
  }

  private ScheduleResponse buildResponse(
      RecurringSchedule schedule,
      Map<UUID, String> templateNames,
      Map<UUID, String> customerNames,
      Map<UUID, String> memberNames) {
    return new ScheduleResponse(
        schedule.getId(),
        schedule.getTemplateId(),
        templateNames.getOrDefault(schedule.getTemplateId(), ""),
        schedule.getCustomerId(),
        customerNames.getOrDefault(schedule.getCustomerId(), ""),
        schedule.getFrequency(),
        schedule.getStartDate(),
        schedule.getEndDate(),
        schedule.getLeadTimeDays(),
        schedule.getStatus(),
        schedule.getNextExecutionDate(),
        schedule.getLastExecutedAt(),
        schedule.getExecutionCount(),
        schedule.getProjectLeadMemberId(),
        schedule.getProjectLeadMemberId() != null
            ? memberNames.getOrDefault(schedule.getProjectLeadMemberId(), "")
            : null,
        schedule.getNameOverride(),
        schedule.getCreatedBy(),
        schedule.getCreatedBy() != null
            ? memberNames.getOrDefault(schedule.getCreatedBy(), "")
            : null,
        schedule.getCreatedAt(),
        schedule.getUpdatedAt());
  }

  private Map<UUID, String> resolveTemplateNames(List<RecurringSchedule> schedules) {
    var ids =
        schedules.stream()
            .map(RecurringSchedule::getTemplateId)
            .filter(Objects::nonNull)
            .distinct()
            .toList();
    if (ids.isEmpty()) return Map.of();
    return templateRepository.findAllById(ids).stream()
        .collect(
            Collectors.toMap(
                t -> t.getId(), t -> t.getName() != null ? t.getName() : "", (a, b) -> a));
  }

  private Map<UUID, String> resolveCustomerNames(List<RecurringSchedule> schedules) {
    var ids =
        schedules.stream()
            .map(RecurringSchedule::getCustomerId)
            .filter(Objects::nonNull)
            .distinct()
            .toList();
    if (ids.isEmpty()) return Map.of();
    return customerRepository.findAllById(ids).stream()
        .collect(
            Collectors.toMap(
                Customer::getId, c -> c.getName() != null ? c.getName() : "", (a, b) -> a));
  }

  private Map<UUID, String> resolveMemberNames(List<RecurringSchedule> schedules) {
    var ids =
        schedules.stream()
            .flatMap(s -> Stream.of(s.getProjectLeadMemberId(), s.getCreatedBy()))
            .filter(Objects::nonNull)
            .distinct()
            .toList();
    return memberNameResolver.resolveNames(ids);
  }

  private Map<UUID, String> resolveProjectNames(List<ScheduleExecution> executions) {
    var ids =
        executions.stream()
            .map(ScheduleExecution::getProjectId)
            .filter(Objects::nonNull)
            .distinct()
            .toList();
    if (ids.isEmpty()) return Map.of();
    return projectRepository.findAllById(ids).stream()
        .collect(
            Collectors.toMap(
                Project::getId, p -> p.getName() != null ? p.getName() : "", (a, b) -> a));
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
    return memberNameResolver.resolveNameOrNull(memberId);
  }

  private void publishSchedulePausedEvent(
      RecurringSchedule schedule, String templateName, String customerName) {
    String tenantId = RequestScopes.getTenantIdOrNull();
    String orgId = RequestScopes.getOrgIdOrNull();
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
