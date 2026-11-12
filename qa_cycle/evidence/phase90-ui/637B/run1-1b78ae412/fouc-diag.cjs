// 637B OBS 2 follow-up: cold-cache (CDP cache disabled) throttled reload with stored theme.
// Records the DOM at the first rAF and every composited frame (CDP Page.startScreencast), then
// measures each frame's mean luminance in a separate decoder page, to tell whether a light frame
// was actually presented before the html "dark" class landed.
const { chromium } = require('/Users/rakheendama/Projects/2026/worktree-epic-637B/frontend/node_modules/@playwright/test');
const fs = require('fs');
const OUT = '/Users/rakheendama/Projects/2026/worktree-epic-637B/qa_cycle/evidence/phase90-ui/637B';
const BASE = 'http://localhost:3001';
const HOME = '/org/e2e-test-org/dashboard';
// DIAG_RUNS env (JSON [[stored, cacheDisabled], ...]) overrides the default run list.
const RUNS = process.env.DIAG_RUNS ? JSON.parse(process.env.DIAG_RUNS) : [['dark', true], ['dark', true], ['dark', true], ['light', true], ['dark', false]];
const REPORT = process.env.DIAG_REPORT || 'fouc-diag-report.json';

const INIT = () => {
  const samples = [];
  window.__fouc = samples;
  const snap = () => (document.body ? [...document.body.children].map((c) => c.tagName.toLowerCase() + (c.id ? '#' + c.id : '') + (c.tagName === 'SCRIPT' ? '[' + (c.src ? 'src' : 'inline:' + c.textContent.slice(0, 60)) + ']' : '') + (c.tagName === 'LINK' ? '[' + c.rel + ']' : '')) : null);
  const rec = (ev, extra) => {
    const el = document.documentElement;
    samples.push({ ev, t: Math.round(performance.now() * 10) / 10, dark: el ? el.classList.contains('dark') : null, cls: el ? el.className : null, body: snap(), bodyBg: document.body ? getComputedStyle(document.body).backgroundColor : null, headSheets: document.styleSheets.length, ...extra });
  };
  rec('init-script');
  let n = 0;
  const loop = () => { n += 1; rec('rAF#' + n); if (n < 6) requestAnimationFrame(loop); };
  requestAnimationFrame(loop);
  new MutationObserver((muts) => {
    for (const m of muts) if (m.type === 'attributes' && m.target === document.documentElement) rec('mutation:html.class');
  }).observe(document, { subtree: true, attributes: true, attributeFilter: ['class'] });
  try { new PerformanceObserver((l) => { for (const e of l.getEntries()) rec('paint:' + e.name + '@' + e.startTime.toFixed(1)); }).observe({ type: 'paint', buffered: true }); } catch (e) { /* ignore */ }
};

(async () => {
  const browser = await chromium.launch();
  const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 }, colorScheme: 'light' });
  const page = await ctx.newPage();
  await page.goto(BASE + '/mock-login', { waitUntil: 'load' });
  await page.getByRole('button', { name: 'Sign In' }).click();
  await page.waitForURL(/\/dashboard/, { timeout: 30000 });
  await page.locator('h1').first().waitFor({ timeout: 45000 });
  const decoderCtx = await browser.newContext();
  const decoder = await decoderCtx.newPage();
  const out = [];
  let i = 0;
  for (const [stored, cacheDisabled] of RUNS) {
    i += 1;
    // store the theme first so the outgoing (old) document already paints in it: any frame of the
    // other luminance after the reload can then only come from the new document.
    await page.evaluate((v) => localStorage.setItem('kazi-theme', v), stored);
    const p = await ctx.newPage();
    await p.emulateMedia({ colorScheme: 'light' });
    await p.addInitScript(INIT);
    await p.goto(BASE + HOME, { waitUntil: 'load' });
    await p.locator('h1').first().waitFor({ timeout: 45000 });
    await p.evaluate((v) => localStorage.setItem('kazi-theme', v), stored);
    const cdp = await ctx.newCDPSession(p);
    await cdp.send('Network.enable');
    await cdp.send('Network.setCacheDisabled', { cacheDisabled });
    await cdp.send('Emulation.setCPUThrottlingRate', { rate: 4 });
    await cdp.send('Network.emulateNetworkConditions', { offline: false, latency: 562.5, downloadThroughput: 180000, uploadThroughput: 84375 });
    const frames = [];
    cdp.on('Page.screencastFrame', async (f) => {
      frames.push({ ts: f.metadata.timestamp, data: f.data });
      cdp.send('Page.screencastFrameAck', { sessionId: f.sessionId }).catch(() => {});
    });
    await cdp.send('Page.startScreencast', { format: 'png', everyNthFrame: 1, maxWidth: 360, maxHeight: 225 });
    const t0 = Date.now() / 1000;
    await p.reload({ waitUntil: 'load', timeout: 180000 });
    await p.locator('h1').first().waitFor({ timeout: 120000 });
    await p.waitForTimeout(1500);
    await cdp.send('Page.stopScreencast');
    const samples = await p.evaluate(() => window.__fouc);
    const lum = [];
    for (const f of frames) {
      const v = await decoder.evaluate(async (src) => {
        const img = new Image(); img.src = src; await img.decode();
        const c = document.createElement('canvas'); c.width = img.width; c.height = img.height;
        const g = c.getContext('2d'); g.drawImage(img, 0, 0);
        const d = g.getImageData(0, 0, c.width, c.height).data;
        let s = 0; let light = 0; const px = d.length / 4;
        for (let k = 0; k < d.length; k += 4) { const y = 0.2126 * d[k] + 0.7152 * d[k + 1] + 0.0722 * d[k + 2]; s += y; if (y > 200) light += 1; }
        return { mean: Math.round(s / px), lightPxPct: Math.round((light / px) * 1000) / 10, w: c.width, h: c.height };
      }, 'data:image/png;base64,' + f.data);
      lum.push({ tRel: Math.round((f.ts - t0) * 1000), ...v });
    }
    const label = `${process.env.DIAG_PREFIX || ''}${stored}-${cacheDisabled ? 'nocache' : 'warm'}-run${i}`;
    // save the first 3 frames for the record
    frames.slice(0, 3).forEach((f, k) => fs.writeFileSync(`${OUT}/fouc-frames/${label}-frame${k + 1}.png`, Buffer.from(f.data, 'base64')));
    out.push({ label, stored, cacheDisabled, frameCount: frames.length, frames: lum, samples: samples.slice(0, 14) });
    await p.close();
  }
  fs.writeFileSync(`${OUT}/${REPORT}`, JSON.stringify(out, null, 2));
  for (const r of out) {
    console.log('==', r.label, 'frames', r.frameCount);
    console.log(' frames(mean lum / %light px):', r.frames.slice(0, 8).map((f) => `${f.tRel}ms:${f.mean}/${f.lightPxPct}%`).join('  '));
    for (const s of r.samples.slice(0, 8)) console.log(' ', s.t, s.ev, 'dark=' + s.dark, JSON.stringify(s.cls), 'bodyBg=' + s.bodyBg, 'sheets=' + s.headSheets, JSON.stringify(s.body));
  }
  await browser.close();
})().catch((e) => { console.error('DIAG FAILED', e); process.exit(1); });
