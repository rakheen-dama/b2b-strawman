// 651A OBS — headless Chromium (Playwright library) on the e2e mock-auth stack built from the slice head
// (frontend :3001, backend :8081, mock IDP :8090). Alice (owner).
// Observes: a the seven retired interest-run routes answer 404 (owner JWT) · b kept trust routes still answer 200
// (incl. /lpff-rates, which 651E removes) · c notification preferences no longer offer TRUST_INTEREST_POSTED ·
// d trust surfaces still render in the browser after V27 (no error page, no page errors).
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
    return { method, url, status: res.status, title: json && json.title, body: text.slice(0, 200) };
  };

  // b (first, to get an account id). Kept routes.
  const accounts = await call("GET", "/api/trust-accounts");
  const list = JSON.parse((await (await fetch(`${BACKEND}/api/trust-accounts`, { headers: H })).text()));
  const arr = Array.isArray(list) ? list : (list.content || []);
  const acct = arr[0] && arr[0].id;
  const kept = [accounts,
    await call("GET", `/api/trust-accounts/${acct}`),
    await call("GET", `/api/trust-accounts/${acct}/lpff-rates`),
    await call("GET", `/api/trust-integrity`)];
  R.items.b = { accountId: acct, accountCount: arr.length, kept, pass: !!acct && kept.every((k) => k.status === 200) };

  // a. The seven retired routes (were InterestController's mappings, §16.4.9).
  const runId = "00000000-0000-0000-0000-000000000651";
  const acc = `/api/trust-accounts/${acct}/interest-runs`, run = `/api/interest-runs/${runId}`;
  const retired = [
    await call("GET", acc),
    await call("POST", acc, { periodStart: "2026-10-01", periodEnd: "2026-10-31" }),
    await call("GET", run),
    await call("DELETE", run),
    await call("POST", `${run}/calculate`),
    await call("POST", `${run}/approve`),
    await call("POST", `${run}/post`)];
  R.items.a = { retired, pass: retired.every((r) => r.status === 404) };

  // c. Notification preferences.
  const prefsRes = await fetch(`${BACKEND}/api/notifications/preferences`, { headers: H });
  const prefs = await prefsRes.json();
  const types = (prefs.preferences || []).map((x) => x.notificationType);
  R.items.c = { status: prefsRes.status, typeCount: types.length,
    hasInterestPosted: types.includes("TRUST_INTEREST_POSTED"), hasReconCompleted: types.includes("TRUST_RECONCILIATION_COMPLETED"),
    pass: prefsRes.status === 200 && !types.includes("TRUST_INTEREST_POSTED") && types.includes("TRUST_RECONCILIATION_COMPLETED") };

  // d. Browser — trust surfaces still render.
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
    ["d2-transactions", `${BASE}/trust-accounting/transactions`],
    ["d3-client-ledgers", `${BASE}/trust-accounting/client-ledgers`],
    ["d4-reconciliation", `${BASE}/trust-accounting/reconciliation`],
    ["d5-investments", `${BASE}/trust-accounting/investments`],
    ["d6-other-creditors", `${BASE}/trust-accounting/other-creditors`],
    ["d7-notification-settings", `${BASE}/settings/connections?tab=notifications`],
    ["d8-retired-interest-route", `${BASE}/trust-accounting/interest`],
  ];
  R.items.d = { pages: [] };
  for (const [name, url] of pages) {
    const before = R.pageErrors.length;
    const resp = await p.goto(url); await settle(p);
    const text = await p.locator("body").innerText();
    await p.screenshot({ path: path.join(OUT, `${name}.png`), fullPage: true });
    R.items.d.pages.push({ name, url, status: resp && resp.status(),
      errorPage: /something went wrong|application error|unexpected error/i.test(text),
      notFound: /could not be found|not found/i.test(text), interestRunCopy: /interest run/i.test(text),
      newPageErrors: R.pageErrors.length - before });
  }
  const live = R.items.d.pages.filter((x) => x.name !== "d8-retired-interest-route");
  const retiredPage = R.items.d.pages.find((x) => x.name === "d8-retired-interest-route");
  R.items.d.pass = live.every((x) => x.status === 200 && !x.errorPage && !x.notFound && !x.interestRunCopy && x.newPageErrors === 0)
    && retiredPage.status === 404;
  await browser.close();

  R.finishedAt = new Date().toISOString();
  R.pass = Object.values(R.items).every((i) => i.pass);
  fs.writeFileSync(path.join(OUT, "obs-result.json"), JSON.stringify(R, null, 2));
  console.log(JSON.stringify({ pass: R.pass, a: R.items.a.pass, b: R.items.b.pass, c: R.items.c.pass, d: R.items.d.pass }));
})().catch((e) => { console.error(e); process.exit(1); });
