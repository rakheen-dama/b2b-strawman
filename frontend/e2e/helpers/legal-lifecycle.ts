/**
 * Shared run state + helpers for the `legal-lifecycle` Playwright suite (Phase 88 628E, OD-5).
 *
 * The lifecycle spec files run serially (workers: 1) as one chain: Day 0 sets up the firm, Days 1–3
 * open the four clients and their matters, Days 7–14 log work and fund trust, and Days 30–90 bill,
 * reconcile and report on those same records. Playwright restarts the worker after a failure
 * (re-evaluating module-level constants), and every file is its own module, so a file-local
 * `RUN_ID` cannot name another file's records. The run id and the ids the later days need live in
 * a small JSON file in the OS temp dir instead (the same design as `legal-depth-ii.ts`).
 *
 * The first Day 0 test calls `startLifecycleRun()`, which overwrites any earlier run. Look records
 * up by their exact run-scoped name (`openMatter(page, siphoMatter())`) or recorded id — never by a
 * loose regex + `.first()`: on a reused stack that picks an older run's record (619E CodeRabbit).
 *
 * Re-running on a used stack: every name is run-scoped, Day 0's firm setup asserts-or-creates, but
 * the Day 1–3 conflict checks expect "No Conflict", which holds only on a fresh stack
 * (`e2e-down.sh && e2e-up.sh`) — earlier runs' clients and matters fuzzy-match the same names.
 */
import { expect, type Locator, type Page } from "@playwright/test";
import { randomBytes } from "node:crypto";
import { existsSync, readFileSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import path from "node:path";
import { openGroupedTab } from "./grouped-tabs";
import { BASE, clickThrough, escapeRegExp, openClient, selectNativeOption } from "./legal-depth-ii";

const RUN_FILE = path.join(tmpdir(), "kazi-legal-lifecycle-run.json");

/** The four lifecycle clients. */
export type LifecycleClient = "sipho" | "apex" | "moroka" | "qc";
/** Lifecycle matters recorded in run state (Days 30–90 may add their own keys). */
export type LifecycleMatter = "sipho" | "apex" | "moroka" | "qc" | (string & {});

interface LifecycleRunState {
  runId: string;
  /** Client UUIDs by key (from `/clients/{uuid}`), recorded when each client is created. */
  clients: Partial<Record<LifecycleClient, string>>;
  /** Matter UUIDs by key (from `/matters/{uuid}`), recorded when each matter is created. */
  matters: Record<string, string>;
  /** Free-form values a later day needs (fee-note UUIDs, references …). */
  values: Record<string, string>;
}

function readRunState(): LifecycleRunState {
  if (!existsSync(RUN_FILE)) {
    throw new Error(
      `No legal-lifecycle run state at ${RUN_FILE} — run day-00-firm-setup.spec.ts first`
    );
  }
  return JSON.parse(readFileSync(RUN_FILE, "utf-8")) as LifecycleRunState;
}

function writeRunState(state: LifecycleRunState): void {
  writeFileSync(RUN_FILE, JSON.stringify(state, null, 2));
}

/**
 * Starts a new lifecycle run (called by the first Day 0 test) and returns its id — the single
 * source of the run id for every lifecycle spec. Millisecond time plus 16 random bits, so ids
 * never collide across runs on a reused stack.
 */
export function startLifecycleRun(): string {
  const runId = `${Date.now().toString(36)}${randomBytes(2).toString("hex")}`;
  writeRunState({ runId, clients: {}, matters: {}, values: {} });
  return runId;
}

/** The id of the lifecycle run started by Day 0 (throws if Day 0 has not run). */
export function currentLifecycleRun(): string {
  return readRunState().runId;
}

// ── Name builders (exact, run-scoped) ────────────────────────────────────────────────────────
export const siphoClient = () => `Sipho Ndlovu ${currentLifecycleRun()}`;
export const apexClient = () => `Apex Holdings ${currentLifecycleRun()}`;
export const morokaClient = () => `Moroka Family Trust ${currentLifecycleRun()}`;
export const qcClient = () => `QuickCollect Services ${currentLifecycleRun()}`;

/** Sipho's litigation matter (Litigation (Personal Injury / General) template, Day 1). */
export const siphoMatter = () => `Ndlovu v RAF ${currentLifecycleRun()}`;
/** Apex's commercial matter (Commercial (Corporate & Contract) template, Day 2). */
export const apexMatter = () => `Apex Shareholder Agreement ${currentLifecycleRun()}`;
/** Moroka's estate matter (Deceased Estate Administration template, Day 2). */
export const morokaMatter = () => `Estate Late Peter Moroka ${currentLifecycleRun()}`;
/** QuickCollect's first collections matter (Collections (Debt Recovery) template, Day 3). */
export const qcMatter = () => `QC Debt Recovery #1 ${currentLifecycleRun()}`;

const CLIENT_NAMES: Record<LifecycleClient, () => string> = {
  sipho: siphoClient,
  apex: apexClient,
  moroka: morokaClient,
  qc: qcClient,
};

/** Exact run-scoped client name for a key. */
export function lifecycleClientName(key: LifecycleClient): string {
  return CLIENT_NAMES[key]();
}

// ── Record setters / getters ─────────────────────────────────────────────────────────────────
export function recordClientId(key: LifecycleClient, id: string): void {
  const state = readRunState();
  writeRunState({ ...state, clients: { ...state.clients, [key]: id } });
}

/** Client UUID recorded when the client was created (throws if it was not). */
export function clientId(key: LifecycleClient): string {
  const id = readRunState().clients[key];
  if (!id) throw new Error(`Client "${key}" was not recorded — run day-01-03-onboarding first`);
  return id;
}

export function recordMatterId(key: LifecycleMatter, id: string): void {
  const state = readRunState();
  writeRunState({ ...state, matters: { ...state.matters, [key]: id } });
}

/** Matter UUID recorded when the matter was created (throws if it was not). */
export function matterId(key: LifecycleMatter): string {
  const id = readRunState().matters[key];
  if (!id) throw new Error(`Matter "${key}" was not recorded by an earlier lifecycle day`);
  return id;
}

/** Records any value a later day needs (e.g. a fee-note UUID for a raw-UUID dialog field). */
export function recordValue(key: string, value: string): void {
  const state = readRunState();
  writeRunState({ ...state, values: { ...state.values, [key]: value } });
}

export function recordedValue(key: string): string {
  const value = readRunState().values[key];
  if (value === undefined) throw new Error(`Run-state value "${key}" was not recorded`);
  return value;
}

// ── Small shared helpers ─────────────────────────────────────────────────────────────────────
const UUID = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

/** The UUID after `/<segment>/` in the current URL (e.g. `idFromUrl(page, "clients")`). */
export function idFromUrl(page: Page, segment: string): string {
  const match = new URL(page.url()).pathname.match(new RegExp(`/${segment}/(${UUID})`));
  if (!match) throw new Error(`No /${segment}/<uuid> in ${page.url()}`);
  return match[1];
}

/** A `Rand` amount as rendered by `formatCurrency` (en-ZA: NBSP/narrow-NBSP group separator). */
export function randPattern(amount: string): RegExp {
  // "250 000,00" → /250[\s  ]?000,00/
  const [whole, cents = "00"] = amount.split(",");
  const groups = whole.split(/[\s  ]/).map(escapeRegExp);
  return new RegExp(`${groups.join("[\\s\\u00a0\\u202f]?")},${cents}`);
}

/**
 * Opens a new matter from a template, linked to `client`, and returns its UUID. Leaves the browser
 * on the new matter's page. The client must not be a PROSPECT (CustomerLifecycleGuard).
 */
export async function createTemplateMatter(
  page: Page,
  { template, name, client }: { template: RegExp; name: string; client: string }
): Promise<string> {
  await page.goto(`${BASE}/matters`);
  await page.getByRole("button", { name: "New from Template" }).click();
  const dialog = page.getByRole("dialog", { name: /New from Template/ });
  await expect(dialog).toBeVisible();
  await dialog.getByRole("option", { name: template }).click();
  await dialog.getByRole("button", { name: "Next" }).click();
  await dialog.getByLabel(/Matter name/).fill(name);
  await selectNativeOption(dialog.getByLabel(/^Client/), client);
  await dialog.getByRole("button", { name: "Create Matter" }).click();
  await page.waitForURL(/\/matters\/[0-9a-f-]{36}/, { timeout: 15_000 });
  await expect(page.getByRole("heading", { level: 1, name })).toBeVisible({ timeout: 15_000 });
  await expect(page.locator("main")).toContainText(client);
  return idFromUrl(page, "matters");
}

/**
 * Logs time on one task of the open matter (Work → Tasks → row "Log Time"). Time is logged per
 * task; there is no matter-level Log Time. `rate` (e.g. "2 500,00") asserts the billing-rate
 * preview, i.e. that the Day 0 rate card applies to this member.
 */
export async function logTimeOnTask(
  page: Page,
  opts: {
    task: string;
    hours: number;
    minutes: number;
    description: string;
    date: string;
    rate?: string;
  }
): Promise<void> {
  await openGroupedTab(page, "work", "tasks");
  const taskRow = page
    .getByRole("tabpanel")
    .getByRole("row")
    .filter({ has: page.getByRole("button", { name: `Open task detail for ${opts.task}` }) });
  await expect(taskRow, `task "${opts.task}" should be listed`).toHaveCount(1, {
    timeout: 15_000,
  });
  await taskRow.getByRole("button", { name: "Log Time" }).click();

  const dialog = page.getByRole("dialog", { name: "Log Time" });
  await expect(dialog).toBeVisible();
  await dialog.locator("#time-hours").fill(String(opts.hours));
  await dialog.locator("#time-minutes").fill(String(opts.minutes));
  await dialog.locator("#time-date").fill(opts.date);
  await dialog.locator("#time-description").fill(opts.description);
  await expect(dialog.locator("#time-billable")).toBeChecked();
  if (opts.rate) {
    await expect(dialog.getByTestId("rate-preview")).toContainText(
      new RegExp(`Billing rate: R[\\s\\u00a0\\u202f]?${randPattern(opts.rate).source}/hr`),
      { timeout: 15_000 }
    );
  }
  await dialog.getByRole("button", { name: "Log Time" }).click();
  await expect(dialog).toBeHidden({ timeout: 15_000 });
}

/**
 * Runs a conflict check from `/conflict-check` and returns the result panel (its heading is the
 * verdict: "No Conflict" | "Potential Conflict" | "Conflict Found").
 */
export async function runConflictCheck(page: Page, name: string): Promise<Locator> {
  await page.goto(`${BASE}/conflict-check`);
  await expect(page.getByRole("heading", { level: 1, name: "Conflict Check" })).toBeVisible({
    timeout: 15_000,
  });
  const form = page.getByTestId("conflict-check-form");
  await form.getByLabel(/Name to Check/).fill(name);
  await form.getByRole("button", { name: "Run Conflict Check" }).click();
  const result = page.getByTestId("conflict-check-result");
  await expect(result).toBeVisible({ timeout: 15_000 });
  await expect(result).toContainText(`Checked "${name}"`);
  return result;
}

/** The firm's trust account (scenario 0.62; the 628F seed uses the identical name and bank). */
export const TRUST_ACCOUNT = {
  name: "Mathebula & Partners Trust Account",
  bank: "Standard Bank",
  branchCode: "051001",
  accountNumber: "0123456789",
} as const;

/**
 * Creates the primary GENERAL trust account if the firm has none (a second primary GENERAL account
 * is rejected by a unique index). Deterministic: waits for either state before branching.
 */
export async function ensureTrustAccount(page: Page): Promise<void> {
  await page.goto(`${BASE}/trust-accounting`);
  await expect(page.getByRole("heading", { level: 1, name: "Trust Accounting" })).toBeVisible({
    timeout: 15_000,
  });
  const addAccount = page.getByRole("button", { name: "Add Account" });
  const recordTransaction = page.getByRole("link", { name: "Record Transaction" });
  await expect(addAccount.or(recordTransaction).first()).toBeVisible({ timeout: 15_000 });
  if (await addAccount.isVisible()) {
    await addAccount.click();
    const dialog = page.getByRole("dialog", { name: "Add Trust Account" });
    await expect(dialog).toBeVisible();
    await dialog.getByLabel("Account Name").fill(TRUST_ACCOUNT.name);
    await dialog.getByLabel("Bank Name").fill(TRUST_ACCOUNT.bank);
    await dialog.getByLabel("Branch Code").fill(TRUST_ACCOUNT.branchCode);
    await dialog.getByLabel("Account Number").fill(TRUST_ACCOUNT.accountNumber);
    await dialog.getByRole("button", { name: "Create Account" }).click();
    await expect(dialog).toBeHidden({ timeout: 15_000 });
  }
  await expect(recordTransaction).toBeVisible({ timeout: 15_000 });
}

/** Opens Trust → Transactions. */
export async function openTrustTransactions(page: Page): Promise<void> {
  await page.goto(`${BASE}/trust-accounting/transactions`);
  await expect(page.getByRole("heading", { level: 1, name: "Transactions" })).toBeVisible({
    timeout: 15_000,
  });
}

/** The Trust → Transactions row holding `reference`. */
export function trustTransactionRow(page: Page, reference: string): Locator {
  return page.locator("main tbody tr", { hasText: reference });
}

/**
 * Records a client trust deposit (Record Transaction → Record Deposit) and asserts its row lands
 * RECORDED — deposits never wait for approval; the approval path is the fee transfer (Day 30).
 */
export async function recordTrustDeposit(
  page: Page,
  opts: { client: string; matter?: string; amount: string; reference: string; description: string }
): Promise<void> {
  await openTrustTransactions(page);
  await page.getByTestId("record-transaction-button").click();
  await page.getByRole("menuitem", { name: "Record Deposit" }).click();
  const dialog = page.getByRole("dialog", { name: "Record Deposit" });
  await expect(dialog).toBeVisible();
  await dialog.getByTestId("trust-deposit-customer-trigger").click();
  await page
    .getByRole("option", { name: new RegExp(`^${escapeRegExp(opts.client)}(\\s|$)`) })
    .click();
  if (opts.matter) {
    await dialog.getByTestId("trust-deposit-matter-trigger").click();
    await page.getByRole("option", { name: new RegExp(escapeRegExp(opts.matter)) }).click();
  }
  await dialog.getByLabel("Amount").fill(opts.amount);
  await dialog.getByLabel("Reference").fill(opts.reference);
  await dialog.getByLabel(/Description/).fill(opts.description);
  await dialog.getByRole("button", { name: "Record Deposit" }).click();
  await expect(dialog).toBeHidden({ timeout: 15_000 });
  await expect(trustTransactionRow(page, opts.reference)).toContainText("RECORDED", {
    timeout: 15_000,
  });
}

// ── Fee notes (Days 30–90) ───────────────────────────────────────────────────────────────────

/** Opens a client's Fees → Fee notes tab. */
export async function openClientFeeNotes(page: Page, client: string): Promise<void> {
  await openClient(page, client);
  await openGroupedTab(page, "finance", "invoices");
}

/**
 * On a client's Fee notes tab: New fee note → Fetch Unbilled Time → Validate & Create Draft →
 * Create Draft, for the unbilled items of `matter` (`items` = how many the dialog must select).
 * Opens the created draft (the one fee-note link that was not listed before) and returns its UUID.
 */
export async function generateFeeNoteDraft(
  page: Page,
  { matter, items }: { matter: string; items: number }
): Promise<string> {
  const panel = page.getByRole("tabpanel");
  const newFeeNote = panel.getByRole("button", { name: "New fee note" });
  await expect(newFeeNote).toBeEnabled({ timeout: 15_000 });
  const feeNoteLinks = () =>
    panel
      .locator('a[href*="/invoices/"]')
      .evaluateAll((els) => els.map((e) => e.getAttribute("href")!));
  const before = new Set(await feeNoteLinks());

  await newFeeNote.click();
  const generate = page.getByRole("dialog", { name: /Generate fee note|Select Unbilled Items/ });
  await expect(generate).toBeVisible({ timeout: 15_000 });
  await generate.getByRole("button", { name: "Fetch Unbilled Time" }).click();
  // Step 2: the matter's unbilled items, all pre-selected and all rated (Day 0 rate cards)
  await expect(
    generate.getByRole("checkbox", { name: `Select all entries for ${matter}` })
  ).toBeChecked({ timeout: 15_000 });
  // (the dialog always says "items": "1 items selected for …")
  await expect(generate).toContainText(`${items} items selected for`);
  await expect(generate.getByTestId("null-rate-warning")).toHaveCount(0);
  await generate.getByRole("button", { name: "Validate & Create Draft" }).click();
  await generate.getByRole("button", { name: /^Create Draft/ }).click();
  await expect(generate).toBeHidden({ timeout: 15_000 });

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
    page.getByRole("heading", { level: 1, name: "Draft fee note" })
  );
  await expect(page.locator("main")).toContainText(matter);
  return idFromUrl(page, "invoices");
}

/** Opens a fee note by its UUID and waits for its header. */
export async function openFeeNote(page: Page, id: string): Promise<void> {
  await page.goto(`${BASE}/invoices/${id}`);
  await expect(page.getByRole("heading", { level: 1 })).toBeVisible({ timeout: 15_000 });
  await expect(page.locator("main").getByText(/^Client: /)).toBeVisible();
}

/** Adds a manual line (Add Line → Add Line Item) to the open draft fee note, at the default tax rate. */
export async function addFeeNoteLine(
  page: Page,
  { description, quantity, unitPrice }: { description: string; quantity: string; unitPrice: string }
): Promise<void> {
  const main = page.locator("main");
  await main.getByRole("button", { name: "Add Line" }).click();
  const form = main.locator("div").filter({
    has: page.getByRole("heading", { level: 3, name: "Add Line Item" }),
  });
  const editor = form.last();
  await expect(editor).toBeVisible();
  await editor.getByPlaceholder("Line item description").fill(description);
  // Quantity and Unit Price are the editor's two (unlabelled) number inputs
  const numbers = editor.locator('input[type="number"]');
  await numbers.nth(0).fill(quantity);
  await numbers.nth(1).fill(unitPrice);
  await editor.getByRole("button", { name: "Add", exact: true }).click();
  await expect(page.getByRole("heading", { level: 3, name: "Add Line Item" })).toBeHidden({
    timeout: 15_000,
  });
  await expect(main.getByRole("row").filter({ hasText: description })).toHaveCount(1, {
    timeout: 15_000,
  });
}

/**
 * A row of the open fee note's totals block ("Subtotal", "VAT — Standard (15%)", "Total" …): the
 * row whose text starts with `label` (so "Total" does not match "Subtotal").
 */
export function feeNoteTotalsRow(page: Page, label: string): Locator {
  const totals = page.locator("main").getByText("Subtotal", { exact: true }).locator("xpath=../..");
  return totals.locator(":scope > div").filter({ hasText: new RegExp(`^${escapeRegExp(label)}`) });
}
