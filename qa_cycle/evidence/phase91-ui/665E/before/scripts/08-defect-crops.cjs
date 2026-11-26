// 665E OBS step 8: element screenshots (crops) of the defects seen in 01 / 06, Alice, 1024×768 light,
// read-only (page loads + the Email inner-tab click only):
//   defect__templates-table-1024      first Templates category (h2 + table scroller): STATUS / ACTIONS out of view
//   defect__delivery-log-table-1024   Email › Delivery Log table scroller: PROVIDER MESSAGE ID / ERROR out of view
//   defect__editor-topbar-1024        editable editor top bar (ORG_CUSTOM clone from the spec run): overflows main
//   defect__editor-topbar-1024-viewport  the same page, viewport shot (document scrolls sideways)
// Writes $RAW/crops.json + tiles; 05-assemble copies them as crop-*.png.
const path = require("path");
const fs = require("fs");
const L = require("./lib.cjs");

(async () => {
  const tiles = [];
  const out = { at: new Date().toISOString(), crops: [] };
  const a = await L.idpToken("alice");
  const ev = L.list((await L.apiGet(a, "/api/audit-events?eventType=template.cloned&size=50")).json).sort((x, y) => (x.occurredAt < y.occurredAt ? 1 : -1))[0];
  const browser = await L.chromium.launch();
  const S = await L.loginState(browser, "alice");
  const ctx = await L.newCtx(browser, S, { width: 1024, height: 768, theme: "light", extra: L.EXTRA });
  const p = await ctx.newPage();
  const settle = async () => { await p.waitForLoadState("networkidle").catch(() => {}); await p.waitForTimeout(600); };
  const crop = async (key, loc, caption) => {
    const f = path.join(L.RAW, `${key}.png`);
    await loc.screenshot({ path: f });
    tiles.push([key, f, caption]);
    out.crops.push({ key, caption });
  };
  await p.goto(`${L.BASE}/settings/documents?tab=templates`, { waitUntil: "load" });
  await settle();
  const block = p.locator("#main-content h2").first().locator("xpath=..");
  await crop("defect__templates-table-1024", block, "Alice 1024×768 light — Templates, first category table: scroller 496 px, table 666 px; SOURCE cut 22 px, STATUS and ACTIONS fully out of view at scroll 0 (no scroll affordance)");
  await p.goto(`${L.BASE}/settings/documents?tab=email`, { waitUntil: "load" });
  await settle();
  await p.getByRole("tab", { name: "Delivery Log" }).click();
  await settle();
  await crop("defect__delivery-log-table-1024", p.locator("#main-content table").first().locator("xpath=.."), "Alice 1024×768 light — Email › Delivery Log: scroller 494 px, table 1045 px; TEMPLATE cut, STATUS / PROVIDER MESSAGE ID / ERROR out of view at scroll 0");
  await p.goto(`${L.BASE}/settings/documents/templates/${ev.entityId}/edit`, { waitUntil: "load" });
  await settle();
  await crop("defect__editor-topbar-1024", p.locator("#main-content div.border-b.pb-4").first(), "Alice 1024×768 light — editable template editor (ORG_CUSTOM clone) top bar: 731 px content in a 496 px column; the name input is clipped and the buttons spill out of main (document h-overflow 203 px)");
  const f = path.join(L.RAW, "defect__editor-topbar-1024-viewport.png");
  await p.screenshot({ path: f });
  tiles.push(["defect__editor-topbar-1024-viewport", f, "Alice 1024×768 light — the editable editor, viewport: the Save button sits past the right edge (document scrolls sideways)"]);
  out.crops.push({ key: "defect__editor-topbar-1024-viewport" });
  await browser.close();
  out.tiles = tiles;
  fs.writeFileSync(path.join(L.RAW, "crops.json"), JSON.stringify(out));
  console.log("crops", tiles.length);
})();
