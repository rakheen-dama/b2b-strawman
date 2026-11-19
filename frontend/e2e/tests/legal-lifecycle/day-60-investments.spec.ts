/**
 * Day 60 — Investments
 *
 * The calculated interest run is gone (Phase 92 651C, founder decision 6; scenario 60.1–60.10 is
 * amended in 651B). Investments (Phase 92 648A, ADR-379 — the authorised direction of 60.12–60.20):
 * each is placed from the trust banking account into an investment account of its basis and Bob
 * approves the placement — a R200,000 Standard Bank fixed deposit of the firm's surplus trust money
 * (s86(3), names no client) and QuickCollect's R30,000 Nedbank call account on its instruction
 * (s86(4); the client's total is unchanged). Second billing cycle (new Sipho time → fee note),
 * timesheet report and profitability report.
 *
 * Records come from the lifecycle run (helpers/legal-lifecycle.ts): Days 0–45 must have run on the
 * same stack (trust account, deposits, Moroka's fee transfer, rated time).
 *
 * Phase 88 628E: rewritten strict — every step is real or asserted (no `isVisible().catch` guards).
 *
 * Run:
 *   E2E_AUTH_MODE=mock PLAYWRIGHT_BASE_URL=http://localhost:3001 pnpm test:e2e:legal-lifecycle
 */
import { test, expect, type Page } from "@playwright/test";
import { loginAs } from "../../fixtures/auth";
import { captureScreenshot } from "../../helpers/screenshot";
import { apiOk } from "../../helpers/api";
import {
  BASE,
  clickThrough,
  escapeRegExp,
  isoDate,
  openMatter,
} from "../../helpers/legal-depth-ii";
import {
  clientId,
  feeNoteTotalsRow,
  generateFeeNoteDraft,
  logTimeOnTask,
  matterId,
  morokaClient,
  openClientFeeNotes,
  openTrustTransactions,
  qcClient,
  randPattern,
  recordValue,
  siphoClient,
  siphoMatter,
} from "../../helpers/legal-lifecycle";

const SIPHO_SUMMONS_TIME = "Draft combined summons — RAF claim";

async function openInvestments(page: Page): Promise<void> {
  await page.goto(`${BASE}/trust-accounting/investments`);
  await expect(page.getByTestId("investments-page")).toBeVisible({ timeout: 15_000 });
  await expect(page.getByRole("heading", { level: 1, name: "Investments" })).toBeVisible();
}

type Basis = "s86(3)" | "s86(4)";

const BASIS_RADIO: Record<Basis, string> = {
  "s86(3)": "Section 86(3) — the firm invests surplus trust money",
  "s86(4)": "Section 86(4) — invested on a client's instruction",
};

/** The investment accounts the placements go into (648A: one per basis, added from the dialog). */
const INVESTMENT_ACCOUNTS = {
  "s86(3)": {
    name: "Standard Bank Section 86(3) Investments",
    bank: "Standard Bank",
    branchCode: "051001",
    accountNumber: "0863000001",
  },
  "s86(4)": {
    name: "Nedbank Section 86(4) Investments",
    bank: "Nedbank",
    branchCode: "198765",
    accountNumber: "0864000001",
  },
} as const;

const S863_DEPOSIT = "FD-0987654321";
const S864_DEPOSIT = "CALL-1122334455";

/**
 * Picks the basis's investment account in the Place Investment dialog, adding it first ("Add
 * investment account" opens the account dialog preset to the basis) when the firm has none.
 */
async function chooseInvestmentAccount(page: Page, basis: Basis): Promise<void> {
  const account = INVESTMENT_ACCOUNTS[basis];
  const dialog = page.getByTestId("place-investment-dialog");
  await dialog.getByTestId("investment-account-select").click();
  await expect(page.getByRole("listbox")).toBeVisible();
  const option = page.getByRole("option", { name: new RegExp(`^${escapeRegExp(account.name)} `) });
  if ((await option.count()) > 0) {
    await option.first().click();
    return;
  }
  await page.keyboard.press("Escape");
  await dialog.getByRole("button", { name: "Add investment account" }).click();
  const add = page.getByRole("dialog", { name: "Add Investment Account" });
  await expect(add).toBeVisible();
  await expect(add.getByTestId("trust-account-type-select")).toHaveText("Investment");
  await expect(add.getByTestId("trust-account-basis-select")).toContainText(
    basis === "s86(3)" ? "Section 86(3)" : "Section 86(4)"
  );
  await add.getByLabel("Account Name").fill(account.name);
  await add.getByLabel("Bank Name").fill(account.bank);
  await add.getByLabel("Branch Code").fill(account.branchCode);
  await add.getByLabel("Account Number").fill(account.accountNumber);
  await add.getByRole("button", { name: "Create Account" }).click();
  await expect(add).toBeHidden({ timeout: 15_000 });
  await expect(dialog.getByTestId("investment-account-select")).toContainText(account.name);
}

/**
 * Places a trust investment from the trust banking account into the basis's investment account.
 * A section 86(4) investment names the client and the matter whose money it is (the dialog takes
 * raw UUIDs); a section 86(3) one names neither — the dialog does not ask.
 */
async function placeInvestment(
  page: Page,
  opts: {
    basis: Basis;
    client?: { clientId: string; matterId: string };
    institution: string;
    accountNumber: string;
    principal: string;
    ratePercent: string;
    maturityDate?: string;
    notes: string;
  }
): Promise<void> {
  await openInvestments(page);
  await page.getByRole("button", { name: "Place Investment" }).click();
  const dialog = page.getByTestId("place-investment-dialog");
  await expect(dialog.getByRole("heading", { name: "Place Investment" })).toBeVisible();
  await dialog.getByRole("radio", { name: BASIS_RADIO[opts.basis] }).check();
  await expect(dialog.getByTestId("investment-basis-help")).toHaveText(
    opts.basis === "s86(4)" ? /5%|statutory/i : /Fidelity Fund/
  );
  await chooseInvestmentAccount(page, opts.basis);
  if (opts.basis === "s86(4)") {
    await dialog.getByLabel("Client ID").fill(opts.client!.clientId);
    await dialog.getByLabel("Matter ID").fill(opts.client!.matterId);
  } else {
    await expect(dialog.getByLabel("Client ID")).toHaveCount(0);
    await expect(dialog.getByLabel("Matter ID")).toHaveCount(0);
  }
  await dialog.getByLabel("Institution").fill(opts.institution);
  await dialog.getByLabel("Account Number").fill(opts.accountNumber);
  await dialog.getByLabel("Principal").fill(opts.principal);
  await dialog.getByLabel("Interest Rate %").fill(opts.ratePercent);
  if (opts.maturityDate) {
    await dialog.getByLabel("Maturity Date (optional)").fill(opts.maturityDate);
  }
  await dialog.getByLabel("Notes (optional)").fill(opts.notes);
  await dialog.getByRole("button", { name: "Place Investment" }).press("Enter");
  await expect(dialog).toBeHidden({ timeout: 15_000 });
}

interface InvestmentWire {
  id: string;
  accountNumber: string;
  clientId: string | null;
  matterId: string | null;
  balance: number;
  status: string;
  depositTransactionId: string | null;
}

/** The primary trust banking account's id (the investments' funding account). */
async function fundingAccountId(): Promise<string> {
  const accounts =
    await apiOk<{ id: string; accountType: string; isPrimary: boolean }[]>("/api/trust-accounts");
  const banking = accounts.filter((a) => a.accountType !== "INVESTMENT");
  const funding = banking.find((a) => a.isPrimary) ?? banking[0];
  expect(funding, "the trust banking account").toBeDefined();
  return funding!.id;
}

/** The investment placed under the deposit `accountNumber` (read through the API). */
async function investmentNumbered(accountNumber: string): Promise<InvestmentWire> {
  const funding = await fundingAccountId();
  const page = await apiOk<{ content: InvestmentWire[] }>(
    `/api/trust-accounts/${funding}/investments?size=100`
  );
  const matches = page.content.filter((i) => i.accountNumber === accountNumber);
  expect(matches, `one investment numbered ${accountNumber}`).toHaveLength(1);
  return matches[0];
}

/** The client's general (client ledger) balance on the funding account. */
async function generalBalance(client: string): Promise<number> {
  const funding = await fundingAccountId();
  const card = await apiOk<{ balance: number }>(
    `/api/trust-accounts/${funding}/client-ledgers/${client}`
  );
  return Number(card.balance);
}

/** The client's card on the trust banking account: general balance and what is invested from it (648E). */
async function ledgerCard(client: string): Promise<{ balance: number; investedBalance: number }> {
  const funding = await fundingAccountId();
  const card = await apiOk<{ balance: number; investedBalance: number }>(
    `/api/trust-accounts/${funding}/client-ledgers/${client}`
  );
  return { balance: Number(card.balance), investedBalance: Number(card.investedBalance) };
}

/**
 * 30000 → "30 000,00" (the en-ZA figure randPattern matches). Built by hand: Node's ICU data for
 * "en-ZA" differs by build (some print "30,000.00").
 */
function rand(n: number): string {
  const [whole, cents] = n.toFixed(2).split(".");
  return `${whole.replace(/\B(?=(\d{3})+(?!\d))/g, " ")},${cents}`;
}

/** The money held for the matter: its general balance plus what is invested for it. */
async function matterTotal(matter: string): Promise<number> {
  const total = await apiOk<{ balance: number }>(`/api/matters/${matter}/trust-balance`);
  return Number(total.balance);
}

/** Bob approves an investment's pending placement on Trust → Transactions. */
async function approvePlacement(page: Page, investment: InvestmentWire): Promise<void> {
  expect(investment.depositTransactionId, "the placement row").not.toBeNull();
  const txId = investment.depositTransactionId!;
  await openTrustTransactions(page);
  const row = page.getByTestId(`transaction-row-${txId}`);
  await expect(row).toContainText("AWAITING APPROVAL", { timeout: 15_000 });
  // The approver sees where the money goes
  await expect(page.getByTestId(`transaction-description-${txId}`)).toContainText(
    /Placed in section 86\([34]\) investment at/
  );
  await page
    .getByTestId(`approval-actions-${txId}`)
    .getByRole("button", { name: "Approve" })
    .click();
  await expect(row).toContainText("APPROVED", { timeout: 15_000 });
  await expect(row).not.toContainText("AWAITING");
}

test.describe.serial("Day 60 — Investments", () => {
  // ── 60.11-60.17: Investment — R200,000 Fixed Deposit (s86(3), the firm's surplus) ───
  test("Alice: Navigate to Investment Register", async ({ page }) => {
    await loginAs(page, "alice");
    await openInvestments(page);
    await expect(page.getByRole("button", { name: "Place Investment" })).toBeVisible();
    await expect(page.getByTestId("empty-investments")).toBeVisible();
  });

  test("Alice: Place investment — R200,000 Standard Bank Fixed Deposit (s86(3), names no client)", async ({
    page,
  }) => {
    await loginAs(page, "alice");
    await placeInvestment(page, {
      basis: "s86(3)",
      institution: "Standard Bank",
      accountNumber: S863_DEPOSIT,
      principal: "200000",
      ratePercent: "8.5",
      maturityDate: isoDate(90),
      notes: "90-day fixed deposit — the firm's surplus trust money",
    });
    const row = page.locator("main tbody tr").filter({ hasText: S863_DEPOSIT });
    await expect(row).toHaveCount(1, { timeout: 15_000 });
    await expect(row).toContainText("Firm (section 86(3))");
    await expect(row).not.toContainText(morokaClient());
    await expect(row).toContainText("Section 86(3)");
    await expect(row).toContainText(INVESTMENT_ACCOUNTS["s86(3)"].name);
    await expect(row).toContainText("Standard Bank");
    await expect(row).toContainText(randPattern("200 000,00"));
    await expect(row).toContainText("8.50%");
    await expect(row).toContainText("Arrangement");
    // Nothing moves until the placement is approved
    await expect(row).toContainText("Awaiting approval");

    const investment = await investmentNumbered(S863_DEPOSIT);
    expect(investment.clientId).toBeNull();
    expect(investment.matterId).toBeNull();
    expect(Number(investment.balance)).toBe(0);

    // Screenshot: investment placed
    await captureScreenshot(page, "day-60-investment-s86-placed");
    await captureScreenshot(page, "investment-s86-selection", { curated: true });
  });

  test("Bob: Approve the s86(3) placement — R200,000 reaches the investment account; no client's balance moves", async ({
    page,
  }) => {
    await loginAs(page, "bob");
    const investment = await investmentNumbered(S863_DEPOSIT);
    const morokaBefore = await generalBalance(clientId("moroka"));
    await approvePlacement(page, investment);

    const placed = await investmentNumbered(S863_DEPOSIT);
    expect(placed.status).toBe("ACTIVE");
    expect(Number(placed.balance)).toBe(200000);
    expect(await generalBalance(clientId("moroka"))).toBe(morokaBefore);

    await openInvestments(page);
    const row = page.locator("main tbody tr").filter({ hasText: S863_DEPOSIT });
    await expect(row).toContainText("Active", { timeout: 15_000 });
    await expect(row).not.toContainText("Awaiting approval");
  });

  // ── 60.18-60.20: Investment — QuickCollect R30,000 Call Account (s86(4)) ────
  test("Alice: Place investment — QuickCollect R30,000 Nedbank Call Account (s86(4))", async ({
    page,
  }) => {
    await loginAs(page, "alice");
    await placeInvestment(page, {
      basis: "s86(4)",
      client: { clientId: clientId("qc"), matterId: matterId("qc") },
      institution: "Nedbank",
      accountNumber: S864_DEPOSIT,
      principal: "30000",
      ratePercent: "7.25",
      notes: "Call account on QuickCollect's written instruction",
    });
    const row = page.locator("main tbody tr").filter({ hasText: qcClient() });
    await expect(row).toHaveCount(1, { timeout: 15_000 });
    await expect(row).toContainText("Section 86(4)");
    await expect(row).toContainText(INVESTMENT_ACCOUNTS["s86(4)"].name);
    await expect(row).toContainText("Nedbank");
    await expect(row).toContainText(randPattern("30 000,00"));
    await expect(row).toContainText("5% (statutory)");
    await expect(row).toContainText("Call deposit");
    await expect(row).toContainText("Awaiting approval");

    // 60.20: the register tells the two bases apart
    const firmRow = page.locator("main tbody tr").filter({ hasText: S863_DEPOSIT });
    await expect(firmRow).toContainText("Section 86(3)");
  });

  test("Bob: Approve the s86(4) placement — QuickCollect's total is unchanged, R30,000 now invested", async ({
    page,
  }) => {
    await loginAs(page, "bob");
    const investment = await investmentNumbered(S864_DEPOSIT);
    expect(investment.clientId).toBe(clientId("qc"));
    expect(investment.matterId).toBe(matterId("qc"));
    const totalBefore = await matterTotal(matterId("qc"));
    const generalBefore = await generalBalance(clientId("qc"));
    const cardBefore = await ledgerCard(clientId("qc"));

    await approvePlacement(page, investment);

    // The money moved from QuickCollect's general balance to its section 86(4) investment: the
    // client's total trust money is unchanged
    expect(await matterTotal(matterId("qc"))).toBe(totalBefore);
    expect(await generalBalance(clientId("qc"))).toBe(generalBefore - 30000);
    const placed = await investmentNumbered(S864_DEPOSIT);
    expect(Number(placed.balance)).toBe(30000);

    // 648E: the client-ledger card carries the invested figure beside the general balance, and
    // general + invested (the total held in trust) is what it was before the placement
    const card = await ledgerCard(clientId("qc"));
    expect(card.investedBalance).toBe(cardBefore.investedBalance + 30000);
    expect(card.balance + card.investedBalance).toBe(
      cardBefore.balance + cardBefore.investedBalance
    );
    await page.goto(`${BASE}/trust-accounting/client-ledgers/${clientId("qc")}`);
    const summary = page.getByTestId("client-summary");
    await expect(summary).toBeVisible({ timeout: 15_000 });
    await expect(summary.getByTestId("ledger-total-invested")).toContainText(
      randPattern(rand(card.investedBalance))
    );
    await expect(summary.getByTestId("ledger-total-held")).toContainText(
      randPattern(rand(card.balance + card.investedBalance))
    );
    await expect(summary.getByTestId("ledger-total-balance")).toContainText(
      randPattern(rand(card.balance))
    );

    await openInvestments(page);
    const row = page.locator("main tbody tr").filter({ hasText: qcClient() });
    await expect(row).toContainText("Active", { timeout: 15_000 });
    await expect(row.getByRole("button", { name: "Withdraw" })).toBeVisible();
  });

  // ── Phase 92 648B (OQ-15): section 86(4) interest — a bank advice net of the Fund's 5% ──
  test("Alice: Record section 86(4) interest — net of the Fund's 5% (bank advice, gross R100,00)", async ({
    page,
  }) => {
    await loginAs(page, "alice");
    const investment = await investmentNumbered(S864_DEPOSIT);
    expect(Number(investment.balance)).toBe(30000);
    const totalBefore = await matterTotal(matterId("qc"));

    await openInvestments(page);
    const row = page.locator("main tbody tr").filter({ hasText: qcClient() });
    await row.getByRole("button", { name: "Record interest" }).click();
    const dialog = page.getByTestId("record-investment-interest-dialog");
    await expect(dialog.getByRole("heading", { name: "Record interest" })).toBeVisible();
    // No statement line: a bank advice — the bank paid the Fund's 5% at source, so NET only
    await expect(dialog.getByLabel("Statement line")).toHaveValue("");
    await expect(
      dialog.getByRole("radio", { name: "Gross — the Fund's 5% leaves in a separate debit" })
    ).toBeDisabled();
    await expect(dialog.getByRole("radio", { name: "Net of the Fund's 5%" })).toBeChecked();
    await expect(dialog.getByTestId("interest-bank-advice-note")).toBeVisible();
    await dialog.getByLabel("Gross interest").fill("100");
    await expect(dialog.getByTestId("interest-fund-share")).toHaveText(randPattern("5,00"));
    await expect(dialog.getByTestId("interest-client-share")).toHaveText(randPattern("95,00"));
    await dialog.getByRole("button", { name: "Record interest" }).click();
    await expect(dialog).toBeHidden({ timeout: 15_000 });

    // The register: gross = Fund 5% + net; the investment now holds the client's 95%
    const sub = (label: string, amount: string) =>
      new RegExp(`${escapeRegExp(label)} R[\\s\\u00a0\\u202f]?${randPattern(amount).source}`);
    await expect(row).toContainText(sub("Gross interest", "100,00"), { timeout: 15_000 });
    await expect(row).toContainText(sub("Fund 5%", "5,00"));
    await expect(row).toContainText(sub("Net interest", "95,00"));
    await expect(row).toContainText(randPattern("30 095,00"));
    await expect(row).not.toContainText(/\bNET\b|\bGROSS\b|INV_INTEREST/);

    const entries = await apiOk<
      {
        presentation: string;
        status: string;
        grossAmount: number;
        fundShareAmount: number;
        netAmount: number;
      }[]
    >(`/api/trust-investments/${investment.id}/interest`);
    expect(entries).toHaveLength(1);
    expect(entries[0].presentation).toBe("NET");
    expect(entries[0].status).toBe("POSTED");
    expect(Number(entries[0].grossAmount)).toBe(100);
    expect(Number(entries[0].fundShareAmount)).toBe(5);
    expect(Number(entries[0].netAmount)).toBe(95);
    expect(Number((await investmentNumbered(S864_DEPOSIT)).balance)).toBe(30095);
    // The client's 95% is QuickCollect's money: the matter's total trust money rises by it
    expect(await matterTotal(matterId("qc"))).toBe(totalBefore + 95);

    await captureScreenshot(page, "day-60-investment-s864-interest-recorded");
  });

  test("Alice: the s86(3) row offers no Record interest action — its interest is the Fund's", async ({
    page,
  }) => {
    await loginAs(page, "alice");
    await openInvestments(page);
    const firmRow = page.locator("main tbody tr").filter({ hasText: S863_DEPOSIT });
    await expect(firmRow).toContainText("Active", { timeout: 15_000 });
    await expect(firmRow.getByRole("button", { name: "Withdraw" })).toBeVisible();
    await expect(firmRow.getByRole("button", { name: "Record interest" })).toHaveCount(0);
    await expect(firmRow.getByTestId("investment-interest-fund-note")).toHaveText(
      "Interest is the Fund's — record it from the investment account's statement"
    );
  });

  // ── 60.21-60.24: Second billing cycle fee notes ───────────────────
  test("Alice: Second billing cycle — log new Sipho time and generate its fee note", async ({
    page,
  }) => {
    await loginAs(page, "alice");
    await openMatter(page, siphoMatter());
    await logTimeOnTask(page, {
      task: "Issue summons / combined summons",
      hours: 1,
      minutes: 0,
      date: isoDate(),
      description: SIPHO_SUMMONS_TIME,
      rate: "2 500,00",
    });

    // Only the new entry is unbilled: Day 7–14 time is on the first (sent) fee note
    await openClientFeeNotes(page, siphoClient());
    const feeNoteId = await generateFeeNoteDraft(page, { matter: siphoMatter(), items: 1 });
    recordValue("siphoFeeNote2", feeNoteId);
    await expect(
      page.locator("main").getByRole("row").filter({ hasText: SIPHO_SUMMONS_TIME })
    ).toHaveCount(1);
    await expect(feeNoteTotalsRow(page, "Subtotal")).toContainText(randPattern("2 500,00"));
    await expect(feeNoteTotalsRow(page, "Total")).toContainText(randPattern("2 875,00"));
  });

  // ── 60.25-60.29: Reports ──────────────────────────────────────────
  test("Alice: Run the timesheet report for this month", async ({ page }) => {
    await loginAs(page, "alice");
    await page.goto(`${BASE}/reports`);
    await clickThrough(
      page.getByRole("link", { name: /Timesheet Report/ }),
      page.getByRole("heading", { level: 1, name: "Timesheet Report" })
    );
    await expect(page).toHaveURL(/\/reports\/timesheet/);
    await page.getByLabel(/^From/).fill(isoDate(-30));
    await page.getByLabel(/^To/).fill(isoDate());
    await page.getByRole("button", { name: "Run Report" }).click();
    await expect(page.getByRole("button", { name: "Export CSV" })).toBeEnabled({
      timeout: 15_000,
    });
    // The lifecycle's fee earners appear in the results
    const results = page.locator("main table");
    for (const member of ["Alice Owner", "Bob Admin", "Carol Member"]) {
      await expect(results.getByText(member).first()).toBeVisible({ timeout: 15_000 });
    }
  });

  test("Alice: Check profitability report", async ({ page }) => {
    await loginAs(page, "alice");
    // Phase 87 S6a (ADR-344): route — /profitability 308s to /reports/profitability (612A)
    await page.goto(`${BASE}/profitability`);
    await expect(page).toHaveURL(/\/reports\/profitability/);
    await expect(page.getByRole("heading", { level: 1, name: "Profitability" })).toBeVisible({
      timeout: 15_000,
    });
    // (a second, hidden copy of the page renders outside #main-content)
    await expect(
      page.locator("#main-content").getByTestId("profitability-cost-note")
    ).toBeVisible();
    // This month's rated time puts Sipho's matter on the matter table
    await expect(page.getByRole("row").filter({ hasText: siphoMatter() }).first()).toBeVisible();

    // Screenshot: profitability report
    await captureScreenshot(page, "day-60-profitability-loaded");
    await captureScreenshot(page, "profitability-per-matter", { curated: true });
  });
});
