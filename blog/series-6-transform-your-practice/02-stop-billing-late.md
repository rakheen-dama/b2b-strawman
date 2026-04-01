# Stop Billing Late: From Time Sheet to Invoice in Minutes

*Part 2 of "Run a Leaner Practice with Kazi."*

---

Every accounting firm has the same dirty secret: invoicing happens weeks after the work is done.

The partner finishes the annual financial statements on the 15th. The invoice goes out on the 30th — maybe. More likely it goes out in the first week of the following month, because the partner was busy with the next engagement and invoicing is admin work that always gets pushed.

Two weeks of float. For a firm billing R150,000/month, that's R75,000 permanently sitting in "work done, not yet billed." Over a year, that's R75,000 of working capital you're financing for your clients — interest-free.

The fix isn't discipline. It's reducing the gap between "work done" and "invoice sent" from weeks to minutes.

## Where the Time Goes (Before It Becomes an Invoice)

In most firms, the invoicing workflow looks like this:

1. Team logs time in a spreadsheet or time-tracking tool *(happens daily-ish)*
2. At month-end, partner reviews time sheets *(happens weekly-ish)*
3. Partner calculates what's billable, applies rates *(happens eventually)*
4. Admin creates invoices in Sage or Xero *(happens when admin has time)*
5. Partner reviews and approves invoices *(happens between meetings)*
6. Admin sends invoices to clients *(finally)*

Six steps, three people, two weeks. And that's the happy path — it assumes nobody's on leave, no invoices get stuck in review, and the spreadsheet doesn't have errors.

## What Kazi Does Differently

When your team logs time in Kazi, every entry is tagged as billable or non-billable, and every billable entry has a rate attached (resolved automatically from the [rate hierarchy](01-which-clients-are-profitable.md)).

At any moment, you can see the **unbilled time summary** per client:

```
Unbilled Time Summary — March 2026

Thornton Properties     12 entries   18.5 hours   R 22,175
Van der Merwe Trust      8 entries   11.0 hours   R  9,350
Naidoo & Partners        5 entries    6.0 hours   R  5,100
Cape Quarter CC          3 entries    2.5 hours   R  2,125
                                                  ─────────
                                     38.0 hours   R 38,750
```

No spreadsheet. No month-end reconciliation. This is live — updated as your team logs time.

### Billing Runs

When you're ready to invoice, Kazi batches the process:

1. **Select clients** — pick one client or all of them
2. **Review line items** — see every time entry that will appear on the invoice, with hours, rates, and amounts. Remove entries you don't want to bill yet. Add fixed-fee line items.
3. **Preview** — see the invoice as it will look, with your firm's logo, branding, and VAT details
4. **Generate** — one click creates all invoices in DRAFT status
5. **Approve and send** — review, approve, and email in batch

Steps 1-5 take about 10 minutes for a firm with 15 clients. Not 10 minutes per client — 10 minutes total.

### The Double-Billing Guard

Here's a subtle problem that spreadsheet-based invoicing creates: billing the same hours twice. A time entry gets included in March's invoice, then accidentally included in April's too. The client notices (or doesn't — either outcome is bad).

Kazi prevents this structurally. Once a time entry is included in an invoice, it's marked as billed. It cannot appear on another invoice. The "unbilled time summary" only shows entries that haven't been invoiced. There's no possible path to double-billing.

### From Draft to Paid

Invoices move through a clear lifecycle:

```
DRAFT → APPROVED → SENT → PAID
                     ↘
                    VOID (if cancelled)
```

**DRAFT**: Generated from time entries. Editable — add line items, adjust amounts, change the due date.

**APPROVED**: Partner has reviewed and approved. No further edits (create a new invoice instead).

**SENT**: Emailed to the client. Payment terms start.

**PAID**: Payment received. Records when and how.

**VOID**: Cancelled (e.g., sent in error). Creates an audit trail — the invoice existed, was voided, and why.

Every transition is logged. If a client disputes an invoice, you can show: when it was created, who approved it, when it was sent, and exactly which time entries it covers.

## Retainers: Predictable Revenue, Visible Consumption

Not all work is billed by the hour. Monthly bookkeeping retainers, annual compliance packages, and advisory agreements are fixed-fee — the client pays a set amount regardless of hours consumed.

Kazi tracks retainers as living agreements:

- **Monthly amount**: R5,000/month for bookkeeping
- **Hours included**: 8 hours (optional — some retainers are purely fixed-fee)
- **Period**: Monthly, auto-renewing
- **Consumption**: 6.5 of 8 hours used this month (visible to both you and the client)

At period close, Kazi generates the retainer invoice automatically. No manual creation. No forgetting. The invoice matches the retainer amount, references the agreement, and goes into the same DRAFT → APPROVED → SENT flow.

If the client consistently consumes more hours than the retainer includes, the consumption report shows it: "Van der Merwe Trust has exceeded their 8-hour retainer in 4 of the last 6 months. Average consumption: 11.2 hours." That's a fee review conversation backed by data — not a gut feeling.

## The Cash Flow Effect

When invoicing goes from "weeks after work" to "day of completion," the cash flow impact is immediate:

**Before Kazi** (typical):
- Work done: Week 1-2
- Invoice created: Week 3-4
- Invoice sent: Week 4-5
- Payment received: Week 8-9 (30-day terms from send date)
- **Cash gap: 8-9 weeks**

**With Kazi:**
- Work done: ongoing (time logged daily)
- Invoice created: end of engagement or end of month (minutes)
- Invoice sent: same day
- Payment received: Week 4-5 (30-day terms from send date)
- **Cash gap: 4-5 weeks**

Cutting the cash gap in half means R75,000 of working capital returns to your firm. For a small practice, that's the difference between comfortable and stretched.

## Aged Debtors: Who Owes What

Once invoices are out, Kazi tracks payment status automatically:

```
Aged Debtors — as at 31 March 2026

                    Current    30 days    60 days    90+ days    Total
Thornton Props      R 22,175        —          —           —    R 22,175
Van der Merwe       R  9,350   R 8,200         —           —    R 17,550
Naidoo & Partners        —    R 5,100    R 4,800           —    R  9,900
Cape Quarter CC          —         —     R 2,125     R 1,900    R  4,025
                   ─────────  ────────  ─────────  ──────────  ──────────
                   R 31,525   R 13,300   R 6,925     R 1,900    R 53,650
```

Cape Quarter CC has R1,900 outstanding for 90+ days. That's a follow-up call — or a decision to write it off. Either way, you're making the decision with data, not discovering it at year-end.

## What This Means for Your Practice

The firms that bill fastest collect fastest. It's not about being aggressive — it's about reducing the gap between delivering value and being paid for it.

If your current invoicing process involves a spreadsheet, a month-end scramble, and a hope that nothing was missed — Kazi replaces all of that with a 10-minute workflow that's accurate by design.

---

*[Request early access to Kazi →](#)*

*Next: [FICA Compliance Without the Filing Cabinet](03-fica-without-filing-cabinet.md)*
*Previous: [Which of Your Clients Are Actually Profitable?](01-which-clients-are-profitable.md)*
