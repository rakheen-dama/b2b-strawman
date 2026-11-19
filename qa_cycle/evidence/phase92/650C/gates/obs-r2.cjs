// 650C OBS run 2 (re-probe @733b8352f) — phase r: the F-650C-OBS-1..5 fixes, read from the downloads of
// phases s / c / e / f of the same run (OBS_OUT=obs-r2) plus new firm-wide schedule runs through the UI.
//   OBS-1  wide PDFs on landscape A4, every column on the page (text + page size); the other seven PDFs
//          rendered hi-res for a visual right-edge check (qlmanage, vector render of page 1)
//   OBS-2  reconciliation / Section 35 PDFs print the comparison labels with "–", no "#"
//   OBS-3  firm-wide schedule includes the primary account (opened 19 Nov 2026, postings Aug–Sep 2026);
//          a firm-wide range before any posting shows Identity Holds "—"
//   OBS-4  summary-card dates in words; Section 35 part (2) / (6) labels dated in words
//   OBS-5  Rule 54.15 list "After Lock Net" as currency
const L = require("./obs-lib.cjs");
const { fs, path, OUT, qa, flat, settle, snap } = L;
const { execFileSync } = require("child_process");

module.exports = (X) => {
  const { state, R, B, q, TAG, NINE, SCOPE_NOTE, runReport } = X;
  const SCHED = "trust-interest-charges-schedule";
  const card = (res, label) => res.cards.find((c) => c.label === label)?.value ?? null;
  const asObjects = (res) => res.rows.map((cells) => Object.fromEntries(res.headers.map((h, i) => [h, cells[i]])));
  const readJson = (f) => JSON.parse(fs.readFileSync(path.join(OUT, f), "utf8"));
  /** page size of page 1 in points (sips reads the PDF's page box) */
  const pageSize = (pdf) => {
    const o = execFileSync("sips", ["-g", "pixelWidth", "-g", "pixelHeight", path.join(OUT, pdf)], { encoding: "utf8" });
    const w = Number(o.match(/pixelWidth: ([\d.]+)/)[1]), h = Number(o.match(/pixelHeight: ([\d.]+)/)[1]);
    return { w, h, landscape: w > h, a4: Math.abs(Math.max(w, h) - 841.9) < 2 && Math.abs(Math.min(w, h) - 595.3) < 2 };
  };
  const pageCount = (pdf) => Number(execFileSync("osascript", ["-l", "JavaScript", "-e", `ObjC.import('PDFKit'); function run(a){ return $.PDFDocument.alloc.initWithURL($.NSURL.fileURLWithPath(a[0])).pageCount }`, path.join(OUT, pdf)], { encoding: "utf8" }).trim());
  /** vector render of page 1 at 1800 px on the long edge, for looking at the right edge */
  const hiRes = (pdf) => {
    const dir = fs.mkdtempSync(path.join(require("os").tmpdir(), "obs-r2-ql-"));
    try {
      execFileSync("qlmanage", ["-t", "-s", "1800", "-o", dir, path.join(OUT, pdf)], { stdio: "ignore" });
      const png = fs.readdirSync(dir).find((f) => f.endsWith(".png"));
      if (!png) return null;
      const dest = pdf.replace(/\.pdf$/, ".pdf-page1-hi.png");
      fs.copyFileSync(path.join(dir, png), path.join(OUT, dest));
      return dest;
    } finally { fs.rmSync(dir, { recursive: true, force: true }); }
  };
  const txt = (f) => (fs.existsSync(path.join(OUT, f)) ? fs.readFileSync(path.join(OUT, f), "utf8") : "");
  const squash = (s) => String(s).toUpperCase().replace(/\s+/g, "");
  // en-ZA short month via the app-wide formatLocalDate (packages/shared/src/format.ts): "Sept" is ICU's en-ZA abbreviation
const HUMAN_DATE = /^\d{1,2} [A-Z][a-z]{2,3} \d{4}$/;

  return {
    async r() {
      const { p, ctx } = await B.login();
      // ── OBS-3: firm-wide schedule ──
      q(`select id, account_name, account_type, is_primary, opened_date, closed_date from T.trust_accounts`, "r: trust accounts (opened_date after the recorded months)");
      q(`select min(transaction_date) first_posting, max(transaction_date) last_posting, count(*) postings from T.trust_transactions where trust_account_id='${state.primary}' and posted_at is not null`, "r: postings on the primary account");
      const fw = (R.firmWide = await runReport(p, `${TAG}-firmwide`, SCHED, { dateFrom: "2026-08-01", dateTo: "2026-09-30" }));
      const fwRows = asObjects(fw.result);
      R.firmWideRows = fwRows;
      const before = (R.firmWideBeforePostings = await runReport(p, `${TAG}-firmwide-before-postings`, SCHED, { dateFrom: "2026-01-01", dateTo: "2026-02-28" }, { exports: false }));
      R.firmWideBeforePostingsRowsSql = qa(`select count(*) from T.trust_transactions where posted_at is not null and transaction_date <= '2026-02-28'`);
      const acctRun = readJson(`result-s.json`).account;
      const acctRows = asObjects(acctRun.result);
      const same = (a, b) => ["Month", "Fund Ledger b/f", "Gross Interest (Cashbook)", "Charges Recoverable", "Charges Non-recoverable", "Charges Refunded", "Paid to the Fund", "Fund Ledger c/f", "Check"].every((k) => a[k] === b[k]);
      // ── OBS-1: wide PDFs ──
      const wide = [
        { pdf: `c-${SCHED}.pdf`, must: ["Charges Refunded", "Paid to the Fund", "Fund Ledger c/f", "Check", "Balanced", "41,45"] },
        { pdf: `s-${SCHED}.pdf`, must: ["Charges Refunded", "Paid to the Fund", "Fund Ledger c/f", "Check", "Balanced", "41,45"] },
        { pdf: `${TAG}-firmwide-${SCHED}.pdf`, must: ["Charges Refunded", "Paid to the Fund", "Fund Ledger c/f", "Check", "Balanced", "41,45"] },
        { pdf: `c-trust-position.pdf`, must: ["Integrity", "No integrity flags"] },
        { pdf: `f-2026-11-19-trust-position.pdf`, must: ["Integrity", "No integrity flags", "No reconciliation has this period end"] },
      ];
      R.widePdfs = wide.map((w) => {
        const t = txt(`${w.pdf}.txt`) || L.pdfText(path.join(OUT, w.pdf));
        if (!fs.existsSync(path.join(OUT, `${w.pdf}.txt`))) fs.writeFileSync(path.join(OUT, `${w.pdf}.txt`), t);
        return { pdf: w.pdf, size: pageSize(w.pdf), pages: pageCount(w.pdf), missing: w.must.filter((m) => !squash(t).includes(squash(m))), hi: hiRes(w.pdf) };
      });
      // the schedule's "Period" totals row: the PDF text holds two "Balanced" month checks
      const schedTxt = txt(`c-${SCHED}.pdf.txt`);
      R.schedulePdfBalancedCount = (schedTxt.match(/Balanced/g) || []).length;
      // ── OBS-1: the other seven PDFs (portrait), page size + hi-res render ──
      const c = readJson("result-c.json");
      R.otherPdfs = NINE.filter((s) => s !== SCHED && s !== "trust-position").map((s) => ({ slug: s, pdf: `c-${s}.pdf`, size: pageSize(`c-${s}.pdf`), pages: pageCount(`c-${s}.pdf`), missingColumns: c.reports[s].pdf?.missingColumns, hi: hiRes(`c-${s}.pdf`) }));
      for (const f of ["d-asof-2026-11-19-unidentified-deposits-age-analysis.pdf", "e-fye-2026-09-30-section-35-data-pack.pdf"]) R.otherPdfs.push({ pdf: f, size: pageSize(f), pages: pageCount(f), hi: hiRes(f) });
      // ── OBS-2: dashes in the reconciliation and Section 35 PDFs ──
      const dashFiles = ["c-trust-reconciliation.pdf.txt", "c-section-35-data-pack.pdf.txt", "e-fye-2026-09-30-section-35-data-pack.pdf.txt"];
      const LABELS = ["Difference (adjusted bank – cashbook)", "Difference (adjusted bank – trust creditors)", "Difference (cashbook – trust creditors)"];
      R.dashes = dashFiles.map((f) => { const t = flat(txt(f)); return { file: f, labelsPresent: LABELS.filter((l) => t.includes(l)), hashCount: (t.match(/#/g) || []).length, hashDifference: (t.match(/Difference \([^)]*#[^)]*\)/g) || []), minusSign: (t.match(/−/g) || []).length }; });
      // ── OBS-4: dates in words ──
      const e = readJson("result-e.json");
      const cardsOf = (s) => c.reports[s].result.cards;
      R.dateCards = {
        "trust-position As Of Date": card({ cards: cardsOf("trust-position") }, "As Of Date"),
        "schedule Period From": card({ cards: cardsOf(SCHED) }, "Period From"),
        "schedule Period To": card({ cards: cardsOf(SCHED) }, "Period To"),
        "age analysis As Of Date": card({ cards: cardsOf("unidentified-deposits-age-analysis") }, "As Of Date"),
        "reconciliation Period End": card({ cards: cardsOf("trust-reconciliation") }, "Period End"),
        "Section 35 Financial Year Start": card({ cards: cardsOf("section-35-data-pack") }, "Financial Year Start"),
        "Section 35 Financial Year End": card({ cards: cardsOf("section-35-data-pack") }, "Financial Year End"),
        "firm-wide schedule Period From": card(fw.result, "Period From"),
        "firm-wide schedule Period To": card(fw.result, "Period To"),
      };
      R.s35Received = e.allRows.filter((r) => r.section === "Trust creditors (Rule 54.15)" && /received/.test(r.item)).map((r) => r.item);
      R.s35Sweeps = e.allRows.filter((r) => /Fund sweeps/.test(r.section)).map((r) => `${r.item} | ${r.amount}`);
      const s35pdf = flat(txt("e-fye-2026-09-30-section-35-data-pack.pdf.txt"));
      R.s35PdfDates = { received12Aug: s35pdf.includes("received 12 August 2026"), sweep30Sep: s35pdf.includes("30 September 2026 · ") };
      // ── OBS-5 ──
      R.afterLockNet = card({ cards: cardsOf("client-trust-balances") }, "After Lock Net");
      await settle(p, 300);
      await ctx.close();
      Object.assign(R.checks, {
        "r OBS-3: firm-wide schedule Aug–Sep 2026 runs, Account Count ≥ 1": !fw.result.error && Number(card(fw.result, "Account Count")) >= 1,
        "r OBS-3: firm-wide rows include the primary account's August and September rows (Balanced)": fwRows.some((r) => r["Trust Account"] === "Mathebula & Partners Trust Account" && /August 2026/.test(r.Month) && r.Check === "Balanced") && fwRows.some((r) => r["Trust Account"] === "Mathebula & Partners Trust Account" && /September 2026/.test(r.Month) && r.Check === "Balanced"),
        "r OBS-3: firm-wide month figures = the account-given run's figures": acctRows.length === 2 && acctRows.every((a) => fwRows.some((f) => same(a, f))),
        "r OBS-3: firm-wide Identity Holds 'Yes' with blocks present": card(fw.result, "Identity Holds") === "Yes",
        "r OBS-3: firm-wide range before any posting (Jan–Feb 2026): no rows, Account Count 0, Identity Holds '—' (not 'Yes')": !before.result.error && before.result.rows.length === 0 && card(before.result, "Account Count") === "0" && card(before.result, "Identity Holds") === "—" && R.firmWideBeforePostingsRowsSql === "0",
        "r OBS-1: schedule + trust-position PDFs are landscape A4": R.widePdfs.every((w) => w.size.landscape && w.size.a4),
        "r OBS-1: every required column / value is in the wide PDFs' text (Refunded, Paid to the Fund, c/f, Check, Balanced; Integrity, flags note, bank note)": R.widePdfs.every((w) => w.missing.length === 0),
        "r OBS-1: account schedule PDF: both month Check cells 'Balanced' in the text": R.schedulePdfBalancedCount >= 2,
        "r OBS-1: the other seven report PDFs are A4 (portrait) and rendered": R.otherPdfs.every((o) => o.size.a4 && !!o.hi),
        "r OBS-2: reconciliation + Section 35 PDFs print the three comparison labels with '–'": R.dashes.every((d) => d.labelsPresent.length === 3),
        "r OBS-2: no '#' inside any 'Difference (…)' label; no '#' anywhere in those PDFs": R.dashes.every((d) => d.hashDifference.length === 0 && d.hashCount === 0),
        "r OBS-4: summary-card dates are words (dd Mon[t] yyyy, en-ZA), not ISO": Object.values(R.dateCards).every((v) => HUMAN_DATE.test(v || "")),
        "r OBS-4: Section 35 part (2) 'received …' labels dated in words (12 August 2026, 25 / 26 September 2026)": R.s35Received.length === 3 && R.s35Received.every((s) => / · received \d{1,2} [A-Z][a-z]+ \d{4}$/.test(s)) && R.s35Received.some((s) => s.endsWith("received 12 August 2026")),
        "r OBS-4: Section 35 part (6) sweep label starts '30 September 2026 · <file> · line 10'": R.s35Sweeps.some((s) => new RegExp(`^30 September 2026 · ${state.statementFile.replace(/\./g, "\\.")} · line 10 · `).test(s)),
        "r OBS-4: Section 35 PDF carries the same worded dates": R.s35PdfDates.received12Aug && R.s35PdfDates.sweep30Sep,
        "r OBS-5: Rule 54.15 list 'After Lock Net' = 'R 0,00'": R.afterLockNet === "R 0,00",
      });
    },
  };
};
