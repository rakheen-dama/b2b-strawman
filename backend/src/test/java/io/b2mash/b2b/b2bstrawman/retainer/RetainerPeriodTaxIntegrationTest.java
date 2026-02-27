package io.b2mash.b2b.b2bstrawman.retainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.invoice.Invoice;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceLine;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceLineRepository;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceRepository;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceService;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.retainer.dto.CreateRetainerRequest;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettings;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.tax.TaxRate;
import io.b2mash.b2b.b2bstrawman.tax.TaxRateRepository;
import io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RetainerPeriodTaxIntegrationTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_retainer_tax_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private RetainerPeriodService retainerPeriodService;
  @Autowired private RetainerAgreementService retainerAgreementService;
  @Autowired private RetainerAgreementRepository retainerAgreementRepository;
  @Autowired private RetainerPeriodRepository retainerPeriodRepository;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private OrgSettingsRepository orgSettingsRepository;
  @Autowired private InvoiceRepository invoiceRepository;
  @Autowired private InvoiceLineRepository invoiceLineRepository;
  @Autowired private InvoiceService invoiceService;
  @Autowired private TaxRateRepository taxRateRepository;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private TransactionTemplate transactionTemplate;

  private String tenantSchema;
  private UUID memberId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Retainer Tax Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");

    memberId =
        UUID.fromString(
            syncMember(
                ORG_ID, "user_ret_tax_owner", "ret_tax_owner@test.com", "RetTax Owner", "owner"));

    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
  }

  @BeforeEach
  void cleanBefore() {
    cleanAll();
  }

  @AfterEach
  void cleanAfter() {
    cleanAll();
  }

  private void cleanAll() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  // Order matters: invoice_lines FK -> retainer_periods, invoices
                  invoiceLineRepository.deleteAll();
                  retainerPeriodRepository.deleteAll();
                  retainerAgreementRepository.deleteAll();
                  invoiceRepository.deleteAll();
                  taxRateRepository.deleteAll();
                }));
  }

  // ======================================================================
  // 183.13 — Retainer tax integration tests
  // ======================================================================

  @Test
  void closePeriod_applies_default_tax_to_lines() {
    var customerId = createCustomer("TaxApply Corp", "taxapply@test.com");
    ensureOrgSettings("ZAR", false);
    createDefaultTaxRate("VAT", new BigDecimal("15.00"), false);

    UUID agreementId =
        createFixedFeeRetainer(customerId, new BigDecimal("5000.00"), pastStart(), null);
    UUID openPeriodId = getOpenPeriodId(agreementId);

    var result = new AtomicReference<RetainerPeriodService.PeriodCloseResult>();
    runInTenant(() -> result.set(retainerPeriodService.closePeriod(agreementId, memberId)));

    var invoice = result.get().generatedInvoice();
    var lines = result.get().invoiceLines();

    // All lines should have tax applied
    assertThat(lines)
        .allSatisfy(
            line -> {
              assertThat(line.getTaxRateId()).isNotNull();
              assertThat(line.getTaxRateName()).isEqualTo("VAT");
              assertThat(line.getTaxRatePercent()).isEqualByComparingTo(new BigDecimal("15.00"));
              assertThat(line.isTaxExempt()).isFalse();
              assertThat(line.getTaxAmount()).isNotNull();
            });

    // Base fee R5000 at 15% = R750 tax
    var baseLine = lines.getFirst();
    assertThat(baseLine.getAmount()).isEqualByComparingTo(new BigDecimal("5000.00"));
    assertThat(baseLine.getTaxAmount()).isEqualByComparingTo(new BigDecimal("750.00"));

    // Invoice total = subtotal + tax = 5000 + 750 = 5750
    runInTenant(
        () -> {
          var freshInvoice = invoiceRepository.findById(invoice.getId()).orElseThrow();
          assertThat(freshInvoice.getSubtotal()).isEqualByComparingTo(new BigDecimal("5000.00"));
          assertThat(freshInvoice.getTaxAmount()).isEqualByComparingTo(new BigDecimal("750.00"));
          assertThat(freshInvoice.getTotal()).isEqualByComparingTo(new BigDecimal("5750.00"));
        });
  }

  @Test
  void closePeriod_no_default_no_tax() {
    var customerId = createCustomer("NoTax Corp", "notax@test.com");
    ensureOrgSettings("ZAR", false);
    // No tax rates configured

    UUID agreementId =
        createFixedFeeRetainer(customerId, new BigDecimal("5000.00"), pastStart(), null);

    var result = new AtomicReference<RetainerPeriodService.PeriodCloseResult>();
    runInTenant(() -> result.set(retainerPeriodService.closePeriod(agreementId, memberId)));

    var lines = result.get().invoiceLines();
    assertThat(lines)
        .allSatisfy(
            line -> {
              assertThat(line.getTaxRateId()).isNull();
              assertThat(line.getTaxRateName()).isNull();
              assertThat(line.getTaxRatePercent()).isNull();
              assertThat(line.getTaxAmount()).isNull();
            });

    // total = subtotal (no tax)
    var invoice = result.get().generatedInvoice();
    runInTenant(
        () -> {
          var freshInvoice = invoiceRepository.findById(invoice.getId()).orElseThrow();
          assertThat(freshInvoice.getSubtotal()).isEqualByComparingTo(new BigDecimal("5000.00"));
          assertThat(freshInvoice.getTotal()).isEqualByComparingTo(freshInvoice.getSubtotal());
        });
  }

  @Test
  void closePeriod_tax_inclusive_total_calculation() {
    var customerId = createCustomer("TaxInclusive Corp", "taxincl@test.com");
    ensureOrgSettings("ZAR", true); // taxInclusive = true
    createDefaultTaxRate("VAT", new BigDecimal("15.00"), false);

    UUID agreementId =
        createFixedFeeRetainer(customerId, new BigDecimal("5000.00"), pastStart(), null);

    var result = new AtomicReference<RetainerPeriodService.PeriodCloseResult>();
    runInTenant(() -> result.set(retainerPeriodService.closePeriod(agreementId, memberId)));

    var baseLine = result.get().invoiceLines().getFirst();
    // Tax inclusive: tax = 5000 - (5000 / 1.15) = 5000 - 4347.83 = 652.17
    assertThat(baseLine.getTaxAmount()).isEqualByComparingTo(new BigDecimal("652.17"));

    // Tax inclusive: total = subtotal (not subtotal + tax)
    var invoice = result.get().generatedInvoice();
    runInTenant(
        () -> {
          var freshInvoice = invoiceRepository.findById(invoice.getId()).orElseThrow();
          assertThat(freshInvoice.getTotal()).isEqualByComparingTo(freshInvoice.getSubtotal());
        });
  }

  @Test
  void closePeriod_exempt_rate_zero_tax() {
    var customerId = createCustomer("Exempt Corp", "exempt@test.com");
    ensureOrgSettings("ZAR", false);
    createDefaultTaxRate("Zero Rated", BigDecimal.ZERO, true); // exempt

    UUID agreementId =
        createFixedFeeRetainer(customerId, new BigDecimal("5000.00"), pastStart(), null);

    var result = new AtomicReference<RetainerPeriodService.PeriodCloseResult>();
    runInTenant(() -> result.set(retainerPeriodService.closePeriod(agreementId, memberId)));

    var baseLine = result.get().invoiceLines().getFirst();
    assertThat(baseLine.getTaxRateId()).isNotNull();
    assertThat(baseLine.isTaxExempt()).isTrue();
    assertThat(baseLine.getTaxAmount()).isEqualByComparingTo(BigDecimal.ZERO);

    // total = subtotal (exempt tax = 0)
    var invoice = result.get().generatedInvoice();
    runInTenant(
        () -> {
          var freshInvoice = invoiceRepository.findById(invoice.getId()).orElseThrow();
          assertThat(freshInvoice.getTotal()).isEqualByComparingTo(freshInvoice.getSubtotal());
        });
  }

  // ======================================================================
  // 183.15 — Backward compatibility tests
  // ======================================================================

  @Test
  void legacy_invoice_no_per_line_tax_displays_flat_taxAmount() {
    var customerId = createCustomer("Legacy Corp", "legacy@test.com");
    ensureOrgSettings("ZAR", false);
    // No tax rates — legacy mode

    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var invoice =
                      new Invoice(
                          customerId,
                          "ZAR",
                          "Legacy Corp",
                          "legacy@test.com",
                          null,
                          "Retainer Tax Test Org",
                          memberId);
                  invoice = invoiceRepository.save(invoice);
                  var line =
                      new InvoiceLine(
                          invoice.getId(),
                          null,
                          null,
                          "Service fee",
                          BigDecimal.ONE,
                          new BigDecimal("1000.00"),
                          0);
                  invoiceLineRepository.save(line);

                  // Manually set taxAmount (legacy mode)
                  invoice.updateDraft(null, null, null, new BigDecimal("150.00"));
                  // Recalculate subtotal
                  invoice.recalculateTotals(
                      new BigDecimal("1000.00"), false, BigDecimal.ZERO, false);
                  // Manual taxAmount should survive (since no per-line tax)
                  invoice.updateDraft(null, null, null, new BigDecimal("150.00"));
                  invoiceRepository.save(invoice);

                  var loaded = invoiceRepository.findById(invoice.getId()).orElseThrow();
                  assertThat(loaded.getTaxAmount()).isEqualByComparingTo(new BigDecimal("150.00"));
                  assertThat(loaded.getTotal()).isEqualByComparingTo(new BigDecimal("1150.00"));
                }));
  }

  @Test
  void legacy_invoice_total_equals_subtotal_plus_taxAmount() {
    var customerId = createCustomer("LegacyTotal Corp", "legacytotal@test.com");
    ensureOrgSettings("ZAR", false);

    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var invoice =
                      new Invoice(
                          customerId,
                          "ZAR",
                          "LegacyTotal Corp",
                          "legacytotal@test.com",
                          null,
                          "Retainer Tax Test Org",
                          memberId);
                  invoice = invoiceRepository.save(invoice);
                  var line =
                      new InvoiceLine(
                          invoice.getId(),
                          null,
                          null,
                          "Consulting",
                          BigDecimal.ONE,
                          new BigDecimal("2000.00"),
                          0);
                  invoiceLineRepository.save(line);

                  // Set manual tax
                  invoice.updateDraft(null, null, null, new BigDecimal("300.00"));
                  invoiceRepository.save(invoice);

                  var loaded = invoiceRepository.findById(invoice.getId()).orElseThrow();
                  // subtotal set by updateDraft is still 0 since we didn't recalculate
                  // Use recalculateTotals to properly set subtotal
                  loaded.recalculateTotals(
                      new BigDecimal("2000.00"), false, BigDecimal.ZERO, false);
                  loaded.updateDraft(null, null, null, new BigDecimal("300.00"));
                  invoiceRepository.save(loaded);

                  var fresh = invoiceRepository.findById(loaded.getId()).orElseThrow();
                  assertThat(fresh.getSubtotal()).isEqualByComparingTo(new BigDecimal("2000.00"));
                  assertThat(fresh.getTaxAmount()).isEqualByComparingTo(new BigDecimal("300.00"));
                  assertThat(fresh.getTotal()).isEqualByComparingTo(new BigDecimal("2300.00"));
                }));
  }

  @Test
  void legacy_invoice_updateDraft_allows_manual_taxAmount() {
    var customerId = createCustomer("ManualTax Corp", "manualtax@test.com");
    ensureOrgSettings("ZAR", false);

    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var invoice =
                      new Invoice(
                          customerId,
                          "ZAR",
                          "ManualTax Corp",
                          "manualtax@test.com",
                          null,
                          "Retainer Tax Test Org",
                          memberId);
                  invoice = invoiceRepository.save(invoice);

                  // No lines with tax — legacy mode
                  var line =
                      new InvoiceLine(
                          invoice.getId(),
                          null,
                          null,
                          "Work done",
                          BigDecimal.ONE,
                          new BigDecimal("3000.00"),
                          0);
                  invoiceLineRepository.save(line);

                  // Update with manual taxAmount — should succeed
                  invoice.recalculateTotals(
                      new BigDecimal("3000.00"), false, BigDecimal.ZERO, false);
                  invoice.updateDraft(
                      LocalDate.now().plusDays(30), "Net 30", "NET30", new BigDecimal("450.00"));
                  invoiceRepository.save(invoice);

                  var loaded = invoiceRepository.findById(invoice.getId()).orElseThrow();
                  assertThat(loaded.getTaxAmount()).isEqualByComparingTo(new BigDecimal("450.00"));
                  assertThat(loaded.getTotal()).isEqualByComparingTo(new BigDecimal("3450.00"));
                  assertThat(loaded.getNotes()).isEqualTo("Net 30");
                }));
  }

  // ======================================================================
  // 183.16 — Delete line item recalculation tests
  // ======================================================================

  @Test
  void deleteLineItem_with_tax_recalculates_invoice_totals() {
    var customerId = createCustomer("DeleteLine Corp", "deleteline@test.com");
    ensureOrgSettings("ZAR", false);
    createDefaultTaxRate("VAT", new BigDecimal("15.00"), false);

    UUID agreementId =
        createFixedFeeRetainer(customerId, new BigDecimal("5000.00"), pastStart(), null);

    var result = new AtomicReference<RetainerPeriodService.PeriodCloseResult>();
    runInTenant(() -> result.set(retainerPeriodService.closePeriod(agreementId, memberId)));

    var invoice = result.get().generatedInvoice();

    // Add a second line manually
    var secondLineId = new AtomicReference<UUID>();
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var line =
                      new InvoiceLine(
                          invoice.getId(),
                          null,
                          null,
                          "Additional Service",
                          BigDecimal.ONE,
                          new BigDecimal("2000.00"),
                          2);
                  // Apply tax to this line too
                  var taxRate = taxRateRepository.findByIsDefaultTrue().orElseThrow();
                  BigDecimal tax =
                      new BigDecimal("2000.00")
                          .multiply(new BigDecimal("15.00"))
                          .divide(new BigDecimal("100"), 2, java.math.RoundingMode.HALF_UP);
                  line.applyTaxRate(taxRate, tax);
                  line = invoiceLineRepository.save(line);
                  secondLineId.set(line.getId());

                  // Recalculate totals
                  var inv = invoiceRepository.findById(invoice.getId()).orElseThrow();
                  var allLines =
                      invoiceLineRepository.findByInvoiceIdOrderBySortOrder(invoice.getId());
                  BigDecimal subtotal =
                      allLines.stream()
                          .map(InvoiceLine::getAmount)
                          .reduce(BigDecimal.ZERO, BigDecimal::add);
                  BigDecimal taxSum =
                      allLines.stream()
                          .map(l -> l.getTaxAmount() != null ? l.getTaxAmount() : BigDecimal.ZERO)
                          .reduce(BigDecimal.ZERO, BigDecimal::add);
                  inv.recalculateTotals(subtotal, true, taxSum, false);
                  invoiceRepository.save(inv);
                }));

    // Now delete the second line
    runInTenant(() -> invoiceService.deleteLineItem(invoice.getId(), secondLineId.get()));

    // Verify invoice totals recalculated to reflect only the base fee line
    runInTenant(
        () -> {
          var freshInvoice = invoiceRepository.findById(invoice.getId()).orElseThrow();
          // Should be back to just base fee: 5000 subtotal + 750 tax = 5750
          assertThat(freshInvoice.getSubtotal()).isEqualByComparingTo(new BigDecimal("5000.00"));
          assertThat(freshInvoice.getTaxAmount()).isEqualByComparingTo(new BigDecimal("750.00"));
          assertThat(freshInvoice.getTotal()).isEqualByComparingTo(new BigDecimal("5750.00"));
        });
  }

  @Test
  void deleteLineItem_last_taxed_line_reverts_to_manual_mode() {
    var customerId = createCustomer("LastLine Corp", "lastline@test.com");
    ensureOrgSettings("ZAR", false);

    // Create invoice directly (no retainer) with one taxed line
    var invoiceId = new AtomicReference<UUID>();
    var lineId = new AtomicReference<UUID>();
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var taxRate = new TaxRate("VAT", new BigDecimal("15.00"), true, false, 0);
                  taxRate = taxRateRepository.save(taxRate);

                  var invoice =
                      new Invoice(
                          customerId,
                          "ZAR",
                          "LastLine Corp",
                          "lastline@test.com",
                          null,
                          "Retainer Tax Test Org",
                          memberId);
                  invoice = invoiceRepository.save(invoice);
                  invoiceId.set(invoice.getId());

                  var line =
                      new InvoiceLine(
                          invoice.getId(),
                          null,
                          null,
                          "Taxed service",
                          BigDecimal.ONE,
                          new BigDecimal("1000.00"),
                          0);
                  line.applyTaxRate(taxRate, new BigDecimal("150.00"));
                  line = invoiceLineRepository.save(line);
                  lineId.set(line.getId());

                  // Recalculate totals
                  invoice.recalculateTotals(
                      new BigDecimal("1000.00"), true, new BigDecimal("150.00"), false);
                  invoiceRepository.save(invoice);
                }));

    // Delete the only taxed line
    runInTenant(() -> invoiceService.deleteLineItem(invoiceId.get(), lineId.get()));

    // After deleting the last taxed line, no per-line tax exists.
    // The invoice reverts to manual taxAmount mode — the old taxAmount persists
    // (backward compatibility: when hasPerLineTax=false, taxAmount is not overwritten).
    runInTenant(
        () -> {
          var freshInvoice = invoiceRepository.findById(invoiceId.get()).orElseThrow();
          var remainingLines =
              invoiceLineRepository.findByInvoiceIdOrderBySortOrder(invoiceId.get());
          assertThat(remainingLines).isEmpty();
          assertThat(freshInvoice.getSubtotal()).isEqualByComparingTo(BigDecimal.ZERO);
          // No per-line tax lines remain, so manual taxAmount mode resumes
          // The old taxAmount (150.00) persists, total = subtotal + taxAmount
          assertThat(freshInvoice.getTaxAmount()).isEqualByComparingTo(new BigDecimal("150.00"));
          assertThat(freshInvoice.getTotal()).isEqualByComparingTo(new BigDecimal("150.00"));
        });
  }

  // ======================================================================
  // Helpers
  // ======================================================================

  private void runInTenant(Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(action);
  }

  private UUID createCustomer(String name, String email) {
    var ref = new AtomicReference<UUID>();
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var customer = TestCustomerFactory.createActiveCustomer(name, email, memberId);
                  customer = customerRepository.save(customer);
                  ref.set(customer.getId());
                }));
    return ref.get();
  }

  private UUID createFixedFeeRetainer(
      UUID customerId, BigDecimal periodFee, LocalDate startDate, LocalDate endDate) {
    var ref = new AtomicReference<UUID>();
    runInTenant(
        () -> {
          var request =
              new CreateRetainerRequest(
                  customerId,
                  null,
                  "Test Fixed Fee Retainer",
                  RetainerType.FIXED_FEE,
                  RetainerFrequency.MONTHLY,
                  startDate,
                  endDate,
                  null,
                  periodFee,
                  RolloverPolicy.FORFEIT,
                  null,
                  null);
          var response = retainerAgreementService.createRetainer(request, memberId);
          ref.set(response.id());
        });
    return ref.get();
  }

  private UUID getOpenPeriodId(UUID agreementId) {
    var ref = new AtomicReference<UUID>();
    runInTenant(
        () -> {
          var period = retainerPeriodService.getCurrentPeriod(agreementId);
          ref.set(period.getId());
        });
    return ref.get();
  }

  private void createDefaultTaxRate(String name, BigDecimal rate, boolean exempt) {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var taxRate = new TaxRate(name, rate, true, exempt, 0);
                  taxRateRepository.save(taxRate);
                }));
  }

  private void ensureOrgSettings(String currency, boolean taxInclusive) {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var existing = orgSettingsRepository.findForCurrentTenant();
                  OrgSettings settings;
                  if (existing.isPresent()) {
                    settings = existing.get();
                    settings.updateCurrency(currency);
                  } else {
                    settings = new OrgSettings(currency);
                  }
                  // Use reflection to set taxInclusive (no setter available)
                  try {
                    var field = OrgSettings.class.getDeclaredField("taxInclusive");
                    field.setAccessible(true);
                    field.set(settings, taxInclusive);
                  } catch (ReflectiveOperationException e) {
                    throw new RuntimeException(e);
                  }
                  orgSettingsRepository.save(settings);
                }));
  }

  private LocalDate pastStart() {
    return LocalDate.now().minusMonths(2);
  }

  private String syncMember(
      String orgId, String clerkUserId, String email, String name, String orgRole)
      throws Exception {
    var result =
        mockMvc
            .perform(
                post("/internal/members/sync")
                    .header("X-API-KEY", API_KEY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"clerkOrgId":"%s","clerkUserId":"%s","email":"%s","name":"%s","avatarUrl":null,"orgRole":"%s"}
                        """
                            .formatted(orgId, clerkUserId, email, name, orgRole)))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.memberId");
  }
}
