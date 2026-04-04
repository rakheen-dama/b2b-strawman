package io.b2mash.b2b.b2bstrawman.verticals.legal.courtcalendar;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.verticals.VerticalModuleGuard;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CourtCalendarService {

  private static final String MODULE_ID = "court_calendar";

  private static final Set<String> VALID_DATE_TYPES =
      Set.of(
          "HEARING",
          "TRIAL",
          "MOTION",
          "MEDIATION",
          "ARBITRATION",
          "PRE_TRIAL",
          "CASE_MANAGEMENT",
          "TAXATION",
          "OTHER");

  private static final Map<String, Set<String>> ALLOWED_TRANSITIONS =
      Map.of(
          "SCHEDULED", Set.of("POSTPONED", "HEARD", "CANCELLED"),
          "POSTPONED", Set.of("HEARD", "CANCELLED"));

  private final CourtDateRepository courtDateRepository;
  private final PrescriptionTrackerRepository prescriptionTrackerRepository;
  private final VerticalModuleGuard moduleGuard;
  private final ProjectRepository projectRepository;
  private final CustomerRepository customerRepository;
  private final AuditService auditService;

  public CourtCalendarService(
      CourtDateRepository courtDateRepository,
      PrescriptionTrackerRepository prescriptionTrackerRepository,
      VerticalModuleGuard moduleGuard,
      ProjectRepository projectRepository,
      CustomerRepository customerRepository,
      AuditService auditService) {
    this.courtDateRepository = courtDateRepository;
    this.prescriptionTrackerRepository = prescriptionTrackerRepository;
    this.moduleGuard = moduleGuard;
    this.projectRepository = projectRepository;
    this.customerRepository = customerRepository;
    this.auditService = auditService;
  }

  // --- DTO Records ---

  public record CreateCourtDateRequest(
      @NotNull UUID projectId,
      @NotBlank String dateType,
      @NotNull LocalDate scheduledDate,
      LocalTime scheduledTime,
      @NotBlank String courtName,
      String courtReference,
      String judgeMagistrate,
      String description,
      Integer reminderDays) {}

  public record UpdateCourtDateRequest(
      @NotBlank String dateType,
      @NotNull LocalDate scheduledDate,
      LocalTime scheduledTime,
      @NotBlank String courtName,
      String courtReference,
      String judgeMagistrate,
      String description,
      Integer reminderDays) {}

  public record CourtDateResponse(
      UUID id,
      UUID projectId,
      String projectName,
      UUID customerId,
      String customerName,
      String dateType,
      LocalDate scheduledDate,
      LocalTime scheduledTime,
      String courtName,
      String courtReference,
      String judgeMagistrate,
      String description,
      String status,
      String outcome,
      int reminderDays,
      UUID createdBy,
      Instant createdAt,
      Instant updatedAt) {}

  public record PostponeRequest(@NotNull LocalDate newDate, String reason) {}

  public record CancelRequest(String reason) {}

  public record OutcomeRequest(@NotBlank String outcome) {}

  public record CourtDateFilters(
      LocalDate dateFrom,
      LocalDate dateTo,
      String dateType,
      String status,
      UUID customerId,
      UUID projectId) {}

  public record UpcomingCourtDateItem(
      UUID id,
      UUID projectId,
      String projectName,
      String dateType,
      LocalDate scheduledDate,
      String courtName,
      String status,
      long daysUntil) {}

  public record UpcomingPrescriptionWarningItem(
      UUID id,
      UUID projectId,
      String projectName,
      String prescriptionType,
      LocalDate prescriptionDate,
      String status,
      long daysUntil) {}

  public record UpcomingResponse(
      List<UpcomingCourtDateItem> courtDates,
      List<UpcomingPrescriptionWarningItem> prescriptionWarnings) {}

  // --- Service Methods ---

  @Transactional
  public CourtDateResponse createCourtDate(CreateCourtDateRequest request, UUID memberId) {
    moduleGuard.requireModule(MODULE_ID);

    var project =
        projectRepository
            .findById(request.projectId())
            .orElseThrow(() -> new ResourceNotFoundException("Project", request.projectId()));

    validateDateType(request.dateType());

    int reminderDays = request.reminderDays() != null ? request.reminderDays() : 7;

    var courtDate =
        new CourtDate(
            request.projectId(),
            project.getCustomerId(),
            request.dateType(),
            request.scheduledDate(),
            request.scheduledTime(),
            request.courtName(),
            request.courtReference(),
            request.judgeMagistrate(),
            request.description(),
            reminderDays,
            memberId);

    var saved = courtDateRepository.save(courtDate);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("court_date.created")
            .entityType("court_date")
            .entityId(saved.getId())
            .details(
                Map.of(
                    "project_id", saved.getProjectId().toString(),
                    "date_type", saved.getDateType(),
                    "scheduled_date", saved.getScheduledDate().toString(),
                    "court_name", saved.getCourtName()))
            .build());

    return toResponse(saved);
  }

  @Transactional
  public CourtDateResponse updateCourtDate(UUID id, UpdateCourtDateRequest request) {
    moduleGuard.requireModule(MODULE_ID);

    var courtDate =
        courtDateRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("CourtDate", id));

    requireMutableState(courtDate);
    validateDateType(request.dateType());

    courtDate.setDateType(request.dateType());
    courtDate.setScheduledDate(request.scheduledDate());
    courtDate.setScheduledTime(request.scheduledTime());
    courtDate.setCourtName(request.courtName());
    courtDate.setCourtReference(request.courtReference());
    courtDate.setJudgeMagistrate(request.judgeMagistrate());
    courtDate.setDescription(request.description());
    if (request.reminderDays() != null) {
      courtDate.setReminderDays(request.reminderDays());
    }
    courtDate.setUpdatedAt(Instant.now());

    var saved = courtDateRepository.save(courtDate);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("court_date.updated")
            .entityType("court_date")
            .entityId(saved.getId())
            .details(
                Map.of(
                    "date_type", saved.getDateType(),
                    "scheduled_date", saved.getScheduledDate().toString(),
                    "court_name", saved.getCourtName()))
            .build());

    return toResponse(saved);
  }

  @Transactional
  public CourtDateResponse postponeCourtDate(UUID id, PostponeRequest request) {
    moduleGuard.requireModule(MODULE_ID);

    var courtDate =
        courtDateRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("CourtDate", id));

    validateTransition(courtDate.getStatus(), "POSTPONED");

    courtDate.setScheduledDate(request.newDate());
    courtDate.setStatus("POSTPONED");
    if (request.reason() != null && !request.reason().isBlank()) {
      courtDate.setOutcome("Postponed: " + request.reason());
    }
    courtDate.setUpdatedAt(Instant.now());

    var saved = courtDateRepository.save(courtDate);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("court_date.postponed")
            .entityType("court_date")
            .entityId(saved.getId())
            .details(
                Map.of(
                    "new_date",
                    request.newDate().toString(),
                    "reason",
                    request.reason() != null ? request.reason() : ""))
            .build());

    return toResponse(saved);
  }

  @Transactional
  public CourtDateResponse cancelCourtDate(UUID id, CancelRequest request) {
    moduleGuard.requireModule(MODULE_ID);

    var courtDate =
        courtDateRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("CourtDate", id));

    validateTransition(courtDate.getStatus(), "CANCELLED");

    courtDate.setStatus("CANCELLED");
    if (request.reason() != null) {
      courtDate.setOutcome(request.reason());
    }
    courtDate.setUpdatedAt(Instant.now());

    var saved = courtDateRepository.save(courtDate);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("court_date.cancelled")
            .entityType("court_date")
            .entityId(saved.getId())
            .details(Map.of("reason", request.reason() != null ? request.reason() : ""))
            .build());

    return toResponse(saved);
  }

  @Transactional
  public CourtDateResponse recordOutcome(UUID id, OutcomeRequest request) {
    moduleGuard.requireModule(MODULE_ID);

    var courtDate =
        courtDateRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("CourtDate", id));

    validateTransition(courtDate.getStatus(), "HEARD");

    courtDate.setStatus("HEARD");
    courtDate.setOutcome(request.outcome());
    courtDate.setUpdatedAt(Instant.now());

    var saved = courtDateRepository.save(courtDate);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("court_date.outcome_recorded")
            .entityType("court_date")
            .entityId(saved.getId())
            .details(Map.of("outcome", request.outcome()))
            .build());

    return toResponse(saved);
  }

  @Transactional(readOnly = true)
  public Page<CourtDateResponse> list(CourtDateFilters filters, Pageable pageable) {
    moduleGuard.requireModule(MODULE_ID);

    var page =
        courtDateRepository.findByFilters(
            filters.dateFrom(),
            filters.dateTo(),
            filters.dateType(),
            filters.status(),
            filters.customerId(),
            filters.projectId(),
            pageable);

    // Batch-fetch projects and customers to avoid N+1 queries
    var projectIds =
        page.getContent().stream().map(CourtDate::getProjectId).collect(Collectors.toSet());
    var customerIds =
        page.getContent().stream().map(CourtDate::getCustomerId).collect(Collectors.toSet());

    var projectMap =
        projectIds.isEmpty()
            ? Map.<UUID, io.b2mash.b2b.b2bstrawman.project.Project>of()
            : projectRepository.findByIdIn(projectIds).stream()
                .collect(
                    Collectors.toMap(
                        io.b2mash.b2b.b2bstrawman.project.Project::getId, Function.identity()));

    var customerMap =
        customerIds.isEmpty()
            ? Map.<UUID, io.b2mash.b2b.b2bstrawman.customer.Customer>of()
            : customerRepository.findByIdIn(customerIds).stream()
                .collect(
                    Collectors.toMap(
                        io.b2mash.b2b.b2bstrawman.customer.Customer::getId, Function.identity()));

    return page.map(entity -> toResponse(entity, projectMap, customerMap));
  }

  @Transactional(readOnly = true)
  public CourtDateResponse getById(UUID id) {
    moduleGuard.requireModule(MODULE_ID);

    var courtDate =
        courtDateRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("CourtDate", id));

    return toResponse(courtDate);
  }

  private static final int UPCOMING_COURT_DATE_DAYS = 30;
  private static final int UPCOMING_PRESCRIPTION_DAYS = 90;

  @Transactional(readOnly = true)
  public UpcomingResponse getUpcoming() {
    moduleGuard.requireModule(MODULE_ID);

    var today = LocalDate.now();

    // Upcoming court dates within lookahead window
    var rawCourtDates =
        courtDateRepository.findByStatusInAndScheduledDateBetween(
            List.of("SCHEDULED", "POSTPONED"), today, today.plusDays(UPCOMING_COURT_DATE_DAYS));

    // Prescription warnings within lookahead window
    var rawPrescriptions =
        prescriptionTrackerRepository.findByStatusInAndPrescriptionDateBetween(
            List.of("RUNNING", "WARNED"), today, today.plusDays(UPCOMING_PRESCRIPTION_DAYS));

    // Batch-fetch all referenced projects to avoid N+1
    var allProjectIds =
        Stream.concat(
                rawCourtDates.stream().map(CourtDate::getProjectId),
                rawPrescriptions.stream().map(PrescriptionTracker::getProjectId))
            .collect(Collectors.toSet());

    var projectMap =
        allProjectIds.isEmpty()
            ? Map.<UUID, io.b2mash.b2b.b2bstrawman.project.Project>of()
            : projectRepository.findByIdIn(allProjectIds).stream()
                .collect(
                    Collectors.toMap(
                        io.b2mash.b2b.b2bstrawman.project.Project::getId, Function.identity()));

    var courtDates =
        rawCourtDates.stream()
            .map(
                cd -> {
                  var project = projectMap.get(cd.getProjectId());
                  long daysUntil = ChronoUnit.DAYS.between(today, cd.getScheduledDate());
                  return new UpcomingCourtDateItem(
                      cd.getId(),
                      cd.getProjectId(),
                      project != null ? project.getName() : null,
                      cd.getDateType(),
                      cd.getScheduledDate(),
                      cd.getCourtName(),
                      cd.getStatus(),
                      daysUntil);
                })
            .toList();

    var prescriptionWarnings =
        rawPrescriptions.stream()
            .map(
                pt -> {
                  var project = projectMap.get(pt.getProjectId());
                  long daysUntil = ChronoUnit.DAYS.between(today, pt.getPrescriptionDate());
                  return new UpcomingPrescriptionWarningItem(
                      pt.getId(),
                      pt.getProjectId(),
                      project != null ? project.getName() : null,
                      pt.getPrescriptionType(),
                      pt.getPrescriptionDate(),
                      pt.getStatus(),
                      daysUntil);
                })
            .toList();

    return new UpcomingResponse(courtDates, prescriptionWarnings);
  }

  // --- Private Helpers ---

  private static final Set<String> TERMINAL_STATES = Set.of("HEARD", "CANCELLED");

  private void requireMutableState(CourtDate courtDate) {
    if (TERMINAL_STATES.contains(courtDate.getStatus())) {
      throw new InvalidStateException(
          "Court date is in a terminal state",
          "Cannot modify a court date with status " + courtDate.getStatus());
    }
  }

  private void validateTransition(String currentStatus, String targetStatus) {
    var allowed = ALLOWED_TRANSITIONS.getOrDefault(currentStatus, Set.of());
    if (!allowed.contains(targetStatus)) {
      throw new InvalidStateException(
          "Invalid court date state transition",
          "Cannot transition from " + currentStatus + " to " + targetStatus);
    }
  }

  private void validateDateType(String dateType) {
    if (!VALID_DATE_TYPES.contains(dateType)) {
      throw new InvalidStateException(
          "Invalid date type",
          "Date type '" + dateType + "' is not valid. Valid types: " + VALID_DATE_TYPES);
    }
  }

  private CourtDateResponse toResponse(CourtDate entity) {
    String projectName = null;
    String customerName = null;

    var project = projectRepository.findById(entity.getProjectId()).orElse(null);
    if (project != null) {
      projectName = project.getName();
    }

    var customer = customerRepository.findById(entity.getCustomerId()).orElse(null);
    if (customer != null) {
      customerName = customer.getName();
    }

    return buildResponse(entity, projectName, customerName);
  }

  private CourtDateResponse toResponse(
      CourtDate entity,
      Map<UUID, io.b2mash.b2b.b2bstrawman.project.Project> projectMap,
      Map<UUID, io.b2mash.b2b.b2bstrawman.customer.Customer> customerMap) {
    var project = projectMap.get(entity.getProjectId());
    var customer = customerMap.get(entity.getCustomerId());
    return buildResponse(
        entity,
        project != null ? project.getName() : null,
        customer != null ? customer.getName() : null);
  }

  private CourtDateResponse buildResponse(
      CourtDate entity, String projectName, String customerName) {
    return new CourtDateResponse(
        entity.getId(),
        entity.getProjectId(),
        projectName,
        entity.getCustomerId(),
        customerName,
        entity.getDateType(),
        entity.getScheduledDate(),
        entity.getScheduledTime(),
        entity.getCourtName(),
        entity.getCourtReference(),
        entity.getJudgeMagistrate(),
        entity.getDescription(),
        entity.getStatus(),
        entity.getOutcome(),
        entity.getReminderDays(),
        entity.getCreatedBy(),
        entity.getCreatedAt(),
        entity.getUpdatedAt());
  }
}
