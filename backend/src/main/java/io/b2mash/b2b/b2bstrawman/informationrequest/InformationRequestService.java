package io.b2mash.b2b.b2bstrawman.informationrequest;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.customer.Customer;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.document.DocumentRepository;
import io.b2mash.b2b.b2bstrawman.event.InformationRequestCancelledEvent;
import io.b2mash.b2b.b2bstrawman.event.InformationRequestCompletedEvent;
import io.b2mash.b2b.b2bstrawman.event.InformationRequestSentEvent;
import io.b2mash.b2b.b2bstrawman.event.RequestItemAcceptedEvent;
import io.b2mash.b2b.b2bstrawman.event.RequestItemRejectedEvent;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.informationrequest.dto.InformationRequestDtos.AdHocItemRequest;
import io.b2mash.b2b.b2bstrawman.informationrequest.dto.InformationRequestDtos.AddItemRequest;
import io.b2mash.b2b.b2bstrawman.informationrequest.dto.InformationRequestDtos.CreateInformationRequestRequest;
import io.b2mash.b2b.b2bstrawman.informationrequest.dto.InformationRequestDtos.DashboardSummaryResponse;
import io.b2mash.b2b.b2bstrawman.informationrequest.dto.InformationRequestDtos.FicaStatusResponse;
import io.b2mash.b2b.b2bstrawman.informationrequest.dto.InformationRequestDtos.InformationRequestResponse;
import io.b2mash.b2b.b2bstrawman.informationrequest.dto.InformationRequestDtos.UpdateInformationRequestRequest;
import io.b2mash.b2b.b2bstrawman.member.Member;
import io.b2mash.b2b.b2bstrawman.member.MemberRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.portal.PortalContact;
import io.b2mash.b2b.b2bstrawman.portal.PortalContactRepository;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
  private final DocumentRepository documentRepository;
  private final ApplicationEventPublisher eventPublisher;
  private final AuditService auditService;
  private final MemberRepository memberRepository;

  public InformationRequestService(
      InformationRequestRepository requestRepository,
      RequestItemRepository itemRepository,
      RequestNumberService requestNumberService,
      RequestTemplateRepository templateRepository,
      RequestTemplateItemRepository templateItemRepository,
      CustomerRepository customerRepository,
      ProjectRepository projectRepository,
      PortalContactRepository portalContactRepository,
      DocumentRepository documentRepository,
      ApplicationEventPublisher eventPublisher,
      AuditService auditService,
      MemberRepository memberRepository) {
    this.requestRepository = requestRepository;
    this.itemRepository = itemRepository;
    this.requestNumberService = requestNumberService;
    this.templateRepository = templateRepository;
    this.templateItemRepository = templateItemRepository;
    this.customerRepository = customerRepository;
    this.projectRepository = projectRepository;
    this.portalContactRepository = portalContactRepository;
    this.documentRepository = documentRepository;
    this.eventPublisher = eventPublisher;
    this.auditService = auditService;
    this.memberRepository = memberRepository;
  }

  private String resolveActorName() {
    UUID memberId = RequestScopes.requireMemberId();
    return memberRepository.findById(memberId).map(Member::getName).orElse("Unknown");
  }

  // ========== Create Operations ==========

  private static final int DEFAULT_SCHEDULER_REMINDER_INTERVAL_DAYS = 7;

  /**
   * Creates an information request from a template identified by slug (packId). Used by post-create
   * actions in recurring schedule execution. Finds the primary portal contact for the customer
   * automatically. Accepts an explicit memberId so the scheduler can call this without MEMBER_ID
   * being bound in RequestScopes.
   *
   * <p>Intentionally NOT annotated with @Transactional — this method participates in the caller's
   * existing transaction (executeSingleSchedule's REQUIRES_NEW). This avoids two problems: (1)
   * REQUIRES_NEW would create a separate transaction that can't see the uncommitted project (FK
   * violation), and (2) @Transactional(REQUIRED) would cause Spring's TX interceptor to mark the
   * outer transaction as rollback-only on exceptions, defeating the caller's try-catch.
   */
  public InformationRequestResponse createFromTemplateSlug(
      String templateSlug, UUID customerId, UUID projectId, UUID memberId) {
    var templates = templateRepository.findByPackId(templateSlug);
    // Sort by createdAt descending to ensure deterministic selection when multiple active templates
    // exist for the same packId
    var template =
        templates.stream()
            .filter(RequestTemplate::isActive)
            .sorted(Comparator.comparing(RequestTemplate::getCreatedAt).reversed())
            .findFirst()
            .orElseThrow(
                () -> new ResourceNotFoundException("RequestTemplate", "slug=" + templateSlug));

    var portalContact =
        portalContactRepository
            .findFirstByCustomerIdAndRoleAndStatusActive(
                customerId, PortalContact.ContactRole.PRIMARY)
            .or(() -> portalContactRepository.findByCustomerId(customerId).stream().findFirst())
            .orElseThrow(
                () -> new ResourceNotFoundException("PortalContact", "customerId=" + customerId));

    // dueDays controls how long the customer has to respond; reminderIntervalDays controls
    // how often reminders are sent. Use a sensible default for reminders.
    var request =
        new CreateInformationRequestRequest(
            template.getId(),
            customerId,
            projectId,
            portalContact.getId(),
            DEFAULT_SCHEDULER_REMINDER_INTERVAL_DAYS,
            null,
            null);
    return createFromTemplateWithMemberId(request, memberId);
  }

  @Transactional
  public InformationRequestResponse create(CreateInformationRequestRequest request) {
    if (request.requestTemplateId() != null) {
      return createFromTemplate(
          request.requestTemplateId(),
          request.customerId(),
          request.projectId(),
          request.portalContactId(),
          request.reminderIntervalDays(),
          request.dueDate());
    }
    return createAdHoc(
        request.customerId(),
        request.projectId(),
        request.portalContactId(),
        request.reminderIntervalDays(),
        request.dueDate(),
        request.items());
  }

  /**
   * Creates an information request from a template using an explicit memberId. Used by the
   * scheduler path where MEMBER_ID is not bound in RequestScopes.
   */
  private InformationRequestResponse createFromTemplateWithMemberId(
      CreateInformationRequestRequest request, UUID memberId) {
    return doCreateFromTemplate(
        request.requestTemplateId(),
        request.customerId(),
        request.projectId(),
        request.portalContactId(),
        request.reminderIntervalDays(),
        request.dueDate(),
        memberId,
        true);
  }

  private InformationRequestResponse createFromTemplate(
      UUID templateId,
      UUID customerId,
      UUID projectId,
      UUID portalContactId,
      Integer reminderIntervalDays,
      java.time.LocalDate dueDate) {
    return doCreateFromTemplate(
        templateId,
        customerId,
        projectId,
        portalContactId,
        reminderIntervalDays,
        dueDate,
        RequestScopes.requireMemberId(),
        false);
  }

  /**
   * Shared implementation for template-based information request creation. Both the interactive
   * (RequestScopes-based) and scheduler (explicit memberId) paths delegate here.
   */
  private InformationRequestResponse doCreateFromTemplate(
      UUID templateId,
      UUID customerId,
      UUID projectId,
      UUID portalContactId,
      Integer reminderIntervalDays,
      java.time.LocalDate dueDate,
      UUID memberId,
      boolean createdByScheduler) {
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

    String requestNumber = requestNumberService.allocateNumber();
    var infoRequest = new InformationRequest(requestNumber, customerId, portalContactId, memberId);
    infoRequest.setRequestTemplateId(templateId);
    infoRequest.setProjectId(projectId);
    infoRequest.setReminderIntervalDays(reminderIntervalDays);
    infoRequest.setDueDate(dueDate);
    var saved = requestRepository.save(infoRequest);

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

    var createAuditDetails = new HashMap<String, Object>();
    createAuditDetails.put("request_number", requestNumber);
    createAuditDetails.put("customer_id", customerId.toString());
    createAuditDetails.put("template_id", templateId.toString());
    if (createdByScheduler) {
      createAuditDetails.put("created_by_scheduler", "true");
    }
    if (projectId != null) {
      createAuditDetails.put("project_id", projectId.toString());
    }
    auditService.log(
        AuditEventBuilder.builder()
            .eventType("information_request.created")
            .entityType("information_request")
            .entityId(saved.getId())
            .details(createAuditDetails)
            .build());
    log.info(
        "Created information request {} ({}) from template {} for customer {}{}",
        saved.getId(),
        requestNumber,
        template.getName(),
        customerId,
        createdByScheduler ? " (scheduler)" : "");

    return toResponse(saved);
  }

  private InformationRequestResponse createAdHoc(
      UUID customerId,
      UUID projectId,
      UUID portalContactId,
      Integer reminderIntervalDays,
      java.time.LocalDate dueDate,
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
    request.setDueDate(dueDate);
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

    var adHocAuditDetails = new HashMap<String, Object>();
    adHocAuditDetails.put("request_number", requestNumber);
    adHocAuditDetails.put("customer_id", customerId.toString());
    adHocAuditDetails.put("ad_hoc", "true");
    if (projectId != null) {
      adHocAuditDetails.put("project_id", projectId.toString());
    }
    auditService.log(
        AuditEventBuilder.builder()
            .eventType("information_request.created")
            .entityType("information_request")
            .entityId(saved.getId())
            .details(adHocAuditDetails)
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

    var sentAuditDetails = new HashMap<String, Object>();
    sentAuditDetails.put("request_number", saved.getRequestNumber());
    if (saved.getProjectId() != null) {
      sentAuditDetails.put("project_id", saved.getProjectId().toString());
    }
    auditService.log(
        AuditEventBuilder.builder()
            .eventType("information_request.sent")
            .entityType("information_request")
            .entityId(saved.getId())
            .details(sentAuditDetails)
            .build());
    var actorName = resolveActorName();
    eventPublisher.publishEvent(
        new InformationRequestSentEvent(
            "information_request.sent",
            "information_request",
            requestId,
            saved.getProjectId(),
            RequestScopes.requireMemberId(),
            actorName,
            RequestScopes.TENANT_ID.get(),
            RequestScopes.ORG_ID.get(),
            Instant.now(),
            Map.of("request_number", saved.getRequestNumber()),
            requestId,
            saved.getCustomerId(),
            saved.getPortalContactId()));
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

    var cancelAuditDetails = new HashMap<String, Object>();
    cancelAuditDetails.put("request_number", saved.getRequestNumber());
    if (saved.getProjectId() != null) {
      cancelAuditDetails.put("project_id", saved.getProjectId().toString());
    }
    auditService.log(
        AuditEventBuilder.builder()
            .eventType("information_request.cancelled")
            .entityType("information_request")
            .entityId(saved.getId())
            .details(cancelAuditDetails)
            .build());
    var actorName = resolveActorName();
    eventPublisher.publishEvent(
        new InformationRequestCancelledEvent(
            "information_request.cancelled",
            "information_request",
            requestId,
            saved.getProjectId(),
            RequestScopes.requireMemberId(),
            actorName,
            RequestScopes.TENANT_ID.get(),
            RequestScopes.ORG_ID.get(),
            Instant.now(),
            Map.of("request_number", saved.getRequestNumber()),
            requestId,
            saved.getCustomerId()));
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

    var acceptAuditDetails = new HashMap<String, Object>();
    acceptAuditDetails.put("request_id", requestId.toString());
    acceptAuditDetails.put("request_number", request.getRequestNumber());
    acceptAuditDetails.put("item_name", item.getName());
    if (request.getProjectId() != null) {
      acceptAuditDetails.put("project_id", request.getProjectId().toString());
    }
    auditService.log(
        AuditEventBuilder.builder()
            .eventType("information_request.item_accepted")
            .entityType("request_item")
            .entityId(itemId)
            .details(acceptAuditDetails)
            .build());
    var acceptActorName = resolveActorName();
    eventPublisher.publishEvent(
        new RequestItemAcceptedEvent(
            "information_request.item_accepted",
            "request_item",
            itemId,
            request.getProjectId(),
            RequestScopes.requireMemberId(),
            acceptActorName,
            RequestScopes.TENANT_ID.get(),
            RequestScopes.ORG_ID.get(),
            Instant.now(),
            Map.of("request_id", requestId.toString(), "item_name", item.getName()),
            requestId,
            itemId,
            request.getCustomerId()));

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

    var rejectAuditDetails = new HashMap<String, Object>();
    rejectAuditDetails.put("request_id", requestId.toString());
    rejectAuditDetails.put("request_number", request.getRequestNumber());
    rejectAuditDetails.put("item_name", item.getName());
    rejectAuditDetails.put("reason", reason);
    if (request.getProjectId() != null) {
      rejectAuditDetails.put("project_id", request.getProjectId().toString());
    }
    auditService.log(
        AuditEventBuilder.builder()
            .eventType("information_request.item_rejected")
            .entityType("request_item")
            .entityId(itemId)
            .details(rejectAuditDetails)
            .build());
    var rejectActorName = resolveActorName();
    eventPublisher.publishEvent(
        new RequestItemRejectedEvent(
            "information_request.item_rejected",
            "request_item",
            itemId,
            request.getProjectId(),
            RequestScopes.requireMemberId(),
            rejectActorName,
            RequestScopes.TENANT_ID.get(),
            RequestScopes.ORG_ID.get(),
            Instant.now(),
            Map.of(
                "request_id", requestId.toString(),
                "item_name", item.getName(),
                "reason", reason),
            requestId,
            itemId,
            request.getCustomerId(),
            reason));

    return toResponse(request);
  }

  // checkAutoComplete is intentionally only called from acceptItem, not rejectItem.
  // Rejecting an item cannot trigger request completion — only accepting all required items can.
  private void checkAutoComplete(InformationRequest request) {
    var items = itemRepository.findByRequestId(request.getId());
    boolean allRequiredAccepted =
        items.stream()
            .filter(RequestItem::isRequired)
            .allMatch(i -> i.getStatus() == ItemStatus.ACCEPTED);

    if (allRequiredAccepted && items.stream().anyMatch(RequestItem::isRequired)) {
      request.complete();
      requestRepository.save(request);

      var completedAuditDetails = new HashMap<String, Object>();
      completedAuditDetails.put("request_number", request.getRequestNumber());
      completedAuditDetails.put("auto_completed", "true");
      if (request.getProjectId() != null) {
        completedAuditDetails.put("project_id", request.getProjectId().toString());
      }
      auditService.log(
          AuditEventBuilder.builder()
              .eventType("information_request.completed")
              .entityType("information_request")
              .entityId(request.getId())
              .details(completedAuditDetails)
              .build());
      var completeActorName = resolveActorName();
      eventPublisher.publishEvent(
          new InformationRequestCompletedEvent(
              "information_request.completed",
              "information_request",
              request.getId(),
              request.getProjectId(),
              RequestScopes.requireMemberId(),
              completeActorName,
              RequestScopes.TENANT_ID.get(),
              RequestScopes.ORG_ID.get(),
              Instant.now(),
              Map.of("request_number", request.getRequestNumber(), "auto_completed", "true"),
              request.getId(),
              request.getCustomerId(),
              request.getPortalContactId()));
      log.info(
          "Auto-completed information request {} ({})",
          request.getId(),
          request.getRequestNumber());
    }
  }

  // ========== Update Operations ==========

  @Transactional
  public InformationRequestResponse updateRequest(
      UUID requestId, UpdateInformationRequestRequest dto) {
    var request =
        requestRepository
            .findById(requestId)
            .orElseThrow(() -> new ResourceNotFoundException("InformationRequest", requestId));
    request.requireEditable();

    if (dto.projectId() != null) {
      projectRepository
          .findById(dto.projectId())
          .orElseThrow(() -> new ResourceNotFoundException("Project", dto.projectId()));
    }
    request.setProjectId(dto.projectId());
    request.setReminderIntervalDays(dto.reminderIntervalDays());
    request.setDueDate(dto.dueDate());
    var saved = requestRepository.save(request);

    var updatedAuditDetails = new HashMap<String, Object>();
    updatedAuditDetails.put("request_number", saved.getRequestNumber());
    if (saved.getProjectId() != null) {
      updatedAuditDetails.put("project_id", saved.getProjectId().toString());
    }
    auditService.log(
        AuditEventBuilder.builder()
            .eventType("information_request.updated")
            .entityType("information_request")
            .entityId(saved.getId())
            .details(updatedAuditDetails)
            .build());

    return toResponse(saved);
  }

  @Transactional
  public InformationRequestResponse addItem(UUID requestId, AddItemRequest dto) {
    var request =
        requestRepository
            .findById(requestId)
            .orElseThrow(() -> new ResourceNotFoundException("InformationRequest", requestId));
    request.requireEditable();

    var item =
        new RequestItem(
            requestId,
            dto.name(),
            dto.description(),
            dto.responseType(),
            dto.required(),
            dto.fileTypeHints(),
            dto.sortOrder());
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
    var resendActorName = resolveActorName();
    eventPublisher.publishEvent(
        new InformationRequestSentEvent(
            "information_request.sent",
            "information_request",
            requestId,
            request.getProjectId(),
            RequestScopes.requireMemberId(),
            resendActorName,
            RequestScopes.TENANT_ID.get(),
            RequestScopes.ORG_ID.get(),
            Instant.now(),
            Map.of("request_number", request.getRequestNumber(), "resend", "true"),
            requestId,
            request.getCustomerId(),
            request.getPortalContactId()));
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
    if (customerId != null && projectId != null && status != null) {
      requests =
          requestRepository.findByCustomerIdAndProjectIdAndStatus(customerId, projectId, status);
    } else if (customerId != null && status != null) {
      requests = requestRepository.findByCustomerIdAndStatus(customerId, status);
    } else if (projectId != null && status != null) {
      requests = requestRepository.findByProjectIdAndStatus(projectId, status);
    } else if (customerId != null && projectId != null) {
      requests = requestRepository.findByCustomerIdAndProjectId(customerId, projectId);
    } else if (customerId != null) {
      requests = requestRepository.findByCustomerId(customerId);
    } else if (projectId != null) {
      requests = requestRepository.findByProjectId(projectId);
    } else if (status != null) {
      requests = requestRepository.findByStatus(status);
    } else {
      requests = requestRepository.findAll();
    }

    return toResponseList(requests);
  }

  @Transactional(readOnly = true)
  public List<InformationRequestResponse> listByCustomer(UUID customerId) {
    return toResponseList(requestRepository.findByCustomerId(customerId));
  }

  @Transactional(readOnly = true)
  public List<InformationRequestResponse> listByProject(UUID projectId) {
    return toResponseList(requestRepository.findByProjectId(projectId));
  }

  // ========== FICA Status Projection (GAP-L-46) ==========

  /** Pack identifier for the FICA onboarding request template (legal-ZA). */
  public static final String FICA_ONBOARDING_PACK_ID = "fica-onboarding-pack";

  /**
   * Projects the FICA onboarding status for a customer from the customer's information requests
   * backed by the {@link #FICA_ONBOARDING_PACK_ID} template. Info-request-only signal —
   * intentionally does NOT couple to any KYC-adapter output or beneficial-owner coverage. Designed
   * so those two concerns can be folded into the same DTO later without a schema change (tracked
   * under a separate phase).
   */
  @Transactional(readOnly = true)
  public FicaStatusResponse getFicaStatus(UUID customerId) {
    // All active FICA templates (org may have duplicated the pack; we
    // treat every request tied to any of them as "a FICA request").
    var ficaTemplateIds =
        templateRepository.findByPackId(FICA_ONBOARDING_PACK_ID).stream()
            .map(RequestTemplate::getId)
            .toList();
    if (ficaTemplateIds.isEmpty()) {
      return new FicaStatusResponse(customerId, "NOT_STARTED", null, null);
    }

    var customerRequests =
        requestRepository.findByCustomerId(customerId).stream()
            .filter(r -> r.getRequestTemplateId() != null)
            .filter(r -> ficaTemplateIds.contains(r.getRequestTemplateId()))
            // Ignore cancelled requests — they were never a live FICA pack.
            .filter(r -> r.getStatus() != RequestStatus.CANCELLED)
            .sorted(Comparator.comparing(InformationRequest::getCreatedAt).reversed())
            .toList();

    if (customerRequests.isEmpty()) {
      return new FicaStatusResponse(customerId, "NOT_STARTED", null, null);
    }

    // Prefer the most-recent completed request; otherwise the most-recent
    // live one. This lets a firm issue a second FICA pack without losing
    // the earlier verification record while the new one is still open.
    var completed =
        customerRequests.stream().filter(r -> r.getStatus() == RequestStatus.COMPLETED).findFirst();
    if (completed.isPresent()) {
      var r = completed.get();
      var items = itemRepository.findByRequestId(r.getId());
      boolean allAccepted =
          !items.isEmpty() && items.stream().allMatch(i -> i.getStatus() == ItemStatus.ACCEPTED);
      if (allAccepted) {
        return new FicaStatusResponse(customerId, "DONE", r.getCompletedAt(), r.getId());
      }
      // COMPLETED in lifecycle but not every item ACCEPTED — treat as
      // IN_PROGRESS so the UI prompts review rather than a green check.
      return new FicaStatusResponse(customerId, "IN_PROGRESS", null, r.getId());
    }

    // No completed FICA request — pick the most-recent non-terminal one.
    var live = customerRequests.get(0);
    return new FicaStatusResponse(customerId, "IN_PROGRESS", null, live.getId());
  }

  // ========== Dashboard Summary ==========

  @Transactional(readOnly = true)
  public DashboardSummaryResponse getDashboardSummary() {
    long total = requestRepository.count();

    Map<String, Long> byStatus = new LinkedHashMap<>();
    for (RequestStatus s : RequestStatus.values()) {
      byStatus.put(s.name(), requestRepository.countByStatus(s));
    }

    // Items pending review — single query across all active request items
    var activeRequests =
        requestRepository.findByStatusIn(List.of(RequestStatus.SENT, RequestStatus.IN_PROGRESS));
    List<UUID> activeRequestIds = activeRequests.stream().map(InformationRequest::getId).toList();
    long itemsPendingReview =
        activeRequestIds.isEmpty()
            ? 0
            : itemRepository.countByRequestIdInAndStatus(activeRequestIds, ItemStatus.SUBMITTED);

    // Overdue requests — iterate only active requests (not all)
    long overdueRequests = 0;
    for (var req : activeRequests) {
      if (isOverdue(req)) {
        overdueRequests++;
      }
    }

    // Completion rate last 30 days — use count queries instead of findAll()
    Instant thirtyDaysAgo = Instant.now().minus(30, ChronoUnit.DAYS);
    long completedLast30 =
        requestRepository.countByStatusAndCompletedAtAfter(RequestStatus.COMPLETED, thirtyDaysAgo);
    long sentLast30 = requestRepository.countBySentAtAfter(thirtyDaysAgo);
    long totalRelevantLast30 = completedLast30 + sentLast30;
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

  /** Maps a single request to its response — used for single-entity operations. */
  private InformationRequestResponse toResponse(InformationRequest request) {
    var items = itemRepository.findByRequestIdOrderBySortOrder(request.getId());

    String customerName =
        customerRepository.findById(request.getCustomerId()).map(Customer::getName).orElse(null);

    String projectName = null;
    if (request.getProjectId() != null) {
      projectName =
          projectRepository.findById(request.getProjectId()).map(Project::getName).orElse(null);
    }

    String portalContactName = null;
    String portalContactEmail = null;
    var contact = portalContactRepository.findById(request.getPortalContactId());
    if (contact.isPresent()) {
      portalContactName = contact.get().getDisplayName();
      portalContactEmail = contact.get().getEmail();
    }

    Map<UUID, String> documentFileNames = resolveDocumentFileNames(items);

    return InformationRequestResponse.from(
        request,
        items,
        customerName,
        projectName,
        portalContactName,
        portalContactEmail,
        documentFileNames);
  }

  /**
   * Resolves display file names for any request items that have an attached document. Mirrors the
   * pattern used in {@code AcceptanceService} (line 822) so the firm-side request-detail UI can
   * surface the per-item Download button (which is gated on both {@code documentId} and {@code
   * documentFileName}).
   */
  private Map<UUID, String> resolveDocumentFileNames(List<RequestItem> items) {
    var documentIds =
        items.stream().map(RequestItem::getDocumentId).filter(Objects::nonNull).distinct().toList();
    if (documentIds.isEmpty()) {
      return Map.of();
    }
    Map<UUID, String> names = new HashMap<>();
    documentRepository.findAllById(documentIds).forEach(d -> names.put(d.getId(), d.getFileName()));
    return names;
  }

  /**
   * Maps a list of requests to responses with batch-fetched related entities. Pre-fetches
   * customers, projects, and portal contacts into maps to avoid N+1 queries.
   */
  private List<InformationRequestResponse> toResponseList(List<InformationRequest> requests) {
    if (requests.isEmpty()) {
      return List.of();
    }

    // Batch-fetch customers
    var customerIds = requests.stream().map(InformationRequest::getCustomerId).distinct().toList();
    Map<UUID, String> customerNames = new HashMap<>();
    customerRepository
        .findAllById(customerIds)
        .forEach(c -> customerNames.put(c.getId(), c.getName()));

    // Batch-fetch projects
    var projectIds =
        requests.stream()
            .map(InformationRequest::getProjectId)
            .filter(Objects::nonNull)
            .distinct()
            .toList();
    Map<UUID, String> projectNames = new HashMap<>();
    if (!projectIds.isEmpty()) {
      projectRepository
          .findAllById(projectIds)
          .forEach(p -> projectNames.put(p.getId(), p.getName()));
    }

    // Batch-fetch portal contacts
    var contactIds =
        requests.stream().map(InformationRequest::getPortalContactId).distinct().toList();
    Map<UUID, PortalContact> contactMap = new HashMap<>();
    portalContactRepository.findAllById(contactIds).forEach(c -> contactMap.put(c.getId(), c));

    return requests.stream()
        .map(
            request -> {
              var items = itemRepository.findByRequestIdOrderBySortOrder(request.getId());
              String customerName = customerNames.get(request.getCustomerId());
              String projectName =
                  request.getProjectId() != null ? projectNames.get(request.getProjectId()) : null;
              var contact = contactMap.get(request.getPortalContactId());
              String portalContactName = contact != null ? contact.getDisplayName() : null;
              String portalContactEmail = contact != null ? contact.getEmail() : null;
              Map<UUID, String> documentFileNames = resolveDocumentFileNames(items);
              return InformationRequestResponse.from(
                  request,
                  items,
                  customerName,
                  projectName,
                  portalContactName,
                  portalContactEmail,
                  documentFileNames);
            })
        .toList();
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
