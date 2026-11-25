package io.b2mash.b2b.b2bstrawman.integration.accounting.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.integration.IntegrationDomain;
import io.b2mash.b2b.b2bstrawman.integration.OrgIntegration;
import io.b2mash.b2b.b2bstrawman.integration.OrgIntegrationRepository;
import io.b2mash.b2b.b2bstrawman.integration.accounting.xero.AccountingXeroConnection;
import io.b2mash.b2b.b2bstrawman.integration.accounting.xero.AccountingXeroConnectionRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestEntityHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * Xero pushes ship switched off (PR #1860 ruling): {@code kazi.accounting.push.enabled} is false
 * when absent, and this class runs on that default. The drain worker still sends placeholder
 * payloads, so while pushes are off nothing may be queued from the event route, the worker must not
 * dispatch an INVOICE or CUSTOMER push (and must not dead-letter it either), and a manual resync is
 * refused with a readable 409. Xero is CONNECTED here, so every "nothing queued" is the switch and
 * not the no-connection guard. Asserts by entity id, never by global counts.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AccountingPushDisabledIntegrationTest {

  private static final String ORG_ID = "org_sync_push_off_test";
  private static final String OWNER = "user_sync_push_off_owner";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private CustomerRepository customers;
  @Autowired private AccountingSyncEntryRepository syncEntries;
  @Autowired private AccountingSyncWorker worker;
  @Autowired private OrgIntegrationRepository orgIntegrations;
  @Autowired private AccountingXeroConnectionRepository xeroConnections;

  private String schema;
  private UUID clientId;

  @BeforeAll
  void setup() throws Exception {
    schema =
        provisioningService.provisionTenant(ORG_ID, "Push Off Attorneys", "legal-za").schemaName();
    UUID ownerId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, OWNER, "sync_push_off_owner@test.com", "Push Off Owner", "owner"));
    clientId =
        inTenant(
            () ->
                customers
                    .save(
                        TestCustomerFactory.createActiveCustomerWithPrerequisiteFields(
                            "Ayanda Zulu", "sync.push.off.client@example.test", ownerId))
                    .getId());
    inTenant(
        () -> {
          createConnectedXeroConnection();
          return null;
        });
  }

  @Test
  void approvingSendingAndVoidingAFeeNote_queueNothing() throws Exception {
    UUID id = approvedFeeNote();
    assertThat(entriesFor(SyncEntityType.INVOICE, id)).as("after approve").isEmpty();

    postOk("/api/invoices/" + id + "/send", "{\"overrideWarnings\": true}");
    assertThat(entriesFor(SyncEntityType.INVOICE, id)).as("after send").isEmpty();

    postOk("/api/invoices/" + id + "/void", null);
    assertThat(entriesFor(SyncEntityType.INVOICE, id)).as("after void").isEmpty();
  }

  @Test
  void creatingAndUpdatingAClient_queueNothing() throws Exception {
    UUID customerId =
        UUID.fromString(
            TestEntityHelper.createCustomer(
                mockMvc,
                TestJwtFactory.ownerJwt(ORG_ID, OWNER),
                "Nomvula Sithole",
                "sync.push.off.created@example.test"));
    assertThat(entriesFor(SyncEntityType.CUSTOMER, customerId)).as("after create").isEmpty();

    mockMvc
        .perform(
            MockMvcRequestBuilders.put("/api/clients/" + customerId)
                .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"name\": \"Nomvula Sithole-Ndlovu\","
                        + " \"email\": \"sync.push.off.created@example.test\"}"))
        .andExpect(status().isOk());
    assertThat(entriesFor(SyncEntityType.CUSTOMER, customerId)).as("after update").isEmpty();
  }

  @Test
  void theWorker_leavesPendingPushEntriesUntouched_andStillDrainsAPaymentPull() {
    // queued before the switch went off (or by a direct service call): never dispatched to the stub
    var invoicePush = savedEntry(SyncEntityType.INVOICE, SyncDirection.PUSH);
    var customerPush = savedEntry(SyncEntityType.CUSTOMER, SyncDirection.PUSH);
    // non-push work is unchanged by the switch
    var paymentPull = savedEntry(SyncEntityType.PAYMENT_PULL, SyncDirection.PULL);

    inTenant(worker::drainForTenant);

    for (var before : List.of(invoicePush, customerPush)) {
      var after = inTenant(() -> syncEntries.findOneById(before.getId()).orElseThrow());
      assertThat(after.getState()).as(before.getEntityType().name()).isEqualTo(SyncState.PENDING);
      assertThat(after.getAttemptCount()).as(before.getEntityType().name()).isZero();
      assertThat(after.getLastErrorCode()).as(before.getEntityType().name()).isNull();
      assertThat(after.getExternalId()).as(before.getEntityType().name()).isNull();
      assertThat(after.getUpdatedAt())
          .as(before.getEntityType().name())
          .isEqualTo(before.getUpdatedAt());
    }
    assertThat(
            inTenant(() -> syncEntries.findOneById(paymentPull.getId()).orElseThrow()).getState())
        .isEqualTo(SyncState.COMPLETED);
  }

  @Test
  void aManualResync_isRefusedWithAReadable409_andQueuesNothing() throws Exception {
    UUID id = approvedFeeNote();

    mockMvc
        .perform(
            MockMvcRequestBuilders.post("/api/integrations/sync/invoice/" + id + "/resync")
                .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.title").value("Xero sync is not available yet"))
        .andExpect(
            jsonPath("$.detail")
                .value("Kazi does not send fee notes or clients to Xero yet. Nothing was queued."))
        .andExpect(jsonPath("$.code").value("XERO_SYNC_NOT_AVAILABLE"));

    assertThat(entriesFor(SyncEntityType.INVOICE, id)).isEmpty();
  }

  // --- helpers ---

  /** An ordinary Kazi fee note through the API: createDraft + a manual line + approve. */
  private UUID approvedFeeNote() throws Exception {
    String draft =
        mockMvc
            .perform(
                MockMvcRequestBuilders.post("/api/invoices")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"clientId\": \"%s\", \"currency\": \"ZAR\"}".formatted(clientId)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String id = JsonPath.read(draft, "$.id");
    mockMvc
        .perform(
            MockMvcRequestBuilders.post("/api/invoices/" + id + "/lines")
                .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"description\": \"Consultation\", \"quantity\": 1, \"unitPrice\": 250.00,"
                        + " \"sortOrder\": 0}"))
        .andExpect(status().isCreated());
    postOk("/api/invoices/" + id + "/approve", null);
    return UUID.fromString(id);
  }

  private void postOk(String path, String body) throws Exception {
    var request = MockMvcRequestBuilders.post(path).with(TestJwtFactory.ownerJwt(ORG_ID, OWNER));
    if (body != null) {
      request = request.contentType(MediaType.APPLICATION_JSON).content(body);
    }
    mockMvc.perform(request).andExpect(status().isOk());
  }

  private AccountingSyncEntry savedEntry(SyncEntityType type, SyncDirection direction) {
    UUID entityId = UUID.randomUUID();
    UUID id =
        inTenant(
            () ->
                syncEntries
                    .save(
                        new AccountingSyncEntry(
                            type,
                            entityId,
                            "xero",
                            direction,
                            SyncTrigger.EVENT,
                            "KAZI-" + entityId))
                    .getId());
    // re-read so the "before" snapshot carries the stored (database-precision) timestamps
    return inTenant(() -> syncEntries.findOneById(id).orElseThrow());
  }

  private List<AccountingSyncEntry> entriesFor(SyncEntityType type, UUID entityId) {
    return inTenant(() -> syncEntries.findByEntity(type, entityId));
  }

  private void createConnectedXeroConnection() {
    var orgIntegration = new OrgIntegration(IntegrationDomain.ACCOUNTING, "xero");
    orgIntegration.enable();
    var savedIntegration = orgIntegrations.save(orgIntegration);
    xeroConnections.save(
        new AccountingXeroConnection(
            savedIntegration.getId(),
            "xero-tenant-" + UUID.randomUUID().toString().substring(0, 8),
            "Push Off Xero Org",
            UUID.randomUUID(),
            Instant.now().plus(30, ChronoUnit.MINUTES),
            "accounting.transactions openid profile email"));
  }

  private <T> T inTenant(Callable<T> action) {
    return RequestScopes.callForTenant(schema, ORG_ID, action);
  }
}
