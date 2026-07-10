package io.b2mash.b2b.b2bstrawman.collections;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.Customer;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.integration.ai.AiCompletionResponse;
import io.b2mash.b2b.b2bstrawman.integration.ai.execution.AiExecution;
import io.b2mash.b2b.b2bstrawman.integration.ai.execution.AiExecutionRepository;
import io.b2mash.b2b.b2bstrawman.integration.ai.gate.AiExecutionGate;
import io.b2mash.b2b.b2bstrawman.integration.ai.gate.AiExecutionGateRepository;
import io.b2mash.b2b.b2bstrawman.integration.ai.gate.AiExecutionGateService;
import io.b2mash.b2b.b2bstrawman.invoice.Invoice;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
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
import org.springframework.test.web.servlet.MockMvc;

/**
 * Gate reject/expiry transition tests (Phase 83, 589B.3), driving the scan + {@link
 * CollectionsPaymentListener} together — the first end-to-end proof of the retryable loop.
 *
 * <ul>
 *   <li>Reject → activity {@code REJECTED} (terminal for the stage); the next scan does NOT
 *       re-propose that stage.
 *   <li>Expiry → activity {@code SKIPPED(gate_expired)} (retryable; gateId retained); the next scan
 *       re-evaluates the row in place (landing as {@code SKIPPED(ai_unavailable)} with the 590A
 *       no-op composer), proving the retry loop fired.
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GateLifecycleTransitionsTest {

  private static final String ORG_ID = "org_collections_gatelifecycle_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private CollectionsScanService scanService;
  @Autowired private CollectionActivityRepository activityRepository;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private InvoiceRepository invoiceRepository;
  @Autowired private OrgSettingsRepository orgSettingsRepository;
  @Autowired private AiExecutionRepository executionRepository;
  @Autowired private AiExecutionGateRepository gateRepository;
  @Autowired private AiExecutionGateService gateService;

  private String tenantSchema;
  private UUID ownerMemberId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Collections Gate Lifecycle Test Org", null);
    ownerMemberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, "user_gate_life", "gate_life@test.com", "Gate Life", "owner"));
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
        .where(RequestScopes.MEMBER_ID, ownerMemberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(body);
  }

  private AiExecution createExecution() {
    var execution =
        new AiExecution(
            "collection-reminder",
            "collection_activity",
            UUID.randomUUID(),
            ownerMemberId,
            "claude-sonnet-4-6",
            1);
    execution.markCompleted(
        new AiCompletionResponse(
            "output", "claude-sonnet-4-6", 2000, 800, 1500, 0, "end_turn", 5000L),
        4250L);
    return executionRepository.save(execution);
  }

  private UUID seedSentInvoice(String name, int daysOverdue) {
    UUID[] holder = new UUID[1];
    runInTenant(
        () -> {
          Customer customer =
              TestCustomerFactory.createActiveCustomer(name, name + "@test.com", ownerMemberId);
          var savedCustomer = customerRepository.save(customer);
          var invoice =
              new Invoice(
                  savedCustomer.getId(),
                  "ZAR",
                  name,
                  name + "@test.com",
                  null,
                  "Test Org",
                  ownerMemberId);
          invoice.updateDraft(LocalDate.now().minusDays(daysOverdue), null, null, BigDecimal.ZERO);
          invoice.recalculateTotals(BigDecimal.valueOf(1500), false, BigDecimal.ZERO, false);
          invoice.approve("INV-" + UUID.randomUUID().toString().substring(0, 6), ownerMemberId);
          invoice.markSent();
          holder[0] = invoiceRepository.save(invoice).getId();
        });
    return holder[0];
  }

  /**
   * Seeds a PROPOSED STAGE_1 activity + gate; returns the gate id. Gate expires at {@code
   * expiresAt}.
   */
  private UUID seedProposedActivityWithGate(UUID invoiceId, UUID customerId, Instant expiresAt) {
    UUID[] gateId = new UUID[1];
    runInTenant(
        () -> {
          var execution = createExecution();
          var gate =
              gateRepository.save(
                  new AiExecutionGate(
                      execution,
                      CollectionsPaymentListener.GATE_TYPE_SEND_COLLECTION_REMINDER,
                      Map.of("invoice_id", invoiceId.toString()),
                      "draft reasoning",
                      expiresAt));
          var activity =
              new CollectionActivity(
                  invoiceId,
                  customerId,
                  CollectionStage.STAGE_1,
                  CollectionActivityStatus.PROPOSED,
                  10,
                  null);
          activity.markProposed(gate.getId(), 10);
          activityRepository.saveAndFlush(activity);
          gateId[0] = gate.getId();
        });
    return gateId[0];
  }

  private UUID customerIdFor(UUID invoiceId) {
    UUID[] holder = new UUID[1];
    runInTenant(
        () -> holder[0] = invoiceRepository.findById(invoiceId).orElseThrow().getCustomerId());
    return holder[0];
  }

  @Test
  void reject_marksActivityRejected_andScanDoesNotRepropose() {
    UUID invoiceId = seedSentInvoice("Reject Co", 10);
    UUID customerId = customerIdFor(invoiceId);
    UUID gateId =
        seedProposedActivityWithGate(
            invoiceId, customerId, Instant.now().plus(Duration.ofHours(72)));

    runInTenant(() -> gateService.reject(gateId, ownerMemberId, "not chasing"));

    runInTenant(
        () -> {
          var row =
              activityRepository
                  .findByInvoiceIdAndStage(invoiceId, CollectionStage.STAGE_1)
                  .orElseThrow();
          assertThat(row.getStatus()).isEqualTo(CollectionActivityStatus.REJECTED);
        });

    // Next scan must NOT re-propose the rejected stage (REJECTED is non-retryable).
    runInTenant(scanService::scanForTenant);

    runInTenant(
        () -> {
          var row =
              activityRepository
                  .findByInvoiceIdAndStage(invoiceId, CollectionStage.STAGE_1)
                  .orElseThrow();
          assertThat(row.getStatus()).isEqualTo(CollectionActivityStatus.REJECTED);
        });
  }

  @Test
  void expiry_marksSkippedGateExpired_andScanReEvaluatesInPlace() {
    UUID invoiceId = seedSentInvoice("Expiry Co", 10);
    UUID customerId = customerIdFor(invoiceId);
    // Gate already expired → the sweep will expire it and fire AiGateExpiredEvent.
    UUID gateId =
        seedProposedActivityWithGate(
            invoiceId, customerId, Instant.now().minus(Duration.ofHours(1)));

    // Dual-mode inline sweep (test profile) expires the stale gate and fires the event.
    runInTenant(() -> gateService.expireStaleGates());

    UUID[] rowId = new UUID[1];
    runInTenant(
        () -> {
          var row =
              activityRepository
                  .findByInvoiceIdAndStage(invoiceId, CollectionStage.STAGE_1)
                  .orElseThrow();
          assertThat(row.getStatus()).isEqualTo(CollectionActivityStatus.SKIPPED);
          assertThat(row.getReason()).isEqualTo("gate_expired");
          // gateId retained per §2.2.
          assertThat(row.getGateId()).isEqualTo(gateId);
          rowId[0] = row.getId();
        });

    // Next scan re-evaluates the retryable row in place → SKIPPED(ai_unavailable) (590A AI
    // composer pre-flight — no firm profile in this tenant).
    runInTenant(scanService::scanForTenant);

    runInTenant(
        () -> {
          var row =
              activityRepository
                  .findByInvoiceIdAndStage(invoiceId, CollectionStage.STAGE_1)
                  .orElseThrow();
          assertThat(row.getId()).isEqualTo(rowId[0]);
          assertThat(row.getStatus()).isEqualTo(CollectionActivityStatus.SKIPPED);
          assertThat(row.getReason()).isEqualTo("ai_unavailable");
        });
  }

  /**
   * Review-fix regression (Epic 589): a lower stage left {@code PROPOSED} from an earlier scan (a
   * live, un-reviewed gate) must be superseded in place when a later scan targets a higher stage —
   * its stale PENDING gate expired — so the client can never hold two concurrently-PROPOSED gates
   * for one invoice. Before the fix the supersede loop only wrote SKIPPED rows for lower stages
   * with NO existing row; a lingering PROPOSED lower stage kept its live gate.
   */
  @Test
  void supersede_expiresStalePendingLowerStageGate_whenHigherStageBecomesEligible() {
    // 30d overdue crosses stage1(7) + stage2(21) → highest eligible = STAGE_2.
    UUID invoiceId = seedSentInvoice("Supersede Co", 30);
    UUID customerId = customerIdFor(invoiceId);
    // A stale STAGE_1 reminder left PROPOSED from an earlier scan, its gate still PENDING.
    UUID staleGateId =
        seedProposedActivityWithGate(
            invoiceId, customerId, Instant.now().plus(Duration.ofHours(72)));

    runInTenant(scanService::scanForTenant);

    runInTenant(
        () -> {
          // STAGE_1 superseded in place: SKIPPED(superseded_by_higher_stage), gateId retained.
          var stage1 =
              activityRepository
                  .findByInvoiceIdAndStage(invoiceId, CollectionStage.STAGE_1)
                  .orElseThrow();
          assertThat(stage1.getStatus()).isEqualTo(CollectionActivityStatus.SKIPPED);
          assertThat(stage1.getReason()).isEqualTo("superseded_by_higher_stage");
          assertThat(stage1.getGateId()).isEqualTo(staleGateId);

          // Its stale PENDING gate is now EXPIRED — the stale reminder can never be sent.
          var staleGate = gateRepository.findById(staleGateId).orElseThrow();
          assertThat(staleGate.getStatus()).isEqualTo("EXPIRED");

          // The higher target stage exists (SKIPPED(ai_unavailable) under the 590A composer
          // pre-flight — no firm profile in this tenant).
          var stage2 =
              activityRepository
                  .findByInvoiceIdAndStage(invoiceId, CollectionStage.STAGE_2)
                  .orElseThrow();
          assertThat(stage2.getStatus()).isEqualTo(CollectionActivityStatus.SKIPPED);
          assertThat(stage2.getReason()).isEqualTo("ai_unavailable");

          // Invariant: no activity for this invoice is left PROPOSED — at most one active reminder
          // (here zero, the no-op composer proposes none).
          assertThat(
                  activityRepository.findByInvoiceIdAndStatus(
                      invoiceId, CollectionActivityStatus.PROPOSED))
              .isEmpty();
        });
  }
}
