package io.b2mash.b2b.b2bstrawman.takeon;

import static io.b2mash.b2b.b2bstrawman.takeon.TakeOnClientsFiles.csv;
import static io.b2mash.b2b.b2bstrawman.takeon.TakeOnClientsFiles.mapping;
import static io.b2mash.b2b.b2bstrawman.takeon.TakeOnClientsFiles.row;
import static io.b2mash.b2b.b2bstrawman.takeon.TakeOnTestApi.attest;
import static io.b2mash.b2b.b2bstrawman.takeon.TakeOnTestApi.validatedClients;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMapping;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnProblems.TakeOnProblemException;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnTestApi.Validated;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.sql.Connection;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Phase 93 655A (task 655.8, §17.3.3, X5, R-4): the commit engine every dataset uses. The facade
 * refuses while the queue is off and leaves the batch {@code VALIDATED}; the claim allows one
 * {@code COMMITTING} batch per tenant (a double-submit is {@code ALREADY_COMMITTING}, another batch
 * {@code ANOTHER_BATCH_COMMITTING}, raced on real threads); rows are claimed {@code FOR UPDATE SKIP
 * LOCKED}, so two drains create each row once and a drain never completes a batch while another
 * worker holds a row (C-6, proven with a row lock held on a second connection); the budget stops a
 * run and the next finishes it; the drain acts as the member who confirmed the commit; the sweep
 * finds only tenants with work. Each test runs its own batches in this class's own tenant; the
 * tenant's one commit slot is freed after each test.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TakeOnCommitEngineIntegrationTest {

  private static final String ORG_ID = "org_takeon_commit_engine_test";
  private static final String OWNER = "user_tce_owner";
  private static final String ADMIN = "user_tce_admin";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private DataSource dataSource;
  @Autowired private TakeOnCommitService commitService;
  @Autowired private TakeOnDrainJobHandler drain;
  @Autowired private TakeOnDrainSweep sweep;
  @Autowired private OrgSchemaMappingRepository mappings;
  @Autowired private TakeOnSignOffRepository signOffs;

  private String schema;
  private UUID ownerId;
  private UUID adminId;
  private int seq;

  @BeforeAll
  void setup() throws Exception {
    schema = provisioningService.provisionTenant(ORG_ID, "Commit Engine", "legal-za").schemaName();
    ownerId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, OWNER, "tce_owner@test.com", "Engine Owner", "owner"));
    adminId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, ADMIN, "tce_admin@test.com", "Engine Admin", "admin"));
  }

  /** Frees the tenant's one commit slot whatever a test left behind (drains to completion). */
  @AfterEach
  void drainLeftovers() {
    TakeOnTestDrain.run(drain, schema, ORG_ID);
  }

  // 1
  @Test
  void queueOff_commitIsRefusedBeforeAnyClaim_andTheBatchStaysValidated() throws Exception {
    var batch = attested(2);
    TakeOnTestApi.commit(mockMvc, owner(), batch.batchId(), batch.hash())
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("TAKE_ON_QUEUE_DISABLED"))
        .andExpect(jsonPath("$.title").value("Take-on commits are switched off on this server"))
        .andExpect(
            jsonPath("$.detail")
                .value(
                    "Take-on commits run in the background, and the background worker is switched"
                        + " off on this server."));
    assertThat(batchStatus(batch.batchId())).isEqualTo("VALIDATED");
    assertThat(auditTypes(batch.batchId())).doesNotContain("takeon.batch.commit_requested");
  }

  // 2
  @Test
  void aDoubleSubmit_racedOnTwoThreads_claimsOnce_andTheSecondIsAlreadyCommitting()
      throws Exception {
    var batch = attested(1);
    var outcomes = race(2, () -> claim(ownerId, batch));
    assertThat(outcomes).containsExactlyInAnyOrder("CLAIMED", "ALREADY_COMMITTING");
    assertThat(batchStatus(batch.batchId())).isEqualTo("COMMITTING");
    assertThatThrownBy(() -> claimOrThrow(ownerId, batch))
        .isInstanceOfSatisfying(
            TakeOnProblemException.class,
            e -> {
              assertThat(e.getCode()).isEqualTo("ALREADY_COMMITTING");
              assertThat(e.getBody().getDetail())
                  .isEqualTo("This take-on batch is already committing. Wait for it to finish.");
            });
    assertThat(auditTypes(batch.batchId()))
        .filteredOn("takeon.batch.commit_requested"::equals)
        .hasSize(1);
  }

  // 3
  @Test
  void twoBatchesClaimedAtOnce_oneWins_andTheOtherIsAnotherBatchCommitting() throws Exception {
    var first = attested(1);
    var second = attested(1);
    var results = new ArrayList<String>();
    var latch = new CountDownLatch(1);
    try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
      List<Future<String>> futures = new ArrayList<>();
      for (var b : List.of(first, second)) {
        futures.add(
            pool.submit(
                () -> {
                  latch.await();
                  return claim(ownerId, b);
                }));
      }
      latch.countDown();
      for (var f : futures) {
        results.add(f.get(60, TimeUnit.SECONDS));
      }
    }
    assertThat(results).containsExactlyInAnyOrder("CLAIMED", "ANOTHER_BATCH_COMMITTING");
    var loser = "CLAIMED".equals(results.getFirst()) ? second : first;
    assertThat(batchStatus(loser.batchId())).isEqualTo("VALIDATED");
    assertThatThrownBy(() -> claimOrThrow(ownerId, loser))
        .isInstanceOfSatisfying(
            TakeOnProblemException.class,
            e ->
                assertThat(e.getBody().getDetail())
                    .isEqualTo("Another take-on batch is committing. Wait for it to finish."));
  }

  // 4
  @Test
  void aCommitOnAnotherHash_isHashMismatch() throws Exception {
    var batch = attested(1);
    assertThatThrownBy(
            () ->
                claimOrThrow(ownerId, new Validated(batch.batchId(), "0".repeat(64), batch.json())))
        .isInstanceOfSatisfying(
            TakeOnProblemException.class, e -> assertThat(e.getCode()).isEqualTo("HASH_MISMATCH"));
    assertThat(batchStatus(batch.batchId())).isEqualTo("VALIDATED");
  }

  // 5
  @Test
  void twoDrainsRacingOnOneBatch_createEachRowOnce_andCompleteItOnce() throws Exception {
    var batch = attested(12);
    claimOrThrow(ownerId, batch);
    var outcomes =
        race(
            2,
            () -> {
              TakeOnTestDrain.run(drain, schema, ORG_ID);
              return "DRAINED";
            });
    assertThat(outcomes).containsExactly("DRAINED", "DRAINED");
    TakeOnTestDrain.run(drain, schema, ORG_ID); // whichever was mid-row last has completed it
    assertThat(batchStatus(batch.batchId())).isEqualTo("COMPLETED");
    assertThat(
            jdbc.queryForObject(
                ("SELECT count(*) FROM \"%s\".take_on_rows WHERE batch_id = ? AND status ="
                        + " 'CREATED'")
                    .formatted(schema),
                Integer.class,
                batch.batchId()))
        .isEqualTo(12);
    assertThat(createdCount(batch.batchId())).isEqualTo(12);
    assertThat(
            jdbc.queryForObject(
                ("SELECT count(DISTINCT lower(email)) FROM \"%s\".customers"
                        + " WHERE take_on_batch_id = ?")
                    .formatted(schema),
                Integer.class,
                batch.batchId()))
        .isEqualTo(12);
    assertThat(
            jdbc.queryForObject(
                ("SELECT count(*) FROM \"%s\".client_fica_attestations WHERE take_on_batch_id = ?")
                    .formatted(schema),
                Integer.class,
                batch.batchId()))
        .isEqualTo(12);
    assertThat(auditTypes(batch.batchId())).filteredOn("takeon.batch.committed"::equals).hasSize(1);
    var committed = committedDetails(batch.batchId());
    assertThat(committed)
        .containsEntry("created_rows", 12)
        .containsEntry("skipped_rows", 0)
        .containsEntry("failed_rows", 0)
        .containsEntry("dataset", "CLIENTS");
  }

  // 6
  @Test
  void aRowHeldByAnotherWorker_isSkipped_andTheBatchIsNotCompletedUnderIt() throws Exception {
    var batch = attested(3);
    claimOrThrow(ownerId, batch);
    UUID held =
        jdbc.queryForObject(
            ("SELECT id FROM \"%s\".take_on_rows WHERE batch_id = ? AND status = 'VALIDATED'"
                    + " ORDER BY row_number OFFSET 1 LIMIT 1")
                .formatted(schema),
            UUID.class,
            batch.batchId());
    var locked = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    try (ExecutorService pool = Executors.newSingleThreadExecutor()) {
      Future<?> holder =
          pool.submit(
              () -> {
                try (Connection c = dataSource.getConnection()) {
                  c.setAutoCommit(false);
                  try (var st =
                      c.prepareStatement(
                          "SELECT id FROM \"%s\".take_on_rows WHERE id = ? FOR UPDATE"
                              .formatted(schema))) {
                    st.setObject(1, held);
                    st.executeQuery();
                  }
                  locked.countDown(); // worker A is "mid-row" on the held row
                  release.await(60, TimeUnit.SECONDS);
                  c.rollback();
                }
                return null;
              });
      assertThat(locked.await(30, TimeUnit.SECONDS)).isTrue();

      TakeOnTestDrain.run(drain, schema, ORG_ID); // worker B: skips the held row, never waits
      assertThat(batchStatus(batch.batchId()))
          .as("B must not complete the batch while A holds a row (C-6)")
          .isEqualTo("COMMITTING");
      assertThat(rowStatus(held)).isEqualTo("VALIDATED");
      assertThat(
              jdbc.queryForObject(
                  ("SELECT count(*) FROM \"%s\".take_on_rows WHERE batch_id = ? AND status ="
                          + " 'CREATED'")
                      .formatted(schema),
                  Integer.class,
                  batch.batchId()))
          .isEqualTo(2);
      var live = TakeOnTestApi.detail(mockMvc, owner(), batch.batchId());
      live.andExpect(jsonPath("$.counts.created").value(2))
          .andExpect(jsonPath("$.status").value("COMMITTING"));

      release.countDown();
      holder.get(60, TimeUnit.SECONDS);
    }
    TakeOnTestDrain.run(drain, schema, ORG_ID); // the next drain finishes the released row
    assertThat(rowStatus(held)).isEqualTo("CREATED");
    assertThat(batchStatus(batch.batchId())).isEqualTo("COMPLETED");
  }

  // 7
  @Test
  void aDrainStopsTakingWorkAtItsBudget_andTheNextRunFinishesTheBatch() throws Exception {
    var batch = attested(3);
    claimOrThrow(ownerId, batch);
    TakeOnTestDrain.run(drain, schema, ORG_ID, Duration.ZERO);
    assertThat(batchStatus(batch.batchId())).isEqualTo("COMMITTING");
    assertThat(createdCount(batch.batchId())).isEqualTo(1);
    TakeOnTestDrain.run(drain, schema, ORG_ID, Duration.ZERO);
    assertThat(createdCount(batch.batchId())).isEqualTo(2);
    TakeOnTestDrain.run(drain, schema, ORG_ID);
    assertThat(batchStatus(batch.batchId())).isEqualTo("COMPLETED");
    assertThat(createdCount(batch.batchId())).isEqualTo(3);
  }

  // 8
  @Test
  void theDrainActsAsTheMemberWhoConfirmedTheCommit() throws Exception {
    var batch = attested(1); // validated and attested by the owner
    claimOrThrow(adminId, batch); // confirmed by the admin
    TakeOnTestDrain.run(drain, schema, ORG_ID);
    UUID client =
        jdbc.queryForObject(
            "SELECT result_entity_id FROM \"%s\".take_on_rows WHERE batch_id = ?".formatted(schema),
            UUID.class,
            batch.batchId());
    assertThat(
            jdbc.queryForMap(
                ("SELECT created_by, lifecycle_status_changed_by FROM \"%s\".customers"
                        + " WHERE id = ?")
                    .formatted(schema),
                client))
        .containsEntry("created_by", adminId)
        .containsEntry("lifecycle_status_changed_by", adminId);
    var created = TakeOnTestApi.auditEvents(jdbc, schema, client).getFirst();
    assertThat(created.eventType()).isEqualTo("customer.created");
    assertThat(created.actorType()).isEqualTo("USER");
    assertThat(created.actorName()).isEqualTo("Engine Admin");
    var batchAudit = TakeOnTestApi.auditEvents(jdbc, schema, batch.batchId());
    var requested =
        batchAudit.stream()
            .filter(a -> a.eventType().equals("takeon.batch.commit_requested"))
            .findFirst()
            .orElseThrow();
    assertThat(requested.actorName()).isEqualTo("Engine Admin");
    assertThat(requested.details())
        .containsOnlyKeys("batch_id", "dataset", "sign_off_id")
        .containsEntry("dataset", "CLIENTS");
    assertThat(
            jdbc.queryForObject(
                ("SELECT actor_id FROM \"%s\".audit_events WHERE entity_id = ? AND event_type ="
                        + " 'takeon.batch.committed'")
                    .formatted(schema),
                UUID.class,
                batch.batchId()))
        .isEqualTo(adminId);
  }

  // 9
  @Test
  void theSweep_findsOnlyTenantsWithACommittingBatch_andSkipsBadSchemaNamesAndBrokenSchemas()
      throws Exception {
    var mine = mappings.findBySchemaName(schema).orElseThrow();
    assertThat(sweep.tenantsWithWork(List.of(mine))).isEmpty();
    var batch = attested(1);
    claimOrThrow(ownerId, batch);
    assertThat(sweep.tenantsWithWork(List.of(mine)))
        .extracting(OrgSchemaMapping::getSchemaName)
        .containsExactly(schema);
    var hostile = new OrgSchemaMapping("org_hostile", "tenant_x\"; DROP TABLE customers; --");
    assertThat(sweep.tenantsWithWork(List.of(hostile, mine)))
        .extracting(OrgSchemaMapping::getSchemaName)
        .containsExactly(schema);
    // a mapping whose schema has no take_on_batches (its boot-time migration failed — here it
    // does not exist at all) must not hide the healthy tenant in the same chunk (review CR-8)
    var broken = new OrgSchemaMapping("org_broken", "tenant_0000deadbeef");
    assertThat(sweep.tenantsWithWork(List.of(broken, mine)))
        .extracting(OrgSchemaMapping::getSchemaName)
        .containsExactly(schema);
    TakeOnTestDrain.run(drain, schema, ORG_ID);
    assertThat(sweep.tenantsWithWork(List.of(mine))).isEmpty();
  }

  // 10
  @Test
  void anAttestationGoneBeforeTheFirstRow_returnsTheBatchToValidatedWithTheReason()
      throws Exception {
    var batch = attested(2);
    claimOrThrow(ownerId, batch);
    // Simulates a lost attestation (the revoke endpoint refuses while committing).
    RequestScopes.runForTenant(
        schema,
        ORG_ID,
        () ->
            signOffs
                .findByBatchIdAndKindAndRevokedAtIsNull(
                    batch.batchId(), TakeOnSignOffKind.FICA_ATTESTATION)
                .ifPresent(
                    o -> {
                      o.revoke(TakeOnSignOff.RevokedReason.REVOKED);
                      signOffs.save(o);
                    }));
    TakeOnTestDrain.run(drain, schema, ORG_ID);
    var detail =
        TakeOnTestApi.detail(mockMvc, owner(), batch.batchId())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat((String) JsonPath.read(detail, "$.status")).isEqualTo("VALIDATED");
    assertThat((String) JsonPath.read(detail, "$.lastError"))
        .isEqualTo(
            "The attestation for this take-on batch was withdrawn before the commit ran. Attest"
                + " it again, then commit.");
    assertThat(createdCount(batch.batchId())).isZero();
    var failed =
        TakeOnTestApi.auditEvents(jdbc, schema, batch.batchId()).stream()
            .filter(a -> a.eventType().equals("takeon.batch.commit_failed"))
            .findFirst()
            .orElseThrow();
    assertThat(failed.details())
        .isEqualTo(
            Map.of(
                "batch_id", batch.batchId().toString(),
                "dataset", "CLIENTS",
                "reason_code", "ATTESTATION_MISSING"));
  }

  // --- helpers ---

  /** A validated, attested clients batch of {@code n} fresh rows. */
  private Validated attested(int n) throws Exception {
    int base = ++seq * 100;
    var rows =
        IntStream.range(0, n)
            .mapToObj(
                i ->
                    row("Engine Client " + (base + i), "engine." + (base + i) + "@example.test")
                        .ref("E-" + (base + i)))
            .toArray(TakeOnClientsFiles.Row[]::new);
    var batch = validatedClients(mockMvc, owner(), csv(rows), mapping());
    attest(mockMvc, owner(), batch.batchId(), batch.hash(), Map.of()).andExpect(status().isOk());
    return batch;
  }

  private String claim(UUID member, Validated batch) {
    try {
      claimOrThrow(member, batch);
      return "CLAIMED";
    } catch (TakeOnProblemException e) {
      return e.getCode();
    }
  }

  private void claimOrThrow(UUID member, Validated batch) {
    TakeOnTestDrain.commit(commitService, schema, ORG_ID, member, batch.batchId(), batch.hash());
  }

  /** Runs {@code task} on {@code n} threads released together by one latch. */
  private List<String> race(int n, java.util.concurrent.Callable<String> task) throws Exception {
    var start = new CountDownLatch(1);
    var results = new ArrayList<String>();
    try (ExecutorService pool = Executors.newFixedThreadPool(n)) {
      List<Future<String>> futures = new ArrayList<>();
      for (int i = 0; i < n; i++) {
        futures.add(
            pool.submit(
                () -> {
                  start.await();
                  return task.call();
                }));
      }
      start.countDown();
      for (var f : futures) {
        results.add(f.get(120, TimeUnit.SECONDS));
      }
    }
    return results;
  }

  private String batchStatus(UUID batchId) {
    return jdbc.queryForObject(
        "SELECT status FROM \"%s\".take_on_batches WHERE id = ?".formatted(schema),
        String.class,
        batchId);
  }

  private String rowStatus(UUID rowId) {
    return jdbc.queryForObject(
        "SELECT status FROM \"%s\".take_on_rows WHERE id = ?".formatted(schema),
        String.class,
        rowId);
  }

  private int createdCount(UUID batchId) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM \"%s\".customers WHERE take_on_batch_id = ?".formatted(schema),
        Integer.class,
        batchId);
  }

  private List<String> auditTypes(UUID batchId) {
    return TakeOnTestApi.auditEvents(jdbc, schema, batchId).stream()
        .map(TakeOnTestApi.AuditRow::eventType)
        .toList();
  }

  private Map<String, Object> committedDetails(UUID batchId) {
    return TakeOnTestApi.auditEvents(jdbc, schema, batchId).stream()
        .filter(a -> a.eventType().equals("takeon.batch.committed"))
        .findFirst()
        .orElseThrow()
        .details();
  }

  private RequestPostProcessor owner() {
    return TestJwtFactory.ownerJwt(ORG_ID, OWNER);
  }
}
