// 666B OBS step 1: capture the fee-note detail page /invoices/[id] per status for ONE persona on the
// running e2e mock-auth stack (:3001). Adapted from 666A/before/scripts/01-capture.cjs (not edited);
// in-page probes in ./probes.cjs (DETAIL / COMPACT2). Playwright library, headless Chromium, DPR 1,
// reduced motion, timezone Africa/Johannesburg, app rail expanded (`sidebar_state=true`), theme via
// localStorage `kazi-theme`.
// Usage: PHASE=before node 01-capture.cjs <alice|carol>
//   alice: one target per status (ids from $RAW/setup-02b.json + the seeded / take-on fee notes,
//          looked up by GET): draft (seeded), approved, sent-overdue (OBS, due 2026-11-01), sent
//          (seeded INV-0001, not overdue), sent-takeon (take-on PS-1001, overdue, badge), partial,
//          paid, void.
//   carol: the approved fee note's URL only (expect the 404 notFound()).
// Read-only (page loads only). Per target × 1024×768 / 1440×900 × light / dark: a full-page shot
// (raw, /tmp, never committed) and the compact probe; at 1440 light the full probe; at 1024 light
// the spec selectors (SEL-COMPAT). Output: $RAW/persona-<persona>.json + tiles (05 merges).
const path = require("path");
const fs = require("fs");
const L = require("./lib.cjs");
const P = require("./probes.cjs");

const KEY = process.argv[2];
if (!["alice", "carol"].includes(KEY)) throw new Error("usage: <alice|carol>");
const VIEWPORTS = [[1024, 768], [1440, 900]];
const U = /[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/g;
const R = { persona: KEY, capturedAt: new Date().toISOString(), caps: null, targets: {}, variants: {}, surfaces: {}, selCompat: {} };
const consoleSink = [];
const tiles = [];
const lab = { label: "" };
let pagesOpened = 0;

async function selCompat(p) {
  const c = (loc) => loc.count();
  const main = p.locator("main");
  const out = {
    "main#main-content": await c(p.locator("main#main-content")),
    "main (strict locator('main'))": await c(p.locator("main")),
    "h1 in #main-content": await c(p.locator("#main-content h1")),
    "h1 page-wide": await c(p.locator("h1")),
    "body 'Something went wrong'": await c(p.getByText("Something went wrong")),
    "day-30-billing:64 / helpers legal-lifecycle:445 heading h1 'Draft fee note' (substring)": await c(p.getByRole("heading", { level: 1, name: "Draft fee note" })),
    "heading h1 'Draft fee note' exact": await c(p.getByRole("heading", { level: 1, name: "Draft fee note", exact: true })),
    "heading h1 accessible name": JSON.stringify(await p.locator("#main-content h1").evaluateAll((hs) => hs.map((h) => h.textContent.trim()))),
    "invoice-crud:50 / lifecycle:94 text 'Draft' exact (.first())": await c(p.getByText("Draft", { exact: true })),
    "lifecycle:116,238 text 'Approved' exact": await c(p.getByText("Approved", { exact: true })),
    "lifecycle:127,141,200 text 'Sent' exact": await c(p.getByText("Sent", { exact: true })),
    "lifecycle:171 text 'Paid' exact": await c(p.getByText("Paid", { exact: true })),
    "lifecycle:65 text 'Void' exact": await c(p.getByText("Void", { exact: true })),
    "lifecycle:110 text /INV-\\d+/": await c(p.getByText(/INV-\d+/)),
    "text 'Partially Paid' exact": await c(p.getByText("Partially Paid", { exact: true })),
    "lifecycle:100 button 'Approve' exact": await c(p.getByRole("button", { name: "Approve", exact: true })),
    "crud:79 / lifecycle:241 button 'Delete Draft'": await c(p.getByRole("button", { name: "Delete Draft" })),
    "lifecycle:104,121 button 'Send fee note'": await c(p.getByRole("button", { name: "Send fee note" })),
    "lifecycle:123,144 button /Record Payment/i": await c(p.getByRole("button", { name: /Record Payment/i })),
    "lifecycle:61 button 'Void' exact": await c(p.getByRole("button", { name: "Void", exact: true })),
    "button 'Preview'": await c(p.getByRole("button", { name: "Preview" })),
    "crud:55 main getByRole('row')": await c(main.getByRole("row")),
    "crud:101 button /^Edit / (line rows)": await c(main.getByRole("button", { name: /^Edit / })),
    "crud:133 button /^Delete / (line rows)": await c(main.getByRole("button", { name: /^Delete (?!Draft)/ })),
    "button 'Add Line'": await c(main.getByRole("button", { name: "Add Line", exact: true })),
    "helpers legal-lifecycle:473 placeholder 'Line item description'": await c(p.getByPlaceholder("Line item description")),
    "day-30:248 / trust-balanced:924 testid invoice-balance-due (text)": (await c(p.getByTestId("invoice-balance-due"))) ? `${await c(p.getByTestId("invoice-balance-due"))} ${(await p.getByTestId("invoice-balance-due").first().innerText()).trim()}` : 0,
    "testid totals-balance-due (text)": (await c(p.getByTestId("totals-balance-due"))) ? `${await c(p.getByTestId("totals-balance-due"))} ${(await p.getByTestId("totals-balance-due").first().innerText()).trim()}` : 0,
    "testid partially-paid-indicator": await c(p.getByTestId("partially-paid-indicator")),
    "lifecycle:151 / day-45:172 testid record-payment-form (closed)": await c(p.getByTestId("record-payment-form")),
    "testid payment-method-select (closed)": await c(p.getByTestId("payment-method-select")),
    "day-30:70 testid send-override-dialog": await c(p.getByTestId("send-override-dialog")),
    "take-on:986 testid fee-note-takeon-badge (text)": (await c(p.getByTestId("fee-note-takeon-badge"))) ? `${await c(p.getByTestId("fee-note-takeon-badge"))} ${(await p.getByTestId("fee-note-takeon-badge").first().innerText()).trim()}` : 0,
    "heading 'Payment History'": await c(p.getByRole("heading", { name: "Payment History" })),
    "text 'No payment events yet.'": await c(p.getByText("No payment events yet.")),
    "testid invoice-audit-section / heading 'Audit trail'": `${await c(p.getByTestId("invoice-audit-section"))}/${await c(p.getByRole("heading", { name: "Audit trail" }))}`,
    "testid custom-field-section / field-group-selector": `${await c(p.getByTestId("custom-field-section"))}/${await c(p.getByTestId("field-group-selector"))}`,
    "heading 'Generated Documents' / 'Collection activity'": `${await c(p.getByRole("heading", { name: "Generated Documents" }))}/${await c(p.getByRole("heading", { name: "Collection activity" }))}`,
    "text /overdue/i in main": await c(main.getByText(/overdue/i)),
    "link 'Back to fee notes'": await c(main.getByRole("link", { name: "Back to fee notes" })),
  };
  return out;
}

const shot = async (p, key, caption, opt = {}) => { const f = path.join(L.RAW, `${key}.png`); await p.screenshot({ path: f, ...opt }); tiles.push([key, f, caption]); };
const probe = async (p) => { await p.evaluate(P.HELPERS, ""); await p.evaluate(P.HELPERS2); await p.evaluate(`window.__detail = (${P.DETAIL.toString()})`); };
const settle = async (p) => { await p.waitForLoadState("networkidle").catch(() => {}); await p.waitForTimeout(900); };

(async () => {
  const browser = await L.chromium.launch();
  const S = await L.loginState(browser, KEY);
  const tok = await L.idpToken(KEY);
  const caps = await L.apiGet(tok, "/api/me/capabilities");
  R.caps = caps.json ? { role: caps.json.role, isAdmin: caps.json.isAdmin, isOwner: caps.json.isOwner, n: (caps.json.capabilities || []).length, hasInvoicing: (caps.json.capabilities || []).includes("INVOICING") } : caps;
  const a = await L.idpToken("alice");
  const ids = JSON.parse(fs.readFileSync(path.join(L.RAW, "setup-02b.json"), "utf8")).notes;
  const all = L.list((await L.apiGet(a, "/api/invoices")).json);
  const seededDraft = all.find((i) => i.status === "DRAFT" && !Object.values(ids).includes(i.id));
  const seededSent = all.find((i) => i.invoiceNumber === "INV-0001");
  const takeOn = all.filter((i) => i.takeOnBatchId || i.status === "SENT").map((i) => i).find((i) => i.invoiceNumber === "PS-1001");
  const TARGETS = KEY === "carol" ? [["carol-404", ids.approved]] : [["draft", seededDraft.id], ["approved", ids.approved], ["sent-overdue", ids["sent-overdue"]], ["sent", seededSent.id], ["sent-takeon", takeOn.id], ["partial", ids.partial], ["paid", ids.paid], ["void", ids.void]];
  for (const [k, id] of TARGETS) { const j = (await L.apiGet(a, `/api/invoices/${id}`)).json; R.targets[k] = { id, invoiceNumber: j.invoiceNumber, status: j.status, dueDate: j.dueDate, total: j.total, amountPaid: j.amountPaid, balanceDue: j.balanceDue, takeOnBatchId: j.takeOnBatchId }; }
  for (const [w, h] of VIEWPORTS)
    for (const theme of ["light", "dark"]) {
      const ctx = await L.newCtx(browser, S, { width: w, height: h, theme, extra: L.EXTRA });
      const p = await ctx.newPage();
      L.watch(p, consoleSink, lab);
      for (const [k, id] of TARGETS) {
        const v = `${w}-${theme}`;
        lab.label = `${KEY}|${k}|${v}`;
        const resp = await p.goto(`${L.BASE}/invoices/${id}`, { waitUntil: "load" });
        await settle(p);
        pagesOpened++;
        await probe(p);
        const m = await p.evaluate(P.COMPACT2);
        m.status = resp ? resp.status() : null;
        m.url = p.url().replace(L.BASE, "").replace(U, "{id}");
        m.title = await p.title();
        (R.variants[k] = R.variants[k] || {})[v] = m;
        const tg = R.targets[k];
        await shot(p, `s__${k}-${KEY}-${v}`, `${KEY} ${tg.invoiceNumber || "Draft"} (${tg.status}${k === "sent-overdue" || k === "sent-takeon" ? `, due ${tg.dueDate}` : ""}) ${w}×${h} ${theme} full page (${m.docHeight}px) — HTTP ${m.status}; h1 ${JSON.stringify(m.h1)}; docHOverflow ${m.hOverflow}`, { fullPage: true });
        if (w === 1440 && theme === "light") { const f = await p.evaluate(P.FULL); R.surfaces[k] = f; }
        if (w === 1024 && theme === "light") R.selCompat[k] = await selCompat(p);
      }
      await ctx.close();
    }
  await browser.close();
  R.console = { pagesOpened, errors: consoleSink.filter((c) => c.type === "error" || c.type === "pageerror").length, warnings: consoleSink.filter((c) => c.type === "warning").length, hydration: consoleSink.filter((c) => /hydrat/i.test(c.text)).length, messages: consoleSink };
  R.tiles = tiles;
  fs.writeFileSync(path.join(L.RAW, `persona-${KEY}.json`), JSON.stringify(R));
  console.log(KEY, "tiles", tiles.length, "pages", pagesOpened, "console", JSON.stringify({ e: R.console.errors, w: R.console.warnings, h: R.console.hydration }), "caps", JSON.stringify(R.caps));
  for (const [k] of TARGETS)
    for (const v of ["1024-light", "1440-light"]) {
      const m = R.variants[k][v];
      const d = m.detail || {};
      console.log(k, v, JSON.stringify({ st: m.status, h1: m.h1, h1n: m.h1Count, mainN: m.mainElCount, mainId: m.mainCount, inner: d.mainInner, frame: d.pageFrame && d.pageFrame.w, header: d.header && `${d.header.box.w}x${d.header.box.h} L${d.header.left.w} A${d.header.actions.w}`, hOv: m.hOverflow, badges: (d.badges || []).map((b) => b.text), meta: d.meta, btn: (d.buttons || []).map((b) => `${b.text}/${b.variant}`), totals: d.totals && d.totals.rows, lt: d.lineTable && d.lineTable.measure && `${d.lineTable.measure.scroller ? d.lineTable.measure.scroller.clientWidth + "/" + d.lineTable.measure.scroller.scrollWidth : "-"}`, ph: d.paymentHistory && d.paymentHistory.table && `${d.paymentHistory.table.scroller.clientWidth}/${d.paymentHistory.table.scroller.scrollWidth}`, nf: m.notFound, body: m.h1.length ? undefined : m.bodyTextHead.slice(0, 120), lowC: (m.lowContrast || []).slice(0, 3).map((x) => `${x.text}:${x.ratio}`) }));
    }
  console.log("selCompat 1024-light", JSON.stringify(R.selCompat).slice(0, 3000));
  console.log("console sample", JSON.stringify(consoleSink.slice(0, 6)));
})();
