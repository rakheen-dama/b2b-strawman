package io.b2mash.b2b.b2bstrawman.provisioning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class ProvisioningControllerTest {

  private static final String API_KEY = "test-api-key";

  @Autowired private MockMvc mockMvc;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private OrganizationRepository organizationRepository;

  @Test
  void shouldProvisionTenantViaApi() throws Exception {
    mockMvc
        .perform(
            post("/internal/orgs/provision")
                .header("X-API-KEY", API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"clerkOrgId": "org_ctrl_test", "orgName": "Controller Test Org"}
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.schemaName").exists())
        .andExpect(jsonPath("$.status").value("COMPLETED"))
        .andExpect(jsonPath("$.message").value("Tenant provisioned successfully"));
  }

  @Test
  void shouldReturn409OnDuplicateProvision() throws Exception {
    mockMvc
        .perform(
            post("/internal/orgs/provision")
                .header("X-API-KEY", API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"clerkOrgId": "org_dup_test", "orgName": "Duplicate Org"}
                    """))
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            post("/internal/orgs/provision")
                .header("X-API-KEY", API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"clerkOrgId": "org_dup_test", "orgName": "Duplicate Org"}
                    """))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message").value("Tenant already provisioned"));
  }

  @Test
  void shouldReject401WithoutApiKey() throws Exception {
    mockMvc
        .perform(
            post("/internal/orgs/provision")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"clerkOrgId": "org_no_key", "orgName": "No Key Org"}
                    """))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void shouldReject400OnMissingClerkOrgId() throws Exception {
    mockMvc
        .perform(
            post("/internal/orgs/provision")
                .header("X-API-KEY", API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"clerkOrgId": "", "orgName": "Invalid Org"}
                    """))
        .andExpect(status().isBadRequest());
  }

  @Test
  void shouldReject400OnMissingOrgName() throws Exception {
    mockMvc
        .perform(
            post("/internal/orgs/provision")
                .header("X-API-KEY", API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"clerkOrgId": "org_no_name", "orgName": ""}
                    """))
        .andExpect(status().isBadRequest());
  }

  // --- Phase 86 (Epic 604B, ADR-332): legal-za default, unknown profile → 400 ---

  @Test
  void shouldDefaultVerticalProfileToLegalZaWhenOmitted() throws Exception {
    var result =
        mockMvc
            .perform(
                post("/internal/orgs/provision")
                    .header("X-API-KEY", API_KEY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"clerkOrgId": "org_ctrl_default_profile", "orgName": "Default Profile Org"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    String schema = JsonPath.read(result.getResponse().getContentAsString(), "$.schemaName");
    assertThat(schema).matches("tenant_[0-9a-f]{12}");

    String profile =
        jdbcTemplate.queryForObject(
            "SELECT vertical_profile FROM " + schema + ".org_settings", String.class);
    assertThat(profile).isEqualTo("legal-za");
  }

  @Test
  void shouldReject400OnUnknownVerticalProfile() throws Exception {
    mockMvc
        .perform(
            post("/internal/orgs/provision")
                .header("X-API-KEY", API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"clerkOrgId": "org_ctrl_unknown_profile", "orgName": "Unknown Profile Org",
                     "verticalProfile": "accounting-za"}
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.title").value("Unknown vertical profile"));

    assertThat(organizationRepository.findByClerkOrgId("org_ctrl_unknown_profile")).isEmpty();
  }
}
