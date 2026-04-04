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
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PrescriptionTrackerService {

  private static final String MODULE_ID = "court_calendar";

  private static final Set<String> VALID_TYPES =
      Set.of("GENERAL_3Y", "DEBT_6Y", "MORTGAGE_30Y", "DELICT_3Y", "CONTRACT_3Y", "CUSTOM");

  private final PrescriptionTrackerRepository prescriptionTrackerRepository;
  private final VerticalModuleGuard moduleGuard;
  private final ProjectRepository projectRepository;
  private final CustomerRepository customerRepository;
  private final AuditService auditService;

  public PrescriptionTrackerService(
      PrescriptionTrackerRepository prescriptionTrackerRepository,
      VerticalModuleGuard moduleGuard,
      ProjectRepository projectRepository,
      CustomerRepository customerRepository,
      AuditService auditService) {
    this.prescriptionTrackerRepository = prescriptionTrackerRepository;
    this.moduleGuard = moduleGuard;
    this.projectRepository = projectRepository;
    this.customerRepository = customerRepository;
    this.auditService = auditService;
  }

  // --- DTO Records ---

  public record CreatePrescriptionTrackerRequest(
      @NotNull UUID projectId,
      @NotNull LocalDate causeOfActionDate,
      @NotBlank String prescriptionType,
      Integer customYears,
      String notes) {}

  public record UpdatePrescriptionTrackerRequest(
      @NotNull LocalDate causeOfActionDate,
      @NotBlank String prescriptionType,
      Integer customYears,
      String notes) {}

  public record InterruptRequest(
      @NotNull LocalDate interruptionDate, @NotBlank String interruptionReason) {}

  public record PrescriptionTrackerResponse(
      UUID id,
      UUID projectId,
      String projectName,
      UUID customerId,
      String customerName,
      LocalDate causeOfActionDate,
      String prescriptionType,
      Integer customYears,
      LocalDate prescriptionDate,
      LocalDate interruptionDate,
      String interruptionReason,
      String status,
      String notes,
      UUID createdBy,
      Instant createdAt,
      Instant updatedAt) {}

  public record PrescriptionTrackerFilters(String status, UUID customerId, UUID projectId) {}

  // --- Service Methods ---

  @Transactional
  public PrescriptionTrackerResponse create(
      CreatePrescriptionTrackerRequest request, UUID memberId) {
    moduleGuard.requireModule(MODULE_ID);

    var project =
        projectRepository
            .findById(request.projectId())
            .orElseThrow(() -> new ResourceNotFoundException("Project", request.projectId()));

    validatePrescriptionType(request.prescriptionType());

    var prescriptionDate =
        PrescriptionRuleRegistry.calculatePrescriptionDate(
            request.causeOfActionDate(), request.prescriptionType(), request.customYears());

    var tracker =
        new PrescriptionTracker(
            request.projectId(),
            project.getCustomerId(),
            request.causeOfActionDate(),
            request.prescriptionType(),
            request.customYears(),
            prescriptionDate,
            request.notes(),
            memberId);

    var saved = prescriptionTrackerRepository.save(tracker);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("prescription_tracker.created")
            .entityType("prescription_tracker")
            .entityId(saved.getId())
            .details(
                Map.of(
                    "project_id", saved.getProjectId().toString(),
                    "prescription_type", saved.getPrescriptionType(),
                    "cause_of_action_date", saved.getCauseOfActionDate().toString(),
                    "prescription_date", saved.getPrescriptionDate().toString()))
            .build());

    return toResponse(saved);
  }

  @Transactional
  public PrescriptionTrackerResponse update(UUID id, UpdatePrescriptionTrackerRequest request) {
    moduleGuard.requireModule(MODULE_ID);

    var tracker =
        prescriptionTrackerRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("PrescriptionTracker", id));

    requireMutableState(tracker);
    validatePrescriptionType(request.prescriptionType());

    var prescriptionDate =
        PrescriptionRuleRegistry.calculatePrescriptionDate(
            request.causeOfActionDate(), request.prescriptionType(), request.customYears());

    tracker.setCauseOfActionDate(request.causeOfActionDate());
    tracker.setPrescriptionType(request.prescriptionType());
    tracker.setCustomYears(request.customYears());
    tracker.setPrescriptionDate(prescriptionDate);
    tracker.setNotes(request.notes());
    tracker.setUpdatedAt(Instant.now());

    var saved = prescriptionTrackerRepository.save(tracker);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("prescription_tracker.updated")
            .entityType("prescription_tracker")
            .entityId(saved.getId())
            .details(
                Map.of(
                    "prescription_type", saved.getPrescriptionType(),
                    "cause_of_action_date", saved.getCauseOfActionDate().toString(),
                    "prescription_date", saved.getPrescriptionDate().toString()))
            .build());

    return toResponse(saved);
  }

  @Transactional
  public PrescriptionTrackerResponse interrupt(UUID id, InterruptRequest request) {
    moduleGuard.requireModule(MODULE_ID);

    var tracker =
        prescriptionTrackerRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("PrescriptionTracker", id));

    requireMutableState(tracker);

    tracker.setStatus("INTERRUPTED");
    tracker.setInterruptionDate(request.interruptionDate());
    tracker.setInterruptionReason(request.interruptionReason());
    tracker.setUpdatedAt(Instant.now());

    var saved = prescriptionTrackerRepository.save(tracker);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("prescription_tracker.interrupted")
            .entityType("prescription_tracker")
            .entityId(saved.getId())
            .details(
                Map.of(
                    "interruption_date", saved.getInterruptionDate().toString(),
                    "interruption_reason", saved.getInterruptionReason()))
            .build());

    return toResponse(saved);
  }

  @Transactional(readOnly = true)
  public Page<PrescriptionTrackerResponse> list(
      PrescriptionTrackerFilters filters, Pageable pageable) {
    moduleGuard.requireModule(MODULE_ID);

    var page =
        prescriptionTrackerRepository.findByFilters(
            filters.status(), filters.customerId(), filters.projectId(), pageable);

    var projectIds =
        page.getContent().stream()
            .map(PrescriptionTracker::getProjectId)
            .collect(Collectors.toSet());
    var customerIds =
        page.getContent().stream()
            .map(PrescriptionTracker::getCustomerId)
            .collect(Collectors.toSet());

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
  public PrescriptionTrackerResponse getById(UUID id) {
    moduleGuard.requireModule(MODULE_ID);

    var tracker =
        prescriptionTrackerRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("PrescriptionTracker", id));

    return toResponse(tracker);
  }

  // --- Private Helpers ---

  /**
   * Warning threshold in days — matches {@link CourtDateReminderJob#PRESCRIPTION_LOOKAHEAD_DAYS}.
   */
  private static final int PRESCRIPTION_WARNING_DAYS = 90;

  private static final Set<String> TERMINAL_STATES = Set.of("INTERRUPTED", "EXPIRED");

  /**
   * Computes the effective prescription status dynamically at query time. Terminal states
   * (INTERRUPTED, EXPIRED) are returned as-is. For RUNNING/WARNED trackers, the prescription date
   * is compared against today to determine if the tracker has expired or entered the warning
   * window.
   */
  private String computeEffectiveStatus(PrescriptionTracker entity) {
    if (TERMINAL_STATES.contains(entity.getStatus())) {
      return entity.getStatus();
    }
    LocalDate today = LocalDate.now();
    if (!entity.getPrescriptionDate().isAfter(today)) {
      return "EXPIRED";
    }
    long daysRemaining = ChronoUnit.DAYS.between(today, entity.getPrescriptionDate());
    if (daysRemaining <= PRESCRIPTION_WARNING_DAYS) {
      return "WARNED";
    }
    return entity.getStatus();
  }

  private void requireMutableState(PrescriptionTracker tracker) {
    if (TERMINAL_STATES.contains(tracker.getStatus())) {
      throw new InvalidStateException(
          "Prescription tracker is in a terminal state",
          "Cannot modify a prescription tracker with status " + tracker.getStatus());
    }
  }

  private void validatePrescriptionType(String prescriptionType) {
    if (!VALID_TYPES.contains(prescriptionType)) {
      throw new InvalidStateException(
          "Invalid prescription type",
          "Prescription type '" + prescriptionType + "' is not valid. Valid types: " + VALID_TYPES);
    }
  }

  private PrescriptionTrackerResponse toResponse(PrescriptionTracker entity) {
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

  private PrescriptionTrackerResponse toResponse(
      PrescriptionTracker entity,
      Map<UUID, io.b2mash.b2b.b2bstrawman.project.Project> projectMap,
      Map<UUID, io.b2mash.b2b.b2bstrawman.customer.Customer> customerMap) {
    var project = projectMap.get(entity.getProjectId());
    var customer = customerMap.get(entity.getCustomerId());
    return buildResponse(
        entity,
        project != null ? project.getName() : null,
        customer != null ? customer.getName() : null);
  }

  private PrescriptionTrackerResponse buildResponse(
      PrescriptionTracker entity, String projectName, String customerName) {
    return new PrescriptionTrackerResponse(
        entity.getId(),
        entity.getProjectId(),
        projectName,
        entity.getCustomerId(),
        customerName,
        entity.getCauseOfActionDate(),
        entity.getPrescriptionType(),
        entity.getCustomYears(),
        entity.getPrescriptionDate(),
        entity.getInterruptionDate(),
        entity.getInterruptionReason(),
        computeEffectiveStatus(entity),
        entity.getNotes(),
        entity.getCreatedBy(),
        entity.getCreatedAt(),
        entity.getUpdatedAt());
  }
}
