// D-127 portal Diary dates status filters — OBS through the UI (real Chromium via the Playwright
// library; never the founder's Playwright-MCP Chrome). E2E mock-auth stack: firm app :3001, backend
// :8081, IDP :8090, Mailpit :8026; the portal is this worktree's production build on :3002
// (NEXT_PUBLIC_PORTAL_API_URL=http://localhost:8081). Adapted from PR #1897's OBS script.
//
// Fixtures (firm UI, Alice) on Website Redesign (Acme) → Court dates tab, each journalled as it is
// created:
//   past-open      HEARING on SA-today −3, left unresolved                 → portal OVERDUE
//   past-heard     HEARING on SA-today −5, Record Outcome (→ HEARD)        → portal COMPLETED
//   past-cancelled HEARING on SA-today −4, then Cancel                     → portal row DELETED (B4)
//   future         HEARING on SA-today +2                                  → portal DUE_SOON (control)
// Readings (portal, Africa/Johannesburg): Diary dates with the Status filter on Overdue, Completed,
// Cancelled, Due soon and All statuses, picked in the UI. Each records the /portal/deadlines request
// URL (must carry status only, no from/to), the API rows and the rendered rows.
// `mainEquivalent` is a read-only GET with from=SA-today (what main applied when no from was sent),
// to show the delta on this head without running main.
// Restore (finally): past-open and future are cancelled through the UI. A HEARD court date has no
// actions in the firm UI (court-date-list-view.tsx canTransition), so past-heard cannot be undone
// through the UI; it is recorded as residue and the stack is wiped (e2e-down) after OBS.
// No SQL; the only non-UI calls are read-only (GET /api/matters, Mailpit, GET /portal/deadlines).
const path = require("path");
const fs = require("fs");
const { execSync } = require("child_process");

const W = path.resolve(__dirname, "../../../..");
const { chromium } = require(path.join(W, "frontend/node_modules/@playwright/test"));
const APP = "http://localhost:3001";
const API = "http://localhost:8081";
const IDP = "http://localhost:8090";
const PORTAL = "http://localhost:3002";
const MAILPIT = "http://localhost:8026";
const ORG = "e2e-test-org";
const EMAIL = "contact@acme.example.com";
const MATTER = "Website Redesign";
const OUT = path.resolve(__dirname, "..");
const RUN = Date.now().toString(36);
const FIXTURES = [
  { key: "past-open", offset: -3, action: null },
  { key: "past-heard", offset: -5, action: "outcome" },
  { key: "past-cancelled", offset: -4, action: "cancel" },
  { key: "future", offset: 2, action: null },
];
const FILTERS = [
  { value: "OVERDUE", label: "Overdue" },
  { value: "COMPLETED", label: "Completed" },
  { value: "CANCELLED", label: "Cancelled" },
  { value: "DUE_SOON", label: "Due soon" },
  { value: "ALL", label: "All statuses" },
];

const esc = (t) => t.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
const courtRow = (page, name) =>
  page.getByTestId("court-date-list").locator("tr").filter({ has: page.locator("td", { hasText: new RegExp(`^${esc(name)}$`) }) });
const dateIn = (zone, at) =>
  new Intl.DateTimeFormat("en-CA", { timeZone: zone, year: "numeric", month: "2-digit", day: "2-digit" }).format(at);
const addDays = (iso, n) => {
  const [y, m, d] = iso.split("-").map(Number);
  return new Date(Date.UTC(y, m - 1, d + n)).toISOString().slice(0, 10);
};

const out = { startedAt: new Date().toISOString(), gitHead: execSync("git rev-parse HEAD", { cwd: W }).toString().trim(), run: RUN, journal: [], steps: [], console: [], checks: [], readings: [], mainEquivalent: {}, restore: {}, deferred: [] };
const check = (name, ok, detail) => out.checks.push({ name, ok: !!ok, detail });
const step = (s) => out.steps.push(`${new Date().toISOString()} ${s}`);
const save = () => fs.writeFileSync(path.join(OUT, "obs.json"), JSON.stringify(out, null, 2));

async function firmToken() {
  const r = await fetch(`${IDP}/token`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ userId: "user_e2e_alice", orgId: "org_e2e_test", orgSlug: ORG, orgRole: "org:owner" }),
  });
  return (await r.json()).access_token;
}
const watch = (page, tag) => {
  page.on("console", (m) => m.type() === "error" && out.console.push(`[${tag}] ${m.text().slice(0, 300)}`));
  page.on("pageerror", (e) => out.console.push(`[${tag}] ${String(e).slice(0, 300)}`));
};
const mailIds = async () => {
  const r = await fetch(`${MAILPIT}/api/v1/search?query=${encodeURIComponent(`to:${EMAIL}`)}`);
  return ((await r.json()).messages || []).map((m) => ({ id: m.ID, subject: m.Subject }));
};
async function portalDeadlines(jwt, qs = "") {
  const r = await fetch(`${API}/portal/deadlines${qs}`, { headers: { Authorization: `Bearer ${jwt}` } });
  if (!r.ok) throw new Error(`GET /portal/deadlines${qs} -> ${r.status}`);
  return r.json();
}
const keyOf = (d) => `${d.sourceEntity}-${d.id}`;

async function firmLogin(browser) {
  const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 }, timezoneId: "Africa/Johannesburg" });
  const page = await ctx.newPage();
  watch(page, "firm");
  await page.goto(`${APP}/mock-login`, { waitUntil: "load" });
  await page.getByRole("button", { name: "Sign In", exact: true }).click();
  await page.waitForURL(/\/org\/.*\/dashboard/, { timeout: 60000 });
  return page;
}

async function portalLogin(browser) {
  const ctx = await browser.newContext({ viewport: { width: 1280, height: 1400 }, timezoneId: "Africa/Johannesburg" });
  const page = await ctx.newPage();
  watch(page, "portal");
  const before = (await mailIds()).map((m) => m.id);
  await page.goto(`${PORTAL}/login`, { waitUntil: "load" });
  await page.getByLabel("Email address").fill(EMAIL);
  const resP = page.waitForResponse((r) => r.request().method() === "POST" && r.url().endsWith("/portal/auth/request-link"));
  await page.getByRole("button", { name: "Send Magic Link" }).click();
  const res = await resP;
  if (res.status() !== 200) throw new Error(`request-link -> ${res.status()} (429 = per-email cap)`);
  await page.getByText("Check your email.").waitFor({ timeout: 15000 });
  let mail = null;
  for (let i = 0; i < 30 && !mail; i++) {
    mail = (await mailIds()).find((m) => !before.includes(m.id) && m.subject.startsWith("Your portal access link from"));
    if (!mail) await page.waitForTimeout(1000);
  }
  if (!mail) throw new Error("no magic-link email in Mailpit");
  const detail = await (await fetch(`${MAILPIT}/api/v1/message/${mail.id}`)).json();
  const href = (detail.HTML.match(/href="([^"]*\/auth\/exchange\?[^"]+)"/) || [])[1].replace(/&amp;/g, "&");
  const u = new URL(href);
  await page.goto(`${PORTAL}${u.pathname}${u.search}`);
  await page.getByRole("heading", { name: "Your matters" }).waitFor({ timeout: 30000 });
  step(`portal sign-in via /login + Mailpit message ${mail.id} → /matters`);
  const jwt = await page.evaluate(() => localStorage.getItem("portal_jwt"));
  return { page, jwt };
}

const isDeadlinesGet = (r) => new URL(r.url()).pathname === "/portal/deadlines" && r.request().method() === "GET";

// Reads the rendered list after a /portal/deadlines response: rows, or the empty state.
async function readList(page, res, filter) {
  const api = await res.json();
  if (api.length > 0) await page.locator('[data-testid^="deadline-row-"]').first().waitFor({ timeout: 20000 });
  else await page.getByTestId("deadline-list-empty").waitFor({ timeout: 20000 });
  const rows = await page.locator('[data-testid^="deadline-row-"]').evaluateAll((els) =>
    els.map((e) => ({ testid: e.getAttribute("data-testid"), text: e.innerText.replace(/\s+/g, " ").trim() })),
  );
  const empty = await page.getByTestId("deadline-list-empty").isVisible().catch(() => false);
  const tag = `portal-deadlines-${filter.value.toLowerCase()}`;
  await page.screenshot({ path: path.join(OUT, `obs-${tag}.png`), fullPage: true });
  return { tag, filter: filter.value, requestUrl: res.url(), status: res.status(), api, rows, empty };
}

async function createHearing(firm, matterId, date, courtName) {
  await firm.getByTestId("create-court-date-trigger").click();
  const dialog = firm.getByTestId("create-court-date-dialog");
  await dialog.waitFor();
  const matterSelect = dialog.getByLabel("Matter");
  await matterSelect.locator("option", { hasText: MATTER }).first().waitFor({ state: "attached", timeout: 15000 });
  const opt = await matterSelect.locator("option").evaluateAll((os, name) => os.map((o) => o.textContent).find((t) => t.includes(name)), MATTER);
  await matterSelect.selectOption({ label: opt });
  await dialog.getByLabel("Type").selectOption("HEARING");
  await dialog.getByLabel("Date", { exact: true }).fill(date);
  await dialog.getByLabel("Court Name").fill(courtName);
  await dialog.getByRole("button", { name: "Schedule Court Date" }).click();
  await dialog.waitFor({ state: "hidden", timeout: 15000 });
  await courtRow(firm, courtName).waitFor({ timeout: 15000 });
}

async function openCourtTab(firm, matterId) {
  await firm.goto(`${APP}/org/${ORG}/matters/${matterId}?tab=court-dates`, { waitUntil: "load" });
  await firm.getByTestId("project-court-dates-tab").waitFor({ timeout: 30000 });
}

async function cancelHearing(firm, courtName) {
  const row = courtRow(firm, courtName);
  await row.getByRole("button", { name: "Actions" }).click();
  await firm.getByRole("menuitem", { name: "Cancel" }).click();
  const dlg = firm.getByTestId("cancel-court-date-dialog");
  await dlg.getByLabel("Reason").fill(`OBS ${RUN}`);
  await dlg.getByRole("button", { name: "Cancel Court Date" }).click();
  await dlg.waitFor({ state: "hidden", timeout: 15000 });
}

async function recordOutcome(firm, courtName) {
  const row = courtRow(firm, courtName);
  await row.getByRole("button", { name: "Actions" }).click();
  await firm.getByRole("menuitem", { name: "Record Outcome" }).click();
  const dlg = firm.getByTestId("outcome-dialog");
  await dlg.getByLabel("Outcome").fill(`OBS ${RUN}: matter heard, judgment reserved`);
  await dlg.getByRole("button", { name: "Record Outcome" }).click();
  await dlg.waitFor({ state: "hidden", timeout: 15000 });
}

(async () => {
  const browser = await chromium.launch();
  let firm = null;
  let jwt = null;
  let matter = null;
  const originals = {};
  try {
    const tok = await firmToken();
    const mb = await (await fetch(`${API}/api/matters`, { headers: { Authorization: `Bearer ${tok}` } })).json();
    matter = (Array.isArray(mb) ? mb : mb.content || []).find((m) => m.name === MATTER);
    out.matter = { id: matter.id, name: matter.name };
    const today = dateIn("Africa/Johannesburg", new Date());
    out.todaySA = today;

    const login = await portalLogin(browser);
    jwt = login.jwt;
    const portal = login.page;
    watch(portal, "portal-deadlines");
    // Originals per filter (read-only), so the restore check and the "new rows" checks are exact.
    for (const f of FILTERS) originals[f.value] = (await portalDeadlines(jwt, f.value === "ALL" ? "" : `?status=${f.value}`)).map(keyOf).sort();
    out.restore.originals = originals;
    save();

    firm = await firmLogin(browser);
    await openCourtTab(firm, matter.id);
    for (const fx of FIXTURES) {
      const date = addDays(today, fx.offset);
      const courtName = `OBS-D127 ${RUN} ${fx.key}`;
      const entry = { ...fx, date, courtName, label: `Hearing — ${courtName}`, state: "creating" };
      out.journal.push(entry);
      save();
      await createHearing(firm, matter.id, date, courtName);
      entry.state = "created";
      step(`firm UI: scheduled HEARING "${courtName}" on ${date} (${fx.offset})`);
      if (fx.action === "outcome") {
        await recordOutcome(firm, courtName);
        entry.state = "heard";
        step(`firm UI: recorded outcome on "${courtName}" (HEARD)`);
      } else if (fx.action === "cancel") {
        await cancelHearing(firm, courtName);
        entry.state = "cancelled";
        step(`firm UI: cancelled "${courtName}"`);
      }
      save();
    }
    const fx = Object.fromEntries(out.journal.map((j) => [j.key, j]));

    // Wait for the AFTER_COMMIT portal projection (read-only polling with explicit from).
    let projected = [];
    for (let i = 0; i < 30; i++) {
      projected = await portalDeadlines(jwt, `?from=${addDays(today, -10)}`);
      const has = (k) => projected.some((d) => d.label === fx[k].label);
      if (has("past-open") && has("past-heard") && has("future") && !has("past-cancelled")) break;
      await new Promise((r) => setTimeout(r, 1000));
    }
    out.projected = projected.filter((d) => d.label.includes(RUN));
    save();

    // main's effective default for a no-from request was from=SA-today (read-only, for the delta).
    for (const f of ["OVERDUE", "COMPLETED"]) {
      const rows = await portalDeadlines(jwt, `?status=${f}&from=${today}`);
      out.mainEquivalent[f] = { request: `/portal/deadlines?status=${f}&from=${today}`, fixtureRows: rows.filter((d) => d.label.includes(RUN)).map((d) => d.label) };
    }

    // Portal UI: Diary dates, pick each Status filter.
    const firstP = portal.waitForResponse(isDeadlinesGet, { timeout: 30000 });
    await portal.goto(`${PORTAL}/deadlines`, { waitUntil: "load" });
    await portal.getByRole("heading", { level: 1, name: "Diary dates" }).waitFor();
    out.initialRequest = (await firstP).url();
    const select = portal.locator("#deadline-status-filter");
    let current = "ALL";
    for (const f of FILTERS) {
      if (f.value === current) {
        // "All statuses" is the initial value: pick another, then pick it back to fire a fresh request.
        await select.selectOption("UPCOMING");
        await portal.waitForResponse(isDeadlinesGet, { timeout: 30000 });
      }
      const resP = portal.waitForResponse(isDeadlinesGet, { timeout: 30000 });
      await select.selectOption({ label: f.label });
      const res = await resP;
      current = f.value;
      out.readings.push(await readList(portal, res, f));
      save();
    }

    const R = Object.fromEntries(out.readings.map((r) => [r.filter, r]));
    const apiHas = (r, k) => r.api.find((d) => d.sourceEntity === "COURT_DATE" && d.label === fx[k].label);
    const uiHas = (r, k) => {
      const row = apiHas(r, k);
      return !!row && r.rows.some((x) => x.testid === `deadline-row-COURT_DATE-${row.id}` && x.text.includes(fx[k].courtName));
    };
    for (const r of out.readings) {
      const q = new URL(r.requestUrl).searchParams;
      const want = r.filter === "ALL" ? null : r.filter;
      check(`${r.tag}: request carries status=${want ?? "(none)"} and no from/to`, r.status === 200 && !q.has("from") && !q.has("to") && q.get("status") === want, r.requestUrl);
      if (want) check(`${r.tag}: every row returned reads ${want}`, r.api.every((d) => d.status === want), r.api.map((d) => [d.label, d.dueDate, d.status]));
      check(`${r.tag}: rendered rows = API rows (${r.api.length})`, r.rows.length === r.api.length && (r.api.length > 0 || r.empty), { rows: r.rows.length, api: r.api.length, empty: r.empty });
    }
    check(`OVERDUE lists the unresolved past hearing (${fx["past-open"].date}) — main's from=today excluded it`, uiHas(R.OVERDUE, "past-open") && !out.mainEquivalent.OVERDUE.fixtureRows.includes(fx["past-open"].label), { row: apiHas(R.OVERDUE, "past-open"), mainEquivalent: out.mainEquivalent.OVERDUE });
    check("OVERDUE does not list the heard, cancelled or future hearings", !apiHas(R.OVERDUE, "past-heard") && !apiHas(R.OVERDUE, "past-cancelled") && !apiHas(R.OVERDUE, "future"), R.OVERDUE.api.map((d) => d.label));
    check(`COMPLETED lists the heard past hearing (${fx["past-heard"].date}) — main's from=today excluded it`, uiHas(R.COMPLETED, "past-heard") && !out.mainEquivalent.COMPLETED.fixtureRows.includes(fx["past-heard"].label), { row: apiHas(R.COMPLETED, "past-heard"), mainEquivalent: out.mainEquivalent.COMPLETED });
    check("COMPLETED does not list the open, cancelled or future hearings", !apiHas(R.COMPLETED, "past-open") && !apiHas(R.COMPLETED, "past-cancelled") && !apiHas(R.COMPLETED, "future"), R.COMPLETED.api.map((d) => d.label));
    check("CANCELLED: the cancelled hearing's portal row is deleted (B4), so it is not listed", !apiHas(R.CANCELLED, "past-cancelled") && !projected.some((d) => d.label === fx["past-cancelled"].label), R.CANCELLED.api.map((d) => d.label));
    check(`DUE_SOON lists the future hearing (${fx.future.date}) and no past one`, uiHas(R.DUE_SOON, "future") && !apiHas(R.DUE_SOON, "past-open") && !apiHas(R.DUE_SOON, "past-heard"), R.DUE_SOON.api.map((d) => d.label));
    check("All statuses keeps today..today+60: future listed, past hearings not listed", uiHas(R.ALL, "future") && !apiHas(R.ALL, "past-open") && !apiHas(R.ALL, "past-heard") && R.ALL.api.every((d) => d.dueDate >= today && d.dueDate <= addDays(today, 60)), R.ALL.api.map((d) => [d.label, d.dueDate]));
    check("initial page load requests no status and no from/to", (() => { const q = new URL(out.initialRequest).searchParams; return !q.has("status") && !q.has("from") && !q.has("to"); })(), out.initialRequest);
    check("no console errors on the portal", !out.console.some((c) => c.startsWith("[portal")), out.console.filter((c) => c.startsWith("[portal")));
    if (R.CANCELLED.api.length === 0)
      out.deferred.push({ item: "CANCELLED filter listing a past cancelled row", reason: "no firm UI action produces a CANCELLED portal row: a cancelled court date (and the original row of a postpone) DELETES its portal row (DeadlinePortalSyncService.onCourtDateCancelled, B4), prescription interrupt deletes too, and filing-schedule sync is dormant. The rule is covered by PortalDeadlineStatusFilterLookbackIntegrationTest [B3]/[D3] on seeded rows." });
  } catch (e) {
    out.error = String(e && e.stack ? e.stack : e);
  } finally {
    // Each fixture is restored independently: one failure does not strand the rest.
    for (const c of out.journal) {
      if (c.state === "cancelled" || c.state === "heard") continue;
      try {
        if (!firm) throw new Error("no firm session");
        await openCourtTab(firm, matter.id);
        const row = courtRow(firm, c.courtName);
        await row.first().waitFor({ timeout: 15000 }).catch(() => {});
        if ((await row.count()) === 0) {
          c.state = c.state === "creating" ? "never-created" : "missing";
          continue;
        }
        await cancelHearing(firm, c.courtName);
        c.state = "cancelled";
        step(`restore: cancelled "${c.courtName}"`);
      } catch (e) {
        c.cleanupError = String(e).slice(0, 300);
        out.restoreError = `${out.restoreError || ""} ${c.courtName}: ${c.cleanupError}`.trim();
      }
      save();
    }
    try {
      const heard = out.journal.filter((c) => c.state === "heard");
      out.restore.residue = heard.map((c) => ({ courtName: c.courtName, date: c.date, why: "HEARD court dates have no firm-UI actions (canTransition → all false); stack wiped with e2e-down after OBS" }));
      if (jwt && originals.ALL) {
        let after = {};
        for (let i = 0; i < 20; i++) {
          after = {};
          for (const f of FILTERS) after[f.value] = (await portalDeadlines(jwt, f.value === "ALL" ? "" : `?status=${f.value}`)).map(keyOf);
          const heardKeys = (after.COMPLETED || []).filter((k) => !originals.COMPLETED.includes(k));
          if (FILTERS.every((f) => JSON.stringify(after[f.value].filter((k) => !heardKeys.includes(k)).sort()) === JSON.stringify(originals[f.value]))) break;
          await new Promise((r) => setTimeout(r, 1000));
        }
        const heardKeys = after.COMPLETED.filter((k) => !originals.COMPLETED.includes(k));
        out.restore.after = after;
        check(
          `restore: every filter is back to its originals, except the ${heard.length} HEARD residue row(s) in COMPLETED`,
          heardKeys.length === heard.length && FILTERS.every((f) => JSON.stringify(after[f.value].filter((k) => !heardKeys.includes(k)).sort()) === JSON.stringify(originals[f.value])),
          { originals, after, heardKeys },
        );
      } else check("restore: originals captured", false, "sign-in failed before originals were read");
    } catch (e) {
      out.restoreError = `${out.restoreError || ""} ${String(e).slice(0, 300)}`.trim();
    }
    out.finishedAt = new Date().toISOString();
    out.allChecksPass = !out.error && !out.restoreError && out.checks.length > 0 && out.checks.every((c) => c.ok);
    save();
    await browser.close();
    process.exitCode = out.allChecksPass ? 0 : 1;
    console.log(JSON.stringify({ allChecksPass: out.allChecksPass, error: out.error, restoreError: out.restoreError, checks: out.checks.length, failed: out.checks.filter((c) => !c.ok).map((c) => c.name), deferred: out.deferred }, null, 2));
  }
})();
