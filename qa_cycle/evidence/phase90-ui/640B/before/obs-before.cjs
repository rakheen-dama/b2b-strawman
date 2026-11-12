// 640B "before" OBS (task 640.7): Home widget screenshots on the mock-auth e2e stack (:3001).
// Usage: node obs-before.cjs <phase: empty|seeded> [themes=light,dark] [users=alice,carol]
// Writes PNGs + <phase>-cells.json next to this file and merges console messages into console.json.
const path = require("path");
const fs = require("fs");
const { chromium } = require(
  "/Users/rakheendama/Projects/2026/worktree-epic-640B/frontend/node_modules/@playwright/test"
);

const BASE = "http://localhost:3001";
const ORG = "e2e-test-org";
const OUT = __dirname;
const USERS = { alice: "user_e2e_alice", bob: "user_e2e_bob", carol: "user_e2e_carol" };

const phase = process.argv[2] || "seeded";
const themes = (process.argv[3] || "light,dark").split(",");
const users = (process.argv[4] || "alice,carol").split(",");

async function run() {
  const browser = await chromium.launch();
  const consoleFile = path.join(OUT, "console.json");
  const consoleLog = fs.existsSync(consoleFile) ? JSON.parse(fs.readFileSync(consoleFile, "utf8")) : {};
  const cellsReport = {};

  for (const user of users) {
    for (const theme of themes) {
      const key = `${phase}-${user}-1440-${theme}`;
      const context = await browser.newContext({ viewport: { width: 1440, height: 900 } });
      await context.addInitScript((t) => {
        try {
          localStorage.setItem("kazi-theme", t);
        } catch (e) {}
      }, theme);
      const page = await context.newPage();
      const messages = [];
      page.on("console", (m) => {
        if (m.type() === "error" || m.type() === "warning")
          messages.push({ type: m.type(), text: m.text().slice(0, 500), url: page.url() });
      });
      page.on("pageerror", (e) => messages.push({ type: "pageerror", text: String(e).slice(0, 500) }));

      await page.goto(`${BASE}/mock-login`, { waitUntil: "load" });
      await page.locator("#user-select").selectOption(USERS[user]);
      await page.getByRole("button", { name: "Sign In" }).click();
      await page.waitForURL(`**/org/${ORG}/dashboard`, { timeout: 60_000 });
      await page.waitForLoadState("load");
      const main = page.locator("#main-content");
      await main.getByRole("heading", { level: 1, name: "Home" }).waitFor({ timeout: 60_000 });
      const home = main.getByTestId("home-page");
      await home.waitFor({ timeout: 30_000 });
      await page.waitForTimeout(1500); // let client widgets/charts settle

      const htmlClass = await page.evaluate(() => document.documentElement.className);
      const audience = await home.getAttribute("data-audience");
      await page.screenshot({ path: path.join(OUT, `${key}-home-full.png`), fullPage: true });

      const cells = home.locator('[data-testid^="home-cell-"]');
      const n = await cells.count();
      const cellInfo = [];
      for (let i = 0; i < n; i++) {
        const cell = cells.nth(i);
        const testId = await cell.getAttribute("data-testid");
        const widget = testId.replace("home-cell-", "");
        await cell.scrollIntoViewIfNeeded();
        await cell.screenshot({ path: path.join(OUT, `${phase}-${user}-${widget}-1440-${theme}.png`) });
        const info = await cell.evaluate((el) => {
          const r = el.getBoundingClientRect();
          const inner = el.querySelector("[data-testid]");
          const truncated = Array.from(el.querySelectorAll(".truncate"))
            .filter((t) => t.scrollWidth > t.clientWidth + 1)
            .map((t) => t.textContent.trim())
            .slice(0, 15);
          return {
            className: el.className,
            width: Math.round(r.width),
            height: Math.round(r.height),
            innerTestId: inner ? inner.getAttribute("data-testid") : null,
            text: el.innerText.replace(/\s+/g, " ").trim().slice(0, 600),
            truncated,
          };
        });
        cellInfo.push({ widget, testId, ...info });
      }
      cellsReport[key] = { audience, htmlClass, cellCount: n, cells: cellInfo };
      consoleLog[key] = messages;
      console.log(`${key}: audience=${audience} html=${htmlClass} cells=${n} console=${messages.length}`);
      await context.close();
    }
  }
  await browser.close();
  fs.writeFileSync(consoleFile, JSON.stringify(consoleLog, null, 2));
  const cellsFile = path.join(OUT, `${phase}-cells.json`);
  const prev = fs.existsSync(cellsFile) ? JSON.parse(fs.readFileSync(cellsFile, "utf8")) : {};
  fs.writeFileSync(cellsFile, JSON.stringify({ ...prev, ...cellsReport }, null, 2));
}

run().catch((e) => {
  console.error(e);
  process.exit(1);
});
