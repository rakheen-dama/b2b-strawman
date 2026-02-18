package io.b2mash.b2b.b2bstrawman.datarequest;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
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

  public DataSubjectRequestService(
      DataSubjectRequestRepository requestRepository,
      CustomerRepository customerRepository,
      OrgSettingsRepository orgSettingsRepository,
      AuditService auditService) {
    this.requestRepository = requestRepository;
    this.customerRepository = customerRepository;
    this.orgSettingsRepository = orgSettingsRepository;
    this.auditService = auditService;
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

    int deadlineDays =
        orgSettingsRepository
            .findForCurrentTenant()
            .map(s -> s.getDataRequestDeadlineDays())
            .orElse(DEFAULT_DEADLINE_DAYS);
    if (deadlineDays <= 0) {
      deadlineDays = DEFAULT_DEADLINE_DAYS;
    }

    LocalDate deadline = LocalDate.now().plusDays(deadlineDays);

    var request = new DataSubjectRequest(customerId, requestType, description, actorId, deadline);
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
}
