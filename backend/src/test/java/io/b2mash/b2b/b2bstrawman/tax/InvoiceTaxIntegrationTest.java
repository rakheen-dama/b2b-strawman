package io.b2mash.b2b.b2bstrawman.tax;

import static io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory.createActiveCustomerWithPrerequisiteFields;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.CustomerProject;
import io.b2mash.b2b.b2bstrawman.customer.CustomerProjectRepository;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.invoice.Invoice;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceLineRepository;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceRepository;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceService;
import io.b2mash.b2b.b2bstrawman.invoice.dto.AddLineItemRequest;
import io.b2mash.b2b.b2bstrawman.invoice.dto.CreateInvoiceRequest;
import io.b2mash.b2b.b2bstrawman.invoice.dto.UpdateInvoiceRequest;
import io.b2mash.b2b.b2bstrawman.invoice.dto.UpdateLineItemRequest;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.task.Task;
import io.b2mash.b2b.b2bstrawman.task.TaskRepository;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import io.b2mash.b2b.b2bstrawman.timeentry.TimeEntry;
import io.b2mash.b2b.b2bstrawman.timeentry.TimeEntryRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InvoiceTaxIntegrationTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_inv_tax_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private InvoiceService invoiceService;
  @Autowired private InvoiceRepository invoiceRepository;
  @Autowired private InvoiceLineRepository lineRepository;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private CustomerProjectRepository customerProjectRepository;
  @Autowired private ProjectRepository projectRepository;
  @Autowired private TaskRepository taskRepository;
  @Autowired private TimeEntryRepository timeEntryRepository;
  @Autowired private TaxRateRepository taxRateRepository;
  @Autowired private OrgSettingsRepository orgSettingsRepository;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private TransactionTemplate transactionTemplate;

  private String tenantSchema;
  private UUID memberIdOwner;
  private UUID customerId;
  private UUID projectId;
  private UUID taskId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Invoice Tax Test Org", null);

    memberIdOwner =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                ORG_ID,
                "user_inv_tax_owner",
                "inv_tax_owner@test.com",
                "Tax Owner",
                "owner"));

    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var customer =
                      createActiveCustomerWithPrerequisiteFields(
                          "Tax Test Corp", "taxtest@test.com", memberIdOwner);
                  customer = customerRepository.save(customer);
                  customerId = customer.getId();

                  var project =
                      new Project("Invoice Tax Test Project", "Test project", memberIdOwner);
                  project = projectRepository.save(project);
                  projectId = project.getId();

                  customerProjectRepository.save(
                      new CustomerProject(customerId, projectId, memberIdOwner));

                  var task =
                      new Task(
                          projectId,
                          "Tax Test Task",
                          "Task for time entries",
                          "MEDIUM",
                          "TASK",
                          null,
                          memberIdOwner);
                  task = taskRepository.save(task);
                  taskId = task.getId();
                }));
  }

  @BeforeEach
  void cleanTaxRates() {
    runInTenant(
        () -> transactionTemplate.executeWithoutResult(tx -> taxRateRepository.deleteAll()));
  }

  @AfterEach
  void cleanInvoices() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  // Unlink time entries first
                  timeEntryRepository.findAll().stream()
                      .filter(te -> te.getInvoiceId() != null)
                      .forEach(
                          te -> {
                            te.setInvoiceId(null);
                            timeEntryRepository.save(te);
                          });
                  // Delete all invoice lines, then invoices
                  lineRepository.deleteAll();
                  invoiceRepository.deleteAll();
                  taxRateRepository.deleteAll();
                }));
  }

  // --- Tests ---

  @Test
  void addLineItem_autoAppliesDefaultTaxRate() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var taxRate = new TaxRate("VAT 15%", new BigDecimal("15.00"), true, false, 0);
                  taxRateRepository.save(taxRate);

                  var invoice = createDraftInvoice();
                  var request =
                      new AddLineItemRequest(
                          projectId,
                          "Test service",
                          new BigDecimal("2"),
                          new BigDecimal("100.00"),
                          0,
                          null,
                          null);

                  var response = invoiceService.addLineItem(invoice.getId(), request);

                  assertThat(response.lines()).hasSize(1);
                  var line = response.lines().getFirst();
                  assertThat(line.taxRateId()).isEqualTo(taxRate.getId());
                  assertThat(line.taxRateName()).isEqualTo("VAT 15%");
                  assertThat(line.taxRatePercent()).isEqualByComparingTo(new BigDecimal("15.00"));
                  // amount = 2 * 100 = 200; tax = 200 * 15 / 100 = 30
                  assertThat(line.taxAmount()).isEqualByComparingTo(new BigDecimal("30.00"));
                  assertThat(line.taxExempt()).isFalse();
                }));
  }

  @Test
  void addLineItem_usesSpecifiedTaxRate() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var defaultRate = new TaxRate("VAT 15%", new BigDecimal("15.00"), true, false, 0);
                  taxRateRepository.save(defaultRate);

                  var customRate =
                      new TaxRate("Reduced VAT", new BigDecimal("5.00"), false, false, 1);
                  taxRateRepository.save(customRate);

                  var invoice = createDraftInvoice();
                  var request =
                      new AddLineItemRequest(
                          projectId,
                          "Reduced rate service",
                          BigDecimal.ONE,
                          new BigDecimal("200.00"),
                          0,
                          customRate.getId(),
                          null);

                  var response = invoiceService.addLineItem(invoice.getId(), request);

                  var line = response.lines().getFirst();
                  assertThat(line.taxRateId()).isEqualTo(customRate.getId());
                  assertThat(line.taxRateName()).isEqualTo("Reduced VAT");
                  assertThat(line.taxRatePercent()).isEqualByComparingTo(new BigDecimal("5.00"));
                  // amount = 1 * 200 = 200; tax = 200 * 5 / 100 = 10
                  assertThat(line.taxAmount()).isEqualByComparingTo(new BigDecimal("10.00"));
                }));
  }

  @Test
  void addLineItem_noDefaultRate_noTaxApplied() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  // No tax rates exist (cleaned in @BeforeEach)
                  var invoice = createDraftInvoice();
                  var request =
                      new AddLineItemRequest(
                          projectId,
                          "No tax service",
                          BigDecimal.ONE,
                          new BigDecimal("100.00"),
                          0,
                          null,
                          null);

                  var response = invoiceService.addLineItem(invoice.getId(), request);

                  var line = response.lines().getFirst();
                  assertThat(line.taxRateId()).isNull();
                  assertThat(line.taxRateName()).isNull();
                  assertThat(line.taxRatePercent()).isNull();
                  assertThat(line.taxAmount()).isNull();
                  assertThat(line.taxExempt()).isFalse();
                }));
  }

  @Test
  void addLineItem_inactiveRate_throwsNotFound() {
    // Create the inactive rate in a separate transaction
    var rateId = new UUID[1];
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var inactiveRate =
                      new TaxRate("Old Rate", new BigDecimal("10.00"), false, false, 0);
                  inactiveRate.deactivate();
                  taxRateRepository.save(inactiveRate);
                  rateId[0] = inactiveRate.getId();
                }));

    // Create invoice in a separate transaction
    var invoiceId = new UUID[1];
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var invoice = createDraftInvoice();
                  invoiceId[0] = invoice.getId();
                }));

    // Expect exception in its own scope (no transaction wrapping the service call)
    runInTenant(
        () -> {
          var request =
              new AddLineItemRequest(
                  projectId, "Test", BigDecimal.ONE, new BigDecimal("100.00"), 0, rateId[0], null);

          assertThatThrownBy(() -> invoiceService.addLineItem(invoiceId[0], request))
              .isInstanceOf(ResourceNotFoundException.class);
        });
  }

  @Test
  void updateLineItem_changesTaxRate() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var rate1 = new TaxRate("VAT 15%", new BigDecimal("15.00"), true, false, 0);
                  taxRateRepository.save(rate1);

                  var rate2 = new TaxRate("VAT 20%", new BigDecimal("20.00"), false, false, 1);
                  taxRateRepository.save(rate2);

                  var invoice = createDraftInvoice();
                  var addResponse =
                      invoiceService.addLineItem(
                          invoice.getId(),
                          new AddLineItemRequest(
                              projectId,
                              "Taxable service",
                              BigDecimal.ONE,
                              new BigDecimal("100.00"),
                              0,
                              null,
                              null));

                  var lineId = addResponse.lines().getFirst().id();

                  var updateResponse =
                      invoiceService.updateLineItem(
                          invoice.getId(),
                          lineId,
                          new UpdateLineItemRequest(
                              "Taxable service",
                              BigDecimal.ONE,
                              new BigDecimal("100.00"),
                              0,
                              rate2.getId(),
                              null));

                  var line = updateResponse.lines().getFirst();
                  assertThat(line.taxRateId()).isEqualTo(rate2.getId());
                  assertThat(line.taxRateName()).isEqualTo("VAT 20%");
                  assertThat(line.taxRatePercent()).isEqualByComparingTo(new BigDecimal("20.00"));
                  // amount = 100; tax = 100 * 20 / 100 = 20
                  assertThat(line.taxAmount()).isEqualByComparingTo(new BigDecimal("20.00"));
                }));
  }

  @Test
  void updateLineItem_clearsTaxRate() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var rate = new TaxRate("VAT 15%", new BigDecimal("15.00"), true, false, 0);
                  taxRateRepository.save(rate);

                  var invoice = createDraftInvoice();
                  var addResponse =
                      invoiceService.addLineItem(
                          invoice.getId(),
                          new AddLineItemRequest(
                              projectId,
                              "Service",
                              BigDecimal.ONE,
                              new BigDecimal("100.00"),
                              0,
                              null,
                              null));

                  var lineId = addResponse.lines().getFirst().id();
                  assertThat(addResponse.lines().getFirst().taxRateId()).isNotNull();

                  var updateResponse =
                      invoiceService.updateLineItem(
                          invoice.getId(),
                          lineId,
                          new UpdateLineItemRequest(
                              "Service", BigDecimal.ONE, new BigDecimal("100.00"), 0, null, true));

                  var line = updateResponse.lines().getFirst();
                  assertThat(line.taxRateId()).isNull();
                  assertThat(line.taxRateName()).isNull();
                  assertThat(line.taxRatePercent()).isNull();
                  assertThat(line.taxAmount()).isNull();
                }));
  }

  @Test
  void updateLineItem_recalculatesInvoiceTotals() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var rate = new TaxRate("VAT 15%", new BigDecimal("15.00"), true, false, 0);
                  taxRateRepository.save(rate);

                  var invoice = createDraftInvoice();
                  var addResponse =
                      invoiceService.addLineItem(
                          invoice.getId(),
                          new AddLineItemRequest(
                              projectId,
                              "Service",
                              BigDecimal.ONE,
                              new BigDecimal("200.00"),
                              0,
                              null,
                              null));

                  // subtotal = 200, tax = 30, total = 230
                  assertThat(addResponse.subtotal()).isEqualByComparingTo(new BigDecimal("200.00"));
                  assertThat(addResponse.taxAmount()).isEqualByComparingTo(new BigDecimal("30.00"));
                  assertThat(addResponse.total()).isEqualByComparingTo(new BigDecimal("230.00"));
                  assertThat(addResponse.hasPerLineTax()).isTrue();
                }));
  }

  @Test
  void createDraft_fromTimeEntries_appliesDefaultTax() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var rate = new TaxRate("VAT 15%", new BigDecimal("15.00"), true, false, 0);
                  taxRateRepository.save(rate);

                  var timeEntry =
                      new TimeEntry(
                          taskId,
                          memberIdOwner,
                          LocalDate.now(),
                          60,
                          true,
                          null,
                          "Test time entry");
                  timeEntry.snapshotBillingRate(new BigDecimal("150.00"), "USD");
                  timeEntry = timeEntryRepository.save(timeEntry);

                  var request =
                      new CreateInvoiceRequest(
                          customerId,
                          "USD",
                          List.of(timeEntry.getId()),
                          null,
                          null,
                          null,
                          null,
                          null,
                          null,
                          null,
                          null,
                          null);

                  var response = invoiceService.createDraft(request, memberIdOwner);

                  assertThat(response.lines()).hasSize(1);
                  var line = response.lines().getFirst();
                  assertThat(line.taxRateId()).isEqualTo(rate.getId());
                  assertThat(line.taxRateName()).isEqualTo("VAT 15%");
                  // amount = 1h * 150 = 150; tax = 150 * 15 / 100 = 22.50
                  assertThat(line.taxAmount()).isEqualByComparingTo(new BigDecimal("22.50"));
                  assertThat(response.hasPerLineTax()).isTrue();
                }));
  }

  @Test
  void createDraft_fromTimeEntries_noDefaultRate_noTax() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  // No tax rates (cleaned in @BeforeEach)
                  var timeEntry =
                      new TimeEntry(
                          taskId,
                          memberIdOwner,
                          LocalDate.now(),
                          120,
                          true,
                          null,
                          "No tax time entry");
                  timeEntry.snapshotBillingRate(new BigDecimal("100.00"), "USD");
                  timeEntry = timeEntryRepository.save(timeEntry);

                  var request =
                      new CreateInvoiceRequest(
                          customerId,
                          "USD",
                          List.of(timeEntry.getId()),
                          null,
                          null,
                          null,
                          null,
                          null,
                          null,
                          null,
                          null,
                          null);

                  var response = invoiceService.createDraft(request, memberIdOwner);

                  assertThat(response.lines()).hasSize(1);
                  var line = response.lines().getFirst();
                  assertThat(line.taxRateId()).isNull();
                  assertThat(line.taxAmount()).isNull();
                  assertThat(response.hasPerLineTax()).isFalse();
                }));
  }

  @Test
  void updateDraft_rejectsManualTaxWhenPerLineTaxActive() {
    // Setup: create rate + invoice + line in one transaction
    var invoiceId = new UUID[1];
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var rate = new TaxRate("VAT 15%", new BigDecimal("15.00"), true, false, 0);
                  taxRateRepository.save(rate);

                  var invoice = createDraftInvoice();
                  invoiceService.addLineItem(
                      invoice.getId(),
                      new AddLineItemRequest(
                          projectId,
                          "Taxed service",
                          BigDecimal.ONE,
                          new BigDecimal("100.00"),
                          0,
                          null,
                          null));
                  invoiceId[0] = invoice.getId();
                }));

    // Expect exception in separate scope
    runInTenant(
        () -> {
          var updateRequest =
              new UpdateInvoiceRequest(
                  null, null, null, new BigDecimal("50.00"), null, null, null, null);

          assertThatThrownBy(() -> invoiceService.updateDraft(invoiceId[0], updateRequest))
              .isInstanceOf(InvalidStateException.class);
        });
  }

  @Test
  void updateDraft_allowsManualTaxWhenNoPerLineTax() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  // No tax rates (cleaned in @BeforeEach)
                  var invoice = createDraftInvoice();
                  invoiceService.addLineItem(
                      invoice.getId(),
                      new AddLineItemRequest(
                          projectId,
                          "No tax service",
                          BigDecimal.ONE,
                          new BigDecimal("100.00"),
                          0,
                          null,
                          null));

                  var response =
                      invoiceService.updateDraft(
                          invoice.getId(),
                          new UpdateInvoiceRequest(
                              null, null, null, new BigDecimal("15.00"), null, null, null, null));

                  assertThat(response.taxAmount()).isEqualByComparingTo(new BigDecimal("15.00"));
                  assertThat(response.total()).isEqualByComparingTo(new BigDecimal("115.00"));
                }));
  }

  @Test
  void updateLineItem_recalculatesTaxWhenAmountChanges() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var rate = new TaxRate("VAT 15%", new BigDecimal("15.00"), true, false, 0);
                  taxRateRepository.save(rate);

                  var invoice = createDraftInvoice();
                  var addResponse =
                      invoiceService.addLineItem(
                          invoice.getId(),
                          new AddLineItemRequest(
                              projectId,
                              "Taxable service",
                              BigDecimal.ONE,
                              new BigDecimal("100.00"),
                              0,
                              null,
                              null));

                  var lineId = addResponse.lines().getFirst().id();
                  // Initial: amount = 1 * 100 = 100; tax = 100 * 15 / 100 = 15
                  assertThat(addResponse.lines().getFirst().taxAmount())
                      .isEqualByComparingTo(new BigDecimal("15.00"));

                  // Update quantity to 2 without changing tax rate
                  var updateResponse =
                      invoiceService.updateLineItem(
                          invoice.getId(),
                          lineId,
                          new UpdateLineItemRequest(
                              "Taxable service",
                              new BigDecimal("2"),
                              new BigDecimal("100.00"),
                              0,
                              null,
                              null));

                  var line = updateResponse.lines().getFirst();
                  // After: amount = 2 * 100 = 200; tax = 200 * 15 / 100 = 30
                  assertThat(line.taxAmount()).isEqualByComparingTo(new BigDecimal("30.00"));
                  assertThat(line.taxRateId()).isEqualTo(rate.getId());
                  assertThat(line.taxRateName()).isEqualTo("VAT 15%");
                  // Invoice totals should reflect updated tax
                  assertThat(updateResponse.subtotal())
                      .isEqualByComparingTo(new BigDecimal("200.00"));
                  assertThat(updateResponse.taxAmount())
                      .isEqualByComparingTo(new BigDecimal("30.00"));
                  assertThat(updateResponse.total()).isEqualByComparingTo(new BigDecimal("230.00"));
                }));
  }

  @Test
  void invoiceResponse_includesTaxBreakdown() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var rate = new TaxRate("VAT 15%", new BigDecimal("15.00"), true, false, 0);
                  taxRateRepository.save(rate);

                  var invoice = createDraftInvoice();
                  invoiceService.addLineItem(
                      invoice.getId(),
                      new AddLineItemRequest(
                          projectId,
                          "Service A",
                          BigDecimal.ONE,
                          new BigDecimal("100.00"),
                          0,
                          null,
                          null));
                  var response =
                      invoiceService.addLineItem(
                          invoice.getId(),
                          new AddLineItemRequest(
                              projectId,
                              "Service B",
                              BigDecimal.ONE,
                              new BigDecimal("200.00"),
                              1,
                              null,
                              null));

                  assertThat(response.taxBreakdown()).hasSize(1);
                  var breakdown = response.taxBreakdown().getFirst();
                  assertThat(breakdown.rateName()).isEqualTo("VAT 15%");
                  assertThat(breakdown.ratePercent()).isEqualByComparingTo(new BigDecimal("15.00"));
                  // Total taxable = 100 + 200 = 300; total tax = 15 + 30 = 45
                  assertThat(breakdown.taxableAmount())
                      .isEqualByComparingTo(new BigDecimal("300.00"));
                  assertThat(breakdown.taxAmount()).isEqualByComparingTo(new BigDecimal("45.00"));
                  assertThat(response.hasPerLineTax()).isTrue();
                  assertThat(response.taxInclusive()).isFalse();
                }));
  }

  // --- F-666B-B1: saving draft details must never zero the fee note's VAT ---

  @Test
  void updateDraft_detailsOnly_keepsPerLineVatAndTotal() {
    // INV-0009's shape: one line of R 1 875,00 at VAT 15% → VAT R 281,25, total R 2 156,25.
    var invoiceId = draftWithTaxedLine(new BigDecimal("1875.00"));

    // Due date only — the UI sends no taxAmount for a per-line-tax fee note.
    runInTenant(
        () ->
            invoiceService.updateDraft(
                invoiceId,
                new UpdateInvoiceRequest(
                    LocalDate.of(2026, 12, 31), null, null, null, null, null, null, null)));
    assertStoredTotals(invoiceId, "1875.00", "281.25", "2156.25");

    // A second save of other details (notes, PO number) leaves the totals alone too.
    runInTenant(
        () -> {
          var response =
              invoiceService.updateDraft(
                  invoiceId,
                  new UpdateInvoiceRequest(
                      LocalDate.of(2026, 12, 31),
                      "Please pay promptly",
                      null,
                      null,
                      "PO-777",
                      null,
                      null,
                      null));
          assertThat(response.taxAmount()).isEqualByComparingTo(new BigDecimal("281.25"));
          assertThat(response.total()).isEqualByComparingTo(new BigDecimal("2156.25"));
          assertThat(response.notes()).isEqualTo("Please pay promptly");
          assertThat(response.poNumber()).isEqualTo("PO-777");
        });
    assertStoredTotals(invoiceId, "1875.00", "281.25", "2156.25");
  }

  @Test
  void updateDraft_detailsOnly_taxInclusive_keepsVatInsideTotal() {
    setTaxInclusive(true);
    try {
      // Tax-inclusive: R 1 150,00 line carries R 150,00 VAT; total stays R 1 150,00.
      var invoiceId = draftWithTaxedLine(new BigDecimal("1150.00"));
      assertStoredTotals(invoiceId, "1150.00", "150.00", "1150.00");

      runInTenant(
          () ->
              invoiceService.updateDraft(
                  invoiceId,
                  new UpdateInvoiceRequest(
                      LocalDate.of(2026, 12, 31), null, null, null, null, null, null, null)));

      assertStoredTotals(invoiceId, "1150.00", "150.00", "1150.00");
    } finally {
      setTaxInclusive(false);
    }
  }

  @Test
  void updateDraft_headerTax_omittedTaxAmount_keepsHeaderTax() {
    var invoiceId = new UUID[1];
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  // No tax rates — a header-tax fee note.
                  var invoice = createDraftInvoice();
                  invoiceService.addLineItem(
                      invoice.getId(),
                      new AddLineItemRequest(
                          projectId,
                          "No tax service",
                          BigDecimal.ONE,
                          new BigDecimal("100.00"),
                          0,
                          null,
                          null));
                  invoiceService.updateDraft(
                      invoice.getId(),
                      new UpdateInvoiceRequest(
                          null, null, null, new BigDecimal("15.00"), null, null, null, null));
                  invoiceId[0] = invoice.getId();
                }));
    assertStoredTotals(invoiceId[0], "100.00", "15.00", "115.00");

    // A details save that omits taxAmount keeps the header tax; an explicit value still sets it.
    runInTenant(
        () ->
            invoiceService.updateDraft(
                invoiceId[0],
                new UpdateInvoiceRequest(
                    LocalDate.of(2026, 12, 31), null, null, null, null, null, null, null)));
    assertStoredTotals(invoiceId[0], "100.00", "15.00", "115.00");

    runInTenant(
        () ->
            invoiceService.updateDraft(
                invoiceId[0],
                new UpdateInvoiceRequest(
                    null, null, null, BigDecimal.ZERO, null, null, null, null)));
    assertStoredTotals(invoiceId[0], "100.00", "0.00", "100.00");
  }

  // --- The last taxed line goes: its VAT must not survive as header tax ---

  @Test
  void updateLineItem_clearingLastTaxedLinesRate_dropsVatToZero() {
    // R 100,00 at VAT 15% → VAT R 15,00, total R 115,00.
    var invoiceId = draftWithTaxedLine(new BigDecimal("100.00"));
    assertStoredTotals(invoiceId, "100.00", "15.00", "115.00");
    var lineId = onlyLineId(invoiceId);

    runInTenant(
        () -> {
          var response =
              invoiceService.updateLineItem(
                  invoiceId,
                  lineId,
                  new UpdateLineItemRequest(
                      "Taxed service", BigDecimal.ONE, new BigDecimal("100.00"), 0, null, true));
          assertThat(response.hasPerLineTax()).isFalse();
          assertThat(response.taxAmount()).isEqualByComparingTo(BigDecimal.ZERO);
          assertThat(response.total()).isEqualByComparingTo(new BigDecimal("100.00"));
        });
    assertStoredTotals(invoiceId, "100.00", "0.00", "100.00");

    // A later details save (due date only) brings no phantom VAT back.
    runInTenant(
        () ->
            invoiceService.updateDraft(
                invoiceId,
                new UpdateInvoiceRequest(
                    LocalDate.of(2026, 12, 31), null, null, null, null, null, null, null)));
    assertStoredTotals(invoiceId, "100.00", "0.00", "100.00");
  }

  @Test
  void updateLineItem_clearingLastTaxedLinesRate_taxInclusive_dropsVatToZero() {
    setTaxInclusive(true);
    try {
      // Tax-inclusive: R 1 150,00 line carries R 150,00 VAT; total stays R 1 150,00.
      var invoiceId = draftWithTaxedLine(new BigDecimal("1150.00"));
      assertStoredTotals(invoiceId, "1150.00", "150.00", "1150.00");
      var lineId = onlyLineId(invoiceId);

      runInTenant(
          () ->
              invoiceService.updateLineItem(
                  invoiceId,
                  lineId,
                  new UpdateLineItemRequest(
                      "Taxed service", BigDecimal.ONE, new BigDecimal("1150.00"), 0, null, true)));

      assertStoredTotals(invoiceId, "1150.00", "0.00", "1150.00");
    } finally {
      setTaxInclusive(false);
    }
  }

  @Test
  void deleteLineItem_lastTaxedLine_dropsVatToZero() {
    var ids = new UUID[2];
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  // No rates yet: an untaxed R 50,00 line.
                  var invoice = createDraftInvoice();
                  invoiceService.addLineItem(
                      invoice.getId(),
                      new AddLineItemRequest(
                          projectId,
                          "Untaxed service",
                          BigDecimal.ONE,
                          new BigDecimal("50.00"),
                          0,
                          null,
                          null));
                  // Then a R 100,00 line at VAT 15% → VAT R 15,00.
                  var rate =
                      taxRateRepository.save(
                          new TaxRate("Standard (15%)", new BigDecimal("15.00"), false, false, 0));
                  var response =
                      invoiceService.addLineItem(
                          invoice.getId(),
                          new AddLineItemRequest(
                              projectId,
                              "Taxed service",
                              BigDecimal.ONE,
                              new BigDecimal("100.00"),
                              1,
                              rate.getId(),
                              null));
                  ids[0] = invoice.getId();
                  ids[1] =
                      response.lines().stream()
                          .filter(l -> l.taxRateId() != null)
                          .findFirst()
                          .orElseThrow()
                          .id();
                }));
    assertStoredTotals(ids[0], "150.00", "15.00", "165.00");

    runInTenant(() -> invoiceService.deleteLineItem(ids[0], ids[1]));

    assertStoredTotals(ids[0], "50.00", "0.00", "50.00");
  }

  @Test
  void clearedPerLineTax_thenExplicitHeaderTax_applies_andSurvivesUntaxedLineEdits() {
    var invoiceId = draftWithTaxedLine(new BigDecimal("100.00"));
    var lineId = onlyLineId(invoiceId);
    runInTenant(
        () ->
            invoiceService.updateLineItem(
                invoiceId,
                lineId,
                new UpdateLineItemRequest(
                    "Taxed service", BigDecimal.ONE, new BigDecimal("100.00"), 0, null, true)));
    assertStoredTotals(invoiceId, "100.00", "0.00", "100.00");

    // Header tax typed afterwards is the firm's choice: it applies...
    runInTenant(
        () ->
            invoiceService.updateDraft(
                invoiceId,
                new UpdateInvoiceRequest(
                    null, null, null, new BigDecimal("20.00"), null, null, null, null)));
    assertStoredTotals(invoiceId, "100.00", "20.00", "120.00");

    // ...and an edit of the now-untaxed line (no rate introduced) keeps it.
    runInTenant(
        () ->
            invoiceService.updateLineItem(
                invoiceId,
                lineId,
                new UpdateLineItemRequest(
                    "Service", new BigDecimal("2"), new BigDecimal("100.00"), 0, null, true)));
    assertStoredTotals(invoiceId, "200.00", "20.00", "220.00");
  }

  @Test
  void headerTaxOnlyDraft_keepsManualTax_throughLineAddEditAndDelete() {
    var ids = new UUID[2];
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  // No tax rates — a header-tax fee note with R 15,00 typed by hand.
                  var invoice = createDraftInvoice();
                  var response =
                      invoiceService.addLineItem(
                          invoice.getId(),
                          new AddLineItemRequest(
                              projectId,
                              "No tax service",
                              BigDecimal.ONE,
                              new BigDecimal("100.00"),
                              0,
                              null,
                              null));
                  invoiceService.updateDraft(
                      invoice.getId(),
                      new UpdateInvoiceRequest(
                          null, null, null, new BigDecimal("15.00"), null, null, null, null));
                  ids[0] = invoice.getId();
                  ids[1] = response.lines().getFirst().id();
                }));
    assertStoredTotals(ids[0], "100.00", "15.00", "115.00");

    // Add a second untaxed line.
    var secondLineId = new UUID[1];
    runInTenant(
        () -> {
          var response =
              invoiceService.addLineItem(
                  ids[0],
                  new AddLineItemRequest(
                      projectId,
                      "Second service",
                      BigDecimal.ONE,
                      new BigDecimal("40.00"),
                      1,
                      null,
                      null));
          secondLineId[0] =
              response.lines().stream()
                  .filter(l -> !l.id().equals(ids[1]))
                  .findFirst()
                  .orElseThrow()
                  .id();
        });
    assertStoredTotals(ids[0], "140.00", "15.00", "155.00");

    // Edit it — with and without the clear flag — no rate is ever introduced.
    runInTenant(
        () ->
            invoiceService.updateLineItem(
                ids[0],
                secondLineId[0],
                new UpdateLineItemRequest(
                    "Second service", BigDecimal.ONE, new BigDecimal("60.00"), 1, null, null)));
    assertStoredTotals(ids[0], "160.00", "15.00", "175.00");
    runInTenant(
        () ->
            invoiceService.updateLineItem(
                ids[0],
                secondLineId[0],
                new UpdateLineItemRequest(
                    "Second service", BigDecimal.ONE, new BigDecimal("60.00"), 1, null, true)));
    assertStoredTotals(ids[0], "160.00", "15.00", "175.00");

    // Delete it.
    runInTenant(() -> invoiceService.deleteLineItem(ids[0], secondLineId[0]));
    assertStoredTotals(ids[0], "100.00", "15.00", "115.00");
  }

  @Test
  void clearingOneOfTwoTaxedLines_keepsTheOtherLinesVat() {
    var invoiceId = draftWithTaxedLine(new BigDecimal("100.00"));
    var firstLineId = onlyLineId(invoiceId);
    runInTenant(
        () ->
            invoiceService.addLineItem(
                invoiceId,
                new AddLineItemRequest(
                    projectId,
                    "Second taxed service",
                    BigDecimal.ONE,
                    new BigDecimal("200.00"),
                    1,
                    null,
                    null)));
    assertStoredTotals(invoiceId, "300.00", "45.00", "345.00");

    runInTenant(
        () ->
            invoiceService.updateLineItem(
                invoiceId,
                firstLineId,
                new UpdateLineItemRequest(
                    "Taxed service", BigDecimal.ONE, new BigDecimal("100.00"), 0, null, true)));

    assertStoredTotals(invoiceId, "300.00", "30.00", "330.00");
  }

  // --- Helpers ---

  private UUID onlyLineId(UUID invoiceId) {
    var lineId = new UUID[1];
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var lines = lineRepository.findByInvoiceIdOrderBySortOrder(invoiceId);
                  assertThat(lines).hasSize(1);
                  lineId[0] = lines.getFirst().getId();
                }));
    return lineId[0];
  }

  /** A draft with one R {@code amount} line at the default VAT 15% rate; returns its id. */
  private UUID draftWithTaxedLine(BigDecimal amount) {
    var invoiceId = new UUID[1];
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  taxRateRepository.save(
                      new TaxRate("Standard (15%)", new BigDecimal("15.00"), true, false, 0));
                  var invoice = createDraftInvoice();
                  invoiceService.addLineItem(
                      invoice.getId(),
                      new AddLineItemRequest(
                          projectId, "Taxed service", BigDecimal.ONE, amount, 0, null, null));
                  invoiceId[0] = invoice.getId();
                }));
    return invoiceId[0];
  }

  private void assertStoredTotals(UUID invoiceId, String subtotal, String tax, String total) {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var stored = invoiceRepository.findById(invoiceId).orElseThrow();
                  assertThat(stored.getSubtotal()).isEqualByComparingTo(new BigDecimal(subtotal));
                  assertThat(stored.getTaxAmount()).isEqualByComparingTo(new BigDecimal(tax));
                  assertThat(stored.getTotal()).isEqualByComparingTo(new BigDecimal(total));
                }));
  }

  private void setTaxInclusive(boolean taxInclusive) {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var settings = orgSettingsRepository.findForCurrentTenant().orElseThrow();
                  settings.getTax().setTaxInclusive(taxInclusive);
                  orgSettingsRepository.save(settings);
                }));
  }

  private Invoice createDraftInvoice() {
    var invoice =
        new Invoice(
            customerId,
            "USD",
            "Tax Test Corp",
            "taxtest@test.com",
            null,
            "Invoice Tax Test Org",
            memberIdOwner);
    return invoiceRepository.save(invoice);
  }

  private void runInTenant(Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberIdOwner)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(action);
  }
}
