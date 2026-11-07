/**
 * Day 30 + Day 45 — Phase 67: Statement of Account + Disbursement Write-off
 *
 * Exercises:
 *   - Day 30 (Epic 487): generate a Statement of Account on the oldest active matter
 *     (Sipho's litigation) for period = matter-opened to today. Preview HTML
 *     shows fees + disbursements (from Day 5) + trust activity + summary. Save
 *     as GeneratedDocument, download PDF.
 *   - Day 45 (Epic 491): write off one approved-UNBILLED disbursement with reason.
 *     Verify status transition, audit event, its removal from the next statement (its
 *     disbursements and the unbilled-WIP line) and its exclusion from the next fee-note draft.
 *
 * Summary basis (G89-F16 / founder D9, #1748; the WIP row ships with #1749): "Closing balance
 * owing" counts issued fee notes only. Sipho's matter has none in this group, so nothing is owing
 * (R0,00); the unbilled fees and disbursements (excl. VAT) are shown separately as "Unbilled work
 * in progress (not yet billed)" and never counted as owing.
 *
 * Prerequisites:
 *   1. E2E stack running: bash compose/scripts/e2e-up.sh
 *   2. Day 5 completed in this run (Sipho's depth-II client + matter with an APPROVED,
 *      UNBILLED sheriff-fee disbursement and trust deposit/payment)
 *   3. A billing rate and time on Sipho's matter (for SoA fee content) — both set up by
 *      this spec's [setup] steps through the UI.
 *
 * Re-run safety: a CI retry re-runs this serial group for the same run id, so the
 * time-entry description is attempt-scoped, the statement saved here is located by
 * diffing the statement rows before/after generation (not by list order), and
 * the first-attempt-only "no statements yet" check is skipped on a retry.
 *
 * Run:
 *   PLAYWRIGHT_BASE_URL=http://localhost:3001 pnpm test:e2e:legal-depth-ii
 */
import { test, expect, type Locator, type Page } from "@playwright/test";
import { loginAs } from "../../fixtures/auth";
import { captureScreenshot } from "../../helpers/screenshot";
import {
  attemptRunId,
  BASE,
  clickThrough,
  currentDepthIiRun,
  escapeRegExp,
  isoDate,
  openClient,
  openDisbursementList,
  openMatter,
  openGroupedTab,
  selectNativeOption,
  siphoClientName,
  siphoMatterName,
} from "../../helpers/legal-depth-ii";

const SHERIFF_FEE_DESCRIPTION = "Sheriff fee — service of process";
/** Attempt-scoped so a retried group's statement is checked against its own time entry. */
const timeDescription = () =>
  `Drafted and served letter of demand on the Road Accident Fund (${attemptRunId()})`;
const WRITE_OFF_REASON =
  "Client dispute — unable to recover from third party, firm absorbing the cost";

async function openStatementsTab(page: Page) {
  await openMatter(page, siphoMatterName());
  await openGroupedTab(page, "finance", "statements");
  const tab = page.getByTestId("project-statements-tab");
  await expect(tab).toBeVisible();
  await expect(tab.getByText(/Loading statements/)).toBeHidden({ timeout: 15_000 });
  return tab;
}

async function generateStatement(page: Page, tab: Locator): Promise<Locator> {
  await tab.getByTestId("open-generate-statement-btn").click();
  const dialog = page.getByTestId("statement-of-account-dialog");
  await expect(dialog).toBeVisible();
  // Period = matter-opened (approximated as 90 days ago) to today
  await dialog.getByTestId("statement-period-start-input").fill(isoDate(-90));
  await dialog.getByTestId("statement-period-end-input").fill(isoDate());
  await dialog.getByTestId("statement-generate-btn").click();
  await expect(dialog.getByTestId("statement-download-btn")).toBeVisible({ timeout: 30_000 });
  return dialog;
}

/** Whitespace-tolerant pattern for a printed rand amount, from its text ("R 1 500,00"). */
function amountPattern(printed: string): RegExp {
  const digits = printed.replace(/\D/g, "");
  const grouped = digits.slice(0, -2).split("").join("[\\s\\u00a0\\u202f]?");
  return new RegExp(`R\\s?${grouped},${digits.slice(-2)}`);
}

/** The amount printed after `label` in the preview text, as a pattern (fails if absent). */
function printedAmount(text: string, label: string): RegExp {
  const match = text.match(
    new RegExp(`${escapeRegExp(label)}:\\s*R\\s?([\\d\\s\\u00a0\\u202f]+,\\d{2})`)
  );
  expect(match, `"${label}" amount in the statement`).not.toBeNull();
  return amountPattern(match![1]);
}

/**
 * What the statement must show at this point of the group. First attempt: 2h at R2 500 = R5 000
 * fees excl. VAT; the Day 5 sheriff fee (R750) + deeds-office fee (R1 250) until the Day 45
 * write-off takes the sheriff fee out. A retried group adds time, so its WIP is only non-zero.
 */
interface ExpectedStatement {
  sheriffFeeListed: boolean;
  /** Disbursements table total, e.g. /R\s?2\s?000,00/. */
  disbursementsTotal: RegExp;
  /** First-attempt WIP, e.g. /R\s?7\s?000,00/. */
  unbilledWip: RegExp;
}

const BEFORE_WRITE_OFF: ExpectedStatement = {
  sheriffFeeListed: true,
  disbursementsTotal: /R\s?2\s?000,00/,
  unbilledWip: /R\s?7\s?000,00/,
};

const AFTER_WRITE_OFF: ExpectedStatement = {
  sheriffFeeListed: false,
  disbursementsTotal: /R\s?1\s?250,00/,
  unbilledWip: /R\s?6\s?250,00/,
};

/**
 * Fees + disbursements (Day 5) + trust activity (Day 5) + summary. Returns the printed closing
 * balance owing and trust balance held, as patterns for the saved statement's row.
 */
async function verifyStatementPreview(
  dialog: Locator,
  expected: ExpectedStatement = BEFORE_WRITE_OFF
): Promise<{ closingBalanceOwing: RegExp; trustBalanceHeld: RegExp }> {
  const preview = dialog
    .getByTestId("statement-preview-container")
    .frameLocator('iframe[title="Document Preview"]')
    .locator("body");
  await expect(preview).toContainText("Statement of Account", { timeout: 15_000 });
  await expect(preview).toContainText(siphoMatterName());
  await expect(preview).toContainText(siphoClientName());
  // Fees: the 2h letter-of-demand entry, billed at Alice's (non-zero) rate
  await expect(preview).toContainText("Professional Fees");
  const feeRow = preview.locator("tr", { hasText: timeDescription() });
  await expect(feeRow).toHaveCount(1);
  await expect(feeRow.locator("td")).toHaveText([
    /\d/, // date
    /\S/, // fee earner
    timeDescription(),
    "2",
    /^R\s?[1-9][\d\s]*,\d{2}$/, // rate
    /^R\s?[1-9][\d\s]*,\d{2}$/, // amount
  ]);
  // Disbursements: the Day 5 sheriff fee (R750) + trust-linked deeds-office fee (R1,250)
  // (scenario 30.4; 621D S1); after the Day 45 write-off only the deeds-office fee (#1748: the
  // statement lists approved, not-written-off disbursements)
  if (expected.sheriffFeeListed) {
    await expect(preview).toContainText(SHERIFF_FEE_DESCRIPTION);
  } else {
    await expect(preview).not.toContainText(SHERIFF_FEE_DESCRIPTION);
  }
  await expect(preview).toContainText(
    new RegExp(`Total disbursements:\\s*${expected.disbursementsTotal.source}`)
  );
  // Trust activity: Sipho's client trust ledger (client-scoped) with the Day 5 deposit + payment
  const runId = currentDepthIiRun();
  await expect(preview).toContainText("Trust Activity");
  await expect(preview).toContainText(new RegExp(`Deposits[\\s\\S]*DEP/${runId}`));
  await expect(preview).toContainText(new RegExp(`Payments[\\s\\S]*PAY/DEEDS/${runId}`));
  await expect(preview).toContainText(/Trust balance held:\s*R\s?[1-9]/);
  // Summary (fee-note basis, D9): no fee note issued yet, so nothing owing; the unbilled fees +
  // disbursements are the work in progress, shown but never counted as owing
  await expect(preview).toContainText("Summary");
  await expect(preview).toContainText(/Closing balance owing:\s*R\s?0,00/);
  const wip = test.info().retry === 0 ? expected.unbilledWip : /R\s?[1-9][\d\s\u00a0\u202f]*,\d{2}/;
  await expect(preview).toContainText(
    new RegExp(`Unbilled work in progress \\(not yet billed\\):\\s*${wip.source}`)
  );
  // The printed amounts, for the saved statement's row
  const text = (await preview.textContent()) ?? "";
  return {
    closingBalanceOwing: printedAmount(text, "Closing balance owing"),
    trustBalanceHeld: printedAmount(text, "Trust balance held"),
  };
}

async function openSheriffFeeDetail(page: Page) {
  await openDisbursementList(page, siphoMatterName());
  const row = page
    .locator('[data-testid^="disbursement-row-"]')
    .filter({ hasText: siphoMatterName() })
    .filter({ hasText: SHERIFF_FEE_DESCRIPTION })
    .first();
  await expect(row).toContainText("Approved", { timeout: 15_000 });
  const detail = page.getByTestId("disbursement-detail");
  // Ready once its detail page has rendered
  await clickThrough(row, detail.getByText(SHERIFF_FEE_DESCRIPTION, { exact: true }));
  return detail;
}

/** data-testid of the statement row saved by "Save Statement" (same worker within the group). */
let savedStatementRowId = "";

test.describe.serial("Day 30-45 — Statement of Account + Write-off (Phase 67)", () => {
  // ── Day 0 prerequisite: a billing rate for Alice on Sipho's client ──
  // A freshly seeded tenant has no billing rates (legal-lifecycle Day 0 sets org rates), so
  // time would be billed at R0. A client-level override keeps the setup scoped to this run.
  test("[setup] Alice (Thandi): Billing rate override for Alice on Sipho's client", async ({
    page,
  }) => {
    await loginAs(page, "alice");
    await openClient(page, siphoClientName());
    await openGroupedTab(page, "finance", "rates");
    const aliceRate = page
      .getByRole("tabpanel")
      .getByRole("row")
      .filter({ hasText: "Alice Owner" });
    // A retried group keeps the first attempt's override (a second one would overlap it)
    if (test.info().retry > 0 && (await aliceRate.count()) > 0) return;
    await page.getByRole("tabpanel").getByRole("button", { name: "Add Override" }).first().click();
    // Phase 88 628A (ADR-357): label → "Add Client Rate Override"
    const dialog = page.getByRole("dialog", { name: "Add Client Rate Override" });
    await expect(dialog).toBeVisible();
    await selectNativeOption(dialog.getByLabel("Member"), "Alice Owner");
    await dialog.getByLabel("Hourly Rate").fill("2500");
    await dialog.getByLabel("Effective From").fill(isoDate(-90));
    await dialog.getByRole("button", { name: "Create Override" }).click();
    await expect(dialog).toBeHidden({ timeout: 15_000 });
    await expect(aliceRate).toContainText(/2[\s\u00a0\u202f]?500,00/, { timeout: 15_000 });
  });

  // ── Day 7 prerequisite: fee-earning time on Sipho's matter ────────
  test("[setup] Alice (Thandi): Log 2h on Sipho's matter — letter of demand", async ({ page }) => {
    await loginAs(page, "alice");
    await openMatter(page, siphoMatterName());
    await openGroupedTab(page, "work", "tasks");

    const taskRow = page.getByRole("row").filter({ hasText: "Letter of demand" }).first();
    await expect(taskRow).toBeVisible({ timeout: 15_000 });
    await taskRow.getByRole("button", { name: "Log Time" }).click();

    const dialog = page.getByRole("dialog", { name: "Log Time" });
    await expect(dialog).toBeVisible();
    await dialog.locator("#time-hours").fill("2");
    await dialog.locator("#time-minutes").fill("0");
    await dialog.locator("#time-date").fill(isoDate());
    await dialog.locator("#time-description").fill(timeDescription());
    await expect(dialog.locator("#time-billable")).toBeChecked();
    await dialog.getByRole("button", { name: "Log Time" }).click();
    await expect(dialog).toBeHidden({ timeout: 15_000 });

    // Finance → Time summarises the entry by task and by member (a retried group adds to
    // the first attempt's 2h; the entry itself is checked line-by-line in the statement)
    await openGroupedTab(page, "finance", "time");
    const timePanel = page.getByRole("tabpanel");
    const hours = test.info().retry === 0 ? "2h" : "\\d+h";
    await expect(timePanel).toContainText(new RegExp(`Letter of demand\\s*${hours}`), {
      timeout: 15_000,
    });
    await expect(timePanel).toContainText(new RegExp(`Alice Owner\\s*${hours}`));
  });

  // ── 30.1-30.7: Generate Statement of Account on Sipho's matter ────
  test("Alice (Thandi): Open Statements tab on oldest matter", async ({ page }) => {
    await loginAs(page, "alice");
    const tab = await openStatementsTab(page);
    await expect(tab.getByRole("heading", { name: "Statements of Account" })).toBeVisible();
    await expect(tab.getByTestId("open-generate-statement-btn")).toBeVisible();
    // A retried group finds the first attempt's statement already listed
    if (test.info().retry === 0) {
      await expect(tab.getByTestId("statements-empty")).toBeVisible();
      await page.waitForLoadState("networkidle");
      await captureScreenshot(page, "day-30-matter-statements-tab");
    }
  });

  // Regression (was FIXME depth-ii:soa-preview-unbounded-scale, G-C4): the preview stays inside
  // the dialog (626F A4PreviewWrapper contentRect + contain:inline-size, min-w-0 form).
  test("Alice: Generate Statement of Account with HTML preview", async ({ page }) => {
    await loginAs(page, "alice");
    const tab = await openStatementsTab(page);
    const dialog = await generateStatement(page, tab);
    await verifyStatementPreview(dialog);

    // The footer actions must stay inside the dialog
    const dialogBox = await dialog.boundingBox();
    const downloadBox = await dialog.getByTestId("statement-download-btn").boundingBox();
    expect(downloadBox!.x + downloadBox!.width).toBeLessThanOrEqual(
      dialogBox!.x + dialogBox!.width
    );

    // Not networkidle: the sandboxed srcdoc preview iframe can keep it from settling (harness
    // hang, PR #1749 e2e gate). The preview content is asserted above; the dialog is ready once
    // its download action is.
    await expect(dialog.getByTestId("statement-download-btn")).toBeEnabled();
    await captureScreenshot(page, "day-30-statement-of-account-preview");
    await captureScreenshot(page, "statement-of-account-preview", { curated: true });
  });

  test("Alice: Save Statement — listed on the matter, PDF downloadable from the Documents tab", async ({
    page,
  }) => {
    await loginAs(page, "alice");
    const tab = await openStatementsTab(page);
    const rows = tab.locator('[data-testid^="statement-row-"]');
    const rowIds = () => rows.evaluateAll((els) => els.map((e) => e.getAttribute("data-testid")!));
    const before = new Set(await rowIds());

    // "Preview & Save" generates the statement, saves it to the matter and renders the preview
    const dialog = await generateStatement(page, tab);
    const printed = await verifyStatementPreview(dialog);
    await expect(page.getByText("Statement generated")).toBeVisible();
    // Dismiss with Escape (the dialog footer sits below the fold at the default 1280×720 viewport)
    await page.keyboard.press("Escape");
    await expect(dialog).toBeHidden();

    // The saved statement is listed on the matter with its balances — located as the one
    // row that was not there before (independent of the API's list order)
    await expect
      .poll(async () => (await rowIds()).filter((id) => !before.has(id)), { timeout: 15_000 })
      .toHaveLength(1);
    savedStatementRowId = (await rowIds()).find((id) => !before.has(id))!;
    const row = tab.getByTestId(savedStatementRowId);
    // Columns: Generated | Closing balance owing | Trust balance held | Actions
    await expect(row.getByRole("cell").nth(1)).toHaveText(printed.closingBalanceOwing);
    await expect(row.getByRole("cell").nth(2)).toHaveText(printed.trustBalanceHeld);
    await expect(row.locator('[data-testid^="statement-download-"]')).toBeEnabled();
    await page.waitForLoadState("networkidle");
    await captureScreenshot(page, "day-30-statement-saved-list");

    // The statement PDF is also saved to the matter's documents; the Documents-tab download
    // hands the browser the S3 presigned URL, which the browser fetches.
    const fileName = new RegExp(
      `^Download statement-of-account-${escapeRegExp(
        siphoMatterName()
          .toLowerCase()
          .replace(/[^a-z0-9]+/g, "-")
      )}`
    );
    await openGroupedTab(page, "work", "documents");
    const docDownload = page
      .getByRole("tabpanel")
      .getByRole("button", { name: /^Download statement-of-account-.*\.pdf$/ })
      .first();
    await expect(docDownload).toBeVisible({ timeout: 15_000 });
    await expect(docDownload).toHaveAccessibleName(fileName);
    const [pdf] = await Promise.all([
      page.waitForResponse((r) => /\/statement-of-account-[^/?]*\.pdf\?/.test(r.url())),
      docDownload.click(),
    ]);
    expect(pdf.status()).toBe(200);
    expect(pdf.headers()["content-type"]).toContain("pdf");
  });

  // FIXME(depth-ii:e2e-s3-presigner-unreachable) E2E ENVIRONMENT LIMITATION — not a product
  // bug. The Statements-tab "Download" button calls downloadGeneratedDocumentAction
  // (settings/documents/_tabs/template-generation-actions.ts), a server action that fetches
  // GET /api/generated-documents/{id}/download with `redirect: "follow"` from inside the
  // frontend container. The backend answers 302 to the S3 presigned URL, whose host is
  // AWS_S3_PRESIGNER_ENDPOINT=http://localhost:4567 (compose/docker-compose.e2e.yml) — the
  // LocalStack port as published to the host, so it is reachable from the browser but not
  // from inside the frontend container. Reproduced: host → backend gives 302 to
  // http://localhost:4567/docteams-dev/.../statement-of-account-*.pdf; the same request from
  // the e2e-frontend container gets the 302 and then `fetch failed ECONNREFUSED` on the
  // presigned URL, so the action returns "Failed to download document." and no file is
  // saved. In a real deployment the presigned URL points at S3, reachable from the server.
  test.fixme("Alice: Statements-tab Download fetches the PDF", async ({ page }) => {
    await loginAs(page, "alice");
    const tab = await openStatementsTab(page);
    const row = tab.getByTestId(savedStatementRowId);
    await expect(row).toBeVisible({ timeout: 15_000 });
    const [download] = await Promise.all([
      page.waitForEvent("download", { timeout: 15_000 }),
      row.locator('[data-testid^="statement-download-"]').click(),
    ]);
    expect(download.suggestedFilename()).toMatch(/\.pdf$/);
  });

  // ── 45.5-45.11: Disbursement write-off ─────────────────────────────
  // Regression (was FIXME depth-ii:no-write-off-action, G-C9): the disbursement detail page
  // offers Write off for an APPROVED/UNBILLED disbursement (621B).
  test("Alice (Thandi): Write off one approved-UNBILLED disbursement", async ({ page }) => {
    await loginAs(page, "alice");
    const detail = await openSheriffFeeDetail(page);
    await expect(detail.getByText("Unbilled", { exact: true })).toBeVisible();

    await detail.getByRole("button", { name: /write[\s-]*off/i }).click();
    const dialog = page.getByRole("dialog", { name: /write[\s-]*off/i });
    await expect(dialog).toBeVisible();
    await dialog.getByRole("textbox", { name: /reason/i }).fill(WRITE_OFF_REASON);
    await dialog.getByRole("button", { name: /write[\s-]*off|confirm/i }).click();
    await expect(dialog).toBeHidden({ timeout: 15_000 });

    await expect(detail.getByText("Written off", { exact: true })).toBeVisible();
    await expect(detail).toContainText("Write-off reason");
    await expect(detail).toContainText(WRITE_OFF_REASON);
    await expect(page.locator("main")).not.toContainText(/Expense written off/i);
    await page.waitForLoadState("networkidle");
    await captureScreenshot(page, "day-45-disbursement-written-off");
  });

  // Regression (was FIXME depth-ii:no-write-off-action): the write-off above emits
  // disbursement.written_off with the reason (621B).
  test("Alice: Verify write-off audit event present in audit log", async ({ page }) => {
    await loginAs(page, "alice");
    // Phase 87 S9 (ADR-344): /settings/audit-log → /settings/firm?tab=audit-trail
    await page.goto(`${BASE}/settings/firm?tab=audit-trail`);
    await page.waitForLoadState("networkidle");
    // 625D: Event type is a searchable combobox — type the full key, Enter commits it (exact facet
    // key first, else the key-shaped query's "Event key starts with …" prefix item).
    const eventType = page.locator("#audit-filter-event-type");
    await eventType.click();
    const search = page.getByPlaceholder("Search event types…");
    await expect(search).toBeVisible();
    await search.fill("disbursement.written_off");
    await search.press("Enter");
    await page.waitForURL(/eventType=/);

    const row = page.locator('[data-testid^="audit-row-"]').first();
    await expect(row).toContainText(/written.off/i, { timeout: 15_000 });
    await row.getByRole("button", { name: "Expand details" }).click();
    await expect(page.locator('[data-testid^="audit-row-details-"]').first()).toContainText(
      WRITE_OFF_REASON
    );
    await page.waitForLoadState("networkidle");
    await captureScreenshot(page, "day-45-audit-log-write-off-event");
  });

  // D9 (#1748, #1749): the written-off disbursement leaves the next statement — its disbursements
  // table and total, and the unbilled-WIP line — while nothing becomes owing.
  test("Alice: Statement after the write-off — sheriff fee out of the disbursements and the WIP", async ({
    page,
  }) => {
    await loginAs(page, "alice");
    const tab = await openStatementsTab(page);
    const dialog = await generateStatement(page, tab);
    await verifyStatementPreview(dialog, AFTER_WRITE_OFF);
    await page.keyboard.press("Escape");
    await expect(dialog).toBeHidden();
  });

  // Regression (was FIXME depth-ii:no-write-off-action): the written-off disbursement is not
  // offered on the next fee-note draft (621B).
  test("Alice: Verify written-off disbursement excluded from next invoice draft", async ({
    page,
  }) => {
    await loginAs(page, "alice");
    await page.goto(`${BASE}/invoices`);
    await page.getByRole("button", { name: "New Fee Note" }).click();
    await page
      .getByRole("option", { name: new RegExp(`^${escapeRegExp(siphoClientName())}(\\s|$)`) })
      .click();
    // Phase 87 S4 (ADR-344): route rename (610D — post-redirect URL)
    await page.waitForURL(/\/clients\/[0-9a-f-]{36}\?tab=invoices/);

    await page.getByRole("tabpanel").getByRole("button", { name: "New Fee Note" }).click();
    // Phase 88 621D (B-7, mirrors 611B / ADR-338 at day-75-85-closure): label amendment — the
    // title switches to "Select Unbilled Items" after Fetch (invoice-generation-dialog.tsx:67)
    const dialog = page.getByRole("dialog", { name: /Generate fee note|Select Unbilled Items/ });
    await expect(dialog).toBeVisible();
    await dialog.getByRole("button", { name: "Fetch Unbilled Time" }).click();

    // The fee-note draft's selectable items must not offer the written-off disbursement
    await expect(dialog.getByText("Select Unbilled Items")).toBeVisible({ timeout: 15_000 });
    await expect(dialog).not.toContainText(SHERIFF_FEE_DESCRIPTION);
    await page.waitForLoadState("networkidle");
    await captureScreenshot(page, "day-45-fee-note-draft-excludes-writeoff");
  });
});
