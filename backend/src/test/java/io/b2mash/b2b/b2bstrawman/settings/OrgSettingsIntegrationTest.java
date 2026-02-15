package io.b2mash.b2b.b2bstrawman.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
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
import org.springframework.security.core.authority.SimpleGrantedAuthority;
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
class OrgSettingsIntegrationTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_settings_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private OrgSettingsRepository orgSettingsRepository;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private TransactionTemplate transactionTemplate;

  private String tenantSchema;
  private String memberIdOwner;
  private String memberIdAdmin;
  private String memberIdMember;

  @BeforeAll
  void provisionTenant() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "OrgSettings Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");

    memberIdOwner =
        syncMember(ORG_ID, "user_settings_owner", "settings_owner@test.com", "Owner", "owner");
    memberIdAdmin =
        syncMember(ORG_ID, "user_settings_admin", "settings_admin@test.com", "Admin", "admin");
    memberIdMember =
        syncMember(ORG_ID, "user_settings_member", "settings_member@test.com", "Member", "member");

    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
  }

  @Test
  @Order(1)
  void getSettings_returnsDefaultWhenNoSettingsExist() throws Exception {
    mockMvc
        .perform(get("/api/settings").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.defaultCurrency").value("USD"));
  }

  @Test
  @Order(2)
  void putSettings_createsSettings() throws Exception {
    mockMvc
        .perform(
            put("/api/settings")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"defaultCurrency": "EUR"}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.defaultCurrency").value("EUR"));
  }

  @Test
  @Order(3)
  void getSettings_returnsUpdatedValueAfterPut() throws Exception {
    // Ensure settings exist with a known value
    mockMvc
        .perform(
            put("/api/settings")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"defaultCurrency": "GBP"}
                    """))
        .andExpect(status().isOk());

    mockMvc
        .perform(get("/api/settings").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.defaultCurrency").value("GBP"));
  }

  @Test
  @Order(4)
  void putSettings_updatesExistingSettings() throws Exception {
    // First create
    mockMvc
        .perform(
            put("/api/settings")
                .with(adminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"defaultCurrency": "ZAR"}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.defaultCurrency").value("ZAR"));

    // Then update
    mockMvc
        .perform(
            put("/api/settings")
                .with(adminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"defaultCurrency": "JPY"}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.defaultCurrency").value("JPY"));
  }

  @Test
  void putSettings_memberGetsForbidden() throws Exception {
    mockMvc
        .perform(
            put("/api/settings")
                .with(memberJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"defaultCurrency": "CAD"}
                    """))
        .andExpect(status().isForbidden());
  }

  @Test
  void getSettings_memberGetsForbidden() throws Exception {
    mockMvc.perform(get("/api/settings").with(memberJwt())).andExpect(status().isForbidden());
  }

  @Test
  void putSettings_rejectsTwoCharCurrency() throws Exception {
    mockMvc
        .perform(
            put("/api/settings")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"defaultCurrency": "US"}
                    """))
        .andExpect(status().isBadRequest());
  }

  @Test
  void putSettings_rejectsFourCharCurrency() throws Exception {
    mockMvc
        .perform(
            put("/api/settings")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"defaultCurrency": "USDX"}
                    """))
        .andExpect(status().isBadRequest());
  }

  @Test
  void putSettings_rejectsBlankCurrency() throws Exception {
    mockMvc
        .perform(
            put("/api/settings")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"defaultCurrency": ""}
                    """))
        .andExpect(status().isBadRequest());
  }

  // --- Compliance column tests ---

  @Test
  @Order(10)
  void complianceColumnsHaveDefaults() throws Exception {
    // Ensure settings exist by calling GET (creates default if absent)
    mockMvc.perform(get("/api/settings").with(ownerJwt())).andExpect(status().isOk());

    var settingsRef = new AtomicReference<OrgSettings>();
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var settings = orgSettingsRepository.findForCurrentTenant().orElseThrow();
                      settingsRef.set(settings);
                    }));

    var settings = settingsRef.get();
    assertEquals(90, settings.getDormancyThresholdDays());
    assertEquals(30, settings.getDataRequestDeadlineDays());
    assertNull(settings.getCompliancePackStatus());
  }

  @Test
  @Order(11)
  void recordCompliancePackApplicationAppendsToJsonbArray() throws Exception {
    // Ensure settings exist
    mockMvc.perform(get("/api/settings").with(ownerJwt())).andExpect(status().isOk());

    // Apply two compliance packs
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var settings = orgSettingsRepository.findForCurrentTenant().orElseThrow();
                      settings.recordCompliancePackApplication("generic-onboarding", 1);
                      settings.recordCompliancePackApplication("sa-fica-individual", 1);
                      orgSettingsRepository.save(settings);
                    }));

    // Verify JSONB array has 2 entries
    var settingsRef = new AtomicReference<OrgSettings>();
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var settings = orgSettingsRepository.findForCurrentTenant().orElseThrow();
                      settingsRef.set(settings);
                    }));

    var settings = settingsRef.get();
    assertNotNull(settings.getCompliancePackStatus());
    assertEquals(2, settings.getCompliancePackStatus().size());
    assertEquals("generic-onboarding", settings.getCompliancePackStatus().get(0).get("packId"));
    assertEquals("sa-fica-individual", settings.getCompliancePackStatus().get(1).get("packId"));
  }

  // --- Helpers ---

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

  private JwtRequestPostProcessor ownerJwt() {
    return jwt()
        .jwt(j -> j.subject("user_settings_owner").claim("o", Map.of("id", ORG_ID, "rol", "owner")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_OWNER")));
  }

  private JwtRequestPostProcessor adminJwt() {
    return jwt()
        .jwt(j -> j.subject("user_settings_admin").claim("o", Map.of("id", ORG_ID, "rol", "admin")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_ADMIN")));
  }

  private JwtRequestPostProcessor memberJwt() {
    return jwt()
        .jwt(
            j ->
                j.subject("user_settings_member").claim("o", Map.of("id", ORG_ID, "rol", "member")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_MEMBER")));
  }
}
