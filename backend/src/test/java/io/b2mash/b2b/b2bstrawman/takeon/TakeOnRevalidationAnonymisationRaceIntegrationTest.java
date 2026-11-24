package io.b2mash.b2b.b2bstrawman.takeon;

import static io.b2mash.b2b.b2bstrawman.takeon.TakeOnTrustWorld.C;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnTrustWorld.Accounts;
import io.b2mash.b2b.b2bstrawman.takeon.dataset.TrustPositionDatasetHandler;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * F-657C-7 (657C delta review, hypothesis): the dry run re-validates a batch's rows read without a
 * row lock and writes each row back whole ({@link TakeOnRow} has no {@code @Version} / {@code
 * DynamicUpdate}). A client anonymisation that commits between that read and the dry run's flush
 * must keep its redaction: the dry run may fail, but it must never put the client's values back.
 *
 * <p>The dry run is paused deterministically after it has read every row and before it writes any
 * (at its first {@link TrustPositionDatasetHandler#mapAndValidate}); the anonymisation runs and
 * commits; then the dry run is released. Observed outcome: the dry run's {@code REPEATABLE_READ}
 * snapshot predates the redaction, so its write of the redacted row is refused by Postgres ("could
 * not serialize access due to concurrent update", 40001) and the request answers 409 {@code
 * TAKE_ON_CONCURRENT_CHANGE} (C-21) — the stale values are never written. Paused any later (after
 * the handler's first query autoflushes the rows) the dry run holds the rows' locks and the
 * anonymisation simply waits for it.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TakeOnRevalidationAnonymisationRaceIntegrationTest {

  private static final String ORG_ID = "org_take_on_revalidation_race_test";
  private static final String OWNER = "user_trr_owner";
  private static final String PREFIX = "trr";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private TakeOnCommitService commitService;
  @Autowired private TakeOnDrainJobHandler drain;
  @MockitoSpyBean private TrustPositionDatasetHandler trustHandler;

  @Autowired
  @Qualifier("portalJdbcClient")
  private JdbcClient portalJdbc;

  private String schema;
  private TakeOnTrustWorld world;

  @BeforeAll
  void setup() throws Exception {
    schema =
        provisioningService.provisionTenant(ORG_ID, "Revalidation Race", "legal-za").schemaName();
    UUID ownerId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, OWNER, "trr_owner@test.com", "Race Owner", "owner"));
    world =
        new TakeOnTrustWorld(
            mockMvc, jdbc, commitService, drain, ORG_ID, OWNER, schema, ownerId, PREFIX);
    world.takeOnClientsAndMatters(List.of("A", "B", "R"));
  }

  @AfterEach
  void unpause() {
    reset(trustHandler);
  }

  @Test
  void anAnonymisationCommittingDuringARevalidation_keepsItsRedaction() throws Exception {
    Accounts acc = world.freshAccounts();
    var files =
        TakeOnTrustFiles.withRows(
            world.files(acc),
            TakeOnTrustFiles.CLIENT_LEDGER,
            List.of("A,A1,300000.00,", "B,B1,150000.00,", "R,R1,49500.00,"));
    var batch = world.validated(acc.g(), files, TakeOnTrustFiles.section17313Position(C));
    UUID r = world.client("R");
    assertThat(rowsNaming(batch.batchId(), r)).as("R's ledger row before the race").isEqualTo(1);

    // pause the re-validation at its first row: every row is read (one unlocked select), none is
    // written yet — the earliest point of the window between the read and the flush. Later the
    // handler's own queries autoflush the rows (taking their row locks), which closes the window.
    var paused = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    doAnswer(
            invocation -> {
              TakeOnRow row = invocation.getArgument(0);
              if (row.getBatchId().equals(batch.batchId()) && paused.getCount() > 0) {
                paused.countDown();
                await(release);
              }
              return invocation.callRealMethod();
            })
        .when(trustHandler)
        .mapAndValidate(any(), any());

    var revalidation = new AtomicReference<MockHttpServletResponse>();
    var revalidationFailure = new AtomicReference<Throwable>();
    Thread validating =
        Thread.ofVirtual()
            .start(
                () -> {
                  try {
                    revalidation.set(
                        TakeOnTestApi.validate(mockMvc, world.owner(), batch.batchId())
                            .andReturn()
                            .getResponse());
                  } catch (Throwable t) {
                    revalidationFailure.set(t);
                  }
                });
    try {
      assertThat(paused.await(60, TimeUnit.SECONDS)).as("the re-validation paused").isTrue();

      // the anonymisation runs and commits while the re-validation holds its stale rows
      var anonymiseFailure = new AtomicReference<Throwable>();
      Thread anonymising =
          Thread.ofVirtual()
              .start(
                  () -> {
                    try {
                      anonymise(r);
                    } catch (Throwable t) {
                      anonymiseFailure.set(t);
                    }
                  });
      anonymising.join(30_000);
      assertThat(anonymising.isAlive())
          .as(
              "the anonymisation completes while the re-validation is paused; backends: %s",
              backends())
          .isFalse();
      assertThat(anonymiseFailure.get()).isNull();
      assertThat(redactedRows(batch.batchId()))
          .as("the anonymisation committed its redaction")
          .isEqualTo(1);
    } finally {
      release.countDown();
    }
    validating.join(60_000);
    assertThat(validating.isAlive()).isFalse();
    assertThat(revalidationFailure.get()).isNull();

    // the client's values must not have come back
    assertThat(rowsNaming(batch.batchId(), r))
        .as(
            "take-on rows naming the anonymised client after the re-validation (HTTP %s)",
            revalidation.get().getStatus())
        .isZero();
    assertThat(redactedRows(batch.batchId()))
        .as(
            "redacted take-on rows after the re-validation (HTTP %s)",
            revalidation.get().getStatus())
        .isEqualTo(1);
    assertThat(rawValuesMentioning(batch.batchId(), "R1"))
        .as("rows whose raw values still quote the anonymised client's matter")
        .isZero();

    // the dry run's snapshot predates the redaction: its write is a serialization failure (C-21)
    var response = revalidation.get();
    assertThat(response.getStatus())
        .as("re-validation response %s", response.getContentAsString())
        .isEqualTo(409);
    assertThat(response.getContentAsString()).contains("\"code\":\"TAKE_ON_CONCURRENT_CHANGE\"");
  }

  private void anonymise(UUID clientId) throws Exception {
    mockMvc
        .perform(
            post("/api/clients/" + clientId + "/anonymize")
                .with(world.owner())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"confirmationName\": \"Client R %s\", \"reason\": \"test\"}"
                        .formatted(PREFIX)))
        .andExpect(status().isOk());
  }

  /** The database's active backends (read through the portal pool: the app pool may be busy). */
  private Object backends() {
    return portalJdbc
        .sql(
            "SELECT state, wait_event_type, wait_event, pg_blocking_pids(pid)::text AS blockers,"
                + " left(query, 200) AS query FROM pg_stat_activity"
                + " WHERE datname = current_database() AND state <> 'idle'"
                + " AND pid <> pg_backend_pid()")
        .query()
        .listOfRows();
  }

  private long rowsNaming(UUID batchId, UUID clientId) {
    return jdbc.queryForObject(
        ("SELECT count(*) FROM \"%s\".take_on_rows WHERE batch_id = ?"
                + " AND mapped_values ->> 'clientId' = ?")
            .formatted(schema),
        Long.class,
        batchId,
        clientId.toString());
  }

  private long redactedRows(UUID batchId) {
    return jdbc.queryForObject(
        ("SELECT count(*) FROM \"%s\".take_on_rows WHERE batch_id = ?"
                + " AND raw_values = '{\"redacted\": true}'::jsonb"
                + " AND mapped_values = '{\"redacted\": true}'::jsonb")
            .formatted(schema),
        Long.class,
        batchId);
  }

  private long rawValuesMentioning(UUID batchId, String text) {
    return jdbc.queryForObject(
        ("SELECT count(*) FROM \"%s\".take_on_rows WHERE batch_id = ?"
                + " AND raw_values::text LIKE ?")
            .formatted(schema),
        Long.class,
        batchId,
        "%\"" + text + "\"%");
  }

  private static void await(CountDownLatch latch) {
    try {
      if (!latch.await(60, TimeUnit.SECONDS)) {
        throw new IllegalStateException("latch timed out");
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }
}
