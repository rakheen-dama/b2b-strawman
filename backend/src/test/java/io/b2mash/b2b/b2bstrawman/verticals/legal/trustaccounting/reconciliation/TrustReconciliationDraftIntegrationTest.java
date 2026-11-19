package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TrustBooksFixture;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation.TrustReconciliationService.BankStatementResponse;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation.TrustReconciliationService.TrustReconciliationResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Phase 92 slice 652C (task 652.7, architecture §16.3.12): one reconciliation per trust account and
 * period end. The wizard (`reconciliation/new/page.tsx`) imports the statement, auto-matches it,
 * then on "Review Matches" calls {@code createReconciliation} and {@code calculateReconciliation}.
 * Any reset (re-upload, a changed account or period, a fresh wizard) drops its handle to the draft,
 * so the next "Review Matches" creates again. The second create must return the first draft, not
 * write a second row.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TrustReconciliationDraftIntegrationTest {

  private static final String ORG_ID = "org_trust_recon_draft";

  private static final LocalDate MARCH_END = LocalDate.of(2026, 3, 31);

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private TrustBooksFixture fixture;
  @Autowired private TrustReconciliationService reconciliationService;
  @Autowired private JdbcTemplate jdbc;

  private TrustBooksFixture.Tenant books;
  private UUID memberId;

  @BeforeAll
  void setup() throws Exception {
    var schema =
        provisioningService.provisionTenant(ORG_ID, "Trust Recon Draft Org", null).schemaName();
    memberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, "user_trd_owner", "trd@test.com", "TRD Owner", "owner"));
    fixture.enableTrustModule(schema);
    books = fixture.tenant(schema, ORG_ID, memberId);
  }

  /**
   * The wizard's sequence twice for one account and period end (the second after a reset dropped
   * the wizard's handle to the draft): import → auto-match → create → calculate, then create →
   * calculate again. One row for (account, period end); the second create returns the first id.
   */
  @Test
  void secondCreateForSameAccountAndPeriod_returnsTheDraft() {
    var account = books.freshAccount(false);
    var client = books.newClient("draft");
    books.deposit(account, client, "1000.00");
    var march =
        importStatement(
            account, "draft-march.csv", "01/03/2026,Deposit received,1000.00,1000.00,TRD-DEP");
    books.as(memberId, () -> reconciliationService.autoMatchStatement(march.id()));

    var first = createAndCalculate(account, MARCH_END, march.id());
    var second = createAndCalculate(account, MARCH_END, march.id());

    assertThat(reconciliationRows(account, MARCH_END))
        .as("one reconciliation row for (account, period end)")
        .isEqualTo(1);
    assertThat(second.id()).as("the second create returns the first draft").isEqualTo(first.id());
    assertThat(second.status()).isEqualTo("DRAFT");
  }

  // --- helpers -----------------------------------------------------------------------------------

  private long reconciliationRows(UUID account, LocalDate periodEnd) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM \"%s\".trust_reconciliations WHERE trust_account_id = ? AND period_end = ?"
            .formatted(books.schema()),
        Long.class,
        account,
        periodEnd);
  }

  /** Imports an FNB statement through the production import (as the maker). */
  private BankStatementResponse importStatement(UUID account, String fileName, String... lines) {
    var csv =
        """
        FNB Trust Account Statement - Account 62400000000
        Date,Description,Amount,Balance,Reference
        %s
        """
            .formatted(String.join("\n", lines));
    var file =
        new MockMultipartFile("file", fileName, "text/csv", csv.getBytes(StandardCharsets.UTF_8));
    var statement = new BankStatementResponse[1];
    books.as(
        memberId, () -> statement[0] = reconciliationService.importBankStatement(account, file));
    return statement[0];
  }

  /** The wizard's "Review Matches": create, then calculate (production service calls). */
  private TrustReconciliationResponse createAndCalculate(
      UUID account, LocalDate periodEnd, UUID statementId) {
    var recon = new TrustReconciliationResponse[1];
    books.as(
        memberId,
        () -> {
          var created = reconciliationService.createReconciliation(account, periodEnd, statementId);
          recon[0] = reconciliationService.calculateReconciliation(created.id());
        });
    return recon[0];
  }
}
