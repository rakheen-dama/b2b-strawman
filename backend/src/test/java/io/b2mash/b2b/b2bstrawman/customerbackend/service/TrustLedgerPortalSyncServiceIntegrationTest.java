package io.b2mash.b2b.b2bstrawman.customerbackend.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.CustomerService;
import io.b2mash.b2b.b2bstrawman.customerbackend.repository.PortalTrustReadModelRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccount;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountType;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.event.TrustDomainEvent;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.event.TrustTransactionApprovalEvent;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.event.TrustTransactionRecordedEvent;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransaction;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionService;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionService.RecordDepositRequest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Integration tests for {@link TrustLedgerPortalSyncService}. Exercises the full event-listener +
 * read-model stack on embedded Postgres (no mocks).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TrustLedgerPortalSyncServiceIntegrationTest {

  private static final String ORG_ID = "org_portal_trust_sync_test";
  private static final String ORG_NAME = "Portal Trust Sync Test Org";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private CustomerService customerService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private TrustAccountRepository trustAccountRepository;
  @Autowired private TrustTransactionRepository trustTransactionRepository;
  @Autowired private ProjectRepository projectRepository;
  @Autowired private ApplicationEventPublisher eventPublisher;
  @Autowired private PortalTrustReadModelRepository portalTrustRepo;
  @Autowired private TrustLedgerPortalSyncService syncService;
  @Autowired private PlatformTransactionManager transactionManager;
  @Autowired private TrustTransactionService trustTransactionService;

  private String tenantSchema;
  private UUID customerId;
  private UUID matterId;
  private UUID trustAccountId;
  private UUID memberId;
  private TransactionTemplate tenantTxTemplate;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, ORG_NAME, "legal-za");
    memberId = UUID.fromString(TestMemberHelper.syncOwner(mockMvc, ORG_ID, "portal_trust_sync"));
    tenantSchema = orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).get().getSchemaName();
    tenantTxTemplate = new TransactionTemplate(transactionManager);

    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberId)
        .run(
            () ->
                tenantTxTemplate.executeWithoutResult(
                    status -> {
                      var customer =
                          customerService.createCustomer(
                              "Trust Sync Customer",
                              "trust-sync@test.com",
                              null,
                              null,
                              null,
                              memberId);
                      customerId = customer.getId();

                      var project = new Project("Trust Sync Matter", "Test matter", memberId);
                      project.setCustomerId(customerId);
                      project = projectRepository.saveAndFlush(project);
                      matterId = project.getId();

                      var trustAccount =
                          new TrustAccount(
                              "Portal Sync Trust",
                              "Test Bank",
                              "250655",
                              "6200-000-SYNC",
                              TrustAccountType.GENERAL,
                              true,
                              false,
                              null,
                              LocalDate.of(2026, 1, 1),
                              "Seed trust account for portal sync tests");
                      trustAccount = trustAccountRepository.saveAndFlush(trustAccount);
                      trustAccountId = trustAccount.getId();
                    }));
  }

  @BeforeEach
  void clearPortalState() {
    // Portal read-model is shared across test methods (PER_CLASS lifecycle); wipe between each
    // test so event-listener assertions see only the rows produced by the method under test.
    // Also wipe firm-side trust_transactions so each test starts from a clean ledger (running
    // balance assertions depend on prior non-backfill rows not leaking in).
    if (customerId != null) {
      portalTrustRepo.deleteTransactionsByCustomer(customerId);
      portalTrustRepo.deleteBalancesByCustomer(customerId);
    }
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberId)
        .run(
            () ->
                tenantTxTemplate.executeWithoutResult(
                    status -> trustTransactionRepository.deleteAll()));
  }

  // ==========================================================================
  // Event listener: trust_transaction.approved
  // ==========================================================================

  @Test
  void approvedTransactionEventUpsertsPortalTransactionAndBalance() {
    UUID txnId =
        recordApprovedTransaction(
            "DEPOSIT", new BigDecimal("1500.00"), "DEP-SYNC-1", "Initial deposit");

    publishApprovedEvent(txnId, "DEPOSIT", new BigDecimal("1500.00"));

    var transactions = portalTrustRepo.findTransactions(customerId, matterId, null, null, 50, 0);
    assertThat(transactions).hasSize(1);
    assertThat(transactions.getFirst().transactionType()).isEqualTo("DEPOSIT");
    assertThat(transactions.getFirst().amount()).isEqualByComparingTo("1500.00");
    assertThat(transactions.getFirst().runningBalance()).isEqualByComparingTo("1500.00");
    assertThat(transactions.getFirst().description()).isEqualTo("Initial deposit");

    var balance = portalTrustRepo.findBalance(customerId, matterId).orElseThrow();
    assertThat(balance.currentBalance()).isEqualByComparingTo("1500.00");
    assertThat(balance.lastTransactionAt()).isNotNull();
  }

  // ==========================================================================
  // Event listener: trust_transaction.recorded (GAP-L-52 regression guard)
  // — direct-RECORDED deposits bypass the awaiting_approval/approved lifecycle,
  // so the portal sync must fire off a separate RECORDED event. Before the fix
  // the portal /trust view stayed empty because the listener only filtered on
  // "trust_transaction.approved".
  // ==========================================================================

  @Test
  void recordedTransactionEventUpsertsPortalTransactionAndBalance() {
    UUID txnId =
        recordRecordedTransaction(
            "DEPOSIT", new BigDecimal("2500.00"), "DEP-REC-1", "Direct-recorded deposit");

    publishRecordedEvent(txnId, "DEPOSIT", new BigDecimal("2500.00"));

    var transactions = portalTrustRepo.findTransactions(customerId, matterId, null, null, 50, 0);
    assertThat(transactions).hasSize(1);
    assertThat(transactions.getFirst().transactionType()).isEqualTo("DEPOSIT");
    assertThat(transactions.getFirst().amount()).isEqualByComparingTo("2500.00");
    assertThat(transactions.getFirst().runningBalance()).isEqualByComparingTo("2500.00");

    var balance = portalTrustRepo.findBalance(customerId, matterId).orElseThrow();
    assertThat(balance.currentBalance()).isEqualByComparingTo("2500.00");
    assertThat(balance.lastTransactionAt()).isNotNull();
  }

  // ==========================================================================
  // End-to-end: calling TrustTransactionService.recordDeposit triggers the
  // full publish → AFTER_COMMIT listener → portal upsert chain. Before the
  // fix recordDeposit never published, so the portal rows stayed empty.
  // ==========================================================================

  @Test
  void recordDepositEndToEndPopulatesPortalReadModel() {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberId)
        .run(
            () ->
                tenantTxTemplate.executeWithoutResult(
                    status ->
                        trustTransactionService.recordDeposit(
                            trustAccountId,
                            new RecordDepositRequest(
                                customerId,
                                matterId,
                                new BigDecimal("4200.00"),
                                "DEP-E2E-1",
                                "End-to-end deposit",
                                LocalDate.of(2026, 4, 5)))));

    var transactions = portalTrustRepo.findTransactions(customerId, matterId, null, null, 50, 0);
    assertThat(transactions)
        .as("Portal trust transaction must be populated for direct-RECORDED deposits")
        .hasSize(1);
    assertThat(transactions.getFirst().transactionType()).isEqualTo("DEPOSIT");
    assertThat(transactions.getFirst().amount()).isEqualByComparingTo("4200.00");
    assertThat(transactions.getFirst().runningBalance()).isEqualByComparingTo("4200.00");

    var balance = portalTrustRepo.findBalance(customerId, matterId).orElseThrow();
    assertThat(balance.currentBalance()).isEqualByComparingTo("4200.00");
  }

  // ==========================================================================
  // Running balance: each historical row gets its own progressive sum, not the
  // latest aggregate (regression guard for PR #1084 review comment C1).
  // ==========================================================================

  @Test
  void runningBalanceIsProgressiveAcrossHistoricalRows() {
    // Seed three approved deposits on distinct transaction dates so the ASC walk produces
    // 100, 300, 600 — NOT 600/600/600 (the pre-fix bug).
    UUID txn1 =
        recordApprovedTransactionOnDate(
            "DEPOSIT",
            new BigDecimal("100.00"),
            "PROG-1",
            "First deposit",
            LocalDate.of(2026, 2, 1));
    UUID txn2 =
        recordApprovedTransactionOnDate(
            "DEPOSIT",
            new BigDecimal("200.00"),
            "PROG-2",
            "Second deposit",
            LocalDate.of(2026, 2, 2));
    UUID txn3 =
        recordApprovedTransactionOnDate(
            "DEPOSIT",
            new BigDecimal("300.00"),
            "PROG-3",
            "Third deposit",
            LocalDate.of(2026, 2, 3));

    // Publishing any one approval recomputes the entire matter history, so a single event
    // is enough to populate all three rows with their progressive balances.
    publishApprovedEvent(txn3, "DEPOSIT", new BigDecimal("300.00"));

    var byId =
        portalTrustRepo.findTransactions(customerId, matterId, null, null, 50, 0).stream()
            .collect(
                java.util.stream.Collectors.toMap(
                    io.b2mash.b2b.b2bstrawman.customerbackend.model.PortalTrustTransactionView::id,
                    t -> t));

    assertThat(byId.get(txn1).runningBalance()).isEqualByComparingTo("100.00");
    assertThat(byId.get(txn2).runningBalance()).isEqualByComparingTo("300.00");
    assertThat(byId.get(txn3).runningBalance()).isEqualByComparingTo("600.00");

    var balance = portalTrustRepo.findBalance(customerId, matterId).orElseThrow();
    assertThat(balance.currentBalance()).isEqualByComparingTo("600.00");
  }

  // ==========================================================================
  // Event listener: interest posted (fan-out)
  // ==========================================================================

  @Test
  void interestPostedEventUpsertsInterestCreditRow() {
    UUID txnId =
        recordApprovedTransaction(
            "INTEREST_CREDIT", new BigDecimal("25.00"), "INT-SYNC-1", "Q1 interest credit");

    publishInterestPostedEvent();

    var transactions = portalTrustRepo.findTransactions(customerId, matterId, null, null, 50, 0);
    assertThat(transactions)
        .anyMatch(
            t ->
                t.id().equals(txnId)
                    && "INTEREST_CREDIT".equals(t.transactionType())
                    && t.amount().compareTo(new BigDecimal("25.00")) == 0);
    var balance = portalTrustRepo.findBalance(customerId, matterId).orElseThrow();
    assertThat(balance.currentBalance()).isGreaterThan(BigDecimal.ZERO);
  }

  // ==========================================================================
  // Sanitiser: internal-tagged description falls back to synthesised value
  // ==========================================================================

  @Test
  void internalTaggedDescriptionIsReplacedWithSynthesisedFallback() {
    UUID txnId =
        recordApprovedTransaction(
            "PAYMENT", new BigDecimal("250.00"), "TX-INT-42", "[internal] do not share");

    publishApprovedEvent(txnId, "PAYMENT", new BigDecimal("250.00"));

    var match =
        portalTrustRepo.findTransactions(customerId, matterId, null, null, 50, 0).stream()
            .filter(t -> t.id().equals(txnId))
            .findFirst()
            .orElseThrow();
    assertThat(match.description()).doesNotContain("do not share");
    assertThat(match.description()).isEqualTo("PAYMENT \u2014 TX-INT-42");
  }

  // ==========================================================================
  // Sanitiser: long descriptions truncate at 140 with ellipsis
  // ==========================================================================

  @Test
  void longDescriptionIsTruncatedAt140Characters() {
    String longDesc = "a".repeat(200);
    UUID txnId =
        recordApprovedTransaction("DEPOSIT", new BigDecimal("10.00"), "DEP-LONG-1", longDesc);

    publishApprovedEvent(txnId, "DEPOSIT", new BigDecimal("10.00"));

    var match =
        portalTrustRepo.findTransactions(customerId, matterId, null, null, 50, 0).stream()
            .filter(t -> t.id().equals(txnId))
            .findFirst()
            .orElseThrow();
    assertThat(match.description()).hasSize(140);
    assertThat(match.description()).endsWith("\u2026");
  }

  // ==========================================================================
  // Event listener: reconciliation completed (fan-out)
  // ==========================================================================

  @Test
  void reconciliationCompletedEventRecomputesMatterBalance() {
    // Seed a pair of deposits — a pre-fan-out approval recompute would otherwise leave portal
    // rows empty, so we force-sync via the event handler under test.
    recordApprovedTransactionOnDate(
        "DEPOSIT",
        new BigDecimal("500.00"),
        "REC-1",
        "Pre-reconciliation deposit 1",
        LocalDate.of(2026, 3, 10));
    recordApprovedTransactionOnDate(
        "DEPOSIT",
        new BigDecimal("250.00"),
        "REC-2",
        "Pre-reconciliation deposit 2",
        LocalDate.of(2026, 3, 11));

    publishReconciliationCompletedEvent();

    var transactions = portalTrustRepo.findTransactions(customerId, matterId, null, null, 50, 0);
    assertThat(transactions).hasSize(2);

    var balance = portalTrustRepo.findBalance(customerId, matterId).orElseThrow();
    assertThat(balance.currentBalance()).isEqualByComparingTo("750.00");
  }

  // ==========================================================================
  // Backfill: seeds balance + transactions up to per-matter cap
  // ==========================================================================

  // ==========================================================================
  // Backfill: drops stale portal rows (matters that no longer have firm-side
  // transactions) — drift repair semantics per PR #1084 review.
  // ==========================================================================

  @Test
  void backfillForTenantWipesStaleMatterRows() {
    // Pre-seed the portal read-model with a balance row for a matter that has NO firm-side
    // trust transactions. The tenant-scoped findAll() during backfill will not surface it, so
    // the wipe-and-rewrite pass must drop it.
    portalTrustRepo.deleteTransactionsByCustomer(customerId);
    portalTrustRepo.deleteBalancesByCustomer(customerId);

    UUID staleMatterId = UUID.randomUUID();
    portalTrustRepo.upsertBalance(
        customerId,
        staleMatterId,
        new BigDecimal("777.00"),
        java.time.Instant.parse("2026-01-01T00:00:00Z"));
    portalTrustRepo.upsertTransaction(
        UUID.randomUUID(),
        customerId,
        staleMatterId,
        "DEPOSIT",
        new BigDecimal("777.00"),
        new BigDecimal("777.00"),
        java.time.Instant.parse("2026-01-01T00:00:00Z"),
        "stale",
        "STALE-REF");

    // Seed one firm-side approved transaction so the backfill has at least one retained matter
    // for this customer and exercises the matter-set delete branch (rather than the
    // no-retained-matters fall-through).
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberId)
        .run(
            () ->
                tenantTxTemplate.executeWithoutResult(
                    status -> {
                      var txn =
                          new TrustTransaction(
                              trustAccountId,
                              "DEPOSIT",
                              new BigDecimal("50.00"),
                              customerId,
                              matterId,
                              null,
                              "RETAIN-1",
                              "Retained deposit",
                              LocalDate.of(2026, 2, 1),
                              "APPROVED",
                              memberId);
                      trustTransactionRepository.save(txn);
                    }));

    syncService.backfillForTenant(ORG_ID);

    // The stale matter's balance row must be gone.
    assertThat(portalTrustRepo.findBalance(customerId, staleMatterId)).isEmpty();
    // And no stale transactions for that matter either.
    assertThat(portalTrustRepo.findTransactions(customerId, staleMatterId, null, null, 50, 0))
        .isEmpty();
    // Sanity: the retained matter's balance is present.
    assertThat(portalTrustRepo.findBalance(customerId, matterId)).isPresent();
  }

  @Test
  void backfillForTenantSeedsBalanceAndTransactions() {
    // Isolate from earlier test fixtures by clearing portal-side state for this customer.
    portalTrustRepo.deleteTransactionsByCustomer(customerId);
    portalTrustRepo.deleteBalancesByCustomer(customerId);

    // Seed 3 approved deposits on the matter.
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberId)
        .run(
            () ->
                tenantTxTemplate.executeWithoutResult(
                    status -> {
                      for (int i = 0; i < 3; i++) {
                        var txn =
                            new TrustTransaction(
                                trustAccountId,
                                "DEPOSIT",
                                new BigDecimal("100.00"),
                                customerId,
                                matterId,
                                null,
                                "BKF-" + i,
                                "Backfill deposit " + i,
                                LocalDate.of(2026, 1, 1 + i),
                                "APPROVED",
                                memberId);
                        trustTransactionRepository.save(txn);
                      }
                    }));

    var result = syncService.backfillForTenant(ORG_ID);
    assertThat(result.mattersProjected()).isGreaterThanOrEqualTo(1);
    assertThat(result.transactionsProjected()).isGreaterThanOrEqualTo(3);

    var balance = portalTrustRepo.findBalance(customerId, matterId).orElseThrow();
    assertThat(balance.currentBalance()).isGreaterThanOrEqualTo(new BigDecimal("300.00"));

    var transactions = portalTrustRepo.findTransactions(customerId, matterId, null, null, 100, 0);
    assertThat(transactions).hasSizeGreaterThanOrEqualTo(3);
  }

  // ==========================================================================
  // Helpers
  // ==========================================================================

  private UUID recordApprovedTransaction(
      String type, BigDecimal amount, String reference, String description) {
    return recordApprovedTransactionOnDate(
        type, amount, reference, description, LocalDate.of(2026, 2, 1));
  }

  private UUID recordApprovedTransactionOnDate(
      String type, BigDecimal amount, String reference, String description, LocalDate txnDate) {
    UUID[] holder = new UUID[1];
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberId)
        .run(
            () ->
                tenantTxTemplate.executeWithoutResult(
                    status -> {
                      var txn =
                          new TrustTransaction(
                              trustAccountId,
                              type,
                              amount,
                              customerId,
                              matterId,
                              null,
                              reference,
                              description,
                              txnDate,
                              "APPROVED",
                              memberId);
                      holder[0] = trustTransactionRepository.saveAndFlush(txn).getId();
                    }));
    return holder[0];
  }

  private void publishApprovedEvent(UUID txnId, String type, BigDecimal amount) {
    // Publish inside a bound tenant scope so the surrounding transaction (and any session
    // reused by the AFTER_COMMIT listener's repository call) resolves to the tenant schema.
    // The sync listener runs AFTER_COMMIT on this transaction — AFTER_COMMIT fires because
    // the event is published inside TransactionTemplate's executeWithoutResult.
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberId)
        .run(
            () ->
                tenantTxTemplate.executeWithoutResult(
                    status ->
                        eventPublisher.publishEvent(
                            TrustTransactionApprovalEvent.approved(
                                txnId,
                                trustAccountId,
                                type,
                                amount,
                                customerId,
                                memberId,
                                memberId,
                                tenantSchema,
                                ORG_ID))));
  }

  /**
   * Seeds a {@code RECORDED}-status trust transaction directly via the repository (no event). Used
   * by the GAP-L-52 listener test to exercise the {@link TrustTransactionRecordedEvent} branch in
   * isolation.
   */
  private UUID recordRecordedTransaction(
      String type, BigDecimal amount, String reference, String description) {
    UUID[] holder = new UUID[1];
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberId)
        .run(
            () ->
                tenantTxTemplate.executeWithoutResult(
                    status -> {
                      var txn =
                          new TrustTransaction(
                              trustAccountId,
                              type,
                              amount,
                              customerId,
                              matterId,
                              null,
                              reference,
                              description,
                              LocalDate.of(2026, 4, 1),
                              "RECORDED",
                              memberId);
                      holder[0] = trustTransactionRepository.saveAndFlush(txn).getId();
                    }));
    return holder[0];
  }

  private void publishRecordedEvent(UUID txnId, String type, BigDecimal amount) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberId)
        .run(
            () ->
                tenantTxTemplate.executeWithoutResult(
                    status ->
                        eventPublisher.publishEvent(
                            TrustTransactionRecordedEvent.recorded(
                                txnId,
                                trustAccountId,
                                type,
                                amount,
                                customerId,
                                memberId,
                                tenantSchema,
                                ORG_ID))));
  }

  private void publishInterestPostedEvent() {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberId)
        .run(
            () ->
                tenantTxTemplate.executeWithoutResult(
                    status ->
                        eventPublisher.publishEvent(
                            TrustDomainEvent.InterestPosted.of(
                                UUID.randomUUID(),
                                trustAccountId,
                                LocalDate.of(2026, 1, 1),
                                LocalDate.of(2026, 3, 31),
                                new BigDecimal("25.00"),
                                new BigDecimal("25.00"),
                                memberId,
                                tenantSchema,
                                ORG_ID))));
  }

  private void publishReconciliationCompletedEvent() {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberId)
        .run(
            () ->
                tenantTxTemplate.executeWithoutResult(
                    status ->
                        eventPublisher.publishEvent(
                            TrustDomainEvent.ReconciliationCompleted.of(
                                UUID.randomUUID(),
                                trustAccountId,
                                LocalDate.of(2026, 3, 31),
                                memberId,
                                tenantSchema,
                                ORG_ID))));
  }
}
