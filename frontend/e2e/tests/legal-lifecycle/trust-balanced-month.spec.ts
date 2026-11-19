/**
 * Phase 92 exit gate — one balanced month, worked (architecture §16.9.6; tasks 652.16–652.31).
 *
 * The month is the previous calendar month by the SA business date, computed ONCE (OQ #12); the
 * worked example is October 2026 ("31" = the last day, "30" = the day before). Every figure of
 * §16.9.6 is asserted literally (R-12): a mismatch is a product defect to report, never a spec
 * edit, never a skip.
 *
 * Rulings applied (tasks/phase92-decisions.md, 652E D-1 … D-12):
 *  - D-1  G is the seeded primary GENERAL account (every posting screen binds to the primary); the
 *         spec creates I-A and I-B. Step 1 fails fast unless G is untouched (fresh stack).
 *  - D-2  reversed originals match their own bank lines (652H) — twelve matched, seven unmatched.
 *  - D-3  the portal runs outside the compose stack: PORTAL_BASE_URL, no default, unset = FAIL.
 *  - D-7  the fee note is 4 h × R 2 000,00 + 15 % VAT = R 9 200,00; the fee transfer is R 8 500,00.
 *  - D-8  I-A's withdrawal line is matched in the wizard (Back → Review Matches → Match).
 *  - D-9  one direct complete call, refused once with G1 + G4.
 *  - D-12 own Playwright project `exit-month` — run alone, on a fresh stack:
 *           bash compose/scripts/e2e-down.sh && bash compose/scripts/e2e-up.sh
 *           (portal: cd portal && NEXT_PUBLIC_PORTAL_API_URL=http://localhost:8081 pnpm dev)
 *           PLAYWRIGHT_BASE_URL=http://localhost:3001 PORTAL_BASE_URL=http://localhost:3002 \
 *             pnpm test:e2e:exit-month
 *
 * Single-shot per stack: a completed reconciliation for the month refuses a second one (652C).
 * Evidence (screenshots, the CSVs as generated, the refused complete, psql) goes to
 * qa_cycle/evidence/phase92-exit/ (README.md there).
 */
import { test, expect, type BrowserContext, type Locator, type Page } from "@playwright/test";
import {
  apiOk,
  apiStatus,
  createActiveClient,
  createMatter,
  createMatterRate,
  createTask,
  memberNamed,
} from "../../helpers/api";
import {
  BASE,
  escapeRegExp,
  isoDate,
  openMatter,
  selectNativeOption,
} from "../../helpers/legal-depth-ii";
import {
  feeNoteTotalsRow,
  generateFeeNoteDraft,
  logTimeOnTask,
  openClientFeeNotes,
  openFeeNote,
  randPattern,
} from "../../helpers/legal-lifecycle";
import {
  bankLine,
  cents,
  CSV_NAMES,
  csvFile,
  dbCents,
  ddFor,
  evidenceShot,
  exactText,
  exitMonth,
  firmSession,
  idFromTestId,
  investmentKhumaloCsv,
  investmentNdlovuCsv,
  monthEndTrustAccountCsv,
  portalBaseUrl,
  portalLogin,
  randExact,
  randIn,
  readReportRows,
  reportRow,
  reportSummary,
  runPsqlCheck,
  runReport,
  statCardValue,
  txRowByRef,
  writeEvidence,
} from "../../helpers/trust-balanced-month";

// ── The month (OQ #12: once, at load) ────────────────────────────────────────────────────────
const M = exitMonth();

// ── Set-up names (§16.9.6; D-1 for G) ────────────────────────────────────────────────────────
const G = "Mathebula & Partners Trust Account"; // compose/seed/lib/trust.sh — primary GENERAL
const IA = { name: "Section 86(4) investment — Ndlovu", number: "62000000001" };
const IB = { name: "Section 86(4) investment — Khumalo", number: "62000000002" };
const NDLOVU = {
  client: "Ndlovu Family Trust",
  matter: "Ndlovu — estate transfer",
  email: "ndlovu.family.trust@exit-month.example.com",
};
const KHUMALO = {
  client: "Khumalo Holdings",
  matter: "Khumalo — lease dispute",
  email: "khumalo.holdings@exit-month.example.com",
};
const M1_TASK = "Estate transfer — draft the transfer documents";
const BANK = "Standard Bank";
const BRANCH = "051001";

// ── §16.9.6 figures, as the architecture writes them (R-12: asserted literally) ──────────────
/** A reconciliation summary (wizard step 4) as at the period end. */
interface ReconFigures {
  bank: string;
  outstandingDeposits: string;
  outstandingPayments: string;
  adjustedBank: string;
  cashbook: string;
  bankToCashbook: string;
  trustCreditors: string;
  cashbookToCreditors: string;
  clientLedgers: string;
  /** null = the row is not shown (zero). */
  investmentCreditors: string | null;
  fund: string;
  unidentified: string;
  unresolvedLines: number;
}

const ZERO_RECON: ReconFigures = {
  bank: "0,00",
  outstandingDeposits: "0,00",
  outstandingPayments: "0,00",
  adjustedBank: "0,00",
  cashbook: "0,00",
  bankToCashbook: "0,00",
  trustCreditors: "0,00",
  cashbookToCreditors: "0,00",
  clientLedgers: "0,00",
  investmentCreditors: null,
  fund: "0,00",
  unidentified: "0,00",
  unresolvedLines: 0,
};

const FIG = {
  /** Step 2 (#1–#10 posted): Ndlovu's balance just before the placement (step 3's "before"). */
  ndlovuBeforePlacement: "69 000,00",
  /** Step 3: after #11 is approved. */
  ndlovuAfterPlacement: { general: "19 000,00", invested: "50 000,00", total: "69 000,00" },
  /** D-7: the fee note the R 8 500,00 fee transfer settles in part. */
  feeNoteTotal: "9 200,00",
  feeNoteBalanceAfterTransfer: "700,00",
  /** Steps 4–6 (I-A). */
  iAInterest: { gross: "400,00", fund: "20,00", client: "380,00" },
  iAInvestment: { balance: "50 380,00", gross: "400,00", fund: "20,00", net: "380,00" },
  iAWithdrawal: "50 380,00",
  iA: ZERO_RECON,
  /** Step 7 (I-B). */
  iBInterest: { gross: "100,00", fund: "5,00", client: "95,00" },
  iB: {
    ...ZERO_RECON,
    bank: "20 095,00",
    adjustedBank: "20 095,00",
    cashbook: "20 095,00",
    trustCreditors: "20 095,00",
    investmentCreditors: "20 095,00",
  } satisfies ReconFigures,
  /** Step 10 (six of seven recorded): the bank R 1 250,00 above the cashbook, one line open. */
  gStep10: {
    bank: "103 130,00",
    outstandingPayments: "1 500,00",
    adjustedBank: "101 630,00",
    cashbook: "100 380,00",
    bankToCashbook: "1 250,00",
    unresolvedLines: 1,
  },
  /** Step 11: both comparisons agree. */
  gStep11: "101 630,00",
  /** Step 12. */
  fundLedger: "0,00",
  fundLedgerRows: 5,
  unidentifiedHeld: ["7 500,00", "1 250,00"],
  /** Step 13. */
  allocation: "7 500,00",
  /** Step 14 — trust position as at the 30th, G. */
  at30: {
    cashbook: "102 957,50",
    clientLedgers: "101 880,00",
    fund: "-172,50",
    unidentified: "1 250,00",
    trustCreditors: "102 957,50",
    difference: "0,00",
    fundInDebit: "172,50",
    chargesToRefund: "57,50",
  },
  /** Step 15 — the expected table at completion, 31st. */
  g: {
    bank: "103 130,00",
    outstandingDeposits: "0,00",
    outstandingPayments: "1 500,00",
    adjustedBank: "101 630,00",
    cashbook: "101 630,00",
    bankToCashbook: "0,00",
    trustCreditors: "101 630,00",
    cashbookToCreditors: "0,00",
    clientLedgers: "100 380,00",
    investmentCreditors: null,
    fund: "0,00",
    unidentified: "1 250,00",
    unresolvedLines: 0,
  } satisfies ReconFigures,
  firm: {
    bank: "123 225,00",
    adjustedBank: "121 725,00",
    cashbook: "121 725,00",
    clientLedgers: "100 380,00",
    investmentCreditors: "20 095,00",
    fund: "0,00",
    unidentified: "1 250,00",
    interAccount: "0,00",
    trustCreditors: "121 725,00",
    difference: "0,00",
  },
  ndlovuGeneral: "69 380,00",
  khumalo: { general: "31 000,00", invested: "20 095,00", total: "51 095,00" },
  /** Step 16 — Rule 54.15 (firm-wide). */
  list: {
    ndlovu: { general: "69 380,00", invested: "0,00", total: "69 380,00" },
    khumalo: { general: "31 000,00", invested: "20 095,00", total: "51 095,00" },
    fund: "0,00",
    unidentified: "1 250,00",
    total: "121 725,00",
    trustBank: "101 630,00",
    trustInvestment: "20 095,00",
    difference: "0,00",
  },
  /** Step 17 — Rule 54.28, the month. */
  schedule: {
    g: {
      grossInterest: "1 234,56",
      recoverable: "115,00",
      nonRecoverable: "57,50",
      vat: "22,50", // 15,00 (recoverable) + 7,50 (non-recoverable); split shown in step 12
      refunded: "57,50",
      paidToFund: "1 119,56",
      broughtForward: "0,00",
      carriedForward: "0,00",
    },
    iA: { grossInterest: "400,00", client: "380,00", fundCredited: "20,00", paidToFund: "20,00" },
    iB: { grossInterest: "100,00", client: "95,00", fundPaidAtSource: "5,00" },
    totalGrossInterest: "1 734,56",
    totalGrossCharges: "172,50",
  },
  /** Step 18 — Section 35 data pack, the two Fund sweeps. */
  sweeps: { g: "-1 119,56", iA: "-20,00" },
  /** Step 19 — portal (D-4 labels). */
  portal: {
    ndlovuInterest: "380,00",
    ndlovuHeld: "69 380,00",
    khumaloHeld: "51 095,00",
    khumaloGeneral: "31 000,00",
    khumaloInvested: "20 095,00",
    khumaloInterest: "95,00",
    hidden: "1 234,56",
  },
} as const;

// ── Run state (ids and references captured along the way) ────────────────────────────────────
interface RunState {
  gId: string;
  iAId: string;
  iBId: string;
  ndlovuId: string;
  khumaloId: string;
  m1: string;
  m2: string;
  feeNoteId: string;
  invAId: string;
  invBId: string;
  invA: string;
  invB: string;
  invWdA: string;
  iAListId: string;
  iBListId: string;
  gListId: string;
  sweepLineG: string;
  sweepLineIA: string;
  bankG: string;
  bankIA: string;
  bankIB: string;
}
const state: Partial<RunState> = {};
function need<K extends keyof RunState>(key: K): RunState[K] {
  const value = state[key];
  if (value === undefined)
    throw new Error(`run state "${key}" was not recorded by an earlier step`);
  return value as RunState[K];
}

// ── Sessions: Alice (owner) records and reconciles; Bob (admin) approves ─────────────────────
let aliceCtx: BrowserContext;
let bobCtx: BrowserContext;
/** Alice's working tab. */
let alice: Page;
/** Alice's reconciliation wizard — stays open from step 9 to step 15 (a second tab). */
let wizard: Page;
let bob: Page;

// ── Spec-local helpers (UI) ──────────────────────────────────────────────────────────────────

/** Trust → Transactions (the primary banking account = G), narrowed by URL filters. */
async function openTransactions(page: Page, filters: Record<string, string>): Promise<void> {
  await page.goto(`${BASE}/trust-accounting/transactions?${new URLSearchParams(filters)}`);
  await expect(page.getByRole("heading", { level: 1, name: "Transactions" })).toBeVisible({
    timeout: 30_000,
  });
}

/** The single row of `reference` on `date` (page reloaded: next@16.2.6 lost-ping), with `status`. */
async function rowId(page: Page, reference: string, date: string, status: string): Promise<string> {
  await openTransactions(page, { dateFrom: date, dateTo: date });
  const row = txRowByRef(page, reference);
  await expect(row, `one transaction "${reference}" on ${date}`).toHaveCount(1, {
    timeout: 15_000,
  });
  await expect(row).toContainText(status);
  return idFromTestId(row, "transaction-row-");
}

type ClientPosting = "Deposit" | "Payment" | "Refund";

/** Record Deposit / Payment / Refund for a client and matter, back-dated; returns the row id. */
async function recordClientPosting(
  page: Page,
  kind: ClientPosting,
  o: { client: string; matter: string; amount: string; reference: string; date: string }
): Promise<string> {
  await openTransactions(page, { dateFrom: o.date, dateTo: o.date });
  await page.getByTestId("record-transaction-button").click();
  await page.getByRole("menuitem", { name: `Record ${kind}`, exact: true }).click();
  const dialog = page.getByRole("dialog", { name: `Record ${kind}` });
  await expect(dialog).toBeVisible();
  const k = kind.toLowerCase();
  await dialog.getByTestId(`trust-${k}-customer-trigger`).click();
  await page.getByRole("option", { name: new RegExp(`^${escapeRegExp(o.client)}(\\s|$)`) }).click();
  const matterTrigger = dialog.getByTestId(`trust-${k}-matter-trigger`);
  await expect(matterTrigger).toBeEnabled({ timeout: 15_000 });
  await matterTrigger.click();
  await page.getByRole("option", { name: new RegExp(escapeRegExp(o.matter)) }).click();
  await dialog.getByLabel("Amount", { exact: true }).fill(o.amount);
  await dialog.getByLabel("Reference", { exact: true }).fill(o.reference);
  await dialog.getByLabel("Transaction Date").fill(o.date);
  await dialog.getByRole("button", { name: `Record ${kind}`, exact: true }).click();
  await expect(dialog).toBeHidden({ timeout: 20_000 });
  // A deposit never waits for approval; a payment and a refund do (TrustTransactionType)
  return rowId(page, o.reference, o.date, kind === "Deposit" ? "RECORDED" : "AWAITING APPROVAL");
}

/** Alice approves her own entry: refused with the detail sentence (the UI shows `detail`). */
async function expectSelfApprovalRefused(page: Page, txId: string, date: string): Promise<void> {
  await openTransactions(page, { dateFrom: date, dateTo: date });
  const actions = page.getByTestId(`approval-actions-${txId}`);
  await actions.getByRole("button", { name: "Approve", exact: true }).click();
  await expect(actions).toContainText(
    "The transaction recorder cannot be the sole approver. A different member with APPROVE_TRUST_PAYMENT capability must approve this transaction.",
    { timeout: 15_000 }
  );
  await openTransactions(page, { dateFrom: date, dateTo: date });
  await expect(page.getByTestId(`transaction-row-${txId}`)).toContainText("AWAITING APPROVAL");
}

/** Bob approves `txId` (on the transactions page, filtered to its date); asserts APPROVED. */
async function bobApproves(txId: string, date: string): Promise<void> {
  await openTransactions(bob, { dateFrom: date, dateTo: date });
  const row = bob.getByTestId(`transaction-row-${txId}`);
  await expect(row).toContainText("AWAITING APPROVAL");
  await bob
    .getByTestId(`approval-actions-${txId}`)
    .getByRole("button", { name: "Approve", exact: true })
    .click();
  await expect(bob.getByText("Transaction approved", { exact: true })).toBeVisible({
    timeout: 20_000,
  });
  await openTransactions(bob, { dateFrom: date, dateTo: date });
  await expect(row).toContainText("APPROVED");
  await expect(row).not.toContainText("AWAITING");
}

/** Reverses `txId` (dated `originalDate`) with a back-dated reversal; returns the contra's id. */
async function reverse(
  page: Page,
  o: {
    txId: string;
    originalDate: string;
    reason: string;
    reversalDate: string;
    contraRef: string;
    contraStatus: string;
  }
): Promise<string> {
  await openTransactions(page, { dateFrom: o.originalDate, dateTo: o.originalDate });
  await page.getByTestId(`reversal-action-${o.txId}`).getByTestId("reverse-button").click();
  const dialog = page.getByRole("dialog", { name: "Reverse Transaction" });
  await expect(dialog).toBeVisible();
  await dialog.getByTestId("reversal-reason-input").fill(o.reason);
  await dialog.getByTestId("reversal-date-input").fill(o.reversalDate);
  await dialog.getByTestId("confirm-reverse-button").click();
  await expect(dialog).toBeHidden({ timeout: 20_000 });
  return rowId(page, o.contraRef, o.reversalDate, o.contraStatus);
}

/** Creates a section 86(4) investment account from Settings → Trust accounting (primary unticked). */
async function createInvestmentAccount(page: Page, acct: { name: string; number: string }) {
  await page.goto(`${BASE}/settings/fees?tab=trust-accounting`);
  const section = page.getByTestId("trust-accounts-section");
  await expect(section).toBeVisible({ timeout: 30_000 });
  await page.getByTestId("add-trust-account-button").click();
  const dialog = page.getByTestId("create-trust-account-dialog");
  await expect(dialog).toBeVisible();
  await dialog.getByLabel("Account Name", { exact: true }).fill(acct.name);
  await dialog.getByLabel("Bank Name", { exact: true }).fill(BANK);
  await dialog.getByLabel("Branch Code", { exact: true }).fill(BRANCH);
  await dialog.getByLabel("Account Number", { exact: true }).fill(acct.number);
  // Type first: the basis select exists only for an investment account
  await dialog.getByTestId("trust-account-type-select").click();
  await page.getByRole("option", { name: "Investment", exact: true }).click();
  await dialog.getByTestId("trust-account-basis-select").click();
  await page
    .getByRole("option")
    .filter({ hasText: "Section 86(4) — invested on a client's instruction" })
    .click();
  await dialog.getByLabel("Opened Date", { exact: true }).fill(M.d(1));
  // A non-preset create defaults to primary (CreateTrustAccountDialog `isPrimary: preset === undefined`)
  const primary = dialog.getByRole("checkbox", { name: "Set as primary trust account" });
  await expect(primary).toBeChecked();
  await primary.click();
  await expect(primary).not.toBeChecked();
  await dialog.getByRole("button", { name: "Create Account" }).click();
  await expect(dialog).toBeHidden({ timeout: 20_000 });
  await page.reload();
  await expect(section).toContainText(acct.name, { timeout: 15_000 });
}

/**
 * Places a section 86(4) investment from G on the investments page; returns the investment id,
 * the placement row's id and the reference Kazi generated (read from the transactions page).
 */
async function placeInvestment(
  page: Page,
  o: {
    account: { name: string; number: string };
    client: string;
    clientId: string;
    matterId: string;
    principal: string;
    date: string;
  }
): Promise<{ investmentId: string; txId: string; reference: string }> {
  await page.goto(`${BASE}/trust-accounting/investments`);
  await expect(page.getByTestId("investments-page")).toBeVisible({ timeout: 30_000 });
  await page.getByRole("button", { name: "Place Investment" }).first().click();
  const dialog = page.getByTestId("place-investment-dialog");
  await expect(dialog).toBeVisible();
  // Basis FIRST: changing it clears the account, client and matter (PlaceInvestmentDialog)
  await dialog
    .getByRole("radio", { name: "Section 86(4) — invested on a client's instruction" })
    .check();
  await dialog.getByTestId("investment-account-select").click();
  await page
    .getByRole("option", { name: new RegExp(`^${escapeRegExp(o.account.name)} \\(`) })
    .click();
  await dialog.getByLabel("Client ID", { exact: true }).fill(o.clientId);
  await dialog.getByLabel("Matter ID", { exact: true }).fill(o.matterId);
  await dialog.getByLabel("Institution", { exact: true }).fill(BANK);
  await dialog.getByLabel("Account Number", { exact: true }).fill(o.account.number);
  await dialog.getByLabel("Principal", { exact: true }).fill(o.principal);
  await dialog.getByLabel("Interest Rate %", { exact: true }).fill("7.5");
  await dialog.getByLabel("Deposit Date", { exact: true }).fill(o.date);
  await dialog.getByRole("button", { name: "Place Investment", exact: true }).click();
  await expect(dialog).toBeHidden({ timeout: 30_000 });

  await page.reload();
  const investmentRow = page
    .locator('[data-testid^="investment-row-"]')
    .filter({ hasText: o.client })
    .filter({ hasText: o.account.name });
  await expect(investmentRow).toHaveCount(1, { timeout: 15_000 });
  await expect(investmentRow).toContainText("Awaiting approval");
  const investmentId = await idFromTestId(investmentRow, "investment-row-");

  // The placement row on G: AWAITING APPROVAL, reference INV- + the investment's first 8 hex
  await openTransactions(page, { dateFrom: o.date, dateTo: o.date, type: "INV_PLACE" });
  const txRow = page
    .locator('[data-testid^="transaction-row-"]')
    .filter({ hasText: o.account.name });
  await expect(txRow).toHaveCount(1, { timeout: 15_000 });
  await expect(txRow).toContainText("AWAITING APPROVAL");
  const reference = ((await txRow.locator("td").nth(1).textContent()) ?? "").trim();
  expect(reference, "the generated placement reference").toBe(
    `INV-${investmentId.slice(0, 8).toUpperCase()}`
  );
  return { investmentId, txId: await idFromTestId(txRow, "transaction-row-"), reference };
}

/** The client ledger page's general / invested / total figures (invested + total only when ≠ 0). */
async function expectClientLedger(
  page: Page,
  clientId: string,
  f: { general: string; invested: string | null; total: string | null }
): Promise<void> {
  await page.goto(`${BASE}/trust-accounting/client-ledgers/${clientId}`);
  await expect(page.getByTestId("client-detail-page")).toBeVisible({ timeout: 30_000 });
  await expect(page.getByTestId("ledger-total-balance").locator("dd")).toHaveText(
    randExact(f.general)
  );
  if (f.invested === null) {
    await expect(page.getByTestId("ledger-total-invested")).toHaveCount(0);
    await expect(page.getByTestId("ledger-total-held")).toHaveCount(0);
  } else {
    await expect(page.getByTestId("ledger-total-invested").locator("dd")).toHaveText(
      randExact(f.invested)
    );
    await expect(page.getByTestId("ledger-total-held").locator("dd")).toHaveText(
      randExact(f.total!)
    );
  }
}

/** Approves the open draft fee note and sends it (copied from day-30-billing.spec.ts). */
async function approveAndSend(page: Page): Promise<void> {
  const main = page.locator("main");
  await expect(page.getByRole("heading", { level: 1, name: "Draft fee note" })).toBeVisible();
  await main.getByRole("button", { name: "Approve", exact: true }).click();
  await expect(main.getByText("Approved", { exact: true })).toBeVisible({ timeout: 15_000 });
  await expect(page.getByRole("heading", { level: 1, name: "Draft fee note" })).toHaveCount(0);
  await main.getByRole("button", { name: "Send fee note" }).click();
  const override = page.getByTestId("send-override-dialog");
  const sent = main.getByText("Sent", { exact: true });
  // Deterministic wait for either state before branching (AUTOMATION-NOTES › Assertion rules)
  await expect(sent.or(override).first()).toBeVisible({ timeout: 15_000 });
  if (await override.isVisible()) {
    await override.getByRole("button", { name: "Send Anyway" }).click();
  }
  await expect(sent).toBeVisible({ timeout: 15_000 });
}

// ── Reconciliation wizard ────────────────────────────────────────────────────────────────────

/**
 * Wizard steps 1–3 for `account` with period end = the month's last day: upload the statement,
 * run auto-match, open the review (step 4).
 */
async function reconcile(
  page: Page,
  o: {
    account: string;
    csv: { name: string; text: string };
    imported: number;
    period: [string, string];
    autoMatched: number;
    remaining: number;
  }
): Promise<void> {
  await page.goto(`${BASE}/trust-accounting/reconciliation/new`);
  await expect(page.getByTestId("step-select")).toBeVisible({ timeout: 30_000 });
  await selectNativeOption(page.getByTestId("account-select"), o.account);
  await page.getByTestId("period-end-input").fill(M.d(31));
  await page.getByTestId("step-next-btn").click();

  const upload = page.getByTestId("bank-statement-upload");
  await expect(upload).toBeVisible({ timeout: 15_000 });
  await upload.getByTestId("file-input").setInputFiles(csvFile(o.csv.name, o.csv.text));
  const result = page.getByTestId("upload-result");
  await expect(result).toContainText("Statement imported successfully", { timeout: 30_000 });
  await expect(result).toContainText(`Lines imported: ${o.imported}`);
  await expect(result).toContainText(`File: ${o.csv.name}`);
  await expect(result).toContainText(`Period: ${o.period[0]} to ${o.period[1]}`);
  await page.getByTestId("step-next-btn").click();

  const step3 = page.getByTestId("step-auto-match");
  await expect(step3).toContainText(`${o.imported} bank statement lines ready to match`);
  await step3.getByTestId("auto-match-btn").click();
  const matched = page.getByTestId("auto-match-result");
  await expect(matched).toContainText(`Auto-matched ${o.autoMatched} lines`, { timeout: 30_000 });
  await expect(matched).toContainText(`${o.remaining} lines remaining for manual review`);
  await openReview(page);
  // No balance-marker row became a line (652G)
  await expect(bankLine(page, "Opening Balance")).toHaveCount(0);
  await expect(page.getByTestId("match-progress")).toContainText(
    `${o.autoMatched} / ${o.imported} lines resolved`
  );
}

/** Step 3 → "Review Matches" (get-or-create + calculate) → the review step. */
async function openReview(page: Page): Promise<void> {
  await page.getByTestId("step-auto-match").getByTestId("step-next-btn").click();
  await expect(page.getByTestId("reconciliation-split-pane")).toBeVisible({ timeout: 30_000 });
  await expect(page.getByTestId("balance-summary")).toBeVisible({ timeout: 30_000 });
  await expect(page.getByTestId("interest-investments-loading")).toHaveCount(0, {
    timeout: 30_000,
  });
}

/** Back to step 3 and "Review Matches" again: re-reads the unmatched rows and recalculates. */
async function recalculate(page: Page): Promise<void> {
  await page.getByRole("button", { name: "Back", exact: true }).click();
  await expect(page.getByTestId("step-auto-match")).toBeVisible();
  await openReview(page);
}

type Classification =
  | "FUND_INTEREST"
  | "CHARGE_REFUND"
  | "UNIDENTIFIED_DEPOSIT"
  | "INVESTMENT_INTEREST"
  | "FUND_SWEEP"
  | "BANK_CHARGE";

/** The registry label a recorded line shows ("Recorded: …"). */
const CLASSIFICATION_LABEL: Record<Classification, string> = {
  FUND_INTEREST: "Interest received for the Legal Practitioners' Fidelity Fund",
  CHARGE_REFUND: "Bank charge refunded from the business account",
  UNIDENTIFIED_DEPOSIT: "Unidentified deposit",
  INVESTMENT_INTEREST: "Section 86(4) investment interest",
  FUND_SWEEP: "Interest paid to the Legal Practitioners' Fidelity Fund",
  BANK_CHARGE: "Bank charge",
};

/**
 * Record from statement on the UNMATCHED line `description`: asserts the parser's suggestion
 * (the pre-selected classification, or none), chooses `classification`, fills its fields and
 * records. Returns the line's number as the wizard shows it ("#n").
 */
async function recordLine(
  page: Page,
  description: string,
  o: {
    suggested: Classification | null;
    classification: Classification;
    fill?: (dialog: Locator) => Promise<void>;
  }
): Promise<string> {
  const line = bankLine(page, description);
  await expect(line).toHaveCount(1);
  await expect(line).toContainText("Unmatched");
  const lineId = await idFromTestId(line, "bank-line-");
  const lineNumber = ((await line.locator("span").first().textContent()) ?? "").replace("#", "");
  await line.click();
  await page.getByTestId(`record-btn-${lineId}`).click();
  const dialog = page.getByTestId("record-from-statement-dialog");
  await expect(dialog).toBeVisible();
  if (o.suggested) {
    await expect(dialog.locator(`#classification-${o.suggested}`)).toBeChecked();
    await expect(dialog.getByTestId("classification-suggested-note")).toBeVisible();
  } else {
    await expect(dialog.getByRole("radio", { checked: true })).toHaveCount(0);
    await expect(dialog.getByTestId("classification-suggested-note")).toHaveCount(0);
  }
  await dialog.locator(`label[for="classification-${o.classification}"]`).click();
  await expect(dialog.locator(`#classification-${o.classification}`)).toBeChecked();
  if (o.fill) await o.fill(dialog);
  await dialog.getByTestId("record-submit").click();
  await expect(dialog).toBeHidden({ timeout: 30_000 });
  await expect(page.getByTestId(`recorded-badge-${lineId}`)).toHaveText(
    `Recorded: ${CLASSIFICATION_LABEL[o.classification]}`,
    { timeout: 30_000 }
  );
  return lineNumber;
}

/** Wizard step 4's summary, every figure (rows that only show when non-zero asserted absent). */
async function expectRecon(page: Page, f: ReconFigures): Promise<void> {
  const bank = page.getByTestId("comparison-bank-cashbook");
  await expect(ddFor(bank, "Bank balance")).toHaveText(randExact(f.bank), { timeout: 30_000 });
  await expect(ddFor(bank, "+ Outstanding deposits")).toHaveText(randExact(f.outstandingDeposits));
  await expect(ddFor(bank, "− Outstanding payments")).toHaveText(randExact(f.outstandingPayments));
  await expect(ddFor(bank, "= Adjusted bank balance")).toHaveText(randExact(f.adjustedBank));
  await expect(ddFor(bank, "Cashbook")).toHaveText(randExact(f.cashbook));
  await expect(page.getByTestId("bank-to-cashbook-difference")).toHaveText(
    randExact(f.bankToCashbook)
  );
  const books = page.getByTestId("comparison-cashbook-creditors");
  await expect(ddFor(books, "Cashbook")).toHaveText(randExact(f.cashbook));
  await expect(ddFor(books, "Trust creditors")).toHaveText(randExact(f.trustCreditors));
  await expect(page.getByTestId("cashbook-to-creditors-difference")).toHaveText(
    randExact(f.cashbookToCreditors)
  );
  const creditors = page.getByTestId("creditor-breakdown");
  await expect(ddFor(creditors, "Client ledgers")).toHaveText(randExact(f.clientLedgers));
  if (f.investmentCreditors === null) {
    await expect(ddFor(creditors, "Investment creditors (section 86(4))")).toHaveCount(0);
  } else {
    await expect(ddFor(creditors, "Investment creditors (section 86(4))")).toHaveText(
      randExact(f.investmentCreditors)
    );
  }
  await expect(
    ddFor(creditors, "Legal Practitioners' Fidelity Fund — interest and charges")
  ).toHaveText(randExact(f.fund));
  await expect(ddFor(creditors, "Unidentified deposits")).toHaveText(randExact(f.unidentified));
  // Section 86(3) inter-account and other non-client creditors: nil, so not shown
  await expect(ddFor(creditors, "Placed in section 86(3) investment accounts")).toHaveCount(0);
  await expect(ddFor(creditors, "Held for the firm's trust creditors")).toHaveCount(0);
  await expect(ddFor(creditors, "Other non-client trust creditors")).toHaveCount(0);
  await expect(page.getByTestId("unresolved-line-count")).toHaveText(
    f.unresolvedLines === 1
      ? "1 statement line is neither matched, recorded nor excluded"
      : `${f.unresolvedLines} statement lines are neither matched, recorded nor excluded`
  );
}

/** Complete (enabled only when balanced, nothing unresolved, no blocking flag); returns the list id. */
async function completeAndLock(page: Page, creditorTotal: string): Promise<string> {
  await expect(page.getByTestId("balance-summary")).toContainText("Reconciliation is balanced");
  const complete = page.getByTestId("complete-reconciliation-btn");
  await expect(complete).toBeEnabled();
  await complete.click();
  const done = page.getByTestId("step-complete");
  await expect(done).toBeVisible({ timeout: 30_000 });
  // The month's list locks for the reconciliation's period end (the 31st), whatever the
  // statement's last line
  const locked = done.getByTestId("monthly-list-locked");
  await expect(locked).toContainText(`${M.monthName} list locked —`);
  await expect(locked).toContainText(randIn(creditorTotal));
  await expect(locked).toContainText("agrees with the cashbook and the adjusted bank balance.");
  const href = await done.getByTestId("view-locked-list").getAttribute("href");
  const listId = href?.match(/\/reconciliation\/lists\/([0-9a-f-]{36})$/)?.[1];
  if (!listId) throw new Error(`no locked-list link on step 5 (href "${href}")`);
  return listId;
}

/** The figure text of the wizard's "Bank balance" (kept for the firm total of step 15). */
async function bankBalanceText(page: Page): Promise<string> {
  return (
    (await ddFor(page.getByTestId("comparison-bank-cashbook"), "Bank balance").textContent()) ?? ""
  );
}

// ── The month ────────────────────────────────────────────────────────────────────────────────

test.describe.serial(`Phase 92 exit — one balanced month (§16.9.6), ${M.monthName}`, () => {
  test.beforeAll(async ({ browser }, testInfo) => {
    aliceCtx = await firmSession(browser, testInfo, "alice");
    bobCtx = await firmSession(browser, testInfo, "bob");
    alice = await aliceCtx.newPage();
    wizard = await aliceCtx.newPage();
    bob = await bobCtx.newPage();
  });

  test.afterEach(async ({}, testInfo) => {
    // The shared pages are not the `page` fixture: capture them on failure for the report
    if (testInfo.status === testInfo.expectedStatus) return;
    for (const [name, p] of [
      ["alice", alice],
      ["wizard", wizard],
      ["bob", bob],
    ] as const) {
      if (!p || p.isClosed()) continue;
      await testInfo.attach(`${name}-on-failure`, {
        body: await p.screenshot({ fullPage: true }),
        contentType: "image/png",
      });
    }
  });

  test.afterAll(async () => {
    await aliceCtx?.close();
    await bobCtx?.close();
  });

  // ── 652.17 Step 1 — fresh stack and set-up ─────────────────────────────────────────────────
  test("1 · fresh stack; set-up as Alice: I-A, I-B, two clients with matters and portal contacts, a sent fee note", async () => {
    test.setTimeout(300_000);

    // D-1 / D-11 precondition: G is the seeded primary account, untouched (run once per fresh stack)
    const accounts =
      await apiOk<{ id: string; accountName: string; accountType: string; isPrimary: boolean }[]>(
        "/api/trust-accounts"
      );
    const g = accounts.find((a) => a.accountName === G);
    expect(g, `the seeded trust account "${G}" — run on a fresh stack`).toBeDefined();
    expect(g!.accountType).toBe("GENERAL");
    expect(g!.isPrimary).toBe(true);
    state.gId = g!.id;
    const freshStack =
      "trust-balanced-month runs once per fresh stack: bash compose/scripts/e2e-down.sh && bash compose/scripts/e2e-up.sh";
    const gTx = await apiOk<{ page: { totalElements: number } }>(
      `/api/trust-accounts/${state.gId}/transactions?page=0&size=1`
    );
    expect(gTx.page.totalElements, `${G} already has trust transactions — ${freshStack}`).toBe(0);
    const gRecons = await apiOk<unknown[]>(`/api/trust-accounts/${state.gId}/reconciliations`);
    expect(gRecons, `${G} already has a reconciliation — ${freshStack}`).toHaveLength(0);
    for (const name of [IA.name, IB.name]) {
      expect(
        accounts.find((a) => a.accountName === name),
        `${name} exists — ${freshStack}`
      ).toBe(undefined);
    }
    // GET /api/clients answers a bare array (CustomerController.listCustomers)
    const clients = await apiOk<{ name: string }[]>("/api/clients?size=200");
    for (const name of [NDLOVU.client, KHUMALO.client]) {
      expect(
        clients.map((c) => c.name),
        `${name} exists — ${freshStack}`
      ).not.toContain(name);
    }

    // I-A and I-B: Investment, basis client instruction (section 86(4)), not primary
    await createInvestmentAccount(alice, IA);
    await createInvestmentAccount(alice, IB);
    const after = await apiOk<
      {
        id: string;
        accountName: string;
        accountType: string;
        investmentBasis: string | null;
        section: string;
        isPrimary: boolean;
      }[]
    >("/api/trust-accounts");
    for (const [key, acct] of [
      ["iAId", IA],
      ["iBId", IB],
    ] as const) {
      const hit = after.find((a) => a.accountName === acct.name);
      expect(hit, acct.name).toBeDefined();
      expect(hit!.accountType).toBe("INVESTMENT");
      expect(hit!.investmentBasis).toBe("CLIENT_INSTRUCTION");
      expect(hit!.section).toBe("S86_4");
      expect(hit!.isPrimary).toBe(false);
      state[key] = hit!.id;
    }
    // G is still the primary banking account every posting screen binds to
    expect(after.find((a) => a.accountName === G)!.isPrimary).toBe(true);

    // Clients (ACTIVE: the posting pickers list ACTIVE clients) and matters — API set-up; each
    // client's email becomes its portal contact (PortalContactAutoProvisioner)
    state.ndlovuId = await createActiveClient(NDLOVU.client, NDLOVU.email);
    state.khumaloId = await createActiveClient(KHUMALO.client, KHUMALO.email);
    for (const [id, email] of [
      [state.ndlovuId, NDLOVU.email],
      [state.khumaloId, KHUMALO.email],
    ]) {
      await expect
        .poll(async () =>
          (await apiOk<{ email: string }[]>(`/api/clients/${id}/portal-contacts`)).map(
            (c) => c.email
          )
        )
        .toContain(email);
    }
    state.m1 = await createMatter(NDLOVU.matter, state.ndlovuId);
    state.m2 = await createMatter(KHUMALO.matter, state.khumaloId);

    // D-7: a fee note on M1 from 4 hours of billable time at R 2 000,00 an hour (+ 15 % VAT):
    // the fee transfer infers its matter from a time line; approving the transfer needs it SENT
    await createTask(state.m1, M1_TASK);
    await createMatterRate(state.m1, (await memberNamed("Alice")).id, 2000);
    await openMatter(alice, NDLOVU.matter);
    await logTimeOnTask(alice, {
      task: M1_TASK,
      hours: 4,
      minutes: 0,
      description: "Drafted the transfer documents and the deed of sale annexures",
      date: isoDate(),
      rate: "2 000,00",
    });
    await openClientFeeNotes(alice, NDLOVU.client);
    state.feeNoteId = await generateFeeNoteDraft(alice, { matter: NDLOVU.matter, items: 1 });
    await expect(feeNoteTotalsRow(alice, "Total")).toContainText(randPattern(FIG.feeNoteTotal));
    await approveAndSend(alice);
  });

  // ── 652.18 Step 2 — postings #1–12 in date order; Bob approves ────────────────────────────
  test("2 · postings #1–11 in date order (Alice records; her own approval refused; Bob approves)", async () => {
    test.setTimeout(600_000);
    const ndlovu = { client: NDLOVU.client, matter: NDLOVU.matter };
    const khumalo = { client: KHUMALO.client, matter: KHUMALO.matter };

    // #1, #2 deposits
    await recordClientPosting(alice, "Deposit", {
      ...ndlovu,
      amount: "100000",
      reference: "DEP-A-001",
      date: M.d(1),
    });
    await recordClientPosting(alice, "Deposit", {
      ...khumalo,
      amount: "40000",
      reference: "DEP-B-001",
      date: M.d(2),
    });

    // #3 payment — Alice's own approval refused, Bob approves
    const pay1 = await recordClientPosting(alice, "Payment", {
      ...ndlovu,
      amount: "12500",
      reference: "PAY-A-001",
      date: M.d(5),
    });
    await expectSelfApprovalRefused(alice, pay1, M.d(5));
    await bobApproves(pay1, M.d(5));

    // #4 fee transfer for the fee note, dated the 6th; Bob approves
    await openTransactions(alice, { dateFrom: M.d(6), dateTo: M.d(6) });
    await alice.getByTestId("record-transaction-button").click();
    await alice.getByRole("menuitem", { name: "Record Fee Transfer", exact: true }).click();
    const fee = alice.getByRole("dialog", { name: "Record Fee Transfer" });
    await expect(fee).toBeVisible();
    await fee.getByLabel("Client ID", { exact: true }).fill(need("ndlovuId"));
    await fee.getByLabel("Fee note ID", { exact: true }).fill(need("feeNoteId"));
    await fee.getByLabel("Amount", { exact: true }).fill("8500");
    await fee.getByLabel("Reference", { exact: true }).fill("FT-A-001");
    await fee.getByTestId("fee-transfer-date-input").fill(M.d(6));
    await fee.getByRole("button", { name: "Record Fee Transfer", exact: true }).click();
    await expect(fee).toBeHidden({ timeout: 20_000 });
    const ft = await rowId(alice, "FT-A-001", M.d(6), "AWAITING APPROVAL");
    await bobApproves(ft, M.d(6));
    // D-7: R 9 200,00 less the R 8 500,00 transfer
    await openFeeNote(alice, need("feeNoteId"));
    await expect(alice.getByTestId("invoice-balance-due")).toContainText(
      randPattern(FIG.feeNoteBalanceAfterTransfer)
    );

    // #5 refund; Bob approves
    const ref = await recordClientPosting(alice, "Refund", {
      ...khumalo,
      amount: "5000",
      reference: "REF-B-001",
      date: M.d(7),
    });
    await bobApproves(ref, M.d(7));

    // #6 transfer Ndlovu / M1 → Khumalo / M2 (both legs RECORDED, one reference, no cash effect)
    await openTransactions(alice, { dateFrom: M.d(8), dateTo: M.d(8) });
    await alice.getByTestId("record-transaction-button").click();
    await alice.getByRole("menuitem", { name: "Record Transfer", exact: true }).click();
    const transfer = alice.getByRole("dialog", { name: "Record Transfer" });
    await expect(transfer).toBeVisible();
    await transfer.getByLabel("Source Client ID", { exact: true }).fill(need("ndlovuId"));
    await transfer.getByLabel("Target Client ID", { exact: true }).fill(need("khumaloId"));
    await transfer.getByLabel("Source Matter", { exact: true }).fill(need("m1"));
    await transfer.getByLabel("Target Matter", { exact: true }).fill(need("m2"));
    await transfer.getByLabel("Amount", { exact: true }).fill("10000");
    await transfer.getByLabel("Reference", { exact: true }).fill("TRF-A-B-001");
    await transfer.getByLabel("Transaction Date").fill(M.d(8));
    await transfer.getByRole("button", { name: "Record Transfer", exact: true }).click();
    await expect(transfer).toBeHidden({ timeout: 20_000 });
    await openTransactions(alice, { dateFrom: M.d(8), dateTo: M.d(8) });
    const legs = txRowByRef(alice, "TRF-A-B-001");
    await expect(legs).toHaveCount(2, { timeout: 15_000 });
    await expect(legs.nth(0)).toContainText("RECORDED");
    await expect(legs.nth(1)).toContainText("RECORDED");

    // #7 deposit; #8 reversal of a credit, dated the 12th — the contra waits for Bob
    const dep2 = await recordClientPosting(alice, "Deposit", {
      ...khumalo,
      amount: "3000",
      reference: "DEP-B-002",
      date: M.d(9),
    });
    const revDep = await reverse(alice, {
      txId: dep2,
      originalDate: M.d(9),
      reason: "Deposit returned unpaid by the bank",
      reversalDate: M.d(12),
      contraRef: "REV-DEP-B-002",
      contraStatus: "AWAITING APPROVAL",
    });
    await bobApproves(revDep, M.d(12));
    await openTransactions(alice, { dateFrom: M.d(9), dateTo: M.d(9) });
    await expect(alice.getByTestId(`transaction-row-${dep2}`)).toContainText("REVERSED");

    // #9 payment, Bob approves; #10 reversal of a debit, dated the 15th — posts at once
    const pay2 = await recordClientPosting(alice, "Payment", {
      ...ndlovu,
      amount: "2000",
      reference: "PAY-A-002",
      date: M.d(13),
    });
    await bobApproves(pay2, M.d(13));
    await reverse(alice, {
      txId: pay2,
      originalDate: M.d(13),
      reason: "Payment to counsel returned by the bank",
      reversalDate: M.d(15),
      contraRef: "REV-PAY-A-002",
      contraStatus: "RECORDED",
    });
    await openTransactions(alice, { dateFrom: M.d(13), dateTo: M.d(13) });
    await expect(alice.getByTestId(`transaction-row-${pay2}`)).toContainText("REVERSED");

    // Before the placement: Ndlovu holds R 69 000,00, nothing invested (step 3's "before")
    await expectClientLedger(alice, need("ndlovuId"), {
      general: FIG.ndlovuBeforePlacement,
      invested: null,
      total: null,
    });

    // #11, #12 section 86(4) placements from G; Bob approves each. Kazi generates the references.
    const placeA = await placeInvestment(alice, {
      account: IA,
      client: NDLOVU.client,
      clientId: need("ndlovuId"),
      matterId: need("m1"),
      principal: "50000",
      date: M.d(15),
    });
    await bobApproves(placeA.txId, M.d(15));
    state.invAId = placeA.investmentId;
    state.invA = placeA.reference;
    state.invWdA = `INV-WD-${placeA.investmentId.slice(0, 8).toUpperCase()}`;

    // ── 652.19 Step 3 is asserted right after #11's approval (next test) — #12 follows it ──
  });

  // ── 652.19 Step 3 — the client's total is unchanged by placement ───────────────────────────
  test("3 · after #11: Ndlovu general R 19 000,00 · invested R 50 000,00 · total R 69 000,00 (unchanged); then #12", async () => {
    test.setTimeout(180_000);
    await expectClientLedger(alice, need("ndlovuId"), FIG.ndlovuAfterPlacement);

    const placeB = await placeInvestment(alice, {
      account: IB,
      client: KHUMALO.client,
      clientId: need("khumaloId"),
      matterId: need("m2"),
      principal: "20000",
      date: M.d(16),
    });
    await bobApproves(placeB.txId, M.d(16));
    state.invBId = placeB.investmentId;
    state.invB = placeB.reference;
  });

  // ── 652.20 Steps 4–6 — reconcile I-A ──────────────────────────────────────────────────────
  test("4–6 · reconcile I-A to the 31st: interest gross (#13), Fund sweep, withdraw on the 30th (#14), match, complete; October list locks", async () => {
    test.setTimeout(420_000);
    // Step 4: period end the 31st; four lines, the last on the 30th (allowed)
    await reconcile(wizard, {
      account: IA.name,
      csv: {
        name: CSV_NAMES.iA,
        text: investmentNdlovuCsv(M, { invA: need("invA"), invWdA: need("invWdA") }),
      },
      imported: 4,
      period: [M.d(15), M.d(30)],
      autoMatched: 1,
      remaining: 3,
    });
    await expect(bankLine(wizard, "TRANSFER FROM TRUST")).toContainText("Auto-matched");

    // #13: Section 86(4) investment interest, gross 400,00, gross presentation → client 380, Fund 20
    await recordLine(wizard, "INTEREST CAPITALISED", {
      suggested: "INVESTMENT_INTEREST",
      classification: "INVESTMENT_INTEREST",
      fill: async (dialog) => {
        await selectNativeOption(dialog.getByTestId("interest-investment-select"), NDLOVU.client);
        await dialog.locator('label[for="presentation-GROSS"]').click();
        await expect(dialog.getByTestId("interest-gross-input")).toHaveValue("400");
        await expect(dialog.getByTestId("interest-fund-share")).toHaveText(
          randExact(FIG.iAInterest.fund)
        );
        await expect(dialog.getByTestId("interest-client-share")).toHaveText(
          randExact(FIG.iAInterest.client)
        );
      },
    });
    // The −20,00 line: the Fund sweep
    state.sweepLineIA = await recordLine(wizard, "LPFF 5PCT S86(4)", {
      suggested: "FUND_SWEEP",
      classification: "FUND_SWEEP",
    });

    // Step 5: the investment — balance 50 380,00; gross 400,00, Fund 5 % 20,00, net 380,00
    await alice.goto(`${BASE}/trust-accounting/investments`);
    await expect(alice.getByTestId("investments-page")).toBeVisible({ timeout: 30_000 });
    const investment = alice.getByTestId(`investment-row-${need("invAId")}`);
    await expect(investment).toBeVisible();
    await expect(investment.locator("td").nth(5)).toHaveText(randExact(FIG.iAInvestment.balance));
    await expect(investment).toContainText(
      new RegExp(`Gross interest ${randIn(FIG.iAInvestment.gross).source}`)
    );
    await expect(investment).toContainText(
      new RegExp(`Fund 5% ${randIn(FIG.iAInvestment.fund).source}`)
    );
    await expect(investment).toContainText(
      new RegExp(`Net interest ${randIn(FIG.iAInvestment.net).source}`)
    );

    // Step 6 (#14): withdraw, dated the 30th — the dialog shows R 50 380,00 and takes no amount
    await investment.getByRole("button", { name: "Withdraw", exact: true }).click();
    const withdraw = alice.getByTestId("withdraw-investment-dialog");
    await expect(withdraw).toBeVisible();
    await expect(withdraw.getByTestId("withdrawal-amount")).toContainText(randIn(FIG.iAWithdrawal));
    await expect(withdraw.getByRole("spinbutton")).toHaveCount(0);
    await withdraw.getByLabel("Withdrawal date", { exact: true }).fill(M.d(30));
    await withdraw.getByRole("button", { name: "Confirm Withdrawal" }).click();
    await expect(withdraw).toBeHidden({ timeout: 30_000 });
    // The return to G carries INV-WD- + the same hex — the reference written into both CSVs
    await openTransactions(alice, { dateFrom: M.d(30), dateTo: M.d(30), type: "INV_RETURN" });
    await expect(txRowByRef(alice, need("invWdA"))).toHaveCount(1, { timeout: 15_000 });
    await expect(txRowByRef(alice, need("invWdA"))).toContainText(randIn(FIG.iAWithdrawal));

    // D-8: the return transfer is matched in the wizard — Back → Review Matches re-reads the
    // unmatched rows; the line's candidate is the INV-WD row
    await recalculate(wizard);
    const returnLine = bankLine(wizard, "TRANSFER TO TRUST");
    await expect(returnLine).toContainText("Unmatched");
    await returnLine.click();
    const candidate = wizard
      .getByTestId("reconciliation-split-pane")
      .locator('[data-testid^="transaction-"]')
      .filter({ has: wizard.locator("p", { hasText: exactText(need("invWdA")) }) });
    await expect(candidate).toHaveCount(1, { timeout: 15_000 });
    const withdrawalTxId = await idFromTestId(candidate, "transaction-");
    await wizard.getByTestId(`match-btn-${withdrawalTxId}`).click();
    await expect(returnLine).toContainText("Matched", { timeout: 30_000 });
    await expect(wizard.getByTestId("match-progress")).toContainText("4 / 4 lines resolved");

    // As at the 31st: the statement's closing 0,00 (last line the 30th) carried to the 31st
    await expectRecon(wizard, FIG.iA);
    state.bankIA = await bankBalanceText(wizard);
    state.iAListId = await completeAndLock(wizard, FIG.iA.trustCreditors);
  });

  // ── 652.21 Step 7 — reconcile I-B ─────────────────────────────────────────────────────────
  test("7 · reconcile I-B to the 31st: interest net (#15), complete; October list locks", async () => {
    test.setTimeout(300_000);
    await reconcile(wizard, {
      account: IB.name,
      csv: { name: CSV_NAMES.iB, text: investmentKhumaloCsv(M, { invB: need("invB") }) },
      imported: 2,
      period: [M.d(16), M.d(31)],
      autoMatched: 1,
      remaining: 1,
    });
    await expect(bankLine(wizard, "TRANSFER FROM TRUST")).toContainText("Auto-matched");

    // #15: no suggestion (a credit naming LPFF); Section 86(4) interest, gross 100,00, net
    await recordLine(wizard, "INTEREST NET OF LPFF 5PCT", {
      suggested: null,
      classification: "INVESTMENT_INTEREST",
      fill: async (dialog) => {
        await selectNativeOption(dialog.getByTestId("interest-investment-select"), KHUMALO.client);
        await dialog.locator('label[for="presentation-NET"]').click();
        await dialog.getByTestId("interest-gross-input").fill("100");
        await expect(dialog.getByTestId("interest-fund-share")).toHaveText(
          randExact(FIG.iBInterest.fund)
        );
        await expect(dialog.getByTestId("interest-client-share")).toHaveText(
          randExact(FIG.iBInterest.client)
        );
      },
    });
    await expect(wizard.getByTestId("match-progress")).toContainText("2 / 2 lines resolved");
    await expectRecon(wizard, FIG.iB);
    state.bankIB = await bankBalanceText(wizard);
    state.iBListId = await completeAndLock(wizard, FIG.iB.trustCreditors);
  });

  // ── 652.22 Step 8 — the outstanding payment ───────────────────────────────────────────────
  test("8 · payment #16 PAY-B-001 R 1 500,00 dated the 31st; Bob approves (not on the statement)", async () => {
    test.setTimeout(180_000);
    const pay = await recordClientPosting(alice, "Payment", {
      client: KHUMALO.client,
      matter: KHUMALO.matter,
      amount: "1500",
      reference: "PAY-B-001",
      date: M.d(31),
    });
    await bobApproves(pay, M.d(31));
  });

  // ── 652.23 Steps 9–11 — reconcile G; the gate refuses once; then it balances ───────────────
  test("9–11 · reconcile G: 19 lines, 12 matched / 7 unmatched; record six; one 400 (G1 + G4); record #18 → R 101 630,00", async () => {
    test.setTimeout(480_000);
    // Step 9: the Opening Balance row is not a line (652G); twelve match postings made beforehand
    await reconcile(wizard, {
      account: G,
      csv: {
        name: CSV_NAMES.g,
        text: monthEndTrustAccountCsv(M, {
          invA: need("invA"),
          invB: need("invB"),
          invWdA: need("invWdA"),
        }),
      },
      imported: 19,
      period: [M.d(1), M.d(31)],
      autoMatched: 12,
      remaining: 7,
    });
    for (const matched of [
      "DEPOSIT NDLOVU FAMILY TRUST",
      "EFT PAYMENT SHERIFF PRETORIA",
      "TRANSFER TO BUSINESS ACC",
      "EFT REFUND KHUMALO HOLDINGS",
      "UNPAID DEPOSIT RETURNED",
      "EFT PAYMENT COUNSEL",
      "EFT RETURNED COUNSEL",
      "TRANSFER TO INVESTMENT 62000000001",
      "TRANSFER TO INVESTMENT 62000000002",
      "TRANSFER FROM INVESTMENT 62000000001",
    ]) {
      await expect(bankLine(wizard, matched), matched).toContainText("Auto-matched");
    }
    const khumaloDeposits = bankLine(wizard, "DEPOSIT KHUMALO HOLDINGS"); // DEP-B-001, DEP-B-002
    await expect(khumaloDeposits).toHaveCount(2);
    await expect(khumaloDeposits.nth(0)).toContainText("Auto-matched");
    await expect(khumaloDeposits.nth(1)).toContainText("Auto-matched");
    await expect(wizard.getByTestId("balance-summary")).toContainText(
      "Reconciliation is not balanced"
    );
    await expect(wizard.getByTestId("complete-reconciliation-btn")).toBeDisabled();

    // Step 10: record six of the seven (statement order), leaving "ACB CREDIT UNKNOWN"; the
    // parser's suggestions: a bank charge for the two fee lines, Fund interest for CREDIT INTEREST,
    // a Fund sweep for LPFF INTEREST TRANSFER, nothing for the others
    await recordLine(wizard, "CASH DEPOSIT BRANCH 0045", {
      suggested: null,
      classification: "UNIDENTIFIED_DEPOSIT",
    }); // #17
    await recordLine(wizard, "MONTHLY SERVICE FEE", {
      suggested: "BANK_CHARGE",
      classification: "BANK_CHARGE",
      fill: async (dialog) => {
        await dialog.locator('label[for="charge-recoverable-yes"]').click();
        await dialog.getByTestId("charge-vat-input").fill("15");
      },
    }); // #19
    await recordLine(wizard, "CASH DEPOSIT FEE", {
      suggested: "BANK_CHARGE",
      classification: "BANK_CHARGE",
      fill: async (dialog) => {
        await dialog.locator('label[for="charge-recoverable-no"]').click();
        await dialog.getByTestId("charge-vat-input").fill("7.5");
      },
    }); // #20
    await recordLine(wizard, "CREDIT INTEREST", {
      suggested: "FUND_INTEREST",
      classification: "FUND_INTEREST",
    }); // #21
    state.sweepLineG = await recordLine(wizard, "LPFF INTEREST TRANSFER", {
      suggested: "FUND_SWEEP",
      classification: "FUND_SWEEP",
    }); // #22
    await recordLine(wizard, "TRANSFER FROM BUSINESS ACC", {
      suggested: null,
      classification: "CHARGE_REFUND",
    }); // #23

    // One unresolved line; the bank R 1 250,00 above the cashbook; Complete disabled
    const bankSide = wizard.getByTestId("comparison-bank-cashbook");
    await expect(ddFor(bankSide, "Bank balance")).toHaveText(randExact(FIG.gStep10.bank));
    await expect(ddFor(bankSide, "− Outstanding payments")).toHaveText(
      randExact(FIG.gStep10.outstandingPayments)
    );
    await expect(ddFor(bankSide, "= Adjusted bank balance")).toHaveText(
      randExact(FIG.gStep10.adjustedBank)
    );
    await expect(ddFor(bankSide, "Cashbook")).toHaveText(randExact(FIG.gStep10.cashbook));
    await expect(wizard.getByTestId("bank-to-cashbook-difference")).toHaveText(
      randExact(FIG.gStep10.bankToCashbook)
    );
    await expect(wizard.getByTestId("unresolved-line-count")).toHaveText(
      "1 statement line is neither matched, recorded nor excluded"
    );
    await expect(bankLine(wizard, "ACB CREDIT UNKNOWN")).toContainText("Unmatched");
    await expect(wizard.getByTestId("complete-reconciliation-btn")).toBeDisabled();
    await evidenceShot(wizard, "step-10-gate-refused");

    // D-9: the complete endpoint, called directly once, refuses with ONE 400 carrying both failed
    // conditions — G1 (title, first in order) and G4 — and nothing of G2 / G3
    const recons = await apiOk<
      { id: string; periodEnd: string; bankStatementId: string; status: string }[]
    >(`/api/trust-accounts/${need("gId")}/reconciliations`);
    const draft = recons.filter((r) => r.periodEnd === M.d(31));
    expect(draft, "one reconciliation of G for the month").toHaveLength(1);
    const refusal = await apiStatus<{ title: string; detail: string }>(
      `/api/trust-reconciliations/${draft[0].id}/complete`,
      { method: "POST", body: { expectedBankStatementId: draft[0].bankStatementId } }
    );
    writeEvidence("step-10-complete-refusal.json", JSON.stringify(refusal, null, 2) + "\n");
    expect(refusal.status).toBe(400);
    expect(refusal.data.title).toBe("Reconciliation does not balance");
    expect(refusal.data.detail.replace(/[  ]/g, " ")).toMatch(
      new RegExp(
        `^Adjusted bank balance ${randIn(FIG.gStep10.adjustedBank).source} differs from the cashbook ` +
          `${randIn(FIG.gStep10.cashbook).source} by ${randIn(FIG.gStep10.bankToCashbook).source} ` +
          `as at ${escapeRegExp(M.words(31))}\\. 1 statement line is neither matched, recorded nor excluded\\.$`
      )
    );
    // Nothing completed: the draft is still open
    const after = await apiOk<{ id: string; status: string }[]>(
      `/api/trust-accounts/${need("gId")}/reconciliations`
    );
    expect(after.find((r) => r.id === draft[0].id)?.status).not.toBe("COMPLETED");

    // Step 11 (#18): the seventh line as an unidentified deposit — both comparisons agree
    await recordLine(wizard, "ACB CREDIT UNKNOWN", {
      suggested: null,
      classification: "UNIDENTIFIED_DEPOSIT",
    });
    await expect(ddFor(bankSide, "= Adjusted bank balance")).toHaveText(randExact(FIG.gStep11));
    await expect(ddFor(bankSide, "Cashbook")).toHaveText(randExact(FIG.gStep11));
    const books = wizard.getByTestId("comparison-cashbook-creditors");
    await expect(ddFor(books, "Trust creditors")).toHaveText(randExact(FIG.gStep11));
    await expect(wizard.getByTestId("bank-to-cashbook-difference")).toHaveText(randExact("0,00"));
    await expect(wizard.getByTestId("cashbook-to-creditors-difference")).toHaveText(
      randExact("0,00")
    );
    await expect(wizard.getByTestId("unresolved-line-count")).toHaveText(
      "0 statement lines are neither matched, recorded nor excluded"
    );
    await expect(wizard.getByTestId("match-progress")).toContainText("19 / 19 lines resolved");
    // Completed in step 15, after the allocation (the wizard stays open in its own tab)
  });

  // ── 652.24 Steps 12–13 — other trust creditors and the allocation ─────────────────────────
  test("12–13 · other trust creditors: Fund R 0,00 with five rows, two held; allocate R 7 500,00 to Khumalo / M2 on the 27th (#24), Bob approves", async () => {
    test.setTimeout(300_000);
    // Step 12
    await alice.goto(`${BASE}/trust-accounting/other-creditors`);
    await expect(alice.getByTestId("other-creditors-page")).toBeVisible({ timeout: 30_000 });
    await expect(
      statCardValue(alice, "Legal Practitioners' Fidelity Fund — interest and charges")
    ).toHaveText(randExact(FIG.fundLedger));
    const fundRows = alice.getByTestId("fund-ledger-section").getByTestId("fund-ledger-row");
    await expect(fundRows).toHaveCount(FIG.fundLedgerRows);
    // The two charges keep their recoverability and VAT (#19 recoverable 15,00; #20 not, 7,50)
    const charges = alice.getByTestId("fund-ledger-charge");
    await expect(charges.filter({ hasText: /^Recoverable/ })).toHaveCount(1);
    await expect(charges.filter({ hasText: /^Recoverable/ })).toContainText(randIn("15,00"));
    await expect(charges.filter({ hasText: /^Not recoverable/ })).toHaveCount(1);
    await expect(charges.filter({ hasText: /^Not recoverable/ })).toContainText(randIn("7,50"));
    const items = alice.getByTestId("unidentified-deposit-row");
    await expect(items).toHaveCount(2);
    const cashItem = items.filter({ hasText: "CASH DEPOSIT BRANCH 0045" });
    const acbItem = items.filter({ hasText: "ACB CREDIT UNKNOWN" });
    await expect(cashItem).toContainText(randIn(FIG.unidentifiedHeld[0]));
    await expect(acbItem).toContainText(randIn(FIG.unidentifiedHeld[1]));
    await expect(cashItem.getByTestId("unidentified-deposit-status")).toHaveText("Held");
    await expect(acbItem.getByTestId("unidentified-deposit-status")).toHaveText("Held");

    // Step 13 (#24): allocate the R 7 500,00 item, allocation date the 27th
    await cashItem.locator('[data-testid^="allocate-btn-"]').click();
    const allocate = alice.getByTestId("allocate-unidentified-dialog");
    await expect(allocate).toBeVisible();
    await allocate.getByTestId("allocate-customer-trigger").click();
    await alice
      .getByRole("option", { name: new RegExp(`^${escapeRegExp(KHUMALO.client)}(\\s|$)`) })
      .click();
    const matterTrigger = allocate.getByTestId("allocate-matter-trigger");
    await expect(matterTrigger).toBeEnabled({ timeout: 15_000 });
    await matterTrigger.click();
    await alice.getByRole("option", { name: new RegExp(escapeRegExp(KHUMALO.matter)) }).click();
    await allocate.getByTestId("allocate-date").fill(M.d(27));
    await allocate.getByTestId("allocate-submit").click();
    await expect(allocate).toBeHidden({ timeout: 20_000 });
    await alice.reload();
    await expect(
      items
        .filter({ hasText: "CASH DEPOSIT BRANCH 0045" })
        .getByTestId("unidentified-deposit-pending")
    ).toHaveText(`Allocation to ${KHUMALO.client} awaiting approval`, { timeout: 15_000 });

    // Bob approves the allocation (the non-client leg on G)
    await openTransactions(bob, {
      dateFrom: M.d(27),
      dateTo: M.d(27),
      type: "UNIDENT_ALLOC_OUT",
    });
    const allocRow = bob.locator('[data-testid^="transaction-row-"]');
    await expect(allocRow).toHaveCount(1, { timeout: 15_000 });
    await bobApproves(await idFromTestId(allocRow, "transaction-row-"), M.d(27));

    // Khumalo's ledger: "Deposit identified and allocated" R 7 500,00 dated the 27th
    await alice.goto(`${BASE}/trust-accounting/client-ledgers/${need("khumaloId")}`);
    await expect(alice.getByTestId("client-detail-page")).toBeVisible({ timeout: 30_000 });
    const allocated = alice
      .locator('[data-testid^="history-row-"]')
      .filter({ hasText: "Deposit identified and allocated" });
    await expect(allocated).toHaveCount(1, { timeout: 15_000 });
    await expect(allocated.locator("td").nth(3)).toHaveText(randExact(FIG.allocation));
    // The firm's client-ledger history has no description column, so the receipt date in the
    // allocation's description ("Deposit received <date> — <bank description>") is read from the
    // ledger's own rows (read-only) — and seen on screen in the portal (step 19)
    const ledgerRows = await apiOk<{
      content: { transactionType: string; description: string; transactionDate: string }[];
    }>(
      `/api/trust-accounts/${need("gId")}/transactions?clientId=${need("khumaloId")}&type=UNIDENT_ALLOC_IN&page=0&size=20`
    );
    expect(ledgerRows.content).toHaveLength(1);
    expect(ledgerRows.content[0].transactionDate).toBe(M.d(27));
    expect(ledgerRows.content[0].description).toBe(
      `Deposit received ${M.words(20)} — CASH DEPOSIT BRANCH 0045`
    );

    // One item remains held
    await alice.goto(`${BASE}/trust-accounting/other-creditors`);
    await expect(alice.getByTestId("other-creditors-page")).toBeVisible({ timeout: 30_000 });
    await expect(
      items.filter({ hasText: "ACB CREDIT UNKNOWN" }).getByTestId("unidentified-deposit-status")
    ).toHaveText("Held");
    await expect(
      items
        .filter({ hasText: "CASH DEPOSIT BRANCH 0045" })
        .getByTestId("unidentified-deposit-status")
    ).toHaveText("Allocated to a client");
    await expect(
      items.getByTestId("unidentified-deposit-status").filter({ hasText: /^Held$/ })
    ).toHaveCount(1);
  });

  // ── 652.25 Step 14 — allow-and-flag, observed as at the 30th ──────────────────────────────
  test("14 · trust position as at the 30th for G: 102 957,50 = 102 957,50; Fund −172,50 flagged; R 57,50 charges to refund flagged; balanced", async () => {
    test.setTimeout(180_000);
    await runReport(alice, BASE, "trust-position", {
      dates: { asOfDate: M.d(30) },
      trustAccount: G,
    });
    const rows = await readReportRows(alice);
    const g = reportRow(rows, { "Trust Account": G });
    expect(g["Cashbook"]).toMatch(randExact(FIG.at30.cashbook));
    expect(g["Client Ledgers"]).toMatch(randExact(FIG.at30.clientLedgers));
    expect(g["Fidelity Fund Interest & Charges"]).toMatch(randExact(FIG.at30.fund));
    expect(g["Unidentified Deposits"]).toMatch(randExact(FIG.at30.unidentified));
    expect(g["Section 86(4) Investments"]).toMatch(randExact("0,00"));
    expect(g["Section 86(3) Placements"]).toMatch(randExact("0,00"));
    expect(g["Trust Creditors"]).toMatch(randExact(FIG.at30.trustCreditors));
    expect(g["Difference"]).toMatch(randExact(FIG.at30.difference));
    // The two flags, in the backend's words, and the books balance (decision 9: allow and flag)
    const integrity = g["Integrity"].replace(/[  ]/g, " ");
    expect(integrity).toMatch(
      new RegExp(
        `The Fund interest and charges ledger is ${randIn(FIG.at30.fundInDebit).source} in debit\\.`
      )
    );
    expect(integrity).toMatch(
      new RegExp(
        `${randIn(FIG.at30.chargesToRefund).source} of non-recoverable bank charges has been debited to trust and not yet refunded from the business account\\.`
      )
    );
    await evidenceShot(alice, "step-14-position-30-oct");

    // The same as-at check through the integrity endpoint (read-only): flag codes and "balanced"
    const check = await apiOk<{
      balanced: boolean;
      position: { cashbook: number; creditorTotal: number; fundLedger: number; difference: number };
      flags: { code: string; amount: number }[];
    }>(`/api/trust-accounts/${need("gId")}/integrity?asAt=${M.d(30)}`);
    expect(check.balanced).toBe(true);
    expect(Math.round(check.position.cashbook * 100)).toBe(cents(FIG.at30.cashbook));
    expect(Math.round(check.position.creditorTotal * 100)).toBe(cents(FIG.at30.trustCreditors));
    expect(Math.round(check.position.fundLedger * 100)).toBe(cents(FIG.at30.fund));
    expect(check.flags.map((f) => f.code).sort()).toEqual(
      ["CHARGES_TO_REFUND", "FUND_LEDGER_IN_DEBIT"].sort()
    );
  });

  // ── 652.26 Step 15 — complete G with the expected table ───────────────────────────────────
  test("15 · back in G's reconciliation, recalculate: the expected table (G, I-A, I-B, firm); complete; G's October list locks", async () => {
    test.setTimeout(300_000);
    await recalculate(wizard);
    await expectRecon(wizard, FIG.g);
    state.bankG = await bankBalanceText(wizard);
    await evidenceShot(wizard, "step-15-g-before-complete");
    state.gListId = await completeAndLock(wizard, FIG.g.trustCreditors);
    await evidenceShot(wizard, "step-15-g-complete");

    // Firm, as at the 31st — trust position firm-wide (summary cards = all accounts; one row each)
    await runReport(alice, BASE, "trust-position", { dates: { asOfDate: M.d(31) } });
    const rows = await readReportRows(alice);
    const g = reportRow(rows, { "Trust Account": G });
    const iA = reportRow(rows, { "Trust Account": IA.name });
    const iB = reportRow(rows, { "Trust Account": IB.name });
    expect(g["Cashbook"]).toMatch(randExact(FIG.g.cashbook));
    expect(g["Client Ledgers"]).toMatch(randExact(FIG.g.clientLedgers));
    expect(g["Fidelity Fund Interest & Charges"]).toMatch(randExact(FIG.g.fund));
    expect(g["Unidentified Deposits"]).toMatch(randExact(FIG.g.unidentified));
    expect(g["Section 86(3) Placements"]).toMatch(randExact("0,00"));
    expect(g["Trust Creditors"]).toMatch(randExact(FIG.g.trustCreditors));
    expect(g["Adjusted Bank Balance"]).toMatch(randExact(FIG.g.adjustedBank));
    expect(g["Difference"]).toMatch(randExact("0,00"));
    for (const column of [
      "Cashbook",
      "Client Ledgers",
      "Section 86(4) Investments",
      "Fidelity Fund Interest & Charges",
      "Unidentified Deposits",
      "Section 86(3) Placements",
      "Trust Creditors",
      "Difference",
      "Adjusted Bank Balance",
    ]) {
      expect(iA[column], `I-A ${column}`).toMatch(randExact("0,00"));
    }
    for (const column of [
      "Cashbook",
      "Section 86(4) Investments",
      "Trust Creditors",
      "Adjusted Bank Balance",
    ]) {
      expect(iB[column], `I-B ${column}`).toMatch(randExact(FIG.iB.cashbook));
    }
    expect(iB["Client Ledgers"]).toMatch(randExact("0,00"));
    expect(iB["Fidelity Fund Interest & Charges"]).toMatch(randExact("0,00"));
    expect(iB["Difference"]).toMatch(randExact("0,00"));

    await expect(reportSummary(alice, "Cashbook")).toHaveText(randExact(FIG.firm.cashbook));
    await expect(reportSummary(alice, "Client Ledgers")).toHaveText(
      randExact(FIG.firm.clientLedgers)
    );
    await expect(reportSummary(alice, "Investment Creditors")).toHaveText(
      randExact(FIG.firm.investmentCreditors)
    );
    await expect(reportSummary(alice, "Fund Ledger")).toHaveText(randExact(FIG.firm.fund));
    await expect(reportSummary(alice, "Unidentified Deposits")).toHaveText(
      randExact(FIG.firm.unidentified)
    );
    await expect(reportSummary(alice, "Inter Account")).toHaveText(
      randExact(FIG.firm.interAccount)
    );
    await expect(reportSummary(alice, "Creditor Total")).toHaveText(
      randExact(FIG.firm.trustCreditors)
    );
    await expect(reportSummary(alice, "Difference")).toHaveText(randExact(FIG.firm.difference));
    await expect(reportSummary(alice, "Balanced")).toHaveText("Yes");
    // No single screen shows the firm's unadjusted bank or adjusted-bank total: they are the sums of
    // the three accounts' figures, each observed above (wizard "Bank balance"; report column)
    expect(
      cents(need("bankG")) + cents(need("bankIA")) + cents(need("bankIB")),
      "firm bank = G + I-A + I-B statement closing balances"
    ).toBe(cents(FIG.firm.bank));
    expect(
      cents(g["Adjusted Bank Balance"]) +
        cents(iA["Adjusted Bank Balance"]) +
        cents(iB["Adjusted Bank Balance"]),
      "firm adjusted bank"
    ).toBe(cents(FIG.firm.adjustedBank));

    // Client ledgers as at completion
    await expectClientLedger(alice, need("ndlovuId"), {
      general: FIG.ndlovuGeneral,
      invested: null,
      total: null,
    });
    await expectClientLedger(alice, need("khumaloId"), FIG.khumalo);
  });

  // ── 652.27 Step 16 — Rule 54.15 list ──────────────────────────────────────────────────────
  test("16 · Rule 54.15 list for the 31st: the three locked lists; firm-wide 121 725,00 = 101 630,00 + 20 095,00", async () => {
    test.setTimeout(240_000);
    // (a) each account's locked October list (D-5)
    const lists = [
      {
        id: need("gListId"),
        account: G,
        adjusted: FIG.g.adjustedBank,
        cashbook: FIG.g.cashbook,
        creditors: FIG.g.trustCreditors,
        lines: [
          { name: NDLOVU.client, kind: "Client", balance: FIG.list.ndlovu.general },
          { name: KHUMALO.client, kind: "Client", balance: FIG.list.khumalo.general },
          {
            name: "Legal Practitioners' Fidelity Fund — interest and charges",
            kind: "Legal Practitioners' Fidelity Fund",
            balance: FIG.list.fund,
          },
        ],
        unidentified: { description: "ACB CREDIT UNKNOWN", balance: FIG.list.unidentified },
      },
      {
        id: need("iAListId"),
        account: IA.name,
        adjusted: "0,00",
        cashbook: "0,00",
        creditors: "0,00",
        lines: [
          {
            name: "Legal Practitioners' Fidelity Fund — interest and charges",
            kind: "Legal Practitioners' Fidelity Fund",
            balance: "0,00",
          },
        ],
        unidentified: null,
      },
      {
        id: need("iBListId"),
        account: IB.name,
        adjusted: FIG.iB.adjustedBank,
        cashbook: FIG.iB.cashbook,
        creditors: FIG.iB.trustCreditors,
        lines: [
          {
            name: KHUMALO.client,
            kind: "Invested (section 86(4))",
            balance: FIG.list.khumalo.invested,
          },
          {
            name: "Legal Practitioners' Fidelity Fund — interest and charges",
            kind: "Legal Practitioners' Fidelity Fund",
            balance: "0,00",
          },
        ],
        unidentified: null,
      },
    ];
    for (const list of lists) {
      await alice.goto(`${BASE}/trust-accounting/reconciliation/lists/${list.id}`);
      const page = alice.getByTestId("monthly-list-page");
      await expect(page).toBeVisible({ timeout: 30_000 });
      await expect(page).toContainText(list.account);
      await expect(page).toContainText("Rule 54.15");
      await expect(statCardValue(alice, "Adjusted bank balance")).toHaveText(
        randExact(list.adjusted)
      );
      await expect(statCardValue(alice, "Cashbook")).toHaveText(randExact(list.cashbook));
      await expect(statCardValue(alice, "Trust creditors")).toHaveText(randExact(list.creditors));
      await expect(statCardValue(alice, "Difference")).toHaveText(randExact("0,00"));
      const table = alice.getByTestId("monthly-list-creditors");
      for (const line of list.lines) {
        const row = table
          .locator("tbody tr")
          .filter({ has: alice.locator("td:nth-child(1)", { hasText: exactText(line.name) }) })
          .filter({ has: alice.locator("td:nth-child(2)", { hasText: exactText(line.kind) }) });
        await expect(row, `${list.account}: ${line.name} (${line.kind})`).toHaveCount(1);
        await expect(row.locator("td").nth(2)).toHaveText(randExact(line.balance));
      }
      if (list.unidentified) {
        const row = table
          .locator("tbody tr")
          .filter({ hasText: list.unidentified.description })
          .filter({
            has: alice.locator("td:nth-child(2)", { hasText: exactText("Unidentified deposit") }),
          });
        await expect(row).toHaveCount(1);
        await expect(row.locator("td").nth(0)).toContainText("· received");
        await expect(row.locator("td").nth(2)).toHaveText(randExact(list.unidentified.balance));
      }
      await expect(table.locator("tfoot td").nth(1)).toHaveText(randExact(list.creditors));
    }

    // (b) firm-wide: the Trust Creditors List report as at the 31st, every account locked
    await runReport(alice, BASE, "client-trust-balances", { dates: { asOfDate: M.d(31) } });
    const rows = await readReportRows(alice);
    const ndlovu = reportRow(rows, { "Trust Creditor": NDLOVU.client });
    expect(ndlovu["General Balance"]).toMatch(randExact(FIG.list.ndlovu.general));
    expect(ndlovu["Invested (s86(4))"]).toMatch(randExact(FIG.list.ndlovu.invested));
    expect(ndlovu["Total"]).toMatch(randExact(FIG.list.ndlovu.total));
    const khumalo = reportRow(rows, { "Trust Creditor": KHUMALO.client });
    expect(khumalo["General Balance"]).toMatch(randExact(FIG.list.khumalo.general));
    expect(khumalo["Invested (s86(4))"]).toMatch(randExact(FIG.list.khumalo.invested));
    expect(khumalo["Total"]).toMatch(randExact(FIG.list.khumalo.total));
    const fundLines = rows.filter(
      (r) => r["Trust Creditor"] === "Legal Practitioners' Fidelity Fund — interest and charges"
    );
    expect(fundLines.length, "the Fund line(s)").toBeGreaterThan(0);
    for (const fund of fundLines) expect(fund["Total"]).toMatch(randExact(FIG.list.fund));
    const acb = reportRow(rows, { "Trust Creditor": "ACB CREDIT UNKNOWN" });
    expect(acb["Received"]).toBe(M.d(22));
    expect(acb["Total"]).toMatch(randExact(FIG.list.unidentified));
    expect(rows.filter((r) => r["Trust Creditor"] === "CASH DEPOSIT BRANCH 0045")).toHaveLength(0);
    // Each account's lines come from its locked list ("Locked <date> by <member>")
    for (const r of [...fundLines, acb]) {
      expect(r["Source"], `${r["Trust Creditor"]} source`).toMatch(/^Locked /);
    }

    await expect(reportSummary(alice, "Creditor Total")).toHaveText(randExact(FIG.list.total));
    await expect(reportSummary(alice, "Bank Total")).toHaveText(randExact(FIG.list.trustBank));
    await expect(reportSummary(alice, "Investment Account Total")).toHaveText(
      randExact(FIG.list.trustInvestment)
    );
    await expect(reportSummary(alice, "Difference")).toHaveText(randExact(FIG.list.difference));
    await expect(reportSummary(alice, "Fund Total")).toHaveText(randExact(FIG.list.fund));
    await expect(reportSummary(alice, "Unidentified Total")).toHaveText(
      randExact(FIG.list.unidentified)
    );
    await expect(reportSummary(alice, "Invested Total")).toHaveText(
      randExact(FIG.list.khumalo.invested)
    );
    await expect(reportSummary(alice, "Fully Locked")).toHaveText("Yes");
    await evidenceShot(alice, "step-16-rule-54-15");
    // The three trust_monthly_lists rows: step 21 (psql)
  });

  // ── 652.28 Step 17 — Rule 54.28 schedule ──────────────────────────────────────────────────
  test("17 · Rule 54.28 schedule for the month: G, I-A, I-B; total gross interest 1 734,56; total gross charges 172,50", async () => {
    test.setTimeout(180_000);
    await runReport(alice, BASE, "trust-interest-charges-schedule", {
      dates: { dateFrom: M.d(1), dateTo: M.d(31) },
    });
    const rows = await readReportRows(alice);
    const s = FIG.schedule;
    const g = reportRow(rows, { "Trust Account": G });
    expect(g["Section"]).toBe("Section 86(2) trust banking account");
    expect(g["Month"]).toBe(M.monthName);
    expect(g["Gross Interest (Cashbook)"]).toMatch(randExact(s.g.grossInterest));
    expect(g["Charges Recoverable"]).toMatch(randExact(s.g.recoverable));
    expect(g["Charges Non-recoverable"]).toMatch(randExact(s.g.nonRecoverable));
    expect(g["VAT in Charges"]).toMatch(randExact(s.g.vat));
    expect(g["Charges Refunded"]).toMatch(randExact(s.g.refunded));
    expect(g["Paid to the Fund"]).toMatch(randExact(s.g.paidToFund));
    expect(g["Fund Ledger b/f"]).toMatch(randExact(s.g.broughtForward));
    expect(g["Fund Ledger c/f"]).toMatch(randExact(s.g.carriedForward));

    const iA = reportRow(rows, { "Trust Account": IA.name });
    expect(iA["Section"]).toBe("Section 86(4) investment account");
    expect(iA["Gross Interest (Cashbook)"]).toMatch(randExact(s.iA.grossInterest));
    expect(iA["Client's Share (s86(4))"]).toMatch(randExact(s.iA.client));
    expect(iA["Fund 5% Credited"]).toMatch(randExact(s.iA.fundCredited));
    expect(iA["Fund 5% Paid at Source"]).toMatch(randExact("0,00"));
    expect(iA["Paid to the Fund"]).toMatch(randExact(s.iA.paidToFund));
    expect(iA["Fund Ledger b/f"]).toMatch(randExact("0,00"));
    expect(iA["Fund Ledger c/f"]).toMatch(randExact("0,00"));

    const iB = reportRow(rows, { "Trust Account": IB.name });
    expect(iB["Section"]).toBe("Section 86(4) investment account");
    expect(iB["Gross Interest (Cashbook)"]).toMatch(randExact(s.iB.grossInterest));
    expect(iB["Client's Share (s86(4))"]).toMatch(randExact(s.iB.client));
    expect(iB["Fund 5% Credited"]).toMatch(randExact("0,00"));
    expect(iB["Fund 5% Paid at Source"]).toMatch(randExact(s.iB.fundPaidAtSource));
    expect(iB["Paid to the Fund"]).toMatch(randExact("0,00"));
    expect(iB["Fund Ledger b/f"]).toMatch(randExact("0,00"));
    expect(iB["Fund Ledger c/f"]).toMatch(randExact("0,00"));

    // The schedule has no firm-total card: the totals are the sums of its rows (each asserted)
    const sum = (column: string) =>
      rows.reduce((total, r) => total + (/\d/.test(r[column] ?? "") ? cents(r[column]) : 0), 0);
    expect(sum("Gross Interest (Cashbook)"), "total gross interest").toBe(
      cents(s.totalGrossInterest)
    );
    expect(sum("Charges Recoverable") + sum("Charges Non-recoverable"), "total gross charges").toBe(
      cents(s.totalGrossCharges)
    );
    await expect(reportSummary(alice, "Identity Holds")).toHaveText("Yes");
    await evidenceShot(alice, "step-17-rule-54-28");
  });

  // ── 652.29 Step 18 — Section 35 data pack ─────────────────────────────────────────────────
  test("18 · Section 35 data pack, year ending the 31st, per account (D-6): creditors, investments, schedule, the two Fund sweeps with file and line", async () => {
    test.setTimeout(300_000);
    const CREDITORS = "Trust creditors (Rule 54.15)";
    const S86_4 = "Section 86(4) Investments (Client Instruction)";
    const SCHEDULE = "Interest and charges schedule (Rule 54.28)";
    const SWEEPS = "Fund sweeps and their statement lines (Rule 54.14.16)";
    const FUND_LINE = "Legal Practitioners' Fidelity Fund — interest and charges";
    const pack = async (account: string) => {
      await runReport(alice, BASE, "section-35-data-pack", {
        dates: { financial_year_end: M.d(31) },
        trustAccount: account,
      });
      return readReportRows(alice);
    };
    const amount = (rows: Record<string, string>[], section: string, item: string) =>
      reportRow(rows, { Section: section, Item: item })["Amount"];
    const sweep = (rows: Record<string, string>[], file: string, line: string) => {
      const hits = rows.filter(
        (r) => r["Section"] === SWEEPS && r["Item"].includes(`${file} · line ${line}`)
      );
      expect(hits, `the Fund sweep on ${file} line ${line}`).toHaveLength(1);
      return hits[0]["Amount"];
    };
    const s = FIG.schedule;

    // G
    const g = await pack(G);
    expect(amount(g, CREDITORS, NDLOVU.client)).toMatch(randExact(FIG.list.ndlovu.general));
    expect(amount(g, CREDITORS, KHUMALO.client)).toMatch(randExact(FIG.list.khumalo.general));
    expect(amount(g, CREDITORS, FUND_LINE)).toMatch(randExact(FIG.list.fund));
    expect(amount(g, CREDITORS, `ACB CREDIT UNKNOWN · received ${M.words(22)}`)).toMatch(
      randExact(FIG.list.unidentified)
    );
    expect(amount(g, CREDITORS, "Total")).toMatch(randExact(FIG.g.trustCreditors));
    // Investments on the books at the year end (the register lists G's placements)
    expect(amount(g, S86_4, `${KHUMALO.client} · ${BANK} ${IB.number}`)).toMatch(
      randExact(FIG.list.trustInvestment)
    );
    expect(amount(g, S86_4, "Total")).toMatch(randExact(FIG.list.trustInvestment));
    expect(amount(g, SCHEDULE, `${G} — Fund ledger brought forward`)).toMatch(randExact("0,00"));
    expect(amount(g, SCHEDULE, `${G} — gross interest earned (in the cashbook)`)).toMatch(
      randExact(s.g.grossInterest)
    );
    expect(amount(g, SCHEDULE, `${G} — gross charges levied (in the cashbook)`)).toMatch(
      randExact(s.totalGrossCharges)
    );
    expect(amount(g, SCHEDULE, `${G} — of which recoverable from the business account`)).toMatch(
      randExact(s.g.recoverable)
    );
    expect(amount(g, SCHEDULE, `${G} — of which VAT`)).toMatch(randExact(s.g.vat));
    expect(amount(g, SCHEDULE, `${G} — charges refunded`)).toMatch(randExact(s.g.refunded));
    expect(amount(g, SCHEDULE, `${G} — paid to the Fund`)).toMatch(randExact(s.g.paidToFund));
    expect(amount(g, SCHEDULE, `${G} — Fund ledger carried forward`)).toMatch(randExact("0,00"));
    expect(sweep(g, CSV_NAMES.g, need("sweepLineG"))).toMatch(randExact(FIG.sweeps.g));
    await evidenceShot(alice, "step-18-section-35-g");

    // I-A
    const iA = await pack(IA.name);
    expect(amount(iA, SCHEDULE, `${IA.name} — gross interest earned (in the cashbook)`)).toMatch(
      randExact(s.iA.grossInterest)
    );
    expect(amount(iA, SCHEDULE, `${IA.name} — client's share (section 86(4))`)).toMatch(
      randExact(s.iA.client)
    );
    expect(amount(iA, SCHEDULE, `${IA.name} — Fund's 5% credited to the account`)).toMatch(
      randExact(s.iA.fundCredited)
    );
    expect(amount(iA, SCHEDULE, `${IA.name} — paid to the Fund`)).toMatch(
      randExact(s.iA.paidToFund)
    );
    expect(amount(iA, SCHEDULE, `${IA.name} — Fund ledger carried forward`)).toMatch(
      randExact("0,00")
    );
    expect(sweep(iA, CSV_NAMES.iA, need("sweepLineIA"))).toMatch(randExact(FIG.sweeps.iA));

    // I-B
    const iB = await pack(IB.name);
    expect(amount(iB, CREDITORS, KHUMALO.client)).toMatch(randExact(FIG.list.khumalo.invested));
    expect(amount(iB, S86_4, `${KHUMALO.client} · ${BANK} ${IB.number}`)).toMatch(
      randExact(FIG.list.trustInvestment)
    );
    expect(amount(iB, SCHEDULE, `${IB.name} — gross interest earned (in the cashbook)`)).toMatch(
      randExact(s.iB.grossInterest)
    );
    expect(amount(iB, SCHEDULE, `${IB.name} — client's share (section 86(4))`)).toMatch(
      randExact(s.iB.client)
    );
    expect(amount(iB, SCHEDULE, `${IB.name} — Fund's 5% paid at source`)).toMatch(
      randExact(s.iB.fundPaidAtSource)
    );
  });

  // ── 652.30 Steps 19–20 — portal and dashboard ─────────────────────────────────────────────
  test("19 · portal: Ndlovu sees R 380,00 s86(4) interest only; Khumalo R 95,00, R 31 000,00 + R 20 095,00 = R 51 095,00; neither sees R 1 234,56", async ({
    browser,
  }, testInfo) => {
    test.setTimeout(240_000);
    const origin = portalBaseUrl(); // D-3: unset = this step FAILS (never skipped)
    const open = async (email: string, matterId: string) => {
      const context = await browser.newContext({
        viewport: { width: 1280, height: 900 },
        timezoneId: testInfo.project.use.timezoneId,
      });
      const page = await context.newPage();
      await portalLogin(page, email); // one login per contact (magic-link cap)
      await page.goto(`${origin}/trust/${matterId}`);
      return { context, page };
    };
    const interestRows = (page: Page) =>
      page
        .locator('table[aria-label="Trust transactions"] tbody tr')
        .filter({ has: page.locator("td:nth-child(2)", { hasText: /interest/i }) });

    // Ndlovu: everything withdrawn — R 69 380,00 held, no invested breakdown
    const ndlovu = await open(NDLOVU.email, need("m1"));
    await expect(async () => {
      await ndlovu.page.reload();
      await expect(ndlovu.page.getByLabel("Current balance")).toHaveText(
        randExact(FIG.portal.ndlovuHeld),
        { timeout: 10_000 }
      );
    }).toPass({ timeout: 90_000 });
    await expect(interestRows(ndlovu.page)).toHaveCount(1);
    await expect(interestRows(ndlovu.page).locator("td").nth(1)).toHaveText(
      "Section 86(4) investment interest"
    );
    await expect(interestRows(ndlovu.page).locator("td").nth(3)).toHaveText(
      randExact(FIG.portal.ndlovuInterest)
    );
    await expect(ndlovu.page.locator("body")).not.toContainText(randIn(FIG.portal.hidden));
    await evidenceShot(ndlovu.page, "step-19-portal-ndlovu");
    await ndlovu.context.close();

    // Khumalo (D-4 labels): "Held in trust" R 51 095,00 = R 31 000,00 in the firm's trust account
    // + R 20 095,00 invested on her instruction; s86(4) interest R 95,00
    const khumalo = await open(KHUMALO.email, need("m2"));
    await expect(async () => {
      await khumalo.page.reload();
      await expect(khumalo.page.getByLabel("Current balance")).toHaveText(
        randExact(FIG.portal.khumaloHeld),
        { timeout: 10_000 }
      );
    }).toPass({ timeout: 90_000 });
    await expect(khumalo.page.getByText("Held in trust", { exact: true })).toBeVisible();
    // The balance card's breakdown (portal/components/trust/balance-card.tsx), no test ids
    const main = khumalo.page.locator("body");
    await expect(ddFor(main, "In the firm's trust account")).toHaveText(
      randExact(FIG.portal.khumaloGeneral)
    );
    await expect(ddFor(main, "Invested on your instruction (section 86(4))")).toHaveText(
      randExact(FIG.portal.khumaloInvested)
    );
    await expect(interestRows(khumalo.page)).toHaveCount(1);
    await expect(interestRows(khumalo.page).locator("td").nth(1)).toHaveText(
      "Section 86(4) investment interest"
    );
    await expect(interestRows(khumalo.page).locator("td").nth(3)).toHaveText(
      randExact(FIG.portal.khumaloInterest)
    );
    // The allocation (#24) with the original receipt date in its description
    const allocation = khumalo.page
      .locator('table[aria-label="Trust transactions"] tbody tr')
      .filter({ hasText: "Deposit identified and allocated" });
    await expect(allocation).toHaveCount(1);
    await expect(allocation.locator("td").nth(2)).toHaveText(
      `Deposit received ${M.words(20)} — CASH DEPOSIT BRANCH 0045`
    );
    await expect(allocation.locator("td").nth(3)).toHaveText(randExact(FIG.allocation));
    await expect(khumalo.page.locator("body")).not.toContainText(randIn(FIG.portal.hidden));
    await evidenceShot(khumalo.page, "step-19-portal-khumalo");
    await khumalo.context.close();
  });

  test('20 · trust dashboard: integrity card "Books balance" firm-wide; flags: none', async () => {
    test.setTimeout(120_000);
    await alice.goto(`${BASE}/trust-accounting`);
    const card = alice.getByTestId("trust-integrity-card");
    await expect(card).toBeVisible({ timeout: 30_000 });
    await expect(card).toContainText("all trust accounts");
    await expect(card.getByTestId("integrity-books-balance")).toHaveText("Books balance");
    await expect(card.getByTestId("integrity-no-flags")).toBeVisible();
    await expect(card.getByTestId("integrity-flags")).toHaveCount(0);
    await evidenceShot(alice, "step-20-dashboard");
  });

  // ── 652.31 Step 21 — database check (read-only) and evidence ──────────────────────────────
  test("21 · psql (read-only): per account Σ cashbook_effect = Σ ledger_effect to the 31st; the three locked lists hold the totals", async () => {
    test.setTimeout(180_000);
    const { effects, lists } = runPsqlCheck(M.d(31)); // writes psql-output.txt

    const expected: Record<string, string> = {
      [G]: FIG.g.cashbook,
      [IA.name]: "0,00",
      [IB.name]: FIG.iB.cashbook,
    };
    expect(effects.map((e) => e.account).sort()).toEqual(Object.keys(expected).sort());
    for (const e of effects) {
      expect(dbCents(e.cashbook), `${e.account} cashbook`).toBe(cents(expected[e.account]));
      expect(dbCents(e.ledgers), `${e.account} Σ ledger effects`).toBe(dbCents(e.cashbook));
      expect(dbCents(e.diff), `${e.account} difference`).toBe(0);
    }

    expect(lists.map((l) => l.account).sort()).toEqual(Object.keys(expected).sort());
    const list = (account: string) => lists.find((l) => l.account === account)!;
    for (const l of lists) {
      expect(l.periodEnd, `${l.account} list period end`).toBe(M.d(31));
      expect(dbCents(l.difference), `${l.account} list difference`).toBe(0);
      expect(dbCents(l.interAccount), `${l.account} inter-account`).toBe(0);
      expect(dbCents(l.outstandingDeposits), `${l.account} outstanding deposits`).toBe(0);
    }
    const g = list(G);
    expect(dbCents(g.bank)).toBe(cents(FIG.g.bank));
    expect(dbCents(g.outstandingPayments)).toBe(cents(FIG.g.outstandingPayments));
    expect(dbCents(g.adjustedBank)).toBe(cents(FIG.g.adjustedBank));
    expect(dbCents(g.cashbook)).toBe(cents(FIG.g.cashbook));
    expect(dbCents(g.clientLedgers)).toBe(cents(FIG.g.clientLedgers));
    expect(dbCents(g.investmentCreditors)).toBe(0);
    expect(dbCents(g.fund)).toBe(cents(FIG.g.fund));
    expect(dbCents(g.unidentified)).toBe(cents(FIG.g.unidentified));
    expect(dbCents(g.creditorTotal)).toBe(cents(FIG.g.trustCreditors));
    const iA = list(IA.name);
    for (const value of [
      iA.bank,
      iA.outstandingPayments,
      iA.adjustedBank,
      iA.cashbook,
      iA.clientLedgers,
      iA.investmentCreditors,
      iA.fund,
      iA.unidentified,
      iA.creditorTotal,
    ]) {
      expect(dbCents(value), `${IA.name} list`).toBe(0);
    }
    const iB = list(IB.name);
    expect(dbCents(iB.bank)).toBe(cents(FIG.iB.bank));
    expect(dbCents(iB.outstandingPayments)).toBe(0);
    expect(dbCents(iB.adjustedBank)).toBe(cents(FIG.iB.adjustedBank));
    expect(dbCents(iB.cashbook)).toBe(cents(FIG.iB.cashbook));
    expect(dbCents(iB.clientLedgers)).toBe(0);
    expect(dbCents(iB.investmentCreditors)).toBe(cents(FIG.iB.investmentCreditors!));
    expect(dbCents(iB.fund)).toBe(0);
    expect(dbCents(iB.unidentified)).toBe(0);
    expect(dbCents(iB.creditorTotal)).toBe(cents(FIG.iB.trustCreditors));
    // Firm: the three lists add up to the firm-wide list of step 16
    expect(lists.reduce((t, l) => t + dbCents(l.creditorTotal), 0)).toBe(cents(FIG.list.total));
  });
});
