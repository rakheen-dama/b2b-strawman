// 640B OBS seeding (task 640.11 step 3) — browser-only (no SQL / REST writes) on the mock-auth stack.
// Usage: node seed-ui.cjs <step: aged|bob|carol>
//   aged  — Alice logs 1h on the Apex matter, generates a fee note from it, sets the due date 60
//           days in the past, approves and sends it (debtor with current + 60-day buckets).
//   bob   — Bob logs 3h billable on the Sipho matter.
//   carol — Carol logs 1h30 billable on the Sipho matter.
const path = require("path");
const fs = require("fs");
const os = require("os");
const { chromium } = require(
  "/Users/rakheendama/Projects/2026/worktree-epic-640B/frontend/node_modules/@playwright/test"
);

const BASE = "http://localhost:3001";
const ORG = "e2e-test-org";
const APP = `${BASE}/org/${ORG}`;
const OUT = __dirname;
const USERS = { alice: "user_e2e_alice", bob: "user_e2e_bob", carol: "user_e2e_carol" };
const run = JSON.parse(fs.readFileSync(path.join(os.tmpdir(), "kazi-legal-lifecycle-run.json"), "utf8"));

function isoDate(days = 0) {
  const sa = new Date(Date.now() + 2 * 3600 * 1000).toISOString().slice(0, 10);
  const [y, m, d] = sa.split("-").map(Number);
  return new Date(Date.UTC(y, m - 1, d + days)).toISOString().slice(0, 10);
}

async function login(browser, user) {
  const context = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  const page = await context.newPage();
  await page.goto(`${BASE}/mock-login`, { waitUntil: "load" });
  await page.locator("#user-select").selectOption(USERS[user]);
  await page.getByRole("button", { name: "Sign In" }).click();
  await page.waitForURL(`**/org/${ORG}/dashboard`, { timeout: 60_000 });
  return { context, page };
}

async function openGroupedTab(page, groupId, tabId) {
  const group = page.getByTestId(`tab-group-${groupId}`).filter({ visible: true });
  const item = page.getByTestId(`tab-item-${tabId}`).filter({ visible: true });
  await group.waitFor({ timeout: 30_000 });
  for (let attempt = 0; attempt < 3; attempt++) {
    await group.click();
    const open = await item.waitFor({ state: "visible", timeout: 2_000 }).then(
      () => true,
      () => false
    );
    if (open) {
      await item.click();
      return;
    }
  }
  throw new Error(`could not open ${groupId}/${tabId}`);
}

async function logTime(page, matterId, { hours, minutes, description }) {
  await page.goto(`${APP}/matters/${matterId}`, { waitUntil: "load" });
  await page.getByRole("heading", { level: 1 }).waitFor({ timeout: 30_000 });
  await openGroupedTab(page, "work", "tasks");
  const panel = page.getByRole("tabpanel");
  const btn = panel.getByRole("button", { name: "Log Time" }).first();
  await btn.waitFor({ timeout: 15_000 });
  const rowText = await panel
    .getByRole("row")
    .filter({ has: page.getByRole("button", { name: "Log Time" }) })
    .first()
    .innerText();
  await btn.click();
  const dialog = page.getByRole("dialog", { name: "Log Time" });
  await dialog.waitFor();
  await dialog.locator("#time-hours").fill(String(hours));
  await dialog.locator("#time-minutes").fill(String(minutes));
  await dialog.getByLabel("Date", { exact: true }).fill(isoDate());
  await dialog.getByRole("textbox", { name: /^Description/ }).fill(description);
  const billable = dialog.getByRole("checkbox", { name: "Billable" });
  if (!(await billable.isChecked())) throw new Error("Billable not checked by default");
  const rate = await dialog.getByTestId("rate-preview").innerText().catch(() => "(no rate preview)");
  await dialog.getByRole("button", { name: "Log Time" }).click();
  await dialog.waitFor({ state: "hidden", timeout: 15_000 });
  return { task: rowText.replace(/\s+/g, " ").slice(0, 80), rate };
}

async function agedFeeNote(page, { clientKey = "apex", matterKey = "apex", matterName = null, daysAgo = 60, shot = "seed-aged-fee-note-sent.png" } = {}) {
  const logged = await logTime(page, run.matters[matterKey], {
    hours: 1,
    minutes: 0,
    description: "640B OBS — aged fee note work",
  });
  console.log("alice logged", logged);
  await page.goto(`${APP}/clients/${run.clients[clientKey]}`, { waitUntil: "load" });
  await openGroupedTab(page, "finance", "invoices");
  const panel = page.getByRole("tabpanel");
  const links = async () =>
    panel.locator('a[href*="/invoices/"]').evaluateAll((els) => els.map((e) => e.getAttribute("href")));
  const before = new Set(await links());
  await panel.getByRole("button", { name: "New fee note" }).click();
  const gen = page.getByRole("dialog", { name: /Generate fee note|Select Unbilled Items/ });
  await gen.waitFor({ timeout: 15_000 });
  await gen.getByRole("button", { name: "Fetch Unbilled Time" }).click();
  await page.waitForTimeout(2000);
  if (matterName) {
    const boxes = gen.getByRole("checkbox", { name: /^Select all entries for / });
    const n = await boxes.count();
    for (let i = 0; i < n; i++) {
      const b = boxes.nth(i);
      const label = await b.getAttribute("aria-label");
      if (!label.includes(matterName) && (await b.isChecked())) await b.click();
    }
    await page.waitForTimeout(500);
  }
  console.log("generate dialog:", (await gen.innerText()).replace(/\s+/g, " ").slice(0, 400));
  await gen.getByRole("button", { name: "Validate & Create Draft" }).click();
  await gen.getByRole("button", { name: /^Create Draft/ }).click();
  await gen.waitFor({ state: "hidden", timeout: 15_000 });
  let created;
  for (let i = 0; i < 30; i++) {
    created = (await links()).find((h) => !before.has(h));
    if (created) break;
    await page.waitForTimeout(500);
  }
  if (!created) throw new Error("draft not found");
  await page.goto(`${BASE}${created}`, { waitUntil: "load" });
  await page.getByRole("heading", { level: 1, name: "Draft fee note" }).waitFor({ timeout: 30_000 });
  const due = isoDate(-daysAgo);
  await page.locator("#invoice-due-date").fill(due);
  const main = page.locator("main");
  await main.getByRole("button", { name: "Save Changes" }).click();
  await page.waitForTimeout(2000);
  await main.getByRole("button", { name: "Approve", exact: true }).click();
  await main.getByText("Approved", { exact: true }).waitFor({ timeout: 15_000 });
  await main.getByRole("button", { name: "Send fee note" }).click();
  const override = page.getByTestId("send-override-dialog");
  const sent = main.getByText("Sent", { exact: true });
  await sent.or(override).first().waitFor({ timeout: 15_000 });
  if (await override.isVisible()) await override.getByRole("button", { name: "Send Anyway" }).click();
  await sent.waitFor({ timeout: 15_000 });
  const header = (await main.innerText()).replace(/\s+/g, " ").slice(0, 400);
  console.log("fee note", created, "due", due, "→", header);
  await page.screenshot({ path: path.join(OUT, shot), fullPage: false });
}

async function main() {
  const step = process.argv[2];
  const browser = await chromium.launch();
  try {
    if (step === "aged") {
      const { context, page } = await login(browser, "alice");
      await agedFeeNote(page);
      await context.close();
    } else if (step === "aged2") {
      const { context, page } = await login(browser, "alice");
      await agedFeeNote(page, {
        clientKey: "sipho",
        matterKey: "siphoProperty",
        matterName: `Ndlovu Property Dispute ${run.runId}`,
        daysAgo: 45,
        shot: "seed-aged2-fee-note-sent.png",
      });
      await context.close();
    } else if (step === "bob" || step === "carol") {
      const { context, page } = await login(browser, step);
      const r = await logTime(page, run.matters.sipho, {
        hours: step === "bob" ? 3 : 1,
        minutes: step === "bob" ? 0 : 30,
        description: `640B OBS — ${step} unbilled work`,
      });
      console.log(step, "logged", r);
      await openGroupedTab(page, "finance", "time");
      await page.screenshot({ path: path.join(OUT, `seed-${step}-time-logged.png`), fullPage: false });
      await context.close();
    } else {
      throw new Error("step?");
    }
  } finally {
    await browser.close();
  }
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
