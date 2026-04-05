package io.b2mash.b2b.b2bstrawman.verticals.legal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.TestPostgresConfiguration;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsService;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestPostgresConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LegalModuleStubControllerTest {
  private static final String ENABLED_ORG_ID = "org_legal_enabled";
  private static final String DISABLED_ORG_ID = "org_legal_disabled";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSettingsRepository orgSettingsRepository;
  @Autowired private OrgSettingsService orgSettingsService;
  @Autowired private TransactionTemplate transactionTemplate;

  private String enabledTenantSchema;
  private String disabledTenantSchema;

  @BeforeAll
  void provisionTenantsAndSeedData() throws Exception {
    // Provision tenant with legal modules enabled
    enabledTenantSchema =
        provisioningService.provisionTenant(ENABLED_ORG_ID, "Legal Enabled Org", null).schemaName();
    TestMemberHelper.syncMemberQuietly(
        mockMvc,
        ENABLED_ORG_ID,
        "user_legal_enabled_owner",
        "legal_enabled@test.com",
        "Legal Owner",
        "owner");

    ScopedValue.where(RequestScopes.TENANT_ID, enabledTenantSchema)
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var settings = orgSettingsService.getOrCreateForCurrentTenant();
                      settings.setEnabledModules(
                          List.of("trust_accounting", "court_calendar", "conflict_check"));
                      orgSettingsRepository.save(settings);
                    }));

    // Provision tenant with no modules enabled (default empty list)
    disabledTenantSchema =
        provisioningService
            .provisionTenant(DISABLED_ORG_ID, "Legal Disabled Org", null)
            .schemaName();
    TestMemberHelper.syncMemberQuietly(
        mockMvc,
        DISABLED_ORG_ID,
        "user_legal_disabled_owner",
        "legal_disabled@test.com",
        "Legal Disabled Owner",
        "owner");
  }

  @Test
  void trustAccountingList_returns200_whenModuleEnabled() throws Exception {
    mockMvc
        .perform(
            get("/api/trust-accounts")
                .with(TestJwtFactory.ownerJwt(ENABLED_ORG_ID, "user_legal_enabled_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray());
  }

  @Test
  void trustAccountingList_returns403_whenModuleDisabled() throws Exception {
    mockMvc
        .perform(
            get("/api/trust-accounts")
                .with(TestJwtFactory.ownerJwt(DISABLED_ORG_ID, "user_legal_disabled_owner")))
        .andExpect(status().isForbidden());
  }

  @Test
  void conflictCheckList_returns200_whenModuleEnabled() throws Exception {
    mockMvc
        .perform(
            get("/api/conflict-checks")
                .with(TestJwtFactory.ownerJwt(ENABLED_ORG_ID, "user_legal_enabled_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isArray());
  }

  @Test
  void conflictCheckList_returns403_whenModuleDisabled() throws Exception {
    mockMvc
        .perform(
            get("/api/conflict-checks")
                .with(TestJwtFactory.ownerJwt(DISABLED_ORG_ID, "user_legal_disabled_owner")))
        .andExpect(status().isForbidden());
  }
}
