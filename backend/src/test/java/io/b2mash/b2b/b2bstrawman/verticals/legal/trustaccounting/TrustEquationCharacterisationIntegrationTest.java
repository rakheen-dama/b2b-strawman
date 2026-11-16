package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.demo.seed.LegalDemoDataSeeder;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceRepository;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceStatus;
import io.b2mash.b2b.b2bstrawman.invoice.PaymentEventRepository;
import io.b2mash.b2b.b2bstrawman.invoice.PaymentEventStatus;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.OrganizationRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.BooksSnapshot;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TrustBooksFixture;
import io.b2mash.b2b.b2bstrawman.testutil.TrustBooksHarness;
import io.b2mash.b2b.b2bstrawman.testutil.TrustBooksHarness.Expected;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Phase 92 slice 649A (task 649.2, architecture §16.9.2 paths 1–8 and 11): characterisation of
 * every existing trust posting path on the HEAD model through the {@link TrustBooksHarness} v0
 * oracle. A path that balances is asserted to balance and to move each term by exactly its amount;
 * a path that does not is pinned with the measured outcome in a method named {@code
 * …_pinsKnownDefect}, naming the slice that flips it.
 *
 * <p>Measured on {@code main} {@code aecf93279}: paths 1–6, 7a, 7c and 8 balance and move exactly
 * as expected; two paths were pinned — 7b and 7d, flipped to the intended behaviour by 649B (red
 * runs: {@code qa_cycle/evidence/phase92/649A/RED-RUNS.md} and {@code
 * qa_cycle/evidence/phase92/649B/red-run.txt}); path 11 was closed by 646A.
 *
 * <p>646A swaps the harness internals to the effect columns; no expectation here changes except the
 * pins it closes (path 11). 649B flips the reversal pins (paths 7b, 7d); with harness EQ-6 v2 every
 * reversal path here also checks the contra shape (the original keeps its effects, the {@code
 * REVERSAL} row carries their negation).
 *
 * <p>Paths 7 and 8 reach a reversible row only by <b>hand-setting {@code APPROVED}</b> (a deposit
 * or a {@code TRANSFER_OUT} is saved {@code RECORDED} and never becomes {@code APPROVED}); each
 * such setup is itself evidence for H-d (founder ruling #1) and is removed by 649B.
 *
 * <p>Setup and posting steps come from the shared {@link TrustBooksFixture}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TrustEquationCharacterisationIntegrationTest {

  private static final String ORG_ID = "org_trust_equation_characterisation";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrganizationRepository organizationRepository;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private InvoiceRepository invoiceRepository;
  @Autowired private PaymentEventRepository paymentEventRepository;
  @Autowired private LegalDemoDataSeeder legalDemoDataSeeder;
  @Autowired private TrustBooksHarness harness;
  @Autowired private TrustBooksFixture fixture;

  private TrustBooksFixture.Tenant books;
  private UUID approverId;
  private UUID secondApproverId;

  @BeforeAll
  void setup() throws Exception {
    var tenantSchema =
        provisioningService
            .provisionTenant(ORG_ID, "Trust Equation Characterisation Org", null)
            .schemaName();
    var memberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, "user_trust_eq_owner", "trust_eq@test.com", "Eq Owner", "owner"));
    approverId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                ORG_ID,
                "user_trust_eq_approver",
                "trust_eq_approver@test.com",
                "Eq Approver",
                "owner"));
    secondApproverId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                ORG_ID,
                "user_trust_eq_second",
                "trust_eq_second@test.com",
                "Eq Second Approver",
                "owner"));

    fixture.enableTrustModule(tenantSchema);
    books = fixture.tenant(tenantSchema, ORG_ID, memberId);
  }

  @AfterAll
  void booksBalanceFirmWide() {
    books.assertFirmBalanced();
  }

  // --- Path 1: deposit ------------------------------------------------------------------------

  @Test
  void deposit_movesCashbookAndClient() {
    var account = books.freshAccount(false);
    var client = books.newClient("P1");
    books.deposit(account, client, "10000.00");
    books.assertServicesAgree(account);
  }

  // --- Path 2: payment ------------------------------------------------------------------------

  @Test
  void paymentRequested_movesNothing() {
    var account = books.freshAccount(false);
    var client = books.newClient("P2a");
    books.deposit(account, client, "10000.00");
    books.requestPayment(account, client, "3000.00");
    books.assertServicesAgree(account);
  }

  @Test
  void paymentApprovedSingleMode_movesCashbookAndClient() {
    var account = books.freshAccount(false);
    var client = books.newClient("P2b");
    books.deposit(account, client, "10000.00");
    var paymentId = books.requestPayment(account, client, "3000.00");
    books.approve(account, paymentId, approverId, Expected.cashbookAndClient("-3000.00"));
    assertThat(books.statusOf(paymentId)).isEqualTo("APPROVED");
    books.assertServicesAgree(account);
  }

  @Test
  void paymentDualMode_firstApprovalMovesNothing_secondPosts() {
    var account = books.freshAccount(true);
    var client = books.newClient("P2c");
    books.deposit(account, client, "10000.00");
    var paymentId = books.requestPayment(account, client, "3000.00");
    books.approve(account, paymentId, approverId, Expected.none());
    assertThat(books.statusOf(paymentId)).isEqualTo("AWAITING_APPROVAL");
    books.approve(account, paymentId, secondApproverId, Expected.cashbookAndClient("-3000.00"));
    assertThat(books.statusOf(paymentId)).isEqualTo("APPROVED");
    books.assertServicesAgree(account);
  }

  @Test
  void paymentRejected_movesNothing() {
    var account = books.freshAccount(false);
    var client = books.newClient("P2d");
    books.deposit(account, client, "10000.00");
    var paymentId = books.requestPayment(account, client, "3000.00");
    books.reject(account, paymentId, approverId);
    assertThat(books.statusOf(paymentId)).isEqualTo("REJECTED");
    books.assertServicesAgree(account);
  }

  // --- Path 3: fee transfer -------------------------------------------------------------------

  @Test
  void feeTransferRequested_movesNothing() {
    var account = books.freshAccount(false);
    var client = books.newClient("P3a");
    books.deposit(account, client, "5000.00");
    books.requestFeeTransfer(account, client);
    books.assertServicesAgree(account);
  }

  @Test
  void feeTransferApproved_movesCashbookAndClient() {
    var account = books.freshAccount(false);
    var client = books.newClient("P3b");
    books.deposit(account, client, "5000.00");
    var fee = books.requestFeeTransfer(account, client);
    books.approve(account, fee.transactionId(), approverId, Expected.cashbookAndClient("-1150.00"));
    assertThat(invoiceStatusOf(fee.invoiceId())).isEqualTo(InvoiceStatus.PAID);
    books.assertServicesAgree(account);
  }

  @Test
  void feeTransferRejected_movesNothing() {
    var account = books.freshAccount(false);
    var client = books.newClient("P3c");
    books.deposit(account, client, "5000.00");
    var fee = books.requestFeeTransfer(account, client);
    books.reject(account, fee.transactionId(), approverId);
    assertThat(invoiceStatusOf(fee.invoiceId())).isEqualTo(InvoiceStatus.SENT);
    books.assertServicesAgree(account);
  }

  // --- Path 4: refund -------------------------------------------------------------------------

  @Test
  void refundRequested_movesNothing() {
    var account = books.freshAccount(false);
    var client = books.newClient("P4a");
    books.deposit(account, client, "8000.00");
    books.requestRefund(account, client, "2500.00");
    books.assertServicesAgree(account);
  }

  @Test
  void refundApproved_movesCashbookAndClient() {
    var account = books.freshAccount(false);
    var client = books.newClient("P4b");
    books.deposit(account, client, "8000.00");
    var refundId = books.requestRefund(account, client, "2500.00");
    books.approve(account, refundId, approverId, Expected.cashbookAndClient("-2500.00"));
    books.assertServicesAgree(account);
  }

  @Test
  void refundRejected_movesNothing() {
    var account = books.freshAccount(false);
    var client = books.newClient("P4c");
    books.deposit(account, client, "8000.00");
    var refundId = books.requestRefund(account, client, "2500.00");
    books.reject(account, refundId, approverId);
    books.assertServicesAgree(account);
  }

  // --- Path 5: inter-client transfer ----------------------------------------------------------

  @Test
  void interClientTransfer_isCashbookNeutralAndClientTotalUnchanged() {
    var account = books.freshAccount(false);
    var source = books.newClient("P5 source");
    var target = books.newClient("P5 target");
    books.deposit(account, source, "10000.00");

    var sourceBefore = books.clientLedger(account, source);
    var targetBefore = books.clientLedger(account, target);
    books.transfer(account, source, target, "4000.00");

    assertThat(books.clientLedger(account, source).subtract(sourceBefore))
        .isEqualByComparingTo("-4000.00");
    assertThat(books.clientLedger(account, target).subtract(targetBefore))
        .isEqualByComparingTo("4000.00");
    books.assertServicesAgree(account);
  }

  // --- Path 6: reversal of a debit ------------------------------------------------------------

  @Test
  void paymentReversal_movesCashbookAndClientBack() {
    var account = books.freshAccount(false);
    var client = books.newClient("P6a");
    books.deposit(account, client, "10000.00");
    var paymentId = books.requestPayment(account, client, "3000.00");
    books.approve(account, paymentId, approverId, Expected.cashbookAndClient("-3000.00"));

    var reversal = books.reverse(account, paymentId, Expected.cashbookAndClient("3000.00"));

    assertThat(reversal.status()).isEqualTo("RECORDED");
    assertThat(books.statusOf(paymentId)).isEqualTo("REVERSED");
    books.assertServicesAgree(account);
  }

  @Test
  void feeTransferReversal_movesBackAndCascadesToFeeNote() {
    var account = books.freshAccount(false);
    var client = books.newClient("P6b");
    books.deposit(account, client, "5000.00");
    var fee = books.requestFeeTransfer(account, client);
    books.approve(account, fee.transactionId(), approverId, Expected.cashbookAndClient("-1150.00"));
    assertThat(invoiceStatusOf(fee.invoiceId())).isEqualTo(InvoiceStatus.PAID);

    books.reverse(account, fee.transactionId(), Expected.cashbookAndClient("1150.00"));

    assertThat(books.statusOf(fee.transactionId())).isEqualTo("REVERSED");
    assertThat(invoiceStatusOf(fee.invoiceId()))
        .as("fee note PAID → SENT")
        .isEqualTo(InvoiceStatus.SENT);
    assertThat(completedPaymentEventsOf(fee.invoiceId()))
        .as("trust payment event removed")
        .isZero();
    books.assertServicesAgree(account);
  }

  @Test
  void refundReversal_movesCashbookAndClientBack() {
    var account = books.freshAccount(false);
    var client = books.newClient("P6c");
    books.deposit(account, client, "8000.00");
    var refundId = books.requestRefund(account, client, "2500.00");
    books.approve(account, refundId, approverId, Expected.cashbookAndClient("-2500.00"));

    books.reverse(account, refundId, Expected.cashbookAndClient("2500.00"));

    assertThat(books.statusOf(refundId)).isEqualTo("REVERSED");
    books.assertServicesAgree(account);
  }

  // --- Path 7: reversal of a credit -----------------------------------------------------------

  @Test
  void creditReversalRequested_movesNothing() {
    var account = books.freshAccount(false);
    var client = books.newClient("P7a");
    var depositId = books.deposit(account, client, "5000.00");
    books.handSetApproved(depositId); // H-d evidence (ruling #1)

    var reversal = books.reverse(account, depositId, Expected.none());

    assertThat(reversal.status()).isEqualTo("AWAITING_APPROVAL");
    assertThat(books.statusOf(depositId)).isEqualTo("APPROVED");
    books.assertServicesAgree(account);
  }

  /**
   * Path 7b — measured on {@code main} {@code aecf93279} as moving nothing (same mechanism as H-a
   * in {@code TrustReversalEquationIntegrationTest}) and pinned there; fixed in <b>649B</b>:
   * approving the credit reversal posts the contra entry, the cashbook and the client fall by the
   * amount, and the original is {@code REVERSED}.
   */
  @Test
  void creditReversalApproved_movesCashbookAndClient() {
    var account = books.freshAccount(false);
    var client = books.newClient("P7b");
    var depositId = books.deposit(account, client, "5000.00");
    books.handSetApproved(depositId); // H-d evidence (ruling #1)
    var reversal = books.reverse(account, depositId, Expected.none());

    books.approve(account, reversal.id(), approverId, Expected.cashbookAndClient("-5000.00"));

    assertThat(books.statusOf(reversal.id())).isEqualTo("APPROVED");
    assertThat(books.statusOf(depositId)).isEqualTo("REVERSED");
    books.assertServicesAgree(account);
  }

  @Test
  void creditReversalRejected_movesNothing() {
    var account = books.freshAccount(false);
    var client = books.newClient("P7c");
    var depositId = books.deposit(account, client, "5000.00");
    books.handSetApproved(depositId); // H-d evidence (ruling #1)
    var reversal = books.reverse(account, depositId, Expected.none());

    books.reject(account, reversal.id(), approverId);

    assertThat(books.statusOf(reversal.id())).isEqualTo("REJECTED");
    assertThat(books.statusOf(depositId)).isEqualTo("APPROVED");
    books.assertServicesAgree(account);
  }

  /**
   * Path 7d — measured on {@code main} {@code aecf93279} as refused "Transaction has already been
   * reversed" (same mechanism as H-c in {@code TrustReversalEquationIntegrationTest}) and pinned
   * there; fixed in <b>649B</b>: the rejected reversal is ignored, so the second reversal is
   * accepted — a {@code REVERSAL} awaiting approval that moves nothing.
   */
  @Test
  void creditReversalRejectedThenReversedAgain_isAccepted() {
    var account = books.freshAccount(false);
    var client = books.newClient("P7d");
    var depositId = books.deposit(account, client, "5000.00");
    books.handSetApproved(depositId); // H-d evidence (ruling #1)
    var first = books.reverse(account, depositId, Expected.none());
    books.reject(account, first.id(), approverId);

    var second = books.reverse(account, depositId, Expected.none());

    assertThat(second.status()).isEqualTo("AWAITING_APPROVAL");
    assertThat(second.reversalOf()).isEqualTo(depositId);
    books.assertServicesAgree(account);
  }

  // --- Path 8: reversal of a transfer ---------------------------------------------------------

  @Test
  void transferReversal_reversesBothLegs() {
    var account = books.freshAccount(false);
    var source = books.newClient("P8 source");
    var target = books.newClient("P8 target");
    books.deposit(account, source, "10000.00");
    var legs = books.transfer(account, source, target, "4000.00");
    var transferOutId = legs.get(0).id();
    var transferInId = legs.get(1).id();
    books.handSetApproved(transferOutId); // H-d evidence (ruling #1)

    var sourceBefore = books.clientLedger(account, source);
    var targetBefore = books.clientLedger(account, target);
    books.reverse(account, transferOutId, Expected.none());

    assertThat(books.clientLedger(account, source).subtract(sourceBefore))
        .isEqualByComparingTo("4000.00");
    assertThat(books.clientLedger(account, target).subtract(targetBefore))
        .isEqualByComparingTo("-4000.00");
    assertThat(books.statusOf(transferOutId)).isEqualTo("REVERSED");
    assertThat(books.statusOf(transferInId)).isEqualTo("REVERSED");
    books.assertServicesAgree(account);
  }

  // --- Path 11: the seeded demo data ----------------------------------------------------------

  /**
   * Path 11 — the seeded demo data (R2, §16.3.15). Pinned by 649A at a R27 000,00 difference
   * (cashbook R27 000,00, stored cards R0,00); closed in 646A: the seeder posts through
   * TrustPostingService, so EQ-2 holds — cashbook R27 000,00 = client ledgers R30 000,00 + (−R3
   * 000,00). Seeded into a separate tenant (the demo seeder creates its own clients).
   */
  @Test
  void seededDemoBooks_cashbookEqualsClientLedgers() {
    var slug = "trust-eq-seeded-" + UUID.randomUUID().toString().substring(0, 8);
    var seededSchema =
        provisioningService.provisionTenant(slug, "Trust Eq Seeded Org", null).schemaName();
    var orgId = organizationRepository.findByExternalOrgId(slug).orElseThrow().getId();
    legalDemoDataSeeder.seed(seededSchema, orgId);

    var accountIds =
        jdbcTemplate.queryForList(
            "SELECT id FROM \"%s\".trust_accounts".formatted(seededSchema), UUID.class);
    assertThat(accountIds).as("the seeder creates one trust account").hasSize(1);

    var snapshot = new BooksSnapshot[1];
    ScopedValue.where(RequestScopes.TENANT_ID, seededSchema)
        .run(() -> snapshot[0] = harness.snapshot(accountIds.get(0)));

    assertThat(snapshot[0].cashbook()).as("seeded cashbook").isEqualByComparingTo("27000.00");
    assertThat(snapshot[0].clientLedgers())
        .as("seeded client ledgers")
        .isEqualByComparingTo("27000.00");
    assertThat(snapshot[0].difference())
        .as("seeded cashbook − creditors")
        .isEqualByComparingTo("0.00");
  }

  // --- helpers --------------------------------------------------------------------------------

  private InvoiceStatus invoiceStatusOf(UUID invoiceId) {
    return books.read(() -> invoiceRepository.findById(invoiceId).orElseThrow().getStatus());
  }

  private int completedPaymentEventsOf(UUID invoiceId) {
    return books.read(
        () ->
            paymentEventRepository
                .findByInvoiceIdAndStatus(invoiceId, PaymentEventStatus.COMPLETED)
                .size());
  }
}
