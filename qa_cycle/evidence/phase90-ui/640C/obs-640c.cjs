// 640C OBS (task 640.14) on the mock-auth e2e stack (:3001), built from worktree-epic-640C @ a9e613443.
// Usage:
//   node obs-640c.cjs mywork <tag> [users=alice,carol] [widths=1024,1440] [themes=light,dark]
//   node obs-640c.cjs home   <tag> [users=alice]       [widths=1024,1440] [themes=light,dark] [modes=client,reload]
// mywork: the "This Week" card (StackedMeter billable vs non-billable): screenshot, a11y name +
//   sr summary, segment fills vs --chart-1/--chart-2, swatches, contrast / white surfaces, and the
//   empty week (Previous week until "No time tracked this week").
// home: throttled (Slow 4G: 2000ms latency, 50KB/s down/up + CPU x4) navigation to Home — client-side
//   (sidebar "Home" link from My Work → loading.tsx fallback) and hard reload. Samples the DOM every
//   ~50ms, screenshots the skeleton (StatCard state="loading", OQ #27), measures header / KPI cell /
//   diary / row-3 boxes in skeleton vs loaded, and the layout-shift (CLS) entries.
// Writes PNG + <mode>-<tag>.json next to this file; merges console messages into console.json.
const path = require("path");
const fs = require("fs");
const { chromium } = require(
  "/Users/rakheendama/Projects/2026/worktree-epic-640C/frontend/node_modules/@playwright/test"
);

const BASE = "http://localhost:3001";
const ORG = "e2e-test-org";
const OUT = __dirname;
const USERS = { alice: "user_e2e_alice", bob: "user_e2e_bob", carol: "user_e2e_carol" };
const VIEWPORTS = { 1024: { width: 1024, height: 768 }, 1440: { width: 1440, height: 900 } };

const mode = process.argv[2];
const tag = process.argv[3] || "run";
const users = (process.argv[4] || (mode === "mywork" ? "alice,carol" : "alice")).split(",");
const widths = (process.argv[5] || "1024,1440").split(",");
const themes = (process.argv[6] || "light,dark").split(",");
const navModes = (process.argv[7] || "client,reload").split(",");

const consoleFile = path.join(OUT, "console.json");
const consoleLog = fs.existsSync(consoleFile) ? JSON.parse(fs.readFileSync(consoleFile, "utf8")) : {};

async function newContext(browser, w, theme) {
  const context = await browser.newContext({ viewport: VIEWPORTS[w] });
  // In-page sampler (rAF, >= 40ms apart, plus every state change): independent of Playwright
  // round-trips, so it also sees the streamed skeleton during a hard reload.
  await context.addInitScript({
    content: `window.__measureHome = ${measureHome.toString()};
      window.__homeSamples = [];
      (function loop() {
        let last = 0, lastState = null;
        const tick = () => {
          try {
            const now = performance.now();
            if (document.querySelector("#main-content")) {
              const st = document.querySelector('#main-content [data-testid="home-page"]') ? "l" : document.querySelector('#main-content div.space-y-6[aria-busy="true"]') ? "s" : "o";
              if (now - last >= 40 || st !== lastState) {
                const m = window.__measureHome();
                if (window.__homeSamples.length < 4000) window.__homeSamples.push(m);
                last = now; lastState = st;
              }
            }
          } catch (e) {}
          requestAnimationFrame(tick);
        };
        requestAnimationFrame(tick);
      })();`,
  });
  await context.addInitScript((t) => {
    try {
      localStorage.setItem("kazi-theme", t);
    } catch (e) {}
    window.__shifts = [];
    try {
      new PerformanceObserver((list) => {
        for (const e of list.getEntries()) {
          window.__shifts.push({
            t: Math.round(e.startTime),
            value: e.value,
            hadRecentInput: e.hadRecentInput,
            sources: (e.sources || []).map((s) => ({
              node: s.node
                ? `${s.node.nodeName}${s.node.getAttribute ? `[slot=${s.node.getAttribute("data-slot")}][testid=${s.node.getAttribute("data-testid")}]` : ""}.${String(s.node.className || "").slice(0, 70)}`
                : null,
              prev: s.previousRect && { x: s.previousRect.x, y: s.previousRect.y, w: s.previousRect.width, h: s.previousRect.height },
              cur: s.currentRect && { x: s.currentRect.x, y: s.currentRect.y, w: s.currentRect.width, h: s.currentRect.height },
            })),
          });
        }
      }).observe({ type: "layout-shift", buffered: true });
    } catch (e) {}
  }, theme);
  const page = await context.newPage();
  const messages = [];
  page.on("console", (m) => {
    const text = m.text();
    if (m.type() === "error" || m.type() === "warning" || /hydrat/i.test(text))
      messages.push({ type: m.type(), text: text.slice(0, 600), url: page.url() });
  });
  page.on("pageerror", (e) => messages.push({ type: "pageerror", text: String(e).slice(0, 600), url: page.url() }));
  return { context, page, messages };
}

async function login(page, user) {
  await page.goto(`${BASE}/mock-login`, { waitUntil: "load" });
  await page.locator("#user-select").selectOption(USERS[user]);
  await page.getByRole("button", { name: "Sign In" }).click();
  await page.waitForURL(`**/org/${ORG}/dashboard`, { timeout: 90_000 });
  await page.locator("#main-content").getByRole("heading", { level: 1, name: "Home" }).waitFor({ timeout: 90_000 });
}

// ---------------------------------------------------------------- My Work weekly summary

/** Runs in the page on the "This Week" card. */
function measureWeekCard(card) {
  const cv = document.createElement("canvas");
  cv.width = 1;
  cv.height = 1;
  const ctx = cv.getContext("2d", { willReadFrequently: true });
  const toRgb = (c) => {
    if (!c || c === "none") return null;
    if (c === "transparent") return { r: 0, g: 0, b: 0, a: 0 };
    let alpha = 1;
    const am = c.match(/\/\s*([\d.]+%?)\s*\)$/);
    if (am) alpha = am[1].endsWith("%") ? parseFloat(am[1]) / 100 : parseFloat(am[1]);
    const rm = c.match(/^rgba\(([^)]+)\)/);
    if (rm) alpha = Number(rm[1].split(",")[3]);
    ctx.clearRect(0, 0, 1, 1);
    ctx.fillStyle = "#000";
    ctx.fillStyle = c.replace(/\/\s*[\d.]+%?\s*\)$/, ")").replace(/^rgba\(([^,]+),([^,]+),([^,]+),[^)]+\)/, "rgb($1,$2,$3)");
    ctx.fillRect(0, 0, 1, 1);
    const d = ctx.getImageData(0, 0, 1, 1).data;
    return { r: d[0], g: d[1], b: d[2], a: alpha };
  };
  const hex = (c) => (c ? "#" + [c.r, c.g, c.b].map((v) => v.toString(16).padStart(2, "0")).join("") : null);
  const lum = ({ r, g, b }) => {
    const f = (x) => ((x /= 255) <= 0.03928 ? x / 12.92 : Math.pow((x + 0.055) / 1.055, 2.4));
    return 0.2126 * f(r) + 0.7152 * f(g) + 0.0722 * f(b);
  };
  const contrast = (a, b) => {
    const l1 = lum(a);
    const l2 = lum(b);
    return (Math.max(l1, l2) + 0.05) / (Math.min(l1, l2) + 0.05);
  };
  // Composite translucent backgrounds up the tree (bg-muted/50 on bg-card).
  const bgOf = (node) => {
    const stack = [];
    let n = node;
    while (n && n.nodeType === 1) {
      const c = toRgb(getComputedStyle(n).backgroundColor);
      if (c && c.a > 0) {
        stack.push(c);
        if (c.a >= 0.999) break;
      }
      n = n.parentElement;
    }
    let base = stack.length && stack[stack.length - 1].a >= 0.999 ? stack.pop() : toRgb(getComputedStyle(document.body).backgroundColor) || { r: 255, g: 255, b: 255, a: 1 };
    while (stack.length) {
      const top = stack.pop();
      base = {
        r: Math.round(top.r * top.a + base.r * (1 - top.a)),
        g: Math.round(top.g * top.a + base.g * (1 - top.a)),
        b: Math.round(top.b * top.a + base.b * (1 - top.a)),
        a: 1,
      };
    }
    return base;
  };
  const resolveVar = (v) => {
    const probe = document.createElementNS("http://www.w3.org/2000/svg", "rect");
    probe.setAttribute("fill", `var(${v})`);
    const svg = document.createElementNS("http://www.w3.org/2000/svg", "svg");
    svg.appendChild(probe);
    card.appendChild(svg);
    const f = getComputedStyle(probe).fill;
    svg.remove();
    return hex(toRgb(f));
  };

  const r = card.getBoundingClientRect();
  const meter = card.querySelector('svg[role="img"]');
  let meterInfo = null;
  if (meter) {
    const descId = meter.getAttribute("aria-describedby");
    const desc = descId ? document.getElementById(descId) : null;
    const segs = Array.from(meter.querySelectorAll("rect[data-segment]")).map((s) => ({
      key: s.getAttribute("data-segment"),
      fillAttr: s.getAttribute("fill"),
      fillComputed: hex(toRgb(getComputedStyle(s).fill)),
      widthPx: Math.round(s.getBoundingClientRect().width * 10) / 10,
      title: s.querySelector("title")?.textContent ?? null,
    }));
    const track = meter.querySelector('[data-slot="stacked-meter-track"]');
    meterInfo = {
      ariaLabel: meter.getAttribute("aria-label"),
      describedBy: descId,
      srSummary: desc ? desc.textContent : null,
      srSummaryIsSrOnly: desc ? getComputedStyle(desc).position === "absolute" && desc.getBoundingClientRect().width <= 1 : null,
      widthPx: Math.round(meter.getBoundingClientRect().width),
      heightPx: Math.round(meter.getBoundingClientRect().height),
      segments: segs,
      trackFill: track ? hex(toRgb(getComputedStyle(track).fill)) : null,
      gapCount: meter.querySelectorAll('[data-slot="stacked-meter-gap"]').length,
    };
  }
  const swatches = Array.from(card.querySelectorAll('span[aria-hidden="true"].rounded-full')).map((s) => ({
    label: s.parentElement?.textContent.trim().slice(0, 40),
    bg: hex(toRgb(getComputedStyle(s).backgroundColor)),
    size: Math.round(s.getBoundingClientRect().width),
  }));

  const lowContrast = [];
  const texts = [];
  let minContrast = 99;
  for (const t of card.querySelectorAll("*")) {
    const own = Array.from(t.childNodes).filter((n) => n.nodeType === 3).map((n) => n.textContent).join("").trim();
    if (!own) continue;
    const cs = getComputedStyle(t);
    const rr = t.getBoundingClientRect();
    if (rr.width <= 1 || rr.height <= 1 || cs.visibility === "hidden") continue;
    if (t.closest("svg")) continue;
    const fg = toRgb(cs.color);
    const bg = bgOf(t);
    const c = contrast(fg, bg);
    texts.push({ text: own.slice(0, 40), contrast: +c.toFixed(2), fg: hex(fg), bg: hex(bg), fontSize: cs.fontSize });
    if (c < minContrast) minContrast = c;
    if (c < 4.5) lowContrast.push({ text: own.slice(0, 40), contrast: +c.toFixed(2), fg: hex(fg), bg: hex(bg) });
  }
  const whiteSurfaces = Array.from([card, ...card.querySelectorAll("*")])
    .filter((t) => {
      const c = toRgb(getComputedStyle(t).backgroundColor);
      const rr = t.getBoundingClientRect();
      return c && c.a > 0.5 && c.r > 240 && c.g > 240 && c.b > 240 && rr.width > 8 && rr.height > 8;
    })
    .map((t) => `${t.tagName.toLowerCase()}.${String(t.className).slice(0, 80)}`);

  return {
    box: { w: Math.round(r.width), h: Math.round(r.height) },
    cardBg: hex(bgOf(card)),
    text: card.innerText.replace(/\s+/g, " ").trim(),
    hasMeter: !!meter,
    meter: meterInfo,
    chartVars: { "--chart-1": resolveVar("--chart-1"), "--chart-2": resolveVar("--chart-2") },
    swatches,
    minContrast: +minContrast.toFixed(2),
    lowContrast,
    texts,
    whiteSurfaces,
  };
}

async function myWork(browser) {
  const report = {};
  for (const user of users) {
    for (const w of widths) {
      for (const theme of themes) {
        const key = `mywork-${tag}-${user}-${w}-${theme}`;
        const { context, page, messages } = await newContext(browser, w, theme);
        await login(page, user);
        await page.goto(`${BASE}/org/${ORG}/my-work`, { waitUntil: "load" });
        const heading = page.locator("#main-content").getByRole("heading", { level: 2, name: "This Week" });
        await heading.waitFor({ timeout: 90_000 });
        await page.mouse.move(0, 0);
        await page.waitForTimeout(2500);
        const card = heading.locator('xpath=ancestor::*[@data-slot="card"][1]');
        await card.scrollIntoViewIfNeeded();
        const htmlClass = await page.evaluate(() => document.documentElement.className);
        await card.screenshot({ path: path.join(OUT, `mywork-${tag}-${user}-${w}-${theme}-week.png`) });
        await page.screenshot({ path: path.join(OUT, `mywork-${tag}-${user}-${w}-${theme}-page.png`), fullPage: true });
        const current = await card.evaluate(measureWeekCard);
        const aria = await card.ariaSnapshot();
        fs.writeFileSync(path.join(OUT, `mywork-${tag}-${user}-${w}-${theme}-aria.txt`), aria);
        const imgByRole = await page.getByRole("img", { name: "Billable vs non-billable time" }).count();

        // Empty week: step back until "No time tracked this week" (max 12 weeks).
        let empty = null;
        const prev = card.getByRole("button", { name: "Previous week" });
        for (let i = 1; i <= 12; i++) {
          await prev.click();
          await card.getByText("Loading...").waitFor({ state: "detached", timeout: 30_000 }).catch(() => {});
          await page.waitForTimeout(600);
          if (await card.getByText("No time tracked this week").isVisible()) {
            await page.mouse.move(0, 0);
            await card.screenshot({ path: path.join(OUT, `mywork-${tag}-${user}-${w}-${theme}-empty-week.png`) });
            empty = { weeksBack: i, ...(await card.evaluate(measureWeekCard)) };
            empty.ariaSnapshot = await card.ariaSnapshot();
            break;
          }
        }
        report[key] = { htmlClass, imgByRoleCount: imgByRole, current, empty };
        consoleLog[key] = messages;
        console.log(
          `${key}: html=${htmlClass} meter=${current.hasMeter} name="${current.meter?.ariaLabel}" sr="${current.meter?.srSummary}" ` +
            `segs=${JSON.stringify(current.meter?.segments?.map((s) => [s.key, s.fillComputed]))} vars=${JSON.stringify(current.chartVars)} ` +
            `minContrast=${current.minContrast} low=${current.lowContrast.length} white=${current.whiteSurfaces.length} ` +
            `empty=${empty ? `${empty.weeksBack}wk meter=${empty.hasMeter}` : "not found"} console=${messages.length}`
        );
        await context.close();
      }
    }
  }
  return report;
}

// ---------------------------------------------------------------- Home throttled navigation

/** Runs in the page: Home layout boxes for whichever state (skeleton / loaded) is visible. */
function measureHome() {
  const vis = (e) => {
    if (!e) return false;
    const r = e.getBoundingClientRect();
    return r.width > 0 && r.height > 0;
  };
  const box = (e) => {
    if (!e) return null;
    const r = e.getBoundingClientRect();
    return { y: Math.round((r.top + window.scrollY) * 10) / 10, h: Math.round(r.height * 10) / 10, w: Math.round(r.width) };
  };
  const main = document.querySelector("#main-content");
  if (!main) return { t: Math.round(performance.now()), state: "no-main" };
  const loaded = Array.from(main.querySelectorAll('[data-testid="home-page"]')).find(vis) || null;
  const skel = Array.from(main.querySelectorAll('div.space-y-6[aria-busy="true"]')).find(
    (e) => vis(e) && e.querySelector('[data-slot="stat-card"][data-state="loading"]')
  ) || null;
  const root = loaded || skel;
  const out = { t: Math.round(performance.now()), state: loaded ? "loaded" : skel ? "skeleton" : "other", both: !!(loaded && skel) };
  out.main = box(main);
  out.mainPaddingTop = getComputedStyle(main).paddingTop;
  out.header = box(document.querySelector("header"));
  if (!root) {
    out.mainText = main.innerText.slice(0, 80);
    return out;
  }
  const h1 = root.querySelector("h1");
  out.h1 = box(h1);
  // Transform-free layout values: template.tsx runs a 100ms slide-in-from-bottom-[2px] entrance
  // animation (a transform), so absolute y can read 2px low on the first frames.
  const rootTop = root.getBoundingClientRect().top;
  out.rootOffsetTop = root.offsetTop;
  out.rootOffsetParent = root.offsetParent ? `${root.offsetParent.tagName}#${root.offsetParent.id}` : null;
  out.wrapperTransform = root.parentElement ? getComputedStyle(root.parentElement).transform : null;
  const rows = Array.from(root.children).filter((c) => String(c.className).includes("lg:grid-cols-12"));
  out.rows = rows.map((row) => ({
    ...box(row),
    ry: Math.round((row.getBoundingClientRect().top - rootTop) * 10) / 10,
    cells: Array.from(row.children).map((c) => ({
      testid: c.getAttribute("data-testid"),
      ...box(c),
    })),
  }));
  const tiles = Array.from(root.querySelectorAll('[data-slot="stat-card"]'));
  out.tiles = tiles.map((t) => ({
    state: t.getAttribute("data-state"),
    role: t.getAttribute("role"),
    ariaBusy: t.getAttribute("aria-busy"),
    label: t.querySelector('[data-slot="stat-card-label"]')?.textContent,
    hasDelta: !!t.querySelector('[data-slot="stat-card-delta"]'),
    hasDeltaSkeleton: !!t.querySelector('[data-slot="stat-card-delta-skeleton"]'),
    hasHint: !!t.querySelector('[data-slot="stat-card-hint"]'),
    hasSparkline: !!t.querySelector('[data-slot="stat-card-sparkline"]'),
    hasFooter: !!t.querySelector('[data-slot="stat-card-footer"]'),
    valueH: (() => {
      const v = t.querySelector('[data-slot="stat-card-value"]') || t.querySelector('[data-slot="stat-card-value-skeleton"]');
      return v ? Math.round(v.getBoundingClientRect().height * 10) / 10 : null;
    })(),
    ...box(t),
  }));
  const kpiFrame = tiles[0] ? tiles[0].closest('[class~="@container"]') : null;
  out.kpiFrame = kpiFrame ? { dataSlot: kpiFrame.getAttribute("data-slot"), ...box(kpiFrame) } : null;
  out.testidsInRoot = root.querySelectorAll("[data-testid]").length;
  out.homeTestidsInSkeleton = skel ? skel.querySelectorAll('[data-testid^="home-"]').length + (skel.hasAttribute("data-audience") ? 1 : 0) : null;
  out.ariaBusyRoot = root.getAttribute("aria-busy");
  out.audience = loaded ? loaded.getAttribute("data-audience") : null;
  out.docH = document.documentElement.scrollHeight;
  return out;
}

async function throttle(context, page, on) {
  const cdp = await context.newCDPSession(page);
  await cdp.send("Network.enable");
  if (on) {
    await cdp.send("Network.setCacheDisabled", { cacheDisabled: true });
    await cdp.send("Network.emulateNetworkConditions", {
      offline: false,
      latency: 2000,
      downloadThroughput: 50 * 1024,
      uploadThroughput: 50 * 1024,
    });
    await cdp.send("Emulation.setCPUThrottlingRate", { rate: 4 });
  } else {
    await cdp.send("Network.emulateNetworkConditions", { offline: false, latency: 0, downloadThroughput: -1, uploadThroughput: -1 });
    await cdp.send("Emulation.setCPUThrottlingRate", { rate: 1 });
  }
  return cdp;
}

function deltas(sk, ld) {
  if (!sk || !ld || !sk.rows || !ld.rows) return null;
  const d = (a, b) => (a == null || b == null ? null : Math.round((b - a) * 10) / 10);
  const out = {
    h1Y: d(sk.h1?.y, ld.h1?.y),
    rootOffsetTop: d(sk.rootOffsetTop, ld.rootOffsetTop),
    rowCount: { skeleton: sk.rows.length, loaded: ld.rows.length },
    rows: [],
  };
  const n = Math.max(sk.rows.length, ld.rows.length);
  for (let i = 0; i < n; i++) {
    const a = sk.rows[i];
    const b = ld.rows[i];
    out.rows.push({
      index: i,
      loadedCells: b ? b.cells.map((c) => c.testid) : null,
      skeleton: a ? { y: a.y, h: a.h } : null,
      loaded: b ? { y: b.y, h: b.h } : null,
      dy: a && b ? d(a.y, b.y) : null,
      dyLayout: a && b ? d(a.ry, b.ry) : null,
      dh: a && b ? d(a.h, b.h) : null,
    });
  }
  out.kpiFrame = { skeleton: sk.kpiFrame, loaded: ld.kpiFrame, dy: d(sk.kpiFrame?.y, ld.kpiFrame?.y), dh: d(sk.kpiFrame?.h, ld.kpiFrame?.h) };
  return out;
}

async function homeRun(browser, user, w, theme, navMode) {
  const key = `home-${tag}-${user}-${w}-${theme}-${navMode}`;
  const { context, page, messages } = await newContext(browser, w, theme);
  await login(page, user);
  // Unthrottled reference: the loaded layout at rest.
  await page.waitForTimeout(3000);
  const restLoaded = await page.evaluate(measureHome);
  let cdp;
  const samples = [];
  let skeletonSample = null;
  let skeletonShot = null;
  let navStart;
  if (navMode === "client") {
    await page.goto(`${BASE}/org/${ORG}/my-work`, { waitUntil: "load" });
    await page.locator("#main-content").getByRole("heading", { level: 2, name: "This Week" }).waitFor({ timeout: 90_000 });
    await page.waitForTimeout(2000);
    await page.evaluate(() => {
      window.__shifts = [];
      window.__homeSamples = [];
    });
    cdp = await throttle(context, page, true);
    const homeLink = page.getByRole("link", { name: "Home", exact: true }).filter({ visible: true }).first();
    navStart = Date.now();
    await homeLink.click({ timeout: 60_000 });
  } else {
    cdp = await throttle(context, page, true);
    navStart = Date.now();
    page.reload({ waitUntil: "commit", timeout: 300_000 }).catch(() => {});
  }
  const deadline = Date.now() + 300_000;
  while (Date.now() < deadline) {
    const m = await page.evaluate(measureHome).catch(() => null);
    if (m) {
      m.wall = Date.now() - navStart;
      samples.push(m);
      if (m.state === "skeleton" && !skeletonSample) {
        skeletonSample = m;
        skeletonShot = `home-${tag}-${user}-${w}-${theme}-${navMode}-skeleton.png`;
        await page.screenshot({ path: path.join(OUT, skeletonShot) }).catch(() => (skeletonShot = null));
        // Re-measure after the shot (fonts / late CSS) and keep the later skeleton sample too.
        const again = await page.evaluate(measureHome).catch(() => null);
        if (again && again.state === "skeleton") {
          again.wall = Date.now() - navStart;
          samples.push(again);
          skeletonSample.after = again;
        }
      }
      if (m.state === "loaded") break;
    }
    await page.waitForTimeout(50);
  }
  await page.locator("#main-content").getByTestId("home-page").filter({ visible: true }).waitFor({ timeout: 300_000 });
  await page.waitForTimeout(6000);
  const inPage = (await page.evaluate(() => window.__homeSamples).catch(() => [])) || [];
  const pool = inPage.length ? inPage : samples;
  const firstLoaded = pool.find((s) => s.state === "loaded") || null;
  const skelPool = pool.filter((s) => s.state === "skeleton");
  const lastSkeleton = skelPool[skelPool.length - 1] || ([...samples].reverse().find((s) => s.state === "skeleton") || null);
  const firstSkeleton = skelPool[0] || null;
  // Did anything inside the skeleton move while it was up (e.g. late CSS / fonts / shell)?
  const skeletonDrift = skelPool.length
    ? {
        h1Y: [...new Set(skelPool.map((s) => s.h1?.y))],
        mainY: [...new Set(skelPool.map((s) => s.main?.y))],
        headerH: [...new Set(skelPool.map((s) => s.header?.h))],
        kpiH: [...new Set(skelPool.map((s) => s.rows?.[0]?.h))],
        diaryY: [...new Set(skelPool.map((s) => s.rows?.[1]?.y))],
      }
    : null;
  const loaded = await page.evaluate(measureHome);
  const loadedShot = `home-${tag}-${user}-${w}-${theme}-${navMode}-loaded.png`;
  await page.screenshot({ path: path.join(OUT, loadedShot) });
  const shifts = await page.evaluate(() => window.__shifts);
  await throttle(context, page, false).catch(() => {});
  const htmlClass = await page.evaluate(() => document.documentElement.className);
  const result = {
    user,
    width: w,
    theme,
    navMode,
    htmlClass,
    skeletonObserved: !!skeletonSample || skelPool.length > 0,
    skeletonScreenshotted: !!skeletonShot,
    skeletonSamples: samples.filter((s) => s.state === "skeleton").length,
    inPageSamples: inPage.length,
    inPageSkeletonSamples: skelPool.length,
    inPageSkeletonSpanMs: skelPool.length ? skelPool[skelPool.length - 1].t - skelPool[0].t : null,
    inPageStates: inPage.map((s) => s.state).filter((s, i, a) => i === 0 || a[i - 1] !== s),
    skeletonDrift,
    firstSkeleton,
    sampleCount: samples.length,
    skeletonFirstAtMs: skeletonSample?.wall ?? null,
    firstLoadedAtMs: firstLoaded?.wall ?? null,
    skeletonToLoadedMs: firstSkeleton && firstLoaded ? firstLoaded.t - firstSkeleton.t : null,
    skeletonShot,
    loadedShot,
    skeleton: lastSkeleton,
    firstLoaded,
    loaded,
    restLoadedUnthrottled: restLoaded,
    deltaSkeletonVsLoaded: deltas(lastSkeleton, loaded),
    deltaSkeletonVsFirstLoaded: deltas(lastSkeleton, firstLoaded),
    layoutShifts: shifts,
    cls: +shifts.reduce((a, s) => a + s.value, 0).toFixed(4),
    clsExclRecentInput: +shifts.filter((s) => !s.hadRecentInput).reduce((a, s) => a + s.value, 0).toFixed(4),
    statesSeen: [...new Set(samples.map((s) => s.state))],
  };
  consoleLog[key] = messages;
  const dk = result.deltaSkeletonVsLoaded;
  console.log(
    `${key}: html=${htmlClass} skeleton=${result.skeletonObserved} shot=${!!skeletonShot} inPage=${skelPool.length}/${inPage.length} span=${result.inPageSkeletonSpanMs}ms drift=${JSON.stringify(skeletonDrift)} (${result.skeletonSamples} pw samples, first@${result.skeletonFirstAtMs}ms, loaded@${result.firstLoadedAtMs}ms) ` +
      `tiles=${lastSkeleton ? lastSkeleton.tiles.map((t) => `${t.state}/${t.ariaBusy}`).join(",") : "-"} ` +
      `CLS=${result.cls} (excl input ${result.clsExclRecentInput}) h1dy=${dk?.h1Y} rootOffsetDy=${dk?.rootOffsetTop} rows=${dk ? dk.rows.map((r) => `[${r.index}:dy${r.dy}/L${r.dyLayout},dh${r.dh}]`).join("") : "-"} console=${messages.length}`
  );
  await context.close();
  return [key, result];
}

async function home(browser) {
  const report = {};
  for (const user of users)
    for (const w of widths)
      for (const theme of themes)
        for (const nm of navModes) {
          try {
            const [k, r] = await homeRun(browser, user, w, theme, nm);
            report[k] = r;
          } catch (e) {
            console.error(`home ${user} ${w} ${theme} ${nm} FAILED`, e);
            report[`home-${tag}-${user}-${w}-${theme}-${nm}`] = { error: String(e) };
          }
        }
  return report;
}

(async () => {
  const browser = await chromium.launch();
  let report;
  try {
    report = mode === "mywork" ? await myWork(browser) : await home(browser);
  } finally {
    await browser.close();
    fs.writeFileSync(consoleFile, JSON.stringify(consoleLog, null, 2));
  }
  const file = path.join(OUT, `${mode}-${tag}.json`);
  const prev = fs.existsSync(file) ? JSON.parse(fs.readFileSync(file, "utf8")) : {};
  fs.writeFileSync(file, JSON.stringify({ ...prev, ...report }, null, 2));
})().catch((e) => {
  console.error(e);
  process.exit(1);
});
