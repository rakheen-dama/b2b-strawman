// F-669A-A-4 fix — OBS through the UI (real Chromium via the Playwright library; never the
// founder's Playwright-MCP Chrome). E2E mock-auth stack: app :3001, backend :8081, IDP :8090.
// The e2e frontend container (the SSR server that computes the countdown) runs in UTC.
//
// The UI cannot set a data request's deadline: the backend sets it to SA-today + the firm's
// "Data Request Deadline (days)" (Settings › Firm › Compliance, minimum 1). So, as Alice:
//   phase "pre"  (before 00:00 SAST): set the setting to 1 → create request T (deadline = tomorrow),
//                set it to 2 → create request U (deadline = today+2), restore the setting,
//                then read the Compliance › Overview countdowns.
//   phase "post" (after 00:00 SAST): read the countdowns again — T's deadline is now TODAY, U's is
//                tomorrow — and create request V with the setting at 1 (tomorrow), then restore.
// A past deadline cannot be produced through the UI (no SQL writes); vitest covers it.
// Deadlines are read with read-only GET /api/data-requests (the endpoint the page uses). No SQL.
// Usage: node obs.cjs pre|post   → ../obs-<phase>.json and ../<phase>-*.png
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
const PHASE = process.argv[2];
if (!["pre", "post"].includes(PHASE)) throw new Error("usage: node obs.cjs pre|post");
const FILE = path.join(OUT, `obs-${PHASE}.json`);

const todaySA = () =>
  new Intl.DateTimeFormat("en-CA", { timeZone: "Africa/Johannesburg", year: "numeric", month: "2-digit", day: "2-digit" }).format(new Date());
const daysBetween = (a, b) => {
  const [ay, am, ad] = a.split("-").map(Number);
  const [by, bm, bd] = b.split("-").map(Number);
  return Math.round((Date.UTC(by, bm - 1, bd) - Date.UTC(ay, am - 1, ad)) / 86400000);
};
const expectedText = (n) => (n < 0 ? `${-n}d overdue` : n === 0 ? "Due today" : `${n}d remaining`);
// What main's formula (floor((UTC midnight of deadline − now) / 1 day) on the UTC SSR server) shows.
const mainText = (deadline) => {
  const [y, m, d] = deadline.split("-").map(Number);
  return expectedText(Math.floor((Date.UTC(y, m - 1, d) - Date.now()) / 86400000));
};

const out = { phase: PHASE, startedAt: new Date().toISOString(), todaySA: todaySA(), steps: [], console: [], checks: [] };
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
async function openRequests(tok) {
  const all = [];
  for (const s of ["RECEIVED", "IN_PROGRESS"]) {
    const r = await fetch(`${API}/api/data-requests?status=${s}`, { headers: { Authorization: `Bearer ${tok}` } });
    if (!r.ok) throw new Error(`GET data-requests ${s} -> ${r.status}`);
    all.push(...(await r.json()));
  }
  return all.map((r) => ({ id: r.id, clientName: r.clientName, status: r.status, deadline: r.deadline, description: r.description }));
}

(async () => {
  const tok = await token();
  const browser = await chromium.launch();
  const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 }, timezoneId: "Africa/Johannesburg", reducedMotion: "reduce" });
  const page = await ctx.newPage();
  page.on("console", (m) => m.type() === "error" && out.console.push(m.text().slice(0, 300)));
  page.on("pageerror", (e) => out.console.push(String(e).slice(0, 300)));
  try {
    await page.goto(`${APP}/mock-login`, { waitUntil: "load" });
    await page.getByRole("button", { name: "Sign In", exact: true }).click();
    await page.waitForURL(/\/org\/.*\/dashboard/, { timeout: 60000 });
    const main = page.locator("#main-content");

    const setDeadlineDays = async (n) => {
      await page.goto(`${BASE}/settings/firm?tab=compliance`, { waitUntil: "load" });
      const input = main.locator("#data-request-deadline");
      await input.waitFor({ timeout: 20000 });
      const before = await input.inputValue();
      await input.fill(String(n));
      await main.getByRole("button", { name: "Save Settings", exact: true }).click();
      await page.waitForTimeout(1500);
      await page.reload({ waitUntil: "load" });
      const after = await main.locator("#data-request-deadline").inputValue();
      out.steps.push({ step: `setting Data Request Deadline (days) ${before} → ${n}`, reloadedValue: after });
      check(`setting saved as ${n}`, after === String(n), { before, after });
      return before;
    };
    const createRequest = async (label) => {
      await page.goto(`${BASE}/compliance/requests`, { waitUntil: "load" });
      await main.getByRole("button", { name: "New Request" }).click();
      const dlg = page.getByRole("dialog");
      await dlg.getByText("Select a client...", { exact: true }).click();
      const opt = page.getByRole("listbox").getByRole("option").first();
      await opt.waitFor({ timeout: 20000 });
      const client = (await opt.textContent()).trim();
      await opt.click();
      await dlg.locator("#request-type").selectOption("ACCESS");
      await dlg.locator("#description").fill(`OBS F-669A-A-4 ${label}`);
      await dlg.getByRole("button", { name: "Create Request" }).click();
      await dlg.waitFor({ state: "hidden", timeout: 20000 });
      out.steps.push({ step: `created request "${label}" for ${client} at ${new Date().toISOString()} (SA ${todaySA()})` });
    };
    const observe = async (tag) => {
      await page.goto(`${BASE}/compliance`, { waitUntil: "load" });
      const section = main.getByRole("heading", { level: 2, name: "Data Requests" }).locator("xpath=../..");
      await section.waitFor({ timeout: 20000 });
      await page.waitForTimeout(500);
      const api = await openRequests(tok);
      const rows = await section.getByRole("row").evaluateAll((trs) =>
        trs.slice(1).map((tr) => Array.from(tr.querySelectorAll("td")).map((td) => td.innerText.trim()))
      );
      await section.screenshot({ path: path.join(OUT, `${PHASE}-${tag}-overview-data-requests.png`) });
      await page.screenshot({ path: path.join(OUT, `${PHASE}-${tag}-overview.png`), fullPage: true });
      // The overview shows the 5 open requests with the earliest deadlines, in that order.
      const top = [...api].sort((a, b) => (a.deadline < b.deadline ? -1 : 1)).slice(0, 5);
      const today = todaySA();
      const rowsOut = top.map((r, i) => {
        const cells = rows[i] || [];
        const deadlineCell = cells[2] || "";
        const observed = (deadlineCell.match(/(\d+d overdue|Due today|\d+d remaining)/) || [])[0] || null;
        const expected = expectedText(daysBetween(today, r.deadline));
        return { clientName: r.clientName, description: r.description, deadline: r.deadline, todaySA: today, expected, observed, mainFormulaWouldShow: mainText(r.deadline), deadlineCell };
      });
      out[`overview_${tag}`] = { at: new Date().toISOString(), todaySA: today, openRequests: api.length, rows: rowsOut };
      for (const r of rowsOut) check(`${tag}: ${r.description ?? r.clientName} (deadline ${r.deadline}) shows "${r.expected}"`, r.observed === r.expected, r);
      await page.goto(`${BASE}/compliance/requests`, { waitUntil: "load" });
      await page.waitForTimeout(500);
      await page.screenshot({ path: path.join(OUT, `${PHASE}-${tag}-requests-list.png`), fullPage: true });
      await page.goto(`${BASE}/settings/firm?tab=data-requests`, { waitUntil: "load" });
      await page.waitForTimeout(800);
      await page.screenshot({ path: path.join(OUT, `${PHASE}-${tag}-settings-dsar-list.png`), fullPage: true });
    };

    out.openRequestsAtStart = await openRequests(tok);
    if (PHASE === "pre") {
      await observe("0-before-create");
      // ORIGINAL_DAYS: the firm's value before attempt 1 (which stopped after setting it to 1).
      const before = await setDeadlineDays(1);
      const original = process.env.ORIGINAL_DAYS || before;
      out.originalDeadlineDays = original;
      await createRequest("T (deadline days 1)");
      await setDeadlineDays(2);
      await createRequest("U (deadline days 2)");
      await setDeadlineDays(original);
      await observe("1-after-create");
    } else {
      await observe("0-after-midnight");
      const before = await setDeadlineDays(1);
      const original = process.env.ORIGINAL_DAYS || before;
      out.originalDeadlineDays = original;
      await createRequest("V (deadline days 1, after midnight)");
      await setDeadlineDays(original);
      await observe("1-after-create");
    }
  } catch (e) {
    out.error = String(e && e.stack ? e.stack : e);
    await page.screenshot({ path: path.join(OUT, `${PHASE}-error.png`), fullPage: true }).catch(() => {});
  } finally {
    out.finishedAt = new Date().toISOString();
    out.allChecksPass = !out.error && out.checks.every((c) => c.ok);
    save();
    await browser.close();
    console.log(JSON.stringify({ phase: PHASE, allChecksPass: out.allChecksPass, error: out.error, failed: out.checks.filter((c) => !c.ok).map((c) => c.name) }, null, 2));
  }
})();
