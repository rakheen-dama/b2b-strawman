// 669B in-page probe (page.evaluate). Pure DOM reads — no clicks. One JSON object per page: shell
// counts (h1 / level-1 headings / main / main#main-content), the page header (h1 text, font size +
// weight + family, subtitle, PageHeader slot), document overflow, every data-testid (UUIDs → <id>),
// every visible table (in #main-content, else in <body> — platform-admin has no #main-content before
// K-98) with its scroller / frame widths, headers, rows (testid, role, tabindex) and the colour of
// header + first-row cells (WCAG composite), every empty state ([data-slot=empty-state] with
// data-variant, *empty* testids, "No …" / permission text) with its box, low-contrast leaf text,
// tabs, headings, buttons, and two page-family blocks:
//   team — tr[role=button] / tr[tabindex] counts, buttons inside tbody, invite card, member pill;
//   pa   — the platform-admin nav links (aria-current, underline via ::after / border / decoration)
//          and the page content root (outermost mx-auto ancestor of the h1: width, max-w class).
// Structure-agnostic: the same probe reads the before (raw tables, hand-rolled headers) and the after
// (DataTable / PageHeader) DOM.
function probe() {
  const UUID = /[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/g;
  const norm = (s) => (s || "").replace(/\s+/g, " ").trim();
  const R = (el) => { if (!el) return null; const r = el.getBoundingClientRect(); return { x: Math.round(r.left), y: Math.round(r.top + scrollY), w: Math.round(r.width), h: Math.round(r.height), right: Math.round(r.right) }; };
  const mainId = document.querySelector("#main-content");
  const main = mainId || document.body;
  const cv = document.createElement("canvas");
  cv.width = cv.height = 1;
  const cx = cv.getContext("2d", { willReadFrequently: true });
  const px = (layers) => { cx.globalAlpha = 1; cx.fillStyle = "#fff"; cx.fillRect(0, 0, 1, 1); for (const c of layers) { cx.fillStyle = "rgba(0,0,0,0)"; cx.fillStyle = c; cx.fillRect(0, 0, 1, 1); } const d = cx.getImageData(0, 0, 1, 1).data; return [d[0], d[1], d[2]]; };
  const bgLayers = (el) => { const ls = []; for (let e = el; e; e = e.parentElement) { const b = getComputedStyle(e).backgroundColor; if (b && b !== "rgba(0, 0, 0, 0)" && b !== "transparent") { ls.unshift(b); if (px([b]).join() === px(["#000", b]).join()) break; } } return ls; };
  const lum = ([r, g, b]) => { const f = (v) => { v /= 255; return v <= 0.04045 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4); }; return 0.2126 * f(r) + 0.7152 * f(g) + 0.0722 * f(b); };
  const ratio = (a, b) => { const [l1, l2] = [lum(a), lum(b)].sort((x, y) => y - x); return +((l1 + 0.05) / (l2 + 0.05)).toFixed(2); };
  const hex = (a) => "#" + a.map((v) => v.toString(16).padStart(2, "0")).join("");
  const colour = (el) => { const ls = bgLayers(el); const bg = px(ls); const fg = px([...ls, getComputedStyle(el).color]); return { fg: hex(fg), bg: hex(bg), ratio: ratio(fg, bg) }; };
  const cls = (e) => (e.className && e.className.baseVal != null ? e.className.baseVal : e.className || "");
  const visible = (e) => { const r = e.getBoundingClientRect(); const s = getComputedStyle(e); return r.width > 0 && r.height > 0 && s.visibility !== "hidden" && s.display !== "none"; };
  const ownText = (e) => norm([...e.childNodes].filter((n) => n.nodeType === 3).map((n) => n.textContent).join(" "));

  const out = { url: location.pathname + location.search, title: document.title, vw: innerWidth, docClientWidth: document.documentElement.clientWidth, theme: document.documentElement.classList.contains("dark") ? "dark" : "light", scope: mainId ? "#main-content" : "body (no #main-content)" };
  out.h1Main = [...main.querySelectorAll("h1")].filter(visible).map((h) => norm(h.innerText));
  out.level1Main = [...main.querySelectorAll("h1,[role=heading][aria-level='1']")].filter(visible).length;
  out.h1Page = document.querySelectorAll("h1").length;
  out.mainIdCount = document.querySelectorAll("main#main-content").length;
  out.mainElCount = document.querySelectorAll("main").length;
  out.docHOverflowPx = document.documentElement.scrollWidth - document.documentElement.clientWidth;
  out.docHeight = document.documentElement.scrollHeight;
  if (mainId) { const cs = getComputedStyle(mainId); out.mainInnerWidth = Math.round(mainId.clientWidth - parseFloat(cs.paddingLeft) - parseFloat(cs.paddingRight)); }
  out.notFound = /could not be found|Page not found|\b404\b/i.test(norm(document.body.innerText).slice(0, 600));
  out.errorBoundary = /Something went wrong/.test(document.body.innerText);
  out.bodyHead = norm(main.innerText).slice(0, 400);
  const bc = document.querySelector('nav[aria-label="Breadcrumb"]');
  out.breadcrumb = bc ? norm(bc.innerText) : null;
  out.navActive = [...document.querySelectorAll('[data-sidebar="menu-button"][data-active="true"], nav a[aria-current="page"]')].map((a) => norm(a.innerText)).filter(Boolean);

  const h1 = [...main.querySelectorAll("h1")].find(visible);
  if (h1) {
    const s = getComputedStyle(h1);
    const nxt = (e) => (e && e.nextElementSibling && e.nextElementSibling.tagName === "P" ? e.nextElementSibling : null);
    const sub = nxt(h1) || (h1.parentElement !== main && !/\bspace-y-\d/.test(cls(h1.parentElement)) ? nxt(h1.parentElement) : null);
    const ph = h1.closest('[data-slot="page-header"]');
    out.header = { text: norm(h1.innerText), cls: cls(h1), fontSize: s.fontSize, fontWeight: s.fontWeight, fontFamily: s.fontFamily.split(",")[0].replace(/"/g, ""), letterSpacing: s.letterSpacing, box: R(h1), ...colour(h1), pageHeader: !!ph, h1Slot: h1.dataset.slot || null, buttonsInH1: [...h1.querySelectorAll("button")].map((b) => b.getAttribute("aria-label") || norm(b.innerText)), subtitle: sub ? norm(sub.innerText).slice(0, 160) : ph && ph.querySelector('[data-slot="page-header-description"]') ? norm(ph.querySelector('[data-slot="page-header-description"]').innerText) : null, actions: ph && ph.querySelector('[data-slot="page-header-actions"]') ? norm(ph.querySelector('[data-slot="page-header-actions"]').innerText) : null };
  }
  const tids = {};
  for (const e of document.querySelectorAll("[data-testid]")) { const k = e.dataset.testid.replace(UUID, "<id>"); tids[k] = (tids[k] || 0) + 1; }
  out.testids = tids;
  const isBadge = (e) => e.dataset.slot === "badge";
  out.tables = [...main.querySelectorAll("table")].filter(visible).map((t) => {
    let sc = t.parentElement;
    while (sc && sc !== main && !/(auto|scroll|hidden)/.test(getComputedStyle(sc).overflowX)) sc = sc.parentElement;
    const scR = sc && sc !== main ? sc.getBoundingClientRect() : main.getBoundingClientRect();
    let card = t.parentElement;
    while (card && card !== main && !(parseFloat(getComputedStyle(card).borderTopWidth) > 0 && parseFloat(getComputedStyle(card).borderLeftWidth) > 0)) card = card.parentElement;
    if (card === main) card = null;
    const ths = [...t.querySelectorAll("thead th")].map((th) => { const b = th.getBoundingClientRect(); const shown = getComputedStyle(th).display !== "none" && b.width > 0; return { text: norm(th.textContent), w: Math.round(b.width), shown, inView: shown && b.left < scR.right - 1 && b.right > scR.left + 1, fullyInView: shown && b.left >= scR.left - 1 && b.right <= scR.right + 1 }; });
    const rows = [...t.querySelectorAll("tbody tr")];
    const th0 = t.querySelector("thead th");
    const r0 = rows[0];
    return {
      testid: t.dataset.testid || null,
      wrapperTestid: t.parentElement.closest("[data-testid]") ? t.parentElement.closest("[data-testid]").dataset.testid.replace(UUID, "<id>") : null,
      inDataTable: !!t.closest('[data-slot="data-table"]'),
      width: Math.round(t.getBoundingClientRect().width),
      tableScrollWidth: t.scrollWidth,
      scroller: sc && sc !== main ? { clientWidth: sc.clientWidth, scrollWidth: sc.scrollWidth, overflowPx: sc.scrollWidth - sc.clientWidth } : null,
      container: { clientWidth: Math.round(scR.width) },
      card: card ? { clientWidth: card.clientWidth, testid: card.dataset.testid || null } : null,
      headers: ths,
      columnsInView: ths.filter((h) => h.inView).map((h) => h.text || "(blank)"),
      headerColour: th0 ? { ...colour(th0), fontSize: getComputedStyle(th0).fontSize, transform: getComputedStyle(th0).textTransform } : null,
      firstRowCellColours: r0 ? [...r0.querySelectorAll("td")].map((td) => { const leaf = [...td.querySelectorAll("*")].filter((e) => ownText(e) && visible(e)).sort((x, y) => ownText(y).length - ownText(x).length)[0] || td; return `${norm(leaf.innerText).slice(0, 30)} ${colour(leaf).fg} on ${colour(leaf).bg} ${colour(leaf).ratio}`; }) : [],
      rowCount: rows.length,
      rows: rows.slice(0, 25).map((r) => ({
        testid: (r.dataset.testid || "").replace(UUID, "<id>"),
        role: r.getAttribute("role"),
        tabindex: r.getAttribute("tabindex"),
        cursor: getComputedStyle(r).cursor,
        cells: [...r.querySelectorAll("td")].map((td) => norm(td.innerText).slice(0, 80)),
        h: Math.round(r.getBoundingClientRect().height),
        buttons: [...r.querySelectorAll("button,a,[tabindex]")].map((b) => `${b.tagName.toLowerCase()}:${norm(b.innerText).slice(0, 30) || b.getAttribute("aria-label") || ""}`),
        badges: [...r.querySelectorAll("span,div")].filter(isBadge).map((b) => `${norm(b.innerText)} ${colour(b).ratio}`),
      })),
    };
  });
  const empties = new Set([...main.querySelectorAll('[data-slot="empty-state"], [data-testid*="empty"]')]);
  for (const p of main.querySelectorAll("p,h2,h3,div")) {
    if (p.children.length > 2) continue;
    const t = norm(p.innerText);
    if (/^(No |Nothing|You do not have permission|Invitations are managed)/.test(t) && ![...empties].some((e) => e.contains(p) || p.contains(e))) empties.add(p);
  }
  out.empties = [...empties].filter(visible).map((e) => ({ slot: e.dataset.slot || null, variant: e.dataset.variant || null, testid: e.dataset.testid || null, tag: e.tagName.toLowerCase(), text: norm(e.innerText).slice(0, 160), h: Math.round(e.getBoundingClientRect().height), w: Math.round(e.getBoundingClientRect().width), hasIcon: !!e.querySelector("svg"), heading: (e.querySelector("h1,h2,h3,h4") || { tagName: "" }).tagName.toLowerCase() || null, titleTag: e.dataset.slot === "empty-state" ? ((e.querySelector("h2,h3,p") || { tagName: "" }).tagName || "").toLowerCase() : null, ...colour(e) }));
  out.lowContrast = [...main.querySelectorAll("*")].filter((e) => ownText(e) && visible(e) && !e.closest("svg")).map((e) => ({ text: ownText(e).slice(0, 50), tag: e.tagName.toLowerCase(), cls: cls(e).split(/\s+/).filter((c) => /^(dark:)?(text|bg)-/.test(c)).join(" "), ...colour(e) })).filter((x) => x.ratio < 4.5).slice(0, 60);
  out.isoDates = [...main.querySelectorAll("*")].filter((e) => /\b\d{4}-\d{2}-\d{2}\b/.test(ownText(e)) && visible(e)).map((e) => ownText(e).slice(0, 60)).slice(0, 20);
  out.badges = [...main.querySelectorAll('[data-slot="badge"]')].filter(visible).map((b) => ({ text: norm(b.innerText), variant: b.dataset.variant || null, ...colour(b) })).slice(0, 40);
  out.inputs = [...main.querySelectorAll("input,select,textarea,button[role=combobox]")].filter(visible).map((i) => ({ tag: i.tagName.toLowerCase(), type: i.type, name: i.name || i.id || i.getAttribute("aria-label") || i.getAttribute("placeholder") || null, value: i.tagName === "BUTTON" ? norm(i.innerText) : i.value, w: Math.round(i.getBoundingClientRect().width) }));
  out.tabs = [...main.querySelectorAll('[role="tab"]')].filter(visible).map((t) => `${norm(t.innerText) || t.getAttribute("aria-label")}${t.getAttribute("aria-selected") === "true" ? "*" : ""}`);
  out.headings = [...main.querySelectorAll("h1,h2,h3,[role=heading]")].filter(visible).map((h) => `${h.tagName.toLowerCase()}: ${norm(h.innerText).slice(0, 70)}`);
  const btn = {};
  for (const b of main.querySelectorAll("button")) { if (!visible(b)) continue; const k = `${norm(b.innerText) || b.getAttribute("aria-label") || "(icon)"}${b.disabled ? " [disabled]" : ""}`.slice(0, 60); btn[k] = (btn[k] || 0) + 1; }
  out.buttons = btn;
  out.dialogs = [...document.querySelectorAll('[role="dialog"],[role="alertdialog"]')].filter(visible).map((d) => ({ role: d.getAttribute("role"), name: norm((d.querySelector("h2,[data-slot$=title]") || {}).innerText || d.getAttribute("aria-label") || ""), box: R(d) }));

  // Team page family
  const memberPill = [...main.querySelectorAll("span")].find((s) => /^\d+ members?$/.test(norm(s.innerText)));
  out.team = {
    trRoleButton: main.querySelectorAll('tr[role="button"]').length,
    trTabindex: main.querySelectorAll("tr[tabindex]").length,
    memberRows: main.querySelectorAll('[data-testid^="member-row-"]').length,
    tbodyButtons: main.querySelectorAll("tbody button").length,
    inviteCard: [...main.querySelectorAll("h2")].some((h) => norm(h.innerText) === "Invite a team member"),
    memberPill: memberPill ? { text: norm(memberPill.innerText), inH1: !!memberPill.closest("h1"), inActions: !!memberPill.closest('[data-slot="page-header-actions"]'), ...colour(memberPill) } : null,
    helpTip: [...main.querySelectorAll("h1 button, [data-slot=page-header] button")].map((b) => b.getAttribute("aria-label") || norm(b.innerText)),
    revokeColumn: [...main.querySelectorAll("thead th")].some((th) => norm(th.textContent) === "Actions"),
  };
  // Platform-admin family
  const paLinks = location.pathname.startsWith("/platform-admin") ? [...document.querySelectorAll('a[href^="/platform-admin/"]')].filter(visible) : [];
  if (paLinks.length) {
    const nav = paLinks[0].closest("nav");
    let root = null;
    for (let e = h1 ? h1.parentElement : null; e && e !== document.body; e = e.parentElement) if (/\bmx-auto\b/.test(cls(e))) root = e;
    const rs = root ? getComputedStyle(root) : null;
    out.pa = {
      nav: nav ? { tag: nav.tagName.toLowerCase(), ariaLabel: nav.getAttribute("aria-label"), box: R(nav), bg: colour(nav).bg, insideMain: !!nav.closest("main") } : null,
      links: paLinks.map((a) => { const s = getComputedStyle(a); const af = getComputedStyle(a, "::after"); return { text: norm(a.innerText), href: a.getAttribute("href"), ariaCurrent: a.getAttribute("aria-current"), fontWeight: s.fontWeight, textDecoration: s.textDecorationLine, borderBottom: `${s.borderBottomWidth} ${s.borderBottomStyle}`, afterContent: af.content, afterHeight: af.height, afterBg: af.backgroundColor, cls: cls(a), ...colour(a) }; }),
      contentRoot: root ? { cls: cls(root), maxW: (cls(root).match(/max-w-\S+/) || [null])[0], w: Math.round(root.getBoundingClientRect().width), innerW: Math.round(root.clientWidth - parseFloat(rs.paddingLeft) - parseFloat(rs.paddingRight)), x: Math.round(root.getBoundingClientRect().left), insideMain: !!root.closest("main") } : null,
      h1InsideMain: h1 ? !!h1.closest("main#main-content") : null,
    };
  }
  return out;
}
module.exports = { probe };
