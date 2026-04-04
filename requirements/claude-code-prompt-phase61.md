# Phase 61 — Legal Compliance Refinements: Section 86 Investment Distinction & KYC Verification Integration

## System Context

Phase 60 builds the full trust accounting system — trust ledger, client ledger cards, bank reconciliation, interest calculation, and investment register. However, the investment model treats all investments identically. The Legal Practice Act, 2014 distinguishes two legally distinct investment types with different interest treatment rules. This phase closes that compliance gap.

**The existing infrastructure (from Phase 60)**:

- **TrustInvestment entity**: Tracks money placed on interest-bearing deposit per client. Fields: principal, institution, account number, interest rate, deposit/maturity dates, interest earned, status lifecycle (ACTIVE → MATURED → WITHDRAWN). Linked to trust transactions for deposit/withdrawal.
- **InterestService**: Daily balance method interest calculation with LPFF allocation. Uses `LpffRate.lpff_share_percent` (configurable per trust account, updated when LPFF publishes new circulars) to split interest between client credit and LPFF.
- **LpffRate entity**: Rate history table with `effective_from`, `rate_percent`, `lpff_share_percent`. Per trust account.
- **TrustTransaction types**: INTEREST_CREDIT (to client), INTEREST_LPFF (to Fund).

**The problem**: Section 86 of the Legal Practice Act defines two distinct investment mechanisms:

- **Section 86(3)**: The firm invests surplus trust money *of its own accord* when it's not immediately needed. The interest earned on these investments follows the general LPFF arrangement (configurable rate from `LpffRate` table).
- **Section 86(4)**: The firm invests *on client instruction* in a separate account for that specific client. Section 86(5) prescribes that interest must be paid to the client, with **exactly 5% going to the LPFF** — this is a statutory rate, not the general LPFF arrangement rate.

The current model doesn't distinguish between these. A firm investing R100,000 from the general trust pool (86(3)) and R100,000 on a specific client's instruction (86(4)) would calculate LPFF share identically using the configurable rate. This is incorrect — the 86(4) investment must use the statutory 5%.

An auditor reviewing the Section 35 certificate would flag this as non-compliant.

**The fix**: Add an `investment_basis` enum to `TrustInvestment`, and make the interest service apply the correct LPFF share based on the investment type.

## Objective

1. **Investment basis distinction** — Add `investment_basis` field to `TrustInvestment`: `FIRM_DISCRETION` (Section 86(3)) or `CLIENT_INSTRUCTION` (Section 86(4)). Default to `FIRM_DISCRETION` for backward compatibility with any investments created in Phase 60 before this migration runs.

2. **Statutory 5% LPFF share on 86(4) investments** — When calculating interest on `CLIENT_INSTRUCTION` investments, the LPFF share is fixed at 5% (per Section 86(5)), regardless of the `LpffRate.lpff_share_percent` configured on the trust account. This is hardcoded because it's a statutory requirement, not a firm-configurable policy.

3. **Investment register report distinction** — The Investment Register report (from Phase 60) should display the investment basis and, for 86(4) investments, show the statutory 5% LPFF rate rather than the general rate.

4. **Frontend investment form update** — The "Place Investment" dialog should ask whether the investment is firm-initiated (86(3)) or client-instructed (86(4)), with a brief explanation of the distinction.

5. **Section 86(6) approved bank warning** — Optional soft validation: when creating a trust account or investment, display an informational note reminding the firm that the bank must have an LPFF arrangement (Section 86(6)). This is a UI-only advisory — not a hard block, since the system has no way to verify the bank's LPFF arrangement status.

## Constraints & Assumptions

- **Phase 60 must be complete** before this phase starts. This phase extends Phase 60 entities, not replaces them.
- **Backward compatibility**: Existing `TrustInvestment` rows (created in Phase 60) default to `FIRM_DISCRETION`. No data loss.
- **The 5% is statutory, not configurable.** It's defined in Section 86(5) of the Legal Practice Act. Hardcode it as a constant (`STATUTORY_LPFF_SHARE_PERCENT = 0.05`). Do not put it in the `LpffRate` table or make it admin-editable.
- **No new entities.** This phase modifies `TrustInvestment` (add column) and `InterestService` (conditional logic). No new tables.
- **Migration**: V86 — ALTER TABLE trust_investments ADD COLUMN investment_basis.

---

## Section 1 — Data Model Changes

### 1.1 TrustInvestment Extension

Add one column to `trust_investments`:

| Column | Type | Notes |
|--------|------|-------|
| `investment_basis` | `VARCHAR(20)` | Enum: `FIRM_DISCRETION`, `CLIENT_INSTRUCTION`. Default: `FIRM_DISCRETION`. NOT NULL. |

**Migration V86**:
```sql
ALTER TABLE trust_investments
    ADD COLUMN investment_basis VARCHAR(20) NOT NULL DEFAULT 'FIRM_DISCRETION';

ALTER TABLE trust_investments
    ADD CONSTRAINT chk_investment_basis
    CHECK (investment_basis IN ('FIRM_DISCRETION', 'CLIENT_INSTRUCTION'));
```

### 1.2 Constant

```java
public static final BigDecimal STATUTORY_LPFF_SHARE_PERCENT = new BigDecimal("0.05"); // Section 86(5)
```

Defined in `InterestService` or a shared `TrustAccountingConstants` class.

---

## Section 2 — Interest Calculation Changes

### 2.1 Investment Interest Split Logic

When `InterestService` calculates interest on a `TrustInvestment`:

**Current (Phase 60)**:
```
lpff_share = gross_interest × lpffRate.lpff_share_percent
client_share = gross_interest - lpff_share
```

**New (Phase 61)**:
```
if investment.investment_basis == CLIENT_INSTRUCTION:
    lpff_share = gross_interest × 0.05  // Section 86(5) statutory rate
else:
    lpff_share = gross_interest × lpffRate.lpff_share_percent  // General arrangement
client_share = gross_interest - lpff_share
```

### 2.2 InterestAllocation Extension

The `InterestAllocation` entity (or a related record) should capture which rate was used:
- For `FIRM_DISCRETION`: the `lpff_rate_id` from the `LpffRate` table (as today)
- For `CLIENT_INSTRUCTION`: null `lpff_rate_id` + a note field or flag indicating the statutory 5% was applied

This ensures the audit trail records WHY that specific percentage was used.

---

## Section 3 — API Changes

### 3.1 Investment Endpoints

`POST /api/trust-accounts/{accountId}/investments` — Add `investmentBasis` to the request body (required, enum: `FIRM_DISCRETION` | `CLIENT_INSTRUCTION`).

`GET /api/trust-investments/{id}` — Response includes `investmentBasis` field.

`GET /api/trust-accounts/{accountId}/investments` — List response includes `investmentBasis` for filtering.

### 3.2 Interest Endpoints

No endpoint changes. The interest calculation service internally reads the investment basis and applies the correct rate. The `InterestAllocation` response already includes `lpff_share` — it just calculates differently now.

---

## Section 4 — Frontend Changes

### 4.1 Place Investment Dialog

Add a radio button or select field:
- **"Investment initiated by"**:
  - "Firm (surplus trust funds)" — maps to `FIRM_DISCRETION`, Section 86(3)
  - "Client instruction" — maps to `CLIENT_INSTRUCTION`, Section 86(4)
- Below the selector, display a brief help text:
  - For FIRM_DISCRETION: "Interest follows your firm's LPFF arrangement rate."
  - For CLIENT_INSTRUCTION: "Interest paid to client, with 5% to the LPFF (Section 86(5))."

### 4.2 Investment Register Page

Add an "Investment Basis" column showing "Firm" or "Client Instruction" badges. Filterable.

### 4.3 Interest Allocation Table

For 86(4) investments, show "5% (statutory)" in the LPFF rate column instead of the general rate percentage.

### 4.4 Section 86(6) Advisory Note

On the Trust Account creation/edit form and the Place Investment dialog, add an info callout:
> "The bank must have an arrangement with the Legal Practitioners Fidelity Fund (Section 86(6)). Contact the LPFF to verify."

This is purely informational — no validation logic.

---

## Section 5 — Report Changes

### 5.1 Investment Register Report

Add `Investment Basis` column. Group or filter by basis type.

### 5.2 Section 35 Data Pack

The Section 35 combined report should separately list:
- 86(3) investments (firm-initiated) with general LPFF rate
- 86(4) investments (client-instructed) with statutory 5% rate

This is how auditors expect to see it — the two categories have different compliance obligations.

---

---

## Section 6 — KYC Verification Integration (BYOAK)

### 6.1 Context

Law firms are "accountable institutions" under FICA and must verify the identity of every client during onboarding. Phase 14 built a checklist engine with compliance packs (FICA KYC). Currently, identity verification is manual — the firm checks the client's ID document themselves and marks the checklist item as complete.

This section adds an optional, firm-configured KYC verification integration via the existing BYOAK (Bring Your Own API Key) infrastructure from Phase 21. The firm brings their own credentials for a supported KYC provider. If configured, the FICA checklist shows a "Verify ID" button that calls the provider's API and records the result. If not configured, nothing changes — manual verification remains the default.

### 6.2 Supported Providers

**VerifyNow** (primary):
- SA identity verification platform, connects to Home Affairs HANIS database
- REST JSON API with API key authentication
- Verifies SA ID numbers, Smart ID cards, passports against government sources
- Returns: verified/not_verified/needs_review, photo, demographics, reason codes
- Key endpoints: `POST /verifications` (submit), `GET /verifications/{id}` (poll result), webhook for async
- Pricing: credit-based, from R2.99/credit (data-only) to R29.90/credit (ID + photo match). No monthly minimum.
- POPIA compliant, SA-hosted data

**Check ID SA** (lightweight pre-check):
- SA ID number format validation and checksum verification
- Does NOT verify against Home Affairs — only structural validation
- REST API, base URL: `https://api.checkid.co.za/`, Bearer token auth
- Returns: birth date, age, gender, citizenship status, validity
- Pricing: Free (10 lifetime), R99/month (100), R299/month (unlimited + API)
- Zero data storage, POPIA compliant
- Useful as a fast pre-check before the more expensive Home Affairs lookup

### 6.3 Integration Architecture

Uses the existing Phase 21 BYOAK pattern:

```
IntegrationDomain: KYC_VERIFICATION
Adapters: VerifyNowKycAdapter, CheckIdKycAdapter

SecretStore keys:
  - "kyc:verifynow:api_key"
  - "kyc:checkid:api_key"
```

**New interface**: `KycVerificationPort`

```java
public interface KycVerificationPort {
    KycVerificationResult verify(KycVerificationRequest request);
}

public record KycVerificationRequest(
    String idNumber,
    String fullName,
    String dateOfBirth,     // optional, for cross-check
    String idDocumentType   // SA_ID, SMART_ID, PASSPORT
) {}

public record KycVerificationResult(
    KycVerificationStatus status,  // VERIFIED, NOT_VERIFIED, NEEDS_REVIEW, ERROR
    String providerName,
    String providerReference,      // transaction ID from provider
    String reasonCode,             // provider-specific reason code
    String reasonDescription,      // human-readable explanation
    Instant verifiedAt,
    Map<String, String> metadata   // provider-specific extra data (photo URL, demographics)
) {}
```

**Adapter implementations**:
- `VerifyNowKycAdapter` — Calls VerifyNow's `POST /verifications` endpoint, polls or uses webhook for result. Maps response to `KycVerificationResult`.
- `CheckIdKycAdapter` — Calls Check ID SA's `GET /{id}` endpoint. Maps format validation result. Always returns `NEEDS_REVIEW` (not `VERIFIED`) because it cannot confirm identity against Home Affairs.
- `NoOpKycAdapter` — Default when no integration configured. Returns a result indicating manual verification is required. Used by the guard service to determine whether to show the "Verify ID" button.

### 6.4 FICA Checklist Integration

The existing FICA checklist (Phase 14) has checklist items for identity verification. This section wires the KYC integration into the checklist flow:

**When the firm has a KYC integration configured**:
1. The "Verify Identity" checklist item shows a **"Verify Now"** button alongside the manual "Mark Complete" option
2. Clicking "Verify Now" opens a dialog:
   - Pre-filled with the client's ID number (from `id_passport_number` custom field)
   - Pre-filled with the client's name
   - Shows which provider will be used (VerifyNow or Check ID SA)
   - "Verify" button triggers the API call
3. Result displayed in the dialog:
   - **VERIFIED** (green): ID confirmed against Home Affairs. Checklist item auto-completed with verification details (provider, reference, timestamp).
   - **NOT_VERIFIED** (red): Verification failed. Reason shown. Checklist item remains incomplete. Firm can retry or do manual verification.
   - **NEEDS_REVIEW** (amber): Result inconclusive (e.g., Check ID SA can only do format validation). Firm must still do manual verification but the pre-check result is recorded.
   - **ERROR** (grey): API call failed (network, auth, credits exhausted). Error message shown. Firm falls back to manual verification.
4. Verification result stored on the checklist item:
   - `verificationProvider`: "verifynow" | "checkid" | null
   - `verificationReference`: provider transaction ID
   - `verificationStatus`: VERIFIED | NOT_VERIFIED | NEEDS_REVIEW
   - `verifiedAt`: timestamp
   - `verificationMetadata`: JSONB (provider-specific data)

**When NO KYC integration configured**:
- No "Verify Now" button appears
- Manual verification flow unchanged (mark complete with notes)
- Zero UI change from today

### 6.5 Data Model Changes

**ChecklistItem extension** (existing entity, Phase 14):

Add columns to `checklist_items`:

| Column | Type | Notes |
|--------|------|-------|
| `verification_provider` | `VARCHAR(30)` | Nullable. Provider used for automated verification. |
| `verification_reference` | `VARCHAR(200)` | Nullable. Provider transaction/reference ID. |
| `verification_status` | `VARCHAR(20)` | Nullable. Enum: `VERIFIED`, `NOT_VERIFIED`, `NEEDS_REVIEW`. |
| `verified_at` | `TIMESTAMPTZ` | Nullable. When automated verification completed. |
| `verification_metadata` | `JSONB` | Nullable. Provider-specific result data. |

**Migration V86 extension**: Add these columns in the same V86 migration as the `investment_basis` column.

### 6.6 POPIA Consent

VerifyNow requires the enterprise partner to obtain explicit consent from the data subject before verification. The verification dialog must:

1. Display a consent notice: "By proceeding, you confirm that [Client Name] has given explicit written consent for identity verification against government databases, as required by POPIA and FICA."
2. Require the firm user to check a consent acknowledgement checkbox before the "Verify" button becomes active.
3. Record that consent was acknowledged (timestamp + user who clicked) in the `verification_metadata` JSONB.

The actual written consent from the client is the firm's responsibility (part of their FICA onboarding forms). The system records that the firm user confirmed consent was obtained, not the consent itself.

### 6.7 Endpoints

```
POST   /api/kyc/verify                — trigger KYC verification (body: { customerId, idNumber, fullName, idDocumentType })
GET    /api/kyc/result/{reference}    — poll verification result (for async providers)
GET    /api/integrations/kyc/status   — check if KYC integration is configured (returns { configured: boolean, provider: string })
```

**Authorization**: `MANAGE_LEGAL` capability (same as FICA checklist operations).

**Module gating**: KYC verification is gated behind a feature flag (`IntegrationGuardService.isConfigured("KYC_VERIFICATION")`), not a vertical module. An accounting firm doing FICA onboarding should also be able to use it — this is not legal-specific.

### 6.8 Settings UI

In Settings → Integrations, add a **"KYC Verification"** integration card (follows existing integration card pattern from Phase 21):

- Card shows: provider name, status (configured/not configured), credit balance (if provider supports it)
- "Configure" button opens dialog: select provider (VerifyNow / Check ID SA), enter API key, "Test Connection" button
- API key stored via `SecretStore` (encrypted)
- "Remove" button clears the integration

### 6.9 Audit Events

| Event Type | Trigger |
|------------|---------|
| `KYC_VERIFICATION_INITIATED` | Firm user clicks "Verify" with consent acknowledged |
| `KYC_VERIFICATION_COMPLETED` | Provider returns result (VERIFIED, NOT_VERIFIED, NEEDS_REVIEW) |
| `KYC_VERIFICATION_FAILED` | Provider returns error (network, auth, credits) |
| `KYC_INTEGRATION_CONFIGURED` | Firm configures KYC provider in settings |
| `KYC_INTEGRATION_REMOVED` | Firm removes KYC provider |

---

## Out of Scope

- **Approved bank list verification** (Section 86(6)): The system cannot verify whether a bank has an LPFF arrangement. This is an operational check the firm performs. The advisory note is sufficient.
- **Section 86(7) arrangement compliance**: The terms of the bank-LPFF arrangement are contractual, not software-enforceable.
- **Changes to the general trust account interest model**: The configurable `LpffRate` table and general interest calculation are unchanged. Only investment interest is affected.
- **VerifyNow face match / liveness detection**: Only ID number verification in v1. Face match (ID + photo comparison) is a future enhancement once the basic integration is proven.
- **Automatic FICA checklist completion**: The KYC result informs the checklist but does not auto-complete the entire FICA process. Only the identity verification item is affected.
- **Bulk verification**: One client at a time. Batch verification for migrating existing clients is not in scope.
- **Check ID SA as sole FICA verification**: Check ID SA only validates ID format, not identity. It's a pre-check that always returns `NEEDS_REVIEW`. Firms relying solely on Check ID SA must still do manual verification.

## ADR Topics

1. **ADR: Statutory vs configurable LPFF share** — Why the 86(4) 5% rate is hardcoded as a constant rather than stored in the rate table. The risk of making a statutory rate admin-editable vs. the maintenance cost when the statute changes (which would require a code change, but this is a primary legislation change — it hasn't changed since 2014 and any change would require parliamentary process).

2. **ADR: KYC provider adapter strategy** — Why BYOAK (firm brings own API key) over platform-managed credentials or reseller model. How the `KycVerificationPort` interface abstracts provider differences. Why Check ID SA returns `NEEDS_REVIEW` rather than `VERIFIED` (format validation ≠ identity verification).
