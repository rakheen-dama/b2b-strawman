package io.b2mash.b2b.b2bstrawman.integration.accounting.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.integration.IntegrationDomain;
import io.b2mash.b2b.b2bstrawman.integration.IntegrationRegistry;
import io.b2mash.b2b.b2bstrawman.integration.OrgIntegration;
import io.b2mash.b2b.b2bstrawman.integration.OrgIntegrationRepository;
import io.b2mash.b2b.b2bstrawman.integration.accounting.AccountingProvider;
import io.b2mash.b2b.b2bstrawman.integration.accounting.AccountingSyncResult;
import io.b2mash.b2b.b2bstrawman.integration.accounting.CustomerSyncRequest;
import io.b2mash.b2b.b2bstrawman.integration.accounting.InvoiceSyncRequest;
import io.b2mash.b2b.b2bstrawman.integration.accounting.xero.AccountingXeroConnection;
import io.b2mash.b2b.b2bstrawman.integration.accounting.xero.AccountingXeroConnectionRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import java.time.Duration;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AccountingSyncWorkerTest {

  private static final String ORG_ID = "org_sync_worker_test";

  @MockitoBean private IntegrationRegistry integrationRegistry;

  @MockitoBean(name = "noOpAccountingProvider")
  private AccountingProvider accountingProvider;

  @Autowired private AccountingSyncWorker syncWorker;
  @Autowired private AccountingSyncEntryRepository syncEntryRepository;
  @Autowired private AccountingXeroConnectionRepository xeroConnectionRepository;
  @Autowired private OrgIntegrationRepository orgIntegrationRepository;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;

  private String tenantSchema;

  @BeforeAll
  void setUp() {
    provisioningService.provisionTenant(ORG_ID, "Sync Worker Test Org", null);
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

    // Create a connected Xero connection once for all tests
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
            "Worker Test Xero Org",
            UUID.randomUUID(),
            Instant.now().plus(30, ChronoUnit.MINUTES),
            "accounting.transactions openid profile email");
    xeroConnectionRepository.save(connection);
  }

  @Test
  void workerDrain_marksEntryCompletedOnSuccess() {
    runInTenant(
        () -> {
          when(integrationRegistry.resolve(IntegrationDomain.ACCOUNTING, AccountingProvider.class))
              .thenReturn(accountingProvider);
          when(accountingProvider.syncInvoice(any(InvoiceSyncRequest.class)))
              .thenReturn(new AccountingSyncResult(true, "XERO-INV-001", null));

          var invoiceId = UUID.randomUUID();
          var entry =
              new AccountingSyncEntry(
                  SyncEntityType.INVOICE,
                  invoiceId,
                  "xero",
                  SyncDirection.PUSH,
                  SyncTrigger.EVENT,
                  "KAZI-INV-" + invoiceId);
          syncEntryRepository.save(entry);

          syncWorker.drainForTenant();

          var processed = syncEntryRepository.findByEntity(SyncEntityType.INVOICE, invoiceId);
          assertThat(processed).hasSize(1);
          assertThat(processed.getFirst().getState()).isEqualTo(SyncState.COMPLETED);
          assertThat(processed.getFirst().getExternalId()).isEqualTo("XERO-INV-001");
        });
  }

  @Test
  void workerDrain_retriesWithBackoffOnTransientError() {
    runInTenant(
        () -> {
          when(integrationRegistry.resolve(IntegrationDomain.ACCOUNTING, AccountingProvider.class))
              .thenReturn(accountingProvider);
          when(accountingProvider.syncInvoice(any(InvoiceSyncRequest.class)))
              .thenReturn(new AccountingSyncResult(false, null, "connection timeout"));

          var invoiceId = UUID.randomUUID();
          var entry =
              new AccountingSyncEntry(
                  SyncEntityType.INVOICE,
                  invoiceId,
                  "xero",
                  SyncDirection.PUSH,
                  SyncTrigger.EVENT,
                  "KAZI-INV-" + invoiceId);
          syncEntryRepository.save(entry);

          syncWorker.drainForTenant();

          var processed = syncEntryRepository.findByEntity(SyncEntityType.INVOICE, invoiceId);
          assertThat(processed).hasSize(1);

          var updated = processed.getFirst();
          assertThat(updated.getState()).isEqualTo(SyncState.FAILED_RETRYING);
          assertThat(updated.getAttemptCount()).isEqualTo(1);
          assertThat(updated.getLastErrorCode()).isEqualTo("TRANSIENT");
          assertThat(updated.getNextAttemptAt()).isNotNull();
          // First attempt back-off is 1 minute
          assertThat(updated.getNextAttemptAt())
              .isAfter(Instant.now().minus(1, ChronoUnit.SECONDS))
              .isBefore(Instant.now().plus(2, ChronoUnit.MINUTES));
        });
  }

  @Test
  void workerDrain_deadLettersAfterMaxAttempts() {
    runInTenant(
        () -> {
          when(integrationRegistry.resolve(IntegrationDomain.ACCOUNTING, AccountingProvider.class))
              .thenReturn(accountingProvider);
          when(accountingProvider.syncInvoice(any(InvoiceSyncRequest.class)))
              .thenReturn(new AccountingSyncResult(false, null, "connection timeout"));

          var invoiceId = UUID.randomUUID();
          var entry =
              new AccountingSyncEntry(
                  SyncEntityType.INVOICE,
                  invoiceId,
                  "xero",
                  SyncDirection.PUSH,
                  SyncTrigger.EVENT,
                  "KAZI-INV-" + invoiceId);
          // Simulate 4 prior failed attempts
          entry.markInFlight();
          entry.markFailedRetrying(
              "TRANSIENT", "timeout", Instant.now().minus(1, ChronoUnit.HOURS));
          entry.markInFlight();
          entry.markFailedRetrying(
              "TRANSIENT", "timeout", Instant.now().minus(1, ChronoUnit.HOURS));
          entry.markInFlight();
          entry.markFailedRetrying(
              "TRANSIENT", "timeout", Instant.now().minus(1, ChronoUnit.HOURS));
          entry.markInFlight();
          entry.markFailedRetrying(
              "TRANSIENT", "timeout", Instant.now().minus(1, ChronoUnit.MINUTES));
          syncEntryRepository.save(entry);

          syncWorker.drainForTenant();

          var processed = syncEntryRepository.findByEntity(SyncEntityType.INVOICE, invoiceId);
          assertThat(processed).hasSize(1);
          assertThat(processed.getFirst().getState()).isEqualTo(SyncState.DEAD_LETTER);
          assertThat(processed.getFirst().getLastErrorCode()).isEqualTo("TRANSIENT");
        });
  }

  @Test
  void workerDrain_validationErrorGoesDirectlyToDeadLetter() {
    runInTenant(
        () -> {
          when(integrationRegistry.resolve(IntegrationDomain.ACCOUNTING, AccountingProvider.class))
              .thenReturn(accountingProvider);
          when(accountingProvider.syncCustomer(any(CustomerSyncRequest.class)))
              .thenReturn(
                  new AccountingSyncResult(false, null, "validation error: email is required"));

          var customerId = UUID.randomUUID();
          var entry =
              new AccountingSyncEntry(
                  SyncEntityType.CUSTOMER,
                  customerId,
                  "xero",
                  SyncDirection.PUSH,
                  SyncTrigger.EVENT,
                  "KAZI-CUST-" + customerId);
          syncEntryRepository.save(entry);

          syncWorker.drainForTenant();

          var processed = syncEntryRepository.findByEntity(SyncEntityType.CUSTOMER, customerId);
          assertThat(processed).hasSize(1);
          assertThat(processed.getFirst().getState()).isEqualTo(SyncState.DEAD_LETTER);
          assertThat(processed.getFirst().getLastErrorCode()).isEqualTo("VALIDATION_FAILED");
        });
  }

  @Test
  void backoffSchedule_hasCorrectDurations() {
    Duration[] schedule = AccountingSyncWorker.getBackoffSchedule();
    assertThat(schedule).hasSize(5);
    assertThat(schedule[0]).isEqualTo(Duration.ofMinutes(1));
    assertThat(schedule[1]).isEqualTo(Duration.ofMinutes(5));
    assertThat(schedule[2]).isEqualTo(Duration.ofMinutes(15));
    assertThat(schedule[3]).isEqualTo(Duration.ofHours(1));
    assertThat(schedule[4]).isEqualTo(Duration.ofHours(6));
  }
}
