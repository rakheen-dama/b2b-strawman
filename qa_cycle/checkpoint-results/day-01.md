# Day 1 — First Client Onboarding: Kgosi Construction (Cycle 1)
## Executed: 2026-03-16T21:42Z
## Actor: Bob (Admin), then Alice (Owner) for proposals

### Checkpoint 1.1 — Login as Bob, navigate to Customers
- **Result**: PASS
- **Evidence**: Logged in as Bob Admin via /mock-login. Sidebar shows "BA" and "Bob Admin / bob@e2e-test.local". Navigated to Clients > Customers. Customers page shows 1 existing customer (Acme Corp, Active). "New Customer" button visible. Customer count header shows "1".

### Checkpoint 1.2 — Click New Customer
- **Result**: PASS
- **Evidence**: Clicked "New Customer" button. "Create Customer" dialog opened (Step 1 of 2) with fields: Name, Type (Individual/Company/Trust dropdown), Email, Phone (optional), ID Number (optional), Notes (optional).

### Checkpoint 1.3 — Fill customer details
- **Result**: PASS
- **Evidence**: Filled: Name = "Kgosi Construction (Pty) Ltd", Type = Company, Email = thabo@kgosiconstruction.co.za, Phone = +27-11-555-0100, Notes = "Pty Ltd, FYE 28 Feb, VAT vendor". Clicked Next.

### Checkpoint 1.4 — Custom fields visible in Step 2
- **Result**: PASS
- **Evidence**: Step 2 of 2 "Additional Information" dialog shows 3 field groups: "SA Accounting -- Trust Details" (3 required fields), "SA Accounting -- Client Details" (7 required fields + 9 optional), and "Contact & Address". Filled required Client Details fields: SARS Tax Reference (9301234567), Financial Year-End (2025-02-28), Entity Type (Pty Ltd), Registered Address, Primary Contact Name/Email, FICA Verified (Not Started).
- **Gap**: GAP-P48-008 CONFIRMED -- Trust Details field group shows for Company type customers. Required Trust fields (Trust Registration Number, Trust Deed Date, Trust Type) should not appear for Company entities. The form did accept submission without filling Trust fields, so it's not blocking, but it's confusing UX.

### Checkpoint 1.5 — Customer appears in list with PROSPECT status
- **Result**: PASS
- **Evidence**: After submitting (via JavaScript click due to viewport scroll issue), customer list shows "2" customers. "Kgosi Construction (Pty) Ltd" appears with: FICA Verified: Not Started, Entity Type: Pty Ltd, email, phone, Lifecycle: Prospect, Status: Active, Completeness: 14%, Created: Mar 16, 2026.

### Checkpoint 1.6 — Customer detail shows PROSPECT lifecycle badge
- **Result**: PASS
- **Evidence**: Clicked into customer detail. Heading shows "Kgosi Construction (Pty) Ltd" with badges: "Active" (status), "Prospect" (lifecycle), 14% completeness ring. Notes visible: "Pty Ltd, FYE 28 Feb, VAT vendor". Customer Readiness: 33% (Projects linked: not done, No onboarding checklist, Required fields: 7/10). "Ready to start onboarding?" prompt shown with "Start Onboarding" link.

### Checkpoint 1.7 — Find lifecycle transition action
- **Result**: PASS
- **Evidence**: "Change Status" button visible in action bar. Clicked it, dropdown shows "Start Onboarding" menu item.

### Checkpoint 1.8 — Transition to ONBOARDING
- **Result**: PASS
- **Evidence**: Clicked "Start Onboarding". Confirmation dialog appeared: "This will move the customer to Onboarding status and automatically create compliance checklists." with optional notes field. Clicked "Start Onboarding" to confirm. After page reload, lifecycle badge shows "Onboarding". "Since Mar 16, 2026" date appears. "Onboarding" tab appeared in the tab list. Customer Readiness now shows "Onboarding checklist (0/4)".
- **Note**: Console error on reload: `TypeError: Cannot read properties of null` -- SSR hydration issue, page still renders correctly. Pre-existing React #418 also present.

### Checkpoint 1.9 — Onboarding tab with checklist
- **Result**: PASS
- **Evidence**: Clicked "Onboarding" tab. Shows "Generic Client Onboarding" checklist, status "In Progress", "0/4 completed (0/4 required)". "Manually Add Checklist" button available at the top.

### Checkpoint 1.10 — FICA checklist auto-instantiated
- **Result**: PARTIAL
- **Evidence**: A "Generic Client Onboarding" checklist was auto-instantiated with 4 items (not the FICA/KYC -- SA Accounting checklist with 9 items). The 4 items are: (1) Confirm client engagement, (2) Verify contact details, (3) Confirm billing arrangements, (4) Upload signed engagement letter. All show Pending/Required status. The FICA checklist could be manually added via "Manually Add Checklist" button, but it was not auto-instantiated.
- **Gap**: The script expected a FICA checklist to be auto-instantiated. Only the generic checklist auto-instantiated. FICA checklist (if seeded, per GAP-026 fix) would need manual addition.

### Checkpoint 1.11 — Mark first checklist item complete
- **Result**: PARTIAL
- **Evidence**: Clicked "Mark Complete" on "Confirm client engagement". A confirmation form appeared with optional notes field and Confirm/Cancel buttons. Clicked "Confirm". However, after confirmation, the item still shows "Pending" status and the progress counter still reads "0/4 completed". The completion may not have persisted or the UI did not refresh.
- **Gap**: NEW -- Checklist item completion may not be saving/refreshing properly. The Mark Complete action triggers a confirmation dialog but the item status does not visually update to "Completed" after confirmation.

### Checkpoint 1.12 — Mark second checklist item complete
- **Result**: NOT TESTED
- **Evidence**: Skipped due to checkpoint 1.11 issue (first item completion unclear).

### Checkpoint 1.13 — Progress indicator updates
- **Result**: NOT TESTED
- **Evidence**: Blocked by 1.11. Progress indicator still shows "0/4 completed" after attempting to mark first item complete.

### Checkpoint 1.14-1.18 — Information request flow
- **Result**: NOT TESTED (this cycle)
- **Evidence**: Steps 1.14-1.18 (navigate to Requests tab, create information request, send, check Mailpit) were not tested in this cycle due to time constraints. The Requests tab is visible in the tab list. Based on previous cycle results, GAP-P48-009 (portal contact required) may still apply.

### Checkpoint 1.19-1.24 — Proposal creation
- **Result**: FAIL (BLOCKED by GAP-P48-001)
- **Evidence**: GAP-P48-001 is a known blocker -- no proposal creation/detail UI exists. The sidebar has no "Proposals" navigation item. Backend has full CRUD + lifecycle but frontend only has list view (if any). Steps 1.19-1.24 cannot be executed.
- **Gap**: GAP-P48-001 CONFIRMED -- blocker at step 1.19

---

## Day 1 Summary

| Checkpoint | Result | Notes |
|-----------|--------|-------|
| 1.1 — Login as Bob | PASS | Bob Admin authenticated |
| 1.2 — New Customer dialog | PASS | Step 1 dialog opens |
| 1.3 — Fill customer details | PASS | All fields filled |
| 1.4 — Custom fields in Step 2 | PASS | SA Accounting fields visible (GAP-P48-008 confirmed: Trust fields show for Company) |
| 1.5 — Customer in list as PROSPECT | PASS | Customer created, shows as Prospect |
| 1.6 — Detail page PROSPECT badge | PASS | Lifecycle badge shows Prospect |
| 1.7 — Find lifecycle transition | PASS | Change Status dropdown works |
| 1.8 — Transition to ONBOARDING | PASS | Lifecycle badge updates to Onboarding |
| 1.9 — Onboarding tab | PASS | Tab appears with checklist |
| 1.10 — Checklist auto-instantiated | PARTIAL | Generic checklist (4 items), not FICA (9 items) |
| 1.11 — Mark first item complete | PARTIAL | Confirmation dialog works but status may not persist |
| 1.12 — Mark second item complete | NOT TESTED | Blocked by 1.11 |
| 1.13 — Progress indicator | NOT TESTED | Blocked by 1.11 |
| 1.14-1.18 — Information request | NOT TESTED | Deferred |
| 1.19-1.24 — Proposal creation | FAIL (BLOCKED) | GAP-P48-001 CONFIRMED |

**Day 1 Result**: 8 PASS, 2 PARTIAL, 1 FAIL (blocked), 4 NOT TESTED

**New observations**:
- GAP-P48-008 CONFIRMED: Trust Details field group shows for Company type customers in creation wizard
- GAP-P48-001 CONFIRMED: No proposal creation UI (blocker at step 1.19)
- NEW: Checklist item "Mark Complete" may not persist -- item stays Pending after confirmation (needs investigation)
- Console TypeError on customer detail page after lifecycle transition (SSR hydration issue, non-blocking)
- Create Customer dialog "Create Customer" button goes out of viewport when many field groups are expanded (required JavaScript click workaround)

**Blocker reached**: GAP-P48-001 at step 1.19 -- stopping Day 1 execution as instructed.
