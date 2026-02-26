package io.b2mash.b2b.b2bstrawman.tax;

import static io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory.createActiveCustomer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
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
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.task.Task;
import io.b2mash.b2b.b2bstrawman.task.TaskRepository;
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
import org.springframework.http.MediaType;
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
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private TransactionTemplate transactionTemplate;

  private String tenantSchema;
  private UUID memberIdOwner;
  private UUID customerId;
  private UUID projectId;
  private UUID taskId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Invoice Tax Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");

    memberIdOwner =
        UUID.fromString(
            syncMember(
                ORG_ID, "user_inv_tax_owner", "inv_tax_owner@test.com", "Tax Owner", "owner"));

    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var customer =
                      createActiveCustomer("Tax Test Corp", "taxtest@test.com", memberIdOwner);
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
                          customRate.getId());

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
                  projectId, "Test", BigDecimal.ONE, new BigDecimal("100.00"), 0, rateId[0]);

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
                              rate2.getId()));

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
                              null));

                  var lineId = addResponse.lines().getFirst().id();
                  assertThat(addResponse.lines().getFirst().taxRateId()).isNotNull();

                  var updateResponse =
                      invoiceService.updateLineItem(
                          invoice.getId(),
                          lineId,
                          new UpdateLineItemRequest(
                              "Service", BigDecimal.ONE, new BigDecimal("100.00"), 0, null));

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
                          customerId, "USD", List.of(timeEntry.getId()), null, null, null);

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
                          customerId, "USD", List.of(timeEntry.getId()), null, null, null);

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
                          null));
                  invoiceId[0] = invoice.getId();
                }));

    // Expect exception in separate scope
    runInTenant(
        () -> {
          var updateRequest = new UpdateInvoiceRequest(null, null, null, new BigDecimal("50.00"));

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
                          null));

                  var response =
                      invoiceService.updateDraft(
                          invoice.getId(),
                          new UpdateInvoiceRequest(null, null, null, new BigDecimal("15.00")));

                  assertThat(response.taxAmount()).isEqualByComparingTo(new BigDecimal("15.00"));
                  assertThat(response.total()).isEqualByComparingTo(new BigDecimal("115.00"));
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

  // --- Helpers ---

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
                        {
                          "clerkOrgId": "%s",
                          "clerkUserId": "%s",
                          "email": "%s",
                          "name": "%s",
                          "avatarUrl": null,
                          "orgRole": "%s"
                        }
                        """
                            .formatted(orgId, clerkUserId, email, name, orgRole)))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.memberId");
  }
}
