package io.b2mash.b2b.b2bstrawman.compliance;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.checklist.ChecklistInstanceService;
import io.b2mash.b2b.b2bstrawman.exception.ResourceConflictException;
import io.b2mash.b2b.b2bstrawman.integration.ai.AiCompletionResponse;
import io.b2mash.b2b.b2bstrawman.integration.ai.execution.AiExecution;
import io.b2mash.b2b.b2bstrawman.integration.ai.execution.AiExecutionRepository;
import io.b2mash.b2b.b2bstrawman.integration.ai.skill.complianceaudit.ComplianceAuditOutput;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import io.b2mash.b2b.b2bstrawman.verticals.legal.conflictcheck.ConflictCheckService;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * AIVERIFY-004: enforce the "exactly one PUBLISHED compliance audit report" invariant under
 * concurrency. Before the V128 partial unique index, two concurrent {@code publishReport} calls for
 * distinct executions could each read the PUBLISHED set, archive the shared previous report, and
 * insert a new PUBLISHED row — leaving two PUBLISHED reports. The index makes the second insert
 * fail with a unique violation so exactly one wins; the loser surfaces a {@link
 * ResourceConflictException} (409) the reviewer can retry.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ComplianceAuditReportConcurrencyTest {

  // Each test method gets its own tenant schema so leftover PUBLISHED rows from one method
  // cannot pollute the other (both methods care about the exact PUBLISHED count).
  private static final String ORG_INSERTS = "org_compliance_rpt_conc_inserts";
  private static final String ORG_SERVICE = "org_compliance_rpt_conc_service";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private ComplianceAuditReportService reportService;
  @Autowired private ComplianceAuditReportRepository reportRepository;
  @Autowired private AiExecutionRepository executionRepository;
  @Autowired private TransactionTemplate transactionTemplate;

  @MockitoBean private ChecklistInstanceService checklistInstanceService;
  @MockitoBean private ConflictCheckService conflictCheckService;

  private String insertsSchema;
  private String serviceSchema;
  private UUID insertsOwnerId;
  private UUID serviceOwnerId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_INSERTS, ORG_INSERTS, null);
    insertsOwnerId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                ORG_INSERTS,
                "user_comp_conc_inserts",
                "user_comp_conc_inserts@test.com",
                "Owner",
                "owner"));
    insertsSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_INSERTS).orElseThrow().getSchemaName();

    provisioningService.provisionTenant(ORG_SERVICE, ORG_SERVICE, null);
    serviceOwnerId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                ORG_SERVICE,
                "user_comp_conc_service",
                "user_comp_conc_service@test.com",
                "Owner",
                "owner"));
    serviceSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_SERVICE).orElseThrow().getSchemaName();
  }

  /**
   * Deterministic reproduction: two transactions each insert a PUBLISHED report directly, holding
   * at a barrier so both inserts are issued before either commits. The V128 partial unique index
   * lets exactly one commit; the other fails with a constraint violation. Without the index, both
   * would commit and two PUBLISHED rows would remain (the bug).
   */
  @Test
  void twoConcurrentPublishedInserts_onlyOneSurvives() throws Exception {
    var executionA = saveExecution(insertsSchema, insertsOwnerId);
    var executionB = saveExecution(insertsSchema, insertsOwnerId);

    var barrier = new CyclicBarrier(2);
    var errors = new ConcurrentLinkedQueue<Throwable>();
    var executor = Executors.newFixedThreadPool(2);
    try {
      Future<?> f1 = executor.submit(() -> insertPublishedAtBarrier(executionA, barrier, errors));
      Future<?> f2 = executor.submit(() -> insertPublishedAtBarrier(executionB, barrier, errors));
      f1.get(30, TimeUnit.SECONDS);
      f2.get(30, TimeUnit.SECONDS);
    } finally {
      executor.shutdownNow();
    }

    // Exactly one insert should have hit the partial unique index; the other commits.
    long constraintViolations = errors.stream().filter(this::isUniqueViolation).count();
    assertThat(errors)
        .as("the only acceptable failure is the index rejecting the duplicate PUBLISHED insert")
        .allSatisfy(t -> assertThat(t).isInstanceOf(DataIntegrityViolationException.class));
    assertThat(constraintViolations)
        .as("exactly one of the two concurrent PUBLISHED inserts must be rejected by the index")
        .isEqualTo(1);

    long published = countPublished(insertsSchema, insertsOwnerId);
    assertThat(published)
        .as("the singleton invariant must hold: exactly one PUBLISHED report remains")
        .isEqualTo(1);
  }

  /**
   * Real-service concurrency: two threads approve distinct compliance gates simultaneously by
   * calling {@link ComplianceAuditReportService#publishReport}. Exactly one ends PUBLISHED and the
   * loser (when the race interleaves) surfaces a clean {@link ResourceConflictException}, never a
   * duplicate or a leaked 500.
   */
  @Test
  void twoConcurrentPublishReportCalls_atMostOnePublished() throws Exception {
    var executionA = saveExecution(serviceSchema, serviceOwnerId);
    var executionB = saveExecution(serviceSchema, serviceOwnerId);

    var barrier = new CyclicBarrier(2);
    var failures = new ConcurrentLinkedQueue<Throwable>();
    var executor = Executors.newFixedThreadPool(2);
    try {
      Future<?> f1 = executor.submit(() -> publishAtBarrier(executionA, barrier, failures));
      Future<?> f2 = executor.submit(() -> publishAtBarrier(executionB, barrier, failures));
      f1.get(30, TimeUnit.SECONDS);
      f2.get(30, TimeUnit.SECONDS);
    } finally {
      executor.shutdownNow();
    }

    // Any failure must be a clean conflict (409), not a leaked 500 / unexpected type, and at most
    // one of the two concurrent publishes may fail (the loser of the race).
    assertThat(failures)
        .as("publishReport may only fail with a ResourceConflictException under concurrency")
        .allSatisfy(t -> assertThat(t).isInstanceOf(ResourceConflictException.class));
    assertThat(failures.size())
        .as("at most one of the two concurrent publishes may lose the race")
        .isLessThanOrEqualTo(1);

    long published = countPublished(serviceSchema, serviceOwnerId);
    assertThat(published)
        .as("after two concurrent publishes, the singleton invariant must hold")
        .isEqualTo(1);
  }

  // --- Workers ---

  private void insertPublishedAtBarrier(
      AiExecution execution, CyclicBarrier barrier, ConcurrentLinkedQueue<Throwable> errors) {
    inScope(
        insertsSchema,
        ORG_INSERTS,
        insertsOwnerId,
        () -> {
          try {
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var report =
                      new ComplianceAuditReport(
                          execution, "B", "Concurrent insert", Map.of(), insertsOwnerId);
                  report.publish(insertsOwnerId);
                  // Sync both transactions here — both are open and about to write the PUBLISHED
                  // row — so they genuinely contend for the single-PUBLISHED slot. saveAndFlush
                  // forces the V128 index check now: the loser blocks on the index entry until the
                  // winner commits, then fails with a constraint violation.
                  awaitBarrier(barrier);
                  reportRepository.saveAndFlush(report);
                });
          } catch (DataIntegrityViolationException uniqueViolation) {
            errors.add(uniqueViolation);
          } catch (Exception other) {
            errors.add(other);
          }
        });
  }

  private void publishAtBarrier(
      AiExecution execution, CyclicBarrier barrier, ConcurrentLinkedQueue<Throwable> failures) {
    inScope(
        serviceSchema,
        ORG_SERVICE,
        serviceOwnerId,
        () -> {
          try {
            transactionTemplate.executeWithoutResult(
                tx -> {
                  // Open the transaction first, then sync so both publishReport transactions
                  // interleave at the archive/insert window the bug lives in.
                  awaitBarrier(barrier);
                  reportService.publishReport(
                      createMockOutput(), execution.getId(), serviceOwnerId);
                });
          } catch (RuntimeException businessFailure) {
            failures.add(businessFailure);
          } catch (Exception other) {
            failures.add(other);
          }
        });
  }

  // --- Helpers ---

  private static void awaitBarrier(CyclicBarrier barrier) {
    try {
      barrier.await(20, TimeUnit.SECONDS);
    } catch (Exception e) {
      throw new IllegalStateException("barrier sync failed", e);
    }
  }

  private boolean isUniqueViolation(Throwable t) {
    return t instanceof DataIntegrityViolationException;
  }

  private void inScope(String schema, String orgId, UUID memberId, Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, schema)
        .where(RequestScopes.ORG_ID, orgId)
        .where(RequestScopes.MEMBER_ID, memberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .where(RequestScopes.CAPABILITIES, Set.of("AI_EXECUTE", "AI_REVIEW", "AI_MANAGE"))
        .run(action);
  }

  private long countPublished(String schema, UUID memberId) {
    return ScopedValue.where(RequestScopes.TENANT_ID, schema)
        .where(RequestScopes.MEMBER_ID, memberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .where(RequestScopes.CAPABILITIES, Set.of("AI_EXECUTE", "AI_REVIEW", "AI_MANAGE"))
        .call(
            () ->
                reportRepository
                    .findByStatusOrderByCreatedAtDesc(
                        ReportStatus.PUBLISHED.name(), PageRequest.of(0, 100))
                    .getTotalElements());
  }

  private AiExecution saveExecution(String schema, UUID memberId) {
    return ScopedValue.where(RequestScopes.TENANT_ID, schema)
        .where(RequestScopes.MEMBER_ID, memberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .where(RequestScopes.CAPABILITIES, Set.of("AI_EXECUTE", "AI_REVIEW", "AI_MANAGE"))
        .call(
            () -> {
              var execution =
                  new AiExecution(
                      "compliance-audit",
                      "FIRM",
                      UUID.randomUUID(),
                      memberId,
                      "claude-sonnet-4-6",
                      1);
              execution.markCompleted(
                  new AiCompletionResponse(
                      "output", "claude-sonnet-4-6", 2000, 800, 1500, 0, "end_turn", 5000L),
                  4250L);
              return executionRepository.save(execution);
            });
  }

  private ComplianceAuditOutput createMockOutput() {
    var entityRef =
        new ComplianceAuditOutput.EntityReference("customer", UUID.randomUUID(), "Test Customer");
    var finding1 =
        new ComplianceAuditOutput.AuditFinding(
            "F-001",
            "CRITICAL",
            "FICA_CDD",
            "Missing CDD documents",
            "Customer does not have verified identity documents on file",
            "FICA Section 21",
            "Upload and verify identity documents",
            List.of(entityRef));
    var categoryScore = new ComplianceAuditOutput.CategoryScore("C", 5, 3, 1);
    var recommendation =
        new ComplianceAuditOutput.Recommendation(
            "HIGH", "Conduct CDD review for all flagged customers", "2 weeks");
    return new ComplianceAuditOutput(
        "2026-05-24",
        "B",
        "Overall compliance posture is adequate with critical gaps in FICA CDD",
        Map.of("FICA_CDD", categoryScore),
        List.of(finding1),
        List.of(recommendation));
  }
}
