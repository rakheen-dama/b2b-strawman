# Verification Results — FIXED Gaps (Cycle 1)

Date: 2026-04-14
Verified by: QA Agent
Stack: Keycloak dev (localhost:3000/8080/8443/8180)
Branch: `bugfix_cycle_2026-04-14`
PRs under test: #1031 (backend + frontend), #1032 (frontend only)

---

## PR #1031 Fixes

### GAP-D0-03 (MED) — Create Engagement dialog title: **VERIFIED**

**What was tested:** Navigated to Engagements page as Bob (admin), clicked "New Engagement" button.

**Result:** Dialog title reads "Create Engagement" (not "Create Project"). Dialog description reads "Add a new engagement to your organization." Submit button reads "Create Engagement". The `t("Project")` terminology translation is working correctly for accounting-za profile.

**Evidence:** Accessibility snapshot confirms `heading "Create Engagement"` and `button "Create Engagement"` in dialog.

---

### GAP-D0-05 (MED) — Engagement templates pre-seeded: **VERIFIED**

**What was tested:** Navigated to Settings > Engagement Templates as Bob.

**Result:** All 5 accounting-za engagement templates are pre-seeded and visible:
1. Monthly Bookkeeping — 6 tasks
2. Tax Return — Company (ITR14) — 7 tasks
3. Tax Return — Individual (ITR12) — 7 tasks
4. VAT Return (VAT201) — 5 tasks
5. Year-End Pack (Annual Financial Statements) — 7 tasks

Total: 32 tasks across 5 templates. All show Source = "Manual", Status = "Active".

**Evidence:** Full accessibility snapshot of Settings > Engagement Templates page at `/org/thornton-associates/settings/project-templates`.

---

### GAP-D0-07 (MED) — FICA checklist auto-instantiate on ONBOARDING: **REOPENED**

**What was tested:** Created a new client "QA Test Client (Verify FICA)" as Bob (Individual type, SARS Tax Reference filled, FICA Verified = Not Started). Transitioned from PROSPECT to ONBOARDING via Change Status > Start Onboarding. Checked Onboarding tab for auto-instantiated FICA checklist.

**Result:** Onboarding tab shows "No checklists yet." The FICA checklist was NOT auto-instantiated.

**Root cause:** The pack.json fix (`autoInstantiate: true`) is correct in source, but the pack reconciliation runner skips already-seeded packs ("Compliance pack fica-kyc-za already applied for tenant ..."). The existing checklist template record in the database still has `autoInstantiate=false` from the original seed before the fix.

**Backend log evidence:**
```
ChecklistInstantiationService: "Instantiated 0 checklist(s) for customer 20d339c5-... (type=INDIVIDUAL, typedAvailable=false)"
CustomerLifecycleService: "Customer 20d339c5-... lifecycle transitioned from PROSPECT to ONBOARDING by actor ... (checklistsInstantiated=0)"
```

**Fix needed:** Pack reconciliation must update existing template settings when pack.json changes (currently it only creates, never updates). Alternatively, a one-time SQL update or a reconciliation flag bump could force re-application.

---

## PR #1032 Fixes

### GAP-D0-01 (LOW) — Access requests page tab switching: **REOPENED**

**What was tested:** Logged in as platform admin (padmin@docteams.local). Navigated to Access Requests page. Default tab "Pending" is selected. Clicked "All" tab, then "Approved" tab.

**Result:** "Pending" tab remains permanently selected (bold text + teal border indicator). The empty state message continues to say "No pending access requests" regardless of which tab is clicked. The `onValueChange` callback from Radix TabsPrimitive.Root does not appear to fire.

**Evidence:** Screenshot saved at `qa_cycle/checkpoint-results/gap-d0-01-tabs-broken.png` showing "Pending" still active after clicking "Approved". Accessibility snapshot also confirms `tab "Pending" [selected]` after clicking other tabs.

**Code review:** PR #1032 correctly replaced `motion.span` layoutId with CSS-only `border-b-2 data-[state=active]:border-teal-500` (line 73 of `access-requests-table.tsx`). However, the underlying Radix Tabs state management is not functioning — this may be a hydration issue where the client component is not properly hydrating, preventing React state updates from `onValueChange`.

---

### GAP-D0-04 (LOW) — Trust fields hidden for non-Trust entity type: **VERIFIED**

**What was tested:** Opened Create Client dialog (as Bob during GAP-D0-07 testing). Selected "Individual" type in Step 1. Advanced to Step 2.

**Result:** Step 2 shows "SA Accounting — Client Details" group with SARS Tax Reference and FICA Verified fields. Trust-specific field groups (those with "trust" in their slug) are NOT displayed for non-Trust entity types.

**Code confirmation:** `create-customer-dialog.tsx` line 221-225:
- `form.watch("customerType")` reactively watches the selected type
- `intakeGroups.filter((g) => !g.slug.includes("trust"))` filters out trust groups when type != TRUST

**Evidence:** Accessibility snapshot of Step 2 dialog during client creation; code review of the filtering logic.

---

### GAP-D36-03 (LOW) — Customer invoices tab 500 errors: **VERIFIED**

**What was tested:** Navigated to Kgosi Holdings customer detail > Invoices tab as Thandi (owner). Checked console messages and network requests for 403/500 errors.

**Result:** Zero console errors, zero console warnings, zero failed network requests. The TrustBalanceCard SWR fetch is correctly gated — no trust_accounting API calls are made for this tenant.

**Code confirmation:** `TrustBalanceCard.tsx` line 51-59:
- `isModuleEnabled("trust_accounting")` checks module status via `useOrgProfile()`
- SWR cache key set to `null` when module disabled, preventing fetch entirely

**Evidence:** Console messages output showing 0 errors/warnings; network request filter for "trust|403|500" returned empty.

---

## Summary

| GAP ID | PR | Severity | Previous Status | New Status | Verdict |
|--------|-----|----------|----------------|------------|---------|
| GAP-D0-03 | #1031 | MED | FIXED | VERIFIED | Dialog says "Create Engagement" |
| GAP-D0-05 | #1031 | MED | FIXED | VERIFIED | 5 templates, 32 tasks seeded |
| GAP-D0-07 | #1031 | MED | FIXED | REOPENED | Pack reconciliation skips updates |
| GAP-D0-01 | #1032 | LOW | FIXED | REOPENED | Tabs still don't switch |
| GAP-D0-04 | #1032 | LOW | FIXED | VERIFIED | Trust fields filtered correctly |
| GAP-D36-03 | #1032 | LOW | FIXED | VERIFIED | No console errors on invoices tab |

**4 VERIFIED, 2 REOPENED**
