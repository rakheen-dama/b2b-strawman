package io.b2mash.b2b.b2bstrawman.provisioning;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.security.keycloak.KeycloakAdminClient;
import java.util.List;
import java.util.Map;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OrgCreationControllerTest {

  private static final String EXISTING_ORG_ID = "org_orgctrl_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;

  @MockitoBean private KeycloakAdminClient keycloakAdminClient;

  @BeforeAll
  void setup() {
    // Provision a tenant so there's a valid org context for JWT-authenticated requests
    provisioningService.provisionTenant(EXISTING_ORG_ID, "OrgCtrl Test Org");
    planSyncService.syncPlan(EXISTING_ORG_ID, "pro-plan");
  }

  @Test
  void createOrg_platformAdmin_returns201() throws Exception {
    when(keycloakAdminClient.createOrganization(eq("New Test Org"), eq("new-test-org"), any()))
        .thenReturn("kc-new-org-id");

    mockMvc
        .perform(
            post("/api/orgs")
                .with(platformAdminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    { "name": "New Test Org" }
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.orgId").value("kc-new-org-id"))
        .andExpect(jsonPath("$.slug").value("new-test-org"));

    verify(keycloakAdminClient).addMember(eq("kc-new-org-id"), eq("user_platform_admin"));
    verify(keycloakAdminClient)
        .updateMemberRole(eq("kc-new-org-id"), eq("user_platform_admin"), eq("owner"));
  }

  @Test
  void createOrg_regularMember_returns403() throws Exception {
    mockMvc
        .perform(
            post("/api/orgs")
                .with(regularMemberJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    { "name": "Unauthorized Org" }
                    """))
        .andExpect(status().isForbidden());
  }

  @Test
  void createOrg_missingName_returns400() throws Exception {
    mockMvc
        .perform(
            post("/api/orgs")
                .with(platformAdminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    { "name": "" }
                    """))
        .andExpect(status().isBadRequest());
  }

  @Test
  void createOrg_duplicateName_returns409() throws Exception {
    when(keycloakAdminClient.createOrganization(eq("Duplicate Org"), eq("duplicate-org"), any()))
        .thenThrow(new RuntimeException("409 Conflict"));

    mockMvc
        .perform(
            post("/api/orgs")
                .with(platformAdminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    { "name": "Duplicate Org" }
                    """))
        .andExpect(status().isConflict());
  }

  private JwtRequestPostProcessor platformAdminJwt() {
    return jwt()
        .jwt(
            j ->
                j.subject("user_platform_admin")
                    .claim("groups", List.of("platform-admins"))
                    .claim("o", Map.of("id", EXISTING_ORG_ID, "rol", "owner")));
  }

  private JwtRequestPostProcessor regularMemberJwt() {
    return jwt()
        .jwt(
            j ->
                j.subject("user_regular_member")
                    .claim("o", Map.of("id", EXISTING_ORG_ID, "rol", "member")));
  }
}
