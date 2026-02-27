package io.b2mash.b2b.b2bstrawman.settings;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
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
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

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
  @Order(5)
  void getSettings_returnsCompliancePackStatus() throws Exception {
    mockMvc
        .perform(get("/api/settings").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.compliancePackStatus").isArray())
        .andExpect(jsonPath("$.compliancePackStatus").isNotEmpty())
        .andExpect(jsonPath("$.compliancePackStatus[0].packId").isString())
        .andExpect(jsonPath("$.compliancePackStatus[0].version").isString())
        .andExpect(jsonPath("$.compliancePackStatus[0].appliedAt").isString());
  }

  @Test
  @Order(6)
  void patchComplianceSettings_updatesDormancyThreshold() throws Exception {
    mockMvc
        .perform(
            patch("/api/settings/compliance")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"dormancyThresholdDays": 90, "dataRequestDeadlineDays": 30}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.dormancyThresholdDays").value(90))
        .andExpect(jsonPath("$.dataRequestDeadlineDays").value(30));
  }

  @Test
  @Order(7)
  void patchComplianceSettings_partialUpdateOnlyDormancy() throws Exception {
    mockMvc
        .perform(
            patch("/api/settings/compliance")
                .with(adminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"dormancyThresholdDays": 120}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.dormancyThresholdDays").value(120))
        .andExpect(jsonPath("$.dataRequestDeadlineDays").value(30));
  }

  @Test
  void patchComplianceSettings_memberGetsForbidden() throws Exception {
    mockMvc
        .perform(
            patch("/api/settings/compliance")
                .with(memberJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"dormancyThresholdDays": 90}
                    """))
        .andExpect(status().isForbidden());
  }

  @Test
  void patchComplianceSettings_rejectsNegativeValues() throws Exception {
    mockMvc
        .perform(
            patch("/api/settings/compliance")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"dormancyThresholdDays": -1}
                    """))
        .andExpect(status().isBadRequest());
  }

  @Test
  void patchComplianceSettings_rejectsZeroValues() throws Exception {
    mockMvc
        .perform(
            patch("/api/settings/compliance")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"dataRequestDeadlineDays": 0}
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

  @Test
  @Order(8)
  void getSettings_includesIntegrationFlagsDefaultFalse() throws Exception {
    mockMvc
        .perform(get("/api/settings").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accountingEnabled").value(false))
        .andExpect(jsonPath("$.aiEnabled").value(false))
        .andExpect(jsonPath("$.documentSigningEnabled").value(false));
  }

  @Test
  @Order(9)
  void putSettings_setsIntegrationFlags() throws Exception {
    // Set flags to true
    mockMvc
        .perform(
            put("/api/settings")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "defaultCurrency": "USD",
                      "accountingEnabled": true,
                      "aiEnabled": true,
                      "documentSigningEnabled": false
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accountingEnabled").value(true))
        .andExpect(jsonPath("$.aiEnabled").value(true))
        .andExpect(jsonPath("$.documentSigningEnabled").value(false));

    // Verify via GET
    mockMvc
        .perform(get("/api/settings").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accountingEnabled").value(true))
        .andExpect(jsonPath("$.aiEnabled").value(true))
        .andExpect(jsonPath("$.documentSigningEnabled").value(false));
  }

  @Test
  @Order(10)
  void patchTaxSettings_savesTaxFields() throws Exception {
    mockMvc
        .perform(
            patch("/api/settings/tax")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "taxRegistrationNumber": "VAT-123456789",
                      "taxRegistrationLabel": "VAT Number",
                      "taxLabel": "VAT",
                      "taxInclusive": true
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.taxRegistrationNumber").value("VAT-123456789"))
        .andExpect(jsonPath("$.taxRegistrationLabel").value("VAT Number"))
        .andExpect(jsonPath("$.taxLabel").value("VAT"))
        .andExpect(jsonPath("$.taxInclusive").value(true));
  }

  @Test
  @Order(11)
  void getSettings_returnsTaxFields() throws Exception {
    mockMvc
        .perform(get("/api/settings").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.taxRegistrationNumber").value("VAT-123456789"))
        .andExpect(jsonPath("$.taxRegistrationLabel").value("VAT Number"))
        .andExpect(jsonPath("$.taxLabel").value("VAT"))
        .andExpect(jsonPath("$.taxInclusive").value(true));
  }

  @Test
  @Order(12)
  void patchTaxSettings_validatesMaxLengths() throws Exception {
    // taxRegistrationNumber max 50
    mockMvc
        .perform(
            patch("/api/settings/tax")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "taxRegistrationNumber": "%s",
                      "taxRegistrationLabel": "Label",
                      "taxLabel": "Tax",
                      "taxInclusive": false
                    }
                    """
                        .formatted("A".repeat(51))))
        .andExpect(status().isBadRequest());

    // taxRegistrationLabel max 30
    mockMvc
        .perform(
            patch("/api/settings/tax")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "taxRegistrationNumber": "VAT-123",
                      "taxRegistrationLabel": "%s",
                      "taxLabel": "Tax",
                      "taxInclusive": false
                    }
                    """
                        .formatted("B".repeat(31))))
        .andExpect(status().isBadRequest());

    // taxLabel max 20
    mockMvc
        .perform(
            patch("/api/settings/tax")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "taxRegistrationNumber": "VAT-123",
                      "taxRegistrationLabel": "Label",
                      "taxLabel": "%s",
                      "taxInclusive": false
                    }
                    """
                        .formatted("C".repeat(21))))
        .andExpect(status().isBadRequest());
  }

  @Test
  @Order(13)
  void patchAcceptanceSettings_updatesExpiryDays() throws Exception {
    mockMvc
        .perform(
            patch("/api/settings/acceptance")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"acceptanceExpiryDays": 60}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.acceptanceExpiryDays").value(60));
  }

  @Test
  @Order(14)
  void getSettings_returnsAcceptanceExpiryDays() throws Exception {
    mockMvc
        .perform(get("/api/settings").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.acceptanceExpiryDays").value(60));
  }

  @Test
  void patchAcceptanceSettings_rejectsZeroValue() throws Exception {
    mockMvc
        .perform(
            patch("/api/settings/acceptance")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"acceptanceExpiryDays": 0}
                    """))
        .andExpect(status().isBadRequest());
  }

  @Test
  void patchAcceptanceSettings_rejectsValueOver365() throws Exception {
    mockMvc
        .perform(
            patch("/api/settings/acceptance")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"acceptanceExpiryDays": 366}
                    """))
        .andExpect(status().isBadRequest());
  }

  @Test
  void patchAcceptanceSettings_memberGetsForbidden() throws Exception {
    mockMvc
        .perform(
            patch("/api/settings/acceptance")
                .with(memberJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"acceptanceExpiryDays": 30}
                    """))
        .andExpect(status().isForbidden());
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
