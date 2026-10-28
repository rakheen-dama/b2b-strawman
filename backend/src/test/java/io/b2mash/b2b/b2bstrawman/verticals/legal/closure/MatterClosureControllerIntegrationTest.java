package io.b2mash.b2b.b2bstrawman.verticals.legal.closure;

import static io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory.createActiveCustomer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.settings.DataProtectionSettings;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsService;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
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
 * Controller-level integration tests for {@link MatterClosureController} (Epic 489B, task 489.19).
 *
 * <p>Covers module guard (403 on non-legal tenant), 200 on evaluate + close happy path (uses
 * override=true so it works without full financial scaffolding), 409 gate-fail with ProblemDetail
 * {@code report} extension, and 403 when an admin (no OVERRIDE_MATTER_CLOSURE capability) attempts
 * an override.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MatterClosureControllerIntegrationTest {

  private static final String LEGAL_ORG_ID = "org_matter_closure_ctrl_legal";
  private static final String NON_LEGAL_ORG_ID = "org_matter_closure_ctrl_nonlegal";

  private static final String VALID_JUSTIFICATION =
      "Senior partner approved override: trust reconciled, no open court dates, client notified.";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private OrgSettingsRepository orgSettingsRepository;
  @Autowired private OrgSettingsService orgSettingsService;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private ProjectRepository projectRepository;
  @Autowired private JdbcTemplate jdbcTemplate;

  private String legalTenantSchema;
  private String nonLegalTenantSchema;
  private UUID legalOwnerMemberId;
  private UUID customerId;

  @BeforeAll
  void setup() throws Exception {
    // --- Legal tenant with matter_closure module enabled ---
    provisioningService.provisionTenant(LEGAL_ORG_ID, "Closure Ctrl Firm", "legal-za");

    legalOwnerMemberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                LEGAL_ORG_ID,
                "user_closure_ctrl_owner",
                "closure_ctrl_owner@test.com",
                "Closure Ctrl Owner",
                "owner"));
    TestMemberHelper.syncMember(
        mockMvc,
        LEGAL_ORG_ID,
        "user_closure_ctrl_admin",
        "closure_ctrl_admin@test.com",
        "Closure Ctrl Admin",
        "admin");

    legalTenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(LEGAL_ORG_ID).orElseThrow().getSchemaName();

    runInLegalTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var settings = orgSettingsService.getOrCreateForCurrentTenant();
                  settings.setEnabledModules(List.of("matter_closure"));
                  orgSettingsRepository.save(settings);

                  var customer =
                      createActiveCustomer(
                          "Closure Ctrl Client", "closure_ctrl@test.com", legalOwnerMemberId);
                  customer = customerRepository.saveAndFlush(customer);
                  customerId = customer.getId();
                }));

    // --- Non-legal tenant (matter_closure NOT enabled) ---
    provisioningService.provisionTenant(NON_LEGAL_ORG_ID, "NonLegal Ctrl Firm", null);
    TestMemberHelper.syncMember(
        mockMvc,
        NON_LEGAL_ORG_ID,
        "user_closure_nonlegal_owner",
        "closure_nonlegal@test.com",
        "NonLegal Owner",
        "owner");
    nonLegalTenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(NON_LEGAL_ORG_ID).orElseThrow().getSchemaName();

    ScopedValue.where(RequestScopes.TENANT_ID, nonLegalTenantSchema)
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var settings = orgSettingsService.getOrCreateForCurrentTenant();
                      settings.setEnabledModules(List.of());
                      orgSettingsRepository.save(settings);
                    }));
  }

  // ==========================================================================
  // Module guard
  // ==========================================================================

  @Test
  void nonLegalTenant_GET_evaluate_returns403_fromModuleGuard() throws Exception {
    mockMvc
        .perform(
            get("/api/matters/" + UUID.randomUUID() + "/closure/evaluate")
                .with(TestJwtFactory.ownerJwt(NON_LEGAL_ORG_ID, "user_closure_nonlegal_owner")))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.title").value("Module not enabled"))
        .andExpect(jsonPath("$.moduleId").value("matter_closure"));
  }

  // ==========================================================================
  // GET /evaluate happy path
  // ==========================================================================

  @Test
  void legalTenant_GET_evaluate_returns200_withAllGates() throws Exception {
    UUID projectId = createProject("Evaluate Happy Matter");

    mockMvc
        .perform(
            get("/api/matters/" + projectId + "/closure/evaluate")
                .with(TestJwtFactory.ownerJwt(LEGAL_ORG_ID, "user_closure_ctrl_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.matterId").value(projectId.toString()))
        .andExpect(jsonPath("$.allPassed").exists())
        .andExpect(jsonPath("$.gates", org.hamcrest.Matchers.hasSize(9)))
        .andExpect(jsonPath("$.gates[0].order").value(1))
        .andExpect(jsonPath("$.gates[0].code").value("TRUST_BALANCE_ZERO"));
  }

  // ==========================================================================
  // POST /close happy path (owner + override=true bypasses gates)
  // ==========================================================================

  @Test
  void legalTenant_POST_close_withOverrideAsOwner_returns200_withResponseShape() throws Exception {
    UUID projectId = createProject("Close Happy Matter");

    String body =
        """
        {
          "reason": "CONCLUDED",
          "notes": "All work done; client wants file closed.",
          "generateClosureLetter": false,
          "override": true,
          "overrideJustification": "%s"
        }
        """
            .formatted(VALID_JUSTIFICATION);

    mockMvc
        .perform(
            post("/api/matters/" + projectId + "/closure/close")
                .with(TestJwtFactory.ownerJwt(LEGAL_ORG_ID, "user_closure_ctrl_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.matterId").value(projectId.toString()))
        .andExpect(jsonPath("$.status").value("CLOSED"))
        .andExpect(jsonPath("$.closedAt").isNotEmpty())
        .andExpect(jsonPath("$.closureLogId").isNotEmpty())
        .andExpect(jsonPath("$.retentionEndsAt").isNotEmpty());
  }

  // ==========================================================================
  // POST /close — gates fail, no override → 409 with ProblemDetail.report
  // ==========================================================================

  @Test
  void legalTenant_POST_close_gatesFail_noOverride_returns409_withReportProperty()
      throws Exception {
    UUID projectId = createProject("Gate Fail Matter");

    String body =
        """
        {
          "reason": "CONCLUDED",
          "notes": "trying to close prematurely",
          "generateClosureLetter": false,
          "override": false,
          "overrideJustification": null
        }
        """;

    mockMvc
        .perform(
            post("/api/matters/" + projectId + "/closure/close")
                .with(TestJwtFactory.ownerJwt(LEGAL_ORG_ID, "user_closure_ctrl_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.title").value("Matter closure gates failed"))
        .andExpect(jsonPath("$.report").exists())
        .andExpect(jsonPath("$.report.matterId").value(projectId.toString()))
        .andExpect(jsonPath("$.report.allPassed").value(false))
        .andExpect(jsonPath("$.report.gates", org.hamcrest.Matchers.hasSize(9)));
  }

  // ==========================================================================
  // POST /close with override=true but admin lacks OVERRIDE_MATTER_CLOSURE → 403
  // ==========================================================================

  @Test
  void legalTenant_POST_close_adminWithOverride_returns403_missingOverrideCapability()
      throws Exception {
    UUID projectId = createProject("Admin Override Missing Cap Matter");

    String body =
        """
        {
          "reason": "CONCLUDED",
          "notes": "admin attempting override",
          "generateClosureLetter": false,
          "override": true,
          "overrideJustification": "%s"
        }
        """
            .formatted(VALID_JUSTIFICATION);

    mockMvc
        .perform(
            post("/api/matters/" + projectId + "/closure/close")
                .with(TestJwtFactory.adminJwt(LEGAL_ORG_ID, "user_closure_ctrl_admin"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isForbidden())
        // CR-Minor-3: assert the ProblemDetail body, not just the status.
        .andExpect(jsonPath("$.status").value(403))
        .andExpect(jsonPath("$.title").value("Access denied"))
        .andExpect(jsonPath("$.detail").exists());
  }

  // ==========================================================================
  // H4 — module guard coverage for the remaining endpoints (POST /close,
  //       POST /reopen, GET /log) on a non-legal tenant.
  // ==========================================================================

  @Test
  void nonLegalTenant_POST_close_returns403_fromModuleGuard() throws Exception {
    String body =
        """
        {
          "reason": "CONCLUDED",
          "notes": "attempted close on non-legal tenant",
          "generateClosureLetter": false,
          "override": false,
          "overrideJustification": null
        }
        """;
    mockMvc
        .perform(
            post("/api/matters/" + UUID.randomUUID() + "/closure/close")
                .with(TestJwtFactory.ownerJwt(NON_LEGAL_ORG_ID, "user_closure_nonlegal_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.title").value("Module not enabled"))
        .andExpect(jsonPath("$.moduleId").value("matter_closure"));
  }

  @Test
  void nonLegalTenant_POST_reopen_returns403_fromModuleGuard() throws Exception {
    String body =
        """
        {
          "notes": "attempted reopen on non-legal tenant"
        }
        """;
    mockMvc
        .perform(
            post("/api/matters/" + UUID.randomUUID() + "/closure/reopen")
                .with(TestJwtFactory.ownerJwt(NON_LEGAL_ORG_ID, "user_closure_nonlegal_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.title").value("Module not enabled"))
        .andExpect(jsonPath("$.moduleId").value("matter_closure"));
  }

  @Test
  void nonLegalTenant_GET_log_returns403_fromModuleGuard() throws Exception {
    mockMvc
        .perform(
            get("/api/matters/" + UUID.randomUUID() + "/closure/log")
                .with(TestJwtFactory.ownerJwt(NON_LEGAL_ORG_ID, "user_closure_nonlegal_owner")))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.title").value("Module not enabled"))
        .andExpect(jsonPath("$.moduleId").value("matter_closure"));
  }

  // ==========================================================================
  // H6 — GET /log happy path after a successful close.
  // ==========================================================================

  @Test
  void legalTenant_GET_log_returns200_withClosureEntry_afterClose() throws Exception {
    UUID projectId = createProject("Log Happy Matter");

    String closeBody =
        """
        {
          "reason": "CONCLUDED",
          "notes": "Close for log happy-path test.",
          "generateClosureLetter": false,
          "override": true,
          "overrideJustification": "%s"
        }
        """
            .formatted(VALID_JUSTIFICATION);

    mockMvc
        .perform(
            post("/api/matters/" + projectId + "/closure/close")
                .with(TestJwtFactory.ownerJwt(LEGAL_ORG_ID, "user_closure_ctrl_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(closeBody))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            get("/api/matters/" + projectId + "/closure/log")
                .with(TestJwtFactory.ownerJwt(LEGAL_ORG_ID, "user_closure_ctrl_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(1)))
        .andExpect(jsonPath("$[0].matterId").value(projectId.toString()))
        .andExpect(jsonPath("$[0].reason").value("CONCLUDED"))
        .andExpect(jsonPath("$[0].overrideUsed").value(true))
        .andExpect(jsonPath("$[0].closedAt").isNotEmpty())
        .andExpect(jsonPath("$[0].reopenedAt").value(org.hamcrest.Matchers.nullValue()));
  }

  // ==========================================================================
  // LZKC-014 — GET /log must resolve member names for closedBy / reopenedBy,
  // not leak raw UUIDs to the closure-history UI.
  // ==========================================================================

  @Test
  void legalTenant_GET_log_resolvesClosedByName_andReopenedByName() throws Exception {
    UUID projectId = createProject("Log Name Resolution Matter");

    String closeBody =
        """
        {
          "reason": "CONCLUDED",
          "notes": "Close for name-resolution test.",
          "generateClosureLetter": false,
          "override": true,
          "overrideJustification": "%s"
        }
        """
            .formatted(VALID_JUSTIFICATION);

    mockMvc
        .perform(
            post("/api/matters/" + projectId + "/closure/close")
                .with(TestJwtFactory.ownerJwt(LEGAL_ORG_ID, "user_closure_ctrl_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(closeBody))
        .andExpect(status().isOk());

    // Closed entry: raw id retained for machine consumers, display name resolved alongside.
    mockMvc
        .perform(
            get("/api/matters/" + projectId + "/closure/log")
                .with(TestJwtFactory.ownerJwt(LEGAL_ORG_ID, "user_closure_ctrl_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(1)))
        .andExpect(jsonPath("$[0].closedBy").value(legalOwnerMemberId.toString()))
        .andExpect(jsonPath("$[0].closedByName").value("Closure Ctrl Owner"))
        .andExpect(jsonPath("$[0].reopenedBy").value(org.hamcrest.Matchers.nullValue()))
        .andExpect(jsonPath("$[0].reopenedByName").value(org.hamcrest.Matchers.nullValue()));

    // Reopen the matter and verify reopenedByName is resolved too.
    String reopenBody =
        """
        {
          "notes": "Reopening for name-resolution test."
        }
        """;
    mockMvc
        .perform(
            post("/api/matters/" + projectId + "/closure/reopen")
                .with(TestJwtFactory.ownerJwt(LEGAL_ORG_ID, "user_closure_ctrl_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(reopenBody))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.matterId").value(projectId.toString()))
        .andExpect(jsonPath("$.status").value("ACTIVE"));

    mockMvc
        .perform(
            get("/api/matters/" + projectId + "/closure/log")
                .with(TestJwtFactory.ownerJwt(LEGAL_ORG_ID, "user_closure_ctrl_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(1)))
        .andExpect(jsonPath("$[0].closedByName").value("Closure Ctrl Owner"))
        .andExpect(jsonPath("$[0].reopenedBy").value(legalOwnerMemberId.toString()))
        .andExpect(jsonPath("$[0].reopenedByName").value("Closure Ctrl Owner"));
  }

  // ==========================================================================
  // Reopen after retention elapsed → 409 ProblemDetail carrying matterId
  // ==========================================================================

  @Test
  void legalTenant_POST_reopen_afterRetentionElapsed_returns409_withMatterIdProblemDetail()
      throws Exception {
    // Pin retention years so the expected retentionEndedOn is deterministic regardless of the
    // order other tests in this class mutate the org setting.
    runInLegalTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var settings = orgSettingsService.getOrCreateForCurrentTenant();
                  settings.getDataProtection().setLegalMatterRetentionYears(7);
                  orgSettingsRepository.save(settings);
                }));

    UUID projectId = createProject("Retention Elapsed Reopen Matter");

    String closeBody =
        """
        {
          "reason": "CONCLUDED",
          "notes": "Close for retention-elapsed reopen test.",
          "generateClosureLetter": false,
          "override": true,
          "overrideJustification": "%s"
        }
        """
            .formatted(VALID_JUSTIFICATION);
    mockMvc
        .perform(
            post("/api/matters/" + projectId + "/closure/close")
                .with(TestJwtFactory.ownerJwt(LEGAL_ORG_ID, "user_closure_ctrl_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(closeBody))
        .andExpect(status().isOk());

    // Backdate the persisted retention end date (stamped at close) to a fixed day in the past:
    // the reopen guard reads this frozen per-matter date, so 2007-01-15 must block the reopen.
    jdbcTemplate.update(
        "UPDATE \"%s\".projects SET retention_ends_on = ? WHERE id = ?"
            .formatted(legalTenantSchema),
        java.sql.Date.valueOf("2007-01-15"),
        projectId);

    mockMvc
        .perform(
            post("/api/matters/" + projectId + "/closure/reopen")
                .with(TestJwtFactory.ownerJwt(LEGAL_ORG_ID, "user_closure_ctrl_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "notes": "Attempting reopen after retention elapsed."
                    }
                    """))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.title").value("Retention elapsed"))
        .andExpect(
            jsonPath("$.detail")
                .value(
                    "Matter retention window expired on 2007-01-15. This matter can no longer be"
                        + " reopened."))
        .andExpect(jsonPath("$.matterId").value(projectId.toString()))
        .andExpect(jsonPath("$.retentionEndedOn").value("2007-01-15"));
  }

  // ==========================================================================
  // GAP-OBS-Day60-RetentionShape — matter detail surfaces retentionEndsOn
  // ==========================================================================

  @Test
  void matterDetail_returnsRetentionEndsOn_forClosedMatter_whenRetentionYearsConfigured()
      throws Exception {
    // Arrange: configure a non-default retention period so the persisted end date proves the close
    // used the firm's setting rather than the seeded default.
    runInLegalTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var settings = orgSettingsService.getOrCreateForCurrentTenant();
                  settings.getDataProtection().setLegalMatterRetentionYears(7);
                  orgSettingsRepository.save(settings);
                }));

    UUID projectId = createProject("Retention Detail Closed Matter");

    // Close the matter via the existing controller path (override=true bypasses gates).
    String closeBody =
        """
        {
          "reason": "CONCLUDED",
          "notes": "Close for retention detail test.",
          "generateClosureLetter": false,
          "override": true,
          "overrideJustification": "%s"
        }
        """
            .formatted(VALID_JUSTIFICATION);
    mockMvc
        .perform(
            post("/api/matters/" + projectId + "/closure/close")
                .with(TestJwtFactory.ownerJwt(LEGAL_ORG_ID, "user_closure_ctrl_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(closeBody))
        .andExpect(status().isOk());

    // Act + Assert: matter detail returns the persisted end date = UTC close date + 7 years.
    mockMvc
        .perform(
            get("/api/matters/" + projectId)
                .with(TestJwtFactory.ownerJwt(LEGAL_ORG_ID, "user_closure_ctrl_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(projectId.toString()))
        .andExpect(jsonPath("$.status").value("CLOSED"))
        .andExpect(jsonPath("$.retentionClockStartedAt").isNotEmpty())
        .andExpect(
            jsonPath("$.retentionEndsOn").value(closedOnUtc(projectId).plusYears(7).toString()));
  }

  @Test
  void matterDetail_returnsNullRetentionEndsOn_forActiveMatter() throws Exception {
    // Retention years configured, but the matter is ACTIVE — retentionEndsOn must be null.
    runInLegalTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var settings = orgSettingsService.getOrCreateForCurrentTenant();
                  settings.getDataProtection().setLegalMatterRetentionYears(5);
                  orgSettingsRepository.save(settings);
                }));

    UUID projectId = createProject("Retention Detail Active Matter");

    mockMvc
        .perform(
            get("/api/matters/" + projectId)
                .with(TestJwtFactory.ownerJwt(LEGAL_ORG_ID, "user_closure_ctrl_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ACTIVE"))
        .andExpect(jsonPath("$.retentionEndsOn").value(org.hamcrest.Matchers.nullValue()));
  }

  @Test
  void matterDetail_closedWithUnsetYears_persistsEffectiveDefault() throws Exception {
    // A firm whose retention years are NULL (a legacy row the migration has not backfilled) still
    // gets a scheduled end date: the close stamps the effective default of 5 years.
    setLegalRetentionYears(null);
    try {
      UUID projectId = createProject("Retention Detail Unconfigured Matter");

      String closeBody =
          """
          {
            "reason": "CONCLUDED",
            "notes": "Close without configured retention years.",
            "generateClosureLetter": false,
            "override": true,
            "overrideJustification": "%s"
          }
          """
              .formatted(VALID_JUSTIFICATION);
      mockMvc
          .perform(
              post("/api/matters/" + projectId + "/closure/close")
                  .with(TestJwtFactory.ownerJwt(LEGAL_ORG_ID, "user_closure_ctrl_owner"))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(closeBody))
          .andExpect(status().isOk());

      String expected =
          closedOnUtc(projectId)
              .plusYears(DataProtectionSettings.DEFAULT_LEGAL_MATTER_RETENTION_YEARS)
              .toString();
      mockMvc
          .perform(
              get("/api/matters/" + projectId)
                  .with(TestJwtFactory.ownerJwt(LEGAL_ORG_ID, "user_closure_ctrl_owner")))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.status").value("CLOSED"))
          .andExpect(jsonPath("$.retentionClockStartedAt").isNotEmpty())
          .andExpect(jsonPath("$.retentionEndsOn").value(expected));
    } finally {
      // Restore the seeded value so sibling methods are not order-dependent.
      setLegalRetentionYears(DataProtectionSettings.DEFAULT_LEGAL_MATTER_RETENTION_YEARS);
    }
  }

  // ==========================================================================
  // Helpers
  // ==========================================================================

  private UUID createProject(String name) {
    final UUID[] id = new UUID[1];
    runInLegalTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var project = new Project(name, "controller test matter", legalOwnerMemberId);
                  project.setCustomerId(customerId);
                  id[0] = projectRepository.saveAndFlush(project).getId();
                }));
    return id[0];
  }

  private void setLegalRetentionYears(Integer years) {
    runInLegalTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var settings = orgSettingsService.getOrCreateForCurrentTenant();
                  settings.getDataProtection().setLegalMatterRetentionYears(years);
                  orgSettingsRepository.save(settings);
                }));
  }

  /** The matter's persisted close instant as a UTC calendar date. */
  private LocalDate closedOnUtc(UUID projectId) {
    final LocalDate[] day = new LocalDate[1];
    runInLegalTenant(
        () ->
            day[0] =
                projectRepository
                    .findById(projectId)
                    .orElseThrow()
                    .getClosedAt()
                    .atZone(ZoneOffset.UTC)
                    .toLocalDate());
    return day[0];
  }

  private void runInLegalTenant(Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, legalTenantSchema)
        .where(RequestScopes.ORG_ID, LEGAL_ORG_ID)
        .where(RequestScopes.MEMBER_ID, legalOwnerMemberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(action);
  }
}
