// 663B OBS assemble: one contact sheet per surface from capture.cjs's raw tiles — rows Alice (owner) /
// Carol (member), tiles 1440 light / 1440 dark at 0.5x, each captioned with the measured EmptyState
// (variant, w×h, title tag, outerHTML sha256 prefix) or why there is none (gated / populated).
// PHASE=before|after: reads <worktree>/.tmp/663B-raw/<PHASE>/, writes qa_cycle/evidence/phase91-ui/663B/<PHASE>/sheet-*.png.
// Usage (worktree root): PHASE=after NODE_OPTIONS="" node qa_cycle/evidence/phase91-ui/663B/before/scripts/assemble.cjs
const path = require("path");
const fs = require("fs");
const { execFileSync } = require("child_process");

const PHASE = process.env.PHASE;
if (!/^(before|after)$/.test(PHASE || "")) throw new Error("set PHASE=before or PHASE=after");
const ROOT = path.resolve(__dirname, "../../../../../..");
const RAW = path.join(ROOT, ".tmp/663B-raw", PHASE);
const OUT = path.resolve(__dirname, "../..", PHASE);
const COMMON = "qa_cycle/evidence/phase91-ui/661B/walk/scripts/common.cjs";
function loadCommon() {
  try {
    return require(path.join(ROOT, COMMON));
  } catch (e) {
    if (e.code !== "MODULE_NOT_FOUND") throw e;
    const gitCommon = execFileSync("git", ["rev-parse", "--path-format=absolute", "--git-common-dir"], { cwd: ROOT, encoding: "utf8" }).trim();
    return require(path.join(path.dirname(gitCommon), COMMON));
  }
}
const C = loadCommon();

const WHO = { alice: "Alice (owner)", carol: "Carol (member)" };
const SURF = {
  activity: "Matter › Activity — activity-feed.tsx (populated: no empty state reachable, see README)",
  budget: "Matter › Fees · Fee estimate — budget-panel.tsx",
  documents: "Matter › Documents — documents-panel.tsx",
  "time-summary": "Matter › Time — time-summary-panel.tsx",
  "customer-rates": "Client › Rates — customer-rates-tab.tsx (Carol: tab not rendered, isAdmin-gated)",
  "matter-clients": "Matter › Clients — project-customers-panel.tsx (substitute for Activity)",
};
const esc = (s) => String(s).replace(/[&<>]/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;" })[c]);
const rd = (k, who, t) => { const f = path.join(RAW, `${k}__${who}-1440-${t}.json`); return fs.existsSync(f) ? JSON.parse(fs.readFileSync(f, "utf8")) : null; };
const caption = (j, who, t) => {
  const head = `${who} 1440 ${t}${j && j.dark !== (t === "dark") ? " [THEME MISMATCH]" : ""}`;
  if (!j) return `${head} — not captured`;
  if (j.error) return `${head} — ERROR ${j.error.slice(0, 90)}`;
  const x = j.target;
  if (x) return `${head} — "${x.title}" · ${x.variant} · ${Math.round(x.w)}×${Math.round(x.h)} · <${x.titleTag}> ${x.titleStyle.fontSize} · pad ${x.boxStyle.paddingTop} gap ${x.boxStyle.rowGap} · sha ${x.sha256.slice(0, 12)}`;
  if (j.gated) return `${head} — no empty state: tab not rendered for this persona (fell back to ?tab=${j.activeTab})`;
  return `${head} — no empty state: tab populated ("${(j.panelText || "").slice(0, 70)}…")`;
};
const CSS = `<style>body{margin:0;background:#777;font:12px/1.25 -apple-system,Helvetica,sans-serif}h2{margin:6px 8px;color:#fff;font-size:15px}
.row{display:flex;gap:6px;padding:4px 8px;align-items:flex-start}.lab{writing-mode:vertical-rl;transform:rotate(180deg);color:#fff;font-weight:700;font-size:13px}
figure{margin:0;background:#fff;width:720px}figcaption{padding:3px 5px;font-weight:600;overflow-wrap:anywhere;font-size:11px}img{display:block;width:720px}.miss{padding:30px 8px;color:#a00}</style>`;

(async () => {
  const browser = await C.chromium.launch();
  const files = [];
  for (const [k, title] of Object.entries(SURF)) {
    const rows = ["alice", "carol"].map((who) => {
      const tiles = ["light", "dark"].map((t) => {
        const j = rd(k, who, t);
        const png = path.join(RAW, `${k}__${who}-1440-${t}.png`);
        const img = fs.existsSync(png) ? `<img src="data:image/png;base64,${fs.readFileSync(png).toString("base64")}">` : `<div class="miss">not captured</div>`;
        return `<figure><figcaption>${esc(caption(j, who, t))}</figcaption>${img}</figure>`;
      });
      return `<div class="row"><div class="lab">${WHO[who]}</div>${tiles.join("")}</div>`;
    });
    const f = path.join(OUT, `sheet-${k}.png`);
    const p = await browser.newPage({ viewport: { width: 800, height: 600 } });
    await p.setContent(`<html><body>${CSS}<h2>663B ${PHASE.toUpperCase()} · ${esc(title)} · 1440×900 light · dark (tiles at 0.5x)</h2>${rows.join("")}</body></html>`, { waitUntil: "load" });
    await p.screenshot({ path: f, fullPage: true });
    await p.close();
    files.push(f);
  }
  await browser.close();
  console.log(files.map((f) => path.relative(ROOT, f) + " " + Math.round(fs.statSync(f).size / 1024) + "KB").join("\n"));
})().catch((e) => { console.error(e); process.exit(1); });
