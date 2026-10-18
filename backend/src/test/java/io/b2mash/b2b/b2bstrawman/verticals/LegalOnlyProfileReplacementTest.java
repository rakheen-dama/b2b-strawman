package io.b2mash.b2b.b2bstrawman.verticals;

import static io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory.createActiveCustomer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.automation.AutomationRuleRepository;
import io.b2mash.b2b.b2bstrawman.automation.RuleSource;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import io.b2mash.b2b.b2bstrawman.verticals.legal.courtcalendar.CourtDate;
import io.b2mash.b2b.b2bstrawman.verticals.legal.courtcalendar.CourtDateRepository;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
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
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Replacement-path test for Epic 604 (Phase 86 slice 10, ADR-332 / ADR-334): the vertical mechanism
 * is reduced to one {@code legal-za} profile. On a fresh legal-za tenant this asserts the
 * single-profile registry and endpoints, the 8-module registry (2 toggleable horizontals), that
 * unknown/removed profile ids are rejected with 400, that the firm-side regulatory deadlines routes
 * are plain 404s, that V143/V144 dropped {@code filing_statuses} and {@code
 * org_settings.rate_pack_status}, and that the deleted classes are gone from the classpath.
 *
 * <p>On the same legal-za tenant it also proves the surviving automation surface (ADR-334): the
 * pack-installed rules list and toggle with no {@code automation_builder} module, and the removed
 * builder write endpoints answer 405/404; and that matter upcoming deadlines are court dates only.
 * {@code AutomationWithoutBuilderModuleTest} covers the same automation surface on a profile-less
 * tenant plus the headless engine; {@code ProjectUpcomingDeadlinesIntegrationTest} the deadline
 * filtering/ordering.
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
  @Autowired private AutomationRuleRepository ruleRepository;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private ProjectRepository projectRepository;
  @Autowired private CourtDateRepository courtDateRepository;
  @Autowired private TransactionTemplate transactionTemplate;

  private String tenantSchema;
  private UUID ownerMemberId;

  @BeforeAll
  void setup() throws Exception {
    tenantSchema =
        provisioningService
            .provisionTenant(ORG_ID, "Legal Only Profile Org", "legal-za")
            .schemaName();
    ownerMemberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, OWNER, "lop_owner@test.com", "LOP Owner", "owner"));
  }

  @Test
  void packInstalledAutomationRulesListAndToggleWithoutBuilderModule() throws Exception {
    var owner = TestJwtFactory.ownerJwt(ORG_ID, OWNER);
    // Pack-installed (TEMPLATE) rules on this legal-za tenant — no automation_builder module.
    List<UUID> templateRuleIds =
        inTenant(
            () ->
                transactionTemplate.execute(
                    tx ->
                        ruleRepository.findAllByOrderByCreatedAtDesc().stream()
                            .filter(r -> r.getSource() == RuleSource.TEMPLATE)
                            .map(r -> r.getId())
                            .toList()));
    assertThat(templateRuleIds).as("pack-installed rules on a legal-za tenant").isNotEmpty();

    mockMvc
        .perform(get("/api/automation-rules").with(owner))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath(
                "$[*].id",
                hasItems(templateRuleIds.stream().map(UUID::toString).toArray(String[]::new))));

    UUID ruleId = templateRuleIds.getFirst();
    boolean originallyEnabled =
        inTenant(
            () ->
                transactionTemplate.execute(
                    tx -> ruleRepository.findById(ruleId).orElseThrow().isEnabled()));
    mockMvc
        .perform(post("/api/automation-rules/{id}/toggle", ruleId).with(owner))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.enabled", is(!originallyEnabled)));
    // Toggle back so the tenant's pack rules are left as installed.
    mockMvc
        .perform(post("/api/automation-rules/{id}/toggle", ruleId).with(owner))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.enabled", is(originallyEnabled)));
  }

  @Test
  void removedAutomationBuilderWriteEndpointsAreGone() throws Exception {
    var owner = TestJwtFactory.ownerJwt(ORG_ID, OWNER);
    UUID ruleId =
        inTenant(
            () ->
                transactionTemplate.execute(
                    tx -> ruleRepository.findAllByOrderByCreatedAtDesc().getFirst().getId()));
    String body =
        """
        {"name": "x", "triggerType": "TASK_STATUS_CHANGED", "triggerConfig": {}, "actions": []}
        """;
    // A GET mapping survives on these paths, so Spring answers 405 Method Not Allowed.
    mockMvc
        .perform(
            post("/api/automation-rules")
                .with(owner)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isMethodNotAllowed());
    mockMvc
        .perform(
            put("/api/automation-rules/{id}", ruleId)
                .with(owner)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isMethodNotAllowed());
    mockMvc
        .perform(delete("/api/automation-rules/{id}", ruleId).with(owner))
        .andExpect(status().isMethodNotAllowed());
    // No mapping at all on these paths.
    for (String suffix : List.of("duplicate", "test", "actions")) {
      mockMvc
          .perform(
              post("/api/automation-rules/{id}/" + suffix, ruleId)
                  .with(owner)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{}"))
          .andExpect(status().isNotFound());
    }
    // The rule survives the rejected writes.
    mockMvc
        .perform(get("/api/automation-rules/{id}", ruleId).with(owner))
        .andExpect(status().isOk());
  }

  @Test
  void matterUpcomingDeadlinesAreCourtDatesOnly() throws Exception {
    UUID projectId =
        inTenant(
            () ->
                transactionTemplate.execute(
                    tx -> {
                      var customer =
                          customerRepository.saveAndFlush(
                              createActiveCustomer(
                                  "LOP Client", "lop_client@test.com", ownerMemberId));
                      var project = new Project("LOP v Other", "Litigation", ownerMemberId);
                      project.setCustomerId(customer.getId());
                      project = projectRepository.saveAndFlush(project);
                      courtDateRepository.saveAndFlush(
                          new CourtDate(
                              project.getId(),
                              customer.getId(),
                              "HEARING",
                              LocalDate.now().plusDays(30),
                              LocalTime.of(10, 0),
                              "Pretoria High Court",
                              "2026/LOP/1",
                              "Judge LOP",
                              "Motion hearing",
                              7,
                              ownerMemberId));
                      return project.getId();
                    }));

    mockMvc
        .perform(
            get("/api/projects/{id}/upcoming-deadlines", projectId)
                .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)))
        .andExpect(jsonPath("$[*].type", everyItem(is("COURT"))));
  }

  private <T> T inTenant(Supplier<T> work) {
    return ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .call(work::get);
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
