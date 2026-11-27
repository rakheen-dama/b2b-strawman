// 665F OBS step 8: element screenshots (crops) of the defects seen in 01 / 03, read-only (page loads +
// the Packs inner-tab click only). Adapted from 665E/before/scripts/08-defect-crops.cjs (not edited).
//   defect__integrations-grid-1024     Alice 1024 light — Integrations card grid: card header badges spill
//                                      past the cards (K-100 / F-664A-1); document scrolls sideways
//   defect__automations-table-1024     Alice 1024 light — Automations rules table 1092 px in a 496 px
//                                      scroller (K-114); ENABLED switch out of view
//   defect__executions-table-1024      Alice 1024 light — Execution Log table: Trigger column shows the Java
//                                      event class name (K-122), STATUS etc. out of view
//   defect__notifications-other-1440   Alice 1440 light — Notifications "OTHER" group: 13 raw enum names
//                                      (K-115); slate-500 category header on slate-50 (K-85)
//   defect__packs-installed-1440       Alice 1440 light — Packs › Installed: raw packId titles, no
//                                      description (K-121)
//   defect__packs-grid-1024            Alice 1024 light — Packs card grid: cards spill past the panel
//   defect__carol-integrations-alert-1440  Carol (Member) 1440 light — Integrations: "Insufficient permissions
//                                      for this operation" alert inside a card
// Writes $RAW/crops.json + tiles; 05-assemble copies them as crop-*.png.
const path = require("path");
const fs = require("fs");
const L = require("./lib.cjs");

(async () => {
  const tiles = [];
  const out = { at: new Date().toISOString(), crops: [] };
  const browser = await L.chromium.launch();
  const crop = async (p, key, loc, caption) => {
    const f = path.join(L.RAW, `${key}.png`);
    if (!(await loc.count())) { out.crops.push({ key, missing: true }); return; }
    await loc.first().screenshot({ path: f });
    tiles.push([key, f, caption]);
    out.crops.push({ key, caption });
  };
  const open = async (who, w, h) => {
    const S = await L.loginState(browser, who);
    const ctx = await L.newCtx(browser, S, { width: w, height: h, theme: "light", extra: L.EXTRA });
    const p = await ctx.newPage();
    return { ctx, p };
  };
  const go = async (p, url) => { await p.goto(`${L.BASE}${url}`, { waitUntil: "load" }); await p.waitForLoadState("networkidle").catch(() => {}); await p.waitForTimeout(700); };

  let { ctx, p } = await open("alice", 1024, 768);
  await go(p, "/settings/connections?tab=xero");
  await crop(p, "defect__integrations-grid-1024", p.locator('#main-content [role="tabpanel"] div.grid').first(), "Alice 1024×768 light — Integrations card grid (2 cols × 234 px in a 496 px panel): the card-header status badges (Not Connected / Not Configured / Manual Payments / Disabled) spill 14–76 px past their cards; the panel scrolls 60 px and the document 28 px sideways (K-100 / F-664A-1)");
  await go(p, "/settings/connections?tab=automations");
  await crop(p, "defect__automations-table-1024", p.locator("#main-content table").first().locator("xpath=.."), "Alice 1024×768 light — Automations rules table: 1092 px in a 496 px scroller; TRIGGER cut, ENABLED / LAST UPDATED / ACTIONS out of view at scroll 0 (K-114)");
  await go(p, "/settings/connections/automations/executions");
  await crop(p, "defect__executions-table-1024", p.locator("#main-content table").first().locator("xpath=.."), "Alice 1024×768 light — Execution Log table: 897 px in a 494 px scroller; TRIGGER shows the Java class name \"CustomerStatusChangedEvent\" (the rules list says \"Client Status\") (K-122); STATUS / CONDITIONS / ACTIONS / DURATION out of view");
  await go(p, "/settings/connections?tab=packs");
  await crop(p, "defect__packs-grid-1024", p.locator('#main-content [role="tabpanel"] div.grid').first(), "Alice 1024×768 light — Packs › Available card grid (3 cols in a 496 px panel): cards spill 7–14 px past the panel (panel scrollWidth 503)");
  await ctx.close();

  ({ ctx, p } = await open("alice", 1440, 900));
  await go(p, "/settings/connections?tab=notifications");
  await crop(p, "defect__notifications-other-1440", p.locator("#main-content p.uppercase").filter({ hasText: /^Other$/i }).locator("xpath=../.."), "Alice 1440×900 light — Notifications \"OTHER\" group: 13 raw enum names (COURT_DATE_REMINDER … CASH_DIGEST) (K-115); category header text-slate-500 on bg-slate-50 = 4.48:1 (K-85)");
  await go(p, "/settings/connections?tab=packs");
  await p.getByRole("tab", { name: /Installed/i }).click();
  await p.waitForTimeout(1200);
  await crop(p, "defect__packs-installed-1440", p.locator('#main-content [role="tabpanel"][data-state="active"] div.grid').first(), "Alice 1440×900 light — Packs › Installed: the automation packs' cards are titled with the raw packId (\"automation-common\", \"automation-legal-za\"), with no description and a \"Legal - Za\" badge (K-121)");
  await ctx.close();

  ({ ctx, p } = await open("carol", 1440, 900));
  await go(p, "/settings/connections?tab=xero");
  const alert = p.locator('#main-content [role="alert"], #main-content [role="status"]').filter({ hasText: "Insufficient permissions" });
  out.carolAlert = await alert.evaluateAll((els) => els.map((e) => ({ role: e.getAttribute("role"), text: e.textContent.trim(), cls: e.className, card: (e.closest('[data-slot="card"]') && (e.closest('[data-slot="card"]').querySelector('[data-slot="card-title"]') || {}).textContent) || null })));
  await crop(p, "defect__carol-integrations-alert-1440", alert.locator('xpath=ancestor::*[@data-slot="card"][1]'), `Carol (Member) 1440×900 light — Integrations: a member sees the manage cards; one shows "Insufficient permissions for this operation" (${JSON.stringify(out.carolAlert.map((x) => x.card))})`);
  await ctx.close();
  await browser.close();
  out.tiles = tiles;
  fs.writeFileSync(path.join(L.RAW, "crops.json"), JSON.stringify(out));
  console.log("crops", tiles.length, JSON.stringify(out.crops.map((c) => c.key + (c.missing ? " MISSING" : ""))), JSON.stringify(out.carolAlert));
})();
