/**
 * Day 75 + Day 85 — Phase 67: Matter closure (happy path + override)
 *
 * Exercises:
 *   - Day 75 (Epic 489): Thandi closes a clean matter (all 9 gates pass,
 *     trust balance = R0). Reason = CONCLUDED, generateClosureLetter = true.
 *     Verify matter → CLOSED, closure letter created, retention policy
 *     inserted with end_date = today + 5 years (ADR-249).
 *   - Day 85 (Epic 490): Matter with failing trust-balance gate:
 *       Pass A — Bob (Admin, no OVERRIDE_MATTER_CLOSURE): dialog advances to
 *                step 3 "Cannot close", override toggle hidden; API returns 409.
 *       Pass B — Thandi (Owner, has OVERRIDE_MATTER_CLOSURE): override toggle
 *                visible, enter justification ≥20 chars, close → CLOSED,
 *                audit event records override_used=true + justification verbatim.
 *
 * Prerequisites:
 *   1. E2E stack running: bash compose/scripts/e2e-up.sh (matter_closure module enabled —
 *      a seed default for legal-za)
 *   2. Day 5 + Day 30 + Day 45 flows completed in this run — Sipho's depth-II matter holds
 *      an unresolved trust balance (Day 5 deposit R5,000 − deeds payment R1,250), which is
 *      the failing-gates matter for Day 85
 *
 * Standalone: no seeded or lifecycle matter is "clean" (all gates green needs a final bill
 * SENT, no open tasks, no trust balance), so Day 75 prepares one through the UI: a new
 * run-scoped client "Apex Holdings <run>", a template-free matter for it, one approved
 * counsel-fee disbursement, and a fee note billing it that is approved and sent.
 *
 * Run:
 *   PLAYWRIGHT_BASE_URL=http://localhost:3001 pnpm test:e2e:legal-depth-ii
 */
import { test, expect, type Page } from "@playwright/test";
import { loginAs } from "../../fixtures/auth";
import { captureScreenshot } from "../../helpers/screenshot";
import {
  attemptRunId,
  BASE,
  clickThrough,
  createClient,
  escapeRegExp,
  isoDate,
  openClient,
  openClosureDialog,
  openDisbursementList,
  openGroupedTab,
  openMatter,
  selectNativeOption,
  siphoMatterName,
  startClientOnboarding,
} from "../../helpers/legal-depth-ii";

// Scoped to the depth-II run and the attempt (a CI retry of this group prepares fresh records)
const apexClient = () => `Apex Holdings ${attemptRunId()}`;
const cleanMatterName = () => `Apex Holdings — Shareholders' Agreement Review ${attemptRunId()}`;
const counselFeeDescription = () =>
  `Counsel's opinion on shareholders' agreement ${attemptRunId()}`;
const OVERRIDE_JUSTIFICATION =
  "Trust remainder of R12,000 earmarked for final Section 35 payout pending master approval — closing under owner override";

const CLOSURE_GATES = [
  "TRUST_BALANCE_ZERO",
  "ALL_DISBURSEMENTS_APPROVED",
  "ALL_DISBURSEMENTS_SETTLED",
  "FINAL_BILL_ISSUED",
  "NO_OPEN_COURT_DATES",
  "NO_OPEN_PRESCRIPTIONS",
  "ALL_TASKS_RESOLVED",
  "ALL_INFO_REQUESTS_CLOSED",
  "ALL_ACCEPTANCE_REQUESTS_FINAL",
];

async function openAuditLogFilteredBy(page: Page, eventType: string) {
  await page.goto(`${BASE}/settings/audit-log`);
  await page.waitForLoadState("networkidle");
  const filter = page.locator("#audit-filter-event-type");
  await expect(filter).toBeVisible({ timeout: 15_000 });
  await filter.fill(eventType);
  await filter.press("Tab"); // the filter commits on blur
  await expect(page).toHaveURL(new RegExp(`eventType=${escapeRegExp(eventType)}`));
  const row = page
    .locator('[data-testid^="audit-row-"]:not([data-testid^="audit-row-details-"])')
    .filter({ hasText: eventType })
    .first();
  await expect(row).toBeVisible({ timeout: 15_000 });
  return row;
}

test.describe.serial("Day 75-85 — Matter Closure (Phase 67)", () => {
  // ── 75 pre-state: a clean matter (final bill sent, nothing open) ──
  test("[setup] Alice (Thandi): Create client Apex Holdings and start FICA onboarding", async ({
    page,
  }) => {
    await loginAs(page, "alice");
    // Billing address + tax number: fee-note generation and sending require them
    await createClient(page, {
      name: apexClient(),
      type: "Company",
      email: `apex.holdings.${attemptRunId()}@example.com`,
      taxNumber: "9012345678",
    });
    // New matters cannot be opened for a PROSPECT client
    await startClientOnboarding(page);
  });

  test("[setup] Alice (Thandi): Open a clean commercial matter for Apex Holdings", async ({
    page,
  }) => {
    await loginAs(page, "alice");
    await page.goto(`${BASE}/projects`);
    // "New Matter" (not "New from Template"): no template → no open tasks
    await page.getByRole("button", { name: "New Matter" }).click();
    const dialog = page.getByRole("dialog", { name: "Create Matter" });
    await expect(dialog).toBeVisible();
    await dialog.getByLabel("Name", { exact: true }).fill(cleanMatterName());
    await dialog
      .getByLabel(/^Description/)
      .fill("Review of the Apex Holdings shareholders' agreement ahead of the 2026 AGM.");
    await selectNativeOption(dialog.getByLabel(/^Client/), apexClient());
    await dialog.getByLabel(/^Work Type/).fill("COMMERCIAL");
    await dialog.getByRole("button", { name: "Create Matter" }).click();
    await expect(dialog).toBeHidden({ timeout: 15_000 });

    await openMatter(page, cleanMatterName());
    await expect(page.locator("main")).toContainText(apexClient());
    await expect(page.locator("main")).toContainText("COMMERCIAL");
  });

  test("[setup] Bob: Record + approve counsel's fee disbursement on the Apex matter", async ({
    page,
  }) => {
    await loginAs(page, "bob");
    await openDisbursementList(page, cleanMatterName());
    await page.getByTestId("create-disbursement-trigger").click();
    const dialog = page.getByTestId("create-disbursement-dialog");
    await expect(dialog).toBeVisible();
    await selectNativeOption(dialog.getByLabel("Matter"), cleanMatterName());
    await selectNativeOption(dialog.getByLabel("Client"), apexClient());
    await dialog.getByLabel("Category").selectOption("COUNSEL_FEES");
    await expect(dialog.getByLabel("VAT Treatment")).toHaveValue("STANDARD_15");
    await dialog.getByLabel("Description").fill(counselFeeDescription());
    await dialog.getByTestId("disbursement-amount-input").fill("3500.00");
    await dialog.getByLabel("Incurred Date").fill(isoDate());
    await dialog.getByLabel("Supplier", { exact: true }).fill("Adv. N. Mokoena SC");
    await dialog.getByRole("button", { name: "Create Disbursement" }).click();
    await expect(dialog).toBeHidden({ timeout: 15_000 });

    const row = page
      .locator('[data-testid^="disbursement-row-"]')
      .filter({ hasText: counselFeeDescription() });
    await expect(row).toContainText("Draft", { timeout: 15_000 });
    // Ready once its detail page has rendered
    const detail = page.getByTestId("disbursement-detail");
    await clickThrough(row, detail.getByText(counselFeeDescription(), { exact: true }));
    await expect(page).toHaveURL(/\/legal\/disbursements\/[0-9a-f-]{36}$/);
    await detail.getByTestId("disbursement-submit-for-approval-button").click();
    await expect(detail.getByTestId("disbursement-approval-panel")).toBeVisible({
      timeout: 15_000,
    });
    await detail.getByTestId("disbursement-approve-button").click();
    const approve = page.getByTestId("disbursement-approve-dialog");
    await approve.getByTestId("disbursement-confirm-approve-button").click();
    await expect(approve).toBeHidden({ timeout: 15_000 });
    await expect(detail.getByText("Approved", { exact: true })).toBeVisible();
  });

  test("[setup] Alice (Thandi): Bill the Apex matter — fee note drafted, approved and sent", async ({
    page,
  }) => {
    await loginAs(page, "alice");
    await openClient(page, apexClient());
    await openGroupedTab(page, "finance", "invoices");
    const panel = page.getByRole("tabpanel");
    const newFeeNote = panel.getByRole("button", { name: "New Fee Note" });
    await expect(newFeeNote).toBeVisible({ timeout: 15_000 });
    const feeNoteLinks = () =>
      panel
        .locator('a[href*="/invoices/"]')
        .evaluateAll((els) => els.map((e) => e.getAttribute("href")!));
    const before = new Set(await feeNoteLinks());

    await newFeeNote.click();
    const generate = page.getByRole("dialog", { name: /Generate Fee Note|Select Unbilled Items/ });
    await expect(generate).toBeVisible({ timeout: 15_000 });
    await generate.getByRole("button", { name: "Fetch Unbilled Time" }).click();
    await expect(generate.getByTestId("disbursement-selection-section")).toContainText(
      counselFeeDescription(),
      { timeout: 15_000 }
    );
    await generate.getByRole("button", { name: "Validate & Create Draft" }).click();
    await generate.getByRole("button", { name: /^Create Draft/ }).click();
    await expect(generate).toBeHidden({ timeout: 15_000 });

    // The created fee note is the one link that was not listed before (not "first Draft row")
    await expect
      .poll(async () => (await feeNoteLinks()).filter((href) => !before.has(href)), {
        timeout: 15_000,
      })
      .toHaveLength(1);
    const created = (await feeNoteLinks()).find((href) => !before.has(href))!;
    expect(created).toMatch(/\/invoices\/[0-9a-f-]{36}$/);
    const createdRow = panel.getByRole("row").filter({ has: page.locator(`a[href="${created}"]`) });
    await expect(createdRow).toContainText("Draft");
    await clickThrough(
      createdRow.locator(`a[href="${created}"]`).first(),
      page.locator("main").getByText(counselFeeDescription()).first()
    );
    await expect(page).toHaveURL(new RegExp(`${escapeRegExp(created)}$`));
    await expect(page.locator("main")).toContainText(cleanMatterName());

    await page.getByRole("button", { name: "Approve" }).click();
    await expect(page.getByRole("button", { name: "Send Fee Note" })).toBeVisible({
      timeout: 15_000,
    });
    await page.getByRole("button", { name: "Send Fee Note" }).click();
    await expect(page.locator("main").getByText("Sent", { exact: true })).toBeVisible({
      timeout: 15_000,
    });
  });

  // ── 75.2-75.11: Happy-path closure (all gates green) ──────────────
  test("Alice (Thandi): Open Close Matter dialog on clean matter", async ({ page }) => {
    await loginAs(page, "alice");
    await openMatter(page, cleanMatterName());
    await expect(page.getByTestId("close-matter-btn")).toBeVisible();
    const dialog = await openClosureDialog(page);
    await expect(dialog.getByTestId("matter-closure-step-1")).toBeVisible();
    await page.waitForLoadState("networkidle");
    await captureScreenshot(page, "day-75-closure-dialog-opened");
  });

  test("Alice: Step 1 gate report renders all-green (happy path)", async ({ page }) => {
    await loginAs(page, "alice");
    await openMatter(page, cleanMatterName());
    const dialog = await openClosureDialog(page);

    const report = dialog.getByTestId("matter-closure-report");
    await expect(report.locator("[data-testid^='matter-closure-gate-row-']")).toHaveCount(9);
    for (const gate of CLOSURE_GATES) {
      await expect(report.getByTestId(`matter-closure-gate-row-${gate}`), gate).toHaveAttribute(
        "data-passed",
        "true"
      );
      await expect(report.getByTestId(`matter-closure-gate-pass-${gate}`)).toBeVisible();
    }
    await expect(report.locator("[data-testid^='matter-closure-gate-fail-']")).toHaveCount(0);
    await expect(report).toContainText("Matter trust balance is R0.00.");
    await expect(report).toContainText("Final bill issued with no unbilled items.");

    await page.waitForLoadState("networkidle");
    await captureScreenshot(page, "day-75-closure-gate-report-all-green");
    await captureScreenshot(page, "matter-closure-dialog-all-green", { curated: true });
  });

  test("Alice: Advance to Step 2 + confirm close with CONCLUDED reason", async ({ page }) => {
    await loginAs(page, "alice");
    await openMatter(page, cleanMatterName());
    const dialog = await openClosureDialog(page);
    await dialog.getByTestId("matter-closure-next-btn").click();

    const step2 = dialog.getByTestId("matter-closure-step-2");
    await expect(step2).toBeVisible();
    // 75.5-75.6: defaults — reason CONCLUDED, closure letter ticked; no override offered
    await expect(step2.getByTestId("matter-closure-reason-select")).toContainText("Concluded");
    await expect(step2.getByTestId("matter-closure-generate-letter-checkbox")).toHaveAttribute(
      "data-state",
      "checked"
    );
    await expect(step2.getByTestId("matter-closure-override-toggle")).toHaveCount(0);
    await step2.getByTestId("matter-closure-confirm-close-btn").click();

    // 75.7-75.8: success toast, matter CLOSED
    await expect(page.getByText("Matter closed")).toBeVisible({ timeout: 30_000 });
    await expect(dialog).toBeHidden();
    await expect(page.locator("main").getByText("Closed", { exact: true })).toBeVisible({
      timeout: 15_000,
    });
    await expect(page.getByRole("button", { name: /Reopen Matter/ })).toBeVisible();
    await page.waitForLoadState("networkidle");
    await captureScreenshot(page, "day-75-matter-closed-success");

    // 75.10 (part 1): closure starts the retention clock (ADR-249). The scheduled deletion
    // date is checked by the 75.10 fixme below.
    const retention = page.getByTestId("retention-card");
    await expect(retention).toContainText(/clock started on/i, { timeout: 15_000 });

    // 75.9: closure letter generated and attached to the matter
    await openGroupedTab(page, "work", "generated");
    const letter = page.getByRole("row", { name: /Matter Closure Letter/ });
    await expect(letter).toBeVisible({ timeout: 15_000 });
    await page.waitForLoadState("networkidle");
    await captureScreenshot(page, "closure-letter-preview", { curated: true });
  });

  // ── 75.10 (part 2): retention card shows the scheduled deletion date ──
  // FIXME(depth-ii:retention-period-unconfigurable) PRODUCT BUG (reproduced): the closed
  // matter's retention card renders data-state="unconfigured" ("Your firm's matter-retention
  // period isn't configured yet") instead of the deletion date (closure + 5 years). On the
  // freshly provisioned tenant org_settings.legal_matter_retention_years is NULL — the column
  // DEFAULT 5 (V1__baseline.sql) is bypassed by the entity insert — and nothing can set it:
  // DataProtectionSettings.setLegalMatterRetentionYears has no callers (no settings endpoint),
  // and the card's "Configure retention period →" link opens Settings → Data Protection, which
  // has no retention-years field. Closure itself uses the 5-year fallback
  // (getEffectiveLegalMatterRetentionYears) while GET /api/projects/{id} reports
  // retentionEndsOn = null, so the date is never shown.
  test.fixme("Alice: Closed matter's retention card shows the deletion date (closure + 5 years)", async ({
    page,
  }) => {
    await loginAs(page, "alice");
    await openMatter(page, cleanMatterName(), { closed: true });
    const retention = page.getByTestId("retention-card");
    await expect(retention).toHaveAttribute("data-state", "active", { timeout: 15_000 });
    await expect(retention).toContainText(/permanently deleted on/);
    await expect(retention).toContainText(String(new Date().getFullYear() + 5));
  });

  // ── 85.5-85.9: Admin (Bob) — override not available, step 3 ───────
  // FIXME(depth-ii:admin-offered-closure-override) PRODUCT BUG (reproduced): Bob (Admin) is
  // offered the override. The backend correctly
  // withholds OVERRIDE_MATTER_CLOSURE from Admin (GET /api/me/capabilities for Bob has no
  // OVERRIDE_MATTER_CLOSURE), but the frontend `hasCapability` (lib/capabilities.tsx) returns
  // true for every capability when isAdmin || isOwner, so MatterClosureDialog's `canOverride`
  // is true: Continue goes to Step 2 with the "Override failing gates" toggle instead of
  // Step 3 "Cannot close". Submitting the override then fails with 403
  // "Insufficient permissions for this operation".
  test.fixme("Bob (Admin): Close Matter on failing-gates matter routes to Step 3 (no override)", async ({
    page,
  }) => {
    await loginAs(page, "bob");
    await openMatter(page, siphoMatterName());
    const dialog = await openClosureDialog(page);

    // 85.6: trust-balance gate fails (Day 5 deposit − deeds payment left funds in trust)
    await expect(dialog.getByTestId("matter-closure-gate-row-TRUST_BALANCE_ZERO")).toHaveAttribute(
      "data-passed",
      "false"
    );
    await dialog.getByTestId("matter-closure-next-btn").click();

    // 85.7-85.8: no OVERRIDE_MATTER_CLOSURE → Step 3, override toggle absent
    const step3 = dialog.getByTestId("matter-closure-step-3");
    await expect(step3).toBeVisible();
    await expect(step3).toContainText("Cannot close — resolve gates");
    await expect(dialog.getByTestId("matter-closure-override-toggle")).toHaveCount(0);
    await page.waitForLoadState("networkidle");
    await captureScreenshot(page, "day-85-admin-cannot-close-step-3");
    await captureScreenshot(page, "matter-closure-dialog-gate-report-failing", { curated: true });
  });

  // ── 85.10-85.17: Owner (Thandi/alice) — override flow ─────────────
  test("Alice (Thandi): Override close with justification ≥20 chars", async ({ page }) => {
    await loginAs(page, "alice");
    await openMatter(page, siphoMatterName());
    const dialog = await openClosureDialog(page);

    // 85.11: the same failing trust-balance gate
    const trustGate = dialog.getByTestId("matter-closure-gate-row-TRUST_BALANCE_ZERO");
    await expect(trustGate).toHaveAttribute("data-passed", "false");
    await expect(trustGate).toContainText(/Matter trust balance is R[1-9]/);
    await dialog.getByTestId("matter-closure-next-btn").click();

    // 85.12-85.14: Owner + failing gates → Step 2 with the override section
    const step2 = dialog.getByTestId("matter-closure-step-2");
    await expect(step2).toBeVisible();
    await step2.getByTestId("matter-closure-override-toggle").click();
    const justification = step2.getByTestId("matter-closure-override-justification-input");
    await expect(justification).toBeVisible();
    await justification.fill(OVERRIDE_JUSTIFICATION);
    await page.waitForLoadState("networkidle");
    await captureScreenshot(page, "day-85-override-justification-entered");

    // 85.15-85.16: close → success toast, matter CLOSED
    await step2.getByTestId("matter-closure-confirm-close-btn").click();
    await expect(page.getByText("Matter closed")).toBeVisible({ timeout: 30_000 });
    await expect(dialog).toBeHidden();
    await expect(page.locator("main").getByText("Closed", { exact: true })).toBeVisible({
      timeout: 15_000,
    });
    await page.waitForLoadState("networkidle");
    await captureScreenshot(page, "day-85-matter-closed-via-override");
  });

  test("Alice: Verify override audit event captures override_used=true + justification", async ({
    page,
  }) => {
    await loginAs(page, "alice");

    // The closure event records override_used = true
    const closed = await openAuditLogFilteredBy(page, "matter_closure.closed");
    await closed.getByRole("button", { name: "Expand details" }).click();
    const closedDetails = page.locator('[data-testid^="audit-row-details-"]').first();
    await expect(closedDetails).toContainText(/override_used:\s*true/);

    // The dedicated override event carries the justification verbatim
    const override = await openAuditLogFilteredBy(page, "matter.closure.override_used");
    await expect(override).toContainText("Matter Closure Override Used");
    await override.getByRole("button", { name: "Expand details" }).click();
    const overrideDetails = page.locator('[data-testid^="audit-row-details-"]').first();
    await expect(overrideDetails).toContainText("justification");
    await expect(overrideDetails).toContainText(OVERRIDE_JUSTIFICATION);

    await page.waitForLoadState("networkidle");
    await captureScreenshot(page, "day-85-audit-log-override-event");
  });
});
