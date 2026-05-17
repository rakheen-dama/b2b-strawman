package io.b2mash.b2b.b2bstrawman.integration.accounting.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.integration.IntegrationDomain;
import io.b2mash.b2b.b2bstrawman.integration.OrgIntegration;
import io.b2mash.b2b.b2bstrawman.integration.OrgIntegrationRepository;
import io.b2mash.b2b.b2bstrawman.integration.accounting.xero.AccountingXeroConnection;
import io.b2mash.b2b.b2bstrawman.integration.accounting.xero.AccountingXeroConnectionRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AccountingSyncServiceTest {

  private static final String ORG_ID = "org_sync_service_test";

  @Autowired private AccountingSyncService syncService;
  @Autowired private AccountingSyncEntryRepository syncEntryRepository;
  @Autowired private AccountingXeroConnectionRepository xeroConnectionRepository;
  @Autowired private OrgIntegrationRepository orgIntegrationRepository;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;

  private String tenantSchema;

  @BeforeAll
  void setUp() {
    provisioningService.provisionTenant(ORG_ID, "Sync Service Test Org", null);
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

    // Create a connected Xero connection once for all tests that need it
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(this::createConnectedXeroConnection);
  }

  private void runInTenant(Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(action);
  }

  private void createConnectedXeroConnection() {
    var orgIntegration = new OrgIntegration(IntegrationDomain.ACCOUNTING, "xero");
    orgIntegration.enable();
    var savedIntegration = orgIntegrationRepository.save(orgIntegration);

    var connection =
        new AccountingXeroConnection(
            savedIntegration.getId(),
            "xero-tenant-" + UUID.randomUUID().toString().substring(0, 8),
            "Test Xero Org",
            UUID.randomUUID(),
            Instant.now().plus(30, ChronoUnit.MINUTES),
            "accounting.transactions openid profile email");
    xeroConnectionRepository.save(connection);
  }

  @Test
  void enqueueInvoicePush_createsPendingEntry() {
    runInTenant(
        () -> {
          var invoiceId = UUID.randomUUID();
          syncService.enqueueInvoicePush(invoiceId, SyncTrigger.EVENT);

          var entries = syncEntryRepository.findByEntity(SyncEntityType.INVOICE, invoiceId);
          assertThat(entries).hasSize(1);

          var entry = entries.getFirst();
          assertThat(entry.getState()).isEqualTo(SyncState.PENDING);
          assertThat(entry.getEntityType()).isEqualTo(SyncEntityType.INVOICE);
          assertThat(entry.getEntityId()).isEqualTo(invoiceId);
          assertThat(entry.getProviderId()).isEqualTo("xero");
          assertThat(entry.getDirection()).isEqualTo(SyncDirection.PUSH);
          assertThat(entry.getTrigger()).isEqualTo(SyncTrigger.EVENT);
          assertThat(entry.getExternalReference()).isEqualTo("KAZI-INV-" + invoiceId);
          assertThat(entry.getAttemptCount()).isZero();
        });
  }

  @Test
  void enqueueInvoicePush_idempotentWhenActiveEntryExists() {
    runInTenant(
        () -> {
          var invoiceId = UUID.randomUUID();
          syncService.enqueueInvoicePush(invoiceId, SyncTrigger.EVENT);
          syncService.enqueueInvoicePush(invoiceId, SyncTrigger.EVENT);

          var entries = syncEntryRepository.findByEntity(SyncEntityType.INVOICE, invoiceId);
          assertThat(entries).hasSize(1);
        });
  }

  @Test
  void enqueueInvoicePush_noOpWithoutConnectedXeroConnection() {
    // Use a fresh org with no Xero connection
    var freshOrg = "org_sync_no_conn_" + UUID.randomUUID().toString().substring(0, 6);
    provisioningService.provisionTenant(freshOrg, "No Connection Org", null);
    var freshTenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(freshOrg).orElseThrow().getSchemaName();

    ScopedValue.where(RequestScopes.TENANT_ID, freshTenantSchema)
        .where(RequestScopes.ORG_ID, freshOrg)
        .run(
            () -> {
              var invoiceId = UUID.randomUUID();
              syncService.enqueueInvoicePush(invoiceId, SyncTrigger.EVENT);

              var entries = syncEntryRepository.findByEntity(SyncEntityType.INVOICE, invoiceId);
              assertThat(entries).isEmpty();
            });
  }

  @Test
  void retryFromDeadLetter_resetsToPending() {
    runInTenant(
        () -> {
          // Create an entry and move it to DEAD_LETTER
          var invoiceId = UUID.randomUUID();
          var entry =
              new AccountingSyncEntry(
                  SyncEntityType.INVOICE,
                  invoiceId,
                  "xero",
                  SyncDirection.PUSH,
                  SyncTrigger.EVENT,
                  "KAZI-INV-" + invoiceId);
          entry.markInFlight();
          entry.markDeadLetter("TRANSIENT", "connection timeout");
          var saved = syncEntryRepository.save(entry);

          syncService.retryFromDeadLetter(saved.getId());

          var reloaded = syncEntryRepository.findOneById(saved.getId()).orElseThrow();
          assertThat(reloaded.getState()).isEqualTo(SyncState.PENDING);
          assertThat(reloaded.getAttemptCount()).isZero();
          assertThat(reloaded.getTrigger()).isEqualTo(SyncTrigger.MANUAL_RETRY);
          assertThat(reloaded.getLastErrorCode()).isNull();
          assertThat(reloaded.getLastErrorDetail()).isNull();
        });
  }

  @Test
  void retryFromDeadLetter_throwsForNonExistentEntry() {
    runInTenant(
        () -> {
          var randomId = UUID.randomUUID();
          assertThatThrownBy(() -> syncService.retryFromDeadLetter(randomId))
              .isInstanceOf(io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException.class);
        });
  }

  @Test
  void getSyncSummary_returnsCounts() {
    runInTenant(
        () -> {
          // Create some entries in various states
          var entry1 =
              new AccountingSyncEntry(
                  SyncEntityType.INVOICE,
                  UUID.randomUUID(),
                  "xero",
                  SyncDirection.PUSH,
                  SyncTrigger.EVENT,
                  "KAZI-INV-SUMMARY-1");
          syncEntryRepository.save(entry1);

          var entry2 =
              new AccountingSyncEntry(
                  SyncEntityType.CUSTOMER,
                  UUID.randomUUID(),
                  "xero",
                  SyncDirection.PUSH,
                  SyncTrigger.EVENT,
                  "KAZI-CUST-SUMMARY-1");
          entry2.markInFlight();
          entry2.markCompleted("XERO-123");
          syncEntryRepository.save(entry2);

          var summary = syncService.getSyncSummary();
          // At minimum we should have PENDING entries from this and other tests
          assertThat(summary).isNotEmpty();
          assertThat(summary.getOrDefault(SyncState.PENDING, 0L)).isGreaterThanOrEqualTo(1);
        });
  }
}
