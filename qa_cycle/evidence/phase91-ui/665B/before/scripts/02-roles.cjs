// 665B OBS step 2: the custom-role persona, through the UI only (as Alice, 1440×900 light).
// REUSES the two custom roles 665A created through Settings › Firm › Roles › "New Role"
// (665A/before/scripts/02-roles.cjs create): "665A OBS Oversight" (TEAM_OVERSIGHT) and
// "665A OBS No Oversight" (PROJECT_MANAGEMENT). `check` verifies they still exist with exactly those
// capabilities (GET /api/org-roles) and that the Roles tab lists them (Edit buttons); it creates
// nothing — if either is missing the run stops (create it with 665A's script, then re-run).
//   node 02-roles.cjs check
//   node 02-roles.cjs assign "<role>" — Team page → Carol's row → member panel → Role select → Save
//                                       (copied from 665A's assign). Used to give Carol a custom role
//                                       for 01-capture, then to restore her to "Member" (no
//                                       overrides). Verified by GET /api/members/{id}/capabilities
//                                       and Carol's own GET /api/me/capabilities after each save.
// Writes $RAW/roles-<step>.json + tiles; 05-assemble merges them.
const path = require("path");
const fs = require("fs");
const L = require("./lib.cjs");

const ROLES = [
  { name: "665A OBS Oversight", caps: ["TEAM_OVERSIGHT"] },
  { name: "665A OBS No Oversight", caps: ["PROJECT_MANAGEMENT"] },
];
const cmd = process.argv[2];
const arg = process.argv[3];
const out = { cmd, arg: arg || null, at: new Date().toISOString(), steps: [] };
const tiles = [];
const consoleSink = [];
const lab = { label: "" };
const log = (o) => { out.steps.push(o); console.log(JSON.stringify(o)); };
const shot = async (p, key, caption, opt = {}) => {
  const f = path.join(L.RAW, `${key}.png`);
  await p.screenshot({ path: f, ...opt });
  tiles.push([key, f, caption]);
};
async function page(browser, S, w, h, theme = "light") {
  const ctx = await L.newCtx(browser, S, { width: w, height: h, theme, extra: L.EXTRA });
  const p = await ctx.newPage();
  L.watch(p, consoleSink, lab);
  return { ctx, p };
}

async function check(browser, S) {
  const t = await L.idpToken("alice");
  const roles = (await L.apiGet(t, "/api/org-roles")).json || [];
  for (const r of ROLES) {
    const found = roles.find((x) => x.name === r.name);
    const ok = !!found && JSON.stringify([...found.capabilities].sort()) === JSON.stringify([...r.caps].sort());
    log({ step: "api-check", role: r.name, found: !!found, capabilities: found && found.capabilities, memberCount: found && found.memberCount, ok });
    if (!ok) throw new Error(`custom role "${r.name}" missing or changed — create it with 665A's 02-roles.cjs create`);
  }
  const { ctx, p } = await page(browser, S, 1440, 900);
  lab.label = "roles|check|1440";
  await p.goto(`${L.BASE}/settings/firm?tab=roles`, { waitUntil: "load" });
  await p.waitForLoadState("networkidle").catch(() => {});
  for (const r of ROLES) log({ step: "ui-check", role: r.name, editButtons: await p.getByRole("button", { name: `Edit ${r.name}`, exact: true }).count() });
  await ctx.close();
}

async function assign(browser, S, roleName) {
  const { ctx, p } = await page(browser, S, 1440, 900);
  lab.label = `team|assign ${roleName}`;
  await p.goto(`${L.BASE}/team`, { waitUntil: "load" });
  await p.waitForLoadState("networkidle").catch(() => {});
  await p.getByTestId(`member-row-${L.CAROL_EMAIL}`).click();
  const sheet = p.getByRole("dialog", { name: "Member Detail" });
  await sheet.waitFor();
  await sheet.getByText("Loading capabilities...").waitFor({ state: "hidden", timeout: 15000 }).catch(() => {});
  const combo = sheet.getByRole("combobox");
  const before = (await combo.innerText()).trim();
  await combo.click();
  await p.getByRole("option", { name: roleName, exact: true }).click();
  await p.waitForTimeout(300);
  const after = (await combo.innerText()).trim();
  const checked = await sheet.evaluate((d) => [...d.querySelectorAll('[data-testid^="member-capability-"]')].filter((c) => c.getAttribute("aria-checked") === "true" || c.getAttribute("data-state") === "checked").map((c) => c.dataset.testid.replace("member-capability-", "")));
  await shot(p, `team__assign-${roleName.replace(/\W+/g, "_")}`, `Alice 1440 light — Team › Carol's member panel: role "${before}" → "${roleName}" (before Save); checked: ${checked.join(", ") || "none"}`);
  await sheet.getByRole("button", { name: "Save", exact: true }).click();
  await sheet.waitFor({ state: "hidden", timeout: 15000 });
  const t = await L.idpToken("alice");
  const members = (await L.apiGet(t, "/api/members")).json || [];
  const carol = members.find((m) => m.email === L.CAROL_EMAIL);
  const caps = (await L.apiGet(t, `/api/members/${carol.id}/capabilities`)).json;
  const ct = await L.idpToken("carol");
  const me = (await L.apiGet(ct, "/api/me/capabilities")).json;
  log({ step: "assign", role: roleName, comboBefore: before, comboAfter: after, panelChecked: checked, apiMemberCaps: { roleName: caps.roleName, overrides: caps.overrides, effective: caps.effectiveCapabilities }, carolMe: { role: me.role, isAdmin: me.isAdmin, isOwner: me.isOwner, capabilities: me.capabilities } });
  await ctx.close();
}

(async () => {
  const browser = await L.chromium.launch();
  const S = await L.loginState(browser, "alice");
  if (cmd === "check") await check(browser, S);
  else if (cmd === "assign") await assign(browser, S, arg);
  else throw new Error("cmd: check | assign <role>");
  await browser.close();
  out.console = consoleSink;
  out.tiles = tiles;
  const tag = cmd === "assign" ? `assign-${arg.replace(/\W+/g, "_")}` : cmd;
  fs.writeFileSync(path.join(L.RAW, `roles-${tag}.json`), JSON.stringify(out));
  console.log("console", consoleSink.length, "tiles", tiles.length);
})();
