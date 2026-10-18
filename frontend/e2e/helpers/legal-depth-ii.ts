/**
 * Shared helpers for the Phase 67 `legal-depth-ii` Playwright suite.
 *
 * The four depth-II spec files run serially (workers: 1) and share state: Day 5
 * creates Sipho's litigation matter + its disbursements/trust activity, and the
 * Day 30-45 / Day 85 specs act on that same matter. Playwright restarts the
 * worker after a failure (re-evaluating module-level constants), so the run id
 * that names the shared matter is persisted to a small file in the OS temp dir
 * instead of living in a module constant.
 */
import { expect, type Locator, type Page } from "@playwright/test";
import { existsSync, readFileSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import path from "node:path";

export const ORG = "e2e-test-org";
export const BASE = `/org/${ORG}`;

/** Mailpit of the E2E mock-auth stack (compose/docker-compose.e2e.yml maps 8025 -> 8026). */
export const E2E_MAILPIT_URL = process.env.E2E_MAILPIT_URL || "http://localhost:8026";

const RUN_FILE = path.join(tmpdir(), "kazi-legal-depth-ii-run.json");

interface DepthIiRunState {
  runId: string;
  /** Exact name of the lifecycle client Sipho's depth-II matter was opened for. */
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

/** Starts a new depth-II run (called by the first Day 5 test) and returns its id. */
export function startDepthIiRun(): string {
  const runId = Date.now().toString(36).slice(-5);
  writeFileSync(RUN_FILE, JSON.stringify({ runId } satisfies DepthIiRunState));
  return runId;
}

/** The id of the depth-II run started by Day 5 (throws if Day 5 has not run). */
export function currentDepthIiRun(): string {
  return readRunState().runId;
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
 * Opens a matter from the Matters list. The row link's target is loaded as a full
 * navigation (rather than a client-side transition) so the page is hydrated and
 * network-idle before the test starts firing server actions on it.
 */
export async function openMatter(page: Page, matterName: string): Promise<void> {
  await page.goto(`${BASE}/projects`);
  const link = page.getByRole("link", { name: new RegExp(escapeRegExp(matterName)) }).first();
  await expect(link, `matter "${matterName}" should be listed`).toBeVisible({ timeout: 15_000 });
  const href = await link.getAttribute("href");
  expect(href).toMatch(/\/projects\/[0-9a-f-]{36}/);
  await page.goto(href!);
  await page.waitForLoadState("networkidle");
  await expect(page.getByRole("heading", { level: 1, name: matterName })).toBeVisible({
    timeout: 15_000,
  });
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
