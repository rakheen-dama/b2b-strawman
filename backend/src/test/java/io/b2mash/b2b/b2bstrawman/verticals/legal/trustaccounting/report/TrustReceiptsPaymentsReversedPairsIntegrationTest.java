package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.report;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.reporting.ReportResult;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsService;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TrustBooksFixture;
import io.b2mash.b2b.b2bstrawman.testutil.TrustBooksHarness.Expected;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * FU-13 (Phase 92 649B F1 / F-652E-5, F-650C-2): Receipts & Payments counts every posted row — a
 * reversed original and its contra included — by its own transaction date, each with its own
 * cashbook effect; Section 35 part (1) agrees with it.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TrustReceiptsPaymentsReversedPairsIntegrationTest {

  private static final String ORG_ID = "org_rp_reversed_pairs";
  private static final String FROM = "2026-01-01";
  private static final String TO = "2026-03-31";
  private static final LocalDate SAME_PERIOD = LocalDate.of(2026, 3, 10);
  private static final LocalDate AFTER_PERIOD = LocalDate.of(2026, 4, 15);

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSettingsRepository orgSettingsRepository;
  @Autowired private OrgSettingsService orgSettingsService;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private TrustBooksFixture fixture;
  @Autowired private TrustReceiptsPaymentsQuery receiptsPaymentsQuery;
  @Autowired private Section35DataPackQuery section35DataPackQuery;

  private String tenantSchema;
  private UUID ownerMemberId;
  private UUID approverMemberId;

  @BeforeAll
  void setup() throws Exception {
    tenantSchema =
        provisioningService.provisionTenant(ORG_ID, "RP Reversed Pairs Org", null).schemaName();
    ownerMemberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, "user_rp_owner", "rp_owner@test.com", "RP Owner", "owner"));
    approverMemberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                ORG_ID,
                "user_rp_approver",
                "rp_approver@test.com",
                "RP Approver",
                "admin"));
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var settings = orgSettingsService.getOrCreateForCurrentTenant();
                      settings.setEnabledModules(List.of("trust_accounting"));
                      orgSettingsRepository.save(settings);
                    }));
  }

  private <T> T inTenant(Supplier<T> action) {
    var out = new Object[1];
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, ownerMemberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(() -> transactionTemplate.executeWithoutResult(tx -> out[0] = action.get()));
    @SuppressWarnings("unchecked")
    T value = (T) out[0];
    return value;
  }

  /** A credit's reversal awaits approval (ruling #1); the approver posts the contra. */
  private void reverseDeposit(
      TrustBooksFixture.Tenant books, UUID account, UUID deposit, String amount, LocalDate date) {
    books.handSetApproved(deposit);
    var contra = books.reverse(account, deposit, Expected.none(), date);
    books.approve(account, contra.id(), approverMemberId, Expected.cashbookAndClient("-" + amount));
  }

  private ReportResult receiptsPayments(UUID account) {
    return inTenant(
        () -> {
          var params = new HashMap<String, Object>();
          params.put("trust_account_id", account);
          params.put("dateFrom", FROM);
          params.put("dateTo", TO);
          return receiptsPaymentsQuery.executeAll(params);
        });
  }

  private ReportResult section35(UUID account) {
    return inTenant(
        () ->
            section35DataPackQuery.executeAll(
                Map.of("trust_account_id", account, "financial_year_end", TO)));
  }

  private static BigDecimal sum(List<Map<String, Object>> rows, String key) {
    return rows.stream()
        .filter(r -> r.get(key) instanceof BigDecimal)
        .map(r -> (BigDecimal) r.get(key))
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  /** (a) a deposit and a payment, each reversed in the period: both halves of each pair count. */
  @Test
  void pairsReversedInThePeriod_countBothHalves_netUnchanged() {
    var books = fixture.tenant(tenantSchema, ORG_ID, ownerMemberId);
    UUID account = books.freshAccount(false);
    var client = books.newClient("rp same period");
    books.deposit(account, client, "1000.00"); // stays
    UUID deposit = books.deposit(account, client, "400.00");
    UUID payment = books.requestPayment(account, client, "150.00");
    books.approve(account, payment, approverMemberId, Expected.cashbookAndClient("-150.00"));

    reverseDeposit(books, account, deposit, "400.00", SAME_PERIOD);
    books.reverse(account, payment, Expected.cashbookAndClient("150.00"), SAME_PERIOD);

    var result = receiptsPayments(account);
    // receipts: 1000 + 400 + (contra of payment) 150; payments: 150 + (contra of deposit) 400
    assertThat(result.rows()).hasSize(5);
    assertThat((BigDecimal) result.summary().get("totalReceipts")).isEqualByComparingTo("1550.00");
    assertThat((BigDecimal) result.summary().get("totalPayments")).isEqualByComparingTo("550.00");
    assertThat((BigDecimal) result.summary().get("netMovement")).isEqualByComparingTo("1000.00");
    assertThat(result.summary().get("transactionCount")).isEqualTo(5);
  }

  /** (b) a deposit in the period reversed after it: the original counts, the contra does not. */
  @Test
  void depositReversedAfterThePeriod_countsTheOriginalOnly() {
    var books = fixture.tenant(tenantSchema, ORG_ID, ownerMemberId);
    UUID account = books.freshAccount(false);
    var client = books.newClient("rp after period");
    UUID deposit = books.deposit(account, client, "700.00");
    reverseDeposit(books, account, deposit, "700.00", AFTER_PERIOD);

    var result = receiptsPayments(account);
    assertThat(result.rows()).hasSize(1);
    assertThat((BigDecimal) result.summary().get("totalReceipts")).isEqualByComparingTo("700.00");
    assertThat((BigDecimal) result.summary().get("totalPayments")).isEqualByComparingTo("0.00");
  }

  /** (c) Section 35 part (1) carries the same rows and totals as the R&P report. */
  @Test
  void section35PartOne_agreesWithReceiptsPayments() {
    var books = fixture.tenant(tenantSchema, ORG_ID, ownerMemberId);
    UUID account = books.freshAccount(false);
    var client = books.newClient("rp section 35");
    books.deposit(account, client, "900.00");
    UUID pairDeposit = books.deposit(account, client, "300.00");
    UUID later = books.deposit(account, client, "200.00");
    reverseDeposit(books, account, pairDeposit, "300.00", SAME_PERIOD);
    reverseDeposit(books, account, later, "200.00", AFTER_PERIOD);

    var rp = receiptsPayments(account);
    var pack = section35(account);
    var partOne =
        pack.rows().stream()
            .filter(r -> "Trust Receipts & Payments".equals(r.get("_section")))
            .filter(r -> !Boolean.TRUE.equals(r.get("_total")))
            .toList();

    assertThat(partOne).hasSameSizeAs(rp.rows());
    assertThat(sum(partOne, "credit"))
        .isEqualByComparingTo((BigDecimal) rp.summary().get("totalReceipts"));
    assertThat(sum(partOne, "debit"))
        .isEqualByComparingTo((BigDecimal) rp.summary().get("totalPayments"));
    // 900 + 300 + 200 receipts; 300 contra payment (the 200 contra is after the year end)
    assertThat((BigDecimal) rp.summary().get("totalReceipts")).isEqualByComparingTo("1400.00");
    assertThat((BigDecimal) rp.summary().get("totalPayments")).isEqualByComparingTo("300.00");
  }
}
