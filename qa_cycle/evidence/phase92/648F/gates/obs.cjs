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
//                                     | check <soaQcGeneratedId> <soaSiphoGeneratedId>   (offline: saved evidence + ghostscript only)
// Exit status: nonzero when a check fails, or a browser phase records a fatal error / a failed stored-PDF download.
// (Hardened in the 648F CodeRabbit round 2: paths from __dirname, lazy Playwright, PDF artefacts bound to
// their run's record, null-safe report checks, positive report-screen card check, nonzero exit.)
const fs = require("fs");
const path = require("path");
const crypto = require("crypto");
const os = require("os");
const { execFileSync } = require("child_process");
// this file lives in <repo>/qa_cycle/evidence/phase92/648F/gates
const W = path.resolve(__dirname, "../../../../..");
/** Playwright is loaded only by the browser phases, so `check` runs without the frontend's node_modules. */
const loadChromium = () => require(require.resolve("@playwright/test", { paths: [path.join(W, "frontend")] })).chromium;

const FIRM = "http://localhost:3001";
const BACKEND = "http://localhost:8081";
const IDP = "http://localhost:8090";
const SLUG = "e2e-test-org";
const BASE = `${FIRM}/org/${SLUG}`;
const EV = __dirname;
const OUT = path.join(EV, "obs");
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
const sha256File = (f) => crypto.createHash("sha256").update(fs.readFileSync(f)).digest("hex");
/** Removes a document's PDF artefacts before a download, so a failed download cannot leave an earlier run's files behind. */
function clearPdfArtefacts(tag) {
  const png = new RegExp(`^${tag}-pdf-p\\d+\\.png$`);
  for (const f of fs.readdirSync(OUT)) {
    if (f === `${tag}.pdf` || f === `${tag}-pdf.txt` || png.test(f)) fs.rmSync(path.join(OUT, f));
  }
}

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
    clearPdfArtefacts(tag);
    const btn = dialog.getByRole("button", { name: /^(Download PDF|Download anyway)$/ });
    await btn.waitFor({ timeout: 15_000 });
    const dl = p.waitForEvent("download", { timeout: 60_000 });
    await btn.click();
    const d = await dl;
    rec.pdfFilename = d.suggestedFilename();
    const pdf = `${OUT}/${tag}.pdf`;
    await d.saveAs(pdf);
    rec.pdfSha256 = sha256File(pdf); rec.runStartedAt = result.startedAt;
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
    clearPdfArtefacts(tag);
    const dl = p.waitForEvent("download", { timeout: 45_000 });
    await dialog.getByTestId("statement-download-btn").click();
    const d = await dl;
    rec.pdfFilename = d.suggestedFilename();
    const pdf = `${OUT}/${tag}.pdf`;
    await d.saveAs(pdf);
    rec.pdfSha256 = sha256File(pdf); rec.runStartedAt = result.startedAt;
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
  // wait for the rendered results (stat cards + table), not a fixed delay: a still-loading screen has no
  // invested card either, so the absence checks only mean something once the report has rendered
  await p.locator("main").getByText("Transaction Count", { exact: true }).waitFor({ timeout: 30_000 });
  await p.locator("main table").first().waitFor({ timeout: 30_000 });
  rec.statCards = await p.locator("main div.grid").first().locator(":scope > *").evaluateAll((cards) => cards.map((c) => (c.querySelector("p") || c).innerText.trim()));
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
  clearPdfArtefacts(tag);
  const res = await p.request.get(`${BACKEND}/api/generated-documents/${generatedId}/download`, { headers: { Authorization: `Bearer ${token}` }, maxRedirects: 5 });
  const rec = { generatedId, status: res.status(), contentType: res.headers()["content-type"], runStartedAt: result.startedAt };
  if (res.ok()) {
    const pdf = `${OUT}/${tag}.pdf`;
    fs.writeFileSync(pdf, await res.body());
    rec.pdfSha256 = sha256File(pdf);
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
// Offline: reads only the saved evidence (+ ghostscript to re-extract PDF text). Every artefact must be
// bound to the record of the run that produced it; a missing record or a failed download is a FAILED
// check, never a skipped one. Returns the number of failed checks.
function runChecks(expectSoaQcId, expectSoaSiphoId) {
  const rd = (f) => (fs.existsSync(path.join(OUT, f)) ? fs.readFileSync(path.join(OUT, f), "utf8") : null);
  const rdJson = (f) => { const t = rd(f); if (t == null) return null; try { return JSON.parse(t); } catch { return null; } };
  const n = (s) => norm(s);
  const has = (t, re) => (typeof re === "string" ? n(t).includes(re) : re.test(n(t)));
  const after = (t, label, amt) => new RegExp(`${label.replace(/[()]/g, "\\$&")}:? ?R ?${amt.replace(/ /g, " ?")}`).test(n(t).replace(/ \n/g, " "));
  const checks = [];
  const ck = (cp, name, ok, detail) => checks.push({ cp, name, ok: !!ok, ...(detail !== undefined ? { detail } : {}) });
  const NO_RAW = /Inv place|Inv return|Inv receive|Inv interest|INV_|FIRM_INV|FEE_TRANSFER|INTEREST_LPFF|INTEREST_CREDIT/;
  const NO_REVERSAL = /[Rr]evers/;

  const capture = rdJson("obs-result-capture.json");
  const stored = rdJson("obs-result-pdf.json");
  ck("a", "capture record obs-result-capture.json present, no fatal error", capture && !capture.fatal, capture ? capture.fatal : "missing");
  ck("c", "stored-PDF record obs-result-pdf.json present, no fatal error", stored && !stored.fatal, stored ? stored.fatal : "missing");
  const doc = (rec, key) => (rec && rec.docs && rec.docs[key]) || null;

  /** Preview text counts only when the capture record has it and the saved -preview.txt is that text. */
  function boundPreview(cp, label, rec, file) {
    const t = rd(file);
    const problems = [];
    if (!rec) problems.push("no capture record for this document");
    else if (typeof rec.previewText !== "string" || !rec.previewText) problems.push("capture record has no preview text");
    if (t == null) problems.push(`${file} missing`);
    if (!problems.length && t !== rec.previewText) problems.push(`${file} ≠ the capture record's preview text`);
    ck(cp, `${label}: preview text bound to the capture record`, !problems.length, problems.length ? problems : undefined);
    return problems.length ? null : t;
  }

  /**
   * A PDF artefact counts only when the record of the run that fetched it shows a successful download of the
   * expected document (no download error; for a stored document HTTP 200, application/pdf and the expected
   * generated-document id) and the files on disk are that download: the sha256 the run recorded (runs after
   * round 2), the saved -pdf.txt, and a fresh ghostscript re-extraction of the .pdf all equal the record.
   * Returns the PDF text, or null (binding failed → the text checks below record failures).
   */
  function boundPdf(cp, label, rec, tag, identity) {
    const pdf = path.join(OUT, `${tag}.pdf`);
    const txtFile = path.join(OUT, `${tag}-pdf.txt`);
    const problems = [];
    if (!rec) problems.push("no run record for this PDF");
    else {
      if (rec.pdfError) problems.push(`download failed in that run: ${String(rec.pdfError).slice(0, 160)}`);
      if ("status" in rec && rec.status !== 200) problems.push(`HTTP ${rec.status}`);
      if ("contentType" in rec && !/^application\/pdf\b/.test(rec.contentType || "")) problems.push(`content-type ${rec.contentType}`);
      if ("generatedId" in identity) {
        if (!identity.generatedId) problems.push("expected generated-document id not given (node obs.cjs check <soaQcId> <soaSiphoId>)");
        else if (rec.generatedId !== identity.generatedId) problems.push(`record is generated document ${rec.generatedId}, expected ${identity.generatedId}`);
      }
      if (typeof rec.pdfText !== "string" || !rec.pdfText) problems.push("record has no PDF text");
    }
    if (!fs.existsSync(pdf)) problems.push(`${tag}.pdf missing`);
    if (!fs.existsSync(txtFile)) problems.push(`${tag}-pdf.txt missing`);
    if (!problems.length) {
      if (rec.pdfSha256 && sha256File(pdf) !== rec.pdfSha256) problems.push("PDF sha256 ≠ the run's record");
      if (fs.readFileSync(txtFile, "utf8") !== rec.pdfText) problems.push(`${tag}-pdf.txt ≠ the run's record`);
      const tmp = path.join(os.tmpdir(), `obs-648F-${process.pid}-${tag}.txt`);
      try {
        if (pdfToText(pdf, tmp) !== rec.pdfText) problems.push("ghostscript re-extraction of the PDF ≠ the run's record");
      } catch (e) {
        problems.push(`ghostscript re-extraction failed: ${String(e).slice(0, 160)}`);
      } finally {
        fs.rmSync(tmp, { force: true });
      }
      if (identity.text && !n(rec.pdfText).includes(identity.text)) problems.push(`PDF is not the expected document (no "${identity.text}")`);
    }
    ck(cp, `${label}: PDF bound to that run's successful download of the expected document`, !problems.length, problems.length ? problems : undefined);
    return problems.length ? null : rec.pdfText;
  }

  /**
   * The CTS general table's Type column read from the PDF text layout (ghostscript txtwrite keeps the
   * columns): the characters between the header's "Type" and "Reference" offsets, on every line from the
   * header to "Closing balance", joined. Wrapped cells ("Placed in / section 86 / (4) / investment") join back.
   */
  function pdfTypeColumn(t) {
    const lines = t.split(/\r?\n/);
    const h = lines.findIndex((l) => /\bDate\s+Type\s+Reference\s+Description\s+Amount\s+Balance\b/.test(l));
    if (h < 0) return null;
    const from = lines[h].indexOf("Type");
    const to = lines[h].indexOf("Reference");
    const cells = [];
    for (let i = h + 1; i < lines.length && !/^\s*Closing balance/.test(lines[i]); i++) cells.push(lines[i].slice(from, to).trim());
    return cells.filter(Boolean).join(" ");
  }
  const squash = (s) => (s || "").replace(/\s+/g, "");

  const ctsQc = doc(capture, "cts-quickcollect");
  for (const [src, t] of [
    ["preview", boundPreview("a", "CTS QC preview", ctsQc, "cts-quickcollect-preview.txt")],
    ["pdf", boundPdf("a", "CTS QC pdf", ctsQc, "cts-quickcollect", { text: ctsQc && ctsQc.clientHeading ? `Client: ${ctsQc.clientHeading}` : "Client: QuickCollect" })],
  ]) {
    if (!t) { ck("a", `CTS QC ${src}: evidence usable`, false, "see the binding check above"); continue; }
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
    // text presence only: the phrase is also the placement's description and the block's label, so this
    // does not prove the Type cell — the Type cell is checked structurally (preview table / PDF column) below
    ck("b", `CTS QC ${src}: "Placed in section 86(4) investment" text present (whole document — text presence, not the Type cell)`, has(t, "Placed in section 86(4) investment"));
    ck("b", `CTS QC ${src}: "Deposit" text present (whole document — text presence, not the Type cell)`, has(t, "Deposit"));
    ck("a", `CTS QC ${src}: no reversal reasons`, !NO_REVERSAL.test(t));
    if (src === "pdf") {
      const col = pdfTypeColumn(t);
      ck("b", "CTS QC pdf: general table Type column (PDF text layout) = [Deposit, Placed in section 86(4) investment]", col != null && squash(col) === squash("Deposit Placed in section 86(4) investment"), col);
    }
  }
  // CTS general table: only the two CLIENT-ledger rows; the INV_INTEREST (investment ledger) row is in the block only
  const ctsTables = ctsQc && ctsQc.previewTables;
  ck("b", "CTS QC preview: tables present in the capture record", Array.isArray(ctsTables) && ctsTables.length > 0);
  const tbl = (ctsTables || []).find((x) => x.rows && x.rows[0] && x.rows[0].includes("Balance"));
  ck("b", "CTS QC preview: general table Type column = [Deposit, Placed in section 86(4) investment]", tbl && JSON.stringify(tbl.rows.slice(1).map((r) => r[1])) === JSON.stringify(["Deposit", "Placed in section 86(4) investment"]), tbl ? tbl.rows : "no general table");
  ck("a", "CTS QC preview: no interest / Fund row in the general table", tbl && !tbl.rows.slice(1).some((r) => /interest|Fund/i.test(r.join(" "))));
  const soaQc = doc(capture, "soa-quickcollect");
  const soaTables = soaQc && soaQc.previewTables;
  ck("c", "SoA QC preview: tables present in the capture record", Array.isArray(soaTables) && soaTables.length > 0);
  const hdrs = (soaTables || []).map((x) => x.rows && x.rows[0]);
  const typeTables = (soaTables || []).filter((x) => x.rows && x.rows[0] && x.rows[0].join("|") === "Date|Type|Reference|Description|Amount");
  ck("c", "SoA QC preview: Deposits + Payments tables both have a Type column (Date|Type|Reference|Description|Amount)", typeTables.length === 2, hdrs);
  ck("c", "SoA QC preview: Deposits row Type = Deposit", typeTables[0] && typeTables[0].rows[1] && typeTables[0].rows[1][1] === "Deposit", typeTables[0] && typeTables[0].rows);
  ck("c", "SoA QC preview: Payments row Type = Placed in section 86(4) investment", typeTables[1] && typeTables[1].rows[1] && typeTables[1].rows[1][1] === "Placed in section 86(4) investment", typeTables[1] && typeTables[1].rows);
  ck("c", "SoA QC preview: no interest / Fund row in Deposits/Payments", typeTables.length === 2 && typeTables.every((x) => !x.rows.slice(1).some((r) => /interest|Fund/i.test(r.join(" ")))));

  // SoA PDFs: the dialog's Download PDF fails on the e2e stack (see README) — that capture-record error is
  // expected and recovered: the stored statement is fetched by the pdf phase and bound here by generated id.
  for (const [src, t] of [
    ["preview", boundPreview("c", "SoA QC preview", soaQc, "soa-quickcollect-preview.txt")],
    ["pdf(stored)", boundPdf("c", "SoA QC pdf(stored)", doc(stored, "soa-quickcollect-stored"), "soa-quickcollect", { generatedId: expectSoaQcId, text: `Reference: SOA-${QC.matterId.slice(0, 8)}-` })],
  ]) {
    if (!t) { ck("c", `SoA QC ${src}: evidence usable`, false, "see the binding check above"); continue; }
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
  const ctsSipho = doc(capture, "cts-sipho");
  const soaSipho = doc(capture, "soa-sipho");
  for (const [d, t] of [
    ["CTS preview", boundPreview("d", "Sipho CTS preview", ctsSipho, "cts-sipho-preview.txt")],
    ["CTS pdf", boundPdf("d", "Sipho CTS pdf", ctsSipho, "cts-sipho", { text: ctsSipho && ctsSipho.clientHeading ? `Client: ${ctsSipho.clientHeading}` : "Client: Sipho" })],
    ["SoA preview", boundPreview("d", "Sipho SoA preview", soaSipho, "soa-sipho-preview.txt")],
    ["SoA pdf(stored)", boundPdf("d", "Sipho SoA pdf(stored)", doc(stored, "soa-sipho-stored"), "soa-sipho", { generatedId: expectSoaSiphoId, text: `Reference: SOA-${SIPHO.matterId.slice(0, 8)}-` })],
  ]) {
    if (!t) { ck("d", `Sipho ${d}: evidence usable`, false, "see the binding check above"); continue; }
    ck("d", `Sipho ${d}: no "Section 86(4) investment"`, !has(t, "Section 86(4) investment") && !/86\(4\)/.test(t));
    ck("d", `Sipho ${d}: no "Invested on your instruction"`, !has(t, "Invested on your instruction"));
    if (d.startsWith("CTS")) {
      ck("d", `Sipho ${d}: keeps "The current balance held in trust on your behalf is R3 750,00."`, /The current balance held in trust on your behalf is R ?3 ?750,00\./.test(n(t)));
      ck("d", `Sipho ${d}: no three-line split`, !has(t, "In the firm's trust account") && !has(t, "Held in trust:"));
    } else ck("d", `Sipho ${d}: Trust balance held R3 750,00`, after(t, "Trust balance held", "3 750,00"));
    ck("d", `Sipho ${d}: no raw type labels`, !NO_RAW.test(t));
  }

  // report API JSON: null-safe; JSON values compared as-is (null is not 0)
  const rq = rdJson("api-report-quickcollect.json");
  const rs = rdJson("api-report-sipho.json");
  const want = { openingBalance: 0, capitalPlaced: 30000, interestGross: 100, interestFundShare: 5, interestNet: 95, returnedToTrust: 0, closingBalance: 30095 };
  const sum = rq && rq.status === 200 && rq.body && typeof rq.body === "object" ? rq.body.summary : undefined;
  const inv = sum && sum.invested;
  const isObj = (x) => x != null && typeof x === "object" && !Array.isArray(x);
  ck("e", "report QC: HTTP 200", rq && rq.status === 200, rq ? rq.status : "api-report-quickcollect.json missing");
  ck("e", "report QC: summary.invested present (an object)", isObj(inv), sum);
  ck("e", "report QC: summary.invested keys exactly the 7 named, in order", isObj(inv) && JSON.stringify(Object.keys(inv)) === JSON.stringify(Object.keys(want)), inv);
  ck("e", "report QC: summary.invested values = 0 / 30000 / 100 / 5 / 95 / 0 / 30095", isObj(inv) && Object.entries(want).every(([k, v]) => inv[k] === v), inv);
  ck("e", "report QC: general summary unchanged (opening 0, closing 15000, debits 30000, credits 45000, 2 rows)", isObj(sum) && sum.openingBalance === 0 && sum.closingBalance === 15000 && sum.totalDebits === 30000 && sum.totalCredits === 45000 && sum.transactionCount === 2, sum);
  const ssum = rs && rs.status === 200 && rs.body && typeof rs.body === "object" ? rs.body.summary : undefined;
  ck("e", "report Sipho: HTTP 200 with a summary and no summary.invested", isObj(ssum) && !("invested" in ssum), rs ? (ssum || rs.status) : "api-report-sipho.json missing");

  // report screen: first prove it rendered (exactly the five stat cards with the report's values, then the
  // table header), only then that nothing else (an invested card, "[object Object]") is on it
  const rr = rdJson("obs-result-report.json");
  const sc = rr && rr.report && rr.report.screenQc;
  const screenErr = rr && rr.report && rr.report.screenQcError;
  ck("e", "report screen QC: captured without a screen error", sc && typeof sc.mainText === "string" && !screenErr, screenErr || (rr ? "no screenQc" : "obs-result-report.json missing"));
  const mt = sc && typeof sc.mainText === "string" ? sc.mainText : "";
  const cardsText = mt.includes("Export PDF") && mt.includes("DATE REFERENCE") ? mt.split("DATE REFERENCE")[0].split("Export PDF").slice(1).join("Export PDF").trim() : null;
  const CARDS = ["Opening Balance", "Closing Balance", "Total Debits", "Total Credits", "Transaction Count"];
  const CARDS_RE = /^Opening Balance R 0,00 Closing Balance R 15 000,00 Total Debits R 30 000,00 Total Credits R 45 000,00 Transaction Count 2$/;
  ck("e", "report screen QC: rendered — stat cards exactly Opening / Closing / Debits / Credits / Count (R 0,00 / R 15 000,00 / R 30 000,00 / R 45 000,00 / 2), table header after", cardsText != null && CARDS_RE.test(cardsText) && (!sc.statCards || JSON.stringify(sc.statCards) === JSON.stringify(CARDS)), { cardsText, statCards: sc && sc.statCards });
  ck("e", "report screen QC: no invested card, no [object Object]", cardsText != null && !sc.showsObjectObject && !/\[object Object\]/.test(mt) && !/Invested|Capital Placed|Interest Gross/.test(cardsText), cardsText);

  const lg = rdJson("obs-result-ledger.json");
  const lq = lg && lg.ledger && lg.ledger.qc;
  const ls = lg && lg.ledger && lg.ledger.sipho;
  ck("g", "ledger record obs-result-ledger.json present, no fatal error", lg && !lg.fatal, lg ? lg.fatal : "missing");
  ck("g", "firm client ledger QC (648E): Plus R 30 095,00 invested — R 45 095,00 held in trust in total", lq && /Plus R ?30 ?095,00 invested \(section 86\(4\)\) — R ?45 ?095,00 held in trust in total/.test(lq.investedText || ""), lq);
  ck("g", "firm client ledger Sipho: no invested line", ls && ls.investedText === null, ls);

  const failed = checks.filter((c) => !c.ok).length;
  fs.writeFileSync(path.join(OUT, "obs-checks.json"), JSON.stringify({ at: new Date().toISOString(), args: { expectSoaQcId: expectSoaQcId || null, expectSoaSiphoId: expectSoaSiphoId || null }, total: checks.length, failed, checks }, null, 2));
  for (const c of checks) console.log(`${c.ok ? "PASS" : "FAIL"} [${c.cp}] ${c.name}${c.ok ? "" : "  " + JSON.stringify(c.detail === undefined ? "" : c.detail)}`);
  console.log(`${checks.length - failed}/${checks.length} passed`);
  return failed;
}

if (PHASE === "check") {
  let failed;
  try {
    failed = runChecks(process.argv[3], process.argv[4]);
  } catch (e) {
    console.error(`check phase crashed: ${e.stack || e}`);
    failed = 1;
  }
  process.exitCode = failed ? 1 : 0;
} else {
  (async () => {
    let p;
    try {
      browser = await loadChromium().launch();
      const fp = await firmPage();
      const ctx = fp.ctx;
      p = fp.p;
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
        if (!process.argv[3] || !process.argv[4]) throw new Error("usage: node obs.cjs pdf <soaQcGeneratedId> <soaSiphoGeneratedId>");
        const token = await aliceToken();
        result.docs["soa-quickcollect-stored"] = await storedPdf(p, token, process.argv[3], "soa-quickcollect");
        result.docs["soa-sipho-stored"] = await storedPdf(p, token, process.argv[4], "soa-sipho");
        const bad = Object.entries(result.docs).filter(([, v]) => v.status !== 200).map(([k, v]) => `${k}: HTTP ${v.status}`);
        if (bad.length) { result.notes.push(`stored-PDF download failed: ${bad.join(", ")}`); process.exitCode = 1; }
        save();
      }
      if (PHASE === "ledger" || PHASE === "all") {
        result.ledger.qc = await ledgerPage(p, QC);
        result.ledger.sipho = await ledgerPage(p, SIPHO);
        save();
      }
    } catch (e) {
      result.fatal = String(e.stack || e).slice(0, 1500);
      if (p) await p.screenshot({ path: `${OUT}/fatal-${PHASE}-1280.png`, fullPage: true }).catch(() => {});
    } finally {
      result.finishedAt = new Date().toISOString();
      save(); // diagnostics first, then the exit status
      if (result.fatal) process.exitCode = 1;
      if (browser) await browser.close().catch(() => {});
      console.log(JSON.stringify({ fatal: result.fatal, docs: Object.fromEntries(Object.entries(result.docs).map(([k, v]) => [k, { pdf: v.pdfFilename, pdfError: v.pdfError, status: v.status, textLen: (v.previewText || "").length }])), report: Object.keys(result.report), consoleErrors: result.consoleErrors.length, pageErrors: result.pageErrors.length }, null, 2));
    }
  })();
}
