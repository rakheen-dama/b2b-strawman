// 665E OBS-AFTER copy of ../../before/scripts/06-custom-editor.cjs (that file is not edited). Changes,
// additive: (1) PROBE also records the Save button's box and whether it lies fully inside the viewport
// (`saveBox`, `saveInViewport`), and the top bar's two groups' boxes / line count (`topBarGroups`,
// K-118 wrap); (2) Alice also runs at 390×844 light (found.md routing: "no page-level horizontal
// scroll at 1024 and 390"); (3) viewport (non-full-page) shots at 1024 / 390 light for the crops.
// 665E OBS step 6 (run AFTER the spec baseline): the editable template editor. The seed has no
// ORG_CUSTOM template (all 22 are PLATFORM, so the editor is read-only with an h1 for everyone), and
// the OBS creates no template itself; the spec run's `template-management.spec` "Clone template"
// test creates one ORG_CUSTOM clone (then soft-deactivates it in afterAll — it is no longer in GET
// /api/templates but GET /api/templates/{id} still answers). Its id is read from the audit log
// (`template.cloned`, newest). Read-only: page loads only, no clicks.
//   Alice (owner): editable branch of TemplateEditorClient (name <input aria-label="Template name">,
//     Save button, contenteditable=true) — 1024/1440 × light/dark.
//   Carol (member): the same template — readOnly because !isAdmin (edit/page.tsx:38) — 1024 light.
// Writes $RAW/custom-editor.json + tiles; 05-assemble merges them.
const path = require("path");
const fs = require("fs");
const L = require("./lib.cjs");

const out = { at: new Date().toISOString(), template: null, runs: [] };
const tiles = [];
const consoleSink = [];
const lab = { label: "" };
const PROBE = () => {
  const main = document.querySelector("main#main-content");
  const t = (e) => (e.innerText || e.textContent || "").replace(/\s+/g, " ").trim();
  const se = document.scrollingElement;
  const nameInput = main.querySelector('input[aria-label="Template name"]');
  return {
    h1InMain: [...main.querySelectorAll("h1")].map(t),
    h1Count: main.querySelectorAll("h1").length,
    h1InEditorBody: [...main.querySelectorAll(".ProseMirror h1")].map(t),
    templateNameInput: nameInput ? { value: nameInput.value, width: Math.round(nameInput.getBoundingClientRect().width), clipped: nameInput.scrollWidth > nameInput.clientWidth } : null,
    saveButton: [...main.querySelectorAll("button")].filter((b) => t(b) === "Save").length,
    proseMirror: [...main.querySelectorAll(".ProseMirror")].map((e) => e.getAttribute("contenteditable")),
    topBar: (() => { const b = main.querySelector("div.border-b.pb-4"); if (!b) return null; const r = b.getBoundingClientRect(); return { width: Math.round(r.width), scrollWidth: b.scrollWidth, overflowPx: b.scrollWidth - b.clientWidth, height: Math.round(r.height) }; })(),
    hOverflow: se.scrollWidth > se.clientWidth ? se.scrollWidth - se.clientWidth : 0,
    saveBox: (() => { const b = [...main.querySelectorAll("button")].find((x) => t(x) === "Save"); if (!b) return null; const r = b.getBoundingClientRect(); return { x: Math.round(r.left), right: Math.round(r.right), y: Math.round(r.top), w: Math.round(r.width) }; })(),
    saveInViewport: (() => { const b = [...main.querySelectorAll("button")].find((x) => t(x) === "Save"); if (!b) return null; const r = b.getBoundingClientRect(); return r.left >= 0 && r.right <= document.documentElement.clientWidth; })(),
    viewportW: document.documentElement.clientWidth,
    topBarGroups: (() => { const b = main.querySelector("div.border-b.pb-4"); if (!b) return null; const kids = [...b.children].map((k) => { const r = k.getBoundingClientRect(); return { x: Math.round(r.left), right: Math.round(r.right), y: Math.round(r.top), h: Math.round(r.height), items: [...k.children].map((c) => { const q = c.getBoundingClientRect(); return { tag: c.tagName.toLowerCase(), text: t(c).slice(0, 30) || c.getAttribute("aria-label") || c.getAttribute("value") || "", x: Math.round(q.left), right: Math.round(q.right), y: Math.round(q.top) }; }) }; }); return { cls: b.className, groups: kids, mainRight: Math.round(main.getBoundingClientRect().right) }; })(),
    mainCount: document.querySelectorAll("main").length,
    title: document.title,
  };
};

(async () => {
  const a = await L.idpToken("alice");
  const au = await L.apiGet(a, "/api/audit-events?eventType=template.cloned&size=50");
  const ev = L.list(au.json).sort((x, y) => (x.occurredAt < y.occurredAt ? 1 : -1))[0];
  if (!ev) throw new Error("no template.cloned audit event — run the spec baseline first");
  const d = await L.apiGet(a, `/api/templates/${ev.entityId}`);
  out.template = { id: ev.entityId, clonedAt: ev.occurredAt, originalId: ev.details && ev.details.original_id, detailStatus: d.status, name: d.json && d.json.name, source: d.json && d.json.source, active: d.json && d.json.active, format: d.json && d.json.format };
  console.log(JSON.stringify(out.template));
  const browser = await L.chromium.launch();
  for (const who of ["alice", "carol"]) {
    const S = await L.loginState(browser, who);
    const V = who === "alice" ? [[1024, 768, "light"], [1024, 768, "dark"], [1440, 900, "light"], [1440, 900, "dark"], [390, 844, "light"]] : [[1024, 768, "light"]];
    for (const [w, h, theme] of V) {
      const ctx = await L.newCtx(browser, S, { width: w, height: h, theme, extra: L.EXTRA });
      const p = await ctx.newPage();
      L.watch(p, consoleSink, lab);
      lab.label = `${who}|tpl-edit-custom|${w}-${theme}`;
      const resp = await p.goto(`${L.BASE}/settings/documents/templates/${out.template.id}/edit`, { waitUntil: "load" });
      await p.waitForLoadState("networkidle").catch(() => {});
      await p.waitForTimeout(800);
      const m = await p.evaluate(PROBE);
      const sel = {
        "template-management.spec:112/135/150 getByLabel('Template name')": await p.getByLabel("Template name").count(),
        "template-management.spec:136/154 button 'Save' exact": await p.getByRole("button", { name: "Save", exact: true }).count(),
        "template-management.spec:152 .ProseMirror first contenteditable": (await p.locator(".ProseMirror").count()) ? await p.locator(".ProseMirror").first().getAttribute("contenteditable") : null,
        [`heading h1 '${out.template.name}'`]: await p.getByRole("heading", { level: 1, name: out.template.name }).count(),
        "h1 in #main-content": await p.locator("#main-content h1").count(),
      };
      const key = `s__tpl-edit-custom-${who}-${w}-${theme}`;
      const f = path.join(L.RAW, `${key}.png`);
      await p.screenshot({ path: f, fullPage: true });
      if (who === "alice" && theme === "light" && w !== 1440) { const fv = path.join(L.RAW, `${key}-viewport.png`); await p.screenshot({ path: fv }); tiles.push([`${key}-viewport`, fv, `alice tpl-edit-custom ${w}×${h} light — VIEWPORT shot (K-118: Save in viewport?)`]); }
      tiles.push([key, f, `${who} tpl-edit-custom (ORG_CUSTOM clone, inactive) ${w}×${h} ${theme} — h1 ${JSON.stringify(m.h1InMain)}; name input ${m.templateNameInput ? "yes" : "no"}; Save ${m.saveButton}`]);
      out.runs.push({ who, viewport: `${w}-${theme}`, status: resp && resp.status(), ...m, selCompat: sel });
      console.log(who, w, theme, JSON.stringify({ h1: m.h1InMain, body: m.h1InEditorBody, input: m.templateNameInput, save: m.saveButton, saveBox: m.saveBox, saveInVp: m.saveInViewport, pm: m.proseMirror, top: m.topBar, ov: m.hOverflow, groups: m.topBarGroups && m.topBarGroups.groups.map((g) => `${g.x}-${g.right}@y${g.y}h${g.h}`) }));
      await ctx.close();
    }
  }
  await browser.close();
  out.console = consoleSink;
  out.tiles = tiles;
  fs.writeFileSync(path.join(L.RAW, "custom-editor.json"), JSON.stringify(out));
  console.log("console", consoleSink.length, JSON.stringify([...new Set(consoleSink.map((x) => x.text.slice(0, 90)))]));
})();
