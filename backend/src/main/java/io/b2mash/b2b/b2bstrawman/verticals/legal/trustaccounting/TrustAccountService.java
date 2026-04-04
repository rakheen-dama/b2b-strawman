package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.verticals.VerticalModuleGuard;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.lpff.LpffRate;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.lpff.LpffRateRepository;
import jakarta.persistence.EntityManager;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TrustAccountService {

  private static final String MODULE_ID = "trust_accounting";

  private final TrustAccountRepository trustAccountRepository;
  private final LpffRateRepository lpffRateRepository;
  private final VerticalModuleGuard moduleGuard;
  private final AuditService auditService;
  private final EntityManager entityManager;

  public TrustAccountService(
      TrustAccountRepository trustAccountRepository,
      LpffRateRepository lpffRateRepository,
      VerticalModuleGuard moduleGuard,
      AuditService auditService,
      EntityManager entityManager) {
    this.trustAccountRepository = trustAccountRepository;
    this.lpffRateRepository = lpffRateRepository;
    this.moduleGuard = moduleGuard;
    this.auditService = auditService;
    this.entityManager = entityManager;
  }

  // --- DTO Records ---

  public record CreateTrustAccountRequest(
      @NotBlank String accountName,
      @NotBlank String bankName,
      @NotBlank String branchCode,
      @NotBlank String accountNumber,
      String accountType,
      Boolean isPrimary,
      Boolean requireDualApproval,
      BigDecimal paymentApprovalThreshold,
      LocalDate openedDate,
      String notes) {}

  public record UpdateTrustAccountRequest(
      String accountName,
      String bankName,
      String branchCode,
      String accountNumber,
      Boolean requireDualApproval,
      BigDecimal paymentApprovalThreshold,
      String notes) {}

  public record TrustAccountResponse(
      UUID id,
      String accountName,
      String bankName,
      String branchCode,
      String accountNumber,
      TrustAccountType accountType,
      boolean isPrimary,
      boolean requireDualApproval,
      BigDecimal paymentApprovalThreshold,
      TrustAccountStatus status,
      LocalDate openedDate,
      LocalDate closedDate,
      String notes,
      Instant createdAt,
      Instant updatedAt) {}

  public record CreateLpffRateRequest(
      @NotNull LocalDate effectiveFrom,
      @NotNull BigDecimal ratePercent,
      @NotNull BigDecimal lpffSharePercent,
      String notes) {}

  public record LpffRateResponse(
      UUID id,
      UUID trustAccountId,
      LocalDate effectiveFrom,
      BigDecimal ratePercent,
      BigDecimal lpffSharePercent,
      String notes,
      Instant createdAt) {}

  // --- Service Methods ---

  @Transactional(readOnly = true)
  public List<TrustAccountResponse> listTrustAccounts() {
    moduleGuard.requireModule(MODULE_ID);

    return trustAccountRepository.findByStatus(TrustAccountStatus.ACTIVE).stream()
        .map(this::toResponse)
        .toList();
  }

  @Transactional(readOnly = true)
  public TrustAccountResponse getTrustAccount(UUID id) {
    moduleGuard.requireModule(MODULE_ID);

    var account =
        trustAccountRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("TrustAccount", id));

    return toResponse(account);
  }

  @Transactional
  public TrustAccountResponse createTrustAccount(CreateTrustAccountRequest request) {
    moduleGuard.requireModule(MODULE_ID);

    boolean primary = request.isPrimary() != null ? request.isPrimary() : true;
    boolean dualApproval =
        request.requireDualApproval() != null ? request.requireDualApproval() : false;
    TrustAccountType accountType =
        request.accountType() != null
            ? TrustAccountType.valueOf(request.accountType())
            : TrustAccountType.GENERAL;

    var account =
        new TrustAccount(
            request.accountName(),
            request.bankName(),
            request.branchCode(),
            request.accountNumber(),
            accountType,
            primary,
            dualApproval,
            request.paymentApprovalThreshold(),
            request.openedDate() != null ? request.openedDate() : LocalDate.now(),
            request.notes());

    var saved = trustAccountRepository.save(account);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("trust_account.created")
            .entityType("trust_account")
            .entityId(saved.getId())
            .details(
                Map.of(
                    "account_name", saved.getAccountName(),
                    "bank_name", saved.getBankName(),
                    "account_type", saved.getAccountType().name()))
            .build());

    return toResponse(saved);
  }

  @Transactional
  public TrustAccountResponse updateTrustAccount(UUID id, UpdateTrustAccountRequest request) {
    moduleGuard.requireModule(MODULE_ID);

    var account =
        trustAccountRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("TrustAccount", id));

    if (TrustAccountStatus.CLOSED == account.getStatus()) {
      throw new InvalidStateException(
          "Cannot update trust account", "Account is closed and cannot be modified");
    }

    if (request.accountName() != null) {
      account.setAccountName(request.accountName());
    }
    if (request.bankName() != null) {
      account.setBankName(request.bankName());
    }
    if (request.branchCode() != null) {
      account.setBranchCode(request.branchCode());
    }
    if (request.accountNumber() != null) {
      account.setAccountNumber(request.accountNumber());
    }
    if (request.requireDualApproval() != null) {
      account.setRequireDualApproval(request.requireDualApproval());
    }
    if (request.paymentApprovalThreshold() != null) {
      account.setPaymentApprovalThreshold(request.paymentApprovalThreshold());
    }
    if (request.notes() != null) {
      account.setNotes(request.notes());
    }

    var saved = trustAccountRepository.save(account);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("trust_account.updated")
            .entityType("trust_account")
            .entityId(saved.getId())
            .details(
                Map.of(
                    "account_name", saved.getAccountName(),
                    "bank_name", saved.getBankName()))
            .build());

    return toResponse(saved);
  }

  @Transactional
  public TrustAccountResponse closeTrustAccount(UUID id) {
    moduleGuard.requireModule(MODULE_ID);

    var account =
        trustAccountRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("TrustAccount", id));

    if (TrustAccountStatus.CLOSED == account.getStatus()) {
      throw new InvalidStateException("Cannot close trust account", "Account is already closed");
    }

    // Closing guard: check client ledger balances sum to zero
    // The client_ledger_cards table exists from V85 but the entity is created in Epic 440.
    // This native query is forward-compatible.
    var result =
        entityManager
            .createNativeQuery(
                "SELECT COALESCE(SUM(balance), 0) FROM client_ledger_cards WHERE trust_account_id = :accountId")
            .setParameter("accountId", id)
            .getSingleResult();
    BigDecimal totalBalance = (BigDecimal) result;
    if (totalBalance.compareTo(BigDecimal.ZERO) != 0) {
      throw new InvalidStateException(
          "Cannot close trust account",
          "Account has non-zero client balances totalling " + totalBalance);
    }

    account.setStatus(TrustAccountStatus.CLOSED);
    account.setClosedDate(LocalDate.now());

    var saved = trustAccountRepository.save(account);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("trust_account.closed")
            .entityType("trust_account")
            .entityId(saved.getId())
            .details(
                Map.of(
                    "account_name", saved.getAccountName(),
                    "closed_date", saved.getClosedDate().toString()))
            .build());

    return toResponse(saved);
  }

  /**
   * Returns the primary GENERAL trust account, or empty if none has been configured yet. A newly
   * onboarded tenant may not have a trust account, so Optional is the correct return type here.
   */
  @Transactional(readOnly = true)
  public Optional<TrustAccountResponse> getPrimaryAccount() {
    moduleGuard.requireModule(MODULE_ID);

    return trustAccountRepository
        .findByAccountTypeAndPrimaryTrue(TrustAccountType.GENERAL)
        .map(this::toResponse);
  }

  @Transactional
  public LpffRateResponse addLpffRate(UUID accountId, CreateLpffRateRequest request) {
    moduleGuard.requireModule(MODULE_ID);

    trustAccountRepository
        .findById(accountId)
        .orElseThrow(() -> new ResourceNotFoundException("TrustAccount", accountId));

    var rate =
        new LpffRate(
            accountId,
            request.effectiveFrom(),
            request.ratePercent(),
            request.lpffSharePercent(),
            request.notes());

    var saved = lpffRateRepository.save(rate);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("lpff_rate.created")
            .entityType("lpff_rate")
            .entityId(saved.getId())
            .details(
                Map.of(
                    "trust_account_id", accountId.toString(),
                    "effective_from", saved.getEffectiveFrom().toString(),
                    "rate_percent", saved.getRatePercent().toString(),
                    "lpff_share_percent", saved.getLpffSharePercent().toString()))
            .build());

    return toLpffResponse(saved);
  }

  @Transactional(readOnly = true)
  public List<LpffRateResponse> listLpffRates(UUID accountId) {
    moduleGuard.requireModule(MODULE_ID);

    return lpffRateRepository.findByTrustAccountIdOrderByEffectiveFromDesc(accountId).stream()
        .map(this::toLpffResponse)
        .toList();
  }

  @Transactional(readOnly = true)
  public LpffRateResponse getCurrentLpffRate(UUID accountId, LocalDate asOfDate) {
    moduleGuard.requireModule(MODULE_ID);

    var rate =
        lpffRateRepository
            .findFirstByTrustAccountIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                accountId, asOfDate)
            .orElseThrow(
                () ->
                    new InvalidStateException(
                        "No LPFF rate configured",
                        "No effective LPFF rate found for account "
                            + accountId
                            + " as of "
                            + asOfDate));

    return toLpffResponse(rate);
  }

  // --- Private Helpers ---

  private TrustAccountResponse toResponse(TrustAccount entity) {
    return new TrustAccountResponse(
        entity.getId(),
        entity.getAccountName(),
        entity.getBankName(),
        entity.getBranchCode(),
        entity.getAccountNumber(),
        entity.getAccountType(),
        entity.isPrimary(),
        entity.getRequireDualApproval(),
        entity.getPaymentApprovalThreshold(),
        entity.getStatus(),
        entity.getOpenedDate(),
        entity.getClosedDate(),
        entity.getNotes(),
        entity.getCreatedAt(),
        entity.getUpdatedAt());
  }

  private LpffRateResponse toLpffResponse(LpffRate entity) {
    return new LpffRateResponse(
        entity.getId(),
        entity.getTrustAccountId(),
        entity.getEffectiveFrom(),
        entity.getRatePercent(),
        entity.getLpffSharePercent(),
        entity.getNotes(),
        entity.getCreatedAt());
  }
}
