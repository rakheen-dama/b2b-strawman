// Client-portal deadline countdown fix — OBS through the UI (real Chromium via the Playwright
// library; never the founder's Playwright-MCP Chrome). E2E mock-auth stack: firm app :3001,
// backend :8081, IDP :8090, Mailpit :8026; the portal is this worktree's production build on :3002
// (NEXT_PUBLIC_PORTAL_API_URL=http://localhost:8081).
//
// Firm side (Alice, firm UI): on Website Redesign (Acme) → Court dates tab, schedule HEARINGs on
// SA-today +0, +1, +7, +8, +14, +15 and start a GENERAL_3Y prescription tracker due +21 (cause of
// action = due − 3 years). They sync to the portal's Diary dates (DeadlinePortalSyncService).
// Portal side (Acme contact): sign in through /login → Mailpit magic link → /auth/exchange, then
// open /deadlines in browser zones Johannesburg, UTC and New York, at the real time and with the
// browser clock pinned to 01:00 SAST today (the window where UTC / New York browsers' date is
// still yesterday). Each row's data-tone must equal the tone of daysBetween(SA-today, dueDate).
// The /portal/deadlines response is captured to confirm dueDate is a date-only yyyy-MM-dd.
// finally: cancel the court dates and interrupt the tracker through the firm UI, then confirm the
// client's deadline ids are back to the originals. No SQL; the only non-UI calls are read-only
// (GET /api/matters for the matter id, Mailpit search, GET /portal/deadlines for the restore check).
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
const CLEANUP = process.argv[2] === "cleanup" ? JSON.parse(fs.readFileSync(path.resolve(process.argv[3]), "utf8")) : null;
const RUN = CLEANUP ? CLEANUP.run : Date.now().toString(36);
const esc = (t) => t.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
const courtRow = (page, name) =>
  page.getByTestId("court-date-list").locator("tr").filter({ has: page.locator("td", { hasText: new RegExp(`^${esc(name)}$`) }) });
const COURT_OFFSETS = [0, 1, 7, 8, 14, 15];
const TRACKER_OFFSET = 21;
const ZONES = ["Africa/Johannesburg", "UTC", "America/New_York"];

const dateIn = (zone, at) =>
  new Intl.DateTimeFormat("en-CA", { timeZone: zone, year: "numeric", month: "2-digit", day: "2-digit" }).format(at);
const addDays = (iso, n) => {
  const [y, m, d] = iso.split("-").map(Number);
  return new Date(Date.UTC(y, m - 1, d + n)).toISOString().slice(0, 10);
};
const minusYears = (iso, n) => `${Number(iso.slice(0, 4)) - n}${iso.slice(4)}`;
const daysBetween = (a, b) => {
  const [ay, am, ad] = a.split("-").map(Number);
  const [by, bm, bd] = b.split("-").map(Number);
  return Math.round((Date.UTC(by, bm - 1, bd) - Date.UTC(ay, am - 1, ad)) / 86400000);
};
const tone = (n) => (n < 0 ? "red-solid" : n <= 7 ? "red" : n <= 14 ? "amber" : "grey");

const out = { startedAt: new Date().toISOString(), gitHead: execSync("git rev-parse HEAD", { cwd: W }).toString().trim(), run: RUN, steps: [], console: [], checks: [], readings: [], restore: {} };
const check = (name, ok, detail) => out.checks.push({ name, ok: !!ok, detail });
const step = (s) => out.steps.push(`${new Date().toISOString()} ${s}`);
const save = () => fs.writeFileSync(path.join(OUT, CLEANUP ? `obs-cleanup-${RUN}.json` : "obs.json"), JSON.stringify(out, null, 2));

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
async function portalDeadlines(jwt) {
  const r = await fetch(`${API}/portal/deadlines`, { headers: { Authorization: `Bearer ${jwt}` } });
  if (!r.ok) throw new Error(`GET /portal/deadlines -> ${r.status}`);
  return r.json();
}

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
  const ctx = await browser.newContext({ viewport: { width: 1280, height: 900 }, timezoneId: "Africa/Johannesburg" });
  const page = await ctx.newPage();
  watch(page, "portal-login");
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
  const state = await ctx.storageState();
  const jwt = await page.evaluate(() => localStorage.getItem("portal_jwt"));
  await ctx.close();
  return { state, jwt };
}

async function readDeadlines(browser, state, zone, pinned) {
  const ctx = await browser.newContext({ viewport: { width: 1280, height: 1400 }, timezoneId: zone, storageState: state });
  if (pinned) await ctx.clock.setFixedTime(new Date(pinned));
  const page = await ctx.newPage();
  const tag = `${zone.split("/").pop().toLowerCase()}-${pinned ? "0100sast" : "now"}`;
  watch(page, tag);
  const resP = page.waitForResponse((r) => new URL(r.url()).pathname === "/portal/deadlines" && r.request().method() === "GET");
  await page.goto(`${PORTAL}/deadlines`, { waitUntil: "load" });
  const res = await resP;
  const api = await res.json();
  await page.getByRole("heading", { level: 1, name: "Diary dates" }).waitFor();
  await page.locator('[data-testid^="deadline-row-"]').first().waitFor({ timeout: 20000 });
  const browserNow = await page.evaluate(() => new Date().toString());
  const rows = await page.locator('[data-testid^="deadline-row-"]').evaluateAll((els) =>
    els.map((e) => ({ testid: e.getAttribute("data-testid"), tone: e.getAttribute("data-tone"), text: e.innerText.replace(/\s+/g, " ").trim() })),
  );
  await page.screenshot({ path: path.join(OUT, `obs-${tag}.png`), fullPage: true });
  await ctx.close();
  return { tag, zone, pinned, browserNow, requestUrl: res.url(), api, rows };
}

(async () => {
  const browser = await chromium.launch();
  const created = { courtDates: [], tracker: null };
  let firm = null;
  let jwt = null;
  let originalPortalIds = null;
  try {
    const tok = await firmToken();
    const mr = await fetch(`${API}/api/matters`, { headers: { Authorization: `Bearer ${tok}` } });
    const mb = await mr.json();
    const matter = (Array.isArray(mb) ? mb : mb.content || []).find((m) => m.name === MATTER);
    out.matter = { id: matter.id, name: matter.name };
    const URL_TAB = `${APP}/org/${ORG}/matters/${matter.id}?tab=court-dates`;

    const login = await portalLogin(browser);
    jwt = login.jwt;
    originalPortalIds = (await portalDeadlines(jwt)).map((d) => `${d.sourceEntity}-${d.id}`).sort();
    out.restore.originalPortalIds = originalPortalIds;

    firm = await firmLogin(browser);
    await firm.goto(URL_TAB, { waitUntil: "load" });
    await firm.getByTestId("project-court-dates-tab").waitFor({ timeout: 30000 });
    if (CLEANUP) {
      // Restore a previous run's leftovers: its originals are the restore target.
      Object.assign(created, CLEANUP.created);
      originalPortalIds = CLEANUP.restore.originalPortalIds;
      out.restore.originalPortalIds = originalPortalIds;
      out.mode = `cleanup of run ${RUN}`;
      return;
    }
    const today = dateIn("Africa/Johannesburg", new Date());
    out.todaySA = today;
    for (const n of COURT_OFFSETS) {
      const date = addDays(today, n);
      const courtName = `OBS-PDL ${RUN} day${n}x`;
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
      try {
        await dialog.waitFor({ state: "hidden", timeout: 15000 });
      } catch (e) {
        out.dialogText = await dialog.innerText();
        await firm.screenshot({ path: path.join(OUT, "obs-firm-dialog-stuck.png") });
        throw e;
      }
      created.courtDates.push({ offset: n, date, courtName, label: `Hearing — ${courtName}` });
      step(`firm UI: scheduled HEARING "${courtName}" on ${date} (+${n})`);
      await courtRow(firm, courtName).waitFor({ timeout: 15000 });
    }
    const due = addDays(today, TRACKER_OFFSET);
    const cause = minusYears(due, 3);
    const list = firm.getByTestId("prescription-tracker-list");
    await list.getByRole("button", { name: "New prescription tracker" }).click();
    const pd = firm.getByTestId("create-prescription-dialog");
    await pd.waitFor();
    await pd.getByLabel("Cause of Action Date").fill(cause);
    await pd.getByLabel("Prescription Type").selectOption("GENERAL_3Y");
    await pd.getByRole("button", { name: "Create Tracker" }).click();
    await pd.waitFor({ state: "hidden", timeout: 15000 });
    created.tracker = { offset: TRACKER_OFFSET, date: due, cause };
    step(`firm UI: started GENERAL_3Y tracker, cause ${cause} → due ${due} (+${TRACKER_OFFSET})`);
    out.created = created;

    // Wait for the portal read model to carry every new row (read-only API poll).
    const wanted = created.courtDates.length + 1;
    let fresh = [];
    for (let i = 0; i < 30; i++) {
      fresh = (await portalDeadlines(jwt)).filter((d) => !originalPortalIds.includes(`${d.sourceEntity}-${d.id}`));
      if (fresh.length >= wanted) break;
      await firm.waitForTimeout(1000);
    }
    check(`portal read model has the ${wanted} new rows`, fresh.length === wanted, fresh);

    const pinned = `${today}T01:00:00+02:00`;
    for (const zone of ZONES) for (const p of [null, pinned]) out.readings.push(await readDeadlines(browser, login.state, zone, p));

    for (const r of out.readings) {
      const instant = r.pinned ? new Date(r.pinned) : new Date();
      const saToday = dateIn("Africa/Johannesburg", instant);
      const localToday = dateIn(r.zone, instant);
      for (const d of r.api) check(`${r.tag}: API dueDate ${d.dueDate} is date-only yyyy-MM-dd`, /^\d{4}-\d{2}-\d{2}$/.test(d.dueDate), d.dueDate);
      const items = [
        ...created.courtDates.map((c) => ({ ...c, row: r.api.find((d) => d.sourceEntity === "COURT_DATE" && d.label === c.label) })),
        { ...created.tracker, label: "Prescription deadline", row: r.api.find((d) => d.sourceEntity === "PRESCRIPTION_TRACKER" && d.dueDate === due && !originalPortalIds.includes(`PRESCRIPTION_TRACKER-${d.id}`)) },
      ];
      r.results = items.map((it) => {
        const ui = it.row && r.rows.find((x) => x.testid === `deadline-row-${it.row.sourceEntity}-${it.row.id}`);
        const n = daysBetween(saToday, it.date);
        const mainN = daysBetween(localToday, it.date); // main: local-midnight arithmetic in the browser zone
        return { label: it.label, dueDate: it.date, apiDueDate: it.row && it.row.dueDate, status: it.row && it.row.status, saToday, browserLocalDate: localToday, days: n, expectedTone: tone(n), observedTone: ui && ui.tone, mainWouldShow: tone(mainN), rowText: ui && ui.text };
      });
      for (const x of r.results)
        check(`${r.tag}: ${x.label} (${x.dueDate}, ${x.days}d) reads ${x.expectedTone}`, x.observedTone === x.expectedTone && x.apiDueDate === x.dueDate, x);
    }
  } catch (e) {
    out.error = String(e && e.stack ? e.stack : e);
  } finally {
    try {
      if (firm) {
        for (const c of created.courtDates) {
          const row = courtRow(firm, c.courtName);
          if (CLEANUP && (await row.getByRole("button", { name: "Actions" }).count()) === 0) {
            step(`cleanup: "${c.courtName}" already cancelled (no actions)`);
            continue;
          }
          await row.getByRole("button", { name: "Actions" }).click();
          await firm.getByRole("menuitem", { name: "Cancel" }).click();
          const dlg = firm.getByTestId("cancel-court-date-dialog");
          await dlg.getByLabel("Reason").fill(`OBS ${RUN} cleanup`);
          await dlg.getByRole("button", { name: "Cancel Court Date" }).click();
          await dlg.waitFor({ state: "hidden", timeout: 15000 });
          step(`firm UI: cancelled "${c.courtName}"`);
        }
        if (created.tracker) {
          const row = firm.getByTestId("prescription-tracker-list").locator('[data-testid^="prescription-tracker-row-"]', { hasText: created.tracker.cause });
          await row.getByRole("button", { name: "Interrupt" }).click();
          const dlg = firm.getByTestId("interrupt-dialog");
          await dlg.getByLabel("Interruption Date").fill(dateIn("Africa/Johannesburg", new Date()));
          await dlg.getByLabel("Reason").fill(`OBS ${RUN} cleanup`);
          await dlg.getByRole("button", { name: "Record Interruption" }).click();
          await dlg.waitFor({ state: "hidden", timeout: 15000 });
          step(`firm UI: interrupted tracker (cause ${created.tracker.cause})`);
        }
      }
      if (jwt && originalPortalIds) {
        let after = [];
        for (let i = 0; i < 20; i++) {
          after = (await portalDeadlines(jwt)).map((d) => `${d.sourceEntity}-${d.id}`).sort();
          if (JSON.stringify(after) === JSON.stringify(originalPortalIds)) break;
          await new Promise((r) => setTimeout(r, 1000));
        }
        out.restore.afterPortalIds = after;
        check("restore: the client's deadline ids are back to the originals", JSON.stringify(after) === JSON.stringify(originalPortalIds), { before: originalPortalIds, after });
      } else check("restore: originals captured", false, "sign-in failed before originals were read");
    } catch (e) {
      out.restoreError = String(e && e.stack ? e.stack : e);
    }
    out.finishedAt = new Date().toISOString();
    out.allChecksPass = !out.error && !out.restoreError && out.checks.length > 0 && out.checks.every((c) => c.ok);
    save();
    await browser.close();
    process.exitCode = out.allChecksPass ? 0 : 1;
    console.log(JSON.stringify({ allChecksPass: out.allChecksPass, error: out.error, restoreError: out.restoreError, checks: out.checks.length, failed: out.checks.filter((c) => !c.ok).map((c) => c.name) }, null, 2));
  }
})();
