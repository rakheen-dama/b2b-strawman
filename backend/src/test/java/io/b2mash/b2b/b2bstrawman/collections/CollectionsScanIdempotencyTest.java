package io.b2mash.b2b.b2bstrawman.collections;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.Customer;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.infrastructure.jobqueue.JobHandlerRegistry;
import io.b2mash.b2b.b2bstrawman.invoice.Invoice;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Idempotency + handler-registration tests (Phase 83, 589A.4 / 589A.5). A same-day re-run of the
 * scan for an invoice already actioned must create zero new activity rows and leave existing rows
 * untouched (the {@code (invoice, stage)} UNIQUE index plus the non-retryable status filter). Also
 * asserts {@link JobHandlerRegistry} resolves the {@code collections_scan} handler.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestPropertySource(properties = {"kazi.job-queue.enabled=true", "kazi.job-queue.auto-start=false"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CollectionsScanIdempotencyTest {

  private static final String ORG_ID = "org_collections_idem_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private CollectionsScanService scanService;
  @Autowired private CollectionActivityRepository activityRepository;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private InvoiceRepository invoiceRepository;
  @Autowired private OrgSettingsRepository orgSettingsRepository;
  @Autowired private JobHandlerRegistry jobHandlerRegistry;

  private String tenantSchema;
  private UUID memberId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Collections Idempotency Test Org", null);
    memberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, "user_coll_idem", "coll_idem@test.com", "Coll Idem", "owner"));
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
    runInTenant(
        () -> {
          var settings = orgSettingsRepository.findForCurrentTenant().orElseThrow();
          settings.getCollections().updateCollectionsSettings(true, 7, 21, 45, 60);
          orgSettingsRepository.save(settings);
        });
  }

  private void runInTenant(Runnable body) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(body);
  }

  private UUID seedSentInvoice(String name, int daysOverdue) {
    UUID[] holder = new UUID[1];
    runInTenant(
        () -> {
          Customer customer =
              TestCustomerFactory.createActiveCustomer(name, name + "@test.com", memberId);
          var savedCustomer = customerRepository.save(customer);
          var invoice =
              new Invoice(
                  savedCustomer.getId(),
                  "ZAR",
                  name,
                  name + "@test.com",
                  null,
                  "Test Org",
                  memberId);
          invoice.updateDraft(LocalDate.now().minusDays(daysOverdue), null, null, BigDecimal.ZERO);
          invoice.recalculateTotals(BigDecimal.valueOf(1500), false, BigDecimal.ZERO, false);
          invoice.approve("INV-" + UUID.randomUUID().toString().substring(0, 6), memberId);
          invoice.markSent();
          holder[0] = invoiceRepository.save(invoice).getId();
        });
    return holder[0];
  }

  @Test
  void sameDayRescanCreatesNoNewRowsAndLeavesExistingUntouched() {
    UUID invoiceId = seedSentInvoice("Idempotent Co", 30);

    runInTenant(scanService::scanForTenant);

    // Snapshot the (id, status, version) of every row for this invoice after the first scan.
    Map<UUID, String>[] before = new Map[1];
    runInTenant(
        () -> {
          before[0] = snapshot(activityRepository.findByInvoiceId(invoiceId));
        });

    // Re-run the SAME scan (same day) — no new rows, no version bumps for this invoice.
    runInTenant(scanService::scanForTenant);

    runInTenant(
        () -> {
          var after = snapshot(activityRepository.findByInvoiceId(invoiceId));
          assertThat(after).isEqualTo(before[0]);
        });
  }

  private static Map<UUID, String> snapshot(List<CollectionActivity> rows) {
    return rows.stream()
        .collect(
            java.util.stream.Collectors.toMap(
                CollectionActivity::getId,
                a -> a.getStatus() + ":" + a.getReason() + ":v" + a.getVersion()));
  }

  @Test
  void jobHandlerRegistryResolvesCollectionsScan() {
    assertThat(jobHandlerRegistry.getRegisteredTypes()).contains("collections_scan");
    assertThat(jobHandlerRegistry.getHandler("collections_scan"))
        .isInstanceOf(CollectionsScanHandler.class);
  }
}
