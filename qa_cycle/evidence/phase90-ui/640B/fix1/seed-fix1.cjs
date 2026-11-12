// 640B fix1 seeding — browser-only (no SQL / REST writes) on the mock-auth stack. The stack was
// re-upped (fresh seed, 5 matters) before fix1, so the WIP chart (needs ≥ 2 fee earners with
// unbilled time) was absent. Usage: node seed-fix1.cjs <bob|carol|alice> [matterNameRegex]
// Logs billable time on the first task of the named matter (default: Khumalo Property).
const path = require("path");
const { chromium } = require(
  path.resolve(__dirname, "../../../../../frontend/node_modules/@playwright/test")
);

const BASE = "http://localhost:3001";
const ORG = "e2e-test-org";
const APP = `${BASE}/org/${ORG}`;
const OUT = __dirname;
const USERS = { alice: "user_e2e_alice", bob: "user_e2e_bob", carol: "user_e2e_carol" };
const HOURS = { alice: [2, 0], bob: [3, 0], carol: [1, 30] };

function isoDate(days = 0) {
  const sa = new Date(Date.now() + 2 * 3600 * 1000).toISOString().slice(0, 10);
  const [y, m, d] = sa.split("-").map(Number);
  return new Date(Date.UTC(y, m - 1, d + days)).toISOString().slice(0, 10);
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

(async () => {
  const user = process.argv[2];
  const matterRe = new RegExp(process.argv[3] || "Khumalo Property");
  const [hours, minutes] = HOURS[user];
  const browser = await chromium.launch();
  const context = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  const page = await context.newPage();
  await page.goto(`${BASE}/mock-login`, { waitUntil: "load" });
  await page.locator("#user-select").selectOption(USERS[user]);
  await page.getByRole("button", { name: "Sign In" }).click();
  await page.waitForURL(`**/org/${ORG}/dashboard`, { timeout: 60_000 });
  await page.goto(`${APP}/matters`, { waitUntil: "load" });
  const link = page.locator("#main-content").getByRole("link", { name: matterRe }).first();
  await link.waitFor({ timeout: 30_000 });
  await link.click();
  await page.waitForURL(/\/matters\/[0-9a-f-]{36}/, { timeout: 30_000 });
  await page.getByRole("heading", { level: 1 }).waitFor({ timeout: 30_000 });
  await openGroupedTab(page, "work", "tasks");
  const panel = page.getByRole("tabpanel");
  const btn = panel.getByRole("button", { name: "Log Time" }).first();
  await btn.waitFor({ timeout: 15_000 });
  await btn.click();
  const dialog = page.getByRole("dialog", { name: "Log Time" });
  await dialog.waitFor();
  await dialog.locator("#time-hours").fill(String(hours));
  await dialog.locator("#time-minutes").fill(String(minutes));
  await dialog.getByLabel("Date", { exact: true }).fill(isoDate());
  await dialog.getByRole("textbox", { name: /^Description/ }).fill(`640B fix1 OBS — ${user} unbilled work`);
  const billable = dialog.getByRole("checkbox", { name: "Billable" });
  if (!(await billable.isChecked())) throw new Error("Billable not checked by default");
  await dialog.getByRole("button", { name: "Log Time" }).click();
  await dialog.waitFor({ state: "hidden", timeout: 15_000 });
  await openGroupedTab(page, "finance", "time");
  await page.waitForTimeout(1000);
  await page.screenshot({ path: path.join(OUT, `seed-${user}-time-logged.png`), fullPage: false });
  console.log(user, "logged", hours, minutes, page.url());
  await browser.close();
})().catch((e) => {
  console.error(e);
  process.exit(1);
});
