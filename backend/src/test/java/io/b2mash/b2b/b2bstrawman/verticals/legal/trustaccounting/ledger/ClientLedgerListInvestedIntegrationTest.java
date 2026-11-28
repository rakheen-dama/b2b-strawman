package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TrustBooksFixture;
import io.b2mash.b2b.b2bstrawman.testutil.TrustBooksHarness.Expected;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.InvestmentBasis;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * D-126: the client-ledgers list's "Non-zero balances only" keeps every client who holds money in
 * trust on the account — general ({@code CLIENT}) balance + what is invested for them in section
 * 86(4) investments placed from it (648E, ADR-379 L7) — so a client whose general balance is R0
 * while an s86(4) investment is still held stays listed, and counted. A client taken on with only
 * an s86(4) investment (657A: no {@code CLIENT} row on the funding account at all) is listed too,
 * filtered and unfiltered. The filter only chooses rows: every figure on a row is unchanged.
 *
 * <p>One funding account and one s86(4) investment account in this test's own tenant, seeded
 * through the posting and investment services (TrustBooksFixture), read through the HTTP list.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ClientLedgerListInvestedIntegrationTest {
  private static final String ORG_ID = "org_client_ledger_list_invested_test";
  private static final String OWNER_SUB = "user_ledger_invested_owner";
  private static final String APPROVER_SUB = "user_ledger_invested_approver";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private TrustBooksFixture fixture;

  private UUID funding;
  private UUID investmentAccount;

  /** Deposit R1 000, nothing invested: listed either way. */
  private TrustBooksFixture.Client plain;

  /** Deposit R30 000, all of it placed in s86(4): general R0, invested R30 000. */
  private TrustBooksFixture.Client holder;

  /** Deposit R500, paid out: general R0, nothing invested — the one the filter hides. */
  private TrustBooksFixture.Client settled;

  /** Taken on with only an s86(4) investment of R75 000: no CLIENT row on the funding account. */
  private TrustBooksFixture.Client takenOn;

  @BeforeAll
  void setup() throws Exception {
    var schema =
        provisioningService
            .provisionTenant(ORG_ID, "Client Ledger List Invested Org", null)
            .schemaName();
    var ownerId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, OWNER_SUB, "ledger_inv_owner@test.com", "Inv Owner", "owner"));
    // Single-approval mode refuses self-approval: a second owner approves
    var approverId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                ORG_ID,
                APPROVER_SUB,
                "ledger_inv_approver@test.com",
                "Inv Approver",
                "owner"));
    fixture.enableTrustModule(schema);
    var books = fixture.tenant(schema, ORG_ID, ownerId);

    funding = books.freshAccount(false);
    investmentAccount = books.freshInvestmentAccount(InvestmentBasis.CLIENT_INSTRUCTION);

    plain = books.newClient("Plain");
    books.deposit(funding, plain, "1000.00");

    holder = books.newClient("Holder");
    books.deposit(funding, holder, "30000.00");
    var placed = books.requestPlacement(funding, investmentAccount, holder, "30000.00");
    books.approvePlacement(
        funding,
        Expected.cashbookAndClient("-30000.00"),
        investmentAccount,
        Expected.cashbookAndInvestment("30000.00"),
        placed.depositTransactionId(),
        approverId);

    settled = books.newClient("Settled");
    books.deposit(funding, settled, "500.00");
    var payment = books.requestPayment(funding, settled, "500.00");
    books.approve(funding, payment, approverId, Expected.cashbookAndClient("-500.00"));

    takenOn = books.newClient("TakenOn");
    LocalDate cutover = YearMonth.from(books.today()).minusMonths(4).atEndOfMonth();
    var authority = books.authority(books.takeOnBatch(funding, cutover), cutover);
    books.takenOnInvestment(
        authority, funding, investmentAccount, takenOn, "75000.00", cutover.minusMonths(3));
  }

  @Test
  void nonZeroOnly_keepsAClientWhoseGeneralBalanceIsZeroButWhoHoldsAnInvestment() throws Exception {
    var page = list(funding, 0, 20, Map.of("nonZeroOnly", "true"));
    assertThat(page.ids())
        .as("listed under the non-zero filter")
        .containsExactlyInAnyOrder(id(plain), id(holder), id(takenOn));
    assertThat(page.ids()).doesNotContain(id(settled));
    assertThat(page.totalElements()).as("N clients found").isEqualTo(3);
    assertThat(page.totalPages()).isEqualTo(1);
  }

  @Test
  void unfiltered_listsTheClientTakenOnWithOnlyAnInvestment() throws Exception {
    var page = list(funding, 0, 20, Map.of());
    assertThat(page.ids())
        .containsExactlyInAnyOrder(id(plain), id(holder), id(settled), id(takenOn));
    assertThat(page.totalElements()).isEqualTo(4);
  }

  @Test
  void theRowsFiguresAreUnchanged_generalBalanceAndInvestedBalanceSideBySide() throws Exception {
    var page = list(funding, 0, 20, Map.of());
    assertThat(page.row(plain).balance()).isEqualByComparingTo("1000.00");
    assertThat(page.row(plain).invested()).isEqualByComparingTo("0");
    assertThat(page.row(holder).balance()).isEqualByComparingTo("0");
    assertThat(page.row(holder).invested()).isEqualByComparingTo("30000.00");
    assertThat(page.row(settled).balance()).isEqualByComparingTo("0");
    assertThat(page.row(settled).invested()).isEqualByComparingTo("0");
    // as its card reads it (657A): general R0, R75 000 invested, no CLIENT row so no last date
    assertThat(page.row(takenOn).balance()).isEqualByComparingTo("0");
    assertThat(page.row(takenOn).invested()).isEqualByComparingTo("75000.00");
    assertThat(page.row(takenOn).lastTransactionDate()).isNull();
    assertThat(page.row(takenOn).name()).isEqualTo(takenOn.name());
  }

  @Test
  void sortStaysBalanceDescThenClientId_andPagesNeitherDropNorRepeat() throws Exception {
    var all = list(funding, 0, 20, Map.of());
    // R1 000 first, then the three R0 rows by client id (a UUID orders as its lowercase hex)
    var expected = new ArrayList<>(List.of(id(plain)));
    expected.addAll(List.of(id(holder), id(settled), id(takenOn)).stream().sorted().toList());
    assertThat(all.ids()).containsExactlyElementsOf(expected);

    var p0 = list(funding, 0, 2, Map.of("nonZeroOnly", "true"));
    var p1 = list(funding, 1, 2, Map.of("nonZeroOnly", "true"));
    assertThat(p0.totalElements()).isEqualTo(3);
    assertThat(p0.totalPages()).isEqualTo(2);
    assertThat(p1.totalElements()).isEqualTo(3);
    var paged = new ArrayList<>(p0.ids());
    paged.addAll(p1.ids());
    assertThat(paged)
        .doesNotHaveDuplicates()
        .containsExactlyInAnyOrder(id(plain), id(holder), id(takenOn));
  }

  @Test
  void searchApplies_toAClientListedOnlyForItsInvestment() throws Exception {
    var page = list(funding, 0, 20, Map.of("search", "takenon", "nonZeroOnly", "true"));
    assertThat(page.ids()).containsExactly(id(takenOn));
    assertThat(page.totalElements()).isEqualTo(1);
    assertThat(list(funding, 0, 20, Map.of("search", "settled", "nonZeroOnly", "true")).ids())
        .isEmpty();
  }

  @Test
  void onTheInvestmentAccountItself_nothingIsPlacedFromIt_soTheListIsUnchanged() throws Exception {
    assertThat(list(investmentAccount, 0, 20, Map.of()).totalElements()).isZero();
    assertThat(list(investmentAccount, 0, 20, Map.of("nonZeroOnly", "true")).totalElements())
        .isZero();
  }

  // --- helpers ---

  private static String id(TrustBooksFixture.Client client) {
    return client.id().toString();
  }

  private record Row(
      String name, BigDecimal balance, BigDecimal invested, String lastTransactionDate) {}

  private record ListPage(
      long totalElements, int totalPages, List<String> ids, Map<String, Row> rows) {
    Row row(TrustBooksFixture.Client client) {
      assertThat(rows).as("list row for " + client.name()).containsKey(id(client));
      return rows.get(id(client));
    }
  }

  private ListPage list(UUID account, int page, int size, Map<String, String> params)
      throws Exception {
    MockHttpServletRequestBuilder request =
        get("/api/trust-accounts/" + account + "/client-ledgers")
            .param("page", String.valueOf(page))
            .param("size", String.valueOf(size))
            // The UI's sort param (actions.ts) is still sent and still ignored by the native query
            .param("sort", "balance,desc")
            .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER_SUB));
    params.forEach(request::param);
    String body =
        mockMvc
            .perform(request)
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    List<Map<String, Object>> content = JsonPath.read(body, "$.content");
    var rows = new LinkedHashMap<String, Row>();
    for (var r : content) {
      rows.put(
          (String) r.get("clientId"),
          new Row(
              (String) r.get("clientName"),
              new BigDecimal(r.get("balance").toString()),
              new BigDecimal(r.get("investedBalance").toString()),
              (String) r.get("lastTransactionDate")));
    }
    return new ListPage(
        ((Number) JsonPath.read(body, "$.page.totalElements")).longValue(),
        JsonPath.read(body, "$.page.totalPages"),
        new ArrayList<>(rows.keySet()),
        rows);
  }
}
