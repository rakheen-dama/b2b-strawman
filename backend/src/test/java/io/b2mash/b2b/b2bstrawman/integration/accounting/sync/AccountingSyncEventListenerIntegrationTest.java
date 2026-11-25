package io.b2mash.b2b.b2bstrawman.integration.accounting.sync;

import static org.assertj.core.api.Assertions.assertThat;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * The event route into the Xero push queue (F-658A-1): approving, sending or voiding an ordinary
 * fee note through the HTTP API — and creating or updating a client — must leave a PENDING {@code
 * accounting_sync_entry} in the firm's own schema when Xero is CONNECTED. {@link
 * AccountingSyncServiceTest} calls the service directly, so it never exercised the AFTER_COMMIT
 * listener ({@link AccountingSyncEventListener}). Asserts by entity id, never by global counts.
 *
 * <p>Pushes are off by default ({@code kazi.accounting.push.enabled}, PR #1860 ruling) until the
 * worker sends real payloads; {@link AccountingPushDisabledIntegrationTest} covers the default.
 * This class is about the enabled route, so it turns pushes on. A class-level property is the
 * narrowest mechanism here: the value genuinely differs from the suite default for this class
 * (backend/CLAUDE.md), and {@link AccountingSyncWorkerTest} uses the same single property.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestPropertySource(properties = "kazi.accounting.push.enabled=true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AccountingSyncEventListenerIntegrationTest {

  private static final String ORG_ID = "org_sync_listener_test";
  private static final String OWNER = "user_sync_listener_owner";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private CustomerRepository customers;
  @Autowired private AccountingSyncEntryRepository syncEntries;
  @Autowired private OrgIntegrationRepository orgIntegrations;
  @Autowired private AccountingXeroConnectionRepository xeroConnections;

  private String schema;
  private UUID clientId;

  @BeforeAll
  void setup() throws Exception {
    schema =
        provisioningService
            .provisionTenant(ORG_ID, "Sync Listener Attorneys", "legal-za")
            .schemaName();
    UUID ownerId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, OWNER, "sync_listener_owner@test.com", "Listener Owner", "owner"));
    clientId =
        inTenant(
            () ->
                customers
                    .save(
                        TestCustomerFactory.createActiveCustomerWithPrerequisiteFields(
                            "Lindiwe Dube", "sync.listener.client@example.test", ownerId))
                    .getId());
    inTenant(
        () -> {
          createConnectedXeroConnection();
          return null;
        });
  }

  @Test
  void approvingAnOrdinaryFeeNote_queuesAPendingXeroPush_inTheFirmsSchema() throws Exception {
    UUID id = approvedFeeNote();

    assertThat(entriesFor(SyncEntityType.INVOICE, id))
        .singleElement()
        .satisfies(
            e -> {
              assertThat(e.getState()).isEqualTo(SyncState.PENDING);
              assertThat(e.getTrigger()).isEqualTo(SyncTrigger.EVENT);
              assertThat(e.getDirection()).isEqualTo(SyncDirection.PUSH);
              assertThat(e.getExternalReference()).isEqualTo("KAZI-INV-" + id);
            });
    // landed in this firm's schema, not anywhere else
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM \"%s\".accounting_sync_entry WHERE entity_id = ?"
                    .formatted(schema),
                Long.class,
                id))
        .isEqualTo(1L);
  }

  @Test
  void sendingAnOrdinaryFeeNote_queuesANewPendingXeroPush() throws Exception {
    UUID id = approvedFeeNote();
    completeActiveEntries(SyncEntityType.INVOICE, id); // the approval's push went out

    postOk("/api/invoices/" + id + "/send", "{\"overrideWarnings\": true}");

    assertThat(entriesFor(SyncEntityType.INVOICE, id))
        .filteredOn(e -> e.getState() == SyncState.PENDING)
        .singleElement()
        .satisfies(e -> assertThat(e.getTrigger()).isEqualTo(SyncTrigger.EVENT));
  }

  @Test
  void voidingAnOrdinaryFeeNote_queuesANewPendingXeroPush() throws Exception {
    UUID id = approvedFeeNote();
    postOk("/api/invoices/" + id + "/send", "{\"overrideWarnings\": true}");
    completeActiveEntries(SyncEntityType.INVOICE, id); // approval / send pushes went out

    postOk("/api/invoices/" + id + "/void", null);

    assertThat(entriesFor(SyncEntityType.INVOICE, id))
        .filteredOn(e -> e.getState() == SyncState.PENDING)
        .singleElement()
        .satisfies(e -> assertThat(e.getTrigger()).isEqualTo(SyncTrigger.EVENT));
  }

  @Test
  void sendingWhileTheApprovalPushIsStillPending_doesNotQueueASecondEntry() throws Exception {
    UUID id = approvedFeeNote();

    postOk("/api/invoices/" + id + "/send", "{\"overrideWarnings\": true}");

    assertThat(entriesFor(SyncEntityType.INVOICE, id))
        .singleElement()
        .satisfies(e -> assertThat(e.getState()).isEqualTo(SyncState.PENDING));
  }

  @Test
  void creatingAClient_queuesAPendingXeroPush() throws Exception {
    UUID customerId =
        UUID.fromString(
            TestEntityHelper.createCustomer(
                mockMvc,
                TestJwtFactory.ownerJwt(ORG_ID, OWNER),
                "Sipho Mokoena",
                "sync.listener.created@example.test"));

    assertThat(entriesFor(SyncEntityType.CUSTOMER, customerId))
        .singleElement()
        .satisfies(
            e -> {
              assertThat(e.getState()).isEqualTo(SyncState.PENDING);
              assertThat(e.getTrigger()).isEqualTo(SyncTrigger.EVENT);
            });
  }

  @Test
  void updatingAClient_queuesANewPendingXeroPush() throws Exception {
    UUID customerId =
        UUID.fromString(
            TestEntityHelper.createCustomer(
                mockMvc,
                TestJwtFactory.ownerJwt(ORG_ID, OWNER),
                "Zanele Khumalo",
                "sync.listener.updated@example.test"));
    completeActiveEntries(SyncEntityType.CUSTOMER, customerId); // the create's push went out

    mockMvc
        .perform(
            MockMvcRequestBuilders.put("/api/clients/" + customerId)
                .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"name\": \"Zanele Khumalo-Dlamini\","
                        + " \"email\": \"sync.listener.updated@example.test\"}"))
        .andExpect(status().isOk());

    assertThat(entriesFor(SyncEntityType.CUSTOMER, customerId))
        .filteredOn(e -> e.getState() == SyncState.PENDING)
        .singleElement()
        .satisfies(e -> assertThat(e.getTrigger()).isEqualTo(SyncTrigger.EVENT));
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

  private List<AccountingSyncEntry> entriesFor(SyncEntityType type, UUID entityId) {
    return inTenant(() -> syncEntries.findByEntity(type, entityId));
  }

  /** Stands in for the worker having pushed every queued entry for the entity. */
  private void completeActiveEntries(SyncEntityType type, UUID entityId) {
    inTenant(
        () -> {
          syncEntries
              .findActiveEntryForEntity(type, entityId)
              .ifPresent(
                  e -> {
                    e.markCompleted("xero-" + entityId);
                    syncEntries.save(e);
                  });
          return null;
        });
  }

  private void createConnectedXeroConnection() {
    var orgIntegration = new OrgIntegration(IntegrationDomain.ACCOUNTING, "xero");
    orgIntegration.enable();
    var savedIntegration = orgIntegrations.save(orgIntegration);
    xeroConnections.save(
        new AccountingXeroConnection(
            savedIntegration.getId(),
            "xero-tenant-" + UUID.randomUUID().toString().substring(0, 8),
            "Listener Xero Org",
            UUID.randomUUID(),
            Instant.now().plus(30, ChronoUnit.MINUTES),
            "accounting.transactions openid profile email"));
  }

  private <T> T inTenant(Callable<T> action) {
    return RequestScopes.callForTenant(schema, ORG_ID, action);
  }
}
