# Cycle 4 — Day 45: Reconciliation & Prescription

**Date**: 2026-04-06
**Actors**: Alice (Owner), Bob (Admin)
**Stack**: E2E mock-auth (localhost:3001 / backend:8081 / Mailpit:8026)

## Summary

Trust reconciliation is fully blocked (no trust account). Court date lifecycle (Postpone) works well. Payment recording works end-to-end with reference tracking. Prescription tracking tab exists but has no trackers configured. Resources page loads with capacity grid.

## Checkpoint Results

| Step | Description | Result | Evidence |
|------|-------------|--------|----------|
| 45.1-45.8 | Bank reconciliation (trust) | BLOCKED | No trust account exists (GAP-D0-02 WONT_FIX). Trust Accounting page exists in sidebar but cannot create account or access reconciliation features. |
| 45.9 | Open Sipho matter Prescription tab | NOT_TESTED | Prescription tab is on Court Calendar page (global), not per-matter. |
| 45.10-45.12 | Prescription tracking | PARTIAL | Prescriptions tab exists on Court Calendar page. Shows "No prescription trackers found" with "Add Tracker" button. No prescription data configured for Sipho's litigation matter. Feature exists but requires manual tracker creation. |
| 45.13 | Screenshot prescription | N/A | Not captured. |
| 45.14 | Navigate to Court Calendar | PASS | Page loads with 1 court date. Shows filters: Status (All/Scheduled/Postponed/Heard/Cancelled), Type (Hearing/Trial/Motion/etc), Date range, Search. Tabs: List, Calendar, Prescriptions. |
| 45.15 | Find Sipho's motion date -> Postpone | PASS | Court date row: 2026-05-06, Motion, Gauteng Division Johannesburg, Sipho Ndlovu, Scheduled. Clicked Actions -> Postpone. |
| 45.16 | Add postponement details | PASS | "Postpone Court Date" dialog with New Date and Reason fields. Set date to 2026-05-20, reason "Postponement by agreement - respondent counsel unavailable". Confirmed. |
| 45.17 | Verify original POSTPONED + new SCHEDULED | PARTIAL | Court date updated to 2026-05-20 with status "Postponed". However, only 1 row shows -- the postponement updates the existing entry rather than creating a separate new SCHEDULED entry alongside the old POSTPONED one. **GAP-D45-01**. |
| 45.18 | Record Payment on Sipho INV-0001 | PASS | Navigated to INV-0001 (Sent, R4,973.75). "Record Payment" button available. |
| 45.19 | Enter payment reference | PASS | Inline "Record Payment" form appeared with "Payment Reference (optional)" field and "Confirm Payment" button. Entered "EFT-2026-SN-001". |
| 45.20 | Verify status = PAID | PASS | Status changed to **Paid**. Payment History section shows: "Payment Received - Paid on: Apr 6, 2026 - Reference: EFT-2026-SN-001". |
| 45.21 | Navigate to Resources | PASS | `/resources` loads. Shows team capacity grid with weekly view (4w/8w/12w toggle). Members: Alice Owner, Bob Admin, Carol Member + 3 "Unknown" entries (stale API members). |
| 45.22 | Check utilization breakdown | PARTIAL | All members show 0/40h for all weeks (0% avg). Resource allocation grid shows capacity but does not reflect logged time entries as utilization. This may be by design (allocation vs actuals). |

## New Gaps Found

| ID | Summary | Severity | Status |
|----|---------|----------|--------|
| GAP-D45-01 | Court date postponement replaces existing entry instead of keeping original as POSTPONED and creating new SCHEDULED entry. Test plan expected 2 rows (old=POSTPONED, new=SCHEDULED) but only 1 row exists with updated date and POSTPONED status. | MEDIUM | OPEN |
| GAP-D45-02 | Prescription tracking requires manual tracker creation -- not auto-derived from matter type (e.g., personal injury = 3-year prescription per Prescription Act). "Add Tracker" button exists but no automation. | MEDIUM | OPEN |
| GAP-D45-03 | Resources page shows 3 "Unknown" member entries alongside the 3 real members. Likely stale API-created member records from earlier cycles. | LOW | OPEN |

## Console Errors

0 errors during Day 45 testing.
