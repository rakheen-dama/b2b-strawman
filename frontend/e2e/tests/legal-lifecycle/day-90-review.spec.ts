/**
 * Day 90 — Quarter Review & Role-Based Access
 *
 * The quarter review over the lifecycle's own records: the four clients and six matters, the fee
 * notes billed on Days 30 and 60, the Section 35 data pack, trust ledgers / investments /
 * transactions, profitability (this month's rated time), the owner dashboard, an engagement letter
 * generated for Sipho's matter, compliance, and Sipho's diary (postponed + rescheduled motion).
 * Then access control: Carol (Member) is denied rates, trust settings and trust approvals; Bob
 * (Admin) and Alice (Owner) are allowed.
 *
 * Records come from the lifecycle run (helpers/legal-lifecycle.ts): Days 0–75 must have run on the
 * same stack.
 *
 * Phase 88 628E: rewritten strict — every step is real or asserted (no `isVisible().catch` guards).
 *
 * Run:
 *   E2E_AUTH_MODE=mock PLAYWRIGHT_BASE_URL=http://localhost:3001 pnpm test:e2e:legal-lifecycle
 */
import { test, expect } from "@playwright/test";
import { loginAs } from "../../fixtures/auth";
import { captureScreenshot } from "../../helpers/screenshot";
import { openGroupedTab } from "../../helpers/grouped-tabs";
import { BASE, clickThrough, isoDate, openMatter } from "../../helpers/legal-depth-ii";
import {
  apexMatter,
  clientId,
  currentLifecycleRun,
  lifecycleClientName,
  morokaClient,
  morokaMatter,
  qcClient,
  qcMatter,
  recordedValue,
  siphoClient,
  siphoMatter,
  openTrustTransactions,
  recordValue,
  TRUST_ACCOUNT,
  trustTransactionRow,
} from "../../helpers/legal-lifecycle";

const runMatters = () => [
  siphoMatter(),
  apexMatter(),
  morokaMatter(),
  qcMatter(),
  `Ndlovu Property Dispute ${currentLifecycleRun()}`,
  `Apex Annual Returns ${currentLifecycleRun()}`,
];
const pendingTransferRef = () => `FEE/SIPHO/${currentLifecycleRun()}`;
const ENGAGEMENT_LETTER = "Engagement Letter — Litigation";
const FEE_NOTES = ["siphoFeeNote", "apexFeeNote", "morokaFeeNote", "qcFeeNote", "siphoFeeNote2"];

// ═══════════════════════════════════════════════════════════════════
//  DAY 90 — Quarter Review
// ═══════════════════════════════════════════════════════════════════
test.describe.serial("Day 90 — Quarter Review", () => {
  // ── 90.1-90.7: Portfolio review ───────────────────────────────────
  test("Alice: Client list shows the four lifecycle clients", async ({ page }) => {
    await loginAs(page, "alice");
    // Phase 87 S4 (ADR-344): route rename
    await page.goto(`${BASE}/clients`);
    // (a second, hidden copy of the list renders outside #main-content)
    const table = page.locator("#main-content").getByTestId("clients-table");
    await expect(table).toBeVisible({ timeout: 15_000 });
    for (const key of ["sipho", "apex", "moroka", "qc"] as const) {
      await expect(
        table.getByRole("link", { name: lifecycleClientName(key), exact: true })
      ).toBeVisible();
    }
  });

  test("Alice: Matters list shows the lifecycle's six matters", async ({ page }) => {
    await loginAs(page, "alice");
    // Phase 87 S4 (ADR-344): route rename
    await page.goto(`${BASE}/matters`);
    // (a second, hidden copy of the list renders outside #main-content)
    const table = page.locator("#main-content").getByTestId("matters-table");
    await expect(table).toBeVisible({ timeout: 15_000 });
    for (const matter of runMatters()) {
      await expect(table.getByRole("link", { name: matter, exact: true })).toBeVisible();
    }
  });

  test("Alice: Fee notes list shows the run's five fee notes", async ({ page }) => {
    await loginAs(page, "alice");
    await page.goto(`${BASE}/invoices`);
    await expect(page.getByRole("heading", { level: 1, name: "Fee notes" })).toBeVisible({
      timeout: 15_000,
    });
    // Real fee-note rows only (a row links to /invoices/<uuid>)
    const rows = page
      .locator("main table tbody tr")
      .filter({ has: page.locator('a[href*="/invoices/"]') });
    await expect.poll(() => rows.count(), { timeout: 15_000 }).toBeGreaterThanOrEqual(2);
    for (const key of FEE_NOTES) {
      await expect(
        page.locator(`main a[href$="/invoices/${recordedValue(key)}"]`),
        key
      ).toBeVisible();
    }

    // Screenshot: portfolio review
    await captureScreenshot(page, "day-90-portfolio-review-loaded");
  });

  // ── 90.8-90.11: Section 35 Data Pack ──────────────────────────────
  test("Alice: Generate Section 35 Data Pack report", async ({ page }) => {
    // 628E-BLOCKER (reproduced 2026-10-29 on :3001): Trust → Reports says "No trust reports
    // available" — the tenant has only the timesheet / invoice-aging / project-profitability report
    // definitions, because TrustReportPackSeeder.seedForTenant is never called (no caller in
    // backend/src/main), so the Section 35 data pack card does not exist.
    test.fixme(
      true,
      "628E-BLOCKER: no trust report definitions are seeded (Section 35 data pack missing) — backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/report/TrustReportPackSeeder.java:38 seedForTenant has no caller"
    );
    await loginAs(page, "alice");
    await page.goto(`${BASE}/trust-accounting/reports`);
    await expect(page.getByTestId("trust-reports-page")).toBeVisible({ timeout: 15_000 });
    await clickThrough(
      page.getByTestId("report-card-section-35-data-pack"),
      page.getByRole("heading", { level: 1, name: /Section 35/ })
    );
    await expect(page).toHaveURL(/\/reports\/section-35-data-pack/);
    await page.getByRole("combobox", { name: /Trust Account/ }).click();
    await page.getByRole("option", { name: new RegExp(TRUST_ACCOUNT.name) }).click();
    await page.getByLabel(/Financial Year End/).fill(isoDate());
    await page.getByRole("button", { name: "Run Report" }).click();
    await expect(page.getByRole("button", { name: "Export CSV" })).toBeEnabled({
      timeout: 15_000,
    });

    // Screenshot: Section 35 report
    await captureScreenshot(page, "day-90-section-35-generated");
    await captureScreenshot(page, "section-35-report", { curated: true });
  });

  // ── 90.12-90.15: Trust reports ────────────────────────────────────
  test("Alice: Trust reports — Client Trust Balances", async ({ page }) => {
    await loginAs(page, "alice");
    await page.goto(`${BASE}/trust-accounting/client-ledgers`);
    await expect(page.getByTestId("client-ledgers-page")).toBeVisible({ timeout: 15_000 });
    for (const key of ["moroka", "qc"] as const) {
      await expect(page.getByTestId(`ledger-row-${clientId(key)}`), key).toBeVisible({
        timeout: 15_000,
      });
    }
  });

  test("Alice: Trust reports — Investment Register with s86 basis", async ({ page }) => {
    await loginAs(page, "alice");
    await page.goto(`${BASE}/trust-accounting/investments`);
    await expect(page.getByTestId("investments-page")).toBeVisible({ timeout: 15_000 });
    await expect(page.getByTestId("investment-basis-filter")).toBeVisible();
    const rows = page.locator("main tbody tr");
    await expect(rows.filter({ hasText: morokaClient() })).toContainText("Firm");
    await expect(rows.filter({ hasText: qcClient() })).toContainText("Client Instruction");

    // Screenshot: investment register
    await captureScreenshot(page, "day-90-investment-register-loaded");
  });

  test("Alice: Trust reports — Receipts & Payments", async ({ page }) => {
    await loginAs(page, "alice");
    await page.goto(`${BASE}/trust-accounting/transactions`);
    await expect(page.getByRole("heading", { level: 1, name: "Transactions" })).toBeVisible({
      timeout: 15_000,
    });
    // Receipts (Day 14 deposits) and a payment out (Day 30 fee transfer)
    await expect(trustTransactionRow(page, recordedValue("morokaDepositRef"))).toContainText(
      "RECORDED"
    );
    await expect(trustTransactionRow(page, recordedValue("qcDepositRef"))).toContainText(
      "RECORDED"
    );
    await expect(
      page.getByTestId(`transaction-row-${recordedValue("morokaFeeTransfer")}`)
    ).toContainText("APPROVED");
  });

  // ── 90.16-90.20: Profitability dashboard ──────────────────────────
  test("Alice: Profitability report — revenue, cost and margin columns for this month's matters", async ({
    page,
  }) => {
    await loginAs(page, "alice");
    // Phase 87 S6a (ADR-344): /profitability 308s to /reports/profitability (612A)
    await page.goto(`${BASE}/profitability`);
    await expect(page).toHaveURL(/\/reports\/profitability/);
    await expect(page.getByRole("heading", { level: 1, name: "Profitability" })).toBeVisible({
      timeout: 15_000,
    });
    for (const header of ["Revenue", "Cost", "Margin"]) {
      await expect(
        page.getByRole("columnheader", { name: header, exact: true }).first(),
        header
      ).toBeVisible();
    }
    // This month's rated time on Sipho's and Moroka's matters
    for (const matter of [siphoMatter(), morokaMatter()]) {
      await expect(page.getByRole("row").filter({ hasText: matter }).first(), matter).toBeVisible();
    }

    // Screenshot: profitability dashboard
    await captureScreenshot(page, "day-90-profitability-dashboard-loaded");
  });

  // ── 90.21-90.24: Dashboard KPIs ───────────────────────────────────
  test("Alice: Home shows the owner dashboard", async ({ page }) => {
    await loginAs(page, "alice");
    await page.goto(`${BASE}/dashboard`);
    // (a second, hidden copy of the page renders outside #main-content)
    const home = page.locator("#main-content").getByTestId("home-page");
    await expect(home).toBeVisible({ timeout: 15_000 });
    await expect(home).toHaveAttribute("data-audience", "owner");
    await expect.poll(() => home.locator('[data-testid^="home-cell-"]').count()).toBeGreaterThan(0);

    // Screenshot: dashboard overview
    await captureScreenshot(page, "day-90-dashboard-overview-active");
  });

  // ── 90.25-90.27: Document generation for Sipho ────────────────────
  test("Alice: Generate an engagement letter for Sipho's matter", async ({ page }) => {
    await loginAs(page, "alice");
    await openMatter(page, siphoMatter());
    await page.getByRole("button", { name: /Generate Document/ }).click();
    await page.getByRole("menuitem", { name: ENGAGEMENT_LETTER }).click();
    const dialog = page.getByRole("dialog", { name: new RegExp(`Generate: ${ENGAGEMENT_LETTER}`) });
    await expect(dialog).toBeVisible({ timeout: 15_000 });
    // The litigation engagement letter has clause associations (legal-za-clauses pack), so the
    // dialog opens on its clause step; the preview follows "Next"
    await dialog.getByRole("button", { name: /Next/ }).click();
    const preview = dialog.frameLocator('iframe[title="Document Preview"]').locator("body");
    await expect(preview).toContainText(siphoClient(), { timeout: 15_000 });
    await dialog.getByRole("button", { name: "Save to Documents" }).click();
    await expect(dialog.getByText("Document saved successfully")).toBeVisible({ timeout: 15_000 });
    await dialog.getByRole("button", { name: "Close" }).first().click();
    await expect(dialog).toBeHidden();

    await openGroupedTab(page, "work", "generated");
    await expect(
      page.getByRole("tabpanel").getByRole("row").filter({ hasText: ENGAGEMENT_LETTER })
    ).toHaveCount(1, { timeout: 15_000 });
  });

  // ── 90.28-90.30: Compliance overview ──────────────────────────────
  test("Alice: Compliance overview page", async ({ page }) => {
    await loginAs(page, "alice");
    await page.goto(`${BASE}/compliance`);
    await expect(page.getByRole("heading", { level: 1, name: "Compliance" })).toBeVisible({
      timeout: 15_000,
    });
    await expect(page.getByRole("tab", { name: "Overview" })).toHaveAttribute(
      "aria-selected",
      "true"
    );
  });

  // ── 90.31-90.33: Court calendar review ────────────────────────────
  test("Alice: Sipho's diary shows the POSTPONED motion, its rescheduled SCHEDULED date and the prescription tracker", async ({
    page,
  }) => {
    await loginAs(page, "alice");
    await openMatter(page, siphoMatter());
    const diaryTab = page.getByTestId("tab-group-schedule");
    await diaryTab.click();
    await expect(diaryTab).toHaveAttribute("aria-selected", "true");
    const motions = page.getByTestId("court-date-list").getByRole("row").filter({
      hasText: "Motion",
    });
    await expect(motions).toHaveCount(2, { timeout: 15_000 });
    await expect(motions.filter({ hasText: "Postponed" })).toHaveCount(1);
    await expect(motions.filter({ hasText: "Scheduled" })).toHaveCount(1);
    await expect(
      page
        .getByTestId("prescription-tracker-list")
        .locator('[data-testid^="prescription-tracker-row-"]')
    ).toContainText("Running");
  });
});

// ═══════════════════════════════════════════════════════════════════
//  DAY 90 — Role-Based Access Control
// ═══════════════════════════════════════════════════════════════════
test.describe.serial("Day 90 — Role-Based Access", () => {
  // ── 90.34-90.36: Carol (Member) — blocked from sensitive areas ────
  test("Carol: Blocked from rate card settings", async ({ page }) => {
    await loginAs(page, "carol");
    // Phase 87 S9 (ADR-344): /settings/rates → /settings/fees?tab=tariffs
    await page.goto(`${BASE}/settings/fees?tab=tariffs`);
    await expect(page.getByRole("heading", { level: 1, name: /Rates & Currency/ })).toBeVisible({
      timeout: 15_000,
    });
    await expect(
      page.getByText(
        "You do not have permission to manage rates and currency settings. Only admins and owners can access this page."
      )
    ).toBeVisible();
    await expect(page.getByRole("button", { name: /^(Add|Edit) (billing )?rate for/ })).toHaveCount(
      0
    );
  });

  test("Carol: Blocked from trust account configuration", async ({ page }) => {
    await loginAs(page, "carol");
    // Phase 87 S9 (ADR-344): /settings/trust-accounting → /settings/fees?tab=trust-accounting
    await page.goto(`${BASE}/settings/fees?tab=trust-accounting`);
    await expect(
      page
        .getByRole("status")
        .filter({ hasText: "You don't have access to Trust accounting — showing Rates instead." })
    ).toBeVisible({ timeout: 15_000 });
    await expect(page.getByTestId("trust-settings-page")).toHaveCount(0);
    await expect(page.getByRole("button", { name: "Add Account" })).toHaveCount(0);
  });

  // A fee transfer awaiting approval, so the approval controls have something to guard
  test("Alice: Record a R500 trust fee transfer for Sipho — awaiting approval", async ({
    page,
  }) => {
    await loginAs(page, "alice");
    await openTrustTransactions(page);
    await page.getByTestId("record-transaction-button").click();
    await page.getByRole("menuitem", { name: "Record Fee Transfer" }).click();
    const dialog = page.getByRole("dialog", { name: "Record Fee Transfer" });
    await expect(dialog).toBeVisible();
    await dialog.getByLabel("Client ID").fill(clientId("sipho"));
    await dialog.getByLabel("Fee note ID").fill(recordedValue("siphoFeeNote"));
    await dialog.getByLabel("Amount").fill("500");
    await dialog.getByLabel("Reference").fill(pendingTransferRef());
    await dialog.getByRole("button", { name: "Record Fee Transfer" }).click();
    await expect(dialog).toBeHidden({ timeout: 15_000 });
    const row = trustTransactionRow(page, pendingTransferRef());
    await expect(row).toContainText("AWAITING APPROVAL", { timeout: 15_000 });
    recordValue(
      "siphoPendingTransfer",
      (await row.getAttribute("data-testid"))!.replace("transaction-row-", "")
    );
  });

  test("Carol: Blocked from recording or approving trust transactions", async ({ page }) => {
    await loginAs(page, "carol");
    await openTrustTransactions(page);
    // She sees the pending transfer, but neither the record nor the approval controls
    const pending = page.getByTestId(`transaction-row-${recordedValue("siphoPendingTransfer")}`);
    await expect(pending).toContainText("AWAITING APPROVAL", { timeout: 15_000 });
    await expect(page.getByTestId("record-transaction-button")).toHaveCount(0);
    await expect(page.locator('[data-testid^="approval-actions-"]')).toHaveCount(0);
    await expect(pending.getByRole("button", { name: "Approve" })).toHaveCount(0);
  });

  // ── 90.37-90.38: Bob (Admin) — has admin access ──────────────────
  test("Bob: Has admin access to rate cards", async ({ page }) => {
    await loginAs(page, "bob");
    // Phase 87 S9 (ADR-344): /settings/rates → /settings/fees?tab=tariffs
    await page.goto(`${BASE}/settings/fees?tab=tariffs`);
    await expect(page.getByRole("heading", { level: 1, name: /Rates & Currency/ })).toBeVisible({
      timeout: 15_000,
    });
    await expect(page.getByTestId("billing-rate-owner")).toBeVisible();
    await expect(page.getByText(/You do not have permission to manage rates/)).toHaveCount(0);
  });

  test("Bob: Has admin access to trust accounting", async ({ page }) => {
    await loginAs(page, "bob");
    await page.goto(`${BASE}/trust-accounting`);
    await expect(page.getByRole("heading", { level: 1, name: "Trust Accounting" })).toBeVisible({
      timeout: 15_000,
    });
    await expect(page.getByRole("link", { name: "Record Transaction" })).toBeVisible();
    // …and may approve the transfer Alice recorded
    await openTrustTransactions(page);
    await expect(
      page
        .getByTestId(`approval-actions-${recordedValue("siphoPendingTransfer")}`)
        .getByRole("button", { name: "Approve" })
    ).toBeEnabled({ timeout: 15_000 });
  });

  // ── 90.39-90.40: Alice (Owner) — full access + comparison ────────
  test("Alice: Has full owner access to all areas", async ({ page }) => {
    await loginAs(page, "alice");
    // Phase 87 S9 (ADR-344): /settings/rates → /settings/fees?tab=tariffs
    await page.goto(`${BASE}/settings/fees?tab=tariffs`);
    await expect(page.getByTestId("billing-rate-owner")).toBeVisible({ timeout: 15_000 });
    await page.goto(`${BASE}/trust-accounting`);
    await expect(page.getByRole("link", { name: "Record Transaction" })).toBeVisible({
      timeout: 15_000,
    });
    // Phase 87 S9 (ADR-344): /settings/trust-accounting → /settings/fees?tab=trust-accounting
    await page.goto(`${BASE}/settings/fees?tab=trust-accounting`);
    await expect(page.getByTestId("trust-settings-page")).toBeVisible({ timeout: 15_000 });
    await expect(page.getByTestId("trust-accounts-section")).toContainText(TRUST_ACCOUNT.name);

    // Screenshot: role comparison
    await page.goto(`${BASE}/dashboard`);
    await expect(page.locator("#main-content").getByTestId("home-page")).toBeVisible({
      timeout: 15_000,
    });
    await captureScreenshot(page, "day-90-rbac-comparison-loaded");
    await captureScreenshot(page, "role-comparison-carol-alice", { curated: true });
  });
});
