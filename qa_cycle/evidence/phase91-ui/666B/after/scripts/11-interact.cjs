// 666B OBS-AFTER step 11 (new): the UI interactions OBS-after checks beyond the per-status capture.
//   A. seeded DRAFT de6ac48f: per-cell line counts (Range rects) of the line table at 1024 + 1440 light,
//      whether the row Edit / Delete buttons sit inside the scroller's visible box at 1024; "Add Line"
//      opened (placeholder "Line item description" 0 → 1) and cancelled; row "Edit …" opened and
//      cancelled (NO save — F-666B-B1: saving the draft form zeroes VAT).
//   B. Record Payment on INV-0009 (SENT, overdue) at 1440 light: hero tiles / badge / totals / testids
//      before → form opened → filled R 500,00 EFT ref "666B-OBS-AFTER" → Confirm → state WITHOUT a
//      reload → after reload (same?). The only write of this script.
//   C. Keyboard: Tab through header actions and the tables on the draft (1024 + 1440) and INV-0010
//      (1440): the focused element per Tab, its focus ring (outline / box-shadow), focus on <body>?
//      For buttons inside a horizontally scrolled table, whether focus scrolls them into view.
//   D. DetailHero consumers at 1440 light: a matter, a client, the dashboard — h1 count, hero count,
//      tile labels / values, document overflow (+ full-page shot).
// Usage: PHASE=after node 11-interact.cjs → $RAW/interact.json + tiles.
const path = require("path");
const fs = require("fs");
const L = require("./lib.cjs");
const P = require("./probes.cjs");
const P2 = require("./probes2.cjs");

const DRAFT_ID = "de6ac48f-bcfe-4c5e-b122-a8d012f152ec";
const out = { at: new Date().toISOString(), draft: {}, payment: {}, keyboard: {}, consumers: {} };
const tiles = [];
const consoleSink = [];
const lab = { label: "" };
const settle = async (p) => { await p.waitForLoadState("networkidle").catch(() => {}); await p.waitForTimeout(800); };
const shot = async (p, key, caption, opt = { fullPage: true }) => { const f = path.join(L.RAW, `${key}.png`); await p.screenshot({ path: f, ...opt }); tiles.push([key, f, caption]); };
const inst = async (p) => { await p.evaluate(P.HELPERS, ""); await p.evaluate(P.HELPERS2); };
const hero = async (p) => { await inst(p); return p.evaluate(P2.HERO); };

// Per-cell rendered line count from the text's client rects (not the row height).
const CELL_LINES = () => {
  const lt = [...document.querySelectorAll("#main-content h2")].find((h) => window.__t(h) === "Line Items");
  const tb = lt ? lt.closest("div.space-y-4").querySelector("table") : null;
  if (!tb) return null;
  const sc = window.__scroller(tb);
  const sr = sc.getBoundingClientRect();
  const heads = [...tb.querySelectorAll("thead th")].filter(window.__v).map(window.__t);
  const rows = [...tb.querySelectorAll("tbody tr")].filter((r) => window.__v(r) && !r.dataset.testid);
  return {
    headers: heads, scroller: { clientWidth: sc.clientWidth, scrollWidth: sc.scrollWidth, scrollLeft: sc.scrollLeft },
    rows: rows.map((r) => [...r.children].filter(window.__v).map((c) => { const rg = document.createRange(); rg.selectNodeContents(c); const tops = new Set([...rg.getClientRects()].filter((x) => x.width > 0).map((x) => Math.round(x.top))); return { text: window.__t(c).slice(0, 50), lines: tops.size, w: Math.round(c.getBoundingClientRect().width) }; })),
    rowButtons: rows.flatMap((r) => [...r.querySelectorAll("button")].map((b) => { const br = b.getBoundingClientRect(); return { label: b.getAttribute("aria-label"), left: Math.round(br.left), right: Math.round(br.right), visibleInScroller: br.left >= sr.left - 1 && br.right <= sr.left + sc.clientWidth + 1 }; })),
  };
};

const FOCUS = () => {
  const e = document.activeElement;
  if (!e || e === document.body) return { body: true };
  const cs = getComputedStyle(e);
  const region = e.closest('[data-slot="detail-hero-actions"]') ? "hero-actions" : e.closest('[data-slot="detail-hero"]') ? "hero" : e.closest("table") ? `table:${(() => { const s = e.closest("div.space-y-3, div.space-y-4, section, div.rounded-lg"); const h = s && s.querySelector("h2,h3"); return h ? window.__t(h) : "?"; })()}` : e.closest("main") ? "main" : e.closest("nav, aside, header") ? "shell" : "other";
  const sc = e.closest("table") ? window.__scroller(e.closest("table")) : null;
  const r = e.getBoundingClientRect();
  return { tag: e.tagName.toLowerCase(), name: (e.getAttribute("aria-label") || window.__t(e) || "").slice(0, 50), region, outline: `${cs.outlineStyle} ${cs.outlineWidth} ${cs.outlineColor}`, boxShadow: cs.boxShadow === "none" ? "none" : cs.boxShadow.slice(0, 80), ring: cs.outlineStyle !== "none" && parseFloat(cs.outlineWidth) > 0 || cs.boxShadow !== "none", inViewport: r.top >= 0 && r.bottom <= innerHeight && r.left >= 0 && r.right <= innerWidth, inScroller: sc ? r.left >= sc.getBoundingClientRect().left - 1 && r.right <= sc.getBoundingClientRect().left + sc.clientWidth + 1 : null, scrollerLeft: sc ? sc.scrollLeft : null };
};

async function tabWalk(p, n) {
  await p.locator("#main-content h1").click({ position: { x: 2, y: 2 } }).catch(() => {});
  const seq = [];
  for (let i = 0; i < n; i++) { await p.keyboard.press("Tab"); await p.waitForTimeout(60); await inst(p); seq.push(await p.evaluate(FOCUS)); }
  return seq;
}

(async () => {
  const browser = await L.chromium.launch();
  const S = await L.loginState(browser, "alice");
  const t = await L.idpToken("alice");
  const ids = JSON.parse(fs.readFileSync(path.join(L.RAW, "setup-02b.json"), "utf8")).notes;
  const api = async (id) => (await L.apiGet(t, `/api/invoices/${id}`)).json;

  // ---------- A. draft line table + editors ----------
  for (const w of [1024, 1440]) {
    const ctx = await L.newCtx(browser, S, { width: w, height: w === 1024 ? 768 : 900, theme: "light", extra: L.EXTRA });
    const p = await ctx.newPage(); L.watch(p, consoleSink, lab);
    lab.label = `draft|${w}`;
    await p.goto(`${L.BASE}/invoices/${DRAFT_ID}`, { waitUntil: "load" }); await settle(p); await inst(p);
    const cl = await p.evaluate(CELL_LINES);
    out.draft[`cells-${w}`] = cl;
    const lt = p.locator("#main-content h2", { hasText: "Line Items" }).locator("xpath=ancestor::div[contains(@class,'space-y-4')][1]");
    const f = path.join(L.RAW, `ia__draft-lines-${w}.png`); await lt.screenshot({ path: f });
    tiles.push([`ia__draft-lines-${w}`, f, `seeded Draft line items at ${w} light — scroller ${cl.scroller.clientWidth}/${cl.scroller.scrollWidth}; cell lines ${cl.rows[0].map((c) => c.lines).join("/")}; row buttons visible in scroller: ${cl.rowButtons.map((b) => `${b.label.split(" ")[0]} ${b.visibleInScroller}`).join(", ")}`]);
    // other line tables at 1024 for comparison
    if (w === 1024) for (const k of ["partial", "approved"]) { await p.goto(`${L.BASE}/invoices/${ids[k]}`, { waitUntil: "load" }); await settle(p); await inst(p); out.draft[`cells-${k}-1024`] = await p.evaluate(CELL_LINES); }
    if (w === 1440) {
      const ph0 = await p.getByPlaceholder("Line item description").count();
      await p.locator("#main-content").getByRole("button", { name: "Add Line", exact: true }).click(); await p.waitForTimeout(600);
      const ph1 = await p.getByPlaceholder("Line item description").count();
      await shot(p, "ia__draft-add-line-open", `seeded Draft — "Add Line" clicked: placeholder "Line item description" ${ph0} → ${ph1}; cancelled after (nothing saved)`);
      await p.getByRole("button", { name: "Cancel" }).first().click(); await p.waitForTimeout(500);
      const ph2 = await p.getByPlaceholder("Line item description").count();
      const edit = p.locator("#main-content").getByRole("button", { name: /^Edit / }).first();
      const editLabel = await edit.getAttribute("aria-label");
      await edit.click(); await p.waitForTimeout(600);
      const editHeading = await p.getByText("Edit Line Item").count();
      await shot(p, "ia__draft-edit-line-open", `seeded Draft — "${editLabel}" clicked: Edit Line Item open (heading ×${editHeading}); Cancel after (nothing saved)`);
      await p.getByRole("button", { name: "Cancel" }).first().click(); await p.waitForTimeout(500);
      const dl = await p.locator("#main-content").getByRole("button", { name: /^Delete (?!Draft)/ }).count();
      const a = await api(DRAFT_ID);
      out.draft.editors = { placeholderClosed: ph0, placeholderAfterAddLine: ph1, placeholderAfterCancel: ph2, editLabel, editHeading, rowDeleteButtons: dl, apiAfter: { status: a.status, total: a.total, taxAmount: a.taxAmount, updatedAt: a.updatedAt, lines: (a.lines || []).length } };
      out.keyboard["draft-1440"] = await tabWalk(p, 24);
    }
    if (w === 1024) { await p.goto(`${L.BASE}/invoices/${DRAFT_ID}`, { waitUntil: "load" }); await settle(p); out.keyboard["draft-1024"] = await tabWalk(p, 24); const f2 = path.join(L.RAW, "ia__draft-lines-1024-focused-edit.png"); const fo = out.keyboard["draft-1024"].find((x) => /^Edit /.test(x.name)); if (fo) { const focused = p.locator("#main-content").getByRole("button", { name: /^Edit / }).first(); await focused.focus(); await p.waitForTimeout(200); await lt.screenshot({ path: f2 }); tiles.push(["ia__draft-lines-1024-focused-edit", f2, `seeded Draft at 1024 — row Edit button focused by keyboard: scroller scrolled to ${(await p.evaluate(() => { const b = document.activeElement; let e = b; while (e && getComputedStyle(e).overflowX !== "auto") e = e.parentElement; return e ? e.scrollLeft : null; }))} px`]); } }
    await ctx.close();
  }

  // ---------- B. record payment on INV-0009 ----------
  {
    const id = ids["sent-overdue"];
    const ctx = await L.newCtx(browser, S, { width: 1440, height: 900, theme: "light", extra: L.EXTRA });
    const p = await ctx.newPage(); L.watch(p, consoleSink, lab);
    const net = []; p.on("request", (r) => { if (r.method() === "POST" || r.headers()["rsc"]) net.push(`${r.method()}${r.headers()["next-action"] ? " action" : ""}${r.headers()["rsc"] ? " rsc" : ""} ${r.url().replace(L.C.APP, "").replace(/[0-9a-f-]{36}/g, "{id}").slice(0, 100)}`); });
    const navs = []; p.on("framenavigated", (fr) => { if (fr === p.mainFrame()) navs.push(fr.url().replace(L.C.APP, "")); });
    const state = async () => { const h = await hero(p); return { badge: h.status.map((s) => `${s.text}/${s.variant}`), actions: h.actions.map((a) => a.text), tiles: h.tiles.map((x) => `${x.label}: ${x.value}${x.hint ? ` (${x.hint})` : ""}`), totals: h.totalsRows, agree: h.agree, invoiceBalanceDue: await p.getByTestId("invoice-balance-due").count() ? (await p.getByTestId("invoice-balance-due").first().innerText()).trim() : 0, totalsBalanceDue: await p.getByTestId("totals-balance-due").count() ? (await p.getByTestId("totals-balance-due").first().innerText()).trim() : 0, paymentRows: await p.evaluate(() => { const h = [...document.querySelectorAll("#main-content h3")].find((x) => x.textContent.trim() === "Payment History"); const s = h && h.parentElement; return s ? [...s.querySelectorAll("tbody tr")].map((r) => r.innerText.replace(/\s+/g, " ").trim()) : null; }), indicators: await p.evaluate(() => [...document.querySelectorAll("#main-content div.rounded-lg")].filter((d) => /^(Payment Received|Part payment received)/.test(d.innerText.trim())).map((d) => d.innerText.replace(/\s+/g, " ").slice(0, 160))) }; };
    lab.label = "payment|INV-0009";
    await p.goto(`${L.BASE}/invoices/${id}`, { waitUntil: "load" }); await settle(p);
    const a0 = await api(id);
    out.payment.apiBefore = { status: a0.status, total: a0.total, amountPaid: a0.amountPaid, balanceDue: a0.balanceDue };
    if (a0.status !== "SENT") throw new Error(`INV-0009 is ${a0.status}, expected SENT`);
    out.payment.before = await state();
    await shot(p, "ia__pay-0-before", `INV-0009 before Record Payment (SENT, overdue): ${out.payment.before.tiles.join(" · ")}`);
    await p.locator("#main-content").getByRole("button", { name: "Record Payment" }).click();
    const form = p.getByTestId("record-payment-form"); await form.waitFor();
    out.payment.formDefaults = { amount: await form.getByLabel("Amount", { exact: true }).inputValue(), method: (await form.getByTestId("payment-method-select").innerText()).trim(), balanceLine: (await p.getByTestId("payment-form-balance-due").innerText()).trim(), counts: { form: await p.getByTestId("record-payment-form").count(), method: await p.getByTestId("payment-method-select").count() } };
    await form.getByLabel("Amount", { exact: true }).fill("500");
    await form.getByPlaceholder(/CHK-12345/).fill("666B-OBS-AFTER");
    await shot(p, "ia__pay-1-filled", `INV-0009 record-payment-form filled R 500,00 EFT ref 666B-OBS-AFTER (defaults: amount ${out.payment.formDefaults.amount}, ${out.payment.formDefaults.method})`);
    const n0 = net.length, nav0 = navs.length;
    await form.getByRole("button", { name: "Confirm Payment" }).click();
    await form.waitFor({ state: "detached", timeout: 20000 }); await p.waitForTimeout(1500);
    out.payment.afterNoReload = await state();
    out.payment.requests = net.slice(n0); out.payment.navigationsDuringSave = navs.slice(nav0);
    await shot(p, "ia__pay-2-after-no-reload", `INV-0009 after Confirm Payment, NO reload: ${out.payment.afterNoReload.badge.join(",")}; ${out.payment.afterNoReload.tiles.join(" · ")}`);
    await p.reload({ waitUntil: "load" }); await settle(p);
    out.payment.afterReload = await state();
    await shot(p, "ia__pay-3-after-reload", `INV-0009 after reload: ${out.payment.afterReload.badge.join(",")}; ${out.payment.afterReload.tiles.join(" · ")}`);
    const a1 = await api(id);
    out.payment.apiAfter = { status: a1.status, total: a1.total, amountPaid: a1.amountPaid, balanceDue: a1.balanceDue };
    out.payment.sameAfterReload = JSON.stringify(out.payment.afterNoReload) === JSON.stringify(out.payment.afterReload);
    const hl = p.locator('#main-content [data-slot="detail-hero"]'); const f = path.join(L.RAW, "ia__pay-hero-after-reload.png"); await hl.screenshot({ path: f }); tiles.push(["ia__pay-hero-after-reload", f, "INV-0009 hero after the payment + reload (PARTIALLY_PAID, still overdue)"]);
    lab.label = "keyboard|INV-0010";
    await p.goto(`${L.BASE}/invoices/${ids.partial}`, { waitUntil: "load" }); await settle(p);
    out.keyboard["partial-1440"] = await tabWalk(p, 30);
    await ctx.close();
  }

  // ---------- D. DetailHero consumers ----------
  {
    const matters = L.list((await L.apiGet(t, "/api/matters?size=100")).json);
    const m = matters.find((x) => x.name === L.SETUP.matter);
    const targets = [["matter", `/matters/${m.id}`], ["client", `/clients/${m.clientId || m.customerId}`], ["dashboard", `/dashboard`]];
    const ctx = await L.newCtx(browser, S, { width: 1440, height: 900, theme: "light", extra: L.EXTRA });
    const p = await ctx.newPage(); L.watch(p, consoleSink, lab);
    for (const [k, u] of targets) {
      lab.label = `consumer|${k}`;
      const resp = await p.goto(`${L.BASE}${u}`, { waitUntil: "load" }); await settle(p); await inst(p);
      const h = await p.evaluate(P2.HERO);
      const c = await p.evaluate(() => { const se = document.scrollingElement; return { h1: [...document.querySelectorAll("#main-content h1")].map((x) => x.innerText.trim()), h1Page: document.querySelectorAll("h1").length, hOverflow: se.scrollWidth - se.clientWidth, heroes: document.querySelectorAll('[data-slot="detail-hero"]').length, statCards: document.querySelectorAll('#main-content [data-slot="stat-card"]').length, somethingWentWrong: document.body.innerText.includes("Something went wrong") }; });
      out.consumers[k] = { url: u.replace(/[0-9a-f-]{36}/g, "{id}"), status: resp && resp.status(), ...c, heroTiles: h.tiles ? h.tiles.map((x) => `${x.label}: ${x.value}`) : [], tileTestids: h.tileTestids, identifierChips: h.identifierChips, tileGrid: h.tileGrid && `${h.tileGrid.cols}x${h.tileGrid.rows}`, empties: await p.evaluate(P2.EMPTIES) };
      await shot(p, `ia__consumer-${k}`, `${k} ${u.replace(/[0-9a-f-]{36}/g, "{id}")} 1440 light — h1 ${JSON.stringify(c.h1)}, heroes ${c.heroes}, tiles ${out.consumers[k].heroTiles.join(" · ") || "-"}; docHOverflow ${c.hOverflow}`, { fullPage: false });
    }
    // the client page's own collections history (CollectionsHistoryTable's other caller)
    await ctx.close();
  }

  await browser.close();
  out.console = consoleSink;
  out.tiles = tiles;
  fs.writeFileSync(path.join(L.RAW, "interact.json"), JSON.stringify(out));
  console.log("draft", JSON.stringify({ c1024: out.draft["cells-1024"], ed: out.draft.editors }));
  console.log("payment", JSON.stringify({ api: [out.payment.apiBefore, out.payment.apiAfter], before: out.payment.before.tiles, after: out.payment.afterNoReload, same: out.payment.sameAfterReload, req: out.payment.requests, navs: out.payment.navigationsDuringSave }));
  console.log("consumers", JSON.stringify(out.consumers).slice(0, 2500));
  console.log("console", consoleSink.length, JSON.stringify(consoleSink.slice(0, 5)));
})();
