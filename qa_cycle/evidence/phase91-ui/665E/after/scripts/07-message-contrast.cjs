// 665E OBS-AFTER copy of ../../before/scripts/07-message-contrast.cjs (that file is not edited). Changes:
//   (1) CASES use the AFTER classes (665E: Branding success `text-teal-600` (text-sm now comes from the
//       footer status slot), error `text-destructive`; Acceptance success `text-success-foreground`,
//       error `text-destructive`) and the detached <p> is appended inside the card's footer status
//       slot (`[data-slot=card-footer]`, wrapped in the slot's own `text-muted-foreground mr-auto
//       text-sm` div) — where the real message renders — instead of the card root;
//   (2) IN-SITU cases (no injection): the on-screen Branding helpers "PNG, JPG, or SVG. Max 2 MB." and
//       "N/500 characters", the Branding / Acceptance section descriptions, and (new tab) the Email ›
//       Delivery Log "to" separator, located by their text;
//   (3) the card is found as the closest [data-slot=settings-section] of the h2 (falls back to
//       div.rounded-lg.border).
// 665E OBS step 7: WCAG contrast of the inline save-message classes, which are only on screen for a
// moment after a save (04 saw them but its pages ran no contrast probe). No save is repeated: on each
// page a detached copy of the message <p> (same classes as in the source) is appended to the same
// card, measured with the 01 probe's compositing, then removed. Nothing is submitted or stored.
//   Branding  "Branding settings saved."      branding-section.tsx:211  `text-sm text-teal-600` (no dark: variant)
//   Editor    successMsg                       TemplateEditorClient.tsx:270 `text-sm text-teal-600` (not on screen here; same class)
//   Acceptance "Acceptance settings updated."  AcceptanceSettingsForm.tsx:80-84 `text-sm text-green-600 dark:text-green-400`
//   Branding helper "PNG, JPG, or SVG. Max 2 MB." `mt-1 text-xs text-slate-400` (already on screen; re-measured)
// 1440×900 light and dark, Alice. Writes $RAW/message-contrast.json.
const path = require("path");
const fs = require("fs");
const L = require("./lib.cjs");

const MEASURE = ({ cardHeading, cls, text, inSitu }) => {
  let p, wrap = null;
  if (inSitu) {
    p = [...document.querySelectorAll("#main-content p, #main-content span, #main-content div")].find((x) => x.children.length === 0 && new RegExp(inSitu).test(x.textContent.trim()));
    if (!p) return { error: `no in-situ element ${inSitu}` };
  } else {
    const h = [...document.querySelectorAll("#main-content h2")].find((x) => x.textContent.trim() === cardHeading);
    const card = h && (h.closest('[data-slot="settings-section"]') || h.closest("div.rounded-lg.border"));
    if (!card) return { error: `no card ${cardHeading}` };
    const footer = card.querySelector('[data-slot="card-footer"]') || card;
    wrap = document.createElement("div");
    wrap.className = "text-muted-foreground mr-auto text-sm";
    p = document.createElement("p");
    p.className = cls;
    p.textContent = text;
    wrap.appendChild(p);
    footer.insertBefore(wrap, footer.firstChild);
  }
  const cv = document.createElement("canvas"); cv.width = cv.height = 1; const cx = cv.getContext("2d", { willReadFrequently: true });
  const px = (c, base) => { cx.globalCompositeOperation = "copy"; cx.fillStyle = base; cx.fillRect(0, 0, 1, 1); cx.globalCompositeOperation = "source-over"; cx.fillStyle = "rgba(0,0,0,0)"; cx.fillStyle = c; cx.fillRect(0, 0, 1, 1); const d = cx.getImageData(0, 0, 1, 1).data; return [d[0], d[1], d[2]]; };
  const alpha = (c) => { cx.globalCompositeOperation = "copy"; cx.fillStyle = "rgba(0,0,0,0)"; cx.fillStyle = c; cx.fillRect(0, 0, 1, 1); return cx.getImageData(0, 0, 1, 1).data[3]; };
  const chain = []; for (let x = p; x; x = x.parentElement) { const b = getComputedStyle(x).backgroundColor; if (alpha(b) > 0) { chain.push(b); if (alpha(b) === 255) break; } }
  let base = [255, 255, 255]; for (const b of chain.reverse()) base = px(b, `rgb(${base})`);
  const fg = px(getComputedStyle(p).color, `rgb(${base})`);
  const lum = ([r, g, b]) => { const f = (v) => { v /= 255; return v <= 0.04045 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4); }; return 0.2126 * f(r) + 0.7152 * f(g) + 0.0722 * f(b); };
  const [l1, l2] = [lum(fg), lum(base)].sort((a, b) => b - a);
  const res = { color: getComputedStyle(p).color, fg: `rgb(${fg})`, bg: `rgb(${base})`, ratio: +((l1 + 0.05) / (l2 + 0.05)).toFixed(2), fontPx: parseFloat(getComputedStyle(p).fontSize), elCls: p.className, measuredText: p.textContent.trim().slice(0, 80) };
  if (wrap) wrap.remove();
  return res;
};
const CASES = [
  { tab: "templates", cardHeading: "Branding", cls: "text-teal-600", text: "Branding settings saved.", src: "branding-section.tsx:91 status (also TemplateEditorClient successMsg, text-teal-600)" },
  { tab: "templates", cardHeading: "Branding", cls: "text-destructive", text: "Failed to save branding settings.", src: "branding-section.tsx:90 status error" },
  { tab: "templates", inSitu: "^PNG, JPG, or SVG\\. Max 2 MB\\.$", text: "PNG, JPG, or SVG. Max 2 MB.", src: "branding-section.tsx helper (text-muted-foreground, was text-slate-400)" },
  { tab: "templates", inSitu: "^\\d+/500 characters$", text: "N/500 characters", src: "branding-section.tsx footer counter (text-muted-foreground, was text-slate-400)" },
  { tab: "templates", inSitu: "^Customize your organization", text: "Branding description", src: "SettingsSection CardDescription" },
  { tab: "acceptance", cardHeading: "Document Acceptance", cls: "text-success-foreground", text: "Acceptance settings updated.", src: "AcceptanceSettingsForm.tsx status success" },
  { tab: "acceptance", cardHeading: "Document Acceptance", cls: "text-destructive", text: "Failed to update settings.", src: "AcceptanceSettingsForm.tsx status error" },
  { tab: "acceptance", inSitu: "^Number of days before", text: "Acceptance field hint", src: "AcceptanceSettingsForm.tsx hint (text-muted-foreground)" },
  { tab: "email-log", inSitu: "^to$", text: "to", src: "DeliveryLogTable.tsx date separator (text-muted-foreground, was text-slate-400)" },
];

(async () => {
  const browser = await L.chromium.launch();
  const S = await L.loginState(browser, "alice");
  const out = { at: new Date().toISOString(), results: [] };
  for (const theme of ["light", "dark"]) {
    const ctx = await L.newCtx(browser, S, { width: 1440, height: 900, theme, extra: L.EXTRA });
    const p = await ctx.newPage();
    for (const tab of ["templates", "acceptance", "email-log"]) {
      await p.goto(`${L.BASE}/settings/documents?tab=${tab === "email-log" ? "email" : tab}`, { waitUntil: "load" });
      await p.waitForLoadState("networkidle").catch(() => {});
      await p.waitForTimeout(500);
      if (tab === "email-log") { await p.getByRole("tab", { name: "Delivery Log" }).click(); await p.waitForLoadState("networkidle").catch(() => {}); await p.waitForTimeout(800); }
      for (const c of CASES.filter((x) => x.tab === tab)) {
        const r = await p.evaluate(MEASURE, c);
        out.results.push({ theme, ...c, ...r, passAA: r.ratio >= 4.5 });
        console.log(theme, c.text, JSON.stringify(r));
      }
    }
    await ctx.close();
  }
  await browser.close();
  fs.writeFileSync(path.join(L.RAW, "message-contrast.json"), JSON.stringify(out));
})();
