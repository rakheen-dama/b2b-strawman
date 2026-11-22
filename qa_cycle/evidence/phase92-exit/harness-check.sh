#!/usr/bin/env bash
#
# harness-check.sh — Phase 92 exit gate (652F, task 652.33): prove every §16.9.2 harness test
# named in harness.md ran green in a backend verify.
#
# For each (path, class, method) row below it looks for a surefire
#   <testcase classname="CLASS" name="METHOD" ...>
# in the TEST-*.xml files and reports, per row:
#   PASS     found, and no <failure>, <error> or <skipped> child
#   FAIL     found, with a <failure> or <error> child
#   SKIPPED  found, with a <skipped> child (and no failure / error)
#   ABSENT   no such testcase in the reports
# Matching is by prefix, so that a @Nested class (reported as Outer$Inner) and a parameterised
# name (reported as name(String)[1]) still match: classname == CLASS or starts with CLASS$, and
# name == METHOD or starts with METHOD( or METHOD[. When several testcases match one row, any
# FAIL wins, then any SKIPPED, else PASS. A row whose testcase carries a <flakyFailure> /
# <flakyError> (passed on a surefire rerun) prints PASS with a "(flaky)" note.
# CDATA sections (system-out / system-err) are stripped first, so log text can never be read as
# a <failure> tag.
#
# Usage:  bash harness-check.sh [surefire-reports-dir]
#         (default: backend/target/surefire-reports, relative to the current directory)
# Exit:   0 every row PASS · 1 at least one row FAIL / SKIPPED / ABSENT · 2 usage / no reports
#
# FU-27 (2026-11-23): 70 rows — path 12 gains the recoverable BANK_CHARGE unmatch test; path 11's
# seededClients_holdR30000_andMinusR3000_exactlyOneInDebit now also asserts CLIENT_LEDGER_IN_DEBIT.
#
# Plain bash 3.2 + find + awk (macOS BSD tools; no GNU-only flags). Read-only: it never writes
# into the reports directory.
#
set -eu

DIR=${1:-backend/target/surefire-reports}
if [ ! -d "$DIR" ]; then
  echo "harness-check: reports directory not found: $DIR" >&2
  exit 2
fi

TMP=$(mktemp "${TMPDIR:-/tmp}/harness-check.XXXXXX")
trap 'rm -f "$TMP"' EXIT

# 1. Every <testcase> in every TEST-*.xml → "classname|name|STATUS|FLAKY" lines.
find "$DIR" -type f -name 'TEST-*.xml' -exec awk '
  function attr(s, a,   r) {
    if (match(s, " " a "=\"[^\"]*\"")) { r = substr(s, RSTART, RLENGTH); sub("^ " a "=\"", "", r); sub("\"$", "", r); return r }
    return ""
  }
  function emit() { print cls "|" nm "|" st "|" fl; intc = 0 }
  function children(s) {
    if (s ~ /<(failure|error)[ \/>]/ || s ~ /<(failure|error)$/) st = "FAIL"
    else if (st != "FAIL" && (s ~ /<skipped[ \/>]/ || s ~ /<skipped$/)) st = "SKIPPED"
    if (s ~ /<flaky(Failure|Error)[ \/>]/) fl = "FLAKY"
  }
  FNR == 1 { incdata = 0; intc = 0 }
  {
    line = $0
    # strip CDATA (may span lines)
    if (incdata) { e = index(line, "]]>"); if (e == 0) next; line = substr(line, e + 3); incdata = 0 }
    while ((b = index(line, "<![CDATA[")) > 0) {
      rest = substr(line, b + 9); e = index(rest, "]]>")
      if (e > 0) line = substr(line, 1, b - 1) substr(rest, e + 3)
      else { line = substr(line, 1, b - 1); incdata = 1 }
    }
    while (line != "") {
      if (!intc) {
        t = index(line, "<testcase")
        if (t == 0) break
        line = substr(line, t)
        g = index(line, ">")
        tag = (g > 0) ? substr(line, 1, g) : line
        cls = attr(tag, "classname"); nm = attr(tag, "name"); st = "PASS"; fl = ""; intc = 1
        if (g == 0) { line = ""; break }
        line = substr(line, g + 1)
        if (tag ~ /\/>$/) emit()
      } else {
        c = index(line, "</testcase>")
        if (c > 0) { children(substr(line, 1, c - 1)); emit(); line = substr(line, c + 11) }
        else { children(line); line = "" }
      }
    }
  }
' {} + > "$TMP"

# 2. The rows of harness.md: path|fully-qualified class|method
ROWS=$(cat <<'EOF'
1|io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustEquationCharacterisationIntegrationTest|deposit_movesCashbookAndClient
2|io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustEquationCharacterisationIntegrationTest|paymentRequested_movesNothing
2|io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustEquationCharacterisationIntegrationTest|paymentApprovedSingleMode_movesCashbookAndClient
2|io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustEquationCharacterisationIntegrationTest|paymentDualMode_firstApprovalMovesNothing_secondPosts
2|io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustEquationCharacterisationIntegrationTest|paymentRejected_movesNothing
3|io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustEquationCharacterisationIntegrationTest|feeTransferRequested_movesNothing
3|io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustEquationCharacterisationIntegrationTest|feeTransferApproved_movesCashbookAndClient
3|io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustEquationCharacterisationIntegrationTest|feeTransferRejected_movesNothing
4|io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustEquationCharacterisationIntegrationTest|refundRequested_movesNothing
4|io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustEquationCharacterisationIntegrationTest|refundApproved_movesCashbookAndClient
4|io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustEquationCharacterisationIntegrationTest|refundRejected_movesNothing
5|io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustEquationCharacterisationIntegrationTest|interClientTransfer_isCashbookNeutralAndClientTotalUnchanged
6|io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustEquationCharacterisationIntegrationTest|paymentReversal_movesCashbookAndClientBack
6|io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustEquationCharacterisationIntegrationTest|feeTransferReversal_movesBackAndCascadesToFeeNote
6|io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustEquationCharacterisationIntegrationTest|refundReversal_movesCashbookAndClientBack
6|io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustReversalEquationIntegrationTest|debitReversalForAClientWithoutAnAnchor_movesBothSides
7|io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustEquationCharacterisationIntegrationTest|creditReversalRequested_movesNothing
7|io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustEquationCharacterisationIntegrationTest|creditReversalApproved_movesCashbookAndClient
7|io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustEquationCharacterisationIntegrationTest|creditReversalRejected_movesNothing
7|io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustEquationCharacterisationIntegrationTest|creditReversalRejectedThenReversedAgain_isAccepted
7|io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustReversalEquationIntegrationTest|approvedCreditReversal_movesCashbookAndClient
7|io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustReversalEquationIntegrationTest|rejectedCreditReversal_leavesOriginalReversible
8|io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustEquationCharacterisationIntegrationTest|transferReversal_reversesBothLegs
8|io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustReversalEquationIntegrationTest|transferReversedOnAnEarlierDate_bothLegsCarryTheDate
9|io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustReversalEquationIntegrationTest|recordedDeposit_isReversible
9|io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustReversalEquationIntegrationTest|recordedDepositReversal_approved_movesCashbookAndClient
10|io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustReversalEquationIntegrationTest|debitReversedOnAnEarlierDate_asAtBalancesBeforeBetweenAndAfter
11|io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustEquationCharacterisationIntegrationTest|seededDemoBooks_cashbookEqualsClientLedgers
11|io.b2mash.b2b.b2bstrawman.demo.seed.LegalDemoTrustBooksIntegrationTest|seededBooks_satisfyEq2_cashbookEqualsClientLedgers
11|io.b2mash.b2b.b2bstrawman.demo.seed.LegalDemoTrustBooksIntegrationTest|seededClients_holdR30000_andMinusR3000_exactlyOneInDebit
12|io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation.StatementLinePostingIntegrationTest|recordsFundInterest_fromACreditLine_dated_the_lines_date
12|io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation.StatementLinePostingIntegrationTest|recordsFundSweep_fromADebitLine_withTheStatementReference_andNoSufficiencyCheck
12|io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation.StatementLinePostingIntegrationTest|recordsBankCharge_recoverable_withVat
12|io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation.StatementLinePostingIntegrationTest|recordsBankCharge_notRecoverable_zeroVat
12|io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation.StatementLinePostingIntegrationTest|recordsChargeRefund_fromACreditLine
12|io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation.StatementLinePostingIntegrationTest|aRecordedLine_reversesOnUnmatch_dated_the_lines_date
12|io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation.StatementLinePostingIntegrationTest|afterUnmatch_theCancellingPairIsNotAnOutstandingItem
12|io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation.StatementLinePostingIntegrationTest|audit_recordAndUnmatch
12|io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation.StatementLinePostingIntegrationTest|aRecordedChargeRefund_reversesOnUnmatch_dated_the_lines_date
12|io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation.StatementLinePostingIntegrationTest|aRecordedRecoverableBankCharge_reversesOnUnmatch_dated_the_lines_date_andIsNeverToRefund
13|io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation.StatementLinePostingIntegrationTest|aChargeBeforeAnyInterest_drivesTheFundLedgerIntoDebit_andTheBooksStayBalanced
13|io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.books.TrustIntegrityServiceIntegrationTest|fundLedgerInDebit_isAWarning_andClearedByInterest
14|io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.creditor.UnidentifiedDepositIntegrationTest|recordFromACreditLine_opensAHeldItem_thatItsRowNames
14|io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.creditor.UnidentifiedDepositIntegrationTest|unmatchWhileHeld_reversesTheRowAndTheItem_onTheLinesDate_andAReRecordOpensASecondItem
14|io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.creditor.UnidentifiedDepositIntegrationTest|unmatch_whileAResolutionIsPendingOrOnceAllocated_isRefused_andNothingMoves
15|io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.creditor.UnidentifiedDepositIntegrationTest|allocation_requested_movesNothing_andHoldsTheItemPending
15|io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.creditor.UnidentifiedDepositIntegrationTest|allocation_approved_movesUnidentifiedToClient_onTheAllocationDate
15|io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.creditor.UnidentifiedDepositIntegrationTest|allocation_rejected_movesNothing_andTheItemCanBeAllocatedAgain
15|io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.creditor.UnidentifiedDepositIntegrationTest|allocation_undoneAfterApproval_reopensTheItem_EQ4BeforeBetweenAfter
16|io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.creditor.UnidentifiedDepositIntegrationTest|fundPayment_requested_thenApproved_takesItOutOfTheCashbook
16|io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.creditor.UnidentifiedDepositIntegrationTest|fundPayment_rejected_movesNothing_andTheItemStaysHeld
16|io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.creditor.UnidentifiedDepositIntegrationTest|fundPayment_reversed_reopensTheItem
17|io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.investment.TrustInvestmentServiceIntegrationTest|path17_clientInstruction_requestMovesNothing_approvalMovesBothAccounts_clientTotalSame
17|io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.investment.TrustInvestmentServiceIntegrationTest|path17_rejection_movesNothing_andCancelsTheInvestment
17|io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.investment.TrustInvestmentServiceIntegrationTest|path17_dualApproval_postsBothRowsOnlyOnTheSecondApproval
18|io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.investment.TrustInvestmentServiceIntegrationTest|path18_firmDiscretion_placementAndReturn_interAccountOnBoth_noClientMoves
19|io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.investment.TrustInvestmentServiceIntegrationTest|path19_clientInstruction_withdrawal_returnsTheBalanceToThePlacementsMatter
20|io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.investment.TrustInvestmentInterestIntegrationTest|path20_gross_fromALine_postsTheNetAndTheFundShare_thenTheSweepEmptiesTheFundLedger
20|io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.investment.TrustInvestmentInterestIntegrationTest|path20_net_fromABankAdvice_postsTheNetOnly_andTheEntryHoldsGrossAndFund
20|io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.investment.TrustInvestmentInterestIntegrationTest|path20_reversal_ofABankAdviceEntry_isAContraDatedTheEntryDate
20|io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.investment.TrustInvestmentInterestIntegrationTest|path20_net_fromALine_reversesByUnmatch
21|io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.investment.TrustInvestmentInterestIntegrationTest|path21_section86_3_interestOnAnInvestmentAccount_isFundInterest_andItsSweep
22|io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.books.TrustPositionAsAtIntegrationTest|path22_paymentMatchedToANextMonthLine_isOutstandingAtTheFirstMonthEndOnly
23|io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.books.TrustMonthlyListIntegrationTest|path23_aRowBackDatedIntoALockedMonth_leavesTheListUnchanged_movesTheLiveAsAtPosition_andRaisesTheFlag
24|io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation.TrustReconciliationCompletionGateIntegrationTest|refusedOnG1_whenTheAdjustedBankDiffersFromTheCashbook
24|io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation.TrustReconciliationCompletionGateIntegrationTest|refusedOnG2_whenTheCashbookDiffersFromTheTrustCreditors
24|io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation.TrustReconciliationCompletionGateIntegrationTest|refusedOnG4_whenAStatementLineIsUnmatched
24|io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation.TrustReconciliationCompletionGateIntegrationTest|passes_afterTheLineIsRecorded_statementReconciled_flagsStored_audited
24|io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation.TrustReconciliationCompletionGateIntegrationTest|completion_atAMonthEnd_locksTheList_andAuditsItsId_midMonthDoesNot
24|io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.books.TrustMonthlyListIntegrationTest|path24_lock_aMonthEndLocks_aMidMonthDoesNot
EOF
)

# 3. Resolve each row against the testcases.
printf '%s\n' "$ROWS" | awk -F'|' -v cases="$TMP" '
  BEGIN { while ((getline l < cases) > 0) { n++; split(l, p, "|"); C[n] = p[1]; N[n] = p[2]; S[n] = p[3]; F[n] = p[4] } }
  NF == 3 {
    path = $1; cls = $2; m = $3; hit = 0; fail = 0; skip = 0; flaky = 0
    for (i = 1; i <= n; i++) {
      if (!(C[i] == cls || index(C[i], cls "$") == 1)) continue
      if (!(N[i] == m || index(N[i], m "(") == 1 || index(N[i], m "[") == 1)) continue
      hit++
      if (S[i] == "FAIL") fail++
      else if (S[i] == "SKIPPED") skip++
      if (F[i] == "FLAKY") flaky++
    }
    st = (hit == 0) ? "ABSENT" : (fail > 0) ? "FAIL" : (skip > 0) ? "SKIPPED" : "PASS"
    short = cls; sub(/.*\./, "", short)
    note = (st == "PASS" && flaky > 0) ? " (flaky: passed on a surefire rerun)" : ""
    printf "%-8s path %-3s %s#%s%s\n", st, path, short, m, note
    rows++; cnt[st]++
  }
  END {
    printf "TOTAL rows=%d PASS=%d FAIL=%d SKIPPED=%d ABSENT=%d (testcases read: %d)\n", rows, cnt["PASS"], cnt["FAIL"], cnt["SKIPPED"], cnt["ABSENT"], n
    exit (cnt["PASS"] == rows && rows > 0) ? 0 : 1
  }
'
