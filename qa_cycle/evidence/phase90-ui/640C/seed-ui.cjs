// 640C OBS seeding — UI only (no SQL, no REST): logs time entries through the matter's
// Work → Tasks → "Log Time" dialog on the mock-auth e2e stack (:3001).
// Usage: node seed-ui.cjs <user> '<entries JSON>' [matterName]
//   entries: [{ "hours": 1, "minutes": 0, "date": "2026-11-12", "billable": false, "description": "..." }]
//   matterName omitted → the first matter in the user's Matters list that has a task with "Log Time".
const path = require("path");
const fs = require("fs");
const { chromium } = require(
  "/Users/rakheendama/Projects/2026/worktree-epic-640C/frontend/node_modules/@playwright/test"
);

const BASE = "http://localhost:3001";
const ORG = "e2e-test-org";
const OUT = __dirname;
const USERS = { alice: "user_e2e_alice", bob: "user_e2e_bob", carol: "user_e2e_carol" };
const user = process.argv[2];
const entries = JSON.parse(process.argv[3]);
const matterName = process.argv[4] || null;

async function openTasks(page) {
  const group = page.getByTestId("tab-group-work").filter({ visible: true });
  const item = page.getByTestId("tab-item-tasks").filter({ visible: true });
  await group.waitFor({ timeout: 60_000 });
  for (let i = 0; i < 3; i++) {
    await group.click();
    if (await item.waitFor({ state: "visible", timeout: 3000 }).then(() => true, () => false)) {
      await item.click();
      return;
    }
  }
  throw new Error("Work → Tasks menu never opened");
}

(async () => {
  const browser = await chromium.launch();
  const context = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  const page = await context.newPage();
  const log = [];
  page.on("console", (m) => {
    if (m.type() === "error") log.push(m.text().slice(0, 300));
  });
  await page.goto(`${BASE}/mock-login`);
  await page.locator("#user-select").selectOption(USERS[user]);
  await page.getByRole("button", { name: "Sign In" }).click();
  await page.waitForURL(`**/org/${ORG}/dashboard`, { timeout: 90_000 });

  await page.goto(`${BASE}/org/${ORG}/matters`);
  await page.locator("#main-content").getByRole("heading", { level: 1 }).first().waitFor({ timeout: 60_000 });
  await page.waitForTimeout(2000);
  const links = page.locator('#main-content a[href*="/matters/"]');
  const hrefs = [];
  for (let i = 0; i < (await links.count()); i++) {
    const href = await links.nth(i).getAttribute("href");
    const text = (await links.nth(i).innerText()).trim();
    if (/\/matters\/[0-9a-f-]{36}$/.test(href) && !hrefs.find((h) => h.href === href)) hrefs.push({ href, text });
  }
  const candidates = matterName ? hrefs.filter((h) => h.text.includes(matterName)) : hrefs;
  console.log(`matters visible to ${user}: ${hrefs.length}; candidates: ${candidates.length}`, JSON.stringify(hrefs.map((h) => h.text.slice(0, 60))));

  let done = false;
  for (const m of candidates.slice(0, 8)) {
    await page.goto(`${BASE}${m.href}`);
    await openTasks(page);
    await page.waitForTimeout(1500);
    const logBtn = page.getByRole("tabpanel").getByRole("button", { name: "Log Time" }).first();
    if (!(await logBtn.isVisible().catch(() => false))) {
      console.log(`no Log Time on "${m.text}"`);
      continue;
    }
    for (const e of entries) {
      await logBtn.click();
      const dialog = page.getByRole("dialog", { name: "Log Time" });
      await dialog.waitFor();
      await dialog.locator("#time-hours").fill(String(e.hours));
      await dialog.locator("#time-minutes").fill(String(e.minutes));
      await dialog.locator("#time-date").fill(e.date);
      await dialog.locator("#time-description").fill(e.description);
      const cb = dialog.locator("#time-billable");
      if ((await cb.isChecked()) !== e.billable) await cb.click();
      await page.waitForTimeout(500);
      await dialog.screenshot({ path: path.join(OUT, `seed-${user}-${e.date}-${e.billable ? "billable" : "nonbillable"}-dialog.png`) });
      await dialog.getByRole("button", { name: "Log Time" }).click();
      await dialog.waitFor({ state: "hidden", timeout: 30_000 });
      console.log(`logged ${e.hours}h${e.minutes}m ${e.billable ? "billable" : "non-billable"} on ${e.date} for ${user} on "${m.text}"`);
      await page.waitForTimeout(1000);
    }
    done = true;
    break;
  }
  fs.appendFileSync(path.join(OUT, "seed-ui.log"), `${new Date().toISOString()} ${user} ${JSON.stringify(entries)} done=${done} consoleErrors=${JSON.stringify(log)}\n`);
  await browser.close();
  if (!done) {
    console.error("no matter with a loggable task found");
    process.exit(1);
  }
})().catch((e) => {
  console.error(e);
  process.exit(1);
});
