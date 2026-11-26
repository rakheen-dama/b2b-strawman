// 665A OBS-AFTER copy of ../../before/scripts/02-roles.cjs. Adaptations (README "Scripts"):
//   B1 K-102: the Create Role dialog is measured at 1024×768, 1280×720 and 1440×900 (before: 1280,
//      1440). DIALOG_BOX also records the scroll body (overflow-y, scrollHeight vs clientHeight), the
//      Cancel button, and whether elementFromPoint at the Create button's centre hits the button.
//      At 1024 and 1280 the body is wheel-scrolled to the bottom and re-measured + shot.
//   B2 the two roles are created at 1280×720 and 1024×768 (before: both at 1440×900) with a real
//      Playwright click on "Create". The dispatchEvent fallback is kept but recorded; it is a FAIL.
//   B3 "custom-roles-section-text" reads the Custom Roles SettingsSection (before: `.space-y-4`).
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
  const body = [...d.querySelectorAll("div")].find((x) => getComputedStyle(x).overflowY === "auto");
  const cancel = [...d.querySelectorAll("button")].find((x) => x.textContent.trim() === "Cancel");
  const cb = cancel ? cancel.getBoundingClientRect() : null;
  const hit = bb ? document.elementFromPoint(bb.left + bb.width / 2, bb.top + bb.height / 2) : null;
  const extra = { body: body ? { overflowY: "auto", scrollHeight: body.scrollHeight, clientHeight: body.clientHeight, scrollTop: Math.round(body.scrollTop), scrolls: body.scrollHeight > body.clientHeight } : null, cancel: cb ? { top: Math.round(cb.top), bottom: Math.round(cb.bottom), inViewport: cb.top >= 0 && cb.bottom <= innerHeight } : null, submitHit: hit ? !!(btn && (hit === btn || btn.contains(hit))) : null, titleBottomInViewport: tb ? tb.bottom <= innerHeight : null };
  return { ...extra, viewport: [innerWidth, innerHeight], dialog: { top: Math.round(b.top), bottom: Math.round(b.bottom), height: Math.round(b.height) }, titleTop: tb ? Math.round(tb.top) : null, submit: bb ? { text: btn.textContent.trim(), top: Math.round(bb.top), bottom: Math.round(bb.bottom), inViewport: bb.top >= 0 && bb.bottom <= innerHeight } : null, titleInViewport: tb ? tb.top >= 0 : null, overflowY: getComputedStyle(d).overflowY, maxHeight: getComputedStyle(d).maxHeight };
};

async function create(browser, S) {
  // B1: 1024×768, 1280×720 (the 628F-BLOCKER size) and 1440×900 — open, measure, scroll the body to
  // the bottom (1024/1280), re-measure, Escape (nothing saved).
  for (const [w, h, theme] of [[1024, 768, "light"], [1024, 768, "dark"], [1280, 720, "light"], [1440, 900, "light"]]) {
    const { ctx, p } = await page(browser, S, w, h, theme);
    lab.label = `roles|create-dialog|${w}-${theme}`;
    await p.goto(`${L.BASE}/settings/firm?tab=roles`, { waitUntil: "load" });
    await p.waitForLoadState("networkidle").catch(() => {});
    await p.getByRole("button", { name: "New Role" }).click();
    await p.getByRole("dialog", { name: "Create Role" }).waitFor();
    await p.waitForTimeout(500);
    const m = await p.evaluate(DIALOG_BOX);
    log({ step: "create-dialog-measure", viewport: `${w}x${h}`, theme, at: "top", ...m });
    await shot(p, `roles__dialog-${w}-${theme}-top`, `K-102 Alice ${w}×${h} ${theme} — Create Role dialog opened: height ${m.dialog.height} (top ${m.dialog.top}, bottom ${m.dialog.bottom}); title ${m.titleInViewport ? "in" : "OUT OF"} viewport; "Create" ${m.submit && m.submit.inViewport ? "in" : "OUT OF"} viewport (${m.submit && m.submit.top}–${m.submit && m.submit.bottom}), hit-test ${m.submitHit}; body scroll ${m.body && m.body.clientHeight}/${m.body && m.body.scrollHeight}`);
    if (w < 1440 && theme === "light") {
      const box = await p.evaluate(() => { const d = document.querySelector('[role="dialog"]'); const b = [...d.querySelectorAll("div")].find((x) => getComputedStyle(x).overflowY === "auto").getBoundingClientRect(); return { x: b.left + b.width / 2, y: b.top + b.height / 2 }; });
      await p.mouse.move(box.x, box.y);
      for (let i = 0; i < 12; i++) await p.mouse.wheel(0, 400);
      await p.waitForTimeout(400);
      const m2 = await p.evaluate(DIALOG_BOX);
      log({ step: "create-dialog-measure", viewport: `${w}x${h}`, theme, at: "after wheel-scrolling the body", ...m2 });
      await shot(p, `roles__dialog-${w}-${theme}-scrolled`, `K-102 Alice ${w}×${h} ${theme} — body wheel-scrolled (scrollTop ${m2.body && m2.body.scrollTop} of ${m2.body && m2.body.scrollHeight - m2.body.clientHeight}); title ${m2.titleInViewport ? "still in" : "OUT OF"} viewport, "Create" ${m2.submit && m2.submit.inViewport ? "in" : "OUT OF"} viewport`);
    }
    await p.keyboard.press("Escape");
    await ctx.close();
  }
  // B2: role 1 is created at 1280×720, role 2 at 1024×768, each with a real click on "Create".
  const SIZES = [[1280, 720], [1024, 768]];
  let ctx, p;
  for (const [ri, r] of ROLES.entries()) {
    if (ctx) await ctx.close();
    const [w, h] = SIZES[ri];
    ({ ctx, p } = await page(browser, S, w, h));
    lab.label = `roles|create|${w}`;
    await p.goto(`${L.BASE}/settings/firm?tab=roles`, { waitUntil: "load" });
    await p.waitForLoadState("networkidle").catch(() => {});
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
    const mf = await p.evaluate(DIALOG_BOX);
    await shot(p, `roles__dialog-${w}-${r.name.replace(/\W+/g, "_")}`, `K-102 Alice ${w}×${h} light — Create Role "${r.name}" (${r.caps.join(", ")}) filled; "Create" ${mf.submit && mf.submit.inViewport ? "in" : "OUT OF"} viewport (${mf.submit && mf.submit.top}–${mf.submit && mf.submit.bottom}), hit-test ${mf.submitHit} → real click`);
    let how = "click";
    if (mf.submit && mf.submit.inViewport && mf.submitHit) await dlg.getByRole("button", { name: "Create", exact: true }).click();
    else { how = "dispatchEvent (submit off-screen)"; await dlg.getByRole("button", { name: "Create", exact: true }).dispatchEvent("click"); }
    await dlg.waitFor({ state: "hidden", timeout: 15000 });
    await p.getByRole("button", { name: `Edit ${r.name}`, exact: true }).waitFor({ timeout: 15000 });
    log({ step: "create", role: r.name, viewport: `${w}x${h}`, caps: r.caps, why: r.why, submit: how, dialogOpened: m, dialogFilled: mf, result: "created (Edit button visible)" });
  }
  await p.reload({ waitUntil: "load" });
  await p.waitForLoadState("networkidle").catch(() => {});
  await shot(p, "roles__after-create-full", "Alice 1024×768 light — Roles tab with the two custom roles (full page)", { fullPage: true });
  const cards = await p.evaluate(() => {
    const h = [...document.querySelectorAll("#main-content h2")].find((x) => x.textContent.trim() === "Custom Roles");
    const sec = h && h.closest('[data-slot="settings-section"]');
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
