// 661B shared helpers for the post-fix re-check (../../after-fix/scripts/), the whole-app light walk
// (./walk-light.cjs) and the scheme-after re-shoot (../../../scheme-after/scripts/). A NEW file: the
// helpers are copied from ../../after/scripts/capture-after.cjs (never edited) — login, theme via
// localStorage `kazi-theme`, sidebar via cookie `sidebar_state`, colour probes, WCAG maths, contact
// sheets, pixel diff — plus a light-scheme contrast audit adapted from Phase 90's
// phase90-ui/matrix/scripts/common-kc.cjs pageAudit().
// Firm app: e2e mock-auth stack on :3001 (backend :8081, mock IDP :8090).
const path = require("path");
const fs = require("fs");
const W = path.resolve(__dirname, "../../../../../..");
const { chromium } = require(path.join(W, "frontend/node_modules/@playwright/test"));

const APP = "http://localhost:3001";
const API = "http://localhost:8081";
const IDP = "http://localhost:8090";
const ORG = "e2e-test-org";
const USERS = {
  alice: { userId: "user_e2e_alice", orgId: "org_e2e_test", orgSlug: ORG, orgRole: "owner" },
  carol: { userId: "user_e2e_carol", orgId: "org_e2e_test", orgSlug: ORG, orgRole: "org:member" },
};
// Repo-relative when inside the worktree; otherwise a /tmp-style label (no home-directory paths).
const rel = (p) => {
  const r = path.relative(W, p);
  return r.startsWith("..") ? p.replace(/^\/(Users|home)\/[^/]+/, "~") : r;
};

async function idpToken(who = "alice") {
  const r = await fetch(`${IDP}/token`, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(USERS[who]) });
  return (await r.json()).access_token;
}
async function api(token, p) {
  const r = await fetch(`${API}${p}`, { headers: { Authorization: `Bearer ${token}` } });
  if (!r.ok) throw new Error(`${p} -> ${r.status}`);
  return r.json();
}
const list = (j) => (Array.isArray(j) ? j : j.content || j.items || j.data || []);

// Alice: through the /mock-login UI ("Sign In", the default user), as capture-after.cjs did.
// Carol: the mock-auth-token cookie, as frontend/e2e/fixtures/auth.ts loginAs() does.
async function loginState(browser, who = "alice") {
  const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  if (who === "alice") {
    const p = await ctx.newPage();
    await p.goto(`${APP}/mock-login`, { waitUntil: "load" });
    await p.getByRole("button", { name: "Sign In", exact: true }).click();
    await p.waitForURL(/\/org\/.*\/dashboard/, { timeout: 60000 });
  } else {
    const t = await idpToken(who);
    await ctx.addCookies([{ name: "mock-auth-token", value: t, domain: "localhost", path: "/", httpOnly: false, sameSite: "Lax" }]);
  }
  const state = await ctx.storageState();
  await ctx.close();
  return state;
}

// Page helpers; t = the kazi-theme to store (null = store nothing, e.g. marketing / portal).
const INIT = (t) => {
  if (t)
    try {
      localStorage.setItem("kazi-theme", t);
    } catch (e) {}
  window.__hex = (c) => {
    const cv = document.createElement("canvas");
    cv.width = cv.height = 1;
    const x = cv.getContext("2d");
    x.fillStyle = "#000";
    x.fillStyle = c;
    x.fillRect(0, 0, 1, 1);
    const d = x.getImageData(0, 0, 1, 1).data;
    return "#" + [d[0], d[1], d[2]].map((v) => v.toString(16).padStart(2, "0")).join("") + (d[3] < 255 ? ` a=${(d[3] / 255).toFixed(2)}` : "");
  };
  window.__rgb = (c, over) => {
    const cv = document.createElement("canvas");
    cv.width = cv.height = 1;
    const x = cv.getContext("2d");
    x.fillStyle = "#fff";
    x.fillRect(0, 0, 1, 1);
    if (over) {
      x.fillStyle = over;
      x.fillRect(0, 0, 1, 1);
    }
    x.fillStyle = "rgba(0,0,0,0)";
    x.fillStyle = c;
    x.fillRect(0, 0, 1, 1);
    const d = x.getImageData(0, 0, 1, 1).data;
    return [d[0], d[1], d[2]];
  };
};
async function newCtx(browser, state, { width, height, theme = "light", collapsed = false, extra = {} }) {
  const ctx = await browser.newContext({ viewport: { width, height }, deviceScaleFactor: 1, storageState: state, ...extra });
  await ctx.addCookies([{ name: "sidebar_state", value: String(!collapsed), domain: "localhost", path: "/" }]);
  await ctx.addInitScript(INIT, theme);
  return ctx;
}
function watch(page, sink, labelRef) {
  page.on("console", (m) => {
    if (m.type() === "error" || m.type() === "warning" || /hydrat/i.test(m.text())) sink.push({ label: labelRef.label, type: m.type(), text: m.text().slice(0, 300) });
  });
  page.on("pageerror", (e) => sink.push({ label: labelRef.label, type: "pageerror", text: String(e).slice(0, 300) }));
}

// WCAG 2.x contrast of two opaque sRGB triplets, unrounded.
function lum([r, g, b]) {
  const f = (v) => {
    v /= 255;
    return v <= 0.04045 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
  };
  return 0.2126 * f(r) + 0.7152 * f(g) + 0.0722 * f(b);
}
const ratio = (a, b) => {
  const [l1, l2] = [lum(a), lum(b)].sort((x, y) => y - x);
  return +((l1 + 0.05) / (l2 + 0.05)).toFixed(3);
};
const hex3 = (a) => "#" + a.map((v) => v.toString(16).padStart(2, "0")).join("");

// Contact sheet: tiles [caption, file|dataURL|null] in `cols` columns, each scaled to `tileW` px wide.
async function sheet(browser, outFile, title, files, cols, tileW = null) {
  const cells = files
    .map(([cap, f]) =>
      f && (f.startsWith("data:") || fs.existsSync(f))
        ? `<figure><figcaption>${cap}</figcaption><img src="${f.startsWith("data:") ? f : "data:image/png;base64," + fs.readFileSync(f).toString("base64")}"${tileW ? ` style="width:${tileW}px"` : ""}></figure>`
        : `<figure><figcaption>${cap}</figcaption><div class="miss">not captured</div></figure>`,
    )
    .join("");
  const html = `<html><body style="margin:0;background:#888;font:14px sans-serif"><h2 style="margin:8px">${title}</h2>
  <div style="display:grid;grid-template-columns:repeat(${cols},max-content);gap:8px;padding:8px;align-items:start">${cells}</div>
  <style>figure{margin:0;background:#fff}figcaption{padding:4px 6px;font-weight:600;max-width:${tileW || 1440}px;overflow-wrap:anywhere}img{display:block}.miss{padding:40px}</style></body></html>`;
  const p = await browser.newPage({ viewport: { width: 800, height: 600 } });
  await p.setContent(html, { waitUntil: "load" });
  await p.screenshot({ path: outFile, fullPage: true });
  await p.close();
}

// Pixel diff in a blank Chromium page (copied from capture-after.cjs cmp(); same semantics):
// exact = pixels whose RGB differs at all, tol8 = differs by > 8 in a channel; `search` slides A's
// crop over B to the best origin first.
function differ(browser) {
  let diffPage = null;
  const diffs = {};
  const visuals = [];
  async function cmp(name, { a, b, ra, rb = null, search = null, keepVisual = false }) {
    if (!diffPage) {
      diffPage = await browser.newPage();
      await diffPage.setContent("<html><body></body></html>");
    }
    if (!a || !b || !fs.existsSync(a) || !fs.existsSync(b)) {
      diffs[name] = { error: `missing ${!a || !fs.existsSync(a) ? "after" : "before"} tile` };
      return diffs[name];
    }
    const res = await diffPage.evaluate(
      async ({ A, B, ra, rb, search, keepVisual }) => {
        const load = (src) =>
          new Promise((ok, ko) => {
            const i = new Image();
            i.onload = () => ok(i);
            i.onerror = ko;
            i.src = src;
          });
        const [ia, ib] = await Promise.all([load(A), load(B)]);
        const data = (img) => {
          const c = document.createElement("canvas");
          c.width = img.width;
          c.height = img.height;
          const x = c.getContext("2d");
          x.drawImage(img, 0, 0);
          return x.getImageData(0, 0, img.width, img.height);
        };
        const da = data(ia);
        const db = data(ib);
        const [ax, ay, aw0, ah0] = ra || [0, 0, ia.width, ia.height];
        const aw = Math.min(aw0, ia.width - ax);
        const ah = Math.min(ah0, ia.height - ay);
        const count = (bx, by, limit) => {
          let n = 0;
          for (let y = 0; y < ah; y++) {
            const pa = ((ay + y) * da.width + ax) * 4;
            const pb = ((by + y) * db.width + bx) * 4;
            for (let x = 0; x < aw; x++) {
              const i = pa + x * 4;
              const j = pb + x * 4;
              if (da.data[i] !== db.data[j] || da.data[i + 1] !== db.data[j + 1] || da.data[i + 2] !== db.data[j + 2]) {
                if (++n > limit) return n;
              }
            }
          }
          return n;
        };
        let best = rb ? { x: rb[0], y: rb[1] } : { x: ax, y: ay };
        if (search) {
          let bn = Infinity;
          for (let y = Math.max(0, search.y0); y <= Math.min(search.y1, db.height - ah); y++)
            for (let x = Math.max(0, search.x0); x <= Math.min(search.x1, db.width - aw); x++) {
              const n = count(x, y, bn);
              if (n < bn) {
                bn = n;
                best = { x, y };
                if (n === 0) break;
              }
            }
        }
        if (best.x + aw > db.width || best.y + ah > db.height) return { error: `crop ${aw}x${ah} at ${best.x},${best.y} outside before ${db.width}x${db.height}`, sizeA: [ia.width, ia.height], sizeB: [db.width, db.height] };
        let exact = 0;
        let tol8 = 0;
        let maxDelta = 0;
        let box = null;
        const vis = keepVisual ? new ImageData(aw, ah) : null;
        for (let y = 0; y < ah; y++)
          for (let x = 0; x < aw; x++) {
            const i = ((ay + y) * da.width + ax + x) * 4;
            const j = ((best.y + y) * db.width + best.x + x) * 4;
            const d = Math.max(Math.abs(da.data[i] - db.data[j]), Math.abs(da.data[i + 1] - db.data[j + 1]), Math.abs(da.data[i + 2] - db.data[j + 2]));
            if (vis) {
              const k = (y * aw + x) * 4;
              const g = (da.data[i] + da.data[i + 1] + da.data[i + 2]) / 12 + 160;
              vis.data[k] = d ? 255 : g;
              vis.data[k + 1] = d ? 0 : g;
              vis.data[k + 2] = d ? 0 : g;
              vis.data[k + 3] = 255;
            }
            if (d) {
              exact++;
              if (d > 8) tol8++;
              if (d > maxDelta) maxDelta = d;
              box = box ? { x0: Math.min(box.x0, x), y0: Math.min(box.y0, y), x1: Math.max(box.x1, x), y1: Math.max(box.y1, y) } : { x0: x, y0: y, x1: x, y1: y };
            }
          }
        const crop = (img, sx, sy) => {
          const c = document.createElement("canvas");
          c.width = aw;
          c.height = ah;
          c.getContext("2d").drawImage(img, sx, sy, aw, ah, 0, 0, aw, ah);
          return c.toDataURL("image/png");
        };
        let visual = null;
        if (keepVisual) {
          const c = document.createElement("canvas");
          c.width = aw;
          c.height = ah;
          c.getContext("2d").putImageData(vis, 0, 0);
          visual = { before: crop(ib, best.x, best.y), after: crop(ia, ax, ay), diff: c.toDataURL("image/png") };
        }
        return { cropA: [ax, ay, aw, ah], originB: [best.x, best.y], pixels: aw * ah, exact, tol8, maxDelta, diffBox: box, sizeA: [ia.width, ia.height], sizeB: [db.width, db.height], visual };
      },
      { A: "data:image/png;base64," + fs.readFileSync(a).toString("base64"), B: "data:image/png;base64," + fs.readFileSync(b).toString("base64"), ra, rb, search, keepVisual },
    );
    const { visual, ...rest } = res;
    diffs[name] = { after: rel(a), before: rel(b), ...rest };
    if (visual) visuals.push([name, visual, rest]);
    console.log("DIFF", name, JSON.stringify(rest));
    return res;
  }
  return { cmp, diffs, visuals };
}

module.exports = { W, chromium, APP, API, IDP, ORG, USERS, INIT, rel, idpToken, api, list, loginState, newCtx, watch, lum, ratio, hex3, sheet, differ };
