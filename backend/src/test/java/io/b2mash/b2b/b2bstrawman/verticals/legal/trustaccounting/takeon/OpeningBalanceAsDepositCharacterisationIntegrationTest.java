package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.takeon;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TrustBooksFixture;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.books.TrustBooksService;
import java.time.YearMonth;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
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
 * Phase 93 / 657A task 657.1 (architecture §17.3.8, §17.3.13 "without cleared_at_cutover"):
 * characterises the hazard ADR-386 removes. An opening balance posted as an ordinary DEPOSIT dated
 * at the cutover date, on an account whose bank statement at that date already holds the money, is
 * an outstanding deposit — so EQ-1 at the cutover date fails by exactly the posted amount (the bank
 * balance is counted twice). Measured on main 4299bc058 before any 657A production change; it stays
 * green after 657A (an ordinary DEPOSIT is still outstanding until a line shows it) — the take-on
 * types, not a change to DEPOSIT, are the fix.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OpeningBalanceAsDepositCharacterisationIntegrationTest {

  private static final String ORG_ID = "org_takeon_deposit_double_count";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private TrustBooksFixture fixture;
  @Autowired private TrustBooksService trustBooksService;

  private TrustBooksFixture.Tenant books;
  private UUID ownerId;

  @BeforeAll
  void setup() throws Exception {
    var tenantSchema =
        provisioningService
            .provisionTenant(ORG_ID, "Take-on Deposit Double Count Org", null)
            .schemaName();
    ownerId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                ORG_ID,
                "user_takeon_dd_owner",
                "takeon_dd@test.com",
                "Take-on DD Owner",
                "owner"));
    fixture.enableTrustModule(tenantSchema);
    books = fixture.tenant(tenantSchema, ORG_ID, ownerId);
  }

  @AfterAll
  void booksBalanceFirmWide() {
    books.assertFirmBalanced();
  }

  @Test
  void openingBalancePostedAsDepositAtCutover_isOutstanding_andEq1FailsByItsAmount() {
    var account = books.freshAccount(false);
    var client = books.newClient("CHAR");
    var cutover = YearMonth.from(books.today()).minusMonths(4).atEndOfMonth();

    books.deposit(account, client, "300000.00", cutover);
    // The bank's statement at the cutover already contains the brought-forward money
    var statementId =
        books.statementDirect(account, cutover.withDayOfMonth(1), cutover, "0.00", "300000.00");

    var bank = new TrustBooksService.BankPosition[1];
    books.as(
        ownerId, () -> bank[0] = trustBooksService.bankPosition(account, statementId, cutover));
    var cashbook = books.snapshotAsAt(account, cutover).cashbook();

    assertThat(cashbook).isEqualByComparingTo("300000.00");
    assertThat(bank[0].outstandingDeposits()).isEqualByComparingTo("300000.00");
    assertThat(bank[0].adjustedBankBalance()).isEqualByComparingTo("600000.00");
    // EQ-1 at C is out by exactly the posted amount
    assertThat(bank[0].adjustedBankBalance().subtract(cashbook)).isEqualByComparingTo("300000.00");
  }
}
