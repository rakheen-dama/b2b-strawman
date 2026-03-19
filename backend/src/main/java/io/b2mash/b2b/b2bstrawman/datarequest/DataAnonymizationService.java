package io.b2mash.b2b.b2bstrawman.datarequest;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.comment.CommentRepository;
import io.b2mash.b2b.b2bstrawman.compliance.CustomerLifecycleService;
import io.b2mash.b2b.b2bstrawman.customer.CustomerProject;
import io.b2mash.b2b.b2bstrawman.customer.CustomerProjectRepository;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.customer.LifecycleStatus;
import io.b2mash.b2b.b2bstrawman.document.DocumentRepository;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceConflictException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.integration.storage.StorageService;
import io.b2mash.b2b.b2bstrawman.invoice.Invoice;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceRepository;
import io.b2mash.b2b.b2bstrawman.portal.PortalContactRepository;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.timeentry.TimeEntryRepository;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
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
  private final CustomerProjectRepository customerProjectRepository;
  private final TimeEntryRepository timeEntryRepository;
  private final OrgSettingsRepository orgSettingsRepository;
  private final DataExportService dataExportService;

  public DataAnonymizationService(
      DataSubjectRequestRepository requestRepository,
      CustomerRepository customerRepository,
      DocumentRepository documentRepository,
      CommentRepository commentRepository,
      PortalContactRepository portalContactRepository,
      InvoiceRepository invoiceRepository,
      CustomerLifecycleService customerLifecycleService,
      StorageService storageService,
      AuditService auditService,
      CustomerProjectRepository customerProjectRepository,
      TimeEntryRepository timeEntryRepository,
      OrgSettingsRepository orgSettingsRepository,
      DataExportService dataExportService) {
    this.requestRepository = requestRepository;
    this.customerRepository = customerRepository;
    this.documentRepository = documentRepository;
    this.commentRepository = commentRepository;
    this.portalContactRepository = portalContactRepository;
    this.invoiceRepository = invoiceRepository;
    this.customerLifecycleService = customerLifecycleService;
    this.storageService = storageService;
    this.auditService = auditService;
    this.customerProjectRepository = customerProjectRepository;
    this.timeEntryRepository = timeEntryRepository;
    this.orgSettingsRepository = orgSettingsRepository;
    this.dataExportService = dataExportService;
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

  /**
   * Standalone customer anonymization — does NOT require a pre-existing DSAR. Generates a
   * pre-anonymization export, anonymizes all PII, sets ANONYMIZED status, and preserves financial
   * records.
   *
   * @param customerId the customer to anonymize
   * @param confirmationName the customer name for confirmation (must match case-insensitively)
   * @param reason optional reason for anonymization
   * @param actorId the member performing the anonymization
   * @return anonymization result with counts
   */
  @Transactional
  public AnonymizationResult anonymizeCustomer(
      UUID customerId, String confirmationName, String reason, UUID actorId) {

    var customer =
        customerRepository
            .findById(customerId)
            .orElseThrow(() -> new ResourceNotFoundException("Customer", customerId));

    // Guard: reject if already ANONYMIZED
    if (customer.getLifecycleStatus() == LifecycleStatus.ANONYMIZED) {
      throw new ResourceConflictException(
          "Customer is already anonymized",
          "Customer " + customerId + " has already been anonymized");
    }

    // Confirmation name validation (case-insensitive)
    if (!customer.getName().equalsIgnoreCase(confirmationName)) {
      throw new InvalidStateException(
          "Confirmation name does not match customer name",
          "The provided confirmation name does not match the customer name");
    }

    // Step 0: Pre-anonymization export (BEFORE any modification).
    // Export runs in a nested transaction; if anonymization fails after this point,
    // the S3 export may be orphaned but customer data is preserved — acceptable
    // tradeoff for data protection compliance where the export is a safety net.
    var exportResult = dataExportService.exportCustomerData(customerId, actorId);

    // Step 1: Anonymize customer PII (existing method)
    String hash = customerId.toString().substring(0, 6);
    customer.anonymize("Anonymized Customer " + hash);

    // Step 2: Clear notes and customFields
    customer.setNotes(null);
    customer.setCustomFields(null);

    // Step 3: Set ANONYMIZED status directly (bypass transition validation)
    customer.setLifecycleStatus(LifecycleStatus.ANONYMIZED, actorId);
    customerRepository.save(customer);

    // Step 4: Redact portal-visible comments (BEFORE deleting documents)
    var portalComments = commentRepository.findPortalVisibleByCustomerId(customerId);
    int commentsRedacted = portalComments.size();
    for (var comment : portalComments) {
      comment.redact("[Removed]");
      commentRepository.save(comment);
    }

    // Step 5: Delete customer-scoped documents
    var documents = documentRepository.findByCustomerId(customerId);
    int documentsDeleted = documents.size();
    for (var doc : documents) {
      if (doc.getS3Key() != null && !"pending".equals(doc.getS3Key())) {
        storageService.delete(doc.getS3Key());
      }
      documentRepository.delete(doc);
    }

    // Step 6: Anonymize portal contacts
    var contacts = portalContactRepository.findByCustomerId(customerId);
    int contactsAnonymized = contacts.size();
    for (var contact : contacts) {
      contact.anonymize("Removed Contact");
      portalContactRepository.save(contact);
    }

    // Step 7: Update invoice customer references to REF-{shortId}
    var invoices = invoiceRepository.findByCustomerId(customerId);
    String refId = "REF-" + hash;
    for (var invoice : invoices) {
      invoice.setCustomerName(refId);
      invoice.setCustomerEmail(null);
      invoice.setCustomerAddress(null);
      invoiceRepository.save(invoice);
    }
    int invoicesPreserved = invoices.size();

    // Step 8: Audit event with pre-anonymization export key (actual S3 key from export)
    auditService.log(
        AuditEventBuilder.builder()
            .eventType("data.subject.anonymized")
            .entityType("customer")
            .entityId(customerId)
            .details(
                Map.of(
                    "customerId", customerId.toString(),
                    "referenceId", refId,
                    "documentsDeleted", documentsDeleted,
                    "commentsRedacted", commentsRedacted,
                    "portalContactsAnonymized", contactsAnonymized,
                    "invoicesPreserved", invoicesPreserved,
                    "preAnonymizationExportKey", exportResult.s3Key(),
                    "reason", reason != null ? reason : ""))
            .build());

    log.info(
        "Customer anonymized: customerId={}, docs={}, comments={}, contacts={}, invoices={}",
        customerId,
        documentsDeleted,
        commentsRedacted,
        contactsAnonymized,
        invoicesPreserved);

    return new AnonymizationResult(
        true, documentsDeleted, commentsRedacted, contactsAnonymized, invoicesPreserved);
  }

  /**
   * Previews the impact of anonymizing a customer. Returns counts of all related entities that
   * would be affected, plus financial retention information. This is a read-only operation with no
   * side effects.
   *
   * @param customerId the customer to preview
   * @return preview with entity counts and financial retention info
   */
  @Transactional(readOnly = true)
  public AnonymizationPreview previewAnonymization(UUID customerId) {
    var customer =
        customerRepository
            .findById(customerId)
            .orElseThrow(() -> new ResourceNotFoundException("Customer", customerId));

    // Count related entities using count queries (avoid loading full entity lists)
    int portalContacts = (int) portalContactRepository.countByCustomerId(customerId);
    var customerProjects = customerProjectRepository.findByCustomerId(customerId);
    int projects = (int) customerProjectRepository.countByCustomerId(customerId);
    int documents = (int) documentRepository.countByCustomerId(customerId);

    // Time entries via customer's projects
    List<UUID> projectIds = customerProjects.stream().map(CustomerProject::getProjectId).toList();
    int timeEntries =
        projectIds.isEmpty() ? 0 : (int) timeEntryRepository.countByProjectIdIn(projectIds);

    // Invoices: use count for the total, but load list for retention date calculation
    var invoices = invoiceRepository.findByCustomerId(customerId);
    int invoiceCount = (int) invoiceRepository.countByCustomerId(customerId);
    int comments = (int) commentRepository.countPortalVisibleByCustomerId(customerId);

    // Custom field values count
    int customFieldValues =
        customer.getCustomFields() != null ? customer.getCustomFields().size() : 0;

    // Financial retention calculation
    var settings = orgSettingsRepository.findForCurrentTenant().orElse(null);
    int financialRetentionMonths =
        (settings != null && settings.getFinancialRetentionMonths() != null)
            ? settings.getFinancialRetentionMonths()
            : 60;

    // Find oldest invoice date for retention expiry calculation
    LocalDate oldestInvoiceDate =
        invoices.stream()
            .map(DataAnonymizationService::resolveInvoiceDate)
            .min(LocalDate::compareTo)
            .orElse(null);

    LocalDate financialRetentionExpiresAt =
        oldestInvoiceDate != null ? oldestInvoiceDate.plusMonths(financialRetentionMonths) : null;

    // All invoices are preserved (never deleted) — financialRecordsRetained = total invoice count
    int financialRecordsRetained = invoiceCount;

    return new AnonymizationPreview(
        customerId,
        customer.getName(),
        portalContacts,
        projects,
        documents,
        timeEntries,
        invoiceCount,
        comments,
        customFieldValues,
        financialRecordsRetained,
        financialRetentionExpiresAt);
  }

  /** Resolves the effective date of an invoice — uses issueDate if available, else createdAt. */
  private static LocalDate resolveInvoiceDate(Invoice invoice) {
    if (invoice.getIssueDate() != null) {
      return invoice.getIssueDate();
    }
    return invoice.getCreatedAt().atZone(ZoneOffset.UTC).toLocalDate();
  }

  /** Result of anonymization execution. */
  public record AnonymizationResult(
      boolean customerAnonymized,
      int documentsDeleted,
      int commentsRedacted,
      int portalContactsAnonymized,
      int invoicesPreserved) {}
}
