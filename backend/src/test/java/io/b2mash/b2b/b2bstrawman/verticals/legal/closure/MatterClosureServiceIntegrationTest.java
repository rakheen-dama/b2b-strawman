package io.b2mash.b2b.b2bstrawman.verticals.legal.closure;

import static io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory.createActiveCustomer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.document.Document;
import io.b2mash.b2b.b2bstrawman.document.DocumentRepository;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.project.ProjectStatus;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.retention.RetentionPolicyRepository;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsService;
import io.b2mash.b2b.b2bstrawman.template.GeneratedDocumentRepository;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import io.b2mash.b2b.b2bstrawman.verticals.legal.closure.dto.ClosureReason;
import io.b2mash.b2b.b2bstrawman.verticals.legal.closure.dto.ClosureRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.closure.dto.ReopenRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.closure.event.MatterClosedEvent;
import io.b2mash.b2b.b2bstrawman.verticals.legal.closure.event.MatterReopenedEvent;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Integration tests for {@link MatterClosureService} (Epic 489B, tasks 489.17/489.18). Covers
 * evaluate, close (gate-fail + override branches), reopen (happy + retention-elapsed), and event
 * publication. The happy-path close uses {@code override=true} so we don't have to seed a full
 * financial state (invoice + settled disbursements) just to exercise the orchestrator.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@RecordApplicationEvents
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MatterClosureServiceIntegrationTest {

  private static final String ORG_ID = "org_matter_closure_svc";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private OrgSettingsRepository orgSettingsRepository;
  @Autowired private OrgSettingsService orgSettingsService;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private MatterClosureService matterClosureService;
  @Autowired private MatterClosureLogRepository matterClosureLogRepository;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private ProjectRepository projectRepository;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private ApplicationEvents events;
  @Autowired private GeneratedDocumentRepository generatedDocumentRepository;
  @Autowired private DocumentRepository documentRepository;
  @Autowired private RetentionPolicyRepository retentionPolicyRepository;

  private String tenantSchema;
  private UUID memberId;
  private UUID customerId;

  private static final String VALID_JUSTIFICATION =
      "Client withdrew mid-matter; all trust funds disbursed and no court dates remain outstanding.";
  private static final String VALID_REOPEN_NOTES = "Client returned with new instructions.";

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Matter Closure Svc Firm", "legal-za");

    memberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                ORG_ID,
                "user_closure_svc_owner",
                "closure_svc_owner@test.com",
                "Closure Svc Owner",
                "owner"));

    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

    runInTenantAsOwner(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var settings = orgSettingsService.getOrCreateForCurrentTenant();
                  settings.setEnabledModules(List.of("matter_closure", "disbursements"));
                  orgSettingsRepository.save(settings);

                  var customer =
                      createActiveCustomer("Closure Svc Client", "closure_svc@test.com", memberId);
                  customer = customerRepository.saveAndFlush(customer);
                  customerId = customer.getId();
                }));
  }

  // ==========================================================================
  // evaluate
  // ==========================================================================

  @Test
  void evaluate_freshProject_returnsReportWith9Gates_allPassedFalse() {
    UUID projectId = createProject("Evaluate Report Matter");

    runInTenantAsOwner(
        () -> {
          var report = matterClosureService.evaluate(projectId);
          assertThat(report.projectId()).isEqualTo(projectId);
          assertThat(report.gates()).hasSize(9);
          // FINAL_BILL_ISSUED should fail on a fresh project with no invoices — so allPassed=false
          assertThat(report.allPassed()).isFalse();
          // Gates are returned ordered by `order`
          assertThat(report.gates().get(0).order()).isEqualTo(1);
          assertThat(report.gates().get(0).code()).isEqualTo("TRUST_BALANCE_ZERO");
          // Gate codes are all populated
          assertThat(report.gates()).allSatisfy(g -> assertThat(g.code()).isNotBlank());
        });
  }

  // ==========================================================================
  // close — gate failure path
  // ==========================================================================

  @Test
  void close_withFailingGates_andOverrideFalse_throwsClosureGateFailedException_withFullReport() {
    UUID projectId = createProject("Gate Fail Matter");

    runInTenantAsOwner(
        () ->
            assertThatThrownBy(
                    () ->
                        matterClosureService.close(
                            projectId,
                            new ClosureRequest(
                                ClosureReason.CONCLUDED, "note", false, false, false, null),
                            memberId))
                .isInstanceOfSatisfying(
                    ClosureGateFailedException.class,
                    ex -> {
                      assertThat(ex.getReport().projectId()).isEqualTo(projectId);
                      assertThat(ex.getReport().allPassed()).isFalse();
                      assertThat(ex.getReport().gates()).hasSize(9);
                      // At least one gate in the report has passed=false (FINAL_BILL_ISSUED)
                      assertThat(ex.getReport().gates())
                          .anyMatch(g -> !g.passed() && "FINAL_BILL_ISSUED".equals(g.code()));
                    }));
  }

  // ==========================================================================
  // close — override branches
  // ==========================================================================

  @Test
  void close_withOverrideTrue_butNoOverrideCapability_throwsAccessDeniedException() {
    UUID projectId = createProject("Override Missing Cap Matter");

    // Run in tenant but bind capabilities WITHOUT OVERRIDE_MATTER_CLOSURE.
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberId)
        .where(RequestScopes.ORG_ROLE, "admin")
        .where(RequestScopes.CAPABILITIES, Set.of("CLOSE_MATTER", "VIEW_LEGAL"))
        .run(
            () ->
                assertThatThrownBy(
                        () ->
                            matterClosureService.close(
                                projectId,
                                new ClosureRequest(
                                    ClosureReason.CONCLUDED,
                                    null,
                                    false,
                                    false,
                                    true,
                                    VALID_JUSTIFICATION),
                                memberId))
                    .isInstanceOf(AccessDeniedException.class));
  }

  @Test
  void close_withOverride_shortJustification_throwsInvalidStateException() {
    UUID projectId = createProject("Short Justification Matter");

    runInTenantAsOwner(
        () ->
            assertThatThrownBy(
                    () ->
                        matterClosureService.close(
                            projectId,
                            new ClosureRequest(
                                ClosureReason.CONCLUDED, null, false, false, true, "too short"),
                            memberId))
                .isInstanceOf(InvalidStateException.class));
  }

  @Test
  void
      close_withOverrideAndCapability_transitionsToClosed_persistsLog_stampsRetention_emitsEvent() {
    UUID projectId = createProject("Override Success Matter");

    runInTenantAsOwner(
        () -> {
          var response =
              matterClosureService.close(
                  projectId,
                  new ClosureRequest(
                      ClosureReason.CLIENT_TERMINATED,
                      "closing due to client termination",
                      /* generateClosureLetter */ false,
                      /* generateStatementOfAccount */ false,
                      /* override */ true,
                      VALID_JUSTIFICATION),
                  memberId);

          assertThat(response.projectId()).isEqualTo(projectId);
          assertThat(response.status()).isEqualTo(ProjectStatus.CLOSED.name());
          assertThat(response.closedAt()).isNotNull();
          assertThat(response.closureLogId()).isNotNull();
          assertThat(response.retentionEndsAt()).isNotNull();
        });

    // Assert project persisted as CLOSED + log row is present
    runInTenantAsOwner(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var project = projectRepository.findById(projectId).orElseThrow();
                  assertThat(project.getStatus()).isEqualTo(ProjectStatus.CLOSED);
                  assertThat(project.getClosedAt()).isNotNull();
                  assertThat(project.getRetentionClockStartedAt()).isNotNull();

                  var log =
                      matterClosureLogRepository
                          .findTopByProjectIdOrderByClosedAtDesc(projectId)
                          .orElseThrow();
                  assertThat(log.isOverrideUsed()).isTrue();
                  assertThat(log.getOverrideJustification()).isEqualTo(VALID_JUSTIFICATION);
                  assertThat(log.getReason()).isEqualTo("CLIENT_TERMINATED");
                  assertThat(log.getClosedBy()).isEqualTo(memberId);
                  assertThat(log.getGateReport()).isNotNull();
                }));

    long closedEvents =
        events.stream(MatterClosedEvent.class).filter(e -> e.projectId().equals(projectId)).count();
    assertThat(closedEvents).isEqualTo(1);
  }

  // ==========================================================================
  // reopen
  // ==========================================================================

  @Test
  void reopen_happyPath_transitionsToActive_stampsLog_emitsEvent() {
    UUID projectId = createProject("Reopen Happy Matter");

    runInTenantAsOwner(
        () ->
            matterClosureService.close(
                projectId,
                new ClosureRequest(
                    ClosureReason.CONCLUDED, "done", false, false, true, VALID_JUSTIFICATION),
                memberId));

    runInTenantAsOwner(
        () -> {
          var response =
              matterClosureService.reopen(
                  projectId, new ReopenRequest(VALID_REOPEN_NOTES), memberId);
          assertThat(response.projectId()).isEqualTo(projectId);
          assertThat(response.status()).isEqualTo(ProjectStatus.ACTIVE.name());
          assertThat(response.reopenedAt()).isNotNull();
          assertThat(response.closureLogId()).isNotNull();
        });

    runInTenantAsOwner(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var project = projectRepository.findById(projectId).orElseThrow();
                  assertThat(project.getStatus()).isEqualTo(ProjectStatus.ACTIVE);
                  assertThat(project.getClosedAt()).isNull();
                  // Retention clock must be PRESERVED across reopen
                  assertThat(project.getRetentionClockStartedAt()).isNotNull();

                  var log =
                      matterClosureLogRepository
                          .findTopByProjectIdOrderByClosedAtDesc(projectId)
                          .orElseThrow();
                  assertThat(log.getReopenedAt()).isNotNull();
                  assertThat(log.getReopenedBy()).isEqualTo(memberId);
                  assertThat(log.getReopenNotes()).isEqualTo(VALID_REOPEN_NOTES);
                }));

    long reopenedEvents =
        events.stream(MatterReopenedEvent.class)
            .filter(e -> e.projectId().equals(projectId))
            .count();
    assertThat(reopenedEvents).isEqualTo(1);
  }

  @Test
  void reopen_shortNotes_throwsInvalidStateException() {
    UUID projectId = createProject("Short Reopen Notes Matter");

    runInTenantAsOwner(
        () ->
            matterClosureService.close(
                projectId,
                new ClosureRequest(
                    ClosureReason.CONCLUDED, null, false, false, true, VALID_JUSTIFICATION),
                memberId));

    runInTenantAsOwner(
        () ->
            assertThatThrownBy(
                    () ->
                        matterClosureService.reopen(
                            projectId, new ReopenRequest("short"), memberId))
                .isInstanceOf(InvalidStateException.class));
  }

  @Test
  void reopen_afterRetentionElapsed_throwsRetentionElapsedException() {
    UUID projectId = createProject("Retention Elapsed Matter");

    runInTenantAsOwner(
        () ->
            matterClosureService.close(
                projectId,
                new ClosureRequest(
                    ClosureReason.CONCLUDED, null, false, false, true, VALID_JUSTIFICATION),
                memberId));

    // Backdate the retention anchor far past the retention window (default 7 years at OrgSettings).
    // Setting retentionClockStartedAt to 100 years ago guarantees retentionEndsOn < today.
    Instant longAgo = Instant.now().minus(365L * 100, ChronoUnit.DAYS);
    jdbcTemplate.update(
        "UPDATE \"%s\".projects SET retention_clock_started_at = ? WHERE id = ?"
            .formatted(tenantSchema),
        java.sql.Timestamp.from(longAgo),
        projectId);

    runInTenantAsOwner(
        () ->
            assertThatThrownBy(
                    () ->
                        matterClosureService.reopen(
                            projectId, new ReopenRequest(VALID_REOPEN_NOTES), memberId))
                .isInstanceOf(RetentionElapsedException.class));
  }

  // ==========================================================================
  // GAP-L-74 part B / GAP-L-74-followup — closure letter linked Document is
  // flipped to PORTAL (system-auto-shared) so it appears on the portal Documents
  // tab. PORTAL is the "system" twin of SHARED — both render to portal contacts,
  // but audit + analytics can tell manual vs system shares apart.
  // ==========================================================================

  @Test
  void close_withGenerateClosureLetterTrue_flipsLinkedDocumentVisibility_toPortal() {
    UUID projectId = createProject("L-74b Closure Letter Visibility Matter");

    var response =
        runInTenantReturning(
            () ->
                matterClosureService.close(
                    projectId,
                    new ClosureRequest(
                        ClosureReason.CONCLUDED,
                        "L-74b regression",
                        /* generateClosureLetter */ true,
                        /* generateStatementOfAccount */ false,
                        /* override */ true,
                        VALID_JUSTIFICATION),
                    memberId));

    assertThat(response.closureLetterDocumentId())
        .as("closure letter GeneratedDocument id must be set when generateClosureLetter=true")
        .isNotNull();

    runInTenantAsOwner(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var generated =
                      generatedDocumentRepository
                          .findById(response.closureLetterDocumentId())
                          .orElseThrow();
                  assertThat(generated.getDocumentId())
                      .as("closure letter must have a paired Document via createLinkedDocument")
                      .isNotNull();

                  var linkedDoc =
                      documentRepository.findById(generated.getDocumentId()).orElseThrow();
                  assertThat(linkedDoc.getVisibility())
                      .as(
                          "GAP-L-74 part B / GAP-L-74-followup: closure-letter linked Document"
                              + " must be flipped to PORTAL (system-auto-shared) so it appears on"
                              + " the portal Documents tab — distinct from manual SHARED")
                      .isEqualTo(Document.Visibility.PORTAL);
                  assertThat(linkedDoc.getProjectId()).isEqualTo(projectId);
                  assertThat(linkedDoc.getFileName()).startsWith("matter-closure-letter");
                }));
  }

  // ==========================================================================
  // GAP-L-93 — Statement of Account auto-attach on close
  // ==========================================================================

  @Test
  void close_withGenerateStatementOfAccountTrue_returnsStatementOfAccountDocumentId() {
    UUID projectId = createProject("L-93 SoA Auto-Attach Matter");

    var response =
        runInTenantReturning(
            () ->
                matterClosureService.close(
                    projectId,
                    new ClosureRequest(
                        ClosureReason.CONCLUDED,
                        "L-93 SoA on close",
                        /* generateClosureLetter */ false,
                        /* generateStatementOfAccount */ true,
                        /* override */ true,
                        VALID_JUSTIFICATION),
                    memberId));

    assertThat(response.statementOfAccountDocumentId())
        .as(
            "GAP-L-93: when generateStatementOfAccount=true, the SoA must be auto-attached and"
                + " the response must carry the GeneratedDocument id")
        .isNotNull();
    assertThat(response.closureLetterDocumentId())
        .as("Closure letter was suppressed in this test — expected null")
        .isNull();
  }

  @Test
  void close_withGenerateStatementOfAccountFalse_returnsNullStatementOfAccountDocumentId() {
    UUID projectId = createProject("L-93 SoA Suppressed Matter");

    var response =
        runInTenantReturning(
            () ->
                matterClosureService.close(
                    projectId,
                    new ClosureRequest(
                        ClosureReason.CONCLUDED,
                        null,
                        /* generateClosureLetter */ false,
                        /* generateStatementOfAccount */ false,
                        /* override */ true,
                        VALID_JUSTIFICATION),
                    memberId));

    assertThat(response.statementOfAccountDocumentId())
        .as("Suppressed SoA flag → response carries null statementOfAccountDocumentId")
        .isNull();
  }

  @Test
  void close_withGenerateStatementOfAccountNull_treatsAsFalse_backwardCompat() {
    // GAP-L-93 backward-compat: legacy clients (older frontend) won't send the new flag at all
    // — Boolean field on the record allows null, the helper treats null as false.
    UUID projectId = createProject("L-93 SoA Null-Flag Backward-Compat Matter");

    var response =
        runInTenantReturning(
            () ->
                matterClosureService.close(
                    projectId,
                    new ClosureRequest(
                        ClosureReason.CONCLUDED,
                        null,
                        /* generateClosureLetter */ false,
                        /* generateStatementOfAccount */ null,
                        /* override */ true,
                        VALID_JUSTIFICATION),
                    memberId));

    assertThat(response.statementOfAccountDocumentId())
        .as("null SoA flag must be treated as false (legacy-client compatibility)")
        .isNull();
  }

  // ==========================================================================
  // GAP-L-96 / ADR-249 — retention_policies MATTER row seed on close
  // ==========================================================================

  @Test
  void close_seedsMatterRetentionPolicyIfMissing() {
    UUID projectId = createProject("L-96 Retention Policy Seed Matter");

    runInTenantAsOwner(
        () ->
            matterClosureService.close(
                projectId,
                new ClosureRequest(
                    ClosureReason.CONCLUDED, null, false, false, true, VALID_JUSTIFICATION),
                memberId));

    runInTenantAsOwner(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var policy =
                      retentionPolicyRepository
                          .findByRecordTypeAndTriggerEvent("MATTER", "MATTER_CLOSED")
                          .orElseThrow(
                              () ->
                                  new AssertionError(
                                      "GAP-L-96: expected a MATTER retention policy row after"
                                          + " first matter closure (ADR-249)"));
                  // Default OrgSettings.legalMatterRetentionYears = 5 (per OrgSettings.java:36)
                  assertThat(policy.getRetentionDays()).isEqualTo(5 * 365);
                  assertThat(policy.getAction()).isEqualTo("ARCHIVE");
                  assertThat(policy.isActive()).isTrue();
                }));
  }

  @Test
  void close_isIdempotentOnSecondClosure_doesNotThrow_keepsExactlyOneMatterPolicyRow() {
    UUID projectIdA = createProject("L-96 Idempotent Close A");
    UUID projectIdB = createProject("L-96 Idempotent Close B");

    runInTenantAsOwner(
        () ->
            matterClosureService.close(
                projectIdA,
                new ClosureRequest(
                    ClosureReason.CONCLUDED, null, false, false, true, VALID_JUSTIFICATION),
                memberId));

    // Second close on the same tenant — must NOT throw a ResourceConflictException, must NOT
    // mark the outer transaction rollback-only, and must leave exactly ONE MATTER policy row.
    runInTenantAsOwner(
        () ->
            matterClosureService.close(
                projectIdB,
                new ClosureRequest(
                    ClosureReason.CONCLUDED, null, false, false, true, VALID_JUSTIFICATION),
                memberId));

    runInTenantAsOwner(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  long matterPolicies =
                      retentionPolicyRepository.findByActive(true).stream()
                          .filter(p -> "MATTER".equals(p.getRecordType()))
                          .count();
                  assertThat(matterPolicies)
                      .as("Idempotent — second close must not insert a duplicate MATTER policy")
                      .isEqualTo(1);
                }));
  }

  // ==========================================================================
  // Helpers
  // ==========================================================================

  /**
   * Creates a persisted Project in the tenant schema and returns its id. Projects start in ACTIVE
   * status which is required for the close path.
   */
  private UUID createProject(String name) {
    final UUID[] id = new UUID[1];
    runInTenantAsOwner(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var project = new Project(name, "test matter for closure", memberId);
                  project.setCustomerId(customerId);
                  id[0] = projectRepository.saveAndFlush(project).getId();
                }));
    return id[0];
  }

  private void runInTenantAsOwner(Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .where(
            RequestScopes.CAPABILITIES,
            Set.copyOf(io.b2mash.b2b.b2bstrawman.orgrole.Capability.ALL_NAMES))
        .run(action);
  }

  private <T> T runInTenantReturning(java.util.function.Supplier<T> action) {
    Object[] holder = new Object[1];
    runInTenantAsOwner(() -> holder[0] = action.get());
    @SuppressWarnings("unchecked")
    T value = (T) holder[0];
    return value;
  }
}
