// 665A OBS step 2: the custom-role persona, through the UI only (as Alice, 1440×900 light).
//   node 02-roles.cjs create          — Settings › Firm › Roles: "New Role" → Create Role dialog for
//                                       each ROLE below unless an "Edit <name>" button already exists
//                                       (idempotent). Measures the dialog at 1280×720 (628F-BLOCKER
//                                       fixme in roles-capabilities.spec.ts:54) and 1440×900.
//   node 02-roles.cjs assign "<role>" — Team page → Carol's row → member panel → Role select → Save.
//                                       Used to give Carol a custom role for 01-capture, then to
//                                       restore her to "Member" (no overrides). Verified by GET
//                                       /api/members/{id}/capabilities after each save.
// Writes $RAW/roles-<step>.json + tiles; 04-assemble merges them.
const path = require("path");
const fs = require("fs");
const L = require("./lib.cjs");

const ROLES = [
  { name: "665A OBS Oversight", caps: ["Team Oversight"], why: "HOLDS TEAM_OVERSIGHT (the only capability that gates a Firm tab: Audit trail)" },
  { name: "665A OBS No Oversight", caps: ["Matter Management"], why: "LACKS TEAM_OVERSIGHT (PROJECT_MANAGEMENT only)" },
];
const CAROL_EMAIL = "carol@e2e-test.local";
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
const DIALOG_BOX = () => {
  const d = document.querySelector('[role="dialog"]');
  if (!d) return null;
  const b = d.getBoundingClientRect();
  const btn = [...d.querySelectorAll("button")].find((x) => /^(Create|Save)$/.test(x.textContent.trim()));
  const bb = btn ? btn.getBoundingClientRect() : null;
  const title = d.querySelector("h2");
  const tb = title ? title.getBoundingClientRect() : null;
  return { viewport: [innerWidth, innerHeight], dialog: { top: Math.round(b.top), bottom: Math.round(b.bottom), height: Math.round(b.height) }, titleTop: tb ? Math.round(tb.top) : null, submit: bb ? { text: btn.textContent.trim(), top: Math.round(bb.top), bottom: Math.round(bb.bottom), inViewport: bb.top >= 0 && bb.bottom <= innerHeight } : null, titleInViewport: tb ? tb.top >= 0 : null, overflowY: getComputedStyle(d).overflowY, maxHeight: getComputedStyle(d).maxHeight };
};

async function create(browser, S) {
  // 1280×720: the size the 628F-BLOCKER fixme names — open, measure, Escape (nothing saved).
  {
    const { ctx, p } = await page(browser, S, 1280, 720);
    lab.label = "roles|create-dialog|1280";
    await p.goto(`${L.BASE}/settings/firm?tab=roles`, { waitUntil: "load" });
    await p.waitForLoadState("networkidle").catch(() => {});
    await p.getByRole("button", { name: "New Role" }).click();
    await p.getByRole("dialog", { name: "Create Role" }).waitFor();
    await p.waitForTimeout(500);
    const m = await p.evaluate(DIALOG_BOX);
    log({ step: "create-dialog-measure", viewport: "1280x720", ...m });
    await shot(p, "roles__dialog-1280", `Alice 1280×720 light — Create Role dialog: height ${m.dialog.height}, top ${m.dialog.top}, submit "${m.submit && m.submit.text}" ${m.submit && m.submit.inViewport ? "in" : "OUT OF"} viewport (bottom ${m.submit && m.submit.bottom})`);
    await p.keyboard.press("Escape");
    await ctx.close();
  }
  const { ctx, p } = await page(browser, S, 1440, 900);
  lab.label = "roles|create|1440";
  await p.goto(`${L.BASE}/settings/firm?tab=roles`, { waitUntil: "load" });
  await p.waitForLoadState("networkidle").catch(() => {});
  for (const r of ROLES) {
    if ((await p.getByRole("button", { name: `Edit ${r.name}`, exact: true }).count()) > 0) {
      log({ step: "create", role: r.name, result: "exists — skipped" });
      continue;
    }
    await p.getByRole("button", { name: "New Role" }).click();
    const dlg = p.getByRole("dialog", { name: "Create Role" });
    await dlg.waitFor();
    await p.waitForTimeout(500);
    const m = await p.evaluate(DIALOG_BOX);
    await dlg.getByLabel("Name", { exact: true }).fill(r.name);
    for (const c of r.caps) await dlg.getByRole("checkbox", { name: c, exact: true }).check();
    await shot(p, `roles__dialog-1440-${r.name.replace(/\W+/g, "_")}`, `Alice 1440×900 light — Create Role "${r.name}" (${r.caps.join(", ")}) filled; dialog height ${m.dialog.height}, submit ${m.submit && m.submit.inViewport ? "in" : "OUT OF"} viewport`);
    let how = "click";
    if (m.submit && m.submit.inViewport) await dlg.getByRole("button", { name: "Create", exact: true }).click();
    else { how = "dispatchEvent (submit off-screen)"; await dlg.getByRole("button", { name: "Create", exact: true }).dispatchEvent("click"); }
    await dlg.waitFor({ state: "hidden", timeout: 15000 });
    await p.getByRole("button", { name: `Edit ${r.name}`, exact: true }).waitFor({ timeout: 15000 });
    log({ step: "create", role: r.name, caps: r.caps, why: r.why, submit: how, dialog1440: m, result: "created (Edit button visible)" });
  }
  await p.reload({ waitUntil: "load" });
  await p.waitForLoadState("networkidle").catch(() => {});
  await shot(p, "roles__after-create-full", "Alice 1440×900 light — Roles tab with the two custom roles (full page)", { fullPage: true });
  const cards = await p.evaluate(() => {
    const h = [...document.querySelectorAll("#main-content h2")].find((x) => x.textContent.trim() === "Custom Roles");
    const sec = h && h.closest(".space-y-4");
    return sec ? (sec.innerText || "").replace(/\s+/g, " ").slice(0, 600) : null;
  });
  log({ step: "custom-roles-section-text", text: cards });
  await ctx.close();
  const t = await L.idpToken("alice");
  const roles = (await L.apiGet(t, "/api/org-roles")).json || [];
  log({ step: "api-verify", customRoles: roles.filter((x) => !x.isSystem).map((x) => ({ id: x.id, name: x.name, slug: x.slug, capabilities: x.capabilities, memberCount: x.memberCount })) });
}

async function assign(browser, S, roleName) {
  const { ctx, p } = await page(browser, S, 1440, 900);
  lab.label = `team|assign ${roleName}`;
  await p.goto(`${L.BASE}/team`, { waitUntil: "load" });
  await p.waitForLoadState("networkidle").catch(() => {});
  await p.getByTestId(`member-row-${CAROL_EMAIL}`).click();
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
  const carol = members.find((m) => m.email === CAROL_EMAIL);
  const caps = (await L.apiGet(t, `/api/members/${carol.id}/capabilities`)).json;
  const ct = await L.idpToken("carol");
  const me = (await L.apiGet(ct, "/api/me/capabilities")).json;
  log({ step: "assign", role: roleName, comboBefore: before, comboAfter: after, panelChecked: checked, apiMemberCaps: { roleName: caps.roleName, overrides: caps.overrides, effective: caps.effectiveCapabilities }, carolMe: { role: me.role, isAdmin: me.isAdmin, isOwner: me.isOwner, capabilities: me.capabilities } });
  await ctx.close();
}

(async () => {
  const browser = await L.chromium.launch();
  const S = await L.loginState(browser, "alice");
  if (cmd === "create") await create(browser, S);
  else if (cmd === "assign") await assign(browser, S, arg);
  else throw new Error("cmd: create | assign <role>");
  await browser.close();
  out.console = consoleSink;
  out.tiles = tiles;
  const tag = cmd === "assign" ? `assign-${arg.replace(/\W+/g, "_")}` : cmd;
  fs.writeFileSync(path.join(L.RAW, `roles-${tag}.json`), JSON.stringify(out));
  console.log("console", consoleSink.length, "tiles", tiles.length);
})();
