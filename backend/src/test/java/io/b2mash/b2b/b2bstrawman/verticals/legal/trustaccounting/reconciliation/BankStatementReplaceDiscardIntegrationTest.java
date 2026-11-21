package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceConflictException;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TrustBooksFixture;
import io.b2mash.b2b.b2bstrawman.testutil.TrustBooksHarness.Expected;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.books.FlagSubject;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.books.IntegrityFlag;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.books.TrustIntegrityService;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation.TrustReconciliationService.BankStatementResponse;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation.TrustReconciliationService.MatchResult;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation.TrustReconciliationService.TrustReconciliationResponse;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionService;
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Phase 92 FU-6b (rulings F-646C-1, F-652C-1; orchestrator default FU-6 D-1): a second statement
 * for the same account with the same end date is refused unless it replaces the first; an imported
 * statement can be replaced (its matches released for the new statement) or discarded (the step-4
 * leftover), and a retired statement no longer counts anywhere a live statement does.
 *
 * <p>The first three tests are the red reproductions (written against unmodified code, run through
 * HTTP so they compile there): {@link #sameEndDateSecondImport_isRefused}, {@link
 * #replace_releasesTheFirstStatementsMatches_forTheNewStatement} and {@link
 * #discard_ofAStep4Leftover_stopsItCounting}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BankStatementReplaceDiscardIntegrationTest {

  private static final String ORG_ID = "org_fu6b_stmt_replace";
  private static final String OWNER = "user_fu6b_owner";
  private static final LocalDate MARCH_END = LocalDate.of(2026, 3, 31);

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private TrustBooksFixture fixture;
  @Autowired private TrustReconciliationService reconciliationService;
  @Autowired private TrustIntegrityService integrityService;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private StatementLinePostingService postingService;
  @Autowired private TrustTransactionService transactionService;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private EntityManager entityManager;

  private TrustBooksFixture.Tenant books;
  private UUID memberId;
  private UUID approverId;

  @BeforeAll
  void setup() throws Exception {
    var schema =
        provisioningService
            .provisionTenant(ORG_ID, "FU-6b Statement Replace Org", null)
            .schemaName();
    memberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, OWNER, "fu6b@test.com", "FU6b Owner", "owner"));
    approverId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, "user_fu6b_admin", "fu6b-admin@test.com", "FU6b Admin", "admin"));
    fixture.enableTrustModule(schema);
    books = fixture.tenant(schema, ORG_ID, memberId);
  }

  // --- red reproductions ---------------------------------------------------------------------

  /**
   * (a) F-646C-1: an intraday export and the full-day export of one account both end on 31 March.
   * The second import, without a replace intent, is refused (409) and names the statement it would
   * duplicate, so the UI can offer "Replace". Nothing is stored.
   */
  @Test
  void sameEndDateSecondImport_isRefused() throws Exception {
    var account = books.freshAccount(false);
    var intraday =
        importStatement(
            account, "fu6b-intraday.csv", "31/03/2026,Deposit received,1000.00,1000.00,FU6B-A1");

    importHttp(
            account,
            null,
            "fu6b-fullday.csv",
            "31/03/2026,Deposit received,1000.00,1000.00,FU6B-A1",
            "31/03/2026,Bank charge,-10.00,990.00,FU6B-A2")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.title").value("A statement ending on this date is already imported"))
        .andExpect(jsonPath("$.existingStatementId").value(intraday.id().toString()))
        .andExpect(jsonPath("$.code").value("bank_statement.duplicate_end_date"));

    assertThat(statementCount(account)).as("the refused import stored nothing").isEqualTo(1L);
  }

  /**
   * (b) F-652C-1: the wizard auto-matched statement A, then the user uploads a corrected B with the
   * replace intent. A is RETIRED, its match is released (line UNMATCHED, deposit unlinked, A's
   * matched_count 0), and B's auto-match now finds the deposit. Before FU-6b the deposit stayed
   * linked to A's line and was never offered to B.
   */
  @Test
  void replace_releasesTheFirstStatementsMatches_forTheNewStatement() throws Exception {
    var account = books.freshAccount(false);
    var client = books.newClient("fu6b replace");
    var deposit = books.deposit(account, client, "1000.00");
    var a =
        importStatement(
            account, "fu6b-a.csv", "01/03/2026,Deposit received,1000.00,1000.00,FU6B-REP");
    var aMatch = autoMatch(a.id());
    assertThat(aMatch.autoMatched()).isEqualTo(1);

    var body =
        importHttp(
                account,
                a.id(),
                "fu6b-b.csv",
                "01/03/2026,Deposit received,1000.00,1000.00,FU6B-REP",
                "02/03/2026,Bank charge,-10.00,990.00,FU6B-CHG")
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    var bId = UUID.fromString(com.jayway.jsonpath.JsonPath.read(body, "$.id"));

    // B's auto-match first: before FU-6b the deposit stayed linked to A's line (stranded)
    var bMatch = autoMatch(bId);
    assertThat(bMatch.autoMatched()).as("B's auto-match finds the released deposit").isEqualTo(1);
    assertThat(statementStatus(a.id())).as("A is retired").isEqualTo("RETIRED");
    assertThat(matchStatus(a.lines().getFirst().id()))
        .as("A's match is released")
        .isEqualTo("UNMATCHED");
    assertThat(matchedCount(a.id())).as("A's matched_count follows the release").isZero();
    assertThat(linkedLineStatement(deposit)).isEqualTo(bId);
  }

  /**
   * (c) F-652C-1 / 647B O-1: a statement refused at the wizard's step 4 (its line is after the
   * period end chosen) stays IMPORTED with no reconciliation. It makes a later reconciliation of
   * another statement fail the period-fit rule and counts in UNRESOLVED_STATEMENT_LINES. Discarding
   * it (with a reason) retires it, and it stops counting in both.
   */
  @Test
  void discard_ofAStep4Leftover_stopsItCounting() throws Exception {
    var account = books.freshAccount(false);
    var leftover =
        importStatement(
            account, "fu6b-leftover.csv", "10/03/2026,Unknown credit,50.00,50.00,FU6B-LEFT");
    books.as(
        memberId,
        () ->
            assertThatThrownBy(
                    () ->
                        reconciliationService.createReconciliation(
                            account, LocalDate.of(2026, 3, 5), leftover.id()))
                .hasMessageContaining("Statement does not fit the period end"));
    var march =
        importStatement(account, "fu6b-march.csv", "05/03/2026,Bank charge,-1.00,-1.00,FU6B-M");
    // Before the discard the leftover counts: it is in UNRESOLVED_STATEMENT_LINES and its line
    // (10 March) makes the period-fit rule refuse the March statement for 31 March.
    assertThat(unresolvedSubjects(account)).contains(leftover.id());
    books.as(
        memberId,
        () ->
            assertThatThrownBy(
                    () ->
                        reconciliationService.createReconciliation(account, MARCH_END, march.id()))
                .hasMessageContaining("Statement does not fit the period end"));

    mockMvc
        .perform(
            post("/api/bank-statements/{id}/discard", leftover.id())
                .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\": \"Uploaded in error\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("RETIRED"))
        .andExpect(jsonPath("$.retiredReason").value("Uploaded in error"));

    assertThat(statementStatus(leftover.id())).isEqualTo("RETIRED");
    assertThat(unresolvedSubjects(account))
        .as("a retired statement is not in UNRESOLVED_STATEMENT_LINES")
        .doesNotContain(leftover.id());
    var recon = new TrustReconciliationResponse[1];
    books.as(
        memberId,
        () ->
            recon[0] = reconciliationService.createReconciliation(account, MARCH_END, march.id()));
    assertThat(recon[0].bankStatementId())
        .as("the leftover no longer blocks the period-fit rule")
        .isEqualTo(march.id());
  }

  // --- replace ---------------------------------------------------------------------------------

  /**
   * An identical re-import with the replace intent is accepted although it ends on the replaced
   * statement's date; the replacement records who, when and why, and both events are audited.
   */
  @Test
  void identicalReImport_withReplace_isAccepted_andAudited() throws Exception {
    var account = books.freshAccount(false);
    var a =
        importStatement(account, "fu6b-same.csv", "15/03/2026,Bank charge,-5.00,-5.00,FU6B-SAME");

    var b =
        replace(account, a.id(), "fu6b-same.csv", "15/03/2026,Bank charge,-5.00,-5.00,FU6B-SAME");

    var retired = getStatement(a.id());
    assertThat(retired.status()).isEqualTo("RETIRED");
    assertThat(retired.replacedByStatementId()).isEqualTo(b.id());
    assertThat(retired.retiredBy()).isEqualTo(memberId);
    assertThat(retired.retiredAt()).isNotNull();
    assertThat(retired.retiredReason()).isEqualTo("Replaced by fu6b-same.csv");
    assertThat(retired.lines()).as("the lines are kept for the audit trail").hasSize(1);
    assertThat(b.status()).isEqualTo("IMPORTED");
    assertThat(auditCount(a.id(), "bank_statement.replaced")).isEqualTo(1L);
    assertThat(auditDetail(a.id(), "bank_statement.replaced", "replaced_by_statement_id"))
        .isEqualTo(b.id().toString());
    assertThat(auditDetail(b.id(), "bank_statement.imported", "replaces_statement_id"))
        .isEqualTo(a.id().toString());
  }

  /**
   * A draft reconciliation on the replaced statement is re-pointed to the replacement and
   * recalculated (652C's continuation); a wizard still on the old statement then gets the 409.
   */
  @Test
  void replace_repointsTheDraftReconciliation_andRecalculates() {
    var account = books.freshAccount(false);
    var client = books.newClient("fu6b draft");
    var deposit = books.deposit(account, client, "1000.00");
    var a =
        importStatement(
            account, "fu6b-da.csv", "01/03/2026,Deposit received,1000.00,1000.00,FU6B-D");
    match(a, 0, deposit);
    var draft = createAndCalculate(account, MARCH_END, a.id());
    assertThat(draft.bankBalance()).isEqualByComparingTo("1000.00");

    var b =
        replace(
            account,
            a.id(),
            "fu6b-db.csv",
            "01/03/2026,Deposit received,1000.00,1000.00,FU6B-D",
            "03/03/2026,Bank charge,-10.00,990.00,FU6B-DC");

    var repointed = getReconciliation(draft.id());
    assertThat(repointed.bankStatementId()).as("re-pointed to B").isEqualTo(b.id());
    assertThat(repointed.status()).isEqualTo("DRAFT");
    assertThat(repointed.bankBalance())
        .as("recalculated: B's closing balance")
        .isEqualByComparingTo("990.00");
    assertThat(repointed.outstandingDeposits())
        .as("the released deposit is outstanding until B matches it")
        .isEqualByComparingTo("1000.00");
    assertThat(
            auditDetail(draft.id(), "trust_reconciliation.statement_changed", "bank_statement_id"))
        .isEqualTo(b.id().toString());
    books.as(
        memberId,
        () ->
            assertThatThrownBy(
                    () -> reconciliationService.calculateReconciliation(draft.id(), a.id()))
                .isInstanceOf(ResourceConflictException.class)
                .hasMessageContaining("This reconciliation now uses a different statement"));
  }

  /**
   * A replace whose new statement does not fit a draft's period end is refused whole: nothing is
   * stored, the old statement keeps its match and its draft.
   */
  @Test
  void replace_whenTheNewStatementDoesNotFitTheDraft_isRefused_andChangesNothing() {
    var account = books.freshAccount(false);
    var client = books.newClient("fu6b nofit");
    var deposit = books.deposit(account, client, "1000.00");
    var a =
        importStatement(
            account, "fu6b-nf-a.csv", "01/03/2026,Deposit received,1000.00,1000.00,FU6B-NF");
    match(a, 0, deposit);
    var periodEnd = LocalDate.of(2026, 3, 10);
    var draft = createAndCalculate(account, periodEnd, a.id());

    books.as(
        memberId,
        () ->
            assertThatThrownBy(
                    () ->
                        reconciliationService.importBankStatement(
                            account,
                            csvFile(
                                "fu6b-nf-b.csv",
                                "01/03/2026,Deposit received,1000.00,1000.00,FU6B-NF",
                                "20/03/2026,Bank charge,-10.00,990.00,FU6B-NF2"),
                            a.id()))
                .isInstanceOfSatisfying(
                    InvalidStateException.class,
                    e -> {
                      assertThat(e.getBody().getTitle()).isEqualTo("Cannot replace statement");
                      assertThat(e.getBody().getDetail())
                          .startsWith(
                              "The draft reconciliation to 10 March 2026 uses the statement")
                          .contains("has lines dated after 10 March 2026");
                    }));

    assertThat(statementCount(account)).as("nothing stored").isEqualTo(1L);
    assertThat(statementStatus(a.id())).isEqualTo("IMPORTED");
    assertThat(matchStatus(a.lines().getFirst().id())).isEqualTo("MANUALLY_MATCHED");
    assertThat(linkedLine(deposit)).isEqualTo(a.lines().getFirst().id());
    assertThat(matchedCount(a.id())).isEqualTo(1);
    assertThat(getReconciliation(draft.id()).bankStatementId()).isEqualTo(a.id());
  }

  /**
   * A statement with a RECORDED line is not replaced (nor discarded): its rows are real trust
   * entries. Unmatching the line (which reverses them) first makes the replace possible.
   */
  @Test
  void replaceOrDiscard_whileALineIsRecorded_isRefused_untilItIsUnmatched() {
    var account = books.freshAccount(false);
    var a =
        importStatement(account, "fu6b-rec.csv", "05/03/2026,Interest earned,5.00,5.00,FU6B-INT");
    var lineId = a.lines().getFirst().id();
    books.as(
        memberId,
        () ->
            postingService.recordFromLine(
                lineId,
                new StatementLinePostingService.RecordFromLineRequest(
                    StatementLineClassification.FUND_INTEREST,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null)));
    assertThat(matchStatus(lineId)).isEqualTo("RECORDED");

    var replacement = csvFile("fu6b-rec-b.csv", "05/03/2026,Interest earned,5.00,5.00,FU6B-INT");
    books.as(
        memberId,
        () -> {
          assertThatThrownBy(
                  () -> reconciliationService.importBankStatement(account, replacement, a.id()))
              .isInstanceOfSatisfying(
                  InvalidStateException.class,
                  e -> {
                    assertThat(e.getBody().getTitle()).isEqualTo("Cannot replace statement");
                    assertThat(e.getBody().getDetail())
                        .isEqualTo(
                            "1 line of this statement was recorded from the statement and created"
                                + " trust entries. Unmatch it first, which reverses those"
                                + " entries.");
                  });
          assertThatThrownBy(() -> reconciliationService.discardBankStatement(a.id(), "wrong"))
              .isInstanceOf(InvalidStateException.class)
              .hasMessageContaining("Cannot discard statement");
        });
    assertThat(statementCount(account)).isEqualTo(1L);
    assertThat(matchStatus(lineId)).as("the recorded line is untouched").isEqualTo("RECORDED");

    books.as(memberId, () -> reconciliationService.unmatch(lineId));
    var b =
        replace(account, a.id(), "fu6b-rec-b.csv", "05/03/2026,Interest earned,5.00,5.00,FU6B-INT");
    assertThat(statementStatus(a.id())).isEqualTo("RETIRED");
    assertThat(b.status()).isEqualTo("IMPORTED");
  }

  /** A statement completed in a reconciliation (RECONCILED) is never replaced or discarded. */
  @Test
  void replaceOrDiscard_ofAReconciledStatement_isRefused() {
    var account = books.freshAccount(false);
    var client = books.newClient("fu6b done");
    var deposit = books.deposit(account, client, "1000.00");
    var a =
        importStatement(
            account, "fu6b-done.csv", "01/03/2026,Deposit received,1000.00,1000.00,FU6B-OK");
    match(a, 0, deposit);
    var draft = createAndCalculate(account, MARCH_END, a.id());
    books.as(memberId, () -> reconciliationService.completeReconciliation(draft.id(), a.id()));
    assertThat(statementStatus(a.id())).isEqualTo("RECONCILED");

    var replacement =
        csvFile("fu6b-done-b.csv", "01/03/2026,Deposit received,1000.00,1000.00,FU6B-OK");
    books.as(
        memberId,
        () -> {
          assertThatThrownBy(
                  () -> reconciliationService.importBankStatement(account, replacement, a.id()))
              .isInstanceOf(InvalidStateException.class)
              .hasMessageContaining("Cannot replace statement");
          assertThatThrownBy(() -> reconciliationService.discardBankStatement(a.id(), "wrong"))
              .isInstanceOfSatisfying(
                  InvalidStateException.class,
                  e ->
                      assertThat(e.getBody().getDetail())
                          .isEqualTo(
                              "The reconciliation to 31 March 2026 was completed with this"
                                  + " statement, so it cannot be discarded."));
        });
    assertThat(statementCount(account)).isEqualTo(1L);
    assertThat(matchStatus(a.lines().getFirst().id())).isEqualTo("MANUALLY_MATCHED");

    // A RECONCILED statement with no reconciliation row (status alone) is refused too
    var other =
        importStatement(account, "fu6b-done-2.csv", "02/03/2026,Bank charge,-1.00,-1.00,FU6B-R2");
    books.markStatementReconciledDirect(other.id());
    books.as(
        memberId,
        () ->
            assertThatThrownBy(() -> reconciliationService.discardBankStatement(other.id(), "x"))
                .isInstanceOfSatisfying(
                    InvalidStateException.class,
                    e ->
                        assertThat(e.getBody().getDetail())
                            .isEqualTo(
                                "This statement's reconciliation is complete, so it cannot be"
                                    + " discarded.")));
  }

  // --- discard ---------------------------------------------------------------------------------

  /**
   * Discard default (FU-6b D-1 rule 4): refused while a draft reconciliation uses the statement;
   * once the draft is continued with another statement the discard goes through. Matches are
   * released on a discard too; a reason is required.
   */
  @Test
  void discard_whileADraftUsesIt_isRefused_andReleasesMatchesOnceFree() throws Exception {
    var account = books.freshAccount(false);
    var client = books.newClient("fu6b discard draft");
    var deposit = books.deposit(account, client, "1000.00");
    var a =
        importStatement(
            account, "fu6b-dd-a.csv", "01/03/2026,Deposit received,1000.00,1000.00,FU6B-DD");
    autoMatch(a.id());
    var draft = createAndCalculate(account, MARCH_END, a.id());

    mockMvc
        .perform(
            post("/api/bank-statements/{id}/discard", a.id())
                .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\": \"Wrong file\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.title").value("Cannot discard statement"))
        .andExpect(
            jsonPath("$.detail")
                .value(
                    "The draft reconciliation to 31 March 2026 uses this statement. Continue that"
                        + " reconciliation with another statement, or import the corrected"
                        + " statement as its replacement."));
    mockMvc
        .perform(
            post("/api/bank-statements/{id}/discard", a.id())
                .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\": \" \"}"))
        .andExpect(status().isBadRequest());
    assertThat(statementStatus(a.id())).isEqualTo("IMPORTED");

    // The draft is continued with another statement (ending on another date), then A is free
    var b =
        importStatement(
            account,
            "fu6b-dd-b.csv",
            "01/03/2026,Deposit received,1000.00,1000.00,FU6B-DD",
            "02/03/2026,Bank charge,-1.00,999.00,FU6B-DDC");
    books.as(
        memberId, () -> reconciliationService.createReconciliation(account, MARCH_END, b.id()));
    var discarded = new BankStatementResponse[1];
    books.as(
        memberId,
        () -> discarded[0] = reconciliationService.discardBankStatement(a.id(), "Wrong file"));

    assertThat(discarded[0].status()).isEqualTo("RETIRED");
    assertThat(discarded[0].replacedByStatementId()).isNull();
    assertThat(discarded[0].matchedCount()).isZero();
    assertThat(matchStatus(a.lines().getFirst().id())).isEqualTo("UNMATCHED");
    assertThat(linkedLine(deposit)).as("the deposit is released for B").isNull();
    assertThat(auditCount(a.id(), "bank_statement.discarded")).isEqualTo(1L);
    assertThat(auditDetail(a.id(), "bank_statement.discarded", "reason")).isEqualTo("Wrong file");
    assertThat(autoMatch(b.id()).autoMatched()).isEqualTo(1);
  }

  /**
   * A RETIRED statement counts nowhere: it is hidden from the statement list (shown only with
   * includeRetired), it is not reconciled against, and none of its lines can be matched, recorded
   * or excluded; it is not auto-matched.
   */
  @Test
  void retiredStatement_isExcludedFromListsSelectionAndLineActions() throws Exception {
    var account = books.freshAccount(false);
    var client = books.newClient("fu6b retired");
    var deposit = books.deposit(account, client, "1000.00");
    var a =
        importStatement(
            account,
            "fu6b-ret.csv",
            "01/03/2026,Deposit received,1000.00,1000.00,FU6B-RET",
            "04/03/2026,Unknown credit,7.00,1007.00,FU6B-UNK");
    books.as(memberId, () -> reconciliationService.discardBankStatement(a.id(), "Duplicate"));
    var line0 = a.lines().get(0).id();
    var line1 = a.lines().get(1).id();

    mockMvc
        .perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                    "/api/trust-accounts/{accountId}/bank-statements", account)
                .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
    mockMvc
        .perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                    "/api/trust-accounts/{accountId}/bank-statements", account)
                .param("includeRetired", "true")
                .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(a.id().toString()))
        .andExpect(jsonPath("$[0].status").value("RETIRED"))
        .andExpect(jsonPath("$[0].retiredReason").value("Duplicate"));

    books.as(
        memberId,
        () -> {
          assertThatThrownBy(
                  () -> reconciliationService.createReconciliation(account, MARCH_END, a.id()))
              .hasMessageContaining("Statement retired");
          assertThatThrownBy(() -> reconciliationService.autoMatchStatement(a.id()))
              .hasMessageContaining("Statement retired");
          assertThatThrownBy(() -> reconciliationService.manualMatch(line0, deposit))
              .hasMessageContaining("Statement retired");
          assertThatThrownBy(() -> reconciliationService.excludeLine(line1, "not trust"))
              .hasMessageContaining("Statement retired");
          assertThatThrownBy(
                  () ->
                      postingService.recordFromLine(
                          line1,
                          new StatementLinePostingService.RecordFromLineRequest(
                              StatementLineClassification.FUND_INTEREST,
                              null,
                              null,
                              null,
                              null,
                              null,
                              null,
                              null)))
              .hasMessageContaining("Statement retired");
          assertThatThrownBy(() -> reconciliationService.discardBankStatement(a.id(), "again"))
              .hasMessageContaining("Cannot discard statement");
        });
    assertThat(linkedLine(deposit)).isNull();
    assertThat(matchStatus(line1)).isEqualTo("UNMATCHED");
  }

  /** A statement of another account cannot be named as the one replaced. */
  @Test
  void replace_ofAnotherAccountsStatement_isRefused() {
    var account = books.freshAccount(false);
    var otherAccount = books.freshAccount(false);
    var foreign =
        importStatement(
            otherAccount, "fu6b-foreign.csv", "03/03/2026,Bank charge,-2.00,-2.00,FU6B-F");
    var file = csvFile("fu6b-mine.csv", "03/03/2026,Bank charge,-2.00,-2.00,FU6B-F");
    books.as(
        memberId,
        () ->
            assertThatThrownBy(
                    () -> reconciliationService.importBankStatement(account, file, foreign.id()))
                .hasMessageContaining("Trust account mismatch"));
    assertThat(statementStatus(foreign.id())).isEqualTo("IMPORTED");
    assertThat(statementCount(account)).isZero();
  }

  // --- concurrency -------------------------------------------------------------------------------

  /**
   * A create that pointed a draft at the statement while a discard was running (the create holds
   * the reconciliation and the statement lock, uncommitted): the discard waits on the statement
   * lock, then sees the new draft and refuses (409) — it never retires a statement a reconciliation
   * now uses.
   */
  @Test
  void discard_racingACreateOnTheStatement_waitsAndRefuses() throws Exception {
    var account = books.freshAccount(false);
    var s =
        importStatement(account, "fu6b-race-1.csv", "02/03/2026,Bank charge,-3.00,-3.00,FU6B-RC1");

    var holder =
        holdInTransaction(
            () -> reconciliationService.createReconciliation(account, MARCH_END, s.id()));
    var outcome =
        runConcurrently(() -> reconciliationService.discardBankStatement(s.id(), "Wrong"));
    boolean blocked;
    try {
      blocked = awaitBlockedOnStatementsHeldByOrFinished(holder.pid(), outcome.finished());
    } finally {
      holder.release().countDown();
      join(holder.thread());
      join(outcome.thread());
    }

    assertThat(holder.error().get()).as("the create committed").isNull();
    assertThat(blocked).as("the discard waited on the create's statement lock").isTrue();
    assertThat(outcome.error().get())
        .isInstanceOfSatisfying(
            ResourceConflictException.class,
            e ->
                assertThat(e.getBody().getTitle())
                    .isEqualTo("Reconciliation started at the same time"));
    assertThat(statementStatus(s.id())).isEqualTo("IMPORTED");
  }

  /**
   * The other order: a discard holds the statement (retired, uncommitted); a create on it waits on
   * the statement lock and then finds it RETIRED — no draft ever points at a retired statement.
   */
  @Test
  void create_racingADiscard_waitsAndFindsTheStatementRetired() throws Exception {
    var account = books.freshAccount(false);
    var s =
        importStatement(account, "fu6b-race-2.csv", "02/03/2026,Bank charge,-3.00,-3.00,FU6B-RC2");

    var holder =
        holdInTransaction(() -> reconciliationService.discardBankStatement(s.id(), "Wrong"));
    var outcome =
        runConcurrently(
            () -> reconciliationService.createReconciliation(account, MARCH_END, s.id()));
    boolean blocked;
    try {
      blocked = awaitBlockedOnStatementsHeldByOrFinished(holder.pid(), outcome.finished());
    } finally {
      holder.release().countDown();
      join(holder.thread());
      join(outcome.thread());
    }

    assertThat(holder.error().get()).as("the discard committed").isNull();
    assertThat(blocked).as("the create waited on the discard's statement lock").isTrue();
    assertThat(outcome.error().get())
        .isInstanceOfSatisfying(
            InvalidStateException.class,
            e -> assertThat(e.getBody().getTitle()).isEqualTo("Statement retired"));
    assertThat(reconciliationRows(account)).isZero();
  }

  /**
   * FU-6b review: a reversal of a matched row commits while a discard releases its match. The
   * release locks the row (line, then transaction, then statement), so the discard waits for the
   * reversal and unlinks the row as committed: REVERSED stays REVERSED. With a plain read the
   * release would write the row's pre-reversal status back over the committed reversal.
   */
  @Test
  void discard_racingAReversalOfAMatchedRow_waitsAndKeepsTheReversal() throws Exception {
    var account = books.freshAccount(false);
    var client = books.newClient("fu6b reversal race");
    books.deposit(account, client, "10000.00");
    var paymentId = books.requestPayment(account, client, "3000.00");
    books.approve(account, paymentId, approverId, Expected.cashbookAndClient("-3000.00"));
    var s =
        importStatement(
            account, "fu6b-rev-race.csv", "01/03/2026,Payment,-3000.00,-3000.00,FU6B-RV");
    match(s, 0, paymentId);

    var holder =
        holdInTransaction(
            () ->
                transactionService.reverseTransaction(
                    paymentId, new TrustTransactionService.ReverseRequest("fu6b race", null)));
    var outcome =
        runConcurrently(() -> reconciliationService.discardBankStatement(s.id(), "Wrong"));
    boolean blocked;
    try {
      blocked =
          awaitBlockedHeldByOrFinished(holder.pid(), outcome.finished(), "trust_transactions");
    } finally {
      holder.release().countDown();
      join(holder.thread());
      join(outcome.thread());
    }

    assertThat(holder.error().get()).as("the reversal committed").isNull();
    assertThat(blocked).as("the discard waited on the reversal's row lock").isTrue();
    assertThat(outcome.error().get()).as("the discard").isNull();
    assertThat(books.statusOf(paymentId)).as("the reversal stands").isEqualTo("REVERSED");
    assertThat(linkedLine(paymentId)).as("unlinked by the discard").isNull();
    assertThat(statementStatus(s.id())).isEqualTo("RETIRED");
  }

  // --- helpers -----------------------------------------------------------------------------------

  private record Holder(
      Thread thread, CountDownLatch release, int pid, AtomicReference<Throwable> error) {}

  private record Outcome(Thread thread, AtomicBoolean finished, AtomicReference<Throwable> error) {}

  /** Runs {@code action} in a transaction on its own thread and holds it open until released. */
  private Holder holdInTransaction(Runnable action) throws InterruptedException {
    var started = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    var pid = new AtomicReference<Integer>();
    var error = new AtomicReference<Throwable>();
    Thread thread =
        Thread.ofPlatform()
            .start(
                () -> {
                  try {
                    books.as(
                        memberId,
                        () ->
                            transactionTemplate.executeWithoutResult(
                                tx -> {
                                  action.run();
                                  pid.set(
                                      ((Number)
                                              entityManager
                                                  .createNativeQuery("SELECT pg_backend_pid()")
                                                  .getSingleResult())
                                          .intValue());
                                  started.countDown();
                                  await(release);
                                }));
                  } catch (Throwable t) {
                    error.set(t);
                    started.countDown();
                  }
                });
    assertThat(started.await(30, TimeUnit.SECONDS)).isTrue();
    assertThat(error.get()).as("the holder's action").isNull();
    return new Holder(thread, release, pid.get(), error);
  }

  private Outcome runConcurrently(Runnable action) {
    var finished = new AtomicBoolean(false);
    var error = new AtomicReference<Throwable>();
    Thread thread =
        Thread.ofPlatform()
            .start(
                () -> {
                  try {
                    books.as(memberId, action);
                  } catch (Throwable t) {
                    error.set(t);
                  } finally {
                    finished.set(true);
                  }
                });
    return new Outcome(thread, finished, error);
  }

  private boolean awaitBlockedOnStatementsHeldByOrFinished(
      int holderPid, AtomicBoolean waiterFinished) {
    return awaitBlockedHeldByOrFinished(holderPid, waiterFinished, "bank_statements");
  }

  /**
   * Waits (bounded, 30 s) until a backend blocked by {@code holderPid} runs a statement on {@code
   * table} (true) or the waiter finishes without having been seen blocked (false).
   */
  private boolean awaitBlockedHeldByOrFinished(
      int holderPid, AtomicBoolean waiterFinished, String table) {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
    while (System.nanoTime() < deadline) {
      Long blocked =
          jdbc.queryForObject(
              "SELECT count(*) FROM pg_stat_activity WHERE ? = ANY(pg_blocking_pids(pid))"
                  + " AND query ILIKE ?",
              Long.class,
              holderPid,
              "%" + table + "%");
      if (blocked != null && blocked > 0) {
        return true;
      }
      if (waiterFinished.get()) {
        return false;
      }
      LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10)); // poll interval
    }
    throw new AssertionError("the waiter neither blocked nor finished within 30 s");
  }

  private static void join(Thread thread) throws InterruptedException {
    thread.join(30_000);
    assertThat(thread.isAlive()).as("thread finished within 30 s").isFalse();
  }

  private static void await(CountDownLatch latch) {
    try {
      latch.await(30, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  private BankStatementResponse replace(
      UUID account, UUID replacesStatementId, String fileName, String... lines) {
    var file = csvFile(fileName, lines);
    var statement = new BankStatementResponse[1];
    books.as(
        memberId,
        () ->
            statement[0] =
                reconciliationService.importBankStatement(account, file, replacesStatementId));
    return statement[0];
  }

  private BankStatementResponse getStatement(UUID statementId) {
    var statement = new BankStatementResponse[1];
    books.as(memberId, () -> statement[0] = reconciliationService.getBankStatement(statementId));
    return statement[0];
  }

  private TrustReconciliationResponse getReconciliation(UUID reconciliationId) {
    var recon = new TrustReconciliationResponse[1];
    books.as(memberId, () -> recon[0] = reconciliationService.getReconciliation(reconciliationId));
    return recon[0];
  }

  private TrustReconciliationResponse createAndCalculate(
      UUID account, LocalDate periodEnd, UUID statementId) {
    var recon = new TrustReconciliationResponse[1];
    books.as(
        memberId,
        () -> {
          var created = reconciliationService.createReconciliation(account, periodEnd, statementId);
          recon[0] =
              reconciliationService.calculateReconciliation(
                  created.id(), created.bankStatementId());
        });
    return recon[0];
  }

  private void match(BankStatementResponse statement, int index, UUID transactionId) {
    var lineId = statement.lines().get(index).id();
    books.as(memberId, () -> reconciliationService.manualMatch(lineId, transactionId));
  }

  private long auditCount(UUID entityId, String eventType) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM \"%s\".audit_events WHERE entity_id = ? AND event_type = ?"
            .formatted(books.schema()),
        Long.class,
        entityId,
        eventType);
  }

  private String auditDetail(UUID entityId, String eventType, String key) {
    return jdbc.queryForObject(
        "SELECT details ->> ? FROM \"%s\".audit_events WHERE entity_id = ? AND event_type = ?"
            .formatted(books.schema()),
        String.class,
        key,
        entityId,
        eventType);
  }

  private long reconciliationRows(UUID account) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM \"%s\".trust_reconciliations WHERE trust_account_id = ?"
            .formatted(books.schema()),
        Long.class,
        account);
  }

  private ResultActions importHttp(
      UUID account, UUID replacesStatementId, String fileName, String... lines) throws Exception {
    var request =
        multipart("/api/trust-accounts/{accountId}/bank-statements", account)
            .file(csvFile(fileName, lines))
            .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER));
    if (replacesStatementId != null) {
      request.param("replacesStatementId", replacesStatementId.toString());
    }
    return mockMvc.perform(request);
  }

  private BankStatementResponse importStatement(UUID account, String fileName, String... lines) {
    var file = csvFile(fileName, lines);
    var statement = new BankStatementResponse[1];
    books.as(
        memberId, () -> statement[0] = reconciliationService.importBankStatement(account, file));
    return statement[0];
  }

  private static MockMultipartFile csvFile(String fileName, String... lines) {
    var csv =
        """
        FNB Trust Account Statement - Account 62400000000
        Date,Description,Amount,Balance,Reference
        %s
        """
            .formatted(String.join("\n", lines));
    return new MockMultipartFile(
        "file", fileName, "text/csv", csv.getBytes(StandardCharsets.UTF_8));
  }

  private MatchResult autoMatch(UUID statementId) {
    var result = new MatchResult[1];
    books.as(memberId, () -> result[0] = reconciliationService.autoMatchStatement(statementId));
    return result[0];
  }

  /** The statement ids named by the account's UNRESOLVED_STATEMENT_LINES flag (no statement). */
  private List<UUID> unresolvedSubjects(UUID account) {
    var flags = new List<?>[1];
    books.as(
        memberId, () -> flags[0] = integrityService.check(account, books.today(), null).flags());
    @SuppressWarnings("unchecked")
    var list = (List<IntegrityFlag>) flags[0];
    return list.stream()
        .filter(f -> TrustIntegrityService.UNRESOLVED_STATEMENT_LINES.equals(f.code()))
        .flatMap(f -> f.subjects().stream())
        .map(FlagSubject::id)
        .toList();
  }

  private long statementCount(UUID account) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM \"%s\".bank_statements WHERE trust_account_id = ?"
            .formatted(books.schema()),
        Long.class,
        account);
  }

  private String statementStatus(UUID statementId) {
    return jdbc.queryForObject(
        "SELECT status FROM \"%s\".bank_statements WHERE id = ?".formatted(books.schema()),
        String.class,
        statementId);
  }

  private int matchedCount(UUID statementId) {
    return jdbc.queryForObject(
        "SELECT matched_count FROM \"%s\".bank_statements WHERE id = ?".formatted(books.schema()),
        Integer.class,
        statementId);
  }

  private String matchStatus(UUID lineId) {
    return jdbc.queryForObject(
        "SELECT match_status FROM \"%s\".bank_statement_lines WHERE id = ?"
            .formatted(books.schema()),
        String.class,
        lineId);
  }

  private UUID linkedLine(UUID transactionId) {
    return jdbc.queryForObject(
        "SELECT bank_statement_line_id FROM \"%s\".trust_transactions WHERE id = ?"
            .formatted(books.schema()),
        UUID.class,
        transactionId);
  }

  private UUID linkedLineStatement(UUID transactionId) {
    return jdbc.queryForObject(
        ("SELECT l.bank_statement_id FROM \"%1$s\".trust_transactions t"
                + " JOIN \"%1$s\".bank_statement_lines l ON l.id = t.bank_statement_line_id"
                + " WHERE t.id = ?")
            .formatted(books.schema()),
        UUID.class,
        transactionId);
  }
}
