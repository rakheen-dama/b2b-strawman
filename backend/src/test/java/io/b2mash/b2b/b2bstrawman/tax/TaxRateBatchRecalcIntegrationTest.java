package io.b2mash.b2b.b2bstrawman.tax;

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
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettings;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.tax.dto.UpdateTaxRateRequest;
import io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory;
import java.math.BigDecimal;
import java.time.Instant;
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
class TaxRateBatchRecalcIntegrationTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_batch_recalc_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TaxRateService taxRateService;
  @Autowired private TaxRateRepository taxRateRepository;
  @Autowired private InvoiceRepository invoiceRepository;
  @Autowired private InvoiceLineRepository invoiceLineRepository;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private OrgSettingsRepository orgSettingsRepository;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private TransactionTemplate transactionTemplate;

  private String tenantSchema;
  private UUID memberId;
  private UUID customerId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Batch Recalc Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");

    memberId =
        UUID.fromString(
            syncMember(
                ORG_ID,
                "user_batch_recalc_owner",
                "batch_recalc_owner@test.com",
                "BatchRecalc Owner",
                "owner"));

    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

    customerId = createCustomer("BatchRecalc Corp", "batchrecalc@test.com");
    ensureOrgSettings("ZAR", false);
  }

  @BeforeEach
  void cleanBefore() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  invoiceLineRepository.deleteAll();
                  invoiceRepository.deleteAll();
                  taxRateRepository.deleteAll();
                }));
  }

  @AfterEach
  void cleanAfter() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  invoiceLineRepository.deleteAll();
                  invoiceRepository.deleteAll();
                  taxRateRepository.deleteAll();
                }));
  }

  // ======================================================================
  // 183.14 — Batch recalculation integration tests
  // ======================================================================

  @Test
  void updateTaxRate_recalculates_draft_lines() {
    var taxRateId = new AtomicReference<UUID>();
    var lineId = new AtomicReference<UUID>();

    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var taxRate = new TaxRate("VAT", new BigDecimal("15.00"), true, false, 0);
                  taxRate = taxRateRepository.save(taxRate);
                  taxRateId.set(taxRate.getId());

                  var invoice = createDraftInvoice();
                  var line =
                      new InvoiceLine(
                          invoice.getId(),
                          null,
                          null,
                          "Service",
                          BigDecimal.ONE,
                          new BigDecimal("1000.00"),
                          0);
                  line.applyTaxRate(taxRate, new BigDecimal("150.00"));
                  line = invoiceLineRepository.save(line);
                  lineId.set(line.getId());

                  invoice.recalculateTotals(
                      new BigDecimal("1000.00"), true, new BigDecimal("150.00"), false);
                  invoiceRepository.save(invoice);
                }));

    // Update rate from 15% to 20%
    runInTenant(
        () ->
            taxRateService.updateTaxRate(
                taxRateId.get(),
                new UpdateTaxRateRequest("VAT", new BigDecimal("20.00"), true, false, true, 0)));

    // Verify line tax recalculated: 1000 * 20% = 200
    runInTenant(
        () -> {
          var line = invoiceLineRepository.findById(lineId.get()).orElseThrow();
          assertThat(line.getTaxAmount()).isEqualByComparingTo(new BigDecimal("200.00"));
          assertThat(line.getTaxRatePercent()).isEqualByComparingTo(new BigDecimal("20.00"));
        });
  }

  @Test
  void updateTaxRate_does_not_touch_approved_lines() {
    var taxRateId = new AtomicReference<UUID>();
    var approvedLineId = new AtomicReference<UUID>();

    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var taxRate = new TaxRate("VAT", new BigDecimal("15.00"), true, false, 0);
                  taxRate = taxRateRepository.save(taxRate);
                  taxRateId.set(taxRate.getId());

                  // Create APPROVED invoice with a taxed line
                  var invoice = createDraftInvoice();
                  var line =
                      new InvoiceLine(
                          invoice.getId(),
                          null,
                          null,
                          "Approved Service",
                          BigDecimal.ONE,
                          new BigDecimal("1000.00"),
                          0);
                  line.applyTaxRate(taxRate, new BigDecimal("150.00"));
                  line = invoiceLineRepository.save(line);
                  approvedLineId.set(line.getId());

                  invoice.recalculateTotals(
                      new BigDecimal("1000.00"), true, new BigDecimal("150.00"), false);
                  invoice.approve("INV-001", memberId);
                  invoiceRepository.save(invoice);
                }));

    // Update rate from 15% to 20%
    runInTenant(
        () ->
            taxRateService.updateTaxRate(
                taxRateId.get(),
                new UpdateTaxRateRequest("VAT", new BigDecimal("20.00"), true, false, true, 0)));

    // Verify APPROVED line is NOT touched — still 150.00
    runInTenant(
        () -> {
          var line = invoiceLineRepository.findById(approvedLineId.get()).orElseThrow();
          assertThat(line.getTaxAmount()).isEqualByComparingTo(new BigDecimal("150.00"));
          assertThat(line.getTaxRatePercent()).isEqualByComparingTo(new BigDecimal("15.00"));
        });
  }

  @Test
  void updateTaxRate_updates_parent_invoice_totals() {
    var taxRateId = new AtomicReference<UUID>();
    var invoiceId = new AtomicReference<UUID>();

    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var taxRate = new TaxRate("VAT", new BigDecimal("15.00"), true, false, 0);
                  taxRate = taxRateRepository.save(taxRate);
                  taxRateId.set(taxRate.getId());

                  var invoice = createDraftInvoice();
                  invoiceId.set(invoice.getId());

                  var line =
                      new InvoiceLine(
                          invoice.getId(),
                          null,
                          null,
                          "Service",
                          BigDecimal.ONE,
                          new BigDecimal("1000.00"),
                          0);
                  line.applyTaxRate(taxRate, new BigDecimal("150.00"));
                  invoiceLineRepository.save(line);

                  invoice.recalculateTotals(
                      new BigDecimal("1000.00"), true, new BigDecimal("150.00"), false);
                  invoiceRepository.save(invoice);
                }));

    // Update rate from 15% to 20%
    runInTenant(
        () ->
            taxRateService.updateTaxRate(
                taxRateId.get(),
                new UpdateTaxRateRequest("VAT", new BigDecimal("20.00"), true, false, true, 0)));

    // Verify invoice totals updated: subtotal=1000, tax=200, total=1200
    runInTenant(
        () -> {
          var invoice = invoiceRepository.findById(invoiceId.get()).orElseThrow();
          assertThat(invoice.getSubtotal()).isEqualByComparingTo(new BigDecimal("1000.00"));
          assertThat(invoice.getTaxAmount()).isEqualByComparingTo(new BigDecimal("200.00"));
          assertThat(invoice.getTotal()).isEqualByComparingTo(new BigDecimal("1200.00"));
        });
  }

  @Test
  void updateTaxRate_name_change_refreshes_snapshot() {
    var taxRateId = new AtomicReference<UUID>();
    var lineId = new AtomicReference<UUID>();

    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var taxRate = new TaxRate("VAT", new BigDecimal("15.00"), true, false, 0);
                  taxRate = taxRateRepository.save(taxRate);
                  taxRateId.set(taxRate.getId());

                  var invoice = createDraftInvoice();
                  var line =
                      new InvoiceLine(
                          invoice.getId(),
                          null,
                          null,
                          "Service",
                          BigDecimal.ONE,
                          new BigDecimal("1000.00"),
                          0);
                  line.applyTaxRate(taxRate, new BigDecimal("150.00"));
                  line = invoiceLineRepository.save(line);
                  lineId.set(line.getId());

                  invoice.recalculateTotals(
                      new BigDecimal("1000.00"), true, new BigDecimal("150.00"), false);
                  invoiceRepository.save(invoice);
                }));

    // Update name only (rate stays at 15%)
    runInTenant(
        () ->
            taxRateService.updateTaxRate(
                taxRateId.get(),
                new UpdateTaxRateRequest(
                    "Value Added Tax", new BigDecimal("15.00"), true, false, true, 0)));

    // Verify line snapshot updated
    runInTenant(
        () -> {
          var line = invoiceLineRepository.findById(lineId.get()).orElseThrow();
          assertThat(line.getTaxRateName()).isEqualTo("Value Added Tax");
          // Tax amount should remain the same since rate didn't change
          assertThat(line.getTaxAmount()).isEqualByComparingTo(new BigDecimal("150.00"));
        });
  }

  @Test
  void updateTaxRate_no_change_no_recalculation() {
    var taxRateId = new AtomicReference<UUID>();
    var lineId = new AtomicReference<UUID>();
    var originalUpdatedAt = new AtomicReference<Instant>();

    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var taxRate = new TaxRate("VAT", new BigDecimal("15.00"), true, false, 0);
                  taxRate = taxRateRepository.save(taxRate);
                  taxRateId.set(taxRate.getId());

                  var invoice = createDraftInvoice();
                  var line =
                      new InvoiceLine(
                          invoice.getId(),
                          null,
                          null,
                          "Service",
                          BigDecimal.ONE,
                          new BigDecimal("1000.00"),
                          0);
                  line.applyTaxRate(taxRate, new BigDecimal("150.00"));
                  line = invoiceLineRepository.save(line);
                  lineId.set(line.getId());
                  originalUpdatedAt.set(line.getUpdatedAt());

                  invoice.recalculateTotals(
                      new BigDecimal("1000.00"), true, new BigDecimal("150.00"), false);
                  invoiceRepository.save(invoice);
                }));

    // Small delay to ensure timestamp difference is detectable
    try {
      Thread.sleep(50);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }

    // Update only sortOrder (no rate/name/exempt change)
    runInTenant(
        () ->
            taxRateService.updateTaxRate(
                taxRateId.get(),
                new UpdateTaxRateRequest("VAT", new BigDecimal("15.00"), true, false, true, 5)));

    // Verify line was NOT touched (updatedAt unchanged)
    runInTenant(
        () -> {
          var line = invoiceLineRepository.findById(lineId.get()).orElseThrow();
          assertThat(line.getUpdatedAt()).isEqualTo(originalUpdatedAt.get());
          assertThat(line.getTaxAmount()).isEqualByComparingTo(new BigDecimal("150.00"));
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

  private Invoice createDraftInvoice() {
    var invoice =
        new Invoice(
            customerId,
            "ZAR",
            "BatchRecalc Corp",
            "batchrecalc@test.com",
            null,
            "Batch Recalc Test Org",
            memberId);
    return invoiceRepository.save(invoice);
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
