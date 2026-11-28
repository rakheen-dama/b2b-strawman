// OBS — prescription tracker stores and shows the LAST DAY TO SERVE; Interrupt is validated by the
// date entered (founder ruling 2026-11-28, tasks/legal/prescription-computation-2026-11-28.md).
// Real Chromium via the Playwright library (never the founder's Playwright-MCP Chrome), E2E
// mock-auth stack (:3001 / :8081 / :8090) plus a dev portal on :3002 pointed at :8081, as Alice.
// No SQL. The one GET /api/matters call only finds the seeded Website Redesign matter's id.
//   1. Create (UI) a GENERAL_3Y tracker with cause = (today + 1) − 3y → last day to serve = today:
//      row shows the date under "Last day to serve", Days left "Today", status not Expired.
//   2. Create (UI) one with cause = today − 3y → last day = yesterday: Expired, Interrupt offered.
//   4. Interrupt it with a date after the last day (today): rejected inline with the message.
//   3. Interrupt it with the last day itself: accepted (Interrupted).
//   5. Org Diary (day view) and the client portal /deadlines entry for tracker 1: record them.
// finally: interrupt tracker 1 through the UI (service date today ≤ its last day) so no open
// prescription is left on the seeded matter; its portal row is deleted by that interruption.
// Usage: node obs.cjs → ../obs.json, ../obs-*.png
const path = require("path");
const fs = require("fs");

const W = path.resolve(__dirname, "../../../..");
const { chromium } = require(path.join(W, "frontend/node_modules/@playwright/test"));
const APP = "http://localhost:3001";
const API = "http://localhost:8081";
const IDP = "http://localhost:8090";
const PORTAL = "http://localhost:3002";
const ORG = "e2e-test-org";
const BASE = `${APP}/org/${ORG}`;
const MATTER = "Website Redesign"; // owned by Acme Corp, whose portal contact is seeded
const CONTACT = "contact@acme.example.com";
const OUT = path.resolve(__dirname, "..");
const FILE = path.join(OUT, "obs.json");
const RUN = `OBS ${new Date().toISOString()}`;

const todaySA = () =>
  new Intl.DateTimeFormat("en-CA", { timeZone: "Africa/Johannesburg", year: "numeric", month: "2-digit", day: "2-digit" }).format(new Date());
const addDays = (iso, n) => {
  const [y, m, d] = iso.split("-").map(Number);
  return new Date(Date.UTC(y, m - 1, d + n)).toISOString().slice(0, 10);
};
const minusYears = (iso, n) => `${Number(iso.slice(0, 4)) - n}${iso.slice(4)}`;

const out = { startedAt: new Date().toISOString(), todaySA: todaySA(), steps: [], console: [], checks: [] };
const check = (name, ok, detail) => out.checks.push({ name, ok: !!ok, detail });
const step = (s) => out.steps.push({ at: new Date().toISOString(), step: s });
const save = () => fs.writeFileSync(FILE, JSON.stringify(out, null, 2));

async function token() {
  const r = await fetch(`${IDP}/token`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ userId: "user_e2e_alice", orgId: "org_e2e_test", orgSlug: ORG, orgRole: "org:owner" }),
  });
  return (await r.json()).access_token;
}

function watch(page, tag) {
  page.on("console", (m) => m.type() === "error" && out.console.push(`[${tag}] ${m.text().slice(0, 300)}`));
  page.on("pageerror", (e) => out.console.push(`[${tag}] ${String(e).slice(0, 300)}`));
}

async function login(browser) {
  const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 }, timezoneId: "Africa/Johannesburg", reducedMotion: "reduce" });
  const page = await ctx.newPage();
  watch(page, "firm");
  await page.goto(`${APP}/mock-login`, { waitUntil: "load" });
  await page.getByRole("button", { name: "Sign In", exact: true }).click();
  await page.waitForURL(/\/org\/.*\/dashboard/, { timeout: 60000 });
  return page;
}

async function readRows(page) {
  const list = page.getByTestId("prescription-tracker-list");
  await list.waitFor({ timeout: 20000 });
  await page.waitForTimeout(800);
  const headers = await list.locator("thead th").allInnerTexts();
  const rows = await list.locator('[data-testid^="prescription-tracker-row-"]').evaluateAll((trs) =>
    trs.map((tr) => {
      const td = Array.from(tr.querySelectorAll("td"));
      const span = td[4] && td[4].querySelector("span");
      return {
        id: tr.getAttribute("data-testid").replace("prescription-tracker-row-", ""),
        cause: td[1].innerText.trim(),
        lastDayToServe: td[2].innerText.trim(),
        status: td[3].innerText.trim(),
        daysLeft: span ? span.innerText.trim() : null,
        interruptOffered: Array.from(tr.querySelectorAll("button")).some((b) => b.innerText.trim() === "Interrupt"),
      };
    })
  );
  return { headers: headers.map((h) => h.trim()), rows };
}

async function createTracker(page, cause) {
  const list = page.getByTestId("prescription-tracker-list");
  const before = (await readRows(page)).rows.map((r) => r.id);
  await list.getByRole("button", { name: "New prescription tracker" }).click();
  const dialog = page.getByTestId("create-prescription-dialog");
  await dialog.waitFor({ timeout: 15000 });
  await dialog.getByLabel("Cause of Action Date").fill(cause);
  await dialog.getByLabel("Prescription Type").selectOption("GENERAL_3Y");
  await dialog.getByLabel("Notes").fill(RUN).catch(() => {});
  await dialog.getByRole("button", { name: "Create Tracker" }).click();
  await dialog.waitFor({ state: "hidden", timeout: 15000 });
  for (let i = 0; i < 20; i++) {
    const { rows } = await readRows(page);
    const fresh = rows.find((r) => !before.includes(r.id) && r.cause === cause);
    if (fresh) return fresh;
    await page.waitForTimeout(500);
  }
  throw new Error(`created tracker (cause ${cause}) did not appear`);
}

async function openInterrupt(page, id) {
  await page.getByTestId(`prescription-tracker-row-${id}`).getByRole("button", { name: "Interrupt", exact: true }).click();
  const dialog = page.getByTestId("interrupt-dialog");
  await dialog.waitFor({ timeout: 15000 });
  return dialog;
}

async function submitInterrupt(dialog, date, reason) {
  await dialog.getByLabel("Interruption Date").fill(date);
  await dialog.getByLabel("Reason").fill(reason);
  await dialog.getByRole("button", { name: "Record Interruption" }).click();
}

const rowById = async (page, id) => (await readRows(page)).rows.find((r) => r.id === id);

(async () => {
  const tok = await token();
  const r = await fetch(`${API}/api/matters`, { headers: { Authorization: `Bearer ${tok}` } });
  if (!r.ok) throw new Error(`GET /api/matters -> ${r.status}`);
  const body = await r.json();
  const matter = (Array.isArray(body) ? body : body.content || []).find((m) => m.name === MATTER);
  if (!matter) throw new Error(`seeded matter ${MATTER} not found`);
  out.matter = { id: matter.id, name: matter.name };
  const URL = `${BASE}/matters/${matter.id}?tab=court-dates`;
  const today = todaySA();
  const yesterday = addDays(today, -1);

  const browser = await chromium.launch();
  let page;
  let t0 = null; // tracker 1: last day to serve = today (left live until finally)
  try {
    page = await login(browser);
    await page.goto(URL, { waitUntil: "load" });
    out.original = await readRows(page);
    step(`original tracker list on ${MATTER}: ${out.original.rows.length} rows`);

    // 1. Last day to serve = today.
    const cause0 = minusYears(addDays(today, 1), 3);
    t0 = await createTracker(page, cause0);
    step(`1. created GENERAL_3Y tracker ${t0.id}, cause ${cause0}`);
    const view = await readRows(page);
    out.step1 = { headers: view.headers, row: t0 };
    await page.getByTestId("prescription-tracker-list").screenshot({ path: path.join(OUT, "obs-1-last-day-today.png") });
    check("1. date column header reads 'Last day to serve' (no 'Prescription date')", view.headers.some((h) => /^last day to serve$/i.test(h)) && !view.headers.some((h) => /prescription date/i.test(h)), view.headers);
    check(`1. row shows last day to serve ${today}`, t0.lastDayToServe === today, t0);
    check("1. Days left reads 'Today'", t0.daysLeft === "Today", t0);
    check("1. status is not Expired (Warned)", t0.status !== "Expired" && t0.status === "Warned", t0);

    // 2. Last day to serve = yesterday.
    const causeY = minusYears(today, 3);
    const tY = await createTracker(page, causeY);
    step(`2. created GENERAL_3Y tracker ${tY.id}, cause ${causeY}`);
    out.step2 = { row: tY };
    await page.getByTestId("prescription-tracker-list").screenshot({ path: path.join(OUT, "obs-2-expired-interrupt-offered.png") });
    check(`2. row shows last day to serve ${yesterday}`, tY.lastDayToServe === yesterday, tY);
    check("2. status is Expired", tY.status === "Expired", tY);
    check("2. Interrupt is offered on the Expired tracker", tY.interruptOffered, tY);

    // 4. A date after the last day: rejected inline.
    let dialog = await openInterrupt(page, tY.id);
    await submitInterrupt(dialog, today, `${RUN} late`);
    const expectedMsg = `The interruption date is after the last day to serve (${yesterday}). The claim had already prescribed, so it cannot be interrupted.`;
    const msg = dialog.getByText(expectedMsg, { exact: true });
    await msg.waitFor({ timeout: 10000 }).catch(() => {});
    const shown = await msg.isVisible().catch(() => false);
    await dialog.screenshot({ path: path.join(OUT, "obs-4-late-date-rejected.png") });
    const stillOpen = await dialog.isVisible();
    await dialog.getByRole("button", { name: "Cancel" }).click();
    await dialog.waitFor({ state: "hidden", timeout: 10000 });
    const afterReject = await rowById(page, tY.id);
    out.step4 = { enteredDate: today, messageShown: shown, expectedMsg, dialogStayedOpen: stillOpen, rowAfter: afterReject };
    check("4. a date after the last day shows the inline message", shown, out.step4);
    check("4. rejected: dialog stayed open and the tracker is still Expired", stillOpen && afterReject.status === "Expired", out.step4);

    // 3. The last day itself: accepted.
    dialog = await openInterrupt(page, tY.id);
    await submitInterrupt(dialog, yesterday, `${RUN} served on the last day`);
    await dialog.waitFor({ state: "hidden", timeout: 15000 });
    let interrupted;
    for (let i = 0; i < 20 && !(interrupted && interrupted.status === "Interrupted"); i++) {
      await page.waitForTimeout(500);
      interrupted = await rowById(page, tY.id);
    }
    out.step3 = { enteredDate: yesterday, rowAfter: interrupted };
    await page.getByTestId("prescription-tracker-list").screenshot({ path: path.join(OUT, "obs-3-interrupted.png") });
    check("3. interruption dated the last day to serve is accepted (Interrupted, no Interrupt button)", interrupted && interrupted.status === "Interrupted" && !interrupted.interruptOffered, interrupted);

    // 5a. Org Diary, day view for today: tracker 1's entry.
    await page.goto(`${BASE}/diary?view=day&from=${today}`, { waitUntil: "load" });
    const ev = page.getByTestId(`diary-event-${t0.id}`);
    await ev.waitFor({ timeout: 20000 }).catch(() => {});
    const diaryText = (await ev.isVisible().catch(() => false)) ? (await ev.innerText()).replace(/\s+/g, " ").trim() : null;
    await page.screenshot({ path: path.join(OUT, "obs-5-diary-day.png"), fullPage: true });
    out.step5diary = { url: page.url(), entry: diaryText };
    check("5. the org Diary day view for today lists tracker 1's prescription entry", !!diaryText, out.step5diary);

    // 5b. Client portal /deadlines as the Acme contact.
    const pctx = await browser.newContext({ viewport: { width: 1280, height: 900 }, timezoneId: "Africa/Johannesburg" });
    const pp = await pctx.newPage();
    watch(pp, "portal");
    await pp.goto(`${PORTAL}/login?orgId=${ORG}`, { waitUntil: "load" });
    await pp.getByLabel("Email address").fill(CONTACT);
    await pp.getByRole("button", { name: "Send Magic Link" }).click();
    const link = pp.locator('a[href*="/auth/exchange"]').first();
    await link.waitFor({ timeout: 20000 });
    await link.click();
    await pp.waitForURL((u) => !/\/(login|auth)/.test(u.pathname), { timeout: 30000 });
    await pp.goto(`${PORTAL}/deadlines`, { waitUntil: "load" });
    const prow = pp.getByTestId(`deadline-row-PRESCRIPTION_TRACKER-${t0.id}`);
    await prow.waitFor({ timeout: 20000 }).catch(() => {});
    const portalText = (await prow.isVisible().catch(() => false)) ? (await prow.innerText()).replace(/\s+/g, " ").trim() : null;
    const expiredRow = await pp.getByTestId(`deadline-row-PRESCRIPTION_TRACKER-${tY.id}`).count();
    await pp.screenshot({ path: path.join(OUT, "obs-5-portal-deadlines.png"), fullPage: true });
    out.step5portal = { entry: portalText, interruptedTrackerRows: expiredRow };
    check("5. the portal /deadlines lists tracker 1 (Prescription deadline)", !!portalText && /Prescription/.test(portalText), out.step5portal);
    check("5. the interrupted tracker has no portal row", expiredRow === 0, out.step5portal);
    await pctx.close();
  } catch (e) {
    out.error = String(e && e.stack ? e.stack : e);
  } finally {
    // Restore: no open prescription left on the seeded matter (interrupt tracker 1 via the UI).
    try {
      if (page && t0) {
        await page.goto(URL, { waitUntil: "load" });
        const cur = await rowById(page, t0.id);
        if (cur && cur.status !== "Interrupted") {
          const dialog = await openInterrupt(page, t0.id);
          await submitInterrupt(dialog, todaySA(), `${RUN} cleanup`);
          await dialog.waitFor({ state: "hidden", timeout: 15000 });
        }
        await page.waitForTimeout(800);
        out.restored = await readRows(page);
        const left = out.restored.rows.filter((x) => x.status !== "Interrupted" && !out.original.rows.some((o) => o.id === x.id));
        check("restore: no OBS tracker is left open on the matter", left.length === 0, left);
      }
    } catch (e) {
      out.restoreError = String(e && e.stack ? e.stack : e);
    }
    out.finishedAt = new Date().toISOString();
    out.allChecksPass = !out.error && !out.restoreError && out.checks.length > 0 && out.checks.every((c) => c.ok);
    save();
    await browser.close();
    console.log(JSON.stringify({ allChecksPass: out.allChecksPass, error: out.error, restoreError: out.restoreError, failed: out.checks.filter((c) => !c.ok).map((c) => c.name) }, null, 2));
  }
})();
