// 662A step 7: register-referenced crops kinks/<K-id>-<slug>.png, cut from the raw walk tiles in
// .tmp/662A-walk-raw (never committed). Chromium only (no ImageMagick / sharp on this machine): each crop
// is an HTML page holding the raw PNG as a data URL inside an overflow-hidden box, screenshotted by element.
// A caption strip names the row, persona, viewport, theme and the raw file it came from.
// Usage (worktree root): NODE_OPTIONS="" node qa_cycle/evidence/phase91-ui/kinks/scripts/07-register-crops.cjs [--only=<regex>]
const path = require("path");
const fs = require("fs");
const L = require("./lib.cjs");

const ONLY = (process.argv.find((a) => a.startsWith("--only=")) || "").slice(7);
const pngSize = (f) => {
  const b = fs.readFileSync(f);
  return { w: b.readUInt32BE(16), h: b.readUInt32BE(20) };
};

// One entry per register row that gets its own PNG (≤ 25; the rest point at contact-sheet tiles).
// clip = {x, y, w, h} in raw-tile CSS pixels (deviceScaleFactor 1).
const CROPS = [
  { id: "K-06", slug: "matters-table-1440", caption: "K-06 · /matters · owner · 1440 · light — table wider than its container, Next-in-diary cut, row actions out of view", parts: [{ raw: "S1/matters__alice-1440-light.png", clip: { x: 256, y: 270, w: 1184, h: 520 } }] },
  { id: "K-11", slug: "saved-view-client-column", caption: "K-11 · /clients saved view '662A K-11 view' · owner · 1024 · light — Client th 16 px, overlaps LIFECYCLE, names 0 px", parts: [{ raw: "S8/K-11-saved-view-1024-light.png", clip: { x: 264, y: 90, w: 760, h: 678 } }] },
  { id: "K-45", slug: "audit-entity-link-404", caption: "K-45 · Settings › Firm › Audit trail · owner · 1440 · light — 'Matter closure …' rendered as a link to a route with no page (404)", parts: [{ raw: "S1/settings-firm--audit-trail__alice-1440-light.png", clip: { x: 960, y: 940, w: 480, h: 300 } }] },
  { id: "K-47", slug: "save-view-dialog-offscreen", caption: "K-47 · /matters Save View (Configure Filters) · owner · 1440×900 · light — dialog runs off the top and bottom, no title, no Next", parts: [{ raw: "S7/matters__dlg-save-view__alice-1440-light.png", clip: { x: 256, y: 0, w: 928, h: 900 } }] },
  { id: "K-48", slug: "client-hero-dead-edit", caption: "K-48 · /clients/[id] · owner · 1440 · light — the 'Edit' smart action opens nothing", parts: [{ raw: "S1/clients-id__alice-1440-light.png", clip: { x: 256, y: 140, w: 1184, h: 160 } }] },
  { id: "K-49", slug: "info-request-dialog-overflow", caption: "K-49 · matter Requests tab · Create Information Request · owner · 1440 · light — selects and buttons outside the 512 px panel", parts: [{ raw: "S7/matters-id__dlg-new-request__alice-1440-light.png", clip: { x: 440, y: 90, w: 820, h: 720 } }] },
  { id: "K-63", slug: "member-404-crash-panel", caption: "K-63 · /invoices · member · 1440 · light — expected 404 rendered as 'Something went wrong', no h1", parts: [{ raw: "S1/invoices__carol-1440-light.png", clip: { x: 256, y: 0, w: 1184, h: 560 } }] },
  { id: "K-65", slug: "fee-run-empty-title", caption: "K-65 · /invoices/billing-runs/[id] · owner · 1440 · light — h1 empty (fee run has no name), status pill alone", parts: [{ raw: "S1/invoices-billing-runs-id__alice-1440-light.png", clip: { x: 256, y: 80, w: 1184, h: 300 } }] },
  { id: "K-66", slug: "disbursements-closed-matter-dash", caption: "K-66 · /legal/disbursements · owner · 1440 · dark — Matter '—' for disbursements on closed matters", parts: [{ raw: "S1/legal-disbursements__alice-1440-dark.png", clip: { x: 280, y: 300, w: 1140, h: 360 } }] },
  { id: "K-67", slug: "reminder-every-days", caption: "K-67 · /information-requests/[id] · owner · 1440 · light — 'Reminder Interval: Every days'", parts: [{ raw: "S1/information-requests-id__alice-1440-light.png", clip: { x: 280, y: 140, w: 1140, h: 260 } }] },
  { id: "K-68", slug: "dsar-two-received-dates", caption: "K-68 · /compliance/requests/[id] · owner · 1440 · light — Requested 26 Nov (header) vs Request Received 25 Nov (timeline)", parts: [{ raw: "S1/compliance-requests-id__alice-1440-light.png", clip: { x: 280, y: 150, w: 1140, h: 360 } }] },
  { id: "K-88", slug: "timesheet-empty-week", caption: "K-88 · /my-work/timesheet · owner · 1440 · light — week grid empty although 10 h 30 m is logged on Wed 25", parts: [{ raw: "S1/my-work-timesheet__alice-1440-light.png", clip: { x: 256, y: 80, w: 1184, h: 300 } }] },
  { id: "K-89", slug: "client-ledgers-columns-dont-add-up", caption: "K-89 · /trust-accounting/client-ledgers · owner · 1440 · light — QuickCollect: Trust Balance R 15 000 beside Deposits R 45 000, no outflows", parts: [{ raw: "S1/trust-accounting-client-ledgers__alice-1440-light.png", clip: { x: 280, y: 250, w: 1130, h: 290 } }] },
  { id: "K-90", slug: "trust-transactions-1024", caption: "K-90 · /trust-accounting/transactions · owner · 1024 · light — Actions column out of view, Type pill clipped, 7-line description", parts: [{ raw: "S1/trust-accounting-transactions__alice-1024-light.png", clip: { x: 280, y: 830, w: 744, h: 620 } }] },
  { id: "K-100", slug: "settings-content-456px", caption: "K-100 · Settings › Matters › Checklists · owner · 1024 · light — 456 px content column, rows wrap per word, columns out of view", parts: [{ raw: "S1/settings-matters--checklists__alice-1024-light.png", clip: { x: 500, y: 60, w: 524, h: 760 } }] },
  { id: "K-102", slug: "create-role-dialog-offscreen", caption: "K-102 · Settings › Firm › Roles · Create Role · owner · 1440×900 · light — dialog 986 px tall, title cut, buttons at the bottom edge", parts: [{ raw: "S7/settings-firm--roles__dlg-new-role__alice-1440-light.png", clip: { x: 440, y: 0, w: 560, h: 900 } }] },
  { id: "K-103", slug: "roles-no-default-capabilities", caption: "K-103 / K-104 · Settings › Firm › Roles · owner · 1024 · light — Owner/Admin 'No default capabilities'; Member badges past the card", parts: [{ raw: "S1/settings-firm--roles__alice-1024-light.png", clip: { x: 500, y: 150, w: 524, h: 450 } }] },
  { id: "K-110", slug: "x5-tab-switch-loses-edit", caption: "K-110 (X5) · Settings › Firm › General · owner · 1440 · light — 1: typed '662A-X5'; 2: after Roles and back, the field is empty", parts: [
    { raw: "S8/X5-1-typed.png", clip: { x: 544, y: 470, w: 880, h: 230 }, label: "1 · typed, not saved" },
    { raw: "S8/X5-2-after-tab-round-trip.png", clip: { x: 544, y: 470, w: 880, h: 230 }, label: "2 · after clicking Roles, then General" },
  ] },
  { id: "K-112", slug: "template-untitled-clauses", caption: "K-112 · template editor (Engagement Letter — Litigation) · owner · 1440 · light — 5 × 'Untitled Clause / No content available'", parts: [{ raw: "S1/settings-documents-templates-id-edit__alice-1440-light.png", clip: { x: 560, y: 1760, w: 860, h: 420 } }] },
  { id: "K-113", slug: "member-integrations-write-controls", caption: "K-113 · Settings › Connections › Integrations · member · 1440 · light — write controls offered, raw 403 text in red", parts: [{ raw: "S1/settings-connections--xero__carol-1440-light.png", clip: { x: 540, y: 250, w: 900, h: 850 } }] },
];

(async () => {
  const list = CROPS.filter((c) => !ONLY || new RegExp(ONLY).test(c.id));
  if (CROPS.length > 25) throw new Error(`crop budget exceeded: ${CROPS.length} > 25`);
  const browser = await L.chromium.launch();
  const page = await browser.newPage({ viewport: { width: 1500, height: 1000 }, deviceScaleFactor: 1 });
  for (const c of list) {
    const figs = c.parts.map((p) => {
      const f = path.join(L.RAW, p.raw);
      const s = pngSize(f);
      const { x, y, w, h } = p.clip;
      if (x < 0 || y < 0 || x + w > s.w || y + h > s.h) throw new Error(`${c.id}: clip outside ${p.raw} (${s.w}×${s.h})`);
      const src = "data:image/png;base64," + fs.readFileSync(f).toString("base64");
      const label = `${p.label ? p.label + " · " : ""}raw ${p.raw} · clip ${x},${y} ${w}×${h}`;
      return `<figure><figcaption>${label}</figcaption><div class="box" style="width:${w}px;height:${h}px"><img src="${src}" style="left:${-x}px;top:${-y}px;width:${s.w}px;height:${s.h}px"></div></figure>`;
    });
    const html = `<!doctype html><html><head><style>
      body{margin:0;background:#fff;font:12px/1.4 -apple-system,system-ui,sans-serif;color:#111}
      #sheet{display:inline-block;padding:8px;background:#fff}
      h1{font-size:13px;font-weight:600;margin:0 0 6px;max-width:1440px}
      figure{margin:0 0 8px}
      figcaption{color:#444;margin:0 0 3px}
      .box{position:relative;overflow:hidden;outline:1px solid #c33}
      .box img{position:absolute;max-width:none}
    </style></head><body><div id="sheet"><h1>${c.caption}</h1>${figs.join("")}<div style="color:#666">662A walk · head 16423ca4b · 2026-11-25</div></div></body></html>`;
    await page.setContent(html, { waitUntil: "load" });
    const out = path.join(L.EV, `${c.id}-${c.slug}.png`);
    await page.locator("#sheet").screenshot({ path: out });
    console.log(`${c.id} -> ${path.relative(L.EV, out)} (${Math.round(fs.statSync(out).size / 1024)} KB)`);
  }
  await browser.close();
  console.log(`ALL DONE 07-register-crops ${list.length} crops`);
})().catch((e) => {
  console.error(e);
  process.exit(1);
});
