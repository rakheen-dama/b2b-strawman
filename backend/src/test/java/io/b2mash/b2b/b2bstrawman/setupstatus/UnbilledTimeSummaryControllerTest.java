package io.b2mash.b2b.b2bstrawman.setupstatus;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.Customer;
import io.b2mash.b2b.b2bstrawman.customer.CustomerProject;
import io.b2mash.b2b.b2bstrawman.customer.CustomerProjectRepository;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.invoice.Invoice;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettings;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.task.Task;
import io.b2mash.b2b.b2bstrawman.task.TaskRepository;
import io.b2mash.b2b.b2bstrawman.timeentry.TimeEntry;
import io.b2mash.b2b.b2bstrawman.timeentry.TimeEntryRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class UnbilledTimeSummaryControllerTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_unbilled_summary_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private ProjectRepository projectRepository;
  @Autowired private TaskRepository taskRepository;
  @Autowired private TimeEntryRepository timeEntryRepository;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private CustomerProjectRepository customerProjectRepository;
  @Autowired private InvoiceRepository invoiceRepository;
  @Autowired private OrgSettingsRepository orgSettingsRepository;
  @Autowired private EntityManager entityManager;
  @Autowired private TransactionTemplate transactionTemplate;

  private String tenantSchema;
  private UUID memberIdOwner;

  // Project A: will have 2 billable unbilled entries
  private UUID projectIdA;
  // Project B: will have all entries invoiced
  private UUID projectIdB;
  // Project C: will have mixed billable and non-billable
  private UUID projectIdC;

  private UUID customerIdWithEntries;
  private UUID customerIdEmpty;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Unbilled Summary Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");

    memberIdOwner =
        UUID.fromString(
            syncMember(
                ORG_ID,
                "user_unbilled_owner",
                "unbilled_owner@test.com",
                "Unbilled Owner",
                "owner"));

    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberIdOwner)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      // === Phase 0: Create OrgSettings with ZAR currency ===
                      orgSettingsRepository.deleteAll();
                      orgSettingsRepository.save(new OrgSettings("ZAR"));

                      // === Phase 1: Create all parent entities ===
                      var customer =
                          new Customer(
                              "Test Customer",
                              "customer@test.com",
                              "0123456789",
                              null,
                              null,
                              memberIdOwner);
                      customer = customerRepository.save(customer);
                      customerIdWithEntries = customer.getId();

                      var emptyCustomer =
                          new Customer(
                              "Empty Customer",
                              "empty@test.com",
                              "0000000000",
                              null,
                              null,
                              memberIdOwner);
                      emptyCustomer = customerRepository.save(emptyCustomer);
                      customerIdEmpty = emptyCustomer.getId();

                      var projectA = new Project("Project Alpha", "Desc A", memberIdOwner);
                      projectA = projectRepository.save(projectA);
                      projectIdA = projectA.getId();

                      var projectB = new Project("Project Beta", "Desc B", memberIdOwner);
                      projectB = projectRepository.save(projectB);
                      projectIdB = projectB.getId();

                      var projectC = new Project("Project Gamma", "Desc C", memberIdOwner);
                      projectC = projectRepository.save(projectC);
                      projectIdC = projectC.getId();

                      var taskA =
                          new Task(
                              projectIdA, "Task A1", "Desc", "MEDIUM", "TASK", null, memberIdOwner);
                      taskA = taskRepository.save(taskA);

                      var taskB =
                          new Task(
                              projectIdB, "Task B1", "Desc", "MEDIUM", "TASK", null, memberIdOwner);
                      taskB = taskRepository.save(taskB);

                      var taskC =
                          new Task(
                              projectIdC, "Task C1", "Desc", "MEDIUM", "TASK", null, memberIdOwner);
                      taskC = taskRepository.save(taskC);

                      // Flush all parent entities before creating child records
                      entityManager.flush();

                      // === Phase 2: Create invoice (needs customer FK) ===
                      var invoice =
                          new Invoice(
                              customerIdWithEntries,
                              "ZAR",
                              "Test Customer",
                              "customer@test.com",
                              null,
                              "Test Org",
                              memberIdOwner);
                      invoice = invoiceRepository.saveAndFlush(invoice);

                      // === Phase 3: Create time entries (needs task + invoice FKs) ===

                      // Project A: 2 billable unbilled entries
                      var entry1 =
                          new TimeEntry(
                              taskA.getId(),
                              memberIdOwner,
                              LocalDate.of(2025, 6, 1),
                              120,
                              true,
                              null,
                              "Billable work 1");
                      entry1.snapshotBillingRate(new BigDecimal("1500.00"), "ZAR");
                      timeEntryRepository.save(entry1);

                      var entry2 =
                          new TimeEntry(
                              taskA.getId(),
                              memberIdOwner,
                              LocalDate.of(2025, 6, 2),
                              60,
                              true,
                              null,
                              "Billable work 2");
                      entry2.snapshotBillingRate(new BigDecimal("1500.00"), "ZAR");
                      timeEntryRepository.save(entry2);

                      // Project B: all entries invoiced
                      var invoicedEntry =
                          new TimeEntry(
                              taskB.getId(),
                              memberIdOwner,
                              LocalDate.of(2025, 6, 3),
                              90,
                              true,
                              null,
                              "Invoiced work");
                      invoicedEntry.snapshotBillingRate(new BigDecimal("1500.00"), "ZAR");
                      invoicedEntry.setInvoiceId(invoice.getId());
                      timeEntryRepository.save(invoicedEntry);

                      // Project C: mixed billable and non-billable
                      var billableEntry =
                          new TimeEntry(
                              taskC.getId(),
                              memberIdOwner,
                              LocalDate.of(2025, 6, 4),
                              90,
                              true,
                              null,
                              "Billable");
                      billableEntry.snapshotBillingRate(new BigDecimal("2000.00"), "ZAR");
                      timeEntryRepository.save(billableEntry);

                      var nonBillable =
                          new TimeEntry(
                              taskC.getId(),
                              memberIdOwner,
                              LocalDate.of(2025, 6, 5),
                              60,
                              false,
                              null,
                              "Admin work");
                      timeEntryRepository.save(nonBillable);

                      // === Phase 4: Link projects to customer ===
                      customerProjectRepository.save(
                          new CustomerProject(customerIdWithEntries, projectIdA, memberIdOwner));
                      customerProjectRepository.save(
                          new CustomerProject(customerIdWithEntries, projectIdC, memberIdOwner));
                    }));
  }

  @Test
  @Order(1)
  void projectUnbilledSummary_withBillableEntries_returnsCorrectTotals() throws Exception {
    // Project A: 2 entries, 120+60=180 min = 3.00 hours, amount = 3000+1500 = 4500
    mockMvc
        .perform(get("/api/projects/" + projectIdA + "/unbilled-summary").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalHours").value(3.0))
        .andExpect(jsonPath("$.totalAmount").value(4500.0))
        .andExpect(jsonPath("$.entryCount").value(2))
        .andExpect(jsonPath("$.currency").value("ZAR"))
        .andExpect(jsonPath("$.byProject").doesNotExist());
  }

  @Test
  @Order(2)
  void projectUnbilledSummary_allInvoiced_returnsZeros() throws Exception {
    // Project B: all entries have invoice_id set
    mockMvc
        .perform(get("/api/projects/" + projectIdB + "/unbilled-summary").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalHours").value(0))
        .andExpect(jsonPath("$.totalAmount").value(0))
        .andExpect(jsonPath("$.entryCount").value(0))
        .andExpect(jsonPath("$.byProject").doesNotExist());
  }

  @Test
  @Order(3)
  void customerUnbilledSummary_withProjects_returnsBreakdown() throws Exception {
    // Customer linked to Project A (2 entries) and Project C (1 billable entry)
    // Total: 3 entries, 180+90=270 min = 4.50 hours, amount = 4500+3000 = 7500
    mockMvc
        .perform(
            get("/api/customers/" + customerIdWithEntries + "/unbilled-summary").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.entryCount").value(3))
        .andExpect(jsonPath("$.byProject").isArray())
        .andExpect(jsonPath("$.byProject.length()").value(2));
  }

  @Test
  @Order(4)
  void customerUnbilledSummary_noEntries_returnsZeros() throws Exception {
    mockMvc
        .perform(get("/api/customers/" + customerIdEmpty + "/unbilled-summary").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalHours").value(0))
        .andExpect(jsonPath("$.totalAmount").value(0))
        .andExpect(jsonPath("$.entryCount").value(0))
        .andExpect(jsonPath("$.byProject").doesNotExist());
  }

  @Test
  @Order(5)
  void projectUnbilledSummary_unauthenticated_returns401() throws Exception {
    mockMvc
        .perform(get("/api/projects/" + projectIdA + "/unbilled-summary"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @Order(6)
  void customerUnbilledSummary_unauthenticated_returns401() throws Exception {
    mockMvc
        .perform(get("/api/customers/" + customerIdWithEntries + "/unbilled-summary"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @Order(7)
  void projectUnbilledSummary_mixedBillableAndNonBillable_onlyCountsBillable() throws Exception {
    // Project C: 1 billable (90 min, rate 2000) + 1 non-billable
    // Only billable counted: 90 min = 1.50 hours, amount = (90/60)*2000 = 3000
    mockMvc
        .perform(get("/api/projects/" + projectIdC + "/unbilled-summary").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalHours").value(1.5))
        .andExpect(jsonPath("$.totalAmount").value(3000.0))
        .andExpect(jsonPath("$.entryCount").value(1))
        .andExpect(jsonPath("$.byProject").doesNotExist());
  }

  @Test
  @Order(8)
  void projectUnbilledSummary_nonExistentProject_returns404() throws Exception {
    var nonExistent = UUID.randomUUID();
    mockMvc
        .perform(get("/api/projects/" + nonExistent + "/unbilled-summary").with(ownerJwt()))
        .andExpect(status().isNotFound());
  }

  @Test
  @Order(9)
  void customerUnbilledSummary_nonExistentCustomer_returns404() throws Exception {
    var nonExistent = UUID.randomUUID();
    mockMvc
        .perform(get("/api/customers/" + nonExistent + "/unbilled-summary").with(ownerJwt()))
        .andExpect(status().isNotFound());
  }

  @Test
  @Order(10)
  void projectUnbilledSummary_memberRole_returns403() throws Exception {
    mockMvc
        .perform(get("/api/projects/" + projectIdA + "/unbilled-summary").with(memberJwt()))
        .andExpect(status().isForbidden());
  }

  @Test
  @Order(11)
  void customerUnbilledSummary_memberRole_returns403() throws Exception {
    mockMvc
        .perform(
            get("/api/customers/" + customerIdWithEntries + "/unbilled-summary").with(memberJwt()))
        .andExpect(status().isForbidden());
  }

  // --- Helpers ---

  private JwtRequestPostProcessor ownerJwt() {
    return jwt()
        .jwt(j -> j.subject("user_unbilled_owner").claim("o", Map.of("id", ORG_ID, "rol", "owner")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_OWNER")));
  }

  private JwtRequestPostProcessor memberJwt() {
    return jwt()
        .jwt(
            j ->
                j.subject("user_unbilled_member").claim("o", Map.of("id", ORG_ID, "rol", "member")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_MEMBER")));
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
