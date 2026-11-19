// 650C OBS — phases s (schedule identity), c (each of the nine reports), d (age analysis),
// e (Section 35), f (trust position vs GET /api/trust-integrity), g (Carol, member).
const L = require("./obs-lib.cjs");
const { fs, path, OUT, BASE, qa, eq, flat, rand, parseRand, api, settle, snap, scanAll } = L;

module.exports = (X) => {
  const { state, R, B, q, TAG, NINE, SCOPE_NOTE, ACCT, ACME, fundSql, fundBal, runReport } = X;
  /** Table rows as objects keyed by header label. */
  const asObjects = (res) => res.rows.map((cells) => Object.fromEntries(res.headers.map((h, i) => [h, cells[i]])));
  const card = (res, label) => res.cards.find((c) => c.label === label)?.value ?? null;
  const preview = async (ctx, slug, params, save) => {
    const qs = new URLSearchParams(params).toString();
    const r = await api(ctx, `/api/report-definitions/${slug}/preview?${qs}`, null, true);
    fs.writeFileSync(path.join(OUT, `${save}.html`), r.text || "");
    const text = flat((r.text || "").replace(/<style[\s\S]*?<\/style>/g, " ").replace(/<[^>]+>/g, " ").replace(/&nbsp;|&#160;/g, " ").replace(/&amp;/g, "&").replace(/&#39;|&apos;/g, "'").replace(/&quot;/g, '"').replace(/&mdash;/g, "—").replace(/&ndash;/g, "–"));
    fs.writeFileSync(path.join(OUT, `${save}.txt`), text);
    return { status: r.status, chars: text.length, scopeNote: text.includes(SCOPE_NOTE), scan: scanAll(text), text };
  };
  const SCHED = "trust-interest-charges-schedule";
  const num = (s) => (s == null || s === "—" ? 0 : parseRand(s));
  /** b/f + interest − charges + refunds − sweeps = c/f, with the figures as shown */
  const identity = (r) => {
    const bf = num(r["Fund Ledger b/f"]), gi = num(r["Gross Interest (Cashbook)"]), cr = num(r["Charges Recoverable"]), cn = num(r["Charges Non-recoverable"]), rf = num(r["Charges Refunded"]), sw = num(r["Paid to the Fund"]), cf = num(r["Fund Ledger c/f"]);
    const lhs = +(bf + gi - (cr + cn) + rf - sw).toFixed(2);
    return { bf, gi, charges: +(cr + cn).toFixed(2), rf, sw, cf, lhs, holds: eq(lhs, cf), text: `${bf.toFixed(2)} + ${gi.toFixed(2)} − ${(cr + cn).toFixed(2)} + ${rf.toFixed(2)} − ${sw.toFixed(2)} = ${lhs.toFixed(2)} vs c/f ${cf.toFixed(2)}` };
  };

  return {
    // s: the Rule 54.28 schedule on the recorded months; identity by hand; SQL/API cross-check; refusals
    async s() {
      const { p, ctx } = await B.login();
      const run = (R.account = await runReport(p, TAG, SCHED, { dateFrom: "2026-08-01", dateTo: "2026-09-30", trust_account_id: ACCT }));
      const rows = asObjects(run.result);
      R.rowsShown = rows;
      const aug = rows.find((r) => /August 2026/.test(r.Month)), sep = rows.find((r) => /September 2026/.test(r.Month));
      R.identity = { aug: aug && identity(aug), sep: sep && identity(sep) };
      q(fundSql("2026-08-01", "2026-08-31"), "s: Fund-ledger rows August 2026 by type");
      q(fundSql("2026-09-01", "2026-09-30"), "s: Fund-ledger rows September 2026 by type");
      q(`select '2026-07-31' as_of, ${fundBal("2026-07-31")} fund union all select '2026-08-31', ${fundBal("2026-08-31")} union all select '2026-09-30', ${fundBal("2026-09-30")}`, "s: Fund ledger at month ends");
      q(`select period_month, gross_interest, gross_charges, charges_vat from T.trust_interest_memos`, "s: memos");
      const sepSql = Object.fromEntries(qa(fundSql("2026-09-01", "2026-09-30")).split("\n").map((l) => l.split("|")).map(([t, n, le, amt, rec, nonrec, vat]) => [t, { n: +n, le: +le, amt: +amt, rec: +rec || 0, nonrec: +nonrec || 0, vat: +vat || 0 }]));
      const fundAcct = qa(`select id from T.trust_creditor_accounts where trust_account_id='${state.primary}' and account_type='FUND_INTEREST_AND_CHARGES'`);
      const credAccts = await api(ctx, `/api/trust-accounts/${state.primary}/creditor-accounts`, "s-creditor-accounts");
      const totSep = await api(ctx, `/api/trust-accounts/${state.primary}/total-balance?asAt=2026-09-30`, "s-total-balance-2026-09-30");
      const totAug = await api(ctx, `/api/trust-accounts/${state.primary}/total-balance?asAt=2026-08-31`, "s-total-balance-2026-08-31");
      R.fundAcct = fundAcct;
      R.apiFund = { sep: totSep.body?.creditorBreakdown, aug: totAug.body?.creditorBreakdown };
      const fundOf = (b) => { const cb = b?.creditorBreakdown; if (!cb) return null; return cb.fundLedger ?? cb.fundInterestAndCharges ?? cb.fund ?? null; };
      R.apiFundSep = fundOf(totSep.body); R.apiFundAug = fundOf(totAug.body);
      R.preview = await preview(ctx, SCHED, { dateFrom: "2026-08-01", dateTo: "2026-09-30", trust_account_id: state.primary }, "s-preview-schedule");
      delete R.preview.text;
      // firm-wide (no account): the account was opened 19 Nov 2026 on this stack (D-34)
      R.firmWide = await runReport(p, `${TAG}-firmwide`, SCHED, { dateFrom: "2026-08-01", dateTo: "2026-09-30" }, { exports: false });
      R.firmWideToNov = await runReport(p, `${TAG}-firmwide-to-nov`, SCHED, { dateFrom: "2026-08-01", dateTo: "2026-11-30" }, { exports: false });
      // refusals: inverted range across months, and inside one month (D-33)
      R.inverted = await runReport(p, `${TAG}-inverted`, SCHED, { dateFrom: "2026-09-30", dateTo: "2026-08-01", trust_account_id: ACCT }, { exports: false });
      R.invertedInMonth = await runReport(p, `${TAG}-inverted-in-month`, SCHED, { dateFrom: "2026-09-20", dateTo: "2026-09-05", trust_account_id: ACCT }, { exports: false });
      const memoShownSep = sep && num(sep["Gross Interest (At Source, Memo)"]);
      Object.assign(R.checks, {
        "s: schedule runs (account given): no error, a row per month (Aug, Sep 2026)": !run.result.error && !!aug && !!sep,
        "s: every row headed by the account and its section words": rows.every((r) => r["Trust Account"] === "Mathebula & Partners Trust Account" && /86\(2\)/.test(r.Section || "")),
        "s: August row: b/f R 0,00, interest R 30,00, c/f R 30,00, Check 'Balanced'": !!aug && R.identity.aug.bf === 0 && R.identity.aug.gi === 30 && R.identity.aug.cf === 30 && aug.Check === "Balanced",
        "s: September row b/f = August c/f": !!aug && !!sep && R.identity.sep.bf === R.identity.aug.cf,
        "s: September row shown: b/f 30,00 · interest 123,45 · charges 57,50 rec + 12,00 non-rec (VAT 9,07) · refunds 57,50 · paid to Fund 100,00 · c/f 41,45 · Check 'Balanced'": !!sep && sep["Fund Ledger b/f"] === "R 30,00" && sep["Gross Interest (Cashbook)"] === "R 123,45" && sep["Charges Recoverable"] === "R 57,50" && sep["Charges Non-recoverable"] === "R 12,00" && sep["VAT in Charges"] === "R 9,07" && sep["Charges Refunded"] === "R 57,50" && sep["Paid to the Fund"] === "R 100,00" && sep["Fund Ledger c/f"] === "R 41,45" && sep.Check === "Balanced",
        "s: identity by hand on screen figures holds for both rows (b/f + interest − charges + refunds − sweeps = c/f)": !!R.identity.aug?.holds && !!R.identity.sep?.holds,
        "s: September screen figures = SQL sums of the posted rows by type": !!sep && eq(R.identity.sep.gi, sepSql.FUND_INTEREST?.amt) && eq(num(sep["Charges Recoverable"]), sepSql.BANK_CHARGE?.rec) && eq(num(sep["Charges Non-recoverable"]), sepSql.BANK_CHARGE?.nonrec) && eq(num(sep["VAT in Charges"]), sepSql.BANK_CHARGE?.vat) && eq(R.identity.sep.rf, sepSql.CHARGE_REFUND?.amt) && eq(R.identity.sep.sw, sepSql.FUND_SWEEP?.amt),
        "s: c/f = SQL Fund-ledger balance at each month end": !!sep && eq(R.identity.aug.cf, fundBal("2026-08-31")) && eq(R.identity.sep.cf, fundBal("2026-09-30")),
        "s: c/f = API total-balance creditorBreakdown Fund line at each month end": R.apiFundSep != null && eq(R.identity.sep.cf, R.apiFundSep) && eq(R.identity.aug.cf, R.apiFundAug),
        "s: memo in its own columns (Sep: interest R 200,00, charges R 15,00, VAT R 1,96; Aug: R 0,00) and outside the identity": !!sep && memoShownSep === 200 && sep["Charges (At Source, Memo)"] === "R 15,00" && sep["VAT (Memo)"] === "R 1,96" && aug["Gross Interest (At Source, Memo)"] === "R 0,00" && R.identity.sep.holds,
        "s: s86(4)-only columns blank on a section 86(2) block": !!sep && [sep["Client's Share (s86(4))"], sep["Fund 5% Credited"], sep["Fund 5% Paid at Source"]].every((v) => v === "—"),
        "s: no raw code / raw decimal in the summary cards": run.cardScan.codes.length === 0 && run.cardScan.rawDecimals.length === 0,
        "s: no raw ISO date in the summary cards (Period From / Period To)": run.cardScan.iso.length === 0,
        "s: no raw code in the table": run.tableCodeHits.length === 0,
        "s: scope sentence on screen (summary card), in the PDF and in the preview": run.result.cards.some((c) => c.value === SCOPE_NOTE) && !!run.pdf?.scopeNote && R.preview.scopeNote,
        "s: CSV and PDF exported through the buttons": !!run.csv?.ok && !!run.pdf?.ok && run.pdf.magic === "%PDF-",
        "s: CSV carries every column incl. Charges Refunded / Paid to the Fund / c/f / Check, both rows 'Balanced'": !!run.csv?.ok && /Charges Refunded,Paid to the Fund,Fund Ledger c\/f,Check/.test(fs.readFileSync(path.join(OUT, run.csv.file), "utf8")) && (fs.readFileSync(path.join(OUT, run.csv.file), "utf8").match(/,Balanced$/gm) || []).length === 2,
        "s: PDF shows every schedule column (no column cut at the page edge)": !!run.pdf?.ok && run.pdf.missingColumns.length === 0,
        "s: inverted range (30 Sep → 1 Aug) refused with a readable message, no table": !!R.inverted.result.error && !/Exception|stack|500|\bnull\b/i.test(R.inverted.result.error) && R.inverted.result.rows.length === 0,
        "s: inverted range inside one month (20 Sep → 5 Sep) refused (D-33)": !!R.invertedInMonth.result.error && R.invertedInMonth.result.rows.length === 0,
      });
    },

    // c: each of the nine reports runs through the UI: table, CSV and PDF; no codes; the scope sentence
    async c() {
      const { p, ctx } = await B.login();
      const P = {
        "trust-receipts-payments": { trust_account_id: ACCT, dateFrom: "2026-08-01", dateTo: "2026-11-19" },
        "client-trust-balances": { asOfDate: "2026-09-30" },
        "trust-position": { asOfDate: "2026-09-30" },
        "client-ledger-statement": { trust_account_id: ACCT, customer_id: ACME, dateFrom: "2026-08-01", dateTo: "2026-11-19" },
        "trust-reconciliation": { trust_account_id: ACCT },
        "investment-register": { trust_account_id: ACCT },
        "trust-interest-charges-schedule": { dateFrom: "2026-08-01", dateTo: "2026-09-30", trust_account_id: ACCT },
        "unidentified-deposits-age-analysis": { asOfDate: "2026-11-19" },
        "section-35-data-pack": { trust_account_id: ACCT, financial_year_end: "2026-09-30" },
      };
      const PP = { // the same parameters as GET query strings for the HTML preview cross-check
        "trust-receipts-payments": { trust_account_id: state.primary, dateFrom: "2026-08-01", dateTo: "2026-11-19" },
        "client-trust-balances": { asOfDate: "2026-09-30" },
        "trust-position": { asOfDate: "2026-09-30" },
        "client-ledger-statement": { trust_account_id: state.primary, customer_id: qa("select id from T.customers where name='Acme Corp'"), dateFrom: "2026-08-01", dateTo: "2026-11-19" },
        "trust-reconciliation": { trust_account_id: state.primary },
        "investment-register": { trust_account_id: state.primary },
        "trust-interest-charges-schedule": { dateFrom: "2026-08-01", dateTo: "2026-09-30", trust_account_id: state.primary },
        "unidentified-deposits-age-analysis": { asOfDate: "2026-11-19" },
        "section-35-data-pack": { trust_account_id: state.primary, financial_year_end: "2026-09-30" },
      };
      R.reports = {};
      for (const slug of NINE) {
        const r = await runReport(p, TAG, slug, P[slug]);
        const pv = await preview(ctx, slug, PP[slug], `${TAG}-preview-${slug}`);
        delete pv.text;
        r.preview = pv;
        R.reports[slug] = r;
        const ok = !r.result.error && r.result.toasts.length === 0 && r.result.formErrors.length === 0;
        const content = r.result.cards.length > 0 && (r.result.rows.length > 0 || r.result.noData);
        Object.assign(R.checks, {
          [`c: ${slug}: runs (no error box / toast), shows summary cards + table`]: ok && content,
          [`c: ${slug}: title is words`]: !!r.title && !/[a-z]-[a-z]/.test(r.title),
          [`c: ${slug}: no raw code in summary cards or table`]: r.cardScan.codes.length === 0 && r.tableCodeHits.length === 0,
          [`c: ${slug}: summary cards: no unformatted decimal`]: r.cardScan.rawDecimals.length === 0,
          [`c: ${slug}: summary cards: no raw ISO date`]: r.cardScan.iso.length === 0,
          [`c: ${slug}: scope sentence on screen (card)`]: r.result.cards.some((c) => c.value === SCOPE_NOTE),
          [`c: ${slug}: CSV exported (button) with a header row`]: !!r.csv?.ok && r.csv.lines >= 1,
          [`c: ${slug}: PDF exported (button), %PDF-, text extracted`]: !!r.pdf?.ok && r.pdf.magic === "%PDF-" && r.pdf.extractOk,
          [`c: ${slug}: scope sentence in the PDF and the preview`]: !!r.pdf?.scopeNote && pv.scopeNote,
          [`c: ${slug}: no raw code in the PDF text or the preview`]: (r.pdf?.codes ?? ["?"]).length === 0 && pv.scan.codes.length === 0,
          [`c: ${slug}: PDF shows every on-screen column (none cut at the page edge)`]: !!r.pdf?.ok && r.pdf.missingColumns.length === 0,
        });
      }
      R.cardIsoHits = Object.fromEntries(NINE.map((s) => [s, R.reports[s].cardScan.iso]));
      R.tableIsoHits = Object.fromEntries(NINE.map((s) => [s, R.reports[s].tableIsoHits]));
      R.pdfIsoHits = Object.fromEntries(NINE.map((s) => [s, R.reports[s].pdf?.scan?.iso]));
      R.pdfRawDecimalHits = Object.fromEntries(NINE.map((s) => [s, R.reports[s].pdf?.scan?.rawDecimals]));
    },

    // d: unidentified deposits age analysis — bands, as-at behaviour (EQ-4), resolved list; Σ open = API/SQL
    async d() {
      const { p, ctx } = await B.login();
      const A = "unidentified-deposits-age-analysis";
      const now = (R.now = await runReport(p, `${TAG}-asof-2026-11-19`, A, { asOfDate: "2026-11-19", trust_account_id: ACCT }));
      const oct = (R.oct1 = await runReport(p, `${TAG}-asof-2026-10-01`, A, { asOfDate: "2026-10-01", trust_account_id: ACCT }));
      const rowsNow = asObjects(now.result), rowsOct = asObjects(oct.result);
      R.rowsNow = rowsNow; R.rowsOct = rowsOct;
      const by = (rows, desc) => rows.find((r) => r["Bank Description"] === desc);
      const totNow = await api(ctx, `/api/trust-accounts/${state.primary}/total-balance`, "d-total-balance-today");
      const totOct = await api(ctx, `/api/trust-accounts/${state.primary}/total-balance?asAt=2026-10-01`, "d-total-balance-2026-10-01");
      const items = await api(ctx, `/api/trust-accounts/${state.primary}/unidentified-deposits`, "d-unidentified-deposits");
      q(`select received_date, bank_description, amount, status, resolved_date, ('2026-11-19'::date - received_date) age_today, ('2026-10-01'::date - received_date) age_oct1 from T.unidentified_deposits order by received_date`, "d: items and ages");
      q(`select t.transaction_date, t.transaction_type, t.status, t.ledger_effect, u.bank_description from T.trust_transactions t join T.unidentified_deposits u on u.id=t.unidentified_deposit_id order by t.transaction_date`, "d: rows of each item");
      const u1 = by(rowsNow, "Eft credit unknown remitter"), u2 = by(rowsNow, "Eft credit no reference"), u3 = by(rowsNow, "Eft credit estate payer");
      const o1 = by(rowsOct, "Eft credit unknown remitter"), o2 = by(rowsOct, "Eft credit no reference"), o3 = by(rowsOct, "Eft credit estate payer");
      Object.assign(R.checks, {
        "d: as at 19 Nov 2026: R 750,00 received 25 Sep (55 days) in '31–90 days', Open": !!u2 && u2["Age (days)"] === "55" && u2["Age Band"] === "31–90 days" && u2.Position === "Open" && u2.Amount === "R 750,00",
        "d: as at 19 Nov 2026: R 600,00 received 12 Aug (99 days) in '91–180 days', Open": !!u1 && u1["Age (days)"] === "99" && u1["Age Band"] === "91–180 days" && u1.Position === "Open",
        "d: as at 19 Nov 2026: R 200,00 allocated 15 Oct listed as resolved 'Allocated to Acme Corp on 15 October 2026'": !!u3 && u3["Age Band"] === "Resolved" && u3.Position === "Allocated to Acme Corp on 15 October 2026",
        "d: as at 19 Nov 2026: band cards 31–90 R 750,00 · 91–180 R 600,00 · others R 0,00; Open Total R 1 350,00": card(now.result, "One To Three Months Total") === "R 750,00" && card(now.result, "Three To Six Months Total") === "R 600,00" && card(now.result, "Up To One Month Total") === "R 0,00" && card(now.result, "Six To Twelve Months Total") === "R 0,00" && card(now.result, "Over One Year Total") === "R 0,00" && card(now.result, "Open Total") === "R 1 350,00",
        "d: Σ open (19 Nov) = API unidentified creditor today = SQL": eq(totNow.body?.creditorBreakdown?.unidentifiedDeposits, 1350) && eq(qa(`select sum(ledger_effect) from T.trust_transactions t join T.trust_creditor_accounts ca on ca.id=t.creditor_account_id where ca.account_type='UNIDENTIFIED_DEPOSITS' and t.posted_at is not null`), 1350),
        "d: as at 1 Oct 2026 (before the allocation): all three open by their own rows (EQ-4) — 50 d '31–90', 6 d and 5 d '0–30'; no resolved row": !!o1 && !!o2 && !!o3 && o1["Age (days)"] === "50" && o1["Age Band"] === "31–90 days" && o2["Age (days)"] === "6" && o2["Age Band"] === "0–30 days" && o3["Age (days)"] === "5" && o3["Age Band"] === "0–30 days" && rowsOct.every((r) => r.Position === "Open"),
        "d: as at 1 Oct: Open Total R 1 550,00 = API total-balance?asAt=2026-10-01 unidentified": card(oct.result, "Open Total") === "R 1 550,00" && eq(totOct.body?.creditorBreakdown?.unidentifiedDeposits, 1550),
        "d: no raw code in either run (cards + table)": [now, oct].every((r) => r.cardScan.codes.length === 0 && r.tableCodeHits.length === 0),
      });
      R.apiItems = (items.body || []).map?.((i) => ({ status: i.status, amount: i.amount, receivedDate: i.receivedDate }));
    },

    // e: Section 35 data pack for the financial year containing the recorded months
    async e() {
      const { p, ctx } = await B.login();
      const S = "section-35-data-pack";
      const run = (R.fy = await runReport(p, `${TAG}-fye-2026-09-30`, S, { trust_account_id: ACCT, financial_year_end: "2026-09-30" }));
      // every page of the table
      const all = [...run.result.rows];
      for (let i = 0; i < 10 && !(await p.getByRole("button", { name: "Next" }).isDisabled().catch(() => true)); i++) {
        await p.getByRole("button", { name: "Next" }).click();
        await settle(p, 800);
        all.push(...(await readResults(p)).rows);
      }
      R.allRows = all.map((c) => ({ section: c[0], item: c[1], amount: c[2] }));
      const pv = (R.preview = await preview(ctx, S, { trust_account_id: state.primary, financial_year_end: "2026-09-30" }, `${TAG}-preview-fye-2026-09-30`));
      const text = pv.text; delete R.preview.text;
      const sec = (name) => R.allRows.filter((r) => r.section === name);
      const item = (name, re) => sec(name).find((r) => re.test(r.item));
      const SCH = "Interest and charges schedule (Rule 54.28)";
      const sched = (re) => item(SCH, re)?.amount;
      R.schedule = sec(SCH);
      const yb = { bf: num(sched(/brought forward/)), gi: num(sched(/gross interest earned \(in the cashbook\)/)), ch: num(sched(/gross charges levied \(in the cashbook\)/)), rf: num(sched(/charges refunded/)), sw: num(sched(/paid to the Fund/)), cf: num(sched(/carried forward/)) };
      R.yearIdentity = { ...yb, lhs: +(yb.bf + yb.gi - yb.ch + yb.rf - yb.sw).toFixed(2) };
      q(fundSql("2025-10-01", "2026-09-30"), "e: Fund-ledger rows in the financial year by type");
      q(`select t.transaction_date, t.reference, t.amount, s.file_name, l.line_number from T.trust_transactions t join T.bank_statement_lines l on l.id=t.bank_statement_line_id join T.bank_statements s on s.id=l.bank_statement_id where t.transaction_type='FUND_SWEEP'`, "e: Fund sweeps with their statement line");
      const sweepLine = qa(`select s.file_name || '|' || l.line_number from T.trust_transactions t join T.bank_statement_lines l on l.id=t.bank_statement_line_id join T.bank_statements s on s.id=l.bank_statement_id where t.transaction_type='FUND_SWEEP'`).split("|");
      const sweepRow = item("Fund sweeps and their statement lines (Rule 54.14.16)", /line/);
      R.sweepRow = sweepRow;
      // a second, live year end (the list for Feb 2027 is not locked; the allocation is in it)
      R.fyLive = await runReport(p, `${TAG}-fye-2027-02-28`, S, { trust_account_id: ACCT, financial_year_end: "2027-02-28" }, { exports: false });
      const pvLive = (R.previewLive = await preview(ctx, S, { trust_account_id: state.primary, financial_year_end: "2027-02-28" }, `${TAG}-preview-fye-2027-02-28`));
      const liveText = pvLive.text; delete R.previewLive.text;
      Object.assign(R.checks, {
        "e: pack runs for FYE 30 Sep 2026: 7 sections, FY 1 Oct 2025 – 30 Sep 2026": !run.result.error && card(run.result, "Section Count") === "7",
        "e: (1) receipts & payments: 10 rows of the year + Total R 11 591,45": sec("Trust Receipts & Payments").length === 11 && item("Trust Receipts & Payments", /^Total$/)?.amount === "R 11 591,45",
        "e: (2) 'Trust creditors (Rule 54.15)': Acme R 10 000,00, the Fund R 41,45, three unidentified lines, Total R 11 591,45; locked note": item("Trust creditors (Rule 54.15)", /^Acme Corp$/)?.amount === "R 10 000,00" && item("Trust creditors (Rule 54.15)", /Fidelity Fund/)?.amount === "R 41,45" && sec("Trust creditors (Rule 54.15)").filter((r) => /received/.test(r.item)).length === 3 && item("Trust creditors (Rule 54.15)", /^Total$/)?.amount === "R 11 591,45" && /Trust creditors \(Rule 54\.15\) Locked 19 November 2026 by Alice Owner\./.test(text),
        "e: (3) the 30 Sep 2026 reconciliation with its stored breakdown (both comparisons)": sec("Trust Reconciliation").length === 14 && !!item("Trust Reconciliation", /Difference \(adjusted bank [−–] cashbook\)/) && !!item("Trust Reconciliation", /Difference \(cashbook [−–] trust creditors\)/),
        "e: (5) Rule 54.28 year totals: b/f 0,00 · interest 153,45 · memo 200,00 · charges 69,50 (rec 57,50, VAT 9,07) · memo charges 15,00 (VAT 1,96) · refunded 57,50 · paid 100,00 · c/f 41,45": yb.bf === 0 && yb.gi === 153.45 && sched(/at source, memo\)$/) === "R 200,00" && yb.ch === 69.5 && sched(/of which recoverable/) === "R 57,50" && sched(/of which VAT/) === "R 9,07" && sched(/gross charges levied \(at source, memo\)/) === "R 15,00" && sched(/VAT in charges at source/) === "R 1,96" && yb.rf === 57.5 && yb.sw === 100 && yb.cf === 41.45,
        "e: (5) year identity on the shown totals: b/f + interest − charges + refunds − paid = c/f; c/f = SQL Fund ledger at 30 Sep 2026": eq(R.yearIdentity.lhs, yb.cf) && eq(yb.cf, fundBal("2026-09-30")),
        "e: (6) the Fund sweep with its statement file name and line number (b-statement-….csv · line 10), -R 100,00": !!sweepRow && sweepRow.item.includes(`${sweepLine[0]} · line ${sweepLine[1]}`) && sweepRow.amount === "-R 100,00" && sweepLine[1] === "10",
        "e: (6) 'Fund sweeps with no statement line' present: 'None in the financial year'": /Fund sweeps with no statement line None in the financial year/.test(text),
        "e: (7) ledger statements part present": /Client Ledger Statements/.test(text),
        "e: no raw code on screen, in the PDF or the preview": run.cardScan.codes.length === 0 && run.tableCodeHits.length === 0 && (run.pdf?.codes ?? ["?"]).length === 0 && pv.scan.codes.length === 0,
        "e: scope sentence on screen, in the PDF and the preview": run.result.cards.some((c) => c.value === SCOPE_NOTE) && !!run.pdf?.scopeNote && pv.scopeNote,
        "e: FYE 28 Feb 2027 (live list): runs; creditors part 'Not locked' note; Acme R 10 200,00 and two unidentified lines after the 15 Oct allocation": !R.fyLive.result.error && /Not locked/.test(liveText) && /Acme Corp R 10 200,00/.test(liveText) && (liveText.match(/· received/g) || []).length === 2,
      });
      R.investmentsPartShown = /Section 86\([34]\) Investments/.test(text);
    },

    // f: trust position = GET /api/trust-integrity (firm) as at the same date
    async f() {
      const { p, ctx } = await B.login();
      const T = "trust-position";
      R.runs = {};
      for (const d of ["2026-09-30", "2026-11-19"]) {
        const run = await runReport(p, `${TAG}-${d}`, T, { asOfDate: d }, { exports: d === "2026-11-19" });
        const firm = await api(ctx, `/api/trust-integrity?asAt=${d}`, `f-trust-integrity-${d}`);
        const acct = await api(ctx, `/api/trust-accounts/${state.primary}/integrity?asAt=${d}`, `f-account-integrity-${d}`);
        const label = (k) => k.replace(/([a-z])([A-Z])/g, "$1 $2").replace(/^./, (c) => c.toUpperCase());
        const body = firm.body || {};
        const src = body.position || body.totals || body;
        const cmp = {};
        for (const k of ["cashbook", "clientLedgers", "investmentCreditors", "fundLedger", "unidentifiedDeposits", "otherNonClient", "interAccount", "creditorTotal", "difference"]) {
          const apiV = src[k] ?? body[k];
          cmp[k] = { card: card(run.result, label(k)), api: apiV, equal: apiV != null && card(run.result, label(k)) === rand(apiV) };
        }
        const row = asObjects(run.result)[0] || {};
        R.runs[d] = { cmp, row, apiKeys: Object.keys(body), cards: run.result.cards, balancedCard: card(run.result, "Balanced"), apiBalanced: body.balanced ?? src.balanced, acctBank: acct.body?.bank ?? null };
        Object.assign(R.checks, {
          [`f: ${d}: report runs, one account row`]: !run.result.error && run.result.rows.length === 1,
          [`f: ${d}: every firm-wide card = GET /api/trust-integrity?asAt (formatted as currency)`]: Object.values(cmp).every((c) => c.equal),
          [`f: ${d}: Balanced card 'Yes' = API balanced`]: R.runs[d].balancedCard === ((R.runs[d].apiBalanced) ? "Yes" : "No"),
          [`f: ${d}: summary money values as currency (no raw decimal / code)`]: run.cardScan.rawDecimals.length === 0 && run.cardScan.codes.length === 0,
          [`f: ${d}: row Section in words, Integrity in words`]: /^Section 86\(2\)/.test(row.Section || "") && !!row.Integrity && !/[A-Z]{3,}_/.test(row.Integrity),
        });
      }
      const sep = R.runs["2026-09-30"].row, nov = R.runs["2026-11-19"].row;
      Object.assign(R.checks, {
        "f: 30 Sep 2026 (a reconciliation has this period end): Adjusted Bank Balance R 11 591,45 shown, no bank note": sep["Adjusted Bank Balance"] === "R 11 591,45" && sep["Bank Comparison"] === "—",
        "f: 19 Nov 2026 (no reconciliation ends then): bank note 'No reconciliation has this period end…'": /^No reconciliation has this period end/.test(nov["Bank Comparison"] || ""),
        "f: 19 Nov 2026: Fund R 41,45, unidentified R 1 350,00, client ledgers R 10 200,00": nov["Fidelity Fund Interest & Charges"] === "R 41,45" && nov["Unidentified Deposits"] === "R 1 350,00" && nov["Client Ledgers"] === "R 10 200,00",
      });
    },

    // g: Carol (member) — what the reports page and a report URL show (capability rules unchanged by the slice)
    async g() {
      const { p, ctx } = await B.login("user_e2e_carol");
      const caps = await api(ctx, "/api/me/capabilities", "g-carol-capabilities");
      R.caps = caps.body;
      await p.goto(`${BASE}/trust-accounting/reports`, { waitUntil: "load" });
      await settle(p, 1500);
      R.page = { url: p.url(), h1: flat(await p.locator("h1").first().textContent().catch(() => "")), cards: await p.locator('[data-testid^="report-card-"]').count(), fvRequired: await p.getByTestId("trust-reports-financial-visibility-required").count(), notFound: /not found|404/i.test(await p.locator("body").textContent()) };
      R.pageShot = await snap(p, TAG, "01-carol-trust-reports");
      await p.goto(`${BASE}/reports/trust-interest-charges-schedule`, { waitUntil: "load" });
      await settle(p, 1500);
      R.direct = { url: p.url(), h1: flat(await p.locator("h1").first().textContent().catch(() => "")), body: flat(await p.locator("main").first().textContent().catch(() => "")).slice(0, 400) };
      R.directShot = await snap(p, TAG, "02-carol-schedule-url");
      const defs = await api(ctx, "/api/report-definitions", "g-carol-report-definitions");
      R.defsStatus = defs.status;
      const hasCap = (c) => JSON.stringify(R.caps || {}).includes(`"${c}"`);
      R.hasViewTrust = hasCap("VIEW_TRUST"); R.hasFinancialVisibility = hasCap("FINANCIAL_VISIBILITY");
      Object.assign(R.checks, {
        "g: Carol's capabilities read (GET /api/me/capabilities 200)": caps.status === 200,
        "g: page behaviour matches the capability rules (VIEW_TRUST gates the page; FINANCIAL_VISIBILITY gates the cards)": R.hasViewTrust ? (R.hasFinancialVisibility ? R.page.cards === 9 : R.page.fvRequired === 1 && R.page.cards === 0) : R.page.notFound,
        "g: GET /api/report-definitions status agrees with FINANCIAL_VISIBILITY": R.hasFinancialVisibility ? defs.status === 200 : defs.status === 403,
      });
    },

    // s2: on-screen reachability of the right-hand schedule columns (horizontal scroll) at 1440 and 1280
    async s2() {
      R.widths = {};
      for (const w of [1440, 1280]) {
        const { p, ctx } = await B.login("user_e2e_alice", w);
        await runReport(p, `${TAG}-${w}`, SCHED, { dateFrom: "2026-08-01", dateTo: "2026-09-30", trust_account_id: ACCT }, { exports: false, shot: false });
        const m = await p.evaluate(() => {
          const t = document.querySelector("main table") || document.querySelector("table");
          let sc = t.parentElement; while (sc && getComputedStyle(sc).overflowX === "visible") sc = sc.parentElement;
          const before = { scrollWidth: sc.scrollWidth, clientWidth: sc.clientWidth, overflowX: getComputedStyle(sc).overflowX };
          sc.scrollLeft = sc.scrollWidth;
          const vw = document.documentElement.clientWidth;
          const th = [...t.querySelectorAll("thead th")];
          const last = th[th.length - 1].getBoundingClientRect();
          const monthCells = [...t.querySelectorAll("tbody tr")].map((tr) => { const td = tr.querySelectorAll("td")[2]; const r = document.createRange(); r.selectNodeContents(td); return { text: td.textContent, lines: new Set([...r.getClientRects()].map((x) => Math.round(x.top))).size }; });
          return { ...before, lastHeader: th[th.length - 1].textContent, lastHeaderLeft: last.left, lastHeaderRight: last.right, vw, monthCells };
        });
        R.widths[w] = m;
        await p.locator("main table").first().scrollIntoViewIfNeeded();
        R.widths[w].shot = await snap(p, TAG, `${w}-table-scrolled-right`, p.locator("main table").first().locator("xpath=ancestor::div[contains(@class,'rounded')][1]"));
        await ctx.close();
        Object.assign(R.checks, {
          [`s2: ${w}: table scrolls horizontally; after scrolling, the last column 'Check' is inside the viewport`]: m.overflowX !== "visible" && m.lastHeader === "Check" && m.lastHeaderRight <= m.vw + 0.5,
          [`s2: ${w}: month labels do not break mid-word`]: m.monthCells.every((c) => c.lines <= 2 && !/^\S+$/.test(c.text) ? true : c.lines === 1),
        });
      }
    },
  };
};
