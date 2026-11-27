// 669B step 0: seed — RUN ONCE (PHASE=before). Idempotent: each access request is found again by
// its email, so a re-run creates nothing and only appends an all-"already" entry to runs[].
// Always writes before/seed.json, whatever PHASE is. No SQL; no direct REST writes to the app; the
// only REST calls are GETs (inventory) and Mailpit's read API (the OTP code).
// (1) Read-only inventory (as Alice unless noted): members, billing subscription (member pill),
//     AI gates, access requests, billing tenants, demo tenants (PILOT + COMPLIMENTARY), the three
//     personas' capabilities.
// (2) Before any write: the naturally EMPTY /platform-admin/access-requests (0 requests on this
//     stack) is probed and shot on all four tabs (All / Pending / Approved / Rejected) at Alice
//     1440 light + 1024 light — the K-01 248 px page-level empties cannot be observed after the seed.
// (3) Writes, all through the browser:
//     a. two access requests through the public /request-access form (anonymous context): Work
//        Email, Full Name, Organisation Name, Country "South Africa", Industry "Legal Services" →
//        "Submit" → OTP step; the 6-digit code is read from Mailpit REST, typed, "Verify" → PENDING;
//     b. Alice on /platform-admin/access-requests (Pending tab) › row "OBS 669B Beta Inc" › "Reject"
//        › AlertDialog "Reject" → REJECTED. NEVER Approve (it provisions a tenant on the shared stack).
//     AI gates and demo tenants are not seeded (no AI config; provisioning a demo tenant is a write
//     that creates a tenant) — DA-19.
// Usage: node 00-seed.cjs
const fs = require("fs");
const path = require("path");
const L = require("./lib.cjs");
const { probe } = require("./probe.cjs");

const REQS = [
  { key: "alpha", email: "obs-669b-alpha@alpha-attorneys.e2e-test.local", fullName: "Naledi Alpha", org: "OBS 669B Alpha Attorneys", reject: false },
  { key: "beta", email: "obs-669b-beta@beta-inc.e2e-test.local", fullName: "Sipho Beta", org: "OBS 669B Beta Inc", reject: true },
];
const TABS = ["All", "Pending", "Approved", "Rejected"];

async function inventory() {
  const ta = await L.idpToken("alice");
  const g = async (p, t = ta) => { const r = await L.apiGet(t, p); return r.json != null ? r.json : { status: r.status, text: r.text }; };
  const s = {};
  s.members = L.list(await g("/api/members")).map((m) => ({ name: m.name, email: m.email, orgRole: m.orgRole }));
  const sub = await g("/api/billing/subscription");
  s.subscriptionLimits = sub && sub.limits;
  const gates = await g("/api/ai/gates?size=50");
  s.aiGates = { total: gates && gates.page ? gates.page.totalElements : L.list(gates).length };
  s.accessRequests = L.list(await g("/api/platform-admin/access-requests")).map((r) => ({ id: r.id, org: r.organizationName, email: r.email, status: r.status, createdAt: r.createdAt }));
  s.billingTenants = L.list(await g("/api/platform-admin/billing/tenants")).map((t) => ({ org: t.organizationName, status: t.subscriptionStatus, method: t.billingMethod, profile: t.verticalProfile, demo: t.isDemoTenant, createdAt: t.createdAt }));
  s.demoTenants = [...L.list(await g("/api/platform-admin/billing/tenants?billingMethod=PILOT")), ...L.list(await g("/api/platform-admin/billing/tenants?billingMethod=COMPLIMENTARY"))].map((t) => ({ org: t.organizationName, method: t.billingMethod }));
  s.caps = {};
  for (const who of ["alice", "bob", "carol"]) { const c = await g("/api/me/capabilities", await L.idpToken(who)); s.caps[who] = c && { role: c.role, isAdmin: c.isAdmin, isOwner: c.isOwner, capabilities: c.capabilities }; }
  return s;
}

async function settle(page) {
  await page.waitForLoadState("networkidle").catch(() => {});
  await page.waitForTimeout(800);
}

async function submitRequest(browser, r) {
  const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 }, deviceScaleFactor: 1, ...L.EXTRA });
  const page = await ctx.newPage();
  const t0 = Date.now() - 5000;
  await page.goto(`${L.APP}/request-access`, { waitUntil: "load" });
  await settle(page);
  await page.getByTestId("email-input").fill(r.email);
  await page.getByTestId("full-name-input").fill(r.fullName);
  await page.getByTestId("org-name-input").fill(r.org);
  await page.getByTestId("country-select").click();
  await page.getByRole("option", { name: "South Africa", exact: true }).click();
  await page.getByTestId("industry-select").click();
  await page.getByRole("option", { name: "Legal Services", exact: true }).click();
  await page.getByTestId("submit-request-btn").click();
  const otpInput = page.getByTestId("otp-input");
  const ok = await otpInput.waitFor({ timeout: 20000 }).then(() => true).catch(() => false);
  if (!ok) {
    const alert = await page.getByRole("alert").first().innerText().catch(() => null);
    const f = path.join(L.RAW, `seed-request-${r.key}-refused.png`);
    await page.screenshot({ path: f, fullPage: true });
    await ctx.close();
    return { ok: false, step: "submit", alert, file: f };
  }
  const mail = await L.mailpitOtp(r.email, { after: t0 });
  if (!mail || !mail.code) { await ctx.close(); return { ok: false, step: "otp-mail", mail }; }
  await otpInput.fill(mail.code);
  await page.getByTestId("verify-otp-btn").click();
  const done = await page.getByTestId("success-message").waitFor({ timeout: 20000 }).then(() => true).catch(() => false);
  const alert = done ? null : await page.getByRole("alert").first().innerText().catch(() => null);
  await ctx.close();
  return { ok: done, step: done ? "verified" : "verify", mailpitMessageId: mail.id, subject: mail.subject, alert };
}

async function main() {
  const prev = fs.existsSync(L.SEED_FILE) ? JSON.parse(fs.readFileSync(L.SEED_FILE, "utf8")) : null;
  const run = { at: new Date().toISOString(), phase: L.PHASE, steps: [] };
  const before = await inventory();
  console.log("inventory", JSON.stringify(before).slice(0, 2000));

  const browser = await L.chromium.launch();
  const st = await L.loginState(browser, "alice");
  const preSeed = prev && prev.preSeedEmpty ? prev.preSeedEmpty : {};
  if (before.accessRequests.length === 0 && !Object.keys(preSeed).length) {
    for (const v of ["1440-light", "1024-light"]) {
      const [w, theme] = v.split("-");
      const ctx = await L.newCtx(browser, st, { width: +w, height: w === "1024" ? 768 : 900, theme, extra: L.EXTRA });
      const page = await ctx.newPage();
      const resp = await page.goto(`${L.PA}/access-requests`, { waitUntil: "load" });
      await settle(page);
      for (const tab of TABS) {
        await page.getByRole("tab", { name: tab, exact: true }).click();
        await page.waitForTimeout(500);
        const m = await page.evaluate(probe);
        const file = path.join(L.RAW, `pa-access-preseed-${tab}--alice--${v}.png`);
        await page.screenshot({ path: file, fullPage: true });
        preSeed[`${tab}/alice/${v}`] = { file, http: resp && resp.status(), url: m.url, tabs: m.tabs, h1Main: m.h1Main, header: m.header, empties: m.empties, mainIdCount: m.mainIdCount, pa: m.pa, docHOverflowPx: m.docHOverflowPx };
      }
      await ctx.close();
    }
    run.steps.push("probed + shot the empty /platform-admin/access-requests (0 requests) on All / Pending / Approved / Rejected at alice 1440-light and 1024-light");
  }

  const results = {};
  for (const r of REQS) {
    const have = before.accessRequests.find((x) => x.email === r.email);
    if (have) { results[r.key] = { already: have }; run.steps.push(`access request ${r.key} already ${have.id} (${have.status})`); continue; }
    const res = await submitRequest(browser, r);
    results[r.key] = res;
    run.steps.push(`UI: /request-access (anonymous) › ${r.email} / "${r.fullName}" / "${r.org}" / South Africa / Legal Services › Submit › OTP from Mailpit ${res.mailpitMessageId || "-"} › Verify → ${res.ok ? "submitted" : "FAILED at " + res.step + (res.alert ? ": " + res.alert : "")}`);
  }

  // Reject Beta through the UI (only if it is PENDING)
  const mid = await inventory();
  const beta = mid.accessRequests.find((x) => x.email === REQS[1].email);
  if (beta && beta.status === "PENDING") {
    const ctx = await L.newCtx(browser, st, { width: 1440, height: 900, theme: "light", extra: L.EXTRA });
    const page = await ctx.newPage();
    await page.goto(`${L.PA}/access-requests`, { waitUntil: "load" });
    await settle(page);
    await page.getByRole("tab", { name: "Pending", exact: true }).click();
    const row = page.getByTestId(`request-row-${REQS[1].org}`);
    await row.getByRole("button", { name: "Reject", exact: true }).click();
    const dlg = page.getByRole("alertdialog");
    await dlg.waitFor({ timeout: 10000 });
    const f = path.join(L.RAW, "seed-reject-dialog--alice--1440-light.png");
    await page.screenshot({ path: f, fullPage: false });
    await dlg.getByRole("button", { name: "Reject", exact: true }).click();
    await dlg.waitFor({ state: "hidden", timeout: 20000 });
    await settle(page);
    await ctx.close();
    run.steps.push(`UI: /platform-admin/access-requests › Pending › row "${REQS[1].org}" › Reject › AlertDialog "Reject" → ${beta.id}`);
    results.rejectDialogFile = f;
  } else if (beta) run.steps.push(`beta already ${beta.status}`);
  await browser.close();

  const after = await inventory();
  const ids = {};
  for (const r of REQS) { const x = after.accessRequests.find((a) => a.email === r.email); ids[r.key] = x ? { id: x.id, org: x.org, status: x.status } : null; }
  const out = {
    note: "669B OBS seed record. dataBefore = the stack before any write; dataAfter = after the seed. ids = the two access requests (alpha PENDING, beta REJECTED). preSeedEmpty = probes of the empty /platform-admin/access-requests tabs taken before the writes. results = per-request outcome (Mailpit message ids). Never Approved anything.",
    dataBefore: prev ? prev.dataBefore : before,
    dataAfter: after,
    ids: prev && prev.ids && prev.ids.alpha ? prev.ids : ids,
    preSeedEmpty: preSeed,
    results: prev && prev.results ? prev.results : results,
    writes: prev ? prev.writes : run.steps.filter((x) => x.startsWith("UI:")),
    runs: [...(prev ? prev.runs : []), run],
  };
  fs.writeFileSync(L.SEED_FILE, JSON.stringify(out));
  console.log("seed", JSON.stringify({ ids: out.ids, steps: run.steps, results }));
}
main().catch((e) => { console.error("SEED FAIL", e); process.exit(1); });
