// 648E OBS clip measure (copied from 648A gates-b6afe8c69) (from 648C probe-clip.cjs measureIn; 647C/647D bounding-rect method). Runs in the page.
// rootSel: a CSS selector. opts.boxIsViewport: compare descendants with the viewport instead of the root box.
function measureIn({ rootSel, opts }) {
  const vw = document.documentElement.clientWidth, vh = document.documentElement.clientHeight;
  const root = document.querySelector(rootSel);
  if (!root) return { missing: true, rootSel };
  const rr = root.getBoundingClientRect();
  const boxL = opts.boxIsViewport ? 0 : rr.left, boxR = opts.boxIsViewport ? vw : rr.right;
  const cs0 = getComputedStyle(root);
  const out = { rootSel, vw, vh, root: { left: Math.round(rr.left), right: Math.round(rr.right), top: Math.round(rr.top), bottom: Math.round(rr.bottom),
      clientWidth: root.clientWidth, scrollWidth: root.scrollWidth, clientHeight: root.clientHeight, scrollHeight: root.scrollHeight, overflowY: cs0.overflowY },
    rootInsideViewport: rr.left >= -0.5 && rr.right <= vw + 0.5 && rr.top >= -0.5 && rr.bottom <= vh + 0.5, offenders: [], truncated: [] };
  for (const el of root.querySelectorAll(opts.only || "*")) {
    const r = el.getBoundingClientRect();
    if (r.width === 0 || r.height === 0) continue;
    const cs = getComputedStyle(el);
    if (cs.visibility === "hidden" || (cs.position === "fixed" && el !== root)) continue;
    if (el.classList.contains("sr-only") || (r.width <= 1 && r.height <= 1)) continue;
    if (el.closest("[aria-hidden='true']") && !el.closest("[role='dialog']")) continue;
    const label = `${el.tagName.toLowerCase()}${el.getAttribute("data-testid") ? `[${el.getAttribute("data-testid")}]` : ""} "${(el.innerText || "").replace(/\s+/g, " ").trim().slice(0, 60)}"`;
    if (r.left < boxL - 0.5 || r.right > boxR + 0.5 || r.right > vw + 0.5) out.offenders.push({ el: label, left: Math.round(r.left), right: Math.round(r.right) });
    const hasText = [...el.childNodes].some((n) => n.nodeType === 3 && n.textContent.trim());
    if (hasText && el.scrollWidth > el.clientWidth + 1 && cs.overflowX !== "visible") out.truncated.push({ el: label, scrollWidth: el.scrollWidth, clientWidth: el.clientWidth, overflowX: cs.overflowX, textOverflow: cs.textOverflow });
    if (hasText && cs.textOverflow === "ellipsis" && el.scrollWidth > el.clientWidth + 1) out.truncated.push({ el: label, ellipsis: true });
    // a non-wrapping badge whose text is wider than its box (647C F-1 class)
    if (el.getAttribute("data-slot") === "badge" && el.scrollWidth > el.clientWidth + 1) out.truncated.push({ el: label, badge: true, scrollWidth: el.scrollWidth, clientWidth: el.clientWidth });
  }
  out.noHorizontalOverflow = root.scrollWidth <= root.clientWidth + 1;
  out.ok = out.offenders.length === 0 && out.truncated.length === 0 && out.noHorizontalOverflow;
  return out;
}
module.exports = { measureIn };
