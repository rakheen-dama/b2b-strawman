package io.b2mash.b2b.b2bstrawman.compliance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.customer.LifecycleStatus;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnClientsFiles;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnCommitService;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnDrainJobHandler;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnTestApi;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnTestDrain;
import io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestEntityHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
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

/**
 * Phase 93 655A (tasks 655.1, 655.8, R-9): the dormancy job and imported clients, through {@link
 * CustomerLifecycleService#executeDormancyTransitions()} (the job's own entry, as {@code
 * DormancyScheduledJob} calls it).
 *
 * <p>{@link #activeClientWithNoActivityRows_goesDormantAtTheFirstRun} was written first and run on
 * unchanged {@code main} (characterisation, {@code qa_cycle/evidence/phase93/655A/}): a client
 * persisted {@code ACTIVE} with no activity rows goes {@code DORMANT} at the first run — the {@code
 * GREATEST(...) IS NULL} branch of {@code DORMANCY_SQL}, the hazard every imported client would
 * hit. It stays as "a non-imported client's result is unchanged". The take-on counts as activity
 * ({@code MAX(c.taken_on_at)} in all three {@code GREATEST}s): an imported client stays {@code
 * ACTIVE}, and goes dormant only when nothing has happened for the threshold after take-on.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ImportedClientDormancyIntegrationTest {

  private static final String ORG_ID = "org_imported_client_dormancy_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private CustomerLifecycleService lifecycleService;
  @Autowired private TakeOnCommitService commitService;
  @Autowired private TakeOnDrainJobHandler drain;
  @Autowired private JdbcTemplate jdbc;

  private String schema;
  private UUID memberId;

  @BeforeAll
  void setup() throws Exception {
    schema =
        provisioningService
            .provisionTenant(ORG_ID, "Imported Client Dormancy", "legal-za")
            .schemaName();
    memberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                ORG_ID,
                "user_icd_owner",
                "icd_owner@test.com",
                "Dormancy Owner",
                "owner"));
  }

  @Test
  void importedClient_staysActiveAtTheFirstRun() throws Exception {
    UUID imported = takeOn("Imported Active", "imported.active@example.test");
    inTenant(lifecycleService::executeDormancyTransitions);
    assertThat(lifecycle(imported)).isEqualTo(LifecycleStatus.ACTIVE);
    assertThat(
            inTenant(lifecycleService::runDormancyCheck).candidates().stream()
                .map(c -> c.clientId())
                .toList())
        .doesNotContain(imported);
  }

  @Test
  void importedClient_withNoActivityForTheThresholdAfterTakeOn_goesDormant() throws Exception {
    UUID imported = takeOn("Imported Idle", "imported.idle@example.test");
    // Time travel on this test's own client only: the take-on happened 91 days ago (threshold 90).
    jdbc.update(
        "UPDATE \"%s\".customers SET taken_on_at = now() - interval '91 days' WHERE id = ?"
            .formatted(schema),
        imported);
    inTenant(lifecycleService::executeDormancyTransitions);
    assertThat(lifecycle(imported)).isEqualTo(LifecycleStatus.DORMANT);
  }

  @Test
  void activeClientWithNoActivityRows_goesDormantAtTheFirstRun() {
    UUID clientId =
        inTenant(
            () ->
                customerRepository
                    .save(
                        TestCustomerFactory.createActiveCustomer(
                            "No Activity Client", "no.activity@example.test", memberId))
                    .getId());

    inTenant(lifecycleService::executeDormancyTransitions);

    LifecycleStatus status =
        inTenant(() -> customerRepository.findById(clientId).orElseThrow().getLifecycleStatus());
    assertThat(status).isEqualTo(LifecycleStatus.DORMANT);
  }

  /**
   * A client whose last activity row is older than the threshold (a non-null {@code last_activity})
   * goes dormant too — the path an imported client takes once its {@code taken_on_at} is the old
   * activity. Found while building 655A: on {@code main} this path threw {@code ClassCastException}
   * (Hibernate 7 returns {@code Instant}, the code cast to {@code Timestamp}), see {@code
   * qa_cycle/evidence/phase93/655A/characterisation.md} run 2.
   */
  @Test
  void clientWithActivityOlderThanTheThreshold_goesDormant() throws Exception {
    var owner = TestJwtFactory.ownerJwt(ORG_ID, "user_icd_owner");
    UUID clientId =
        inTenant(
            () ->
                customerRepository
                    .save(
                        TestCustomerFactory.createActiveCustomer(
                            "Old Activity Client", "old.activity@example.test", memberId))
                    .getId());
    String matterId = TestEntityHelper.createProject(mockMvc, owner, "Old Activity Matter");
    mockMvc
        .perform(post("/api/clients/" + clientId + "/matters/" + matterId).with(owner))
        .andExpect(status().isCreated());
    String taskId = TestEntityHelper.createTask(mockMvc, owner, matterId, "Old task");
    jdbc.update(
        "UPDATE \"%s\".tasks SET updated_at = now() - interval '120 days' WHERE id = ?::uuid"
            .formatted(schema),
        taskId);
    inTenant(lifecycleService::executeDormancyTransitions);
    assertThat(lifecycle(clientId)).isEqualTo(LifecycleStatus.DORMANT);
  }

  /** Takes one client on through the wizard's endpoints and the drain; returns its id. */
  private UUID takeOn(String name, String email) throws Exception {
    var owner = TestJwtFactory.ownerJwt(ORG_ID, "user_icd_owner");
    var batch =
        TakeOnTestApi.validatedClients(
            mockMvc,
            owner,
            TakeOnClientsFiles.csv(TakeOnClientsFiles.row(name, email)),
            TakeOnClientsFiles.mapping());
    TakeOnTestApi.attest(mockMvc, owner, batch.batchId(), batch.hash(), Map.of())
        .andExpect(status().isOk());
    TakeOnTestDrain.commitAndDrain(
        commitService, drain, schema, ORG_ID, memberId, batch.batchId(), batch.hash());
    UUID id =
        jdbc.queryForObject(
            "SELECT result_entity_id FROM \"%s\".take_on_rows WHERE batch_id = ? AND status = 'CREATED'"
                .formatted(schema),
            UUID.class,
            batch.batchId());
    assertThat(lifecycle(id)).isEqualTo(LifecycleStatus.ACTIVE);
    return id;
  }

  private LifecycleStatus lifecycle(UUID clientId) {
    return inTenant(() -> customerRepository.findById(clientId).orElseThrow().getLifecycleStatus());
  }

  private <T> T inTenant(Callable<T> action) {
    return RequestScopes.callForTenant(schema, ORG_ID, action);
  }
}
