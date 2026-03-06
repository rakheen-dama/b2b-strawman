package io.b2mash.b2b.b2bstrawman.informationrequest;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.informationrequest.InformationRequestEvents.InformationRequestCancelledEvent;
import io.b2mash.b2b.b2bstrawman.informationrequest.InformationRequestEvents.InformationRequestCompletedEvent;
import io.b2mash.b2b.b2bstrawman.informationrequest.InformationRequestEvents.InformationRequestSentEvent;
import io.b2mash.b2b.b2bstrawman.informationrequest.InformationRequestEvents.RequestItemAcceptedEvent;
import io.b2mash.b2b.b2bstrawman.informationrequest.InformationRequestEvents.RequestItemRejectedEvent;
import io.b2mash.b2b.b2bstrawman.informationrequest.dto.InformationRequestDtos.AdHocItemRequest;
import io.b2mash.b2b.b2bstrawman.informationrequest.dto.InformationRequestDtos.CreateInformationRequestRequest;
import io.b2mash.b2b.b2bstrawman.informationrequest.dto.InformationRequestDtos.DashboardSummaryResponse;
import io.b2mash.b2b.b2bstrawman.informationrequest.dto.InformationRequestDtos.InformationRequestResponse;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.portal.PortalContact;
import io.b2mash.b2b.b2bstrawman.portal.PortalContactRepository;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InformationRequestService {

  private static final Logger log = LoggerFactory.getLogger(InformationRequestService.class);
  private static final int DEFAULT_REMINDER_INTERVAL_DAYS = 5;

  private final InformationRequestRepository requestRepository;
  private final RequestItemRepository itemRepository;
  private final RequestNumberService requestNumberService;
  private final RequestTemplateRepository templateRepository;
  private final RequestTemplateItemRepository templateItemRepository;
  private final CustomerRepository customerRepository;
  private final ProjectRepository projectRepository;
  private final PortalContactRepository portalContactRepository;
  private final ApplicationEventPublisher eventPublisher;
  private final AuditService auditService;

  public InformationRequestService(
      InformationRequestRepository requestRepository,
      RequestItemRepository itemRepository,
      RequestNumberService requestNumberService,
      RequestTemplateRepository templateRepository,
      RequestTemplateItemRepository templateItemRepository,
      CustomerRepository customerRepository,
      ProjectRepository projectRepository,
      PortalContactRepository portalContactRepository,
      ApplicationEventPublisher eventPublisher,
      AuditService auditService) {
    this.requestRepository = requestRepository;
    this.itemRepository = itemRepository;
    this.requestNumberService = requestNumberService;
    this.templateRepository = templateRepository;
    this.templateItemRepository = templateItemRepository;
    this.customerRepository = customerRepository;
    this.projectRepository = projectRepository;
    this.portalContactRepository = portalContactRepository;
    this.eventPublisher = eventPublisher;
    this.auditService = auditService;
  }

  // ========== Create Operations ==========

  @Transactional
  public InformationRequestResponse create(CreateInformationRequestRequest request) {
    if (request.requestTemplateId() != null) {
      return createFromTemplate(
          request.requestTemplateId(),
          request.customerId(),
          request.projectId(),
          request.portalContactId(),
          request.reminderIntervalDays());
    }
    return createAdHoc(
        request.customerId(),
        request.projectId(),
        request.portalContactId(),
        request.reminderIntervalDays(),
        request.items());
  }

  private InformationRequestResponse createFromTemplate(
      UUID templateId,
      UUID customerId,
      UUID projectId,
      UUID portalContactId,
      Integer reminderIntervalDays) {
    customerRepository
        .findById(customerId)
        .orElseThrow(() -> new ResourceNotFoundException("Customer", customerId));
    validatePortalContact(portalContactId, customerId);
    if (projectId != null) {
      projectRepository
          .findById(projectId)
          .orElseThrow(() -> new ResourceNotFoundException("Project", projectId));
    }
    var template =
        templateRepository
            .findById(templateId)
            .orElseThrow(() -> new ResourceNotFoundException("RequestTemplate", templateId));

    UUID memberId = RequestScopes.requireMemberId();
    String requestNumber = requestNumberService.allocateNumber();
    var request = new InformationRequest(requestNumber, customerId, portalContactId, memberId);
    request.setRequestTemplateId(templateId);
    request.setProjectId(projectId);
    request.setReminderIntervalDays(reminderIntervalDays);
    var saved = requestRepository.save(request);

    // Copy template items
    var templateItems = templateItemRepository.findByTemplateIdOrderBySortOrder(templateId);
    for (var ti : templateItems) {
      var item =
          new RequestItem(
              saved.getId(),
              ti.getName(),
              ti.getDescription(),
              ti.getResponseType(),
              ti.isRequired(),
              ti.getFileTypeHints(),
              ti.getSortOrder());
      item.setTemplateItemId(ti.getId());
      itemRepository.save(item);
    }

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("information_request.created")
            .entityType("information_request")
            .entityId(saved.getId())
            .details(
                Map.of(
                    "request_number",
                    requestNumber,
                    "customer_id",
                    customerId.toString(),
                    "template_id",
                    templateId.toString()))
            .build());
    log.info(
        "Created information request {} ({}) from template {} for customer {}",
        saved.getId(),
        requestNumber,
        template.getName(),
        customerId);

    return toResponse(saved);
  }

  private InformationRequestResponse createAdHoc(
      UUID customerId,
      UUID projectId,
      UUID portalContactId,
      Integer reminderIntervalDays,
      List<AdHocItemRequest> items) {
    customerRepository
        .findById(customerId)
        .orElseThrow(() -> new ResourceNotFoundException("Customer", customerId));
    validatePortalContact(portalContactId, customerId);
    if (projectId != null) {
      projectRepository
          .findById(projectId)
          .orElseThrow(() -> new ResourceNotFoundException("Project", projectId));
    }

    UUID memberId = RequestScopes.requireMemberId();
    String requestNumber = requestNumberService.allocateNumber();
    var request = new InformationRequest(requestNumber, customerId, portalContactId, memberId);
    request.setProjectId(projectId);
    request.setReminderIntervalDays(reminderIntervalDays);
    var saved = requestRepository.save(request);

    if (items != null) {
      for (var item : items) {
        itemRepository.save(
            new RequestItem(
                saved.getId(),
                item.name(),
                item.description(),
                item.responseType(),
                item.required(),
                item.fileTypeHints(),
                item.sortOrder()));
      }
    }

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("information_request.created")
            .entityType("information_request")
            .entityId(saved.getId())
            .details(
                Map.of(
                    "request_number",
                    requestNumber,
                    "customer_id",
                    customerId.toString(),
                    "ad_hoc",
                    "true"))
            .build());
    log.info(
        "Created ad-hoc information request {} ({}) for customer {}",
        saved.getId(),
        requestNumber,
        customerId);

    return toResponse(saved);
  }

  // ========== Lifecycle Operations ==========

  @Transactional
  public InformationRequestResponse send(UUID requestId) {
    var request =
        requestRepository
            .findById(requestId)
            .orElseThrow(() -> new ResourceNotFoundException("InformationRequest", requestId));
    var items = itemRepository.findByRequestId(requestId);
    if (items.isEmpty()) {
      throw new InvalidStateException(
          "Cannot send empty request",
          "Information request must have at least one item before sending");
    }
    request.send();
    var saved = requestRepository.save(request);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("information_request.sent")
            .entityType("information_request")
            .entityId(saved.getId())
            .details(Map.of("request_number", saved.getRequestNumber()))
            .build());
    eventPublisher.publishEvent(new InformationRequestSentEvent(requestId));
    log.info("Sent information request {} ({})", saved.getId(), saved.getRequestNumber());

    return toResponse(saved);
  }

  @Transactional
  public InformationRequestResponse cancel(UUID requestId) {
    var request =
        requestRepository
            .findById(requestId)
            .orElseThrow(() -> new ResourceNotFoundException("InformationRequest", requestId));
    request.cancel();
    var saved = requestRepository.save(request);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("information_request.cancelled")
            .entityType("information_request")
            .entityId(saved.getId())
            .details(Map.of("request_number", saved.getRequestNumber()))
            .build());
    eventPublisher.publishEvent(new InformationRequestCancelledEvent(requestId));
    log.info("Cancelled information request {} ({})", saved.getId(), saved.getRequestNumber());

    return toResponse(saved);
  }

  // ========== Item Review Operations ==========

  @Transactional
  public InformationRequestResponse acceptItem(UUID requestId, UUID itemId) {
    var request =
        requestRepository
            .findById(requestId)
            .orElseThrow(() -> new ResourceNotFoundException("InformationRequest", requestId));
    var item =
        itemRepository
            .findById(itemId)
            .orElseThrow(() -> new ResourceNotFoundException("RequestItem", itemId));
    if (!item.getRequestId().equals(requestId)) {
      throw new ResourceNotFoundException("RequestItem", itemId);
    }

    UUID memberId = RequestScopes.requireMemberId();
    item.accept(memberId);
    itemRepository.save(item);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("information_request.item_accepted")
            .entityType("request_item")
            .entityId(itemId)
            .details(
                Map.of(
                    "request_id", requestId.toString(),
                    "item_name", item.getName()))
            .build());
    eventPublisher.publishEvent(new RequestItemAcceptedEvent(requestId, itemId));

    checkAutoComplete(request);
    return toResponse(request);
  }

  @Transactional
  public InformationRequestResponse rejectItem(UUID requestId, UUID itemId, String reason) {
    var request =
        requestRepository
            .findById(requestId)
            .orElseThrow(() -> new ResourceNotFoundException("InformationRequest", requestId));
    var item =
        itemRepository
            .findById(itemId)
            .orElseThrow(() -> new ResourceNotFoundException("RequestItem", itemId));
    if (!item.getRequestId().equals(requestId)) {
      throw new ResourceNotFoundException("RequestItem", itemId);
    }

    UUID memberId = RequestScopes.requireMemberId();
    item.reject(reason, memberId);
    itemRepository.save(item);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("information_request.item_rejected")
            .entityType("request_item")
            .entityId(itemId)
            .details(
                Map.of(
                    "request_id", requestId.toString(),
                    "item_name", item.getName(),
                    "reason", reason))
            .build());
    eventPublisher.publishEvent(new RequestItemRejectedEvent(requestId, itemId));

    return toResponse(request);
  }

  private void checkAutoComplete(InformationRequest request) {
    var items = itemRepository.findByRequestId(request.getId());
    boolean allRequiredAccepted =
        items.stream()
            .filter(RequestItem::isRequired)
            .allMatch(i -> i.getStatus() == ItemStatus.ACCEPTED);

    if (allRequiredAccepted && items.stream().anyMatch(RequestItem::isRequired)) {
      request.complete();
      requestRepository.save(request);

      auditService.log(
          AuditEventBuilder.builder()
              .eventType("information_request.completed")
              .entityType("information_request")
              .entityId(request.getId())
              .details(
                  Map.of("request_number", request.getRequestNumber(), "auto_completed", "true"))
              .build());
      eventPublisher.publishEvent(new InformationRequestCompletedEvent(request.getId()));
      log.info(
          "Auto-completed information request {} ({})",
          request.getId(),
          request.getRequestNumber());
    }
  }

  // ========== Update Operations ==========

  @Transactional
  public InformationRequestResponse updateRequest(
      UUID requestId, Integer reminderIntervalDays, UUID projectId) {
    var request =
        requestRepository
            .findById(requestId)
            .orElseThrow(() -> new ResourceNotFoundException("InformationRequest", requestId));
    request.requireEditable();

    if (projectId != null) {
      projectRepository
          .findById(projectId)
          .orElseThrow(() -> new ResourceNotFoundException("Project", projectId));
    }
    request.setProjectId(projectId);
    request.setReminderIntervalDays(reminderIntervalDays);
    var saved = requestRepository.save(request);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("information_request.updated")
            .entityType("information_request")
            .entityId(saved.getId())
            .details(Map.of("request_number", saved.getRequestNumber()))
            .build());

    return toResponse(saved);
  }

  @Transactional
  public InformationRequestResponse addItem(
      UUID requestId,
      String name,
      String description,
      ResponseType responseType,
      boolean required,
      String fileTypeHints,
      int sortOrder) {
    var request =
        requestRepository
            .findById(requestId)
            .orElseThrow(() -> new ResourceNotFoundException("InformationRequest", requestId));
    request.requireEditable();

    var item =
        new RequestItem(
            requestId, name, description, responseType, required, fileTypeHints, sortOrder);
    itemRepository.save(item);

    return toResponse(request);
  }

  @Transactional
  public InformationRequestResponse resendNotification(UUID requestId) {
    var request =
        requestRepository
            .findById(requestId)
            .orElseThrow(() -> new ResourceNotFoundException("InformationRequest", requestId));
    if (request.getStatus() != RequestStatus.SENT
        && request.getStatus() != RequestStatus.IN_PROGRESS) {
      throw new InvalidStateException(
          "Cannot resend notification",
          "Request must be in SENT or IN_PROGRESS status to resend notification");
    }
    eventPublisher.publishEvent(new InformationRequestSentEvent(requestId));
    log.info(
        "Resent notification for information request {} ({})",
        request.getId(),
        request.getRequestNumber());

    return toResponse(request);
  }

  // ========== Query Operations ==========

  @Transactional(readOnly = true)
  public InformationRequestResponse getById(UUID requestId) {
    var request =
        requestRepository
            .findById(requestId)
            .orElseThrow(() -> new ResourceNotFoundException("InformationRequest", requestId));
    return toResponse(request);
  }

  @Transactional(readOnly = true)
  public List<InformationRequestResponse> list(
      UUID customerId, UUID projectId, RequestStatus status) {
    List<InformationRequest> requests;
    if (customerId != null) {
      requests = requestRepository.findByCustomerId(customerId);
    } else if (projectId != null) {
      requests = requestRepository.findByProjectId(projectId);
    } else if (status != null) {
      requests = requestRepository.findByStatusIn(List.of(status));
    } else {
      requests = requestRepository.findAll();
    }

    if (status != null && customerId != null) {
      requests = requests.stream().filter(r -> r.getStatus() == status).toList();
    }
    if (status != null && projectId != null) {
      requests = requests.stream().filter(r -> r.getStatus() == status).toList();
    }

    return requests.stream().map(this::toResponse).toList();
  }

  @Transactional(readOnly = true)
  public List<InformationRequestResponse> listByCustomer(UUID customerId) {
    return requestRepository.findByCustomerId(customerId).stream().map(this::toResponse).toList();
  }

  @Transactional(readOnly = true)
  public List<InformationRequestResponse> listByProject(UUID projectId) {
    return requestRepository.findByProjectId(projectId).stream().map(this::toResponse).toList();
  }

  // ========== Dashboard Summary ==========

  @Transactional(readOnly = true)
  public DashboardSummaryResponse getDashboardSummary() {
    long total = requestRepository.count();

    Map<String, Long> byStatus = new LinkedHashMap<>();
    for (RequestStatus s : RequestStatus.values()) {
      byStatus.put(s.name(), requestRepository.countByStatus(s));
    }

    // Items pending review (SUBMITTED status across all items)
    long itemsPendingReview = 0;
    var activeRequests =
        requestRepository.findByStatusIn(List.of(RequestStatus.SENT, RequestStatus.IN_PROGRESS));
    for (var req : activeRequests) {
      itemsPendingReview +=
          itemRepository.countByRequestIdAndStatus(req.getId(), ItemStatus.SUBMITTED);
    }

    // Overdue requests
    long overdueRequests = 0;
    for (var req : activeRequests) {
      if (isOverdue(req)) {
        overdueRequests++;
      }
    }

    // Completion rate last 30 days
    Instant thirtyDaysAgo = Instant.now().minus(30, ChronoUnit.DAYS);
    var allRequests = requestRepository.findAll();
    long completedLast30 =
        allRequests.stream()
            .filter(r -> r.getStatus() == RequestStatus.COMPLETED)
            .filter(r -> r.getCompletedAt() != null && r.getCompletedAt().isAfter(thirtyDaysAgo))
            .count();
    long totalRelevantLast30 =
        allRequests.stream()
            .filter(
                r ->
                    (r.getStatus() == RequestStatus.COMPLETED
                            && r.getCompletedAt() != null
                            && r.getCompletedAt().isAfter(thirtyDaysAgo))
                        || (r.getSentAt() != null && r.getSentAt().isAfter(thirtyDaysAgo)))
            .count();
    double completionRate =
        totalRelevantLast30 > 0 ? (double) completedLast30 / totalRelevantLast30 : 0.0;

    return new DashboardSummaryResponse(
        total, byStatus, itemsPendingReview, overdueRequests, completionRate);
  }

  private boolean isOverdue(InformationRequest request) {
    Integer interval = request.getReminderIntervalDays();
    if (interval != null && interval == 0) {
      return false;
    }
    int effectiveInterval =
        (interval != null && interval > 0) ? interval : DEFAULT_REMINDER_INTERVAL_DAYS;
    int overdueDays = effectiveInterval * 2;

    // Find last activity: max submittedAt of items, or sentAt
    var items = itemRepository.findByRequestId(request.getId());
    Instant lastActivity = request.getSentAt();
    for (var item : items) {
      if (item.getSubmittedAt() != null
          && (lastActivity == null || item.getSubmittedAt().isAfter(lastActivity))) {
        lastActivity = item.getSubmittedAt();
      }
    }

    if (lastActivity == null) {
      return false;
    }

    return lastActivity.plus(overdueDays, ChronoUnit.DAYS).isBefore(Instant.now());
  }

  // ========== Response Mapping ==========

  private InformationRequestResponse toResponse(InformationRequest request) {
    var items = itemRepository.findByRequestIdOrderBySortOrder(request.getId());

    String customerName =
        customerRepository.findById(request.getCustomerId()).map(c -> c.getName()).orElse(null);

    String projectName = null;
    if (request.getProjectId() != null) {
      projectName =
          projectRepository.findById(request.getProjectId()).map(p -> p.getName()).orElse(null);
    }

    String portalContactName = null;
    String portalContactEmail = null;
    var contact = portalContactRepository.findById(request.getPortalContactId());
    if (contact.isPresent()) {
      portalContactName = contact.get().getDisplayName();
      portalContactEmail = contact.get().getEmail();
    }

    return InformationRequestResponse.from(
        request, items, customerName, projectName, portalContactName, portalContactEmail);
  }

  private void validatePortalContact(UUID portalContactId, UUID customerId) {
    PortalContact contact =
        portalContactRepository
            .findById(portalContactId)
            .orElseThrow(() -> new ResourceNotFoundException("PortalContact", portalContactId));
    if (!contact.getCustomerId().equals(customerId)) {
      throw new InvalidStateException(
          "Portal contact mismatch", "Portal contact does not belong to the specified customer");
    }
  }
}
