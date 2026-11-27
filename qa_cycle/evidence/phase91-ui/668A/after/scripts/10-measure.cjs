// 668A OBS-after extra measurements (read-only on the data; nothing saved, no item clicked that
// writes). Re-uses ../../before/scripts/lib.cjs (unedited). Usage: PHASE=after node 10-measure.cjs
//  - K-09 task tables at 1024 / 1280 / 1366 / 1440 (light; 1024 + 1440 dark): scroller widths,
//    visible columns, matter-badge truncation + title
//  - K-97 pager (Page n of m, Previous / Next) and row-open paths (title click, row click,
//    keyboard Tab + Enter), counting history pushes per activation
//  - K-96 header geometry at 1024 / 1440 (Alice + Carol)
//  - K-39 agenda text contrast composited on the canvas: My Work and Home (/dashboard)
//  - Diary + court-date badges: icon present, label, data-status; SCHEDULED rows carry none
//  - Court dates at 1024: CSS-injection prototypes for the +10 px badge width
//  - Notifications EmptyState variant (Carol)
// Output: ../measure.json + Home contact sheets (sheet-home-<who>.png) + crops → ../
const path = require("path");
const fs = require("fs");
const L = require("../../before/scripts/lib.cjs");

const S = L.readSeed();
const ID = S.ids;
const B = L.BASE;
const OUT = path.resolve(__dirname, "..");
const RAW = L.RAW;
const out = { at: new Date().toISOString(), k09: {}, k97: {}, rowOpen: {}, k96: {}, k39: {}, badges: {}, courtProto: {}, notif: {}, home: {}, console: [] };
const cons = out.console;
const MW = `${B}/my-work?from=2026-11-23&to=2026-11-29`;
const PUSH_INIT = () => {
  window.__pushes = 0;
  const p = history.pushState.bind(history);
  history.pushState = (...a) => { window.__pushes++; return p(...a); };
};

// In-page: composited WCAG contrast of an element's text on everything painted behind it.
const CONTRAST_FN = `
window.__rgba = (c) => { const cv = document.createElement('canvas'); cv.width = cv.height = 1; const x = cv.getContext('2d', { willReadFrequently: true }); x.clearRect(0,0,1,1); x.fillStyle = c; x.fillRect(0,0,1,1); return Array.from(x.getImageData(0,0,1,1).data); };
window.__contrast = (el) => {
  const cv = document.createElement('canvas'); cv.width = cv.height = 1; const x = cv.getContext('2d', { willReadFrequently: true });
  x.fillStyle = '#ffffff'; x.fillRect(0,0,1,1);
  const chain = []; for (let n = el; n; n = n.parentElement) chain.unshift(n);
  for (const n of chain) { const bg = getComputedStyle(n).backgroundColor; if (bg && bg !== 'rgba(0, 0, 0, 0)' && bg !== 'transparent') { x.fillStyle = bg; x.fillRect(0,0,1,1); } }
  const bg = Array.from(x.getImageData(0,0,1,1).data).slice(0,3);
  x.fillStyle = getComputedStyle(el).color; x.fillRect(0,0,1,1);
  const fg = Array.from(x.getImageData(0,0,1,1).data).slice(0,3);
  const lum = (a) => { const f = (v) => { v /= 255; return v <= 0.04045 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4); }; return 0.2126*f(a[0]) + 0.7152*f(a[1]) + 0.0722*f(a[2]); };
  const [l1, l2] = [lum(fg), lum(bg)].sort((a,b) => b - a);
  const hex = (a) => '#' + a.map(v => v.toString(16).padStart(2,'0')).join('');
  return { fg: hex(fg), bg: hex(bg), ratio: +((l1 + 0.05) / (l2 + 0.05)).toFixed(2) };
};`;

async function ctxFor(browser, state, w, theme, push = false) {
  const ctx = await L.newCtx(browser, state, { width: w, height: w <= 1024 ? 768 : 900, theme, extra: L.EXTRA });
  await ctx.addInitScript({ content: CONTRAST_FN });
  if (push) await ctx.addInitScript(PUSH_INIT);
  const page = await ctx.newPage();
  const lbl = { label: "" };
  L.watch(page, cons, lbl);
  return { ctx, page, lbl };
}
async function go(page, url) {
  await page.goto(url, { waitUntil: "load" });
  await page.waitForLoadState("networkidle").catch(() => {});
  await page.locator("#main-content h1").first().waitFor({ timeout: 20000 }).catch(() => {});
  await page.waitForTimeout(700);
}

// K-09: every table in main (task tables are DataTable frames)
const k09Probe = () => {
  const main = document.querySelector("#main-content");
  return [...main.querySelectorAll("table")].map((t) => {
    let sc = t.parentElement;
    while (sc && sc !== main && !/(auto|scroll)/.test(getComputedStyle(sc).overflowX)) sc = sc.parentElement;
    let sec = t, label = null;
    while (sec && sec !== main && !label) {
      const h = sec.querySelector("h2, h3, button[aria-expanded]");
      if (h && !t.contains(h)) label = h.innerText.replace(/\s+/g, " ").trim();
      sec = sec.parentElement;
    }
    const ths = [...t.querySelectorAll("thead th")].filter((th) => th.offsetWidth > 0).map((th) => `${th.innerText.trim()}:${th.offsetWidth}`);
    const badges = [...t.querySelectorAll("tbody tr td:first-child [data-slot=badge], tbody tr td:first-child span")].slice(0, 3).map((b) => ({ text: b.innerText.trim(), title: b.getAttribute("title"), w: b.clientWidth, sw: b.scrollWidth, truncated: b.scrollWidth > b.clientWidth + 1 }));
    return { section: label, tableW: t.offsetWidth, scrollerClient: sc ? sc.clientWidth : null, scrollerScroll: sc ? sc.scrollWidth : null, clip: sc ? sc.scrollWidth - sc.clientWidth : null, rows: t.querySelectorAll("tbody tr").length, visibleColumns: ths, matterBadges: badges };
  });
};

async function k09(browser, state, who) {
  for (const [w, theme] of [[1024, "light"], [1024, "dark"], [1280, "light"], [1366, "light"], [1440, "light"], [1440, "dark"]]) {
    const { ctx, page, lbl } = await ctxFor(browser, state, w, theme);
    lbl.label = `measure k09 ${who} ${w}-${theme}`;
    await go(page, MW);
    out.k09[`${who}/${w}-${theme}`] = { docOverflow: await page.evaluate(() => document.documentElement.scrollWidth - innerWidth), tables: await page.evaluate(k09Probe) };
    if (who === "alice" && w === 1280) await page.locator("#main-content").screenshot({ path: path.join(RAW, `m-k09-1280.png`) }).catch(() => {});
    // prototype (CSS injection, no source edit): force the hidden Status / Logged columns on and re-measure the clip
    if (who === "alice" && w >= 1280 && theme === "light") {
      const P = (out.k09proto = out.k09proto || {});
      for (const [k, css] of [["statusOn", "#main-content table th:nth-child(4), #main-content table td:nth-child(4){display:table-cell!important}"], ["statusLoggedOn", "#main-content table th:nth-child(4), #main-content table td:nth-child(4), #main-content table th:nth-child(6), #main-content table td:nth-child(6){display:table-cell!important}"]]) {
        const h = await page.addStyleTag({ content: css }); await page.waitForTimeout(200);
        P[`${w} ${k}`] = (await page.evaluate(k09Probe)).filter((t) => t.section === "Upcoming").map((t) => ({ clip: t.clip, cols: t.visibleColumns }));
        await h.evaluate((n) => n.remove());
      }
    }
    await ctx.close();
  }
}

async function k96(browser, state, who) {
  for (const [w, theme] of [[1024, "light"], [1024, "dark"], [1440, "light"]]) {
    const { ctx, page, lbl } = await ctxFor(browser, state, w, theme);
    lbl.label = `measure k96 ${who} ${w}-${theme}`;
    await go(page, MW);
    out.k96[`${who}/${w}-${theme}`] = await page.evaluate(() => {
      const hdr = document.querySelector('#main-content [data-slot="page-header"]');
      const r = (el) => { if (!el) return null; const b = el.getBoundingClientRect(); return { x: Math.round(b.x), y: Math.round(b.y), w: Math.round(b.width), h: Math.round(b.height), right: Math.round(b.right), bottom: Math.round(b.bottom) }; };
      const lines = (el) => el ? Math.round(el.getBoundingClientRect().height / parseFloat(getComputedStyle(el).lineHeight)) : null;
      const h1 = hdr && hdr.querySelector("h1"), desc = hdr && hdr.querySelector('[data-slot="page-header-description"]');
      const link = hdr && [...hdr.querySelectorAll("a")].find((a) => a.innerText.trim() === "Timesheet");
      const tb = hdr && hdr.querySelector('[data-slot="page-header-toolbar"]');
      const ctrls = tb ? [...tb.querySelectorAll("button")].map((b) => `${(b.innerText.trim() || b.getAttribute("aria-label"))} x${Math.round(b.getBoundingClientRect().x)}..${Math.round(b.getBoundingClientRect().right)} y${Math.round(b.getBoundingClientRect().y)}${b.disabled ? " (disabled)" : ""}`) : [];
      return { pageHeader: !!hdr, h1: r(h1), h1Lines: lines(h1), h1Font: h1 && getComputedStyle(h1).fontSize, descLines: lines(desc), titleBlock: r(h1 && h1.parentElement), timesheetLink: r(link), linkPastViewport: link ? Math.round(link.getBoundingClientRect().right - innerWidth) : null, toolbar: r(tb), toolbarBelowTitle: tb && h1 ? tb.getBoundingClientRect().top >= h1.getBoundingClientRect().bottom : null, toolbarControls: ctrls, headerScroll: hdr && `${hdr.scrollWidth}/${hdr.clientWidth}`, docScrollWidth: document.documentElement.scrollWidth, innerWidth };
    });
    if (w === 1024 && theme === "light") {
      await page.locator('#main-content [data-slot="page-header"]').screenshot({ path: path.join(OUT, `crop-k96-header-1024-light-${who}-measure.png`) }).catch(() => {});
      if (who === "alice") {
        const from = page.url();
        await page.locator('#main-content [data-slot="page-header-toolbar"]').getByRole("button", { name: "This Month", exact: true }).click();
        for (let i = 0; i < 60 && page.url() === from; i++) await page.waitForTimeout(150);
        out.k96[`${who}/${w}-${theme}`].thisMonthClick = new URL(page.url()).search;
      }
    }
    await ctx.close();
  }
}

async function pagerAndRowOpen(browser, state) {
  const { ctx, page, lbl } = await ctxFor(browser, state, 1440, "light", true);
  lbl.label = "measure k97/rowOpen alice 1440-light";
  await go(page, MW);
  const avail = page.locator('xpath=//*[normalize-space(text())="Available Tasks"]/ancestor::*[.//table][1]');
  const state97 = async () => ({
    pageText: (await avail.getByText(/^Page \d+ of \d+$/).allInnerTexts()).join("|"),
    rows: await avail.locator("tbody tr").count(),
    first: (await avail.locator("tbody tr").first().locator("[data-task-open]").innerText().catch(() => "")).trim(),
    prevDisabled: await avail.getByRole("button", { name: "Previous" }).isDisabled().catch(() => null),
    nextDisabled: await avail.getByRole("button", { name: "Next" }).isDisabled().catch(() => null),
    url: new URL(page.url()).search,
  });
  const k = (out.k97.alice = { p1: await state97() });
  k.rowsPerPageControl = await avail.getByText(/Rows per page/i).count();
  await avail.getByRole("button", { name: "Next" }).click(); await page.waitForTimeout(400); k.next1 = await state97();
  await avail.getByRole("button", { name: "Next" }).click(); await page.waitForTimeout(400); k.next2 = await state97();
  await avail.getByRole("button", { name: "Previous" }).click(); await page.waitForTimeout(400); k.prev = await state97();
  await avail.getByRole("button", { name: "Previous" }).click(); await page.waitForTimeout(400); k.prev2 = await state97();

  const R = out.rowOpen;
  const pushes = () => page.evaluate(() => window.__pushes);
  const sheetInfo = async () => {
    const dlg = page.getByRole("dialog");
    await dlg.first().waitFor({ timeout: 10000 }).catch(() => {});
    await page.waitForTimeout(600);
    return { dialogs: await dlg.count(), taskId: new URL(page.url()).searchParams.get("taskId"), heading: (await dlg.first().getByRole("heading").first().innerText().catch(() => "")).trim().slice(0, 80) };
  };
  const close = async () => { await page.keyboard.press("Escape"); await page.getByRole("dialog").first().waitFor({ state: "hidden", timeout: 8000 }).catch(() => {}); await page.waitForTimeout(500); return new URL(page.url()).searchParams.get("taskId"); };
  const row = avail.locator("tbody tr").first();
  const rowId = await row.locator("[data-task-open]").getAttribute("data-task-open");
  R.firstAvailableRowId = rowId;
  let p0 = await pushes();
  await row.locator("[data-task-open]").click();
  R.titleClick = { ...(await sheetInfo()), pushes: (await pushes()) - p0 };
  R.titleClick.afterClose = await close();
  p0 = await pushes();
  await row.locator("td:visible").last().click({ position: { x: 5, y: 5 } }); // Due Date cell (no control)
  R.rowClickDueCell = { ...(await sheetInfo()), pushes: (await pushes()) - p0 };
  R.rowClickDueCell.afterClose = await close();
  p0 = await pushes();
  await row.locator("td").first().locator("[title]").first().click(); // matter badge (not interactive)
  R.rowClickMatterBadge = { ...(await sheetInfo()), pushes: (await pushes()) - p0 };
  R.rowClickMatterBadge.afterClose = await close();
  // urgency (My Tasks) row
  const urow = page.locator("[data-task-open='" + ID.assignedTaskId + "']");
  if (await urow.count()) {
    p0 = await pushes();
    await urow.locator("xpath=ancestor::tr[1]").locator("td:visible").last().click({ position: { x: 5, y: 5 } });
    R.urgencyRowClick = { ...(await sheetInfo()), pushes: (await pushes()) - p0 };
    R.urgencyRowClick.afterClose = await close();
  }
  // keyboard: focus the Available Tasks toggle, Tab to the first title button, Enter
  await page.getByRole("button", { name: /Available Tasks/ }).focus();
  let tabs = 0, act = null;
  for (; tabs < 15; tabs++) {
    await page.keyboard.press("Tab");
    act = await page.evaluate(() => document.activeElement && (document.activeElement.getAttribute("data-task-open") || document.activeElement.tagName + " " + (document.activeElement.innerText || "").slice(0, 30)));
    if (/^[0-9a-f-]{36}$/.test(act || "")) break;
  }
  const focusVisible = await page.evaluate(() => { const a = document.activeElement; const cs = getComputedStyle(a); return { boxShadow: cs.boxShadow, outline: cs.outlineStyle, focusVisible: a.matches(":focus-visible") }; });
  p0 = await pushes();
  await page.keyboard.press("Enter");
  R.keyboard = { tabsFromToggle: tabs + 1, focused: act, focusVisible, ...(await sheetInfo()), pushes: (await pushes()) - p0 };
  await page.screenshot({ path: path.join(RAW, "m-row-open-keyboard.png") });
  R.keyboard.afterClose = await close();
  R.keyboardRowsFocusable = await avail.locator("tbody tr[tabindex]").count();
  await ctx.close();
}

async function k39(browser, state, who) {
  const sel = '[data-testid="todays-diary"], [data-testid="time-progress-today"], [data-testid="next-deadline"]';
  for (const [key, url] of [["my-work", MW], ["home", `${B}/dashboard`]]) {
    for (const v of ["1024-light", "1024-dark", "1440-light", "1440-dark"]) {
      const [w, theme] = v.split("-");
      const { ctx, page, lbl } = await ctxFor(browser, state, +w, theme);
      lbl.label = `measure k39 ${who} ${key} ${v}`;
      await go(page, url);
      out.k39[`${key} ${who}/${v}`] = await page.evaluate((s) => {
        const roots = [...document.querySelectorAll(s)];
        const seen = new Set(), res = [];
        for (const r of roots) for (const el of r.querySelectorAll("p, span, a, li")) {
          const own = [...el.childNodes].filter((n) => n.nodeType === 3).map((n) => n.textContent).join("").trim();
          if (!own || seen.has(el) || el.offsetWidth === 0) continue;
          seen.add(el);
          const c = window.__contrast(el);
          res.push(`${own.slice(0, 44)} — ${c.fg} on ${c.bg} ${c.ratio}${/slate-400/.test(el.className) ? " [slate-400]" : ""}`);
        }
        return res;
      }, sel);
      if (key === "home") {
        const f = path.join(RAW, `m-home--${who}--${v}.png`);
        await page.screenshot({ path: f, fullPage: true });
        (out.home[who] = out.home[who] || {})[v] = { file: f, h1: await page.locator("#main-content h1").count(), mainCount: await page.locator("main#main-content").count() };
      }
      await ctx.close();
    }
  }
}

async function badges(browser, state) {
  const { ctx, page, lbl } = await ctxFor(browser, state, 1440, "light");
  const read = () => page.evaluate(() => [...document.querySelectorAll("#main-content [data-testid^='diary-event-'], #main-content [data-testid^='diary-list-row-']")].map((r) => {
    const b = r.querySelector("[data-status]");
    return `${r.getAttribute("data-testid").replace(/-[0-9a-f-]{36}$/, "")} ${r.innerText.replace(/\s+/g, " ").slice(0, 60)} → ${b ? `${b.getAttribute("data-status")} "${b.innerText.trim()}" svg=${!!b.querySelector("svg")} aria-hidden=${b.querySelector("svg") && b.querySelector("svg").getAttribute("aria-hidden")} ${window.__contrast(b).ratio}` : "no badge"}`;
  }));
  for (const [k, u] of [["week", `${B}/diary?from=2026-11-23`], ["day", `${B}/diary?view=day&from=2026-11-26`], ["month", `${B}/diary?view=month&from=2026-11-01`], ["list", `${B}/diary?view=list&from=2026-11-01`]]) {
    lbl.label = `measure badges diary-${k}`;
    await go(page, u);
    await page.getByTestId("diary-range").waitFor({ timeout: 15000 }).catch(() => {});
    out.badges[`diary-${k}`] = { url: new URL(page.url()).search, rows: await read() };
  }
  await page.getByTestId(`diary-list-row-${ID.courtDates["POSTPONED-2026-11-26"]}`).screenshot({ path: path.join(OUT, "crop-diary-postponed-row-1440-light.png") }).catch(() => {});
  lbl.label = "measure badges court-dates";
  await go(page, `${B}/matters/${ID.khumaloMatterId}`);
  await page.getByTestId("tab-group-schedule").click();
  await page.getByTestId("court-date-list").waitFor({ timeout: 15000 });
  await page.waitForTimeout(600);
  out.badges.courtDates = await page.evaluate(() => [...document.querySelectorAll("[data-testid=court-date-list] tbody tr")].map((r) => {
    const b = r.querySelector("[data-status]");
    return `${r.cells[0].innerText.trim()} → ${b ? `${b.getAttribute("data-status")} "${b.innerText.trim()}" svg=${!!b.querySelector("svg")} w=${b.offsetWidth}` : "none"}`;
  }));
  await ctx.close();
}

async function courtProto(browser, state) {
  const { ctx, page, lbl } = await ctxFor(browser, state, 1024, "light");
  lbl.label = "measure court-dates 1024 prototypes";
  await go(page, `${B}/matters/${ID.khumaloMatterId}`);
  await page.getByTestId("tab-group-schedule").click();
  await page.getByTestId("court-date-list").waitFor({ timeout: 15000 });
  await page.waitForTimeout(600);
  const m = () => page.evaluate(() => {
    const t = document.querySelector("[data-testid=court-date-list] table") || document.querySelector("[data-testid=court-date-list]");
    let sc = t.parentElement; while (sc && !/(auto|scroll)/.test(getComputedStyle(sc).overflowX)) sc = sc.parentElement;
    const st = [...t.querySelectorAll("thead th")].map((th) => `${th.innerText.trim()}:${th.offsetWidth}`).join(" ");
    return { table: t.offsetWidth, scroller: sc && `${sc.scrollWidth}/${sc.clientWidth}`, docOverflow: document.documentElement.scrollWidth - innerWidth, cols: st };
  });
  const P = out.courtProto;
  P.asShipped = await m();
  const tries = {
    iconHidden: "[data-testid=court-date-list] [data-status] svg{display:none}",
    badgeGap0_5_icon3: "[data-testid=court-date-list] [data-status]{gap:2px}[data-testid=court-date-list] [data-status] svg{width:10px;height:10px}",
    statusCellPx2: "[data-testid=court-date-list] td:nth-child(7),[data-testid=court-date-list] th:nth-child(7){padding-left:8px;padding-right:8px}",
    statusAndActionsPx2: "[data-testid=court-date-list] td:nth-child(n+7),[data-testid=court-date-list] th:nth-child(n+7){padding-left:8px;padding-right:8px}",
  };
  for (const [k, css] of Object.entries(tries)) {
    const h = await page.addStyleTag({ content: css });
    await page.waitForTimeout(200);
    P[k] = { css, ...(await m()) };
    await h.evaluate((n) => n.remove());
  }
  await ctx.close();
}

async function notif(browser, state, who) {
  const { ctx, page, lbl } = await ctxFor(browser, state, 1440, "light");
  lbl.label = `measure notifications ${who}`;
  await go(page, `${B}/notifications`);
  out.notif[who] = await page.evaluate(() => ({
    h1: document.querySelectorAll("#main-content h1").length,
    pageHeader: !!document.querySelector('#main-content [data-slot="page-header"]'),
    toggles: [...document.querySelectorAll("#main-content button[aria-pressed]")].map((b) => `${b.innerText.trim()}=${b.getAttribute("aria-pressed")}`),
    empty: [...document.querySelectorAll('#main-content [data-slot="empty-state"]')].map((e) => `${e.getAttribute("data-variant")}: ${e.innerText.replace(/\s+/g, " ").trim().slice(0, 60)}`),
    card: !!document.querySelector('#main-content [data-slot="card"]'),
  }));
  await ctx.close();
}

(async () => {
  const browser = await L.chromium.launch();
  for (const who of ["alice", "carol"]) {
    const st = await L.loginState(browser, who);
    await k09(browser, st, who);
    await k96(browser, st, who);
    await k39(browser, st, who);
    await notif(browser, st, who);
    if (who === "alice") { await pagerAndRowOpen(browser, st); await badges(browser, st); await courtProto(browser, st); }
    console.log("measured", who);
  }
  for (const who of ["alice", "carol"]) {
    const h = out.home[who];
    await L.sheet(browser, path.join(OUT, `sheet-home-${who}.png`), `668A OBS-after — Home /dashboard — ${who} (K-39 reach)`, ["1024-light", "1024-dark", "1440-light", "1440-dark"].map((v) => [v, h && h[v] && h[v].file]), 2, 720);
  }
  await browser.close();
  fs.writeFileSync(path.join(OUT, "measure.json"), JSON.stringify(out));
  console.log(JSON.stringify(out).slice(0, 200), "console", cons.length);
})().catch((e) => { console.error("MEASURE FAIL", e); process.exit(1); });
