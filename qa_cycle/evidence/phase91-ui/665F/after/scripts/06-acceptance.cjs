// 665F OBS-after step 6 (NEW in after/, no before counterpart): the slice's acceptance probes, as
// Alice, through the UI only. Read-only except one keyboard toggle-and-revert of the rule switch
// "Toggle Investment Maturity Reminder" (two instant writes, net no change, as in 03).
//   k100      Integrations + AI × 1024/1440 × light/dark: panel width, the @container width and its
//             grid's column count, visible descendants past the panel's right edge, document
//             scrollWidth / clientWidth.
//   k114      Automations rules table × 1024/1440 light: table vs scroller widths, Name cell
//             white-space / width / line count, the Enabled switch's reachability (scroll the
//             scroller to its end and back); then keyboard focus: Tab from the h1 to the switch,
//             Space, document.activeElement right away and after the write settles; Space again.
//   k115      Notifications × 1024/1440 light: groups + labels per in-app switch id, the in-app /
//             email switch x positions per row, document overflow at 1024.
//   k121      Packs Available / Installed (1440 light): badges, the Available-tab subtitle, h3s, testids.
//   execLog   Execution Log (1440 light): Trigger cells (K-122); the detail sheet by mouse click on the
//             Rule button, by keyboard (Tab from the h1, Enter), focus return; a click on the Status
//             and Duration cells of the same row (must NOT open it); pager text.
//   paging    Sync Log / AI history / Execution Log row and pager counts (UI) + API page info.
//   dangerDark  every visible `text-destructive` button / Uninstall button on Packs › Installed and
//             MCP in dark + light: computed colour vs `var(--destructive)`.
//   sel       the new spec selectors: h1 /^Notifications/, h1 "MCP".
// Writes $RAW/acceptance.json + tiles.
// Usage: PHASE=after node 06-acceptance.cjs
const path = require("path");
const fs = require("fs");
const L = require("./lib.cjs");

const out = { at: new Date().toISOString(), k100: {}, k114: {}, k115: {}, k121: {}, execLog: {}, paging: {}, dangerDark: {}, sel: {} };
const tiles = [];
const consoleSink = [];
const lab = { label: "" };
const shot = async (p, key, caption, opt = {}) => { const f = path.join(L.RAW, `${key}.png`); await p.screenshot({ path: f, ...opt }); tiles.push([key, f, caption]); };
const eshot = async (loc, key, caption) => { const f = path.join(L.RAW, `${key}.png`); await loc.screenshot({ path: f }); tiles.push([key, f, caption]); };
const settle = async (p) => { await p.waitForLoadState("networkidle").catch(() => {}); await p.waitForTimeout(600); };
const T = (tab) => `${L.BASE}/settings/connections?tab=${tab}`;
const VIS = "window.__v = (e) => { const b = e.getBoundingClientRect(); const cs = getComputedStyle(e); return b.width > 0 && b.height > 0 && cs.visibility !== 'hidden' && cs.display !== 'none'; }; window.__t = (e) => (e.innerText || e.textContent || '').replace(/\\s+/g, ' ').trim();";

const K100 = () => {
  const panel = document.querySelector('main#main-content [role="tabpanel"]');
  const pr = panel.getBoundingClientRect();
  const past = [...panel.querySelectorAll("*")].filter((e) => window.__v(e) && e.getBoundingClientRect().right > pr.right + 1);
  const containers = [...panel.querySelectorAll("*")].filter((e) => (getComputedStyle(e).containerType || "normal") !== "normal").map((e) => {
    const g = [...e.children].find((c) => getComputedStyle(c).display === "grid");
    return { cls: (e.className || "").toString(), width: Math.round(e.getBoundingClientRect().width), containerType: getComputedStyle(e).containerType, gridCls: g ? g.className : null, gridCols: g ? getComputedStyle(g).gridTemplateColumns.split(" ").length : null, gridTemplate: g ? getComputedStyle(g).gridTemplateColumns : null, gridChildren: g ? g.children.length : 0 };
  });
  const se = document.scrollingElement;
  return { panelWidth: Math.round(pr.width), pastPanelEdge: past.length, pastSample: past.slice(0, 3).map((e) => `${e.tagName.toLowerCase()}:${window.__t(e).slice(0, 30)}:+${Math.round(e.getBoundingClientRect().right - pr.right)}`), scrollWidth: se.scrollWidth, clientWidth: se.clientWidth, containers };
};

const K114 = () => {
  const t = document.querySelector("main#main-content table");
  let sc = t.parentElement; while (sc && !/(auto|scroll)/.test(getComputedStyle(sc).overflowX)) sc = sc.parentElement;
  const sr = sc.getBoundingClientRect();
  const row = t.querySelector('tbody tr[data-testid="automation-row"]');
  const name = row.children[0];
  const ps = [...name.querySelectorAll("p")];
  const lines = (e) => Math.round(e.getBoundingClientRect().height / parseFloat(getComputedStyle(e).lineHeight));
  const sw = row.querySelector('[role="switch"]');
  const swRect = () => { const r = sw.getBoundingClientRect(); return { x: Math.round(r.x), right: Math.round(r.right), inScroller: r.left >= sr.left - 0.5 && r.right <= sr.left + sc.clientWidth + 0.5 }; };
  const at0 = swRect();
  sc.scrollLeft = sc.scrollWidth;
  const atEnd = { scrollLeft: Math.round(sc.scrollLeft), ...swRect() };
  sc.scrollLeft = 0;
  const allNames = [...t.querySelectorAll('tbody tr[data-testid="automation-row"]')].map((r) => { const c = r.children[0]; return { w: Math.round(c.getBoundingClientRect().width), h: Math.round(c.getBoundingClientRect().height), descLines: c.querySelectorAll("p")[1] ? lines(c.querySelectorAll("p")[1]) : 0 }; });
  return {
    dataSlot: t.getAttribute("data-slot"), tableWidth: Math.round(t.getBoundingClientRect().width), scrollerClient: sc.clientWidth, scrollerScroll: sc.scrollWidth, scrollerOverflowX: getComputedStyle(sc).overflowX,
    headers: [...t.querySelectorAll("thead th")].map(window.__t),
    nameCell: { whiteSpace: getComputedStyle(name).whiteSpace, maxWidth: getComputedStyle(name).maxWidth, width: Math.round(name.getBoundingClientRect().width), nameLines: ps[0] ? lines(ps[0]) : null, descLines: ps[1] ? lines(ps[1]) : null, text: window.__t(name).slice(0, 80) },
    nameCellsMaxWidth: Math.max(...allNames.map((x) => x.w)), nameCellsMaxDescLines: Math.max(...allNames.map((x) => x.descLines)),
    enabledSwitch: { name: sw.getAttribute("aria-label"), atScroll0: at0, atScrollEnd: atEnd },
    docHOverflow: document.scrollingElement.scrollWidth - document.scrollingElement.clientWidth,
  };
};

const K115 = () => {
  const main = document.querySelector("main#main-content");
  const rows = [...main.querySelectorAll('[role="switch"][id^="inapp-"]')].map((s) => {
    const type = s.id.replace("inapp-", "");
    const em = document.getElementById(`email-${type}`);
    const lbl = document.querySelector(`label[for="${CSS.escape(s.id)}"]`);
    const r = s.getBoundingClientRect(), er = em.getBoundingClientRect();
    let g = s.closest("div.grid").parentElement; const cat = g.querySelector("p.uppercase");
    return { type, label: lbl ? lbl.textContent : null, category: cat ? cat.textContent : null, inX: Math.round(r.x), emX: Math.round(er.x), labelW: Math.round(lbl.getBoundingClientRect().width), labelLines: Math.round(lbl.getBoundingClientRect().height / parseFloat(getComputedStyle(lbl).lineHeight)) };
  });
  const heads = [...main.querySelectorAll("p.uppercase")].map((h) => h.textContent);
  const se = document.scrollingElement;
  return { categories: heads, n: rows.length, rows, inXs: [...new Set(rows.map((r) => r.inX))], emXs: [...new Set(rows.map((r) => r.emX))], docHOverflow: se.scrollWidth - se.clientWidth, docScrollWidth: se.scrollWidth, otherPresent: heads.some((h) => /^other$/i.test(h)) };
};

const DANGER = () => {
  const ref = document.createElement("span"); ref.style.color = "var(--destructive)"; document.body.appendChild(ref);
  const destructive = getComputedStyle(ref).color; ref.remove();
  const main = document.querySelector("main#main-content");
  const btns = [...main.querySelectorAll("button")].filter((b) => window.__v(b) && (/text-destructive/.test(b.className) || /^(Uninstall|Revoke access|Disconnect)$/.test(window.__t(b))));
  return { destructiveToken: destructive, buttons: btns.map((b) => ({ text: window.__t(b), variant: b.getAttribute("data-variant"), cls: b.className.slice(0, 120), color: getComputedStyle(b).color, disabled: b.disabled, equalsToken: getComputedStyle(b).color === destructive })) };
};

async function ctxFor(browser, S, w, h, theme) {
  const ctx = await L.newCtx(browser, S, { width: w, height: h, theme, extra: L.EXTRA });
  const p = await ctx.newPage();
  L.watch(p, consoleSink, lab);
  return { ctx, p };
}
async function tabTo(p, pred, arg, max = 80) {
  await p.locator("#main-content h1").first().click();
  for (let i = 1; i <= max; i++) {
    await p.keyboard.press("Tab");
    if (await p.evaluate(pred, arg)) return i;
  }
  return null;
}
const active = (p) => p.evaluate(() => { const a = document.activeElement; return a ? { tag: a.tagName.toLowerCase(), role: a.getAttribute("role"), name: a.getAttribute("aria-label") || (a.innerText || "").trim().slice(0, 60), checked: a.getAttribute("aria-checked"), disabled: !!a.disabled } : null; });

(async () => {
  const a = await L.idpToken("alice");
  const browser = await L.chromium.launch();
  const S = await L.loginState(browser, "alice");

  for (const [w, h] of [[1024, 768], [1440, 900]])
    for (const theme of ["light", "dark"]) {
      const v = `${w}-${theme}`;
      const { ctx, p } = await ctxFor(browser, S, w, h, theme);
      await p.addInitScript(VIS);
      for (const tab of ["xero", "ai"]) {
        lab.label = `acc|k100-${tab}|${v}`;
        await p.goto(T(tab), { waitUntil: "load" }); await settle(p);
        (out.k100[tab] = out.k100[tab] || {})[v] = await p.evaluate(K100);
      }
      if (theme === "light") {
        lab.label = `acc|k114|${v}`;
        await p.goto(T("automations"), { waitUntil: "load" }); await settle(p);
        out.k114[v] = await p.evaluate(K114);
        await eshot(p.locator("#main-content table").first().locator("xpath=.."), `acc__k114-table-${v}`, `Alice ${w} light — Automations rules table on DataTable: table ${out.k114[v].tableWidth} px in a ${out.k114[v].scrollerClient} px scroller; Name cell white-space ${out.k114[v].nameCell.whiteSpace}, ${out.k114[v].nameCell.width} px; the Enabled switch at x=${out.k114[v].enabledSwitch.atScroll0.x} (in scroller at scroll 0: ${out.k114[v].enabledSwitch.atScroll0.inScroller})`);
        lab.label = `acc|k115|${v}`;
        await p.goto(T("notifications"), { waitUntil: "load" }); await settle(p);
        out.k115[v] = await p.evaluate(K115);
        if (w === 1024) {
          for (const cat of ["Diary", "Trust"]) await eshot(p.locator("#main-content p.uppercase").filter({ hasText: new RegExp(`^${cat}$`, "i") }).locator("xpath=../.."), `acc__k115-${cat.toLowerCase()}-1024`, `Alice 1024 light — Notifications "${cat.toUpperCase()}" group (K-115 new labels); in-app switch x ${out.k115[v].inXs.join("/")}, email x ${out.k115[v].emXs.join("/")} for all ${out.k115[v].n} rows; doc overflow ${out.k115[v].docHOverflow}`);
        }
      }
      if (w === 1440) {
        for (const [tab, inner] of [["packs", "installed"], ["mcp", null]]) {
          lab.label = `acc|danger-${tab}|${v}`;
          await p.goto(T(tab), { waitUntil: "load" }); await settle(p);
          if (inner) { await p.getByRole("tab", { name: /Installed/i }).click(); await p.waitForTimeout(1200); }
          (out.dangerDark[tab] = out.dangerDark[tab] || {})[v] = await p.evaluate(DANGER);
          if (tab === "packs") await eshot(p.locator('[data-testid="pack-card-common"]'), `acc__uninstall-${v}`, `Alice 1440 ${theme} — Packs › Installed › "Common Templates" card: Uninstall (variant ${(out.dangerDark.packs[v].buttons[0] || {}).variant}) colour ${(out.dangerDark.packs[v].buttons[0] || {}).color}; token --destructive ${out.dangerDark.packs[v].destructiveToken}`);
        }
      }
      await ctx.close();
    }

  // ---- K-121 + SEL (1440 light) ----
  {
    const { ctx, p } = await ctxFor(browser, S, 1440, 900, "light");
    await p.addInitScript(VIS);
    lab.label = "acc|k121|1440-light";
    await p.goto(T("packs"), { waitUntil: "load" }); await settle(p);
    const cards = async () => p.locator('[data-testid^="pack-card-"]:visible').evaluateAll((cs) => cs.map((c) => ({ testid: c.dataset.testid, h3: c.querySelector("h3") ? c.querySelector("h3").textContent : null, h3Tag: !!c.querySelector("h3"), badges: [...c.querySelectorAll('[data-slot="badge"]')].map((b) => b.textContent) })));
    out.k121.available = { cards: await cards(), subtitleCount: await p.getByText("Kazi Packs — extend your workspace with pre-built content").count(), subtitleInPanel: await p.locator('[role="tabpanel"][data-state="active"]').getByText(/Kazi Packs/).count(), pageHeaderDescription: await p.locator("#main-content").getByText(/Kazi Packs/).allInnerTexts() };
    await eshot(p.locator('[data-testid="pack-card-legal-za"]'), "acc__k121-legal-za-available", `Alice 1440 light — Packs › Available › "SA Legal Templates": badges ${JSON.stringify(out.k121.available.cards.find((c) => c.testid === "pack-card-legal-za").badges)}`);
    await shot(p, "acc__k121-available-top", `Alice 1440 light — Packs › Available (top): the repeated "Kazi Packs — …" subtitle in the tab panel: ${out.k121.available.subtitleInPanel} (page header description keeps it)`, { clip: { x: 0, y: 0, width: 1440, height: 520 } });
    await p.getByRole("tab", { name: /Installed/i }).click(); await p.waitForTimeout(1200);
    out.k121.installed = { cards: await cards() };
    lab.label = "acc|sel|1440-light";
    await p.goto(T("notifications"), { waitUntil: "load" }); await settle(p);
    out.sel.notificationsH1 = { "heading h1 /^Notifications/ (notification-preferences.spec:55 after PR)": await p.getByRole("heading", { level: 1, name: /^Notifications/ }).count(), "heading h1 /^Notification Preferences/ (before)": await p.getByRole("heading", { level: 1, name: /^Notification Preferences/ }).count() };
    await p.goto(T("mcp"), { waitUntil: "load" }); await settle(p);
    out.sel.mcpH1 = { "heading h1 'MCP' exact": await p.getByRole("heading", { level: 1, name: "MCP", exact: true }).count(), "heading h1 'Claude / MCP Connector' (before)": await p.getByRole("heading", { level: 1, name: "Claude / MCP Connector" }).count(), "h2/h3 'Claude / MCP Connector'": await p.locator("#main-content").getByRole("heading", { name: "Claude / MCP Connector" }).evaluateAll((hs) => hs.map((h) => h.tagName.toLowerCase())) };
    await ctx.close();
  }

  // ---- K-114 focus (1440 light; keyboard toggle + revert) ----
  {
    const { ctx, p } = await ctxFor(browser, S, 1440, 900, "light");
    lab.label = "acc|k114-focus|1440-light";
    await p.goto(T("automations"), { waitUntil: "load" }); await settle(p);
    const name = `Toggle ${L.TOGGLE_RULE}`;
    const sw = p.getByRole("switch", { name });
    const tabs = await tabTo(p, (n) => document.activeElement && document.activeElement.getAttribute("aria-label") === n, name, 80).catch(() => null);
    const tabsN = tabs === null ? await (async () => { await sw.focus(); return "focus() fallback"; })() : tabs;
    const steps = [];
    for (const i of [0, 1]) {
      const before = await sw.getAttribute("aria-checked");
      await p.keyboard.press("Space");
      await p.waitForTimeout(60);
      const during = await active(p);
      await p.getByText(/Rule toggled successfully|Failed to toggle rule/).first().waitFor({ timeout: 15000 }).catch(() => {});
      await p.waitForTimeout(1500);
      const settled = await active(p);
      const api = L.list((await L.apiGet(a, "/api/automation-rules")).json).find((r) => r.name === L.TOGGLE_RULE);
      steps.push({ before, ariaAfter: await sw.getAttribute("aria-checked"), activeDuringPending: during, activeAfterSettle: settled, focusKept: !!(settled && settled.name === name), apiEnabled: api.enabled, apiUpdatedAt: api.updatedAt });
      if (i === 0) await shot(p, "acc__k114-focus-after-space", `Alice 1440 light — Automations: keyboard Tab ×${tabsN} to "${name}", Space → toggled OFF; document.activeElement after settle = ${JSON.stringify(settled)} (reverted with Space next)`, { clip: { x: 0, y: 0, width: 1440, height: 900 } });
      await p.waitForTimeout(4500);
    }
    out.k114.focus = { switch: name, tabsFromH1: tabsN, steps };
    await ctx.close();
  }

  // ---- Execution Log: K-122 + the detail sheet (1440 light) ----
  {
    const { ctx, p } = await ctxFor(browser, S, 1440, 900, "light");
    lab.label = "acc|execlog|1440-light";
    await p.goto(`${L.BASE}${L.SUBPAGES.executions}`, { waitUntil: "load" }); await settle(p);
    const main = p.locator("#main-content");
    const rows = main.locator("table tbody tr");
    out.execLog.rows = await rows.count();
    out.execLog.triggerCells = [...new Set(await rows.evaluateAll((trs) => trs.map((tr) => (tr.children[1].innerText || "").trim())))];
    out.execLog.headers = await main.locator("table thead th").allInnerTexts();
    out.execLog.pagerText = await main.getByText(/^Page \d+ of \d+/).allInnerTexts();
    out.execLog.prevNext = `${await main.getByRole("button", { name: "Previous" }).count()}/${await main.getByRole("button", { name: "Next" }).count()}`;
    const dlg = p.getByRole("dialog", { name: "Execution Detail" });
    const sheetTrigger = async () => dlg.evaluate((d) => { const t = (e) => (e.innerText || "").replace(/\s+/g, " ").trim(); const lab = [...d.querySelectorAll("span,dt,p")].find((e) => t(e) === "Trigger"); return { trigger: lab ? t(lab.parentElement).replace(/^Trigger\s*/, "") : null, hasEventClass: /[a-z]Event\b/.test(t(d)), text: t(d).slice(0, 300) }; });
    // mouse: Rule button
    const ruleBtn = rows.first().locator("td").first().getByRole("button");
    out.execLog.ruleButton = { count: await rows.locator("td:first-child button").count(), text: (await ruleBtn.innerText()).trim() };
    await ruleBtn.click();
    await dlg.waitFor({ timeout: 5000 }).catch(() => {});
    out.execLog.mouse = { opened: await dlg.isVisible(), sheet: (await dlg.isVisible()) ? await sheetTrigger() : null };
    await shot(p, "acc__execlog-sheet-mouse", `Alice 1440 light — Execution Log › click the Rule button "${out.execLog.ruleButton.text}" → sheet "Execution Detail" (Trigger ${JSON.stringify(out.execLog.mouse.sheet && out.execLog.mouse.sheet.trigger)})`);
    await p.keyboard.press("Escape"); await dlg.waitFor({ state: "hidden" }).catch(() => {});
    // keyboard: Tab from the h1 to the first Rule button, Enter
    const n = await tabTo(p, () => { const a = document.activeElement; return !!(a && a.tagName === "BUTTON" && a.closest("tbody")); }, null, 40);
    const focused = await active(p);
    await p.keyboard.press("Enter");
    await dlg.waitFor({ timeout: 5000 }).catch(() => {});
    out.execLog.keyboard = { tabsFromH1: n, focusedBeforeEnter: focused, opened: await dlg.isVisible() };
    await shot(p, "acc__execlog-sheet-keyboard", `Alice 1440 light — Execution Log › Tab ×${n} from the h1 to the Rule button "${focused && focused.name}", Enter → sheet open: ${out.execLog.keyboard.opened}`);
    await p.keyboard.press("Escape"); await dlg.waitFor({ state: "hidden" }).catch(() => {});
    await p.waitForTimeout(300);
    out.execLog.keyboard.focusAfterEscape = await active(p);
    // click elsewhere on the row: Status cell, Duration cell
    out.execLog.rowClickElsewhere = {};
    for (const [ix, label] of [[3, "Status"], [6, "Duration"]]) {
      await rows.first().locator("td").nth(ix).click({ position: { x: 5, y: 5 } });
      await p.waitForTimeout(800);
      out.execLog.rowClickElsewhere[label] = { opened: await dlg.isVisible() };
      if (await dlg.isVisible()) { await p.keyboard.press("Escape"); await dlg.waitFor({ state: "hidden" }).catch(() => {}); }
    }
    await eshot(main.locator("table").first().locator("xpath=.."), "acc__execlog-table-1440", `Alice 1440 light — Execution Log table: Trigger column ${JSON.stringify(out.execLog.triggerCells)} (K-122); ${out.execLog.rows} rows, pager ${JSON.stringify(out.execLog.pagerText)}`);
    // paging (UI) on the other two logs
    for (const [k, url] of [["sync-log", L.SUBPAGES["sync-log"]], ["ai-history", L.SUBPAGES["ai-history"]], ["executions", L.SUBPAGES.executions]]) {
      lab.label = `acc|paging-${k}|1440-light`;
      await p.goto(`${L.BASE}${url}`, { waitUntil: "load" }); await settle(p);
      out.paging[k] = { uiRows: await main.locator("table tbody tr").count(), emptyHeadings: await main.locator("h2,h3").filter({ hasText: /^No / }).allInnerTexts(), pagerText: await main.getByText(/^Page \d+ of \d+/).allInnerTexts(), prevNextButtons: await main.getByRole("button", { name: /^(Previous|Next)( page)?$/ }).count(), prevNextLinks: await main.getByRole("link", { name: /^(Previous|Next)( page)?$/ }).count() };
    }
    await ctx.close();
  }
  out.paging.api = { syncEntries: L.pageInfo((await L.apiGet(a, "/api/integrations/sync/entries?size=20")).json), aiExecutions: L.pageInfo((await L.apiGet(a, "/api/ai/executions?size=20")).json), automationExecutions: L.pageInfo((await L.apiGet(a, "/api/automation-executions?size=20")).json) };

  await browser.close();
  out.console = consoleSink;
  out.tiles = tiles;
  fs.writeFileSync(path.join(L.RAW, "acceptance.json"), JSON.stringify(out));
  const s = (o) => JSON.stringify(o).slice(0, 1600);
  console.log("k100", s(Object.fromEntries(Object.entries(out.k100).map(([t, vv]) => [t, Object.fromEntries(Object.entries(vv).map(([v, m]) => [v, `panel ${m.panelWidth} past ${m.pastPanelEdge} sw ${m.scrollWidth}/${m.clientWidth} cont ${m.containers.map((c) => c.width + ":" + c.gridCols + "col").join(",")}`]))]))));
  console.log("k114", s(Object.fromEntries(Object.entries(out.k114).filter(([k]) => k !== "focus").map(([v, m]) => [v, { t: m.tableWidth, sc: `${m.scrollerClient}/${m.scrollerScroll}`, name: m.nameCell, maxW: m.nameCellsMaxWidth, sw: m.enabledSwitch }]))));
  console.log("k114 focus", s(out.k114.focus));
  console.log("k115", s(Object.fromEntries(Object.entries(out.k115).map(([v, m]) => [v, { cats: m.categories, n: m.n, inXs: m.inXs, emXs: m.emXs, ov: m.docHOverflow, other: m.otherPresent }]))));
  console.log("k121", s(out.k121));
  console.log("execLog", s(out.execLog));
  console.log("paging", s(out.paging));
  console.log("danger", s(out.dangerDark));
  console.log("sel", s(out.sel));
  console.log("console", consoleSink.length, s(consoleSink), "tiles", tiles.length);
})();
