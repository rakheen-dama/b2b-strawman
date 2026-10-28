async (page) => {
  const fs = require('fs');
  const urls = JSON.parse(fs.readFileSync('/tmp/628A/after/work/urls.json','utf8'));
  const START = parseInt(fs.readFileSync('/tmp/628A/after/work/start.txt','utf8'));
  const END = Math.min(urls.length, START + 32);
  const scanFn = () => {
    const re = /\b(projects?|customers?|invoices?|invoic(ed|ing))\b/i;
    const vis = (el) => { if (!el) return false; const s = getComputedStyle(el); if (s.display==='none'||s.visibility==='hidden') return false; const r = el.getClientRects(); return r.length>0 && (r[0].width>0||r[0].height>0); };
    const hits = [];
    const w = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT);
    let n;
    while ((n = w.nextNode())) {
      const t = n.textContent; const m = t && t.match(re);
      if (!m) continue;
      const el = n.parentElement; if (!el || ['SCRIPT','STYLE','NOSCRIPT'].includes(el.tagName)) continue;
      if (!vis(el)) continue;
      const block = (el.closest('p,li,td,th,h1,h2,h3,h4,label,button,a,span,div')||el).innerText || t;
      hits.push({src:'text', word:m[0], ctx:t.trim().slice(0,200), block:block.trim().replace(/\s+/g,' ').slice(0,200), kind:el.tagName.toLowerCase()});
    }
    for (const attr of ['placeholder','aria-label','title','alt']) {
      document.querySelectorAll('['+attr+']').forEach(el => {
        const v = el.getAttribute(attr); const m = v && v.match(re);
        if (m && vis(el)) hits.push({src:attr, word:m[0], ctx:v.slice(0,200), kind:el.tagName.toLowerCase()});
      });
    }
    return hits;
  };
  const results = [];
  for (let i = START; i < END; i++) {
    const u = urls[i];
    let hits = [], err = null;
    try {
      await page.goto(u.url, {waitUntil:'load', timeout:45000});
      await page.waitForTimeout(2500);
      hits = await page.evaluate(scanFn);
      const shot = '/tmp/628A/after/' + String(u.n).padStart(3,'0') + '-' + u.name + '.png';
      await page.screenshot({path: shot, fullPage: true});
    } catch (e) { err = String(e).slice(0,200); }
    results.push({n:u.n, name:u.name, url:u.url, finalUrl: page.url(), hits, err});
  }
  fs.writeFileSync('/tmp/628A/after/work/scan-' + START + '.json', JSON.stringify(results, null, 1));
  return results.map(r => r.n + ' ' + r.name + ' hits=' + r.hits.length + (r.err? ' ERR '+r.err:'')).join('\n');
}
