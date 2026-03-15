package io.b2mash.b2b.b2bstrawman.compliance;

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
class CompliancePackControllerTest {

  private static final String API_KEY = "test-api-key";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private OrgRoleService orgRoleService;
  @Autowired private MemberRepository memberRepository;
  @Autowired private OrgRoleRepository orgRoleRepository;

  private static final String ORG_ID = "org_pack_controller_test";

  private String tenantSchema;
  private UUID memberIdOwner;
  private UUID customRoleMemberId;
  private UUID noCapMemberId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Pack Controller Test Org", null);
    planSyncService.syncPlan(ORG_ID, "pro-plan");

    memberIdOwner =
        UUID.fromString(
            syncMember("user_pack_owner", "pack_owner@test.com", "Pack Owner", "owner"));
    syncMember("user_admin", "pack_admin@test.com", "Pack Admin", "admin");

    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

    customRoleMemberId =
        UUID.fromString(
            syncMember(
                "user_pack_315a_custom", "pack_custom@test.com", "Pack Custom User", "member"));
    noCapMemberId =
        UUID.fromString(
            syncMember("user_pack_315a_nocap", "pack_nocap@test.com", "Pack NoCap User", "member"));

    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberIdOwner)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () -> {
              var withCapRole =
                  orgRoleService.createRole(
                      new io.b2mash.b2b.b2bstrawman.orgrole.dto.OrgRoleDtos.CreateOrgRoleRequest(
                          "Pack Manager", "Can manage packs", Set.of("CUSTOMER_MANAGEMENT")));
              var customMember = memberRepository.findById(customRoleMemberId).orElseThrow();
              customMember.setOrgRoleEntity(
                  orgRoleRepository.findById(withCapRole.id()).orElseThrow());
              memberRepository.save(customMember);

              var withoutCapRole =
                  orgRoleService.createRole(
                      new io.b2mash.b2b.b2bstrawman.orgrole.dto.OrgRoleDtos.CreateOrgRoleRequest(
                          "Team Lead Pack", "Can manage teams", Set.of("TEAM_OVERSIGHT")));
              var noCapMember = memberRepository.findById(noCapMemberId).orElseThrow();
              noCapMember.setOrgRoleEntity(
                  orgRoleRepository.findById(withoutCapRole.id()).orElseThrow());
              memberRepository.save(noCapMember);
            });
  }

  private JwtRequestPostProcessor adminJwt() {
    return jwt().jwt(j -> j.subject("user_admin").claim("o", Map.of("id", ORG_ID, "rol", "admin")));
  }

  private JwtRequestPostProcessor customRoleJwt() {
    return jwt()
        .jwt(
            j ->
                j.subject("user_pack_315a_custom")
                    .claim("o", Map.of("id", ORG_ID, "rol", "member")));
  }

  private JwtRequestPostProcessor noCapabilityJwt() {
    return jwt()
        .jwt(
            j ->
                j.subject("user_pack_315a_nocap")
                    .claim("o", Map.of("id", ORG_ID, "rol", "member")));
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
                        {"clerkOrgId":"%s","clerkUserId":"%s","email":"%s","name":"%s","avatarUrl":null,"orgRole":"%s"}
                        """
                            .formatted(ORG_ID, clerkUserId, email, name, orgRole)))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.memberId");
  }

  // --- Capability Tests (added in Epic 315A) ---

  @Test
  void customRoleWithCapability_accessesPackEndpoint_returns200() throws Exception {
    mockMvc
        .perform(get("/api/compliance-packs/generic-onboarding").with(customRoleJwt()))
        .andExpect(status().isOk());
  }

  @Test
  void customRoleWithoutCapability_accessesPackEndpoint_returns403() throws Exception {
    mockMvc
        .perform(get("/api/compliance-packs/generic-onboarding").with(noCapabilityJwt()))
        .andExpect(status().isForbidden());
  }

  @Test
  void getPackDefinition_returnsPackForGenericOnboarding() throws Exception {
    mockMvc
        .perform(get("/api/compliance-packs/generic-onboarding").with(adminJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.packId").value("generic-onboarding"))
        .andExpect(jsonPath("$.name").value("Generic Client Onboarding"))
        .andExpect(jsonPath("$.version").value("1.0.0"))
        .andExpect(jsonPath("$.checklistTemplate.items").isArray())
        .andExpect(jsonPath("$.checklistTemplate.items.length()").value(4));
  }

  @Test
  void getPackDefinition_returns404ForNonexistentPack() throws Exception {
    mockMvc
        .perform(get("/api/compliance-packs/nonexistent-pack").with(adminJwt()))
        .andExpect(status().isNotFound());
  }

  @Test
  void getPackDefinition_returns401WithoutAuth() throws Exception {
    mockMvc
        .perform(get("/api/compliance-packs/generic-onboarding"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void getPackDefinition_returns403ForMember() throws Exception {
    var memberJwt =
        jwt().jwt(j -> j.subject("user_member").claim("o", Map.of("id", ORG_ID, "rol", "member")));
    mockMvc
        .perform(get("/api/compliance-packs/generic-onboarding").with(memberJwt))
        .andExpect(status().isForbidden());
  }
}
