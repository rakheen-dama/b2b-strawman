// 666A OBS step 1b: 01-capture.cjs (executed once, for `alice pre`; not edited) copied with two
// changes, because GET /api/billing-runs returns no `notes` field so 01's lookup by notes can't find
// the OBS fee run (03-fee-run.cjs stopped at step 2 for the same reason, after creating the run):
//   (1) the OBS run is found by L.RUN.periodFrom/periodTo + createdBy = Alice (the only run on the
//       stack with that period);
//   (2) state `preview` added: the run exists in PREVIEW (created by 03, wizard left at step 2) —
//       surfaces Fee runs list (the "Active billing run in progress" banner) + the run detail.
// Everything else (probes, surfaces, selectors, filter clicks) is 01's.
// Usage: PHASE=before node 01b-capture.cjs <persona> <state>   state = preview | post
const path = require("path");
const fs = require("fs");
const L = require("./lib.cjs");
const P = require("./probes.cjs");

const KEY = process.argv[2];
const STATE = process.argv[3];
const EXPECT_ROLE = { alice: "Owner", carol: "Member", "carol-cr-invoicing": L.NAMES.roleInvoicing };
if (!EXPECT_ROLE[KEY] || !["preview", "post"].includes(STATE)) throw new Error("usage: <alice|carol|carol-cr-invoicing> <preview|post>");
const WHO = KEY.startsWith("carol") ? "carol" : "alice";
const VIEWPORTS = [[1024, 768], [1440, 900]];
const U = /[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/g;
const STATUSES = ["DRAFT", "APPROVED", "SENT", "PARTIALLY_PAID", "PAID", "VOID"];

const R = { persona: KEY, state: STATE, login: WHO, capturedAt: new Date().toISOString(), caps: null, runId: null, surfaces: {}, variants: {}, selCompat: {}, filters: null };
const consoleSink = [];
const tiles = [];
const lab = { label: "" };
let pagesOpened = 0;

async function selCompat(p, sk) {
  const c = (loc) => loc.count();
  const main = p.locator("#main-content");
  const out = {
    "main#main-content": await c(p.locator("main#main-content")),
    "main (strict locator('main'))": await c(p.locator("main")),
    "h1 in #main-content": await c(p.locator("#main-content h1")),
    "h1 page-wide": await c(p.locator("h1")),
    "body 'Something went wrong'": await c(p.getByText("Something went wrong")),
  };
  if (sk.startsWith("list")) {
    out["day-30-billing:83,297 / day-90:113 heading h1 'Fee notes' (strict)"] = await c(p.getByRole("heading", { level: 1, name: "Fee notes" }));
    out["main link 'Fee notes' exact / 'Fee runs' exact"] = `${await c(main.getByRole("link", { name: "Fee notes", exact: true }))}/${await c(main.getByRole("link", { name: "Fee runs", exact: true }))}`;
    out["invoice-crud:55,106 locator('main').getByRole('row')"] = await c(p.locator("main").getByRole("row"));
    out["testid row-balance-due"] = await c(p.getByTestId("row-balance-due"));
    for (const id of ["fee-notes-total-outstanding", "fee-notes-overdue", "fee-notes-approved-unsent", "fee-notes-paid-this-month"]) out[`testid ${id} (text)`] = (await c(p.getByTestId(id))) ? `1 ${(await p.getByTestId(id).innerText()).trim()}` : 0;
    out["testid fee-note-takeon-badge"] = await c(p.getByTestId("fee-note-takeon-badge"));
    out["button 'New Fee Note' / accent create button"] = await c(main.getByRole("button", { name: /New Fee Note|Create|New fee note/i }));
  }
  if (sk.startsWith("billing-runs") && !sk.startsWith("billing-runs-new") && !sk.startsWith("billing-run-detail")) {
    out["billing-runs:20 heading h1 'Fee runs' (strict)"] = await c(p.getByRole("heading", { level: 1, name: "Fee runs" }));
    out["billing-runs:28-29 main link 'Fee notes' exact / 'Fee runs' exact"] = `${await c(main.getByRole("link", { name: "Fee notes", exact: true }))}/${await c(main.getByRole("link", { name: "Fee runs", exact: true }))}`;
    out["billing-runs:31,38 main link 'New Billing Run' (.first())"] = await c(main.getByRole("link", { name: "New Billing Run" }));
    out["main getByRole('row')"] = await c(main.getByRole("row"));
    out["text 'Active billing run in progress'"] = await c(p.getByText("Active billing run in progress"));
    out["text 'You do not have permission to view billing runs.'"] = await c(p.getByText(/You do not have permission to view billing runs/));
    out["heading h2 'Bulk Billing Runs is not enabled'"] = await c(p.getByRole("heading", { level: 2, name: "Bulk Billing Runs is not enabled" }));
  }
  if (sk.startsWith("billing-runs-new")) {
    out["billing-runs:42 heading h1 'New fee run' (strict)"] = await c(p.getByRole("heading", { level: 1, name: "New fee run" }));
    out["billing-runs:46 heading h2 'Configure Billing Run'"] = await c(p.getByRole("heading", { level: 2, name: "Configure Billing Run" }));
    out["nav 'Wizard steps'"] = await c(p.getByRole("navigation", { name: "Wizard steps" }));
  }
  if (sk.startsWith("billing-run-detail")) {
    out["heading h1 (any) / its text"] = `${await c(main.getByRole("heading", { level: 1 }))} ${JSON.stringify(await main.locator("h1").allInnerTexts())}`;
    out["link 'Back to Fee runs'"] = await c(main.getByRole("link", { name: "Back to Fee runs" }));
    out["heading h2 'Items'"] = await c(main.getByRole("heading", { level: 2, name: "Items" }));
    out["main getByRole('row')"] = await c(main.getByRole("row"));
    out["link 'View fee note'"] = await c(main.getByRole("link", { name: "View fee note" }));
    for (const lb of ["Clients", "Fee notes generated", "Sent", "Failed", "Total Amount"]) out[`stat label '${lb}' exact`] = await c(main.getByText(lb, { exact: true }));
    out["button 'Approve All Generated' / 'Cancel Run'"] = `${await c(main.getByRole("button", { name: "Approve All Generated" }))}/${await c(main.getByRole("button", { name: "Cancel Run" }))}`;
  }
  if (sk.startsWith("collections")) {
    out["heading h1 'Debtors'"] = await c(p.getByRole("heading", { level: 1, name: "Debtors" }));
    out["heading h2 'Debtor book' / 'Pending reminders'"] = `${await c(p.getByRole("heading", { level: 2, name: "Debtor book" }))}/${await c(p.getByRole("heading", { level: 2, name: "Pending reminders" }))}`;
    out["main getByRole('row')"] = await c(main.getByRole("row"));
    out["text 'No reminders awaiting approval'"] = await c(p.getByText("No reminders awaiting approval"));
    out["text 'No outstanding balances. Everything is settled.'"] = await c(p.getByText("No outstanding balances. Everything is settled."));
  }
  if (sk.startsWith("unbilled")) {
    out["heading h1 'Unbilled work'"] = await c(p.getByRole("heading", { level: 1, name: "Unbilled work" }));
    out["testid unbilled-period-help (text)"] = (await c(p.getByTestId("unbilled-period-help"))) ? (await p.getByTestId("unbilled-period-help").innerText()).trim() : 0;
    out["form 'Unbilled work filters'"] = await c(p.getByRole("form", { name: "Unbilled work filters" }));
    out["testid unbilled-currency (text)"] = (await c(p.getByTestId("unbilled-currency"))) ? (await p.getByTestId("unbilled-currency").innerText()).trim() : 0;
    out["testid unbilled-work-table / unbilled-grand-total"] = `${await c(p.getByTestId("unbilled-work-table"))}/${await c(p.getByTestId("unbilled-grand-total"))}`;
    out["heading 'No unbilled work'"] = await c(p.getByText("No unbilled work", { exact: true }));
    out["main getByRole('row')"] = await c(main.getByRole("row"));
  }
  return out;
}

async function page(browser, S, w, h, theme) {
  const ctx = await L.newCtx(browser, S, { width: w, height: h, theme, extra: L.EXTRA });
  const p = await ctx.newPage();
  L.watch(p, consoleSink, lab);
  return { ctx, p };
}
const shot = async (p, key, caption, opt = {}) => {
  const f = path.join(L.RAW, `${key}.png`);
  await p.screenshot({ path: f, ...opt });
  tiles.push([key, f, caption]);
};
const probe = async (p) => {
  await p.evaluate(P.HELPERS, "");
  await p.evaluate(P.HELPERS2);
  await p.evaluate(`window.__fee = (${P.FEE.toString()})`);
};
const settle = async (p) => { await p.waitForLoadState("networkidle").catch(() => {}); await p.waitForTimeout(700); };

(async () => {
  const browser = await L.chromium.launch();
  const S = await L.loginState(browser, WHO);
  const tok = await L.idpToken(WHO);
  const caps = await L.apiGet(tok, "/api/me/capabilities");
  R.caps = caps.json ? { role: caps.json.role, isAdmin: caps.json.isAdmin, isOwner: caps.json.isOwner, capabilities: caps.json.capabilities } : caps;
  if (!caps.json || caps.json.role !== EXPECT_ROLE[KEY]) throw new Error(`persona ${KEY} expects role ${EXPECT_ROLE[KEY]}, /api/me/capabilities says ${caps.json && caps.json.role}`);
  const a = await L.idpToken("alice");
  const runs = L.list((await L.apiGet(a, "/api/billing-runs?size=100&sort=createdAt,desc")).json);
  const aliceId = (await L.apiGet(a, "/api/me/capabilities")).json.memberId;
  const run = runs.find((r) => r.periodFrom === L.RUN.periodFrom && r.periodTo === L.RUN.periodTo && r.createdBy === aliceId);
  R.runId = run ? run.id : null;
  R.runStatus = run ? run.status : null;
  if (!run) throw new Error("states preview/post need the OBS fee run (03-fee-run.cjs)");
  const e = L.EMPTY_PERIOD;
  const SURF = STATE === "preview" ? [
    ["billing-runs-preview", "/invoices/billing-runs"],
    ["billing-run-detail-preview", `/invoices/billing-runs/${run.id}`],
  ] : [
    ["list", "/invoices"],
    ["billing-runs", "/invoices/billing-runs"],
    ["billing-runs-new", "/invoices/billing-runs/new"],
    ["billing-run-detail", `/invoices/billing-runs/${run.id}`],
    ["collections", "/invoices/collections"],
    ["unbilled", "/invoices/unbilled"],
    ["unbilled-empty", `/invoices/unbilled?periodFrom=${e.periodFrom}&periodTo=${e.periodTo}&currency=ZAR`],
  ];
  for (const [w, h] of VIEWPORTS)
    for (const theme of ["light", "dark"]) {
      const { ctx, p } = await page(browser, S, w, h, theme);
      for (const [sk, url] of SURF) {
        const v = `${w}-${theme}`;
        lab.label = `${KEY}-${STATE}|${sk}|${v}`;
        const resp = await p.goto(`${L.BASE}${url}`, { waitUntil: "load" });
        await settle(p);
        pagesOpened++;
        await probe(p);
        const m = await p.evaluate(P.COMPACT);
        m.status = resp ? resp.status() : null;
        m.url = p.url().replace(L.BASE, "").replace(U, "{id}");
        m.title = await p.title();
        (R.variants[sk] = R.variants[sk] || {})[v] = m;
        await shot(p, `s__${sk}-${KEY}-${STATE}-${v}`, `${KEY} (${STATE}) ${sk} ${w}×${h} ${theme} full page (${m.docHeight}px) — HTTP ${m.status}; h1 ${JSON.stringify(m.h1)}; docHOverflow ${m.hOverflow}`, { fullPage: true });
        if (w === 1440 && theme === "light") { const f = await p.evaluate(P.FULL); f.requested = url; R.surfaces[sk] = f; }
        if (w === 1024 && theme === "light") R.selCompat[sk] = await selCompat(p, sk);
      }
      await ctx.close();
    }
  // Status filter links (click each; record the resulting URL, rows, empty state) + URL-only state.
  if (KEY !== "carol" && STATE === "post") {
    const { ctx, p } = await page(browser, S, 1440, 900, "light");
    R.filters = { clicks: [], urlOnly: [] };
    lab.label = `${KEY}-${STATE}|filters|1440-light`;
    await p.goto(`${L.BASE}/invoices`, { waitUntil: "load" });
    await settle(p);
    const names = await p.locator("#main-content div.flex-wrap").filter({ hasText: /^Filter:/ }).getByRole("link").allInnerTexts();
    for (const n of names) {
      await p.locator("#main-content div.flex-wrap").filter({ hasText: /^Filter:/ }).getByRole("link", { name: n.trim(), exact: true }).click();
      await p.waitForURL((u) => true);
      await settle(p);
      pagesOpened++;
      await probe(p);
      const m = await p.evaluate(P.COMPACT);
      R.filters.clicks.push({ link: n.trim(), url: p.url().replace(L.BASE, ""), rows: m.fee.tables[0] ? m.fee.tables[0].bodyRows : 0, empty: m.fee.empties.map((x) => x.text), activeChip: (m.fee.filters || []).filter((x) => x.active).map((x) => x.text), pill: m.fee.pill.map((x) => x.text), tiles: m.fee.tiles.map((x) => `${x.label}=${x.value}`), firstCol: m.fee.tables[0] ? m.fee.tables[0].firstCellLines : [] });
      await shot(p, `f__${n.trim().replace(/\W+/g, "_")}-${KEY}-${STATE}`, `${KEY} (${STATE}) /invoices filter "${n.trim()}" clicked → ${p.url().replace(L.BASE, "")} — ${m.fee.tables[0] ? m.fee.tables[0].bodyRows + " rows" : "empty: " + m.fee.empties.map((x) => x.text).join(" / ")}`, { fullPage: true });
    }
    const inv = L.list((await L.apiGet(a, "/api/invoices")).json);
    const cid = inv[0] && inv[0].clientId;
    for (const [lbl, q] of [["customerId (first fee note's client)", `?customerId=${cid}`], ["customerId + status=SENT", `?customerId=${cid}&status=SENT`], ["invalid status=FOO", "?status=FOO"], ["lowercase status=draft", "?status=draft"]]) {
      lab.label = `${KEY}-${STATE}|url ${lbl}|1440-light`;
      const resp = await p.goto(`${L.BASE}/invoices${q}`, { waitUntil: "load" });
      await settle(p);
      pagesOpened++;
      await probe(p);
      const m = await p.evaluate(P.COMPACT);
      R.filters.urlOnly.push({ label: lbl, url: p.url().replace(L.BASE, "").replace(U, "{id}"), status: resp && resp.status(), rows: m.fee.tables[0] ? m.fee.tables[0].bodyRows : 0, clients: m.fee.tables[0] ? [...new Set((await p.locator("#main-content tbody tr td:nth-child(2)").allInnerTexts()).map((x) => x.trim()))] : [], empty: m.fee.empties.map((x) => x.text), activeChip: (m.fee.filters || []).filter((x) => x.active).map((x) => x.text), pill: m.fee.pill.map((x) => x.text) });
    }
    R.filters.customerIdControl = "no UI control sets customerId on /invoices (page.tsx reads searchParams.customerId → API clientId; filter chips drop it: their hrefs are /invoices?status=X)";
    await ctx.close();
  }
  await browser.close();
  R.console = { pagesOpened, errors: consoleSink.filter((c) => c.type === "error" || c.type === "pageerror").length, warnings: consoleSink.filter((c) => c.type === "warning").length, hydration: consoleSink.filter((c) => /hydrat/i.test(c.text)).length, messages: consoleSink };
  R.tiles = tiles;
  fs.writeFileSync(path.join(L.RAW, `persona-${KEY}-${STATE}.json`), JSON.stringify(R));
  console.log(KEY, STATE, "tiles", tiles.length, "pages", pagesOpened, "console", consoleSink.length, "caps", JSON.stringify({ role: R.caps.role, n: (R.caps.capabilities || []).length }), "run", R.runId);
  for (const [sk] of SURF)
    for (const v of ["1024-light", "1440-light"]) {
      const m = R.variants[sk][v];
      const f = m.fee;
      console.log(sk, v, JSON.stringify({ st: m.status, url: m.url, h1: m.h1, h1n: m.h1Count, mainN: m.mainElCount, inner: f.mainInner, hOv: m.hOverflow, tabs: (f.tabLinks || []).map((x) => `${x.text}:${x.ariaCurrent}`), pill: (f.pill || []).map((x) => x.text), tiles: (f.tiles || []).map((x) => `${x.label}=${x.value}`), tileGrid: f.tileGrid && `${f.tileGrid.cols}c w${f.tileGrid.width} parent${f.tileGrid.parentWidth}`, stats: (f.stats || []).map((x) => `${x.label}=${x.value}`), statGrid: f.statGrid && `${f.statGrid.cols}c w${f.statGrid.width}`, tables: (f.tables || []).map((x) => `${x.scroller ? x.scroller.clientWidth + "/" + x.scroller.scrollWidth : "-"} tbl${x.tableWidth} rows${x.bodyRows} h${x.rowHeightMin}-${x.rowHeightMax} cut:${x.cols.filter((c) => c.cutPx).map((c) => c.h + "=" + c.cutPx).join(",")}`), empties: (f.empties || []).map((x) => x.text.slice(0, 60)), nf: m.notFound, body: m.h1.length ? undefined : m.bodyTextHead.slice(0, 120), lowC: m.lowContrast.slice(0, 3).map((x) => `${x.text}:${x.ratio}`) }));
    }
  if (R.filters) console.log("filters", JSON.stringify(R.filters));
  const cBy = {}; for (const mm of consoleSink) cBy[mm.label.split("|")[1]] = (cBy[mm.label.split("|")[1]] || 0) + 1;
  console.log("console by surface", JSON.stringify(cBy), JSON.stringify(consoleSink.slice(0, 5)));
})();
