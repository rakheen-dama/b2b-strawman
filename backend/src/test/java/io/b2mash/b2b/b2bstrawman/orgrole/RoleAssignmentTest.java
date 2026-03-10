package io.b2mash.b2b.b2bstrawman.orgrole;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.member.MemberRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
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
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RoleAssignmentTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_role_assign_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private OrgRoleRepository orgRoleRepository;
  @Autowired private MemberRepository memberRepository;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;

  private String tenantSchema;
  private String ownerMemberId;
  private String adminMemberId;
  private String memberMemberId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Role Assign Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");

    ownerMemberId = syncMember("user_ra_owner", "ra_owner@test.com", "RA Owner", "owner");
    adminMemberId = syncMember("user_ra_admin", "ra_admin@test.com", "RA Admin", "admin");
    memberMemberId = syncMember("user_ra_member", "ra_member@test.com", "RA Member", "member");

    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
  }

  @Test
  void assignRole_validRequest_updatesRole() throws Exception {
    String roleId =
        createCustomRole("Assign Target Role", Set.of("INVOICING", "PROJECT_MANAGEMENT"));

    mockMvc
        .perform(
            put("/api/members/" + memberMemberId + "/role")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"orgRoleId": "%s", "capabilityOverrides": []}
                    """
                        .formatted(roleId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.memberId").value(memberMemberId))
        .andExpect(jsonPath("$.roleName").value("Assign Target Role"))
        .andExpect(jsonPath("$.effectiveCapabilities").isArray());
  }

  @Test
  void assignRole_withOverrides_setsOverrides() throws Exception {
    String roleId = createCustomRole("Override Test Role", Set.of("INVOICING"));

    mockMvc
        .perform(
            put("/api/members/" + memberMemberId + "/role")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"orgRoleId": "%s", "capabilityOverrides": ["+PROJECT_MANAGEMENT", "-INVOICING"]}
                    """
                        .formatted(roleId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.memberId").value(memberMemberId))
        .andExpect(jsonPath("$.overrides").isArray());
  }

  @Test
  void assignRole_cannotChangeOwner_returns403() throws Exception {
    String roleId = createCustomRole("Owner Block Role", Set.of("INVOICING"));

    mockMvc
        .perform(
            put("/api/members/" + ownerMemberId + "/role")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"orgRoleId": "%s", "capabilityOverrides": []}
                    """
                        .formatted(roleId)))
        .andExpect(status().isForbidden());
  }

  @Test
  void assignRole_cannotAssignOwnerRole_returns403() throws Exception {
    // Find the owner system role ID
    String ownerSystemRoleId = findSystemRoleId("owner");

    mockMvc
        .perform(
            put("/api/members/" + memberMemberId + "/role")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"orgRoleId": "%s", "capabilityOverrides": []}
                    """
                        .formatted(ownerSystemRoleId)))
        .andExpect(status().isForbidden());
  }

  @Test
  void assignRole_invalidCapabilityOverride_returns400() throws Exception {
    String roleId = createCustomRole("Bad Override Role", Set.of("INVOICING"));

    mockMvc
        .perform(
            put("/api/members/" + memberMemberId + "/role")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"orgRoleId": "%s", "capabilityOverrides": ["+NOT_A_REAL_CAP"]}
                    """
                        .formatted(roleId)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void assignRole_nonExistentRole_returns404() throws Exception {
    mockMvc
        .perform(
            put("/api/members/" + memberMemberId + "/role")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"orgRoleId": "%s", "capabilityOverrides": []}
                    """
                        .formatted(UUID.randomUUID())))
        .andExpect(status().isNotFound());
  }

  @Test
  void assignRole_memberRole_returns403() throws Exception {
    String roleId = createCustomRole("Member Forbidden Role", Set.of("INVOICING"));

    mockMvc
        .perform(
            put("/api/members/" + memberMemberId + "/role")
                .with(memberJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"orgRoleId": "%s", "capabilityOverrides": []}
                    """
                        .formatted(roleId)))
        .andExpect(status().isForbidden());
  }

  @Test
  void assignRole_adminCanAssignCustomRole() throws Exception {
    String roleId = createCustomRole("Admin Assign Role", Set.of("INVOICING"));

    mockMvc
        .perform(
            put("/api/members/" + memberMemberId + "/role")
                .with(adminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"orgRoleId": "%s", "capabilityOverrides": []}
                    """
                        .formatted(roleId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.roleName").value("Admin Assign Role"));
  }

  @Test
  void lazyCreateMember_setsDefaultSystemRole() throws Exception {
    // Sync a new member and verify they get an orgRoleId
    String newMemberId = syncMember("user_ra_lazy", "ra_lazy@test.com", "RA Lazy", "member");

    UUID memberUuid = UUID.fromString(newMemberId);
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .run(
            () -> {
              var member = memberRepository.findById(memberUuid).orElseThrow();
              // The lazy-created member should have an orgRoleId if system roles exist
              // System roles are seeded during provisioning
              org.junit.jupiter.api.Assertions.assertNotNull(
                  member.getOrgRoleId(), "Lazy-created member should have an orgRoleId");
            });
  }

  @Test
  void assignRole_clearsOverridesWhenOmitted() throws Exception {
    String roleId = createCustomRole("Clear Overrides Role", Set.of("INVOICING"));

    // First assign with overrides
    mockMvc
        .perform(
            put("/api/members/" + memberMemberId + "/role")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"orgRoleId": "%s", "capabilityOverrides": ["+PROJECT_MANAGEMENT"]}
                    """
                        .formatted(roleId)))
        .andExpect(status().isOk());

    // Then assign again without overrides (null/omitted should clear)
    mockMvc
        .perform(
            put("/api/members/" + memberMemberId + "/role")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"orgRoleId": "%s"}
                    """
                        .formatted(roleId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.overrides").isEmpty());
  }

  // --- Helper: sync member via internal API ---
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
                          "clerkOrgId": "%s",
                          "clerkUserId": "%s",
                          "email": "%s",
                          "name": "%s",
                          "avatarUrl": null,
                          "orgRole": "%s"
                        }
                        """
                            .formatted(ORG_ID, clerkUserId, email, name, orgRole)))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.memberId");
  }

  // --- Helper: create a custom role via API ---
  private String createCustomRole(String name, Set<String> capabilities) throws Exception {
    var body =
        """
        {
          "name": "%s",
          "description": "Test role",
          "capabilities": [%s]
        }
        """
            .formatted(
                name,
                capabilities.stream()
                    .map(c -> "\"" + c + "\"")
                    .reduce((a, b) -> a + "," + b)
                    .orElse(""));
    var result =
        mockMvc
            .perform(
                post("/api/org-roles")
                    .with(adminJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
  }

  // --- Helper: find system role ID by slug ---
  private String findSystemRoleId(String slug) throws Exception {
    var listResult =
        mockMvc
            .perform(get("/api/org-roles").with(ownerJwt()))
            .andExpect(status().isOk())
            .andReturn();
    List<Map<String, Object>> roles =
        JsonPath.read(listResult.getResponse().getContentAsString(), "$");
    return roles.stream()
        .filter(r -> Boolean.TRUE.equals(r.get("isSystem")) && slug.equals(r.get("slug")))
        .findFirst()
        .map(r -> r.get("id").toString())
        .orElseThrow(() -> new AssertionError("System role '" + slug + "' not found"));
  }

  // --- JWT helpers ---
  private JwtRequestPostProcessor ownerJwt() {
    return jwt()
        .jwt(j -> j.subject("user_ra_owner").claim("o", Map.of("id", ORG_ID, "rol", "owner")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_OWNER")));
  }

  private JwtRequestPostProcessor adminJwt() {
    return jwt()
        .jwt(j -> j.subject("user_ra_admin").claim("o", Map.of("id", ORG_ID, "rol", "admin")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_ADMIN")));
  }

  private JwtRequestPostProcessor memberJwt() {
    return jwt()
        .jwt(j -> j.subject("user_ra_member").claim("o", Map.of("id", ORG_ID, "rol", "member")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_MEMBER")));
  }
}
