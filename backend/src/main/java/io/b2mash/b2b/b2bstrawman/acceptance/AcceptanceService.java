package io.b2mash.b2b.b2bstrawman.acceptance;

import io.b2mash.b2b.b2bstrawman.customer.CustomerProjectRepository;
import io.b2mash.b2b.b2bstrawman.event.AcceptanceRequestAcceptedEvent;
import io.b2mash.b2b.b2bstrawman.event.AcceptanceRequestExpiredEvent;
import io.b2mash.b2b.b2bstrawman.event.AcceptanceRequestRevokedEvent;
import io.b2mash.b2b.b2bstrawman.event.AcceptanceRequestSentEvent;
import io.b2mash.b2b.b2bstrawman.event.AcceptanceRequestViewedEvent;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.integration.IntegrationDomain;
import io.b2mash.b2b.b2bstrawman.integration.IntegrationRegistry;
import io.b2mash.b2b.b2bstrawman.integration.email.EmailDeliveryLogService;
import io.b2mash.b2b.b2bstrawman.integration.email.EmailMessage;
import io.b2mash.b2b.b2bstrawman.integration.email.EmailProvider;
import io.b2mash.b2b.b2bstrawman.integration.email.EmailRateLimiter;
import io.b2mash.b2b.b2bstrawman.integration.storage.StorageService;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceRepository;
import io.b2mash.b2b.b2bstrawman.member.MemberNameResolver;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.notification.template.EmailContextBuilder;
import io.b2mash.b2b.b2bstrawman.notification.template.EmailTemplateRenderer;
import io.b2mash.b2b.b2bstrawman.portal.PortalContact;
import io.b2mash.b2b.b2bstrawman.portal.PortalContactRepository;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.template.GeneratedDocument;
import io.b2mash.b2b.b2bstrawman.template.GeneratedDocumentRepository;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Core service for the document acceptance workflow. */
@Service
public class AcceptanceService {

  private static final Logger log = LoggerFactory.getLogger(AcceptanceService.class);
  private static final int TOKEN_BYTES = 32;
  private static final int DEFAULT_EXPIRY_DAYS = 30;
  private static final String REFERENCE_TYPE = "ACCEPTANCE_REQUEST";
  private static final String TEMPLATE_REQUEST = "acceptance-request";
  private static final String TEMPLATE_REMINDER = "acceptance-reminder";
  private static final String TEMPLATE_CONFIRMATION = "acceptance-confirmation";

  private static final DateTimeFormatter EMAIL_DATE_FORMAT =
      DateTimeFormatter.ofPattern("d MMMM yyyy").withZone(ZoneOffset.UTC);

  private final AcceptanceRequestRepository acceptanceRequestRepository;
  private final GeneratedDocumentRepository generatedDocumentRepository;
  private final PortalContactRepository portalContactRepository;
  private final OrgSettingsRepository orgSettingsRepository;
  private final CustomerProjectRepository customerProjectRepository;
  private final InvoiceRepository invoiceRepository;
  private final EmailTemplateRenderer emailTemplateRenderer;
  private final EmailContextBuilder emailContextBuilder;
  private final IntegrationRegistry integrationRegistry;
  private final EmailDeliveryLogService deliveryLogService;
  private final EmailRateLimiter emailRateLimiter;
  private final ApplicationEventPublisher eventPublisher;
  private final MemberNameResolver memberNameResolver;
  private final AcceptanceCertificateService certificateService;

  @SuppressWarnings("unused")
  private final StorageService storageService;

  private final String portalBaseUrl;
  private final SecureRandom secureRandom;

  public AcceptanceService(
      AcceptanceRequestRepository acceptanceRequestRepository,
      GeneratedDocumentRepository generatedDocumentRepository,
      PortalContactRepository portalContactRepository,
      OrgSettingsRepository orgSettingsRepository,
      CustomerProjectRepository customerProjectRepository,
      InvoiceRepository invoiceRepository,
      EmailTemplateRenderer emailTemplateRenderer,
      EmailContextBuilder emailContextBuilder,
      IntegrationRegistry integrationRegistry,
      EmailDeliveryLogService deliveryLogService,
      EmailRateLimiter emailRateLimiter,
      ApplicationEventPublisher eventPublisher,
      MemberNameResolver memberNameResolver,
      AcceptanceCertificateService certificateService,
      StorageService storageService,
      @Value("${docteams.portal.base-url:http://localhost:3001}") String portalBaseUrl) {
    this.acceptanceRequestRepository = acceptanceRequestRepository;
    this.generatedDocumentRepository = generatedDocumentRepository;
    this.portalContactRepository = portalContactRepository;
    this.orgSettingsRepository = orgSettingsRepository;
    this.customerProjectRepository = customerProjectRepository;
    this.invoiceRepository = invoiceRepository;
    this.emailTemplateRenderer = emailTemplateRenderer;
    this.emailContextBuilder = emailContextBuilder;
    this.integrationRegistry = integrationRegistry;
    this.deliveryLogService = deliveryLogService;
    this.emailRateLimiter = emailRateLimiter;
    this.eventPublisher = eventPublisher;
    this.memberNameResolver = memberNameResolver;
    this.certificateService = certificateService;
    this.storageService = storageService;
    this.portalBaseUrl = portalBaseUrl;
    this.secureRandom = new SecureRandom();
  }

  /**
   * Creates an acceptance request for a generated document and sends the request email.
   *
   * @param generatedDocumentId the document to request acceptance for
   * @param portalContactId the portal contact to send the request to
   * @param expiryDays optional override for expiry days (null uses org settings or default)
   * @return the created acceptance request in SENT status
   */
  @Transactional
  public AcceptanceRequest createAndSend(
      UUID generatedDocumentId, UUID portalContactId, Integer expiryDays) {
    // 1. Validate document exists
    GeneratedDocument doc =
        generatedDocumentRepository
            .findById(generatedDocumentId)
            .orElseThrow(
                () -> new ResourceNotFoundException("GeneratedDocument", generatedDocumentId));

    // 2. Resolve customerId
    UUID customerId = resolveCustomerId(doc);

    // 3. Validate portal contact exists and belongs to same customer
    PortalContact contact =
        portalContactRepository
            .findById(portalContactId)
            .orElseThrow(() -> new ResourceNotFoundException("PortalContact", portalContactId));

    if (!contact.getCustomerId().equals(customerId)) {
      throw new InvalidStateException(
          "Portal contact does not belong to customer",
          "The selected contact is not associated with the document's customer");
    }

    // 4. Auto-revoke existing active request for same doc-contact pair
    acceptanceRequestRepository
        .findByGeneratedDocumentIdAndPortalContactIdAndStatusIn(
            generatedDocumentId,
            portalContactId,
            List.of(AcceptanceStatus.PENDING, AcceptanceStatus.SENT, AcceptanceStatus.VIEWED))
        .ifPresent(
            existing -> {
              UUID revokedBy = RequestScopes.requireMemberId();
              existing.markRevoked(revokedBy);
              acceptanceRequestRepository.save(existing);
              // Flush to ensure the revoked status is persisted before inserting the new request,
              // which is required by the partial unique index on active requests
              acceptanceRequestRepository.flush();

              // Publish revoked event for the auto-revoked request
              String revokerName = memberNameResolver.resolveName(revokedBy);
              eventPublisher.publishEvent(
                  new AcceptanceRequestRevokedEvent(
                      "acceptance_request.revoked",
                      "acceptance_request",
                      existing.getId(),
                      null,
                      revokedBy,
                      revokerName,
                      RequestScopes.getTenantIdOrNull(),
                      RequestScopes.getOrgIdOrNull(),
                      Instant.now(),
                      Map.of(),
                      existing.getId()));

              log.info(
                  "Auto-revoked existing acceptance request {} for doc={} contact={}",
                  existing.getId(),
                  generatedDocumentId,
                  portalContactId);
            });

    // 5. Generate token
    String token = generateToken();

    // 6. Calculate expiresAt
    int effectiveExpiryDays = resolveExpiryDays(expiryDays);
    Instant expiresAt = Instant.now().plus(effectiveExpiryDays, ChronoUnit.DAYS);

    // 7-8. Create and save
    var request =
        new AcceptanceRequest(
            generatedDocumentId,
            portalContactId,
            customerId,
            token,
            expiresAt,
            RequestScopes.requireMemberId());
    request = acceptanceRequestRepository.save(request);

    // 9. Send email
    sendAcceptanceEmail(request, contact, doc, TEMPLATE_REQUEST);

    // 10-11. Transition to SENT and save
    request.markSent();
    request = acceptanceRequestRepository.save(request);

    // Publish domain event
    UUID memberId = RequestScopes.requireMemberId();
    String actorName = memberNameResolver.resolveName(memberId);
    eventPublisher.publishEvent(
        new AcceptanceRequestSentEvent(
            "acceptance_request.sent",
            "acceptance_request",
            request.getId(),
            null,
            memberId,
            actorName,
            RequestScopes.getTenantIdOrNull(),
            RequestScopes.getOrgIdOrNull(),
            Instant.now(),
            Map.of(
                "document_file_name", doc.getFileName(),
                "contact_name", contact.getDisplayName(),
                "contact_email", contact.getEmail() != null ? contact.getEmail() : ""),
            request.getId(),
            generatedDocumentId,
            portalContactId,
            customerId,
            doc.getFileName(),
            request.getExpiresAt(),
            contact.getDisplayName(),
            contact.getEmail()));

    log.info(
        "Created and sent acceptance request {} for doc={} contact={}",
        request.getId(),
        generatedDocumentId,
        portalContactId);
    return request;
  }

  /**
   * Marks an acceptance request as viewed. Idempotent for VIEWED/ACCEPTED states.
   *
   * @param requestToken the acceptance request token
   * @param ipAddress the viewer's IP address
   * @return the updated acceptance request
   */
  @Transactional
  public AcceptanceRequest markViewed(String requestToken, String ipAddress) {
    AcceptanceRequest request = findByTokenOrThrow(requestToken);

    // Check expiry inline
    if (request.isActive() && request.isExpired()) {
      request.markExpired();
      acceptanceRequestRepository.save(request);
      publishExpiredEvent(request);
      throw new InvalidStateException(
          "Acceptance request has expired",
          "This acceptance request expired on " + EMAIL_DATE_FORMAT.format(request.getExpiresAt()));
    }

    return switch (request.getStatus()) {
      case SENT -> {
        request.markViewed(Instant.now());
        request = acceptanceRequestRepository.save(request);
        eventPublisher.publishEvent(
            new AcceptanceRequestViewedEvent(
                "acceptance_request.viewed",
                "acceptance_request",
                request.getId(),
                null,
                null,
                "Portal Contact",
                RequestScopes.getTenantIdOrNull(),
                RequestScopes.getOrgIdOrNull(),
                Instant.now(),
                Map.of("ip_address", ipAddress != null ? ipAddress : ""),
                request.getId(),
                ipAddress));
        yield request;
      }
      case VIEWED, ACCEPTED -> request; // idempotent — no event
      case EXPIRED, REVOKED ->
          throw new InvalidStateException(
              "Acceptance request is no longer active",
              "Cannot view acceptance request in status " + request.getStatus());
      case PENDING ->
          throw new InvalidStateException(
              "Acceptance request not yet sent",
              "Cannot view acceptance request in status PENDING");
    };
  }

  /**
   * Accepts a document, recording the acceptor's details.
   *
   * @param requestToken the acceptance request token
   * @param submission the acceptor's submission data
   * @param ipAddress the acceptor's IP address
   * @param userAgent the acceptor's user agent
   * @return the updated acceptance request in ACCEPTED status
   */
  @Transactional
  public AcceptanceRequest accept(
      String requestToken, AcceptanceSubmission submission, String ipAddress, String userAgent) {
    AcceptanceRequest request = findByTokenOrThrow(requestToken);

    // Check expiry inline
    if (request.isActive() && request.isExpired()) {
      request.markExpired();
      acceptanceRequestRepository.save(request);
      publishExpiredEvent(request);
      throw new InvalidStateException(
          "Acceptance request has expired",
          "This acceptance request expired on " + EMAIL_DATE_FORMAT.format(request.getExpiresAt()));
    }

    // Must be SENT or VIEWED
    request.markAccepted(submission.name(), ipAddress, userAgent);
    request = acceptanceRequestRepository.save(request);

    // Generate Certificate of Acceptance (synchronous, non-fatal on failure)
    String tenantSchema = RequestScopes.getTenantIdOrNull();
    if (tenantSchema != null) {
      try {
        certificateService.generateCertificate(request, tenantSchema);
        request = acceptanceRequestRepository.save(request);
      } catch (Exception e) {
        log.warn(
            "Certificate generation failed for acceptance request {} (document {}), continuing without certificate",
            request.getId(),
            request.getGeneratedDocumentId(),
            e);
      }
    } else {
      log.warn(
          "Skipping certificate generation for request={} — no tenant context", request.getId());
    }

    // Send confirmation email
    GeneratedDocument doc =
        generatedDocumentRepository.findById(request.getGeneratedDocumentId()).orElse(null);
    PortalContact contact =
        portalContactRepository.findById(request.getPortalContactId()).orElse(null);
    if (doc != null && contact != null) {
      sendConfirmationEmail(request, contact, doc);
    } else {
      log.warn(
          "Skipping confirmation email for request={}: doc={}, contact={} (null indicates data integrity issue)",
          request.getId(),
          doc != null ? doc.getId() : "null",
          contact != null ? contact.getId() : "null");
    }

    // Publish domain event
    String docFileName = doc != null ? doc.getFileName() : "";
    eventPublisher.publishEvent(
        new AcceptanceRequestAcceptedEvent(
            "acceptance_request.accepted",
            "acceptance_request",
            request.getId(),
            null,
            null,
            submission.name(),
            RequestScopes.getTenantIdOrNull(),
            RequestScopes.getOrgIdOrNull(),
            Instant.now(),
            Map.of("acceptor_name", submission.name(), "document_file_name", docFileName),
            request.getId(),
            request.getSentByMemberId(),
            docFileName,
            submission.name()));

    log.info("Acceptance request {} accepted by '{}'", request.getId(), submission.name());
    return request;
  }

  /**
   * Revokes an active acceptance request.
   *
   * @param requestId the acceptance request ID
   * @return the revoked acceptance request
   */
  @Transactional
  public AcceptanceRequest revoke(UUID requestId) {
    AcceptanceRequest request =
        acceptanceRequestRepository
            .findById(requestId)
            .orElseThrow(() -> new ResourceNotFoundException("AcceptanceRequest", requestId));

    UUID memberId = RequestScopes.requireMemberId();
    request.markRevoked(memberId);
    request = acceptanceRequestRepository.save(request);

    // Publish domain event
    String actorName = memberNameResolver.resolveName(memberId);
    eventPublisher.publishEvent(
        new AcceptanceRequestRevokedEvent(
            "acceptance_request.revoked",
            "acceptance_request",
            request.getId(),
            null,
            memberId,
            actorName,
            RequestScopes.getTenantIdOrNull(),
            RequestScopes.getOrgIdOrNull(),
            Instant.now(),
            Map.of(),
            request.getId()));

    log.info("Acceptance request {} revoked", requestId);
    return request;
  }

  /**
   * Re-sends the acceptance email as a reminder.
   *
   * @param requestId the acceptance request ID
   * @return the updated acceptance request with incremented reminder count
   */
  @Transactional
  public AcceptanceRequest remind(UUID requestId) {
    AcceptanceRequest request =
        acceptanceRequestRepository
            .findById(requestId)
            .orElseThrow(() -> new ResourceNotFoundException("AcceptanceRequest", requestId));

    // Validate status
    if (!request.isActive()) {
      throw new InvalidStateException(
          "Acceptance request is not active",
          "Cannot send reminder for acceptance request in status " + request.getStatus());
    }

    // Check expiry inline
    if (request.isExpired()) {
      request.markExpired();
      acceptanceRequestRepository.save(request);
      publishExpiredEvent(request);
      throw new InvalidStateException(
          "Acceptance request has expired",
          "This acceptance request expired on " + EMAIL_DATE_FORMAT.format(request.getExpiresAt()));
    }

    // Re-send email with same token
    GeneratedDocument doc =
        generatedDocumentRepository.findById(request.getGeneratedDocumentId()).orElse(null);
    PortalContact contact =
        portalContactRepository.findById(request.getPortalContactId()).orElse(null);
    if (doc != null && contact != null) {
      sendAcceptanceEmail(request, contact, doc, TEMPLATE_REMINDER);
    } else {
      log.warn(
          "Skipping reminder email for request={}: doc={}, contact={} (null indicates data integrity issue)",
          request.getId(),
          doc != null ? doc.getId() : "null",
          contact != null ? contact.getId() : "null");
    }

    request.recordReminder();
    request = acceptanceRequestRepository.save(request);

    log.info(
        "Reminder sent for acceptance request {} (count={})",
        requestId,
        request.getReminderCount());
    return request;
  }

  /** Looks up an acceptance request by its token. */
  @Transactional(readOnly = true)
  public AcceptanceRequest getByToken(String token) {
    return acceptanceRequestRepository
        .findByRequestToken(token)
        .orElseThrow(() -> new ResourceNotFoundException("AcceptanceRequest", "token"));
  }

  /** Lists all acceptance requests for a generated document. */
  @Transactional(readOnly = true)
  public List<AcceptanceRequest> getByDocument(UUID generatedDocumentId) {
    return acceptanceRequestRepository.findByGeneratedDocumentIdOrderByCreatedAtDesc(
        generatedDocumentId);
  }

  /** Lists all acceptance requests for a customer. */
  @Transactional(readOnly = true)
  public List<AcceptanceRequest> getByCustomer(UUID customerId) {
    return acceptanceRequestRepository.findByCustomerIdAndStatusInOrderByCreatedAtDesc(
        customerId,
        List.of(
            AcceptanceStatus.PENDING,
            AcceptanceStatus.SENT,
            AcceptanceStatus.VIEWED,
            AcceptanceStatus.ACCEPTED,
            AcceptanceStatus.EXPIRED,
            AcceptanceStatus.REVOKED));
  }

  // --- Private helpers ---

  private void publishExpiredEvent(AcceptanceRequest request) {
    eventPublisher.publishEvent(
        new AcceptanceRequestExpiredEvent(
            "acceptance_request.expired",
            "acceptance_request",
            request.getId(),
            null,
            null,
            "System",
            RequestScopes.getTenantIdOrNull(),
            RequestScopes.getOrgIdOrNull(),
            Instant.now(),
            Map.of(),
            request.getId()));
  }

  private String generateToken() {
    byte[] tokenBytes = new byte[TOKEN_BYTES];
    secureRandom.nextBytes(tokenBytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
  }

  private UUID resolveCustomerId(GeneratedDocument doc) {
    return switch (doc.getPrimaryEntityType()) {
      case CUSTOMER -> doc.getPrimaryEntityId();
      case PROJECT ->
          customerProjectRepository
              .findFirstCustomerByProjectId(doc.getPrimaryEntityId())
              .orElseThrow(
                  () ->
                      new InvalidStateException(
                          "No customer linked to project",
                          "Cannot send acceptance: project has no linked customer"));
      case INVOICE ->
          invoiceRepository
              .findById(doc.getPrimaryEntityId())
              .map(invoice -> invoice.getCustomerId())
              .orElseThrow(
                  () -> new ResourceNotFoundException("Invoice", doc.getPrimaryEntityId()));
    };
  }

  private int resolveExpiryDays(Integer expiryDaysOverride) {
    if (expiryDaysOverride != null && expiryDaysOverride > 0) {
      return expiryDaysOverride;
    }
    return orgSettingsRepository
        .findForCurrentTenant()
        .map(settings -> settings.getEffectiveAcceptanceExpiryDays())
        .orElse(DEFAULT_EXPIRY_DAYS);
  }

  private AcceptanceRequest findByTokenOrThrow(String requestToken) {
    return acceptanceRequestRepository
        .findByRequestToken(requestToken)
        .orElseThrow(() -> new ResourceNotFoundException("AcceptanceRequest", "token"));
  }

  private void sendAcceptanceEmail(
      AcceptanceRequest request,
      PortalContact contact,
      GeneratedDocument doc,
      String templateName) {
    String recipientEmail = contact.getEmail();
    if (recipientEmail == null || recipientEmail.isBlank()) {
      log.warn("Skipping acceptance email for contact {} -- no email address", contact.getId());
      return;
    }
    if (!RequestScopes.TENANT_ID.isBound()) {
      log.warn("Skipping acceptance email for contact {} -- no tenant context", contact.getId());
      return;
    }
    try {
      // 1. Resolve provider
      EmailProvider provider =
          integrationRegistry.resolve(IntegrationDomain.EMAIL, EmailProvider.class);

      // 2. Build context
      String acceptanceUrl = portalBaseUrl + "/accept/" + request.getRequestToken();
      Map<String, Object> context =
          emailContextBuilder.buildBaseContext(contact.getDisplayName(), null);
      context.put("contactName", contact.getDisplayName());
      context.put("documentFileName", doc.getFileName());
      context.put("acceptanceUrl", acceptanceUrl);
      context.put("expiresAtFormatted", EMAIL_DATE_FORMAT.format(request.getExpiresAt()));

      String orgName = (String) context.get("orgName");

      if (TEMPLATE_REMINDER.equals(templateName) && request.getSentAt() != null) {
        context.put("sentAtFormatted", EMAIL_DATE_FORMAT.format(request.getSentAt()));
        context.put("subject", "Reminder: " + orgName + " -- Document awaiting your acceptance");
      } else {
        context.put("subject", orgName + " -- Document for your acceptance: " + doc.getFileName());
      }

      // 3. Render template
      var rendered = emailTemplateRenderer.render(templateName, context);

      // 4. Rate limit check
      String tenantSchema = RequestScopes.TENANT_ID.get();
      if (!emailRateLimiter.tryAcquire(tenantSchema, provider.providerId())) {
        log.warn("Rate limit exceeded for acceptance email, request={}", request.getId());
        deliveryLogService.recordRateLimited(
            REFERENCE_TYPE, request.getId(), templateName, recipientEmail, provider.providerId());
        return;
      }

      // 5. Construct message
      var message =
          EmailMessage.withTracking(
              recipientEmail,
              rendered.subject(),
              rendered.htmlBody(),
              rendered.plainTextBody(),
              null,
              REFERENCE_TYPE,
              request.getId().toString(),
              tenantSchema);

      // 6. Send
      var result = provider.sendEmail(message);

      // 7. Record delivery
      deliveryLogService.record(
          REFERENCE_TYPE,
          request.getId(),
          templateName,
          recipientEmail,
          provider.providerId(),
          result);

      if (result.success()) {
        log.info(
            "Acceptance email ({}) sent for request={} to={}",
            templateName,
            request.getId(),
            recipientEmail);
      } else {
        log.warn(
            "Acceptance email ({}) failed for request={}: {}",
            templateName,
            request.getId(),
            result.errorMessage());
      }
    } catch (Exception e) {
      log.error(
          "Unexpected error sending acceptance email ({}) for request={}",
          templateName,
          request.getId(),
          e);
    }
  }

  private void sendConfirmationEmail(
      AcceptanceRequest request, PortalContact contact, GeneratedDocument doc) {
    String recipientEmail = contact.getEmail();
    if (recipientEmail == null || recipientEmail.isBlank()) {
      log.warn("Skipping confirmation email for contact {} -- no email address", contact.getId());
      return;
    }
    if (!RequestScopes.TENANT_ID.isBound()) {
      log.warn("Skipping confirmation email for contact {} -- no tenant context", contact.getId());
      return;
    }
    try {
      EmailProvider provider =
          integrationRegistry.resolve(IntegrationDomain.EMAIL, EmailProvider.class);

      Map<String, Object> context =
          emailContextBuilder.buildBaseContext(contact.getDisplayName(), null);
      context.put("contactName", contact.getDisplayName());
      context.put("documentFileName", doc.getFileName());
      context.put("acceptedAtFormatted", EMAIL_DATE_FORMAT.format(request.getAcceptedAt()));
      context.put("subject", "Confirmed: You have accepted " + doc.getFileName());

      var rendered = emailTemplateRenderer.render(TEMPLATE_CONFIRMATION, context);

      String tenantSchema = RequestScopes.TENANT_ID.get();
      if (!emailRateLimiter.tryAcquire(tenantSchema, provider.providerId())) {
        log.warn("Rate limit exceeded for confirmation email, request={}", request.getId());
        deliveryLogService.recordRateLimited(
            REFERENCE_TYPE,
            request.getId(),
            TEMPLATE_CONFIRMATION,
            recipientEmail,
            provider.providerId());
        return;
      }

      var message =
          EmailMessage.withTracking(
              recipientEmail,
              rendered.subject(),
              rendered.htmlBody(),
              rendered.plainTextBody(),
              null,
              REFERENCE_TYPE,
              request.getId().toString(),
              tenantSchema);

      var result = provider.sendEmail(message);

      deliveryLogService.record(
          REFERENCE_TYPE,
          request.getId(),
          TEMPLATE_CONFIRMATION,
          recipientEmail,
          provider.providerId(),
          result);

      if (result.success()) {
        log.info(
            "Acceptance confirmation email sent for request={} to={}",
            request.getId(),
            recipientEmail);
      } else {
        log.warn(
            "Acceptance confirmation email failed for request={}: {}",
            request.getId(),
            result.errorMessage());
      }
    } catch (Exception e) {
      log.error("Unexpected error sending confirmation email for request={}", request.getId(), e);
    }
  }
}
