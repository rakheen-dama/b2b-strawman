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
import io.b2mash.b2b.b2bstrawman.orgrole.OrgRoleRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
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
  @Autowired private PendingInvitationRepository invitationRepository;

  private String tenantSchema;
  private String ownerMemberId;
  private String adminMemberId;
  private String memberMemberId;
  private String adminRoleId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Invitation Ctrl Test Org", null);
    planSyncService.syncPlan(ORG_ID, "pro-plan");

    ownerMemberId = syncMember("user_inv_owner", "inv_owner@test.com", "Inv Owner", "owner");
    adminMemberId = syncMember("user_inv_admin", "inv_admin@test.com", "Inv Admin", "admin");
    memberMemberId = syncMember("user_inv_member", "inv_member@test.com", "Inv Member", "member");

    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

    // Get the admin role ID for creating invitations
    adminRoleId =
        ScopedValue.where(
                io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes.TENANT_ID, tenantSchema)
            .call(() -> orgRoleRepository.findBySlug("admin").orElseThrow().getId().toString());
  }

  @Test
  void createInvitation_admin_returns201() throws Exception {
    mockMvc
        .perform(
            post("/api/invitations")
                .with(adminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "email": "newuser@example.com",
                      "orgRoleId": "%s"
                    }
                    """
                        .formatted(adminRoleId)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").exists())
        .andExpect(jsonPath("$.email").value("newuser@example.com"))
        .andExpect(jsonPath("$.status").value("PENDING"))
        .andExpect(jsonPath("$.roleName").exists())
        .andExpect(jsonPath("$.expiresAt").exists());
  }

  @Test
  void createInvitation_member_returns403() throws Exception {
    mockMvc
        .perform(
            post("/api/invitations")
                .with(memberJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "email": "forbidden@example.com",
                      "orgRoleId": "%s"
                    }
                    """
                        .formatted(adminRoleId)))
        .andExpect(status().isForbidden());
  }

  @Test
  void createInvitation_duplicateEmail_returns409() throws Exception {
    String email = "duplicate_" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";

    // First invitation should succeed
    mockMvc
        .perform(
            post("/api/invitations")
                .with(adminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"email": "%s", "orgRoleId": "%s"}
                    """
                        .formatted(email, adminRoleId)))
        .andExpect(status().isCreated());

    // Second invitation for same email should fail with 409
    mockMvc
        .perform(
            post("/api/invitations")
                .with(adminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"email": "%s", "orgRoleId": "%s"}
                    """
                        .formatted(email, adminRoleId)))
        .andExpect(status().isConflict());
  }

  @Test
  void createInvitation_existingMember_returns409() throws Exception {
    // inv_owner@test.com is already a member
    mockMvc
        .perform(
            post("/api/invitations")
                .with(adminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"email": "inv_owner@test.com", "orgRoleId": "%s"}
                    """
                        .formatted(adminRoleId)))
        .andExpect(status().isConflict());
  }

  @Test
  void listInvitations_admin_returnsList() throws Exception {
    String email = "list_" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";

    // Create an invitation
    mockMvc
        .perform(
            post("/api/invitations")
                .with(adminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"email": "%s", "orgRoleId": "%s"}
                    """
                        .formatted(email, adminRoleId)))
        .andExpect(status().isCreated());

    // List invitations
    mockMvc
        .perform(get("/api/invitations").with(adminJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.invitations").isArray());
  }

  @Test
  void listInvitations_withStatusFilter_returnsList() throws Exception {
    mockMvc
        .perform(get("/api/invitations").with(adminJwt()).param("status", "PENDING"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.invitations").isArray());
  }

  @Test
  void listInvitations_invalidStatusFilter_returns400() throws Exception {
    mockMvc
        .perform(get("/api/invitations").with(adminJwt()).param("status", "INVALID_STATUS"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void revokeInvitation_admin_returns204() throws Exception {
    String email = "revoke_" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";

    var result =
        mockMvc
            .perform(
                post("/api/invitations")
                    .with(adminJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"email": "%s", "orgRoleId": "%s"}
                        """
                            .formatted(email, adminRoleId)))
            .andExpect(status().isCreated())
            .andReturn();

    String invitationId = JsonPath.read(result.getResponse().getContentAsString(), "$.id");

    mockMvc
        .perform(delete("/api/invitations/" + invitationId).with(adminJwt()))
        .andExpect(status().isNoContent());
  }

  @Test
  void revokeInvitation_alreadyRevoked_returns400() throws Exception {
    String email = "double_revoke_" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";

    var result =
        mockMvc
            .perform(
                post("/api/invitations")
                    .with(adminJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"email": "%s", "orgRoleId": "%s"}
                        """
                            .formatted(email, adminRoleId)))
            .andExpect(status().isCreated())
            .andReturn();

    String invitationId = JsonPath.read(result.getResponse().getContentAsString(), "$.id");

    // First revoke succeeds
    mockMvc
        .perform(delete("/api/invitations/" + invitationId).with(adminJwt()))
        .andExpect(status().isNoContent());

    // Second revoke fails (already revoked)
    mockMvc
        .perform(delete("/api/invitations/" + invitationId).with(adminJwt()))
        .andExpect(status().isBadRequest());
  }

  @Test
  void revokeInvitation_notFound_returns404() throws Exception {
    mockMvc
        .perform(delete("/api/invitations/" + UUID.randomUUID()).with(adminJwt()))
        .andExpect(status().isNotFound());
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
  private JwtRequestPostProcessor adminJwt() {
    return jwt()
        .jwt(j -> j.subject("user_inv_admin").claim("o", Map.of("id", ORG_ID, "rol", "admin")));
  }

  private JwtRequestPostProcessor memberJwt() {
    return jwt()
        .jwt(j -> j.subject("user_inv_member").claim("o", Map.of("id", ORG_ID, "rol", "member")));
  }
}
