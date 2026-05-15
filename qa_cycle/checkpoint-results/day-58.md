# Day 58 Checkpoint Results

**Date**: 2026-05-15
**Actor**: Thandi Thornton (Owner)
**Scenario step**: 58.1 — Second invoice cycle for Kgosi (Year-End Pack unbilled work)

## Steps Executed

### 58.1 — Create second invoice for Kgosi (Year-End Pack work)
1. Navigated to Invoices > New Invoice > selected Kgosi Holdings (Pty) Ltd
2. "Generate Invoice" dialog appeared — clicked "Fetch Unbilled Time"
3. "Select Unbilled Items" dialog showed 7 items from Year-End Pack, total R 29,350.00 (33h)
   - CIPC annual return filing (4h, Bob, R 3,400)
   - Draft annual financial statements x3 (1h+2h+8h, Bob, R 850+R 1,700+R 6,800)
   - Tax computation & ITR14 prep (8h, Bob, R 6,800)
   - Trial balance review & journals (8h, Bob, R 6,800)
   - Request & receive trial balance (2h, Thandi, R 3,000)
4. All 7 items selected. Pre-gen checks: 3 pass, 1 warning (2/5 required fields)
5. Clicked "Create Draft (1 issues)" — draft created
6. Opened draft invoice — verified 7 line items, Subtotal R 29,350, VAT R 4,402.50, Total R 33,752.50
7. Clicked "Approve" — INV-0003 assigned, status Approved, Issued May 15, 2026
8. Clicked "Send Invoice" — validation override dialog appeared (same pattern as INV-0001/0002)
9. Clicked "Send Anyway" — status changed to Sent

**Result**: PASS

**Verification**:
- INV-0003 assigned with correct sequential numbering
- Status: Sent
- 7 line items all from Year-End Pack engagement
- VAT 15% calculated correctly on each line (R 4,402.50 total tax)
- Subtotal R 29,350.00, Total R 33,752.50
- Payment History: "No payment events yet"
- Audit event recorded

## Evidence
- Screenshot: `qa_cycle/evidence/day-58/kgosi-yep-invoice-sent.png`

## Summary
| Step | Description | Result |
|------|-------------|--------|
| 58.1 | Second invoice cycle (Year-End Pack unbilled) | PASS |

**New gaps**: 0
**Third invoice**: Kgosi Year-End Pack **SENT** (INV-0003), ID: 079e7cb4-d635-49e5-b09c-9cafc1bc5e6f, 7 line items, Subtotal R 29,350.00, VAT R 4,402.50, Total R 33,752.50, Issued May 15 2026
