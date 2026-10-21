package io.b2mash.b2b.b2bstrawman.dashboard;

import static io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory.createActiveCustomer;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.member.ProjectMemberService;
import io.b2mash.b2b.b2bstrawman.multitenancy.ActorContext;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.project.ProjectService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsService;
import io.b2mash.b2b.b2bstrawman.task.TaskService;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import io.b2mash.b2b.b2bstrawman.timeentry.TimeEntryService;
import io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.DisbursementCategory;
import io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.DisbursementPaymentSource;
import io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.DisbursementRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.LegalDisbursement;
import io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.VatTreatment;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
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

/**
 * {@code GET /api/dashboard/unbilled-wip} (Phase 87, Epic 614A) — {@code
 * DashboardService.getUnbilledWip} redaction and aggregation, driven through MockMvc so
 * capabilities resolve exactly as in production.
 *
 * <p>Fixture (dedicated org): member1 (rate 600/h) logs 120 billable + 30 non-billable minutes;
 * member2 (rate 900/h) logs 90 billable minutes over two entries; the admin logs 60 billable
 * minutes without any billing rate (no snapshot → minutes count, value 0); one APPROVED+UNBILLED
 * disbursement (300.00) and one DRAFT disbursement (100.00, excluded).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DashboardServiceUnbilledWipTest {
  private static final String ORG_ID = "org_dash_unbilled_wip";
  private static final String OWNER = "user_duw_owner";
  private static final String ADMIN = "user_duw_admin";
  private static final String MEMBER1 = "user_duw_member1";
  private static final String MEMBER2 = "user_duw_member2";
  private static final String IDLE = "user_duw_idle";
  private static final String FIN = "user_duw_fin";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private OrgSettingsService orgSettingsService;
  @Autowired private OrgSettingsRepository orgSettingsRepository;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private ProjectService projectService;
  @Autowired private ProjectMemberService projectMemberService;
  @Autowired private TaskService taskService;
  @Autowired private TimeEntryService timeEntryService;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private ProjectRepository projectRepository;
  @Autowired private DisbursementRepository disbursementRepository;

  private final LocalDate today = LocalDate.now();

  private String tenantSchema;
  private String currency;
  private UUID adminId;
  private UUID member1Id;
  private UUID member2Id;
  private UUID idleId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Dashboard Unbilled WIP Org", null);
    sync(OWNER, "DUW Owner", "owner");
    adminId = sync(ADMIN, "DUW Admin", "admin");
    member1Id = sync(MEMBER1, "DUW Member One", "member");
    member2Id = sync(MEMBER2, "DUW Member Two", "member");
    idleId = sync(IDLE, "DUW Idle Member", "member");
    UUID finId = sync(FIN, "DUW Finance Viewer", "member");
    assignRole(finId, createCustomRole("DUW Finance Viewer Role", "FINANCIAL_VISIBILITY"));

    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
    asMember(
        adminId,
        "admin",
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var settings = orgSettingsService.getOrCreateForCurrentTenant();
                  settings.setEnabledModules(List.of("disbursements"));
                  orgSettingsRepository.save(settings);
                }));
    asMember(adminId, "admin", () -> currency = orgSettingsService.getDefaultCurrency());

    // Billing rates in the org default currency BEFORE any time entry, so entries snapshot them.
    createBillingRate(member1Id, "600.00");
    createBillingRate(member2Id, "900.00");

    asMember(adminId, "admin", this::seedTimeAndDisbursements);
  }

  private void seedTimeAndDisbursements() {
    var admin = new ActorContext(adminId, "admin");
    var matter = projectService.createProject("WIP Matter", "Unbilled WIP fixture", adminId);
    projectMemberService.addMember(matter.getId(), member1Id, adminId);
    projectMemberService.addMember(matter.getId(), member2Id, adminId);
    var task =
        taskService.createTask(matter.getId(), "WIP Task", null, "MEDIUM", "TASK", null, admin);

    // Admin: 60 billable minutes, no billing rate → null-currency row, value 0.
    timeEntryService.createTimeEntry(
        task.getId(), today.minusDays(4), 60, true, null, "Admin unrated", admin);

    asMember(
        member1Id,
        "member",
        () -> {
          var m1 = new ActorContext(member1Id, "member");
          timeEntryService.createTimeEntry(
              task.getId(), today.minusDays(10), 120, true, null, "M1 billable", m1);
          timeEntryService.createTimeEntry(
              task.getId(), today.minusDays(2), 30, false, null, "M1 non-billable", m1);
        });
    asMember(
        member2Id,
        "member",
        () -> {
          var m2 = new ActorContext(member2Id, "member");
          timeEntryService.createTimeEntry(
              task.getId(), today.minusDays(6), 60, true, null, "M2 billable a", m2);
          timeEntryService.createTimeEntry(
              task.getId(), today.minusDays(3), 30, true, null, "M2 billable b", m2);
        });

    transactionTemplate.executeWithoutResult(
        tx -> {
          var client =
              customerRepository.saveAndFlush(
                  createActiveCustomer("WIP Disb Client", "wip_disb@test.com", adminId));
          var disbMatter = new Project("WIP Disb Matter", "Disbursement fixture", adminId);
          disbMatter.setCustomerId(client.getId());
          disbMatter = projectRepository.saveAndFlush(disbMatter);
          var approved = disbursement(disbMatter.getId(), client.getId(), "300.00");
          approved.submitForApproval();
          approved.approve(adminId, "ok");
          disbursementRepository.saveAndFlush(approved);
          disbursementRepository.saveAndFlush(
              disbursement(disbMatter.getId(), client.getId(), "100.00")); // DRAFT — excluded
        });
  }

  @Test
  void adminSeesEveryFeeEarnerWithValues() throws Exception {
    mockMvc
        .perform(get("/api/dashboard/unbilled-wip").with(TestJwtFactory.adminJwt(ORG_ID, ADMIN)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.redacted").value(false))
        .andExpect(jsonPath("$.asOf").value(today.toString()))
        .andExpect(jsonPath("$.currency").value(currency))
        .andExpect(jsonPath("$.byFeeEarner", hasSize(3)))
        // Sorted by value desc: member2 (1350) > member1 (1200) > admin (0).
        .andExpect(jsonPath("$.byFeeEarner[0].memberId").value(member2Id.toString()))
        .andExpect(jsonPath("$.byFeeEarner[0].memberName").value("DUW Member Two"))
        .andExpect(jsonPath("$.byFeeEarner[0].minutes").value(90))
        .andExpect(jsonPath("$.byFeeEarner[0].value").value(1350.00))
        .andExpect(
            jsonPath("$.byFeeEarner[0].oldestEntryDate").value(today.minusDays(6).toString()))
        .andExpect(jsonPath("$.byFeeEarner[1].memberId").value(member1Id.toString()))
        // Non-billable 30 minutes excluded.
        .andExpect(jsonPath("$.byFeeEarner[1].minutes").value(120))
        .andExpect(jsonPath("$.byFeeEarner[1].value").value(1200.00))
        .andExpect(
            jsonPath("$.byFeeEarner[1].oldestEntryDate").value(today.minusDays(10).toString()))
        // Unrated time counts minutes but carries zero value.
        .andExpect(jsonPath("$.byFeeEarner[2].memberId").value(adminId.toString()))
        .andExpect(jsonPath("$.byFeeEarner[2].minutes").value(60))
        .andExpect(jsonPath("$.byFeeEarner[2].value").value(0.00))
        .andExpect(jsonPath("$.totalMinutes").value(270))
        .andExpect(jsonPath("$.totalValue").value(2550.00))
        // Only the APPROVED + UNBILLED disbursement; the DRAFT one is excluded.
        .andExpect(jsonPath("$.unbilledDisbursementTotal").value(300.00));
  }

  @Test
  void ownerSeesFullList() throws Exception {
    mockMvc
        .perform(get("/api/dashboard/unbilled-wip").with(TestJwtFactory.ownerJwt(ORG_ID, OWNER)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.redacted").value(false))
        .andExpect(jsonPath("$.byFeeEarner", hasSize(3)))
        .andExpect(jsonPath("$.totalValue").value(2550.00));
  }

  @Test
  void memberWithFinancialVisibilitySeesFullList() throws Exception {
    mockMvc
        .perform(get("/api/dashboard/unbilled-wip").with(TestJwtFactory.memberJwt(ORG_ID, FIN)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.redacted").value(false))
        .andExpect(jsonPath("$.byFeeEarner", hasSize(3)))
        .andExpect(jsonPath("$.totalValue").value(2550.00))
        .andExpect(jsonPath("$.unbilledDisbursementTotal").value(300.00));
  }

  @Test
  void plainMemberSeesOnlyOwnRowWithoutValues() throws Exception {
    mockMvc
        .perform(get("/api/dashboard/unbilled-wip").with(TestJwtFactory.memberJwt(ORG_ID, MEMBER1)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.redacted").value(true))
        .andExpect(jsonPath("$.currency").value(currency))
        .andExpect(jsonPath("$.byFeeEarner", hasSize(1)))
        .andExpect(jsonPath("$.byFeeEarner[0].memberId").value(member1Id.toString()))
        .andExpect(jsonPath("$.byFeeEarner[0].minutes").value(120))
        .andExpect(jsonPath("$.byFeeEarner[0].value").doesNotExist())
        .andExpect(
            jsonPath("$.byFeeEarner[0].oldestEntryDate").value(today.minusDays(10).toString()))
        .andExpect(jsonPath("$.byFeeEarner[*].memberId", not(hasItem(member2Id.toString()))))
        // Own minutes only — never the firm total (270).
        .andExpect(jsonPath("$.totalMinutes").value(120))
        .andExpect(jsonPath("$.totalValue").doesNotExist())
        .andExpect(jsonPath("$.unbilledDisbursementTotal").doesNotExist());
  }

  @Test
  void memberWithoutUnbilledTimeGetsEmptyRedactedResponse() throws Exception {
    mockMvc
        .perform(get("/api/dashboard/unbilled-wip").with(TestJwtFactory.memberJwt(ORG_ID, IDLE)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.redacted").value(true))
        .andExpect(jsonPath("$.byFeeEarner", hasSize(0)))
        .andExpect(jsonPath("$.totalMinutes").value(0))
        .andExpect(jsonPath("$.totalValue").doesNotExist());
  }

  // --- helpers ---

  private UUID sync(String subject, String name, String role) throws Exception {
    return UUID.fromString(
        TestMemberHelper.syncMember(mockMvc, ORG_ID, subject, subject + "@test.com", name, role));
  }

  private void asMember(UUID memberId, String role, Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberId)
        .where(RequestScopes.ORG_ROLE, role)
        .run(action);
  }

  private void createBillingRate(UUID memberId, String hourlyRate) throws Exception {
    mockMvc
        .perform(
            post("/api/billing-rates")
                .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"memberId": "%s", "currency": "%s", "hourlyRate": %s, "effectiveFrom": "2020-01-01"}
                    """
                        .formatted(memberId, currency, hourlyRate)))
        .andExpect(status().isCreated());
  }

  private String createCustomRole(String name, String capability) throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/org-roles")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name":"%s","description":"Test role","capabilities":["%s"]}"""
                            .formatted(name, capability)))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
  }

  private void assignRole(UUID memberId, String roleId) throws Exception {
    mockMvc
        .perform(
            put("/api/members/" + memberId + "/role")
                .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"orgRoleId\":\"%s\",\"capabilityOverrides\":[]}".formatted(roleId)))
        .andExpect(status().isOk());
  }

  private LegalDisbursement disbursement(UUID matterId, UUID clientId, String amount) {
    return new LegalDisbursement(
        matterId,
        clientId,
        DisbursementCategory.SHERIFF_FEES.name(),
        "WIP fixture disbursement " + amount,
        new BigDecimal(amount),
        VatTreatment.ZERO_RATED_PASS_THROUGH.name(),
        new BigDecimal("0.00"),
        DisbursementPaymentSource.OFFICE_ACCOUNT.name(),
        null,
        today.minusDays(1),
        "Sheriff",
        "REF-WIP",
        null,
        adminId);
  }
}
