package io.b2mash.b2b.b2bstrawman.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.config.KaziTime;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsService;
import io.b2mash.b2b.b2bstrawman.testutil.TestEntityHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TestTrustMatterHelper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
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
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * {@code GET /api/dashboard/trust-balance-trend} (Phase 90 643A, ADR-372, arch §15.3.7): month-end
 * cashbook and client-ledger balances over the last {@code months} SA calendar months, oldest
 * first, zero-filled. The series replays the RECORDED/APPROVED rows with the sign rules of the
 * cashbook and client-ledger reads, so its last point must equal {@code /cashbook-balance} and
 * {@code /total-balance}; REJECTED, AWAITING_APPROVAL and reversed rows move nothing. Gated exactly
 * like {@code /trust-position}: {@code VIEW_TRUST} (403), the {@code trust_accounting} module (403
 * "Module not enabled"), unknown account / no account (404), and {@code months} outside 1–24 (400).
 *
 * <p>Seeded through REST only, dated relative to {@code LocalDate.now(KaziTime.ZONE)}; no row is
 * dated after today ({@code /cashbook-balance} has no date filter). FEE_TRANSFER is not seeded: it
 * shares PAYMENT's branch in both sign rules and needs an approved fee note over REST.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TrustBalanceTrendIntegrationTest {
  private static final String URL = "/api/dashboard/trust-balance-trend";

  private static final String ORG_ID = "org_dash_trust_trend";
  private static final String OWNER = "user_dtt_owner";

  /** Records the debits so the owner can approve them (self-approval is refused). */
  private static final String ADMIN = "user_dtt_admin";

  private static final String MEMBER = "user_dtt_member";

  /** Custom role holding VIEW_LEGAL only — no VIEW_TRUST. */
  private static final String NO_TRUST = "user_dtt_no_trust";

  private static final String NO_MODULE_ORG_ID = "org_dash_trust_trend_nomod";
  private static final String NO_MODULE_OWNER = "user_dtt_nomod_owner";

  private static final String NO_ACCOUNT_ORG_ID = "org_dash_trust_trend_noacct";
  private static final String NO_ACCOUNT_OWNER = "user_dtt_noacct_owner";

  /** Primary GENERAL account closed, a non-primary one still active (its own tenant). */
  private static final String CLOSED_PRIMARY_ORG_ID = "org_dash_trust_trend_closedprim";

  private static final String CLOSED_PRIMARY_OWNER = "user_dtt_closedprim_owner";

  @Autowired private MockMvc mockMvc;
  @Autowired private io.b2mash.b2b.b2bstrawman.customer.CustomerRepository customerRepository;
  @Autowired private io.b2mash.b2b.b2bstrawman.project.ProjectRepository projectRepository;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSettingsRepository orgSettingsRepository;
  @Autowired private OrgSettingsService orgSettingsService;
  @Autowired private TransactionTemplate transactionTemplate;

  private final LocalDate today = LocalDate.now(KaziTime.ZONE);
  private final YearMonth current = YearMonth.from(today);

  private String tenantSchema;
  private String primaryAccountId;
  private String secondAccountId;

  /**
   * Hand-computed month-end balance of the primary account, keyed by months-ago (0 = current). The
   * ledger series equals the cashbook series: every seeded row has a client, and the transfer is
   * ledger-net-zero.
   */
  private final Map<Integer, BigDecimal> primaryBalance = new HashMap<>();

  @BeforeAll
  void setup() throws Exception {
    tenantSchema =
        provisioningService.provisionTenant(ORG_ID, "Trust Trend Org", null).schemaName();
    TestMemberHelper.syncMember(mockMvc, ORG_ID, OWNER, "dtt_owner@test.com", "DTT Owner", "owner");
    TestMemberHelper.syncMember(mockMvc, ORG_ID, ADMIN, "dtt_admin@test.com", "DTT Admin", "admin");
    TestMemberHelper.syncMember(
        mockMvc, ORG_ID, MEMBER, "dtt_member@test.com", "DTT Member", "member");
    String noTrustId =
        TestMemberHelper.syncMember(
            mockMvc, ORG_ID, NO_TRUST, "dtt_no_trust@test.com", "DTT No Trust", "member");
    enableTrustAccounting(tenantSchema);

    var owner = TestJwtFactory.ownerJwt(ORG_ID, OWNER);
    var admin = TestJwtFactory.adminJwt(ORG_ID, ADMIN);
    assignRole(owner, noTrustId, createCustomRole(owner, "DTT No Trust Role", "VIEW_LEGAL"));
    primaryAccountId = createTrustAccount(owner, "Trend Primary", "62000000201", true);
    secondAccountId = createTrustAccount(owner, "Trend Secondary", "62000000202", false);
    String clientA =
        TestEntityHelper.createCustomer(mockMvc, owner, "Trend Client A", "dtt_a@test.com");
    String clientB =
        TestEntityHelper.createCustomer(mockMvc, owner, "Trend Client B", "dtt_b@test.com");
    String clientC =
        TestEntityHelper.createCustomer(mockMvc, owner, "Trend Client C", "dtt_c@test.com");

    // Primary account, recorded in date order. 14 months ago: before every window under test.
    record(owner, primaryAccountId, "deposit", clientA, "10000.00", "DEP-DTT-PRE", day(14));
    // 8 months ago: deposit.
    record(owner, primaryAccountId, "deposit", clientB, "5000.00", "DEP-DTT-8", day(8));
    // 6 months ago: an approved payment.
    approve(record(admin, primaryAccountId, "payment", clientA, "1000.00", "PAY-DTT-6", day(6)));
    // 5 months ago: nothing (zero-filled month).
    // 4 months ago: a REJECTED payment and an AWAITING_APPROVAL payment — neither moves a balance.
    reject(record(admin, primaryAccountId, "payment", clientA, "700.00", "PAY-DTT-REJ", day(4)));
    record(admin, primaryAccountId, "payment", clientB, "300.00", "PAY-DTT-AWAIT", day(4));
    // 3 months ago: an approved refund.
    approve(record(admin, primaryAccountId, "refund", clientA, "400.00", "REF-DTT-3", day(3)));
    // 2 months ago: an approved payment that is then reversed, and a client-to-client transfer.
    String reversed =
        record(admin, primaryAccountId, "payment", clientB, "2000.00", "PAY-DTT-REV", day(2));
    approve(reversed);
    reverse(admin, reversed);
    transfer(owner, primaryAccountId, clientA, clientB, "1500.00", "TRF-DTT-2", day(2));
    // Last month and this month: deposits.
    record(owner, primaryAccountId, "deposit", clientA, "2500.00", "DEP-DTT-1", day(1));
    record(owner, primaryAccountId, "deposit", clientB, "100.00", "DEP-DTT-0", day(0));

    // Secondary account: one deposit last month.
    record(owner, secondAccountId, "deposit", clientC, "750.00", "DEP-DTT-SEC", day(1));

    // Expected primary month-end balances (months-ago → balance), cashbook == ledger.
    BigDecimal running = new BigDecimal("10000.00");
    Map<Integer, BigDecimal> net =
        Map.of(
            8, new BigDecimal("5000.00"),
            6, new BigDecimal("-1000.00"),
            3, new BigDecimal("-400.00"),
            1, new BigDecimal("2500.00"),
            0, new BigDecimal("100.00"));
    for (int k = 13; k >= 0; k--) {
      running = running.add(net.getOrDefault(k, BigDecimal.ZERO));
      primaryBalance.put(k, running);
    }

    provisioningService.provisionTenant(NO_MODULE_ORG_ID, "Trust Trend No Module Org", null);
    TestMemberHelper.syncMember(
        mockMvc,
        NO_MODULE_ORG_ID,
        NO_MODULE_OWNER,
        "dtt_nomod@test.com",
        "DTT NoMod Owner",
        "owner");

    String noAccountSchema =
        provisioningService
            .provisionTenant(NO_ACCOUNT_ORG_ID, "Trust Trend No Account Org", null)
            .schemaName();
    TestMemberHelper.syncMember(
        mockMvc,
        NO_ACCOUNT_ORG_ID,
        NO_ACCOUNT_OWNER,
        "dtt_noacct@test.com",
        "DTT NoAcct Owner",
        "owner");
    enableTrustAccounting(noAccountSchema);

    String closedPrimarySchema =
        provisioningService
            .provisionTenant(CLOSED_PRIMARY_ORG_ID, "Trust Trend Closed Primary Org", null)
            .schemaName();
    TestMemberHelper.syncMember(
        mockMvc,
        CLOSED_PRIMARY_ORG_ID,
        CLOSED_PRIMARY_OWNER,
        "dtt_closedprim@test.com",
        "DTT ClosedPrim Owner",
        "owner");
    enableTrustAccounting(closedPrimarySchema);
  }

  @Test
  void defaultWindowIsTwelveSaMonthsOldestFirstWithPartialCurrentMonth() throws Exception {
    var owner = TestJwtFactory.ownerJwt(ORG_ID, OWNER);
    String body =
        mockMvc
            .perform(get(URL).with(owner))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.trustAccountId").value(primaryAccountId))
            .andExpect(jsonPath("$.accountName").value("Trend Primary"))
            .andExpect(jsonPath("$.asOf").value(today.toString()))
            .andExpect(jsonPath("$.months", hasSize(12)))
            .andExpect(jsonPath("$.accounts", hasSize(2)))
            .andReturn()
            .getResponse()
            .getContentAsString();

    List<String> expectedKeys = new ArrayList<>();
    for (YearMonth m = current.minusMonths(11); !m.isAfter(current); m = m.plusMonths(1)) {
      expectedKeys.add(m.toString());
    }
    assertThat(JsonPath.<List<String>>read(body, "$.months[*].month"))
        .containsExactlyElementsOf(expectedKeys);
    for (int i = 0; i < 12; i++) {
      YearMonth month = current.minusMonths(11L - i);
      boolean last = i == 11;
      assertThat(JsonPath.<Boolean>read(body, "$.months[%d].partial".formatted(i))).isEqualTo(last);
      assertThat(JsonPath.<String>read(body, "$.months[%d].periodEnd".formatted(i)))
          .isEqualTo((last ? today : month.atEndOfMonth()).toString());
    }

    // accounts: both ids with the right isPrimary flag.
    assertThat(
            JsonPath.<List<Object>>read(
                body, "$.accounts[?(@.id == '%s')].isPrimary".formatted(primaryAccountId)))
        .containsExactly(true);
    assertThat(
            JsonPath.<List<Object>>read(
                body, "$.accounts[?(@.id == '%s')].isPrimary".formatted(secondAccountId)))
        .containsExactly(false);
  }

  @Test
  void monthEndBalancesFollowTheSeededPostings() throws Exception {
    String body = trend(TestJwtFactory.ownerJwt(ORG_ID, OWNER), 12, null);

    // Opening = the deposit dated 14 months ago, before the 12-month window.
    assertThat(decimal(body, "$.openingCashbookBalance")).isEqualByComparingTo("10000.00");
    assertThat(decimal(body, "$.openingClientLedgerTotal")).isEqualByComparingTo("10000.00");
    for (int i = 0; i < 12; i++) {
      int monthsAgo = 11 - i;
      BigDecimal expected = primaryBalance.get(monthsAgo);
      assertThat(decimal(body, "$.months[%d].cashbookBalance".formatted(i)))
          .as("cashbook %d months ago", monthsAgo)
          .isEqualByComparingTo(expected);
      assertThat(decimal(body, "$.months[%d].clientLedgerTotal".formatted(i)))
          .as("client ledgers %d months ago", monthsAgo)
          .isEqualByComparingTo(expected);
    }
  }

  @Test
  void lastPointEqualsTheLiveCashbookAndClientLedgerReads() throws Exception {
    var owner = TestJwtFactory.ownerJwt(ORG_ID, OWNER);
    String body = trend(owner, 12, null);
    String base = "/api/trust-accounts/" + primaryAccountId;
    BigDecimal cashbook = decimal(read(owner, base + "/cashbook-balance"), "$.balance");
    BigDecimal ledger = decimal(read(owner, base + "/total-balance"), "$.balance");

    assertThat(decimal(body, "$.months[11].cashbookBalance")).isEqualByComparingTo(cashbook);
    assertThat(decimal(body, "$.months[11].clientLedgerTotal")).isEqualByComparingTo(ledger);
    assertThat(cashbook).isEqualByComparingTo(primaryBalance.get(0));
  }

  @Test
  void rejectedAndAwaitingApprovalPaymentsMoveNothing() throws Exception {
    var owner = TestJwtFactory.ownerJwt(ORG_ID, OWNER);
    String body = trend(owner, 12, null);
    // Month 4-ago holds only a REJECTED (700) and an AWAITING_APPROVAL (300) payment: its balance
    // equals the month before (5-ago, itself zero-filled from 6-ago).
    BigDecimal fourAgo = decimal(body, "$.months[7].cashbookBalance");
    assertThat(fourAgo).isEqualByComparingTo(decimal(body, "$.months[6].cashbookBalance"));
    assertThat(decimal(body, "$.months[7].clientLedgerTotal")).isEqualByComparingTo(fourAgo);
    assertThat(fourAgo).isEqualByComparingTo("14000.00");

    // The awaiting payment is still pending (so it was seeded and is excluded by status).
    String pending = read(owner, "/api/trust-accounts/" + primaryAccountId + "/pending-approvals");
    assertThat(JsonPath.<List<String>>read(pending, "$[*].reference"))
        .containsExactly("PAY-DTT-AWAIT");
  }

  @Test
  void reversedPaymentAndTransferLeaveTheirMonthUnchanged() throws Exception {
    String body = trend(TestJwtFactory.ownerJwt(ORG_ID, OWNER), 12, null);
    // Month 2-ago holds an approved-then-reversed 2000 payment and a 1500 transfer A → B: the
    // reversed payment drops out, the transfer is cashbook-neutral and ledger-net-zero.
    assertThat(decimal(body, "$.months[9].cashbookBalance"))
        .isEqualByComparingTo(decimal(body, "$.months[8].cashbookBalance"));
    assertThat(decimal(body, "$.months[9].clientLedgerTotal"))
        .isEqualByComparingTo(decimal(body, "$.months[8].clientLedgerTotal"));
    assertThat(decimal(body, "$.months[9].cashbookBalance")).isEqualByComparingTo("13600.00");
    // No month carries the reversed 2000 debit.
    for (int i = 0; i < 12; i++) {
      assertThat(decimal(body, "$.months[%d].cashbookBalance".formatted(i)))
          .isEqualByComparingTo(primaryBalance.get(11 - i));
    }
  }

  @Test
  void emptyMonthIsZeroFilledAtThePreviousBalance() throws Exception {
    String body = trend(TestJwtFactory.ownerJwt(ORG_ID, OWNER), 12, null);
    // 5 months ago: no postings → equal to 6 months ago (index 5 vs 6).
    assertThat(decimal(body, "$.months[6].cashbookBalance"))
        .isEqualByComparingTo(decimal(body, "$.months[5].cashbookBalance"));
    assertThat(JsonPath.<String>read(body, "$.months[6].month"))
        .isEqualTo(current.minusMonths(5).toString());
  }

  @Test
  void shortWindowOpensWithEverythingBeforeItsFirstMonth() throws Exception {
    String body = trend(TestJwtFactory.ownerJwt(ORG_ID, OWNER), 3, null);
    assertThat(JsonPath.<List<String>>read(body, "$.months[*].month"))
        .containsExactly(
            current.minusMonths(2).toString(),
            current.minusMonths(1).toString(),
            current.toString());
    // Opening = every row before 2 months ago (the 3-ago balance).
    assertThat(decimal(body, "$.openingCashbookBalance"))
        .isEqualByComparingTo(primaryBalance.get(3));
    assertThat(decimal(body, "$.openingClientLedgerTotal"))
        .isEqualByComparingTo(primaryBalance.get(3));
    // First point = opening + that month's net (0: reversed payment + transfer).
    assertThat(decimal(body, "$.months[0].cashbookBalance"))
        .isEqualByComparingTo(primaryBalance.get(2));
    assertThat(decimal(body, "$.months[2].cashbookBalance"))
        .isEqualByComparingTo(primaryBalance.get(0));
  }

  @Test
  void singleMonthWindowIsTheCurrentMonthToDate() throws Exception {
    String body = trend(TestJwtFactory.ownerJwt(ORG_ID, OWNER), 1, null);
    assertThat(JsonPath.<List<String>>read(body, "$.months[*].month"))
        .containsExactly(current.toString());
    assertThat(JsonPath.<Boolean>read(body, "$.months[0].partial")).isTrue();
    assertThat(JsonPath.<String>read(body, "$.months[0].periodEnd")).isEqualTo(today.toString());
    assertThat(decimal(body, "$.openingCashbookBalance"))
        .isEqualByComparingTo(primaryBalance.get(1));
    assertThat(decimal(body, "$.months[0].cashbookBalance"))
        .isEqualByComparingTo(primaryBalance.get(0));
  }

  @Test
  void zeroMonthsIsRejected() throws Exception {
    assertMonthsRejected(0);
  }

  @Test
  void twentyFiveMonthsIsRejected() throws Exception {
    assertMonthsRejected(25);
  }

  @Test
  void currencyIsTheOrgDefaultCurrency() throws Exception {
    var owner = TestJwtFactory.ownerJwt(ORG_ID, OWNER);
    String settings = read(owner, "/api/settings");
    String body = trend(owner, 12, null);
    assertThat(JsonPath.<String>read(body, "$.currency"))
        .isEqualTo(JsonPath.<String>read(settings, "$.defaultCurrency"));
  }

  @Test
  void requestedAccountReturnsItsOwnSeries() throws Exception {
    var owner = TestJwtFactory.ownerJwt(ORG_ID, OWNER);
    String body = trend(owner, 12, secondAccountId);
    assertThat(JsonPath.<String>read(body, "$.trustAccountId")).isEqualTo(secondAccountId);
    assertThat(JsonPath.<String>read(body, "$.accountName")).isEqualTo("Trend Secondary");
    assertThat(decimal(body, "$.openingCashbookBalance")).isEqualByComparingTo(BigDecimal.ZERO);
    for (int i = 0; i < 10; i++) {
      assertThat(decimal(body, "$.months[%d].cashbookBalance".formatted(i)))
          .isEqualByComparingTo(BigDecimal.ZERO);
    }
    assertThat(decimal(body, "$.months[10].cashbookBalance")).isEqualByComparingTo("750.00");
    assertThat(decimal(body, "$.months[11].cashbookBalance")).isEqualByComparingTo("750.00");
    assertThat(decimal(body, "$.months[11].clientLedgerTotal")).isEqualByComparingTo("750.00");

    String base = "/api/trust-accounts/" + secondAccountId;
    assertThat(decimal(body, "$.months[11].cashbookBalance"))
        .isEqualByComparingTo(decimal(read(owner, base + "/cashbook-balance"), "$.balance"));
    assertThat(decimal(body, "$.months[11].clientLedgerTotal"))
        .isEqualByComparingTo(decimal(read(owner, base + "/total-balance"), "$.balance"));
  }

  @Test
  void unknownAccountIdReturns404() throws Exception {
    UUID unknown = UUID.randomUUID();
    mockMvc
        .perform(
            get(URL)
                .param("accountId", unknown.toString())
                .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(jsonPath("$.title").value("TrustAccount not found"))
        .andExpect(jsonPath("$.detail").value("No trustaccount found with id " + unknown));
  }

  @Test
  void callerWithoutViewTrustIsForbiddenLikeTheParts() throws Exception {
    var noTrust = TestJwtFactory.memberJwt(ORG_ID, NO_TRUST);
    for (String url :
        List.of(URL, "/api/trust-accounts/" + primaryAccountId + "/cashbook-balance")) {
      mockMvc
          .perform(get(url).with(noTrust))
          .andExpect(status().isForbidden())
          .andExpect(jsonPath("$.status").value(403))
          .andExpect(jsonPath("$.title").value("Access denied"))
          .andExpect(jsonPath("$.detail").value("Insufficient permissions for this operation"));
    }
  }

  @Test
  void systemMemberHoldsViewTrustSoSeesTheTrend() throws Exception {
    mockMvc
        .perform(get(URL).with(TestJwtFactory.memberJwt(ORG_ID, MEMBER)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.trustAccountId").value(primaryAccountId))
        .andExpect(jsonPath("$.months", hasSize(12)));
  }

  @Test
  void moduleOffIsForbidden() throws Exception {
    mockMvc
        .perform(get(URL).with(TestJwtFactory.ownerJwt(NO_MODULE_ORG_ID, NO_MODULE_OWNER)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.status").value(403))
        .andExpect(jsonPath("$.title").value("Module not enabled"))
        .andExpect(
            jsonPath("$.detail")
                .value(
                    "This feature is not enabled for your organization. "
                        + "An admin can enable it in Settings → Features."))
        .andExpect(jsonPath("$.moduleId").value("trust_accounting"));
  }

  @Test
  void noTrustAccountConfiguredReturns404() throws Exception {
    mockMvc
        .perform(get(URL).with(TestJwtFactory.ownerJwt(NO_ACCOUNT_ORG_ID, NO_ACCOUNT_OWNER)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(jsonPath("$.title").value("No trust account"))
        .andExpect(jsonPath("$.detail").value("No trust account has been configured"));
  }

  @Test
  void closedPrimaryIsNotTheDefaultAccount() throws Exception {
    var owner = TestJwtFactory.ownerJwt(CLOSED_PRIMARY_ORG_ID, CLOSED_PRIMARY_OWNER);
    String closedPrimary = createTrustAccount(owner, "Closed Primary", "62000000203", true);
    String active = createTrustAccount(owner, "Still Active", "62000000204", false);
    close(owner, closedPrimary);

    // The closed account keeps is_primary = true, but the default resolves among ACTIVE accounts.
    mockMvc
        .perform(get(URL).with(owner))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.trustAccountId").value(active))
        .andExpect(jsonPath("$.accountName").value("Still Active"))
        .andExpect(jsonPath("$.accounts", hasSize(1)))
        .andExpect(jsonPath("$.accounts[0].id").value(active));

    // No active account left → 404, not the closed primary.
    close(owner, active);
    mockMvc
        .perform(get(URL).with(owner))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(jsonPath("$.title").value("No trust account"))
        .andExpect(jsonPath("$.detail").value("No trust account has been configured"));
  }

  // --- helpers ---

  private void close(JwtRequestPostProcessor jwt, String accountId) throws Exception {
    mockMvc
        .perform(post("/api/trust-accounts/" + accountId + "/close").with(jwt))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CLOSED"));
  }

  /** Business date {@code monthsAgo} months back: the 5th, or the 1st of the current month. */
  private LocalDate day(int monthsAgo) {
    return monthsAgo == 0 ? current.atDay(1) : current.minusMonths(monthsAgo).atDay(5);
  }

  private void assertMonthsRejected(int months) throws Exception {
    mockMvc
        .perform(
            get(URL)
                .param("months", String.valueOf(months))
                .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.title").value("Invalid months"))
        .andExpect(jsonPath("$.detail").value("'months' must be between 1 and 24"));
  }

  private String trend(JwtRequestPostProcessor jwt, int months, String accountId) throws Exception {
    var request = get(URL).param("months", String.valueOf(months)).with(jwt);
    if (accountId != null) {
      request = request.param("accountId", accountId);
    }
    return mockMvc
        .perform(request)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.months", hasSize(months)))
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  private static BigDecimal decimal(String json, String path) {
    return new BigDecimal(String.valueOf((Object) JsonPath.read(json, path)));
  }

  private String read(JwtRequestPostProcessor jwt, String url) throws Exception {
    return mockMvc
        .perform(get(url).with(jwt))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  private String createCustomRole(JwtRequestPostProcessor owner, String name, String capability)
      throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/org-roles")
                    .with(owner)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name":"%s","description":"Test role","capabilities":["%s"]}"""
                            .formatted(name, capability)))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
  }

  private void assignRole(JwtRequestPostProcessor owner, String memberId, String roleId)
      throws Exception {
    mockMvc
        .perform(
            put("/api/members/" + memberId + "/role")
                .with(owner)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"orgRoleId\":\"%s\",\"capabilityOverrides\":[]}".formatted(roleId)))
        .andExpect(status().isOk());
  }

  private void enableTrustAccounting(String schema) {
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

  private String createTrustAccount(
      JwtRequestPostProcessor jwt, String name, String accountNumber, boolean primary)
      throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/trust-accounts")
                    .with(jwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "accountName": "%s",
                          "bankName": "Test Bank",
                          "branchCode": "250655",
                          "accountNumber": "%s",
                          "accountType": "GENERAL",
                          "isPrimary": %s,
                          "requireDualApproval": false,
                          "openedDate": "%s"
                        }
                        """
                            .formatted(name, accountNumber, primary, today.minusYears(2))))
            .andExpect(status().isCreated())
            .andReturn();
    return TestEntityHelper.extractId(result);
  }

  /** Records a deposit / payment / refund and returns its id. */
  private String record(
      JwtRequestPostProcessor jwt,
      String accountId,
      String kind,
      String clientId,
      String amount,
      String reference,
      LocalDate date)
      throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/trust-accounts/" + accountId + "/transactions/" + kind)
                    .with(jwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "clientId": "%s",
                          "matterId": "%s",
                          "amount": %s,
                          "reference": "%s",
                          "description": "Trust trend fixture",
                          "transactionDate": "%s"
                        }
                        """
                            .formatted(clientId, trustMatterOf(clientId), amount, reference, date)))
            .andExpect(status().isCreated())
            .andReturn();
    return TestEntityHelper.extractId(result);
  }

  private void transfer(
      JwtRequestPostProcessor jwt,
      String accountId,
      String sourceClientId,
      String targetClientId,
      String amount,
      String reference,
      LocalDate date)
      throws Exception {
    mockMvc
        .perform(
            post("/api/trust-accounts/" + accountId + "/transactions/transfer")
                .with(jwt)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "sourceClientId": "%s",
                      "sourceMatterId": "%s",
                      "targetClientId": "%s",
                      "targetMatterId": "%s",
                      "amount": %s,
                      "reference": "%s",
                      "description": "Trust trend transfer",
                      "transactionDate": "%s"
                    }
                    """
                        .formatted(
                            sourceClientId,
                            trustMatterOf(sourceClientId),
                            targetClientId,
                            trustMatterOf(targetClientId),
                            amount,
                            reference,
                            date)))
        .andExpect(status().isCreated());
  }

  /** Owner approves a debit the admin recorded (self-approval is refused). */
  private void approve(String transactionId) throws Exception {
    mockMvc
        .perform(
            post("/api/trust-transactions/" + transactionId + "/approve")
                .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("APPROVED"));
  }

  private void reject(String transactionId) throws Exception {
    mockMvc
        .perform(
            post("/api/trust-transactions/" + transactionId + "/reject")
                .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\": \"Insufficient documentation\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("REJECTED"));
  }

  private void reverse(JwtRequestPostProcessor jwt, String transactionId) throws Exception {
    mockMvc
        .perform(
            post("/api/trust-transactions/" + transactionId + "/reverse")
                .with(jwt)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\": \"Payment was made in error\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.transactionType").value("REVERSAL"));
  }

  private final Map<String, UUID> trustMatters = new HashMap<>();

  /** G89-F28: a client's trust postings sit on a matter of its own (one per client). */
  private UUID trustMatterOf(String clientId) {
    return trustMatters.computeIfAbsent(
        clientId,
        id ->
            TestTrustMatterHelper.openClientMatter(
                tenantSchema,
                transactionTemplate,
                projectRepository,
                customerRepository,
                UUID.fromString(id),
                "Trust matter " + id));
  }
}
