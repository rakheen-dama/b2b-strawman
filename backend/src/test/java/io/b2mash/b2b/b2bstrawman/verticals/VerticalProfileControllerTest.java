package io.b2mash.b2b.b2bstrawman.verticals;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class VerticalProfileControllerTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_vert_profile_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSettingsRepository orgSettingsRepository;
  @Autowired private OrgSettingsService orgSettingsService;
  @Autowired private TransactionTemplate transactionTemplate;

  private String tenantSchema;

  @BeforeAll
  void provisionTenantAndSeedData() throws Exception {
    tenantSchema =
        provisioningService.provisionTenant(ORG_ID, "Vertical Profile Test Org", null).schemaName();

    syncMember(ORG_ID, "user_vp_owner", "vp_owner@test.com", "VP Owner", "owner");
    syncMember(ORG_ID, "user_vp_admin", "vp_admin@test.com", "VP Admin", "admin");
    syncMember(ORG_ID, "user_vp_member", "vp_member@test.com", "VP Member", "member");

    // Set up enabled_modules for the modules test (inside a transaction)
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var settings = orgSettingsService.getOrCreateForCurrentTenant();
                      settings.setEnabledModules(List.of("trust_accounting"));
                      orgSettingsRepository.save(settings);
                    }));
  }

  @Test
  @Order(1)
  void getProfiles_returnsProfilesWithCorrectStructure() throws Exception {
    mockMvc
        .perform(get("/api/profiles").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))))
        .andExpect(jsonPath("$[0].id").exists())
        .andExpect(jsonPath("$[0].name").exists())
        .andExpect(jsonPath("$[0].description").exists())
        .andExpect(jsonPath("$[0].modules").isArray());
  }

  @Test
  @Order(2)
  void getModules_returnsModulesWithEnabledStatus() throws Exception {
    mockMvc
        .perform(get("/api/modules").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$", hasSize(5)))
        .andExpect(jsonPath("$[?(@.id == 'trust_accounting')].enabled").value(true))
        .andExpect(jsonPath("$[?(@.id == 'court_calendar')].enabled").value(false))
        .andExpect(jsonPath("$[?(@.id == 'conflict_check')].enabled").value(false))
        .andExpect(jsonPath("$[?(@.id == 'trust_accounting')].status").value("active"));
  }

  @Test
  @Order(3)
  void getProfiles_memberWithoutTeamOversight_returns403() throws Exception {
    mockMvc.perform(get("/api/profiles").with(memberJwt())).andExpect(status().isForbidden());
  }

  @Test
  @Order(4)
  void getModules_memberWithoutTeamOversight_returns403() throws Exception {
    mockMvc.perform(get("/api/modules").with(memberJwt())).andExpect(status().isForbidden());
  }

  @Test
  @Order(5)
  void patchVerticalProfile_ownerSwitchesToLegalZa_setsModulesAndTerminology() throws Exception {
    mockMvc
        .perform(
            patch("/api/settings/vertical-profile")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"verticalProfile": "legal-za"}"""))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.verticalProfile").value("legal-za"))
        .andExpect(
            jsonPath(
                "$.enabledModules",
                containsInAnyOrder(
                    "court_calendar", "conflict_check", "lssa_tariff", "trust_accounting")))
        .andExpect(jsonPath("$.terminologyNamespace").value("en-ZA-legal"));
  }

  @Test
  @Order(6)
  void patchVerticalProfile_invalidProfileId_returns400() throws Exception {
    mockMvc
        .perform(
            patch("/api/settings/vertical-profile")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"verticalProfile": "nonexistent-profile-xyz"}"""))
        .andExpect(status().isBadRequest());
  }

  @Test
  @Order(7)
  void getModules_reflectsProfileChanges() throws Exception {
    // Switch to consulting-generic — all modules should be disabled
    mockMvc
        .perform(
            patch("/api/settings/vertical-profile")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"verticalProfile": "consulting-generic"}"""))
        .andExpect(status().isOk());

    mockMvc
        .perform(get("/api/modules").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(5)))
        .andExpect(jsonPath("$[?(@.id == 'trust_accounting')].enabled").value(false))
        .andExpect(jsonPath("$[?(@.id == 'court_calendar')].enabled").value(false))
        .andExpect(jsonPath("$[?(@.id == 'conflict_check')].enabled").value(false));

    // Switch to legal-za — all legal modules should be enabled
    mockMvc
        .perform(
            patch("/api/settings/vertical-profile")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"verticalProfile": "legal-za"}"""))
        .andExpect(status().isOk());

    mockMvc
        .perform(get("/api/modules").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(5)))
        .andExpect(jsonPath("$[?(@.id == 'trust_accounting')].enabled").value(true))
        .andExpect(jsonPath("$[?(@.id == 'court_calendar')].enabled").value(true))
        .andExpect(jsonPath("$[?(@.id == 'conflict_check')].enabled").value(true))
        .andExpect(jsonPath("$[?(@.id == 'lssa_tariff')].enabled").value(true));
  }

  @Test
  @Order(8)
  void patchVerticalProfile_adminCaller_returns403() throws Exception {
    mockMvc
        .perform(
            patch("/api/settings/vertical-profile")
                .with(adminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"verticalProfile": "legal-za"}"""))
        .andExpect(status().isForbidden());
  }

  // --- Helpers ---

  private void syncMember(
      String orgId, String clerkUserId, String email, String name, String orgRole)
      throws Exception {
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
                        .formatted(orgId, clerkUserId, email, name, orgRole)))
        .andExpect(status().isCreated());
  }

  private JwtRequestPostProcessor ownerJwt() {
    return jwt()
        .jwt(j -> j.subject("user_vp_owner").claim("o", Map.of("id", ORG_ID, "rol", "owner")));
  }

  private JwtRequestPostProcessor adminJwt() {
    return jwt()
        .jwt(j -> j.subject("user_vp_admin").claim("o", Map.of("id", ORG_ID, "rol", "admin")));
  }

  private JwtRequestPostProcessor memberJwt() {
    return jwt()
        .jwt(j -> j.subject("user_vp_member").claim("o", Map.of("id", ORG_ID, "rol", "member")));
  }
}
