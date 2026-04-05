package io.b2mash.b2b.b2bstrawman.assistant.tool.read;

import static io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory.createActiveCustomerWithPrerequisiteFields;
import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.assistant.tool.AssistantToolRegistry;
import io.b2mash.b2b.b2bstrawman.assistant.tool.TenantToolContext;
import io.b2mash.b2b.b2bstrawman.budget.ProjectBudgetService;
import io.b2mash.b2b.b2bstrawman.customer.CustomerProject;
import io.b2mash.b2b.b2bstrawman.customer.CustomerProjectRepository;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceService;
import io.b2mash.b2b.b2bstrawman.invoice.dto.CreateInvoiceRequest;
import io.b2mash.b2b.b2bstrawman.multitenancy.ActorContext;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.task.Task;
import io.b2mash.b2b.b2bstrawman.task.TaskRepository;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import io.b2mash.b2b.b2bstrawman.timeentry.TimeEntry;
import io.b2mash.b2b.b2bstrawman.timeentry.TimeEntryRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
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
class FinancialReadToolsTest {
  private static final String ORG_ID = "org_financial_tools_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private CustomerProjectRepository customerProjectRepository;
  @Autowired private ProjectRepository projectRepository;
  @Autowired private TaskRepository taskRepository;
  @Autowired private TimeEntryRepository timeEntryRepository;
  @Autowired private InvoiceService invoiceService;
  @Autowired private ProjectBudgetService projectBudgetService;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private AssistantToolRegistry assistantToolRegistry;

  @Autowired private GetUnbilledTimeTool getUnbilledTimeTool;
  @Autowired private GetProjectBudgetTool getProjectBudgetTool;
  @Autowired private GetProfitabilityTool getProfitabilityTool;
  @Autowired private ListInvoicesTool listInvoicesTool;
  @Autowired private GetInvoiceTool getInvoiceTool;

  private String tenantSchema;
  private UUID memberIdOwner;
  private UUID customerId;
  private UUID projectId;
  private UUID taskId;
  private UUID timeEntryId;
  private UUID invoiceId;

  @BeforeAll
  void provisionAndSeedData() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Financial Tools Test Org", null);

    var memberIdStr =
        TestMemberHelper.syncMember(
            mockMvc, ORG_ID, "user_fin_owner", "fin_owner@test.com", "Fin Owner", "owner");
    memberIdOwner = UUID.fromString(memberIdStr);

    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

    // Seed data using direct repositories (for invoice creation prerequisite fields)
    runInTenantScope(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var customer =
                      createActiveCustomerWithPrerequisiteFields(
                          "Financial Test Customer", "fin_customer@test.com", memberIdOwner);
                  customer = customerRepository.save(customer);
                  customerId = customer.getId();

                  var project =
                      new Project(
                          "Financial Test Project", "A project for financial tests", memberIdOwner);
                  project = projectRepository.save(project);
                  projectId = project.getId();

                  customerProjectRepository.save(
                      new CustomerProject(customerId, projectId, memberIdOwner));

                  var task =
                      new Task(
                          projectId,
                          "Financial Test Task",
                          "A task for time entries",
                          "MEDIUM",
                          "TASK",
                          null,
                          memberIdOwner);
                  task = taskRepository.save(task);
                  taskId = task.getId();

                  var timeEntry =
                      new TimeEntry(
                          taskId, memberIdOwner, LocalDate.now(), 120, true, null, "Billable work");
                  timeEntry.snapshotBillingRate(new BigDecimal("100.00"), "USD");
                  timeEntry = timeEntryRepository.save(timeEntry);
                  timeEntryId = timeEntry.getId();
                }));

    // Create budget
    runInTenantScope(
        () -> {
          var actor = new ActorContext(memberIdOwner, "owner");
          projectBudgetService.upsertBudget(
              projectId,
              new BigDecimal("100"),
              new BigDecimal("10000"),
              "USD",
              80,
              "Test budget",
              actor);
        });

    // Create invoice (needs ORG_ID in scope for org name lookup)
    runInTenantScope(
        () -> {
          var request =
              new CreateInvoiceRequest(
                  customerId, "USD", List.of(timeEntryId), null, null, null, null);
          var invoiceResponse = invoiceService.createDraft(request, memberIdOwner);
          invoiceId = invoiceResponse.id();
        });
  }

  @Test
  void getUnbilledTimeToolReturnsProjectSummary() {
    runInTenantScope(
        () -> {
          var ctx = buildContext();
          @SuppressWarnings("unchecked")
          var result =
              (Map<String, Object>)
                  getUnbilledTimeTool.execute(Map.of("projectId", projectId.toString()), ctx);
          assertThat(result).containsKey("entryCount");
          assertThat(((Number) result.get("entryCount")).longValue()).isGreaterThanOrEqualTo(1);
        });
  }

  @Test
  void getProjectBudgetToolReturnsBudgetStatus() {
    runInTenantScope(
        () -> {
          var ctx = buildContext();
          @SuppressWarnings("unchecked")
          var result =
              (Map<String, Object>)
                  getProjectBudgetTool.execute(Map.of("projectId", projectId.toString()), ctx);
          assertThat(result).containsKey("overallStatus");
          assertThat(result.get("overallStatus")).isNotNull();
        });
  }

  @Test
  void getProfitabilityToolReturnsProjectProfitability() {
    runInTenantScope(
        () -> {
          var ctx = buildContext();
          @SuppressWarnings("unchecked")
          var result =
              (Map<String, Object>)
                  getProfitabilityTool.execute(Map.of("projectId", projectId.toString()), ctx);
          assertThat(result).containsKey("type");
          assertThat(result.get("type")).isEqualTo("project");
          assertThat(result).containsKey("currencies");
        });
  }

  @Test
  void listInvoicesToolReturnsSeededInvoices() {
    runInTenantScope(
        () -> {
          var ctx = buildContext();
          @SuppressWarnings("unchecked")
          var result = (List<Map<String, Object>>) listInvoicesTool.execute(Map.of(), ctx);
          assertThat(result).isNotEmpty();
        });
  }

  @Test
  void getInvoiceToolByIdReturnsFullDetails() {
    runInTenantScope(
        () -> {
          var ctx = buildContext();
          @SuppressWarnings("unchecked")
          var result =
              (Map<String, Object>)
                  getInvoiceTool.execute(Map.of("invoiceId", invoiceId.toString()), ctx);
          assertThat(result).containsKey("lines");
          assertThat(result).containsKey("invoiceNumber");
        });
  }

  // --- Helpers ---

  private TenantToolContext buildContext() {
    return new TenantToolContext(
        tenantSchema, memberIdOwner, "owner", Set.of("FINANCIAL_VISIBILITY", "INVOICING"));
  }

  private void runInTenantScope(Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberIdOwner)
        .where(RequestScopes.ORG_ROLE, "owner")
        .where(RequestScopes.CAPABILITIES, Set.of("FINANCIAL_VISIBILITY", "INVOICING"))
        .run(action);
  }
}
