// 663A OBS-before step 4: build the committed evidence from .tmp/663A-before-raw (03-capture.cjs):
// one contact sheet per surface (rows Alice/Carol x tiles 1024L 1024D 1440L 1440D, scaled 0.5), one
// crop sheet of every measured box at 1440 light, and the single-line measurements.json,
// console.json and sel-compat.json (e2e grep via `git grep -n` + live-DOM titles/testids).
// Usage (worktree root): NODE_OPTIONS="" node qa_cycle/evidence/phase91-ui/663A/before/scripts/04-assemble.cjs
const path = require("path");
const fs = require("fs");
const { execFileSync } = require("child_process");
const C = require("../../../661B/walk/scripts/common.cjs");
const RAW = path.join(C.W, ".tmp/663A-before-raw");
const OUT = path.resolve(__dirname, "..");
const WHO = { alice: "Alice (owner)", carol: "Carol (member)" };
const SURF = {
  "task-sheet-time-entries": "Task Sheet — Time Entries tab (fresh task, no entries)",
  "task-sheet-comments": "Task Sheet — Comments tab (fresh task, no comments)",
  "matter-fields-tab": "Matter › Fields tab (matter with all field groups removed)",
  "client-fields-tab": "Client › Fields tab (client with all field groups removed)",
  "fee-note-custom-fields": "Fee-note detail — custom-fields section (org has no INVOICE fields)",
  "my-work": "My Work (/my-work) — full page, clipped at 2400px",
  "ai-reviews-pending": "AI reviews — Pending tab",
  "ai-reviews-history": "AI reviews — History tab",
};
const esc = (s) => String(s).replace(/[&<>]/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;" })[c]);
const rd = (k, who, w, t) => { const f = path.join(RAW, `${k}__${who}-${w}-${t}.json`); return fs.existsSync(f) ? JSON.parse(fs.readFileSync(f, "utf8")) : null; };
const short = (e) => `"${e.title}" ${e.variant || e.kind.split(" ")[0]} ${Math.round(e.h)}px <${e.titleTag}>`;

async function render(browser, outFile, html) {
  const p = await browser.newPage({ viewport: { width: 800, height: 600 } });
  await p.setContent(html, { waitUntil: "load" });
  await p.screenshot({ path: outFile, fullPage: true });
  await p.close();
}
const CSS = `<style>body{margin:0;background:#777;font:12px/1.25 -apple-system,Helvetica,sans-serif}h2{margin:6px 8px;color:#fff;font-size:15px}
.row{display:flex;gap:6px;padding:4px 8px;align-items:flex-start}.lab{writing-mode:vertical-rl;transform:rotate(180deg);color:#fff;font-weight:700;font-size:13px}
figure{margin:0;background:#fff}figcaption{padding:3px 5px;font-weight:600;overflow-wrap:anywhere;font-size:11px}img{display:block}.miss{padding:30px 8px;color:#a00}</style>`;

(async () => {
  const browser = await C.chromium.launch();
  const files = [];
  for (const [k, title] of Object.entries(SURF)) {
    const rows = ["alice", "carol"].map((who) => {
      const tiles = [];
      for (const w of [1024, 1440]) for (const t of ["light", "dark"]) {
        const j = rd(k, who, w, t);
        const png = path.join(RAW, `${k}__${who}-${w}-${t}.png`);
        const cap = `${who} ${w} ${t} — ${j && j.empties && j.empties.length ? j.empties.map(short).join(" · ") : j && j.deniedText ? "no empty state: " + j.deniedText.replace(/^AI Reviews/, "") : j && j.console && j.console.length ? "no empty state: page error (" + j.console[0].text.slice(0, 70) + ")" : "no empty state"}`;
        const tw = w / 2;
        tiles.push(fs.existsSync(png) ? `<figure style="width:${tw}px"><figcaption>${esc(cap)}</figcaption><img style="width:${tw}px" src="data:image/png;base64,${fs.readFileSync(png).toString("base64")}"></figure>` : `<figure style="width:${tw}px"><figcaption>${esc(cap)}</figcaption><div class="miss">not captured</div></figure>`);
      }
      return `<div class="row"><div class="lab">${WHO[who]}</div>${tiles.join("")}</div>`;
    });
    const f = path.join(OUT, `sheet-${k}.png`);
    await render(browser, f, `<html><body>${CSS}<h2>663A BEFORE · ${esc(title)} · columns 1024 light · 1024 dark · 1440 light · 1440 dark (tiles at 0.5x)</h2>${rows.join("")}</body></html>`);
    files.push(f);
  }
  // ---- measurements + crop sheet (1440 light; Alice required, Carol recorded too)
  const meas = { capturedAt: new Date().toISOString(), viewport: "1440x900", theme: "light", method: "getBoundingClientRect() of the EmptyState root ([data-slot=empty-state]) / local look-alike root / hand-rolled block, in its real container, before any scroll", owner: [], member: [] };
  const crops = [];
  for (const k of [...Object.keys(SURF), "dashboard-my-tasks-widget"]) for (const who of ["alice", "carol"]) {
    const j = rd(k, who, 1440, "light");
    if (!j) continue;
    const list = (j.empties || []).map((e, i) => ({ surface: k, title: e.title, kind: e.kind, variant: e.variant, h: e.h, w: e.w, titleTag: e.titleTag, titleRole: e.titleRole, container: e.container, ancestorTestids: e.ancestorTestids, over212: e.h > 212, crop: j.crops && j.crops[i] ? path.basename(j.crops[i]) : null }));
    if (!list.length) list.push({ surface: k, title: null, note: j.deniedText ? "no empty state — " + j.deniedText.replace(/^AI Reviews/, "") : (j.console || []).length ? "no empty state — page error: " + j.console[0].text : k === "dashboard-my-tasks-widget" ? "my-tasks-widget not rendered on this persona's Home" : "no empty state" });
    meas[who === "alice" ? "owner" : "member"].push(...list);
    for (const [i, e] of (j.empties || []).entries()) if (j.crops && j.crops[i]) crops.push([`${who} · ${k} · ${short(e)} · w ${Math.round(e.w)}px · ${e.container}`, j.crops[i]]);
  }
  fs.writeFileSync(path.join(OUT, "measurements.json"), JSON.stringify(meas) + "\n");
  const cf = path.join(OUT, "crops-measured-boxes-1440-light.png");
  await render(browser, cf, `<html><body>${CSS}<h2>663A BEFORE · every measured EmptyState box at 1440x900 light, 1:1 element crops (budget for compact: 212px)</h2><div style="display:flex;flex-wrap:wrap;gap:8px;padding:8px;align-items:flex-start">${crops.map(([cap, f]) => `<figure><figcaption>${esc(cap)}</figcaption><img src="data:image/png;base64,${fs.readFileSync(f).toString("base64")}"></figure>`).join("")}</div></body></html>`);
  files.push(cf);
  // ---- console
  const cons = { capturedAt: meas.capturedAt, pages: [] };
  for (const f of fs.readdirSync(RAW).filter((f) => /__.*\.json$/.test(f)).sort()) {
    const j = JSON.parse(fs.readFileSync(path.join(RAW, f), "utf8"));
    cons.pages.push({ capture: j.label, url: j.url, errors: (j.console || []).filter((m) => m.type === "error" || m.type === "pageerror"), warnings: (j.console || []).filter((m) => m.type === "warning"), hydration: (j.console || []).filter((m) => /hydrat/i.test(m.text)) });
  }
  cons.totals = { captures: cons.pages.length, errors: cons.pages.reduce((a, p) => a + p.errors.length, 0), warnings: cons.pages.reduce((a, p) => a + p.warnings.length, 0), hydration: cons.pages.reduce((a, p) => a + p.hydration.length, 0) };
  fs.writeFileSync(path.join(OUT, "console.json"), JSON.stringify(cons) + "\n");
  // ---- SEL-COMPAT
  const g = (pat, paths, fixed = true) => { try { return execFileSync("git", ["grep", "-n", ...(fixed ? ["-F"] : ["-E"]), pat, "--", ...paths], { cwd: C.W, encoding: "utf8" }).trim().split("\n").filter(Boolean).map((l) => l.replace(/^frontend\//, "").split(":").slice(0, 2).join(":")); } catch (e) { return []; } };
  const E = ["frontend/e2e/tests", "frontend/e2e/helpers"];
  const sel = {
    capturedAt: meas.capturedAt, head: execFileSync("git", ["rev-parse", "--short", "HEAD"], { cwd: C.W, encoding: "utf8" }).trim(),
    e2eGrep: {
      "matter-fields-tab": g("matter-fields-tab", E),
      "My Tasks (h2)": g("My Tasks", E),
      "No time tracked yet": g("No time tracked yet", E),
      "No pending reviews": g("No pending reviews", E),
      "No review history": g("No review history", E),
      "No custom fields configured": g("No custom fields configured", E),
      "Configure Fields": g("Configure Fields", E),
    },
    unitTestGrep: {
      "My Tasks (h2) outside e2e": g("My Tasks", ["frontend/__tests__", "frontend/components/**/__tests__", "frontend/**/*.test.tsx"]),
      "No custom fields configured outside e2e": g("No custom fields configured", ["frontend/__tests__", "frontend/**/*.test.tsx"]),
      "No pending reviews / No review history outside e2e": g("No (pending reviews|review history)", ["frontend/__tests__", "frontend/**/*.test.tsx"], false),
    },
    liveDom: {},
  };
  for (const k of [...Object.keys(SURF), "dashboard-my-tasks-widget"]) for (const who of ["alice", "carol"]) {
    const j = rd(k, who, 1440, "light");
    if (!j) continue;
    sel.liveDom[`${k}|${who}`] = { empties: (j.empties || []).map((e) => ({ title: e.title, tag: e.titleTag, role: e.titleRole, variant: e.variant, kind: e.kind, testid: e.testid, ancestorTestids: e.ancestorTestids })), testidsPresent: Object.fromEntries(Object.entries(j.tidPresent || {}).filter(([, n]) => n)), myTasksH2: (j.headings || []).filter((h) => h === "h2:My Tasks").length || undefined, sheetSections: j.sheetSections || undefined, note: j.deniedText || ((j.console || []).length ? j.console[0].text : undefined) };
  }
  fs.writeFileSync(path.join(OUT, "sel-compat.json"), JSON.stringify(sel) + "\n");
  await browser.close();
  console.log(files.map((f) => path.relative(C.W, f) + " " + Math.round(fs.statSync(f).size / 1024) + "KB").join("\n"));
  console.log(JSON.stringify(sel.e2eGrep), JSON.stringify(sel.unitTestGrep));
  console.log(JSON.stringify(cons.totals));
})().catch((e) => { console.error(e); process.exit(1); });
