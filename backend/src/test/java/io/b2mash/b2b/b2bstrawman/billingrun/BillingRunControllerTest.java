package io.b2mash.b2b.b2bstrawman.billingrun;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.member.MemberRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.orgrole.OrgRoleRepository;
import io.b2mash.b2b.b2bstrawman.orgrole.OrgRoleService;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import java.util.Map;
import java.util.Set;
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
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class BillingRunControllerTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_billing_run_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private OrgRoleService orgRoleService;
  @Autowired private OrgRoleRepository orgRoleRepository;
  @Autowired private MemberRepository memberRepository;

  private String memberIdOwner;
  private String memberIdMember;
  private String createdRunId;
  private UUID customRoleMemberId;
  private UUID noCapMemberId;

  @BeforeAll
  void provisionAndSeed() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");

    memberIdOwner = syncMember(ORG_ID, "user_br_owner", "browner@test.com", "Owner", "owner");
    memberIdMember = syncMember(ORG_ID, "user_br_member", "brmember@test.com", "Member", "member");

    var tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

    // Assign system owner role to owner member for capability-based auth
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
            });

    // Sync custom-role members for capability tests
    customRoleMemberId =
        UUID.fromString(
            syncMember(
                ORG_ID, "user_br_314a_custom", "br_custom@test.com", "BR Custom User", "member"));
    noCapMemberId =
        UUID.fromString(
            syncMember(
                ORG_ID, "user_br_314a_nocap", "br_nocap@test.com", "BR NoCap User", "member"));

    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, UUID.fromString(memberIdOwner))
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () -> {
              var withCapRole =
                  orgRoleService.createRole(
                      new io.b2mash.b2b.b2bstrawman.orgrole.dto.OrgRoleDtos.CreateOrgRoleRequest(
                          "Invoicer", "Can invoice", Set.of("INVOICING")));
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

  @Test
  @Order(1)
  void createRun_validRequest_returns201() throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/billing-runs")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "name": "March 2026 Billing",
                          "periodFrom": "2026-03-01",
                          "periodTo": "2026-03-31",
                          "currency": "USD",
                          "includeExpenses": true,
                          "includeRetainers": false
                        }
                        """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").exists())
            .andExpect(jsonPath("$.name").value("March 2026 Billing"))
            .andExpect(jsonPath("$.status").value("PREVIEW"))
            .andExpect(jsonPath("$.periodFrom").value("2026-03-01"))
            .andExpect(jsonPath("$.periodTo").value("2026-03-31"))
            .andExpect(jsonPath("$.currency").value("USD"))
            .andExpect(jsonPath("$.includeExpenses").value(true))
            .andExpect(jsonPath("$.includeRetainers").value(false))
            .andExpect(jsonPath("$.totalCustomers").value(0))
            .andExpect(jsonPath("$.totalInvoices").value(0))
            .andReturn();

    createdRunId = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
  }

  @Test
  @Order(2)
  void createRun_setsPreviewStatus() throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/billing-runs")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "periodFrom": "2026-04-01",
                          "periodTo": "2026-04-30",
                          "currency": "ZAR",
                          "includeExpenses": false,
                          "includeRetainers": true
                        }
                        """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("PREVIEW"))
            .andReturn();
  }

  @Test
  @Order(3)
  void createRun_missingPeriod_returns400() throws Exception {
    mockMvc
        .perform(
            post("/api/billing-runs")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "currency": "USD",
                      "includeExpenses": false,
                      "includeRetainers": false
                    }
                    """))
        .andExpect(status().isBadRequest());
  }

  @Test
  @Order(4)
  void createRun_memberRole_returns403() throws Exception {
    mockMvc
        .perform(
            post("/api/billing-runs")
                .with(memberJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "periodFrom": "2026-03-01",
                      "periodTo": "2026-03-31",
                      "currency": "USD",
                      "includeExpenses": false,
                      "includeRetainers": false
                    }
                    """))
        .andExpect(status().isForbidden());
  }

  @Test
  @Order(5)
  void getRun_exists_returns200() throws Exception {
    mockMvc
        .perform(get("/api/billing-runs/" + createdRunId).with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(createdRunId))
        .andExpect(jsonPath("$.name").value("March 2026 Billing"));
  }

  @Test
  @Order(6)
  void getRun_notFound_returns404() throws Exception {
    mockMvc
        .perform(get("/api/billing-runs/00000000-0000-0000-0000-000000000099").with(ownerJwt()))
        .andExpect(status().isNotFound());
  }

  @Test
  @Order(7)
  void listRuns_returns200() throws Exception {
    mockMvc
        .perform(get("/api/billing-runs").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isArray())
        .andExpect(
            jsonPath("$.content.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
  }

  @Test
  @Order(8)
  void listRuns_withStatusFilter_returnsFiltered() throws Exception {
    mockMvc
        .perform(get("/api/billing-runs").param("status", "PREVIEW").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isArray())
        .andExpect(
            jsonPath("$.content.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
  }

  @Test
  @Order(9)
  void createRun_logsAuditEvent() throws Exception {
    // Create a run and verify audit event via the audit query endpoint
    mockMvc
        .perform(
            post("/api/billing-runs")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name": "Audit Test Run",
                      "periodFrom": "2026-05-01",
                      "periodTo": "2026-05-31",
                      "currency": "USD",
                      "includeExpenses": false,
                      "includeRetainers": false
                    }
                    """))
        .andExpect(status().isCreated());

    // Verify audit event was logged by querying audit events
    mockMvc
        .perform(
            get("/api/audit-events").param("eventType", "billing_run.created").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isArray())
        .andExpect(
            jsonPath("$.content.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
  }

  @Test
  @Order(10)
  void cancelRun_previewStatus_deletesRun() throws Exception {
    // Create a fresh run to cancel
    var result =
        mockMvc
            .perform(
                post("/api/billing-runs")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "name": "To Cancel",
                          "periodFrom": "2026-06-01",
                          "periodTo": "2026-06-30",
                          "currency": "USD",
                          "includeExpenses": false,
                          "includeRetainers": false
                        }
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    String runToCancel = JsonPath.read(result.getResponse().getContentAsString(), "$.id");

    // Cancel it
    mockMvc
        .perform(delete("/api/billing-runs/" + runToCancel).with(ownerJwt()))
        .andExpect(status().isNoContent());

    // Verify it's gone
    mockMvc
        .perform(get("/api/billing-runs/" + runToCancel).with(ownerJwt()))
        .andExpect(status().isNotFound());
  }

  @Test
  @Order(11)
  void cancelRun_notFound_returns404() throws Exception {
    mockMvc
        .perform(delete("/api/billing-runs/00000000-0000-0000-0000-000000000099").with(ownerJwt()))
        .andExpect(status().isNotFound());
  }

  @Test
  @Order(12)
  void cancelRun_memberRole_returns403() throws Exception {
    mockMvc
        .perform(delete("/api/billing-runs/" + createdRunId).with(memberJwt()))
        .andExpect(status().isForbidden());
  }

  @Test
  @Order(13)
  void customRoleWithCapability_accessesBillingRunEndpoint_returns200() throws Exception {
    mockMvc.perform(get("/api/billing-runs").with(customRoleJwt())).andExpect(status().isOk());
  }

  @Test
  @Order(14)
  void customRoleWithoutCapability_accessesBillingRunEndpoint_returns403() throws Exception {
    mockMvc
        .perform(get("/api/billing-runs").with(noCapabilityJwt()))
        .andExpect(status().isForbidden());
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
                        { "clerkOrgId": "%s", "clerkUserId": "%s", "email": "%s", "name": "%s",
                          "avatarUrl": null, "orgRole": "%s" }
                        """
                            .formatted(orgId, clerkUserId, email, name, orgRole)))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.memberId");
  }

  private JwtRequestPostProcessor ownerJwt() {
    return jwt()
        .jwt(j -> j.subject("user_br_owner").claim("o", Map.of("id", ORG_ID, "rol", "owner")));
  }

  private JwtRequestPostProcessor memberJwt() {
    return jwt()
        .jwt(j -> j.subject("user_br_member").claim("o", Map.of("id", ORG_ID, "rol", "member")));
  }

  private JwtRequestPostProcessor customRoleJwt() {
    return jwt()
        .jwt(
            j ->
                j.subject("user_br_314a_custom").claim("o", Map.of("id", ORG_ID, "rol", "member")));
  }

  private JwtRequestPostProcessor noCapabilityJwt() {
    return jwt()
        .jwt(
            j -> j.subject("user_br_314a_nocap").claim("o", Map.of("id", ORG_ID, "rol", "member")));
  }
}
