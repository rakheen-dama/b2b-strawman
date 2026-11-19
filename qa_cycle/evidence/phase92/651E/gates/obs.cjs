// 651E OBS — headless Chromium (Playwright library) on the e2e mock-auth stack built from the slice head
// (frontend :3001, backend :8081, mock IDP :8090). Alice (owner). Runs AFTER the legal-lifecycle suite on the
// same stack, so the Day-60 investments exist and the register check is not vacuous.
// Observes: a GET / POST /api/trust-accounts/{id}/lpff-rates answer 404 (owner JWT) · b kept trust routes still
// answer 200 · c investment register rows (every trust account) carry no applicableLpffRate key, at least one row
// exists, and the register preview HTML / PDF export still render · d trust surfaces and the settings trust tab
// render in the browser after V28 (no error page, no page errors, no LPFF-rate copy).
// The database and backend-log checks (e, f) run from the shell — see README.md.
const fs = require("fs");
const path = require("path");
const W = path.resolve(__dirname, "../../../../..");
const { chromium } = require(require.resolve("@playwright/test", { paths: [path.join(W, "frontend")] }));
const FIRM = "http://localhost:3001", SLUG = "e2e-test-org", BASE = `${FIRM}/org/${SLUG}`;
const BACKEND = "http://localhost:8081", IDP = "http://localhost:8090";
const OUT = path.join(__dirname, "obs");
fs.mkdirSync(OUT, { recursive: true });
const R = { startedAt: new Date().toISOString(), consoleErrors: [], pageErrors: [], items: {} };
const settle = async (p, ms = 1500) => { await p.waitForLoadState("networkidle").catch(() => {}); await p.waitForTimeout(ms); };

async function aliceToken() {
  const r = await fetch(`${IDP}/token`, { method: "POST", headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ userId: "user_e2e_alice", orgId: "org_e2e_test", orgSlug: SLUG, orgRole: "org:owner" }) });
  if (!r.ok) throw new Error(`token ${r.status}`);
  return (await r.json()).access_token;
}

(async () => {
  const token = await aliceToken();
  const H = { Authorization: `Bearer ${token}`, "Content-Type": "application/json" };
  const call = async (method, url, body) => {
    const res = await fetch(`${BACKEND}${url}`, { method, headers: H, body: body ? JSON.stringify(body) : undefined });
    const text = await res.text();
    let json = null; try { json = JSON.parse(text); } catch {}
    return { method, url, status: res.status, title: json && json.title, detail: json && json.detail, body: text.slice(0, 200), json };
  };
  const strip = (c) => ({ method: c.method, url: c.url, status: c.status, title: c.title, detail: c.detail });

  // b. Kept routes (first, to get the account ids).
  const accounts = await call("GET", "/api/trust-accounts");
  const arr = Array.isArray(accounts.json) ? accounts.json : ((accounts.json && accounts.json.content) || []);
  const acct = arr[0] && arr[0].id;
  const kept = [accounts,
    await call("GET", `/api/trust-accounts/${acct}`),
    await call("GET", `/api/trust-integrity`),
    await call("GET", `/api/report-definitions/investment-register`)];
  R.items.b = { accountId: acct, accountCount: arr.length, accounts: arr.map((a) => ({ id: a.id, name: a.accountName || a.name, type: a.accountType })),
    kept: kept.map(strip), pass: !!acct && kept.every((k) => k.status === 200) };

  // a. The removed rate routes (were TrustAccountingController's two mappings, §16.3.14).
  const rates = `/api/trust-accounts/${acct}/lpff-rates`;
  const removed = [
    await call("GET", rates),
    await call("POST", rates, { effectiveFrom: "2026-10-01", ratePercent: 7.5, lpffSharePercent: 75 })];
  R.items.a = { removed: removed.map(strip), pass: removed.every((r) => r.status === 404) };

  // c. Investment register — every trust account; no applicableLpffRate key on any row.
  const reg = [];
  for (const a of arr) {
    const ex = await call("POST", `/api/report-definitions/investment-register/execute`,
      { parameters: { trust_account_id: a.id }, page: 0, size: 100 });
    const rows = (ex.json && ex.json.rows) || [];
    reg.push({ accountId: a.id, status: ex.status, rowCount: rows.length,
      rowsWithKey: rows.filter((r) => Object.prototype.hasOwnProperty.call(r, "applicableLpffRate")).length,
      sampleKeys: rows[0] ? Object.keys(rows[0]).sort() : [] });
  }
  const prev = await fetch(`${BACKEND}/api/report-definitions/investment-register/preview?trust_account_id=${acct}`, { headers: H });
  const prevHtml = await prev.text();
  fs.writeFileSync(path.join(OUT, "c-register-preview.html"), prevHtml);
  const pdf = await fetch(`${BACKEND}/api/report-definitions/investment-register/export/pdf?trust_account_id=${acct}`, { headers: H });
  const pdfBuf = Buffer.from(await pdf.arrayBuffer());
  R.items.c = { registers: reg, totalRows: reg.reduce((s, r) => s + r.rowCount, 0),
    preview: { status: prev.status, lpffRateHeader: /LPFF\s*Rate/i.test(prevHtml), applicableKey: /applicableLpffRate/.test(prevHtml) },
    pdf: { status: pdf.status, bytes: pdfBuf.length, magic: pdfBuf.subarray(0, 4).toString() } };
  R.items.c.pass = reg.every((r) => r.status === 200 && r.rowsWithKey === 0) && R.items.c.totalRows > 0
    && prev.status === 200 && !R.items.c.preview.lpffRateHeader && !R.items.c.preview.applicableKey
    && pdf.status === 200 && R.items.c.pdf.magic === "%PDF";

  // d. Browser — trust surfaces and the settings trust tab still render.
  const browser = await chromium.launch({ headless: true });
  const ctx = await browser.newContext({ viewport: { width: 1440, height: 1000 }, timezoneId: "Africa/Johannesburg" });
  const p = await ctx.newPage();
  p.on("console", (m) => { if (m.type() === "error") R.consoleErrors.push({ url: p.url(), text: m.text().slice(0, 300) }); });
  p.on("pageerror", (e) => R.pageErrors.push({ url: p.url(), text: String(e).slice(0, 300) }));
  await p.goto(`${FIRM}/mock-login`, { waitUntil: "load" });
  await p.locator("#user-select").selectOption("user_e2e_alice");
  await p.getByRole("button", { name: "Sign In", exact: true }).click();
  await p.waitForURL(/\/org\//, { timeout: 60_000 });
  await p.goto(`${BASE}/dashboard`); await settle(p, 2500);
  R.consoleErrorsAfterLoginSettle = R.consoleErrors.length; // spurious userinfo error after login is known noise
  const pages = [
    ["d1-trust-accounting", `${BASE}/trust-accounting`],
    ["d2-investments", `${BASE}/trust-accounting/investments`],
    ["d3-other-creditors", `${BASE}/trust-accounting/other-creditors`],
    ["d4-reports", `${BASE}/trust-accounting/reports`],
    ["d5-settings-trust-tab", `${BASE}/settings/fees?tab=trust-accounting`],
  ];
  R.items.d = { pages: [] };
  for (const [name, url] of pages) {
    const before = R.pageErrors.length;
    const resp = await p.goto(url); await settle(p);
    const text = await p.locator("body").innerText();
    await p.screenshot({ path: path.join(OUT, `${name}.png`), fullPage: true });
    R.items.d.pages.push({ name, url, status: resp && resp.status(),
      errorPage: /something went wrong|application error|unexpected error/i.test(text),
      notFound: /could not be found|page not found/i.test(text), lpffRateCopy: /LPFF rate/i.test(text),
      newPageErrors: R.pageErrors.length - before });
  }
  R.items.d.pass = R.items.d.pages.every((x) => x.status === 200 && !x.errorPage && !x.notFound && !x.lpffRateCopy && x.newPageErrors === 0);
  await browser.close();

  R.finishedAt = new Date().toISOString();
  R.pass = Object.values(R.items).every((i) => i.pass);
  fs.writeFileSync(path.join(OUT, "obs-result.json"), JSON.stringify(R, null, 2));
  console.log(JSON.stringify({ pass: R.pass, a: R.items.a.pass, b: R.items.b.pass, c: R.items.c.pass, d: R.items.d.pass, totalRows: R.items.c.totalRows }));
})().catch((e) => { console.error(e); process.exit(1); });
