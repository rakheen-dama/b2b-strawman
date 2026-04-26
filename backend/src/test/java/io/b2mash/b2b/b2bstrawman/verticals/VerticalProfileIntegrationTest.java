package io.b2mash.b2b.b2bstrawman.verticals;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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

/**
 * Integration tests for the vertical profile switching lifecycle and module guard chain. Covers
 * tasks 372.1 (profile switching) and 372.3 (guard denies unprovisioned module access).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class VerticalProfileIntegrationTest {

  // Separate org IDs per test group to ensure independence
  private static final String LIFECYCLE_ORG_ID = "org_vpi_lifecycle";
  private static final String GUARD_ORG_ID = "org_vpi_guard";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private AuditService auditService;

  private String lifecycleTenantSchema;
  private String guardTenantSchema;

  @BeforeAll
  void provisionTenantsAndSeedData() throws Exception {
    // Provision lifecycle tenant (null profile — no vertical profile initially)
    lifecycleTenantSchema =
        provisioningService
            .provisionTenant(LIFECYCLE_ORG_ID, "Lifecycle Test Org", null)
            .schemaName();
    TestMemberHelper.syncMemberQuietly(
        mockMvc,
        LIFECYCLE_ORG_ID,
        "user_vpi_lifecycle_owner",
        "vpi_lifecycle@test.com",
        "VPI Owner",
        "owner");

    // Provision guard tenant with consulting-generic (no modules enabled)
    guardTenantSchema =
        provisioningService
            .provisionTenant(GUARD_ORG_ID, "Guard Test Org", "consulting-generic")
            .schemaName();
    TestMemberHelper.syncMemberQuietly(
        mockMvc,
        GUARD_ORG_ID,
        "user_vpi_guard_owner",
        "vpi_guard@test.com",
        "Guard Owner",
        "owner");
  }

  // --- Task 372.1: Profile Switching Lifecycle ---

  @Test
  @Order(1)
  void switchFromNullToLegalZa_setsModulesTerminologyAndLogsAuditEvent() throws Exception {
    // Switch from null to legal-za
    mockMvc
        .perform(
            patch("/api/settings/vertical-profile")
                .with(TestJwtFactory.ownerJwt(LIFECYCLE_ORG_ID, "user_vpi_lifecycle_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"verticalProfile": "legal-za"}"""))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.verticalProfile").value("legal-za"))
        .andExpect(
            jsonPath(
                "$.enabledModules",
                // GAP-L-61-followup (E9.2): bulk_billing ships ON by default for legal-za.
                containsInAnyOrder(
                    "court_calendar",
                    "conflict_check",
                    "lssa_tariff",
                    "trust_accounting",
                    "disbursements",
                    "matter_closure",
                    "deadlines",
                    "information_requests",
                    "bulk_billing")))
        .andExpect(jsonPath("$.terminologyNamespace").value("en-ZA-legal"));

    // Verify audit event was logged
    ScopedValue.where(RequestScopes.TENANT_ID, lifecycleTenantSchema)
        .run(
            () -> {
              var page =
                  auditService.findEvents(
                      new AuditEventFilter(
                          "org_settings",
                          null,
                          null,
                          "org_settings.vertical_profile_changed",
                          null,
                          null),
                      PageRequest.of(0, 10));
              assertThat(page.getTotalElements()).isGreaterThanOrEqualTo(1);
              var event = page.getContent().getFirst();
              assertThat(event.getEventType()).isEqualTo("org_settings.vertical_profile_changed");
              assertThat(event.getEntityType()).isEqualTo("org_settings");
              assertThat(event.getDetails()).containsKey("old_profile");
              assertThat(event.getDetails()).containsKey("new_profile");
              assertThat(event.getDetails()).containsKey("enabled_modules");
            });
  }

  @Test
  @Order(2)
  void switchFromLegalZaToConsultingGeneric_clearsVerticalModulesAndTerminology() throws Exception {
    // First ensure we are on legal-za
    mockMvc
        .perform(
            patch("/api/settings/vertical-profile")
                .with(TestJwtFactory.ownerJwt(LIFECYCLE_ORG_ID, "user_vpi_lifecycle_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"verticalProfile": "legal-za"}"""))
        .andExpect(status().isOk());

    // Switch to consulting-generic. Per ADR-239, vertical modules from legal-za are dropped and
    // terminology is cleared, but HORIZONTAL modules (information_requests, bulk_billing) survive
    // the profile change — they are profile-independent and manually toggled.
    // GAP-L-61-followup (E9.2): bulk_billing is now part of the legal-za default set, so it
    // also survives the switch like other horizontal modules.
    mockMvc
        .perform(
            patch("/api/settings/vertical-profile")
                .with(TestJwtFactory.ownerJwt(LIFECYCLE_ORG_ID, "user_vpi_lifecycle_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"verticalProfile": "consulting-generic"}"""))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.verticalProfile").value("consulting-generic"))
        .andExpect(
            jsonPath(
                "$.enabledModules", containsInAnyOrder("information_requests", "bulk_billing")))
        .andExpect(jsonPath("$.terminologyNamespace").value(nullValue()));
  }

  // --- Task 372.3: Guard Denies Unprovisioned Module Access ---

  @Test
  @Order(3)
  void guardDeniesAccessThenAllowsAfterProfileSwitch() throws Exception {
    // Guard tenant was provisioned with consulting-generic (no modules)
    // Court calendar should be denied (403)
    mockMvc
        .perform(
            get("/api/court-dates")
                .with(TestJwtFactory.ownerJwt(GUARD_ORG_ID, "user_vpi_guard_owner")))
        .andExpect(status().isForbidden());

    // Switch guard tenant to legal-za
    mockMvc
        .perform(
            patch("/api/settings/vertical-profile")
                .with(TestJwtFactory.ownerJwt(GUARD_ORG_ID, "user_vpi_guard_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"verticalProfile": "legal-za"}"""))
        .andExpect(status().isOk());

    // Now court calendar should be allowed (200)
    mockMvc
        .perform(
            get("/api/court-dates")
                .with(TestJwtFactory.ownerJwt(GUARD_ORG_ID, "user_vpi_guard_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isArray());
  }
}
