package io.b2mash.b2b.b2bstrawman.integration.accounting;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.integration.IntegrationDomain;
import io.b2mash.b2b.b2bstrawman.integration.OrgIntegration;
import io.b2mash.b2b.b2bstrawman.integration.OrgIntegrationRepository;
import io.b2mash.b2b.b2bstrawman.integration.accounting.sync.AccountingSyncEntry;
import io.b2mash.b2b.b2bstrawman.integration.accounting.sync.AccountingSyncEntryRepository;
import io.b2mash.b2b.b2bstrawman.integration.accounting.sync.SyncDirection;
import io.b2mash.b2b.b2bstrawman.integration.accounting.sync.SyncEntityType;
import io.b2mash.b2b.b2bstrawman.integration.accounting.sync.SyncTrigger;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AccountingSyncEntryRepositoryTest {

  private static final String ORG_ID = "org_sync_entry_test";

  @Autowired private AccountingSyncEntryRepository syncEntryRepository;
  @Autowired private AccountingXeroConnectionRepository xeroConnectionRepository;
  @Autowired private OrgIntegrationRepository orgIntegrationRepository;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;

  private String tenantSchema;

  @BeforeAll
  void setup() {
    provisioningService.provisionTenant(ORG_ID, "Sync Entry Test Org", null);
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
  }

  private void runInTenant(Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(action);
  }

  @Test
  void persist_roundTrip() {
    runInTenant(
        () -> {
          var entityId = UUID.randomUUID();
          var entry =
              new AccountingSyncEntry(
                  SyncEntityType.INVOICE,
                  entityId,
                  "xero",
                  SyncDirection.PUSH,
                  SyncTrigger.EVENT,
                  "INV-001");

          var saved = syncEntryRepository.save(entry);
          assertThat(saved.getId()).isNotNull();
          assertThat(saved.getCreatedAt()).isNotNull();

          var loaded = syncEntryRepository.findOneById(saved.getId()).orElseThrow();
          assertThat(loaded.getEntityType()).isEqualTo(SyncEntityType.INVOICE);
          assertThat(loaded.getEntityId()).isEqualTo(entityId);
          assertThat(loaded.getProviderId()).isEqualTo("xero");
          assertThat(loaded.getDirection()).isEqualTo(SyncDirection.PUSH);
          assertThat(loaded.getTrigger()).isEqualTo(SyncTrigger.EVENT);
          assertThat(loaded.getExternalReference()).isEqualTo("INV-001");
          assertThat(loaded.getState())
              .isEqualTo(io.b2mash.b2b.b2bstrawman.integration.accounting.sync.SyncState.PENDING);
        });
  }

  @Test
  void findDrainableEntries_returnsPendingEntriesBeforeNow() {
    runInTenant(
        () -> {
          var entry1 =
              new AccountingSyncEntry(
                  SyncEntityType.INVOICE,
                  UUID.randomUUID(),
                  "xero",
                  SyncDirection.PUSH,
                  SyncTrigger.EVENT,
                  "INV-DRAIN-1");
          syncEntryRepository.save(entry1);

          var entry2 =
              new AccountingSyncEntry(
                  SyncEntityType.CUSTOMER,
                  UUID.randomUUID(),
                  "xero",
                  SyncDirection.PUSH,
                  SyncTrigger.EVENT,
                  null);
          syncEntryRepository.save(entry2);

          var results =
              syncEntryRepository.findDrainableEntries(
                  Instant.now().plus(1, ChronoUnit.MINUTES), PageRequest.of(0, 10));

          assertThat(results).hasSizeGreaterThanOrEqualTo(2);
        });
  }

  @Test
  void findByEntity_returnsEntriesForEntityOrderedByCreatedAt() {
    runInTenant(
        () -> {
          var entityId = UUID.randomUUID();

          var entry1 =
              new AccountingSyncEntry(
                  SyncEntityType.INVOICE,
                  entityId,
                  "xero",
                  SyncDirection.PUSH,
                  SyncTrigger.EVENT,
                  "INV-LOOKUP-1");
          syncEntryRepository.save(entry1);

          var entry2 =
              new AccountingSyncEntry(
                  SyncEntityType.INVOICE,
                  entityId,
                  "xero",
                  SyncDirection.PUSH,
                  SyncTrigger.MANUAL_RETRY,
                  "INV-LOOKUP-1");
          syncEntryRepository.save(entry2);

          var results = syncEntryRepository.findByEntity(SyncEntityType.INVOICE, entityId);

          assertThat(results).hasSize(2);
          // Ordered by createdAt DESC — newest first
          assertThat(results.get(0).getTrigger()).isEqualTo(SyncTrigger.MANUAL_RETRY);
        });
  }

  @Test
  void findCompletedPushByExternalReference_matchesCompletedEntry() {
    runInTenant(
        () -> {
          var entry =
              new AccountingSyncEntry(
                  SyncEntityType.INVOICE,
                  UUID.randomUUID(),
                  "xero",
                  SyncDirection.PUSH,
                  SyncTrigger.EVENT,
                  "INV-EXT-REF-001");
          entry.markInFlight();
          entry.markCompleted("XERO-12345");
          syncEntryRepository.save(entry);

          var found = syncEntryRepository.findCompletedPushByExternalReference("INV-EXT-REF-001");
          assertThat(found).isPresent();
          assertThat(found.get().getExternalId()).isEqualTo("XERO-12345");
          assertThat(found.get().getExternalReference()).isEqualTo("INV-EXT-REF-001");
        });
  }

  @Test
  void xeroConnection_persistAndFindByOrgIntegrationId() {
    runInTenant(
        () -> {
          // Create OrgIntegration prerequisite
          var orgIntegration = new OrgIntegration(IntegrationDomain.ACCOUNTING, "xero");
          var savedIntegration = orgIntegrationRepository.save(orgIntegration);

          var connection =
              new AccountingXeroConnection(
                  savedIntegration.getId(),
                  "xero-tenant-123",
                  "Test Xero Org",
                  UUID.randomUUID(),
                  Instant.now().plus(30, ChronoUnit.MINUTES),
                  "accounting.transactions openid profile email");

          var saved = xeroConnectionRepository.save(connection);
          assertThat(saved.getId()).isNotNull();
          assertThat(saved.getCreatedAt()).isNotNull();

          var found = xeroConnectionRepository.findByOrgIntegrationId(savedIntegration.getId());
          assertThat(found).isPresent();
          assertThat(found.get().getXeroTenantId()).isEqualTo("xero-tenant-123");
          assertThat(found.get().getXeroOrgName()).isEqualTo("Test Xero Org");
          assertThat(found.get().getScope())
              .isEqualTo("accounting.transactions openid profile email");
        });
  }
}
