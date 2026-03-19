package io.b2mash.b2b.b2bstrawman.datarequest;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DataRequestControllerTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_dsr_ctrl_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private OrgRoleService orgRoleService;
  @Autowired private MemberRepository memberRepository;
  @Autowired private OrgRoleRepository orgRoleRepository;

  private String customerId;
  private String tenantSchema;
  private UUID memberIdOwner;
  private UUID customRoleMemberId;
  private UUID noCapMemberId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "DSR Controller Test Org", null);
    planSyncService.syncPlan(ORG_ID, "pro-plan");
    memberIdOwner =
        UUID.fromString(
            syncMember(ORG_ID, "user_dsr_owner", "dsr_owner@test.com", "DSR Owner", "owner"));
    syncMember(ORG_ID, "user_dsr_member", "dsr_member@test.com", "DSR Member", "member");

    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

    customRoleMemberId =
        UUID.fromString(
            syncMember(
                ORG_ID,
                "user_dsr_315a_custom",
                "dsr_custom@test.com",
                "DSR Custom User",
                "member"));
    noCapMemberId =
        UUID.fromString(
            syncMember(
                ORG_ID, "user_dsr_315a_nocap", "dsr_nocap@test.com", "DSR NoCap User", "member"));

    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberIdOwner)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () -> {
              var withCapRole =
                  orgRoleService.createRole(
                      new io.b2mash.b2b.b2bstrawman.orgrole.dto.OrgRoleDtos.CreateOrgRoleRequest(
                          "DSR Manager", "Can manage DSRs", Set.of("CUSTOMER_MANAGEMENT")));
              var customMember = memberRepository.findById(customRoleMemberId).orElseThrow();
              customMember.setOrgRoleEntity(
                  orgRoleRepository.findById(withCapRole.id()).orElseThrow());
              memberRepository.save(customMember);

              var withoutCapRole =
                  orgRoleService.createRole(
                      new io.b2mash.b2b.b2bstrawman.orgrole.dto.OrgRoleDtos.CreateOrgRoleRequest(
                          "Team Lead DSR", "Can manage teams", Set.of("TEAM_OVERSIGHT")));
              var noCapMember = memberRepository.findById(noCapMemberId).orElseThrow();
              noCapMember.setOrgRoleEntity(
                  orgRoleRepository.findById(withoutCapRole.id()).orElseThrow());
              memberRepository.save(noCapMember);
            });

    // Create a customer via the API
    var result =
        mockMvc
            .perform(
                post("/api/customers")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name":"DSR Ctrl Customer","email":"dsr-ctrl@test.com","phone":"+1-555-0300","idNumber":"DSR-C01","notes":"Test"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    customerId = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
  }

  @Test
  void postCreateRequest_returns201() throws Exception {
    mockMvc
        .perform(
            post("/api/data-requests")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"customerId":"%s","requestType":"ACCESS","description":"I want my data"}
                    """
                        .formatted(customerId)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").exists())
        .andExpect(jsonPath("$.status").value("RECEIVED"))
        .andExpect(jsonPath("$.deadline").exists())
        .andExpect(jsonPath("$.requestType").value("ACCESS"));
  }

  @Test
  void createRequest_returns403ForMember() throws Exception {
    mockMvc
        .perform(
            post("/api/data-requests")
                .with(memberJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"customerId":"%s","requestType":"ACCESS","description":"Member attempt"}
                    """
                        .formatted(customerId)))
        .andExpect(status().isForbidden());
  }

  @Test
  void getListRequests_returns200() throws Exception {
    // Create a request first
    mockMvc
        .perform(
            post("/api/data-requests")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"customerId":"%s","requestType":"DELETION","description":"List test"}
                    """
                        .formatted(customerId)))
        .andExpect(status().isCreated());

    // List all
    mockMvc
        .perform(get("/api/data-requests").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$[0].id").exists());
  }

  // --- Capability Tests (added in Epic 315A) ---

  @Test
  void customRoleWithCapability_accessesDsrEndpoint_returns200() throws Exception {
    mockMvc.perform(get("/api/data-requests").with(customRoleJwt())).andExpect(status().isOk());
  }

  @Test
  void customRoleWithoutCapability_accessesDsrEndpoint_returns403() throws Exception {
    mockMvc
        .perform(get("/api/data-requests").with(noCapabilityJwt()))
        .andExpect(status().isForbidden());
  }

  @Test
  void customRoleWithCapability_canCheckDeadlines() throws Exception {
    mockMvc
        .perform(post("/api/data-requests/check-deadlines").with(customRoleJwt()))
        .andExpect(status().isOk());
  }

  @Test
  void customRoleWithoutCapability_cannotCheckDeadlines() throws Exception {
    mockMvc
        .perform(post("/api/data-requests/check-deadlines").with(noCapabilityJwt()))
        .andExpect(status().isForbidden());
  }

  @Test
  void getListRequests_includesDeadlineStatus_onTrackForFreshRequest() throws Exception {
    // Create a request
    mockMvc
        .perform(
            post("/api/data-requests")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"customerId":"%s","requestType":"ACCESS","description":"Deadline status test"}
                    """
                        .formatted(customerId)))
        .andExpect(status().isCreated());

    // List and verify deadlineStatus is present and ON_TRACK for a fresh request
    mockMvc
        .perform(get("/api/data-requests").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].deadlineStatus").exists())
        .andExpect(jsonPath("$[0].deadlineStatus").value("ON_TRACK"));
  }

  private JwtRequestPostProcessor ownerJwt() {
    return jwt()
        .jwt(j -> j.subject("user_dsr_owner").claim("o", Map.of("id", ORG_ID, "rol", "owner")));
  }

  private JwtRequestPostProcessor memberJwt() {
    return jwt()
        .jwt(j -> j.subject("user_dsr_member").claim("o", Map.of("id", ORG_ID, "rol", "member")));
  }

  private JwtRequestPostProcessor customRoleJwt() {
    return jwt()
        .jwt(
            j ->
                j.subject("user_dsr_315a_custom")
                    .claim("o", Map.of("id", ORG_ID, "rol", "member")));
  }

  private JwtRequestPostProcessor noCapabilityJwt() {
    return jwt()
        .jwt(
            j ->
                j.subject("user_dsr_315a_nocap").claim("o", Map.of("id", ORG_ID, "rol", "member")));
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
                        {"clerkOrgId":"%s","clerkUserId":"%s","email":"%s","name":"%s","avatarUrl":null,"orgRole":"%s"}
                        """
                            .formatted(orgId, clerkUserId, email, name, orgRole)))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.memberId");
  }
}
