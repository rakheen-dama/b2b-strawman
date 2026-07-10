package io.b2mash.b2b.b2bstrawman.collections;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.Customer;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
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
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Correctness tests for {@link CollectionsScanService} (Phase 83, 589A.4): stage-per-threshold
 * selection, highest-stage supersession, customer exemption (zero rows), blank-recipient skip +
 * in-place retry, the composer's retryable-skip outcome, and the disabled-policy no-op.
 * Entity-driven SENT-invoice seeding, embedded Postgres, ScopedValue tenant binding — no
 * Testcontainers. Since 590A the {@code @Primary} {@link AiReminderComposer} supersedes the 589
 * {@link NoOpReminderComposer}; this tenant has no AI firm profile, so every reminder lands as the
 * pre-flight {@code SKIPPED(ai_unavailable)} (retryable, like the old {@code draft_unavailable}).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CollectionsScanServiceTest {

  private static final String ORG_ID = "org_collections_scan_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private CollectionsScanService scanService;
  @Autowired private CollectionActivityRepository activityRepository;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private InvoiceRepository invoiceRepository;
  @Autowired private OrgSettingsRepository orgSettingsRepository;

  private String tenantSchema;
  private UUID memberId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Collections Scan Test Org", null);
    memberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, "user_coll_scan", "coll_scan@test.com", "Coll Scan", "owner"));
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
    setCollectionsEnabled(true);
  }

  private void runInTenant(Runnable body) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(body);
  }

  private void setCollectionsEnabled(boolean enabled) {
    runInTenant(
        () -> {
          var settings = orgSettingsRepository.findForCurrentTenant().orElseThrow();
          settings.getCollections().updateCollectionsSettings(enabled, 7, 21, 45, 60);
          orgSettingsRepository.save(settings);
        });
  }

  /** Seeds a SENT invoice overdue by {@code daysOverdue}, returning its id. */
  private UUID seedSentInvoice(String name, String email, int daysOverdue, boolean exempt) {
    UUID[] holder = new UUID[1];
    runInTenant(
        () -> {
          Customer customer =
              TestCustomerFactory.createActiveCustomer(
                  name, email != null ? email : name.replace(' ', '_') + "@test.com", memberId);
          customer.setCollectionsExempt(exempt);
          var savedCustomer = customerRepository.save(customer);

          var invoice =
              new Invoice(savedCustomer.getId(), "ZAR", name, email, null, "Test Org", memberId);
          invoice.updateDraft(LocalDate.now().minusDays(daysOverdue), null, null, BigDecimal.ZERO);
          invoice.recalculateTotals(BigDecimal.valueOf(1500), false, BigDecimal.ZERO, false);
          invoice.approve("INV-" + UUID.randomUUID().toString().substring(0, 6), memberId);
          invoice.markSent();
          holder[0] = invoiceRepository.save(invoice).getId();
        });
    return holder[0];
  }

  @Test
  void stageOneSelectedWhenOnlyStageOneThresholdCrossed() {
    UUID invoiceId = seedSentInvoice("Stage One Co", "stage1@test.com", 10, false);

    runInTenant(scanService::scanForTenant);

    runInTenant(
        () -> {
          var rows = activityRepository.findByInvoiceId(invoiceId);
          assertThat(rows).hasSize(1);
          var row = rows.get(0);
          assertThat(row.getStage()).isEqualTo(CollectionStage.STAGE_1);
          // 590A AI composer pre-flight, no firm profile in this tenant → ai_unavailable
          // (retryable).
          assertThat(row.getStatus()).isEqualTo(CollectionActivityStatus.SKIPPED);
          assertThat(row.getReason()).isEqualTo("ai_unavailable");
          assertThat(row.getDaysOverdueAtAction()).isEqualTo(10);
        });
  }

  @Test
  void highestStageSelected_lowerStagesRecordedSuperseded() {
    // 50d overdue with 7/21/45 thresholds → stage-3 is the target; stage-1 and stage-2 are
    // recorded SKIPPED(superseded_by_higher_stage) for ledger completeness.
    UUID invoiceId = seedSentInvoice("Supersede Co", "supersede@test.com", 50, false);

    runInTenant(scanService::scanForTenant);

    runInTenant(
        () -> {
          var stage1 =
              activityRepository.findByInvoiceIdAndStage(invoiceId, CollectionStage.STAGE_1);
          var stage2 =
              activityRepository.findByInvoiceIdAndStage(invoiceId, CollectionStage.STAGE_2);
          var stage3 =
              activityRepository.findByInvoiceIdAndStage(invoiceId, CollectionStage.STAGE_3);

          assertThat(stage1).isPresent();
          assertThat(stage1.get().getStatus()).isEqualTo(CollectionActivityStatus.SKIPPED);
          assertThat(stage1.get().getReason()).isEqualTo("superseded_by_higher_stage");

          assertThat(stage2).isPresent();
          assertThat(stage2.get().getStatus()).isEqualTo(CollectionActivityStatus.SKIPPED);
          assertThat(stage2.get().getReason()).isEqualTo("superseded_by_higher_stage");

          assertThat(stage3).isPresent();
          assertThat(stage3.get().getStatus()).isEqualTo(CollectionActivityStatus.SKIPPED);
          assertThat(stage3.get().getReason()).isEqualTo("ai_unavailable");
        });
  }

  @Test
  void exemptCustomerProducesZeroRows() {
    UUID invoiceId = seedSentInvoice("Exempt Co", "exempt@test.com", 50, true);

    runInTenant(scanService::scanForTenant);

    runInTenant(() -> assertThat(activityRepository.findByInvoiceId(invoiceId)).isEmpty());
  }

  @Test
  void blankRecipientSkipped_thenRetriedInPlaceAfterEmailAdded() {
    // Invoice with no customer_email snapshot → SKIPPED(no_recipient).
    UUID invoiceId = seedSentInvoice("No Email Co", null, 10, false);

    runInTenant(scanService::scanForTenant);

    UUID[] rowId = new UUID[1];
    runInTenant(
        () -> {
          var rows = activityRepository.findByInvoiceId(invoiceId);
          assertThat(rows).hasSize(1);
          var row = rows.get(0);
          assertThat(row.getStatus()).isEqualTo(CollectionActivityStatus.SKIPPED);
          assertThat(row.getReason()).isEqualTo("no_recipient");
          rowId[0] = row.getId();
        });

    // Add an email to the invoice snapshot, re-scan → the SAME row transitions in place.
    runInTenant(
        () -> {
          var invoice = invoiceRepository.findById(invoiceId).orElseThrow();
          invoice.setCustomerEmail("nowhasemail@test.com");
          invoiceRepository.save(invoice);
        });

    runInTenant(scanService::scanForTenant);

    runInTenant(
        () -> {
          var rows = activityRepository.findByInvoiceId(invoiceId);
          assertThat(rows).hasSize(1);
          var row = rows.get(0);
          // Same row id, reason changed from no_recipient → ai_unavailable (590A composer
          // pre-flight — no firm profile in this tenant).
          assertThat(row.getId()).isEqualTo(rowId[0]);
          assertThat(row.getStatus()).isEqualTo(CollectionActivityStatus.SKIPPED);
          assertThat(row.getReason()).isEqualTo("ai_unavailable");
        });
  }

  @Test
  void disabledPolicyIsANoOp() {
    UUID invoiceId = seedSentInvoice("Disabled Policy Co", "disabled@test.com", 30, false);
    setCollectionsEnabled(false);
    try {
      runInTenant(scanService::scanForTenant);
      runInTenant(() -> assertThat(activityRepository.findByInvoiceId(invoiceId)).isEmpty());
    } finally {
      setCollectionsEnabled(true);
    }
  }
}
