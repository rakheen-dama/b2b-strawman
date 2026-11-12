// 640B fix1 OBS: (F1) Matter Health column visibility / name truncation by viewport, and
// (F2) the kit chart's keyboard focus ring (WIP BarChartPreset) vs mouse focus.
// Usage: node obs-fix1.cjs   (e2e mock stack on :3001, lifecycle-seeded tenant)
const path = require("path");
const fs = require("fs");
const { chromium } = require(
  path.resolve(__dirname, "../../../../../frontend/node_modules/@playwright/test")
);

const BASE = "http://localhost:3001";
const ORG = "e2e-test-org";
const OUT = __dirname;
const USERS = { alice: "user_e2e_alice", carol: "user_e2e_carol" };
const VIEWPORTS = {
  1024: { width: 1024, height: 768 },
  1280: { width: 1280, height: 800 },
  1440: { width: 1440, height: 900 },
  1920: { width: 1920, height: 1080 },
};
const RUNS = [
  ...[1024, 1280, 1440, 1920].flatMap((w) => ["light", "dark"].map((t) => ({ user: "alice", w, t }))),
  { user: "carol", w: 1920, t: "light" },
];

function measureMatterHealth(panel) {
  const content = panel.querySelector('[data-slot="card-content"]');
  const cs = getComputedStyle(content);
  const contentBox =
    content.getBoundingClientRect().width - parseFloat(cs.paddingLeft) - parseFloat(cs.paddingRight);
  const headerRow = panel.querySelector(".border-b");
  if (!headerRow) return { containerContentBox: Math.round(contentBox), empty: panel.innerText.replace(/\s+/g, " ") };
  const cols = {};
  for (const h of Array.from(headerRow.children)) {
    const name = h.textContent.trim().replace(/[^A-Za-z]/g, "");
    const r = h.getBoundingClientRect();
    cols[name] = r.width > 0 && getComputedStyle(h).display !== "none" ? Math.round(r.width) : 0;
  }
  const rows = Array.from(panel.querySelectorAll("button")).filter((b) => b.querySelector("p.truncate"));
  const rowInfo = rows.map((b) => {
    const name = b.querySelector("p.truncate");
    const label = b.querySelector("[data-tone] span");
    const lr = label.getBoundingClientRect();
    return {
      name: name.textContent.trim(),
      nameW: name.clientWidth,
      truncated: name.scrollWidth > name.clientWidth,
      label: label.textContent,
      labelSrOnly: getComputedStyle(label).position === "absolute" && lr.width <= 1,
    };
  });
  return {
    containerContentBox: Math.round(contentBox),
    columnsVisible: Object.entries(cols).filter(([, w]) => w > 0).map(([n]) => n),
    colWidths: cols,
    rowCount: rowInfo.length,
    truncated: rowInfo.filter((r) => r.truncated).length,
    statusLabel: rowInfo.every((r) => r.labelSrOnly) ? "sr-only" : rowInfo.every((r) => !r.labelSrOnly) ? "visible" : "mixed",
    rows: rowInfo,
  };
}

async function login(browser, user, w, theme) {
  const context = await browser.newContext({ viewport: VIEWPORTS[w] });
  await context.addInitScript((t) => {
    try {
      localStorage.setItem("kazi-theme", t);
    } catch (e) {}
  }, theme);
  const page = await context.newPage();
  await page.goto(`${BASE}/mock-login`, { waitUntil: "load" });
  await page.locator("#user-select").selectOption(USERS[user]);
  await page.getByRole("button", { name: "Sign In" }).click();
  await page.waitForURL(`**/org/${ORG}/dashboard`, { timeout: 60_000 });
  await page.waitForLoadState("load");
  await page.locator("#main-content").getByRole("heading", { level: 1, name: "Home" }).waitFor({ timeout: 60_000 });
  await page.mouse.move(0, 0);
  return { context, page };
}

const ringStyle = (el) => {
  const cs = getComputedStyle(el);
  return {
    tag: el.tagName,
    cls: String(el.getAttribute("class")),
    focusVisible: el.matches(":focus-visible"),
    outline: `${cs.outlineStyle} ${cs.outlineWidth} ${cs.outlineColor}`,
    outlineOffset: cs.outlineOffset,
    boxShadow: cs.boxShadow,
  };
};

(async () => {
  const browser = await chromium.launch();
  const report = { matterHealth: {}, focusRing: {} };

  for (const { user, w, t } of RUNS) {
    const key = `${user}-${w}-${t}`;
    const { context, page } = await login(browser, user, w, t);
    const panel = page.locator("#main-content").getByTestId("matter-health-panel");
    await panel.waitFor({ timeout: 30_000 });
    await panel.scrollIntoViewIfNeeded();
    await page.waitForTimeout(1500);
    report.matterHealth[key] = await panel.evaluate(measureMatterHealth);
    await page.getByTestId("home-cell-matterHealth").screenshot({ path: path.join(OUT, `matterHealth-${key}.png`) });
    const m = report.matterHealth[key];
    console.log(m.empty ? `${key}: box=${m.containerContentBox} empty: ${m.empty}` : `${key}: box=${m.containerContentBox} cols=${m.columnsVisible.join(",")} nameW=${m.rows[0].nameW} truncated=${m.truncated}/${m.rowCount} label=${m.statusLabel}`);

    // F2: keyboard focus ring on the WIP chart (alice, 1440 + 1024)
    if (user === "alice" && (w === 1440 || w === 1024)) {
      const widget = page.locator("#main-content").getByTestId("unbilled-wip-widget");
      const svg = widget.locator("svg.recharts-surface");
      await svg.waitFor({ timeout: 30_000 });
      await widget.scrollIntoViewIfNeeded();
      await page.waitForTimeout(2500);
      // Mouse focus first: click the chart surface — no ring expected (focus without focus-visible).
      await svg.click({ position: { x: 5, y: 5 } });
      await page.waitForTimeout(300);
      const mouse = await page.evaluate(() => {
        const a = document.activeElement;
        return a && a.matches("svg.recharts-surface") ? true : false;
      });
      const mouseRing = mouse ? await svg.evaluate(ringStyle) : { note: "click did not focus the svg" };
      await page.mouse.move(0, 0);
      // Keyboard: Tab from the top until the chart surface has focus.
      await page.evaluate(() => {
        document.activeElement && document.activeElement.blur();
        window.scrollTo(0, 0);
      });
      await page.locator("body").click({ position: { x: 1, y: 1 } }).catch(() => {});
      let tabs = 0;
      for (; tabs < 150; tabs++) {
        await page.keyboard.press("Tab");
        const inChart = await page.evaluate(() => {
          const a = document.activeElement;
          const w = document.querySelector('[data-testid="unbilled-wip-widget"]');
          return !!(a && w && w.contains(a) && a.matches("svg.recharts-surface"));
        });
        if (inChart) break;
      }
      await page.waitForTimeout(400);
      const kb = await page.evaluate(() => {
        const a = document.activeElement;
        const cs = getComputedStyle(a);
        return {
          tag: a.tagName,
          cls: String(a.getAttribute("class")),
          focusVisible: a.matches(":focus-visible"),
          outline: `${cs.outlineStyle} ${cs.outlineWidth} ${cs.outlineColor}`,
          outlineOffset: cs.outlineOffset,
          boxShadow: cs.boxShadow,
        };
      });
      await widget.screenshot({ path: path.join(OUT, `wip-keyboard-focus-${w}-${t}.png`) });
      report.focusRing[`${w}-${t}`] = { tabsToChart: tabs + 1, keyboard: kb, mouseClick: mouseRing };
      console.log(`focus ${w}-${t}: kb=${kb.outline} fv=${kb.focusVisible} | mouse=${mouseRing.outline} fv=${mouseRing.focusVisible}`);
    }
    await context.close();
  }
  await browser.close();
  fs.writeFileSync(path.join(OUT, "matter-health-widths.json"), JSON.stringify(report.matterHealth, null, 1));
  fs.writeFileSync(path.join(OUT, "chart-focus-ring.json"), JSON.stringify(report.focusRing, null, 1));
})().catch((e) => {
  console.error(e);
  process.exit(1);
});
