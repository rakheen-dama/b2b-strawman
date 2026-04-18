package io.b2mash.b2b.b2bstrawman.verticals.legal.closure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Repository + CHECK-constraint integration tests for {@link MatterClosureLog} (Phase 67, Epic
 * 489A). Verifies V101 constraints and repository queries.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MatterClosureLogRepositoryTest {

  private static final String ORG_ID = "org_matter_closure_log_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private MatterClosureLogRepository repository;
  @Autowired private JdbcTemplate jdbcTemplate;

  private String tenantSchema;
  private UUID memberId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Closure Log Test", null);

    memberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                ORG_ID,
                "user_closure_owner",
                "closure_owner@test.com",
                "Closure Owner",
                "owner"));

    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
  }

  // ========================================================================
  // CHECK constraint tests
  // ========================================================================

  @Test
  void overrideUsedWithShortJustification_failsCheck() {
    runInTenant(
        () ->
            assertThatThrownBy(
                    () ->
                        transactionTemplate.executeWithoutResult(
                            tx -> {
                              var log =
                                  new MatterClosureLog(
                                      UUID.randomUUID(),
                                      memberId,
                                      Instant.now(),
                                      "CONCLUDED",
                                      null,
                                      Map.of("gate1", Map.of("passed", true)),
                                      true,
                                      "too short");
                              repository.saveAndFlush(log);
                            }))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_matter_closure_log_override_justification"));
  }

  @Test
  void overrideUsedWithValidJustification_succeeds() {
    final UUID projectId = UUID.randomUUID();
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var log =
                      new MatterClosureLog(
                          projectId,
                          memberId,
                          Instant.now(),
                          "CLIENT_TERMINATED",
                          "urgent closure",
                          Map.of("gate1", Map.of("passed", false)),
                          true,
                          "Override: client withdrew mid-matter, no funds at risk per senior review");
                  var saved = repository.saveAndFlush(log);
                  assertThat(saved.getId()).isNotNull();
                  assertThat(saved.getCreatedAt()).isNotNull();
                  assertThat(saved.isOverrideUsed()).isTrue();
                }));
  }

  @Test
  void invalidReason_failsReasonCheck() {
    runInTenant(
        () ->
            assertThatThrownBy(
                    () ->
                        transactionTemplate.executeWithoutResult(
                            tx -> {
                              var log =
                                  new MatterClosureLog(
                                      UUID.randomUUID(),
                                      memberId,
                                      Instant.now(),
                                      "BAD_REASON",
                                      null,
                                      Map.of(),
                                      false,
                                      null);
                              repository.saveAndFlush(log);
                            }))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_matter_closure_log_reason"));
  }

  @Test
  void partialReopenFields_failsDbConsistencyCheck() {
    // The DB CHECK ck_matter_closure_log_reopen_consistent enforces "all-or-nothing" on the
    // reopen fields. The Java entity's recordReopen(...) enforces the same invariant at the
    // domain layer (Objects.requireNonNull on all three args). This test asserts the DB
    // guard independently by bypassing the entity with a raw UPDATE that sets reopened_at
    // only.
    final UUID projectId = UUID.randomUUID();
    final UUID[] savedId = new UUID[1];
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var log =
                      new MatterClosureLog(
                          projectId,
                          memberId,
                          Instant.now(),
                          "CONCLUDED",
                          null,
                          Map.of("gates", "all-passed"),
                          false,
                          null);
                  savedId[0] = repository.saveAndFlush(log).getId();
                }));

    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "UPDATE \"%s\".matter_closure_log".formatted(tenantSchema)
                        + " SET reopened_at = now() WHERE id = ?",
                    savedId[0]))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("ck_matter_closure_log_reopen_consistent");
  }

  @Test
  void fullReopen_viaRecordReopen_persists() {
    final UUID projectId = UUID.randomUUID();
    final UUID[] savedId = new UUID[1];
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var log =
                      new MatterClosureLog(
                          projectId,
                          memberId,
                          Instant.now(),
                          "CONCLUDED",
                          null,
                          Map.of("ok", true),
                          false,
                          null);
                  savedId[0] = repository.saveAndFlush(log).getId();
                }));

    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var log = repository.findById(savedId[0]).orElseThrow();
                  log.recordReopen(Instant.now(), memberId, "client changed their mind");
                  repository.saveAndFlush(log);
                }));

    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var reopened = repository.findById(savedId[0]).orElseThrow();
                  assertThat(reopened.getReopenedAt()).isNotNull();
                  assertThat(reopened.getReopenedBy()).isEqualTo(memberId);
                  assertThat(reopened.getReopenNotes()).isEqualTo("client changed their mind");
                }));
  }

  // ========================================================================
  // Query tests
  // ========================================================================

  @Test
  void findTopByProjectIdOrderByClosedAtDesc_returnsMostRecent() {
    final UUID projectId = UUID.randomUUID();
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var earlier =
                      new MatterClosureLog(
                          projectId,
                          memberId,
                          Instant.parse("2026-01-01T00:00:00Z"),
                          "CONCLUDED",
                          "first close",
                          Map.of("round", 1),
                          false,
                          null);
                  var later =
                      new MatterClosureLog(
                          projectId,
                          memberId,
                          Instant.parse("2026-02-01T00:00:00Z"),
                          "CONCLUDED",
                          "second close after reopen",
                          Map.of("round", 2),
                          false,
                          null);
                  repository.saveAndFlush(earlier);
                  repository.saveAndFlush(later);

                  var top = repository.findTopByProjectIdOrderByClosedAtDesc(projectId);
                  assertThat(top).isPresent();
                  assertThat(top.get().getNotes()).isEqualTo("second close after reopen");

                  var all = repository.findByProjectIdOrderByClosedAtDesc(projectId);
                  assertThat(all).hasSize(2);
                  assertThat(all.get(0).getNotes()).isEqualTo("second close after reopen");
                  assertThat(all.get(1).getNotes()).isEqualTo("first close");
                }));
  }

  // ========================================================================
  // Helpers
  // ========================================================================

  private void runInTenant(Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(action);
  }
}
