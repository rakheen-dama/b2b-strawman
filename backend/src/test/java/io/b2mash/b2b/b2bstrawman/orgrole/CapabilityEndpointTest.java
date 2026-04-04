package io.b2mash.b2b.b2bstrawman.orgrole;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.member.MemberRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.hamcrest.Matchers;
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

/**
 * Integration tests for the capability query endpoints:
 *
 * <ul>
 *   <li>{@code GET /api/me/capabilities} — returns the current user's resolved capabilities
 *   <li>{@code GET /api/members/{id}/capabilities} — returns a specific member's capabilities
 *       (admin/owner or self only)
 * </ul>
 *
 * <p>Uses a real tenant schema with provisioned Member and OrgRole data. The MemberFilter resolves
 * capabilities from the database via the JWT → tenant → member chain.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CapabilityEndpointTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_cap_endpoint_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private MemberRepository memberRepository;
  @Autowired private OrgRoleRepository orgRoleRepository;
  @Autowired private OrgRoleService orgRoleService;

  private String tenantSchema;
  private UUID ownerMemberId;
  private UUID adminMemberId;
  private UUID memberMemberId;
  private UUID customRoleMemberId;
  private UUID overrideMemberId;
  private UUID customRoleId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Cap Endpoint Test Org", null);

    ownerMemberId =
        UUID.fromString(
            syncMember(ORG_ID, "user_cap_owner", "cap_owner@test.com", "Cap Owner", "owner"));
    adminMemberId =
        UUID.fromString(
            syncMember(ORG_ID, "user_cap_admin", "cap_admin@test.com", "Cap Admin", "admin"));
    memberMemberId =
        UUID.fromString(
            syncMember(ORG_ID, "user_cap_member", "cap_member@test.com", "Cap Member", "member"));
    customRoleMemberId =
        UUID.fromString(
            syncMember(ORG_ID, "user_cap_custom", "cap_custom@test.com", "Cap Custom", "member"));
    overrideMemberId =
        UUID.fromString(
            syncMember(
                ORG_ID, "user_cap_override", "cap_override@test.com", "Cap Override", "member"));

    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

    // Create a custom OrgRole and assign it to members within tenant scope
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, ownerMemberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () -> {
              // Create a custom role with specific capabilities
              var createReq =
                  new io.b2mash.b2b.b2bstrawman.orgrole.dto.OrgRoleDtos.CreateOrgRoleRequest(
                      "Project Manager",
                      "Manages projects",
                      Set.of("PROJECT_MANAGEMENT", "TEAM_OVERSIGHT"));
              var roleResponse = orgRoleService.createRole(createReq);
              customRoleId = roleResponse.id();

              // Assign custom role to customRoleMember
              var customMember = memberRepository.findById(customRoleMemberId).orElseThrow();
              customMember.setOrgRoleEntity(orgRoleRepository.findById(customRoleId).orElseThrow());
              memberRepository.save(customMember);

              // Assign custom role + overrides to overrideMember
              var overrideMember = memberRepository.findById(overrideMemberId).orElseThrow();
              overrideMember.setOrgRoleEntity(
                  orgRoleRepository.findById(customRoleId).orElseThrow());
              overrideMember.setCapabilityOverrides(Set.of("+INVOICING", "-TEAM_OVERSIGHT"));
              memberRepository.save(overrideMember);

              // Assign system owner role to owner member
              var ownerRole =
                  orgRoleRepository.findAll().stream()
                      .filter(r -> r.isSystem() && "owner".equals(r.getSlug()))
                      .findFirst()
                      .orElseThrow();
              var ownerMember = memberRepository.findById(ownerMemberId).orElseThrow();
              ownerMember.setOrgRoleEntity(ownerRole);
              memberRepository.save(ownerMember);

              // Assign system admin role to admin member
              var adminRole =
                  orgRoleRepository.findAll().stream()
                      .filter(r -> r.isSystem() && "admin".equals(r.getSlug()))
                      .findFirst()
                      .orElseThrow();
              var adminMember = memberRepository.findById(adminMemberId).orElseThrow();
              adminMember.setOrgRoleEntity(adminRole);
              memberRepository.save(adminMember);
            });
  }

  // --- GET /api/me/capabilities ---

  @Test
  void getMyCapabilities_admin_returnsAll() throws Exception {
    mockMvc
        .perform(get("/api/me/capabilities").with(adminJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.isAdmin").value(true))
        .andExpect(jsonPath("$.isOwner").value(false))
        .andExpect(jsonPath("$.capabilities").isArray())
        .andExpect(
            jsonPath("$.capabilities.length()")
                .value(Capability.ALL_NAMES.size() - Capability.OWNER_ONLY.size()));
  }

  @Test
  void getMyCapabilities_member_returnsSeededCapabilities() throws Exception {
    mockMvc
        .perform(get("/api/me/capabilities").with(memberJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.isAdmin").value(false))
        .andExpect(jsonPath("$.isOwner").value(false))
        .andExpect(jsonPath("$.capabilities").isArray())
        .andExpect(jsonPath("$.capabilities.length()").value(2))
        .andExpect(
            jsonPath("$.capabilities", Matchers.containsInAnyOrder("VIEW_LEGAL", "VIEW_TRUST")))
        .andExpect(jsonPath("$.role").value("Member"));
  }

  @Test
  void getMyCapabilities_customRole_returnsRoleCaps() throws Exception {
    mockMvc
        .perform(get("/api/me/capabilities").with(customRoleJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.isAdmin").value(false))
        .andExpect(jsonPath("$.isOwner").value(false))
        .andExpect(jsonPath("$.capabilities").isArray())
        .andExpect(jsonPath("$.capabilities.length()").value(2))
        .andExpect(
            jsonPath(
                "$.capabilities",
                Matchers.containsInAnyOrder("PROJECT_MANAGEMENT", "TEAM_OVERSIGHT")));
  }

  @Test
  void getMyCapabilities_withOverrides_returnsEffective() throws Exception {
    mockMvc
        .perform(get("/api/me/capabilities").with(overrideJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.isAdmin").value(false))
        .andExpect(jsonPath("$.isOwner").value(false))
        .andExpect(jsonPath("$.capabilities").isArray())
        .andExpect(jsonPath("$.capabilities.length()").value(2))
        .andExpect(
            jsonPath(
                "$.capabilities", Matchers.containsInAnyOrder("PROJECT_MANAGEMENT", "INVOICING")));
  }

  @Test
  void getMyCapabilities_returnsRoleName() throws Exception {
    mockMvc
        .perform(get("/api/me/capabilities").with(customRoleJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.role").value("Project Manager"));
  }

  // --- GET /api/members/{id}/capabilities ---

  @Test
  void getMemberCapabilities_self_allowed() throws Exception {
    mockMvc
        .perform(get("/api/members/" + customRoleMemberId + "/capabilities").with(customRoleJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.memberId").value(customRoleMemberId.toString()))
        .andExpect(jsonPath("$.roleName").value("Project Manager"))
        .andExpect(
            jsonPath(
                "$.roleCapabilities",
                Matchers.containsInAnyOrder("PROJECT_MANAGEMENT", "TEAM_OVERSIGHT")))
        .andExpect(jsonPath("$.overrides").isArray())
        .andExpect(jsonPath("$.overrides.length()").value(0))
        .andExpect(
            jsonPath(
                "$.effectiveCapabilities",
                Matchers.containsInAnyOrder("PROJECT_MANAGEMENT", "TEAM_OVERSIGHT")));
  }

  @Test
  void getMemberCapabilities_admin_allowed() throws Exception {
    mockMvc
        .perform(get("/api/members/" + customRoleMemberId + "/capabilities").with(adminJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.memberId").value(customRoleMemberId.toString()))
        .andExpect(jsonPath("$.roleName").value("Project Manager"));
  }

  @Test
  void getMemberCapabilities_otherMember_returns403() throws Exception {
    mockMvc
        .perform(get("/api/members/" + customRoleMemberId + "/capabilities").with(memberJwt()))
        .andExpect(status().isForbidden());
  }

  // --- @PreAuthorize and @RequiresCapability coexistence ---

  @Test
  void preAuthorizeAndRequiresCapability_coexist() throws Exception {
    // Verify that an endpoint with @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')") still
    // works
    // The /api/members endpoint uses @PreAuthorize("hasAnyRole('ORG_MEMBER', 'ORG_ADMIN',
    // 'ORG_OWNER')")
    mockMvc.perform(get("/api/members").with(adminJwt())).andExpect(status().isOk());

    // Also confirm our @PreAuthorize("isAuthenticated()") on the capability endpoints works
    mockMvc
        .perform(get("/api/me/capabilities").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.isOwner").value(true));
  }

  // --- Helpers ---

  private JwtRequestPostProcessor ownerJwt() {
    return jwt()
        .jwt(j -> j.subject("user_cap_owner").claim("o", Map.of("id", ORG_ID, "rol", "owner")));
  }

  private JwtRequestPostProcessor adminJwt() {
    return jwt()
        .jwt(j -> j.subject("user_cap_admin").claim("o", Map.of("id", ORG_ID, "rol", "admin")));
  }

  private JwtRequestPostProcessor memberJwt() {
    return jwt()
        .jwt(j -> j.subject("user_cap_member").claim("o", Map.of("id", ORG_ID, "rol", "member")));
  }

  private JwtRequestPostProcessor customRoleJwt() {
    return jwt()
        .jwt(j -> j.subject("user_cap_custom").claim("o", Map.of("id", ORG_ID, "rol", "member")));
  }

  private JwtRequestPostProcessor overrideJwt() {
    return jwt()
        .jwt(j -> j.subject("user_cap_override").claim("o", Map.of("id", ORG_ID, "rol", "member")));
  }

  private String syncMember(
      String orgId, String clerkUserId, String email, String name, String orgRole)
      throws Exception {
    var result =
        mockMvc
            .perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                        "/internal/members/sync")
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
