// Recompute the "before" selected-label contrast from the recorded before-probe colours, painted on a canvas (sRGB).
const { chromium } = require(process.env.PW);
const fs = require('fs');
(async () => {
  const b = await chromium.launch(); const p = await b.newPage();
  const out = {};
  for (const t of ['dark', 'light']) {
    const d = JSON.parse(fs.readFileSync(`${__dirname}/before-${t}-probe.json`));
    out[t] = await p.evaluate(({ card, sb, lc }) => {
      const paint = (...layers) => { const cv = document.createElement('canvas'); cv.width = cv.height = 1; const g = cv.getContext('2d', { colorSpace: 'srgb' }); layers.forEach(c => { g.fillStyle = c; g.fillRect(0, 0, 1, 1); }); const [r, gg, bb] = g.getImageData(0, 0, 1, 1).data; return [r, gg, bb]; };
      const lum = ([r, g, b]) => [r, g, b].map(v => { v /= 255; return v <= 0.03928 ? v / 12.92 : ((v + 0.055) / 1.055) ** 2.4; }).reduce((s, v, i) => s + v * [0.2126, 0.7152, 0.0722][i], 0);
      const bg = paint(card, sb), lab = paint(card, lc);
      const [hi, lo] = [lum(bg), lum(lab)].sort((x, y) => y - x);
      return { bgRgb: bg, labelRgb: lab, contrast: ((hi + 0.05) / (lo + 0.05)).toFixed(2) };
    }, { card: d.resting.cardBg, sb: d.selectedAndHover.selectedBg, lc: d.selectedAndHover.selectedLabelColor });
  }
  console.log(JSON.stringify(out)); fs.writeFileSync(`${__dirname}/before-selected-recompute.json`, JSON.stringify(out, null, 2));
  await b.close();
})();
