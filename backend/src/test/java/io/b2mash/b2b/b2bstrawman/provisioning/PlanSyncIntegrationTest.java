package io.b2mash.b2b.b2bstrawman.provisioning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
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
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PlanSyncIntegrationTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_A = "org_plansync_test_a";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrganizationRepository organizationRepository;

  @BeforeAll
  void provisionTenants() {
    provisioningService.provisionTenant(ORG_A, "Plan Sync Test Org A");
  }

  // --- Happy path ---

  @Test
  void shouldUpdatePlanToProTier() throws Exception {
    mockMvc
        .perform(
            post("/internal/orgs/plan-sync")
                .header("X-API-KEY", API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "clerkOrgId": "%s",
                      "planSlug": "pro"
                    }
                    """
                        .formatted(ORG_A)))
        .andExpect(status().isOk());

    var org = organizationRepository.findByClerkOrgId(ORG_A).orElseThrow();
    assertThat(org.getTier()).isEqualTo(Tier.PRO);
    assertThat(org.getPlanSlug()).isEqualTo("pro");
  }

  @Test
  void shouldUpdatePlanToStarterTier() throws Exception {
    mockMvc
        .perform(
            post("/internal/orgs/plan-sync")
                .header("X-API-KEY", API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "clerkOrgId": "%s",
                      "planSlug": "starter-monthly"
                    }
                    """
                        .formatted(ORG_A)))
        .andExpect(status().isOk());

    var org = organizationRepository.findByClerkOrgId(ORG_A).orElseThrow();
    assertThat(org.getTier()).isEqualTo(Tier.STARTER);
    assertThat(org.getPlanSlug()).isEqualTo("starter-monthly");
  }

  @Test
  void shouldDeriveTierFromPlanSlugContainingPro() throws Exception {
    mockMvc
        .perform(
            post("/internal/orgs/plan-sync")
                .header("X-API-KEY", API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "clerkOrgId": "%s",
                      "planSlug": "pro-annual"
                    }
                    """
                        .formatted(ORG_A)))
        .andExpect(status().isOk());

    var org = organizationRepository.findByClerkOrgId(ORG_A).orElseThrow();
    assertThat(org.getTier()).isEqualTo(Tier.PRO);
    assertThat(org.getPlanSlug()).isEqualTo("pro-annual");
  }

  // --- Not found ---

  @Test
  void shouldReturn404ForUnknownOrg() throws Exception {
    mockMvc
        .perform(
            post("/internal/orgs/plan-sync")
                .header("X-API-KEY", API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "clerkOrgId": "org_nonexistent",
                      "planSlug": "pro"
                    }
                    """))
        .andExpect(status().isNotFound());
  }

  // --- Validation ---

  @Test
  void shouldReject400WhenClerkOrgIdBlank() throws Exception {
    mockMvc
        .perform(
            post("/internal/orgs/plan-sync")
                .header("X-API-KEY", API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "clerkOrgId": "",
                      "planSlug": "pro"
                    }
                    """))
        .andExpect(status().isBadRequest());
  }

  @Test
  void shouldReject400WhenPlanSlugBlank() throws Exception {
    mockMvc
        .perform(
            post("/internal/orgs/plan-sync")
                .header("X-API-KEY", API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "clerkOrgId": "%s",
                      "planSlug": ""
                    }
                    """
                        .formatted(ORG_A)))
        .andExpect(status().isBadRequest());
  }

  // --- Auth ---

  @Test
  void shouldReject401WithoutApiKey() throws Exception {
    mockMvc
        .perform(
            post("/internal/orgs/plan-sync")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "clerkOrgId": "%s",
                      "planSlug": "pro"
                    }
                    """
                        .formatted(ORG_A)))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void shouldReject401WithWrongApiKey() throws Exception {
    mockMvc
        .perform(
            post("/internal/orgs/plan-sync")
                .header("X-API-KEY", "wrong-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "clerkOrgId": "%s",
                      "planSlug": "pro"
                    }
                    """
                        .formatted(ORG_A)))
        .andExpect(status().isUnauthorized());
  }
}
