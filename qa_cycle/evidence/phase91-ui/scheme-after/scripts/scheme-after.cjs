// 661B task 661.14: the 644 companion set — the Phase 90 exit-matrix surfaces
// (qa_cycle/evidence/phase90-ui/matrix/, scripts/matrix.cjs + personas.cjs + portal.cjs +
// marketing-control.cjs) re-shot in LIGHT ONLY on the 661B head, at their original widths
// (1024x768 / 1440x900), on the e2e mock-auth stack (:3001; Phase 90 shot the KC dev stack).
// Read-only except the portal "Send Magic Link" click (as Phase 90's portal.cjs did).
// Per shot: viewport PNG into RAW (out of the tree), the light audit (../../661B/walk/scripts/audit.cjs),
// console errors/warnings. Contact sheets (one per surface group) into OUT.
// Usage (worktree root): node qa_cycle/evidence/phase91-ui/scheme-after/scripts/scheme-after.cjs qa_cycle/evidence/phase91-ui/scheme-after [portalUrl]
const path = require("path");
const fs = require("fs");
const C = require("../../661B/walk/scripts/common.cjs");
const { lightAudit } = require("../../661B/walk/scripts/audit.cjs");
const { chromium, APP, API, ORG, rel, idpToken, api, list, loginState, newCtx, sheet } = C;

const OUT = path.resolve(process.argv[2] || "/tmp/661b-scheme-after");
const PORTAL = process.argv[3] || "http://localhost:3012";
const RAW = process.env.RAW || "/tmp/661b-scheme-after-raw";
fs.mkdirSync(RAW, { recursive: true });
const BASE = `${APP}/org/${ORG}`;
const P90 = "qa_cycle/evidence/phase90-ui/matrix";
const consoleMsgs = [];
const report = [];
const notes = [];
const note = (m) => {
  notes.push(m);
  console.log("NOTE", m);
};
const VP = { 1024: { width: 1024, height: 768 }, 1440: { width: 1440, height: 900 } };
const hook = (page, cur) => {
  page.on("console", (m) => {
    if (m.type() === "error" || m.type() === "warning" || /hydrat/i.test(m.text())) consoleMsgs.push({ label: cur.label, type: m.type(), text: m.text().slice(0, 300) });
  });
  page.on("pageerror", (e) => consoleMsgs.push({ label: cur.label, type: "pageerror", text: String(e).slice(0, 300) }));
};
async function open(page, url, waitSel) {
  const resp = await page.goto(url, { waitUntil: "load", timeout: 120000 });
  await page.locator("h1").first().waitFor({ timeout: 60000 }).catch(() => {});
  if (waitSel) await page.locator(waitSel).first().waitFor({ timeout: 30000 }).catch(() => note(`waitSel ${waitSel} not seen on ${url.replace(APP, "")}`));
  await page.mouse.move(1, 1);
  await page.waitForTimeout(3000);
  return resp ? resp.status() : null;
}
async function capture(page, cur, label, p90, { full = false, audit = true, status = null, persona = "owner-alice" } = {}) {
  cur.label = label;
  const before = consoleMsgs.length;
  const f = path.join(RAW, `${label}.png`);
  await page.screenshot({ path: f, fullPage: full });
  const facts = await page.evaluate(() => ({
    url: location.pathname + location.search,
    h1: document.querySelector("h1")?.textContent?.trim().slice(0, 60) ?? null,
    dark: document.documentElement.classList.contains("dark"),
    tabs: [...document.querySelectorAll('[role="tab"][aria-selected="true"], [role="tab"][data-state="active"]')].map((t) => t.textContent.trim()),
    audience: document.querySelector("[data-audience]")?.getAttribute("data-audience") ?? null,
  }));
  const a = audit ? await page.evaluate(lightAudit).catch((e) => ({ error: String(e).slice(0, 120) })) : null;
  const rec = { label, persona, status, p90: p90 && `${P90}/${p90}`, file: rel(f), ...facts, consoleDuringShot: consoleMsgs.length - before, audit: a };
  report.push(rec);
  console.log(label, status, facts.url, `dark=${facts.dark}`, a ? `low=${a.lowTextCount}/${a.lowTextPreCount} scheme=${a.schemeTextCount} illeg=${a.schemeIllegibleCount}` : "");
  return f;
}

(async () => {
  const browser = await chromium.launch();
  const token = await idpToken("alice");
  const matters = list(await api(token, "/api/matters"));
  const matter = matters.filter((p) => p.status === "ACTIVE" && p.clientName).sort((x, y) => (y.trustBalance || 0) - (x.trustBalance || 0))[0] || matters[0];
  const clientId = matter.clientId || matter.customerId;
  note(`matter=${matter.id} (${matter.name}); client=${clientId} (${matter.clientName})`);
  const SURFACES = [
    ["home", "/dashboard", '[data-testid="home-page"]'],
    ["matters", "/matters", "table"],
    ["matter-detail", `/matters/${matter.id}`, '[data-testid="matter-header-card"]'],
    ["clients", "/clients", "table"],
    ["client-detail", `/clients/${clientId}`, "h1"],
    ["trust", "/trust-accounting", '[data-testid="trust-balance-trend"] svg.recharts-surface'],
    ["profitability", "/reports/profitability", '[data-testid="billing-trend"] svg.recharts-surface'],
  ];
  const FULL = new Set(["home", "profitability"]);
  const groups = {};
  const add = (g, cap, f) => (groups[g] = groups[g] || []).push([cap, f]);

  // ---- Owner (Alice): 7 surfaces x {1024, 1440} light, + full-page Home / profitability at 1440, + 3 matter tabs.
  const alice = await loginState(browser, "alice");
  for (const width of [1024, 1440]) {
    const ctx = await newCtx(browser, alice, { ...VP[width], theme: "light", extra: { reducedMotion: "reduce", timezoneId: "Africa/Johannesburg" } });
    const page = await ctx.newPage();
    const cur = { label: "" };
    hook(page, cur);
    for (const [name, p, waitSel] of SURFACES) {
      const label = `${name}-${width}-light`;
      const status = await open(page, BASE + p, waitSel);
      add(name, `${label} (P90: matrix/${label}.png)`, await capture(page, cur, label, `${label}.png`, { status }));
      if (width === 1440 && FULL.has(name)) add(name, `${label}-full (P90: matrix/${label}-full.png)`, await capture(page, cur, `${label}-full`, `${label}-full.png`, { full: true, audit: false, status }));
    }
    if (width === 1440)
      for (const tab of ["time", "trust", "activity"]) {
        const label = `matter-detail-tab-${tab}-1440-light`;
        const status = await open(page, `${BASE}/matters/${matter.id}?tab=${tab}`, '[role="tabpanel"]');
        add("matter-tabs", `${label} (P90: matrix/${label}.png)`, await capture(page, cur, label, `${label}.png`, { status }));
      }
    await ctx.close();
  }

  // ---- Carol (system Member): Home and the same matter detail at 1440 light (personas.cjs counterparts).
  // The FINANCIAL_VISIBILITY custom-role shot (home-fv-role-carol) is not re-shot: it needs a role
  // created and assigned (tenant writes), which this read-only re-shoot does not do.
  {
    const carol = await loginState(browser, "carol");
    const ctx = await newCtx(browser, carol, { ...VP[1440], theme: "light", extra: { reducedMotion: "reduce", timezoneId: "Africa/Johannesburg" } });
    const page = await ctx.newPage();
    const cur = { label: "" };
    hook(page, cur);
    let status = await open(page, `${BASE}/dashboard`, '[data-testid="home-page"]');
    add("personas", "home-member-carol-1440-light (P90: matrix/home-member-carol-1440-light.png)", await capture(page, cur, "home-member-carol-1440-light", "home-member-carol-1440-light.png", { status, persona: "member-carol" }));
    // Carol is not on the owner's matter's team on this stack (the not-found branch, kept as an extra
    // tile); the counterpart shot uses the ACTIVE client matter with the largest trust balance that
    // Carol's own /api/matters lists.
    status = await open(page, `${BASE}/matters/${matter.id}`, null);
    add("personas", `matter-detail-member-carol-not-on-team-1440-light (extra: Carol on the owner's matter ${matter.name})`, await capture(page, cur, "matter-detail-member-carol-not-on-team-1440-light", null, { status, persona: "member-carol" }));
    const cm = list(await api(await idpToken("carol"), "/api/matters"));
    const carolMatter = cm.filter((p) => p.status === "ACTIVE" && p.clientName).sort((x, y) => (y.trustBalance || 0) - (x.trustBalance || 0))[0] || cm[0];
    note(`carol matter=${carolMatter && carolMatter.id} (${carolMatter && carolMatter.name}); carol lists ${cm.length} matters`);
    status = await open(page, `${BASE}/matters/${carolMatter.id}`, '[data-testid="matter-header-card"]');
    add("personas", `matter-detail-member-carol-1440-light (P90: matrix/matter-detail-member-carol-1440-light.png) — ${carolMatter.name}`, await capture(page, cur, "matter-detail-member-carol-1440-light", "matter-detail-member-carol-1440-light.png", { status, persona: "member-carol" }));
    await ctx.close();
  }

  // ---- Portal 1024 light (portal.cjs counterparts) on the worktree portal (next dev :3012) with the
  // Origin shim (memory env_playwright_mcp_profile_lock_obs). Contact: the e2e Acme Corp contact.
  try {
    const email = process.env.PORTAL_EMAIL || "contact@acme.example.com";
    const lr = await fetch(`${API}/portal/auth/request-link`, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ email, orgId: ORG }) });
    if (!lr.ok) throw new Error(`request-link ${lr.status}`);
    const { magicLink } = await lr.json();
    const tk = magicLink.split("token=").pop().split("&")[0];
    const er = await fetch(`${API}/portal/auth/exchange`, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ token: tk, orgId: ORG }) });
    if (!er.ok) throw new Error(`exchange ${er.status}`);
    const s = await er.json();
    const ctx = await browser.newContext({ viewport: VP[1024], deviceScaleFactor: 1, reducedMotion: "reduce", timezoneId: "Africa/Johannesburg" });
    await ctx.addInitScript(C.INIT, null);
    await ctx.route(`${API}/**`, async (route) => {
      const req = route.request();
      const acao = { "access-control-allow-origin": new URL(PORTAL).origin, "access-control-allow-credentials": "true", "access-control-allow-headers": "*", "access-control-allow-methods": "*" };
      if (req.method() === "OPTIONS") return route.fulfill({ status: 204, headers: acao });
      const resp = await route.fetch({ headers: { ...req.headers(), origin: "http://localhost:3002" } });
      return route.fulfill({ response: resp, headers: { ...resp.headers(), ...acao } });
    });
    const page = await ctx.newPage();
    const cur = { label: "" };
    hook(page, cur);
    let status = await open(page, `${PORTAL}/login?orgId=${ORG}`, null);
    add("portal", "portal /login 1024 light (rest state; P90 shot only the link-sent state)", await capture(page, cur, "portal-login-1024-light", null, { status, persona: "anonymous" }));
    const input = page.locator('input[type="email"], input[name="email"]').first();
    if (await input.count()) {
      await input.fill(email);
      await page.getByRole("button", { name: "Send Magic Link" }).click();
      await page.waitForTimeout(2500);
      await page.mouse.move(1, 1);
      add("portal", "portal-login-link-sent-1024-light (P90: matrix/portal-login-link-sent-1024-light.png) — dev token shown is single-use, 15-min TTL, localhost", await capture(page, cur, "portal-login-link-sent-1024-light", "portal-login-link-sent-1024-light.png", { status, persona: "anonymous" }));
    } else note("portal /login: no email input found");
    await page.evaluate((x) => {
      localStorage.setItem("portal_jwt", x.token);
      localStorage.setItem("portal_customer", JSON.stringify({ id: x.clientId, name: x.clientName, email: x.email, orgId: x.orgId }));
      localStorage.setItem("portal_last_org_id", x.orgId);
    }, { token: s.token, clientId: s.clientId, clientName: s.clientName, email, orgId: ORG });
    status = await open(page, `${PORTAL}/home`, null);
    add("portal", "portal-home-1024-light (P90: matrix/portal-home-1024-light.png)", await capture(page, cur, "portal-home-1024-light", "portal-home-1024-light.png", { status, persona: `portal ${s.clientName}` }));
    status = await open(page, `${PORTAL}/trust`, null);
    await page.waitForTimeout(1000);
    add("portal", "portal-trust-1024-light (P90: matrix/portal-trust-1024-light.png)", await capture(page, cur, "portal-trust-1024-light", "portal-trust-1024-light.png", { status, persona: `portal ${s.clientName}` }));
    await ctx.close();
  } catch (e) {
    note(`portal: ${String(e).slice(0, 300)}`);
  }

  // ---- Marketing / control, 1440, no stored preference, OS light (marketing-control.cjs counterpart).
  {
    const ctx = await browser.newContext({ viewport: VP[1440], deviceScaleFactor: 1, colorScheme: "light", reducedMotion: "reduce", timezoneId: "Africa/Johannesburg" });
    await ctx.addInitScript(C.INIT, null);
    const page = await ctx.newPage();
    const cur = { label: "" };
    hook(page, cur);
    const status = await open(page, `${APP}/`, null);
    add("marketing", "marketing-control-no-pref-1440 (P90: matrix/marketing-control-no-pref-1440.png)", await capture(page, cur, "marketing-control-no-pref-1440", "marketing-control-no-pref-1440.png", { status, persona: "anonymous" }));
    add("marketing", "marketing-control-no-pref-1440-full (P90: matrix/marketing-control-no-pref-1440-full.png)", await capture(page, cur, "marketing-control-no-pref-1440-full", "marketing-control-no-pref-1440-full.png", { full: true, audit: false, status, persona: "anonymous" }));
    await ctx.close();
  }

  // ---- Contact sheets (native scale; full-page shots at half scale).
  const sheets = [];
  for (const [g, files] of Object.entries(groups)) {
    const file = `${g}.png`;
    const tileW = g === "marketing" ? 720 : null;
    await sheet(browser, path.join(OUT, file), `scheme-after (661B head, light only) — ${g}`, files, g === "portal" ? 2 : 2, tileW);
    sheets.push([file, files.map((x) => x[0])]);
  }
  fs.writeFileSync(path.join(OUT, "obs-report.json"), JSON.stringify({ capturedAt: new Date().toISOString(), stack: "e2e mock-auth :3001 (frontend rebuilt from 661B head)", matter: { id: matter.id, name: matter.name }, clientId, notes, sheets, report }) + "\n");
  fs.writeFileSync(path.join(OUT, "console.json"), JSON.stringify(consoleMsgs) + "\n");
  await browser.close();
  console.log("DONE", report.length, "captures", sheets.length, "sheets", consoleMsgs.length, "console entries");
})().catch((e) => {
  console.error("FATAL", e);
  process.exit(1);
});
