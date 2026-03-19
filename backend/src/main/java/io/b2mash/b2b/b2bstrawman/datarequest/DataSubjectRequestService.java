package io.b2mash.b2b.b2bstrawman.datarequest;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.member.MemberNameResolver;
import io.b2mash.b2b.b2bstrawman.notification.NotificationService;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettings;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DataSubjectRequestService {

  private static final Logger log = LoggerFactory.getLogger(DataSubjectRequestService.class);
  private static final int DEFAULT_DEADLINE_DAYS = 30;
  private static final Set<String> ALLOWED_REQUEST_TYPES =
      Set.of("ACCESS", "DELETION", "CORRECTION", "OBJECTION", "PORTABILITY");

  private final DataSubjectRequestRepository requestRepository;
  private final CustomerRepository customerRepository;
  private final OrgSettingsRepository orgSettingsRepository;
  private final AuditService auditService;
  private final MemberNameResolver memberNameResolver;
  private final NotificationService notificationService;

  public DataSubjectRequestService(
      DataSubjectRequestRepository requestRepository,
      CustomerRepository customerRepository,
      OrgSettingsRepository orgSettingsRepository,
      AuditService auditService,
      MemberNameResolver memberNameResolver,
      NotificationService notificationService) {
    this.requestRepository = requestRepository;
    this.customerRepository = customerRepository;
    this.orgSettingsRepository = orgSettingsRepository;
    this.auditService = auditService;
    this.memberNameResolver = memberNameResolver;
    this.notificationService = notificationService;
  }

  @Transactional
  public DataSubjectRequest createRequest(
      UUID customerId, String requestType, String description, UUID actorId) {
    if (!ALLOWED_REQUEST_TYPES.contains(requestType)) {
      throw new InvalidStateException(
          "Invalid request type",
          "requestType must be one of: " + String.join(", ", ALLOWED_REQUEST_TYPES));
    }
    customerRepository
        .findById(customerId)
        .orElseThrow(() -> new ResourceNotFoundException("Customer", customerId));

    var orgSettings = orgSettingsRepository.findForCurrentTenant().orElse(null);
    int deadlineDays =
        orgSettings != null ? resolveDeadlineDays(orgSettings) : DEFAULT_DEADLINE_DAYS;

    LocalDate deadline = LocalDate.now().plusDays(deadlineDays);

    var request = new DataSubjectRequest(customerId, requestType, description, actorId, deadline);
    if (orgSettings != null) {
      request.setJurisdiction(orgSettings.getDataProtectionJurisdiction());
      request.setDeadlineDaysOverride(orgSettings.getDataRequestDeadlineDays());
    }
    request = requestRepository.save(request);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("data.request.created")
            .entityType("data_subject_request")
            .entityId(request.getId())
            .details(Map.of("requestType", requestType, "customerId", customerId.toString()))
            .build());

    log.info(
        "Data subject request {} created for customer {} by {}",
        request.getId(),
        customerId,
        actorId);

    return request;
  }

  @Transactional
  public DataSubjectRequest startProcessing(UUID id, UUID actorId) {
    var request =
        requestRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("DataSubjectRequest", id));

    request.startProcessing(actorId);
    return requestRepository.save(request);
  }

  @Transactional
  public DataSubjectRequest completeRequest(UUID id, UUID actorId) {
    var request =
        requestRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("DataSubjectRequest", id));

    request.complete(actorId);
    request = requestRepository.save(request);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("data.request.completed")
            .entityType("data_subject_request")
            .entityId(request.getId())
            .build());

    return request;
  }

  @Transactional
  public DataSubjectRequest rejectRequest(UUID id, String reason, UUID actorId) {
    if (reason == null || reason.isBlank()) {
      throw new InvalidStateException(
          "Rejection reason required", "A non-blank reason must be provided when rejecting");
    }

    var request =
        requestRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("DataSubjectRequest", id));

    request.reject(reason, actorId);
    request = requestRepository.save(request);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("data.request.rejected")
            .entityType("data_subject_request")
            .entityId(request.getId())
            .details(Map.of("reason", reason))
            .build());

    return request;
  }

  @Transactional(readOnly = true)
  public List<DataSubjectRequest> listAll() {
    return requestRepository.findAll();
  }

  @Transactional(readOnly = true)
  public List<DataSubjectRequest> listByStatus(String status) {
    return requestRepository.findByStatus(status);
  }

  @Transactional(readOnly = true)
  public DataSubjectRequest getById(UUID id) {
    return requestRepository
        .findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("DataSubjectRequest", id));
  }

  /**
   * Checks for requests approaching or past their deadlines and logs audit events.
   *
   * <p>Finds requests with status RECEIVED or IN_PROGRESS whose deadline is within 7 days (or
   * overdue by up to 30 days). The 30-day lookback window prevents re-flagging very old requests
   * indefinitely on each call.
   *
   * <p>Note: This method is safe to call multiple times. Audit events are append-only logs, so
   * duplicate entries for the same request indicate repeated checks rather than data corruption.
   * Callers should schedule invocations appropriately (e.g., daily) to control event volume.
   *
   * @return the number of requests flagged
   */
  @Transactional
  public int checkDeadlines() {
    LocalDate now = LocalDate.now();
    LocalDate horizon = now.plusDays(7);
    LocalDate lookback = now.minusDays(30);

    var requests =
        requestRepository.findByStatusInAndDeadlineBetween(
            List.of("RECEIVED", "IN_PROGRESS"), lookback, horizon);

    int flagged = 0;
    for (var request : requests) {
      String eventType;
      if (request.getDeadline().isBefore(now)) {
        eventType = "data.request.overdue";
      } else {
        eventType = "data.request.deadline.approaching";
      }

      auditService.log(
          AuditEventBuilder.builder()
              .eventType(eventType)
              .entityType("data_subject_request")
              .entityId(request.getId())
              .details(
                  Map.of(
                      "deadline", request.getDeadline().toString(),
                      "status", request.getStatus(),
                      "customerId", request.getCustomerId().toString()))
              .build());
      flagged++;
    }

    log.info("Deadline check complete — {} requests flagged", flagged);
    return flagged;
  }

  /** Resolve customer names for a list of data subject requests. */
  private Map<UUID, String> resolveCustomerNames(List<DataSubjectRequest> requests) {
    var ids =
        requests.stream()
            .map(DataSubjectRequest::getCustomerId)
            .filter(Objects::nonNull)
            .distinct()
            .toList();
    if (ids.isEmpty()) return Map.of();
    return customerRepository.findAllById(ids).stream()
        .collect(
            Collectors.toMap(
                c -> c.getId(), c -> c.getName() != null ? c.getName() : "Unknown", (a, b) -> a));
  }

  /** Resolve member names for a list of data subject requests. */
  private Map<UUID, String> resolveMemberNames(List<DataSubjectRequest> requests) {
    var ids =
        requests.stream()
            .flatMap(r -> Stream.of(r.getRequestedBy(), r.getCompletedBy()))
            .filter(Objects::nonNull)
            .distinct()
            .toList();
    return memberNameResolver.resolveNames(ids);
  }

  private int resolveDeadlineDays(OrgSettings settings) {
    Integer tenantOverride = settings.getDataRequestDeadlineDays();
    String jurisdiction = settings.getDataProtectionJurisdiction();
    if (tenantOverride != null && tenantOverride > 0) {
      return Math.min(tenantOverride, JurisdictionDefaults.getMaxDeadlineDays(jurisdiction));
    }
    return JurisdictionDefaults.getDefaultDeadlineDays(jurisdiction);
  }

  @Transactional
  public int sendDeadlineWarnings() {
    LocalDate today = LocalDate.now();
    LocalDate sevenDaysOut = today.plusDays(7);

    var requests =
        requestRepository.findByStatusInAndDeadlineBetween(
            List.of("RECEIVED", "IN_PROGRESS"), today, sevenDaysOut);

    int notified = 0;
    LocalDate twoDaysOut = today.plusDays(2);
    for (var request : requests) {
      boolean isTwoDayWarning = !request.getDeadline().isAfter(twoDaysOut);
      String title =
          isTwoDayWarning
              ? "DSAR deadline in 2 days: request #" + request.getId().toString().substring(0, 8)
              : "DSAR deadline in 7 days: request #" + request.getId().toString().substring(0, 8);

      notificationService.notifyAdminsAndOwners(
          "DSAR_DEADLINE_WARNING",
          title,
          "A data subject request is approaching its deadline on " + request.getDeadline(),
          "data_subject_request",
          request.getId());
      notified++;
    }

    log.info("DSAR deadline warnings sent for {} requests", notified);
    return notified;
  }

  // --- Summary projection ---

  public enum DeadlineStatus {
    ON_TRACK,
    DUE_SOON,
    OVERDUE
  }

  public record DataSubjectRequestSummary(
      UUID id,
      UUID customerId,
      String customerName,
      String requestType,
      String status,
      String description,
      String rejectionReason,
      LocalDate deadline,
      String jurisdiction,
      Integer effectiveDeadlineDays,
      DeadlineStatus deadlineStatus,
      Instant requestedAt,
      UUID requestedBy,
      String requestedByName,
      Instant completedAt,
      UUID completedBy,
      String completedByName,
      boolean hasExport,
      String notes,
      Instant createdAt) {

    public static DataSubjectRequestSummary from(
        DataSubjectRequest req, String customerName, Map<UUID, String> memberNames) {
      LocalDate today = LocalDate.now();
      DeadlineStatus deadlineStatus;
      if (req.getDeadline() == null) {
        deadlineStatus = DeadlineStatus.ON_TRACK;
      } else if (req.getDeadline().isBefore(today)) {
        deadlineStatus = DeadlineStatus.OVERDUE;
      } else if (!req.getDeadline().isAfter(today.plusDays(7))) {
        deadlineStatus = DeadlineStatus.DUE_SOON;
      } else {
        deadlineStatus = DeadlineStatus.ON_TRACK;
      }

      // Compute effective days from the stored dates rather than the raw override,
      // which may exceed the jurisdiction cap.
      Integer effectiveDeadlineDays =
          (req.getDeadline() != null && req.getRequestedAt() != null)
              ? (int)
                  ChronoUnit.DAYS.between(
                      req.getRequestedAt().atZone(ZoneOffset.UTC).toLocalDate(), req.getDeadline())
              : req.getDeadlineDaysOverride();

      return new DataSubjectRequestSummary(
          req.getId(),
          req.getCustomerId(),
          customerName,
          req.getRequestType(),
          req.getStatus(),
          req.getDescription(),
          req.getRejectionReason(),
          req.getDeadline(),
          req.getJurisdiction(),
          effectiveDeadlineDays,
          deadlineStatus,
          req.getRequestedAt(),
          req.getRequestedBy(),
          req.getRequestedBy() != null ? memberNames.get(req.getRequestedBy()) : null,
          req.getCompletedAt(),
          req.getCompletedBy(),
          req.getCompletedBy() != null ? memberNames.get(req.getCompletedBy()) : null,
          req.getExportFileKey() != null,
          req.getNotes(),
          req.getCreatedAt());
    }
  }

  public DataSubjectRequestSummary toSummary(DataSubjectRequest request) {
    var memberNames = resolveMemberNames(List.of(request));
    var customerNames = resolveCustomerNames(List.of(request));
    return DataSubjectRequestSummary.from(
        request, customerNames.getOrDefault(request.getCustomerId(), "Unknown"), memberNames);
  }

  public List<DataSubjectRequestSummary> toSummaries(List<DataSubjectRequest> requests) {
    var memberNames = resolveMemberNames(requests);
    var customerNames = resolveCustomerNames(requests);
    return requests.stream()
        .map(
            req ->
                DataSubjectRequestSummary.from(
                    req, customerNames.getOrDefault(req.getCustomerId(), "Unknown"), memberNames))
        .toList();
  }
}
