// 669B observe-first shared helpers (copied from 669A before/scripts/lib.cjs; that file is not
// edited). PHASE=before|after|after-fix (default before) picks the output dir (../../$PHASE/) and the
// raw-tile dir (/tmp/669B-$PHASE-raw/, outside the repo — raw per-viewport PNGs are never
// committed). Re-exports the 661B helpers (theme via localStorage `kazi-theme`, sidebar cookie,
// console watch, contact sheet, WCAG ratio). Logins go through the /mock-login UI (user select +
// "Sign In") for every persona. Playwright: the worktree's frontend/node_modules when installed,
// else the sibling main checkout's (../b2b-strawman/frontend/node_modules).
// Added for 669B: PA (the platform-admin base URL), MAILPIT (:8026, read-only REST for the OTP).
const path = require("path");
const fs = require("fs");
const Module = require("module");

const W = path.resolve(__dirname, "../../../../../..");
const candidates = [path.join(W, "frontend/node_modules/@playwright/test"), path.resolve(W, "../b2b-strawman/frontend/node_modules/@playwright/test")];
const pwPath = candidates.find((c) => fs.existsSync(c));
if (!pwPath) throw new Error("no @playwright/test in " + candidates.join(" or "));
const origResolve = Module._resolveFilename;
Module._resolveFilename = function (req, ...rest) {
  if (req === path.join(W, "frontend/node_modules/@playwright/test")) req = pwPath;
  return origResolve.call(this, req, ...rest);
};
const C = require("../../../661B/walk/scripts/common.cjs");
Module._resolveFilename = origResolve;
C.USERS.bob = { userId: "user_e2e_bob", orgId: "org_e2e_test", orgSlug: C.ORG, orgRole: "org:admin" };

const PHASE = process.env.PHASE || "before";
if (!["before", "after", "after-fix"].includes(PHASE)) throw new Error(`PHASE must be before|after|after-fix, got ${PHASE}`);
const OUT = path.resolve(__dirname, "../..", PHASE);
const RAW = `/tmp/669B-${PHASE}-raw`;
fs.mkdirSync(OUT, { recursive: true });
fs.mkdirSync(RAW, { recursive: true });
const BASE = `${C.APP}/org/${C.ORG}`;
const PA = `${C.APP}/platform-admin`;
const MAILPIT = process.env.MAILPIT_URL || "http://localhost:8026";
const EXTRA = { reducedMotion: "reduce", timezoneId: "Africa/Johannesburg" };
const SEED_FILE = path.resolve(__dirname, "../seed.json"); // always the before/ seed record (ids)

const LABELS = { alice: "Alice (Owner)", bob: "Bob (Admin)", carol: "Carol (Member)" };
async function loginState(browser, who = "alice") {
  const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  const p = await ctx.newPage();
  await p.goto(`${C.APP}/mock-login`, { waitUntil: "load" });
  await p.locator("#user-select").selectOption({ label: LABELS[who] });
  await p.getByRole("button", { name: "Sign In", exact: true }).click();
  await p.waitForURL(/\/(org\/.*\/dashboard|platform-admin)/, { timeout: 60000 });
  const state = await ctx.storageState();
  await ctx.close();
  return state;
}

async function apiGet(token, p) {
  const r = await fetch(`${C.API}${p}`, { headers: { Authorization: `Bearer ${token}` } });
  const text = await r.text();
  let json = null;
  try { json = JSON.parse(text); } catch (e) {}
  return { status: r.status, json, headers: Object.fromEntries(r.headers.entries()), text: json ? undefined : text.slice(0, 200) };
}
const list = (j) => (Array.isArray(j) ? j : j && (j.content || j.items || j.data) ? j.content || j.items || j.data : []);
const readSeed = () => (fs.existsSync(SEED_FILE) ? JSON.parse(fs.readFileSync(SEED_FILE, "utf8")) : { ids: {} });

// Mailpit REST (read-only): newest message to `to`, its 6-digit code.
async function mailpitOtp(to, { after = 0, timeout = 30000 } = {}) {
  const t0 = Date.now();
  while (Date.now() - t0 < timeout) {
    const r = await fetch(`${MAILPIT}/api/v1/search?query=${encodeURIComponent(`to:${to}`)}`);
    const j = await r.json();
    const ms = (j.messages || []).filter((m) => new Date(m.Created).getTime() >= after).sort((a, b) => new Date(b.Created) - new Date(a.Created));
    if (ms.length) {
      const d = await (await fetch(`${MAILPIT}/api/v1/message/${ms[0].ID}`)).json();
      const m = (d.HTML || d.Text || "").match(/\b(\d{6})\b/);
      return { id: ms[0].ID, subject: ms[0].Subject, created: ms[0].Created, code: m ? m[1] : null };
    }
    await new Promise((ok) => setTimeout(ok, 1500));
  }
  return null;
}

module.exports = { ...C, C, W, PHASE, OUT, RAW, BASE, PA, MAILPIT, EXTRA, SEED_FILE, LABELS, loginState, apiGet, list, readSeed, mailpitOtp, pwPath };
