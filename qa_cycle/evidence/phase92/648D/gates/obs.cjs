// 648D OBS — portal invested balance, ledger split, typeLabel from the backend.
// Stack: FRESH e2e mock-auth stack built from 7a029760e (backend :8081, mock IDP :8090, Postgres :5433);
// the branch portal runs as `next dev` on :3002 with NEXT_PUBLIC_PORTAL_API_URL=http://localhost:8081.
// Firm-side writes go through the firm REST API with mock-IDP bearer tokens (Alice owner, Bob admin approver) —
// never SQL. psql is READ-ONLY evidence. Portal login = one request-link + exchange (portal/e2e/helpers/auth.ts),
// the JWT seeded into localStorage (portal_jwt / portal_customer / portal_last_org_id).
// Steps (each idempotent on re-run, keyed by run-stable references):
//   a. an INVESTMENT trust account, basis CLIENT_INSTRUCTION (s86(4)), if none exists;
//   b. an Acme deposit of R20 000,00 into the GENERAL account on Website Redesign (funds the placement);
//   P0 portal observation BEFORE the placement (API summary + screenshot);
//   c. an s86(4) placement of R10 000,00 for Acme / Website Redesign from the general account, approved by Bob;
//   P1 portal observation AFTER the placement (total must be unchanged);
//   d. a NET bank-advice interest entry: gross R100,00 → Fund 5% R5,00, client net R95,00;
//   P2 final portal observation (checks a, b, d, e, g) + UI = API = SQL + firm client ledger (648E).
const W = "/Users/rakheendama/Projects/2026/worktree-epic-648D";
const { chromium } = require(`${W}/portal/node_modules/@playwright/test`);
const fs = require("fs");
const { execFileSync } = require("child_process");

const BACKEND = "http://localhost:8081";
const IDP = "http://localhost:8090";
const PORTAL = "http://localhost:3002";
const FIRM = "http://localhost:3001";
const SLUG = "e2e-test-org";
const EV = `${W}/qa_cycle/evidence/phase92/648D/gates`;
const OUT = `${EV}/obs`;
const SQL_FILE = `${EV}/obs-sql.sql`;
const SQL_OUT = `${EV}/obs-sql.out`;
const SESSION_CACHE = "/tmp/648D-portal-session.json"; // JWT — never committed
const DEP_REF = "OBS648D-DEP";
const INV_ACCT_NO = "OBS648D-S864-ACCT";
const INV_NO = "OBS648D-S864-INV";
const PRINCIPAL = 10000;
const GROSS = 100;
const result = { startedAt: new Date().toISOString(), steps: {}, portal: {}, checks: {}, consoleErrors: [], pageErrors: [], notes: [] };
const save = () => fs.writeFileSync(`${OUT}/obs-result.json`, JSON.stringify(result, null, 2));
const TODAY = new Intl.DateTimeFormat("en-CA", { timeZone: "Africa/Johannesburg" }).format(new Date());
result.today = TODAY;
fs.mkdirSync(OUT, { recursive: true });

// ── read-only SQL evidence ──────────────────────────────────────────
const psqlRaw = (args) => execFileSync("docker", ["exec", "e2e-postgres", "psql", "-U", "postgres", "-d", "app", "-X", ...args], { encoding: "utf8" });
const psqlA = (q) => psqlRaw(["-At", "-F", "|", "-c", q]).trim();
const SCHEMA = psqlA("select schema_name from information_schema.schemata where schema_name like 'tenant_%' order by 1 limit 1");
const T = (q) => q.replace(/\bT\./g, `${SCHEMA}.`);
if (!fs.existsSync(SQL_FILE)) fs.writeFileSync(SQL_FILE, `-- 648D OBS read-only evidence queries (tenant schema ${SCHEMA})\n`);
if (!fs.existsSync(SQL_OUT)) fs.writeFileSync(SQL_OUT, `-- 648D OBS read-only evidence output (tenant schema ${SCHEMA})\n`);
function sql(label, q) {
  const qq = T(q);
  fs.appendFileSync(SQL_FILE, `\n-- [${label}]\n${qq};\n`);
  const out = psqlRaw(["-v", "ON_ERROR_STOP=1", "-c", qq]);
  fs.appendFileSync(SQL_OUT, `\n== [${label}] ${new Date().toISOString()}\n${qq}\n${out}`);
  return out;
}
const val = (q) => psqlA(T(q));
const rowsA = (q) => (val(q) || "").split("\n").filter(Boolean).map((l) => l.split("|"));
const eq = (x, y) => x != null && y != null && Math.abs(Number(x) - Number(y)) < 0.005;
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
async function waitSql(q, pred, ms = 30_000) {
  const t0 = Date.now(); let v = val(q);
  while (!pred(v) && Date.now() - t0 < ms) { await sleep(500); v = val(q); }
  return v;
}

// ── firm API (mock IDP bearer) ──────────────────────────────────────
const tokens = {};
async function firmToken(userId) {
  if (!tokens[userId]) {
    const r = await fetch(`${IDP}/token`, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ userId, orgSlug: SLUG }) });
    if (!r.ok) throw new Error(`mock IDP /token ${userId} → ${r.status}`);
    tokens[userId] = (await r.json()).access_token;
  }
  return tokens[userId];
}
async function firm(userId, method, path, body, saveAs) {
  const r = await fetch(`${BACKEND}${path}`, { method, headers: { "Content-Type": "application/json", Authorization: `Bearer ${await firmToken(userId)}` }, body: body === undefined ? undefined : JSON.stringify(body) });
  const text = await r.text();
  let json; try { json = text ? JSON.parse(text) : null; } catch { json = text.slice(0, 500); }
  const rec = { at: new Date().toISOString(), user: userId, method, path, status: r.status, request: body, body: json };
  if (saveAs) fs.writeFileSync(`${OUT}/api-firm-${saveAs}.json`, JSON.stringify(rec, null, 2));
  if (!r.ok) throw new Error(`${method} ${path} → ${r.status} ${text.slice(0, 400)}`);
  return json;
}
const ALICE = "user_e2e_alice";
const BOB = "user_e2e_bob";

// ── portal session + API ────────────────────────────────────────────
async function portalSession() {
  if (fs.existsSync(SESSION_CACHE)) {
    const s = JSON.parse(fs.readFileSync(SESSION_CACHE, "utf8"));
    if (Date.now() - s.at < 45 * 60_000) { result.notes.push(`portal session reused from ${new Date(s.at).toISOString()}`); return s; }
  }
  const email = val("select email from T.portal_contacts order by created_at limit 1");
  const link = await fetch(`${BACKEND}/portal/auth/request-link`, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ email, orgId: SLUG }) });
  if (!link.ok) throw new Error(`request-link → ${link.status} ${await link.text()}`);
  const { magicLink } = await link.json();
  const token = magicLink.split("token=").pop().split("&")[0];
  const ex = await fetch(`${BACKEND}/portal/auth/exchange`, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ token, orgId: SLUG }) });
  if (!ex.ok) throw new Error(`exchange → ${ex.status} ${await ex.text()}`);
  const d = await ex.json();
  const s = { at: Date.now(), token: d.token, clientId: d.clientId, clientName: d.clientName, email, orgId: SLUG };
  fs.writeFileSync(SESSION_CACHE, JSON.stringify(s));
  result.notes.push(`portal session exchanged at ${new Date(s.at).toISOString()} for ${email}`);
  return s;
}
async function portalApi(session, path, saveAs) {
  const r = await fetch(`${BACKEND}${path}`, { headers: { Authorization: `Bearer ${session.token}` } });
  const body = await r.json().catch(async () => (await r.text()).slice(0, 500));
  const rec = { at: new Date().toISOString(), path, status: r.status, body };
  if (saveAs) fs.writeFileSync(`${OUT}/api-portal-${saveAs}.json`, JSON.stringify(rec, null, 2));
  if (!r.ok) throw new Error(`portal GET ${path} → ${r.status}`);
  return body;
}

// ── browser ─────────────────────────────────────────────────────────
const norm = (s) => (s || "").replace(/[\s  ]+/g, " ").trim();
const money = (s) => { if (s == null) return null; const m = norm(s).match(/-?R ?-?\d{1,3}(?: \d{3})*,\d{2}/); if (!m) return null; const n = Number(m[0].replace(/[^\d,]/g, "").replace(",", ".")); return /-/.test(m[0]) ? -n : n; };
let browser;
async function portalPage(session, width = 1440) {
  const ctx = await browser.newContext({ viewport: { width, height: 1100 }, timezoneId: "Africa/Johannesburg" });
  const p = await ctx.newPage();
  p.on("console", (m) => { if (m.type() === "error") result.consoleErrors.push({ url: p.url(), text: m.text() }); });
  p.on("pageerror", (e) => result.pageErrors.push({ url: p.url(), text: String(e) }));
  await p.goto(`${PORTAL}/login`, { waitUntil: "load" });
  await p.evaluate((s) => {
    localStorage.setItem("portal_jwt", s.token);
    localStorage.setItem("portal_customer", JSON.stringify({ id: s.clientId, name: s.clientName, email: s.email, orgId: s.orgId }));
    localStorage.setItem("portal_last_org_id", s.orgId);
  }, session);
  return { ctx, p };
}
async function openMatter(p, matterId) {
  await p.goto(`${PORTAL}/trust/${matterId}`, { waitUntil: "load" });
  await p.getByLabel("Current balance").waitFor({ timeout: 60_000 });
  await p.locator("table[aria-label='Trust transactions'] tbody tr:visible, ul[aria-label='Trust transactions'] li:visible").first().waitFor({ timeout: 60_000 });
  await p.waitForTimeout(800);
}
async function cardState(p) {
  const card = p.getByLabel("Current balance").locator("xpath=ancestor::div[contains(@class,'rounded')][1]");
  const dl = await p.locator("dl").first().count() ? (await p.locator("dl dt").allInnerTexts()).map(norm) : [];
  const dd = await p.locator("dl").first().count() ? (await p.locator("dl dd").allInnerTexts()).map(norm) : [];
  return { headlineText: norm(await p.getByLabel("Current balance").innerText()), headline: money(await p.getByLabel("Current balance").innerText()),
    breakdown: dl.map((t, i) => ({ label: t, text: dd[i], amount: money(dd[i]) })), cardText: norm(await card.innerText().catch(() => "")) };
}
async function tableRows(p) {
  return p.evaluate(() => [...document.querySelectorAll("table[aria-label='Trust transactions'] tbody tr")].map((tr) => {
    const td = [...tr.querySelectorAll("td")];
    return { date: td[0]?.innerText.trim(), type: td[1]?.innerText.trim(), description: td[2]?.innerText.trim(), amount: td[3]?.innerText.trim(), balance: td[4]?.innerText.replace(/\s+/g, " ").trim(), investedQualifier: !!td[4]?.querySelector("span") && td[4].querySelector("span").innerText.trim() };
  }));
}

// bounding-rect clip probe (647D probe-clip method) on the desktop table and the balance card
async function clipDesktop(p, width) {
  await p.setViewportSize({ width, height: 1100 });
  await p.waitForTimeout(700);
  return p.evaluate(() => {
    const vw = document.documentElement.clientWidth;
    const t = document.querySelector("table[aria-label='Trust transactions']");
    let clipRight = vw, clipLeft = 0, clipBy = "viewport";
    for (let x = t.parentElement; x; x = x.parentElement) {
      const cs = getComputedStyle(x);
      if (cs.overflowX !== "visible") { const r = x.getBoundingClientRect(); clipRight = Math.min(vw, r.right); clipLeft = Math.max(0, r.left); clipBy = `${x.tagName}.${(x.className || "").toString().slice(0, 50)}`; break; }
    }
    const inside = (r) => r.left >= clipLeft - 0.5 && r.right <= clipRight + 0.5;
    const cellInfo = (c) => {
      const r = c.getBoundingClientRect();
      const kids = [...c.querySelectorAll("*")].map((k) => k.getBoundingClientRect()).filter((k) => k.width > 0);
      return { text: c.innerText.replace(/\s+/g, " ").trim().slice(0, 120), left: Math.round(r.left), right: Math.round(r.right), inside: inside(r) && kids.every(inside), truncated: c.scrollWidth > c.clientWidth + 1 };
    };
    const header = [...t.querySelectorAll("thead th")].map(cellInfo);
    const rows = [...t.querySelectorAll("tbody tr")].map((tr) => [...tr.querySelectorAll("td")].map(cellInfo));
    const qualifiers = [...t.querySelectorAll("tbody td:nth-child(5) span")].map((s) => { const r = s.getBoundingClientRect(); const cell = s.closest("td").getBoundingClientRect(); return { text: s.innerText, left: Math.round(r.left), right: Math.round(r.right), inCell: r.left >= cell.left - 0.5 && r.right <= cell.right + 0.5, inClip: inside(r) }; });
    const head = document.querySelector("[aria-label='Current balance']");
    const card = head.closest("div.rounded-xl, [class*='rounded']") || head.parentElement;
    const cr = card.getBoundingClientRect();
    const cardKids = [...card.querySelectorAll("p, dt, dd")].map((e) => { const r = e.getBoundingClientRect(); return { text: e.innerText.replace(/\s+/g, " ").trim().slice(0, 80), left: Math.round(r.left), right: Math.round(r.right), inCard: r.left >= cr.left - 0.5 && r.right <= cr.right + 0.5 && r.right <= vw + 0.5, truncated: e.scrollWidth > e.clientWidth + 1 }; });
    const wrap = t.parentElement;
    return { vw, clipBy, clipLeft: Math.round(clipLeft), clipRight: Math.round(clipRight), tableScrollWidth: t.scrollWidth, wrapperScrollWidth: wrap.scrollWidth, wrapperClientWidth: wrap.clientWidth,
      docScrollWidth: document.documentElement.scrollWidth, header, rows, qualifiers, cardKids,
      allInside: header.every((h) => h.inside) && rows.every((r) => r.every((c) => c.inside)),
      noneTruncated: header.every((h) => !h.truncated) && rows.every((r) => r.every((c) => !c.truncated)),
      noHorizontalOverflow: wrap.scrollWidth <= wrap.clientWidth + 1 && document.documentElement.scrollWidth <= vw + 1,
      qualifiersOk: qualifiers.every((q) => q.inCell && q.inClip), cardOk: cardKids.every((k) => k.inCard && !k.truncated) };
  });
}
async function clipMobile(p, width) {
  await p.setViewportSize({ width, height: 900 });
  await p.waitForTimeout(700);
  return p.evaluate(() => {
    const vw = document.documentElement.clientWidth;
    const ul = document.querySelector("ul[aria-label='Trust transactions']");
    const items = [...ul.querySelectorAll("li")].map((li) => {
      const lr = li.getBoundingClientRect();
      const kids = [...li.querySelectorAll("p")].map((e) => { const r = e.getBoundingClientRect(); return { text: e.innerText.replace(/\s+/g, " ").trim().slice(0, 100), left: Math.round(r.left), right: Math.round(r.right), ok: r.left >= lr.left - 0.5 && r.right <= lr.right + 0.5 && r.right <= vw + 0.5, truncated: e.scrollWidth > e.clientWidth + 1 }; });
      return { left: Math.round(lr.left), right: Math.round(lr.right), insideViewport: lr.left >= -0.5 && lr.right <= vw + 0.5, kids };
    });
    const head = document.querySelector("[aria-label='Current balance']");
    const card = head.closest("div.rounded-xl, [class*='rounded']") || head.parentElement;
    const cr = card.getBoundingClientRect();
    const cardKids = [...card.querySelectorAll("p, dt, dd")].map((e) => { const r = e.getBoundingClientRect(); return { text: e.innerText.replace(/\s+/g, " ").trim().slice(0, 80), left: Math.round(r.left), right: Math.round(r.right), ok: r.left >= cr.left - 0.5 && r.right <= cr.right + 0.5 && r.right <= vw + 0.5, truncated: e.scrollWidth > e.clientWidth + 1 }; });
    return { vw, docScrollWidth: document.documentElement.scrollWidth, items, cardKids,
      ok: document.documentElement.scrollWidth <= vw + 1 && items.every((i) => i.insideViewport && i.kids.every((k) => k.ok && !k.truncated)) && cardKids.every((k) => k.ok && !k.truncated) };
  });
}

async function observe(session, matterId, tag, { full = false } = {}) {
  const o = {};
  o.apiSummary = await portalApi(session, "/portal/trust/summary", `${tag}-summary`);
  const { ctx, p } = await portalPage(session, 1440);
  await openMatter(p, matterId);
  o.card = await cardState(p);
  o.rows = await tableRows(p);
  await p.screenshot({ path: `${OUT}/${tag}-matter-1440.png`, fullPage: true });
  if (full) {
    o.text1440 = norm(await p.locator("body").innerText());
    fs.writeFileSync(`${OUT}/${tag}-matter-1440.txt`, await p.locator("body").innerText());
    await p.getByLabel("Current balance").locator("xpath=ancestor::div[contains(@class,'rounded')][1]").screenshot({ path: `${OUT}/${tag}-balance-card-crop.png` }).catch((e) => result.notes.push(`card crop failed ${e}`));
    const interestRow = p.locator("table[aria-label='Trust transactions'] tbody tr", { hasText: "Section 86(4) investment interest" }).first();
    if (await interestRow.count()) await interestRow.screenshot({ path: `${OUT}/${tag}-interest-row-crop.png` });
    o.clip = { w1440: await clipDesktop(p, 1440), w1280: await clipDesktop(p, 1280) };
    await p.screenshot({ path: `${OUT}/${tag}-matter-1280.png`, fullPage: true });
    o.text1280 = norm(await p.locator("body").innerText());
    o.clip.w390 = await clipMobile(p, 390);
    await p.screenshot({ path: `${OUT}/${tag}-matter-390.png`, fullPage: true });
    o.text390 = norm(await p.locator("body").innerText());
    fs.writeFileSync(`${OUT}/${tag}-matter-390.txt`, await p.locator("body").innerText());
    o.mobileItems = (await p.locator("ul[aria-label='Trust transactions'] li").allInnerTexts()).map(norm);
    await p.setViewportSize({ width: 1440, height: 1100 });
    // the /trust index: one trust matter → auto-redirect to the matter (MatterSelector not rendered)
    await p.goto(`${PORTAL}/trust`, { waitUntil: "load" });
    await p.waitForURL(/\/trust\/[0-9a-f-]{36}/, { timeout: 30_000 }).catch(() => {});
    await p.waitForTimeout(800);
    o.trustIndex = { finalUrl: p.url(), selectorRendered: (await p.locator("[aria-label='Matters with trust activity']").count()) > 0 };
    await p.screenshot({ path: `${OUT}/${tag}-trust-index-1440.png`, fullPage: true });
  }
  await ctx.close();
  return o;
}

// ── main ────────────────────────────────────────────────────────────
(async () => {
  browser = await chromium.launch({ headless: true });
  const ids = (result.ids = {});
  ids.schema = SCHEMA;
  ids.general = val("select id from T.trust_accounts where account_type='GENERAL' and is_primary order by created_at limit 1");
  ids.acme = val("select id from T.customers where name='Acme Corp'");
  ids.matter = val("select id from T.projects where name='Website Redesign'");
  sql("context: trust accounts before", "select id, account_name, account_type, investment_basis, is_primary, require_dual_approval, status from T.trust_accounts order by created_at");
  sql("context: portal read-model before", `select customer_id, matter_id, current_balance, invested_balance, last_transaction_at from portal.portal_trust_balance where customer_id='${ids.acme}'`);

  // a. INVESTMENT account, basis CLIENT_INSTRUCTION
  const a = (result.steps.a = {});
  ids.inv4 = val("select id from T.trust_accounts where account_type='INVESTMENT' and investment_basis='CLIENT_INSTRUCTION' order by created_at limit 1");
  if (!ids.inv4) {
    const created = await firm(ALICE, "POST", "/api/trust-accounts", { accountName: "OBS 648D s86(4) Investment Account", bankName: "Nedbank", branchCode: "198765", accountNumber: INV_ACCT_NO, accountType: "INVESTMENT", isPrimary: false, requireDualApproval: false, openedDate: TODAY, notes: "648D OBS", investmentBasis: "CLIENT_INSTRUCTION" }, "a-create-investment-account");
    ids.inv4 = created.id; a.created = true;
  } else a.created = false;
  a.row = sql("a: investment account", `select id, account_name, account_type, investment_basis, status from T.trust_accounts where id='${ids.inv4}'`);
  save();

  // b. deposit R20 000 into the general account for Acme / Website Redesign
  const b = (result.steps.b = {});
  if (!val(`select id from T.trust_transactions where reference='${DEP_REF}'`)) {
    b.response = await firm(ALICE, "POST", `/api/trust-accounts/${ids.general}/transactions/deposit`, { clientId: ids.acme, matterId: ids.matter, amount: 20000, reference: DEP_REF, description: "OBS 648D funding deposit", transactionDate: TODAY }, "b-deposit");
    b.created = true;
  } else b.created = false;
  b.row = sql("b: deposit", `select id, transaction_type, status, amount, ledger, ledger_effect, posted_at is not null posted from T.trust_transactions where reference='${DEP_REF}'`);
  await waitSql(`select count(*) from portal.portal_trust_transaction where reference='${DEP_REF}'`, (v) => Number(v) === 1);
  save();

  const session = await portalSession();
  ids.portalClientId = session.clientId;

  // P0 — before the placement
  const placed = val(`select id from T.trust_investments where account_number='${INV_NO}'`);
  if (!placed) {
    result.portal.p0 = await observe(session, ids.matter, "p0-before-placement");
    sql("p0: portal balance before placement", `select current_balance, invested_balance from portal.portal_trust_balance where customer_id='${ids.acme}' and matter_id='${ids.matter}'`);
    save();
  } else result.notes.push("placement already existed on re-run — P0 kept from the first run");

  // c. placement R10 000 (s86(4)) + Bob's approval
  const c = (result.steps.c = {});
  if (!placed) {
    c.response = await firm(ALICE, "POST", `/api/trust-accounts/${ids.general}/investments`, { investmentAccountId: ids.inv4, clientId: ids.acme, matterId: ids.matter, institution: "Nedbank", accountNumber: INV_NO, principal: PRINCIPAL, interestRate: 0.07, depositDate: TODAY, notes: "648D OBS s86(4) placement", investmentBasis: "CLIENT_INSTRUCTION" }, "c-place");
  }
  const [inv] = rowsA(`select id, deposit_transaction_id, investment_basis, status, principal from T.trust_investments where account_number='${INV_NO}'`);
  ids.investment = inv[0]; ids.placeTx = inv[1];
  c.placeTxStatusBefore = val(`select status from T.trust_transactions where id='${ids.placeTx}'`);
  if (c.placeTxStatusBefore === "AWAITING_APPROVAL") {
    c.approve = await firm(BOB, "POST", `/api/trust-transactions/${ids.placeTx}/approve`, undefined, "c-approve-bob");
  }
  c.rows = sql("c: placement rows (all ledgers) for the investment", `select t.transaction_type, t.status, t.amount, t.ledger, t.ledger_effect, t.cashbook_effect, t.posted_at is not null posted, t.trust_account_id, t.customer_id is not null has_customer, t.project_id is not null has_matter, t.recorded_by, t.approved_by from T.trust_transactions t where t.investment_id='${ids.investment}' or t.id='${ids.placeTx}' order by t.created_at`);
  c.approvedBy = val(`select m.name from T.trust_transactions t join T.members m on m.id=t.approved_by where t.id='${ids.placeTx}'`);
  await waitSql(`select invested_balance from portal.portal_trust_balance where customer_id='${ids.acme}' and matter_id='${ids.matter}'`, (v) => Number(v) >= PRINCIPAL);
  save();

  // P1 — after the placement (total must be unchanged)
  if (!result.portal.p1) {
    result.portal.p1 = await observe(session, ids.matter, "p1-after-placement");
    sql("p1: portal balance after placement", `select current_balance, invested_balance from portal.portal_trust_balance where customer_id='${ids.acme}' and matter_id='${ids.matter}'`);
    save();
  }

  // d. NET bank-advice interest: gross 100 → Fund 5 → client 95
  const d = (result.steps.d = {});
  if (Number(val(`select count(*) from T.trust_investment_interest where investment_id='${ids.investment}'`)) === 0) {
    d.response = await firm(ALICE, "POST", `/api/trust-investments/${ids.investment}/interest`, { entryDate: TODAY, grossAmount: GROSS, presentation: "NET" }, "d-record-interest");
  }
  d.entries = sql("d: interest entry", `select entry_date, gross_amount, fund_share_amount, net_amount, presentation, status, net_transaction_id, fund_share_transaction_id from T.trust_investment_interest where investment_id='${ids.investment}'`);
  d.rows = sql("d: all trust rows for the investment after interest", `select t.transaction_type, t.status, t.amount, t.ledger, t.ledger_effect, t.cashbook_effect, t.customer_id is not null has_customer, t.project_id is not null has_matter, t.trust_account_id from T.trust_transactions t where t.investment_id='${ids.investment}' or t.id='${ids.placeTx}' order by t.created_at`);
  d.portalInvested = await waitSql(`select invested_balance from portal.portal_trust_balance where customer_id='${ids.acme}' and matter_id='${ids.matter}'`, (v) => eq(v, PRINCIPAL + GROSS * 0.95));
  save();

  // P2 — final observation
  const p2 = (result.portal.p2 = await observe(session, ids.matter, "p2-final", { full: true }));
  p2.apiTransactions = await portalApi(session, `/portal/trust/matters/${ids.matter}/transactions?size=200`, "p2-transactions");
  p2.apiMovements = await portalApi(session, "/portal/trust/movements?limit=50", "p2-movements");
  sql("p2: portal.portal_trust_balance", `select customer_id, matter_id, current_balance, invested_balance, last_transaction_at from portal.portal_trust_balance where customer_id='${ids.acme}'`);
  sql("p2: portal.portal_trust_transaction", `select transaction_type, ledger, amount, running_balance, transaction_date, description, reference from portal.portal_trust_transaction where customer_id='${ids.acme}' and matter_id='${ids.matter}' order by occurred_at, id`);
  sql("p2: tenant posted sums for Acme by ledger (firm truth)", `select ledger, sum(ledger_effect) from T.trust_transactions where customer_id='${ids.acme}' and posted_at is not null group by ledger order by ledger`);
  const dbBal = rowsA(`select current_balance, invested_balance from portal.portal_trust_balance where customer_id='${ids.acme}' and matter_id='${ids.matter}'`)[0];
  const dbTx = rowsA(`select transaction_type, ledger, amount, running_balance from portal.portal_trust_transaction where customer_id='${ids.acme}' and matter_id='${ids.matter}' order by occurred_at, id`);
  const tenant = Object.fromEntries(rowsA(`select ledger, sum(ledger_effect) from T.trust_transactions where customer_id='${ids.acme}' and posted_at is not null group by ledger`));
  // firm-side client ledger (648E) — API + firm UI screenshot
  p2.firmLedger = await firm(ALICE, "GET", `/api/trust-accounts/${ids.general}/client-ledgers/${ids.acme}`, undefined, "p2-client-ledger");
  try {
    const fctx = await browser.newContext({ viewport: { width: 1440, height: 1100 }, timezoneId: "Africa/Johannesburg" });
    const fp = await fctx.newPage();
    await fp.goto(`${FIRM}/mock-login`, { waitUntil: "load" });
    await fp.locator("#user-select").selectOption(ALICE);
    await fp.getByRole("button", { name: "Sign In", exact: true }).click();
    await fp.waitForURL(/\/org\//, { timeout: 60_000 });
    await fp.goto(`${FIRM}/org/${SLUG}/trust-accounting/client-ledgers/${ids.acme}?accountId=${ids.general}`, { waitUntil: "load" });
    await fp.getByTestId("ledger-totals-breakdown").waitFor({ timeout: 60_000 });
    await fp.waitForTimeout(800);
    p2.firmUi = { general: norm(await fp.getByTestId("ledger-total-balance").innerText()), invested: norm(await fp.getByTestId("ledger-total-invested").innerText().catch(() => "")), held: norm(await fp.getByTestId("ledger-total-held").innerText().catch(() => "")) };
    await fp.screenshot({ path: `${OUT}/p2-firm-client-ledger-1440.png`, fullPage: true });
    await fp.getByTestId("ledger-totals-breakdown").screenshot({ path: `${OUT}/p2-firm-ledger-totals-crop.png` });
    await fctx.close();
  } catch (e) { result.notes.push(`firm UI client ledger: ${String(e).slice(0, 300)}`); }

  // ── checks ─────────────────────────────────────────────────────────
  const p0 = result.portal.p0, p1 = result.portal.p1;
  const sumApi = (o) => (o.apiSummary.matters || []).find((m) => m.matterId === ids.matter) || {};
  const s0 = sumApi(p0), s1 = sumApi(p1), s2 = sumApi(p2);
  const NET = GROSS * 0.95;
  const brk = (o, label) => (o.card.breakdown.find((x) => x.label === label) || {}).amount;
  const rows = p2.rows;
  const interestRows = rows.filter((r) => /interest/i.test(r.type) || /interest/i.test(r.description));
  const apiRows = p2.apiTransactions.content || [];
  const RAW = ["INV_INTEREST", "INV_RECEIVE", "CLIENT_INVESTMENT", "FUND_INV_SHARE", "DEPOSIT", "INV_PLACE", "INV_WITHDRAW", "INV_RETURN", "FUND_INTEREST", "NON_CLIENT", "INTER_ACCOUNT"];
  const rawHits = (t) => RAW.filter((w) => new RegExp(`\\b${w}\\b`).test(t || ""));
  result.figures = {
    before: { headline: p0.card.headline, apiGeneral: s0.currentBalance, apiInvested: s0.investedBalance, breakdown: p0.card.breakdown },
    afterPlacement: { headline: p1.card.headline, apiGeneral: s1.currentBalance, apiInvested: s1.investedBalance, breakdown: p1.card.breakdown },
    final: { headline: p2.card.headline, general: brk(p2, "In the firm's trust account"), invested: brk(p2, "Invested on your instruction (section 86(4))"), apiGeneral: s2.currentBalance, apiInvested: s2.investedBalance, dbGeneral: dbBal[0], dbInvested: dbBal[1], tenantByLedger: tenant, firmLedger: { balance: p2.firmLedger.balance, investedBalance: p2.firmLedger.investedBalance }, firmUi: p2.firmUi },
  };
  result.checks.a = {
    beforeNoBreakdown: p0.card.breakdown.length === 0 && eq(p0.card.headline, s0.currentBalance) && eq(s0.investedBalance, 0),
    totalUnchangedByPlacement: eq(p1.card.headline, p0.card.headline) && eq(s1.currentBalance + s1.investedBalance, s0.currentBalance + s0.investedBalance),
    placementSplit: eq(brk(p1, "In the firm's trust account"), s0.currentBalance - PRINCIPAL) && eq(brk(p1, "Invested on your instruction (section 86(4))"), PRINCIPAL),
    finalHeadlineIsGeneralPlusInvested: eq(p2.card.headline, s2.currentBalance + s2.investedBalance),
    finalGeneral: eq(brk(p2, "In the firm's trust account"), s2.currentBalance) && eq(s2.currentBalance, s0.currentBalance - PRINCIPAL),
    finalInvested10095: eq(brk(p2, "Invested on your instruction (section 86(4))"), PRINCIPAL + NET),
    headlineAriaLabel: (await Promise.resolve(true)),
  };
  const placeRows = rows.filter((r) => r.type === "Placed in section 86(4) investment");
  result.checks.b = {
    exactlyOneInterestLine: interestRows.length === 1,
    interestTypedAndAmount: interestRows.length === 1 && interestRows[0].type === "Section 86(4) investment interest" && eq(money(interestRows[0].amount), NET),
    interestInvestedQualifier: interestRows.length === 1 && interestRows[0].investedQualifier === "Invested" && eq(money(interestRows[0].balance), PRINCIPAL + NET),
    noFundShareOrCapitalOrFundInterest: !rows.some((r) => /Fund share|Investment capital received|Fund interest|Investment withdrawn/i.test(`${r.type} ${r.description}`)),
    placementLabelled: placeRows.length === 1 && eq(money(placeRows[0].amount), PRINCIPAL) && !placeRows[0].investedQualifier,
    generalRowsNoQualifier: rows.filter((r) => r.type !== "Section 86(4) investment interest").every((r) => !r.investedQualifier),
    everyTypeHuman: rows.every((r) => r.type && !/^[A-Z_]+$/.test(r.type) && !/_/.test(r.type)),
    mobileInterest: p2.mobileItems.filter((t) => /Section 86\(4\) investment interest/.test(t)).length === 1 && p2.mobileItems.some((t) => /Section 86\(4\) investment interest/.test(t) && /Invested bal R ?10 095,00/.test(t)),
  };
  result.checks.c = { na: !p2.trustIndex.selectorRendered && /\/trust\/[0-9a-f-]{36}/.test(p2.trustIndex.finalUrl), reason: "Acme has exactly one trust matter (portal.portal_trust_balance has one row) → /trust auto-redirects to the matter (trust/page.tsx); MatterSelector not rendered", summaryHeadline: eq(s2.currentBalance + s2.investedBalance, p2.card.headline) };
  const apiInterest = apiRows.filter((t) => t.transactionType === "INV_INTEREST");
  result.checks.d = {
    apiSummaryEqualsDb: eq(s2.currentBalance, dbBal[0]) && eq(s2.investedBalance, dbBal[1]),
    uiEqualsApi: eq(brk(p2, "In the firm's trust account"), s2.currentBalance) && eq(brk(p2, "Invested on your instruction (section 86(4))"), s2.investedBalance),
    dbEqualsTenantLedgers: eq(dbBal[0], tenant.CLIENT) && eq(dbBal[1], tenant.CLIENT_INVESTMENT),
    firmLedgerApiEqual: eq(p2.firmLedger.balance, dbBal[0]) && eq(p2.firmLedger.investedBalance, dbBal[1]),
    firmUiEqual: !!p2.firmUi && eq(money(p2.firmUi.general), dbBal[0]) && eq(money(p2.firmUi.invested), dbBal[1]) && eq(money(p2.firmUi.held), Number(dbBal[0]) + Number(dbBal[1])),
    apiRowsMatchDb: apiRows.length === dbTx.length && dbTx.every(([type, ledger, amount, running]) => apiRows.some((t) => t.transactionType === type && t.ledger === ledger && eq(t.amount, amount) && eq(t.runningBalance, running))),
    apiInterestRow: apiInterest.length === 1 && apiInterest[0].typeLabel === "Section 86(4) investment interest" && apiInterest[0].ledger === "CLIENT_INVESTMENT" && eq(apiInterest[0].amount, NET) && eq(apiInterest[0].runningBalance, PRINCIPAL + NET),
    dbLedgersOnlyClientOrInvestment: dbTx.every(([, ledger]) => ledger === "CLIENT" || ledger === "CLIENT_INVESTMENT") && dbTx.filter(([, l]) => l === "CLIENT_INVESTMENT").every(([t]) => t === "INV_INTEREST"),
    uiRowsEqualApiRows: rows.length === apiRows.length && apiRows.every((t) => rows.some((r) => r.type === t.typeLabel && eq(money(r.amount), t.amount) && eq(money(r.balance), t.runningBalance))),
    movementsTypeLabel: (Array.isArray(p2.apiMovements) ? p2.apiMovements : p2.apiMovements.content || []).every((m) => typeof m.typeLabel === "string" && m.typeLabel.length > 0),
  };
  result.checks.e = {
    w1440: p2.clip.w1440.allInside && p2.clip.w1440.noneTruncated && p2.clip.w1440.noHorizontalOverflow && p2.clip.w1440.qualifiersOk && p2.clip.w1440.cardOk && p2.clip.w1440.qualifiers.length === 1,
    w1280: p2.clip.w1280.allInside && p2.clip.w1280.noneTruncated && p2.clip.w1280.noHorizontalOverflow && p2.clip.w1280.qualifiersOk && p2.clip.w1280.cardOk && p2.clip.w1280.qualifiers.length === 1,
    w390: p2.clip.w390.ok,
  };
  fs.writeFileSync(`${OUT}/clip-probe.json`, JSON.stringify(p2.clip, null, 2));
  result.checks.g = { hits1440: rawHits(p2.text1440), hits1280: rawHits(p2.text1280), hits390: rawHits(p2.text390), clean: [p2.text1440, p2.text1280, p2.text390].every((t) => rawHits(t).length === 0) };
  delete result.checks.a.headlineAriaLabel;
  result.verdicts = Object.fromEntries(Object.entries(result.checks).map(([k, v]) => [k, k === "c" ? (v.na ? "N/A" : "FAIL") : k === "g" ? (v.clean ? "PASS" : "FAIL") : (Object.values(v).every(Boolean) ? "PASS" : "FAIL")]));
  result.finishedAt = new Date().toISOString();
  save();
  console.log(JSON.stringify({ verdicts: result.verdicts, checks: result.checks, figures: result.figures, rows, consoleErrors: result.consoleErrors, pageErrors: result.pageErrors, notes: result.notes }, null, 2));
  await browser.close();
})().catch(async (e) => { result.error = String(e.stack || e); save(); console.error("OBS FAILED", e); if (browser) await browser.close(); process.exit(1); });
