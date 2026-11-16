package io.b2mash.b2b.b2bstrawman.testutil;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsService;
import io.b2mash.b2b.b2bstrawman.testutil.TrustBooksHarness.Expected;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountService;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountService.CreateTrustAccountRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustPostingService;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransaction;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionService;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionService.RecordDepositRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionService.RecordFeeTransferRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionService.RecordPaymentRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionService.RecordRefundRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionService.RecordTransferRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionService.TrustTransactionResponse;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionType;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Shared setup and posting steps for {@link TrustBooksHarness}-backed tests (Phase 92, slice 649A
 * onwards). Every posting step runs through the production {@link TrustTransactionService} inside
 * {@link TrustBooksHarness#assertMoves}, so a caller states only what each step must move.
 *
 * <p>Usage: provision a tenant and sync its members (MockMvc stays in the test class), call {@link
 * #enableTrustModule}, then work through a {@link Tenant} from {@link #tenant}. Follow the
 * harness's rules of use (fresh non-primary account per test method, {@code assertFirmBalanced} in
 * {@code @AfterAll}, past posting dates).
 *
 * <p>The fixture is a stateless singleton apart from a uniqueness counter, so classes sharing a
 * Spring context may use it concurrently.
 */
@Component
@Profile("test")
public final class TrustBooksFixture {

  /** Capabilities bound for every acting member (maker, approver, rejecter). */
  public static final Set<String> TRUST_CAPABILITIES =
      Set.of("APPROVE_TRUST_PAYMENT", "MANAGE_TRUST", "VIEW_TRUST");

  /** Posting date of every fixture row — in the past, so it counts in today's figures. */
  public static final LocalDate POSTING_DATE = LocalDate.of(2026, 3, 1);

  private final AtomicInteger sequence = new AtomicInteger();

  private final TrustBooksHarness harness;
  private final TransactionTemplate transactionTemplate;
  private final JdbcTemplate jdbc;
  private final Clock clock;
  private final OrgSettingsService orgSettingsService;
  private final OrgSettingsRepository orgSettingsRepository;
  private final TrustAccountService trustAccountService;
  private final TrustTransactionService transactionService;
  private final TrustTransactionRepository transactionRepository;
  private final TrustPostingService postingService;
  private final CustomerRepository customerRepository;
  private final ProjectRepository projectRepository;

  public TrustBooksFixture(
      TrustBooksHarness harness,
      TransactionTemplate transactionTemplate,
      JdbcTemplate jdbc,
      Clock clock,
      OrgSettingsService orgSettingsService,
      OrgSettingsRepository orgSettingsRepository,
      TrustAccountService trustAccountService,
      TrustTransactionService transactionService,
      TrustTransactionRepository transactionRepository,
      TrustPostingService postingService,
      CustomerRepository customerRepository,
      ProjectRepository projectRepository) {
    this.harness = harness;
    this.transactionTemplate = transactionTemplate;
    this.jdbc = jdbc;
    this.clock = clock;
    this.orgSettingsService = orgSettingsService;
    this.orgSettingsRepository = orgSettingsRepository;
    this.trustAccountService = trustAccountService;
    this.transactionService = transactionService;
    this.transactionRepository = transactionRepository;
    this.postingService = postingService;
    this.customerRepository = customerRepository;
    this.projectRepository = projectRepository;
  }

  /** Enables the {@code trust_accounting} module for the tenant (needed by the services). */
  public void enableTrustModule(String schema) {
    ScopedValue.where(RequestScopes.TENANT_ID, schema)
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var settings = orgSettingsService.getOrCreateForCurrentTenant();
                      settings.setEnabledModules(List.of("trust_accounting"));
                      orgSettingsRepository.save(settings);
                    }));
  }

  /** A handle on one provisioned tenant; {@code memberId} is the maker for every posting step. */
  public Tenant tenant(String schema, String orgId, UUID memberId) {
    return new Tenant(schema, orgId, memberId);
  }

  /** An ACTIVE client and its own trust matter (G89-F28). */
  public record Client(UUID id, UUID matter) {}

  /** A requested fee transfer and the SENT fee note it settles. */
  public record FeeTransfer(UUID transactionId, UUID invoiceId) {}

  /** The fixture's steps, bound to one tenant and its maker. */
  public final class Tenant {

    private final String schema;
    private final String orgId;
    private final UUID memberId;

    private Tenant(String schema, String orgId, UUID memberId) {
      this.schema = schema;
      this.orgId = orgId;
      this.memberId = memberId;
    }

    public String schema() {
      return schema;
    }

    public LocalDate today() {
      return LocalDate.now(clock);
    }

    /** Runs {@code action} as {@code member} (owner, {@link #TRUST_CAPABILITIES}) in the tenant. */
    public void as(UUID member, Runnable action) {
      ScopedValue.where(RequestScopes.TENANT_ID, schema)
          .where(RequestScopes.ORG_ID, orgId)
          .where(RequestScopes.MEMBER_ID, member)
          .where(RequestScopes.ORG_ROLE, "owner")
          .where(RequestScopes.CAPABILITIES, TRUST_CAPABILITIES)
          .run(action);
    }

    /** Runs {@code query} in its own transaction in the tenant schema. */
    public <T> T read(Supplier<T> query) {
      return ScopedValue.where(RequestScopes.TENANT_ID, schema)
          .call(() -> transactionTemplate.execute(tx -> query.get()));
    }

    // --- setup -----------------------------------------------------------------------------

    /** A fresh non-primary GENERAL account (a tenant holds one primary GENERAL account). */
    public UUID freshAccount(boolean dualApproval) {
      var n = sequence.incrementAndGet();
      var id = new UUID[1];
      as(
          memberId,
          () ->
              id[0] =
                  trustAccountService
                      .createTrustAccount(
                          new CreateTrustAccountRequest(
                              "Trust Books " + n,
                              "First National Bank",
                              "250655",
                              "TB-" + n,
                              "GENERAL",
                              false,
                              dualApproval,
                              null,
                              LocalDate.of(2026, 1, 1),
                              "649A harness account"))
                      .id());
      return id[0];
    }

    /** An ACTIVE client with its own trust matter (G89-F28). */
    public Client newClient(String label) {
      var n = sequence.incrementAndGet();
      UUID client =
          read(
              () ->
                  customerRepository
                      .save(
                          TestCustomerFactory.createActiveCustomer(
                              "Trust Books " + label + " " + n,
                              "trust_books_" + n + "@test.com",
                              memberId))
                      .getId());
      var matter =
          TestTrustMatterHelper.openClientMatter(
              schema,
              transactionTemplate,
              projectRepository,
              client,
              "Trust books matter " + n,
              memberId);
      return new Client(client, matter);
    }

    // --- posting paths (each asserted through TrustBooksHarness.assertMoves) ----------------

    /** Path 1: a deposit posts immediately (RECORDED) and moves cashbook and client together. */
    public UUID deposit(UUID account, Client client, String amount) {
      var id = new UUID[1];
      as(
          memberId,
          () ->
              harness.assertMoves(
                  account,
                  Expected.cashbookAndClient(amount),
                  () ->
                      id[0] =
                          transactionService
                              .recordDeposit(
                                  account,
                                  new RecordDepositRequest(
                                      client.id(),
                                      client.matter(),
                                      new BigDecimal(amount),
                                      reference("DEP"),
                                      "Deposit",
                                      POSTING_DATE))
                              .id()));
      assertThat(statusOf(id[0])).isEqualTo("RECORDED");
      return id[0];
    }

    /** A requested payment awaits approval and moves nothing. */
    public UUID requestPayment(UUID account, Client client, String amount) {
      var id = new UUID[1];
      as(
          memberId,
          () ->
              harness.assertMoves(
                  account,
                  Expected.none(),
                  () ->
                      id[0] =
                          transactionService
                              .recordPayment(
                                  account,
                                  new RecordPaymentRequest(
                                      client.id(),
                                      client.matter(),
                                      new BigDecimal(amount),
                                      reference("PAY"),
                                      "Payment",
                                      POSTING_DATE))
                              .id()));
      assertThat(statusOf(id[0])).isEqualTo("AWAITING_APPROVAL");
      return id[0];
    }

    /** A requested refund awaits approval and moves nothing. */
    public UUID requestRefund(UUID account, Client client, String amount) {
      var id = new UUID[1];
      as(
          memberId,
          () ->
              harness.assertMoves(
                  account,
                  Expected.none(),
                  () ->
                      id[0] =
                          transactionService
                              .recordRefund(
                                  account,
                                  new RecordRefundRequest(
                                      client.id(),
                                      client.matter(),
                                      new BigDecimal(amount),
                                      reference("REF"),
                                      "Refund",
                                      POSTING_DATE))
                              .id()));
      assertThat(statusOf(id[0])).isEqualTo("AWAITING_APPROVAL");
      return id[0];
    }

    /**
     * A SENT fee note of R1 150,00 on the client's matter, and a requested fee transfer for it
     * (awaits approval, moves nothing).
     */
    public FeeTransfer requestFeeTransfer(UUID account, Client client) {
      var invoiceId = UUID.randomUUID();
      jdbc.update(
          """
          INSERT INTO "%s".invoices (id, customer_id, invoice_number, status, currency,
              subtotal, tax_amount, total, due_date,
              customer_name, org_name, payment_destination, created_by, created_at, updated_at)
          VALUES (?, ?, ?, 'SENT', 'ZAR', 1000.00, 150.00, 1150.00, '2026-05-30',
              'Trust Books Client', 'Test Org', 'OPERATING', ?, now(), now())
          """
              .formatted(schema),
          invoiceId,
          client.id(),
          reference("INV"),
          memberId);
      jdbc.update(
          """
          INSERT INTO "%s".invoice_lines (id, invoice_id, project_id, line_type,
              description, quantity, unit_price, amount, sort_order, tax_exempt,
              created_at, updated_at)
          VALUES (gen_random_uuid(), ?, ?, 'TIME', 'Trust books work', 1, 1150.00, 1150.00, 0,
              false, now(), now())
          """
              .formatted(schema),
          invoiceId,
          client.matter());

      var id = new UUID[1];
      as(
          memberId,
          () ->
              harness.assertMoves(
                  account,
                  Expected.none(),
                  () ->
                      id[0] =
                          transactionService
                              .recordFeeTransfer(
                                  account,
                                  new RecordFeeTransferRequest(
                                      client.id(),
                                      invoiceId,
                                      new BigDecimal("1150.00"),
                                      reference("FT")))
                              .id()));
      assertThat(statusOf(id[0])).isEqualTo("AWAITING_APPROVAL");
      return new FeeTransfer(id[0], invoiceId);
    }

    /** An inter-client transfer: cashbook-neutral, client total unchanged; returns OUT, IN. */
    public List<TrustTransactionResponse> transfer(
        UUID account, Client source, Client target, String amount) {
      @SuppressWarnings("unchecked")
      List<TrustTransactionResponse>[] legs = new List[1];
      as(
          memberId,
          () ->
              harness.assertMoves(
                  account,
                  Expected.none(),
                  () ->
                      legs[0] =
                          transactionService.recordTransfer(
                              account,
                              new RecordTransferRequest(
                                  source.id(),
                                  source.matter(),
                                  target.id(),
                                  target.matter(),
                                  new BigDecimal(amount),
                                  reference("TRF"),
                                  "Inter-client transfer",
                                  POSTING_DATE))));
      assertThat(legs[0])
          .extracting(TrustTransactionResponse::transactionType)
          .containsExactly("TRANSFER_OUT", "TRANSFER_IN");
      return legs[0];
    }

    /** {@code approver} approves; the account moves by exactly {@code expected}. */
    public void approve(UUID account, UUID transactionId, UUID approver, Expected expected) {
      as(
          approver,
          () ->
              harness.assertMoves(
                  account,
                  expected,
                  () -> transactionService.approveTransaction(transactionId, approver)));
    }

    /** {@code rejecter} rejects; nothing moves and the row ends {@code REJECTED}. */
    public void reject(UUID account, UUID transactionId, UUID rejecter) {
      as(
          rejecter,
          () ->
              harness.assertMoves(
                  account,
                  Expected.none(),
                  () ->
                      transactionService.rejectTransaction(
                          transactionId, rejecter, "trust books fixture: rejected")));
      assertThat(statusOf(transactionId)).as("rejected row").isEqualTo("REJECTED");
    }

    /** The maker reverses; the account moves by exactly {@code expected}. */
    public TrustTransactionResponse reverse(UUID account, UUID transactionId, Expected expected) {
      var reversal = new TrustTransactionResponse[1];
      as(
          memberId,
          () ->
              harness.assertMoves(
                  account,
                  expected,
                  () ->
                      reversal[0] =
                          transactionService.reverseTransaction(
                              transactionId, "trust books fixture: reversed")));
      assertThat(reversal[0].transactionType()).isEqualTo("REVERSAL");
      assertThat(reversal[0].reversalOf()).isEqualTo(transactionId);
      return reversal[0];
    }

    /**
     * The maker's reversal is refused with {@code InvalidStateException} (400) "Cannot reverse
     * transaction" / {@code detail}, and nothing moves.
     */
    public void assertReversalRefused(UUID account, UUID transactionId, String detail) {
      as(
          memberId,
          () ->
              harness.assertMoves(
                  account,
                  Expected.none(),
                  () ->
                      assertThatThrownBy(
                              () ->
                                  transactionService.reverseTransaction(
                                      transactionId, "trust books fixture: reversed"))
                          .isInstanceOfSatisfying(
                              InvalidStateException.class,
                              e -> {
                                assertThat(e.getBody().getStatus()).isEqualTo(400);
                                assertThat(e.getBody().getTitle())
                                    .isEqualTo("Cannot reverse transaction");
                                assertThat(e.getBody().getDetail()).isEqualTo(detail);
                              })));
    }

    /**
     * {@code approver}'s approval is refused with {@code InvalidStateException} (400) titled {@code
     * title}, and nothing moves.
     */
    public void assertApprovalRefused(
        UUID account, UUID transactionId, UUID approver, String title) {
      as(
          approver,
          () ->
              harness.assertMoves(
                  account,
                  Expected.none(),
                  () ->
                      assertThatThrownBy(
                              () -> transactionService.approveTransaction(transactionId, approver))
                          .isInstanceOfSatisfying(
                              InvalidStateException.class,
                              e -> {
                                assertThat(e.getBody().getStatus()).isEqualTo(400);
                                assertThat(e.getBody().getTitle()).isEqualTo(title);
                              })));
    }

    // --- direct writes (bypass the services; keep every such write here) -------------------

    /**
     * Hand-sets {@code APPROVED}. On HEAD a deposit or {@code TRANSFER_OUT} is saved {@code
     * RECORDED} and never becomes {@code APPROVED}, so this is the only route to a reversible
     * credit — itself evidence for H-d (founder ruling #1); 649B removes the need.
     */
    public void handSetApproved(UUID transactionId) {
      read(
          () -> {
            var txn = transactionRepository.findById(transactionId).orElseThrow();
            txn.setStatus("APPROVED");
            return transactionRepository.save(txn);
          });
    }

    /**
     * Posts a row straight through {@link TrustPostingService}, bypassing {@link
     * TrustTransactionService}: no lock anchor, no matter-cell or sufficiency check, no audit. A
     * type posted when recorded ({@code DEPOSIT}, transfers) goes through {@code record}; a type
     * that needs approval ({@code PAYMENT}, {@code FEE_TRANSFER}, {@code REFUND}) through {@code
     * recordAwaitingApproval} then {@code postOnApproval} by the maker (so it ends {@code
     * APPROVED}). Replaces 649A's {@code saveDirect} (646A closed H-b: a posted row can no longer
     * be written without the posting service — {@code chk_trust_txn_posting}).
     */
    public UUID postDirect(UUID account, String type, String amount, Client client) {
      var approval = TrustTransactionType.of(type).approval();
      return read(
          () -> {
            var draft =
                new TrustTransaction(
                    account,
                    type,
                    new BigDecimal(amount),
                    client.id(),
                    client.matter(),
                    null,
                    reference(type.substring(0, 3)),
                    "posted straight through TrustPostingService",
                    POSTING_DATE,
                    approval == TrustTransactionType.Approval.NONE
                        ? "RECORDED"
                        : "AWAITING_APPROVAL",
                    memberId);
            return approval == TrustTransactionType.Approval.NONE
                ? postingService.record(draft).getId()
                : postingService
                    .postOnApproval(postingService.recordAwaitingApproval(draft), memberId)
                    .getId();
          });
    }

    // --- reads and harness assertions -------------------------------------------------------

    public String statusOf(UUID transactionId) {
      return read(() -> transactionRepository.findById(transactionId).orElseThrow().getStatus());
    }

    public UUID reversedByOf(UUID transactionId) {
      return read(
          () -> transactionRepository.findById(transactionId).orElseThrow().getReversedById());
    }

    /** {@link TrustBooksHarness#clientLedger}, as the maker. */
    public BigDecimal clientLedger(UUID account, Client client) {
      var balance = new BigDecimal[1];
      as(memberId, () -> balance[0] = harness.clientLedger(account, client.id()));
      return balance[0];
    }

    /** {@link TrustBooksHarness#assertServicesAgree} as at today, as the maker. */
    public void assertServicesAgree(UUID account) {
      as(memberId, () -> harness.assertServicesAgree(account, today()));
    }

    /** {@link TrustBooksHarness#assertFirmBalanced} as at today, as the maker. */
    public void assertFirmBalanced() {
      as(memberId, () -> harness.assertFirmBalanced(today()));
    }

    private String reference(String prefix) {
      return prefix + "-TB-" + sequence.incrementAndGet();
    }
  }
}
