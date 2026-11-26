// 665C OBS step 4b: corrective restore for 04-saves' Naming round trip (04-saves is kept as executed).
// 04-saves assumed an empty pattern restores null; it does not: the action sends `pattern || null`
// (naming-actions.ts:38) and the backend skips a null pattern (OrgSettingsService.java:190
// `if (projectNamingPattern != null)`), so after "" → Save the stored pattern stayed
// "OBS-{YYYY}-{SEQ}" while the page said "Matter naming pattern updated." (F-665C-3).
// A blank pattern is ignored when a matter is created (ProjectFieldService.java:86
// `namingPattern != null && !namingPattern.isBlank()`), so the closest UI restore is a single space:
//   Naming → reload (record what the input shows) → " " → Save Settings → message → reload → record.
// The stored value ends as " " instead of null: the same behaviour (no pattern applied). Recorded.
// Writes $RAW/naming-restore.json + tiles; 05-assemble merges them.
const path = require("path");
const fs = require("fs");
const L = require("./lib.cjs");

const out = { at: new Date().toISOString(), steps: [] };
const tiles = [];
const consoleSink = [];
const lab = { label: "naming-restore" };
const log = (o) => { out.steps.push(o); console.log(JSON.stringify(o)); };

(async () => {
  const browser = await L.chromium.launch();
  const S = await L.loginState(browser, "alice");
  const ctx = await L.newCtx(browser, S, { width: 1440, height: 900, theme: "light", extra: L.EXTRA });
  const p = await ctx.newPage();
  L.watch(p, consoleSink, lab);
  const a = await L.idpToken("alice");
  const stored = async () => ((await L.apiGet(a, "/api/settings")).json || {}).matterNamingPattern;
  const open = async () => { await p.goto(`${L.BASE}/settings/matters?tab=naming`, { waitUntil: "load" }); await p.waitForLoadState("networkidle").catch(() => {}); await p.waitForTimeout(300); };
  await open();
  log({ step: "before", apiMatterNamingPattern: await stored(), inputShows: await p.locator("#project-naming-pattern").inputValue() });
  const f0 = path.join(L.RAW, "save__naming-after-empty-save-reload.png");
  await p.screenshot({ path: f0 });
  tiles.push(["save__naming-after-empty-save-reload", f0, `Alice 1440 light — Naming reloaded after 04-saves' "" → Save ("Matter naming pattern updated."): the input still shows ${JSON.stringify(await p.locator("#project-naming-pattern").inputValue())} (F-665C-3)`]);
  await p.locator("#project-naming-pattern").fill(" ");
  await p.getByRole("button", { name: "Save Settings" }).click();
  await p.getByText("Matter naming pattern updated.").waitFor({ timeout: 15000 });
  log({ step: "saved-space", message: await p.getByText("Matter naming pattern updated.").innerText(), apiMatterNamingPattern: await stored() });
  await open();
  log({ step: "after-reload", apiMatterNamingPattern: JSON.stringify(await stored()), inputShows: JSON.stringify(await p.locator("#project-naming-pattern").inputValue()) });
  await ctx.close();
  await browser.close();
  out.console = consoleSink;
  out.tiles = tiles;
  fs.writeFileSync(path.join(L.RAW, "naming-restore.json"), JSON.stringify(out));
})();
