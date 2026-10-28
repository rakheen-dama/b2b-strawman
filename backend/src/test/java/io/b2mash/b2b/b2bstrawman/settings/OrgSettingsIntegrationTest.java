package io.b2mash.b2b.b2bstrawman.settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.audit.AuditEvent;
import io.b2mash.b2b.b2bstrawman.audit.AuditEventRepository;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
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
  private static final String ORG_ID = "org_settings_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSettingsRepository orgSettingsRepository;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private AuditEventRepository auditEventRepository;

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
        .andExpect(jsonPath("$.defaultCurrency").value("USD"))
        .andExpect(jsonPath("$.letterhead").isMap())
        .andExpect(jsonPath("$.letterhead.addressLine1").value(nullValue()))
        .andExpect(jsonPath("$.letterhead.email").value(nullValue()))
        .andExpect(jsonPath("$.letterhead.bankAccountNumber").value(nullValue()))
        .andExpect(jsonPath("$.letterhead.signatoryDesignation").value(nullValue()));
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
  void getSettings_memberCanReadSettings() throws Exception {
    mockMvc
        .perform(
            get("/api/settings").with(TestJwtFactory.memberJwt(ORG_ID, "user_settings_member")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.defaultCurrency").exists());
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

  // --- Phase 88 U17 (ADR-354): firm matter-retention period on the data-protection panel ---

  @Test
  void getSettings_legalMatterRetentionYears_seededFive() throws Exception {
    mockMvc
        .perform(get("/api/settings").with(TestJwtFactory.ownerJwt(ORG_ID, "user_settings_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.legalMatterRetentionYears").value(5));
  }

  @Test
  void patchDataProtection_setsLegalMatterRetentionYears() throws Exception {
    try {
      mockMvc
          .perform(
              patch("/api/settings/data-protection")
                  .with(TestJwtFactory.ownerJwt(ORG_ID, "user_settings_owner"))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      """
                      {"legalMatterRetentionYears": 7}
                      """))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.legalMatterRetentionYears").value(7));

      mockMvc
          .perform(
              get("/api/settings").with(TestJwtFactory.ownerJwt(ORG_ID, "user_settings_owner")))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.legalMatterRetentionYears").value(7));

      // A PATCH that omits the field leaves the stored value unchanged.
      mockMvc
          .perform(
              patch("/api/settings/data-protection")
                  .with(TestJwtFactory.ownerJwt(ORG_ID, "user_settings_owner"))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      """
                      {"informationOfficerName": "Retention Officer"}
                      """))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.legalMatterRetentionYears").value(7));
    } finally {
      // Restore the seeded value so sibling methods are not order-dependent.
      mockMvc
          .perform(
              patch("/api/settings/data-protection")
                  .with(TestJwtFactory.ownerJwt(ORG_ID, "user_settings_owner"))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      """
                      {"legalMatterRetentionYears": 5}
                      """))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.legalMatterRetentionYears").value(5));
    }
  }

  @Test
  void patchDataProtection_rejectsRetentionYearsAbove30() throws Exception {
    mockMvc
        .perform(
            patch("/api/settings/data-protection")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_settings_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"legalMatterRetentionYears": 31}
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.title").value("Validation failed"))
        .andExpect(jsonPath("$.detail").value("1 field(s) have validation errors"))
        .andExpect(jsonPath("$.fieldErrors[0].field").value("legalMatterRetentionYears"))
        .andExpect(
            jsonPath("$.fieldErrors[0].message")
                .value("legalMatterRetentionYears must be at most 30"));
  }

  @Test
  void patchDataProtection_rejectsRetentionYearsBelow1() throws Exception {
    mockMvc
        .perform(
            patch("/api/settings/data-protection")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_settings_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"legalMatterRetentionYears": 0}
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.title").value("Validation failed"))
        .andExpect(jsonPath("$.fieldErrors[0].field").value("legalMatterRetentionYears"))
        .andExpect(
            jsonPath("$.fieldErrors[0].message")
                .value("legalMatterRetentionYears must be at least 1"));
  }

  @Test
  void patchDataProtection_retentionYears_adminGetsForbidden() throws Exception {
    // The data-protection panel is owner-only; the years field rides the same endpoint.
    mockMvc
        .perform(
            patch("/api/settings/data-protection")
                .with(TestJwtFactory.adminJwt(ORG_ID, "user_settings_admin"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"legalMatterRetentionYears": 9}
                    """))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(get("/api/settings").with(TestJwtFactory.ownerJwt(ORG_ID, "user_settings_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.legalMatterRetentionYears").value(5));
  }

  @Test
  @Order(28)
  void patchExpenseSettings_persistsMarkupAndIsReturnedByGet() throws Exception {
    mockMvc
        .perform(
            patch("/api/settings/expense")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_settings_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"defaultExpenseMarkupPercent": 15.50}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.defaultExpenseMarkupPercent").value(15.50));

    mockMvc
        .perform(get("/api/settings").with(TestJwtFactory.ownerJwt(ORG_ID, "user_settings_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.defaultExpenseMarkupPercent").value(15.50));
  }

  @Test
  @Order(29)
  void patchExpenseSettings_adminCanUpdate() throws Exception {
    mockMvc
        .perform(
            patch("/api/settings/expense")
                .with(TestJwtFactory.adminJwt(ORG_ID, "user_settings_admin"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"defaultExpenseMarkupPercent": 7.25}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.defaultExpenseMarkupPercent").value(7.25));
  }

  @Test
  @Order(30)
  void patchExpenseSettings_nullClearsMarkup() throws Exception {
    // First set a value...
    mockMvc
        .perform(
            patch("/api/settings/expense")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_settings_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"defaultExpenseMarkupPercent": 20.00}
                    """))
        .andExpect(status().isOk());

    // ...then explicit null clears it (single-field endpoint clears, per Javadoc).
    mockMvc
        .perform(
            patch("/api/settings/expense")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_settings_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"defaultExpenseMarkupPercent": null}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.defaultExpenseMarkupPercent").doesNotExist());
  }

  @Test
  @Order(31)
  void putSettings_persistsMatterNamingPatternOnWire() throws Exception {
    // Phase 87 (609F): the wire key is matterNamingPattern. A stale projectNamingPattern body key
    // would be silently ignored by Jackson, so pin the new name on both PUT and GET.
    mockMvc
        .perform(
            put("/api/settings")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_settings_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"defaultCurrency": "ZAR", "matterNamingPattern": "{year}-{seq}"}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.matterNamingPattern").value("{year}-{seq}"))
        .andExpect(jsonPath("$.projectNamingPattern").doesNotExist());

    mockMvc
        .perform(get("/api/settings").with(TestJwtFactory.ownerJwt(ORG_ID, "user_settings_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.matterNamingPattern").value("{year}-{seq}"));
  }

  @Test
  void patchExpenseSettings_memberGetsForbidden() throws Exception {
    mockMvc
        .perform(
            patch("/api/settings/expense")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_settings_member"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"defaultExpenseMarkupPercent": 10.00}
                    """))
        .andExpect(status().isForbidden());
  }

  @Test
  void patchExpenseSettings_rejectsNegativeMarkup() throws Exception {
    mockMvc
        .perform(
            patch("/api/settings/expense")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_settings_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"defaultExpenseMarkupPercent": -1.00}
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.title").value("Validation failed"))
        .andExpect(jsonPath("$.detail").value("1 field(s) have validation errors"))
        .andExpect(jsonPath("$.fieldErrors[0].field").value("defaultExpenseMarkupPercent"))
        .andExpect(
            jsonPath("$.fieldErrors[0].message")
                .value("defaultExpenseMarkupPercent must be non-negative"));
  }

  @Test
  void patchExpenseSettings_rejectsMarkupAboveCeiling() throws Exception {
    mockMvc
        .perform(
            patch("/api/settings/expense")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_settings_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"defaultExpenseMarkupPercent": 1000.00}
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.title").value("Validation failed"))
        .andExpect(jsonPath("$.detail").value("1 field(s) have validation errors"))
        .andExpect(jsonPath("$.fieldErrors[0].field").value("defaultExpenseMarkupPercent"))
        .andExpect(
            jsonPath("$.fieldErrors[0].message")
                .value("defaultExpenseMarkupPercent must not exceed 999.99"));
  }

  /**
   * An all-null batch-billing PATCH must short-circuit (no save, no audit, no updatedAt bump) and
   * leave previously set values untouched — same contract as the compliance-settings sibling.
   */
  @Test
  void patchBatchBillingSettings_allNullPayloadIsNoOpAndPreservesValues() throws Exception {
    mockMvc
        .perform(
            patch("/api/settings/batch-billing")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_settings_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"billingBatchAsyncThreshold": 75}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.billingBatchAsyncThreshold").value(75));

    mockMvc
        .perform(
            patch("/api/settings/batch-billing")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_settings_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.billingBatchAsyncThreshold").value(75));
  }

  // --- Firm default payment terms (Phase 88 U3, ADR-347) — via the existing batch-billing PATCH
  // ---
  // Every case that changes the terms restores 30 so sibling cases see the default.

  @Test
  void getSettings_exposesDefaultPaymentTermsOf30() throws Exception {
    mockMvc
        .perform(get("/api/settings").with(TestJwtFactory.ownerJwt(ORG_ID, "user_settings_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.defaultPaymentTermsDays").value(30));
  }

  @Test
  void patchBatchBilling_paymentTermsRoundTrip() throws Exception {
    var before =
        mockMvc
            .perform(
                get("/api/settings").with(TestJwtFactory.ownerJwt(ORG_ID, "user_settings_owner")))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    Integer threshold = JsonPath.read(before, "$.billingBatchAsyncThreshold");
    try {
      mockMvc
          .perform(
              patch("/api/settings/batch-billing")
                  .with(TestJwtFactory.ownerJwt(ORG_ID, "user_settings_owner"))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      """
                      {"defaultPaymentTermsDays": 14}
                      """))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.defaultPaymentTermsDays").value(14))
          // partial update: the other billing fields are untouched
          .andExpect(jsonPath("$.billingBatchAsyncThreshold").value(threshold));

      mockMvc
          .perform(
              get("/api/settings").with(TestJwtFactory.ownerJwt(ORG_ID, "user_settings_owner")))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.defaultPaymentTermsDays").value(14));

      // 0 is valid: due on issue.
      mockMvc
          .perform(
              patch("/api/settings/batch-billing")
                  .with(TestJwtFactory.adminJwt(ORG_ID, "user_settings_admin"))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      """
                      {"defaultPaymentTermsDays": 0}
                      """))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.defaultPaymentTermsDays").value(0));
    } finally {
      mockMvc
          .perform(
              patch("/api/settings/batch-billing")
                  .with(TestJwtFactory.ownerJwt(ORG_ID, "user_settings_owner"))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      """
                      {"defaultPaymentTermsDays": 30}
                      """))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.defaultPaymentTermsDays").value(30));
    }
  }

  @Test
  void patchBatchBilling_rejectsPaymentTermsAbove365() throws Exception {
    mockMvc
        .perform(
            patch("/api/settings/batch-billing")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_settings_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"defaultPaymentTermsDays": 366}
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.title").value("Validation failed"))
        .andExpect(jsonPath("$.detail").value("1 field(s) have validation errors"))
        .andExpect(jsonPath("$.fieldErrors[0].field").value("defaultPaymentTermsDays"))
        .andExpect(
            jsonPath("$.fieldErrors[0].message")
                .value("defaultPaymentTermsDays must be at most 365"));
  }

  @Test
  void patchBatchBilling_rejectsNegativePaymentTerms() throws Exception {
    mockMvc
        .perform(
            patch("/api/settings/batch-billing")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_settings_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"defaultPaymentTermsDays": -1}
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.title").value("Validation failed"))
        .andExpect(jsonPath("$.detail").value("1 field(s) have validation errors"))
        .andExpect(jsonPath("$.fieldErrors[0].field").value("defaultPaymentTermsDays"))
        .andExpect(
            jsonPath("$.fieldErrors[0].message")
                .value("defaultPaymentTermsDays must be at least 0"));
  }

  @Test
  void patchBatchBilling_paymentTermsForbiddenForMember() throws Exception {
    mockMvc
        .perform(
            patch("/api/settings/batch-billing")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_settings_member"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"defaultPaymentTermsDays": 14}
                    """))
        .andExpect(status().isForbidden());
  }

  // --- Firm letterhead (Phase 88 U8, ADR-353) ---
  // PATCH /api/settings/letterhead is FULL REPLACE: every field is written; null / absent / blank
  // clears. Every mutating case restores the all-null state in finally with body {}.

  private static final String FULL_LETTERHEAD =
      """
      {
        "addressLine1": "12 Rissik Street",
        "addressLine2": "Marshalltown",
        "addressLine3": "Gauteng",
        "city": "Johannesburg",
        "postalCode": "2001",
        "country": "South Africa",
        "phone": "+27 11 555 0100",
        "email": "info@mathebula.law",
        "website": "https://mathebula.law",
        "bankName": "FNB",
        "bankBranchCode": "250655",
        "bankAccountNumber": "62012345678",
        "bankAccountType": "Cheque",
        "paymentReferenceNote": "Use the fee-note number as your reference",
        "signatoryName": "Thandi Mathebula",
        "signatoryDesignation": "Director"
      }
      """;

  @Test
  void patchLetterhead_roundTripsAllSixteenFields() throws Exception {
    try {
      var patched =
          mockMvc
              .perform(
                  patch("/api/settings/letterhead")
                      .with(TestJwtFactory.ownerJwt(ORG_ID, "user_settings_owner"))
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(FULL_LETTERHEAD))
              .andExpect(status().isOk());
      expectFullLetterhead(patched, "$.letterhead.");

      var settings =
          mockMvc
              .perform(
                  get("/api/settings").with(TestJwtFactory.ownerJwt(ORG_ID, "user_settings_owner")))
              .andExpect(status().isOk());
      expectFullLetterhead(settings, "$.letterhead.");

      var group =
          mockMvc
              .perform(
                  get("/api/settings/letterhead")
                      .with(TestJwtFactory.ownerJwt(ORG_ID, "user_settings_owner")))
              .andExpect(status().isOk());
      expectFullLetterhead(group, "$.");
    } finally {
      clearLetterhead();
    }
  }

  @Test
  void patchLetterhead_omittedFieldsAreCleared() throws Exception {
    try {
      mockMvc
          .perform(
              patch("/api/settings/letterhead")
                  .with(TestJwtFactory.ownerJwt(ORG_ID, "user_settings_owner"))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(FULL_LETTERHEAD))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.letterhead.bankName").value("FNB"));

      // Second PATCH resends the address/contact/signatory fields but omits the banking five.
      mockMvc
          .perform(
              patch("/api/settings/letterhead")
                  .with(TestJwtFactory.ownerJwt(ORG_ID, "user_settings_owner"))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      """
                      {
                        "addressLine1": "12 Rissik Street",
                        "city": "Johannesburg",
                        "signatoryName": "Thandi Mathebula",
                        "bankName": null
                      }
                      """))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.letterhead.addressLine1").value("12 Rissik Street"))
          .andExpect(jsonPath("$.letterhead.city").value("Johannesburg"))
          .andExpect(jsonPath("$.letterhead.signatoryName").value("Thandi Mathebula"))
          .andExpect(jsonPath("$.letterhead.bankName").value(nullValue()))
          .andExpect(jsonPath("$.letterhead.bankBranchCode").value(nullValue()))
          .andExpect(jsonPath("$.letterhead.bankAccountNumber").value(nullValue()))
          .andExpect(jsonPath("$.letterhead.bankAccountType").value(nullValue()))
          .andExpect(jsonPath("$.letterhead.paymentReferenceNote").value(nullValue()))
          // other fields not resent are cleared too (full replace)
          .andExpect(jsonPath("$.letterhead.addressLine2").value(nullValue()))
          .andExpect(jsonPath("$.letterhead.email").value(nullValue()));
    } finally {
      clearLetterhead();
    }
  }

  @Test
  void patchLetterhead_blankValuesAreStoredAsNull() throws Exception {
    try {
      mockMvc
          .perform(
              patch("/api/settings/letterhead")
                  .with(TestJwtFactory.ownerJwt(ORG_ID, "user_settings_owner"))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      """
                      {"phone": "   ", "email": "", "city": " Johannesburg "}
                      """))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.letterhead.phone").value(nullValue()))
          .andExpect(jsonPath("$.letterhead.email").value(nullValue()))
          .andExpect(jsonPath("$.letterhead.city").value("Johannesburg"));
    } finally {
      clearLetterhead();
    }
  }

  @Test
  void patchLetterhead_whitespaceOnlyEmailClears() throws Exception {
    // R-2: blank after trim clears — normalised before @Email runs, so this is not a 400.
    try {
      mockMvc
          .perform(
              patch("/api/settings/letterhead")
                  .with(TestJwtFactory.ownerJwt(ORG_ID, "user_settings_owner"))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      """
                      {"email": "   ", "city": "Johannesburg"}
                      """))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.letterhead.email").value(nullValue()))
          .andExpect(jsonPath("$.letterhead.city").value("Johannesburg"));
    } finally {
      clearLetterhead();
    }
  }

  @Test
  void patchLetterhead_paddedEmailIsTrimmedAndStored() throws Exception {
    try {
      mockMvc
          .perform(
              patch("/api/settings/letterhead")
                  .with(TestJwtFactory.ownerJwt(ORG_ID, "user_settings_owner"))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      """
                      {"email": " info@x.law "}
                      """))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.letterhead.email").value("info@x.law"));

      mockMvc
          .perform(
              get("/api/settings/letterhead")
                  .with(TestJwtFactory.ownerJwt(ORG_ID, "user_settings_owner")))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.email").value("info@x.law"));
    } finally {
      clearLetterhead();
    }
  }

  @Test
  void patchLetterhead_sizeAppliesToTrimmedLength() throws Exception {
    // 20 significant characters padded with whitespace fit the 20-char bankBranchCode column.
    try {
      mockMvc
          .perform(
              patch("/api/settings/letterhead")
                  .with(TestJwtFactory.ownerJwt(ORG_ID, "user_settings_owner"))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"bankBranchCode\": \"  " + "1".repeat(20) + "  \"}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.letterhead.bankBranchCode").value("1".repeat(20)));
    } finally {
      clearLetterhead();
    }
  }

  @Test
  void patchLetterhead_rejectsInvalidEmail() throws Exception {
    mockMvc
        .perform(
            patch("/api/settings/letterhead")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_settings_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"email": "not-an-email"}
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.title").value("Validation failed"))
        .andExpect(jsonPath("$.detail").value("1 field(s) have validation errors"))
        .andExpect(jsonPath("$.fieldErrors[0].field").value("email"))
        .andExpect(jsonPath("$.fieldErrors[0].message").value("email must be a valid email"));
  }

  @Test
  void patchLetterhead_rejectsOverlongBranchCode() throws Exception {
    mockMvc
        .perform(
            patch("/api/settings/letterhead")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_settings_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"bankBranchCode\": \"" + "1".repeat(21) + "\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.title").value("Validation failed"))
        .andExpect(jsonPath("$.detail").value("1 field(s) have validation errors"))
        .andExpect(jsonPath("$.fieldErrors[0].field").value("bankBranchCode"))
        .andExpect(
            jsonPath("$.fieldErrors[0].message")
                .value("bankBranchCode must be at most 20 characters"));
  }

  @Test
  void patchLetterhead_adminCanUpdate() throws Exception {
    try {
      mockMvc
          .perform(
              patch("/api/settings/letterhead")
                  .with(TestJwtFactory.adminJwt(ORG_ID, "user_settings_admin"))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      """
                      {"signatoryName": "Admin Signatory", "signatoryDesignation": "Partner"}
                      """))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.letterhead.signatoryName").value("Admin Signatory"))
          .andExpect(jsonPath("$.letterhead.signatoryDesignation").value("Partner"));

      mockMvc
          .perform(
              get("/api/settings/letterhead")
                  .with(TestJwtFactory.adminJwt(ORG_ID, "user_settings_admin")))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.signatoryName").value("Admin Signatory"));
    } finally {
      clearLetterhead();
    }
  }

  @Test
  void letterhead_memberForbidden() throws Exception {
    mockMvc
        .perform(
            patch("/api/settings/letterhead")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_settings_member"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"bankName": "FNB"}
                    """))
        .andExpect(status().isForbidden());

    mockMvc
        .perform(
            get("/api/settings/letterhead")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_settings_member")))
        .andExpect(status().isForbidden());
  }

  @Test
  void getSettings_exposesLetterheadObject() throws Exception {
    mockMvc
        .perform(get("/api/settings").with(TestJwtFactory.ownerJwt(ORG_ID, "user_settings_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.letterhead").isMap())
        .andExpect(jsonPath("$.letterhead.addressLine1").value(nullValue()))
        .andExpect(jsonPath("$.letterhead.bankAccountNumber").value(nullValue()))
        .andExpect(jsonPath("$.letterhead.signatoryDesignation").value(nullValue()));
  }

  @Test
  void patchLetterhead_writesAuditRowWithChangedFieldNamesOnly() throws Exception {
    try {
      clearLetterhead();
      mockMvc
          .perform(
              patch("/api/settings/letterhead")
                  .with(TestJwtFactory.ownerJwt(ORG_ID, "user_settings_owner"))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(FULL_LETTERHEAD))
          .andExpect(status().isOk());

      // findByFilter orders by occurredAt DESC, so getFirst() is the event this PATCH just wrote.
      var events = readEvents("org_settings.letterhead_updated");
      assertThat(events).isNotEmpty();
      var event = events.getFirst();
      assertThat(event.getEntityType()).isEqualTo("org_settings");
      var details = event.getDetails();
      // The audit service adds actor_name itself; the letterhead payload is changed_fields only.
      assertThat(details).containsKey("changed_fields");
      @SuppressWarnings("unchecked")
      var changed = (List<String>) details.get("changed_fields");
      assertThat(changed).contains("bankAccountNumber", "signatoryName", "phone").hasSize(16);
      // POPIA: names only — no values in the audit log.
      assertThat(details.toString())
          .doesNotContain("62012345678")
          .doesNotContain("Thandi Mathebula")
          .doesNotContain("+27 11 555 0100");
    } finally {
      clearLetterhead();
    }
  }

  @Test
  void patchLetterhead_unchangedPatchWritesNoAuditRow() throws Exception {
    // R-6: audit only when something changed. Id-scoped (not a total count) so concurrent writers
    // of the same event type cannot bleed into the assertion.
    try {
      mockMvc
          .perform(
              patch("/api/settings/letterhead")
                  .with(TestJwtFactory.ownerJwt(ORG_ID, "user_settings_owner"))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(FULL_LETTERHEAD))
          .andExpect(status().isOk());
      var idsBefore =
          readEvents("org_settings.letterhead_updated").stream().map(AuditEvent::getId).toList();
      assertThat(idsBefore).isNotEmpty();

      // Identical content, padded with whitespace that normalisation strips — still a no-op.
      mockMvc
          .perform(
              patch("/api/settings/letterhead")
                  .with(TestJwtFactory.ownerJwt(ORG_ID, "user_settings_owner"))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(FULL_LETTERHEAD.replace("\"FNB\"", "\"  FNB  \"")))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.letterhead.bankName").value("FNB"));

      var idsAfter =
          readEvents("org_settings.letterhead_updated").stream().map(AuditEvent::getId).toList();
      assertThat(idsAfter).containsExactlyInAnyOrderElementsOf(idsBefore);
    } finally {
      clearLetterhead();
    }
  }

  @Test
  void letterhead_tenantWithoutSettingsRow_returnsAllNullGroup() throws Exception {
    // R-5 / R-9: provisioning always writes an org_settings row, so the no-row branch needs a
    // fresh tenant whose row is deleted. The class tenant cannot be used — its row carries the
    // vertical profile, modules and pack versions the other tests depend on.
    String noRowOrg = "org_settings_norow_test";
    provisioningService.provisionTenant(noRowOrg, "OrgSettings No-Row Org", null);
    TestMemberHelper.syncMember(
        mockMvc, noRowOrg, "user_norow_owner", "norow_owner@test.com", "Owner", "owner");
    String schema =
        orgSchemaMappingRepository.findByExternalOrgId(noRowOrg).orElseThrow().getSchemaName();
    ScopedValue.where(RequestScopes.TENANT_ID, schema)
        .run(
            () ->
                transactionTemplate.executeWithoutResult(tx -> orgSettingsRepository.deleteAll()));
    assertThat(settingsRowExists(schema)).isFalse();

    mockMvc
        .perform(
            get("/api/settings/letterhead")
                .with(TestJwtFactory.ownerJwt(noRowOrg, "user_norow_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.addressLine1").value(nullValue()))
        .andExpect(jsonPath("$.city").value(nullValue()))
        .andExpect(jsonPath("$.email").value(nullValue()))
        .andExpect(jsonPath("$.bankAccountNumber").value(nullValue()))
        .andExpect(jsonPath("$.signatoryName").value(nullValue()))
        .andExpect(jsonPath("$.signatoryDesignation").value(nullValue()));

    mockMvc
        .perform(get("/api/settings").with(TestJwtFactory.ownerJwt(noRowOrg, "user_norow_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.letterhead").isMap())
        .andExpect(jsonPath("$.letterhead.addressLine1").value(nullValue()))
        .andExpect(jsonPath("$.letterhead.bankAccountNumber").value(nullValue()))
        .andExpect(jsonPath("$.letterhead.signatoryDesignation").value(nullValue()));

    // Both reads served the default branch: neither created a row.
    assertThat(settingsRowExists(schema)).isFalse();
  }

  private boolean settingsRowExists(String schema) {
    boolean[] holder = new boolean[1];
    ScopedValue.where(RequestScopes.TENANT_ID, schema)
        .run(
            () ->
                holder[0] =
                    Boolean.TRUE.equals(
                        transactionTemplate.execute(
                            tx -> orgSettingsRepository.findForCurrentTenant().isPresent())));
    return holder[0];
  }

  private void expectFullLetterhead(
      org.springframework.test.web.servlet.ResultActions result, String prefix) throws Exception {
    result
        .andExpect(jsonPath(prefix + "addressLine1").value("12 Rissik Street"))
        .andExpect(jsonPath(prefix + "addressLine2").value("Marshalltown"))
        .andExpect(jsonPath(prefix + "addressLine3").value("Gauteng"))
        .andExpect(jsonPath(prefix + "city").value("Johannesburg"))
        .andExpect(jsonPath(prefix + "postalCode").value("2001"))
        .andExpect(jsonPath(prefix + "country").value("South Africa"))
        .andExpect(jsonPath(prefix + "phone").value("+27 11 555 0100"))
        .andExpect(jsonPath(prefix + "email").value("info@mathebula.law"))
        .andExpect(jsonPath(prefix + "website").value("https://mathebula.law"))
        .andExpect(jsonPath(prefix + "bankName").value("FNB"))
        .andExpect(jsonPath(prefix + "bankBranchCode").value("250655"))
        .andExpect(jsonPath(prefix + "bankAccountNumber").value("62012345678"))
        .andExpect(jsonPath(prefix + "bankAccountType").value("Cheque"))
        .andExpect(
            jsonPath(prefix + "paymentReferenceNote")
                .value("Use the fee-note number as your reference"))
        .andExpect(jsonPath(prefix + "signatoryName").value("Thandi Mathebula"))
        .andExpect(jsonPath(prefix + "signatoryDesignation").value("Director"));
  }

  private void clearLetterhead() throws Exception {
    mockMvc
        .perform(
            patch("/api/settings/letterhead")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_settings_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isOk());
  }

  private List<AuditEvent> readEvents(String prefix) {
    String tenantSchema =
        orgSchemaMappingRepository.findByExternalOrgId(ORG_ID).orElseThrow().getSchemaName();
    @SuppressWarnings("unchecked")
    List<AuditEvent>[] holder = new List[1];
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .run(
            () ->
                holder[0] =
                    transactionTemplate.execute(
                        tx ->
                            auditEventRepository
                                .findByFilter(
                                    null, null, null, prefix, null, null, PageRequest.of(0, 200))
                                .getContent()));
    return holder[0];
  }

  // --- Helpers ---

}
