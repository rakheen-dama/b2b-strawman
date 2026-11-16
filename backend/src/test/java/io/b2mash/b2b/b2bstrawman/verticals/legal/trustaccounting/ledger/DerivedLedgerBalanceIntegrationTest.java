package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.BooksSnapshot;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TrustBooksFixture;
import io.b2mash.b2b.b2bstrawman.testutil.TrustBooksHarness;
import io.b2mash.b2b.b2bstrawman.testutil.TrustBooksHarness.Expected;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.report.ClientTrustBalancesQuery;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.ClientBalanceRow;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.MatterBalanceRow;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustLedgerQueryRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Phase 92 slice 646A (task 646.6, ADR-378, acceptance criterion 6): every derived balance in
 * {@link TrustLedgerQueryRepository} — client, matter cell, the matters/clients list batch, the
 * account totals (cashbook, client-ledger total) and the report rows — equals the independent
 * {@link TrustBooksHarness} oracle, which sums the effect columns with its own SQL. A client whose
 * rows were posted with no lock anchor is still a client of the account: it is listed by {@code
 * listClientLedgers} and the client-trust-balances report.
 *
 * <p>Harness rules: a fresh account per method, postings through {@link TrustBooksFixture}, and
 * {@code assertFirmBalanced} in {@code @AfterAll}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DerivedLedgerBalanceIntegrationTest {

  private static final String ORG_ID = "org_derived_ledger_balance";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private TrustBooksHarness harness;
  @Autowired private TrustBooksFixture fixture;
  @Autowired private TrustLedgerQueryRepository ledgerQueries;
  @Autowired private ClientLedgerService clientLedgerService;
  @Autowired private ClientLedgerCardRepository ledgerCardRepository;
  @Autowired private ClientTrustBalancesQuery clientTrustBalancesQuery;

  private TrustBooksFixture.Tenant books;
  private UUID memberId;
  private UUID approverId;

  @BeforeAll
  void setup() throws Exception {
    var tenantSchema =
        provisioningService
            .provisionTenant(ORG_ID, "Derived Ledger Balance Org", null)
            .schemaName();
    memberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, "user_dlb_owner", "dlb_owner@test.com", "DLB Owner", "owner"));
    approverId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                ORG_ID,
                "user_dlb_approver",
                "dlb_approver@test.com",
                "DLB Approver",
                "owner"));
    fixture.enableTrustModule(tenantSchema);
    books = fixture.tenant(tenantSchema, ORG_ID, memberId);
  }

  @AfterAll
  void booksBalanceFirmWide() {
    books.assertFirmBalanced();
  }

  @Test
  void everyDerivedBalance_equalsTheOracle() {
    var account = books.freshAccount(false);
    var a = books.newClient("DLB A");
    var b = books.newClient("DLB B");
    books.deposit(account, a, "10000.00");
    books.deposit(account, b, "4000.00");
    books.approve(
        account,
        books.requestPayment(account, a, "3000.00"),
        approverId,
        Expected.cashbookAndClient("-3000.00"));
    books.transfer(account, a, b, "1500.00");
    // A request still awaiting approval moves nothing anywhere
    books.requestPayment(account, b, "700.00");

    var snapshot = new BooksSnapshot[1];
    var before = new BooksSnapshot[1];
    books.as(
        memberId,
        () -> {
          harness.assertBalanced(account);
          snapshot[0] = harness.snapshot(account);
          before[0] = harness.snapshotAsAt(account, TrustBooksFixture.POSTING_DATE.minusDays(1));
        });
    assertThat(snapshot[0].cashbook()).isEqualByComparingTo("11000.00");
    assertThat(snapshot[0].clientLedgers()).isEqualByComparingTo("11000.00");

    // Account totals
    assertThat(books.read(() -> ledgerQueries.cashbook(account, null)))
        .as("cashbook")
        .isEqualByComparingTo(snapshot[0].cashbook());
    assertThat(books.read(() -> ledgerQueries.clientLedgerTotal(account, null)))
        .as("client-ledger total")
        .isEqualByComparingTo(snapshot[0].clientLedgers());
    // As at a date before every posting
    assertThat(
            books.read(
                () -> ledgerQueries.cashbook(account, TrustBooksFixture.POSTING_DATE.minusDays(1))))
        .isEqualByComparingTo(before[0].cashbook())
        .isEqualByComparingTo("0");
    assertThat(
            books.read(
                () ->
                    ledgerQueries.clientLedgerTotal(
                        account, TrustBooksFixture.POSTING_DATE.minusDays(1))))
        .isEqualByComparingTo(before[0].clientLedgers())
        .isEqualByComparingTo("0");
    books.assertServicesAgree(account);

    for (var client : List.of(a, b)) {
      var oracle = books.clientLedger(account, client);
      assertThat(oracle).isEqualByComparingTo("5500.00");
      // Client
      assertThat(books.read(() -> ledgerQueries.clientBalance(account, client.id(), null)))
          .as("clientBalance of %s", client.id())
          .isEqualByComparingTo(oracle);
      // Matter cell: every posting of the client sits on its own trust matter (G89-F28)
      assertThat(
              books.read(
                  () ->
                      ledgerQueries.matterCellBalance(account, client.id(), client.matter(), null)))
          .as("matter cell of %s", client.id())
          .isEqualByComparingTo(oracle);
      assertThat(books.read(() -> ledgerQueries.matterBalance(client.matter())))
          .as("matter balance of %s", client.matter())
          .isEqualByComparingTo(oracle);
      // Per-matter buckets sum to the client
      List<MatterBalanceRow> buckets =
          books.read(() -> ledgerQueries.clientMatterBalances(account, client.id(), null));
      assertThat(
              buckets.stream()
                  .map(MatterBalanceRow::getBalance)
                  .reduce(BigDecimal.ZERO, BigDecimal::add))
          .as("matter buckets of %s", client.id())
          .isEqualByComparingTo(oracle);
      // Single client summary (the ledger card endpoint)
      assertThat(
              books
                  .read(() -> ledgerQueries.clientSummary(account, client.id()))
                  .orElseThrow()
                  .getBalance())
          .isEqualByComparingTo(oracle);
    }

    // The matters/clients list batch (general = CLIENT ledger across every account)
    Map<UUID, BigDecimal> general =
        books.read(() -> ledgerQueries.generalBalancesByClient(List.of(a.id(), b.id()))).stream()
            .collect(
                Collectors.toMap(ClientBalanceRow::getCustomerId, ClientBalanceRow::getBalance));
    assertThat(general).containsOnlyKeys(a.id(), b.id());
    assertThat(general.get(a.id())).isEqualByComparingTo(books.clientLedger(account, a));
    assertThat(general.get(b.id())).isEqualByComparingTo(books.clientLedger(account, b));
    assertThat(books.read(() -> ledgerQueries.generalBalance(a.id())))
        .isEqualByComparingTo(books.clientLedger(account, a));

    // The report rows (every client with a posted row) sum to the oracle's client ledgers
    Map<UUID, ClientBalanceRow> rows =
        books.read(() -> ledgerQueries.clientBalances(account, null)).stream()
            .collect(Collectors.toMap(ClientBalanceRow::getCustomerId, Function.identity()));
    assertThat(rows).containsOnlyKeys(a.id(), b.id());
    assertThat(
            rows.values().stream()
                .map(ClientBalanceRow::getBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add))
        .isEqualByComparingTo(snapshot[0].clientLedgers());
    assertThat(rows.get(a.id()).getLastTransactionDate()).isEqualTo(TrustBooksFixture.POSTING_DATE);
  }

  @Test
  void aClientWithPostedRowsAndNoAnchor_isListedByTheLedgerListAndTheReport() {
    var account = books.freshAccount(false);
    var anchored = books.newClient("DLB Anchored");
    var anchorless = books.newClient("DLB Anchorless");
    books.deposit(account, anchored, "1000.00");
    // Straight through TrustPostingService: no lock anchor is created
    books.postDirect(account, "DEPOSIT", "2500.00", anchorless);
    books.postDirect(account, "PAYMENT", "400.00", anchorless);

    assertThat(
            books.read(
                () ->
                    ledgerCardRepository.findByTrustAccountIdAndCustomerId(
                        account, anchorless.id())))
        .as("no lock anchor for the client posted straight through TrustPostingService")
        .isEmpty();
    var oracle = books.clientLedger(account, anchorless);
    assertThat(oracle).isEqualByComparingTo("2100.00");
    books.as(memberId, () -> harness.assertBalanced(account));

    // The client-ledgers list — with the UI's sort=balance,desc, which the derived query applies
    // itself (there is no stored balance property to sort on)
    var page =
        books.read(
            () ->
                clientLedgerService.listClientLedgers(
                    account, PageRequest.of(0, 50, Sort.by(Sort.Direction.DESC, "balance"))));
    assertThat(page.getContent())
        .extracting(ClientLedgerService.ClientLedgerCardResponse::clientId)
        .containsExactly(anchorless.id(), anchored.id());
    var listed = page.getContent().getFirst();
    assertThat(listed.balance()).isEqualByComparingTo(oracle);
    assertThat(listed.id()).as("no anchor → no card id").isNull();
    assertThat(listed.lastTransactionDate()).isEqualTo(TrustBooksFixture.POSTING_DATE);

    // The single ledger
    var single = books.read(() -> clientLedgerService.getClientLedger(anchorless.id(), account));
    assertThat(single.balance()).isEqualByComparingTo(oracle);
    assertThat(single.totalDeposits()).isEqualByComparingTo("2500.00");
    assertThat(single.totalPayments()).isEqualByComparingTo("400.00");

    // The client-trust-balances report
    var reportRows =
        books.read(
            () ->
                clientTrustBalancesQuery
                    .executeAll(Map.of("trust_account_id", account.toString()))
                    .rows());
    var reported =
        reportRows.stream().filter(r -> anchorless.id().equals(r.get("customerId"))).toList();
    assertThat(reported).hasSize(1);
    assertThat((BigDecimal) reported.getFirst().get("balance")).isEqualByComparingTo(oracle);
    assertThat(reportRows).hasSize(2);
  }
}
