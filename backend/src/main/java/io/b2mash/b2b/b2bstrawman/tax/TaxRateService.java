package io.b2mash.b2b.b2bstrawman.tax;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceConflictException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceLine;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceLineRepository;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceRepository;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceStatus;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettings;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.tax.dto.CreateTaxRateRequest;
import io.b2mash.b2b.b2bstrawman.tax.dto.TaxRateResponse;
import io.b2mash.b2b.b2bstrawman.tax.dto.UpdateTaxRateRequest;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaxRateService {

  private static final Logger log = LoggerFactory.getLogger(TaxRateService.class);

  private final TaxRateRepository taxRateRepository;
  private final AuditService auditService;
  private final InvoiceLineRepository invoiceLineRepository;
  private final InvoiceRepository invoiceRepository;
  private final TaxCalculationService taxCalculationService;
  private final OrgSettingsRepository orgSettingsRepository;

  public TaxRateService(
      TaxRateRepository taxRateRepository,
      AuditService auditService,
      InvoiceLineRepository invoiceLineRepository,
      InvoiceRepository invoiceRepository,
      TaxCalculationService taxCalculationService,
      OrgSettingsRepository orgSettingsRepository) {
    this.taxRateRepository = taxRateRepository;
    this.auditService = auditService;
    this.invoiceLineRepository = invoiceLineRepository;
    this.invoiceRepository = invoiceRepository;
    this.taxCalculationService = taxCalculationService;
    this.orgSettingsRepository = orgSettingsRepository;
  }

  @Transactional
  public TaxRateResponse createTaxRate(CreateTaxRateRequest request) {
    validateNameUnique(request.name(), null);
    validateExemptZeroConstraint(request.isExempt(), request.rate());

    Optional<UUID> clearedDefaultId = Optional.empty();
    if (request.isDefault()) {
      clearedDefaultId = clearExistingDefault(null);
    }

    var taxRate =
        new TaxRate(
            request.name(),
            request.rate(),
            request.isDefault(),
            request.isExempt(),
            request.sortOrder());
    taxRate = taxRateRepository.save(taxRate);

    log.info("Created tax rate {} name={}", taxRate.getId(), taxRate.getName());

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("tax_rate.created")
            .entityType("tax_rate")
            .entityId(taxRate.getId())
            .details(
                Map.of(
                    "name", taxRate.getName(),
                    "rate", taxRate.getRate().toString(),
                    "is_default", String.valueOf(taxRate.isDefault()),
                    "is_exempt", String.valueOf(taxRate.isExempt())))
            .build());

    if (clearedDefaultId.isPresent()) {
      final UUID finalId = taxRate.getId();
      auditService.log(
          AuditEventBuilder.builder()
              .eventType("tax_rate.default_changed")
              .entityType("tax_rate")
              .entityId(finalId)
              .details(
                  Map.of(
                      "new_default_id", finalId.toString(),
                      "old_default_id", clearedDefaultId.get().toString()))
              .build());
    }

    return TaxRateResponse.from(taxRate);
  }

  @Transactional
  public TaxRateResponse updateTaxRate(UUID id, UpdateTaxRateRequest request) {
    var taxRate =
        taxRateRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("TaxRate", id));

    validateNameUnique(request.name(), id);
    validateExemptZeroConstraint(request.isExempt(), request.rate());

    // Capture old values BEFORE update (needed for change detection)
    String oldName = taxRate.getName();
    BigDecimal oldRate = taxRate.getRate();
    boolean oldExempt = taxRate.isExempt();

    boolean wasDefault = taxRate.isDefault();
    Optional<UUID> clearedDefaultId = Optional.empty();
    if (request.isDefault() && !wasDefault) {
      clearedDefaultId = clearExistingDefault(id);
    }

    taxRate.update(
        request.name(),
        request.rate(),
        request.isDefault(),
        request.isExempt(),
        request.sortOrder(),
        request.active());
    taxRate = taxRateRepository.save(taxRate);

    // Batch recalculate DRAFT lines if rate/name/exempt changed
    boolean rateChanged = oldRate.compareTo(request.rate()) != 0;
    boolean nameChanged = !oldName.equals(request.name());
    boolean exemptChanged = oldExempt != request.isExempt();

    if (rateChanged || nameChanged || exemptChanged) {
      batchRecalculateDraftLines(taxRate);
    }

    log.info("Updated tax rate {} name={}", taxRate.getId(), taxRate.getName());

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("tax_rate.updated")
            .entityType("tax_rate")
            .entityId(taxRate.getId())
            .details(
                Map.of(
                    "name", taxRate.getName(),
                    "rate", taxRate.getRate().toString(),
                    "is_default", String.valueOf(taxRate.isDefault()),
                    "is_exempt", String.valueOf(taxRate.isExempt()),
                    "active", String.valueOf(taxRate.isActive())))
            .build());

    if (clearedDefaultId.isPresent()) {
      final UUID finalId = taxRate.getId();
      auditService.log(
          AuditEventBuilder.builder()
              .eventType("tax_rate.default_changed")
              .entityType("tax_rate")
              .entityId(finalId)
              .details(
                  Map.of(
                      "new_default_id", finalId.toString(),
                      "old_default_id", clearedDefaultId.get().toString()))
              .build());
    }

    return TaxRateResponse.from(taxRate);
  }

  @Transactional
  public void deactivateTaxRate(UUID id) {
    var taxRate =
        taxRateRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("TaxRate", id));

    long draftLineCount = taxRateRepository.countDraftInvoiceLinesByTaxRateId(id);
    if (draftLineCount > 0) {
      throw new InvalidStateException(
          "Cannot deactivate tax rate",
          "Tax rate is referenced by " + draftLineCount + " draft invoice line(s)");
    }

    taxRate.deactivate();
    taxRateRepository.save(taxRate);

    log.info("Deactivated tax rate {} name={}", taxRate.getId(), taxRate.getName());

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("tax_rate.deactivated")
            .entityType("tax_rate")
            .entityId(taxRate.getId())
            .details(Map.of("name", taxRate.getName()))
            .build());
  }

  @Transactional(readOnly = true)
  public List<TaxRateResponse> listTaxRates(boolean includeInactive) {
    var rates =
        includeInactive
            ? taxRateRepository.findAllByOrderBySortOrder()
            : taxRateRepository.findByActiveOrderBySortOrder(true);
    return rates.stream().map(TaxRateResponse::from).toList();
  }

  /**
   * Returns the default tax rate if one is configured. Optional.empty() means "no default
   * configured" — distinct from "not found".
   */
  @Transactional(readOnly = true)
  public Optional<TaxRateResponse> getDefaultTaxRate() {
    return taxRateRepository.findByIsDefaultTrue().map(TaxRateResponse::from);
  }

  // --- Private helpers ---

  /** Validates name uniqueness. Pass null excludeId on create; pass the rate's own ID on update. */
  private void validateNameUnique(String name, UUID excludeId) {
    boolean conflict =
        excludeId == null
            ? taxRateRepository.existsByName(name)
            : taxRateRepository.existsByNameAndIdNot(name, excludeId);
    if (conflict) {
      throw new ResourceConflictException(
          "Tax rate name already exists", "A tax rate with name '" + name + "' already exists");
    }
  }

  /** Validates that exempt rates have a rate of exactly 0.00. */
  private void validateExemptZeroConstraint(boolean isExempt, BigDecimal rate) {
    if (isExempt && rate.compareTo(BigDecimal.ZERO) != 0) {
      throw new InvalidStateException(
          "Invalid tax rate", "Exempt tax rates must have a rate of 0.00");
    }
  }

  /**
   * Batch recalculates all DRAFT invoice lines referencing the given tax rate. Updates tax
   * snapshots, recalculates tax amounts, and recalculates parent invoice totals.
   */
  private void batchRecalculateDraftLines(TaxRate taxRate) {
    var affectedLines =
        invoiceLineRepository.findByTaxRateIdAndInvoice_Status(
            taxRate.getId(), InvoiceStatus.DRAFT);

    if (affectedLines.isEmpty()) {
      return;
    }

    boolean taxInclusive =
        orgSettingsRepository.findForCurrentTenant().map(OrgSettings::isTaxInclusive).orElse(false);

    Set<UUID> affectedInvoiceIds = new HashSet<>();
    for (InvoiceLine line : affectedLines) {
      BigDecimal newTaxAmount =
          taxCalculationService.calculateLineTax(
              line.getAmount(), taxRate.getRate(), taxInclusive, taxRate.isExempt());
      line.applyTaxRate(taxRate, newTaxAmount);
      affectedInvoiceIds.add(line.getInvoiceId());
    }
    invoiceLineRepository.saveAll(affectedLines);

    // Recalculate totals on each affected invoice
    for (UUID invoiceId : affectedInvoiceIds) {
      var invoice = invoiceRepository.findById(invoiceId).orElse(null);
      if (invoice != null) {
        var allLines = invoiceLineRepository.findByInvoiceIdOrderBySortOrder(invoiceId);
        BigDecimal subtotal =
            allLines.stream().map(InvoiceLine::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        boolean hasPerLineTax = taxCalculationService.hasPerLineTax(allLines);
        BigDecimal perLineTaxSum =
            allLines.stream()
                .map(l -> l.getTaxAmount() != null ? l.getTaxAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        invoice.recalculateTotals(subtotal, hasPerLineTax, perLineTaxSum, taxInclusive);
        invoiceRepository.save(invoice);
      }
    }

    log.info(
        "Batch recalculated {} DRAFT lines across {} invoices for tax rate {}",
        affectedLines.size(),
        affectedInvoiceIds.size(),
        taxRate.getId());
  }

  /**
   * Clears the existing default tax rate (if any). Returns the cleared rate's ID if a default was
   * cleared, or empty if no default existed. Skips clearing if the existing default is the same
   * rate as excludeId (update case).
   */
  private Optional<UUID> clearExistingDefault(UUID excludeId) {
    return taxRateRepository
        .findByIsDefaultTrue()
        .filter(existing -> !existing.getId().equals(excludeId))
        .map(
            existing -> {
              UUID clearedId = existing.getId();
              existing.update(
                  existing.getName(),
                  existing.getRate(),
                  false, // clear isDefault
                  existing.isExempt(),
                  existing.getSortOrder(),
                  existing.isActive());
              taxRateRepository.saveAndFlush(existing);
              return clearedId;
            });
  }
}
