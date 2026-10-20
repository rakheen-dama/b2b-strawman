/**
 * Shared helpers for the Phase 67 `legal-depth-ii` Playwright suite.
 *
 * The suite is standalone: it needs only a freshly seeded E2E stack (legal-za
 * profile) and creates every client, matter and record it acts on through the UI.
 * It does not depend on the legal-lifecycle suite.
 *
 * The four spec files run serially (workers: 1) and share state: Day 5 creates
 * Sipho's client + litigation matter + its disbursements/trust activity, and the
 * Day 30-45 / Day 85 specs act on that same matter. Playwright restarts the worker
 * after a failure (re-evaluating module-level constants), so the run id and the
 * recorded entity names live in a small file in the OS temp dir rather than in
 * module constants. (`helpers/e2e-state.ts` is not reused: its schema is the
 * Keycloak onboarding state — owner credentials + org slug — not run-scoped
 * entity names.) The first Day 5 test always starts a fresh run, overwriting any
 * state left by an earlier run or an earlier stack; the later specs then fail fast
 * with a "should be listed" message naming the missing entity if Day 5 has not
 * run against the current stack.
 */
import { expect, test, type Locator, type Page } from "@playwright/test";
import { randomBytes } from "node:crypto";
import { existsSync, readFileSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import path from "node:path";

export const ORG = "e2e-test-org";
export const BASE = `/org/${ORG}`;

const RUN_FILE = path.join(tmpdir(), "kazi-legal-depth-ii-run.json");

interface DepthIiRunState {
  runId: string;
  /** Exact name of the client Sipho's depth-II matter was opened for (created by Day 5). */
  siphoClient?: string;
}

function readRunState(): DepthIiRunState {
  if (!existsSync(RUN_FILE)) {
    throw new Error(
      `No legal-depth-ii run state at ${RUN_FILE} — run day-05-disbursements.spec.ts first`
    );
  }
  return JSON.parse(readFileSync(RUN_FILE, "utf-8")) as DepthIiRunState;
}

/**
 * Starts a new depth-II run (called by the first Day 5 test) and returns its id —
 * the single source of the run id for every spec in the suite. Millisecond time
 * plus 16 random bits, so ids never collide across runs on a reused stack.
 */
export function startDepthIiRun(): string {
  const runId = `${Date.now().toString(36)}${randomBytes(2).toString("hex")}`;
  writeFileSync(RUN_FILE, JSON.stringify({ runId } satisfies DepthIiRunState));
  return runId;
}

/** The id of the depth-II run started by Day 5 (throws if Day 5 has not run). */
export function currentDepthIiRun(): string {
  return readRunState().runId;
}

/**
 * Run id scoped to the current test attempt. A retried serial group (CI runs with
 * `retries: 1`) re-runs every test of the group with `retry > 0`, so names built from
 * this id stay unique when a group re-creates its records for the same run.
 */
export function attemptRunId(): string {
  const retry = test.info().retry;
  return retry ? `${currentDepthIiRun()}-r${retry}` : currentDepthIiRun();
}

/** Records the exact client name Sipho's depth-II matter belongs to. */
export function recordSiphoClient(name: string): void {
  writeFileSync(RUN_FILE, JSON.stringify({ ...readRunState(), siphoClient: name }));
}

/** Exact name of Sipho's client record for this run (recorded by Day 5). */
export function siphoClientName(): string {
  const name = readRunState().siphoClient;
  if (!name) throw new Error("Sipho's client was not recorded — run day-05 first");
  return name;
}

/** Sipho Ndlovu's litigation matter used by Day 5, Day 30-45 and Day 85. */
export function siphoMatterName(runId: string = currentDepthIiRun()): string {
  return `Ndlovu v RAF — Depth II ${runId}`;
}

export function escapeRegExp(value: string): string {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

/** YYYY-MM-DD for today offset by `days` (local time, matching `<input type="date">`). */
export function isoDate(days = 0): string {
  const d = new Date();
  d.setDate(d.getDate() + days);
  const mm = String(d.getMonth() + 1).padStart(2, "0");
  const dd = String(d.getDate()).padStart(2, "0");
  return `${d.getFullYear()}-${mm}-${dd}`;
}

/**
 * Selects the first `<option>` of a native `<select>` whose text matches `label` and
 * returns the selected option's text. Waits for the option to exist first — several
 * selects load their options asynchronously ("Loading matters...").
 */
export async function selectNativeOption(select: Locator, label: string | RegExp): Promise<string> {
  const option = select.locator("option", { hasText: label }).first();
  await expect(option, `option ${String(label)} should load`).toBeAttached({ timeout: 15_000 });
  const value = await option.getAttribute("value");
  expect(value, `option ${String(label)} should have a value`).toBeTruthy();
  await select.selectOption(value!);
  await expect(select).toHaveValue(value!);
  return (await option.textContent())?.trim() ?? "";
}

/**
 * Performs a click that navigates client-side to another page and waits until that page
 * is ready for interaction: its `ready` element is visible. The destination is mounted
 * once per navigation (the org entrance animation is a CSS-only template), so it can be
 * acted on as soon as it is visible. No reload, no fixed sleep.
 */
export async function clickThrough(target: Locator, ready: Locator): Promise<void> {
  await target.click();
  await expect(ready).toBeVisible({ timeout: 15_000 });
}

/**
 * Opens a matter from the Matters list by clicking its row link and waits until the
 * matter page is ready (heading visible).
 */
export async function openMatter(
  page: Page,
  matterName: string,
  { closed = false }: { closed?: boolean } = {}
): Promise<void> {
  await page.goto(`${BASE}/projects`);
  if (closed) {
    // The list shows active matters by default; closed ones sit behind the "Closed" chip
    await page.getByTestId("project-status-filter").getByRole("link", { name: "Closed" }).click();
    await expect(page).toHaveURL(/[?&]status=CLOSED/);
  }
  const link = page.getByRole("link", { name: new RegExp(escapeRegExp(matterName)) }).first();
  await expect(link, `matter "${matterName}" should be listed`).toBeVisible({ timeout: 15_000 });
  await clickThrough(link, page.getByRole("heading", { level: 1, name: matterName }));
  // Phase 87 S4 (ADR-344): route rename (610D — post-redirect URL)
  await expect(page).toHaveURL(/\/matters\/[0-9a-f-]{36}/);
}

/**
 * Opens a client from the Clients list by clicking its link and waits until the client
 * page is ready (heading visible).
 */
export async function openClient(page: Page, clientName: string): Promise<void> {
  await page.goto(`${BASE}/customers`);
  const link = page.getByRole("link", { name: clientName, exact: true });
  await expect(link, `client "${clientName}" should be listed`).toBeVisible({ timeout: 15_000 });
  await clickThrough(link, page.getByRole("heading", { level: 1, name: clientName }));
  // Phase 87 S4 (ADR-344): route rename (610D — post-redirect URL)
  await expect(page).toHaveURL(/\/clients\/[0-9a-f-]{36}/);
}

/**
 * Opens Legal → Disbursements filtered to one matter (the list's "Matter filter"). The
 * list shows one page (50 rows); on a stack reused across runs the unfiltered list can
 * push this run's rows off that page, so every lookup narrows to the run's own matter.
 */
export async function openDisbursementList(page: Page, matterName: string): Promise<void> {
  await page.goto(`${BASE}/legal/disbursements`);
  await expect(page.getByTestId("disbursements-list-page")).toBeVisible({ timeout: 15_000 });
  await selectNativeOption(page.getByLabel("Matter filter"), matterName);
  // The filtered refetch has landed once no row of another matter is listed
  await expect(
    page.locator('[data-testid^="disbursement-row-"]').filter({ hasNotText: matterName })
  ).toHaveCount(0, { timeout: 15_000 });
}

export interface NewClient {
  name: string;
  type: "Individual" | "Company";
  email: string;
  /** SA ID / passport number (step 2 intake field) — individuals only. */
  idNumber?: string;
  /** Tax number — required to send a fee note (auto-filled from the ID number for individuals). */
  taxNumber?: string;
}

/**
 * Creates a client through the New Client dialog (with the billing address fee
 * notes require) and leaves the browser on the new client's page.
 */
export async function createClient(page: Page, client: NewClient): Promise<void> {
  await page.goto(`${BASE}/customers`);
  await page.getByRole("button", { name: "New Client" }).click();

  // The dialog title changes from "Create Client" (step 1) to "Additional Information" (step 2)
  const dialog = page.getByRole("dialog", { name: /^(Create Client|Additional Information)$/ });
  await expect(dialog).toBeVisible();
  await dialog.getByLabel("Name", { exact: true }).fill(client.name);
  await selectNativeOption(dialog.getByLabel("Type", { exact: true }), client.type);
  await dialog.getByLabel("Email", { exact: true }).fill(client.email);
  await dialog.locator('input[name="addressLine1"]').fill("12 Rivonia Road");
  await dialog.locator('input[name="city"]').fill("Sandton");
  await dialog.locator('input[name="postalCode"]').fill("2196");
  await dialog.getByLabel(/^Country/).selectOption("ZA");
  if (client.taxNumber) {
    await dialog.getByLabel(/^Tax Number/).fill(client.taxNumber);
  }
  await dialog.getByRole("button", { name: "Next" }).click();

  // Step 2: SA Legal client intake fields
  await expect(dialog.getByText("Step 2 of 2")).toBeVisible();
  if (client.idNumber) {
    await dialog.getByLabel(/ID \/ Passport Number/).fill(client.idNumber);
  }
  // Creating the client navigates to its page
  await clickThrough(
    dialog.getByRole("button", { name: "Create Client" }),
    page.getByRole("heading", { level: 1, name: client.name })
  );
  // Phase 87 S4 (ADR-344): route rename (610D — post-redirect URL)
  await expect(page).toHaveURL(/\/clients\/[0-9a-f-]{36}/);
}

/** PROSPECT → ONBOARDING from the client page (new matters cannot be opened for a prospect). */
export async function startClientOnboarding(page: Page): Promise<void> {
  const badge = page.getByTestId("lifecycle-badge");
  await expect(badge).toHaveText("Prospect");
  await page.getByRole("button", { name: "Start Onboarding" }).first().click();
  const confirm = page.getByRole("alertdialog", { name: "Start Onboarding" });
  await confirm.getByRole("button", { name: "Start Onboarding" }).click();
  await expect(confirm).toBeHidden({ timeout: 15_000 });
  await expect(badge).toHaveText("Onboarding", { timeout: 15_000 });
}

/**
 * Opens a sub-tab of a grouped tab bar (matter or client detail, e.g. Finance → Statements).
 * Clicking an inactive group activates its first sub-tab; the sub-tab menu is
 * opened by clicking the (now active) group trigger.
 */
export async function openGroupedTab(page: Page, groupId: string, tabId: string): Promise<void> {
  const group = page.getByTestId(`tab-group-${groupId}`);
  const item = page.getByTestId(`tab-item-${tabId}`);
  await expect(group).toBeVisible({ timeout: 15_000 });
  // The group trigger toggles its sub-tab menu; whether the first click opens the
  // menu depends on whether the group was already active, so click until it is open.
  for (let attempt = 0; attempt < 3; attempt++) {
    await group.click();
    const menuOpen = await item.waitFor({ state: "visible", timeout: 2_000 }).then(
      () => true,
      () => false
    );
    if (menuOpen) {
      await item.click();
      await expect(group).toHaveAttribute("aria-selected", "true");
      return;
    }
  }
  throw new Error(`Sub-tab menu for tab-group-${groupId} never opened (looking for ${tabId})`);
}

/** Opens the Matter-closure dialog from the matter header and waits for the gate report. */
export async function openClosureDialog(page: Page): Promise<Locator> {
  await page.getByTestId("close-matter-btn").click();
  const dialog = page.getByTestId("matter-closure-dialog");
  await expect(dialog).toBeVisible();
  await expect(dialog.getByTestId("matter-closure-report")).toBeVisible({ timeout: 15_000 });
  await expect(dialog.getByTestId("matter-closure-next-btn")).toBeEnabled();
  return dialog;
}
