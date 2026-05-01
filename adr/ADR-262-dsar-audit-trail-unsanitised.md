# ADR-262: DSAR Audit-Trail Export is Unsanitised

**Status**: Accepted

**Context**:

Phase 50 introduced Data Subject Access Requests (DSARs) under POPIA. `DataExportService` produces a ZIP pack containing the data subject's `customer.json`, their `projects/`, `invoices/`, `documents/`, and so on — a structured snapshot of the records the firm holds about them. Phase 69 extends this pack with a new top-level folder `audit-trail/` containing the subject's audit events as `events.json`, `events.csv`, and a `README.txt`.

The audit events for a customer include rows that surface internal operational data: IP addresses of firm staff who took actions, user agents, internal notes embedded in `details` (e.g. matter-closure overrides include the operator's free-text justification verbatim, which may discuss the client's circumstances candidly), and references to internal entities (other matters at the firm that the customer was discussed in). Some of these fields would be sanitised — redacted, replaced with neutral placeholders, or omitted — when surfaced through the customer portal (per [Phase 68](../architecture/phase68-portal-redesign-vertical-parity.md)'s portal-side sanitisation rules, established in [ADR-254](ADR-254-portal-description-sanitisation.md)). The portal is a *live* channel: the client sees what the firm is currently working on, and the firm has discretion over how to phrase that.

A DSAR is a different beast. Under POPIA §23, a data subject is entitled to a copy of their personal information held by a responsible party — and "personal information" is defined broadly enough that internal notes about the data subject, IP addresses recorded in connection with actions about them, and full audit metadata are within scope. POPIA does not contemplate sanitisation as a default; redaction is permitted only where another legal basis (third-party privacy, legal privilege, etc.) overrides the §23 entitlement, and even then must be justified per redaction.

The architectural question: should the DSAR audit-trail folder follow Phase 68's portal sanitisation rules, follow the unsanitised internal record, or land somewhere in between?

**Options Considered**:

1. **Sanitise to mirror portal rules.** Apply the same sanitisation pipeline used in Phase 68's portal — strip internal notes, redact IP/UA fields, mask references to other matters, replace internal status enums with client-facing labels. The DSAR pack contains the same view of the customer's history that the portal would show.
   - Pros:
     - Single sanitisation pipeline. Same rules, same code path, no two-flavours-of-redaction-policy.
     - Lower risk of inadvertently disclosing third-party information embedded in internal notes (e.g. a justification that mentions another client).
     - Consistent client-facing posture: "what we show you live is what we'd give you in an export."
   - Cons:
     - Wrong as a matter of law. POPIA §23 entitles the subject to their *full* record; sanitising a DSAR pack to a portal-equivalent view under-delivers the entitlement and exposes the firm to a §23-violation finding.
     - Sanitisation is opinionated — Phase 68's rules are designed for a *live, ongoing relationship* (don't show the client today's draft of an invoice; don't show them an internal status flag that means "stalled"). Those rules don't generalise to "the full record under POPIA."
     - A subject who later requests their information under POPIA after a Phase 68-style sanitised export would receive a *more* complete record than the export they just downloaded — confusing and legally awkward.

2. **Full unsanitised record (CHOSEN).** The DSAR pack contains the full audit trail with all fields intact — internal notes, IP addresses, user agents, raw `details` JSON. The pack is delivered under the existing DSAR fulfilment flow with the same legal posture as the rest of the pack (which already contains internal `customer.json`, internal projects/invoices/documents data).
   - Pros:
     - Correct as a matter of law. POPIA §23 is satisfied: the subject receives the full record about them.
     - Consistent with the rest of the DSAR pack. `customer.json` already includes internal customer-facing data; the audit trail aligns with that posture.
     - One sanitisation policy, applied where it makes sense (the live portal — Phase 68). The DSAR pack does not get a second policy layer.
     - The DSAR fulfilment process already has a legal review touchpoint (per Phase 50) — if a specific export needs partial redaction (third-party privacy, privilege), that is handled at the fulfilment-review stage, not as an automatic pipeline rule.
   - Cons:
     - Higher disclosure surface. Internal notes that mention third parties are exposed unless flagged at fulfilment review.
     - The `details.justification` field on a `matter.closure.override_used` event might be candid in ways the firm would not say aloud to the client (e.g. "Client returned funds — we suspected fraud but couldn't prove it"). That is awkward but legally correct: under POPIA, the subject is entitled to internal opinions held about them.
     - IP addresses of firm staff are exposed. This is a minor staff-privacy concern; mitigated by the existing internal expectation that audit metadata is recordable and disclosable.

3. **Tiered redaction — mask `ipAddress` and `userAgent` but keep the `details` JSON intact.** A middle ground: scrub the operational metadata fields that don't add value to the data subject (IP addresses, user agents) but keep the substantive content (`details` including justifications, who did what when).
   - Pros:
     - Reduces staff-privacy exposure (firm staff IP addresses stay internal).
     - Preserves the legal substance of the disclosure.
     - Modest implementation cost — a single field-mask in the export step.
   - Cons:
     - Introduces a sanitisation policy where one didn't exist, and the policy line is arbitrary. Why mask IP but not user-agent string? Why not also mask the `source` field? Each field becomes a small policy decision.
     - POPIA §23 does include connection metadata as part of "personal information processed in connection with the data subject" — masking IP/UA is a redaction that must be justified. The tiered approach is harder to defend than either "everything" or "nothing."
     - A future audit of the DSAR pack contents has to reason about which fields are present and why — added cognitive load relative to "the full record."

**Decision**: Option 2 — full unsanitised record.

**Rationale**:

POPIA §23 is the determining constraint, not aesthetics. The subject is entitled to their full record; the firm's discretion to redact is narrow and case-specific (third-party privacy, legal privilege), not policy-default. Phase 50's fulfilment workflow already has a legal-review touchpoint where case-specific redactions are applied — that is the correct surface for redaction, not an automatic sanitisation pipeline.

Phase 68's portal sanitisation ([ADR-254](ADR-254-portal-description-sanitisation.md)) and DSAR sanitisation are different problems. The portal is a live channel where the firm controls the relationship and shapes the client's view of ongoing work — sanitisation there is appropriate. A DSAR is a one-time regulatory disclosure under a specific statute that grants the subject access to the full record; sanitising it would under-deliver the statutory entitlement.

The contrast is the architectural point. Same `audit_events` data; two channels; two sanitisation postures. The portal channel is sanitised because that's correct for live engagement; the DSAR channel is unsanitised because that's correct under POPIA. This ADR exists to make that asymmetry explicit and intentional, so a future engineer doesn't "fix the inconsistency" by sanitising the DSAR pack.

The tiered approach (Option 3) is a tempting middle, but it pretends to a precision the law doesn't grant. Either the field is in scope of §23 (answer: yes, all of them are when they pertain to actions about the subject) or it isn't. Drawing a line at "let's mask IPs but not user-agents" is policy invention without legal grounding.

**Consequences**:

- Positive:
  - POPIA §23 compliance is straightforward: the subject receives their full record. Audit posture is defensible.
  - Implementation is minimal: `DataExportService.buildAuditTrail` writes the `audit_events` rows directly to the ZIP. No sanitisation pipeline, no field-mask step.
  - The legal-review touchpoint at DSAR fulfilment (existing from Phase 50) handles case-specific redactions — third-party privacy, privilege — rather than a blunt automatic policy.
  - Single sanitisation policy in the codebase (Phase 68 portal). Adding a DSAR sanitisation policy was rejected; nothing to maintain.

- Negative:
  - Internal notes embedded in `details` (e.g. matter-closure justifications) are disclosed verbatim. Operators writing such notes should treat them as future-disclosable to the client. The DSAR pack `README.txt` includes a brief explanation of what the audit-trail folder contains and why it is unsanitised.
  - Firm staff IP addresses are visible in the export. Acceptable per existing conventions; staff are aware that audit metadata is recordable and disclosable.
  - Operators occasionally write candid notes about clients ("suspected fraud, couldn't prove it") that would be awkward to disclose. Mitigation: training, and the legal-review touchpoint at DSAR fulfilment.

- Neutral:
  - The decision applies only to the DSAR pack's `audit-trail/` folder. Other DSAR-pack contents (`customer.json`, `projects/`, etc.) follow whatever sanitisation policy Phase 50 set for them, which is also unsanitised per [ADR-196](ADR-196-pre-anonymization-export-storage.md).
  - The portal-side sanitisation policy ([ADR-254](ADR-254-portal-description-sanitisation.md)) is unaffected — Phase 68's rules continue to govern the live portal channel.
  - If a future phase introduces granular subject-side controls ("show me only my own actions, not staff actions about me"), that is a UI-level filter on top of the unsanitised record, not a re-sanitisation.

- Related: [ADR-259](ADR-259-audit-ui-read-only-no-write-changes.md) (Phase 69 read-only scope — this ADR is about the DSAR integration's data-disclosure posture), [ADR-195](ADR-195-dsar-deadline-calculation.md) (Phase 50 DSAR deadline rules — same regulatory context), [ADR-196](ADR-196-pre-anonymization-export-storage.md) (Phase 50 export storage — the existing pack already contains unsanitised internal data, this ADR aligns the audit-trail folder with that posture), [ADR-254](ADR-254-portal-description-sanitisation.md) (Phase 68 portal sanitisation — the explicit contrast: portal sanitises, DSAR does not), [ADR-264](ADR-264-audit-export-is-auditable.md) (the DSAR fulfilment is itself audited via `audit.export.generated`).
