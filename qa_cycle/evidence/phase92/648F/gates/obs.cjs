// 648F OBS — the section 86(4) invested block on the Client Trust Statement (CTS) and the
// Statement of Account (SoA), registry Type labels on the CTS, the client-ledger-statement report's
// summary.invested, and the legal-za template pack v16.
// Stack: FRESH e2e mock-auth stack built from the 648F worktree at 6bff38cd0 (frontend :3001,
// backend :8081, mock IDP :8090, Postgres :5433). Data = the legal-lifecycle + legal-depth-ii runs
// (QuickCollect: R45 000 deposit, R30 000 s86(4) placement, interest gross R100 / Fund R5 / net R95;
// Sipho (depth-ii run): R5 000 deposit, R1 250 payment, no investment).
// Browser: headless Chromium (Playwright library), mock-login UI → Alice (owner).
// Documents are OBSERVED as rendered output: the Generate dialog's preview iframe (srcdoc saved +
// re-rendered standalone at 1280 for a full-page PNG) and the PDF downloaded through the dialog
// (text via ghostscript txtwrite, page PNGs via ghostscript png16m).
// Report JSON: POST /api/report-definitions/client-ledger-statement/execute with a mock-IDP bearer
// (Alice) — the firm report screen is a server action, so the backend JSON is not on the wire.
// psql is READ-ONLY (SELECT) evidence. No writes other than what the firm UI itself does when a
// statement is generated (SoA "Preview & Save" stores the statement — a normal firm action).
// Usage: node obs.cjs <phase>   phase = capture | report | ledger | all | pdf <soaQcGeneratedId> <soaSiphoGeneratedId>
const W = "/Users/rakheendama/Projects/2026/worktree-epic-648F";
const { chromium } = require(`${W}/frontend/node_modules/@playwright/test`);
const fs = require("fs");
const { execFileSync } = require("child_process");

const FIRM = "http://localhost:3001";
const BACKEND = "http://localhost:8081";
const IDP = "http://localhost:8090";
const SLUG = "e2e-test-org";
const BASE = `${FIRM}/org/${SLUG}`;
const EV = `${W}/qa_cycle/evidence/phase92/648F/gates`;
const OUT = `${EV}/obs`;
fs.mkdirSync(OUT, { recursive: true });
const PHASE = process.argv[2] || "all";

const QC = { name: "QuickCollect", customerId: "2db7efe4-899b-4589-a1bf-dc483496e762", matterId: "5a7e8a76-b26a-42ea-9de1-ff005a20c63e" };
const SIPHO = { name: "Sipho", customerId: "c2b4ec99-9879-438f-a82e-7112c3b7e519", matterId: "17b1ea6f-c6de-4748-90ae-69abcde76608" };
const GENERAL_ACCT = "27564008-6ca0-4b6d-8147-7e253b574b31";

const TODAY = new Intl.DateTimeFormat("en-CA", { timeZone: "Africa/Johannesburg" }).format(new Date());
const isoDate = (days) => { const d = new Date(`${TODAY}T12:00:00Z`); d.setUTCDate(d.getUTCDate() + days); return d.toISOString().slice(0, 10); };
const PERIOD = { from: isoDate(-90), to: TODAY };

const resultFile = `${OUT}/obs-result-${PHASE}.json`;
const result = { startedAt: new Date().toISOString(), phase: PHASE, today: TODAY, period: PERIOD, docs: {}, report: {}, ledger: {}, consoleErrors: [], pageErrors: [], notes: [] };
const save = () => fs.writeFileSync(resultFile, JSON.stringify(result, null, 2));

const norm = (s) => (s || "").replace(/[\s  ]+/g, " ").trim();
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

// ── ghostscript PDF → text + PNG ────────────────────────────────────
function pdfToText(pdf, txt) {
  execFileSync("gs", ["-q", "-dNOPAUSE", "-dBATCH", "-sDEVICE=txtwrite", `-sOutputFile=${txt}`, pdf]);
  return fs.readFileSync(txt, "utf8");
}
function pdfToPng(pdf, prefix) {
  execFileSync("gs", ["-q", "-dNOPAUSE", "-dBATCH", "-sDEVICE=png16m", "-r110", `-sOutputFile=${prefix}-p%d.png`, pdf]);
}

// ── browser ─────────────────────────────────────────────────────────
let browser;
async function firmPage() {
  const ctx = await browser.newContext({ viewport: { width: 1280, height: 1000 }, timezoneId: "Africa/Johannesburg", acceptDownloads: true });
  const p = await ctx.newPage();
  p.on("console", (m) => { if (m.type() === "error") result.consoleErrors.push({ url: p.url(), text: m.text().slice(0, 400) }); });
  p.on("pageerror", (e) => result.pageErrors.push({ url: p.url(), text: String(e).slice(0, 400) }));
  await p.goto(`${FIRM}/mock-login`, { waitUntil: "load" });
  await p.getByRole("button", { name: "Sign In" }).click();
  await p.waitForURL(/\/org\//, { timeout: 60_000 });
  result.notes.push(`mock-login → ${p.url()}`);
  return { ctx, p };
}

async function openGroupedTab(p, groupId, tabId) {
  const group = p.getByTestId(`tab-group-${groupId}`).filter({ visible: true });
  const item = p.getByTestId(`tab-item-${tabId}`).filter({ visible: true });
  await group.waitFor({ timeout: 15_000 });
  for (let i = 0; i < 3; i++) {
    await group.click();
    const open = await item.waitFor({ state: "visible", timeout: 2_000 }).then(() => true, () => false);
    if (open) { await item.click(); return; }
  }
  throw new Error(`sub-tab ${groupId}/${tabId} never opened`);
}

/** Renders saved HTML standalone at 1280 width for a full-page screenshot of the document itself. */
async function renderStandalone(ctx, html, png) {
  const q = await ctx.newPage();
  await q.setViewportSize({ width: 1280, height: 1000 });
  await q.setContent(html, { waitUntil: "load" });
  await q.screenshot({ path: png, fullPage: true });
  const text = await q.evaluate(() => document.body.innerText);
  // tables as arrays of cell texts, with the nearest preceding heading
  const tables = await q.evaluate(() => [...document.querySelectorAll("table")].map((t) => {
    let h = null; let x = t;
    while (x && !h) { let s = x.previousElementSibling; while (s && !h) { if (/^H[1-6]$/.test(s.tagName) || (s.innerText && s.innerText.length < 80 && s.querySelector && s.querySelector("strong"))) h = s.innerText.trim(); s = s.previousElementSibling; } x = x.parentElement; }
    return { heading: h, rows: [...t.querySelectorAll("tr")].map((tr) => [...tr.querySelectorAll("th,td")].map((c) => c.innerText.replace(/\s+/g, " ").trim())) };
  }));
  await q.close();
  return { text, tables };
}

// ── CTS via the client page → Generate Document → Client Trust Statement ──
async function captureCts(ctx, p, who) {
  const tag = `cts-${who.name.toLowerCase()}`;
  const rec = { who: who.name, customerId: who.customerId };
  await p.goto(`${BASE}/clients/${who.customerId}`, { waitUntil: "load" });
  await p.getByRole("heading", { level: 1 }).waitFor({ timeout: 30_000 });
  rec.clientHeading = await p.getByRole("heading", { level: 1 }).innerText();
  await p.getByTestId("client-header-actions").getByRole("button", { name: "Generate Document" }).click();
  await p.getByRole("menuitem", { name: "Client Trust Statement", exact: true }).click();
  const dialog = p.getByRole("dialog", { name: "Generate: Client Trust Statement" });
  await dialog.waitFor({ timeout: 20_000 });
  const next = dialog.getByRole("button", { name: "Next: Preview" });
  if (await next.isVisible().catch(() => false)) { await next.click(); rec.clauseStep = true; }
  const iframe = dialog.locator('iframe[title="Document Preview"]');
  await iframe.waitFor({ timeout: 30_000 });
  await dialog.frameLocator('iframe[title="Document Preview"]').locator("body").getByText("Client Trust Statement").first().waitFor({ timeout: 30_000 }).catch(() => {});
  await p.waitForTimeout(1200);
  const html = await iframe.getAttribute("srcdoc");
  fs.writeFileSync(`${OUT}/${tag}-preview.html`, html || "");
  await p.screenshot({ path: `${OUT}/${tag}-dialog-1280.png` });
  const r = await renderStandalone(ctx, html, `${OUT}/${tag}-preview-1280.png`);
  rec.previewText = r.text; rec.previewTables = r.tables;
  fs.writeFileSync(`${OUT}/${tag}-preview.txt`, r.text);
  // PDF through the dialog
  try {
    const btn = dialog.getByRole("button", { name: /^(Download PDF|Download anyway)$/ });
    await btn.waitFor({ timeout: 15_000 });
    const dl = p.waitForEvent("download", { timeout: 60_000 });
    await btn.click();
    const d = await dl;
    rec.pdfFilename = d.suggestedFilename();
    const pdf = `${OUT}/${tag}.pdf`;
    await d.saveAs(pdf);
    rec.pdfText = pdfToText(pdf, `${OUT}/${tag}-pdf.txt`);
    pdfToPng(pdf, `${OUT}/${tag}-pdf`);
  } catch (e) { rec.pdfError = String(e).slice(0, 400); }
  await p.keyboard.press("Escape");
  result.docs[tag] = rec; save();
  return rec;
}

// ── SoA via the matter page → Finance → Statements → Generate ──
async function captureSoa(ctx, p, who) {
  const tag = `soa-${who.name.toLowerCase()}`;
  const rec = { who: who.name, matterId: who.matterId, period: PERIOD };
  await p.goto(`${BASE}/matters/${who.matterId}`, { waitUntil: "load" });
  await p.getByRole("heading", { level: 1 }).waitFor({ timeout: 30_000 });
  rec.matterHeading = await p.getByRole("heading", { level: 1 }).innerText();
  await openGroupedTab(p, "finance", "statements");
  const tab = p.getByTestId("project-statements-tab");
  await tab.waitFor({ timeout: 15_000 });
  await tab.getByText(/Loading statements/).waitFor({ state: "hidden", timeout: 15_000 }).catch(() => {});
  await tab.getByTestId("open-generate-statement-btn").click();
  const dialog = p.getByTestId("statement-of-account-dialog");
  await dialog.waitFor({ timeout: 15_000 });
  await dialog.getByTestId("statement-period-start-input").fill(PERIOD.from);
  await dialog.getByTestId("statement-period-end-input").fill(PERIOD.to);
  await dialog.getByTestId("statement-generate-btn").click();
  await dialog.getByTestId("statement-download-btn").waitFor({ timeout: 60_000 });
  const iframe = dialog.getByTestId("statement-preview-container").locator('iframe[title="Document Preview"]');
  await iframe.waitFor({ timeout: 30_000 });
  await p.waitForTimeout(1200);
  const html = await iframe.getAttribute("srcdoc");
  fs.writeFileSync(`${OUT}/${tag}-preview.html`, html || "");
  await p.screenshot({ path: `${OUT}/${tag}-dialog-1280.png` });
  const r = await renderStandalone(ctx, html, `${OUT}/${tag}-preview-1280.png`);
  rec.previewText = r.text; rec.previewTables = r.tables;
  fs.writeFileSync(`${OUT}/${tag}-preview.txt`, r.text);
  try {
    const dl = p.waitForEvent("download", { timeout: 45_000 });
    await dialog.getByTestId("statement-download-btn").click();
    const d = await dl;
    rec.pdfFilename = d.suggestedFilename();
    const pdf = `${OUT}/${tag}.pdf`;
    await d.saveAs(pdf);
    rec.pdfText = pdfToText(pdf, `${OUT}/${tag}-pdf.txt`);
    pdfToPng(pdf, `${OUT}/${tag}-pdf`);
  } catch (e) {
    rec.pdfError = String(e).slice(0, 400);
    rec.dialogAlert = await dialog.getByRole("alert").allInnerTexts().catch(() => []);
    await p.screenshot({ path: `${OUT}/${tag}-download-attempt-1280.png` });
  }
  await p.keyboard.press("Escape");
  result.docs[tag] = rec; save();
  return rec;
}

// ── report API + report screen ──────────────────────────────────────
async function aliceToken() {
  const r = await fetch(`${IDP}/token`, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ userId: "user_e2e_alice", orgId: "org_e2e_test", orgSlug: SLUG, orgRole: "org:owner" }) });
  if (!r.ok) throw new Error(`token ${r.status}`);
  return (await r.json()).access_token;
}
async function reportApi(p, token, who, saveAs) {
  const body = { parameters: { trust_account_id: GENERAL_ACCT, customer_id: who.customerId, dateFrom: PERIOD.from, dateTo: PERIOD.to }, page: 0, size: 50 };
  const res = await p.request.post(`${BACKEND}/api/report-definitions/client-ledger-statement/execute`, { headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" }, data: body });
  const json = await res.json().catch(async () => (await res.text()).slice(0, 800));
  const rec = { at: new Date().toISOString(), method: "POST", path: "/api/report-definitions/client-ledger-statement/execute", request: body, status: res.status(), body: json };
  fs.writeFileSync(`${OUT}/api-report-${saveAs}.json`, JSON.stringify(rec, null, 2));
  return rec;
}
async function reportScreen(p, who, tag) {
  const rec = {};
  await p.goto(`${BASE}/reports/client-ledger-statement`, { waitUntil: "load" });
  await p.getByRole("heading", { level: 1 }).waitFor({ timeout: 30_000 });
  rec.heading = await p.getByRole("heading", { level: 1 }).innerText();
  async function pick(id, search) {
    await p.locator(`#param-${id}`).click();
    const opt = p.getByRole("option").filter({ hasText: search }).first();
    await opt.waitFor({ timeout: 15_000 });
    await opt.click();
  }
  await pick("trust_account_id", "Mathebula & Partners Trust Account");
  await pick("customer_id", who.searchName);
  await p.locator("#param-dateFrom").fill(PERIOD.from);
  await p.locator("#param-dateTo").fill(PERIOD.to);
  await p.getByRole("button", { name: /^(Run Report|Run)$/ }).click();
  await p.waitForTimeout(4000);
  await p.screenshot({ path: `${OUT}/report-screen-${tag}-1280.png`, fullPage: true });
  rec.mainText = norm(await p.locator("main").innerText());
  rec.mentionsInvested = /invest/i.test(rec.mainText);
  rec.showsObjectObject = /\[object Object\]/.test(rec.mainText);
  return rec;
}

// ── stored SoA PDFs (the statement the UI generated) via the backend download endpoint ──
// The dialog's Download PDF is a server action that follows the presigned S3 URL from inside the
// frontend container (localhost:4567 there is not LocalStack) → "Failed to download document."
// (known e2e-stack environment issue, 628F blockers/generated-doc-download-env.txt). The same stored
// PDF is fetched from the host with Alice's bearer; the redirect to localhost:4567 resolves here.
async function storedPdf(p, token, generatedId, tag) {
  const res = await p.request.get(`${BACKEND}/api/generated-documents/${generatedId}/download`, { headers: { Authorization: `Bearer ${token}` }, maxRedirects: 5 });
  const rec = { generatedId, status: res.status(), contentType: res.headers()["content-type"] };
  if (res.ok()) {
    const pdf = `${OUT}/${tag}.pdf`;
    fs.writeFileSync(pdf, await res.body());
    rec.pdfText = pdfToText(pdf, `${OUT}/${tag}-pdf.txt`);
    pdfToPng(pdf, `${OUT}/${tag}-pdf`);
  } else rec.body = (await res.text()).slice(0, 400);
  return rec;
}

// ── firm client-ledger page (648E invested line) ────────────────────
async function ledgerPage(p, who) {
  await p.goto(`${BASE}/trust-accounting/client-ledgers/${who.customerId}`, { waitUntil: "load" });
  await p.getByRole("heading", { level: 1 }).waitFor({ timeout: 30_000 });
  await p.waitForTimeout(2000);
  await p.screenshot({ path: `${OUT}/client-ledger-${who.name.toLowerCase()}-1280.png`, fullPage: true });
  const inv = p.getByTestId("client-summary-invested");
  return { heading: await p.getByRole("heading", { level: 1 }).innerText(), investedText: (await inv.count()) ? norm(await inv.innerText()) : null };
}

// ── check phase: assertions over the saved rendered text (preview innerText + PDF text) and JSON ──
function runChecks() {
  const rd = (f) => (fs.existsSync(`${OUT}/${f}`) ? fs.readFileSync(`${OUT}/${f}`, "utf8") : null);
  const n = (s) => norm(s);
  const has = (t, re) => (typeof re === "string" ? n(t).includes(re) : re.test(n(t)));
  const R = (amt) => new RegExp(`R ?${amt.replace(/ /g, "[ \\u00a0\\u202f]?")}`); // "30 095,00"
  const after = (t, label, amt) => new RegExp(`${label.replace(/[()]/g, "\\$&")}:? ?R ?${amt.replace(/ /g, " ?")}`).test(n(t).replace(/ \n/g, " "));
  const checks = [];
  const ck = (cp, name, ok, detail) => checks.push({ cp, name, ok: !!ok, ...(detail ? { detail } : {}) });
  const NO_RAW = /Inv place|Inv return|Inv receive|Inv interest|INV_|FIRM_INV|FEE_TRANSFER|INTEREST_LPFF|INTEREST_CREDIT/;
  const NO_REVERSAL = /[Rr]evers/;
  for (const [src, f] of [["preview", "cts-quickcollect-preview.txt"], ["pdf", "cts-quickcollect-pdf.txt"]]) {
    const t = rd(f); if (!t) { ck("a", `CTS QC ${src} present`, false); continue; }
    ck("a", `CTS QC ${src}: "Section 86(4) investment" block`, has(t, "Section 86(4) investment"));
    ck("a", `CTS QC ${src}: Placed in section 86(4) investment: R30 000,00`, after(t, "Placed in section 86(4) investment", "30 000,00"));
    ck("a", `CTS QC ${src}: Gross interest: R100,00`, after(t, "Gross interest", "100,00"));
    ck("a", `CTS QC ${src}: Fund share (5%): R5,00`, after(t, "Fund share of section 86(4) interest (5%)", "5,00"));
    ck("a", `CTS QC ${src}: Net interest: R95,00`, after(t, "Net interest", "95,00"));
    ck("a", `CTS QC ${src}: Investment returned to trust: R0,00`, after(t, "Investment returned to trust", "0,00"));
    ck("a", `CTS QC ${src}: Current Balance heading`, has(t, "Current Balance"));
    ck("a", `CTS QC ${src}: In the firm's trust account: R15 000,00`, after(t, "In the firm's trust account", "15 000,00"));
    ck("a", `CTS QC ${src}: Invested on your instruction (section 86(4)): R30 095,00 (block + Current Balance = 2×)`, (n(t).match(/Invested on your instruction \(section 86\(4\)\):? ?R ?30 ?095,00/g) || []).length === 2);
    ck("a", `CTS QC ${src}: Held in trust: R45 095,00`, after(t, "Held in trust", "45 095,00"));
    ck("a", `CTS QC ${src}: general closing balance R15 000,00`, after(t, "Closing balance", "15 000,00"));
    ck("b", `CTS QC ${src}: no raw/abbreviated type labels (${NO_RAW.source})`, !NO_RAW.test(t));
    ck("b", `CTS QC ${src}: "Placed in section 86(4) investment" Type label`, has(t, "Placed in section 86(4) investment"));
    ck("b", `CTS QC ${src}: "Deposit" Type label`, has(t, "Deposit"));
    ck("a", `CTS QC ${src}: no reversal reasons`, !NO_REVERSAL.test(t));
  }
  // CTS general table: only the two CLIENT-ledger rows; the INV_INTEREST (investment ledger) row is in the block only
  const ctsRes = rd("obs-result-capture.json") && JSON.parse(rd("obs-result-capture.json"));
  if (ctsRes) {
    const tbl = (ctsRes.docs["cts-quickcollect"].previewTables || []).find((x) => x.rows[0] && x.rows[0].includes("Balance"));
    ck("b", "CTS QC preview: general table Type column = [Deposit, Placed in section 86(4) investment]", tbl && JSON.stringify(tbl.rows.slice(1).map((r) => r[1])) === JSON.stringify(["Deposit", "Placed in section 86(4) investment"]), tbl && tbl.rows);
    ck("a", "CTS QC preview: no interest / Fund row in the general table", tbl && !tbl.rows.slice(1).some((r) => /interest|Fund/i.test(r.join(" "))));
    const soa = ctsRes.docs["soa-quickcollect"].previewTables || [];
    const hdrs = soa.map((x) => x.rows[0]);
    const typeTables = soa.filter((x) => x.rows[0] && x.rows[0].join("|") === "Date|Type|Reference|Description|Amount");
    ck("c", "SoA QC preview: Deposits + Payments tables both have a Type column (Date|Type|Reference|Description|Amount)", typeTables.length === 2, hdrs);
    ck("c", "SoA QC preview: Deposits row Type = Deposit", typeTables[0] && typeTables[0].rows[1][1] === "Deposit", typeTables[0] && typeTables[0].rows);
    ck("c", "SoA QC preview: Payments row Type = Placed in section 86(4) investment", typeTables[1] && typeTables[1].rows[1][1] === "Placed in section 86(4) investment", typeTables[1] && typeTables[1].rows);
    ck("c", "SoA QC preview: no interest / Fund row in Deposits/Payments", typeTables.every((x) => !x.rows.slice(1).some((r) => /interest|Fund/i.test(r.join(" ")))));
  }
  for (const [src, f] of [["preview", "soa-quickcollect-preview.txt"], ["pdf(stored)", "soa-quickcollect-pdf.txt"]]) {
    const t = rd(f); if (!t) { ck("c", `SoA QC ${src} present`, false); continue; }
    const blk = n(t).split("Section 86(4) investment").slice(1).join("Section 86(4) investment");
    ck("c", `SoA QC ${src}: "Section 86(4) investment" block`, has(t, "Section 86(4) investment"));
    ck("c", `SoA QC ${src}: block Opening balance: R0,00`, /^ ?Opening balance:? ?R ?0,00/.test(blk));
    ck("c", `SoA QC ${src}: Placed: R30 000,00`, after(blk, "Placed in section 86(4) investment", "30 000,00"));
    ck("c", `SoA QC ${src}: Gross R100,00 / Fund (5%) R5,00 / Net R95,00`, after(blk, "Gross interest", "100,00") && after(blk, "Fund share of section 86(4) interest (5%)", "5,00") && after(blk, "Net interest", "95,00"));
    ck("c", `SoA QC ${src}: Returned R0,00`, after(blk, "Investment returned to trust", "0,00"));
    ck("c", `SoA QC ${src}: closing Invested on your instruction: R30 095,00`, after(blk, "Invested on your instruction (section 86(4))", "30 095,00"));
    ck("c", `SoA QC ${src}: Trust Activity closing balance R15 000,00`, after(t, "Closing balance", "15 000,00"));
    ck("c", `SoA QC ${src}: no raw type labels`, !NO_RAW.test(t));
    ck("c", `SoA QC ${src}: no reversal reasons`, !NO_REVERSAL.test(t));
  }
  for (const [doc, f] of [["CTS preview", "cts-sipho-preview.txt"], ["CTS pdf", "cts-sipho-pdf.txt"], ["SoA preview", "soa-sipho-preview.txt"], ["SoA pdf(stored)", "soa-sipho-pdf.txt"]]) {
    const t = rd(f); if (!t) { ck("d", `Sipho ${doc} present`, false); continue; }
    ck("d", `Sipho ${doc}: no "Section 86(4) investment"`, !has(t, "Section 86(4) investment") && !/86\(4\)/.test(t));
    ck("d", `Sipho ${doc}: no "Invested on your instruction"`, !has(t, "Invested on your instruction"));
    if (doc.startsWith("CTS")) {
      ck("d", `Sipho ${doc}: keeps "The current balance held in trust on your behalf is R3 750,00."`, /The current balance held in trust on your behalf is R ?3 ?750,00\./.test(n(t)));
      ck("d", `Sipho ${doc}: no three-line split`, !has(t, "In the firm's trust account") && !has(t, "Held in trust:"));
    } else ck("d", `Sipho ${doc}: Trust balance held R3 750,00`, after(t, "Trust balance held", "3 750,00"));
    ck("d", `Sipho ${doc}: no raw type labels`, !NO_RAW.test(t));
  }
  const rq = rd("api-report-quickcollect.json") && JSON.parse(rd("api-report-quickcollect.json"));
  const rs = rd("api-report-sipho.json") && JSON.parse(rd("api-report-sipho.json"));
  const want = { openingBalance: 0, capitalPlaced: 30000, interestGross: 100, interestFundShare: 5, interestNet: 95, returnedToTrust: 0, closingBalance: 30095 };
  ck("e", "report QC: HTTP 200", rq && rq.status === 200);
  ck("e", "report QC: summary.invested keys exactly the 7 named, in order", rq && JSON.stringify(Object.keys(rq.body.summary.invested || {})) === JSON.stringify(Object.keys(want)), rq && rq.body.summary.invested);
  ck("e", "report QC: summary.invested values = 0 / 30000 / 100 / 5 / 95 / 0 / 30095", rq && Object.entries(want).every(([k, v]) => Number(rq.body.summary.invested[k]) === v));
  ck("e", "report QC: general summary unchanged (opening 0, closing 15000, 2 rows)", rq && rq.body.summary.closingBalance === 15000 && rq.body.summary.transactionCount === 2);
  ck("e", "report Sipho: no summary.invested", rs && rs.status === 200 && !("invested" in rs.body.summary), rs && rs.body.summary);
  const rr = rd("obs-result-report.json") && JSON.parse(rd("obs-result-report.json"));
  const sc = rr && rr.report.screenQc;
  ck("e", "report screen QC: stat cards unchanged (no invested card, no [object Object])", sc && !sc.showsObjectObject && !/Invested|Capital Placed|Interest Gross/.test(sc.mainText.split("DATE REFERENCE")[0]), sc && sc.mainText.split("DATE REFERENCE")[0]);
  const lg = rd("obs-result-ledger.json") && JSON.parse(rd("obs-result-ledger.json"));
  ck("g", "firm client ledger QC (648E): Plus R 30 095,00 invested — R 45 095,00 held in trust in total", lg && /Plus R ?30 ?095,00 invested \(section 86\(4\)\) — R ?45 ?095,00 held in trust in total/.test(lg.ledger.qc.investedText || ""), lg && lg.ledger.qc);
  ck("g", "firm client ledger Sipho: no invested line", lg && lg.ledger.sipho.investedText === null);
  fs.writeFileSync(`${OUT}/obs-checks.json`, JSON.stringify({ at: new Date().toISOString(), total: checks.length, failed: checks.filter((c) => !c.ok).length, checks }, null, 2));
  for (const c of checks) console.log(`${c.ok ? "PASS" : "FAIL"} [${c.cp}] ${c.name}${c.ok ? "" : "  " + JSON.stringify(c.detail || "")}`);
  console.log(`${checks.filter((c) => c.ok).length}/${checks.length} passed`);
}
if (PHASE === "check") { runChecks(); process.exit(0); }

(async () => {
  browser = await chromium.launch();
  const { ctx, p } = await firmPage();
  try {
    if (PHASE === "capture" || PHASE === "all") {
      await captureCts(ctx, p, QC);
      await captureSoa(ctx, p, QC);
      await captureCts(ctx, p, SIPHO);
      await captureSoa(ctx, p, SIPHO);
    }
    if (PHASE === "report" || PHASE === "all") {
      const token = await aliceToken();
      result.report.qc = await reportApi(p, token, QC, "quickcollect");
      result.report.sipho = await reportApi(p, token, SIPHO, "sipho");
      try { result.report.screenQc = await reportScreen(p, { ...QC, searchName: "QuickCollect" }, "quickcollect"); } catch (e) { result.report.screenQcError = String(e).slice(0, 400); await p.screenshot({ path: `${OUT}/report-screen-error-1280.png`, fullPage: true }); }
      save();
    }
    if (PHASE === "pdf") {
      const token = await aliceToken();
      result.docs["soa-quickcollect-stored"] = await storedPdf(p, token, process.argv[3], "soa-quickcollect");
      result.docs["soa-sipho-stored"] = await storedPdf(p, token, process.argv[4], "soa-sipho");
      save();
    }
    if (PHASE === "ledger" || PHASE === "all") {
      result.ledger.qc = await ledgerPage(p, QC);
      result.ledger.sipho = await ledgerPage(p, SIPHO);
      save();
    }
  } catch (e) {
    result.fatal = String(e.stack || e).slice(0, 1500);
    await p.screenshot({ path: `${OUT}/fatal-${PHASE}-1280.png`, fullPage: true }).catch(() => {});
  } finally {
    result.finishedAt = new Date().toISOString();
    save();
    await browser.close();
    console.log(JSON.stringify({ fatal: result.fatal, docs: Object.fromEntries(Object.entries(result.docs).map(([k, v]) => [k, { pdf: v.pdfFilename, pdfError: v.pdfError, textLen: (v.previewText || "").length }])), report: Object.keys(result.report), consoleErrors: result.consoleErrors.length, pageErrors: result.pageErrors.length }, null, 2));
  }
})();
