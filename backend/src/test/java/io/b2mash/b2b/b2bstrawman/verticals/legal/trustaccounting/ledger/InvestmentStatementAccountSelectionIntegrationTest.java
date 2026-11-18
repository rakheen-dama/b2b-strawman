package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestEntityHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TestTrustInvestmentAccountHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TestTrustMatterHelper;
import io.b2mash.b2b.b2bstrawman.verticals.legal.statement.StatementOfAccountContextBuilder;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.InvestmentBasis;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 648A review cycle 4 (High): an approved section 86(4) placement posts its receive leg ({@code
 * INV_RECEIVE}) on the investment account with the client's id, on the client's <i>investment</i>
 * ledger. The two client-visible statements — the Statement of Account trust block and the Client
 * Trust Statement — pick the client's trust account from the accounts the client has activity on,
 * then read that account's <i>client</i> ledger. If the investment account counts as "activity",
 * then with a second candidate the builders fall back to the primary GENERAL account (not this
 * client's here) and then to the lowest id — and print the investment account with R0 and no lines.
 *
 * <p>Setup that turns the coin flip into a guaranteed failure: a primary GENERAL account the
 * investor never uses, the investor's money on a non-primary GENERAL account {@code G}, and an
 * investment account {@code I} whose id sorts before {@code G}'s (Postgres orders uuid bytewise =
 * the canonical lowercase hex string). Both ids are fixed by construction ({@link #rekeyAccount}),
 * so the order never depends on a random draw.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InvestmentStatementAccountSelectionIntegrationTest {

  private static final String ORG_ID = "org_inv_statement_account";
  private static final String OWNER_SUB = "user_isa_owner";
  private static final String APPROVER_SUB = "user_isa_approver";
  private static final Set<String> TRUST_READER =
      Set.of("VIEW_TRUST", "MANAGE_TRUST", "VIEW_LEGAL", "GENERATE_STATEMENT_OF_ACCOUNT");
  // I sorts before G in Postgres's uuid order by construction (lowest and highest v4-shaped ids).
  private static final UUID INVESTMENT_ACCOUNT_ID =
      UUID.fromString("00000000-0000-4000-8000-000000000000");
  private static final UUID FUNDING_ACCOUNT_ID =
      UUID.fromString("ffffffff-ffff-4fff-bfff-ffffffffffff");

  @Autowired private MockMvc mockMvc;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private ProjectRepository projectRepository;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private TrustTransactionRepository trustTransactionRepository;
  @Autowired private StatementOfAccountContextBuilder statementContextBuilder;
  @Autowired private ClientTrustStatementContext clientTrustStatementContext;
  @Autowired private Clock clock;
  @Autowired private JdbcTemplate jdbcTemplate;

  private String tenantSchema;
  private UUID ownerId;
  private LocalDate today;
  private UUID fundingAccountId;
  private UUID investmentAccountId;
  private UUID investor;
  private UUID investorMatter;

  @BeforeAll
  void setup() throws Exception {
    tenantSchema =
        provisioningService
            .provisionTenant(ORG_ID, "Investment Statement Account Attorneys", "legal-za")
            .schemaName();
    ownerId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, OWNER_SUB, "isa_owner@test.com", "ISA Owner", "owner"));
    // Single-approval mode refuses self-approval: a second owner approves the placement
    TestMemberHelper.syncMember(
        mockMvc, ORG_ID, APPROVER_SUB, "isa_approver@test.com", "ISA Approver", "owner");
    today = LocalDate.now(clock);

    openGeneralAccount("ISA Primary Trust Account", "62000000800", true);
    fundingAccountId =
        rekeyAccount(
            openGeneralAccount("ISA Second Trust Account", "62000000801", false),
            FUNDING_ACCOUNT_ID);
    investmentAccountId =
        rekeyAccount(
            TestTrustInvestmentAccountHelper.openInvestmentAccount(
                mockMvc,
                TestJwtFactory.ownerJwt(ORG_ID, OWNER_SUB),
                "ISA Section 86(4) Investments",
                "62000000900",
                InvestmentBasis.CLIENT_INSTRUCTION),
            INVESTMENT_ACCOUNT_ID);
    assertThat(investmentAccountId.toString()).isLessThan(fundingAccountId.toString());

    investor =
        UUID.fromString(
            TestEntityHelper.createCustomer(
                mockMvc,
                TestJwtFactory.ownerJwt(ORG_ID, OWNER_SUB),
                "ISA Investor",
                "isa_inv@test.com"));
    investorMatter =
        TestTrustMatterHelper.openClientMatter(
            tenantSchema,
            transactionTemplate,
            projectRepository,
            customerRepository,
            investor,
            "ISA trust matter");
    deposit("40000.00");

    var placed = place("15000.00");
    var placementId = (String) JsonPath.read(placed, "$.depositTransactionId");
    mockMvc
        .perform(
            post("/api/trust-transactions/" + placementId + "/approve")
                .with(TestJwtFactory.ownerJwt(ORG_ID, APPROVER_SUB)))
        .andExpect(status().isOk());
  }

  @Test
  void accountCandidates_areTheClientLedgerAccountsOnly_notTheInvestmentAccount() {
    List<UUID> candidates =
        inTenant(
            () -> trustTransactionRepository.findDistinctTrustAccountIdsByCustomerId(investor));

    assertThat(candidates)
        .as(
            "the investment account carries the investor's investment ledger, not their trust ledger")
        .containsExactly(fundingAccountId);
  }

  @Test
  @SuppressWarnings("unchecked")
  void statementOfAccount_trustBlock_readsTheFundingAccount() {
    Map<String, Object> context =
        inTenant(() -> statementContextBuilder.build(investorMatter, today.minusDays(60), today));

    var trust = (Map<String, Object>) context.get("trust");
    assertThat(trust.get("status")).isEqualTo("available");
    // R40 000 deposited, R15 000 placed: R25 000 left on the investor's general ledger on G
    assertThat(new BigDecimal(trust.get("closing_balance").toString()))
        .isEqualByComparingTo("25000.00");
    assertThat((List<?>) trust.get("deposits")).hasSize(1);
    assertThat((List<?>) trust.get("payments")).hasSize(1);
  }

  @Test
  @SuppressWarnings("unchecked")
  void clientTrustStatement_readsTheFundingAccount() {
    var block = inTenant(() -> clientTrustStatementContext.buildFor(investor)).orElseThrow();

    assertThat(block.get("status")).isEqualTo("available");
    assertThat(block.get("account_name")).isEqualTo("ISA Second Trust Account");
    assertThat(new BigDecimal(block.get("closing_balance").toString()))
        .isEqualByComparingTo("25000.00");
    var lines = (List<Map<String, Object>>) block.get("lines");
    assertThat(lines)
        .extracting(line -> line.get("transactionType"))
        .containsExactly("DEPOSIT", "INV_PLACE");
  }

  // --- Helpers ---

  private UUID openGeneralAccount(String name, String accountNumber, boolean primary)
      throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/trust-accounts")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER_SUB))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "accountName": "%s",
                          "bankName": "First National Bank",
                          "branchCode": "250655",
                          "accountNumber": "%s",
                          "accountType": "GENERAL",
                          "isPrimary": %s,
                          "requireDualApproval": false,
                          "openedDate": "2025-01-01"
                        }
                        """
                            .formatted(name, accountNumber, primary)))
            .andExpect(status().isCreated())
            .andReturn();
    return UUID.fromString(TestEntityHelper.extractId(result));
  }

  /**
   * Gives a freshly opened account (no trust rows yet) the id {@code to}: clones the row under the
   * new id, moves the default creditor accounts opened with it, then deletes the original. Every
   * step keeps the foreign keys valid; any other row already pointing at the account makes the
   * delete fail loudly rather than leave a dangling fixture.
   */
  private UUID rekeyAccount(UUID from, UUID to) {
    jdbcTemplate.update(
        """
        INSERT INTO "%1$s".trust_accounts
        SELECT (jsonb_populate_record(NULL::"%1$s".trust_accounts,
                                      to_jsonb(a) || jsonb_build_object('id', ?::uuid))).*
        FROM "%1$s".trust_accounts a WHERE a.id = ?
        """
            .formatted(tenantSchema),
        to,
        from);
    jdbcTemplate.update(
        "UPDATE \"%s\".trust_creditor_accounts SET trust_account_id = ? WHERE trust_account_id = ?"
            .formatted(tenantSchema),
        to,
        from);
    assertThat(
            jdbcTemplate.update(
                "DELETE FROM \"%s\".trust_accounts WHERE id = ?".formatted(tenantSchema), from))
        .isEqualTo(1);
    return to;
  }

  private void deposit(String amount) throws Exception {
    mockMvc
        .perform(
            post("/api/trust-accounts/" + fundingAccountId + "/transactions/deposit")
                .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER_SUB))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "clientId": "%s",
                      "matterId": "%s",
                      "amount": %s,
                      "reference": "ISA-DEP-1",
                      "description": "Deposit",
                      "transactionDate": "%s"
                    }
                    """
                        .formatted(investor, investorMatter, amount, today.minusDays(30))))
        .andExpect(status().isCreated());
  }

  private String place(String principal) throws Exception {
    return mockMvc
        .perform(
            post("/api/trust-accounts/" + fundingAccountId + "/investments")
                .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER_SUB))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "investmentAccountId": "%s",
                      "clientId": "%s",
                      "matterId": "%s",
                      "institution": "Investec",
                      "accountNumber": "ISA-FD-1",
                      "principal": %s,
                      "interestRate": 0.0650,
                      "depositDate": "%s",
                      "maturityDate": "%s",
                      "investmentBasis": "CLIENT_INSTRUCTION"
                    }
                    """
                        .formatted(
                            investmentAccountId,
                            investor,
                            investorMatter,
                            principal,
                            today.minusDays(20),
                            today.plusDays(150))))
        .andExpect(status().isCreated())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  private <T> T inTenant(Supplier<T> body) {
    return ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, ownerId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .where(RequestScopes.CAPABILITIES, TRUST_READER)
        .call(() -> transactionTemplate.execute(tx -> body.get()));
  }
}
