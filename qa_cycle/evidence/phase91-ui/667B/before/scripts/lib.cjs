// 667B observe-first shared helpers (adapted from 667A before/scripts/lib.cjs; that file is not
// edited). PHASE=before|after|after-fix (default before) picks the output dir (../../$PHASE/) and the
// raw-tile dir (/tmp/667B-$PHASE-raw/, outside the repo — raw per-viewport PNGs are never
// committed). Re-exports the 661B helpers (theme via localStorage `kazi-theme`, sidebar cookie,
// console watch, contact sheet, WCAG ratio). Logins go through the /mock-login UI (user select +
// "Sign In") for every persona. Playwright: the worktree's frontend/node_modules when installed,
// else the sibling main checkout's (../b2b-strawman/frontend/node_modules).
const path = require("path");
const fs = require("fs");
const crypto = require("crypto");
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
const RAW = `/tmp/667B-${PHASE}-raw`;
fs.mkdirSync(OUT, { recursive: true });
fs.mkdirSync(RAW, { recursive: true });
const BASE = `${C.APP}/org/${C.ORG}`;
const TA = `${BASE}/trust-accounting`;
const EXTRA = { reducedMotion: "reduce", timezoneId: "Africa/Johannesburg" };
const SEED_FILE = path.resolve(__dirname, "../seed.json"); // always the before/ seed record (ids)

const LABELS = { alice: "Alice (Owner)", bob: "Bob (Admin)", carol: "Carol (Member)" };
async function loginState(browser, who = "alice") {
  const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  const p = await ctx.newPage();
  await p.goto(`${C.APP}/mock-login`, { waitUntil: "load" });
  await p.locator("#user-select").selectOption({ label: LABELS[who] });
  await p.getByRole("button", { name: "Sign In", exact: true }).click();
  await p.waitForURL(/\/org\/.*\/dashboard/, { timeout: 60000 });
  const state = await ctx.storageState();
  await ctx.close();
  return state;
}

async function apiGet(token, p) {
  const r = await fetch(`${C.API}${p}`, { headers: { Authorization: `Bearer ${token}` } });
  const text = await r.text();
  let json = null;
  try { json = JSON.parse(text); } catch (e) {}
  return { status: r.status, json, text: json ? undefined : text.slice(0, 200) };
}
// Trust writes carry an Idempotency-Key (UUID), as the frontend's idempotent() helper sends.
async function apiWrite(token, method, p, body) {
  const headers = { Authorization: `Bearer ${token}`, "Idempotency-Key": crypto.randomUUID(), "Content-Type": "application/json" };
  const r = await fetch(`${C.API}${p}`, { method, headers, body: body == null ? undefined : JSON.stringify(body) });
  const text = await r.text();
  let json = null;
  try { json = JSON.parse(text); } catch (e) {}
  if (!r.ok) throw new Error(`${method} ${p} -> ${r.status} ${text.slice(0, 300)}`);
  return json;
}
// The bank-statement import: multipart `file` (+ Idempotency-Key), as uploadBankStatement() posts it.
async function apiUpload(token, p, fileName, text) {
  const fd = new FormData();
  fd.append("file", new Blob([text], { type: "text/csv" }), fileName);
  const r = await fetch(`${C.API}${p}`, { method: "POST", headers: { Authorization: `Bearer ${token}`, "Idempotency-Key": crypto.randomUUID() }, body: fd });
  const t = await r.text();
  let json = null;
  try { json = JSON.parse(t); } catch (e) {}
  if (!r.ok) throw new Error(`POST ${p} -> ${r.status} ${t.slice(0, 300)}`);
  return json;
}
const list = (j) => (Array.isArray(j) ? j : j && (j.content || j.items || j.data) ? j.content || j.items || j.data : []);
const readSeed = () => JSON.parse(fs.readFileSync(SEED_FILE, "utf8"));

module.exports = { ...C, C, W, PHASE, OUT, RAW, BASE, TA, EXTRA, SEED_FILE, LABELS, loginState, apiGet, apiWrite, apiUpload, list, readSeed, pwPath };
