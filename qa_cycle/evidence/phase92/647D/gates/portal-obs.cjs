// 647D OBS (d, portal UI) — read-only. The Moroka portal contact signs in through the branch portal's own login
// page (next dev on :3012, NEXT_PUBLIC_PORTAL_API_URL=:8081; the e2e backend's CORS allows only :3002, so a
// context.route shim re-fetches with Origin :3002 and adds ACAO for :3012). The dev login page prints the magic link;
// the script clicks it. Under next dev (React StrictMode) /auth/exchange runs its effect twice: the first exchange
// stores the session (storeAuth runs before the `cancelled` check), the second gets 401 for the spent token and
// shows "Link expired". So the script proceeds once portal_jwt is in localStorage, then opens the matter's trust page.
const W = "/Users/rakheendama/Projects/2026/worktree-epic-647D";
const { chromium } = require(`${W}/frontend/node_modules/@playwright/test`);
const fs = require("fs");
const { execFileSync } = require("child_process");
const OUT = `${W}/qa_cycle/evidence/phase92/647D/gates/obs`;
const PORTAL = "http://localhost:3012";
const BACKEND = "http://localhost:8081";
const psqlA = (q) => execFileSync("docker", ["exec", "e2e-postgres", "psql", "-U", "postgres", "-d", "app", "-X", "-At", "-F", "|", "-c", q], { encoding: "utf8" }).trim();
const norm = (s) => (s || "").replace(/\s+/g, " ").trim();
(async () => {
  const prev = JSON.parse(fs.readFileSync(`${OUT}/obs-result.json`, "utf8"));
  const ids = prev.ids;
  const S = psqlA("select schema_name from information_schema.schemata where schema_name like 'tenant_%' order by 1 limit 1");
  const email = psqlA(`select email from ${S}.portal_contacts where customer_id='${ids.moroka}' limit 1`);
  const res = { at: new Date().toISOString(), email, matter: ids.morokaMatter, consoleErrors: [], expectedDescription: prev.steps.c.expectedDescription };
  const browser = await chromium.launch({ headless: true });
  const ctx = await browser.newContext({ viewport: { width: 1440, height: 1100 }, timezoneId: "Africa/Johannesburg" });
  const cors = (req) => ({ "access-control-allow-origin": PORTAL, "access-control-allow-credentials": "true",
    "access-control-allow-methods": "GET,POST,PUT,PATCH,DELETE,OPTIONS", "access-control-allow-headers": req.headers()["access-control-request-headers"] || "authorization,content-type" });
  await ctx.route(`${BACKEND}/**`, async (route) => {
    const req = route.request();
    if (req.method() === "OPTIONS") return route.fulfill({ status: 204, headers: cors(req) });
    const resp = await route.fetch({ headers: { ...req.headers(), origin: "http://localhost:3002" } });
    await route.fulfill({ response: resp, headers: { ...resp.headers(), ...cors(req) } });
  });
  const p = await ctx.newPage();
  p.on("console", (m) => { if (m.type() === "error") res.consoleErrors.push({ url: p.url(), text: m.text() }); });
  await p.goto(`${PORTAL}/login?orgId=e2e-test-org`, { waitUntil: "load" });
  await p.locator("input[type='email']").fill(email);
  await p.locator("button[type='submit']").click();
  const link = p.locator("a[href*='/auth/exchange']");
  await link.waitFor({ timeout: 60_000 });
  await link.click();
  await p.waitForFunction(() => !!localStorage.getItem("portal_jwt"), null, { timeout: 60_000 });
  await p.waitForTimeout(1500);
  res.afterExchangeUrl = p.url();
  res.afterExchangeText = norm(await p.locator("body").innerText()).slice(0, 300);
  await p.goto(`${PORTAL}/trust/${ids.morokaMatter}`, { waitUntil: "load" });
  await p.waitForFunction(() => document.body.innerText.includes("Deposit identified and allocated"), null, { timeout: 60_000 });
  await p.waitForTimeout(1000);
  for (const w of [1440, 1280, 390]) {
    await p.setViewportSize({ width: w, height: w === 390 ? 900 : 1100 });
    await p.waitForTimeout(600);
    await p.screenshot({ path: `${OUT}/d1-portal-moroka-trust-${w}.png`, fullPage: true });
    res[`text${w}`] = norm(await p.locator("body").innerText());
  }
  await p.setViewportSize({ width: 1440, height: 1100 });
  await p.waitForTimeout(600);
  const rows = p.locator("tr:visible");
  res.rows = (await rows.allInnerTexts()).map(norm);
  const alloc = p.locator("tr:visible", { hasText: "Deposit identified and allocated" }).first();
  if (await alloc.count()) await alloc.screenshot({ path: `${OUT}/d1-portal-allocated-row-crop.png` });
  fs.writeFileSync(`${OUT}/d1-portal-moroka-trust.txt`, res.text1440);
  const t = res.text1440;
  res.checks = {
    labelShown: t.includes("Deposit identified and allocated") && !/Unident alloc in|UNIDENT_ALLOC_IN/i.test(t),
    descriptionVerbatimD12: t.includes(res.expectedDescription),
    noFirmOnlyLabels: !/Unidentified deposit allocated|paid to the Fund|UNIDENT_/i.test(t),
    mobileLabelShown: res.text390.includes("Deposit identified and allocated"),
  };
  res.verdict = Object.values(res.checks).every(Boolean) ? "PASS" : "FAIL";
  fs.writeFileSync(`${OUT}/portal-obs.json`, JSON.stringify(res, null, 2));
  console.log(JSON.stringify({ verdict: res.verdict, checks: res.checks, rows: res.rows, consoleErrors: res.consoleErrors }, null, 2));
  await browser.close();
})().catch((e) => { console.error("PORTAL-OBS FAILED", e); process.exit(1); });
