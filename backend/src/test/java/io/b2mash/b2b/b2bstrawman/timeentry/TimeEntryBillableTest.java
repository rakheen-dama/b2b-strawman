package io.b2mash.b2b.b2bstrawman.timeentry;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.billingrate.BillingRateService;
import io.b2mash.b2b.b2bstrawman.costrate.CostRateService;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
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
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TimeEntryBillableTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_billable_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private BillingRateService billingRateService;
  @Autowired private CostRateService costRateService;

  private String tenantSchema;
  private UUID memberIdOwner;
  private UUID memberIdMember;
  private String projectId;
  private String taskId;
  private String billableEntryId;
  private String nonBillableEntryId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Billable Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");

    memberIdOwner =
        UUID.fromString(
            syncMember(ORG_ID, "user_bill_owner", "bill_owner@test.com", "Bill Owner", "owner"));
    memberIdMember =
        UUID.fromString(
            syncMember(
                ORG_ID, "user_bill_member", "bill_member@test.com", "Bill Member", "member"));

    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

    // Create project
    var projectResult =
        mockMvc
            .perform(
                post("/api/projects")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "Billable Test Project", "description": "For billable tests"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    projectId = extractIdFromLocation(projectResult);

    // Add member to project
    mockMvc
        .perform(
            post("/api/projects/" + projectId + "/members")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"memberId": "%s"}
                    """
                        .formatted(memberIdMember)))
        .andExpect(status().isCreated());

    // Create task
    var taskResult =
        mockMvc
            .perform(
                post("/api/projects/" + projectId + "/tasks")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"title": "Billable Test Task", "priority": "HIGH"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    taskId = extractIdFromLocation(taskResult);

    // Create billing and cost rates for owner
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberIdOwner)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () -> {
              billingRateService.createRate(
                  memberIdOwner,
                  null,
                  null,
                  "USD",
                  new BigDecimal("150.00"),
                  LocalDate.of(2024, 1, 1),
                  null,
                  memberIdOwner,
                  "owner");
              costRateService.createCostRate(
                  memberIdOwner,
                  "USD",
                  new BigDecimal("80.00"),
                  LocalDate.of(2024, 1, 1),
                  null,
                  memberIdOwner,
                  "owner");
            });

    // Create a billable entry
    var billableResult =
        mockMvc
            .perform(
                post("/api/tasks/" + taskId + "/time-entries")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "date": "2024-06-15",
                          "durationMinutes": 60,
                          "billable": true,
                          "description": "Billable work"
                        }
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    billableEntryId =
        JsonPath.read(billableResult.getResponse().getContentAsString(), "$.id").toString();

    // Create a non-billable entry
    var nonBillableResult =
        mockMvc
            .perform(
                post("/api/tasks/" + taskId + "/time-entries")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "date": "2024-06-16",
                          "durationMinutes": 90,
                          "billable": false,
                          "description": "Non-billable work"
                        }
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    nonBillableEntryId =
        JsonPath.read(nonBillableResult.getResponse().getContentAsString(), "$.id").toString();
  }

  @Test
  @Order(1)
  void patchBillable_toggleFromTrueToFalse() throws Exception {
    mockMvc
        .perform(
            patch("/api/projects/" + projectId + "/time-entries/" + billableEntryId + "/billable")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"billable": false}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.billable").value(false))
        .andExpect(jsonPath("$.billableValue").doesNotExist())
        .andExpect(jsonPath("$.costValue").isNumber());
  }

  @Test
  @Order(2)
  void patchBillable_toggleBackToTrue() throws Exception {
    mockMvc
        .perform(
            patch("/api/projects/" + projectId + "/time-entries/" + billableEntryId + "/billable")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"billable": true}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.billable").value(true))
        .andExpect(jsonPath("$.billableValue").isNumber())
        .andExpect(jsonPath("$.costValue").isNumber());
  }

  @Test
  @Order(3)
  void patchBillable_byNonAuthorizedUser_returns403() throws Exception {
    // member trying to toggle billable on owner's entry should be forbidden
    mockMvc
        .perform(
            patch("/api/projects/" + projectId + "/time-entries/" + billableEntryId + "/billable")
                .with(memberJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"billable": false}
                    """))
        .andExpect(status().isForbidden());
  }

  @Test
  @Order(4)
  void createEntry_notBillable_nullBillableValueButHasCostValue() throws Exception {
    // The non-billable entry created in setup should have null billableValue but costValue present
    mockMvc
        .perform(
            get("/api/tasks/" + taskId + "/time-entries")
                .with(ownerJwt())
                .param("billable", "false"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].billable").value(false))
        .andExpect(jsonPath("$[0].billableValue").doesNotExist())
        .andExpect(jsonPath("$[0].costValue").isNumber());
  }

  @Test
  @Order(5)
  void listEntries_filteredByBillableTrue_returnsOnlyBillable() throws Exception {
    mockMvc
        .perform(
            get("/api/tasks/" + taskId + "/time-entries")
                .with(ownerJwt())
                .param("billable", "true"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].billable").value(true))
        .andExpect(jsonPath("$[0].id").value(billableEntryId));
  }

  @Test
  @Order(6)
  void listEntries_filteredByBillableFalse_returnsOnlyNonBillable() throws Exception {
    mockMvc
        .perform(
            get("/api/tasks/" + taskId + "/time-entries")
                .with(ownerJwt())
                .param("billable", "false"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].billable").value(false))
        .andExpect(jsonPath("$[0].id").value(nonBillableEntryId));
  }

  // --- JWT helpers ---

  private JwtRequestPostProcessor ownerJwt() {
    return jwt()
        .jwt(j -> j.subject("user_bill_owner").claim("o", Map.of("id", ORG_ID, "rol", "owner")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_OWNER")));
  }

  private JwtRequestPostProcessor memberJwt() {
    return jwt()
        .jwt(j -> j.subject("user_bill_member").claim("o", Map.of("id", ORG_ID, "rol", "member")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_MEMBER")));
  }

  // --- Helpers ---

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

  private String extractIdFromLocation(MvcResult result) {
    String location = result.getResponse().getHeader("Location");
    return location.substring(location.lastIndexOf('/') + 1);
  }
}
