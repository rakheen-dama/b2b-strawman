// Review round 1 (PR #1889) — OBS for the status-driven day-0 label. Real Chromium via the
// Playwright library (never the founder's Playwright-MCP Chrome), E2E mock-auth stack (:3001 /
// :8081 / :8090), as Alice. On one seeded matter's Court dates tab, create GENERAL_3Y trackers
// (cause = target − 3 years) for SA-today −1, 0, +1, +2, then read Days left, colour and status in
// Johannesburg and New York browser contexts. Expected with the current backend rule: day 0 is
// EXPIRED and reads "—" (never "Today"); tomorrow is WARNED and reads 1. No settings, no SQL.
// Usage: node obs-r1.cjs → ../obs-r1.json, ../obs-r1-*.png
const path = require("path");
const fs = require("fs");

const W = path.resolve(__dirname, "../../../..");
const { chromium } = require(path.join(W, "frontend/node_modules/@playwright/test"));
const APP = "http://localhost:3001";
const API = "http://localhost:8081";
const IDP = "http://localhost:8090";
const ORG = "e2e-test-org";
const BASE = `${APP}/org/${ORG}`;
const OUT = path.resolve(__dirname, "..");
const POST = false;
const FILE = path.join(OUT, "obs-r1.json");
const OFFSETS = [-1, 0, 1, 2];

const todaySA = () =>
  new Intl.DateTimeFormat("en-CA", { timeZone: "Africa/Johannesburg", year: "numeric", month: "2-digit", day: "2-digit" }).format(new Date());
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
// Status-driven day 0: "Today" only when the backend still treats the tracker as live.
const label = (n, status) => (n > 0 ? String(n) : n === 0 && !["Expired", "Interrupted"].includes(status) ? "Today" : "—");
const expectedStatus = (n) => (n <= 0 ? "Expired" : n <= 90 ? "Warned" : "Running");
const colour = (n) => (n <= 30 ? "text-red-600" : n <= 90 ? "text-amber-600" : "text-slate-600");
// What main showed: ceil((UTC midnight of the date − now) / 1 day), "—" unless > 0.
const mainCount = (iso) => {
  const [y, m, d] = iso.split("-").map(Number);
  return Math.ceil((Date.UTC(y, m - 1, d) - Date.now()) / 86400000);
};

const out = { startedAt: new Date().toISOString(), todaySA: todaySA(), steps: [], console: [], checks: [] };
const check = (name, ok, detail) => out.checks.push({ name, ok: !!ok, detail });
const save = () => fs.writeFileSync(FILE, JSON.stringify(out, null, 2));

async function token() {
  const r = await fetch(`${IDP}/token`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ userId: "user_e2e_alice", orgId: "org_e2e_test", orgSlug: ORG, orgRole: "org:owner" }),
  });
  return (await r.json()).access_token;
}

async function login(browser, timezoneId) {
  const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 }, timezoneId, reducedMotion: "reduce" });
  const page = await ctx.newPage();
  page.on("console", (m) => m.type() === "error" && out.console.push(`[${timezoneId}] ${m.text().slice(0, 300)}`));
  page.on("pageerror", (e) => out.console.push(`[${timezoneId}] ${String(e).slice(0, 300)}`));
  await page.goto(`${APP}/mock-login`, { waitUntil: "load" });
  await page.getByRole("button", { name: "Sign In", exact: true }).click();
  await page.waitForURL(/\/org\/.*\/dashboard/, { timeout: 60000 });
  return page;
}

async function readRows(page) {
  const list = page.getByTestId("prescription-tracker-list");
  await list.waitFor({ timeout: 20000 });
  await page.waitForTimeout(800);
  return list.locator('[data-testid^="prescription-tracker-row-"]').evaluateAll((trs) =>
    trs.map((tr) => {
      const td = Array.from(tr.querySelectorAll("td"));
      const span = td[4] && td[4].querySelector("span");
      return {
        cause: td[1].innerText.trim(),
        prescriptionDate: td[2].innerText.trim(),
        status: td[3].innerText.trim(),
        daysLeft: span ? span.innerText.trim() : null,
        colour: span ? ["text-red-600", "text-amber-600", "text-slate-600"].find((c) => span.classList.contains(c)) || null : null,
      };
    })
  );
}

(async () => {
  const tok = await token();
  const r = await fetch(`${API}/api/matters`, { headers: { Authorization: `Bearer ${tok}` } });
  if (!r.ok) throw new Error(`GET /api/matters -> ${r.status}`);
  const body = await r.json();
  const matters = Array.isArray(body) ? body : body.content || [];
  const prior = POST ? JSON.parse(fs.readFileSync(path.join(OUT, "obs.json"), "utf8")) : null;
  const matter = POST
    ? matters.find((m) => m.id === prior.matter.id)
    : matters.find((m) => (m.status || "ACTIVE") === "ACTIVE") || matters[0];
  out.matter = { id: matter.id, name: matter.name, status: matter.status };
  const URL = `${BASE}/matters/${matter.id}?tab=court-dates`;

  const browser = await chromium.launch();
  try {
    const page = await login(browser, "Africa/Johannesburg");
    await page.goto(URL, { waitUntil: "load" });
    out.rowsBefore = await readRows(page);
    const today = todaySA();
    const wanted = POST ? prior.wanted : OFFSETS.map((n) => ({ offset: n, prescriptionDate: addDays(today, n) })).map((w) => ({
      ...w,
      cause: minusYears(w.prescriptionDate, 3),
    }));
    for (const w of POST ? [] : wanted) {
      const list = page.getByTestId("prescription-tracker-list");
      await list.getByRole("button", { name: "New prescription tracker" }).click();
      const dialog = page.getByTestId("create-prescription-dialog");
      await dialog.waitFor({ timeout: 15000 });
      await dialog.getByLabel("Cause of Action Date").fill(w.cause);
      await dialog.getByLabel("Prescription Type").selectOption("GENERAL_3Y");
      await dialog.getByRole("button", { name: "Create Tracker" }).click();
      await dialog.waitFor({ state: "hidden", timeout: 15000 });
      out.steps.push({ step: `created GENERAL_3Y tracker, cause ${w.cause} (target ${w.prescriptionDate}, offset ${w.offset}) at ${new Date().toISOString()}` });
      await page.waitForTimeout(500);
    }
    out.wanted = wanted;

    for (const [tz, p] of [["Africa/Johannesburg", page], ["America/New_York", await login(browser, "America/New_York")]]) {
      await p.goto(URL, { waitUntil: "load" });
      const rows = await readRows(p);
      const todayNow = todaySA();
      const tag = tz.split("/")[1].toLowerCase() + (POST ? "-post" : "");
      await p.getByTestId("prescription-tracker-list").screenshot({ path: path.join(OUT, `obs-r1-${tag}-tracker-list.png`) });
      await p.screenshot({ path: path.join(OUT, `obs-r1-${tag}-matter-court-dates.png`), fullPage: true });
      const res = wanted.map((w) => {
        const row = rows.filter((x) => x.cause === w.cause && x.prescriptionDate === w.prescriptionDate).pop();
        const n = daysBetween(todayNow, w.prescriptionDate);
        const m = mainCount(w.prescriptionDate);
        return { ...w, todaySA: todayNow, expected: label(n, expectedStatus(n)), expectedColour: colour(n), expectedStatus: expectedStatus(n), observed: row && row.daysLeft, observedColour: row && row.colour, status: row && row.status, mainWouldShow: m > 0 ? String(m) : "—", mainColour: colour(m) };
      });
      out[`observed_${tag.replace("-post", "")}`] = { at: new Date().toISOString(), rows: res, allRows: rows };
      for (const x of res)
        check(`${tz}: ${x.prescriptionDate} (offset ${x.offset}) is ${x.expectedStatus} and shows "${x.expected}" ${x.expectedColour}`, x.status === x.expectedStatus && x.observed === x.expected && x.observedColour === x.expectedColour, x);
      check(`${tz}: no row reads "Today" next to an Expired badge`, !rows.some((x) => x.status === "Expired" && x.daysLeft === "Today"), rows);
    }
  } catch (e) {
    out.error = String(e && e.stack ? e.stack : e);
  } finally {
    out.finishedAt = new Date().toISOString();
    out.allChecksPass = !out.error && out.checks.length > 0 && out.checks.every((c) => c.ok);
    save();
    await browser.close();
    console.log(JSON.stringify({ allChecksPass: out.allChecksPass, error: out.error, failed: out.checks.filter((c) => !c.ok).map((c) => c.name) }, null, 2));
  }
})();
