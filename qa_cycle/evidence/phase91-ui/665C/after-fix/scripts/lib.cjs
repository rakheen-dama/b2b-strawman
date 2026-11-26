// 665C observe-first shared helpers (copied from 665B/before/scripts/lib.cjs, adapted; that file is
// not edited). PHASE=before|after|after-fix (default before) picks the output dir (../../$PHASE/) and
// the raw-tile dir (/tmp/665C-$PHASE-raw/, outside the repo — raw per-viewport PNGs are never
// committed). Re-exports the 661B helpers (login, theme via localStorage `kazi-theme`, sidebar
// cookie, console watch, contact sheet). Playwright: the worktree's frontend/node_modules when
// installed, else the sibling main checkout's (../b2b-strawman/frontend/node_modules).
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

const PHASE = process.env.PHASE || "before";
if (!["before", "after", "after-fix"].includes(PHASE)) throw new Error(`PHASE must be before|after|after-fix, got ${PHASE}`);
const OUT = path.resolve(__dirname, "../..", PHASE);
const RAW = `/tmp/665C-${PHASE}-raw`;
fs.mkdirSync(OUT, { recursive: true });
fs.mkdirSync(RAW, { recursive: true });
const BASE = `${C.APP}/org/${C.ORG}`;
const EXTRA = { reducedMotion: "reduce", timezoneId: "Africa/Johannesburg" };
// Matters family tab ids, in lib/settings-nav.ts order.
const TABS = ["templates", "naming", "custom-fields", "tags", "checklists", "request-templates", "request-settings", "take-on"];
const LABELS = { templates: "Templates", naming: "Naming", "custom-fields": "Custom fields", tags: "Tags", checklists: "Checklists", "request-templates": "Request templates", "request-settings": "Request settings", "take-on": "Take-on" };
const CAROL_EMAIL = "carol@e2e-test.local";
// Data the OBS created / reads; ids are discovered by name at run time (00-discover / 03-data).
const NAMES = {
  checklist: "OBS 665C Checklist",
  digitFirstProbe: "665C OBS Checklist",
  requestTemplate: "665C OBS Request Template",
  matterTemplate: "665C OBS Matter Template",
  takeOnBatch: "665C OBS take-on (mid-wizard)",
  takeOnRole: "665C OBS Take-on",
};

// GET with a token; returns {status, json} and never throws on non-2xx.
async function apiGet(token, p) {
  const r = await fetch(`${C.API}${p}`, { headers: { Authorization: `Bearer ${token}` } });
  const text = await r.text();
  let json = null;
  try { json = JSON.parse(text); } catch (e) {}
  return { status: r.status, json, text: json ? undefined : text.slice(0, 200) };
}
const list = (j) => (Array.isArray(j) ? j : j && (j.content || j.items || j.data) ? j.content || j.items || j.data : []);

// The ids the capture needs (sub-page targets + the mid-wizard batch), read by name, GET only.
async function targets() {
  const t = await C.idpToken("alice");
  const mt = list((await apiGet(t, "/api/matter-templates")).json);
  const cl = list((await apiGet(t, "/api/checklist-templates")).json);
  const rt = list((await apiGet(t, "/api/request-templates")).json);
  const b = list((await apiGet(t, "/api/take-on/batches?page=0&size=50")).json);
  const pick = (arr, name) => arr.find((x) => x.name === name) || null;
  const ownMt = pick(mt, NAMES.matterTemplate);
  const ownCl = pick(cl, NAMES.checklist);
  const ownRt = pick(rt, NAMES.requestTemplate);
  const batch = b.find((x) => x.name === NAMES.takeOnBatch) || null;
  return {
    matterTemplate: ownMt || mt[0] || null,
    // checklists/[id]/edit only renders for ORG_CUSTOM sources; a seeded (pack) one is captured too.
    checklistCustom: ownCl || cl.find((x) => x.source === "ORG_CUSTOM") || null,
    checklistPack: cl.find((x) => x.source && x.source !== "ORG_CUSTOM") || null,
    requestTemplate: ownRt || rt.find((x) => x.source === "CUSTOM") || rt[0] || null,
    requestTemplatePack: rt.find((x) => x.source && x.source !== "CUSTOM") || null,
    batch,
    counts: { matterTemplates: mt.length, checklists: cl.length, requestTemplates: rt.length, batches: b.length },
  };
}

module.exports = { ...C, C, W, PHASE, OUT, RAW, BASE, EXTRA, TABS, LABELS, CAROL_EMAIL, NAMES, apiGet, list, targets, pwPath };
