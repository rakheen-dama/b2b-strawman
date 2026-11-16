package io.b2mash.b2b.b2bstrawman.demo.seed;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.OrganizationRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.BooksSnapshot;
import io.b2mash.b2b.b2bstrawman.testutil.TrustBooksHarness;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.ClientBalanceRow;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustLedgerQueryRepository;
import java.math.BigDecimal;
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
 * and the books were R27 000,00 out (649A path 11).
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

  @Test
  void seededClients_holdR30000_andMinusR3000_exactlyOneInDebit() {
    var balances = new ArrayList<BigDecimal>();
    var oracle = new ArrayList<BigDecimal>();
    ScopedValue.where(RequestScopes.TENANT_ID, schema)
        .run(
            () -> {
              List<ClientBalanceRow> rows =
                  transactionTemplate.execute(tx -> ledgerQueries.clientBalances(accountId, null));
              for (var row : rows) {
                balances.add(row.getBalance());
                oracle.add(harness.clientLedger(accountId, row.getCustomerId()));
              }
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
  }
}
