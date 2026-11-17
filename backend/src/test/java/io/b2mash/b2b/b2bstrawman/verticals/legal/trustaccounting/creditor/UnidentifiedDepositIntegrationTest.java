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
import io.b2mash.b2b.b2bstrawman.testutil.TrustBooksFixture.Client;
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
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
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
 * Phase 92 slices 647B and 647D (tasks 647.10–647.13; ADR-376; architecture §16.2.5, §16.3.5,
 * §16.3.6, §16.9.2 paths 14–16): unidentified deposits — the hold, then the resolve.
 *
 * <p>The hold (647B, path 14). A credit line recorded from the statement as {@code
 * UNIDENTIFIED_DEPOSIT} posts one row on the account's unidentified-deposits ledger and opens a
 * HELD item the row names; the items are listed oldest first with their age; unmatching the line
 * while the item is held reverses the row with a same-date contra that names the item, and the item
 * becomes {@code REVERSED}. Every posting step is asserted through {@link
 * TrustBooksHarness#assertMoves}, EQ-4 (the item's balance from its own rows) is asserted as at the
 * day before, the line's date and today, and the class ends with {@code assertFirmBalanced(today)}.
 *
 * <p>The resolve (647D, paths 15 and 16): a request to allocate the item to a client and matter (or
 * to pay it to the Fund) saves one pending row and moves nothing; another member's approval posts
 * it — an allocation moves the money from the unidentified-deposits ledger to the client (cashbook
 * unchanged), a payment to the Fund takes it out of the cashbook; a rejection moves nothing and
 * leaves the item HELD; reversing the posted resolution undoes it and the item is HELD again, and
 * EQ-4 holds as at a date before, between and after. The requester cannot approve (single mode);
 * dual mode needs two approvers. Unmatching the line is refused while a resolution is pending or
 * once the item is allocated (path 14's tail, now with real resolutions).
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

  /** Approvers: admins (APPROVE_TRUST_PAYMENT) other than the requester (the owner). */
  private static final String APPROVER = "user_uid_approver";

  private static final String SECOND = "user_uid_second";

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
  private UUID approverId;
  private UUID secondId;

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
    approverId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, APPROVER, "uid_approver@test.com", "UID Approver", "admin"));
    secondId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, SECOND, "uid_second@test.com", "UID Second", "admin"));
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
  void unmatch_whileAResolutionIsPendingOrOnceAllocated_isRefused_andNothingMoves()
      throws Exception {
    // Path 14's tail (647D): real resolutions, no simulated write.
    var account = books.freshAccount(false);
    var client = books.newClient("UnmatchRefused");
    var line = books.unmatchedLine(account, pastDate(), "75.00", "UID-PENDING");
    var itemId = recordItem(account, line, "75.00");
    var rowId = item(itemId).getDepositTransactionId();

    // A pending allocation ...
    var allocOutId = requestAllocation(account, itemId, client, null);
    assertUnmatchRefused(account, line, rowId, itemId);

    // ... and the approved allocation.
    approveMoves(account, allocOutId, approver(), Expected.clientFromUnidentified("+75.00"));
    assertThat(item(itemId).getStatus()).isEqualTo(UnidentifiedDepositStatus.ALLOCATED);
    assertUnmatchRefused(account, line, rowId, itemId);
    assertThat(item(itemId).getStatus()).isEqualTo(UnidentifiedDepositStatus.ALLOCATED);

    // A pending payment to the Fund is refused the same way.
    var fundAccount = books.freshAccount(false);
    var fundLine = books.unmatchedLine(fundAccount, pastDate(), "45.00", "UID-PENDING-FUND");
    var fundItemId = recordItem(fundAccount, fundLine, "45.00");
    requestFundPayment(fundAccount, fundItemId, books.today());
    assertUnmatchRefused(
        fundAccount, fundLine, item(fundItemId).getDepositTransactionId(), fundItemId);
  }

  private void assertUnmatchRefused(UUID account, StatementLine line, UUID rowId, UUID itemId) {
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
    assertThat(matchedCount(line)).isEqualTo(1);
    assertThat(item(itemId).getResolutionTransactionId()).isNotNull();
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

  // --- path 15: allocation (647D) --------------------------------------------------------

  @Test
  void allocation_requested_movesNothing_andHoldsTheItemPending() throws Exception {
    var account = books.freshAccount(false);
    var client = books.newClient("AllocRequested");
    var received = books.today().minusDays(10);
    var allocationDate = books.today().minusDays(6);
    var line = books.unmatchedLine(account, received, "300.00", "UID-ALLOC-REQ");
    var itemId = recordItem(account, line, "300.00");

    var body = requestAllocationBody(account, itemId, client, allocationDate);

    var allocOutId = UUID.fromString(JsonPath.read(body, "$.resolutionTransactionId"));
    assertThat(JsonPath.<String>read(body, "$.id")).isEqualTo(itemId.toString());
    assertThat(JsonPath.<String>read(body, "$.status")).isEqualTo("HELD");
    assertThat(JsonPath.<String>read(body, "$.resolutionStatus")).isEqualTo("AWAITING_APPROVAL");
    assertThat(JsonPath.<String>read(body, "$.allocatedClientId"))
        .isEqualTo(client.id().toString());
    assertThat(JsonPath.<String>read(body, "$.allocatedClientName")).isEqualTo(client.name());
    assertThat(JsonPath.<String>read(body, "$.allocatedMatterId"))
        .isEqualTo(client.matter().toString());
    assertThat(JsonPath.<String>read(body, "$.allocatedMatterName")).isNotBlank();
    assertThat(JsonPath.<Object>read(body, "$.resolvedDate")).isNull();

    var pending = row(allocOutId);
    assertThat(pending.getTransactionType()).isEqualTo("UNIDENT_ALLOC_OUT");
    assertThat(pending.getStatus()).isEqualTo("AWAITING_APPROVAL");
    assertThat(pending.getPostedAt()).isNull();
    assertThat(pending.getLedger()).isEqualTo(TrustLedger.NON_CLIENT);
    assertThat(pending.getCreditorAccountId())
        .isEqualTo(books.creditorAccount(account, UNIDENTIFIED));
    assertThat(pending.getUnidentifiedDepositId()).isEqualTo(itemId);
    assertThat(pending.getCustomerId()).isNull();
    assertThat(pending.getProjectId()).isNull();
    assertThat(pending.getAmount()).isEqualByComparingTo("300.00");
    assertThat(pending.getTransactionDate()).isEqualTo(allocationDate);
    assertThat(pending.getReference()).isEqualTo(uidReference(itemId)).matches("UID-[0-9A-F]{8}");
    assertThat(pending.getDescription()).startsWith("To " + client.name() + " — ");
    assertThat(pending.getRecordedBy()).isEqualTo(ownerId);

    var item = item(itemId);
    assertThat(item.getStatus()).isEqualTo(UnidentifiedDepositStatus.HELD);
    assertThat(item.getResolutionTransactionId()).isEqualTo(allocOutId);
    assertThat(item.getAllocatedCustomerId()).isEqualTo(client.id());
    assertThat(item.getAllocatedProjectId()).isEqualTo(client.matter());
    assertThat(item.getAllocationCreditTransactionId()).isNull();
    assertThat(item.getResolvedDate()).isNull();

    // The list shows the pending allocation with its names.
    mockMvc
        .perform(get(itemsUrl(account)).with(owner()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].id").value(itemId.toString()))
        .andExpect(jsonPath("$.content[0].resolutionTransactionId").value(allocOutId.toString()))
        .andExpect(jsonPath("$.content[0].resolutionStatus").value("AWAITING_APPROVAL"))
        .andExpect(jsonPath("$.content[0].allocatedClientName").value(client.name()))
        .andExpect(jsonPath("$.content[0].allocatedMatterName").isNotEmpty());
    // ... and in the approvals list (the pending-approvals endpoint).
    mockMvc
        .perform(get("/api/trust-accounts/{id}/pending-approvals", account).with(approver()))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$[?(@.id == '%s')].typeLabel".formatted(allocOutId))
                .value("Unidentified deposit allocated"));

    var requested =
        audit("unidentified_deposit", itemId, "unidentified_deposit.allocation_requested");
    assertThat(requested.getDetails())
        .containsEntry("project_id", client.matter().toString())
        .containsEntry("customer_id", client.id().toString())
        .containsEntry("amount", "300.00")
        .containsEntry("received_date", received.toString())
        .containsEntry("resolution_transaction_id", allocOutId.toString())
        .containsEntry("allocation_date", allocationDate.toString())
        .containsEntry("reference", uidReference(itemId))
        .containsEntry("notes", "Proof of payment on file");
    assertThat(requested.getActorId()).isEqualTo(ownerId);
  }

  @Test
  void allocation_approved_movesUnidentifiedToClient_onTheAllocationDate() throws Exception {
    var account = books.freshAccount(false);
    var client = books.newClient("AllocApproved");
    var received = books.today().minusDays(10);
    var allocationDate = books.today().minusDays(6);
    var line = books.unmatchedLine(account, received, "300.00", "UID-ALLOC-OK");
    var itemId = recordItem(account, line, "300.00");
    var allocOutId = requestAllocation(account, itemId, client, allocationDate);

    approveMoves(account, allocOutId, approver(), Expected.clientFromUnidentified("+300.00"));

    var out = row(allocOutId);
    assertThat(out.getStatus()).isEqualTo("APPROVED");
    assertThat(out.getPostedAt()).isNotNull();
    assertThat(out.getApprovedBy()).isEqualTo(approverId);
    assertThat(out.getCashbookEffect()).isEqualByComparingTo("0");
    assertThat(out.getLedgerEffect()).isEqualByComparingTo("-300.00");

    var item = item(itemId);
    assertThat(item.getStatus()).isEqualTo(UnidentifiedDepositStatus.ALLOCATED);
    assertThat(item.getResolvedDate()).isEqualTo(allocationDate);
    assertThat(item.getResolutionTransactionId()).isEqualTo(allocOutId);
    var creditId = item.getAllocationCreditTransactionId();
    assertThat(creditId).isNotNull();

    var credit = row(creditId);
    assertThat(credit.getTransactionType()).isEqualTo("UNIDENT_ALLOC_IN");
    assertThat(credit.getStatus()).isEqualTo("APPROVED");
    assertThat(credit.getPostedAt()).isNotNull();
    assertThat(credit.getApprovedBy()).isEqualTo(approverId);
    assertThat(credit.getRecordedBy()).isEqualTo(ownerId);
    assertThat(credit.getLedger()).isEqualTo(TrustLedger.CLIENT);
    assertThat(credit.getCustomerId()).isEqualTo(client.id());
    assertThat(credit.getProjectId()).isEqualTo(client.matter());
    assertThat(credit.getCreditorAccountId()).isNull();
    assertThat(credit.getUnidentifiedDepositId()).isEqualTo(itemId);
    assertThat(credit.getTransactionDate()).isEqualTo(allocationDate);
    assertThat(credit.getReference()).isEqualTo(uidReference(itemId));
    assertThat(credit.getDescription())
        .isEqualTo(
            "Deposit received " + RECEIVED.format(received) + " — " + item.getBankDescription());
    assertThat(credit.getCashbookEffect()).isEqualByComparingTo("0");
    assertThat(credit.getLedgerEffect()).isEqualByComparingTo("300.00");

    // The client's card is served (UNIDENT_ALLOC_IN placed in LedgerTotals → deposits).
    mockMvc
        .perform(
            get("/api/trust-accounts/{a}/client-ledgers/{c}", account, client.id()).with(owner()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.balance").value(300.0))
        .andExpect(jsonPath("$.totalDeposits").value(300.0))
        .andExpect(jsonPath("$.totalTransfersIn").value(0.0));
    assertThat(books.clientLedger(account, client)).isEqualByComparingTo("300.00");

    // The item reads ALLOCATED on the list, with its client and matter.
    mockMvc
        .perform(get(itemsUrl(account)).with(owner()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].status").value("ALLOCATED"))
        .andExpect(jsonPath("$.content[0].resolutionStatus").value("APPROVED"))
        .andExpect(jsonPath("$.content[0].resolvedDate").value(allocationDate.toString()))
        .andExpect(jsonPath("$.content[0].allocatedClientName").value(client.name()));

    // EQ-4: open until the day before the allocation, not open from it.
    assertUnidentifiedAsAt(account, allocationDate.minusDays(1), "300.00");
    assertUnidentifiedAsAt(account, allocationDate, "0");
    assertUnidentifiedAsAt(account, books.today(), "0");
    books.assertServicesAgree(account);

    var allocated = audit("unidentified_deposit", itemId, "unidentified_deposit.allocated");
    assertThat(allocated.getDetails())
        .containsEntry("project_id", client.matter().toString())
        .containsEntry("customer_id", client.id().toString())
        .containsEntry("amount", "300.00")
        .containsEntry("resolution_transaction_id", allocOutId.toString())
        .containsEntry("allocation_credit_transaction_id", creditId.toString())
        .containsEntry("resolved_date", allocationDate.toString());
    assertThat(allocated.getActorId()).isEqualTo(approverId);
    var approved = audit("trust_transaction", allocOutId, "trust_unident_alloc_out.approved");
    assertThat(approved.getDetails()).containsEntry("transaction_type", "UNIDENT_ALLOC_OUT");
  }

  @Test
  void allocation_requesterCannotApprove() throws Exception {
    var account = books.freshAccount(false);
    var client = books.newClient("AllocSelf");
    var line = books.unmatchedLine(account, pastDate(), "120.00", "UID-ALLOC-SELF");
    var itemId = recordItem(account, line, "120.00");
    var allocOutId = requestAllocation(account, itemId, client, null);

    moves(
        account,
        Expected.none(),
        () ->
            approve(allocOutId, owner())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Self-approval not allowed")));
    assertThat(row(allocOutId).getStatus()).isEqualTo("AWAITING_APPROVAL");
    assertThat(item(itemId).getStatus()).isEqualTo(UnidentifiedDepositStatus.HELD);

    approveMoves(account, allocOutId, approver(), Expected.clientFromUnidentified("+120.00"));
    assertThat(item(itemId).getStatus()).isEqualTo(UnidentifiedDepositStatus.ALLOCATED);
    // Dated today by default (the allocation date was not given).
    assertThat(row(allocOutId).getTransactionDate()).isEqualTo(books.today());
  }

  @Test
  void allocation_dualMode_firstApprovalMovesNothing_secondPosts() throws Exception {
    var account = books.freshAccount(true); // no threshold: dual approval always
    var client = books.newClient("AllocDual");
    var line = books.unmatchedLine(account, pastDate(), "80.00", "UID-ALLOC-DUAL");
    var itemId = recordItem(account, line, "80.00");
    var allocOutId = requestAllocation(account, itemId, client, null);

    approveMoves(account, allocOutId, approver(), Expected.none());
    assertThat(row(allocOutId).getStatus()).isEqualTo("AWAITING_APPROVAL");
    assertThat(item(itemId).getStatus()).isEqualTo(UnidentifiedDepositStatus.HELD);
    assertThat(item(itemId).getAllocationCreditTransactionId()).isNull();

    approveMoves(account, allocOutId, second(), Expected.clientFromUnidentified("+80.00"));
    var item = item(itemId);
    assertThat(item.getStatus()).isEqualTo(UnidentifiedDepositStatus.ALLOCATED);
    var credit = row(item.getAllocationCreditTransactionId());
    assertThat(credit.getApprovedBy()).isEqualTo(approverId);
    assertThat(credit.getSecondApprovedBy()).isEqualTo(secondId);
    books.assertServicesAgree(account);
  }

  @Test
  void allocation_rejected_movesNothing_andTheItemCanBeAllocatedAgain() throws Exception {
    var account = books.freshAccount(false);
    var wrong = books.newClient("AllocWrong");
    var right = books.newClient("AllocRight");
    var line = books.unmatchedLine(account, pastDate(), "150.00", "UID-ALLOC-REJ");
    var itemId = recordItem(account, line, "150.00");
    var firstId = requestAllocation(account, itemId, wrong, null);

    rejectMoves(account, firstId);

    assertThat(row(firstId).getStatus()).isEqualTo("REJECTED");
    assertThat(row(firstId).getPostedAt()).isNull();
    var item = item(itemId);
    assertThat(item.getStatus()).isEqualTo(UnidentifiedDepositStatus.HELD);
    assertThat(item.getResolutionTransactionId()).isNull();
    assertThat(item.getAllocatedCustomerId()).isNull();
    assertThat(item.getAllocatedProjectId()).isNull();
    assertThat(item.getResolvedDate()).isNull();

    var rejected =
        audit("unidentified_deposit", itemId, "unidentified_deposit.allocation_rejected");
    assertThat(rejected.getDetails())
        .containsEntry("project_id", wrong.matter().toString())
        .containsEntry("customer_id", wrong.id().toString())
        .containsEntry("resolution_transaction_id", firstId.toString())
        .containsEntry("reason", "Wrong client");
    assertThat(rejected.getActorId()).isEqualTo(approverId);

    var secondRequest = requestAllocation(account, itemId, right, null);
    approveMoves(account, secondRequest, approver(), Expected.clientFromUnidentified("+150.00"));
    assertThat(item(itemId).getAllocatedCustomerId()).isEqualTo(right.id());
    assertThat(books.clientLedger(account, right)).isEqualByComparingTo("150.00");
    assertThat(books.clientLedger(account, wrong)).isEqualByComparingTo("0");
  }

  @Test
  void allocation_undoneAfterApproval_reopensTheItem_EQ4BeforeBetweenAfter() throws Exception {
    // F-646E-3 red → green: the PAIR reversal branch used to look for a TRANSFER_IN.
    var account = books.freshAccount(false);
    var client = books.newClient("AllocUndo");
    var today = books.today();
    var received = today.minusDays(10);
    var allocationDate = today.minusDays(6);
    var reversalDate = today.minusDays(3);
    var line = books.unmatchedLine(account, received, "300.00", "UID-ALLOC-UNDO");
    var itemId = recordItem(account, line, "300.00");
    var allocOutId = requestAllocation(account, itemId, client, allocationDate);
    approveMoves(account, allocOutId, approver(), Expected.clientFromUnidentified("+300.00"));
    var creditId = item(itemId).getAllocationCreditTransactionId();

    moves(
        account,
        Expected.clientFromUnidentified("-300.00"),
        () -> reverse(allocOutId, reversalDate).andExpect(status().isCreated()));

    var outContras = reversalsOf(allocOutId);
    var inContras = reversalsOf(creditId);
    assertThat(outContras).hasSize(1);
    assertThat(inContras).hasSize(1);
    var outContra = outContras.getFirst();
    var inContra = inContras.getFirst();
    assertThat(outContra.getStatus()).isEqualTo("RECORDED");
    assertThat(outContra.getTransactionDate()).isEqualTo(reversalDate);
    assertThat(outContra.getLedger()).isEqualTo(TrustLedger.NON_CLIENT);
    assertThat(outContra.getUnidentifiedDepositId()).isEqualTo(itemId);
    assertThat(outContra.getCreditorAccountId())
        .isEqualTo(books.creditorAccount(account, UNIDENTIFIED));
    assertThat(outContra.getCashbookEffect()).isEqualByComparingTo("0");
    assertThat(outContra.getLedgerEffect()).isEqualByComparingTo("300.00");
    assertThat(inContra.getStatus()).isEqualTo("RECORDED");
    assertThat(inContra.getTransactionDate()).isEqualTo(reversalDate);
    assertThat(inContra.getLedger()).isEqualTo(TrustLedger.CLIENT);
    assertThat(inContra.getCustomerId()).isEqualTo(client.id());
    assertThat(inContra.getProjectId()).isEqualTo(client.matter());
    assertThat(inContra.getUnidentifiedDepositId()).isEqualTo(itemId);
    assertThat(inContra.getCashbookEffect()).isEqualByComparingTo("0");
    assertThat(inContra.getLedgerEffect()).isEqualByComparingTo("-300.00");
    assertThat(row(allocOutId).getStatus()).isEqualTo("REVERSED");
    assertThat(row(creditId).getStatus()).isEqualTo("REVERSED");

    var item = item(itemId);
    assertThat(item.getStatus()).isEqualTo(UnidentifiedDepositStatus.HELD);
    assertThat(item.getResolvedDate()).isNull();
    assertThat(item.getResolutionTransactionId()).isNull();
    assertThat(item.getAllocationCreditTransactionId()).isNull();
    assertThat(item.getAllocatedCustomerId()).isNull();
    assertThat(item.getAllocatedProjectId()).isNull();

    // EQ-4 before (open), between (not open), after (open again).
    assertUnidentifiedAsAt(account, allocationDate.minusDays(1), "300.00");
    assertUnidentifiedAsAt(account, allocationDate, "0");
    assertUnidentifiedAsAt(account, reversalDate.minusDays(1), "0");
    assertUnidentifiedAsAt(account, reversalDate, "300.00");
    assertUnidentifiedAsAt(account, today, "300.00");
    assertThat(books.clientLedgerAsAt(account, client, reversalDate.minusDays(1)))
        .isEqualByComparingTo("300.00");
    assertThat(books.clientLedger(account, client)).isEqualByComparingTo("0");
    mockMvc
        .perform(
            get("/api/trust-accounts/{a}/client-ledgers/{c}", account, client.id()).with(owner()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.balance").value(0.0))
        .andExpect(jsonPath("$.totalDeposits").value(0.0));
    books.assertServicesAgree(account);

    var reopened = audit("unidentified_deposit", itemId, "unidentified_deposit.reopened");
    assertThat(reopened.getDetails())
        .containsEntry("project_id", client.matter().toString())
        .containsEntry("customer_id", client.id().toString())
        .containsEntry("resolution_transaction_id", allocOutId.toString())
        .containsEntry("allocation_credit_transaction_id", creditId.toString())
        .containsEntry("resolution_type", "UNIDENT_ALLOC_OUT")
        .containsEntry("reversal_date", reversalDate.toString());

    // Held again: it can be allocated afresh.
    var again = requestAllocation(account, itemId, client, null);
    approveMoves(account, again, approver(), Expected.clientFromUnidentified("+300.00"));
    assertThat(item(itemId).getStatus()).isEqualTo(UnidentifiedDepositStatus.ALLOCATED);
  }

  @Test
  void allocation_creditLegCannotBeReversedDirectly() throws Exception {
    var account = books.freshAccount(false);
    var client = books.newClient("AllocViaPair");
    var line = books.unmatchedLine(account, pastDate(), "90.00", "UID-ALLOC-VIAPAIR");
    var itemId = recordItem(account, line, "90.00");
    var allocOutId = requestAllocation(account, itemId, client, null);
    approveMoves(account, allocOutId, approver(), Expected.clientFromUnidentified("+90.00"));
    var creditId = item(itemId).getAllocationCreditTransactionId();

    moves(
        account,
        Expected.none(),
        () ->
            reverse(creditId, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Cannot reverse transaction"))
                .andExpect(
                    jsonPath("$.detail")
                        .value(
                            "Reverse the unidentified deposit's allocation instead to undo both"
                                + " entries")));
    assertThat(row(creditId).getStatus()).isEqualTo("APPROVED");
    assertThat(item(itemId).getStatus()).isEqualTo(UnidentifiedDepositStatus.ALLOCATED);
  }

  @Test
  void secondResolution_whilePendingOrOnceResolved_is400() throws Exception {
    var account = books.freshAccount(false);
    var client = books.newClient("AllocTwice");
    var line = books.unmatchedLine(account, pastDate(), "60.00", "UID-ALLOC-TWICE");
    var itemId = recordItem(account, line, "60.00");
    var allocOutId = requestAllocation(account, itemId, client, null);
    var allocate = allocateJson(client, client.matter(), null);
    var pay = fundJson("LPFF-TWICE", books.today());

    moves(
        account,
        Expected.none(),
        () -> {
          refused(
              allocate(itemId, allocate, owner()),
              "Cannot allocate deposit",
              "This deposit already has a pending allocation or payment");
          refused(
              payToFund(itemId, pay, owner()),
              "Cannot pay deposit to the Fund",
              "This deposit already has a pending allocation or payment");
        });

    approveMoves(account, allocOutId, approver(), Expected.clientFromUnidentified("+60.00"));
    moves(
        account,
        Expected.none(),
        () -> {
          refused(
              allocate(itemId, allocate, owner()),
              "Cannot allocate deposit",
              "Only a held deposit can be allocated or paid to the Fund");
          refused(
              payToFund(itemId, pay, owner()),
              "Cannot pay deposit to the Fund",
              "Only a held deposit can be allocated or paid to the Fund");
        });
  }

  @Test
  void allocation_clientNotLinkedToTheMatter_is400_andUnknownsAre404() throws Exception {
    var account = books.freshAccount(false);
    var client = books.newClient("AllocLinked");
    var other = books.newClient("AllocOther");
    var line = books.unmatchedLine(account, pastDate(), "40.00", "UID-ALLOC-LINK");
    var itemId = recordItem(account, line, "40.00");

    moves(
        account,
        Expected.none(),
        () -> {
          allocate(itemId, allocateJson(client, other.matter(), null), owner())
              .andExpect(status().isBadRequest())
              .andExpect(jsonPath("$.status").value(400))
              .andExpect(jsonPath("$.title").value("Matter not linked to client"))
              .andExpect(jsonPath("$.code").value("MATTER_NOT_LINKED_TO_CLIENT"))
              .andExpect(jsonPath("$.matterId").value(other.matter().toString()));
          var unknown = UUID.randomUUID();
          allocate(unknown, allocateJson(client, client.matter(), null), owner())
              .andExpect(status().isNotFound())
              .andExpect(jsonPath("$.title").value("Unidentified deposit not found"));
          payToFund(unknown, fundJson("LPFF-404", books.today()), owner())
              .andExpect(status().isNotFound())
              .andExpect(jsonPath("$.title").value("Unidentified deposit not found"));
          // Missing client and matter: bean validation.
          allocate(itemId, "{}", owner()).andExpect(status().isBadRequest());
          // Missing reference / date on a payment: bean validation.
          payToFund(itemId, "{\"reference\":\"\"}", owner()).andExpect(status().isBadRequest());
        });
    var item = item(itemId);
    assertThat(item.getResolutionTransactionId()).isNull();
    assertThat(item.getAllocatedCustomerId()).isNull();
  }

  @Test
  void resolution_dateOutsideTheWindow_is400() throws Exception {
    var account = books.freshAccount(false);
    var client = books.newClient("AllocWindow");
    var received = pastDate();
    var line = books.unmatchedLine(account, received, "30.00", "UID-ALLOC-WINDOW");
    var itemId = recordItem(account, line, "30.00");
    var allocationDetail =
        "The allocation date must be from the date the deposit was received ("
            + received
            + ") to today";
    var paymentDetail =
        "The payment date must be from the date the deposit was received ("
            + received
            + ") to today";

    moves(
        account,
        Expected.none(),
        () -> {
          refused(
              allocate(
                  itemId, allocateJson(client, client.matter(), received.minusDays(1)), owner()),
              "Cannot allocate deposit",
              allocationDetail);
          refused(
              allocate(
                  itemId,
                  allocateJson(client, client.matter(), books.today().plusDays(1)),
                  owner()),
              "Cannot allocate deposit",
              allocationDetail);
          refused(
              payToFund(itemId, fundJson("LPFF-EARLY", received.minusDays(1)), owner()),
              "Cannot pay deposit to the Fund",
              paymentDetail);
          refused(
              payToFund(itemId, fundJson("LPFF-LATE", books.today().plusDays(1)), owner()),
              "Cannot pay deposit to the Fund",
              paymentDetail);
        });
    assertThat(item(itemId).getResolutionTransactionId()).isNull();
  }

  @Test
  void resolve_needsManageTrust() throws Exception {
    var account = books.freshAccount(false);
    var client = books.newClient("AllocForbidden");
    var line = books.unmatchedLine(account, pastDate(), "20.00", "UID-ALLOC-403");
    var itemId = recordItem(account, line, "20.00");
    var member = TestJwtFactory.memberJwt(ORG_ID, MEMBER);

    moves(
        account,
        Expected.none(),
        () -> {
          allocate(itemId, allocateJson(client, client.matter(), null), member)
              .andExpect(status().isForbidden());
          payToFund(itemId, fundJson("LPFF-403", books.today()), member)
              .andExpect(status().isForbidden());
        });
    assertThat(item(itemId).getResolutionTransactionId()).isNull();
  }

  // --- path 16: payment to the Fund (647D) -----------------------------------------------

  @Test
  void fundPayment_requested_thenApproved_takesItOutOfTheCashbook() throws Exception {
    var account = books.freshAccount(false);
    var received = books.today().minusDays(10);
    var paymentDate = books.today().minusDays(2);
    var line = books.unmatchedLine(account, received, "250.00", "UID-FUND-OK");
    var itemId = recordItem(account, line, "250.00");

    var payId = requestFundPayment(account, itemId, paymentDate);

    var pending = row(payId);
    assertThat(pending.getTransactionType()).isEqualTo("UNIDENT_FUND_PAY");
    assertThat(pending.getStatus()).isEqualTo("AWAITING_APPROVAL");
    assertThat(pending.getLedger()).isEqualTo(TrustLedger.NON_CLIENT);
    assertThat(pending.getCreditorAccountId())
        .isEqualTo(books.creditorAccount(account, UNIDENTIFIED));
    assertThat(pending.getUnidentifiedDepositId()).isEqualTo(itemId);
    assertThat(pending.getTransactionDate()).isEqualTo(paymentDate);
    assertThat(pending.getReference()).isEqualTo("LPFF-" + itemId.toString().substring(0, 8));
    assertThat(pending.getDescription())
        .startsWith("To the Legal Practitioners' Fidelity Fund: deposit received ");
    var held = item(itemId);
    assertThat(held.getStatus()).isEqualTo(UnidentifiedDepositStatus.HELD);
    assertThat(held.getResolutionTransactionId()).isEqualTo(payId);
    assertThat(held.getAllocatedCustomerId()).isNull();

    approveMoves(account, payId, approver(), Expected.cashbookAndUnidentified("-250.00"));

    var paid = row(payId);
    assertThat(paid.getStatus()).isEqualTo("APPROVED");
    assertThat(paid.getCashbookEffect()).isEqualByComparingTo("-250.00");
    assertThat(paid.getLedgerEffect()).isEqualByComparingTo("-250.00");
    var item = item(itemId);
    assertThat(item.getStatus()).isEqualTo(UnidentifiedDepositStatus.PAID_TO_FUND);
    assertThat(item.getResolvedDate()).isEqualTo(paymentDate);
    assertThat(item.getAllocationCreditTransactionId()).isNull();

    assertUnidentifiedAsAt(account, paymentDate.minusDays(1), "250.00");
    assertUnidentifiedAsAt(account, paymentDate, "0");
    books.assertServicesAgree(account);

    var requested = audit("unidentified_deposit", itemId, "unidentified_deposit.payment_requested");
    assertThat(requested.getDetails())
        .containsEntry("amount", "250.00")
        .containsEntry("resolution_transaction_id", payId.toString())
        .containsEntry("transaction_date", paymentDate.toString())
        .doesNotContainKey("project_id");
    var paidAudit = audit("unidentified_deposit", itemId, "unidentified_deposit.paid_to_fund");
    assertThat(paidAudit.getDetails()).containsEntry("resolved_date", paymentDate.toString());
    audit("trust_transaction", payId, "trust_unident_fund_pay.approved");
  }

  @Test
  void fundPayment_rejected_movesNothing_andTheItemStaysHeld() throws Exception {
    var account = books.freshAccount(false);
    var line = books.unmatchedLine(account, pastDate(), "55.00", "UID-FUND-REJ");
    var itemId = recordItem(account, line, "55.00");
    var payId = requestFundPayment(account, itemId, books.today());

    rejectMoves(account, payId);

    assertThat(row(payId).getStatus()).isEqualTo("REJECTED");
    var item = item(itemId);
    assertThat(item.getStatus()).isEqualTo(UnidentifiedDepositStatus.HELD);
    assertThat(item.getResolutionTransactionId()).isNull();
    var rejected = audit("unidentified_deposit", itemId, "unidentified_deposit.payment_rejected");
    assertThat(rejected.getDetails())
        .containsEntry("resolution_transaction_id", payId.toString())
        .containsEntry("reason", "Wrong client");
  }

  @Test
  void fundPayment_reversed_reopensTheItem() throws Exception {
    var account = books.freshAccount(false);
    var today = books.today();
    var paymentDate = today.minusDays(4);
    var reversalDate = today.minusDays(1);
    var line = books.unmatchedLine(account, pastDate(), "65.00", "UID-FUND-REV");
    var itemId = recordItem(account, line, "65.00");
    var payId = requestFundPayment(account, itemId, paymentDate);
    approveMoves(account, payId, approver(), Expected.cashbookAndUnidentified("-65.00"));

    // The payment bounced: the contra puts it back in the cashbook and on the item.
    moves(
        account,
        Expected.cashbookAndUnidentified("+65.00"),
        () -> reverse(payId, reversalDate).andExpect(status().isCreated()));

    var contras = reversalsOf(payId);
    assertThat(contras).hasSize(1);
    assertThat(contras.getFirst().getUnidentifiedDepositId()).isEqualTo(itemId);
    assertThat(contras.getFirst().getTransactionDate()).isEqualTo(reversalDate);
    assertThat(row(payId).getStatus()).isEqualTo("REVERSED");
    var item = item(itemId);
    assertThat(item.getStatus()).isEqualTo(UnidentifiedDepositStatus.HELD);
    assertThat(item.getResolvedDate()).isNull();
    assertThat(item.getResolutionTransactionId()).isNull();

    assertUnidentifiedAsAt(account, paymentDate.minusDays(1), "65.00");
    assertUnidentifiedAsAt(account, paymentDate, "0");
    assertUnidentifiedAsAt(account, reversalDate, "65.00");
    assertUnidentifiedAsAt(account, today, "65.00");
    books.assertServicesAgree(account);

    var reopened = audit("unidentified_deposit", itemId, "unidentified_deposit.reopened");
    assertThat(reopened.getDetails())
        .containsEntry("resolution_type", "UNIDENT_FUND_PAY")
        .containsEntry("reversal_date", reversalDate.toString())
        .doesNotContainKey("project_id");
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

  // --- resolve helpers (647D) ------------------------------------------------------------

  /** The received date as the allocated ledger line prints it. */
  private static final DateTimeFormatter RECEIVED =
      DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH);

  private static RequestPostProcessor approver() {
    return TestJwtFactory.adminJwt(ORG_ID, APPROVER);
  }

  private static RequestPostProcessor second() {
    return TestJwtFactory.adminJwt(ORG_ID, SECOND);
  }

  private static String uidReference(UUID itemId) {
    return "UID-" + itemId.toString().substring(0, 8).toUpperCase(Locale.ROOT);
  }

  private static String allocateJson(Client client, UUID matterId, LocalDate allocationDate) {
    return """
        {"clientId":"%s","matterId":"%s","allocationDate":%s,"notes":"Proof of payment on file"}"""
        .formatted(
            client.id(), matterId, allocationDate == null ? "null" : "\"" + allocationDate + "\"");
  }

  private static String fundJson(String reference, LocalDate transactionDate) {
    return """
        {"reference":"%s","transactionDate":"%s","notes":"Unclaimed after the second closing"}"""
        .formatted(reference, transactionDate);
  }

  private ResultActions allocate(UUID itemId, String json, RequestPostProcessor who)
      throws Exception {
    return mockMvc.perform(
        post("/api/unidentified-deposits/{id}/allocate", itemId)
            .with(who)
            .contentType(MediaType.APPLICATION_JSON)
            .content(json));
  }

  private ResultActions payToFund(UUID itemId, String json, RequestPostProcessor who)
      throws Exception {
    return mockMvc.perform(
        post("/api/unidentified-deposits/{id}/pay-to-fund", itemId)
            .with(who)
            .contentType(MediaType.APPLICATION_JSON)
            .content(json));
  }

  /** The owner requests an allocation (200; nothing moves); returns the response body. */
  private String requestAllocationBody(
      UUID account, UUID itemId, Client client, LocalDate allocationDate) {
    var body = new String[1];
    moves(
        account,
        Expected.none(),
        () ->
            body[0] =
                allocate(itemId, allocateJson(client, client.matter(), allocationDate), owner())
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString());
    return body[0];
  }

  /** As {@link #requestAllocationBody}; returns the pending UNIDENT_ALLOC_OUT's id. */
  private UUID requestAllocation(
      UUID account, UUID itemId, Client client, LocalDate allocationDate) {
    return UUID.fromString(
        JsonPath.read(
            requestAllocationBody(account, itemId, client, allocationDate),
            "$.resolutionTransactionId"));
  }

  /**
   * The owner requests payment to the Fund (200; nothing moves), reference {@code LPFF-} and the
   * item's first 8 hex digits; returns the pending UNIDENT_FUND_PAY's id.
   */
  private UUID requestFundPayment(UUID account, UUID itemId, LocalDate transactionDate) {
    var body = new String[1];
    moves(
        account,
        Expected.none(),
        () ->
            body[0] =
                payToFund(
                        itemId,
                        fundJson("LPFF-" + itemId.toString().substring(0, 8), transactionDate),
                        owner())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("HELD"))
                    .andExpect(jsonPath("$.resolutionStatus").value("AWAITING_APPROVAL"))
                    .andExpect(jsonPath("$.allocatedClientId").isEmpty())
                    .andReturn()
                    .getResponse()
                    .getContentAsString());
    return UUID.fromString(JsonPath.read(body[0], "$.resolutionTransactionId"));
  }

  private ResultActions approve(UUID transactionId, RequestPostProcessor who) throws Exception {
    return mockMvc.perform(post("/api/trust-transactions/{id}/approve", transactionId).with(who));
  }

  /** {@code who} approves (200); the account moves by exactly {@code expected}. */
  private void approveMoves(
      UUID account, UUID transactionId, RequestPostProcessor who, Expected expected) {
    moves(account, expected, () -> approve(transactionId, who).andExpect(status().isOk()));
  }

  /** The approver rejects (200, reason "Wrong client"); nothing moves. */
  private void rejectMoves(UUID account, UUID transactionId) {
    moves(
        account,
        Expected.none(),
        () ->
            mockMvc
                .perform(
                    post("/api/trust-transactions/{id}/reject", transactionId)
                        .with(approver())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Wrong client\"}"))
                .andExpect(status().isOk()));
  }

  /** The owner reverses {@code transactionId}, dated {@code reversalDate} (null = today). */
  private ResultActions reverse(UUID transactionId, LocalDate reversalDate) throws Exception {
    return mockMvc.perform(
        post("/api/trust-transactions/{id}/reverse", transactionId)
            .with(owner())
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                "{\"reason\":\"Allocated in error\",\"reversalDate\":%s}"
                    .formatted(reversalDate == null ? "null" : "\"" + reversalDate + "\"")));
  }

  private static void refused(ResultActions result, String title, String detail) throws Exception {
    result
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.title").value(title))
        .andExpect(jsonPath("$.detail").value(detail));
  }

  /** EQ-4 as at {@code asAt}: the unidentified term, and the account balanced on that date. */
  private void assertUnidentifiedAsAt(UUID account, LocalDate asAt, String expected) {
    assertThat(books.snapshotAsAt(account, asAt).unidentified())
        .as("unidentified as at %s", asAt)
        .isEqualByComparingTo(expected);
    books.assertBalancedAsAt(account, asAt);
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
