// 640B "after" OBS (task 640.11): Home widget screenshots + measurements on the mock-auth e2e stack (:3001).
// Usage: node obs-after.cjs <phase: empty|seeded> [widths=1024,1440] [themes=light,dark] [users=alice,carol]
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
const VIEWPORTS = { 1024: { width: 1024, height: 768 }, 1440: { width: 1440, height: 900 } };

const phase = process.argv[2] || "seeded";
const widths = (process.argv[3] || "1024,1440").split(",");
const themes = (process.argv[4] || "light,dark").split(",");
const users = (process.argv[5] || "alice,carol").split(",");

/** Runs inside the page: measurements for one home cell. */
function measureCell(el) {
  const r = el.getBoundingClientRect();
  const parseRgb = (s) => {
    const m = s && s.match(/rgba?\(([^)]+)\)/);
    if (!m) return null;
    const p = m[1].split(/[ ,/]+/).filter(Boolean).map(Number);
    return { r: p[0], g: p[1], b: p[2], a: p.length > 3 ? p[3] : 1 };
  };
  // Resolve oklch/lab etc. via a canvas round-trip.
  const cv = document.createElement("canvas"); cv.width = 1; cv.height = 1;
  const ctx = cv.getContext("2d", { willReadFrequently: true });
  const toRgb = (c) => {
    if (!c) return null;
    if (/^rgba?\(/.test(c)) return parseRgb(c);
    const am = c.match(/\/\s*([\d.]+%?)\s*\)$/);
    let alpha = 1;
    if (am) alpha = am[1].endsWith("%") ? parseFloat(am[1]) / 100 : parseFloat(am[1]);
    if (c === "transparent") return { r: 0, g: 0, b: 0, a: 0 };
    ctx.clearRect(0, 0, 1, 1);
    ctx.fillStyle = "#000";
    ctx.fillStyle = c.replace(/\/\s*[\d.]+%?\s*\)$/, ")");
    ctx.fillRect(0, 0, 1, 1);
    const d = ctx.getImageData(0, 0, 1, 1).data;
    return { r: d[0], g: d[1], b: d[2], a: alpha };
  };
  const lum = ({ r, g, b }) => {
    const f = (x) => {
      x /= 255;
      return x <= 0.03928 ? x / 12.92 : Math.pow((x + 0.055) / 1.055, 2.4);
    };
    return 0.2126 * f(r) + 0.7152 * f(g) + 0.0722 * f(b);
  };
  const bgOf = (node) => {
    let n = node;
    while (n && n.nodeType === 1) {
      const c = toRgb(getComputedStyle(n).backgroundColor);
      if (c && c.a > 0.5) return c;
      n = n.parentElement;
    }
    return toRgb(getComputedStyle(document.body).backgroundColor) || { r: 255, g: 255, b: 255, a: 1 };
  };
  const contrast = (a, b) => {
    const l1 = lum(a);
    const l2 = lum(b);
    return (Math.max(l1, l2) + 0.05) / (Math.min(l1, l2) + 0.05);
  };

  // Truncation: any element whose content overflows horizontally with text-overflow/overflow hidden.
  const truncated = Array.from(el.querySelectorAll("*"))
    .filter((t) => {
      const cs = getComputedStyle(t);
      return (
        (cs.textOverflow === "ellipsis" || cs.overflow === "hidden" || cs.overflowX === "hidden") &&
        t.clientWidth > 1 &&
        t.scrollWidth > t.clientWidth + 1 &&
        t.textContent.trim().length > 0 &&
        t.children.length === 0
      );
    })
    .map((t) => ({ text: t.textContent.trim(), sw: t.scrollWidth, cw: t.clientWidth }));

  // Low-contrast visible text leaves.
  const lowContrast = [];
  let minContrast = 99;
  for (const t of el.querySelectorAll("*")) {
    if (t.children.length > 0) continue;
    const txt = (t.textContent || "").trim();
    if (!txt) continue;
    const cs = getComputedStyle(t);
    const rr = t.getBoundingClientRect();
    if (rr.width === 0 || rr.height === 0 || cs.visibility === "hidden" || cs.opacity === "0") continue;
    if (cs.position === "absolute" && rr.width <= 1) continue; // sr-only
    const fg = toRgb(t.tagName === "text" || t.tagName === "tspan" ? cs.fill : cs.color);
    if (!fg) continue;
    const bg = bgOf(t);
    const c = contrast(fg, bg);
    if (c < minContrast) minContrast = c;
    if (c < 4.5) lowContrast.push({ text: txt.slice(0, 40), contrast: +c.toFixed(2), fg: cs.color, fontSize: cs.fontSize });
  }

  // White surfaces (for dark mode check).
  const whiteSurfaces = Array.from(el.querySelectorAll("*"))
    .filter((t) => {
      const c = toRgb(getComputedStyle(t).backgroundColor);
      const rr = t.getBoundingClientRect();
      return c && c.a > 0.5 && c.r > 240 && c.g > 240 && c.b > 240 && rr.width > 8 && rr.height > 8;
    })
    .map((t) => `${t.tagName.toLowerCase()}.${String(t.className).slice(0, 80)}`)
    .slice(0, 10);

  // Frames: elements with a visible border or box-shadow + background (card-like) nested in the cell.
  const frames = Array.from(el.querySelectorAll("*"))
    .filter((t) => {
      const cs = getComputedStyle(t);
      const rr = t.getBoundingClientRect();
      if (rr.width < 120 || rr.height < 60) return false;
      const border =
        parseFloat(cs.borderTopWidth) > 0 &&
        parseFloat(cs.borderLeftWidth) > 0 &&
        parseFloat(cs.borderRightWidth) > 0 &&
        parseFloat(cs.borderBottomWidth) > 0;
      const shadow = cs.boxShadow && cs.boxShadow !== "none";
      return border || shadow;
    })
    .map((t) => ({
      tag: t.tagName.toLowerCase(),
      slot: t.getAttribute("data-slot"),
      testid: t.getAttribute("data-testid"),
      w: Math.round(t.getBoundingClientRect().width),
      h: Math.round(t.getBoundingClientRect().height),
    }));

  // Empty space at the bottom of the (stretched) card: card bottom − last content bottom.
  const card = el.firstElementChild;
  let emptyBottom = null;
  if (card) {
    const cr = card.getBoundingClientRect();
    let maxBottom = cr.top;
    for (const d of card.querySelectorAll("*")) {
      const dr = d.getBoundingClientRect();
      if (dr.width === 0 || dr.height === 0) continue;
      const cs = getComputedStyle(d);
      if (cs.position === "absolute" && dr.width <= 1) continue;
      // Ignore stretched layout containers: only leaves / text / svg / buttons.
      if (d.children.length === 0 || d.tagName === "svg" || d.tagName === "BUTTON") maxBottom = Math.max(maxBottom, dr.bottom);
    }
    emptyBottom = Math.round(cr.bottom - maxBottom);
  }

  const titleEl = el.querySelector('[data-slot="card-title"]');
  const tcs = titleEl ? getComputedStyle(titleEl) : null;

  return {
    className: el.className,
    width: Math.round(r.width),
    height: Math.round(r.height),
    innerTestId: el.querySelector("[data-testid]")?.getAttribute("data-testid") ?? null,
    text: el.innerText.replace(/\s+/g, " ").trim().slice(0, 600),
    truncated,
    title: titleEl
      ? { text: titleEl.textContent.trim(), fontSize: tcs.fontSize, fontWeight: tcs.fontWeight, color: tcs.color }
      : null,
    minContrast: +minContrast.toFixed(2),
    lowContrast: lowContrast.slice(0, 12),
    whiteSurfaces,
    frames,
    cardCount: el.querySelectorAll('[data-slot="card"]').length,
    emptyBottom,
  };
}

/** Matter-health specifics. */
function measureMatterHealth(panel) {
  const header = Array.from(panel.querySelectorAll('[role="columnheader"], span')).filter(
    (s) => /^(Matter|Status|Progress|Hours|Tasks)/i.test(s.textContent.trim()) && s.closest(".border-b")
  );
  const cols = {};
  for (const h of header) {
    const name = h.textContent.trim().replace(/[^A-Za-z]/g, "");
    const r = h.getBoundingClientRect();
    cols[name] = { visible: r.width > 0 && getComputedStyle(h).display !== "none", w: Math.round(r.width) };
  }
  const rows = Array.from(panel.querySelectorAll("button")).filter((b) => b.querySelector("p.truncate"));
  const rowInfo = rows.map((b) => {
    const name = b.querySelector("p.truncate");
    const badge = b.querySelector("[data-tone]");
    const label = badge ? badge.querySelector("span") : null;
    const lcs = label ? getComputedStyle(label) : null;
    return {
      name: name.textContent.trim(),
      nameTruncated: name.scrollWidth > name.clientWidth + 1,
      nameW: name.clientWidth,
      nameSW: name.scrollWidth,
      badgeTone: badge?.getAttribute("data-tone"),
      badgeW: badge ? Math.round(badge.getBoundingClientRect().width) : null,
      labelText: label?.textContent,
      labelSrOnly: lcs ? lcs.position === "absolute" && label.getBoundingClientRect().width <= 1 : null,
    };
  });
  const content = panel.querySelector('[data-slot="card-content"]');
  return {
    containerWidth: content ? Math.round(content.getBoundingClientRect().width) : null,
    cols,
    rowCount: rowInfo.length,
    truncatedNames: rowInfo.filter((r) => r.nameTruncated).length,
    rows: rowInfo,
  };
}

/** Unbilled WIP chart specifics. */
function measureWip(w) {
  const svg = w.querySelector("svg.recharts-surface");
  const out = { hasChart: !!svg, variant: w.getAttribute("data-variant") };
  if (!svg) return out;
  const sr = svg.getBoundingClientRect();
  const wr = w.getBoundingClientRect();
  out.svg = { w: Math.round(sr.width), h: Math.round(sr.height), left: Math.round(sr.left), right: Math.round(sr.right) };
  out.widgetRect = { left: Math.round(wr.left), right: Math.round(wr.right) };
  const yTicks = Array.from(svg.querySelectorAll(".recharts-yAxis-tick-labels text"));
  out.yTicks = yTicks.map((t) => {
    const b = t.getBoundingClientRect();
    return { text: t.textContent, left: Math.round(b.left), right: Math.round(b.right), clippedLeft: b.left < sr.left - 0.5 };
  });
  const labels = Array.from(svg.querySelectorAll(".recharts-label-list text"));
  out.valueLabels = labels.map((t) => {
    const b = t.getBoundingClientRect();
    return { text: t.textContent, left: Math.round(b.left), right: Math.round(b.right), clippedRight: b.right > sr.right + 0.5 };
  });
  const bars = Array.from(svg.querySelectorAll(".recharts-bar-rectangle path, .recharts-rectangle"));
  out.bars = bars.map((b) => {
    const r = b.getBoundingClientRect();
    return { w: Math.round(r.width), h: Math.round(r.height) };
  });
  const xTicks = Array.from(svg.querySelectorAll(".recharts-xAxis-tick-labels text"));
  out.xTicks = xTicks.map((t) => {
    const b = t.getBoundingClientRect();
    return { text: t.textContent, left: Math.round(b.left), right: Math.round(b.right), clipped: b.right > sr.right + 0.5 || b.left < sr.left - 0.5 };
  });
  return out;
}

async function run() {
  const browser = await chromium.launch();
  const consoleFile = path.join(OUT, "console.json");
  const consoleLog = fs.existsSync(consoleFile) ? JSON.parse(fs.readFileSync(consoleFile, "utf8")) : {};
  const cellsReport = {};

  for (const user of users) {
    for (const w of widths) {
      for (const theme of themes) {
        const key = `${phase}-${user}-${w}-${theme}`;
        const context = await browser.newContext({ viewport: VIEWPORTS[w] });
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
        // Let client widgets settle: the WIP chart is a dynamic import and recharts draws its value
        // labels only once the bar animation ends. Park the pointer so no hover tooltip is captured.
        await page.mouse.move(0, 0);
        const wipSvg = home.locator('[data-testid="unbilled-wip-widget"] svg.recharts-surface');
        if (await home.locator('[data-slot="wip-chart"]').count()) await wipSvg.waitFor({ timeout: 30_000 });
        await page.waitForTimeout(5000);

        const htmlClass = await page.evaluate(() => document.documentElement.className);
        const audience = await home.getAttribute("data-audience");
        await page.screenshot({ path: path.join(OUT, `${phase}-${user}-${w}-${theme}-home-full.png`), fullPage: true });

        const cells = home.locator('[data-testid^="home-cell-"]');
        const n = await cells.count();
        const cellInfo = [];
        for (let i = 0; i < n; i++) {
          const cell = cells.nth(i);
          const testId = await cell.getAttribute("data-testid");
          const widget = testId.replace("home-cell-", "");
          await cell.scrollIntoViewIfNeeded();
          await page.mouse.move(0, 0);
          await page.waitForTimeout(300);
          await cell.screenshot({ path: path.join(OUT, `${phase}-${user}-${widget}-${w}-${theme}.png`) });
          const info = await cell.evaluate(measureCell);
          const rect = await cell.evaluate((el) => {
            const r = el.getBoundingClientRect();
            return { x: Math.round(r.left + window.scrollX), y: Math.round(r.top + window.scrollY) };
          });
          let extra = null;
          if (widget === "matterHealth") {
            extra = await cell.locator('[data-testid="matter-health-panel"]').evaluate(measureMatterHealth).catch((e) => String(e));
          } else if (widget === "unbilledWip") {
            extra = await cell.locator('[data-testid="unbilled-wip-widget"]').evaluate(measureWip).catch((e) => String(e));
          } else if (widget === "activity") {
            extra = { testidCount: await cell.locator('[data-testid="recent-activity-widget"]').count() };
          }
          cellInfo.push({ widget, testId, ...rect, ...info, extra });
        }
        cellsReport[key] = { audience, htmlClass, cellCount: n, cells: cellInfo };
        consoleLog[key] = messages;
        console.log(`${key}: audience=${audience} html=${htmlClass} cells=${n} console=${messages.length}`);
        await context.close();
      }
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
