// 665A OBS-AFTER copy of ../../before/scripts/lib.cjs (adapted: PHASE defaults to "after"). Original header:
// 665A observe-first shared helpers (copied from 664B/after-fix/scripts/lib.cjs, adapted; that file is
// not edited). PHASE=before|after (default before) picks the output dir (../../$PHASE/) and the raw-tile
// dir (/tmp/665A-$PHASE-raw/), so the scripts can be re-run UNEDITED for the after-capture. Re-exports
// the 661B helpers (login, theme via localStorage `kazi-theme`, sidebar cookie, console watch, contact
// sheet). Playwright: the worktree's frontend/node_modules when installed, else the sibling main
// checkout's (../b2b-strawman/frontend/node_modules).
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

const PHASE = process.env.PHASE || "after";
if (!["before", "after", "after-fix"].includes(PHASE)) throw new Error(`PHASE must be before|after|after-fix, got ${PHASE}`);
const OUT = path.resolve(__dirname, "../..", PHASE);
const RAW = `/tmp/665A-${PHASE}-raw`;
fs.mkdirSync(OUT, { recursive: true });
fs.mkdirSync(RAW, { recursive: true });
const BASE = `${C.APP}/org/${C.ORG}`;
const EXTRA = { reducedMotion: "reduce", timezoneId: "Africa/Johannesburg" };
const TABS = ["general", "roles", "compliance", "features", "subscription"];

// GET with a token; returns {status, json} and never throws on non-2xx.
async function apiGet(token, p) {
  const r = await fetch(`${C.API}${p}`, { headers: { Authorization: `Bearer ${token}` } });
  const text = await r.text();
  let json = null;
  try { json = JSON.parse(text); } catch (e) {}
  return { status: r.status, json, text: json ? undefined : text.slice(0, 200) };
}

module.exports = { ...C, C, W, PHASE, OUT, RAW, BASE, EXTRA, TABS, apiGet, pwPath };
