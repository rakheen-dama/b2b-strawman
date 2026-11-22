package io.b2mash.b2b.b2bstrawman.demo.seed;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.OrganizationRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.template.VariableFormatter;
import io.b2mash.b2b.b2bstrawman.testutil.BooksSnapshot;
import io.b2mash.b2b.b2bstrawman.testutil.TrustBooksFixture;
import io.b2mash.b2b.b2bstrawman.testutil.TrustBooksHarness;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.books.TrustIntegrityService;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.books.TrustIntegrityService.TrustIntegrityReport;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.ClientBalanceRow;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustLedgerQueryRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Phase 92 slice 646A (task 646.8, R2, §16.3.15, founder ruling #2): the legal demo seeder posts
 * through {@code TrustPostingService}, so a freshly seeded demo tenant's books balance — EQ-2:
 * cashbook R27 000,00 = client ledgers R30 000,00 + (−R3 000,00). The second client is left in
 * debit by R3 000,00 on purpose (the posting service applies no sufficiency check), so the demo
 * shows a creditor in debit. Before 646A the seeder wrote card-less rows straight to the repository
 * and the books were R27 000,00 out (649A path 11). 646B: the seeded account also carries its two
 * non-client creditor ledgers (ADR-376).
 *
 * <p>FU-27 (Phase 92 exit item 2, §16.9.2 path 11 "one client flagged in debit"): the client in
 * debit is also asserted as the integrity check's {@code CLIENT_LEDGER_IN_DEBIT} flag, not only as
 * a −R3 000,00 balance. {@code TrustIntegrityService.check} needs the {@code trust_accounting}
 * module, so the module is enabled after seeding (a real demo tenant runs the legal-za profile,
 * which enables it); the seeded rows are not touched.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LegalDemoTrustBooksIntegrationTest {

  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrganizationRepository organizationRepository;
  @Autowired private LegalDemoDataSeeder legalDemoDataSeeder;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private TrustBooksHarness harness;
  @Autowired private TrustLedgerQueryRepository ledgerQueries;
  @Autowired private TrustBooksFixture fixture;
  @Autowired private TrustIntegrityService integrityService;
  @Autowired private Clock clock;

  private String schema;
  private UUID accountId;

  @BeforeAll
  void seedDemoTenant() {
    var slug = "legal-demo-books-" + UUID.randomUUID().toString().substring(0, 8);
    schema = provisioningService.provisionTenant(slug, "Legal Demo Books Org", null).schemaName();
    var orgId = organizationRepository.findByExternalOrgId(slug).orElseThrow().getId();
    legalDemoDataSeeder.seed(schema, orgId);

    var accountIds =
        jdbcTemplate.queryForList(
            "SELECT id FROM \"%s\".trust_accounts".formatted(schema), UUID.class);
    assertThat(accountIds).as("the seeder creates one trust account").hasSize(1);
    accountId = accountIds.getFirst();
    // FU-27: after the seeder, so the seeded rows are as seeded; TrustIntegrityService needs it.
    fixture.enableTrustModule(schema);
  }

  @Test
  void seededBooks_satisfyEq2_cashbookEqualsClientLedgers() {
    var snapshot = new BooksSnapshot[1];
    ScopedValue.where(RequestScopes.TENANT_ID, schema)
        .run(
            () -> {
              harness.assertBalanced(accountId); // EQ-2 and EQ-6
              snapshot[0] = harness.snapshot(accountId);
            });

    assertThat(snapshot[0].cashbook()).as("seeded cashbook").isEqualByComparingTo("27000.00");
    assertThat(snapshot[0].clientLedgers())
        .as("seeded client ledgers")
        .isEqualByComparingTo("27000.00");
    assertThat(snapshot[0].difference()).isEqualByComparingTo("0.00");
  }

  /**
   * §16.9.2 path 11, "one client flagged in debit": the per-client balances are R30 000,00 and −R3
   * 000,00, and (FU-27) the integrity check raises exactly one {@code CLIENT_LEDGER_IN_DEBIT}
   * warning for R3 000,00 naming that client, while the books still balance (no blocking flag; the
   * production services agree with the oracle flag by flag).
   */
  @Test
  void seededClients_holdR30000_andMinusR3000_exactlyOneInDebit() {
    var balances = new ArrayList<BigDecimal>();
    var oracle = new ArrayList<BigDecimal>();
    var debtors = new ArrayList<UUID>();
    var report = new TrustIntegrityReport[1];
    ScopedValue.where(RequestScopes.TENANT_ID, schema)
        .run(
            () -> {
              List<ClientBalanceRow> rows =
                  transactionTemplate.execute(tx -> ledgerQueries.clientBalances(accountId, null));
              for (var row : rows) {
                balances.add(row.getBalance());
                oracle.add(harness.clientLedger(accountId, row.getCustomerId()));
                if (row.getBalance().signum() < 0) {
                  debtors.add(row.getCustomerId());
                }
              }
              report[0] = integrityService.check(accountId, null, null);
              harness.assertServicesAgree(accountId, LocalDate.now(clock));
            });

    assertThat(balances)
        .as("derived per-client balances")
        .usingElementComparator(BigDecimal::compareTo)
        .containsExactlyInAnyOrder(new BigDecimal("30000.00"), new BigDecimal("-3000.00"));
    assertThat(oracle)
        .as("oracle per-client balances")
        .usingElementComparator(BigDecimal::compareTo)
        .containsExactlyInAnyOrderElementsOf(balances);
    var inDebit = balances.stream().filter(b -> b.signum() < 0).toList();
    assertThat(inDebit).as("clients in debit").hasSize(1);
    assertThat(inDebit.getFirst()).isEqualByComparingTo("-3000.00");

    // FU-27: the debit is an integrity flag, not only a balance.
    var flags =
        report[0].flags().stream()
            .filter(f -> TrustIntegrityService.CLIENT_LEDGER_IN_DEBIT.equals(f.code()))
            .toList();
    assertThat(flags).as("CLIENT_LEDGER_IN_DEBIT flags on the seeded books").hasSize(1);
    var flag = flags.getFirst();
    assertThat(flag.severity()).isEqualTo(TrustIntegrityService.WARNING);
    assertThat(flag.amount()).isEqualByComparingTo("3000.00");
    assertThat(flag.message())
        .isEqualTo(
            "1 client ledger is "
                + VariableFormatter.formatCurrencyText(
                    new BigDecimal("3000.00"), VariableFormatter.resolveLocale("ZAR"))
                + " in debit.");
    assertThat(flag.subjects())
        .singleElement()
        .satisfies(
            s -> {
              assertThat(s.kind()).isEqualTo(TrustIntegrityService.SUBJECT_CLIENT);
              assertThat(s.id()).isEqualTo(debtors.getFirst());
              assertThat(s.amount()).isEqualByComparingTo("3000.00");
            });
    assertThat(report[0].balanced()).as("balanced although a client is in debit").isTrue();
    assertThat(report[0].flags())
        .as("no blocking flag on the seeded books")
        .noneMatch(TrustIntegrityService::isBlocking);
    assertThat(report[0].position().difference()).isEqualByComparingTo("0.00");
  }

  @Test
  void seededAccount_carriesBothNonClientCreditorLedgers_atZero() {
    // 646B (D3, ADR-376): the seeder opens the two creditor accounts through the same opener as
    // TrustAccountService.createTrustAccount; nothing has been posted to them.
    var rows =
        jdbcTemplate.queryForList(
            "SELECT id, account_type FROM \"%s\".trust_creditor_accounts WHERE trust_account_id = ?"
                .formatted(schema),
            accountId);
    assertThat(rows)
        .extracting(r -> r.get("account_type"))
        .containsExactlyInAnyOrder("FUND_INTEREST_AND_CHARGES", "UNIDENTIFIED_DEPOSITS");
    ScopedValue.where(RequestScopes.TENANT_ID, schema)
        .run(
            () -> {
              for (var row : rows) {
                BigDecimal balance =
                    transactionTemplate.execute(
                        tx -> ledgerQueries.creditorAccountBalance((UUID) row.get("id"), null));
                assertThat(balance)
                    .as("%s balance", row.get("account_type"))
                    .isEqualByComparingTo("0");
              }
            });
  }
}
