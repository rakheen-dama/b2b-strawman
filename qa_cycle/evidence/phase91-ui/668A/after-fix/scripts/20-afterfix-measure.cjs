// 668A OBS-after-fix measurements (read-only on the data: nothing saved; menus/sheets opened and
// Escaped). Re-uses ../../before/scripts/lib.cjs (unedited). Usage: PHASE=after-fix node 20-afterfix-measure.cjs
//  - F-668A-A-1: court-date list (Khumalo matter, Schedule tab) at 1024 light/dark + 1440 light:
//    table / scroller widths, Status column width, document h-overflow, badge icon + label per row,
//    plus one CSS-injection reference (Actions px-2 on top of the shipped fix)
//  - F-668A-A-3: My Work task tables (Alice) at 1024/1280/1366/1440 light + 1024/1440 dark:
//    scroll/client per table, visible + fully-in-view columns, matter badge truncation + title
//  - single row-activation path: title click, plain-cell click, matter badge click, My Tasks row
//    click, Tab→title + Enter, Tab→title + Space, pager Next/Previous (must NOT open) — history
//    pushes and dialog count per activation
//  - pager clamp: Page 1→2→3→2→1, and open/close a task sheet on pages 2 and 3 (page must not jump)
//  - urgency grouping: My Tasks group headings + rows per group
// Output: ../measure.json (single line) + crops → ../
const path = require("path");
const fs = require("fs");
const L = require("../../before/scripts/lib.cjs");

const S = L.readSeed();
const ID = S.ids;
const B = L.BASE;
const OUT = path.resolve(__dirname, "..");
const out = { at: new Date().toISOString(), court: {}, courtProto: {}, k09: {}, activation: {}, pager: {}, urgency: {}, console: [] };
const cons = out.console;
const MW = `${B}/my-work?from=2026-11-23&to=2026-11-29`;
const PUSH_INIT = () => {
  window.__pushes = 0;
  const p = history.pushState.bind(history);
  history.pushState = (...a) => { window.__pushes++; return p(...a); };
};

async function ctxFor(browser, state, w, theme, push = false) {
  const ctx = await L.newCtx(browser, state, { width: w, height: w <= 1024 ? 768 : 900, theme, extra: L.EXTRA });
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
const shell = (page) => page.evaluate(() => ({ main: document.querySelectorAll("main#main-content").length, h1: document.querySelectorAll("#main-content h1").length, docOverflow: document.documentElement.scrollWidth - innerWidth }));

// ---- F-668A-A-1 court dates
const courtProbe = () => {
  const t = document.querySelector("[data-testid=court-date-list] table");
  let sc = t.parentElement; while (sc && !/(auto|scroll)/.test(getComputedStyle(sc).overflowX)) sc = sc.parentElement;
  const ths = [...t.querySelectorAll("thead th")];
  const status = ths.find((th) => /status/i.test(th.innerText));
  const cs = getComputedStyle(status);
  return {
    table: t.offsetWidth, scroller: sc && `${sc.scrollWidth}/${sc.clientWidth}`, docOverflow: document.documentElement.scrollWidth - innerWidth,
    statusW: status.offsetWidth, statusPad: `${cs.paddingLeft}/${cs.paddingRight}`,
    cols: ths.map((th) => `${th.innerText.trim()}:${th.offsetWidth}`).join(" "),
    badges: [...t.querySelectorAll("tbody tr")].map((r) => { const b = r.querySelector("[data-status]"); const svg = b && b.querySelector("svg"); return `${r.cells[0].innerText.trim()} ${b ? `${b.getAttribute("data-status")} "${b.innerText.trim()}" svg=${!!svg && svg.getBoundingClientRect().width > 0} w=${b.offsetWidth} clipped=${b.scrollWidth > b.clientWidth + 1}` : "none"}`; }),
  };
};
async function openCourt(page) {
  await go(page, `${B}/matters/${ID.khumaloMatterId}`);
  await page.getByTestId("tab-group-schedule").click();
  await page.getByTestId("court-date-list").waitFor({ timeout: 15000 });
  await page.waitForTimeout(600);
}
async function court(browser, state) {
  for (const [w, theme] of [[1024, "light"], [1024, "dark"], [1440, "light"]]) {
    const { ctx, page, lbl } = await ctxFor(browser, state, w, theme);
    lbl.label = `afterfix court ${w}-${theme}`;
    await openCourt(page);
    out.court[`${w}-${theme}`] = { ...(await page.evaluate(courtProbe)), shell: await shell(page) };
    await page.getByTestId("court-date-list").screenshot({ path: path.join(OUT, `crop-court-dates-${w}-${theme}.png`) }).catch(() => {});
    if (w === 1024 && theme === "light") {
      const css = "[data-testid=court-date-list] td:nth-child(8),[data-testid=court-date-list] th:nth-child(8){padding-left:8px;padding-right:8px}";
      const h = await page.addStyleTag({ content: css }); await page.waitForTimeout(200);
      const r = await page.evaluate(courtProbe);
      out.courtProto.plusActionsPx2 = { css, table: r.table, scroller: r.scroller, docOverflow: r.docOverflow, cols: r.cols };
      await h.evaluate((n) => n.remove());
    }
    await ctx.close();
  }
}

// ---- F-668A-A-3 task tables
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
    const sr = sc.getBoundingClientRect();
    const ths = [...t.querySelectorAll("thead th")].filter((th) => th.offsetWidth > 0);
    const full = ths.filter((th) => { const b = th.getBoundingClientRect(); return b.left >= sr.left - 1 && b.right <= sr.right + 1; }).map((th) => th.innerText.trim());
    const b = t.querySelector("tbody tr td:first-child [title]");
    return { section: label, scroll: `${sc.scrollWidth}/${sc.clientWidth}`, clip: sc.scrollWidth - sc.clientWidth, rows: t.querySelectorAll("tbody tr").length, cols: ths.map((th) => `${th.innerText.trim()}:${th.offsetWidth}`).join(" "), fullyInView: full.join(","), badge: b && { text: b.innerText.trim(), title: b.getAttribute("title"), truncated: b.scrollWidth > b.clientWidth + 1 } };
  });
};
async function k09(browser, state) {
  for (const [w, theme] of [[1024, "light"], [1024, "dark"], [1280, "light"], [1366, "light"], [1440, "light"], [1440, "dark"]]) {
    const { ctx, page, lbl } = await ctxFor(browser, state, w, theme);
    lbl.label = `afterfix k09 alice ${w}-${theme}`;
    await go(page, MW);
    out.k09[`${w}-${theme}`] = { shell: await shell(page), tables: await page.evaluate(k09Probe) };
    if (theme === "light" && (w === 1024 || w === 1280 || w === 1366)) await page.locator('[data-testid="tasks-panel"]').first().screenshot({ path: path.join(OUT, `crop-k09-tasks-${w}-light.png`) }).catch(() => {});
    await ctx.close();
  }
}

// ---- activation + pager + urgency (1440 light, push counter)
async function activation(browser, state) {
  const { ctx, page, lbl } = await ctxFor(browser, state, 1440, "light", true);
  lbl.label = "afterfix activation alice 1440-light";
  await go(page, MW);
  const avail = page.locator('xpath=//*[normalize-space(text())="Available Tasks"]/ancestor::*[.//table][1]');
  const pushes = () => page.evaluate(() => window.__pushes);
  const dialogs = () => page.getByRole("dialog").count();
  const sheetInfo = async () => {
    await page.getByRole("dialog").first().waitFor({ timeout: 10000 }).catch(() => {});
    await page.waitForTimeout(700);
    return { dialogs: await dialogs(), taskId: new URL(page.url()).searchParams.get("taskId"), heading: (await page.getByRole("dialog").first().getByRole("heading").first().innerText().catch(() => "")).trim().slice(0, 60) };
  };
  const close = async () => { await page.keyboard.press("Escape"); await page.getByRole("dialog").first().waitFor({ state: "hidden", timeout: 8000 }).catch(() => {}); await page.waitForTimeout(600); return { taskIdAfter: new URL(page.url()).searchParams.get("taskId"), dialogsAfter: await dialogs() }; };
  const pageText = async () => (await avail.getByText(/^Page \d+ of \d+$/).allInnerTexts()).join("|");
  const firstTitle = async () => (await avail.locator("tbody tr").first().locator("[data-task-open]").innerText().catch(() => "")).trim().slice(0, 50);
  const pstate = async () => ({ page: await pageText(), rows: await avail.locator("tbody tr").count(), first: await firstTitle(), prevDisabled: await avail.getByRole("button", { name: "Previous" }).isDisabled().catch(() => null), nextDisabled: await avail.getByRole("button", { name: "Next" }).isDisabled().catch(() => null), url: new URL(page.url()).search });

  // urgency grouping
  out.urgency = await page.evaluate(() => {
    const h2 = [...document.querySelectorAll("#main-content h2")].find((h) => h.innerText.trim() === "My Tasks");
    let box = h2; while (box && !box.querySelector("table")) box = box.parentElement;
    const groups = [...box.querySelectorAll("*")].filter((e) => [...e.childNodes].some((n) => n.nodeType === 3 && /^(Overdue|Due This Week|Upcoming|No Due Date)$/.test(n.textContent.trim()))).map((e) => e.innerText.replace(/\s+/g, " ").trim());
    return { groups, tables: [...box.querySelectorAll("table")].map((t) => ({ rows: t.querySelectorAll("tbody tr").length, titles: [...t.querySelectorAll("[data-task-open]")].map((b) => b.innerText.trim().slice(0, 50)) })) };
  });

  const A = out.activation;
  const row = avail.locator("tbody tr").first();
  A.firstAvailableRowId = await row.locator("[data-task-open]").getAttribute("data-task-open");
  const act = async (key, fn) => { const p0 = await pushes(); await fn(); A[key] = { ...(await sheetInfo()), pushes: (await pushes()) - p0 }; Object.assign(A[key], await close()); };
  await act("titleClick", () => row.locator("[data-task-open]").click());
  await act("plainCellClickDue", () => row.locator("td:visible").last().click({ position: { x: 5, y: 5 } }));
  await act("plainCellClickPriority", () => row.locator("td:visible").nth(2).click({ position: { x: 5, y: 5 } }));
  await act("matterBadgeClick", () => row.locator("td").first().locator("[title]").first().click());
  const urow = page.locator(`[data-task-open='${ID.assignedTaskId}']`);
  if (await urow.count()) await act("myTasksRowClick", () => urow.locator("xpath=ancestor::tr[1]").locator("td:visible").last().click({ position: { x: 5, y: 5 } }));
  for (const key of ["Enter", "Space"]) {
    await page.getByRole("button", { name: /Available Tasks/ }).focus();
    let tabs = 0, f = null;
    for (; tabs < 15; tabs++) { await page.keyboard.press("Tab"); f = await page.evaluate(() => document.activeElement && document.activeElement.getAttribute("data-task-open")); if (f) break; }
    const fv = await page.evaluate(() => document.activeElement.matches(":focus-visible"));
    await act(`keyboard${key}`, () => page.keyboard.press(key));
    Object.assign(A[`keyboard${key}`], { tabsFromToggle: tabs + 1, focused: f, focusVisible: fv });
  }
  A.rowsFocusable = await avail.locator("tbody tr[tabindex]").count();

  // pager: buttons must not open a task; Page 1→2→3→2→1
  const P = out.pager;
  P.p1 = await pstate();
  const pg = async (name) => { const p0 = await pushes(); await avail.getByRole("button", { name, exact: true }).click(); await page.waitForTimeout(500); return { ...(await pstate()), pushes: (await pushes()) - p0, dialogs: await dialogs(), taskId: new URL(page.url()).searchParams.get("taskId") }; };
  P.next1 = await pg("Next"); P.next2 = await pg("Next"); P.prev1 = await pg("Previous"); P.prev2 = await pg("Previous");
  // open / close a task on page 2 and page 3: page must hold
  P.next3 = await pg("Next");
  const t2 = avail.locator("tbody tr").first().locator("[data-task-open]");
  const id2 = await t2.getAttribute("data-task-open");
  await t2.click(); P.openOnP2 = { ...(await sheetInfo()), expected: id2 }; P.openOnP2.close = await close(); P.afterCloseP2 = await pstate();
  P.next4 = await pg("Next");
  const t3 = avail.locator("tbody tr").last().locator("[data-task-open]");
  const id3 = await t3.getAttribute("data-task-open");
  await t3.click(); P.openOnP3 = { ...(await sheetInfo()), expected: id3 }; P.openOnP3.close = await close(); P.afterCloseP3 = await pstate();
  await page.waitForTimeout(1500); P.afterCloseP3Settled = await pstate();
  await avail.screenshot({ path: path.join(OUT, "crop-pager-p3-after-close-1440-light.png") }).catch(() => {});
  await ctx.close();
}

(async () => {
  const browser = await L.chromium.launch();
  const st = await L.loginState(browser, "alice");
  await court(browser, st); console.log("court done");
  await k09(browser, st); console.log("k09 done");
  await activation(browser, st); console.log("activation done");
  await browser.close();
  fs.writeFileSync(path.join(OUT, "measure.json"), JSON.stringify(out));
  console.log("console", cons.length);
})().catch((e) => { console.error("MEASURE FAIL", e); process.exit(1); });
