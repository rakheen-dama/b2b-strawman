package io.b2mash.b2b.b2bstrawman.verticals;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Replacement-path test for Epic 604 (Phase 86 slice 10, ADR-332 / ADR-334): the vertical mechanism
 * is reduced to one {@code legal-za} profile. On a fresh legal-za tenant this asserts the
 * single-profile registry and endpoints, the 8-module registry (2 toggleable horizontals), that
 * unknown/removed profile ids are rejected with 400, that the firm-side regulatory deadlines routes
 * are plain 404s, that V143/V144 dropped {@code filing_statuses} and {@code
 * org_settings.rate_pack_status}, and that the deleted classes are gone from the classpath.
 *
 * <p>The always-on automation surface (list/toggle/executions with no module, removed builder write
 * endpoints) is covered by {@code AutomationWithoutBuilderModuleTest}; court-only matter deadlines
 * by {@code ProjectUpcomingDeadlinesIntegrationTest}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LegalOnlyProfileReplacementTest {

  private static final String ORG_ID = "org_legal_only_profile";
  private static final String OWNER = "user_lop_owner";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private VerticalProfileRegistry verticalProfileRegistry;
  @Autowired private DataSource dataSource;

  private String tenantSchema;

  @BeforeAll
  void setup() throws Exception {
    tenantSchema =
        provisioningService
            .provisionTenant(ORG_ID, "Legal Only Profile Org", "legal-za")
            .schemaName();
    TestMemberHelper.syncMember(mockMvc, ORG_ID, OWNER, "lop_owner@test.com", "LOP Owner", "owner");
  }

  @Test
  void registryAndProfilesEndpointExposeOnlyLegalZa() throws Exception {
    assertThat(verticalProfileRegistry.getAllProfiles())
        .extracting(VerticalProfileRegistry.ProfileDefinition::profileId)
        .containsExactly("legal-za");

    mockMvc
        .perform(get("/api/profiles").with(TestJwtFactory.ownerJwt(ORG_ID, OWNER)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)))
        .andExpect(jsonPath("$[0].id").value("legal-za"));
  }

  @Test
  void moduleRegistryHasEightModulesAndTwoHorizontals() throws Exception {
    mockMvc
        .perform(get("/api/modules").with(TestJwtFactory.ownerJwt(ORG_ID, OWNER)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(8)))
        .andExpect(jsonPath("$[*].id", not(hasItem("regulatory_deadlines"))))
        .andExpect(jsonPath("$[*].id", not(hasItem("automation_builder"))));

    mockMvc
        .perform(get("/api/settings/modules").with(TestJwtFactory.ownerJwt(ORG_ID, OWNER)))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath(
                "$.modules[*].id", containsInAnyOrder("bulk_billing", "information_requests")));

    // The removed horizontal id can no longer be enabled.
    mockMvc
        .perform(
            put("/api/settings/modules")
                .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"enabledModules": ["automation_builder"]}
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.title").value("Unknown module"));
  }

  @Test
  void removedProfileIdsAreRejected() throws Exception {
    for (String profile : List.of("accounting-za", "consulting-za", "consulting-generic")) {
      mockMvc
          .perform(
              patch("/api/settings/vertical-profile")
                  .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      """
                      {"verticalProfile": "%s"}
                      """
                          .formatted(profile)))
          .andExpect(status().isBadRequest());

      String orgId = "org_lop_removed_" + UUID.randomUUID().toString().substring(0, 8);
      assertThatThrownBy(() -> provisioningService.provisionTenant(orgId, "Removed", profile))
          .as(profile)
          .isInstanceOf(InvalidStateException.class);
    }
  }

  @Test
  void regulatoryDeadlineRoutesAreGone() throws Exception {
    for (String path :
        List.of(
            "/api/deadlines",
            "/api/deadlines/summary",
            "/api/filing-statuses",
            "/api/customers/" + UUID.randomUUID() + "/deadlines")) {
      mockMvc
          .perform(get(path).with(TestJwtFactory.ownerJwt(ORG_ID, OWNER)))
          .andExpect(status().isNotFound());
    }
    mockMvc
        .perform(
            put("/api/deadlines/filing-status")
                .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isNotFound());
  }

  @Test
  void droppedSchemaObjectsAreGone() {
    var jdbc = new JdbcTemplate(dataSource);
    String regclass =
        jdbc.queryForObject(
            "SELECT to_regclass(?)::text", String.class, tenantSchema + ".filing_statuses");
    assertThat(regclass).as("filing_statuses (V143)").isNull();

    Integer ratePackColumn =
        jdbc.queryForObject(
            "SELECT count(*) FROM information_schema.columns WHERE table_schema = ?"
                + " AND table_name = 'org_settings' AND column_name = 'rate_pack_status'",
            Integer.class,
            tenantSchema);
    assertThat(ratePackColumn).as("org_settings.rate_pack_status (V144)").isZero();

    List<String> enabledModules =
        jdbc.queryForList(
            "SELECT jsonb_array_elements_text(enabled_modules) FROM "
                + tenantSchema
                + ".org_settings",
            String.class);
    assertThat(enabledModules)
        .contains("trust_accounting")
        .doesNotContain("regulatory_deadlines", "automation_builder");
  }

  @Test
  void removedClassesAreAbsentFromTheClasspath() {
    var classes =
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("io.b2mash.b2b.b2bstrawman");
    assertThat(classes.stream().map(c -> c.getPackageName()))
        .contains("io.b2mash.b2b.b2bstrawman.project")
        .noneMatch(
            p ->
                p.equals("io.b2mash.b2b.b2bstrawman.deadline")
                    || p.startsWith("io.b2mash.b2b.b2bstrawman.deadline."));
    for (String fqcn :
        List.of(
            "io.b2mash.b2b.b2bstrawman.automation.AutomationActionController",
            "io.b2mash.b2b.b2bstrawman.seeder.RatePackSeeder",
            "io.b2mash.b2b.b2bstrawman.seeder.RatePackDefinition",
            "io.b2mash.b2b.b2bstrawman.demo.seed.GenericDemoDataSeeder",
            "io.b2mash.b2b.b2bstrawman.demo.seed.AccountingDemoDataSeeder")) {
      assertThatThrownBy(() -> Class.forName(fqcn))
          .as(fqcn)
          .isInstanceOf(ClassNotFoundException.class);
    }
  }
}
