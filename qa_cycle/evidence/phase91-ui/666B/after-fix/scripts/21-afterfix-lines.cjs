// 666B OBS-AFTER-FIX step 21 (new): re-observe F-666B-A1 / A2 on the rebuilt head.
//   A. Line-items table on the seeded DRAFT de6ac48f, APPROVED INV-0008, PARTIALLY_PAID INV-0010 and
//      INV-0009 (now PARTIALLY_PAID) at 1024×768 + 1440×900 × light + dark: table width vs the scroller's
//      clientWidth, every header (box, cut px past the visible scroller edge, internal clip), per-cell
//      rendered line counts (Range client rects) keyed by header, section-header rows (left / right vs
//      the header row), row Edit / Delete inside the visible scroller at scrollLeft 0. Raw crop per variant.
//   B. Draft only, every variant: "Add Line" → placeholder "Line item description" 0 → 1 → Cancel → 0
//      (NOTHING saved — F-666B-B1); keyboard Tab from the h1 to the first row "Edit …" button: focus ring,
//      inside the scroller, scrollLeft; crop while focused.
//   C. Record Payment R 50,00 EFT ref "666B-OBS-AFTERFIX" on INV-0010 (PARTIALLY_PAID) at 1440 light:
//      hero tiles / totals / testids before → after Confirm with a window sentinel (no document load) →
//      after reload; API before / after. The only write of this script.
// Usage: PHASE=after-fix node 21-afterfix-lines.cjs → $RAW/lines.json + PNGs.
const path = require("path");
const fs = require("fs");
const L = require("./lib.cjs");
const P = require("./probes.cjs");
const P2 = require("./probes2.cjs");

const DRAFT_ID = "de6ac48f-bcfe-4c5e-b122-a8d012f152ec";
const out = { at: new Date().toISOString(), lines: {}, draft: {}, payment: {} };
const tiles = [];
const consoleSink = [];
const lab = { label: "" };
const settle = async (p) => { await p.waitForLoadState("networkidle").catch(() => {}); await p.waitForTimeout(800); };
const inst = async (p) => { await p.evaluate(P.HELPERS, ""); await p.evaluate(P.HELPERS2); };

const LINES = () => {
  const h = [...document.querySelectorAll("#main-content h2")].find((x) => window.__t(x) === "Line Items");
  const tb = h ? h.closest("div.space-y-4").querySelector("table") : null;
  if (!tb) return null;
  const sc = window.__scroller(tb);
  const sr = sc.getBoundingClientRect();
  const visL = sr.left, visR = sr.left + sc.clientWidth;
  const ths = [...tb.querySelectorAll("thead th")].filter(window.__v);
  const heads = ths.map(window.__t);
  const hr = tb.querySelector("thead tr").getBoundingClientRect();
  const lines = (c) => { const rg = document.createRange(); rg.selectNodeContents(c); return new Set([...rg.getClientRects()].filter((x) => x.width > 0).map((x) => Math.round(x.top))).size; };
  const rows = [...tb.querySelectorAll("tbody tr")].filter(window.__v);
  return {
    tableWidth: Math.round(tb.getBoundingClientRect().width), tableScrollWidth: tb.scrollWidth,
    scroller: { clientWidth: sc.clientWidth, scrollWidth: sc.scrollWidth, scrollLeft: sc.scrollLeft },
    fits: sc.scrollWidth <= sc.clientWidth && tb.getBoundingClientRect().width <= sc.clientWidth + 0.5,
    headers: ths.map((th) => { const r = th.getBoundingClientRect(); return { h: window.__t(th), w: Math.round(r.width), left: Math.round(r.left), right: Math.round(r.right), cutPx: Math.max(0, Math.round(r.right - visR)), internalClip: th.scrollWidth > th.clientWidth + 1, lines: lines(th), padL: getComputedStyle(th).paddingLeft }; }),
    sectionHeaders: rows.filter((r) => r.dataset.testid && r.dataset.testid.startsWith("section-header-")).map((r) => { const c = r.children[0]; const b = c.getBoundingClientRect(); const txt = c.firstChild && c.firstChild.nodeType === 3 ? c : c; const rg = document.createRange(); rg.selectNodeContents(txt); const tr = [...rg.getClientRects()][0]; const th0 = ths[0].getBoundingClientRect(); const thText = document.createRange(); thText.selectNodeContents(ths[0]); const t0 = [...thText.getClientRects()][0]; return { testid: r.dataset.testid, text: window.__t(c), colSpan: c.colSpan, leftDelta: Math.round(b.left - hr.left), rightDelta: Math.round(b.right - hr.right), textLeft: tr ? Math.round(tr.left) : null, headerTextLeft: t0 ? Math.round(t0.left) : null, textAligned: tr && t0 ? Math.abs(tr.left - t0.left) <= 1 : null }; }),
    rows: rows.filter((r) => !r.dataset.testid).map((r) => Object.fromEntries([...r.children].filter(window.__v).map((c, i) => [heads[i] || `c${i}`, { lines: lines(c), w: Math.round(c.getBoundingClientRect().width), text: window.__t(c).slice(0, 60) }]))),
    rowButtons: rows.flatMap((r) => [...r.querySelectorAll("button")].map((b) => { const br = b.getBoundingClientRect(); return { label: b.getAttribute("aria-label"), left: Math.round(br.left), right: Math.round(br.right), visibleInScroller: br.left >= visL - 1 && br.right <= visR + 1 }; })),
    docHOverflow: Math.max(0, document.scrollingElement.scrollWidth - document.scrollingElement.clientWidth),
  };
};

const FOCUS = () => {
  const e = document.activeElement;
  if (!e || e === document.body) return { body: true };
  const cs = getComputedStyle(e);
  const t = e.closest("table");
  const sc = t ? window.__scroller(t) : null;
  const r = e.getBoundingClientRect();
  const sr = sc ? sc.getBoundingClientRect() : null;
  // ring = outline or box-shadow; its outer extent must also fit inside the scroller (not clipped)
  const ringPx = cs.outlineStyle !== "none" ? parseFloat(cs.outlineWidth) + parseFloat(cs.outlineOffset || 0) : (cs.boxShadow.match(/(\d+(?:\.\d+)?)px\s*(?:,|$|\))/g) ? 3 : 0);
  return { tag: e.tagName.toLowerCase(), name: (e.getAttribute("aria-label") || window.__t(e) || "").slice(0, 60), outline: `${cs.outlineStyle} ${cs.outlineWidth}`, boxShadow: cs.boxShadow === "none" ? "none" : cs.boxShadow.slice(0, 90), box: { left: Math.round(r.left), right: Math.round(r.right), top: Math.round(r.top), bottom: Math.round(r.bottom) }, scroller: sr ? { left: Math.round(sr.left), visRight: Math.round(sr.left + sc.clientWidth), scrollLeft: sc.scrollLeft } : null, inScroller: sr ? r.left >= sr.left - 1 && r.right <= sr.left + sc.clientWidth + 1 : null, ringRoomRight: sr ? Math.round(sr.left + sc.clientWidth - r.right) : null, ringPx, inViewport: r.top >= 0 && r.bottom <= innerHeight && r.left >= 0 && r.right <= innerWidth };
};

(async () => {
  const browser = await L.chromium.launch();
  const S = await L.loginState(browser, "alice");
  const t = await L.idpToken("alice");
  const ids = JSON.parse(fs.readFileSync(path.join(L.RAW, "setup-02b.json"), "utf8")).notes;
  const api = async (id) => (await L.apiGet(t, `/api/invoices/${id}`)).json;
  const T = [["draft", DRAFT_ID], ["approved", ids.approved], ["partial", ids.partial], ["inv0009", ids["sent-overdue"]]];
  for (const [w, hgt] of [[1024, 768], [1440, 900]])
    for (const theme of ["light", "dark"]) {
      const ctx = await L.newCtx(browser, S, { width: w, height: hgt, theme, extra: L.EXTRA });
      const p = await ctx.newPage(); L.watch(p, consoleSink, lab);
      const v = `${w}-${theme}`;
      for (const [k, id] of T) {
        lab.label = `lines|${k}|${v}`;
        await p.goto(`${L.BASE}/invoices/${id}`, { waitUntil: "load" }); await settle(p); await inst(p);
        const m = await p.evaluate(LINES);
        const a = await api(id);
        m.invoice = { number: a.invoiceNumber, status: a.status };
        (out.lines[k] = out.lines[k] || {})[v] = m;
        const sec = p.locator("#main-content h2", { hasText: "Line Items" }).locator("xpath=ancestor::div[contains(@class,'space-y-4')][1]");
        const key = `lines__${k}-${v}`; const f = path.join(L.RAW, `${key}.png`); await sec.screenshot({ path: f });
        const desc = m.rows.map((r) => (r.Description || {}).lines).join("/"), tax = m.rows.map((r) => (r.Tax || {}).lines).join("/");
        tiles.push([key, f, `${a.invoiceNumber || "Draft"} (${a.status}) ${w} ${theme} — table ${m.tableWidth} / scroller ${m.scroller.clientWidth} (scrollW ${m.scroller.scrollWidth}); headers ${m.headers.map((x) => `${x.h}${x.cutPx ? ` CUT ${x.cutPx}` : ""}`).join(" ")}; Description lines ${desc}; Tax lines ${tax || "-"}${m.rowButtons.length ? `; row buttons in view ${m.rowButtons.filter((b) => b.visibleInScroller).length}/${m.rowButtons.length}` : ""}`]);
        if (k === "draft") {
          const ph0 = await p.getByPlaceholder("Line item description").count();
          await p.locator("#main-content").getByRole("button", { name: "Add Line", exact: true }).click(); await p.waitForTimeout(600);
          const ph1 = await p.getByPlaceholder("Line item description").count();
          const fa = path.join(L.RAW, `draft-addline__${v}.png`); await sec.screenshot({ path: fa });
          tiles.push([`draft-addline__${v}`, fa, `Draft ${w} ${theme} — "Add Line" clicked: placeholder "Line item description" ${ph0} → ${ph1} (then Cancel, nothing saved)`]);
          await p.getByRole("button", { name: "Cancel" }).first().click(); await p.waitForTimeout(500);
          const ph2 = await p.getByPlaceholder("Line item description").count();
          await p.locator("#main-content h1").click({ position: { x: 2, y: 2 } }).catch(() => {});
          const walk = [];
          let focus = null;
          for (let i = 0; i < 40; i++) { await p.keyboard.press("Tab"); await p.waitForTimeout(60); await inst(p); const fo = await p.evaluate(FOCUS); walk.push(fo.name || (fo.body ? "<body>" : "?")); if (/^Edit /.test(fo.name || "")) { focus = fo; break; } }
          const ff = path.join(L.RAW, `draft-focus-edit__${v}.png`); await sec.screenshot({ path: ff });
          tiles.push([`draft-focus-edit__${v}`, ff, `Draft ${w} ${theme} — keyboard focus on "${focus && focus.name}": in scroller ${focus && focus.inScroller}, scrollLeft ${focus && focus.scroller && focus.scroller.scrollLeft}, ${focus && focus.ringRoomRight}px right of the button to the scroller edge`]);
          // the next Tab (row Delete) as well
          await p.keyboard.press("Tab"); await p.waitForTimeout(60); await inst(p); const focusDel = await p.evaluate(FOCUS);
          out.draft[v] = { placeholder: [ph0, ph1, ph2], tabsToEdit: walk.length, walk, focusEdit: focus, focusDelete: focusDel };
        }
      }
      await ctx.close();
    }
  const da = await api(DRAFT_ID);
  out.draft.apiAfter = { status: da.status, total: da.total, taxAmount: da.taxAmount, updatedAt: da.updatedAt, lines: (da.lines || []).length };

  // ---------- C. record R 50 on INV-0010 ----------
  {
    const id = ids.partial;
    const ctx = await L.newCtx(browser, S, { width: 1440, height: 900, theme: "light", extra: L.EXTRA });
    const p = await ctx.newPage(); L.watch(p, consoleSink, lab);
    const loads = []; p.on("load", () => loads.push(new Date().toISOString()));
    const state = async () => { await inst(p); const h = await p.evaluate(P2.HERO); return { badge: h.status.map((s) => `${s.text}/${s.variant}`), actions: h.actions.map((a) => a.text), tiles: h.tiles.map((x) => `${x.label}: ${x.value}${x.hint ? ` (${x.hint})` : ""}`), totals: h.totalsRows, agree: h.agree, invoiceBalanceDue: (await p.getByTestId("invoice-balance-due").count()) ? (await p.getByTestId("invoice-balance-due").first().innerText()).trim() : 0, totalsBalanceDue: (await p.getByTestId("totals-balance-due").count()) ? (await p.getByTestId("totals-balance-due").first().innerText()).trim() : 0, paymentRows: await p.evaluate(() => { const h = [...document.querySelectorAll("#main-content h3")].find((x) => x.textContent.trim() === "Payment History"); const s = h && h.parentElement; return s ? [...s.querySelectorAll("tbody tr")].map((r) => r.innerText.replace(/\s+/g, " ").trim()) : null; }) }; };
    const shot = async (key, caption) => { const f = path.join(L.RAW, `${key}.png`); await p.screenshot({ path: f, fullPage: true }); tiles.push([key, f, caption]); };
    lab.label = "payment|INV-0010";
    await p.goto(`${L.BASE}/invoices/${id}`, { waitUntil: "load" }); await settle(p);
    const a0 = await api(id);
    out.payment.apiBefore = { number: a0.invoiceNumber, status: a0.status, total: a0.total, amountPaid: a0.amountPaid, balanceDue: a0.balanceDue };
    if (a0.status !== "PARTIALLY_PAID") throw new Error(`INV-0010 is ${a0.status}`);
    out.payment.before = await state();
    await shot("pay-0-before", `INV-0010 before: ${out.payment.before.tiles.join(" · ")}`);
    await p.evaluate(() => { window.__obsSentinel = 42; });
    const l0 = loads.length;
    await p.locator("#main-content").getByRole("button", { name: "Record Payment" }).click();
    const form = p.getByTestId("record-payment-form"); await form.waitFor();
    out.payment.formDefaults = { amount: await form.getByLabel("Amount", { exact: true }).inputValue(), method: (await form.getByTestId("payment-method-select").innerText()).trim() };
    await form.getByLabel("Amount", { exact: true }).fill("50");
    await form.getByPlaceholder(/CHK-12345/).fill("666B-OBS-AFTERFIX");
    await form.getByRole("button", { name: "Confirm Payment" }).click();
    await form.waitFor({ state: "detached", timeout: 20000 }); await p.waitForTimeout(1500);
    out.payment.sentinelAfterSave = await p.evaluate(() => window.__obsSentinel ?? null);
    out.payment.loadEventsDuringSave = loads.length - l0;
    out.payment.afterNoReload = await state();
    await shot("pay-1-after-no-reload", `INV-0010 after Confirm R 50,00, NO reload (sentinel ${out.payment.sentinelAfterSave}): ${out.payment.afterNoReload.tiles.join(" · ")}`);
    await p.reload({ waitUntil: "load" }); await settle(p);
    out.payment.afterReload = await state();
    await shot("pay-2-after-reload", `INV-0010 after reload: ${out.payment.afterReload.tiles.join(" · ")}`);
    const a1 = await api(id);
    out.payment.apiAfter = { status: a1.status, total: a1.total, amountPaid: a1.amountPaid, balanceDue: a1.balanceDue };
    out.payment.sameAfterReload = JSON.stringify(out.payment.afterNoReload) === JSON.stringify(out.payment.afterReload);
    await ctx.close();
  }
  await browser.close();
  out.console = consoleSink;
  out.tiles = tiles;
  fs.writeFileSync(path.join(L.RAW, "lines.json"), JSON.stringify(out));
  for (const [k, vv] of Object.entries(out.lines)) for (const [v, m] of Object.entries(vv)) console.log(k, v, m.invoice.status, `table ${m.tableWidth}/${m.scroller.clientWidth} sw ${m.scroller.scrollWidth} fits ${m.fits}`, "hdr", m.headers.map((x) => `${x.h}:${x.w}${x.cutPx ? "CUT" + x.cutPx : ""}${x.internalClip ? "CLIP" : ""}`).join(","), "desc", m.rows.map((r) => (r.Description || {}).lines).join("/"), "tax", m.rows.map((r) => (r.Tax || {}).lines).join("/"), "sec", JSON.stringify(m.sectionHeaders.map((s) => [s.text, s.leftDelta, s.rightDelta, s.textAligned])), "btns", m.rowButtons.map((b) => b.visibleInScroller).join(","), "docOv", m.docHOverflow);
  console.log("draft", JSON.stringify(Object.fromEntries(Object.entries(out.draft).map(([v, d]) => [v, d.placeholder ? { ph: d.placeholder, edit: d.focusEdit, del: d.focusDelete && { inScroller: d.focusDelete.inScroller, sl: d.focusDelete.scroller } } : d]))));
  console.log("payment", JSON.stringify(out.payment));
  console.log("console", consoleSink.length, JSON.stringify(consoleSink.slice(0, 5)));
})();
