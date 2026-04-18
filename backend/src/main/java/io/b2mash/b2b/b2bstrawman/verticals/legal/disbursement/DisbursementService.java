package io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement;

import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.verticals.VerticalModuleGuard;
import io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.dto.ApprovalDecisionRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.dto.CreateDisbursementRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.dto.DisbursementResponse;
import io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.dto.DisbursementStatementDto;
import io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.dto.UpdateDisbursementRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.dto.WriteOffRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.event.DisbursementApprovedEvent;
import io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.event.DisbursementBilledEvent;
import io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.event.DisbursementRejectedEvent;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransaction;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Write-side service for {@link LegalDisbursement}. Owns create/update, the approval lifecycle,
 * billing transitions, write-offs, and statement-range queries.
 *
 * <p>Every mutation method calls {@link VerticalModuleGuard#requireModule(String)} as its first
 * line to block tenants that have not enabled the {@code disbursements} module.
 *
 * <p>Trust-linked disbursements ({@code paymentSource == TRUST_ACCOUNT}) are validated against the
 * {@code TrustTransaction} referenced by {@code trustTransactionId}: the transaction must exist, be
 * {@code APPROVED}, be a {@code DISBURSEMENT_PAYMENT}, and target the same {@code projectId}.
 */
@Service
public class DisbursementService {

  static final String MODULE_ID = "disbursements";

  private static final BigDecimal VAT_RATE_STANDARD = new BigDecimal("0.15");

  private static final Set<DisbursementCategory> STATUTORY_ZERO_RATED =
      Set.of(
          DisbursementCategory.SHERIFF_FEES,
          DisbursementCategory.DEEDS_OFFICE_FEES,
          DisbursementCategory.COURT_FEES);

  private final DisbursementRepository disbursementRepository;
  private final TrustTransactionRepository trustTransactionRepository;
  private final VerticalModuleGuard moduleGuard;
  private final ApplicationEventPublisher eventPublisher;

  public DisbursementService(
      DisbursementRepository disbursementRepository,
      TrustTransactionRepository trustTransactionRepository,
      VerticalModuleGuard moduleGuard,
      ApplicationEventPublisher eventPublisher) {
    this.disbursementRepository = disbursementRepository;
    this.trustTransactionRepository = trustTransactionRepository;
    this.moduleGuard = moduleGuard;
    this.eventPublisher = eventPublisher;
  }

  // ---------- Create / Update ----------

  @Transactional
  public DisbursementResponse create(CreateDisbursementRequest request) {
    moduleGuard.requireModule(MODULE_ID);
    var createdBy = RequestScopes.requireMemberId();

    validateTrustLink(request.projectId(), request.paymentSource(), request.trustTransactionId());

    var effectiveVat =
        request.vatTreatment() != null
            ? request.vatTreatment()
            : defaultVatTreatmentFor(request.category());
    var vatAmount = computeVatAmount(request.amount(), effectiveVat);
    var currency = request.currency() != null ? request.currency() : "ZAR";

    var entity =
        new LegalDisbursement(
            request.projectId(),
            request.customerId(),
            request.incurredDate(),
            request.category().name(),
            request.description(),
            request.amount(),
            currency,
            effectiveVat.name(),
            vatAmount,
            request.paymentSource().name(),
            createdBy);

    if (request.supplierName() != null) {
      entity.setSupplierName(request.supplierName());
    }
    if (request.paymentSource() == DisbursementPaymentSource.TRUST_ACCOUNT) {
      entity.setTrustTransactionId(request.trustTransactionId());
    }

    var saved = disbursementRepository.save(entity);
    return DisbursementResponse.from(saved);
  }

  @Transactional
  public DisbursementResponse update(UUID id, UpdateDisbursementRequest request) {
    moduleGuard.requireModule(MODULE_ID);
    var entity = requireDisbursement(id);

    // Resolve new/effective values, falling back to the current row for any unspecified field.
    var newIncurredDate =
        request.incurredDate() != null ? request.incurredDate() : entity.getIncurredDate();
    var newCategory =
        request.category() != null
            ? request.category()
            : DisbursementCategory.valueOf(entity.getCategory());
    var newDescription =
        request.description() != null ? request.description() : entity.getDescription();
    var newAmount = request.amount() != null ? request.amount() : entity.getAmount();
    var newCurrency = request.currency() != null ? request.currency() : entity.getCurrency();
    var newVatTreatment =
        request.vatTreatment() != null
            ? request.vatTreatment()
            : VatTreatment.valueOf(entity.getVatTreatment());
    var newVatAmount = computeVatAmount(newAmount, newVatTreatment);
    var newPaymentSource =
        request.paymentSource() != null
            ? request.paymentSource()
            : DisbursementPaymentSource.valueOf(entity.getPaymentSource());
    UUID newTrustTxnId;
    if (request.paymentSource() != null) {
      // Caller is changing the source: honour their trustTransactionId (or null for OFFICE).
      newTrustTxnId =
          newPaymentSource == DisbursementPaymentSource.TRUST_ACCOUNT
              ? request.trustTransactionId()
              : null;
    } else {
      newTrustTxnId =
          request.trustTransactionId() != null
              ? request.trustTransactionId()
              : entity.getTrustTransactionId();
    }

    // Re-validate trust link against the post-update state.
    validateTrustLink(entity.getProjectId(), newPaymentSource, newTrustTxnId);

    try {
      entity.update(
          newIncurredDate,
          newCategory.name(),
          newDescription,
          newAmount,
          newCurrency,
          newVatTreatment.name(),
          newVatAmount,
          request.supplierName() != null ? request.supplierName() : entity.getSupplierName(),
          newPaymentSource.name(),
          newTrustTxnId);
    } catch (IllegalStateException e) {
      throw new InvalidStateException("Cannot update disbursement", e.getMessage());
    }

    var saved = disbursementRepository.save(entity);
    return DisbursementResponse.from(saved);
  }

  // ---------- Approval lifecycle ----------

  @Transactional
  public DisbursementResponse submitForApproval(UUID id) {
    moduleGuard.requireModule(MODULE_ID);
    var entity = requireDisbursement(id);
    try {
      entity.submitForApproval();
    } catch (IllegalStateException e) {
      throw new InvalidStateException("Cannot submit disbursement", e.getMessage());
    }
    return DisbursementResponse.from(disbursementRepository.save(entity));
  }

  @Transactional
  public DisbursementResponse approve(UUID id, ApprovalDecisionRequest request) {
    moduleGuard.requireModule(MODULE_ID);
    var approverId = RequestScopes.requireMemberId();
    var entity = requireDisbursement(id);
    try {
      entity.approve(approverId, request == null ? null : request.notes());
    } catch (IllegalStateException e) {
      throw new InvalidStateException("Cannot approve disbursement", e.getMessage());
    }
    var saved = disbursementRepository.save(entity);
    eventPublisher.publishEvent(
        DisbursementApprovedEvent.of(
            saved.getId(), saved.getProjectId(), saved.getCustomerId(), approverId));
    return DisbursementResponse.from(saved);
  }

  @Transactional
  public DisbursementResponse reject(UUID id, ApprovalDecisionRequest request) {
    moduleGuard.requireModule(MODULE_ID);
    var approverId = RequestScopes.requireMemberId();
    var entity = requireDisbursement(id);
    try {
      entity.reject(approverId, request == null ? null : request.notes());
    } catch (IllegalStateException e) {
      throw new InvalidStateException("Cannot reject disbursement", e.getMessage());
    }
    var saved = disbursementRepository.save(entity);
    eventPublisher.publishEvent(
        DisbursementRejectedEvent.of(
            saved.getId(), saved.getProjectId(), saved.getCustomerId(), approverId));
    return DisbursementResponse.from(saved);
  }

  // ---------- Billing / write-off ----------

  @Transactional
  public DisbursementResponse writeOff(UUID id, WriteOffRequest request) {
    moduleGuard.requireModule(MODULE_ID);
    if (request == null || request.reason() == null || request.reason().isBlank()) {
      throw new InvalidStateException(
          "Invalid write-off", "A reason is required to write off a disbursement");
    }
    var entity = requireDisbursement(id);
    try {
      entity.writeOff(request.reason());
    } catch (IllegalStateException e) {
      throw new InvalidStateException("Cannot write off disbursement", e.getMessage());
    }
    return DisbursementResponse.from(disbursementRepository.save(entity));
  }

  @Transactional
  public DisbursementResponse markBilled(UUID id, UUID invoiceLineId) {
    moduleGuard.requireModule(MODULE_ID);
    if (invoiceLineId == null) {
      throw new InvalidStateException(
          "Invalid billing", "invoiceLineId is required to mark a disbursement billed");
    }
    var actorId = RequestScopes.requireMemberId();
    var entity = requireDisbursement(id);
    try {
      entity.markBilled(invoiceLineId);
    } catch (IllegalStateException e) {
      throw new InvalidStateException("Cannot mark billed", e.getMessage());
    }
    var saved = disbursementRepository.save(entity);
    eventPublisher.publishEvent(
        DisbursementBilledEvent.of(
            saved.getId(), saved.getProjectId(), saved.getCustomerId(), actorId, invoiceLineId));
    return DisbursementResponse.from(saved);
  }

  // ---------- Reads ----------

  @Transactional(readOnly = true)
  public DisbursementResponse get(UUID id) {
    moduleGuard.requireModule(MODULE_ID);
    return DisbursementResponse.from(requireDisbursement(id));
  }

  @Transactional(readOnly = true)
  public List<DisbursementResponse> list(
      UUID projectId, String billingStatus, String approvalStatus) {
    moduleGuard.requireModule(MODULE_ID);
    return disbursementRepository.findAll().stream()
        .filter(d -> projectId == null || projectId.equals(d.getProjectId()))
        .filter(d -> billingStatus == null || billingStatus.equals(d.getBillingStatus()))
        .filter(d -> approvalStatus == null || approvalStatus.equals(d.getApprovalStatus()))
        .map(DisbursementResponse::from)
        .toList();
  }

  @Transactional(readOnly = true)
  public List<DisbursementStatementDto> listForStatement(
      UUID projectId, LocalDate from, LocalDate to) {
    moduleGuard.requireModule(MODULE_ID);
    if (projectId == null) {
      throw new InvalidStateException("Invalid statement query", "projectId is required");
    }
    if (from == null || to == null) {
      throw new InvalidStateException("Invalid statement query", "from and to are required");
    }
    return disbursementRepository.findForStatement(projectId, from, to).stream()
        .map(DisbursementStatementDto::from)
        .toList();
  }

  // ---------- Internal helpers ----------

  /** Sets the receipt document id on a disbursement (called from the multipart receipt upload). */
  @Transactional
  public DisbursementResponse attachReceipt(UUID id, UUID documentId) {
    moduleGuard.requireModule(MODULE_ID);
    var entity = requireDisbursement(id);
    entity.setReceiptDocumentId(documentId);
    return DisbursementResponse.from(disbursementRepository.save(entity));
  }

  private LegalDisbursement requireDisbursement(UUID id) {
    return disbursementRepository
        .findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("LegalDisbursement", id));
  }

  /**
   * Default VAT treatment for a disbursement category. Statutory pass-through categories (sheriff,
   * deeds office, court fees) are zero-rated; everything else defaults to standard-15.
   */
  static VatTreatment defaultVatTreatmentFor(DisbursementCategory category) {
    return STATUTORY_ZERO_RATED.contains(category)
        ? VatTreatment.ZERO_RATED_PASS_THROUGH
        : VatTreatment.STANDARD_15;
  }

  /** Computes VAT amount for a given amount and treatment. Standard-15 = 15%; others = 0. */
  static BigDecimal computeVatAmount(BigDecimal amount, VatTreatment treatment) {
    if (amount == null || treatment != VatTreatment.STANDARD_15) {
      return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }
    return amount.multiply(VAT_RATE_STANDARD).setScale(2, RoundingMode.HALF_UP);
  }

  /**
   * Validates the trust-account link on a disbursement. If {@code paymentSource == TRUST_ACCOUNT},
   * {@code trustTransactionId} must be non-null and reference a {@code DISBURSEMENT_PAYMENT} {@code
   * APPROVED} {@link TrustTransaction} targeting the same {@code projectId}. If {@code
   * paymentSource == OFFICE_ACCOUNT}, {@code trustTransactionId} must be null.
   */
  private void validateTrustLink(
      UUID projectId, DisbursementPaymentSource paymentSource, UUID trustTransactionId) {
    if (paymentSource == DisbursementPaymentSource.OFFICE_ACCOUNT) {
      if (trustTransactionId != null) {
        throw new InvalidStateException(
            "Invalid trust link",
            "trustTransactionId must be null when paymentSource is OFFICE_ACCOUNT");
      }
      return;
    }
    // TRUST_ACCOUNT branch
    if (trustTransactionId == null) {
      throw new InvalidStateException(
          "Invalid trust link",
          "trustTransactionId is required when paymentSource is TRUST_ACCOUNT");
    }
    var txn =
        trustTransactionRepository
            .findById(trustTransactionId)
            .orElseThrow(
                () ->
                    new InvalidStateException(
                        "Invalid trust link",
                        "Referenced trust transaction not found: " + trustTransactionId));
    if (!"APPROVED".equals(txn.getStatus())) {
      throw new InvalidStateException(
          "Invalid trust link",
          "Referenced trust transaction must be APPROVED, got: " + txn.getStatus());
    }
    if (!"DISBURSEMENT_PAYMENT".equals(txn.getTransactionType())) {
      throw new InvalidStateException(
          "Invalid trust link",
          "Referenced trust transaction must be type DISBURSEMENT_PAYMENT, got: "
              + txn.getTransactionType());
    }
    if (txn.getProjectId() == null || !txn.getProjectId().equals(projectId)) {
      throw new InvalidStateException(
          "Invalid trust link",
          "Referenced trust transaction projectId does not match disbursement projectId");
    }
  }
}
