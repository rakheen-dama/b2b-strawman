package io.b2mash.b2b.b2bstrawman.invoice;

import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.tax.TaxCalculationService;
import io.b2mash.b2b.b2bstrawman.tax.TaxRate;
import io.b2mash.b2b.b2bstrawman.tax.TaxRateRepository;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Handles per-line tax application, default tax application, and invoice total recalculation.
 * Extracted from InvoiceService as a focused collaborator.
 */
@Service
public class InvoiceTaxService {

  private final InvoiceLineRepository lineRepository;
  private final OrgSettingsRepository orgSettingsRepository;
  private final TaxCalculationService taxCalculationService;
  private final TaxRateRepository taxRateRepository;

  public InvoiceTaxService(
      InvoiceLineRepository lineRepository,
      OrgSettingsRepository orgSettingsRepository,
      TaxCalculationService taxCalculationService,
      TaxRateRepository taxRateRepository) {
    this.lineRepository = lineRepository;
    this.orgSettingsRepository = orgSettingsRepository;
    this.taxCalculationService = taxCalculationService;
    this.taxRateRepository = taxRateRepository;
  }

  /**
   * Applies a tax rate to a line item. If taxRateId is non-null, loads and validates that rate. If
   * taxRateId is null, auto-applies the org default tax rate (if one exists).
   */
  void applyTaxToLine(InvoiceLine line, UUID taxRateId) {
    TaxRate taxRate;
    if (taxRateId != null) {
      taxRate =
          taxRateRepository
              .findById(taxRateId)
              .filter(TaxRate::isActive)
              .orElseThrow(() -> new ResourceNotFoundException("TaxRate", taxRateId));
    } else {
      var defaultRate = taxRateRepository.findByIsDefaultTrue();
      if (defaultRate.isEmpty()) {
        return;
      }
      taxRate = defaultRate.get();
    }

    boolean taxInclusive =
        orgSettingsRepository.findForCurrentTenant().map(s -> s.isTaxInclusive()).orElse(false);
    BigDecimal calculatedTax =
        taxCalculationService.calculateLineTax(
            line.getAmount(), taxRate.getRate(), taxInclusive, taxRate.isExempt());
    line.applyTaxRate(taxRate, calculatedTax);
  }

  /**
   * Applies the org default tax rate to all lines of an invoice. Used during invoice generation
   * from time entries. Delegates to {@link #applyTaxToLine(InvoiceLine, UUID)} with null taxRateId
   * to trigger default rate lookup.
   */
  void applyDefaultTaxToLines(UUID invoiceId) {
    var lines = lineRepository.findByInvoiceIdOrderBySortOrder(invoiceId);
    for (InvoiceLine line : lines) {
      applyTaxToLine(line, null);
    }
    lineRepository.saveAll(lines);
  }

  void recalculateInvoiceTotals(Invoice invoice) {
    var lines = lineRepository.findByInvoiceIdOrderBySortOrder(invoice.getId());
    BigDecimal subtotal =
        lines.stream().map(InvoiceLine::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    boolean taxInclusive =
        orgSettingsRepository.findForCurrentTenant().map(s -> s.isTaxInclusive()).orElse(false);
    boolean hasPerLineTax = taxCalculationService.hasPerLineTax(lines);
    BigDecimal perLineTaxSum =
        lines.stream()
            .map(line -> line.getTaxAmount() != null ? line.getTaxAmount() : BigDecimal.ZERO)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    invoice.recalculateTotals(subtotal, hasPerLineTax, perLineTaxSum, taxInclusive);
  }
}
