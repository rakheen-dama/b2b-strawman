// 648C OBS — real headless Chromium against the e2e mock-auth stack (:3001), after the lifecycle run.
// Every state change goes through the UI: Alice (owner) opens Settings › Fees › Trust accounting, opens Add Account,
// walks the type select (General / Section 86 / Investment), tries to submit Investment without a basis (refused
// inline, no POST), creates an Investment account with each basis and a General account after switching away from
// Investment (each with "Set as primary trust account" UNTICKED — found.md F-3). The two refusal probes of (d) are
// API evidence: the UI cannot express them; they create nothing. psql is read-only EVIDENCE. API reads use the
// browser context's own mock-auth-token against :8081.
const W = "/Users/rakheendama/Projects/2026/worktree-epic-648C";
const { chromium } = require(`${W}/frontend/node_modules/@playwright/test`);
const fs = require("fs");
const { execFileSync } = require("child_process");

const BASE = "http://localhost:3001";
const BACKEND = "http://localhost:8081";
const SLUG = "e2e-test-org";
const SETTINGS = `${BASE}/org/${SLUG}/settings/fees?tab=trust-accounting`;
const EV = `${W}/qa_cycle/evidence/phase92/648C/gates`;
const OUT = `${EV}/obs`;
const SQL_FILE = `${EV}/obs-sums.sql`;
const SQL_OUT = `${EV}/obs-sums.out`;
const result = { steps: {}, consoleErrors: [], pageErrors: [], posts: [], notes: [], startedAt: new Date().toISOString() };
const save = () => fs.writeFileSync(`${OUT}/obs-result.json`, JSON.stringify(result, null, 2));
const RUN = Date.now().toString(36).slice(-5).toUpperCase();
result.run = RUN;

// ── read-only SQL evidence (retry a transient empty read — 647D O-1) ──────────
function psqlRaw(args) {
  for (let i = 0; i < 4; i++) {
    try { return execFileSync("docker", ["exec", "e2e-postgres", "psql", "-U", "postgres", "-d", "app", "-X", ...args], { encoding: "utf8" }); }
    catch (e) { if (i === 3) throw e; execFileSync("sleep", ["1"]); }
  }
}
const psql = (q) => psqlRaw(["-v", "ON_ERROR_STOP=1", "-c", q]);
const psqlA = (q) => psqlRaw(["-At", "-F", "|", "-c", q]).trim();
const SCHEMA = psqlA("select schema_name from information_schema.schemata where schema_name like 'tenant_%' order by 1 limit 1");
fs.mkdirSync(OUT, { recursive: true });
fs.writeFileSync(SQL_FILE, `-- 648C OBS read-only evidence queries (schema ${SCHEMA}); run order = step order\n`);
fs.writeFileSync(SQL_OUT, `-- 648C OBS read-only evidence output (schema ${SCHEMA}) ${new Date().toISOString()}\n`);
const T = (q) => q.replace(/\bT\./g, `${SCHEMA}.`);
function sql(label, q) {
  const qq = T(q);
  fs.appendFileSync(SQL_FILE, `\n-- [${label}]\n${qq};\n`);
  const out = psql(qq);
  fs.appendFileSync(SQL_OUT, `\n== [${label}]\n${qq}\n${out}`);
  return out;
}
const val = (q) => psqlA(T(q));
const rowsA = (q) => (val(q) || "").split("\n").filter(Boolean).map((l) => l.split("|"));
const accountsSql = `select account_name, account_type, coalesce(investment_basis,'<null>') investment_basis, is_primary, status, created_at from T.trust_accounts order by created_at`;
const count = () => Number(val("select count(*) from T.trust_accounts"));
const acctDb = (name) => { const [r] = rowsA(`select id, account_type, coalesce(investment_basis,'<null>'), is_primary from T.trust_accounts where account_name='${name}'`); return r ? { id: r[0], accountType: r[1], investmentBasis: r[2], isPrimary: r[3] === "t" } : null; };

// ── browser helpers ─────────────────────────────────────────────────
let browser, ctx, p;
const norm = (s) => (s || "").replace(/\s+/g, " ").trim();
let shot = 0;
async function snap(name, widths = [1440, 1280]) {
  shot += 1;
  const files = [];
  for (const w of widths) {
    await p.setViewportSize({ width: w, height: 1100 });
    await p.waitForTimeout(500);
    const file = `${OUT}/${String(shot).padStart(2, "0")}-${name}-${w}.png`;
    await p.screenshot({ path: file, fullPage: true });
    files.push(file.replace(`${OUT}/`, ""));
  }
  await p.setViewportSize({ width: 1440, height: 1100 });
  await p.waitForTimeout(300);
  const text = await p.locator("body").innerText().catch(() => "");
  fs.writeFileSync(`${OUT}/${String(shot).padStart(2, "0")}-${name}.txt`, text);
  return files;
}
async function crop(locator, name) {
  const file = `${OUT}/${String(shot).padStart(2, "0")}-${name}.png`;
  await locator.screenshot({ path: file }).catch((e) => result.notes.push(`crop ${name} failed: ${String(e).slice(0, 120)}`));
  return file.replace(`${OUT}/`, "");
}
async function token() {
  const tok = (await ctx.cookies()).find((c) => c.name === "mock-auth-token");
  return tok ? tok.value : "";
}
async function apiGet(path, saveAs) {
  const res = await ctx.request.get(`${BACKEND}${path}`, { headers: { Authorization: `Bearer ${await token()}` } });
  const body = await res.json().catch(async () => (await res.text()).slice(0, 500));
  const r = { method: "GET", path, status: res.status(), body };
  if (saveAs) fs.writeFileSync(`${OUT}/api-${saveAs}.json`, JSON.stringify(r, null, 2));
  return r;
}
async function apiPost(path, data, saveAs) {
  const res = await ctx.request.post(`${BACKEND}${path}`, { headers: { Authorization: `Bearer ${await token()}`, "Content-Type": "application/json" }, data });
  const body = await res.json().catch(async () => (await res.text()).slice(0, 500));
  const r = { request: { method: "POST", url: `${BACKEND}${path}`, headers: { Authorization: "Bearer <alice mock-auth-token from the browser context>", "Content-Type": "application/json" }, body: data },
    response: { status: res.status(), contentType: res.headers()["content-type"], body } };
  if (saveAs) fs.writeFileSync(`${OUT}/api-${saveAs}.json`, JSON.stringify(r, null, 2));
  return r;
}
const section = () => p.getByTestId("trust-accounts-section");
const dialog = () => p.getByRole("dialog");
async function gotoSettings() {
  await p.goto(SETTINGS, { waitUntil: "load" });
  await section().waitFor({ timeout: 60_000 });
  await p.getByTestId("add-trust-account-button").waitFor({ timeout: 30_000 });
  await p.waitForTimeout(500);
}
// settings row (the divide-y child) whose name cell carries `name`
async function settingsRow(name) {
  const row = section().locator("div.divide-y > div").filter({ has: p.getByText(name, { exact: true }) });
  await row.first().waitFor({ timeout: 30_000 });
  const r = row.first();
  return { loc: r, text: norm(await r.innerText()),
    sectionLabel: norm(await r.getByTestId("trust-account-section").locator("p").first().innerText()),
    basisLine: (await r.getByTestId("trust-account-basis").count()) ? norm(await r.getByTestId("trust-account-basis").innerText()) : null };
}
async function openDialog() {
  await p.getByTestId("add-trust-account-button").click();
  await dialog().waitFor({ timeout: 15_000 });
  await p.waitForTimeout(400);
}
async function chooseType(label) {
  await p.getByTestId("trust-account-type-select").click();
  await p.getByRole("option", { name: label, exact: true }).click();
  await p.waitForTimeout(300);
}
async function chooseBasis(prefix) {
  await p.getByTestId("trust-account-basis-select").click();
  await p.getByRole("option").filter({ hasText: prefix }).click();
  await p.waitForTimeout(300);
}
const basisSelectCount = () => p.getByTestId("trust-account-basis-select").count();
async function fillCommon(name, n) {
  const d = dialog();
  await d.getByLabel("Account Name").fill(name);
  await d.getByLabel("Bank Name").fill("Standard Bank");
  await d.getByLabel("Branch Code").fill("051001");
  await d.getByLabel("Account Number").fill(`648C${RUN}${n}`);
}
async function untickPrimary() {
  const cb = dialog().getByRole("checkbox", { name: "Set as primary trust account" });
  if ((await cb.getAttribute("aria-checked")) === "true") await cb.click();
  return await cb.getAttribute("aria-checked");
}
// visible-text scan for raw enum tokens (case-sensitive, whole token). innerText applies CSS text-transform, so a
// hit there is re-checked against textContent (the source text): a hit only in innerText is a CSS-uppercased label.
const RAW = ["FIRM_DISCRETION", "CLIENT_INSTRUCTION", "S86_2", "S86_3", "S86_4", "S86_", "INVESTMENT", "SECTION_86", "GENERAL"];
const KNOWN = ["ACTIVE", "CLOSED"];
async function scanRaw(locator, label) {
  const { inner, content } = await locator.evaluate((el) => ({ inner: el.innerText, content: el.textContent }));
  const hit = (s, tok) => (tok.endsWith("_") ? s.includes(tok) : new RegExp(`(^|[^A-Za-z0-9_])${tok}([^A-Za-z0-9_]|$)`).test(s));
  const r = { label, rawInInnerText: RAW.filter((t) => hit(inner, t)), rawInTextContent: RAW.filter((t) => hit(content, t)),
    knownF4InInnerText: KNOWN.filter((t) => hit(inner, t)) };
  r.clean = r.rawInInnerText.length === 0 && r.rawInTextContent.length === 0;
  fs.writeFileSync(`${OUT}/scan-${label}.txt`, `innerText:\n${inner}\n\n---- textContent:\n${content}\n`);
  (result.steps.e ||= { scans: [] }).scans.push(r);
  return r;
}

(async () => {
  browser = await chromium.launch({ headless: true });
  ctx = await browser.newContext({ viewport: { width: 1440, height: 1100 }, timezoneId: "Africa/Johannesburg" });
  p = await ctx.newPage();
  p.on("console", (m) => { if (m.type() === "error") result.consoleErrors.push({ url: p.url(), text: m.text() }); });
  p.on("pageerror", (e) => result.pageErrors.push({ url: p.url(), text: String(e) }));
  p.on("request", (r) => { if (r.method() === "POST") result.posts.push({ at: new Date().toISOString(), url: r.url(), nextAction: r.headers()["next-action"] || null }); });

  await p.goto(`${BASE}/mock-login`, { waitUntil: "load" });
  await p.locator("#user-select").selectOption("user_e2e_alice");
  await p.getByRole("button", { name: "Sign In", exact: true }).click();
  await p.waitForURL(/\/org\//, { timeout: 60_000 });
  await p.getByRole("heading").first().waitFor({ timeout: 30_000 });

  // ── baseline ──
  const base = (result.steps.baseline = {});
  sql("baseline: trust accounts", accountsSql);
  base.count = count();
  base.auditCount = Number(val("select count(*) from T.audit_events where entity_type='trust_account'"));
  base.apiList = await apiGet("/api/trust-accounts", "00-baseline-list");
  await gotoSettings();
  base.files = await snap("baseline-settings");
  await crop(section(), "baseline-trust-accounts-crop");
  const primary = base.apiList.body.find((a) => a.accountName === "Mathebula & Partners Trust Account");
  base.primaryRow = await settingsRow("Mathebula & Partners Trust Account");
  base.primaryApi = primary ? { accountType: primary.accountType, investmentBasis: primary.investmentBasis, section: primary.section } : null;
  delete base.primaryRow.loc;
  await scanRaw(section(), "baseline-settings-card");
  save();

  // ── c (part 1): General / Section 86 → no basis select ──
  const c = (result.steps.c = {});
  await openDialog();
  c.defaultTypeText = norm(await p.getByTestId("trust-account-type-select").innerText());
  c.generalBasisSelectCount = await basisSelectCount();
  c.generalBasisLabelCount = await dialog().getByText("Investment basis", { exact: true }).count();
  c.filesGeneral = await snap("c1-dialog-general");
  await crop(dialog(), "c1-dialog-general-crop");
  await scanRaw(dialog(), "dialog-general");
  await chooseType("Section 86 Trust Account");
  c.s86TypeText = norm(await p.getByTestId("trust-account-type-select").innerText());
  c.s86BasisSelectCount = await basisSelectCount();
  c.s86BasisLabelCount = await dialog().getByText("Investment basis", { exact: true }).count();
  c.filesS86 = await snap("c2-dialog-section86");
  await crop(dialog(), "c2-dialog-section86-crop");
  await scanRaw(dialog(), "dialog-section86");

  // ── Investment: the basis select appears; its options; submit without a basis → inline refusal, no POST ──
  const inv = (result.steps.investmentNoBasis = {});
  await chooseType("Investment");
  inv.typeText = norm(await p.getByTestId("trust-account-type-select").innerText());
  inv.basisSelectCount = await basisSelectCount();
  inv.basisTriggerText = norm(await p.getByTestId("trust-account-basis-select").innerText());
  inv.description = norm(await dialog().locator("[data-slot='form-description'], p[id$='-form-item-description']").first().innerText().catch(() => ""));
  inv.files = await snap("b0-dialog-investment");
  await crop(dialog(), "b0-dialog-investment-crop");
  await scanRaw(dialog(), "dialog-investment");
  await p.getByTestId("trust-account-basis-select").click();
  await p.getByRole("listbox").waitFor({ timeout: 10_000 });
  inv.options = (await p.getByRole("option").allInnerTexts()).map(norm);
  // no snap() here: a viewport resize closes the Radix listbox (run 1 harness miss)
  await scanRaw(p.getByRole("listbox"), "basis-listbox");
  shot += 1;
  await crop(p.getByRole("listbox"), "b0-basis-listbox-crop");
  await p.screenshot({ path: `${OUT}/${String(shot).padStart(2, "0")}-b0-dialog-investment-basis-open-1440.png` });
  inv.filesOpen = [`${String(shot).padStart(2, "0")}-b0-dialog-investment-basis-open-1440.png`];
  await p.keyboard.press("Escape");
  await p.waitForTimeout(300);
  inv.dialogStillOpen = await dialog().isVisible();
  await fillCommon(`OBS no-basis ${RUN}`, 0);
  inv.primaryAfterUntick = await untickPrimary();
  const postsBefore = result.posts.length;
  const countBefore = count();
  await dialog().getByRole("button", { name: "Create Account" }).click();
  await p.waitForTimeout(2000);
  inv.inlineMessage = await dialog().getByText("Choose the investment basis", { exact: true }).isVisible().catch(() => false);
  inv.postsDuringSubmit = result.posts.slice(postsBefore);
  inv.countBefore = countBefore;
  inv.countAfter = count();
  inv.noRowNamed = val(`select count(*) from T.trust_accounts where account_name='OBS no-basis ${RUN}'`);
  inv.filesRefused = await snap("b1-dialog-investment-no-basis-refused");
  await crop(dialog(), "b1-dialog-no-basis-crop");
  inv.pass = inv.basisSelectCount === 1 && inv.inlineMessage && inv.postsDuringSubmit.length === 0 && inv.countAfter === inv.countBefore && inv.noRowNamed === "0";
  save();

  // ── a: Investment + section 86(3), primary unticked ──
  async function createInvestment(key, name, basisPrefix, n, expBasis, expSection, expLabel, expBasisLine) {
    const s = (result.steps[key] = { name });
    if (!(await dialog().isVisible().catch(() => false))) { await openDialog(); await chooseType("Investment"); }
    await fillCommon(name, n);
    s.primaryAfterUntick = await untickPrimary();
    await chooseBasis(basisPrefix);
    s.basisTriggerText = norm(await p.getByTestId("trust-account-basis-select").innerText());
    s.filesDialog = await snap(`${key}1-dialog-${expSection}`);
    await crop(dialog(), `${key}1-dialog-crop`);
    await scanRaw(dialog(), `dialog-${key}-filled`);
    const before = count();
    const postsBefore = result.posts.length;
    await dialog().getByRole("button", { name: "Create Account" }).click();
    await dialog().waitFor({ state: "hidden", timeout: 30_000 });
    s.postsDuringSubmit = result.posts.slice(postsBefore);
    s.countBefore = before;
    s.countAfter = count();
    await gotoSettings();
    const row = await settingsRow(name);
    s.ui = { text: row.text, sectionLabel: row.sectionLabel, basisLine: row.basisLine };
    s.filesSettings = await snap(`${key}2-settings-${expSection}`);
    await crop(row.loc, `${key}2-settings-row-crop`);
    s.db = acctDb(name);
    sql(`${key}: trust accounts after create`, accountsSql);
    const list = await apiGet("/api/trust-accounts", `${key}-list`);
    const a = list.body.find((x) => x.accountName === name);
    const one = await apiGet(`/api/trust-accounts/${s.db.id}`, `${key}-get-by-id`);
    s.api = { list: a ? { accountType: a.accountType, investmentBasis: a.investmentBasis, section: a.section, isPrimary: a.isPrimary } : null,
      byId: { status: one.status, accountType: one.body.accountType, investmentBasis: one.body.investmentBasis, section: one.body.section } };
    s.pass = s.countAfter === s.countBefore + 1 && s.primaryAfterUntick === "false"
      && s.db && s.db.accountType === "INVESTMENT" && s.db.investmentBasis === expBasis && s.db.isPrimary === false
      && s.api.list && s.api.list.investmentBasis === expBasis && s.api.list.section === expSection
      && s.api.byId.investmentBasis === expBasis && s.api.byId.section === expSection
      && s.ui.sectionLabel === expLabel && s.ui.basisLine === expBasisLine;
    save();
    return s;
  }
  await createInvestment("a", `OBS s86(3) Investment ${RUN}`, "Section 86(3)", 1, "FIRM_DISCRETION", "S86_3", "Section 86(3) investment account", "Invested at the firm's discretion");
  await createInvestment("b", `OBS s86(4) Investment ${RUN}`, "Section 86(4)", 2, "CLIENT_INSTRUCTION", "S86_4", "Section 86(4) investment account", "Invested on a client's instruction");

  // ── c (part 2): Investment → pick 86(4) → General: the select is gone; the General account has no basis ──
  await openDialog();
  const name = `OBS General ${RUN}`;
  await fillCommon(name, 3);
  c.primaryAfterUntick = await untickPrimary();
  await chooseType("Investment");
  await chooseBasis("Section 86(4)");
  c.beforeSwitchBasisSelectCount = await basisSelectCount();
  c.beforeSwitchBasisText = norm(await p.getByTestId("trust-account-basis-select").innerText());
  c.filesBeforeSwitch = await snap("c3-dialog-investment-86-4-before-switch");
  await chooseType("General");
  c.afterSwitchBasisSelectCount = await basisSelectCount();
  c.afterSwitchBasisLabelCount = await dialog().getByText("Investment basis", { exact: true }).count();
  c.afterSwitchTypeText = norm(await p.getByTestId("trust-account-type-select").innerText());
  c.filesAfterSwitch = await snap("c4-dialog-switched-to-general");
  await crop(dialog(), "c4-dialog-switched-crop");
  await scanRaw(dialog(), "dialog-switched-to-general");
  const before = count();
  const postsBefore2 = result.posts.length;
  await dialog().getByRole("button", { name: "Create Account" }).click();
  await dialog().waitFor({ state: "hidden", timeout: 30_000 });
  c.postsDuringSubmit = result.posts.slice(postsBefore2);
  c.countBefore = before;
  c.countAfter = count();
  await gotoSettings();
  const grow = await settingsRow(name);
  c.ui = { text: grow.text, sectionLabel: grow.sectionLabel, basisLine: grow.basisLine };
  c.filesSettings = await snap("c5-settings-general");
  await crop(grow.loc, "c5-settings-general-row-crop");
  c.db = acctDb(name);
  sql("c: trust accounts after the General create", accountsSql);
  const glist = await apiGet("/api/trust-accounts", "c-list");
  const ga = glist.body.find((x) => x.accountName === name);
  c.api = ga ? { accountType: ga.accountType, investmentBasis: ga.investmentBasis, section: ga.section, isPrimary: ga.isPrimary } : null;
  c.pass = c.generalBasisSelectCount === 0 && c.s86BasisSelectCount === 0 && c.beforeSwitchBasisSelectCount === 1 && c.afterSwitchBasisSelectCount === 0
    && c.countAfter === c.countBefore + 1 && c.primaryAfterUntick === "false"
    && c.db && c.db.accountType === "GENERAL" && c.db.investmentBasis === "<null>" && c.db.isPrimary === false
    && c.api && c.api.investmentBasis === null && c.api.section === "S86_2"
    && c.ui.sectionLabel === "Section 86(2) trust banking account" && c.ui.basisLine === null;
  save();

  // ── d: API refusal probes (API evidence — the UI cannot express them) ──
  const d = (result.steps.d = {});
  d.countBefore = count();
  const common = { bankName: "Standard Bank", branchCode: "051001", isPrimary: false, requireDualApproval: false, openedDate: new Intl.DateTimeFormat("en-CA", { timeZone: "Africa/Johannesburg" }).format(new Date()) };
  d.generalWithBasis = await apiPost("/api/trust-accounts", { ...common, accountName: `OBS refused GENERAL+basis ${RUN}`, accountNumber: `648C${RUN}R1`, accountType: "GENERAL", investmentBasis: "FIRM_DISCRETION" }, "d1-general-with-basis");
  d.investmentNoBasis = await apiPost("/api/trust-accounts", { ...common, accountName: `OBS refused INVESTMENT-no-basis ${RUN}`, accountNumber: `648C${RUN}R2`, accountType: "INVESTMENT" }, "d2-investment-without-basis");
  d.countAfter = count();
  d.rowsNamedRefused = val(`select count(*) from T.trust_accounts where account_name like 'OBS refused %${RUN}'`);
  sql("d: trust accounts after the refusal probes", accountsSql);
  d.pass = d.generalWithBasis.response.status === 400 && d.generalWithBasis.response.body.title === "Investment basis not allowed"
    && d.investmentNoBasis.response.status === 400 && d.investmentNoBasis.response.body.title === "Investment basis required"
    && d.countAfter === d.countBefore && d.rowsNamedRefused === "0";
  save();

  // ── final: list, audit, settings scan ──
  const fin = (result.steps.final = {});
  fin.apiList = (await apiGet("/api/trust-accounts", "z-final-list")).body.map((x) => ({ accountName: x.accountName, accountType: x.accountType, investmentBasis: x.investmentBasis, section: x.section, isPrimary: x.isPrimary }));
  fin.everyHasKeys = (await apiGet("/api/trust-accounts")).body.every((x) => "investmentBasis" in x && "section" in x);
  sql("final: audit trust_account events", `select event_type, entity_id, details, occurred_at from T.audit_events where entity_type='trust_account' order by occurred_at desc limit 6`);
  fin.audit = rowsA(`select event_type, details::text from T.audit_events where entity_type='trust_account' and occurred_at > now() - interval '1 hour' order by occurred_at`).map((r) => ({ eventType: r[0], details: JSON.parse(r[1]) }));
  fin.auditCountDelta = Number(val("select count(*) from T.audit_events where entity_type='trust_account'")) - base.auditCount;
  await gotoSettings();
  fin.files = await snap("z-final-settings", [1440, 1280]);
  await crop(section(), "z-final-trust-accounts-crop");
  await scanRaw(section(), "final-settings-card");
  await scanRaw(p.locator("main").first(), "final-settings-main");
  fin.rows = [];
  for (const nm of ["Mathebula & Partners Trust Account", result.steps.a.name, result.steps.b.name, name]) { const r = await settingsRow(nm); delete r.loc; fin.rows.push({ name: nm, ...r }); }
  result.steps.e.pass = result.steps.e.scans.every((s) => s.clean);
  result.finishedAt = new Date().toISOString();
  result.summary = { a: result.steps.a.pass, b: result.steps.b.pass, c: c.pass, d: d.pass, e: result.steps.e.pass, investmentNoBasis: inv.pass,
    consoleErrors: result.consoleErrors.length, pageErrors: result.pageErrors.length };
  save();
  console.log(JSON.stringify(result.summary, null, 2));
  await browser.close();
})().catch(async (e) => { result.error = String(e.stack || e); save(); console.error("OBS FAILED", e); try { await p.screenshot({ path: `${OUT}/zz-failure.png`, fullPage: true }); } catch {} process.exit(1); });
