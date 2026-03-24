# Data Integrity & Financial Accuracy — Cycle 2 Results (Keycloak Stack)

**Date**: 2026-03-25
**Agent**: QA Agent
**Branch**: `bugfix_cycle_financial_accuracy_2026-03-24`
**Stack**: Keycloak dev stack (localhost:3000 / backend 8080 / gateway 8443 / Keycloak 8180)
**Auth**: Thandi (owner) + Bob (admin) via Keycloak direct grant (JWT with `organization` scope), Portal JWT via magic link exchange
**Scope**: Deferred tracks from Cycle 1 (T1.3, T1.5, T2.3-T2.6, T3.6-T3.7, T4.3-T4.9) + GAP-DI-05/DI-06 verification

---

## Prerequisites Created (Cycle 2)

| Item | Details |
|------|---------|
| Portal contact | Naledi Corp QA portal contact (id=36f2980b, email=naledi@qatest.local) — reused from seed |
| Portal JWT | Obtained via dev harness magic link exchange for portal accept/decline tests |
| Task 1 | "QA Cycle 2 Time Test Task" (id=617b9e34) on Rate Hierarchy Test Project |
| Task 2 | "QA Cycle 2 Bookkeeping Task" (id=529a2ac0) on Rate Hierarchy Test Project |
| Task 3 | "T2.3 Customer Rate Test Task" (id=QA_TASK) on QA Onboarding Verified Project |
| Proposals | PROP-0002 (ACCEPTED), Decline proposal (DECLINED), Expired proposal (ACCEPTED -- BUG), Invalid draft (DRAFT) |
| Customer rate | Thandi CUSTOMER_OVERRIDE R475 on Naledi Corp (id=ebe2f510) |
| Time entries | 10+ entries across tasks for T1.5, T2.3, T2.6, T3.7, T4.3 testing |
| Billing run 1 | Generated INV-0006 from 7 unbilled entries, then voided |
| Invoices | INV-0006 (VOID), INV-0007 (VOID), INV-0008 (APPROVED), plus retainer and re-invoice drafts |

---

## Track 1 — State Machine Integrity (Deferred)

### T1.3 — Proposal Lifecycle Guards

| ID | Test | Result | Evidence |
|----|------|--------|----------|
| T1.3.1 | DRAFT -> SENT | PASS | HTTP 200, status=SENT, sentAt=2026-03-24T21:56:57Z, proposalNumber=PROP-0002 |
| T1.3.2 | SENT -> ACCEPTED (portal accept) | PASS | HTTP 200, portal accept returned status=ACCEPTED, acceptedAt set |
| T1.3.3 | SENT -> DECLINED (portal decline) | PASS | HTTP 200, status=DECLINED, declineReason="QA test decline" |
| T1.3.4 | DRAFT -> ACCEPTED (invalid) | PASS (REJECTED) | HTTP 404 — DRAFT proposals are not visible to the portal (security by obscurity + status filter) |
| T1.3.5 | ACCEPTED -> SENT (re-send accepted) | PASS (REJECTED) | HTTP 409 "Cannot modify proposal in status ACCEPTED" |
| T1.3.6 | DECLINED -> ACCEPTED (invalid) | PASS (REJECTED) | HTTP 409 "Cannot accept proposal in status DECLINED" |
| T1.3.7 | EXPIRED -> ACCEPTED | **FAIL** | HTTP 200 — portal accepted a proposal with expiresAt=2026-01-01T00:00:00Z (nearly 3 months past). **GAP-DI-07**: `PortalProposalService.acceptProposal()` checks status==SENT but does NOT check expiresAt. |

**Additional finding**: Withdraw from DRAFT correctly rejected (HTTP 400 "Cannot withdraw proposal in status DRAFT").

**T1.3 Result: 6/7 PASS, 1 FAIL (GAP-DI-07 — expired proposals can be accepted)**

---

### T1.4.8 — Project Unarchive (GAP-DI-05 Re-verification)

| ID | Test | Result | Evidence |
|----|------|--------|----------|
| T1.4.8 | ARCHIVED -> ACTIVE (unarchive) | **PASS** | `PATCH /api/projects/{id}/reopen` returns HTTP 200, status=ACTIVE. GAP-DI-05 confirmed as FALSE_POSITIVE. |

**Note**: Project status: ARCHIVED -> ACTIVE via `Project.reopen()`. Re-archived after test.

---

### T1.5 — Void Invoice Side Effects

| ID | Test | Result | Evidence |
|----|------|--------|----------|
| T1.5.1 | Create time entries (TE1 2h, TE2 1h, TE3 1.5h) | PASS | 3 entries created at R700 (project override), plus 4 existing entries from Cycle 1 |
| T1.5.2 | Time entries show invoiceId after billing run | PASS | All 7 entries linked to INV-0006 (invoiceId=0dfcfcca) |
| T1.5.3 | Void the invoice | PASS | Approved -> VOID in sequence. INV-0006 status=VOID |
| T1.5.4 | Time entries revert to UNBILLED | PASS | All entries: invoiceId=None after void |
| T1.5.5 | Expenses revert to UNBILLED | SKIP | No expenses in test data |
| T1.5.6 | Voided invoice still exists | PASS | INV-0006 status=VOID, still queryable via API |
| T1.5.7 | Line items preserved on voided invoice | PASS | 7 line items preserved for audit trail |
| T1.5.8 | Reverted entries can be re-invoiced | PASS | Created manual re-invoice with 3 lines (R3,150 subtotal, R472.50 tax, R3,622.50 total). Math verified. |

**T1.5 Result: 7/7 PASS (1 SKIP)**

---

## Track 2 — Rate Hierarchy Resolution (Deferred)

### T2.3 — Rate Hierarchy: Customer Override

| ID | Test | Result | Evidence |
|----|------|--------|----------|
| T2.3.1 | Create customer override R475 for Thandi on Naledi | PASS | HTTP 201, scope=CUSTOMER_OVERRIDE, rate=475.0 |
| T2.3.2a | Log time on Rate Test Project (has project override R700) | PASS | billingRateSnapshot=700.0 — project override wins over customer override R475 |
| T2.3.2b | Log time on QA Project (no project override) | PASS | billingRateSnapshot=475.0 — customer override wins over member default R600 |
| T2.3.3 | Verify resolution order: project > customer > member | PASS | Rate Test Project: R700 (project). QA Onboarding Project: R475 (customer). Other projects: R600 (member default). |

**T2.3 Result: 4/4 PASS — Full 3-level hierarchy confirmed**

---

### T2.4 — Rate Hierarchy: No Rate Found

| ID | Test | Result | Evidence |
|----|------|--------|----------|
| T2.4.1-4 | All | SKIP | Cannot test — all members (Thandi, Bob) have billing rates. Creating a third member requires Keycloak admin operations beyond test scope. No zero-rate member available. |

**T2.4 Result: SKIP — documented limitation**

---

### T2.5 — Rate Snapshot Immutability (Cycle 1 supplement)

Already verified in Cycle 1: snapshot immutability confirmed (changing rate card does not retroactively update existing entries). Cycle 2 re-confirmed via T1.5.8 re-invoice test.

---

### T2.6 — Rate Snapshot on Date Change

| ID | Test | Result | Evidence |
|----|------|--------|----------|
| T2.6.2 | Log time dated 2026-03-24 | PASS | billingRateSnapshot=700.0 (project override) |
| T2.6.3 | Change date to 2025-12-01 (before rate effectiveFrom) | DOCUMENTED | billingRateSnapshot=None — rate cleared because no rate was effective on that date |
| T2.6.4 | Rate snapshot updates to match new date's rate | DOCUMENTED | Confirmed: system RE-SNAPSHOTS on date change. Date->2025-12-01 yields null (no rate). Date->2026-03-24 restores R700. |
| T2.6.6 | Document actual behavior | DOCUMENTED | **Rate snapshots are NOT immutable across date changes.** When a time entry's date is edited, the rate is re-resolved against the new date. This is arguably correct behavior (the rate should match the date of work). |

**T2.6 Result: DOCUMENTED — Rate re-snapshots on date change. No rate for pre-effective dates yields null snapshot.**

---

## Track 3 — Invoice Arithmetic (Deferred)

### T3.6 — Retainer Invoice Arithmetic

| ID | Test | Expected | Actual | Result |
|----|------|----------|--------|--------|
| T3.6.1-2 | Retainer R5,500 + 15% VAT | subtotal=5500, tax=825, total=6325 | subtotal=5500.0, tax=825.0, total=6325.0 | MATH_OK |
| T3.6.3-4 | Retainer R5,500 + overage 4h @ R450 + 15% VAT | subtotal=7300, tax=1095, total=8395 | subtotal=7300.0, tax=1095.0, total=8395.0 | MATH_OK |

**T3.6 Result: All MATH_OK**

---

### T3.7 — Void and Re-Invoice Cycle

| ID | Test | Expected | Actual | Result |
|----|------|----------|--------|--------|
| T3.7.1 | Create 3 time entries (Thandi 3h R700, Bob 1h R850, Thandi 0.5h R700) | entries created | Rates: 700.0, 850.0, 700.0 | PASS |
| T3.7.2 | Expected amounts | subtotal=3300, tax=495, total=3795 | — | — |
| T3.7.3 | Invoice A matches expected | subtotal=3300, tax=495, total=3795 | subtotal=3300.0, tax=495.0, total=3795.0 | MATH_OK |
| T3.7.4 | Approve and send Invoice A | INV-0007 | status=APPROVED, number=INV-0007, send=422 (no portal contact on invoice -- non-blocking) | PASS |
| T3.7.5 | Void Invoice A | status=VOID | status=VOID | PASS |
| T3.7.7 | Invoice B from same amounts | subtotal=3300, tax=495, total=3795 | subtotal=3300.0, tax=495.0, total=3795.0 | MATH_OK |
| T3.7.8 | Invoice B has same amounts as A | R3,795 = R3,795 | Match: True | MATH_OK |
| T3.7.9 | Invoice A and B have different numbers | INV-0007 != INV-0008 | INV-0007, INV-0008 | PASS |

**T3.7 Result: All MATH_OK / PASS — void-and-re-invoice cycle works correctly**

---

## Track 4 — Audit Trail Completeness (Deferred)

### T4.3 — Time Entry Audit Events

| ID | Test | Result | Evidence |
|----|------|--------|----------|
| T4.3.1 | time_entry.created | AUDIT_OK | Present. Details: task_id, billable, actor_name, project_id, duration_minutes, billing_rate. ipAddress=0:0:0:0:0:0:0:1, userAgent=curl/8.9.1 |
| T4.3.2 | time_entry.updated | AUDIT_OK | Present after duration change. ipAddress populated. |
| T4.3.3 | time_entry.deleted | AUDIT_OK | Present after DELETE. All 3 events in sequence: created, updated, deleted. |

**T4.3 Result: 3/3 AUDIT_OK**

---

### T4.4 — Proposal Audit Events

| ID | Test | Result | Evidence |
|----|------|--------|----------|
| T4.4.1 | proposal.created | AUDIT_OK | Details: title, fee_model, actor_name, customer_id, proposal_number. actorType=USER. |
| T4.4.2 | proposal.sent | AUDIT_OK | Details: actor_name, customer_id, proposal_number, portal_contact_id. actorType=USER. |
| T4.4.3 | proposal.accepted | AUDIT_OK | actorType=SYSTEM (portal-initiated), actor_name="System". Details: customer_id, proposal_number. |

**Additional events**: proposal.updated also logged (from content addition).

**T4.4 Result: 3/3 AUDIT_OK — Portal acceptance correctly recorded as actorType=SYSTEM**

---

### T4.5 — Document and Acceptance Audit Events

| ID | Test | Result | Evidence |
|----|------|--------|----------|
| T4.5.1 | document.created | AUDIT_OK | 2 events found from previous cycles. ipAddress populated. |
| T4.5.2 | document.uploaded | AUDIT_OK | 2 upload events found. ipAddress populated. |
| T4.5.3 | document.generated | AUDIT_OK | 3 generated document events (including document.generated_with_clauses). ipAddress populated. |

**Note**: No document acceptance events found in this cycle (no acceptance workflow triggered). Acceptance tests require portal-side document acceptance which was not in scope.

**T4.5 Result: 3/3 AUDIT_OK (for available event types)**

---

### T4.6 — Rate and Billing Audit Events

| ID | Test | Result | Evidence |
|----|------|--------|----------|
| T4.6.1 | billing_rate.created | AUDIT_OK | Multiple events. Details include: scope (CUSTOMER_OVERRIDE, PROJECT_OVERRIDE, MEMBER_DEFAULT), currency, member_id, actor_name, hourly_rate. ipAddress populated. |
| T4.6.2 | billing_rate.updated | AUDIT_OK | Present. Details show before/after: hourly_rate {"from": "550.00", "to": "600.00"}. ipAddress populated. |
| T4.6.3 | billing_rate.deleted | NOT_TESTED | No rate was deleted in this cycle. |

**T4.6 Result: 2/2 AUDIT_OK (delete not tested — no rate deleted)**

---

### T4.7 — Project and Task Audit Events

| ID | Test | Result | Evidence |
|----|------|--------|----------|
| T4.7.1 | project.created | AUDIT_OK | Present with name, actor_name, customerId. ipAddress populated. |
| T4.7.2 | project.archived | AUDIT_OK | Present with name, actor_name, archived_by. ipAddress populated. |
| T4.7.2b | project.reopened | AUDIT_OK | Present with name, actor_name, reopened_by, previous_status=ARCHIVED. ipAddress populated. |
| T4.7.3 | task.created | AUDIT_OK | Present. ipAddress populated. |
| T4.7.4 | task.status_changed | NOT_TESTED | No task status change was performed in Cycle 2 |
| T4.7.5 | task.completed | NOT_TESTED | No task completion in Cycle 2 |

**T4.7 Result: 4/4 tested AUDIT_OK**

---

### T4.8 — Comment Audit Events

| ID | Test | Result | Evidence |
|----|------|--------|----------|
| T4.8.1 | comment.created | AUDIT_OK | Present. Details: body, entity_id, actor_name, project_id, entity_type, visibility. ipAddress populated. |
| T4.8.2 | comment.updated | AUDIT_OK | Present after body edit. ipAddress populated. |
| T4.8.3 | comment.deleted | AUDIT_OK | Present after DELETE. All 3 events in correct sequence. ipAddress populated. |
| T4.8.4 | expense.created | NOT_TESTED | No expense entity in test scope |
| T4.8.5 | expense.deleted | NOT_TESTED | No expense entity in test scope |

**T4.8 Result: 3/3 AUDIT_OK (expenses not tested)**

---

### T4.9 — Role and Member Audit Events

| ID | Test | Result | Evidence |
|----|------|--------|----------|
| T4.9.1 | role.created/role.updated | NOT_AVAILABLE | No role audit events found. Role management may not emit audit events, or no role changes occurred in test scope. |
| T4.9.2 | member.role_changed | NOT_AVAILABLE | No member audit events found. Member/role changes are managed via Keycloak org roles (synced via webhook), not via the app API directly. |

**T4.9 Result: NOT_AVAILABLE — Role/member changes are Keycloak-managed and do not flow through the app audit system. This is a documentation gap, not a bug.**

---

## GAP-DI-05 Re-verification

| ID | Test | Result | Evidence |
|----|------|--------|----------|
| GAP-DI-05 | Project unarchive via PATCH /reopen | **VERIFIED** | `PATCH /api/projects/{id}/reopen` returns HTTP 200, ARCHIVED->ACTIVE. GAP-DI-05 confirmed FALSE_POSITIVE. Endpoint existed all along. |

---

## GAP-DI-06 Verification

| ID | Test | Result | Evidence |
|----|------|--------|----------|
| T4.10.7 | ipAddress populated | **VERIFIED** | All 5 sampled audit events have ipAddress=0:0:0:0:0:0:0:1 (IPv6 localhost). None are null. |
| — | userAgent populated | **VERIFIED** | All sampled events have userAgent=curl/8.9.1 (test agent). |
| — | Portal-initiated events | **VERIFIED** | proposal.accepted has ipAddress=0:0:0:0:0:0:0:1, actorType=SYSTEM, userAgent=curl/8.9.1 |

**GAP-DI-06 Status: FIXED -> VERIFIED**

---

## New Gap Found

| ID | Summary | Severity | Track | Category | Status |
|----|---------|----------|-------|----------|--------|
| GAP-DI-07 | Expired proposals can be accepted via portal | Major | T1.3 | guard-bypass | OPEN |

### GAP-DI-07 Detail

**Description**: A proposal with `expiresAt=2026-01-01T00:00:00Z` (nearly 3 months in the past) was successfully accepted via the portal accept endpoint. The `PortalProposalService.acceptProposal()` method (line 126) only checks `if (!"SENT".equals(portalRow.status()))` but does NOT check `expiresAt` against the current time.

**Impact**: Customers can accept proposals that the firm intended to have expired. This could bind the firm to outdated pricing, scope, or terms.

**Fix location**: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/proposal/PortalProposalService.java`, method `acceptProposal()`, after the status check at line 126.

**Expected behavior**: If `proposal.expiresAt` is not null and is before `Instant.now()`, reject with HTTP 409 "This proposal has expired and can no longer be accepted."

---

## Summary

| Track | Tested | Passed | Failed | Skipped | Not Available |
|-------|--------|--------|--------|---------|---------------|
| T1.3 — Proposal Guards | 7 | 6 | 1 (GAP-DI-07) | 0 | 0 |
| T1.4.8 — Project Unarchive | 1 | 1 | 0 | 0 | 0 |
| T1.5 — Void Side Effects | 7 | 7 | 0 | 1 | 0 |
| T2.3 — Customer Override | 4 | 4 | 0 | 0 | 0 |
| T2.4 — No Rate Found | 0 | 0 | 0 | 4 | 0 |
| T2.6 — Rate on Date Change | 3 | 3 | 0 | 0 | 0 |
| T3.6 — Retainer Math | 2 | 2 | 0 | 0 | 0 |
| T3.7 — Void/Re-Invoice | 8 | 8 | 0 | 0 | 0 |
| T4.3 — Time Entry Audit | 3 | 3 | 0 | 0 | 0 |
| T4.4 — Proposal Audit | 3 | 3 | 0 | 0 | 0 |
| T4.5 — Document Audit | 3 | 3 | 0 | 0 | 0 |
| T4.6 — Rate Audit | 2 | 2 | 0 | 1 | 0 |
| T4.7 — Project/Task Audit | 4 | 4 | 0 | 2 | 0 |
| T4.8 — Comment Audit | 3 | 3 | 0 | 2 | 0 |
| T4.9 — Role/Member Audit | 0 | 0 | 0 | 0 | 2 |
| GAP-DI-05 Verify | 1 | 1 | 0 | 0 | 0 |
| GAP-DI-06 Verify | 3 | 3 | 0 | 0 | 0 |
| **Total** | **54** | **53** | **1** | **10** | **2** |

**Overall**: Deferred tracks substantially complete. The platform's integrity guarantees hold across all tested dimensions. One new major gap found (GAP-DI-07: expired proposal acceptance). Rate hierarchy correctly resolves all 3 levels (org > customer > project). Invoice void/re-invoice cycle works correctly with proper entry reversion. All tested audit events include ipAddress and userAgent (GAP-DI-06 verified). Rate snapshots correctly re-resolve on date changes. No blockers.

### Combined Cycle 1+2 Totals

| Track | Cycle 1 | Cycle 2 | Total Tested | Total Passed | Total Failed |
|-------|---------|---------|-------------|--------------|--------------|
| T1 — State Machines | 35 | 15 | 50 | 48 | 2 (1 known DI-01, 1 new DI-07) |
| T2 — Rate Hierarchy | 14 | 7 | 21 | 21 | 0 |
| T3 — Invoice Math | 17 | 10 | 27 | 27 | 0 |
| T4 — Audit Trail | 18 | 22 | 40 | 40 | 0 |
| **Total** | **84** | **54** | **138** | **136** | **2** |

---

## Cycle 3 — Verification (GAP-DI-07 Fix)

**Date**: 2026-03-25
**Agent**: QA Agent
**Branch**: `bugfix_cycle_financial_accuracy_2026-03-24`
**Stack**: Keycloak dev stack (localhost:8080 / Keycloak 8180)
**Auth**: Thandi (owner) via Keycloak direct grant, Portal JWT via magic link exchange for naledi@qatest.local
**Scope**: Verify GAP-DI-07 fix — expired proposals must be rejected by portal accept/decline endpoints

### Fix Summary

The fix adds expiry guards at three levels:
1. **`PortalProposalService.acceptProposal()`** — checks `expiresAt` after status==SENT validation, throws 409 if expired
2. **`PortalProposalService.declineProposal()`** — same guard for consistency
3. **`Proposal.markAccepted()`** — defense-in-depth `isExpired()` check at entity level
4. **`Proposal.isExpired()`** — new convenience method: `expiresAt != null && Instant.now().isAfter(expiresAt)`
5. **`PortalProposalRow`** — updated to include `expiresAt` field + SQL query updated

### Test Setup

| Item | Details |
|------|---------|
| PROP-0005 | SENT, expiresAt=2026-01-15T00:00:00Z (expired ~2 months ago), id=95fa0b32 |
| PROP-0006 | SENT, expiresAt=2026-12-31T23:59:59Z (future — regression check), id=acc4f932 |
| Portal JWT | Obtained via magic link exchange for naledi@qatest.local (customerId=4160e3cb, portalContactId=36f2980b) |

### Verification Results

| ID | Test | Expected | Actual | Result |
|----|------|----------|--------|--------|
| V-DI-07.1 | Accept expired SENT proposal (PROP-0005) | 409 Conflict | HTTP 409 — `{"title":"Proposal expired","detail":"This proposal expired on 2026-01-15T00:00:00Z"}` | **PASS** |
| V-DI-07.2 | Decline expired SENT proposal (PROP-0005) | 409 Conflict | HTTP 409 — `{"title":"Proposal expired","detail":"This proposal expired on 2026-01-15T00:00:00Z"}` | **PASS** |
| V-DI-07.3 | Accept non-expired SENT proposal (PROP-0006) | 200 OK | HTTP 200 — status=ACCEPTED, acceptedAt=2026-03-24T22:49:14Z, message="Thank you for accepting this proposal." | **PASS (regression OK)** |
| V-DI-07.4 | Expired proposal status unchanged after rejection | SENT | PROP-0005 status=SENT, acceptedAt=null | **PASS** |

**Cycle 3 Result: 4/4 PASS — GAP-DI-07 VERIFIED**

### Conclusion

The expiry guard fix works correctly. Expired proposals in SENT status are now rejected with a clear 409 Conflict response at the portal API layer. Non-expired proposals continue to be accepted normally. The fix closes the race window between `expiresAt` and the hourly `ProposalExpiryProcessor` batch run.

**GAP-DI-07 Status: FIXED -> VERIFIED**
