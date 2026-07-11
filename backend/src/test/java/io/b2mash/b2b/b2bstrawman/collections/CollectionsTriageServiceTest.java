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
import io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccount;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountType;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.ledger.ClientLedgerCard;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.ledger.ClientLedgerCardRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 592A.3 — deterministic triage signal derivations (§3.4) and advisor merge (§6.5), exercised
 * end-to-end over embedded Postgres with a dedicated tenant. Each scenario uses its OWN customer so
 * the per-customer signal lists are id-scoped and can be asserted exactly (count-bleed rule). The
 * advisor-merge legs seed a real {@code ClientLedgerCard} — every test tenant HAS the trust tables,
 * so the positive/zero paths run here; the absent-tables (non-legal tenant) path is unit-only in
 * {@code TrustAwareCollectionsAdvisorTest}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CollectionsTriageServiceTest {

  private static final String ORG_ID = "org_collections_triage_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private InvoiceRepository invoiceRepository;
  @Autowired private CollectionActivityRepository activityRepository;
  @Autowired private TrustAccountRepository trustAccountRepository;
  @Autowired private ClientLedgerCardRepository clientLedgerCardRepository;
  @Autowired private CollectionsTriageService triageService;

  private String tenantSchema;
  private UUID ownerMemberId;

  // One customer per scenario (id-scoped signal assertions).
  private UUID custFresh; // no PAID history, no activities → no signals
  private UUID custDrift; // fast historical payer now far overdue → DRIFTING only
  private UUID custSerialLate; // slow-but-reliable, not drifting further → SERIAL_LATE only
  private UUID custSlowDrift; // slow payer drifting even further → DRIFTING + SERIAL_LATE
  private UUID custGoneQuiet; // ≥2 SENT reminders, no payment since → GONE_QUIET
  private UUID custEscalated; // FLAGGED ESCALATION on outstanding invoice → ESCALATED
  private UUID custTrust; // positive trust balance → TRUST_FUNDS_AVAILABLE (advisor)
  private UUID custNoTrust; // no ledger card → zero balance → no advice

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Collections Triage Test Org", null);
    ownerMemberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, "user_triage_owner", "triage@test.com", "Owner", "owner"));
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

    runInTenant(
        () -> {
          // Fresh: only outstanding overdue SENT invoices, no PAID, no activities.
          custFresh = seedCustomer("Fresh Co", "fresh@test.com");
          seedSentInvoice(custFresh, "Fresh Co", "INV-F1", 40);

          // Drift: median days-to-pay ≈ 10 (fast historical payer), now overdue 30 days
          // (30 > 10 + 14) → DRIFTING; median 10 ≤ 30 → not SERIAL_LATE.
          custDrift = seedCustomer("Drift Co", "drift@test.com");
          seedPaidInvoice(custDrift, "Drift Co", "INV-DP1", 10);
          seedPaidInvoice(custDrift, "Drift Co", "INV-DP2", 10);
          seedSentInvoice(custDrift, "Drift Co", "INV-D1", 30);

          // SerialLate: median ≈ 40 (>30, reliable-if-slow), overdue 45 (≤ 40 + 14 = 54)
          // → SERIAL_LATE but NOT DRIFTING (the median-relative threshold is the suppression).
          custSerialLate = seedCustomer("Serial Late Co", "serial@test.com");
          seedPaidInvoice(custSerialLate, "Serial Late Co", "INV-SP1", 40);
          seedPaidInvoice(custSerialLate, "Serial Late Co", "INV-SP2", 40);
          seedSentInvoice(custSerialLate, "Serial Late Co", "INV-S1", 45);

          // SlowDrift: median ≈ 40, overdue 60 (> 40 + 14 = 54) → BOTH DRIFTING and SERIAL_LATE.
          custSlowDrift = seedCustomer("Slow Drift Co", "slowdrift@test.com");
          seedPaidInvoice(custSlowDrift, "Slow Drift Co", "INV-QP1", 40);
          seedPaidInvoice(custSlowDrift, "Slow Drift Co", "INV-QP2", 40);
          seedSentInvoice(custSlowDrift, "Slow Drift Co", "INV-Q1", 60);

          // GoneQuiet: an outstanding invoice chased twice (two SENT activities across stages),
          // no payment since → GONE_QUIET.
          custGoneQuiet = seedCustomer("Gone Quiet Co", "quiet@test.com");
          UUID quietInvoice = seedSentInvoice(custGoneQuiet, "Gone Quiet Co", "INV-GQ1", 50);
          activityRepository.save(
              new CollectionActivity(
                  quietInvoice,
                  custGoneQuiet,
                  CollectionStage.STAGE_1,
                  CollectionActivityStatus.SENT,
                  20,
                  null));
          activityRepository.save(
              new CollectionActivity(
                  quietInvoice,
                  custGoneQuiet,
                  CollectionStage.STAGE_2,
                  CollectionActivityStatus.SENT,
                  50,
                  null));

          // Escalated: a FLAGGED ESCALATION activity on an outstanding invoice → ESCALATED.
          custEscalated = seedCustomer("Escalated Co", "escalated@test.com");
          UUID escInvoice = seedSentInvoice(custEscalated, "Escalated Co", "INV-E1", 90);
          activityRepository.save(
              new CollectionActivity(
                  escInvoice,
                  custEscalated,
                  CollectionStage.ESCALATION,
                  CollectionActivityStatus.FLAGGED,
                  90,
                  "partner_review"));

          // Trust: positive aggregate trust balance → advisor contributes TRUST_FUNDS_AVAILABLE.
          custTrust = seedCustomer("Trust Co", "trust@test.com");
          seedSentInvoice(custTrust, "Trust Co", "INV-T1", 20);
          seedTrustBalance(custTrust, BigDecimal.valueOf(1000));

          // NoTrust: outstanding invoice but no ledger card → zero balance → no advice.
          custNoTrust = seedCustomer("No Trust Co", "notrust@test.com");
          seedSentInvoice(custNoTrust, "No Trust Co", "INV-NT1", 20);
        });
  }

  @Test
  void noPaidHistory_yieldsNoDriftingOrSerialLate() {
    assertThat(signals(custFresh)).isEmpty();
  }

  @Test
  void fastPayerNowFarOverdue_isDriftingOnly() {
    assertThat(signals(custDrift)).containsExactly("DRIFTING");
  }

  @Test
  void slowButReliablePayer_isSerialLate_andSuppressedFromDrifting() {
    assertThat(signals(custSerialLate)).containsExactly("SERIAL_LATE");
  }

  @Test
  void slowPayerDriftingFurther_isBothDriftingAndSerialLate() {
    assertThat(signals(custSlowDrift)).containsExactly("DRIFTING", "SERIAL_LATE");
  }

  @Test
  void chasedTwiceWithNoPaymentSince_isGoneQuiet() {
    assertThat(signals(custGoneQuiet)).containsExactly("GONE_QUIET");
  }

  @Test
  void flaggedEscalationOnOutstandingInvoice_isEscalated() {
    assertThat(signals(custEscalated)).containsExactly("ESCALATED");
  }

  @Test
  void positiveTrustBalance_mergesTrustFundsAvailableSignalAndAdvice() {
    assertThat(signals(custTrust)).containsExactly("TRUST_FUNDS_AVAILABLE");

    List<CollectionsAdvisor.CollectionsAdvice> advice = advice(custTrust);
    assertThat(advice).hasSize(1);
    assertThat(advice.get(0).signal()).isEqualTo("TRUST_FUNDS_AVAILABLE");
    assertThat(advice.get(0).detail()).isEqualTo("R 1 000,00 held in trust");
  }

  @Test
  void zeroTrustBalance_yieldsNoAdviceAndNoSignal() {
    assertThat(signals(custNoTrust)).isEmpty();
    assertThat(advice(custNoTrust)).isEmpty();
  }

  // ── helpers ──────────────────────────────────────────────────────────────────

  private List<String> signals(UUID customerId) {
    return callInTenant(() -> triageService.signalsFor(customerId));
  }

  private List<CollectionsAdvisor.CollectionsAdvice> advice(UUID customerId) {
    return callInTenant(() -> triageService.adviceFor(customerId));
  }

  private void runInTenant(Runnable body) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, ownerMemberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(body);
  }

  private <T> T callInTenant(Callable<T> body) {
    return ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, ownerMemberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .call(
            () -> {
              try {
                return body.call();
              } catch (Exception e) {
                throw new RuntimeException(e);
              }
            });
  }

  private UUID seedCustomer(String name, String email) {
    Customer customer = TestCustomerFactory.createActiveCustomer(name, email, ownerMemberId);
    return customerRepository.save(customer).getId();
  }

  /** A currently-outstanding (SENT) invoice due {@code daysOverdue} in the past. */
  private UUID seedSentInvoice(
      UUID customerId, String name, String invoiceNumber, int daysOverdue) {
    Invoice invoice = draftInvoice(customerId, name, LocalDate.now().minusDays(daysOverdue));
    invoice.approve(invoiceNumber, ownerMemberId);
    invoice.markSent();
    return invoiceRepository.save(invoice).getId();
  }

  /**
   * A PAID invoice whose days-to-pay ({@code paid_at - due_date}) ≈ {@code daysToPay}. {@code
   * recordPayment} stamps {@code paidAt = now}, so the due date is set {@code daysToPay} in the
   * past.
   */
  private void seedPaidInvoice(UUID customerId, String name, String invoiceNumber, int daysToPay) {
    Invoice invoice = draftInvoice(customerId, name, LocalDate.now().minusDays(daysToPay));
    invoice.approve(invoiceNumber, ownerMemberId);
    invoice.markSent();
    invoice.recordPayment("PAY-" + invoiceNumber);
    invoiceRepository.save(invoice);
  }

  private Invoice draftInvoice(UUID customerId, String name, LocalDate dueDate) {
    var invoice =
        new Invoice(customerId, "ZAR", name, name + "@test.com", null, "Triage Org", ownerMemberId);
    invoice.updateDraft(dueDate, null, null, BigDecimal.ZERO);
    invoice.recalculateTotals(BigDecimal.valueOf(1000), false, BigDecimal.ZERO, false);
    return invoice;
  }

  private void seedTrustBalance(UUID customerId, BigDecimal amount) {
    var trustAccount =
        new TrustAccount(
            "Triage Trust",
            "Test Bank",
            "002",
            "9876543210",
            TrustAccountType.INVESTMENT,
            false,
            false,
            null,
            LocalDate.now(),
            null);
    var savedTrustAccount = trustAccountRepository.save(trustAccount);
    var ledgerCard = new ClientLedgerCard(savedTrustAccount.getId(), customerId);
    ledgerCard.addDeposit(amount, LocalDate.now());
    clientLedgerCardRepository.save(ledgerCard);
  }
}
