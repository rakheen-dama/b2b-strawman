package io.b2mash.b2b.b2bstrawman.settings;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.TestPostgresConfiguration;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestPostgresConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class OrgSettingsIntegrationTest {
  private static final String ORG_ID = "org_settings_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSettingsRepository orgSettingsRepository;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private TransactionTemplate transactionTemplate;

  private String memberIdOwner;
  private String memberIdAdmin;
  private String memberIdMember;

  @BeforeAll
  void provisionTenant() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "OrgSettings Test Org", null);

    memberIdOwner =
        TestMemberHelper.syncMember(
            mockMvc, ORG_ID, "user_settings_owner", "settings_owner@test.com", "Owner", "owner");
    memberIdAdmin =
        TestMemberHelper.syncMember(
            mockMvc, ORG_ID, "user_settings_admin", "settings_admin@test.com", "Admin", "admin");
    memberIdMember =
        TestMemberHelper.syncMember(
            mockMvc,
            ORG_ID,
            "user_settings_member",
            "settings_member@test.com",
            "Member",
            "member");
  }

  @Test
  @Order(1)
  void getSettings_returnsDefaultWhenNoSettingsExist() throws Exception {
    mockMvc
        .perform(get("/api/settings").with(TestJwtFactory.ownerJwt(ORG_ID, "user_settings_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.defaultCurrency").value("USD"));
  }

  @Test
  @Order(2)
  void putSettings_createsSettings() throws Exception {
    mockMvc
        .perform(
            put("/api/settings")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_settings_owner"))
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
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_settings_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"defaultCurrency": "GBP"}
                    """))
        .andExpect(status().isOk());

    mockMvc
        .perform(get("/api/settings").with(TestJwtFactory.ownerJwt(ORG_ID, "user_settings_owner")))
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
                .with(TestJwtFactory.adminJwt(ORG_ID, "user_settings_admin"))
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
                .with(TestJwtFactory.adminJwt(ORG_ID, "user_settings_admin"))
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
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_settings_member"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"defaultCurrency": "CAD"}
                    """))
        .andExpect(status().isForbidden());
  }

  @Test
  void getSettings_memberGetsForbidden() throws Exception {
    mockMvc
        .perform(
            get("/api/settings").with(TestJwtFactory.memberJwt(ORG_ID, "user_settings_member")))
        .andExpect(status().isForbidden());
  }

  @Test
  void putSettings_rejectsTwoCharCurrency() throws Exception {
    mockMvc
        .perform(
            put("/api/settings")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_settings_owner"))
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
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_settings_owner"))
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
        .perform(get("/api/settings").with(TestJwtFactory.ownerJwt(ORG_ID, "user_settings_owner")))
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
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_settings_owner"))
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
                .with(TestJwtFactory.adminJwt(ORG_ID, "user_settings_admin"))
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
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_settings_member"))
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
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_settings_owner"))
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
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_settings_owner"))
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
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_settings_owner"))
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
        .perform(get("/api/settings").with(TestJwtFactory.ownerJwt(ORG_ID, "user_settings_owner")))
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
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_settings_owner"))
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
        .perform(get("/api/settings").with(TestJwtFactory.ownerJwt(ORG_ID, "user_settings_owner")))
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
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_settings_owner"))
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
        .perform(get("/api/settings").with(TestJwtFactory.ownerJwt(ORG_ID, "user_settings_owner")))
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
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_settings_owner"))
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
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_settings_owner"))
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
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_settings_owner"))
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
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_settings_owner"))
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
        .perform(get("/api/settings").with(TestJwtFactory.ownerJwt(ORG_ID, "user_settings_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.acceptanceExpiryDays").value(60));
  }

  @Test
  void patchAcceptanceSettings_rejectsZeroValue() throws Exception {
    mockMvc
        .perform(
            patch("/api/settings/acceptance")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_settings_owner"))
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
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_settings_owner"))
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
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_settings_member"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"acceptanceExpiryDays": 30}
                    """))
        .andExpect(status().isForbidden());
  }

  @Test
  @Order(15)
  void patchTimeReminderSettings_updatesAllFields() throws Exception {
    mockMvc
        .perform(
            patch("/api/settings/time-reminders")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_settings_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "timeReminderEnabled": true,
                      "timeReminderDays": "MON,WED,FRI",
                      "timeReminderTime": "09:30",
                      "timeReminderMinMinutes": 120
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.timeReminderEnabled").value(true))
        .andExpect(jsonPath("$.timeReminderDays").value("MON,WED,FRI"))
        .andExpect(jsonPath("$.timeReminderTime").value("09:30"))
        .andExpect(jsonPath("$.timeReminderMinHours").value(2.0));
  }

  @Test
  @Order(16)
  void getSettings_returnsTimeReminderFields() throws Exception {
    mockMvc
        .perform(get("/api/settings").with(TestJwtFactory.ownerJwt(ORG_ID, "user_settings_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.timeReminderEnabled").value(true))
        .andExpect(jsonPath("$.timeReminderDays").value("MON,WED,FRI"))
        .andExpect(jsonPath("$.timeReminderTime").value("09:30"))
        .andExpect(jsonPath("$.timeReminderMinHours").value(2.0));
  }

  @Test
  void patchTimeReminderSettings_rejectsInvalidTime() throws Exception {
    mockMvc
        .perform(
            patch("/api/settings/time-reminders")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_settings_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"timeReminderTime": "25:99"}
                    """))
        .andExpect(status().isBadRequest());
  }

  @Test
  void patchTimeReminderSettings_rejectsInvalidDays() throws Exception {
    mockMvc
        .perform(
            patch("/api/settings/time-reminders")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_settings_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"timeReminderDays": "MONDAY,FUNDAY"}
                    """))
        .andExpect(status().isBadRequest());
  }

  @Test
  void patchTimeReminderSettings_memberGetsForbidden() throws Exception {
    mockMvc
        .perform(
            patch("/api/settings/time-reminders")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_settings_member"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"timeReminderEnabled": true}
                    """))
        .andExpect(status().isForbidden());
  }

  @Test
  @Order(20)
  void getSettings_includesVerticalProfileFields_defaultsToNullAndEmptyList() throws Exception {
    mockMvc
        .perform(get("/api/settings").with(TestJwtFactory.ownerJwt(ORG_ID, "user_settings_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.enabledModules").isArray())
        .andExpect(jsonPath("$.enabledModules").isEmpty());
  }

  @Test
  @Order(21)
  void getSettings_returnsEnabledModulesAfterProvisioning() throws Exception {
    String verticalOrgId = "org_settings_vertical_test";
    provisioningService.provisionTenant(verticalOrgId, "Vertical Test Org", null);

    TestMemberHelper.syncMember(
        mockMvc, verticalOrgId, "user_vert_owner", "vert_owner@test.com", "Vert Owner", "owner");

    var vertJwt =
        jwt()
            .jwt(
                j ->
                    j.subject("user_vert_owner")
                        .claim("o", Map.of("id", verticalOrgId, "rol", "owner")));

    // Default state: no modules, no profile
    mockMvc
        .perform(get("/api/settings").with(vertJwt))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.enabledModules").isArray())
        .andExpect(jsonPath("$.enabledModules").isEmpty());
  }

  @Test
  @Order(22)
  void getSettings_returnsPopulatedEnabledModulesAndVerticalFields() throws Exception {
    String vertOrgId = "org_settings_vert_populated";
    provisioningService.provisionTenant(vertOrgId, "Vertical Populated Org", null);

    TestMemberHelper.syncMember(
        mockMvc, vertOrgId, "user_vert_pop", "vert_pop@test.com", "Vert Pop Owner", "owner");

    // Resolve tenant schema and directly update OrgSettings via repository
    String tenantSchema =
        orgSchemaMappingRepository.findByExternalOrgId(vertOrgId).orElseThrow().getSchemaName();

    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var settings = orgSettingsRepository.findForCurrentTenant().orElseThrow();
                      settings.updateVerticalProfile(
                          "legal", List.of("trust_accounting", "court_calendar"), "legal");
                      orgSettingsRepository.save(settings);
                    }));

    var vertJwt =
        jwt()
            .jwt(
                j ->
                    j.subject("user_vert_pop").claim("o", Map.of("id", vertOrgId, "rol", "owner")));

    mockMvc
        .perform(get("/api/settings").with(vertJwt))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.verticalProfile").value("legal"))
        .andExpect(jsonPath("$.enabledModules").isArray())
        .andExpect(jsonPath("$.enabledModules.length()").value(2))
        .andExpect(jsonPath("$.enabledModules[0]").value("trust_accounting"))
        .andExpect(jsonPath("$.enabledModules[1]").value("court_calendar"))
        .andExpect(jsonPath("$.terminologyNamespace").value("legal"));
  }

  @Test
  @Order(23)
  void patchDataProtectionSettings_ownerCanSetJurisdiction() throws Exception {
    mockMvc
        .perform(
            patch("/api/settings/data-protection")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_settings_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "dataProtectionJurisdiction": "ZA",
                      "retentionPolicyEnabled": true,
                      "financialRetentionMonths": 60
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.dataProtectionJurisdiction").value("ZA"))
        .andExpect(jsonPath("$.retentionPolicyEnabled").value(true))
        .andExpect(jsonPath("$.financialRetentionMonths").value(60));
  }

  @Test
  @Order(24)
  void getSettings_includesDataProtectionFields() throws Exception {
    mockMvc
        .perform(get("/api/settings").with(TestJwtFactory.ownerJwt(ORG_ID, "user_settings_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.dataProtectionJurisdiction").value("ZA"))
        .andExpect(jsonPath("$.retentionPolicyEnabled").value(true))
        .andExpect(jsonPath("$.financialRetentionMonths").value(60));
  }

  @Test
  @Order(25)
  void patchDataProtectionSettings_memberGetsForbidden() throws Exception {
    mockMvc
        .perform(
            patch("/api/settings/data-protection")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_settings_member"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"dataProtectionJurisdiction": "ZA"}
                    """))
        .andExpect(status().isForbidden());
  }

  @Test
  @Order(26)
  void patchDataProtectionSettings_rejectsFinancialRetentionBelowJurisdictionMinimum()
      throws Exception {
    // First set jurisdiction to ZA (minimum 60 months)
    mockMvc
        .perform(
            patch("/api/settings/data-protection")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_settings_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"dataProtectionJurisdiction": "ZA"}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.dataProtectionJurisdiction").value("ZA"));

    // Then try to set financialRetentionMonths below ZA's 60-month minimum
    mockMvc
        .perform(
            patch("/api/settings/data-protection")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_settings_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"financialRetentionMonths": 12}
                    """))
        .andExpect(status().isBadRequest());
  }

  @Test
  @Order(27)
  void patchDataProtectionSettings_rejectsInvalidEmail() throws Exception {
    mockMvc
        .perform(
            patch("/api/settings/data-protection")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_settings_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"informationOfficerEmail": "not-an-email"}
                    """))
        .andExpect(status().isBadRequest());
  }

  // --- Helpers ---

}
