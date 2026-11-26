// 664B observe-first shared helpers. PHASE=before|after (default before) picks the output dir
// (../../$PHASE/) and the raw-tile dir (/tmp/664B-$PHASE-raw/); the scripts are re-run UNEDITED for
// the after-capture. Re-exports the 661B helpers (login, theme via localStorage `kazi-theme`, sidebar
// cookie, console watch, contact sheet). Playwright: the worktree's frontend/node_modules when
// installed, else the sibling main checkout's (../b2b-strawman/frontend/node_modules).
const path = require("path");
const fs = require("fs");
const Module = require("module");

const W = path.resolve(__dirname, "../../../../../..");
const candidates = [path.join(W, "frontend/node_modules/@playwright/test"), path.resolve(W, "../b2b-strawman/frontend/node_modules/@playwright/test")];
const pwPath = candidates.find((c) => fs.existsSync(c));
if (!pwPath) throw new Error("no @playwright/test in " + candidates.join(" or "));
// common.cjs requires W/frontend/node_modules/@playwright/test; redirect that one request.
const origResolve = Module._resolveFilename;
Module._resolveFilename = function (req, ...rest) {
  if (req === path.join(W, "frontend/node_modules/@playwright/test")) req = pwPath;
  return origResolve.call(this, req, ...rest);
};
const C = require("../../../661B/walk/scripts/common.cjs");
Module._resolveFilename = origResolve;

const PHASE = process.env.PHASE || "before";
if (!["before", "after"].includes(PHASE)) throw new Error(`PHASE must be before|after, got ${PHASE}`);
const OUT = path.resolve(__dirname, "../..", PHASE);
const RAW = `/tmp/664B-${PHASE}-raw`;
fs.mkdirSync(OUT, { recursive: true });
fs.mkdirSync(RAW, { recursive: true });
const BASE = `${C.APP}/org/${C.ORG}`;
const EXTRA = { reducedMotion: "reduce", timezoneId: "Africa/Johannesburg" };

// Trust writes need an Idempotency-Key (UUID); every call gets a fresh one.
async function apiWrite(token, method, p, body, extraHeaders = {}) {
  const headers = { Authorization: `Bearer ${token}`, "Idempotency-Key": crypto.randomUUID(), ...extraHeaders };
  let payload = body;
  if (body && !(body instanceof FormData)) {
    headers["Content-Type"] = "application/json";
    payload = JSON.stringify(body);
  }
  const r = await fetch(`${C.API}${p}`, { method, headers, body: payload });
  const text = await r.text();
  let json = null;
  try { json = JSON.parse(text); } catch (e) {}
  if (!r.ok) throw new Error(`${method} ${p} -> ${r.status} ${text.slice(0, 300)}`);
  return json;
}

module.exports = { ...C, C, W, PHASE, OUT, RAW, BASE, EXTRA, apiWrite, pwPath };
