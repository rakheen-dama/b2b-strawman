// 665E OBS-AFTER copy of ../../before/scripts/08-defect-crops.cjs (that file is not edited). Changes:
//   (1) the Templates crop is the whole [data-slot=settings-section] holding the "Engagement Letter"
//       table (the before selector `h2.first()..` now lands on the CardHeader only); it holds the
//       uploaded Word row, so it also shows the K-117 Format-column widening;
//   (2) NEW crop `k117-align-1024` / `k117-align-1440`: a clip from the "Cover Letter" section top to
//       the "Engagement Letter" section bottom, so the two tables' header x can be compared by eye;
//   (3) the Delivery Log crop is the table's [data-slot=settings-section];
//   (4) the editor crops are kept (top bar + viewport) and taken at 1024 (K-118 after-state).
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
  const sec = (name) => p.locator('#main-content [data-slot="settings-section"]').filter({ has: p.getByRole("heading", { level: 2, name, exact: true }) });
  await crop("defect__templates-table-1024", sec("Engagement Letter"), "Alice 1024×768 light — Templates › Engagement Letter section (holds the Word row): scroller 446 px, table 676 px; SOURCE / STATUS / ACTIONS out of view at scroll 0 (F-665E-B1, D-44); the Format column is 85 px here vs 75 px in the Tiptap-only tables (F-665E-A1)");
  const clipAlign = async (key, caption) => {
    // fullPage clip = document coordinates: viewport box + scrollY. Scroll to the top first so the
    // sticky app header is not painted over the clip.
    await p.evaluate(() => window.scrollTo(0, 0));
    await p.waitForTimeout(200);
    const sy = await p.evaluate(() => window.scrollY);
    const a2 = await sec("Cover Letter").boundingBox();
    const b2 = await sec("Engagement Letter").boundingBox();
    const f = path.join(L.RAW, `${key}.png`);
    await p.screenshot({ path: f, clip: { x: a2.x - 4, y: a2.y + sy - 4, width: Math.min(a2.width + 8, p.viewportSize().width - a2.x + 4), height: b2.y + b2.height - a2.y + 8 }, fullPage: true });
    tiles.push([key, f, caption]);
    out.crops.push({ key, caption });
  };
  await clipAlign("defect__k117-align-1024", "Alice 1024×768 light — K-117 alignment: Cover Letter (Tiptap only) above Engagement Letter (with the Word row). FORMAT 75 vs 85 px wide; ENTITY TYPE / SOURCE / STATUS / ACTIONS headers sit 9–10 px further right in the lower table");
  await p.setViewportSize({ width: 1440, height: 900 });
  await p.goto(`${L.BASE}/settings/documents?tab=templates`, { waitUntil: "load" });
  await settle();
  await clipAlign("defect__k117-align-1440", "Alice 1440×900 light — K-117 alignment: Cover Letter above Engagement Letter (Word row). FORMAT starts at x=935 vs 931 (Name 366 vs 362 px); later headers aligned");
  await p.setViewportSize({ width: 1024, height: 768 });
  await p.goto(`${L.BASE}/settings/documents?tab=email`, { waitUntil: "load" });
  await settle();
  await p.getByRole("tab", { name: "Delivery Log" }).click();
  await settle();
  await crop("defect__delivery-log-table-1024", p.locator('#main-content [data-slot="settings-section"]').filter({ has: p.locator("table") }).first(), "Alice 1024×768 light — Email › Delivery Log (after): scroller 446 px, table 540 px; PROVIDER MESSAGE ID cut 34 px, ERROR out of view at scroll 0 (before: 494 / 1045 px)");
  await p.goto(`${L.BASE}/settings/documents/templates/${ev.entityId}/edit`, { waitUntil: "load" });
  await settle();
  await crop("defect__editor-topbar-1024", p.locator("#main-content div.border-b.pb-4").first(), "Alice 1024×768 light — editable template editor (ORG_CUSTOM clone) top bar, AFTER (K-118): wraps to 2 rows inside the 496 px column, scrollWidth = clientWidth, document h-overflow 0; the w-64 name input still clips the long name");
  const f = path.join(L.RAW, "defect__editor-topbar-1024-viewport.png");
  await p.screenshot({ path: f });
  tiles.push(["defect__editor-topbar-1024-viewport", f, "Alice 1024×768 light — the editable editor, viewport, AFTER: the Save button is inside the viewport (x 781–845), no sideways scroll"]);
  out.crops.push({ key: "defect__editor-topbar-1024-viewport" });
  await browser.close();
  out.tiles = tiles;
  fs.writeFileSync(path.join(L.RAW, "crops.json"), JSON.stringify(out));
  console.log("crops", tiles.length);
})();
