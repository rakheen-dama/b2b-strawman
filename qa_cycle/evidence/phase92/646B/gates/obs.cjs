// 646B OBS — real headless Chromium against the e2e mock-auth stack (:3001), after the lifecycle run.
// Read-only: no state changes at all (646B adds only a read surface). psql is read-only EVIDENCE.
const { chromium } = require(
  "/Users/rakheendama/Projects/2026/worktree-epic-646B/frontend/node_modules/@playwright/test"
);
const fs = require("fs");
const { execFileSync } = require("child_process");

const BASE = "http://localhost:3001";
const BACKEND = "http://localhost:8081";
const SLUG = "e2e-test-org";
const EV = "/Users/rakheendama/Projects/2026/646B-gates-evidence";
const OUT = `${EV}/obs`;
const SQL_FILE = `${EV}/obs-sums.sql`;
const SQL_OUT = `${EV}/obs-sums.out`;
const result = { steps: {}, consoleErrors: [], pageErrors: [], notes: [] };
const RAW_ENUMS = ["FUND_INTEREST_AND_CHARGES", "UNIDENTIFIED_DEPOSITS"];
const norm = (s) => (s || "").replace(/[  ]/g, " ").replace(/[ \t]+/g, " ");

// ── read-only SQL evidence ──────────────────────────────────────────
function psql(q) {
  return execFileSync(
    "docker",
    ["exec", "e2e-postgres", "psql", "-U", "postgres", "-d", "app", "-X", "-v", "ON_ERROR_STOP=1", "-c", q],
    { encoding: "utf8" }
  );
}
function psqlA(q) {
  return execFileSync(
    "docker",
    ["exec", "e2e-postgres", "psql", "-U", "postgres", "-d", "app", "-X", "-At", "-F", "|", "-c", q],
    { encoding: "utf8" }
  ).trim();
}
const SCHEMA = psqlA(
  "select schema_name from information_schema.schemata where schema_name like 'tenant_%' order by 1 limit 1"
);
fs.writeFileSync(SQL_FILE, `-- 646B OBS read-only evidence queries (schema ${SCHEMA}); run order = step order\n`);
fs.writeFileSync(SQL_OUT, `-- 646B OBS read-only evidence output (schema ${SCHEMA}) ${new Date().toISOString()}\n`);
function sql(label, q) {
  const qq = q.replace(/\bT\./g, `${SCHEMA}.`);
  fs.appendFileSync(SQL_FILE, `\n-- [${label}]\n${qq};\n`);
  const out = psql(qq);
  fs.appendFileSync(SQL_OUT, `\n== [${label}]\n${qq}\n${out}`);
  return out;
}
function val(q) {
  return psqlA(q.replace(/\bT\./g, `${SCHEMA}.`));
}

// ── browser helpers ─────────────────────────────────────────────────
let browser, context, page;
async function newSession(userId) {
  if (context) await context.close();
  context = await browser.newContext({ viewport: { width: 1440, height: 1100 }, timezoneId: "Africa/Johannesburg" });
  page = await context.newPage();
  page.on("console", (m) => {
    if (m.type() === "error") result.consoleErrors.push({ user: userId, url: page.url(), text: m.text() });
  });
  page.on("pageerror", (e) => result.pageErrors.push({ user: userId, url: page.url(), text: String(e) }));
  await page.goto(`${BASE}/mock-login`, { waitUntil: "load" });
  await page.locator("#user-select").selectOption(userId);
  await page.getByRole("button", { name: "Sign In", exact: true }).click();
  await page.waitForURL(/\/org\//, { timeout: 60_000 });
  await page.getByRole("heading").first().waitFor({ timeout: 30_000 });
}
let shot = 0;
async function snap(name) {
  shot += 1;
  const file = `${OUT}/${String(shot).padStart(2, "0")}-${name}.png`;
  await page.waitForTimeout(800);
  await page.screenshot({ path: file, fullPage: true });
  const text = await page.locator("body").innerText().catch(() => "");
  fs.writeFileSync(file.replace(/\.png$/, ".txt"), text);
  return { file, text };
}

async function trustZone(user) {
  await page.goto(`${BASE}/org/${SLUG}/dashboard`, { waitUntil: "load" });
  const nav = page.getByRole("navigation", { name: "Main navigation" });
  await nav.waitFor({ timeout: 30_000 });
  const zones = await nav.locator("button[aria-expanded]").allInnerTexts();
  const header = nav.getByRole("button", { name: "Trust", exact: true });
  const present = (await header.count()) > 0;
  if (present) {
    for (let i = 0; i < 20; i++) {
      if ((await header.getAttribute("aria-expanded")) === "true") break;
      await header.click().catch(() => {});
      await page.waitForTimeout(500);
    }
  }
  const links = (await nav.getByRole("link").allInnerTexts()).map((t) => t.trim());
  const ci = links.indexOf("Client ledgers");
  const oc = links.indexOf("Other trust creditors");
  const href = oc >= 0 ? await nav.getByRole("link", { name: "Other trust creditors", exact: true }).getAttribute("href") : null;
  const s = await snap(`${user}-sidebar-trust-zone`);
  return {
    zones: zones.map((z) => z.trim()),
    trustZonePresent: present,
    links,
    clientLedgersIndex: ci,
    otherCreditorsIndex: oc,
    directlyAfterClientLedgers: ci >= 0 && oc === ci + 1,
    otherCreditorsHref: href,
    trustZoneLinks: ci >= 0 ? links.slice(Math.max(0, links.indexOf("Overview")), ci + 6) : null,
    shot: s.file,
  };
}

async function otherCreditorsPage(user, viaSidebar) {
  if (viaSidebar) {
    const nav = page.getByRole("navigation", { name: "Main navigation" });
    await nav.getByRole("link", { name: "Other trust creditors", exact: true }).click();
    await page.waitForURL(/\/trust-accounting\/other-creditors/, { timeout: 30_000 });
  } else {
    await page.goto(`${BASE}/org/${SLUG}/trust-accounting/other-creditors`, { waitUntil: "load" });
  }
  const main = page.locator("#main-content");
  await main.getByRole("heading", { level: 1 }).first().waitFor({ timeout: 30_000 });
  const r = { url: page.url() };
  r.h1 = (await main.getByRole("heading", { level: 1 }).first().innerText()).trim();
  r.documentTitle = await page.title();
  r.breadcrumb = norm(await page.getByRole("navigation", { name: "Breadcrumb" }).innerText().catch(() => null));
  r.pageTestIdPresent = (await page.getByTestId("other-creditors-page").count()) > 0;
  r.cardsPresent = (await page.getByTestId("creditor-account-cards").count()) > 0;
  r.cards = {};
  for (const t of RAW_ENUMS) {
    const c = page.getByTestId(`creditor-${t}`);
    r.cards[t] = (await c.count()) > 0 ? norm(await c.innerText()).replace(/\n+/g, " | ") : null;
  }
  r.cardCount = await page.locator('[data-testid^="creditor-"]').filter({ hasNot: page.locator("[data-testid=creditor-account-cards]") }).count();
  r.inDebitNotice = (await page.getByTestId("fund-ledger-in-debit").count()) > 0;
  r.noAccountState = (await page.getByTestId("no-account-state").count()) > 0;
  r.errorState = (await page.getByTestId("error-state").count()) > 0;
  const s = await snap(`${user}-other-creditors-page`);
  const body = norm(s.text);
  r.rawEnumsVisible = RAW_ENUMS.filter((e) => body.includes(e));
  r.zeroBalanceCount = (body.match(/R 0,00/g) || []).length;
  r.somethingWentWrong = body.includes("Something went wrong");
  r.notFound = /404|not be found|could not be found/i.test(body) && !r.pageTestIdPresent;
  r.shot = s.file;
  return r;
}

async function apiCall(user, accountId) {
  const cookies = await context.cookies();
  const tok = cookies.find((c) => c.name === "mock-auth-token");
  const r = { tokenCookiePresent: !!tok };
  const url = `${BACKEND}/api/trust-accounts/${accountId}/creditor-accounts`;
  if (tok) {
    const res = await context.request.get(url, { headers: { Authorization: `Bearer ${tok.value}` } });
    r.status = res.status();
    r.body = await res.json().catch(async () => (await res.text()).slice(0, 500));
  }
  const anon = await context.request.get(url, { headers: { Authorization: "" } });
  r.noTokenStatus = anon.status();
  // the frontend origin has no /api proxy to the backend (calls are server-side via lib/api)
  const fe = await context.request.get(`${BASE}/api/trust-accounts/${accountId}/creditor-accounts`, { maxRedirects: 0 });
  r.frontendOriginStatus = fe.status();
  return r;
}

(async () => {
  fs.mkdirSync(OUT, { recursive: true });
  browser = await chromium.launch({ headless: true });

  // ════ d (before UI): DB evidence ════
  const accountId = val("select id from T.trust_accounts order by created_at limit 1");
  result.trustAccountId = accountId;
  sql("d1: trust accounts", "select id, account_name, account_type, is_primary, status, created_at from T.trust_accounts order by created_at");
  sql(
    "d2: creditor accounts per trust account",
    "select c.trust_account_id, c.account_type, c.name, c.id, c.created_at from T.trust_creditor_accounts c order by c.trust_account_id, c.account_type"
  );
  sql(
    "d3: creditor-account count per trust account (expected 2, one of each type)",
    "select a.id as trust_account_id, a.account_name, count(c.id) as creditor_accounts, count(distinct c.account_type) as distinct_types from T.trust_accounts a left join T.trust_creditor_accounts c on c.trust_account_id=a.id group by a.id, a.account_name order by a.account_name"
  );
  sql(
    "d4: ledger-target / customer-required constraints",
    `select n.nspname, c.conname, pg_get_constraintdef(c.oid) from pg_constraint c join pg_namespace n on n.oid=c.connamespace where n.nspname='${SCHEMA}' and c.conname in ('chk_trust_txn_ledger_target','chk_trust_txn_customer_required') order by c.conname`
  );
  sql(
    "d5: chk_trust_txn_type names",
    `select count(*) as type_count, string_agg(m[1], ', ' order by ord) as names from pg_constraint c join pg_namespace n on n.oid=c.connamespace, lateral regexp_matches(pg_get_constraintdef(c.oid), '''([A-Z_]+)''::character varying', 'g') with ordinality as x(m, ord) where n.nspname='${SCHEMA}' and c.conname='chk_trust_txn_type'`
  );
  sql(
    "d6: trust_transactions creditor_account_id / investment_id / ledger",
    "select count(*) as total, count(*) filter (where creditor_account_id is null) as creditor_null, count(*) filter (where investment_id is null) as investment_null, string_agg(distinct ledger, ',') as ledgers from T.trust_transactions"
  );
  sql(
    "d7: EQ-2",
    `select (select coalesce(sum(cashbook_effect),0) from T.trust_transactions where posted_at is not null) as cashbook,
            (select coalesce(sum(ledger_effect),0) from T.trust_transactions where posted_at is not null and ledger='CLIENT') as sum_client_ledgers,
            (select coalesce(sum(cashbook_effect),0) from T.trust_transactions where posted_at is not null)
          - (select coalesce(sum(ledger_effect),0) from T.trust_transactions where posted_at is not null and ledger='CLIENT') as eq2_difference`
  );
  sql(
    "d8: derived creditor-account balances (expected 0.00 — no NON_CLIENT rows yet)",
    "select c.account_type, coalesce(sum(t.ledger_effect) filter (where t.posted_at is not null),0) as balance from T.trust_creditor_accounts c left join T.trust_transactions t on t.creditor_account_id=c.id group by c.account_type order by 1"
  );
  sql(
    "d9: audit events for trust_creditor_account",
    "select a.event_type, a.entity_type, a.entity_id, a.occurred_at, a.details from T.audit_events a where a.entity_type in ('trust_creditor_account','trust_account') order by a.occurred_at"
  );
  sql(
    "d10: flyway v18",
    "select version, description, success, installed_on from T.flyway_schema_history where version='18'"
  );
  const d = (result.steps.d = {});
  d.creditorRows = val("select count(*) from T.trust_creditor_accounts");
  d.trustAccounts = val("select count(*) from T.trust_accounts");
  d.ledgerTargetPresent = val(`select count(*) from pg_constraint c join pg_namespace n on n.oid=c.connamespace where n.nspname='${SCHEMA}' and c.conname='chk_trust_txn_ledger_target'`);
  d.customerRequiredPresent = val(`select count(*) from pg_constraint c join pg_namespace n on n.oid=c.connamespace where n.nspname='${SCHEMA}' and c.conname='chk_trust_txn_customer_required'`);
  d.typeCount = val(`select count(*) from pg_constraint c join pg_namespace n on n.oid=c.connamespace, lateral regexp_matches(pg_get_constraintdef(c.oid), '''([A-Z_]+)''::character varying', 'g') where n.nspname='${SCHEMA}' and c.conname='chk_trust_txn_type'`);
  d.txnTotal = val("select count(*) from T.trust_transactions");
  d.txnCreditorNotNull = val("select count(*) from T.trust_transactions where creditor_account_id is not null");
  d.eq2 = val(
    "select (select coalesce(sum(cashbook_effect),0) from T.trust_transactions where posted_at is not null) - (select coalesce(sum(ledger_effect),0) from T.trust_transactions where posted_at is not null and ledger='CLIENT')"
  );
  d.cashbook = val("select coalesce(sum(cashbook_effect),0) from T.trust_transactions where posted_at is not null");
  d.creditorAuditEvents = val("select count(*) from T.audit_events where entity_type='trust_creditor_account'");

  // ════ a + b: Alice (owner) ════
  await newSession("user_e2e_alice");
  result.steps.a_alice = await trustZone("a-alice");
  result.steps.b_alice = await otherCreditorsPage("b-alice", true);
  result.steps.b_alice_api = await apiCall("alice", accountId);

  // ════ Bob (admin) ════
  await newSession("user_e2e_bob");
  result.steps.bob_zone = await trustZone("bob");
  result.steps.bob_page = await otherCreditorsPage("bob", false);

  // ════ c: Carol (member — VIEW_TRUST by the seeded matrix) ════
  result.steps.c_carolCapabilities = sql(
    "c: role capability matrix (trust capabilities)",
    "select r.slug, array_agg(c.capability order by c.capability) filter (where c.capability like '%TRUST%') as trust_caps from T.org_roles r left join T.org_role_capabilities c on c.org_role_id=r.id group by r.slug order by r.slug"
  );
  await newSession("user_e2e_carol");
  result.steps.c_carol_zone = await trustZone("c-carol");
  result.steps.c_carol_page = await otherCreditorsPage("c-carol", false);
  result.steps.c_carol_api = await apiCall("carol", accountId);

  fs.writeFileSync(`${OUT}/obs-result.json`, JSON.stringify(result, null, 2));
  console.log(JSON.stringify(result, null, 2));
  await browser.close();
})().catch(async (e) => {
  console.error("OBS FAILED:", e);
  try {
    await snap("zz-fatal");
  } catch {}
  fs.writeFileSync(`${OUT}/obs-result.json`, JSON.stringify({ ...result, fatal: String(e) }, null, 2));
  process.exit(1);
});
