# Day 15 — Trust Client Onboarding (Moroka Family Trust)

**Date**: 2026-05-15
**Branch**: `bugfix_cycle_2026-05-14`
**Cycle**: Accounting ZA 90-Day Lifecycle (Keycloak)
**Stack**: Keycloak dev (frontend `:3000`, backend `:8080`, gateway `:8443`, portal `:3002`, KC `:8180`, Mailpit `:8025`)
**Actor**: Thandi Thornton (Owner)
**Scenario**: `qa/testplan/demos/accounting-za-90day-keycloak-v2.md` -> Day 15

---

## Pre-Flight

- Logged in as Thandi Thornton (TT initials, thandi@thornton-test.local)
- Stack healthy: frontend :3000 (200), backend :8080 (UP)
- Previous agent had partially completed Day 15 before running out of context

## Prior State (from previous agent)

The previous agent created the Moroka Family Trust client with:
- Name: Moroka Family Trust
- Entity Type: Trust
- Email: trust@moroka-family.co.za, Phone: +27-11-555-0401
- Primary Contact: Sipho Moroka (sipho@moroka-family.co.za)
- Address: 45 Jan Smuts Avenue, Johannesburg, Gauteng, 2196, ZA
- Business Details: Registration IT 2345/2020, Tax 9876543210, Entity Type Trust, Financial Year End Feb 28, 2027
- SARS Tax Reference: 9876543210 (in SA Accounting -- Client Details)
- Field Groups: SA Accounting -- Client Details + SA Accounting -- Trust Details
- Status: Onboarding (Active)
- FICA/KYC: 5/11 completed (5/8 required) -- Certified ID, Proof of Residence, Company Registration, Tax Clearance, Bank Confirmation
- 8 documents uploaded: certified-id.txt, proof-of-residence.txt, company-reg.txt, tax-clearance.txt, bank-confirmation.txt, beneficial-ownership.txt, letters-of-authority.txt, trust-deed.txt
- Client ID: `64f79e3d-46b0-4d4b-b9cc-53d1c3968231`

---

## Checkpoint Results

### 15.1 — Create Client: Moroka Family Trust (acct_entity_type = TRUST)

| ID | Checkpoint | Result | Evidence |
|----|-----------|--------|----------|
| 15.1 | Client created: Name = Moroka Family Trust, Entity Type = TRUST | **PASS** | Client visible in Clients list. ID: `64f79e3d-46b0-4d4b-b9cc-53d1c3968231`. Created by previous agent. Business Details shows Entity Type = Trust. |

### 15.2 — Verify Trust-Specific Custom Fields

| ID | Checkpoint | Result | Evidence |
|----|-----------|--------|----------|
| 15.2a | "SA Accounting -- Trust Details" field group assigned to client | **PASS** | Field group visible in Field Groups section on client detail page. |
| 15.2b | Trust-specific fields surfaced on client detail page | **FAIL** | "SA Accounting -- Trust Details" section header renders but **NO fields appear beneath it**. The section is empty. See screenshot: `day-15/moroka-active-trust-fields-empty.png`. |
| 15.2c | Trust fields exist in Settings > Custom Fields | **PASS** | Settings > Custom Fields > Clients tab shows all 6 trust fields: Trust Registration Number (Required), Trust Deed Date (Required), Trust Type (Required), Names of Trustees, Trustee Appointment Type, Letters of Authority Date. All Active. |
| 15.2d | Visibility condition configured correctly in pack | **PASS** | `accounting-za-customer-trust.json` defines all 6 fields with `visibilityCondition: { dependsOnSlug: "acct_entity_type", operator: "eq", value: "TRUST" }`. Entity type is set to TRUST on this client. |

**Root cause hypothesis**: The frontend custom fields renderer does not evaluate `visibilityCondition` from the field pack definition. The fields are defined with a condition that should show them when `acct_entity_type = TRUST`, but the rendering logic appears to ignore visibility conditions entirely -- resulting in an empty section.

**Impact**: Trust-specific required fields (Trust Registration Number, Trust Deed Date, Trust Type) cannot be filled through the UI. Client Readiness shows "Required fields filled (2/5)" and can never reach 5/5. This blocks any workflow that depends on trust field completeness.

### 15.3 — Fill Trust Fields

| ID | Checkpoint | Result | Evidence |
|----|-----------|--------|----------|
| 15.3 | Fill trust fields (trust registration, trustees, beneficiaries, financial_year_end) | **PARTIAL** | Financial Year End is filled (Feb 28, 2027) via the promoted fields on client creation. Trust Registration Number, Trust Deed Date, Trust Type, Names of Trustees, Trustee Appointment Type, Letters of Authority Date **cannot be filled** because the fields do not render in the "SA Accounting -- Trust Details" section. |

### 15.4 — Complete Onboarding -> ACTIVE

| ID | Checkpoint | Result | Evidence |
|----|-----------|--------|----------|
| 15.4a | Complete remaining FICA/KYC required items | **PASS** | Completed 3 remaining required items with document attachments: Beneficial Ownership Declaration (beneficial-ownership.txt), Letters of Authority/Master's Office (letters-of-authority.txt), Trust Deed/Certified Copy (trust-deed.txt). |
| 15.4b | Skip optional FICA/KYC items | **PASS** | Skipped 3 optional items: Proof of Business Address, Resolution/Mandate, Source of Funds Declaration. |
| 15.4c | FICA/KYC checklist status: Completed | **PASS** | Checklist shows "Completed" -- 8/11 completed (8/8 required). |
| 15.4d | Client transitions to ACTIVE | **PASS** | Auto-activated upon FICA/KYC checklist completion. Clients list shows Moroka Family Trust as Active/Active. Client detail header shows two "Active" badges. |

---

## Summary

| Checkpoint | Result |
|-----------|--------|
| 15.1 Create client | **PASS** |
| 15.2 Verify trust-specific custom fields | **FAIL** (OBS-4006) |
| 15.3 Fill trust fields | **PARTIAL** (blocked by OBS-4006) |
| 15.4 Complete onboarding -> ACTIVE | **PASS** |

**Overall: 2 PASS / 1 FAIL / 1 PARTIAL**

---

## New Gaps

| Gap ID | Summary | Severity | Day |
|--------|---------|----------|-----|
| OBS-4006 | Trust-specific custom fields not rendering on client detail page despite field group assigned and entity_type=TRUST. The "SA Accounting -- Trust Details" section header renders but contains no input fields. All 6 fields have `visibilityCondition` tied to `acct_entity_type = TRUST` in the field pack JSON, but the frontend does not evaluate visibility conditions. Trust-specific required fields (Trust Registration Number, Trust Deed Date, Trust Type) cannot be filled. | MEDIUM | 15 |

---

## Console Health

- 2 errors: both 404s for `/api/assistant/invocations` (AI assistant endpoint, not a product bug -- feature not yet wired)
- 0 warnings (excluding dev-mode HMR/React DevTools)
- No JavaScript errors related to trust field rendering

## Evidence

- `qa_cycle/evidence/day-15/moroka-active-trust-fields-empty.png` -- full page screenshot showing empty Trust Details section
- `qa_cycle/evidence/day-15-trust-details-empty-on-customer.png` -- previous agent screenshot showing same issue
- `qa_cycle/evidence/day-15-trust-fields-empty-settings.png` -- previous agent screenshot showing fields exist in Settings

## Status

- **Day 15 COMPLETE -- 2 PASS / 1 FAIL / 1 PARTIAL. 1 new gap: OBS-4006.**
- Moroka Family Trust created, onboarding completed, auto-activated to ACTIVE.
- Trust-specific custom fields blocked by OBS-4006 (visibility condition not evaluated by frontend).
- Ready for triage of OBS-4006 before Day 16.
