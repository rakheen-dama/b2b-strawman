// 666A OBS step 2b: the custom-role persona, through the UI only (as Alice, 1440×900 light). Copied
// from 665F/before/scripts/02-roles.cjs (not edited), adapted.
// Fee-note gates: /invoices, /invoices/collections, /invoices/unbilled → notFound() unless
// isAdmin||isOwner||INVOICING (page.tsx); the four list tiles' debtor-book read needs
// FINANCIAL_VISIBILITY (else only "Paid This Month"); collections' reminder queue needs AI_REVIEW;
// /invoices/billing-runs, /new, /[id] → in-page denial unless isAdmin||isOwner.
// The Create Role dialog offers CAPABILITY_META (lib/capabilities.tsx:23-87): INVOICING is offered
// under the label "Billing" (lib/capabilities.tsx:30-31), FINANCIAL_VISIBILITY as "Financial
// Visibility". So:
//   "666A OBS Invoicing" — "Billing" only = exactly [INVOICING] (HOLDS INVOICING, LACKS
//                          FINANCIAL_VISIBILITY and AI_REVIEW)
//   node 02-roles.cjs create          — idempotent (skips a role whose "Edit <name>" button exists);
//                                       then GET /api/org-roles verifies the exact capability set.
//   node 02-roles.cjs assign "<role>" — Team page → Carol's row → member panel → Role select → Save;
//                                       verified by GET /api/members/{id}/capabilities and Carol's own
//                                       /api/me/capabilities (+ her GET status on the fee-note endpoints).
// Writes $RAW/roles-<step>.json + tiles; 05-assemble merges them.
const path = require("path");
const fs = require("fs");
const L = require("./lib.cjs");

const ROLES = [
  { name: L.NAMES.roleInvoicing, labels: ["Billing"], caps: ["INVOICING"], why: "HOLDS INVOICING (label \"Billing\"), LACKS FINANCIAL_VISIBILITY and AI_REVIEW" },
];
const ENDPOINTS = ["/api/invoices", "/api/invoices/outstanding-summary", "/api/billing-runs?size=100", "/api/collections/debtors?page=0&size=50", "/api/ai/gates?gateType=SEND_COLLECTION_REMINDER&status=PENDING&size=50", "/api/invoices/unbilled-summary?periodFrom=2024-11-27&periodTo=2026-11-27&currency=ZAR", "/api/settings/modules"];
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

async function create(browser, S) {
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
    await p.waitForTimeout(400);
    await dlg.getByLabel("Name", { exact: true }).fill(r.name);
    for (const lb of r.labels) await dlg.getByRole("checkbox", { name: lb, exact: true }).check();
    await shot(p, `team__create-${r.name.replace(/\W+/g, "_")}`, `Alice 1440×900 light — Settings › Firm › Roles › New Role: "${r.name}" with "${r.labels.join('", "')}" checked (before Create)`);
    const submit = dlg.getByRole("button", { name: "Create", exact: true });
    const inView = await submit.evaluate((b) => { const x = b.getBoundingClientRect(); return x.top >= 0 && x.bottom <= innerHeight; });
    if (inView) await submit.click();
    else await submit.dispatchEvent("click");
    await dlg.waitFor({ state: "hidden", timeout: 15000 });
    await p.getByRole("button", { name: `Edit ${r.name}`, exact: true }).waitFor({ timeout: 15000 });
    log({ step: "create", role: r.name, labels: r.labels, why: r.why, submit: inView ? "click" : "dispatchEvent (submit off-screen)", result: "created (Edit button visible)" });
  }
  await ctx.close();
  const t = await L.idpToken("alice");
  const roles = (await L.apiGet(t, "/api/org-roles")).json || [];
  for (const r of ROLES) {
    const found = roles.find((x) => x.name === r.name);
    const ok = !!found && JSON.stringify([...found.capabilities].sort()) === JSON.stringify([...r.caps].sort());
    log({ step: "api-check", role: r.name, id: found && found.id, found: !!found, capabilities: found && found.capabilities, memberCount: found && found.memberCount, ok });
    if (!ok) throw new Error(`custom role "${r.name}" missing or has other capabilities`);
  }
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
  log({ step: "assign", role: roleName, comboBefore: before, comboAfter: after, panelChecked: checked, apiMemberCaps: { roleName: caps.roleName, overrides: caps.overrides, effective: caps.effectiveCapabilities }, carolMe: { role: me.role, isAdmin: me.isAdmin, isOwner: me.isOwner, capabilities: me.capabilities }, carolEndpointStatus: Object.fromEntries(await Promise.all(ENDPOINTS.map(async (e) => [e, (await L.apiGet(ct, e)).status]))) });
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
