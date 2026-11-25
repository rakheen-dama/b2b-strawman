// 661.3 data top-up #2 (UI only, never SQL). After run 1 of the matrix (05-shoot-matrix.cjs) the
// Home "Unbilled WIP" widget showed a single fee-earner row, not the WIP chart: the chart renders only
// for >= 2 fee earners with unbilled time (unbilled-wip-widget.tsx `earners.length >= 2`), and the
// lifecycle suite leaves unbilled time for Alice only. Bob (admin) logs 3h 00m BILLABLE today on the
// first task of Sipho's litigation matter, through the Log Time dialog, so Home draws the WIP chart.
// Usage: node 06-topup-second-earner.cjs <prototype frontend dir>
"use strict";
const fs = require("node:fs");
const os = require("node:os");
const path = require("node:path");

const [, , feDir] = process.argv;
const { chromium } = require(require.resolve("@playwright/test", { paths: [path.resolve(feDir)] }));
const BASE = "http://localhost:3001";
const ORG = `${BASE}/org/e2e-test-org`;
const log = (...a) => console.log(new Date().toISOString(), ...a);

function isoDaysAgo(n) {
  const d = new Date(Date.now() - n * 86_400_000);
  const z = (x) => String(x).padStart(2, "0");
  return `${d.getFullYear()}-${z(d.getMonth() + 1)}-${z(d.getDate())}`;
}

async function openTasks(page) {
  const group = page.getByTestId("tab-group-work").filter({ visible: true });
  const item = page.getByTestId("tab-item-tasks").filter({ visible: true });
  await group.waitFor({ state: "visible", timeout: 15_000 });
  for (let attempt = 0; attempt < 3; attempt++) {
    await group.click();
    const open = await item.waitFor({ state: "visible", timeout: 2_000 }).then(() => true, () => false);
    if (open) {
      await item.click();
      return;
    }
  }
  throw new Error("Work › Tasks sub-tab menu never opened");
}

async function logTime(page, { hours, date, billable, description }) {
  await openTasks(page);
  const row = page.getByRole("tabpanel").getByRole("row").filter({ has: page.getByRole("button", { name: "Log Time" }) }).first();
  await row.waitFor({ state: "visible", timeout: 15_000 });
  const task = (await row.getByRole("button", { name: /^Open task detail for / }).first().getAttribute("aria-label")) ?? "?";
  await row.getByRole("button", { name: "Log Time" }).click();
  const dialog = page.getByRole("dialog", { name: "Log Time" });
  await dialog.waitFor({ state: "visible" });
  await dialog.locator("#time-hours").fill(String(hours));
  await dialog.locator("#time-minutes").fill("0");
  await dialog.getByLabel("Date", { exact: true }).fill(date);
  await dialog.getByRole("textbox", { name: /^Description/ }).fill(description);
  const box = dialog.getByRole("checkbox", { name: "Billable" });
  if ((await box.isChecked()) !== billable) await box.click();
  await dialog.getByRole("button", { name: "Log Time" }).click();
  await dialog.waitFor({ state: "hidden", timeout: 15_000 });
  log(`logged ${hours}h on ${date} billable=${billable} (${task})`);
}

(async () => {
  const run = JSON.parse(fs.readFileSync(path.join(os.tmpdir(), "kazi-legal-lifecycle-run.json"), "utf8"));
  const matterUrl = `${ORG}/matters/${run.matters.sipho}`;
  const browser = await chromium.launch({ headless: true });
  const page = await (await browser.newContext({ viewport: { width: 1440, height: 900 } })).newPage();
  try {
    await page.goto(`${BASE}/mock-login`);
    await page.locator("#user-select").selectOption("user_e2e_bob");
    await page.getByRole("button", { name: "Sign In" }).click();
    await page.waitForURL(/\/org\/[^/]+\//, { timeout: 60_000 });
    await page.goto(matterUrl, { waitUntil: "load" });
    await logTime(page, { hours: 3, date: isoDaysAgo(0), billable: true, description: "661A data top-up: second fee earner (WIP chart)" });
    await page.goto(`${ORG}/dashboard`, { waitUntil: "load" });
    await page.waitForTimeout(3000);
    const kpi = await page.locator('[data-slot="kpi-row"]').innerText();
    log("Home KPI row after top-up:", JSON.stringify(kpi));
  } finally {
    await browser.close();
  }
})().catch((e) => {
  console.error("FAILED:", e);
  process.exit(1);
});
