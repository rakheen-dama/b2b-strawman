package io.b2mash.b2b.b2bstrawman.provisioning;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class ProvisioningControllerTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private OrganizationRepository organizationRepository;

  @Test
  void provisionNewOrganization_returns201() throws Exception {
    mockMvc
        .perform(
            post("/internal/orgs/provision")
                .header("X-API-KEY", "test-api-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"clerkOrgId":"org_test_001","orgName":"Test Org","slug":"test-org"}
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.clerkOrgId").value("org_test_001"))
        .andExpect(jsonPath("$.status").value("PENDING"));
  }

  @Test
  void provisionExistingOrganization_returns409() throws Exception {
    // First provision
    mockMvc
        .perform(
            post("/internal/orgs/provision")
                .header("X-API-KEY", "test-api-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"clerkOrgId":"org_test_duplicate","orgName":"Dup Org","slug":"dup-org"}
                    """))
        .andExpect(status().isCreated());

    // Second provision (duplicate)
    mockMvc
        .perform(
            post("/internal/orgs/provision")
                .header("X-API-KEY", "test-api-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"clerkOrgId":"org_test_duplicate","orgName":"Dup Org","slug":"dup-org"}
                    """))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.clerkOrgId").value("org_test_duplicate"));
  }

  @Test
  void provisionWithoutApiKey_returns401() throws Exception {
    mockMvc
        .perform(
            post("/internal/orgs/provision")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"clerkOrgId":"org_test_nokey","orgName":"No Key Org","slug":"no-key"}
                    """))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void provisionWithInvalidApiKey_returns401() throws Exception {
    mockMvc
        .perform(
            post("/internal/orgs/provision")
                .header("X-API-KEY", "wrong-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"clerkOrgId":"org_test_badkey","orgName":"Bad Key Org","slug":"bad-key"}
                    """))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void provisionWithSvixId_idempotent() throws Exception {
    String svixId = "msg_test_idempotent_001";

    // First call with svix-id
    mockMvc
        .perform(
            post("/internal/orgs/provision")
                .header("X-API-KEY", "test-api-key")
                .header("X-Svix-Id", svixId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"clerkOrgId":"org_test_svix","orgName":"Svix Org","slug":"svix-org"}
                    """))
        .andExpect(status().isCreated());

    // Second call with same svix-id (idempotent)
    mockMvc
        .perform(
            post("/internal/orgs/provision")
                .header("X-API-KEY", "test-api-key")
                .header("X-Svix-Id", svixId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"clerkOrgId":"org_test_svix","orgName":"Svix Org","slug":"svix-org"}
                    """))
        .andExpect(status().isConflict());
  }

  @Test
  void updateOrganization_returns200() throws Exception {
    // First provision
    mockMvc
        .perform(
            post("/internal/orgs/provision")
                .header("X-API-KEY", "test-api-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"clerkOrgId":"org_test_update","orgName":"Update Org","slug":"update-org"}
                    """))
        .andExpect(status().isCreated());

    // Then update
    mockMvc
        .perform(
            post("/internal/orgs/update")
                .header("X-API-KEY", "test-api-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"clerkOrgId":"org_test_update","orgName":"Updated Org Name","slug":"updated-org","updatedAt":"2026-02-06T12:00:00Z"}
                    """))
        .andExpect(status().isOk());
  }

  @Test
  void updateNonExistentOrganization_returns204() throws Exception {
    mockMvc
        .perform(
            post("/internal/orgs/update")
                .header("X-API-KEY", "test-api-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"clerkOrgId":"org_does_not_exist","orgName":"Ghost Org","slug":"ghost","updatedAt":"2026-02-06T12:00:00Z"}
                    """))
        .andExpect(status().isNoContent());
  }
}
