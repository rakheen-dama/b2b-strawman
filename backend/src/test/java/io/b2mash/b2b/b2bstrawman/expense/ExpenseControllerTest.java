package io.b2mash.b2b.b2bstrawman.expense;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestPostgresConfiguration;
import io.b2mash.b2b.b2bstrawman.member.MemberRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.orgrole.OrgRoleRepository;
import io.b2mash.b2b.b2bstrawman.orgrole.OrgRoleService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestEntityHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestPostgresConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ExpenseControllerTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_expense_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private OrgRoleService orgRoleService;
  @Autowired private OrgRoleRepository orgRoleRepository;
  @Autowired private MemberRepository memberRepository;

  private String projectId;
  private String projectId2;
  private String memberIdOwner;
  private String memberIdAdmin;
  private String memberIdMember;
  private String memberIdMember2;
  private UUID customRoleMemberId;
  private UUID noCapMemberId;

  @BeforeAll
  void provisionTenantAndSeedData() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Expense Test Org", null);

    memberIdOwner =
        TestMemberHelper.syncMember(
            mockMvc, ORG_ID, "user_exp_owner", "exp_owner@test.com", "EXP Owner", "owner");
    memberIdAdmin =
        TestMemberHelper.syncMember(
            mockMvc, ORG_ID, "user_exp_admin", "exp_admin@test.com", "EXP Admin", "admin");
    memberIdMember =
        TestMemberHelper.syncMember(
            mockMvc, ORG_ID, "user_exp_member", "exp_member@test.com", "EXP Member", "member");
    memberIdMember2 =
        TestMemberHelper.syncMember(
            mockMvc, ORG_ID, "user_exp_member2", "exp_member2@test.com", "EXP Member2", "member");

    // Create project and add all members
    var projectResult =
        mockMvc
            .perform(
                post("/api/projects")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_exp_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "Expense Test Project", "description": "For expense tests"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    projectId = TestEntityHelper.extractIdFromLocation(projectResult);

    for (String mid : List.of(memberIdAdmin, memberIdMember, memberIdMember2)) {
      mockMvc
          .perform(
              post("/api/projects/" + projectId + "/members")
                  .with(TestJwtFactory.ownerJwt(ORG_ID, "user_exp_owner"))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      """
                      {"memberId": "%s"}
                      """
                          .formatted(mid)))
          .andExpect(status().isCreated());
    }

    // Create a second project for cross-project 404 test
    var project2Result =
        mockMvc
            .perform(
                post("/api/projects")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_exp_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "Expense Test Project 2", "description": "Second project"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    projectId2 = TestEntityHelper.extractIdFromLocation(project2Result);

    // Assign system owner/admin roles for capability-based auth
    var tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, UUID.fromString(memberIdOwner))
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () -> {
              var ownerRole =
                  orgRoleRepository.findAll().stream()
                      .filter(r -> r.isSystem() && "owner".equals(r.getSlug()))
                      .findFirst()
                      .orElseThrow();
              var ownerMember =
                  memberRepository.findById(UUID.fromString(memberIdOwner)).orElseThrow();
              ownerMember.setOrgRoleEntity(ownerRole);
              memberRepository.save(ownerMember);

              var adminRole =
                  orgRoleRepository.findAll().stream()
                      .filter(r -> r.isSystem() && "admin".equals(r.getSlug()))
                      .findFirst()
                      .orElseThrow();
              var adminMember =
                  memberRepository.findById(UUID.fromString(memberIdAdmin)).orElseThrow();
              adminMember.setOrgRoleEntity(adminRole);
              memberRepository.save(adminMember);
            });

    // Sync custom-role members for capability tests
    customRoleMemberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                ORG_ID,
                "user_exp_314a_custom",
                "exp_custom@test.com",
                "EXP Custom User",
                "member"));
    noCapMemberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                ORG_ID,
                "user_exp_314a_nocap",
                "exp_nocap@test.com",
                "EXP NoCap User",
                "member"));

    // Add custom role members to the project so they can create expenses for the write-off test
    for (String mid : List.of(customRoleMemberId.toString(), noCapMemberId.toString())) {
      mockMvc
          .perform(
              post("/api/projects/" + projectId + "/members")
                  .with(TestJwtFactory.ownerJwt(ORG_ID, "user_exp_owner"))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      """
                      {"memberId": "%s"}
                      """
                          .formatted(mid)))
          .andExpect(status().isCreated());
    }

    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, UUID.fromString(memberIdOwner))
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () -> {
              var withCapRole =
                  orgRoleService.createRole(
                      new io.b2mash.b2b.b2bstrawman.orgrole.dto.OrgRoleDtos.CreateOrgRoleRequest(
                          "Financials Viewer",
                          "Can view financials",
                          Set.of("FINANCIAL_VISIBILITY")));
              var customMember = memberRepository.findById(customRoleMemberId).orElseThrow();
              customMember.setOrgRoleEntity(
                  orgRoleRepository.findById(withCapRole.id()).orElseThrow());
              memberRepository.save(customMember);

              var withoutCapRole =
                  orgRoleService.createRole(
                      new io.b2mash.b2b.b2bstrawman.orgrole.dto.OrgRoleDtos.CreateOrgRoleRequest(
                          "Team Lead", "Can manage teams", Set.of("TEAM_OVERSIGHT")));
              var noCapMember = memberRepository.findById(noCapMemberId).orElseThrow();
              noCapMember.setOrgRoleEntity(
                  orgRoleRepository.findById(withoutCapRole.id()).orElseThrow());
              memberRepository.save(noCapMember);
            });
  }

  // ==================== 219.11: CRUD happy paths ====================

  @Test
  void shouldCreateExpense() throws Exception {
    mockMvc
        .perform(
            post("/api/projects/" + projectId + "/expenses")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_exp_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "date": "2026-02-15",
                      "description": "Court filing fee",
                      "amount": 150.00,
                      "category": "FILING_FEE"
                    }
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").exists())
        .andExpect(jsonPath("$.projectId").value(projectId))
        .andExpect(jsonPath("$.memberId").value(memberIdOwner))
        .andExpect(jsonPath("$.memberName").value("EXP Owner"))
        .andExpect(jsonPath("$.date").value("2026-02-15"))
        .andExpect(jsonPath("$.description").value("Court filing fee"))
        .andExpect(jsonPath("$.amount").value(150.00))
        .andExpect(jsonPath("$.category").value("FILING_FEE"))
        .andExpect(jsonPath("$.billable").value(true))
        .andExpect(jsonPath("$.billingStatus").value("UNBILLED"))
        .andExpect(jsonPath("$.billableAmount").value(150.00));
  }

  @Test
  void shouldCreateExpenseWithAllOptionalFields() throws Exception {
    mockMvc
        .perform(
            post("/api/projects/" + projectId + "/expenses")
                .with(TestJwtFactory.jwtAs(ORG_ID, "user_exp_member", "member"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "date": "2026-02-16",
                      "description": "Travel to client site",
                      "amount": 500.00,
                      "currency": "USD",
                      "category": "TRAVEL",
                      "markupPercent": 20.00,
                      "billable": true,
                      "notes": "Flight and hotel for client meeting"
                    }
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.memberId").value(memberIdMember))
        .andExpect(jsonPath("$.memberName").value("EXP Member"))
        .andExpect(jsonPath("$.currency").value("USD"))
        .andExpect(jsonPath("$.category").value("TRAVEL"))
        .andExpect(jsonPath("$.markupPercent").value(20.00))
        .andExpect(jsonPath("$.billable").value(true))
        .andExpect(jsonPath("$.billableAmount").value(600.00))
        .andExpect(jsonPath("$.notes").value("Flight and hotel for client meeting"));
  }

  @Test
  void shouldListExpensesForProject() throws Exception {
    // Create two expenses
    createExpense(
        TestJwtFactory.jwtAs(ORG_ID, "user_exp_member", "member"),
        "Filing fee 1",
        "250.00",
        "FILING_FEE");
    createExpense(
        TestJwtFactory.jwtAs(ORG_ID, "user_exp_member", "member"),
        "Courier service",
        "80.00",
        "COURIER");

    mockMvc
        .perform(
            get("/api/projects/" + projectId + "/expenses")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_exp_owner"))
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isArray())
        .andExpect(jsonPath("$.page.totalElements").isNumber());
  }

  @Test
  void shouldListExpensesWithCategoryFilter() throws Exception {
    // Create expenses with different categories
    createExpense(
        TestJwtFactory.ownerJwt(ORG_ID, "user_exp_owner"),
        "Software license",
        "100.00",
        "SOFTWARE");
    createExpense(
        TestJwtFactory.ownerJwt(ORG_ID, "user_exp_owner"),
        "Another software",
        "200.00",
        "SOFTWARE");
    createExpense(
        TestJwtFactory.ownerJwt(ORG_ID, "user_exp_owner"), "Printing cost", "50.00", "PRINTING");

    mockMvc
        .perform(
            get("/api/projects/" + projectId + "/expenses")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_exp_owner"))
                .param("category", "SOFTWARE"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isArray());
  }

  @Test
  void shouldGetSingleExpense() throws Exception {
    var expenseId =
        createExpense(
            TestJwtFactory.ownerJwt(ORG_ID, "user_exp_owner"),
            "Single expense test",
            "300.00",
            "OTHER");

    mockMvc
        .perform(
            get("/api/projects/" + projectId + "/expenses/" + expenseId)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_exp_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(expenseId))
        .andExpect(jsonPath("$.description").value("Single expense test"))
        .andExpect(jsonPath("$.amount").value(300.00))
        .andExpect(jsonPath("$.memberName").value("EXP Owner"));
  }

  @Test
  void shouldGetMyExpenses() throws Exception {
    // Create expenses as member
    createExpense(
        TestJwtFactory.jwtAs(ORG_ID, "user_exp_member2", "member"),
        "My expense 1",
        "100.00",
        "OTHER");
    createExpense(
        TestJwtFactory.jwtAs(ORG_ID, "user_exp_member2", "member"),
        "My expense 2",
        "200.00",
        "TRAVEL");

    mockMvc
        .perform(
            get("/api/expenses/mine")
                .with(TestJwtFactory.jwtAs(ORG_ID, "user_exp_member2", "member")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isArray())
        .andExpect(jsonPath("$.page.totalElements").isNumber());
  }

  @Test
  void shouldReturn404ForNonexistentExpense() throws Exception {
    var fakeId = UUID.randomUUID().toString();

    mockMvc
        .perform(
            get("/api/projects/" + projectId + "/expenses/" + fakeId)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_exp_owner")))
        .andExpect(status().isNotFound());
  }

  @Test
  void shouldReturn404ForExpenseInWrongProject() throws Exception {
    var expenseId =
        createExpense(
            TestJwtFactory.ownerJwt(ORG_ID, "user_exp_owner"),
            "Wrong project test",
            "100.00",
            "OTHER");

    // Try to get the expense using a different project
    mockMvc
        .perform(
            get("/api/projects/" + projectId2 + "/expenses/" + expenseId)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_exp_owner")))
        .andExpect(status().isNotFound());
  }

  // ==================== 219.12: Update and delete ====================

  @Test
  void creatorCanUpdateOwnExpense() throws Exception {
    var expenseId =
        createExpense(
            TestJwtFactory.jwtAs(ORG_ID, "user_exp_member", "member"),
            "Original description",
            "100.00",
            "OTHER");

    mockMvc
        .perform(
            put("/api/projects/" + projectId + "/expenses/" + expenseId)
                .with(TestJwtFactory.jwtAs(ORG_ID, "user_exp_member", "member"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "description": "Updated description",
                      "amount": 200.00,
                      "category": "TRAVEL"
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.description").value("Updated description"))
        .andExpect(jsonPath("$.amount").value(200.00))
        .andExpect(jsonPath("$.category").value("TRAVEL"));
  }

  @Test
  void adminCanUpdateAnyExpense() throws Exception {
    var expenseId =
        createExpense(
            TestJwtFactory.jwtAs(ORG_ID, "user_exp_member", "member"),
            "Member's expense",
            "100.00",
            "COURIER");

    mockMvc
        .perform(
            put("/api/projects/" + projectId + "/expenses/" + expenseId)
                .with(TestJwtFactory.adminJwt(ORG_ID, "user_exp_admin"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"description": "Admin updated this"}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.description").value("Admin updated this"));
  }

  @Test
  void memberCannotUpdateOthersExpense() throws Exception {
    var expenseId =
        createExpense(
            TestJwtFactory.jwtAs(ORG_ID, "user_exp_member", "member"),
            "Cannot touch this",
            "100.00",
            "OTHER");

    mockMvc
        .perform(
            put("/api/projects/" + projectId + "/expenses/" + expenseId)
                .with(TestJwtFactory.jwtAs(ORG_ID, "user_exp_member2", "member"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"description": "Hijacked"}
                    """))
        .andExpect(status().isForbidden());
  }

  @Test
  void creatorCanDeleteOwnExpense() throws Exception {
    var expenseId =
        createExpense(
            TestJwtFactory.jwtAs(ORG_ID, "user_exp_member", "member"),
            "Delete me",
            "50.00",
            "OTHER");

    mockMvc
        .perform(
            delete("/api/projects/" + projectId + "/expenses/" + expenseId)
                .with(TestJwtFactory.jwtAs(ORG_ID, "user_exp_member", "member")))
        .andExpect(status().isNoContent());

    // Verify it's gone
    mockMvc
        .perform(
            get("/api/projects/" + projectId + "/expenses/" + expenseId)
                .with(TestJwtFactory.jwtAs(ORG_ID, "user_exp_member", "member")))
        .andExpect(status().isNotFound());
  }

  @Test
  void adminCanDeleteAnyExpense() throws Exception {
    var expenseId =
        createExpense(
            TestJwtFactory.jwtAs(ORG_ID, "user_exp_member", "member"),
            "Admin will delete",
            "75.00",
            "PRINTING");

    mockMvc
        .perform(
            delete("/api/projects/" + projectId + "/expenses/" + expenseId)
                .with(TestJwtFactory.adminJwt(ORG_ID, "user_exp_admin")))
        .andExpect(status().isNoContent());
  }

  @Test
  void memberCannotDeleteOthersExpense() throws Exception {
    var expenseId =
        createExpense(
            TestJwtFactory.jwtAs(ORG_ID, "user_exp_member", "member"),
            "Protected expense",
            "100.00",
            "OTHER");

    mockMvc
        .perform(
            delete("/api/projects/" + projectId + "/expenses/" + expenseId)
                .with(TestJwtFactory.jwtAs(ORG_ID, "user_exp_member2", "member")))
        .andExpect(status().isForbidden());
  }

  // ==================== 219.13: Write-off and restore ====================

  @Test
  void adminCanWriteOffExpense() throws Exception {
    var expenseId =
        createExpense(
            TestJwtFactory.ownerJwt(ORG_ID, "user_exp_owner"), "Write off test", "200.00", "OTHER");

    mockMvc
        .perform(
            patch("/api/projects/" + projectId + "/expenses/" + expenseId + "/write-off")
                .with(TestJwtFactory.adminJwt(ORG_ID, "user_exp_admin")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.billable").value(false))
        .andExpect(jsonPath("$.billingStatus").value("NON_BILLABLE"))
        .andExpect(jsonPath("$.billableAmount").value(0));
  }

  @Test
  void memberCannotWriteOffExpense() throws Exception {
    var expenseId =
        createExpense(
            TestJwtFactory.jwtAs(ORG_ID, "user_exp_member", "member"),
            "Member cannot write off",
            "100.00",
            "OTHER");

    mockMvc
        .perform(
            patch("/api/projects/" + projectId + "/expenses/" + expenseId + "/write-off")
                .with(TestJwtFactory.jwtAs(ORG_ID, "user_exp_member", "member")))
        .andExpect(status().isForbidden());
  }

  @Test
  void writeOffAlreadyNonBillableReturns409() throws Exception {
    // Create a non-billable expense
    var result =
        mockMvc
            .perform(
                post("/api/projects/" + projectId + "/expenses")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_exp_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "date": "2026-02-20",
                          "description": "Already non-billable",
                          "amount": 100.00,
                          "category": "OTHER",
                          "billable": false
                        }
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    var expenseId = JsonPath.read(result.getResponse().getContentAsString(), "$.id").toString();

    mockMvc
        .perform(
            patch("/api/projects/" + projectId + "/expenses/" + expenseId + "/write-off")
                .with(TestJwtFactory.adminJwt(ORG_ID, "user_exp_admin")))
        .andExpect(status().isConflict());
  }

  @Test
  void adminCanRestoreExpense() throws Exception {
    var expenseId =
        createExpense(
            TestJwtFactory.ownerJwt(ORG_ID, "user_exp_owner"), "Restore test", "150.00", "OTHER");

    // First write it off
    mockMvc
        .perform(
            patch("/api/projects/" + projectId + "/expenses/" + expenseId + "/write-off")
                .with(TestJwtFactory.adminJwt(ORG_ID, "user_exp_admin")))
        .andExpect(status().isOk());

    // Then restore it
    mockMvc
        .perform(
            patch("/api/projects/" + projectId + "/expenses/" + expenseId + "/restore")
                .with(TestJwtFactory.adminJwt(ORG_ID, "user_exp_admin")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.billable").value(true))
        .andExpect(jsonPath("$.billingStatus").value("UNBILLED"));
  }

  @Test
  void memberCannotRestoreExpense() throws Exception {
    var expenseId =
        createExpense(
            TestJwtFactory.ownerJwt(ORG_ID, "user_exp_owner"),
            "Member cannot restore",
            "100.00",
            "OTHER");

    // Write it off first as admin
    mockMvc
        .perform(
            patch("/api/projects/" + projectId + "/expenses/" + expenseId + "/write-off")
                .with(TestJwtFactory.adminJwt(ORG_ID, "user_exp_admin")))
        .andExpect(status().isOk());

    // Member tries to restore
    mockMvc
        .perform(
            patch("/api/projects/" + projectId + "/expenses/" + expenseId + "/restore")
                .with(TestJwtFactory.jwtAs(ORG_ID, "user_exp_member", "member")))
        .andExpect(status().isForbidden());
  }

  @Test
  void restoreAlreadyBillableReturns409() throws Exception {
    var expenseId =
        createExpense(
            TestJwtFactory.ownerJwt(ORG_ID, "user_exp_owner"),
            "Already billable",
            "100.00",
            "OTHER");

    // Try to restore an expense that is already billable
    mockMvc
        .perform(
            patch("/api/projects/" + projectId + "/expenses/" + expenseId + "/restore")
                .with(TestJwtFactory.adminJwt(ORG_ID, "user_exp_admin")))
        .andExpect(status().isConflict());
  }

  // ==================== Capability Tests ====================

  @Test
  void customRoleWithCapability_accessesWriteOffEndpoint_returns200() throws Exception {
    var expenseId =
        createExpense(
            TestJwtFactory.jwtAs(ORG_ID, "user_exp_314a_custom", "member"),
            "Cap write-off test",
            "100.00",
            "OTHER");

    mockMvc
        .perform(
            patch("/api/projects/" + projectId + "/expenses/" + expenseId + "/write-off")
                .with(TestJwtFactory.jwtAs(ORG_ID, "user_exp_314a_custom", "member")))
        .andExpect(status().isOk());
  }

  @Test
  void customRoleWithoutCapability_accessesWriteOffEndpoint_returns403() throws Exception {
    var expenseId =
        createExpense(
            TestJwtFactory.ownerJwt(ORG_ID, "user_exp_owner"),
            "NoCap write-off test",
            "100.00",
            "OTHER");

    mockMvc
        .perform(
            patch("/api/projects/" + projectId + "/expenses/" + expenseId + "/write-off")
                .with(TestJwtFactory.jwtAs(ORG_ID, "user_exp_314a_nocap", "member")))
        .andExpect(status().isForbidden());
  }

  // ==================== Helpers ====================

  private String createExpense(
      JwtRequestPostProcessor jwt, String description, String amount, String category)
      throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/projects/" + projectId + "/expenses")
                    .with(jwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "date": "2026-02-15",
                          "description": "%s",
                          "amount": %s,
                          "category": "%s"
                        }
                        """
                            .formatted(description, amount, category)))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.id").toString();
  }
}
