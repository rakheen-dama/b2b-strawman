// 662A step 4 (S8 scripted checks): each is a register row's reproduction attempt, numbers recorded in
// RAW/checks.json and tiles in RAW/S8/. Reads first; the only write is K-11's Save View (last, after S1),
// and X5's typing is never saved (the page is reloaded to discard it).
//   K-07 palette focus return · K-05/I-02/I-05 collapsed rail 1024×768 (fold + breadcrumb wrap) · I-06 768×1024
//   matter detail · K-06 matters table width at 1024 · K-09 /my-work tables at 1440 · K-10 long reference ·
//   K-12 forbidden-tab notice colours (Carol) · X5 tab switch with unsaved edits · K-04/K-21/I-03/I-04 focused
//   row-menu item + accent buttons · K-20 matter Activity label · K-11 saved view Client column (mandatory).
// Usage (worktree root): BUDGET_S=540 NODE_OPTIONS="" node qa_cycle/evidence/phase91-ui/kinks/scripts/04-checks.cjs
// Resumable: a check whose key exists in checks.json is not re-run.
const path = require("path");
const fs = require("fs");
const L = require("./lib.cjs");

const ID = L.ids();
const OUTD = path.join(L.RAW, "S8");
fs.mkdirSync(OUTD, { recursive: true });
const CF = path.join(L.RAW, "checks.json");
const checks = L.readJson(CF, {});
const save = () => fs.writeFileSync(CF, JSON.stringify(checks));
const shot = async (page, name, opts = {}) => {
  const f = path.join(OUTD, `${name}.png`);
  await page.screenshot({ path: f, ...opts });
  return L.rel(f);
};
const settle = async (page, url, sel = "#main-content h1") => {
  const r = await page.goto(url.startsWith("http") ? url : L.BASE + url, { waitUntil: "load", timeout: 90000 });
  await page.locator(sel).first().waitFor({ timeout: 20000 }).catch(() => {});
  await page.mouse.move(1, 1);
  await page.waitForTimeout(1800);
  return r ? r.status() : null;
};
// Colour pair → WCAG ratio in node (canvas-normalised by the INIT helpers window.__rgb).
const pair = async (page, handle, { fgOf = null } = {}) =>
  page.evaluate(
    ([el, fgSel]) => {
      const cs = getComputedStyle(el);
      let bgEl = el;
      while (bgEl && getComputedStyle(bgEl).backgroundColor.match(/rgba\(0, 0, 0, 0\)|transparent/)) bgEl = bgEl.parentElement;
      const bg = getComputedStyle(bgEl || document.body).backgroundColor;
      const fgNode = fgSel ? el.querySelector(fgSel) : el;
      const fg = fgNode ? getComputedStyle(fgNode).color : cs.color;
      return { text: el.innerText.trim().slice(0, 40), bg, fg, bgHex: window.__hex(bg), fgHex: window.__hex(fg), bgRgb: window.__rgb(bg), fgRgb: window.__rgb(fg, bg) };
    },
    [handle, fgOf],
  );
const withRatio = (p) => (p ? { ...p, ratio: L.ratio(p.fgRgb, p.bgRgb) } : null);

async function run(key, fn) {
  if (checks[key] || L.overBudget()) return;
  console.log("check", key, L.elapsed() + "s");
  try {
    checks[key] = await fn();
  } catch (e) {
    checks[key] = { error: String(e).slice(0, 300) };
  }
  checks[key].at = new Date().toISOString();
  save();
}

(async () => {
  const browser = await L.chromium.launch();
  const ctxP = async (who, vp, theme, collapsed = false) => {
    const ctx = await L.ctxFor(browser, who, vp, theme, collapsed);
    const page = await ctx.newPage();
    const sink = L.sinkFor(page);
    return { ctx, page, sink };
  };

  // ---- K-07: command palette focus return (header button and sidebar pill), 1440 light + dark, Alice.
  await run("K-07", async () => {
    const out = {};
    for (const theme of ["light", "dark"]) {
      const { ctx, page } = await ctxP("alice", 1440, theme);
      await settle(page, "/dashboard");
      for (const where of ["header", "sidebar"]) {
        const trg = page.locator(where === "header" ? 'header button[aria-label="Search, Command K"]' : '[data-sidebar="sidebar"] [aria-label="Search, Command K"]').first();
        await trg.evaluate((e) => e.setAttribute("data-662a-k07", "1"));
        await trg.focus();
        await page.keyboard.press("Enter");
        const opened = await page.locator('[role="dialog"]:visible').first().waitFor({ timeout: 4000 }).then(() => true, () => false);
        const tile = opened ? await shot(page, `K-07-palette-open-${where}-${theme}`) : null;
        await page.keyboard.press("Escape");
        await page.waitForTimeout(600);
        out[`${where}-${theme}`] = { opened, tile, ...(await page.evaluate(() => {
          const a = document.activeElement;
          return { focusReturnedToTrigger: !!a && a.getAttribute("data-662a-k07") === "1", activeElement: a ? `${a.tagName.toLowerCase()} ${(a.getAttribute("aria-label") || a.innerText || "").trim().slice(0, 40)}` : null };
        })) };
        await trg.evaluate((e) => e.removeAttribute("data-662a-k07"));
      }
      await ctx.close();
    }
    out.verdict = Object.entries(out).filter(([, v]) => v.opened && !v.focusReturnedToTrigger).map(([k]) => k);
    return out;
  });

  // ---- K-05 / I-02 / I-05: 1024×768 with the rail collapsed (fold of the icon rail; breadcrumb wrap), light + dark.
  await run("K-05-I-02-I-05", async () => {
    const out = {};
    for (const theme of ["light", "dark"]) {
      const { ctx, page } = await ctxP("alice", 1024, theme, true);
      for (const [k, u] of [["dashboard", "/dashboard"], ["matter", `/matters/${ID.alice.matter}`]]) {
        await settle(page, u);
        const tile = await shot(page, `I-02-I-05-collapsed-1024x768-${k}-${theme}`);
        const fold = (await page.evaluate(L.pageProbe, {})).fold;
        const crumb = await page.evaluate(() => {
          const n = document.querySelector('nav[aria-label="Breadcrumb"]');
          if (!n) return null;
          const items = [...n.querySelectorAll("a, span")].filter((e) => e.children.length === 0 && e.textContent.trim()).map((e) => ({ text: e.textContent.trim().slice(0, 40), lines: e.getClientRects().length, h: Math.round(e.getBoundingClientRect().height) }));
          return { navH: Math.round(n.getBoundingClientRect().height), navW: Math.round(n.getBoundingClientRect().width), wrapped: items.filter((i) => i.lines > 1 || i.h > 24), items };
        });
        out[`${k}-${theme}`] = { tile, fold, crumb };
      }
      await ctx.close();
    }
    // 1440×900 expanded fold (K-05) for both personas, light.
    for (const who of ["alice", "carol"]) {
      const { ctx, page } = await ctxP(who, 1440, "light");
      await settle(page, "/dashboard");
      out[`fold-1440x900-expanded-${who}`] = (await page.evaluate(L.pageProbe, {})).fold;
      // expand every collapsed rail group, then re-measure (are Trust items below the fold once open?)
      const groups = page.locator('[data-sidebar="sidebar"] button[aria-expanded="false"]');
      const n = await groups.count();
      for (let i = 0; i < n; i++) await groups.nth(0).click().catch(() => {});
      await page.waitForTimeout(500);
      out[`fold-1440x900-all-groups-open-${who}`] = { ...(await page.evaluate(L.pageProbe, {})).fold, tile: await shot(page, `K-05-fold-1440x900-groups-open-${who}`) };
      await ctx.close();
    }
    return out;
  });

  // ---- I-06: 768×1024 matter detail, light + dark (document scrollWidth vs 768).
  await run("I-06", async () => {
    const out = {};
    for (const theme of ["light", "dark"]) {
      const { ctx, page } = await ctxP("alice", 768, theme);
      await settle(page, `/matters/${ID.alice.matter}`);
      out[theme] = { scrollWidth: await page.evaluate(() => document.scrollingElement.scrollWidth), innerWidth: 768, tile: await shot(page, `I-06-matter-768x1024-${theme}`) };
      await ctx.close();
    }
    return out;
  });

  // ---- K-06: /matters table vs its container at 1024 light (641A: 904 in 718); K-09: /my-work tables at 1440.
  await run("K-06-K-09", async () => {
    const tables = (page) =>
      page.evaluate(() =>
        [...document.querySelectorAll("#main-content table")].filter((t) => t.getBoundingClientRect().width > 0).map((t) => {
          const box = t.parentElement;
          const card = t.closest('[data-slot="card"]');
          const cr = card ? card.getBoundingClientRect() : box.getBoundingClientRect();
          return { testid: t.closest("[data-testid]")?.getAttribute("data-testid") || null, tableW: Math.round(t.getBoundingClientRect().width), tableScrollW: t.scrollWidth, containerClientW: box.clientWidth, containerScrollW: box.scrollWidth, containerOverflowX: getComputedStyle(box).overflowX, cardW: Math.round(cr.width), th: [...t.querySelectorAll("thead th")].map((h) => [h.innerText.trim().slice(0, 16), Math.round(h.getBoundingClientRect().width), Math.round(h.getBoundingClientRect().right > cr.right + 1 ? h.getBoundingClientRect().right - cr.right : 0)]) };
        }),
      );
    const out = {};
    {
      const { ctx, page } = await ctxP("alice", 1024, "light");
      await settle(page, "/matters");
      out["K-06-matters-1024-light"] = { tables: await tables(page), tile: await shot(page, "K-06-matters-1024-light") };
      await ctx.close();
    }
    for (const who of ["alice", "carol"])
      for (const theme of ["light", "dark"]) {
        const { ctx, page } = await ctxP(who, 1440, theme);
        await settle(page, "/my-work");
        out[`K-09-my-work-1440-${theme}-${who}`] = { tables: await tables(page), tile: await shot(page, `K-09-my-work-1440-${theme}-${who}`, { fullPage: true }) };
        await ctx.close();
      }
    return out;
  });

  // ---- K-10: only reproducible with a ≥ 150-char reference; the lifecycle data max is recorded by 01-ids.
  await run("K-10", async () => ({ maxReferenceLength: ID.alice.longRefs && ID.alice.longRefs.maxReferenceLength, verdict: "not reproducible on lifecycle data (no reference ≥ 150 chars); the 150-char top-up is not authorised (D-14 covers only the empty detail pages)" }));

  // ---- K-12: Carol on a forbidden tab — the notice's colours, light + dark.
  await run("K-12", async () => {
    const out = {};
    for (const theme of ["light", "dark"]) {
      const { ctx, page } = await ctxP("carol", 1440, theme);
      await settle(page, "/settings/firm?tab=roles");
      const n = page.locator('#main-content p[role="status"]').first();
      const found = await n.count();
      out[theme] = found ? { ...withRatio(await pair(page, await n.elementHandle())), border: await n.evaluate((e) => window.__hex(getComputedStyle(e).borderTopColor)), tile: await shot(page, `K-12-forbidden-notice-${theme}`) } : { found: 0, tile: await shot(page, `K-12-forbidden-notice-${theme}`) };
      await ctx.close();
    }
    return out;
  });

  // ---- X5: type into General, switch tab, come back — is the edit kept? (never saved; reload discards)
  await run("X5", async () => {
    const { ctx, page } = await ctxP("alice", 1440, "light");
    await settle(page, "/settings/firm?tab=general");
    const inp = page.locator('#main-content input:not([type]):not([disabled]):not([readonly]), #main-content input[type="text"]:not([disabled]):not([readonly])').first();
    const meta = await inp.evaluate((e) => ({ id: e.id, name: e.name, label: (e.labels && e.labels[0] && e.labels[0].innerText) || e.getAttribute("aria-label"), value: e.value }));
    await inp.click();
    await page.keyboard.press("End");
    await page.keyboard.type("662A-X5");
    const typed = await inp.inputValue();
    const t1 = await shot(page, "X5-1-typed");
    const tabs = page.locator('#main-content [role="tab"]');
    const other = tabs.nth(1);
    const otherName = (await other.innerText()).trim();
    await other.click();
    await page.waitForTimeout(2500);
    const urlAway = page.url().replace(L.APP, "");
    await tabs.nth(0).click();
    await page.waitForTimeout(2500);
    await page.locator('#main-content input[type="text"], #main-content input:not([type])').first().waitFor({ timeout: 10000 }).catch(() => {});
    const back = await page.evaluate((m) => {
      const e = (m.id && document.getElementById(m.id)) || (m.name && document.querySelector(`#main-content input[name="${m.name}"]`));
      return e ? e.value : null;
    }, meta);
    const t2 = await shot(page, "X5-2-after-tab-round-trip");
    await page.reload({ waitUntil: "load" });
    await page.waitForTimeout(1500);
    const afterReload = await page.evaluate((m) => {
      const e = (m.id && document.getElementById(m.id)) || (m.name && document.querySelector(`#main-content input[name="${m.name}"]`));
      return e ? e.value : null;
    }, meta);
    await ctx.close();
    return { input: meta, typed, otherTab: otherName, urlAway, valueAfterReturn: back, lost: back !== typed, valueAfterReload: afterReload, savedAccidentally: afterReload === typed, tiles: [t1, t2] };
  });

  // ---- K-04 / K-21 / I-03 / I-04: custom-fields row menu, ArrowDown ×2 (focused item bg/fg/icon), and the
  // accent buttons on the fee-note detail, 1440 light + dark (the 661B capture-after.cjs:646-705 probe).
  await run("K-04-K-21-I-03-I-04", async () => {
    const out = {};
    for (const theme of ["light", "dark"]) {
      const { ctx, page } = await ctxP("alice", 1440, theme);
      await settle(page, "/settings/matters?tab=custom-fields");
      const trig = page.locator('button[aria-label^="Actions for"]').first();
      await trig.waitFor({ state: "visible", timeout: 20000 });
      await trig.click();
      const menu = page.locator('[role="menu"]').first();
      await menu.waitFor({ state: "visible" });
      await page.waitForTimeout(300);
      const items = [];
      for (const step of [1, 2]) {
        await page.keyboard.press("ArrowDown");
        await page.waitForTimeout(400);
        const pr = await page.evaluate(() => {
          const el = document.activeElement && document.activeElement.getAttribute("role") === "menuitem" ? document.activeElement : document.querySelector('[role="menuitem"][data-highlighted]');
          if (!el) return null;
          const cs = getComputedStyle(el);
          const svg = el.querySelector("svg");
          const mbg = getComputedStyle(el.closest('[role="menu"]')).backgroundColor;
          return { text: el.textContent.trim(), variant: el.getAttribute("data-variant"), cls: (el.getAttribute("class") || "").slice(0, 160), bgHex: window.__hex(cs.backgroundColor), fgHex: window.__hex(cs.color), bgRgb: window.__rgb(cs.backgroundColor, mbg), fgRgb: window.__rgb(cs.color, cs.backgroundColor), iconHex: svg ? window.__hex(getComputedStyle(svg).color) : null, iconRgb: svg ? window.__rgb(getComputedStyle(svg).color, cs.backgroundColor) : null };
        });
        if (pr) {
          pr.textRatio = L.ratio(pr.fgRgb, pr.bgRgb);
          pr.iconRatio = pr.iconRgb ? L.ratio(pr.iconRgb, pr.bgRgb) : null;
        }
        const mb = await menu.boundingBox();
        pr && (pr.tile = await shot(page, `K-04-I-03-row-menu-${theme}-down${step}`, { clip: { x: Math.max(0, mb.x - 420), y: Math.max(0, mb.y - 60), width: Math.min(820, 1440 - Math.max(0, mb.x - 420)), height: Math.min(900 - Math.max(0, mb.y - 60), mb.height + 120) } }));
        items.push(pr);
      }
      await page.keyboard.press("Escape");
      out[`row-menu-${theme}`] = items;
      // accent buttons on the fee-note detail
      await settle(page, `/invoices/${ID.alice.invoice}`);
      const acc = page.locator('#main-content [data-slot="button"][data-variant="accent"]');
      const n = await acc.count();
      const list = [];
      for (let i = 0; i < Math.min(n, 4); i++) {
        const h = await acc.nth(i).elementHandle();
        if (!(await acc.nth(i).isVisible())) continue;
        list.push(withRatio(await pair(page, h)));
      }
      out[`accent-buttons-fee-note-${theme}`] = { count: n, buttons: list, tile: await shot(page, `K-04-K-21-fee-note-accent-${theme}`) };
      await ctx.close();
    }
    return out;
  });

  // ---- K-20: the matter Activity tab label ("Activity · Activity"?), 1440 light, Alice.
  await run("K-20", async () => {
    const { ctx, page } = await ctxP("alice", 1440, "light");
    await settle(page, `/matters/${ID.alice.matter}?tab=activity`);
    const r = await page.evaluate(() => {
      const bar = document.querySelector('#main-content [role="tablist"]');
      const sel = [...document.querySelectorAll('#main-content [role="tab"][aria-selected="true"], #main-content [data-state="open"][aria-haspopup], #main-content button[aria-current="page"]')].map((t) => t.innerText.trim().replace(/\s+/g, " "));
      const panel = document.querySelector('#main-content [role="tabpanel"][data-state="active"]');
      return { tablistText: bar ? bar.innerText.replace(/\s+/g, " ").slice(0, 400) : null, selected: sel, panelHeadings: panel ? [...panel.querySelectorAll("h2,h3,h4")].map((h) => h.textContent.trim()).slice(0, 5) : [], activityMatches: (document.querySelector("#main-content").innerText.match(/Activity\s*·\s*Activity|Activity\s+Activity/g) || []) };
    });
    const tile = await shot(page, "K-20-matter-activity-1440-light");
    await ctx.close();
    return { ...r, tile };
  });

  // ---- K-11 (FD-11, mandatory; the only write here): saved view with explicit columns on /clients.
  await run("K-11", async () => {
    const VIEW = "662A K-11 view";
    const out = { steps: [] };
    const viewTabs = (page) => page.evaluate(() => [...document.querySelectorAll('[data-testid="saved-view-selector"] [role="tab"]')].map((t) => t.innerText.trim()));
    {
      const { ctx, page } = await ctxP("alice", 1440, "light");
      await settle(page, "/clients");
      out.tabsBefore = await viewTabs(page);
      if (!out.tabsBefore.includes(VIEW)) {
        await page.locator("#main-content").getByRole("button", { name: "Save View" }).click();
        const dlg = page.getByRole("dialog");
        await dlg.waitFor();
        await page.waitForTimeout(500);
        const clickNext = () => dlg.getByRole("button", { name: "Next" }).dispatchEvent("click");
        await clickNext();
        await page.waitForTimeout(400);
        out.step2Columns = await dlg.evaluate((d) => [...d.querySelectorAll('button[role="checkbox"],input[type="checkbox"]')].map((c) => ({ label: ((c.id && d.querySelector(`label[for="${c.id}"]`)) || c.closest("label") || c.parentElement).textContent.trim().slice(0, 30), checked: c.getAttribute("aria-checked") === "true" || c.checked === true })));
        await clickNext();
        await page.waitForTimeout(400);
        await dlg.locator("#view-name").fill(VIEW);
        await dlg.getByRole("button", { name: "Save View" }).dispatchEvent("click");
        await dlg.waitFor({ state: "hidden", timeout: 30000 });
        await page.waitForTimeout(1000);
        out.created = `${VIEW} — created through the Save View dialog (All clients, default columns = explicit columns, not shared)`;
        out.steps.push("created");
      } else out.steps.push("view already existed");
      await ctx.close();
    }
    out.measure = {};
    for (const vp of [1024, 1440])
      for (const theme of ["light", "dark"]) {
        const { ctx, page } = await ctxP("alice", vp, theme);
        await settle(page, "/clients");
        const defaultCol = await page.evaluate(() => {
          const th = [...document.querySelectorAll("#main-content table thead th")].find((h) => /^client$/i.test(h.innerText.trim()));
          return th ? Math.round(th.getBoundingClientRect().width) : null;
        });
        await page.locator('[data-testid="saved-view-selector"]').getByRole("tab", { name: VIEW, exact: true }).first().click();
        await page.waitForTimeout(600);
        await page.locator("#main-content table").first().waitFor({ timeout: 15000 }).catch(() => {});
        await page.mouse.move(1, 1);
        await page.waitForTimeout(1800);
        const m = await page.evaluate(() => {
          const ths = [...document.querySelectorAll("#main-content table thead th")];
          const i = ths.findIndex((h) => /^client$/i.test(h.innerText.trim()));
          const th = ths[i];
          const links = i >= 0 ? [...document.querySelectorAll(`#main-content table tbody tr td:nth-child(${i + 1}) a`)] : [];
          const t = document.querySelector("#main-content table");
          return { url: location.pathname.replace(/^\/org\/[^/]+/, "") + location.search, columns: ths.map((h) => [h.innerText.trim().slice(0, 16), Math.round(h.getBoundingClientRect().width)]), clientTh: th ? Math.round(th.getBoundingClientRect().width) : null, linkWidths: links.map((a) => Math.round(a.getBoundingClientRect().width)), truncated: links.filter((a) => a.scrollWidth > a.clientWidth + 1).length, links: links.length, tableW: t ? Math.round(t.getBoundingClientRect().width) : null, containerW: t ? t.parentElement.clientWidth : null };
        });
        out.measure[`${vp}-${theme}`] = { defaultViewClientTh: defaultCol, ...m, tile: await shot(page, `K-11-saved-view-${vp}-${theme}`) };
        await ctx.close();
      }
    const at1024 = out.measure["1024-light"];
    out.verdict = at1024 && at1024.clientTh != null && at1024.clientTh <= 40 ? `K-11 REPRODUCED on 16423ca4b: Client th = ${at1024.clientTh} px at 1024` : `K-11 NOT reproduced on 16423ca4b: Client th = ${at1024 && at1024.clientTh} px at 1024 (642A baseline 16 px)`;
    return out;
  });

  await browser.close();
  const want = ["K-07", "K-05-I-02-I-05", "I-06", "K-06-K-09", "K-10", "K-12", "X5", "K-04-K-21-I-03-I-04", "K-20", "K-11"];
  const left = want.filter((k) => !checks[k]);
  console.log(left.length ? `REMAINING ${left.join(",")}` : "ALL DONE 04-checks");
})().catch((e) => {
  console.error("FATAL", e);
  process.exit(1);
});
