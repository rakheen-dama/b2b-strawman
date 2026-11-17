package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.creditor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.audit.AuditEvent;
import io.b2mash.b2b.b2bstrawman.audit.AuditEventRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TrustBooksFixture;
import io.b2mash.b2b.b2bstrawman.testutil.TrustBooksFixture.StatementLine;
import io.b2mash.b2b.b2bstrawman.testutil.TrustBooksHarness;
import io.b2mash.b2b.b2bstrawman.testutil.TrustBooksHarness.Expected;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation.BankStatementLine;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation.BankStatementLineRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation.BankStatementRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustLedger;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransaction;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Phase 92 slice 647B (tasks 647.10, 647.11; ADR-376; architecture §16.2.5, §16.3.5, §16.3.6,
 * §16.9.2 path 14): unidentified deposits — the hold. A credit line recorded from the statement as
 * {@code UNIDENTIFIED_DEPOSIT} posts one row on the account's unidentified-deposits ledger and
 * opens a HELD item the row names; the items are listed oldest first with their age; unmatching the
 * line while the item is held reverses the row with a same-date contra that names the item, and the
 * item becomes {@code REVERSED}. Every posting step is asserted through {@link
 * TrustBooksHarness#assertMoves}, EQ-4 (the item's balance from its own rows) is asserted as at the
 * day before, the line's date and today, and the class ends with {@code assertFirmBalanced(today)}.
 *
 * <p>"Unmatch refused while a resolution is pending" cannot be reached through the product in 647B
 * (allocation and payment to the Fund are 647D), but the guard is 647B code: {@link
 * #unmatch_whileAResolutionIsPending_isRefused_andNothingMoves} simulates a pending resolution with
 * one test-only write ({@code resolution_transaction_id} = the item's own opening row — FK-valid,
 * status stays HELD) and restores it. 647D replaces it with the real allocation and Fund cases
 * (paths 15, 16).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UnidentifiedDepositIntegrationTest {

  private static final String ORG_ID = "org_unidentified_deposits";
  private static final String OWNER = "user_uid_owner";
  private static final String MEMBER = "user_uid_member";

  /** A member on a custom role holding VIEW_LEGAL only — no VIEW_TRUST. */
  private static final String NO_TRUST = "user_uid_no_trust";

  /** Module off — its own tenant, never given {@code trust_accounting}. */
  private static final String NO_MODULE_ORG_ID = "org_unidentified_deposits_nomod";

  private static final String NO_MODULE_OWNER = "user_uid_nomod_owner";

  private static final String UNIDENTIFIED = "UNIDENTIFIED_DEPOSITS";
  private static final String RECORD_BODY = "{\"classification\":\"UNIDENTIFIED_DEPOSIT\"}";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private TrustBooksFixture fixture;
  @Autowired private TrustBooksHarness harness;
  @Autowired private TrustTransactionRepository transactionRepository;
  @Autowired private BankStatementLineRepository lineRepository;
  @Autowired private BankStatementRepository statementRepository;
  @Autowired private UnidentifiedDepositRepository depositRepository;
  @Autowired private AuditEventRepository auditEventRepository;
  @Autowired private JdbcTemplate jdbc;

  private TrustBooksFixture.Tenant books;
  private UUID ownerId;

  @BeforeAll
  void setup() throws Exception {
    var schema =
        provisioningService.provisionTenant(ORG_ID, "Unidentified Deposits Org", null).schemaName();
    ownerId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, OWNER, "uid_owner@test.com", "UID Owner", "owner"));
    TestMemberHelper.syncMember(
        mockMvc, ORG_ID, MEMBER, "uid_member@test.com", "UID Member", "member");
    var noTrustMemberId =
        TestMemberHelper.syncMember(
            mockMvc, ORG_ID, NO_TRUST, "uid_no_trust@test.com", "UID No Trust", "member");
    fixture.enableTrustModule(schema);
    assignRole(noTrustMemberId, createCustomRole("UID No Trust Role", "VIEW_LEGAL"));
    books = fixture.tenant(schema, ORG_ID, ownerId);

    provisioningService.provisionTenant(NO_MODULE_ORG_ID, "Unidentified Deposits NoMod Org", null);
    TestMemberHelper.syncMember(
        mockMvc,
        NO_MODULE_ORG_ID,
        NO_MODULE_OWNER,
        "uid_nomod@test.com",
        "UID NoMod Owner",
        "owner");
  }

  @AfterAll
  void booksBalanceFirmWide() {
    books.assertFirmBalanced();
  }

  /** A date ten days back: in the past, so a recorded line can also be unmatched. */
  private LocalDate pastDate() {
    return books.today().minusDays(10);
  }

  // --- path 14: from a line --------------------------------------------------------------

  @Test
  void recordFromACreditLine_opensAHeldItem_thatItsRowNames() throws Exception {
    var account = books.freshAccount(false);
    var date = pastDate();
    var line = books.unmatchedLine(account, date, "300.00", "UID-REF-1");

    var body =
        recordMoves(
            account,
            line,
            "{\"classification\":\"UNIDENTIFIED_DEPOSIT\",\"notes\":\"Ask the bank\"}",
            "300.00");

    var itemId = UUID.fromString(JsonPath.read(body, "$.unidentifiedDepositId"));
    var rowId = UUID.fromString(JsonPath.read(body, "$.transactions[0].id"));
    assertThat(JsonPath.<String>read(body, "$.transactions[0].transactionType"))
        .isEqualTo("UNIDENTIFIED_DEPOSIT");
    assertThat(JsonPath.<String>read(body, "$.transactions[0].typeLabel"))
        .isEqualTo("Unidentified deposit");
    assertThat(JsonPath.<String>read(body, "$.transactions[0].ledger")).isEqualTo("NON_CLIENT");
    assertThat(JsonPath.<String>read(body, "$.line.classificationLabel"))
        .isEqualTo("Unidentified deposit");

    var row = row(rowId);
    assertThat(row.getLedger()).isEqualTo(TrustLedger.NON_CLIENT);
    assertThat(row.getStatus()).isEqualTo("RECORDED");
    assertThat(row.getUnidentifiedDepositId()).isEqualTo(itemId);
    assertThat(row.getCreditorAccountId()).isEqualTo(books.creditorAccount(account, UNIDENTIFIED));
    assertThat(row.getBankStatementLineId()).isEqualTo(line.lineId());
    assertThat(row.getTransactionDate()).isEqualTo(date);
    assertThat(row.getCashbookEffect()).isEqualByComparingTo("300.00");
    assertThat(row.getLedgerEffect()).isEqualByComparingTo("300.00");
    assertThat(row.getCustomerId()).isNull();
    assertThat(row.getProjectId()).isNull();

    var item = item(itemId);
    assertThat(item.getStatus()).isEqualTo(UnidentifiedDepositStatus.HELD);
    assertThat(item.getTrustAccountId()).isEqualTo(account);
    assertThat(item.getCreditorAccountId()).isEqualTo(row.getCreditorAccountId());
    assertThat(item.getDepositTransactionId()).isEqualTo(rowId);
    assertThat(item.getBankStatementLineId()).isEqualTo(line.lineId());
    assertThat(item.getReceivedDate()).isEqualTo(date);
    assertThat(item.getBankDescription()).isEqualTo("Trust books statement line 1");
    assertThat(item.getBankReference()).isEqualTo("UID-REF-1");
    assertThat(item.getAmount()).isEqualByComparingTo("300.00");
    assertThat(item.getResolutionTransactionId()).isNull();
    assertThat(item.getResolvedDate()).isNull();
    assertThat(item.getNotes()).isEqualTo("Ask the bank");

    var recorded = line(line.lineId());
    assertThat(recorded.getMatchStatus()).isEqualTo("RECORDED");
    assertThat(recorded.getClassification()).isEqualTo("UNIDENTIFIED_DEPOSIT");
    assertThat(recorded.getTrustTransactionId()).isEqualTo(rowId);
    assertThat(matchedCount(line)).isEqualTo(1);

    // EQ-4: not open the day before, open on the line's date and today.
    assertThat(books.snapshotAsAt(account, date.minusDays(1)).unidentified())
        .isEqualByComparingTo("0");
    assertThat(books.snapshotAsAt(account, date).unidentified()).isEqualByComparingTo("300.00");
    books.assertBalancedAsAt(account, date.minusDays(1));
    books.assertBalancedAsAt(account, date);
    books.assertBalancedAsAt(account, books.today());
    books.assertServicesAgree(account);
  }

  // --- the list endpoint -----------------------------------------------------------------

  @Test
  void list_oldestFirst_withAgeAndTheOneYearFlag_andAStatusFilter() throws Exception {
    var account = books.freshAccount(false);
    var today = books.today();
    var newer = books.unmatchedLine(account, today.minusDays(365), "100.00", "UID-365");
    var older = books.unmatchedLine(account, today.minusDays(366), "200.00", "UID-366");
    var recent = books.unmatchedLine(account, pastDate(), "50.00", "UID-RECENT");
    var newerId = recordItem(account, newer, "100.00");
    var olderId = recordItem(account, older, "200.00");
    var recentId = recordItem(account, recent, "50.00");
    unmatchMoves(account, recent, "-50.00");

    mockMvc
        .perform(get(itemsUrl(account)).with(owner()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.page.totalElements").value(3))
        .andExpect(jsonPath("$.content.length()").value(3))
        .andExpect(jsonPath("$.content[0].id").value(olderId.toString()))
        .andExpect(jsonPath("$.content[0].trustAccountId").value(account.toString()))
        .andExpect(jsonPath("$.content[0].receivedDate").value(today.minusDays(366).toString()))
        .andExpect(jsonPath("$.content[0].bankDescription").value("Trust books statement line 1"))
        .andExpect(jsonPath("$.content[0].bankReference").value("UID-366"))
        .andExpect(jsonPath("$.content[0].amount").value(200.0))
        .andExpect(jsonPath("$.content[0].status").value("HELD"))
        .andExpect(jsonPath("$.content[0].ageDays").value(366))
        .andExpect(jsonPath("$.content[0].olderThanOneYear").value(true))
        .andExpect(jsonPath("$.content[0].allocatedClientId").isEmpty())
        .andExpect(jsonPath("$.content[0].allocatedClientName").isEmpty())
        .andExpect(jsonPath("$.content[0].allocatedMatterId").isEmpty())
        .andExpect(jsonPath("$.content[0].allocatedMatterName").isEmpty())
        .andExpect(jsonPath("$.content[0].resolutionTransactionId").isEmpty())
        .andExpect(jsonPath("$.content[0].resolutionStatus").isEmpty())
        .andExpect(jsonPath("$.content[0].resolvedDate").isEmpty())
        .andExpect(jsonPath("$.content[0].bankStatementLineId").value(older.lineId().toString()))
        .andExpect(jsonPath("$.content[0].notes").isEmpty())
        .andExpect(jsonPath("$.content[1].id").value(newerId.toString()))
        .andExpect(jsonPath("$.content[1].ageDays").value(365))
        .andExpect(jsonPath("$.content[1].olderThanOneYear").value(false))
        .andExpect(jsonPath("$.content[2].id").value(recentId.toString()))
        .andExpect(jsonPath("$.content[2].status").value("REVERSED"))
        .andExpect(jsonPath("$.content[2].ageDays").value(10))
        .andExpect(jsonPath("$.content[2].olderThanOneYear").value(false))
        .andExpect(jsonPath("$.content[2].resolvedDate").value(pastDate().toString()));

    mockMvc
        .perform(get(itemsUrl(account) + "?status=HELD").with(owner()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.page.totalElements").value(2))
        .andExpect(jsonPath("$.content[0].id").value(olderId.toString()))
        .andExpect(jsonPath("$.content[1].id").value(newerId.toString()));
    mockMvc
        .perform(get(itemsUrl(account) + "?status=REVERSED").with(owner()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.page.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].id").value(recentId.toString()));
    mockMvc
        .perform(get(itemsUrl(account) + "?page=0&size=1").with(owner()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.page.totalElements").value(3))
        .andExpect(jsonPath("$.page.totalPages").value(3))
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].id").value(olderId.toString()));

    // The year-old items are open as at a date between them and before both.
    var between = today.minusDays(366);
    assertThat(books.snapshotAsAt(account, between).unidentified()).isEqualByComparingTo("200.00");
    books.assertBalancedAsAt(account, today.minusDays(367));
    books.assertBalancedAsAt(account, between);
    books.assertBalancedAsAt(account, today);
  }

  @Test
  void list_ofAnUnknownAccount_is404() throws Exception {
    var unknown = UUID.randomUUID();
    mockMvc
        .perform(get(itemsUrl(unknown)).with(owner()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(jsonPath("$.title").value("TrustAccount not found"))
        .andExpect(jsonPath("$.detail").value("No trustaccount found with id " + unknown));
  }

  @Test
  void list_needsViewTrust_andTheModule() throws Exception {
    var account = books.freshAccount(false);
    mockMvc
        .perform(get(itemsUrl(account)).with(TestJwtFactory.memberJwt(ORG_ID, MEMBER)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isEmpty())
        .andExpect(jsonPath("$.page.totalElements").value(0));
    mockMvc
        .perform(get(itemsUrl(account)).with(TestJwtFactory.memberJwt(ORG_ID, NO_TRUST)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.status").value(403))
        .andExpect(jsonPath("$.title").value("Access denied"))
        .andExpect(jsonPath("$.detail").value("Insufficient permissions for this operation"));
    // An unknown id: the module guard answers 403 before the 404 existence check runs.
    mockMvc
        .perform(
            get(itemsUrl(UUID.randomUUID()))
                .with(TestJwtFactory.ownerJwt(NO_MODULE_ORG_ID, NO_MODULE_OWNER)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.status").value(403))
        .andExpect(jsonPath("$.title").value("Module not enabled"))
        .andExpect(jsonPath("$.moduleId").value("trust_accounting"));
  }

  // --- path 14: unmatch while held -------------------------------------------------------

  @Test
  void unmatchWhileHeld_reversesTheRowAndTheItem_onTheLinesDate_andAReRecordOpensASecondItem()
      throws Exception {
    var account = books.freshAccount(false);
    var date = pastDate();
    var line = books.unmatchedLine(account, date, "300.00", "UID-UNMATCH");
    var firstItemId = recordItem(account, line, "300.00");
    var rowId = item(firstItemId).getDepositTransactionId();

    unmatchMoves(account, line, "-300.00");

    var contras = reversalsOf(rowId);
    assertThat(contras).hasSize(1);
    var contra = contras.getFirst();
    assertThat(contra.getTransactionType()).isEqualTo("REVERSAL");
    assertThat(contra.getStatus()).isEqualTo("RECORDED");
    assertThat(contra.getTransactionDate()).isEqualTo(date);
    assertThat(contra.getUnidentifiedDepositId()).isEqualTo(firstItemId);
    assertThat(contra.getCreditorAccountId())
        .isEqualTo(books.creditorAccount(account, UNIDENTIFIED));
    assertThat(contra.getLedger()).isEqualTo(TrustLedger.NON_CLIENT);
    assertThat(contra.getCashbookEffect()).isEqualByComparingTo("-300.00");
    assertThat(contra.getLedgerEffect()).isEqualByComparingTo("-300.00");
    assertThat(row(rowId).getStatus()).isEqualTo("REVERSED");
    assertThat(row(rowId).getBankStatementLineId()).isNull();

    var item = item(firstItemId);
    assertThat(item.getStatus()).isEqualTo(UnidentifiedDepositStatus.REVERSED);
    assertThat(item.getResolvedDate()).isEqualTo(date);
    assertThat(item.getDepositTransactionId()).isEqualTo(rowId);

    var unmatched = line(line.lineId());
    assertThat(unmatched.getMatchStatus()).isEqualTo("UNMATCHED");
    assertThat(unmatched.getClassification()).isNull();
    assertThat(unmatched.getTrustTransactionId()).isNull();
    assertThat(matchedCount(line)).isZero();

    // EQ-4: the item is never open — its balance is 0 at every date (the contra shares its date).
    for (var asAt : List.of(date.minusDays(1), date, books.today())) {
      assertThat(books.snapshotAsAt(account, asAt).unidentified())
          .as("unidentified as at %s", asAt)
          .isEqualByComparingTo("0");
      books.assertBalancedAsAt(account, asAt);
    }

    // The same line recorded again opens a second item; the first stays REVERSED.
    var secondItemId = recordItem(account, line, "300.00");
    assertThat(secondItemId).isNotEqualTo(firstItemId);
    assertThat(item(firstItemId).getStatus()).isEqualTo(UnidentifiedDepositStatus.REVERSED);
    assertThat(item(secondItemId).getStatus()).isEqualTo(UnidentifiedDepositStatus.HELD);
    assertThat(item(secondItemId).getBankStatementLineId()).isEqualTo(line.lineId());
    books.assertBalancedAsAt(account, books.today());

    // Every unidentified-ledger row (the two openings and the contra) names its item.
    var unnamed =
        jdbc.queryForObject(
            """
            SELECT count(*) FROM "%s".trust_transactions
            WHERE trust_account_id = ? AND creditor_account_id = ?
              AND posted_at IS NOT NULL AND unidentified_deposit_id IS NULL
            """
                .formatted(books.schema()),
            Long.class,
            account,
            books.creditorAccount(account, UNIDENTIFIED));
    assertThat(unnamed).isZero();
    var named =
        jdbc.queryForObject(
            """
            SELECT count(*) FROM "%s".trust_transactions
            WHERE trust_account_id = ? AND creditor_account_id = ? AND posted_at IS NOT NULL
            """
                .formatted(books.schema()),
            Long.class,
            account,
            books.creditorAccount(account, UNIDENTIFIED));
    assertThat(named).isEqualTo(3);
  }

  @Test
  void unmatch_whileAResolutionIsPending_isRefused_andNothingMoves() throws Exception {
    var account = books.freshAccount(false);
    var line = books.unmatchedLine(account, pastDate(), "75.00", "UID-PENDING");
    var itemId = recordItem(account, line, "75.00");
    var rowId = item(itemId).getDepositTransactionId();

    // Simulated pending resolution (647D builds the real one): FK-valid, status stays HELD.
    setResolution(itemId, rowId);
    try {
      moves(
          account,
          Expected.none(),
          () ->
              unmatch(line.lineId())
                  .andExpect(status().isBadRequest())
                  .andExpect(jsonPath("$.status").value(400))
                  .andExpect(jsonPath("$.title").value("Cannot unmatch statement line"))
                  .andExpect(jsonPath("$.detail").value("Undo the allocation first")));
      assertThat(line(line.lineId()).getMatchStatus()).isEqualTo("RECORDED");
      assertThat(row(rowId).getStatus()).isEqualTo("RECORDED");
      assertThat(reversalsOf(rowId)).isEmpty();
      assertThat(item(itemId).getStatus()).isEqualTo(UnidentifiedDepositStatus.HELD);
      assertThat(matchedCount(line)).isEqualTo(1);
    } finally {
      setResolution(itemId, null);
    }

    // Restored: the unmatch goes through and the books stay balanced.
    unmatchMoves(account, line, "-75.00");
    assertThat(item(itemId).getStatus()).isEqualTo(UnidentifiedDepositStatus.REVERSED);
  }

  @Test
  void theGenericReverseEndpoint_refusesTheOpeningRow() throws Exception {
    var account = books.freshAccount(false);
    var line = books.unmatchedLine(account, pastDate(), "60.00", "UID-GENERIC");
    var itemId = recordItem(account, line, "60.00");
    var rowId = item(itemId).getDepositTransactionId();

    moves(
        account,
        Expected.none(),
        () ->
            mockMvc
                .perform(
                    post("/api/trust-transactions/{id}/reverse", rowId)
                        .with(owner())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Wrong\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Cannot reverse transaction"))
                .andExpect(
                    jsonPath("$.detail")
                        .value(
                            "This entry is reversed from its own screen (its statement line or"
                                + " investment), not here")));
    assertThat(item(itemId).getStatus()).isEqualTo(UnidentifiedDepositStatus.HELD);
    assertThat(row(rowId).getStatus()).isEqualTo("RECORDED");
  }

  // --- audit -----------------------------------------------------------------------------

  @Test
  void audit_recordAndUnmatch() throws Exception {
    var account = books.freshAccount(false);
    var date = pastDate();
    var line = books.unmatchedLine(account, date, "125.00", "UID-AUDIT");
    var itemId = recordItem(account, line, "125.00");
    var rowId = item(itemId).getDepositTransactionId();

    var lineRecorded = audit("bank_statement_line", line.lineId(), "bank_statement_line.recorded");
    assertThat(lineRecorded.getDetails())
        .containsEntry("classification", "UNIDENTIFIED_DEPOSIT")
        .containsEntry("amount", "125.00")
        .containsEntry("transaction_ids", List.of(rowId.toString()));

    var rowRecorded = audit("trust_transaction", rowId, "trust_unidentified_deposit.recorded");
    assertThat(rowRecorded.getDetails())
        .containsEntry("amount", "125.00")
        .containsEntry("reference", "UID-AUDIT")
        .containsEntry("bank_statement_line_id", line.lineId().toString())
        .containsEntry(
            "creditor_account_id", books.creditorAccount(account, UNIDENTIFIED).toString())
        .containsEntry("unidentified_deposit_id", itemId.toString());

    var opened = audit("unidentified_deposit", itemId, "unidentified_deposit.opened");
    assertThat(opened.getDetails())
        .containsEntry("amount", "125.00")
        .containsEntry("received_date", date.toString())
        .containsEntry("trust_account_id", account.toString())
        .containsEntry("bank_statement_line_id", line.lineId().toString())
        .containsEntry("deposit_transaction_id", rowId.toString());
    assertThat(opened.getActorId()).isEqualTo(ownerId);

    unmatchMoves(account, line, "-125.00");
    var contraId = reversalsOf(rowId).getFirst().getId();

    var lineUnmatched =
        audit("bank_statement_line", line.lineId(), "bank_statement_line.unmatched");
    assertThat(lineUnmatched.getDetails())
        .containsEntry("classification", "UNIDENTIFIED_DEPOSIT")
        .containsEntry("reversed_transaction_ids", List.of(rowId.toString()));

    var reversed = audit("unidentified_deposit", itemId, "unidentified_deposit.reversed");
    assertThat(reversed.getDetails())
        .containsEntry("amount", "125.00")
        .containsEntry("deposit_transaction_id", rowId.toString())
        .containsEntry("reversal_transaction_id", contraId.toString())
        .containsEntry("resolved_date", date.toString());
    assertThat(reversed.getActorId()).isEqualTo(ownerId);
  }

  // --- helpers ---------------------------------------------------------------------------

  /** A MockMvc step that may throw. */
  @FunctionalInterface
  private interface Step {
    void run() throws Exception;
  }

  /** {@link TrustBooksHarness#assertMoves} as the owner, around a MockMvc step. */
  private void moves(UUID account, Expected expected, Step step) {
    books.as(
        ownerId,
        () ->
            harness.assertMoves(
                account,
                expected,
                () -> {
                  try {
                    step.run();
                  } catch (Exception e) {
                    throw new IllegalStateException(e);
                  }
                }));
  }

  /** Records {@code line} with {@code json} (201): cashbook and unidentified move together. */
  private String recordMoves(UUID account, StatementLine line, String json, String signedAmount) {
    var body = new String[1];
    moves(
        account,
        Expected.cashbookAndUnidentified(signedAmount),
        () ->
            body[0] =
                mockMvc
                    .perform(
                        post("/api/bank-statement-lines/{lineId}/record", line.lineId())
                            .with(owner())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isCreated())
                    .andReturn()
                    .getResponse()
                    .getContentAsString());
    return body[0];
  }

  /** Records {@code line} as an unidentified deposit; returns the item id. */
  private UUID recordItem(UUID account, StatementLine line, String amount) {
    var body = recordMoves(account, line, RECORD_BODY, amount);
    return UUID.fromString(JsonPath.read(body, "$.unidentifiedDepositId"));
  }

  /** Unmatches {@code line} (200): cashbook and unidentified move together by {@code amount}. */
  private void unmatchMoves(UUID account, StatementLine line, String signedAmount) {
    moves(
        account,
        Expected.cashbookAndUnidentified(signedAmount),
        () -> unmatch(line.lineId()).andExpect(status().isOk()));
  }

  private ResultActions unmatch(UUID lineId) throws Exception {
    return mockMvc.perform(
        post("/api/bank-statement-lines/{lineId}/unmatch", lineId).with(owner()));
  }

  /** The one test-only write: a simulated pending resolution (Javadoc of the class). */
  private void setResolution(UUID itemId, UUID transactionId) {
    jdbc.update(
        "UPDATE \"%s\".unidentified_deposits SET resolution_transaction_id = ? WHERE id = ?"
            .formatted(books.schema()),
        transactionId,
        itemId);
  }

  private static String itemsUrl(UUID account) {
    return "/api/trust-accounts/" + account + "/unidentified-deposits";
  }

  private static RequestPostProcessor owner() {
    return TestJwtFactory.ownerJwt(ORG_ID, OWNER);
  }

  private UnidentifiedDeposit item(UUID id) {
    return books.read(() -> depositRepository.findById(id).orElseThrow());
  }

  private TrustTransaction row(UUID id) {
    return books.read(() -> transactionRepository.findById(id).orElseThrow());
  }

  private List<TrustTransaction> reversalsOf(UUID originalId) {
    return books.read(
        () ->
            transactionRepository.findAll().stream()
                .filter(t -> originalId.equals(t.getReversalOf()))
                .toList());
  }

  private BankStatementLine line(UUID lineId) {
    return books.read(() -> lineRepository.findById(lineId).orElseThrow());
  }

  private int matchedCount(StatementLine line) {
    return books.read(
        () -> statementRepository.findById(line.statementId()).orElseThrow().getMatchedCount());
  }

  private AuditEvent audit(String entityType, UUID entityId, String eventType) {
    var events =
        books.read(
            () ->
                auditEventRepository
                    .findByFilter(
                        entityType, entityId, null, eventType, null, null, PageRequest.of(0, 10))
                    .getContent());
    assertThat(events).as("%s on %s", eventType, entityId).hasSize(1);
    return events.getFirst();
  }

  private String createCustomRole(String name, String capability) throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/org-roles")
                    .with(owner())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name":"%s","description":"Test role","capabilities":["%s"]}"""
                            .formatted(name, capability)))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
  }

  private void assignRole(String memberId, String roleId) throws Exception {
    mockMvc
        .perform(
            put("/api/members/" + memberId + "/role")
                .with(owner())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"orgRoleId\":\"%s\",\"capabilityOverrides\":[]}".formatted(roleId)))
        .andExpect(status().isOk());
  }
}
