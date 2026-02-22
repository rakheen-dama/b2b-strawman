package io.b2mash.b2b.b2bstrawman.datarequest;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.comment.CommentRepository;
import io.b2mash.b2b.b2bstrawman.compliance.CustomerLifecycleService;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.document.DocumentRepository;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceConflictException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.integration.storage.StorageService;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceRepository;
import io.b2mash.b2b.b2bstrawman.portal.PortalContactRepository;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DataAnonymizationService {

  private static final Logger log = LoggerFactory.getLogger(DataAnonymizationService.class);

  private final DataSubjectRequestRepository requestRepository;
  private final CustomerRepository customerRepository;
  private final DocumentRepository documentRepository;
  private final CommentRepository commentRepository;
  private final PortalContactRepository portalContactRepository;
  private final InvoiceRepository invoiceRepository;
  private final CustomerLifecycleService customerLifecycleService;
  private final StorageService storageService;
  private final AuditService auditService;

  public DataAnonymizationService(
      DataSubjectRequestRepository requestRepository,
      CustomerRepository customerRepository,
      DocumentRepository documentRepository,
      CommentRepository commentRepository,
      PortalContactRepository portalContactRepository,
      InvoiceRepository invoiceRepository,
      CustomerLifecycleService customerLifecycleService,
      StorageService storageService,
      AuditService auditService) {
    this.requestRepository = requestRepository;
    this.customerRepository = customerRepository;
    this.documentRepository = documentRepository;
    this.commentRepository = commentRepository;
    this.portalContactRepository = portalContactRepository;
    this.invoiceRepository = invoiceRepository;
    this.customerLifecycleService = customerLifecycleService;
    this.storageService = storageService;
    this.auditService = auditService;
  }

  /**
   * Executes data anonymization for a DELETION data subject request.
   *
   * @param requestId the data subject request ID
   * @param confirmCustomerName the customer name for confirmation (must match exactly)
   * @param actorId the member performing the deletion
   * @return anonymization result with counts
   */
  @Transactional
  public AnonymizationResult executeAnonymization(
      UUID requestId, String confirmCustomerName, UUID actorId) {

    var request =
        requestRepository
            .findById(requestId)
            .orElseThrow(() -> new ResourceNotFoundException("DataSubjectRequest", requestId));

    // Precondition: must be DELETION type and IN_PROGRESS status
    if (!"DELETION".equals(request.getRequestType())) {
      throw new ResourceConflictException(
          "Invalid request type",
          "Only DELETION requests can be executed — this request is type "
              + request.getRequestType());
    }
    if (!"IN_PROGRESS".equals(request.getStatus())) {
      throw new ResourceConflictException(
          "Invalid request status",
          "Request must be IN_PROGRESS to execute deletion — current status is "
              + request.getStatus());
    }

    UUID customerId = request.getCustomerId();
    var customer =
        customerRepository
            .findById(customerId)
            .orElseThrow(() -> new ResourceNotFoundException("Customer", customerId));

    // Verification: customer name must match exactly
    if (!customer.getName().equals(confirmCustomerName)) {
      throw new InvalidStateException(
          "Customer name mismatch",
          "Provided name does not match the customer record. Please confirm the exact customer name.");
    }

    // Step 1: Anonymize customer PII
    String hash = customerId.toString().substring(0, 6);
    customer.anonymize("Anonymized Customer " + hash);
    customerRepository.save(customer);

    // Step 2: Redact portal-visible comments (BEFORE deleting documents, since the query joins
    // through the document table)
    var portalComments = commentRepository.findPortalVisibleByCustomerId(customerId);
    int commentsRedacted = portalComments.size();
    for (var comment : portalComments) {
      comment.redact("[Removed]");
      commentRepository.save(comment);
    }

    // Step 3: Delete customer-scoped documents from storage (best-effort) and DB
    var documents = documentRepository.findByCustomerId(customerId);
    int documentsDeleted = documents.size();
    for (var doc : documents) {
      if (doc.getS3Key() != null && !"pending".equals(doc.getS3Key())) {
        storageService.delete(doc.getS3Key());
      }
      documentRepository.delete(doc);
    }

    // Step 4: Anonymize portal contacts
    var contacts = portalContactRepository.findByCustomerId(customerId);
    int contactsAnonymized = contacts.size();
    for (var contact : contacts) {
      contact.anonymize("Removed Contact");
      portalContactRepository.save(contact);
    }

    // Step 5: Transition lifecycle to OFFBOARDED (requires two steps: ACTIVE -> OFFBOARDING ->
    // OFFBOARDED)
    customerLifecycleService.transition(
        customerId, "OFFBOARDING", "Data deletion in progress", actorId);
    customerLifecycleService.transition(
        customerId, "OFFBOARDED", "Data deletion executed", actorId);

    // Step 6: Complete the request
    request.complete(actorId);
    requestRepository.save(request);

    // Audit event
    auditService.log(
        AuditEventBuilder.builder()
            .eventType("data.deletion.executed")
            .entityType("data_subject_request")
            .entityId(requestId)
            .details(
                Map.of(
                    "customerId", customerId.toString(),
                    "documentsDeleted", documentsDeleted,
                    "commentsRedacted", commentsRedacted,
                    "portalContactsAnonymized", contactsAnonymized))
            .build());

    // Count preserved financial records
    int invoicesPreserved = invoiceRepository.findByCustomerId(customerId).size();

    log.info(
        "Data deletion executed for request {} — customer={}, docs={}, comments={}, contacts={},"
            + " invoicesPreserved={}",
        requestId,
        customerId,
        documentsDeleted,
        commentsRedacted,
        contactsAnonymized,
        invoicesPreserved);

    return new AnonymizationResult(
        true, documentsDeleted, commentsRedacted, contactsAnonymized, invoicesPreserved);
  }

  /** Result of anonymization execution. */
  public record AnonymizationResult(
      boolean customerAnonymized,
      int documentsDeleted,
      int commentsRedacted,
      int portalContactsAnonymized,
      int invoicesPreserved) {}
}
