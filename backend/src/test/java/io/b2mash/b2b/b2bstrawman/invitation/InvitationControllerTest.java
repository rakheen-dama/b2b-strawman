package io.b2mash.b2b.b2bstrawman.invitation;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.orgrole.OrgRoleRepository;
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

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InvitationControllerTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_invitation_ctrl_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private OrgRoleRepository orgRoleRepository;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;

  private String tenantSchema;
  private UUID memberRoleId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Invitation Ctrl Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");

    syncMember("user_inv_owner", "inv_owner@test.com", "Inv Owner", "owner");
    syncMember("user_inv_admin", "inv_admin@test.com", "Inv Admin", "admin");
    syncMember("user_inv_member", "inv_member@test.com", "Inv Member", "member");

    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

    // Look up member role ID for invitation requests
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .run(
            () -> {
              memberRoleId = orgRoleRepository.findBySlug("member").orElseThrow().getId();
            });
  }

  @Test
  void invite_validRequest_returns200() throws Exception {
    mockMvc
        .perform(
            post("/api/invitations")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"email": "newuser@example.com", "orgRoleId": "%s"}
                    """
                        .formatted(memberRoleId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").exists())
        .andExpect(jsonPath("$.email").value("newuser@example.com"))
        .andExpect(jsonPath("$.roleName").value("Member"))
        .andExpect(jsonPath("$.status").value("PENDING"))
        .andExpect(jsonPath("$.expiresAt").exists())
        .andExpect(jsonPath("$.createdAt").exists())
        .andExpect(jsonPath("$.invitedByName").value("Inv Owner"));
  }

  @Test
  void invite_duplicatePendingEmail_returns409() throws Exception {
    String email = "duplicate-" + UUID.randomUUID() + "@example.com";

    mockMvc
        .perform(
            post("/api/invitations")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"email": "%s", "orgRoleId": "%s"}
                    """
                        .formatted(email, memberRoleId)))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            post("/api/invitations")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"email": "%s", "orgRoleId": "%s"}
                    """
                        .formatted(email, memberRoleId)))
        .andExpect(status().isConflict());
  }

  @Test
  void listInvitations_returns200() throws Exception {
    mockMvc
        .perform(get("/api/invitations").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray());
  }

  @Test
  void revokeInvitation_returns204() throws Exception {
    String email = "revoke-" + UUID.randomUUID() + "@example.com";

    var result =
        mockMvc
            .perform(
                post("/api/invitations")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"email": "%s", "orgRoleId": "%s"}
                        """
                            .formatted(email, memberRoleId)))
            .andExpect(status().isOk())
            .andReturn();

    String invitationId = JsonPath.read(result.getResponse().getContentAsString(), "$.id");

    mockMvc
        .perform(delete("/api/invitations/" + invitationId).with(ownerJwt()))
        .andExpect(status().isNoContent());
  }

  @Test
  void invite_memberRole_returns403() throws Exception {
    mockMvc
        .perform(
            post("/api/invitations")
                .with(memberJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"email": "forbidden@example.com", "orgRoleId": "%s"}
                    """
                        .formatted(memberRoleId)))
        .andExpect(status().isForbidden());
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

  // --- JWT helpers ---
  private JwtRequestPostProcessor ownerJwt() {
    return jwt()
        .jwt(j -> j.subject("user_inv_owner").claim("o", Map.of("id", ORG_ID, "rol", "owner")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_OWNER")));
  }

  private JwtRequestPostProcessor adminJwt() {
    return jwt()
        .jwt(j -> j.subject("user_inv_admin").claim("o", Map.of("id", ORG_ID, "rol", "admin")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_ADMIN")));
  }

  private JwtRequestPostProcessor memberJwt() {
    return jwt()
        .jwt(j -> j.subject("user_inv_member").claim("o", Map.of("id", ORG_ID, "rol", "member")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_MEMBER")));
  }
}
