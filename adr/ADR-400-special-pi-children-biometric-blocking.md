# ADR-400: Special personal information, children and biometric: blocked by default, unblocked on a structured legal basis

**Status**: Accepted

*Amended 2026-11-28 (architecture review): CHILD is ID-derived **or** a manually ticked matter category (orchestrator correction R4.2); `projects.sensitive_categories` holds the s26 codes plus `CHILD`; templates never prefill CHILD (`project_templates.default_sensitive_categories` excludes it). The former "derived-only" consequence is withdrawn.*

**Context**:

POPIA prohibits processing special personal information (s26: religious or philosophical beliefs, race or ethnic origin, trade union membership, political persuasion, health or sex life, biometric information, criminal behaviour) and children's personal information (s34) unless an authorisation applies (s27, s33 for criminal behaviour and biometric information, s35 for children). The authorisation a law firm usually relies on — necessity for the establishment, exercise or defence of a right or obligation in law (s27(1)(b), s35(1)(b)) — lifts only the s26/s34 prohibition. It does **not** satisfy s72, and s57(1)(d) separately requires the Information Regulator's prior authorisation to transfer special or children's PI to a third party in a foreign country without adequate protection "as referred to in s72"; s58 sets the procedure (once-off, four weeks plus up to thirteen, silence deemed approval). The Regulator has issued no adequacy determination, so whether Anthropic's DPA cures US inadequacy is unsettled (Legal basis Q1/Q3; RG-1).

Kazi must detect these cases deterministically (founder decision 5: no ML, no NER, no keyword scanning). At HEAD there is no sensitivity field anywhere; `Project.workType` (`project/Project.java:127`) is inherited from `ProjectTemplate.workType` (`projecttemplate/ProjectTemplate.java:67`); `Customer` has `idNumber` (`customer/Customer.java:40`) and `CustomerType` INDIVIDUAL/COMPANY/TRUST but **no date of birth**; `AdverseParty` has `idNumber` and links to matters through `AdversePartyLink.projectId`. The legal-za project-template pack (`legal-za-project-templates` v4) has no family or children template. No skill sends images today (every `AiSkill.requiresVision()` returns false; FICA sends an "[Image document: …]" placeholder, `FicaDocumentReader.java:165`), but `AiProvider.completeWithVision` exists.

**Options Considered**:

1. **Keyword or NER detection of special categories and minors in content.**
   - Pros: Catches special PI inside ordinary matters.
   - Cons: Excluded by founder decision 5; false positives block legitimate work and false negatives give a false sense of protection; not explainable to an attorney.
2. **Stored date of birth on clients and parties, or a manual CHILD flag on the matter as the only source.**
   - Pros: Covers passport holders and people without an SA ID.
   - Cons: A new PI field to capture and protect, empty for every existing record; a stored or ticked "child" goes stale as people turn 18, and, as the only source, a forgotten tick silently sends a child's data (so option 3 keeps the tick only as a supplement to ID derivation).
3. **Matter-level s26 flags set by the attorney (prefilled by template), CHILD derived from SA ID numbers at egress time or ticked by the attorney as a matter category, images classed as biometric, all blocked by default, and an owner-only unblock that requires a structured statutory basis (chosen).**
   - Pros: Deterministic and explainable; ID-derived CHILD stays correct as people age, and the manual tick covers minors with no SA ID on record without adding a date-of-birth field; the unblock record captures exactly the facts Q1/Q3 say are needed.
   - Cons: Misses special PI in matters the attorney did not flag and minors without a parseable SA ID (RG-4).
4. **As option 3, but let an owner unblock on a free-text reason.**
   - Pros: Simplest UI.
   - Cons: "Owner say-so" is not a legal basis; founder decision 4 rules it out.

**Decision**: Special categories are flagged per matter (`projects.sensitive_categories`, holding the s26 codes and `CHILD`; template-prefilled via `project_templates.default_sensitive_categories`, which never contains `CHILD`), CHILD applies when the SA ID number of the individual client or of an adverse party linked to the matter shows a minor at egress time **or** the attorney has ticked `CHILD` on the matter, every vision call is `SPECIAL_BIOMETRIC`, and all of them are blocked on both routes unless an owner records an append-only `ai_category_unblocks` grant carrying a `PRIOR_AUTHORISATION` or `ADEQUACY_BINDING_AGREEMENT` basis plus a structured reason, behind `heykazi.egress.special-unblock-enabled=false` until RG-1 clears.

**Rationale**:

*Columns (V38).* `projects`: `sensitive_categories` jsonb array of the 7 s26 codes plus `CHILD` (the manual tick; default `[]`), `ai_excluded`, `ai_excluded_reason`, `ai_excluded_by`, `ai_excluded_at`; `customers`: the same `ai_excluded*` columns; `project_templates.default_sensitive_categories` (CHECK: s26 codes only, never `CHILD`) copied at instantiation. Per the founder-approved adjustment A3, no template prefills CHILD (no such template exists) and LITIGATION and ESTATES prefill nothing, so the attorney ticks; templates may prefill other s26 categories later as a data change.

*CHILD.* `SaIdNumber` parses YYMMDD with the century rule "YY > current YY ⇒ 19YY, else 20YY" and returns age < 18 against the business date (`Clock` / `KaziTime.ZONE`, per `ZoneAwareTimeArchTest`). It applies to INDIVIDUAL clients and to every adverse party linked to the matter. The derivation stores nothing, so no back-fill and no staleness; the matter shows a derived "minor involved" badge. The manual `CHILD` tick is an OR on top of it (minors with a passport only, or named only in documents); the attorney sets and clears it with matter edit access and a clear-reason code (FD-4), and it is never prefilled.

*Images.* `completeWithVision` ⇒ `SPECIAL_BIOMETRIC`. Even when BIOMETRIC is unblocked, the run also needs `SkillExecutionRequest.confirmImageEgress = true`; without it the execution is BLOCKED with `IMAGE_CONFIRMATION_REQUIRED`. The staff confirmation is not data-subject consent (RG-2).

*Unblock record.* `ai_category_unblocks` is append-only: category, action GRANTED/REVOKED, basis `PRIOR_AUTHORISATION` (+ regulator_reference, authorisation_date) or `ADEQUACY_BINDING_AGREEMENT` (+ attestation text), reason_type `DATA_SUBJECT_CONSENT` / `RIGHT_IN_LAW` / `OTHER` + reason_text, document_reference, owner, timestamp. The basis answers s57(1)(d)/s58; the reason answers s27/s33/s35. Scope is tenant-wide per category; per-matter unblocks are out of scope. While the config flag is off the endpoints return 403 with a ProblemDetail explaining why and the UI shows the dialog disabled (FD-5). NO_AI is never unblockable.

*Effect.* Blocked on route A = execution BLOCKED, no provider call, no cost ([ADR-402](ADR-402-ai-egress-ledger.md)); on route B = withheld in place with a marker ([ADR-399](ADR-399-mcp-dto-egress-classification-single-writer.md)). When a category is unblocked, route A sends its content with names and identifiers still tokenised (FD-10) and records it; the same unblock lifts the route B withholding, where identifiers stay masked (one policy, founder decision 1).

*Permissions.* New capability `AI_EGRESS_GOVERN`, OWNER_ONLY, gates the transfer basis ([ADR-401](ADR-401-ai-transfer-basis-record.md)), unblocks and their revocation; it is added to `Capability.OWNER_ONLY` and to `OWNER_ONLY_CAPABILITIES` in `frontend/lib/capabilities.tsx` together, because `frontend/__tests__/capabilities.test.tsx` pins parity ([ADR-355](ADR-355-owner-only-capabilities-client-side.md)). Matter flags: anyone with matter edit access (`ProjectAccessService` `canEdit`); clearing a flag needs the same access plus a reason (FD-4). Client `ai_excluded`: `CUSTOMER_MANAGEMENT`.

**Consequences**:

- Positive:
  - Special, child and image egress is off unless a statutory basis is on record; the record holds what an attorney or the Regulator would ask for.
  - ID-derived CHILD needs no data entry and cannot go stale; the manual tick covers minors without an SA ID on record.
- Negative:
  - Detection limits (RG-4): minors identified only by passport or named in free text, and special PI inside an unflagged matter, are not caught. Copy must never present BLOCK as a guarantee.
  - A manual `CHILD` tick can go stale when the child turns 18 and is only as good as the attorney's diligence; a minor who is neither ID-derived nor ticked is still missed (RG-4, FD-18).
  - The unblock flow ships dark until RG-1; a firm with a genuine need (e.g. a RAF personal-injury practice) cannot use route A on flagged matters until then.
  - Audit: `egress.matter.sensitivity_changed`, `egress.matter.ai_excluded_changed`, `egress.client.ai_excluded_changed`, `egress.category.unblocked`, `egress.category.unblock_revoked` (ids and categories only, `SafeAuditDetails`).
- FD-4, FD-5, FD-10 are founder decisions; RG-1, RG-2, RG-4 are release gates.
- Related: [ADR-397](ADR-397-one-egress-guard-two-route-adapters.md), [ADR-398](ADR-398-reversible-ephemeral-tokenisation-route-a.md), [ADR-268](ADR-268-ocr-via-claude-vision-byoak-no-separate-vendor.md), [ADR-284](ADR-284-document-reading-s3-vision-no-vector-store.md), [ADR-332](ADR-332-single-vertical-profile-mechanism-retained.md); [architecture §19](../architecture/phase94-ai-egress-guard.md); slices 94A (unblocks, capability), 94B (flags, CHILD).
