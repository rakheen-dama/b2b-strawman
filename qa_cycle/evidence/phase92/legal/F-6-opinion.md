# F-6 — trust banking account cashbook in debit after a s86(3) placement

Fable legal-adviser opinion, 2026-11-18 (research via Perplexity; the gazetted Rules were not fetched directly —
rule text as consistently quoted in the LSSA copy, De Rebus, the IRBA guide and an SCA judgment).
**Signed off by the founder 2026-11-18.**

## Law
- **s86(3)** LPA: a practice may invest of its own accord "any money which is **not immediately required for any particular purpose**" [1]. Money needed for a client payment now due is not eligible to sit in a s86(3) investment.
- **Rule 54.14.8**: total in trust banking account + trust investment account + trust cash ≥ total trust-creditor credit balances, at any date [2][3] — an **aggregate** test; a covered banking-account shortfall does not breach it.
- **Rule 54.14.9**: no trust-creditor account in debit [2] (client ledgers, not the bank account).
- **Rule 54.14.7.3**: money withdrawn from a trust investment account is deposited promptly in the trust banking account [2] — investment money reaches a client only via the banking account.
- **Rule 54.14.7.1**: adequate internal controls to safeguard trust funds [2].
- **LPC bookkeeping guide (2026)**: "The trust account must never be overdrawn … funds must be immediately advanced to the account by the practice." [4]
- **Rules 54.14.10 / 54.14.11**: immediate written report to the Council (with explanation and proof of rectification) if trust bank money + cash < trust creditors, or a creditor is in debit [2][5]. *Matsi v LPC* [2026] ZASCA 12 treats a deficit as a contravention in itself [3].
- **s87(1)**: proper records of money received, held or paid [1] — recording what the bank did may never be refused.

## Ruling (signed off)
1. **Block at approval** any firm-initiated outflow from a trust banking account — client payment, refund, fee transfer, s86(4) placement, s86(3) placement — unless the account's derived cashbook covers the amount, checked under an account-level lock; message directs the user to withdraw from the s86(3) investment first. (Also closes F-6a, the two-pending-placements race.)
2. **Never block recording** anything the bank has already done — statement-driven postings, dishonoured deposits, charges, sweeps. R14 / ADR-380 stand unchanged for records.
3. **Integrity flag** "trust banking account cashbook in debit" for anything that still gets through (back-dated or bank-driven entries), worded to prompt a Rule 54.14.8 aggregate check and, if short, an immediate Rule 54.14.10 report — without asserting the event is reportable.
4. No mandatory buffer.

Trade-off accepted: approvals of client debits on one account serialise on the account lock (negligible at design-partner volumes). Lock order must be transaction row → account → client anchor (see 648A found.md F-6) to avoid deadlock with placement requests.

## Confidence
| Conclusion | Confidence |
|---|---|
| Rule texts / aggregate nature of 54.14.8–11 and s86(3) | high |
| Recording bank facts must never be blocked | high |
| Approval-time block is the better reading of 54.14.7.1 (not textually mandated) | medium |
| A covered banking-account overdraft is not a 54.14.10 report | medium-low |

**Open point (before the flag's wording relies on it):** a trust auditor or the LPC to confirm whether "trust bank accounts" in Rule 54.14.10 includes investment accounts, and whether an overdrawn s86(2) account is reportable regardless of cover. Related: OL-7 (client ledger in debit at reconciliation completion); the reference repo marks its 54.14.8 / 54.14.9 entries pending attorney confirmation (`claude-for-legal-sa/project/trust-accounting-attorney-review.md`).

## Citations
1. Legal Practice Act 28 of 2014, ss 86(2)–(5), 87(1): https://justice.gov.za/legislation/acts/2014-028.pdf
2. LPC Rules (GenN 401 of 2018), Rules 54.14.7–54.14.11: https://www.lssa.org.za/wp-content/uploads/2020/01/LPC-Rules-9-July-2018.pdf
3. *Matsi v SA Legal Practice Council* [2026] ZASCA 12: https://semantis.ai/law-reports/judgment/59329/matsi-and-another-v-south-african-legal-practice-council-gauteng-province-184202/
4. LPC Guide for Attorneys' Bookkeeping (Reg 6(10)(i)), 2026: https://lpc.org.za/wp-content/uploads/2026/03/i-LPC-Guide-for-Attorneys-Bookkeeping-ito-Regulation-610i.pdf
5. De Rebus, "Handling of trust money" (2019): https://www.derebus.org.za/handling-of-trust-money-dealing-with-the-obligations-of-a-trust-account-legal-practitioner/
6. IRBA Guide, Engagements on Legal Practitioners' Trust Accounts: https://www.irba.co.za/upload/Proposed%20Revised%20Guide%20RAs_Eng%20on%20LP%20Trust%20Accounts.pdf
7. Legal Practitioners' Fidelity Fund, Trust Bank Accounts: https://www.fidfund.co.za/trust-accounts/
