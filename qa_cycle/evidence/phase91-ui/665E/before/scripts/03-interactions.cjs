// 665E OBS step 3: open-and-cancel interactions, through the UI as Alice (1440×900 light; the
// Templates actions menu and the clause sheet also at 1024 light). NOTHING is confirmed or saved:
//   Templates › "Upload Word Template" → dialog "Upload Word Template" → probe → "Cancel".
//   Templates › row "Engagement Letter — General" › actions trigger (an unnamed icon button) → menu
//     items probed → Escape. "Deactivate" is NOT clicked: it has no confirmation dialog
//     (TemplateActionsMenu.tsx:115 onClick={handleDeactivate} → deactivateTemplateAction directly).
//   Templates › Branding › logo delete: the X button exists only when a logo is set
//     (branding-section.tsx:137-155, immediate onDeleteLogo, no dialog) — counted, never clicked.
//   Clauses › "New Clause" → Sheet "New Clause" → probe → "Close".
//   Clauses › first clause "Clause actions" → "View" → Sheet "System Clause" → probe → "Close".
//   Clauses › first clause "Clause actions" → "Clone & Customize" → alertdialog "Clone Clause" →
//     probe → "Cancel". (Every seeded clause is SYSTEM, so the "Deactivate Clause" alertdialog —
//     custom clauses only, clauses-content.tsx:511-516 — is unreachable without creating data.)
//   Clauses › "Expand clause" on the first row (read-only Tiptap body) → probe → collapse.
//   Email › Delivery Log › status filter combobox → options probed → Escape.
// Writes $RAW/interactions.json + tiles; 05-assemble merges them.
const path = require("path");
const fs = require("fs");
const L = require("./lib.cjs");

const out = { at: new Date().toISOString(), steps: [] };
const tiles = [];
const consoleSink = [];
const lab = { label: "" };
const log = (o) => { out.steps.push(o); console.log(JSON.stringify(o).slice(0, 700)); };
const shot = async (p, key, caption, opt = {}) => {
  const f = path.join(L.RAW, `${key}.png`);
  await p.screenshot({ path: f, ...opt });
  tiles.push([key, f, caption]);
};
const settle = async (p) => { await p.waitForLoadState("networkidle").catch(() => {}); await p.waitForTimeout(500); };
const DLG = (sel) => {
  const d = document.querySelector(sel);
  if (!d) return null;
  const t = (e) => (e.innerText || e.textContent || "").replace(/\s+/g, " ").trim();
  const r = d.getBoundingClientRect();
  const lbl = (e) => e.getAttribute("aria-label") || (e.id && document.querySelector(`label[for="${CSS.escape(e.id)}"]`) ? t(document.querySelector(`label[for="${CSS.escape(e.id)}"]`)) : null);
  return {
    role: d.getAttribute("role"),
    name: d.getAttribute("aria-labelledby") ? t(document.getElementById(d.getAttribute("aria-labelledby").split(" ")[0]) || d) : null,
    description: d.getAttribute("aria-describedby") ? t(document.getElementById(d.getAttribute("aria-describedby").split(" ")[0]) || d).slice(0, 200) : null,
    box: { x: Math.round(r.x), y: Math.round(r.y), w: Math.round(r.width), h: Math.round(r.height) },
    headings: [...d.querySelectorAll("h1,h2,h3,[data-slot$=title]")].map((h) => `${h.tagName.toLowerCase()}${h.className.includes("sr-only") ? "(sr-only)" : ""}:${t(h)}`),
    buttons: [...d.querySelectorAll("button,[role=button]")].map((b) => `${b.getAttribute("aria-label") || t(b) || "(unnamed)"}${b.disabled ? "[disabled]" : ""}`),
    controls: [...d.querySelectorAll("input,textarea,select,[role=combobox],[contenteditable]")].filter((e) => e.type !== "hidden").map((e) => ({ tag: e.tagName.toLowerCase(), type: e.type || e.getAttribute("role") || (e.hasAttribute("contenteditable") ? "contenteditable=" + e.getAttribute("contenteditable") : undefined), id: e.id || undefined, testid: e.dataset.testid, label: lbl(e), value: e.type === "file" ? undefined : e.tagName === "SELECT" ? e.value : e.hasAttribute("contenteditable") ? t(e).slice(0, 40) : (e.value || t(e)).slice(0, 60) })),
    scrollable: d.scrollHeight > d.clientHeight + 1,
  };
};

(async () => {
  const browser = await L.chromium.launch();
  const S = await L.loginState(browser, "alice");
  for (const [w, h] of [[1440, 900], [1024, 768]]) {
    const ctx = await L.newCtx(browser, S, { width: w, height: h, theme: "light", extra: L.EXTRA });
    const p = await ctx.newPage();
    L.watch(p, consoleSink, lab);
    const v = `${w}`;
    // ---- Templates ----
    lab.label = `int|templates|${v}`;
    await p.goto(`${L.BASE}/settings/documents?tab=templates`, { waitUntil: "load" });
    await settle(p);
    if (w === 1440) {
      await p.getByRole("button", { name: "Upload Word Template" }).click();
      const dlg = p.getByRole("dialog", { name: "Upload Word Template" });
      await dlg.waitFor();
      await p.waitForTimeout(400);
      const d = await p.evaluate(DLG, '[role="dialog"]');
      await shot(p, `int__upload-dialog-${v}`, `Alice ${w} light — Templates › "Upload Word Template" dialog (opened, then Cancel)`);
      await dlg.getByRole("button", { name: "Cancel", exact: true }).click();
      await dlg.waitFor({ state: "hidden" });
      log({ step: "upload-dialog", viewport: v, dialog: d, closedBy: "Cancel", closed: true });
    }
    const row = p.getByTestId("template-list-item").filter({ has: p.getByRole("link", { name: "Engagement Letter — General", exact: true }) });
    await row.scrollIntoViewIfNeeded();
    const trig = row.getByRole("button").last();
    const trigInfo = await trig.evaluate((b) => { const r = b.getBoundingClientRect(); return { ariaLabel: b.getAttribute("aria-label"), text: b.textContent.trim(), srOnly: !!b.querySelector(".sr-only"), x: Math.round(r.x), right: Math.round(r.right), inViewport: r.right <= innerWidth && r.left >= 0 }; });
    const sc = await row.evaluate((tr) => { let e = tr.closest("table").parentElement; const r = e.getBoundingClientRect(); return { scrollerRight: Math.round(r.left + e.clientWidth), scrollLeft: e.scrollLeft, scrollWidth: e.scrollWidth, clientWidth: e.clientWidth }; });
    await trig.click();
    await p.getByRole("menu").waitFor();
    await p.waitForTimeout(300);
    const items = await p.getByRole("menuitem").evaluateAll((els) => els.map((e) => (e.innerText || "").trim()));
    await shot(p, `int__template-actions-menu-${v}`, `Alice ${w} light — Templates › "Engagement Letter — General" actions menu: ${items.join(" | ")} (Escape; Deactivate not clicked — it has no confirmation)`);
    await p.keyboard.press("Escape");
    await p.getByRole("menu").waitFor({ state: "hidden" });
    const sc2 = await row.evaluate((tr) => { const e = tr.closest("table").parentElement; return { scrollLeftAfterClick: e.scrollLeft }; });
    log({ step: "template-actions-menu", viewport: v, row: "Engagement Letter — General", trigger: trigInfo, tableScroller: { ...sc, ...sc2 }, menuItems: items, closedBy: "Escape", deactivateHasConfirmation: false, deactivateSource: "TemplateActionsMenu.tsx:114-119 onClick={handleDeactivate} (no AlertDialog)" });
    if (w === 1440) {
      const logo = { orgLogoImg: await p.getByRole("img", { name: "Organization logo" }).count(), uploadLogoButton: await p.getByRole("button", { name: "Upload Logo" }).count(), placeholderDashed: await p.locator("div.border-dashed").count() };
      log({ step: "logo-delete", viewport: v, ...logo, note: "no logo set (GET /api/settings logoUrl null), so the unnamed X delete button (branding-section.tsx:146-153, immediate delete, no dialog) is not rendered; nothing to open or cancel" });
      await p.getByRole("heading", { level: 2, name: "Branding" }).scrollIntoViewIfNeeded();
      await shot(p, `int__branding-card-${v}`, `Alice ${w} light — Templates › Branding card (no logo → dashed placeholder, no delete X; Brand Color #000000 = null fallback; footer empty)`);
    }
    // ---- Clauses ----
    lab.label = `int|clauses|${v}`;
    await p.goto(`${L.BASE}/settings/documents?tab=clauses`, { waitUntil: "load" });
    await settle(p);
    await p.getByRole("button", { name: "New Clause" }).click();
    const sheet = p.getByRole("dialog", { name: "New Clause" });
    await sheet.waitFor();
    await p.waitForTimeout(600);
    const sd = await p.evaluate(DLG, '[role="dialog"]');
    await shot(p, `int__clause-sheet-new-${v}`, `Alice ${w} light — Clauses › "New Clause" Sheet (opened, then Close)`);
    await sheet.getByRole("button", { name: "Close", exact: true }).click();
    await sheet.waitFor({ state: "hidden" });
    log({ step: "clause-sheet-new", viewport: v, dialog: sd, closedBy: "Close", closed: true });
    if (w === 1440) {
      const first = p.getByTestId("clause-row").first();
      const firstTitle = (await first.innerText()).split("\n")[0];
      await first.getByRole("button", { name: "Clause actions" }).click();
      await p.getByRole("menu").waitFor();
      const citems = await p.getByRole("menuitem").evaluateAll((els) => els.map((e) => (e.innerText || "").trim()));
      await shot(p, `int__clause-actions-menu-${v}`, `Alice ${w} light — Clauses › "${firstTitle}" actions menu: ${citems.join(" | ")}`);
      await p.getByRole("menuitem", { name: "View" }).click();
      const vs = p.getByRole("dialog", { name: "System Clause" });
      await vs.waitFor();
      await p.waitForTimeout(600);
      const vd = await p.evaluate(DLG, '[role="dialog"]');
      await shot(p, `int__clause-sheet-view-${v}`, `Alice ${w} light — Clauses › "${firstTitle}" › View → Sheet "System Clause" (then Close)`);
      await vs.getByRole("button", { name: "Close", exact: true }).click();
      await vs.waitFor({ state: "hidden" });
      log({ step: "clause-sheet-view", viewport: v, clause: firstTitle, menuItems: citems, dialog: vd, closedBy: "Close", closed: true });
      await first.getByRole("button", { name: "Clause actions" }).click();
      await p.getByRole("menuitem", { name: "Clone & Customize" }).click();
      const ad = p.getByRole("alertdialog", { name: "Clone Clause" });
      await ad.waitFor();
      await p.waitForTimeout(400);
      const add = await p.evaluate(DLG, '[role="alertdialog"]');
      await shot(p, `int__clause-clone-dialog-${v}`, `Alice ${w} light — Clauses › "${firstTitle}" › Clone & Customize → alertdialog "Clone Clause" (then Cancel)`);
      await ad.getByRole("button", { name: "Cancel", exact: true }).click();
      await ad.waitFor({ state: "hidden" });
      log({ step: "clause-clone-dialog", viewport: v, clause: firstTitle, dialog: add, closedBy: "Cancel", closed: true, deactivateClauseDialog: "unreachable: all 32 seeded clauses are SYSTEM (menu = View | Clone & Customize)" });
      await first.getByRole("button", { name: "Expand clause" }).click();
      await p.waitForTimeout(800);
      const exp = await first.evaluate((e) => ({ proseMirror: [...e.querySelectorAll(".ProseMirror")].map((x) => x.getAttribute("contenteditable")), h: [...e.querySelectorAll("h1,h2,h3")].map((x) => x.tagName + ":" + x.textContent.trim().slice(0, 40)) }));
      await shot(p, `int__clause-expanded-${v}`, `Alice ${w} light — Clauses › "${firstTitle}" expanded (read-only Tiptap body)`);
      await first.getByRole("button", { name: "Collapse clause" }).click();
      log({ step: "clause-expand", viewport: v, clause: firstTitle, expanded: exp });
      // ---- Email › Delivery Log status filter ----
      lab.label = `int|email|${v}`;
      await p.goto(`${L.BASE}/settings/documents?tab=email`, { waitUntil: "load" });
      await settle(p);
      await p.getByRole("tab", { name: "Delivery Log" }).click();
      await settle(p);
      const combo = p.locator("#main-content [role=tabpanel] [role=combobox]").first();
      const comboInfo = await combo.evaluate((b) => ({ ariaLabel: b.getAttribute("aria-label"), ariaLabelledby: b.getAttribute("aria-labelledby"), text: b.textContent.trim(), id: b.id, labelFor: b.id ? !!document.querySelector(`label[for="${CSS.escape(b.id)}"]`) : false }));
      await combo.click();
      await p.getByRole("listbox").waitFor();
      const opts = await p.getByRole("option").evaluateAll((els) => els.map((e) => (e.innerText || "").trim()));
      await shot(p, `int__email-status-filter-${v}`, `Alice ${w} light — Email › Delivery Log › status filter open: ${opts.join(" | ")} (Escape)`);
      await p.keyboard.press("Escape");
      log({ step: "email-status-filter", viewport: v, combobox: comboInfo, options: opts, closedBy: "Escape" });
    }
    await ctx.close();
  }
  await browser.close();
  out.console = consoleSink;
  out.tiles = tiles;
  fs.writeFileSync(path.join(L.RAW, "interactions.json"), JSON.stringify(out));
  console.log("console", consoleSink.length, JSON.stringify([...new Set(consoleSink.map((m) => m.label + " " + m.text.slice(0, 80)))]), "tiles", tiles.length);
})();
