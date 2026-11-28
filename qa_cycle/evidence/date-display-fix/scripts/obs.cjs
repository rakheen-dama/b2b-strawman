// Date-only display + portal deadline window fix — OBS through the UI (real Chromium via the
// Playwright library; never the founder's Playwright-MCP Chrome). E2E mock-auth stack: firm app
// :3001, backend :8081, IDP :8090, Mailpit :8026; the portal is this worktree's production build on
// :3002 (NEXT_PUBLIC_PORTAL_API_URL=http://localhost:8081). Adapted from PR #1895's OBS script.
//
// Fixtures (firm UI, Alice): on Website Redesign (Acme) → Court dates tab, schedule HEARINGs on
// SA-today -1, +0, +1 and +2 (today is Sat 28 Nov 2026 → Sun 29 and Mon 30 Nov, so the portal shows two
// "Week of" groups). Every fixture is journalled as it is created and cancelled in `finally`, each
// in its own try (one failure does not strand the rest).
//
// Readings, in browser zones Africa/Johannesburg, America/New_York and Asia/Dubai:
//  - firm fee-note detail (an existing SENT fee note): "Issued: …" / "Due: …" must print the API's
//    yyyy-MM-dd issueDate / dueDate, the same string in every zone;
//  - portal /invoices: "Issued …" / "Due …" for the same fee notes;
//  - portal /deadlines: each fixture row's date and its "Week of" header (the Monday).
// Plus pinned clocks (see the note at the readings): New York at 01:00 SAST and Dubai at 22:30 SAST; the
// /portal/deadlines request carries no from/to and today's hearing is listed.
// `mainWouldShow` evaluates main's expression (toLocaleDateString without timeZone) in the same
// browser zone, to show the delta. No SQL; the only non-UI calls are read-only (GET /api/matters,
// GET /api/invoices, Mailpit search, GET /portal/deadlines for the restore check).
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
// `node obs.cjs cleanup <obs-attempt.json>` cancels a previous run's journalled leftovers only.
const CLEANUP = process.argv[2] === "cleanup" ? JSON.parse(fs.readFileSync(path.resolve(process.argv[3]), "utf8")) : null;
const RUN = CLEANUP ? CLEANUP.run : Date.now().toString(36);
const ZONES = ["Africa/Johannesburg", "America/New_York", "Asia/Dubai"];
const COURT_OFFSETS = [-1, 0, 1, 2];

const esc = (t) => t.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
const courtRow = (page, name) =>
  page.getByTestId("court-date-list").locator("tr").filter({ has: page.locator("td", { hasText: new RegExp(`^${esc(name)}$`) }) });
const dateIn = (zone, at) =>
  new Intl.DateTimeFormat("en-CA", { timeZone: zone, year: "numeric", month: "2-digit", day: "2-digit" }).format(at);
const addDays = (iso, n) => {
  const [y, m, d] = iso.split("-").map(Number);
  return new Date(Date.UTC(y, m - 1, d + n)).toISOString().slice(0, 10);
};
const mondayOf = (iso) => {
  const [y, m, d] = iso.split("-").map(Number);
  const dow = new Date(Date.UTC(y, m - 1, d)).getUTCDay();
  return addDays(iso, dow === 0 ? -6 : 1 - dow);
};
// The stored calendar date, rendered as en-GB "d Mon yyyy" from its parts (zone-free oracle).
const MONTHS = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];
const display = (iso) => {
  const [y, m, d] = iso.split("-").map(Number);
  return `${d} ${MONTHS[m - 1]} ${y}`;
};

const out = { startedAt: new Date().toISOString(), gitHead: execSync("git rev-parse HEAD", { cwd: W }).toString().trim(), run: RUN, journal: [], steps: [], console: [], checks: [], readings: [], restore: {} };
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
// main's formatDate: new Date(v).toLocaleDateString("en-GB", …) with no timeZone, in the page's zone.
const mainWouldShow = (page, v) =>
  page.evaluate((x) => new Date(x).toLocaleDateString("en-GB", { day: "numeric", month: "short", year: "numeric" }), v);

async function firmLogin(browser, zone) {
  const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 }, timezoneId: zone });
  const page = await ctx.newPage();
  watch(page, `firm-${zone}`);
  await page.goto(`${APP}/mock-login`, { waitUntil: "load" });
  await page.getByRole("button", { name: "Sign In", exact: true }).click();
  await page.waitForURL(/\/org\/.*\/dashboard/, { timeout: 60000 });
  return { ctx, page };
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

async function readFirmFeeNote(browser, zone, inv) {
  const { ctx, page } = await firmLogin(browser, zone);
  const tag = `firm-feenote-${zone.split("/").pop().toLowerCase()}`;
  await page.goto(`${APP}/org/${ORG}/invoices/${inv.id}`, { waitUntil: "load" });
  const issued = page.getByText(/^Issued: /).first();
  await issued.waitFor({ timeout: 30000 });
  await page.waitForTimeout(1500); // let hydration settle (a mismatch re-renders client-side)
  const r = {
    tag,
    zone,
    invoice: inv.invoiceNumber,
    apiIssueDate: inv.issueDate,
    apiDueDate: inv.dueDate,
    issued: (await issued.innerText()).trim(),
    due: (await page.getByText(/^Due: /).first().innerText()).trim(),
    mainWouldShow: { issued: await mainWouldShow(page, inv.issueDate), due: await mainWouldShow(page, inv.dueDate) },
    browserNow: await page.evaluate(() => new Date().toString()),
  };
  await page.screenshot({ path: path.join(OUT, `obs-${tag}.png`) });
  await ctx.close();
  return r;
}

async function readPortalInvoices(browser, state, zone, invs) {
  const ctx = await browser.newContext({ viewport: { width: 1280, height: 1200 }, timezoneId: zone, storageState: state });
  const page = await ctx.newPage();
  const tag = `portal-invoices-${zone.split("/").pop().toLowerCase()}`;
  watch(page, tag);
  await page.goto(`${PORTAL}/invoices`, { waitUntil: "load" });
  await page.locator("a:visible", { hasText: invs[0].invoiceNumber }).first().waitFor({ timeout: 30000 });
  const text = (await page.locator("main").innerText()).replace(/\s+/g, " ");
  await page.screenshot({ path: path.join(OUT, `obs-${tag}.png`), fullPage: true });
  const r = { tag, zone, text: text.slice(0, 2000), mainWouldShow: await mainWouldShow(page, invs[0].dueDate) };
  await ctx.close();
  return r;
}

async function readDeadlines(browser, state, zone, pinned, timeout = 30000) {
  const ctx = await browser.newContext({ viewport: { width: 1280, height: 1400 }, timezoneId: zone, storageState: state });
  if (pinned) await ctx.clock.setFixedTime(new Date(pinned));
  const page = await ctx.newPage();
  const tag = `portal-deadlines-${zone.split("/").pop().toLowerCase()}-${pinned ? pinned.slice(11, 16).replace(":", "") + "sast" : "now"}`;
  watch(page, tag);
  const resP = page.waitForResponse((r) => new URL(r.url()).pathname === "/portal/deadlines" && r.request().method() === "GET", { timeout });
  await page.goto(`${PORTAL}/deadlines`, { waitUntil: "load" });
  let res;
  try {
    res = await resP;
  } catch (e) {
    out[`unreached-${tag}`] = { finalUrl: page.url(), browserNow: await page.evaluate(() => new Date().toString()) };
    await page.screenshot({ path: path.join(OUT, `obs-${tag}.png`), fullPage: true });
    await ctx.close();
    throw e;
  }
  const api = await res.json();
  await page.getByRole("heading", { level: 1, name: "Diary dates" }).waitFor();
  await page.locator('[data-testid^="deadline-row-"]').first().waitFor({ timeout: 20000 });
  const browserNow = await page.evaluate(() => new Date().toString());
  const browserLocalDate = await page.evaluate(() => {
    const d = new Date();
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
  });
  const rows = await page.locator('[data-testid^="deadline-row-"]').evaluateAll((els) =>
    els.map((e) => ({ testid: e.getAttribute("data-testid"), text: e.innerText.replace(/\s+/g, " ").trim() })),
  );
  const weeks = await page.locator('[data-testid^="deadline-week-"]').evaluateAll((els) =>
    els.map((e) => ({ testid: e.getAttribute("data-testid"), aria: e.getAttribute("aria-label"), header: (e.querySelector("h3") || {}).textContent })),
  );
  const mainWeekHeaders = {};
  for (const w of weeks) {
    const iso = w.testid.replace("deadline-week-", "");
    mainWeekHeaders[iso] = `Week of ${await mainWouldShow(page, iso)}`;
  }
  await page.screenshot({ path: path.join(OUT, `obs-${tag}.png`), fullPage: true });
  await ctx.close();
  return { tag, zone, pinned, browserNow, browserLocalDate, requestUrl: res.url(), api, rows, weeks, mainWeekHeaders };
}

(async () => {
  const browser = await chromium.launch();
  let firm = null;
  let jwt = null;
  let originalPortalIds = null;
  try {
    const tok = await firmToken();
    const mb = await (await fetch(`${API}/api/matters`, { headers: { Authorization: `Bearer ${tok}` } })).json();
    const matter = (Array.isArray(mb) ? mb : mb.content || []).find((m) => m.name === MATTER);
    out.matter = { id: matter.id, name: matter.name };
    const ib = await (await fetch(`${API}/api/invoices`, { headers: { Authorization: `Bearer ${tok}` } })).json();
    const invs = (Array.isArray(ib) ? ib : ib.content || []).filter((i) => i.status !== "DRAFT" && i.issueDate && i.dueDate && i.invoiceNumber);
    out.feeNotes = invs.map((i) => ({ id: i.id, invoiceNumber: i.invoiceNumber, status: i.status, issueDate: i.issueDate, dueDate: i.dueDate }));
    check("an issued fee note exists to read (date-only issueDate/dueDate)", invs.length > 0 && invs.every((i) => /^\d{4}-\d{2}-\d{2}$/.test(i.issueDate) && /^\d{4}-\d{2}-\d{2}$/.test(i.dueDate)), out.feeNotes);

    const login = await portalLogin(browser);
    jwt = login.jwt;
    originalPortalIds = (await portalDeadlines(jwt)).map((d) => `${d.sourceEntity}-${d.id}`).sort();
    out.restore.originalPortalIds = originalPortalIds;

    firm = (await firmLogin(browser, "Africa/Johannesburg")).page;
    if (CLEANUP) {
      out.mode = `cleanup of run ${RUN}`;
      out.journal = CLEANUP.journal;
      originalPortalIds = CLEANUP.restore.originalPortalIds;
      out.restore.originalPortalIds = originalPortalIds;
      return;
    }
    await firm.goto(`${APP}/org/${ORG}/matters/${matter.id}?tab=court-dates`, { waitUntil: "load" });
    await firm.getByTestId("project-court-dates-tab").waitFor({ timeout: 30000 });
    const today = dateIn("Africa/Johannesburg", new Date());
    out.todaySA = today;
    for (const n of COURT_OFFSETS) {
      const date = addDays(today, n);
      const courtName = `OBS-DDF ${RUN} day${n}x`;
      const entry = { kind: "court-date", offset: n, date, courtName, label: `Hearing — ${courtName}`, state: "creating" };
      out.journal.push(entry);
      save();
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
      entry.state = "created";
      save();
      step(`firm UI: scheduled HEARING "${courtName}" on ${date} (+${n})`);
    }
    const created = out.journal.filter((j) => j.state === "created");

    // The default window (SA today..+60) carries the +0..+2 hearings; the -1 one is outside it.
    const inWindow = created.filter((c) => c.offset >= 0);
    let fresh = [];
    for (let i = 0; i < 30; i++) {
      fresh = (await portalDeadlines(jwt)).filter((d) => !originalPortalIds.includes(`${d.sourceEntity}-${d.id}`));
      if (fresh.length >= inWindow.length) break;
      await new Promise((r) => setTimeout(r, 1000));
    }
    check(`portal default window has the ${inWindow.length} new in-window rows`, fresh.length === inWindow.length && inWindow.every((c) => fresh.some((d) => d.label === c.label && d.dueDate === c.date)), fresh);

    // Firm fee-note detail in each zone.
    const firmReadings = [];
    for (const zone of ZONES) firmReadings.push(await readFirmFeeNote(browser, zone, invs[0]));
    out.readings.push(...firmReadings);
    for (const r of firmReadings) {
      check(`${r.tag}: "${r.issued}" is the stored issue date ${r.apiIssueDate}`, r.issued === `Issued: ${display(r.apiIssueDate)}`, r);
      check(`${r.tag}: "${r.due}" is the stored due date ${r.apiDueDate}`, r.due === `Due: ${display(r.apiDueDate)}`, r);
    }
    check("firm fee-note dates identical across zones", new Set(firmReadings.map((r) => `${r.issued}|${r.due}`)).size === 1, firmReadings.map((r) => [r.zone, r.issued, r.due]));

    // Portal invoices list in each zone.
    const invReadings = [];
    for (const zone of ZONES) invReadings.push(await readPortalInvoices(browser, login.state, zone, invs));
    out.readings.push(...invReadings);
    for (const r of invReadings)
      for (const i of invs)
        check(`${r.tag}: ${i.invoiceNumber} shows issue ${display(i.issueDate)} and due ${display(i.dueDate)}`, r.text.includes(display(i.issueDate)) && r.text.includes(display(i.dueDate)), { zone: r.zone, invoice: i.invoiceNumber, mainWouldShowDue: r.mainWouldShow });

    // Portal deadlines in each zone (real time), then Dubai pinned to 22:30 SAST today.
    // Pinned clocks. The portal session is a 1-hour JWT whose expiry the client checks against the
    // browser clock, so a clock pinned more than an hour ahead signs the client out before any
    // request. 22:30 SAST today (Dubai) is ~14 h ahead of the run, so it is attempted and recorded,
    // not gated. The west-of-SA half of the same bug is reachable with a past pin: at 01:00 SAST a
    // New York browser's date is still SA-yesterday, so main requested from=yesterday and listed
    // yesterday's hearing, which the backend's SA window excludes.
    const dlReadings = [];
    for (const zone of ZONES) dlReadings.push(await readDeadlines(browser, login.state, zone, null));
    const nyEarly = await readDeadlines(browser, login.state, "America/New_York", `${today}T01:00:00+02:00`);
    dlReadings.push(nyEarly);
    out.readings.push(...dlReadings);
    try {
      out.dubaiLate = await readDeadlines(browser, login.state, "Asia/Dubai", `${today}T22:30:00+02:00`, 10000);
    } catch (e) {
      out.dubaiLate = { deferred: true, pinned: `${today}T22:30:00+02:00`, reason: "portal JWT TTL is 1 h (PortalJwtService.SESSION_TTL) and portal/lib/auth.ts getAuth() clears a token whose exp is before Date.now(); a browser clock 14 h ahead is signed out before /portal/deadlines is requested", error: String(e).slice(0, 200) };
    }
    for (const r of dlReadings) {
      const q = new URL(r.requestUrl).searchParams;
      check(`${r.tag}: /portal/deadlines request carries no from/to (backend SA default)`, !q.has("from") && !q.has("to"), r.requestUrl);
      for (const c of created) {
        const row = r.api.find((d) => d.sourceEntity === "COURT_DATE" && d.label === c.label);
        const ui = row && r.rows.find((x) => x.testid === `deadline-row-COURT_DATE-${row.id}`);
        if (c.offset < 0) {
          check(`${r.tag}: ${c.label} (${c.date}, SA-yesterday) is outside the window and not listed (browser local date ${r.browserLocalDate})`, !row && !r.rows.some((x) => x.text.includes(c.courtName)), { apiRow: row || null });
          continue;
        }
        const monday = mondayOf(c.date);
        const wk = r.weeks.find((w) => w.testid === `deadline-week-${monday}`);
        check(`${r.tag}: ${c.label} (${c.date}) listed and reads ${display(c.date)}`, !!ui && ui.text.includes(display(c.date)) && row.dueDate === c.date, { row: ui && ui.text, apiDueDate: row && row.dueDate });
        check(`${r.tag}: week of ${c.date} is headed "Week of ${display(monday)}" (main would show "${r.mainWeekHeaders[monday]}")`, !!wk && wk.header === `Week of ${display(monday)}` && wk.aria === `Week of ${display(monday)}`, wk);
      }
    }
    const sig = (r) => JSON.stringify([r.rows.map((x) => x.text), r.weeks.map((w) => w.header)]);
    check("portal deadlines identical across zones and clocks", new Set(dlReadings.map(sig)).size === 1, dlReadings.map((r) => [r.tag, r.weeks.map((w) => w.header)]));
    check(
      `New York at 01:00 SAST: browser local date ${nyEarly.browserLocalDate} is SA-yesterday (main would have requested from=${nyEarly.browserLocalDate})`,
      nyEarly.browserLocalDate === addDays(today, -1),
      { browserNow: nyEarly.browserNow, requestUrl: nyEarly.requestUrl },
    );
    check(
      "no hydration-mismatch console errors",
      !out.console.some((c) => /hydrat|did not match|Text content/i.test(c)),
      out.console.filter((c) => /hydrat|did not match|Text content/i.test(c)),
    );
  } catch (e) {
    out.error = String(e && e.stack ? e.stack : e);
  } finally {
    // Each fixture is cleaned up independently: one failure does not strand the rest.
    for (const c of out.journal) {
      if (c.state === "cancelled") continue;
      try {
        if (!firm) throw new Error("no firm session");
        await firm.goto(`${APP}/org/${ORG}/matters/${out.matter.id}?tab=court-dates`, { waitUntil: "load" });
        await firm.getByTestId("project-court-dates-tab").waitFor({ timeout: 30000 });
        const row = courtRow(firm, c.courtName);
        // The list renders after the tab mounts: give the row time to appear before calling it gone.
        await row.first().waitFor({ timeout: 15000 }).catch(() => {});
        if ((await row.count()) === 0) {
          c.state = c.state === "creating" ? "never-created" : "missing";
          step(`cleanup: "${c.courtName}" not in the list (${c.state})`);
          continue;
        }
        await row.getByRole("button", { name: "Actions" }).click();
        await firm.getByRole("menuitem", { name: "Cancel" }).click();
        const dlg = firm.getByTestId("cancel-court-date-dialog");
        await dlg.getByLabel("Reason").fill(`OBS ${RUN} cleanup`);
        await dlg.getByRole("button", { name: "Cancel Court Date" }).click();
        await dlg.waitFor({ state: "hidden", timeout: 15000 });
        c.state = "cancelled";
        step(`firm UI: cancelled "${c.courtName}"`);
      } catch (e) {
        c.cleanupError = String(e).slice(0, 300);
        out.restoreError = `${out.restoreError || ""} ${c.courtName}: ${c.cleanupError}`.trim();
      }
      save();
    }
    try {
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
      out.restoreError = `${out.restoreError || ""} ${String(e).slice(0, 300)}`.trim();
    }
    out.finishedAt = new Date().toISOString();
    out.allChecksPass = !out.error && !out.restoreError && out.checks.length > 0 && out.checks.every((c) => c.ok);
    save();
    await browser.close();
    process.exitCode = out.allChecksPass ? 0 : 1;
    console.log(JSON.stringify({ allChecksPass: out.allChecksPass, error: out.error, restoreError: out.restoreError, checks: out.checks.length, failed: out.checks.filter((c) => !c.ok).map((c) => c.name) }, null, 2));
  }
})();
