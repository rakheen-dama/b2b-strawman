package io.b2mash.b2b.b2bstrawman.retainer;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.billingrate.BillingRateRepository;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.invoice.Invoice;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceLine;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceLineRepository;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceRepository;
import io.b2mash.b2b.b2bstrawman.member.MemberRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.notification.NotificationService;
import io.b2mash.b2b.b2bstrawman.provisioning.OrganizationRepository;
import io.b2mash.b2b.b2bstrawman.retainer.dto.PeriodReadyToCloseView;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettings;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RetainerPeriodService {

  private static final Logger log = LoggerFactory.getLogger(RetainerPeriodService.class);

  private final RetainerAgreementRepository agreementRepository;
  private final RetainerPeriodRepository periodRepository;
  private final InvoiceRepository invoiceRepository;
  private final InvoiceLineRepository invoiceLineRepository;
  private final BillingRateRepository billingRateRepository;
  private final OrgSettingsRepository orgSettingsRepository;
  private final CustomerRepository customerRepository;
  private final OrganizationRepository organizationRepository;
  private final MemberRepository memberRepository;
  private final AuditService auditService;
  private final NotificationService notificationService;

  public RetainerPeriodService(
      RetainerAgreementRepository agreementRepository,
      RetainerPeriodRepository periodRepository,
      InvoiceRepository invoiceRepository,
      InvoiceLineRepository invoiceLineRepository,
      BillingRateRepository billingRateRepository,
      OrgSettingsRepository orgSettingsRepository,
      CustomerRepository customerRepository,
      OrganizationRepository organizationRepository,
      MemberRepository memberRepository,
      AuditService auditService,
      NotificationService notificationService) {
    this.agreementRepository = agreementRepository;
    this.periodRepository = periodRepository;
    this.invoiceRepository = invoiceRepository;
    this.invoiceLineRepository = invoiceLineRepository;
    this.billingRateRepository = billingRateRepository;
    this.orgSettingsRepository = orgSettingsRepository;
    this.customerRepository = customerRepository;
    this.organizationRepository = organizationRepository;
    this.memberRepository = memberRepository;
    this.auditService = auditService;
    this.notificationService = notificationService;
  }

  public record PeriodCloseResult(
      RetainerPeriod closedPeriod,
      Invoice generatedInvoice,
      List<InvoiceLine> invoiceLines,
      RetainerPeriod nextPeriod) {}

  private record ResolvedOverageRate(BigDecimal hourlyRate, String currency) {}

  @Transactional
  public PeriodCloseResult closePeriod(UUID agreementId, UUID actorMemberId) {
    // 1. Load agreement
    var agreement =
        agreementRepository
            .findById(agreementId)
            .orElseThrow(() -> new ResourceNotFoundException("RetainerAgreement", agreementId));

    // 2. Load open period (with pessimistic lock to prevent double-close)
    var period =
        periodRepository
            .findByAgreementIdAndStatusForUpdate(agreementId, PeriodStatus.OPEN)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "No open period for RetainerAgreement", agreementId));

    // 3. Validate: period end date must have passed
    if (LocalDate.now().isBefore(period.getPeriodEnd())) {
      throw new InvalidStateException(
          "Period not ready to close",
          "Period end date " + period.getPeriodEnd() + " has not passed yet");
    }

    // 3b. Validate: periodFee must be set before closing
    if (agreement.getPeriodFee() == null) {
      throw new InvalidStateException(
          "Period fee required", "Period fee must be set before closing a period");
    }

    // 4. Load customer
    var customer =
        customerRepository
            .findById(agreement.getCustomerId())
            .orElseThrow(
                () -> new ResourceNotFoundException("Customer", agreement.getCustomerId()));

    // 5. Finalize consumption — re-query billable time entries (authoritative at close time)
    long totalMinutes =
        periodRepository.sumConsumedMinutes(
            agreement.getCustomerId(), period.getPeriodStart(), period.getPeriodEnd());
    BigDecimal finalConsumedHours =
        BigDecimal.valueOf(totalMinutes).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
    period.updateConsumption(finalConsumedHours);

    // 6. Calculate overage and rollover (HOUR_BANK only)
    BigDecimal overageHours = BigDecimal.ZERO;
    BigDecimal rolloverHoursOut = BigDecimal.ZERO;
    ResolvedOverageRate resolvedRate = null;

    if (agreement.getType() == RetainerType.HOUR_BANK && period.getAllocatedHours() != null) {
      BigDecimal allocated = period.getAllocatedHours();
      BigDecimal consumed = period.getConsumedHours();

      // Overage
      BigDecimal excess = consumed.subtract(allocated);
      overageHours = excess.compareTo(BigDecimal.ZERO) > 0 ? excess : BigDecimal.ZERO;

      // If overage exists, resolve billing rate
      if (overageHours.compareTo(BigDecimal.ZERO) > 0) {
        resolvedRate = resolveCustomerRate(agreement.getCustomerId(), period.getPeriodEnd());
        if (resolvedRate == null) {
          throw new InvalidStateException(
              "Cannot calculate overage",
              "No billing rate configured for customer " + customer.getName() + " or org default");
        }
      }

      // Rollover
      BigDecimal unusedHours = allocated.subtract(consumed);
      unusedHours = unusedHours.compareTo(BigDecimal.ZERO) > 0 ? unusedHours : BigDecimal.ZERO;

      rolloverHoursOut =
          switch (agreement.getRolloverPolicy()) {
            case FORFEIT -> BigDecimal.ZERO;
            case CARRY_FORWARD -> unusedHours;
            case CARRY_CAPPED -> {
              BigDecimal cap =
                  agreement.getRolloverCapHours() != null
                      ? agreement.getRolloverCapHours()
                      : BigDecimal.ZERO;
              yield unusedHours.min(cap);
            }
          };
    }

    // 7. Get currency from OrgSettings
    String currency =
        orgSettingsRepository
            .findForCurrentTenant()
            .map(OrgSettings::getDefaultCurrency)
            .orElse("ZAR");

    // 7b. Validate currency match between overage rate and org settings
    if (resolvedRate != null && !resolvedRate.currency().equals(currency)) {
      throw new InvalidStateException(
          "Currency mismatch",
          "Billing rate currency ("
              + resolvedRate.currency()
              + ") does not match organization currency ("
              + currency
              + ")");
    }

    // 8. Look up org name for invoice snapshot
    String orgId = RequestScopes.requireOrgId();
    var organization =
        organizationRepository
            .findByClerkOrgId(orgId)
            .orElseThrow(() -> new ResourceNotFoundException("Organization", orgId));

    // 9. Create DRAFT invoice directly (do NOT use InvoiceService — it requires RequestScopes)
    var invoice =
        new Invoice(
            agreement.getCustomerId(),
            currency,
            customer.getName(),
            customer.getEmail(),
            null, // customerAddress
            organization.getName(),
            actorMemberId);
    invoice = invoiceRepository.save(invoice);

    // Line 1: Base fee
    String periodDesc =
        "Retainer \u2014 " + period.getPeriodStart() + " to " + period.getPeriodEnd();
    var baseLine =
        new InvoiceLine(
            invoice.getId(), null, null, periodDesc, BigDecimal.ONE, agreement.getPeriodFee(), 0);
    baseLine.setRetainerPeriodId(period.getId());
    invoiceLineRepository.save(baseLine);

    // Line 2: Overage (HOUR_BANK with overage > 0)
    if (overageHours.compareTo(BigDecimal.ZERO) > 0 && resolvedRate != null) {
      String overageDesc =
          "Overage (%s hrs @ %s/hr)"
              .formatted(
                  overageHours.stripTrailingZeros().toPlainString(),
                  resolvedRate.hourlyRate().stripTrailingZeros().toPlainString());
      var overageLine =
          new InvoiceLine(
              invoice.getId(), null, null, overageDesc, overageHours, resolvedRate.hourlyRate(), 1);
      overageLine.setRetainerPeriodId(period.getId());
      invoiceLineRepository.save(overageLine);
    }

    // Recalculate invoice totals
    recalculateInvoiceTotals(invoice);
    invoice = invoiceRepository.save(invoice);

    // 9. Close the period
    period.close(invoice.getId(), actorMemberId, overageHours, rolloverHoursOut);
    periodRepository.save(period);

    // 10. Open next period (or auto-terminate)
    RetainerPeriod nextPeriod = null;
    LocalDate nextPeriodStart = period.getPeriodEnd();
    LocalDate nextPeriodEnd = agreement.getFrequency().calculateNextEnd(nextPeriodStart);

    boolean agreementActive = agreement.getStatus() == RetainerStatus.ACTIVE;
    boolean withinEndDate =
        agreement.getEndDate() == null || nextPeriodStart.isBefore(agreement.getEndDate());

    if (agreementActive && withinEndDate) {
      BigDecimal nextAllocated =
          agreement.getType() == RetainerType.HOUR_BANK && agreement.getAllocatedHours() != null
              ? agreement.getAllocatedHours().add(rolloverHoursOut)
              : null;
      nextPeriod =
          new RetainerPeriod(
              agreement.getId(),
              nextPeriodStart,
              nextPeriodEnd,
              nextAllocated,
              agreement.getAllocatedHours(),
              rolloverHoursOut);
      nextPeriod = periodRepository.save(nextPeriod);
    } else if (agreementActive) {
      // Past agreement endDate — auto-terminate
      try {
        agreement.terminate();
      } catch (IllegalStateException e) {
        // Already terminated — ignore
      }
      agreementRepository.save(agreement);
      notifyAdminsAndOwners(
          "RETAINER_TERMINATED",
          "Retainer for " + customer.getName() + " has been automatically terminated",
          agreement.getId());
    }

    // 11. Audit events
    Map<String, Object> closeDetails = new LinkedHashMap<>();
    closeDetails.put("periodId", period.getId().toString());
    closeDetails.put("agreementId", agreement.getId().toString());
    closeDetails.put("periodStart", period.getPeriodStart().toString());
    closeDetails.put("periodEnd", period.getPeriodEnd().toString());
    closeDetails.put("consumedHours", period.getConsumedHours().toString());
    closeDetails.put("overageHours", overageHours.toString());
    closeDetails.put("rolloverHoursOut", rolloverHoursOut.toString());
    closeDetails.put("invoiceId", invoice.getId().toString());
    closeDetails.put("invoiceTotal", invoice.getTotal().toString());

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("retainer.period.closed")
            .entityType("RETAINER_PERIOD")
            .entityId(period.getId())
            .details(closeDetails)
            .build());

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("retainer.invoice.generated")
            .entityType("RETAINER_PERIOD")
            .entityId(period.getId())
            .details(
                Map.of(
                    "invoiceId", invoice.getId().toString(),
                    "agreementId", agreement.getId().toString(),
                    "total", invoice.getTotal().toString()))
            .build());

    if (nextPeriod != null) {
      auditService.log(
          AuditEventBuilder.builder()
              .eventType("retainer.period.opened")
              .entityType("RETAINER_PERIOD")
              .entityId(nextPeriod.getId())
              .details(
                  Map.of(
                      "periodId", nextPeriod.getId().toString(),
                      "agreementId", agreement.getId().toString(),
                      "periodStart", nextPeriod.getPeriodStart().toString(),
                      "periodEnd", nextPeriod.getPeriodEnd().toString(),
                      "rolloverHoursIn", rolloverHoursOut.toString()))
              .build());
    }

    // 12. Notify: period closed
    notifyAdminsAndOwners(
        "RETAINER_PERIOD_CLOSED",
        "Retainer period closed for " + customer.getName(),
        agreement.getId());

    log.info(
        "Closed retainer period {} for agreement {} (overage={}, rolloverOut={})",
        period.getId(),
        agreementId,
        overageHours,
        rolloverHoursOut);

    var invoiceLines = invoiceLineRepository.findByInvoiceIdOrderBySortOrder(invoice.getId());
    return new PeriodCloseResult(period, invoice, invoiceLines, nextPeriod);
  }

  private ResolvedOverageRate resolveCustomerRate(UUID customerId, LocalDate closeDate) {
    // 1. Customer-level rates — prefer member-agnostic (memberId == null) rates first
    var customerRates =
        billingRateRepository.findByFilters(null, null, customerId).stream()
            .filter(r -> r.getProjectId() == null)
            .filter(r -> r.getEffectiveFrom() != null && !r.getEffectiveFrom().isAfter(closeDate))
            .filter(r -> r.getEffectiveTo() == null || !r.getEffectiveTo().isBefore(closeDate))
            .sorted(
                (a, b) -> {
                  // Prefer member-agnostic rates (memberId == null) over member-specific ones
                  boolean aNull = a.getMemberId() == null;
                  boolean bNull = b.getMemberId() == null;
                  if (aNull != bNull) return aNull ? -1 : 1;
                  return 0;
                })
            .toList();
    if (!customerRates.isEmpty()) {
      var rate = customerRates.getFirst();
      return new ResolvedOverageRate(rate.getHourlyRate(), rate.getCurrency());
    }

    // 2. Org-wide default (no member, no project, no customer)
    var defaultRates =
        billingRateRepository.findByFilters(null, null, null).stream()
            .filter(r -> r.getProjectId() == null && r.getCustomerId() == null)
            .filter(r -> r.getEffectiveFrom() != null && !r.getEffectiveFrom().isAfter(closeDate))
            .filter(r -> r.getEffectiveTo() == null || !r.getEffectiveTo().isBefore(closeDate))
            .toList();
    if (!defaultRates.isEmpty()) {
      var rate = defaultRates.getFirst();
      return new ResolvedOverageRate(rate.getHourlyRate(), rate.getCurrency());
    }

    return null;
  }

  private void recalculateInvoiceTotals(Invoice invoice) {
    var lines = invoiceLineRepository.findByInvoiceIdOrderBySortOrder(invoice.getId());
    BigDecimal subtotal =
        lines.stream().map(InvoiceLine::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    invoice.recalculateTotals(subtotal);
  }

  private void notifyAdminsAndOwners(String type, String title, UUID agreementId) {
    var adminsAndOwners = memberRepository.findByOrgRoleIn(List.of("admin", "owner"));
    for (var member : adminsAndOwners) {
      notificationService.createNotification(
          member.getId(), type, title, null, "RETAINER_AGREEMENT", agreementId, null);
    }
  }

  @Transactional(readOnly = true)
  public Page<RetainerPeriod> listPeriods(UUID agreementId, Pageable pageable) {
    agreementRepository
        .findById(agreementId)
        .orElseThrow(() -> new ResourceNotFoundException("RetainerAgreement", agreementId));
    return periodRepository.findByAgreementIdOrderByPeriodStartDesc(agreementId, pageable);
  }

  @Transactional(readOnly = true)
  public RetainerPeriod getCurrentPeriod(UUID agreementId) {
    agreementRepository
        .findById(agreementId)
        .orElseThrow(() -> new ResourceNotFoundException("RetainerAgreement", agreementId));
    return periodRepository
        .findByAgreementIdAndStatus(agreementId, PeriodStatus.OPEN)
        .orElseThrow(
            () ->
                new ResourceNotFoundException("No open period for RetainerAgreement", agreementId));
  }

  @Transactional(readOnly = true)
  public List<PeriodReadyToCloseView> findPeriodsReadyToClose() {
    return periodRepository.findPeriodsReadyToClose().stream()
        .map(
            period -> {
              var agreement = agreementRepository.findById(period.getAgreementId()).orElse(null);
              if (agreement == null) {
                log.warn(
                    "Dangling period {}: agreement {} not found",
                    period.getId(),
                    period.getAgreementId());
                return null;
              }
              var customer = customerRepository.findById(agreement.getCustomerId()).orElse(null);
              if (customer == null) {
                log.warn(
                    "Agreement {} references missing customer {}",
                    agreement.getId(),
                    agreement.getCustomerId());
              }
              String customerName = customer != null ? customer.getName() : "Unknown";
              return new PeriodReadyToCloseView(
                  period.getId(),
                  agreement.getId(),
                  agreement.getName(),
                  agreement.getCustomerId(),
                  customerName,
                  period.getPeriodEnd(),
                  period.getConsumedHours(),
                  period.getAllocatedHours());
            })
        .filter(v -> v != null)
        .toList();
  }
}
