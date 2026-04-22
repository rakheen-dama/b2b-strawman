package io.b2mash.b2b.b2bstrawman.settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.audit.AuditEventFilter;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
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

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ModuleSettingsIntegrationTest {

  private static final String ORG_ID = "org_msi_toggle";
  private static final String PROFILE_ORG_ID = "org_msi_profile";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private AuditService auditService;

  private String toggleTenantSchema;

  @BeforeAll
  void provisionTenants() throws Exception {
    toggleTenantSchema =
        provisioningService
            .provisionTenant(ORG_ID, "Module Settings Toggle Org", null)
            .schemaName();
    TestMemberHelper.syncMember(
        mockMvc, ORG_ID, "user_msi_owner", "msi_owner@test.com", "Owner", "owner");
    TestMemberHelper.syncMember(
        mockMvc, ORG_ID, "user_msi_admin", "msi_admin@test.com", "Admin", "admin");
    TestMemberHelper.syncMember(
        mockMvc, ORG_ID, "user_msi_member", "msi_member@test.com", "Member", "member");

    provisioningService.provisionTenant(PROFILE_ORG_ID, "Module Settings Profile Org", null);
    TestMemberHelper.syncMember(
        mockMvc,
        PROFILE_ORG_ID,
        "user_msi_profile_owner",
        "msi_profile_owner@test.com",
        "Owner",
        "owner");
  }

  // --- Task 470.13 case 1 ---
  @Test
  @Order(1)
  void getModules_returnsFourHorizontalModulesAllDisabledByDefault() throws Exception {
    mockMvc
        .perform(
            get("/api/settings/modules").with(TestJwtFactory.ownerJwt(ORG_ID, "user_msi_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.modules.length()").value(4))
        .andExpect(
            jsonPath(
                "$.modules[*].id",
                containsInAnyOrder(
                    "resource_planning",
                    "bulk_billing",
                    "automation_builder",
                    "information_requests")))
        .andExpect(jsonPath("$.modules[?(@.id == 'resource_planning')].enabled", hasItem(false)))
        .andExpect(jsonPath("$.modules[?(@.id == 'bulk_billing')].enabled", hasItem(false)))
        .andExpect(jsonPath("$.modules[?(@.id == 'automation_builder')].enabled", hasItem(false)))
        .andExpect(
            jsonPath("$.modules[?(@.id == 'information_requests')].enabled", hasItem(false)));
  }

  // --- Task 470.13 case 2 ---
  @Test
  @Order(2)
  void putModules_withValidHorizontalIds_returnsUpdatedSettings() throws Exception {
    mockMvc
        .perform(
            put("/api/settings/modules")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_msi_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"enabledModules": ["resource_planning", "bulk_billing"]}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.enabledModules", hasItems("resource_planning", "bulk_billing")));
  }

  // --- Task 470.13 case 3 ---
  @Test
  @Order(3)
  void getModules_afterPut_showsCorrectEnabledState() throws Exception {
    mockMvc
        .perform(
            get("/api/settings/modules").with(TestJwtFactory.ownerJwt(ORG_ID, "user_msi_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.modules[?(@.id == 'resource_planning')].enabled", hasItem(true)))
        .andExpect(jsonPath("$.modules[?(@.id == 'bulk_billing')].enabled", hasItem(true)))
        .andExpect(jsonPath("$.modules[?(@.id == 'automation_builder')].enabled", hasItem(false)));
  }

  // --- Task 470.13 case 4 ---
  @Test
  @Order(4)
  void putModules_withUnknownModuleId_returnsBadRequest() throws Exception {
    mockMvc
        .perform(
            put("/api/settings/modules")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_msi_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"enabledModules": ["bogus_module"]}
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.title").value("Unknown module"))
        .andExpect(jsonPath("$.detail").value("Unknown module ID: bogus_module"));
  }

  // --- Task 470.13 case 5 ---
  @Test
  @Order(5)
  void putModules_withVerticalModuleId_returnsBadRequest() throws Exception {
    mockMvc
        .perform(
            put("/api/settings/modules")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_msi_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"enabledModules": ["trust_accounting"]}
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.title").value("Invalid module"))
        .andExpect(
            jsonPath(
                "$.detail", org.hamcrest.Matchers.containsString("managed by vertical profile")));
  }

  // --- Task 470.13 case 6 ---
  @Test
  @Order(6)
  void putModules_withEmptyArray_disablesAllHorizontalModules() throws Exception {
    // Enable two first
    mockMvc
        .perform(
            put("/api/settings/modules")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_msi_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"enabledModules": ["resource_planning", "automation_builder"]}
                    """))
        .andExpect(status().isOk());

    // Then disable all
    mockMvc
        .perform(
            put("/api/settings/modules")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_msi_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"enabledModules": []}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.enabledModules", not(hasItem("resource_planning"))))
        .andExpect(jsonPath("$.enabledModules", not(hasItem("bulk_billing"))))
        .andExpect(jsonPath("$.enabledModules", not(hasItem("automation_builder"))));

    // Confirm via GET
    mockMvc
        .perform(
            get("/api/settings/modules").with(TestJwtFactory.ownerJwt(ORG_ID, "user_msi_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.modules[?(@.id == 'resource_planning')].enabled", hasItem(false)))
        .andExpect(jsonPath("$.modules[?(@.id == 'bulk_billing')].enabled", hasItem(false)))
        .andExpect(jsonPath("$.modules[?(@.id == 'automation_builder')].enabled", hasItem(false)));
  }

  // --- Task 470.13 case 7 ---
  @Test
  @Order(7)
  void putModules_preservesVerticalModules() throws Exception {
    // Owner sets vertical profile to legal-za (requires owner role)
    mockMvc
        .perform(
            patch("/api/settings/vertical-profile")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_msi_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"verticalProfile": "legal-za"}
                    """))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath(
                "$.enabledModules",
                hasItems("trust_accounting", "court_calendar", "conflict_check", "lssa_tariff")));

    // Enable resource_planning via module toggle
    mockMvc
        .perform(
            put("/api/settings/modules")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_msi_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"enabledModules": ["resource_planning"]}
                    """))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath(
                "$.enabledModules",
                hasItems(
                    "trust_accounting",
                    "court_calendar",
                    "conflict_check",
                    "lssa_tariff",
                    "resource_planning")));

    // Double-check via GET /api/settings
    mockMvc
        .perform(get("/api/settings").with(TestJwtFactory.ownerJwt(ORG_ID, "user_msi_owner")))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath(
                "$.enabledModules",
                hasItems(
                    "trust_accounting",
                    "court_calendar",
                    "conflict_check",
                    "lssa_tariff",
                    "resource_planning")));
  }

  // --- Task 470.13 case 8 ---
  @Test
  @Order(8)
  void putModules_asMember_returnsForbidden() throws Exception {
    mockMvc
        .perform(
            put("/api/settings/modules")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_msi_member"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"enabledModules": ["resource_planning"]}
                    """))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.title").value("Access denied"))
        .andExpect(jsonPath("$.detail").value("Insufficient permissions for this operation"));
  }

  // --- Task 470.13 case 9 ---
  @Test
  @Order(9)
  void putModules_publishesModulesUpdatedAuditEvent() throws Exception {
    mockMvc
        .perform(
            put("/api/settings/modules")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_msi_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"enabledModules": ["bulk_billing", "automation_builder"]}
                    """))
        .andExpect(status().isOk());

    ScopedValue.where(RequestScopes.TENANT_ID, toggleTenantSchema)
        .run(
            () -> {
              var page =
                  auditService.findEvents(
                      new AuditEventFilter(
                          "org_settings", null, null, "org_settings.modules_updated", null, null),
                      PageRequest.of(0, 10));
              assertThat(page.getTotalElements()).isGreaterThanOrEqualTo(1);
              var event = page.getContent().getFirst();
              assertThat(event.getEventType()).isEqualTo("org_settings.modules_updated");
              assertThat(event.getEntityType()).isEqualTo("org_settings");
              assertThat(event.getDetails()).containsKey("before");
              assertThat(event.getDetails()).containsKey("after");
              assertThat(event.getDetails()).containsKey("added");
              assertThat(event.getDetails()).containsKey("removed");
            });
  }

  // --- Task 470.14 — critical merge test per ADR-239 ---
  @Test
  @Order(10)
  void updateVerticalProfile_preservesHorizontalModules() throws Exception {
    // Step 1: enable a horizontal module on a fresh tenant (no profile yet).
    mockMvc
        .perform(
            put("/api/settings/modules")
                .with(TestJwtFactory.ownerJwt(PROFILE_ORG_ID, "user_msi_profile_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"enabledModules": ["resource_planning"]}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.enabledModules", hasItem("resource_planning")));

    // Step 2: change vertical profile to legal-za (adds legal vertical modules).
    mockMvc
        .perform(
            patch("/api/settings/vertical-profile")
                .with(TestJwtFactory.ownerJwt(PROFILE_ORG_ID, "user_msi_profile_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"verticalProfile": "legal-za"}
                    """))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath(
                "$.enabledModules",
                hasItems(
                    "trust_accounting",
                    "court_calendar",
                    "conflict_check",
                    "lssa_tariff",
                    "resource_planning")));

    // Step 3: GET /api/settings/modules must show resource_planning as enabled.
    mockMvc
        .perform(
            get("/api/settings/modules")
                .with(TestJwtFactory.ownerJwt(PROFILE_ORG_ID, "user_msi_profile_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.modules[?(@.id == 'resource_planning')].enabled", hasItem(true)));

    // Step 4: GET /api/settings must contain both legal vertical + horizontal module.
    mockMvc
        .perform(
            get("/api/settings")
                .with(TestJwtFactory.ownerJwt(PROFILE_ORG_ID, "user_msi_profile_owner")))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath(
                "$.enabledModules",
                hasItems("trust_accounting", "court_calendar", "resource_planning")));

    // Step 5: clearing profile (null) must still preserve resource_planning.
    mockMvc
        .perform(
            patch("/api/settings/vertical-profile")
                .with(TestJwtFactory.ownerJwt(PROFILE_ORG_ID, "user_msi_profile_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"verticalProfile": null}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.enabledModules", hasItem("resource_planning")))
        .andExpect(jsonPath("$.enabledModules", not(hasItem("trust_accounting"))));
  }

  // --- Task 470.13 case 11 — missing enabledModules field is rejected ---
  @Test
  @Order(11)
  void putModules_withMissingEnabledModulesField_returnsBadRequest() throws Exception {
    mockMvc
        .perform(
            put("/api/settings/modules")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_msi_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.title").value("Validation failed"));
  }
}
