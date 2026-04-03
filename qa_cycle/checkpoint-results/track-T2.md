# Track 2 — Prescription Tracker Results

**Executed**: 2026-04-04
**Actor**: QA Agent (Cycle 1, continued)
**Stack**: Keycloak dev (3000/8080/8443/8180)

---

## T2.1 — Prescription List & Status

| Step | Result | Evidence |
|------|--------|----------|
| T2.1.1 | PASS | Prescriptions tab exists on Court Calendar page (third tab: List, Calendar, Prescriptions). |
| T2.1.2 | PASS | 2 seeded trackers visible. Columns: Matter, Client, Type, Cause of Action, Prescription Date, Status, Days Left, Actions. |
| T2.1.3 | PASS | Mabena tracker: Type = Delict (3yr), Cause of Action = 2024-06-15, Prescription Date = 2027-06-15, Status = Running, Days Left = 438. |
| T2.1.4 | FAIL | Mining Rights tracker: Type = General (3yr), Cause of Action = 2023-01-10, Prescription Date = 2026-01-10, Status = **Running** (should be WARNED/EXPIRED — date is 84 days in the past), Days Left = **"—"** (dash, should show negative number or expired indicator). Confirms GAP-P55-002. |
| T2.1.5 | FAIL | No urgency-based sorting. Expired tracker does not sort first. Both show "Running" status so there is no differentiation. |

**Note**: The "Add Tracker" button is present, but was not tested due to potential for same crash as "New Court Date" dialog.

---

## T2.2–T2.5 — Not Executed

Remaining Track 2 checkpoints (Create with different types, Interrupt, Edge Cases, Upcoming View) were deferred to keep pace on remaining tracks. The T2.1 results confirm GAP-P55-002 (expired status not detected) which is the most critical finding for this track.
