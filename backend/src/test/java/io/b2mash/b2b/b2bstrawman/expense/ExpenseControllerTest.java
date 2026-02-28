package io.b2mash.b2b.b2bstrawman.expense;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ExpenseControllerTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_expense_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;

  private String projectId;
  private String projectId2;
  private String memberIdOwner;
  private String memberIdAdmin;
  private String memberIdMember;
  private String memberIdMember2;

  @BeforeAll
  void provisionTenantAndSeedData() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Expense Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");

    memberIdOwner = syncMember("user_exp_owner", "exp_owner@test.com", "EXP Owner", "owner");
    memberIdAdmin = syncMember("user_exp_admin", "exp_admin@test.com", "EXP Admin", "admin");
    memberIdMember = syncMember("user_exp_member", "exp_member@test.com", "EXP Member", "member");
    memberIdMember2 =
        syncMember("user_exp_member2", "exp_member2@test.com", "EXP Member2", "member");

    // Create project and add all members
    var projectResult =
        mockMvc
            .perform(
                post("/api/projects")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "Expense Test Project", "description": "For expense tests"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    projectId = extractIdFromLocation(projectResult);

    for (String mid : List.of(memberIdAdmin, memberIdMember, memberIdMember2)) {
      mockMvc
          .perform(
              post("/api/projects/" + projectId + "/members")
                  .with(ownerJwt())
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
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "Expense Test Project 2", "description": "Second project"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    projectId2 = extractIdFromLocation(project2Result);
  }

  // ==================== 219.11: CRUD happy paths ====================

  @Test
  void shouldCreateExpense() throws Exception {
    mockMvc
        .perform(
            post("/api/projects/" + projectId + "/expenses")
                .with(ownerJwt())
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
                .with(memberJwt())
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
    createExpense(memberJwt(), "Filing fee 1", "250.00", "FILING_FEE");
    createExpense(memberJwt(), "Courier service", "80.00", "COURIER");

    mockMvc
        .perform(
            get("/api/projects/" + projectId + "/expenses")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isArray())
        .andExpect(jsonPath("$.page.totalElements").isNumber());
  }

  @Test
  void shouldListExpensesWithCategoryFilter() throws Exception {
    // Create expenses with different categories
    createExpense(ownerJwt(), "Software license", "100.00", "SOFTWARE");
    createExpense(ownerJwt(), "Another software", "200.00", "SOFTWARE");
    createExpense(ownerJwt(), "Printing cost", "50.00", "PRINTING");

    mockMvc
        .perform(
            get("/api/projects/" + projectId + "/expenses")
                .with(ownerJwt())
                .param("category", "SOFTWARE"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isArray());
  }

  @Test
  void shouldGetSingleExpense() throws Exception {
    var expenseId = createExpense(ownerJwt(), "Single expense test", "300.00", "OTHER");

    mockMvc
        .perform(get("/api/projects/" + projectId + "/expenses/" + expenseId).with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(expenseId))
        .andExpect(jsonPath("$.description").value("Single expense test"))
        .andExpect(jsonPath("$.amount").value(300.00))
        .andExpect(jsonPath("$.memberName").value("EXP Owner"));
  }

  @Test
  void shouldGetMyExpenses() throws Exception {
    // Create expenses as member
    createExpense(member2Jwt(), "My expense 1", "100.00", "OTHER");
    createExpense(member2Jwt(), "My expense 2", "200.00", "TRAVEL");

    mockMvc
        .perform(get("/api/expenses/mine").with(member2Jwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isArray())
        .andExpect(jsonPath("$.page.totalElements").isNumber());
  }

  @Test
  void shouldReturn404ForNonexistentExpense() throws Exception {
    var fakeId = UUID.randomUUID().toString();

    mockMvc
        .perform(get("/api/projects/" + projectId + "/expenses/" + fakeId).with(ownerJwt()))
        .andExpect(status().isNotFound());
  }

  @Test
  void shouldReturn404ForExpenseInWrongProject() throws Exception {
    var expenseId = createExpense(ownerJwt(), "Wrong project test", "100.00", "OTHER");

    // Try to get the expense using a different project
    mockMvc
        .perform(get("/api/projects/" + projectId2 + "/expenses/" + expenseId).with(ownerJwt()))
        .andExpect(status().isNotFound());
  }

  // ==================== 219.12: Update and delete ====================

  @Test
  void creatorCanUpdateOwnExpense() throws Exception {
    var expenseId = createExpense(memberJwt(), "Original description", "100.00", "OTHER");

    mockMvc
        .perform(
            put("/api/projects/" + projectId + "/expenses/" + expenseId)
                .with(memberJwt())
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
    var expenseId = createExpense(memberJwt(), "Member's expense", "100.00", "COURIER");

    mockMvc
        .perform(
            put("/api/projects/" + projectId + "/expenses/" + expenseId)
                .with(adminJwt())
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
    var expenseId = createExpense(memberJwt(), "Cannot touch this", "100.00", "OTHER");

    mockMvc
        .perform(
            put("/api/projects/" + projectId + "/expenses/" + expenseId)
                .with(member2Jwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"description": "Hijacked"}
                    """))
        .andExpect(status().isForbidden());
  }

  @Test
  void creatorCanDeleteOwnExpense() throws Exception {
    var expenseId = createExpense(memberJwt(), "Delete me", "50.00", "OTHER");

    mockMvc
        .perform(delete("/api/projects/" + projectId + "/expenses/" + expenseId).with(memberJwt()))
        .andExpect(status().isNoContent());

    // Verify it's gone
    mockMvc
        .perform(get("/api/projects/" + projectId + "/expenses/" + expenseId).with(memberJwt()))
        .andExpect(status().isNotFound());
  }

  @Test
  void adminCanDeleteAnyExpense() throws Exception {
    var expenseId = createExpense(memberJwt(), "Admin will delete", "75.00", "PRINTING");

    mockMvc
        .perform(delete("/api/projects/" + projectId + "/expenses/" + expenseId).with(adminJwt()))
        .andExpect(status().isNoContent());
  }

  @Test
  void memberCannotDeleteOthersExpense() throws Exception {
    var expenseId = createExpense(memberJwt(), "Protected expense", "100.00", "OTHER");

    mockMvc
        .perform(delete("/api/projects/" + projectId + "/expenses/" + expenseId).with(member2Jwt()))
        .andExpect(status().isForbidden());
  }

  // ==================== 219.13: Write-off and restore ====================

  @Test
  void adminCanWriteOffExpense() throws Exception {
    var expenseId = createExpense(ownerJwt(), "Write off test", "200.00", "OTHER");

    mockMvc
        .perform(
            patch("/api/projects/" + projectId + "/expenses/" + expenseId + "/write-off")
                .with(adminJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.billable").value(false))
        .andExpect(jsonPath("$.billingStatus").value("NON_BILLABLE"))
        .andExpect(jsonPath("$.billableAmount").value(0));
  }

  @Test
  void memberCannotWriteOffExpense() throws Exception {
    var expenseId = createExpense(memberJwt(), "Member cannot write off", "100.00", "OTHER");

    mockMvc
        .perform(
            patch("/api/projects/" + projectId + "/expenses/" + expenseId + "/write-off")
                .with(memberJwt()))
        .andExpect(status().isForbidden());
  }

  @Test
  void writeOffAlreadyNonBillableReturns409() throws Exception {
    // Create a non-billable expense
    var result =
        mockMvc
            .perform(
                post("/api/projects/" + projectId + "/expenses")
                    .with(ownerJwt())
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
                .with(adminJwt()))
        .andExpect(status().isConflict());
  }

  @Test
  void adminCanRestoreExpense() throws Exception {
    var expenseId = createExpense(ownerJwt(), "Restore test", "150.00", "OTHER");

    // First write it off
    mockMvc
        .perform(
            patch("/api/projects/" + projectId + "/expenses/" + expenseId + "/write-off")
                .with(adminJwt()))
        .andExpect(status().isOk());

    // Then restore it
    mockMvc
        .perform(
            patch("/api/projects/" + projectId + "/expenses/" + expenseId + "/restore")
                .with(adminJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.billable").value(true))
        .andExpect(jsonPath("$.billingStatus").value("UNBILLED"));
  }

  @Test
  void memberCannotRestoreExpense() throws Exception {
    var expenseId = createExpense(ownerJwt(), "Member cannot restore", "100.00", "OTHER");

    // Write it off first as admin
    mockMvc
        .perform(
            patch("/api/projects/" + projectId + "/expenses/" + expenseId + "/write-off")
                .with(adminJwt()))
        .andExpect(status().isOk());

    // Member tries to restore
    mockMvc
        .perform(
            patch("/api/projects/" + projectId + "/expenses/" + expenseId + "/restore")
                .with(memberJwt()))
        .andExpect(status().isForbidden());
  }

  @Test
  void restoreAlreadyBillableReturns409() throws Exception {
    var expenseId = createExpense(ownerJwt(), "Already billable", "100.00", "OTHER");

    // Try to restore an expense that is already billable
    mockMvc
        .perform(
            patch("/api/projects/" + projectId + "/expenses/" + expenseId + "/restore")
                .with(adminJwt()))
        .andExpect(status().isConflict());
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

  private String extractIdFromLocation(MvcResult result) {
    String location = result.getResponse().getHeader("Location");
    return location.substring(location.lastIndexOf('/') + 1);
  }

  private String syncMember(String clerkUserId, String email, String name, String orgRole)
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
                          "clerkOrgId": "%s", "clerkUserId": "%s", "email": "%s",
                          "name": "%s", "avatarUrl": null, "orgRole": "%s"
                        }
                        """
                            .formatted(ORG_ID, clerkUserId, email, name, orgRole)))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.memberId");
  }

  private JwtRequestPostProcessor ownerJwt() {
    return jwt()
        .jwt(j -> j.subject("user_exp_owner").claim("o", Map.of("id", ORG_ID, "rol", "owner")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_OWNER")));
  }

  private JwtRequestPostProcessor adminJwt() {
    return jwt()
        .jwt(j -> j.subject("user_exp_admin").claim("o", Map.of("id", ORG_ID, "rol", "admin")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_ADMIN")));
  }

  private JwtRequestPostProcessor memberJwt() {
    return jwt()
        .jwt(j -> j.subject("user_exp_member").claim("o", Map.of("id", ORG_ID, "rol", "member")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_MEMBER")));
  }

  private JwtRequestPostProcessor member2Jwt() {
    return jwt()
        .jwt(j -> j.subject("user_exp_member2").claim("o", Map.of("id", ORG_ID, "rol", "member")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_MEMBER")));
  }
}
