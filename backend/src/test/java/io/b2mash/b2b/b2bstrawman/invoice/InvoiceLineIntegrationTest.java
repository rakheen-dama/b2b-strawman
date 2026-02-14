package io.b2mash.b2b.b2bstrawman.invoice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.Customer;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
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
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InvoiceLineIntegrationTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_inv_line_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private InvoiceRepository invoiceRepository;
  @Autowired private InvoiceLineRepository invoiceLineRepository;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private ProjectRepository projectRepository;
  @Autowired private TaskRepository taskRepository;
  @Autowired private TimeEntryRepository timeEntryRepository;
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
    provisioningService.provisionTenant(ORG_ID, "Invoice Line Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");

    memberIdOwner =
        UUID.fromString(
            syncMember(
                ORG_ID, "user_inv_line_owner", "inv_line_owner@test.com", "Line Owner", "owner"));

    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

    // Create a customer, project, and task for test data
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberIdOwner)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var customer =
                          new Customer(
                              "Line Test Corp",
                              "linetest@test.com",
                              "+1-555-0300",
                              "LTC-001",
                              "Test customer for line items",
                              memberIdOwner);
                      customer = customerRepository.save(customer);
                      customerId = customer.getId();

                      var project =
                          new Project("Invoice Line Test Project", "Test project", memberIdOwner);
                      project = projectRepository.save(project);
                      projectId = project.getId();

                      var task =
                          new Task(
                              projectId,
                              "Test Task",
                              "Task for time entries",
                              "MEDIUM",
                              "TASK",
                              null,
                              memberIdOwner);
                      task = taskRepository.save(task);
                      taskId = task.getId();
                    }));
  }

  @Test
  void shouldSaveAndRetrieveLineItemWithInvoiceIdFk() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var invoice = createDraftInvoice();

                  var line =
                      new InvoiceLine(
                          invoice.getId(),
                          null,
                          null,
                          "Development services",
                          new BigDecimal("8.0000"),
                          new BigDecimal("150.00"),
                          0);
                  line = invoiceLineRepository.save(line);

                  var lines =
                      invoiceLineRepository.findByInvoiceIdOrderBySortOrder(invoice.getId());
                  assertThat(lines).hasSize(1);
                  var found = lines.get(0);
                  assertThat(found.getInvoiceId()).isEqualTo(invoice.getId());
                  assertThat(found.getDescription()).isEqualTo("Development services");
                  assertThat(found.getQuantity()).isEqualByComparingTo(new BigDecimal("8.0000"));
                  assertThat(found.getUnitPrice()).isEqualByComparingTo(new BigDecimal("150.00"));
                  assertThat(found.getAmount()).isEqualByComparingTo(new BigDecimal("1200.0000"));
                  assertThat(found.getSortOrder()).isEqualTo(0);
                  assertThat(found.getProjectId()).isNull();
                  assertThat(found.getTimeEntryId()).isNull();
                  assertThat(found.getCreatedAt()).isNotNull();
                  assertThat(found.getUpdatedAt()).isNotNull();
                }));
  }

  @Test
  void findByInvoiceIdOrderBySortOrderReturnsLinesInCorrectOrder() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var invoice = createDraftInvoice();

                  // Insert lines with sortOrder 2, 0, 1 (out of order)
                  invoiceLineRepository.save(
                      new InvoiceLine(
                          invoice.getId(),
                          null,
                          null,
                          "Third item",
                          new BigDecimal("1.0000"),
                          new BigDecimal("30.00"),
                          2));
                  invoiceLineRepository.save(
                      new InvoiceLine(
                          invoice.getId(),
                          null,
                          null,
                          "First item",
                          new BigDecimal("1.0000"),
                          new BigDecimal("10.00"),
                          0));
                  invoiceLineRepository.save(
                      new InvoiceLine(
                          invoice.getId(),
                          null,
                          null,
                          "Second item",
                          new BigDecimal("1.0000"),
                          new BigDecimal("20.00"),
                          1));

                  var lines =
                      invoiceLineRepository.findByInvoiceIdOrderBySortOrder(invoice.getId());
                  assertThat(lines).hasSize(3);
                  assertThat(lines.get(0).getDescription()).isEqualTo("First item");
                  assertThat(lines.get(0).getSortOrder()).isEqualTo(0);
                  assertThat(lines.get(1).getDescription()).isEqualTo("Second item");
                  assertThat(lines.get(1).getSortOrder()).isEqualTo(1);
                  assertThat(lines.get(2).getDescription()).isEqualTo("Third item");
                  assertThat(lines.get(2).getSortOrder()).isEqualTo(2);
                }));
  }

  @Test
  void doubleBillingPreventionRejectsDuplicateTimeEntryId() {
    // Create two time entries for this test
    var timeEntryIds = new UUID[1];
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var timeEntry =
                      new TimeEntry(
                          taskId, memberIdOwner, LocalDate.now(), 120, true, null, "Billable work");
                  timeEntry = timeEntryRepository.save(timeEntry);
                  timeEntryIds[0] = timeEntry.getId();
                }));

    // Save first line referencing the time entry in one transaction
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var invoice = createDraftInvoice();
                  invoiceLineRepository.save(
                      new InvoiceLine(
                          invoice.getId(),
                          projectId,
                          timeEntryIds[0],
                          "Time entry billing",
                          new BigDecimal("2.0000"),
                          new BigDecimal("100.00"),
                          0));
                }));

    // Attempt to save second line with same timeEntryId in a new transaction
    assertThatThrownBy(
            () ->
                runInTenant(
                    () ->
                        transactionTemplate.executeWithoutResult(
                            tx -> {
                              var invoice2 = createDraftInvoice();
                              invoiceLineRepository.save(
                                  new InvoiceLine(
                                      invoice2.getId(),
                                      projectId,
                                      timeEntryIds[0],
                                      "Duplicate time entry billing",
                                      new BigDecimal("3.0000"),
                                      new BigDecimal("100.00"),
                                      0));
                            })))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  // --- Helpers ---

  private Invoice createDraftInvoice() {
    var invoice =
        new Invoice(
            customerId,
            "USD",
            "Line Test Corp",
            "linetest@test.com",
            null,
            "Invoice Line Test Org",
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
