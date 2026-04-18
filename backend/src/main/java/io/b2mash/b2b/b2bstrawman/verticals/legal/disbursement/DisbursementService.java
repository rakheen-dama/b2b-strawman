package io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.document.Document;
import io.b2mash.b2b.b2bstrawman.document.DocumentRepository;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceConflictException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.integration.storage.StorageService;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.verticals.VerticalModuleGuard;
import io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.dto.CreateDisbursementRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.dto.DisbursementResponse;
import io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.dto.DisbursementStatementDto;
import io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.dto.UnbilledDisbursementDto;
import io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.dto.UpdateDisbursementRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.event.DisbursementApprovedEvent;
import io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.event.DisbursementBilledEvent;
import io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.event.DisbursementRejectedEvent;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransaction;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionRepository;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Write-side service for {@link LegalDisbursement}. Every public mutation method:
 *
 * <ol>
 *   <li>gates the operation through {@code VerticalModuleGuard.requireModule("disbursements")}
 *   <li>validates semantically (amount, trust link, category, VAT treatment)
 *   <li>performs the state transition via the entity's own methods
 *   <li>persists, audits, and publishes the corresponding domain event (where applicable)
 * </ol>
 */
@Service
public class DisbursementService {

  private static final Logger log = LoggerFactory.getLogger(DisbursementService.class);

  static final String MODULE_ID = "disbursements";

  /**
   * Required {@code transactionType} value for a linked {@code TrustTransaction}. The architecture
   * spec references {@code DISBURSEMENT_PAYMENT}; the currently-deployed {@code chk_trust_txn_type}
   * CHECK does not yet include that value (future Phase 67 migration). For today's schema the
   * closest semantic neighbour is {@code PAYMENT} — which covers every trust-account disbursement
   * withdrawal.
   */
  static final String EXPECTED_TRUST_TRANSACTION_TYPE = "PAYMENT";

  static final String APPROVED_STATUS = "APPROVED";

  private static final BigDecimal VAT_RATE = new BigDecimal("0.15");

  private final DisbursementRepository disbursementRepository;
  private final DisbursementQueryRepository disbursementQueryRepository;
  private final ProjectRepository projectRepository;
  private final CustomerRepository customerRepository;
  private final TrustTransactionRepository trustTransactionRepository;
  private final DocumentRepository documentRepository;
  private final StorageService storageService;
  private final VerticalModuleGuard moduleGuard;
  private final AuditService auditService;
  private final ApplicationEventPublisher eventPublisher;

  public DisbursementService(
      DisbursementRepository disbursementRepository,
      DisbursementQueryRepository disbursementQueryRepository,
      ProjectRepository projectRepository,
      CustomerRepository customerRepository,
      TrustTransactionRepository trustTransactionRepository,
      DocumentRepository documentRepository,
      StorageService storageService,
      VerticalModuleGuard moduleGuard,
      AuditService auditService,
      ApplicationEventPublisher eventPublisher) {
    this.disbursementRepository = disbursementRepository;
    this.disbursementQueryRepository = disbursementQueryRepository;
    this.projectRepository = projectRepository;
    this.customerRepository = customerRepository;
    this.trustTransactionRepository = trustTransactionRepository;
    this.documentRepository = documentRepository;
    this.storageService = storageService;
    this.moduleGuard = moduleGuard;
    this.auditService = auditService;
    this.eventPublisher = eventPublisher;
  }

  // ---------------------------------------------------------------------------
  // CRUD / state transitions
  // ---------------------------------------------------------------------------

  @Transactional
  public DisbursementResponse create(CreateDisbursementRequest req, UUID createdBy) {
    moduleGuard.requireModule(MODULE_ID);

    projectRepository
        .findById(req.projectId())
        .orElseThrow(() -> new ResourceNotFoundException("Project", req.projectId()));
    customerRepository
        .findById(req.customerId())
        .orElseThrow(() -> new ResourceNotFoundException("Customer", req.customerId()));

    validateTrustLink(req.paymentSource(), req.trustTransactionId(), req.projectId());

    var effectiveVat =
        req.vatTreatment() != null ? req.vatTreatment() : defaultVatTreatmentFor(req.category());
    var vatAmount = computeVatAmount(req.amount(), effectiveVat);

    var entity =
        new LegalDisbursement(
            req.projectId(),
            req.customerId(),
            req.category().name(),
            req.description(),
            req.amount(),
            effectiveVat.name(),
            vatAmount,
            req.paymentSource().name(),
            req.trustTransactionId(),
            req.incurredDate(),
            req.supplierName(),
            req.supplierReference(),
            req.receiptDocumentId(),
            createdBy);

    var saved = disbursementRepository.save(entity);
    log.info(
        "Created disbursement {} on project {} ({}, amount={})",
        saved.getId(),
        saved.getProjectId(),
        saved.getCategory(),
        saved.getAmount());

    var details = new LinkedHashMap<String, Object>();
    details.put("project_id", saved.getProjectId().toString());
    details.put("customer_id", saved.getCustomerId().toString());
    details.put("category", saved.getCategory());
    details.put("amount", saved.getAmount().toString());
    details.put("vat_treatment", saved.getVatTreatment());
    details.put("payment_source", saved.getPaymentSource());
    auditService.log(
        AuditEventBuilder.builder()
            .eventType("disbursement.created")
            .entityType("legal_disbursement")
            .entityId(saved.getId())
            .details(details)
            .build());

    return DisbursementResponse.from(saved);
  }

  @Transactional(readOnly = true)
  public DisbursementResponse getById(UUID id) {
    moduleGuard.requireModule(MODULE_ID);
    var entity = requireDisbursement(id);
    return DisbursementResponse.from(entity);
  }

  @Transactional
  public DisbursementResponse update(UUID id, UpdateDisbursementRequest req) {
    moduleGuard.requireModule(MODULE_ID);
    var entity = requireDisbursement(id);

    if (!DisbursementApprovalStatus.DRAFT.name().equals(entity.getApprovalStatus())
        && !DisbursementApprovalStatus.PENDING_APPROVAL.name().equals(entity.getApprovalStatus())) {
      throw new InvalidStateException(
          "Cannot update disbursement",
          "Disbursement is in %s and cannot be updated. Reject it first if changes are required."
              .formatted(entity.getApprovalStatus()));
    }

    if (req.amount() != null && req.amount().compareTo(BigDecimal.ZERO) <= 0) {
      throw new InvalidStateException("Invalid amount", "amount must be greater than zero");
    }

    if (req.category() != null) {
      entity.setCategory(req.category().name());
    }
    if (req.description() != null) {
      entity.setDescription(req.description());
    }
    if (req.amount() != null) {
      entity.setAmount(req.amount());
    }
    if (req.vatTreatment() != null) {
      entity.setVatTreatment(req.vatTreatment().name());
    }
    if (req.incurredDate() != null) {
      entity.setIncurredDate(req.incurredDate());
    }
    if (req.supplierName() != null) {
      entity.setSupplierName(req.supplierName());
    }
    if (req.supplierReference() != null) {
      entity.setSupplierReference(req.supplierReference());
    }
    if (req.receiptDocumentId() != null) {
      entity.setReceiptDocumentId(req.receiptDocumentId());
    }

    // Recompute VAT whenever the amount or VAT treatment changes — never rely on read-time
    // recomputation (architecture decision).
    if (req.amount() != null || req.vatTreatment() != null) {
      var effectiveVat = VatTreatment.valueOf(entity.getVatTreatment());
      entity.setVatAmount(computeVatAmount(entity.getAmount(), effectiveVat));
    }

    var saved = disbursementRepository.save(entity);
    auditService.log(
        AuditEventBuilder.builder()
            .eventType("disbursement.updated")
            .entityType("legal_disbursement")
            .entityId(saved.getId())
            .details(Map.of("project_id", saved.getProjectId().toString()))
            .build());

    return DisbursementResponse.from(saved);
  }

  @Transactional
  public DisbursementResponse submitForApproval(UUID id) {
    moduleGuard.requireModule(MODULE_ID);
    var entity = requireDisbursement(id);
    try {
      entity.submitForApproval();
    } catch (IllegalStateException e) {
      throw new InvalidStateException("Cannot submit for approval", e.getMessage());
    }
    var saved = disbursementRepository.save(entity);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("disbursement.submitted")
            .entityType("legal_disbursement")
            .entityId(saved.getId())
            .details(Map.of("project_id", saved.getProjectId().toString()))
            .build());

    return DisbursementResponse.from(saved);
  }

  @Transactional
  public DisbursementResponse approve(UUID id, UUID approverId, String notes) {
    moduleGuard.requireModule(MODULE_ID);
    var entity = requireDisbursement(id);
    try {
      entity.approve(approverId, notes);
    } catch (IllegalStateException e) {
      throw new InvalidStateException("Cannot approve disbursement", e.getMessage());
    } catch (IllegalArgumentException e) {
      throw new InvalidStateException("Invalid approver", e.getMessage());
    }
    var saved = disbursementRepository.save(entity);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("disbursement.approved")
            .entityType("legal_disbursement")
            .entityId(saved.getId())
            .details(
                Map.of(
                    "project_id", saved.getProjectId().toString(),
                    "approver_id", approverId.toString()))
            .build());

    eventPublisher.publishEvent(
        DisbursementApprovedEvent.of(
            saved.getId(), saved.getProjectId(), saved.getCustomerId(), approverId));

    return DisbursementResponse.from(saved);
  }

  @Transactional
  public DisbursementResponse reject(UUID id, UUID approverId, String notes) {
    moduleGuard.requireModule(MODULE_ID);
    var entity = requireDisbursement(id);
    try {
      entity.reject(approverId, notes);
    } catch (IllegalStateException e) {
      throw new InvalidStateException("Cannot reject disbursement", e.getMessage());
    } catch (IllegalArgumentException e) {
      throw new InvalidStateException("Invalid approver", e.getMessage());
    }
    var saved = disbursementRepository.save(entity);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("disbursement.rejected")
            .entityType("legal_disbursement")
            .entityId(saved.getId())
            .details(
                Map.of(
                    "project_id", saved.getProjectId().toString(),
                    "approver_id", approverId.toString()))
            .build());

    eventPublisher.publishEvent(
        DisbursementRejectedEvent.of(
            saved.getId(), saved.getProjectId(), saved.getCustomerId(), approverId));

    return DisbursementResponse.from(saved);
  }

  @Transactional
  public DisbursementResponse writeOff(UUID id, String reason) {
    moduleGuard.requireModule(MODULE_ID);
    if (reason == null || reason.isBlank()) {
      throw new InvalidStateException(
          "Write-off reason required", "reason must not be null or blank");
    }
    var entity = requireDisbursement(id);
    try {
      entity.writeOff(reason);
    } catch (IllegalStateException e) {
      throw new ResourceConflictException("Cannot write off disbursement", e.getMessage());
    }
    var saved = disbursementRepository.save(entity);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("disbursement.written_off")
            .entityType("legal_disbursement")
            .entityId(saved.getId())
            .details(Map.of("project_id", saved.getProjectId().toString(), "reason", reason))
            .build());

    return DisbursementResponse.from(saved);
  }

  /**
   * Marks a disbursement as BILLED. Internal — no controller endpoint; called by 487B's {@code
   * InvoiceCreationService} when an invoice line is generated against this disbursement. Publishes
   * {@link DisbursementBilledEvent} on success.
   */
  @Transactional
  public DisbursementResponse markBilled(UUID id, UUID invoiceLineId) {
    moduleGuard.requireModule(MODULE_ID);
    var entity = requireDisbursement(id);
    try {
      entity.markBilled(invoiceLineId);
    } catch (IllegalStateException e) {
      throw new ResourceConflictException("Cannot bill disbursement", e.getMessage());
    } catch (IllegalArgumentException e) {
      throw new InvalidStateException("Invalid invoice line", e.getMessage());
    }
    var saved = disbursementRepository.save(entity);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("disbursement.billed")
            .entityType("legal_disbursement")
            .entityId(saved.getId())
            .details(
                Map.of(
                    "project_id", saved.getProjectId().toString(),
                    "invoice_line_id", invoiceLineId.toString()))
            .build());

    UUID actorId = RequestScopes.MEMBER_ID.isBound() ? RequestScopes.MEMBER_ID.get() : null;
    eventPublisher.publishEvent(
        DisbursementBilledEvent.of(
            saved.getId(), saved.getProjectId(), saved.getCustomerId(), invoiceLineId, actorId));

    return DisbursementResponse.from(saved);
  }

  @Transactional
  public DisbursementResponse attachReceipt(UUID id, MultipartFile file) {
    moduleGuard.requireModule(MODULE_ID);
    if (file == null || file.isEmpty()) {
      throw new InvalidStateException("Receipt required", "Uploaded file must not be empty");
    }
    var entity = requireDisbursement(id);
    var actorId = RequestScopes.requireMemberId();

    byte[] bytes;
    try {
      bytes = file.getBytes();
    } catch (IOException e) {
      throw new InvalidStateException(
          "Receipt upload failed", "Unable to read uploaded file: " + e.getMessage());
    }

    var tenantId = RequestScopes.getTenantIdOrNull();
    var safeName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "receipt";
    var s3Key =
        "disbursement-receipts/"
            + (tenantId != null ? tenantId : "default")
            + "/"
            + entity.getId()
            + "/"
            + UUID.randomUUID()
            + "-"
            + safeName;
    storageService.upload(s3Key, bytes, file.getContentType());

    var document =
        new Document(
            entity.getProjectId(), safeName, file.getContentType(), file.getSize(), actorId);
    document.assignS3Key(s3Key);
    document.confirmUpload();
    var savedDoc = documentRepository.save(document);

    entity.setReceiptDocumentId(savedDoc.getId());
    var saved = disbursementRepository.save(entity);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("disbursement.receipt_attached")
            .entityType("legal_disbursement")
            .entityId(saved.getId())
            .details(
                Map.of(
                    "project_id", saved.getProjectId().toString(),
                    "document_id", savedDoc.getId().toString(),
                    "file_name", safeName))
            .build());

    return DisbursementResponse.from(saved);
  }

  // ---------------------------------------------------------------------------
  // Read-side
  // ---------------------------------------------------------------------------

  @Transactional(readOnly = true)
  public Page<DisbursementResponse> list(
      UUID projectId,
      DisbursementApprovalStatus approvalStatus,
      DisbursementBillingStatus billingStatus,
      DisbursementCategory category,
      Pageable pageable) {
    moduleGuard.requireModule(MODULE_ID);
    var page =
        disbursementQueryRepository.findFiltered(
            projectId,
            approvalStatus != null ? approvalStatus.name() : null,
            billingStatus != null ? billingStatus.name() : null,
            category != null ? category.name() : null,
            pageable);
    return page.map(DisbursementResponse::from);
  }

  @Transactional(readOnly = true)
  public List<DisbursementStatementDto> listForStatement(
      UUID projectId, LocalDate periodStart, LocalDate periodEnd) {
    moduleGuard.requireModule(MODULE_ID);
    return disbursementRepository.findForStatement(projectId, periodStart, periodEnd).stream()
        .map(DisbursementStatementDto::from)
        .toList();
  }

  /**
   * Returns unbilled-billable disbursements for a project. The customer-scoped variant required by
   * the {@code /unbilled} endpoint is intentionally not implemented here — that is owned by slice
   * 487A.
   */
  @Transactional(readOnly = true)
  public List<UnbilledDisbursementDto> listUnbilledForProject(UUID projectId) {
    moduleGuard.requireModule(MODULE_ID);
    return disbursementRepository
        .findByProjectIdAndApprovalStatusIn(
            projectId, List.of(DisbursementApprovalStatus.APPROVED.name()))
        .stream()
        .filter(d -> DisbursementBillingStatus.UNBILLED.name().equals(d.getBillingStatus()))
        .map(UnbilledDisbursementDto::from)
        .toList();
  }

  // ---------------------------------------------------------------------------
  // VAT helpers (package-private for tests and downstream slices)
  // ---------------------------------------------------------------------------

  /**
   * Returns the category-conventional default {@link VatTreatment} applied when a caller does not
   * explicitly specify one. Pass-through fees (sheriff, deeds office, court) are zero-rated;
   * professional/advisory services default to standard 15%.
   */
  public static VatTreatment defaultVatTreatmentFor(DisbursementCategory category) {
    if (category == null) {
      throw new IllegalArgumentException("category must not be null");
    }
    return switch (category) {
      case SHERIFF_FEES, DEEDS_OFFICE_FEES, COURT_FEES -> VatTreatment.ZERO_RATED_PASS_THROUGH;
      case COUNSEL_FEES, ADVOCATE_FEES, SEARCH_FEES, EXPERT_WITNESS, TRAVEL, OTHER ->
          VatTreatment.STANDARD_15;
    };
  }

  /**
   * Computes the VAT amount for a given base amount and treatment. {@code STANDARD_15} rounds to 2
   * scale HALF_UP; {@code ZERO_RATED_PASS_THROUGH} and {@code EXEMPT} return zero.
   */
  public static BigDecimal computeVatAmount(BigDecimal amount, VatTreatment treatment) {
    if (amount == null) {
      throw new IllegalArgumentException("amount must not be null");
    }
    if (treatment == null) {
      throw new IllegalArgumentException("vatTreatment must not be null");
    }
    return switch (treatment) {
      case STANDARD_15 -> amount.multiply(VAT_RATE).setScale(2, RoundingMode.HALF_UP);
      case ZERO_RATED_PASS_THROUGH, EXEMPT -> BigDecimal.ZERO.setScale(2);
    };
  }

  // ---------------------------------------------------------------------------
  // Trust-link validation (task 486.12)
  // ---------------------------------------------------------------------------

  /**
   * Validates the trust-transaction link. Contract:
   *
   * <ul>
   *   <li>{@code OFFICE_ACCOUNT} → {@code trustTransactionId} must be {@code null}.
   *   <li>{@code TRUST_ACCOUNT} → {@code trustTransactionId} must be non-null and the referenced
   *       trust transaction must exist, be {@code APPROVED}, have {@code transactionType=PAYMENT},
   *       and share the same {@code projectId}.
   * </ul>
   *
   * Fails via {@link InvalidStateException} — surfaces as 400 ProblemDetail to the caller.
   */
  void validateTrustLink(
      DisbursementPaymentSource paymentSource, UUID trustTransactionId, UUID projectId) {
    if (paymentSource == DisbursementPaymentSource.OFFICE_ACCOUNT) {
      if (trustTransactionId != null) {
        throw new InvalidStateException(
            "Invalid trust link",
            "trustTransactionId must be null when paymentSource is OFFICE_ACCOUNT");
      }
      return;
    }

    // TRUST_ACCOUNT
    if (trustTransactionId == null) {
      throw new InvalidStateException(
          "Trust link required",
          "trustTransactionId is required when paymentSource is TRUST_ACCOUNT");
    }

    TrustTransaction tx =
        trustTransactionRepository
            .findById(trustTransactionId)
            .orElseThrow(
                () -> new ResourceNotFoundException("TrustTransaction", trustTransactionId));

    if (!APPROVED_STATUS.equals(tx.getStatus())) {
      throw new InvalidStateException(
          "Trust transaction not approved",
          "Linked trust transaction must be APPROVED (current=%s)".formatted(tx.getStatus()));
    }
    if (!EXPECTED_TRUST_TRANSACTION_TYPE.equals(tx.getTransactionType())) {
      throw new InvalidStateException(
          "Wrong trust transaction type",
          "Linked trust transaction must be of type %s (current=%s)"
              .formatted(EXPECTED_TRUST_TRANSACTION_TYPE, tx.getTransactionType()));
    }
    if (tx.getProjectId() == null || !tx.getProjectId().equals(projectId)) {
      throw new InvalidStateException(
          "Trust transaction project mismatch",
          "Linked trust transaction belongs to a different project");
    }
  }

  // ---------------------------------------------------------------------------
  // Internals
  // ---------------------------------------------------------------------------

  private LegalDisbursement requireDisbursement(UUID id) {
    return disbursementRepository
        .findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("LegalDisbursement", id));
  }
}
