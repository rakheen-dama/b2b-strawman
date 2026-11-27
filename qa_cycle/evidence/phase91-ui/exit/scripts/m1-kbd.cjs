// 670A M1: 390 + keyboard pass (OBS-390/KBD, brief M-4). Three walks, all keyboard-only after load:
//  A. 390 shell mobile nav (Alice + Carol × light/dark, /dashboard): Tab from the top to the sidebar
//     trigger, Enter opens the Sheet, Tab through it (focus kept inside? every stop :focus-visible and
//     inside the Sheet's box?), Escape closes it — where does focus land? Then again from a nav item
//     (Home): how many Escape presses close the Sheet (a focused sidebar-menu-button opens its hidden
//     tooltip, a Radix layer above the Sheet) and where focus lands.
//  B. 390 settings family bar (Alice Firm + Matters × light/dark, Carol Firm light): Tab from the top
//     into nav[aria-label="Settings navigation"] and through its 5 links; per stop: :focus-visible,
//     ring, fully inside the scroll viewport, under a visible edge fade? (FOCUS copied from 664A
//     after/scripts/04-kbd.cjs, not edited there.)
//  C. 1024 DataTable scroller (F-666A-6): the first overflowing table-container among a few settings
//     tables; tabindex, edge fades, then Tab through the page: is the scroller ever a tab stop, do
//     focused cells stay inside it, can the off-frame columns be reached?
// Per-page records (lib MEASURE) → ../measurements.json groups "shell-390" and "settings-390";
// walk results → ../kbd.json (single line); console → ../console.json group "kbd";
// sheets → ../shell/390-mobile-nav.png, ../settings/390-family-bar-kbd.png, ../settings/1024-datatable-scroller-kbd.png.
// Usage: node m1-kbd.cjs
const path = require("path");
const fs = require("fs");
const L = require("./lib.cjs");

const B = L.BASE;
const RAW = path.join(L.RAW, "kbd");
fs.mkdirSync(RAW, { recursive: true });
const sink = [];
const K = { capturedAt: null, mobileNav: {}, familyBar: {}, scroller: {} };
const recs = { "shell-390": [], "settings-390": [] };

async function record(group, key, surface, page, who, v, resp, lbl) {
  const m = await page.evaluate(L.MEASURE);
  const c = L.counts(sink, lbl.label);
  const spec = L.VAR[v];
  const why = [];
  if (resp && resp.status() >= 400) why.push(`status ${resp.status()}`);
  if (m.mainIdCount !== 1) why.push(`main#main-content=${m.mainIdCount}`);
  if (m.h1InMain !== 1) why.push(`h1=${m.h1InMain}`);
  if (m.htmlDark !== spec.expectDark) why.push("theme");
  if (m.overflowPx > 0) why.push(`overflow ${m.overflowPx}px`);
  if (c.cErr) why.push(`console errors ${c.cErr}`);
  if (c.cHyd) why.push(`hydration ${c.cHyd}`);
  const file = path.join(RAW, `${key}--${who}--${v}.png`);
  await page.screenshot({ path: file });
  const r = { group, surface, key, persona: who, variant: v, w: spec.w, h: spec.h, storedTheme: spec.store, expectDark: spec.expectDark, url: page.url(), status: resp ? resp.status() : null, ...m, themeOk: m.htmlDark === spec.expectDark, ...c, shell: true, ok: why.length === 0, why, file: L.rel(file), at: new Date().toISOString() };
  recs[group].push(r);
  console.log(r.ok ? "OK " : "BAD", lbl.label, why.join("; "));
  return file;
}

const ACTIVE = () => {
  const e = document.activeElement;
  if (!e || e === document.body) return { tag: "body", text: "" };
  const b = e.getBoundingClientRect();
  const dlg = e.closest('[role="dialog"]');
  const d = dlg ? dlg.getBoundingClientRect() : null;
  return { tag: e.tagName.toLowerCase(), slot: e.getAttribute("data-slot") || e.getAttribute("data-sidebar"), text: (e.innerText || e.getAttribute("aria-label") || "").replace(/\s+/g, " ").trim().slice(0, 40), href: e.getAttribute("href"), focusVisible: e.matches(":focus-visible"), outline: `${getComputedStyle(e).outlineStyle} ${getComputedStyle(e).outlineWidth}`, boxShadow: getComputedStyle(e).boxShadow.slice(0, 80), inDialog: !!dlg, inViewport: b.top >= -0.5 && b.bottom <= innerHeight + 0.5 && b.left >= -0.5 && b.right <= innerWidth + 0.5, inDialogBox: d ? b.top >= d.top - 0.5 && b.bottom <= d.bottom + 0.5 && b.left >= d.left - 0.5 && b.right <= d.right + 0.5 : null };
};
const dialogs = (page) => page.evaluate(() => [...document.querySelectorAll('[role="dialog"]')].filter((d) => d.getBoundingClientRect().width > 0).map((d) => ({ w: Math.round(d.getBoundingClientRect().width), title: (d.querySelector("h2") || {}).textContent || null })));

// A. 390 mobile Sheet nav
async function mobileNav(browser, state, who, theme) {
  const v = `390-${theme}`;
  const ctx = await L.ctxFor(browser, state, v);
  const page = await ctx.newPage();
  const lbl = { label: `kbd|mobile-nav|${who}|${v}` };
  L.watch(page, sink, lbl, "kbd");
  const resp = await page.goto(`${B}/dashboard`, { waitUntil: "load" });
  await L.settle(page);
  const tiles = [];
  tiles.push([`${who} ${v} · /dashboard closed`, await record("shell-390", "dashboard-390", "Shell › dashboard at 390 (mobile)", page, who, v, resp, lbl), 390]);
  const R = { preTabs: [], steps: [] };
  let f;
  for (let i = 0; i < 40; i++) {
    await page.keyboard.press("Tab");
    f = await page.evaluate(ACTIVE);
    if (f.slot === "sidebar-trigger" || f.slot === "trigger") break;
    R.preTabs.push(`${f.tag} "${f.text}"`);
  }
  R.trigger = f;
  await page.keyboard.press("Enter");
  await page.waitForTimeout(700);
  R.dialogsAfterEnter = await dialogs(page);
  R.opened = R.dialogsAfterEnter.length > 0;
  R.focusAfterOpen = await page.evaluate(ACTIVE);
  const fo = path.join(RAW, `mobile-open--${who}--${v}.png`);
  await page.screenshot({ path: fo });
  tiles.push([`${who} ${v} · Enter on trigger → Sheet open=${R.opened}, focus "${R.focusAfterOpen.text}" in Sheet=${R.focusAfterOpen.inDialog}`, fo, 390]);
  const seen = new Set();
  let mid = null;
  for (let i = 0; i < 45; i++) {
    await page.keyboard.press("Tab");
    await page.waitForTimeout(120);
    f = await page.evaluate(ACTIVE);
    const k = `${f.tag}|${f.text}|${f.href}`;
    R.steps.push(f);
    if (i === 7) { mid = path.join(RAW, `mobile-mid--${who}--${v}.png`); await page.screenshot({ path: mid }); tiles.push([`${who} ${v} · Tab ×8 → "${f.text}" focus-visible=${f.focusVisible} in Sheet=${f.inDialog} visible=${f.inDialogBox}`, mid, 390]); }
    if (seen.has(k)) { R.cycledAfter = i; break; }
    seen.add(k);
  }
  if (!mid) tiles.push([`${who} ${v} · fewer than 8 Tab stops (cycled after ${R.cycledAfter})`, null, 390]);
  await page.keyboard.press("Escape");
  await page.waitForTimeout(700);
  R.dialogsAfterEscape = await dialogs(page);
  R.focusAfterEscape = await page.evaluate(ACTIVE);
  const fe = path.join(RAW, `mobile-esc--${who}--${v}.png`);
  await page.screenshot({ path: fe });
  tiles.push([`${who} ${v} · Escape → open dialogs ${R.dialogsAfterEscape.length}, focus "${R.focusAfterEscape.text}" (${R.focusAfterEscape.slot || R.focusAfterEscape.tag})`, fe, 390]);
  // Escape with focus on a nav item (Home): reload, open by keyboard, Tab ×2 to Home, Escape, Escape.
  // A sidebar-menu-button opens its (hidden on mobile) Radix tooltip on focus; count layers per press.
  await page.goto(`${B}/dashboard`, { waitUntil: "load" });
  await L.settle(page);
  const ST = () => page.evaluate(() => { const e = document.activeElement; return { focus: e === document.body ? "body" : `${e.tagName.toLowerCase()} ${e.getAttribute("data-slot") || ""} "${(e.innerText || "").replace(/\s+/g, " ").trim().slice(0, 20)}"`, dialogs: [...document.querySelectorAll('[role="dialog"]')].map((x) => x.getAttribute("data-state")), tooltips: document.querySelectorAll('[data-slot="tooltip-content"], [role="tooltip"]').length }; });
  for (let i = 0; i < R.preTabs.length + 1; i++) await page.keyboard.press("Tab");
  await page.keyboard.press("Enter");
  await page.waitForTimeout(900);
  await page.keyboard.press("Tab");
  await page.keyboard.press("Tab");
  await page.waitForTimeout(300);
  const E = (R.escapeFromItem = { onItem: await ST() });
  await page.keyboard.press("Escape");
  await page.waitForTimeout(800);
  E.afterEscape1 = await ST();
  await page.keyboard.press("Escape");
  await page.waitForTimeout(1200);
  E.afterEscape2 = await ST();
  R.summary = { escapesToCloseFromItem: E.afterEscape1.dialogs.length === 0 ? 1 : E.afterEscape2.dialogs.length === 0 ? 2 : ">2", focusAfterCloseFromItem: (E.afterEscape1.dialogs.length === 0 ? E.afterEscape1 : E.afterEscape2).focus, tabsToTrigger: R.preTabs.length + 1, opened: R.opened, focusMovedIntoSheet: R.focusAfterOpen.inDialog, stops: R.steps.length, cycledAfter: R.cycledAfter ?? null, focusStayedInSheet: R.steps.every((s) => s.inDialog), allFocusVisible: R.steps.every((s) => s.focusVisible), allInSheetBox: R.steps.every((s) => s.inDialogBox !== false), closedByEscape: R.dialogsAfterEscape.length === 0, focusReturnedToTrigger: ["sidebar-trigger", "trigger"].includes(R.focusAfterEscape.slot), order: R.steps.map((s) => s.text) };
  K.mobileNav[`${who}/${v}`] = R;
  await ctx.close();
  return tiles;
}

// B. 390 family bar (FOCUS copied from 664A after/scripts/04-kbd.cjs, family-bar branch only).
const FOCUS = () => {
  const e = document.activeElement;
  if (!e || e === document.body) return { tag: "body", where: "other" };
  const r = (x) => { const b = x.getBoundingClientRect(); return { left: +b.left.toFixed(1), top: +b.top.toFixed(1), right: +b.right.toFixed(1), bottom: +b.bottom.toFixed(1) }; };
  const nav = e.closest('nav[aria-label="Settings navigation"]');
  const row = nav && nav.querySelector('[data-slot="scroll-area"]') ? nav : null;
  const cs = getComputedStyle(e);
  const out = { tag: e.tagName.toLowerCase(), text: (e.textContent || "").trim().slice(0, 40), where: row ? "family-bar" : "other", focusVisible: e.matches(":focus-visible"), boxShadow: cs.boxShadow, outline: `${cs.outlineStyle} ${cs.outlineWidth}`, box: r(e), ariaCurrent: e.getAttribute("aria-current") };
  if (row) {
    const vp = row.querySelector('[data-slot="scroll-area-viewport"]');
    const vb = vp.getBoundingClientRect();
    const eb = e.getBoundingClientRect();
    out.row = { top: +row.getBoundingClientRect().top.toFixed(1), scrollLeft: vp.scrollLeft, viewport: r(vp) };
    out.fullyInViewport = eb.left >= vb.left - 0.5 && eb.right <= vb.right + 0.5;
    out.fades = ["start", "end"].map((edge) => {
      const fd = row.querySelector(`[data-slot$="-fade-${edge}"]`);
      const fb = fd.getBoundingClientRect();
      const op = +getComputedStyle(fd).opacity;
      const ix = Math.max(0, Math.min(eb.right, fb.right) - Math.max(eb.left, fb.left));
      const iy = Math.max(0, Math.min(eb.bottom, fb.bottom) - Math.max(eb.top, fb.top));
      return { edge, dataVisible: fd.getAttribute("data-visible"), opacity: op, coveredByVisibleFade: op > 0 && ix * iy > 0 };
    });
    out.ringHiddenUnderFade = out.fades.some((x) => x.coveredByVisibleFade);
  }
  out.ringVisible = out.focusVisible && /inset/.test(out.boxShadow) && !/rgba\(0, 0, 0, 0\) 0px 0px 0px 2px inset/.test(out.boxShadow);
  return out;
};
async function familyBar(browser, state, who, fam, theme) {
  const v = `390-${theme}`;
  const ctx = await L.ctxFor(browser, state, v);
  const page = await ctx.newPage();
  const lbl = { label: `kbd|family-bar-${fam}|${who}|${v}` };
  L.watch(page, sink, lbl, "kbd");
  const first = fam === "firm" ? "general" : "templates";
  const resp = await page.goto(`${B}/settings/${fam}?tab=${first}`, { waitUntil: "load" });
  await L.settle(page);
  const row = [[`${who} ${v} · /settings/${fam}?tab=${first}`, await record("settings-390", `settings-${fam}-390`, `Settings › ${fam} at 390 (family bar)`, page, who, v, resp, lbl), 390]];
  const R = { preTabs: [], steps: [] };
  let f;
  for (let i = 0; i < 150; i++) {
    await page.keyboard.press("Tab");
    f = await page.evaluate(FOCUS);
    if (f.where === "family-bar") break;
    R.preTabs.push(`${f.tag} "${f.text}"`);
  }
  for (let i = 0; i < 5 && f.where === "family-bar"; i++) {
    R.steps.push(f);
    const y = Math.max(0, f.row.top - 12);
    const file = path.join(RAW, `bar-${fam}--${who}--${v}-${i}.png`);
    await page.screenshot({ path: file, clip: { x: 0, y, width: 390, height: 72 } });
    row.push([`Tab ${i + 1}: "${f.text}" focus-visible=${f.focusVisible} ring=${f.ringVisible} in-view=${f.fullyInViewport} under-fade=${!!f.ringHiddenUnderFade} sl=${f.row.scrollLeft} fades ${f.fades.map((x) => `${x.edge}:${x.opacity}`).join("/")}`, file, 390]);
    await page.keyboard.press("Tab");
    await page.waitForTimeout(200);
    f = await page.evaluate(FOCUS);
  }
  while (row.length < 6) row.push([`(no further family-bar stop)`, null, 390]);
  R.after = `${f.tag} "${f.text}" (${f.where})`;
  R.summary = { tabsToBar: R.preTabs.length + 1, linkStops: R.steps.length, allFocusVisible: R.steps.every((s) => s.focusVisible), allRingVisible: R.steps.every((s) => s.ringVisible), allFullyInViewport: R.steps.every((s) => s.fullyInViewport), anyRingUnderVisibleFade: R.steps.some((s) => s.ringHiddenUnderFade), scrollLefts: R.steps.map((s) => s.row.scrollLeft), endFadeOpacityFirst: R.steps[0] ? R.steps[0].fades[1].opacity : null, endFadeOpacityLast: R.steps.length ? R.steps[R.steps.length - 1].fades[1].opacity : null, startFadeOpacityLast: R.steps.length ? R.steps[R.steps.length - 1].fades[0].opacity : null, order: R.steps.map((s) => s.text) };
  K.familyBar[`${fam}/${who}/${v}`] = R;
  await ctx.close();
  return row;
}

// C. 1024 DataTable scroller.
const SCROLLERS = () => [...document.querySelectorAll('#main-content [data-slot="table-container"]')].filter((s) => s.getBoundingClientRect().width > 0).map((s, i) => ({ i, clientWidth: s.clientWidth, scrollWidth: s.scrollWidth, overflowPx: s.scrollWidth - s.clientWidth, tabindex: s.getAttribute("tabindex"), role: s.getAttribute("role"), ariaLabel: s.getAttribute("aria-label"), fades: s.parentElement.querySelectorAll('[data-slot*="fade"]').length, headers: [...s.querySelectorAll("thead th")].map((th) => th.textContent.trim()).filter(Boolean), offFrame: [...s.querySelectorAll("thead th")].filter((th) => th.getBoundingClientRect().right > s.getBoundingClientRect().right + 1).map((th) => th.textContent.trim()) }));
async function scroller(browser, state) {
  const v = "1024-light";
  const ctx = await L.ctxFor(browser, state, v);
  const page = await ctx.newPage();
  const lbl = { label: "kbd|scroller|alice|1024-light" };
  L.watch(page, sink, lbl, "kbd");
  const cands = ["fees?tab=tax", "fees?tab=tariffs", "documents?tab=templates", "matters?tab=checklists", "matters?tab=templates", "firm?tab=roles", "connections?tab=automations"];
  const S = (K.scroller = { candidates: {} });
  let chosen = null;
  for (const c of cands) {
    await page.goto(`${B}/settings/${c}`, { waitUntil: "load" });
    await L.settle(page);
    const sc = await page.evaluate(SCROLLERS);
    S.candidates[c] = sc.map((x) => ({ clientWidth: x.clientWidth, scrollWidth: x.scrollWidth, overflowPx: x.overflowPx, tabindex: x.tabindex, fades: x.fades, offFrame: x.offFrame }));
    if (!chosen && sc.some((x) => x.overflowPx > 0)) chosen = c;
  }
  S.chosen = chosen || cands[0];
  await page.goto(`${B}/settings/${S.chosen}`, { waitUntil: "load" });
  await L.settle(page);
  S.scrollers = await page.evaluate(SCROLLERS);
  const tiles = [];
  const t0 = path.join(RAW, "scroller-start.png");
  await page.screenshot({ path: t0 });
  tiles.push([`alice 1024 light · /settings/${S.chosen} · scrollers ${S.scrollers.map((x) => `${x.clientWidth}/${x.scrollWidth} tabindex=${x.tabindex} fades=${x.fades} off-frame=[${x.offFrame.join(", ")}]`).join(" | ")}`, t0, 512]);
  S.stops = [];
  for (let i = 0; i < 120; i++) {
    await page.keyboard.press("Tab");
    await page.waitForTimeout(60);
    const a = await page.evaluate(() => {
      const e = document.activeElement;
      const sc = e && e.closest('[data-slot="table-container"]');
      const isSc = !!e && e.matches('[data-slot="table-container"]');
      if (!sc && !isSc) return { inTable: false, tag: e ? e.tagName.toLowerCase() : null, inMain: !!(e && e.closest("#main-content")) };
      const s = isSc ? e : sc;
      const b = e.getBoundingClientRect(); const sb = s.getBoundingClientRect();
      return { inTable: true, isScroller: isSc, tag: e.tagName.toLowerCase(), text: (e.innerText || e.getAttribute("aria-label") || "").replace(/\s+/g, " ").trim().slice(0, 30), scrollLeft: s.scrollLeft, fullyInsideScroller: b.left >= sb.left - 0.5 && b.right <= sb.right + 0.5, focusVisible: e.matches(":focus-visible") };
    });
    S.stops.push(a);
    if (a.tag === "body" && i > 5) break;
  }
  const inT = S.stops.filter((s) => s.inTable);
  const last = inT[inT.length - 1];
  if (last) {
    const t1 = path.join(RAW, "scroller-last-stop.png");
    await page.screenshot({ path: t1 });
    tiles.push([`after Tab to the last stop inside the table: "${last.text}" scrollLeft=${last.scrollLeft} inside=${last.fullyInsideScroller}`, t1, 512]);
  }
  S.summary = { page: S.chosen, scrollerCount: S.scrollers.length, overflowing: S.scrollers.filter((x) => x.overflowPx > 0).length, anyScrollerTabindex: S.scrollers.some((x) => x.tabindex !== null), scrollerWasATabStop: S.stops.some((s) => s.isScroller), tabStopsInsideTables: inT.length, maxScrollLeftReachedByTab: Math.max(0, ...inT.map((s) => s.scrollLeft)), anyFocusedOutsideScrollerBox: inT.some((s) => !s.fullyInsideScroller), offFrameColumns: S.scrollers.flatMap((x) => x.offFrame), edgeFades: S.scrollers.reduce((n, x) => n + x.fades, 0) };
  delete S.stops;
  S.stopsInTables = inT;
  await ctx.close();
  return tiles;
}

(async () => {
  const browser = await L.chromium.launch();
  K.capturedAt = new Date().toISOString();
  const st = { alice: await L.loginState(browser, "alice"), carol: await L.loginState(browser, "carol") };
  const mob = [];
  for (const who of ["alice", "carol"]) for (const th of ["light", "dark"]) mob.push(...(await mobileNav(browser, st[who], who, th)));
  const bar = [];
  for (const [who, fam, th] of [["alice", "firm", "light"], ["alice", "firm", "dark"], ["alice", "matters", "light"], ["alice", "matters", "dark"], ["carol", "firm", "light"], ["carol", "matters", "dark"]]) bar.push(...(await familyBar(browser, st[who], who, fam, th)));
  const scr = await scroller(browser, st.alice);
  const sheetCells = (tiles) => tiles.map(([c, f, w]) => [c, f, w]);
  await L.sheet(browser, path.join(L.EXIT, "shell", "390-mobile-nav.png"), "670A exit · 390×844 /dashboard mobile Sheet nav, keyboard only · rows Alice light / Alice dark / Carol light / Carol dark · cols: closed · Enter on trigger (Sheet open) · after Tab ×8 · after Escape", sheetCells(mob), 4);
  await L.sheet(browser, path.join(L.EXIT, "settings", "390-family-bar-kbd.png"), "670A exit · 390×844 settings family bar keyboard pass · rows Alice Firm L/D, Alice Matters L/D, Carol Firm L, Carol Matters D · col 1 = page viewport, cols 2–6 = focused row at each Tab through the 5 family links", sheetCells(bar), 6);
  await L.sheet(browser, path.join(L.EXIT, "settings", "1024-datatable-scroller-kbd.png"), "670A exit · 1024×768 light, Alice · DataTable scroller keyboard check (F-666A-6) · page start · last Tab stop inside the table", sheetCells(scr), 2);
  await browser.close();
  K.console = { errors: sink.filter((c) => c.type === "error" || c.type === "pageerror").length, warnings: sink.filter((c) => c.type === "warning").length, hydration: sink.filter((c) => L.HYD.test(c.text)).length };
  fs.writeFileSync(path.join(L.EXIT, "kbd.json"), JSON.stringify(K) + "\n");
  for (const g of Object.keys(recs)) L.appendGroup(path.join(L.EXIT, "measurements.json"), g, recs[g]);
  L.appendGroup(path.join(L.EXIT, "console.json"), "kbd", sink);
  for (const [k, r] of Object.entries(K.mobileNav)) console.log("MOBILE", k, JSON.stringify(r.summary));
  for (const [k, r] of Object.entries(K.familyBar)) console.log("BAR", k, JSON.stringify(r.summary));
  console.log("SCROLLER", JSON.stringify(K.scroller.summary), JSON.stringify(K.scroller.candidates));
  console.log("console", JSON.stringify(K.console));
})().catch((e) => { console.error("KBD FAIL", e); process.exit(1); });
