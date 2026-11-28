# ADR-403: POPIA document integration for AI egress

**Status**: Accepted

*Amended 2026-11-28 (architecture review, FD-13): `processing_activities.legal_basis` keeps an s11 lawful-basis code (`contractual_necessity`); the s72 transfer grounds go in `description` and the provider and country in `recipients`.*

**Context**:

A firm that uses AI must be able to show the transfer on its POPIA surfaces: notice to the data subject of the recipients and of a transfer to a third country with its level of protection (s18(1)(g)/(h)), the identity or categories of third parties who had access in a DSAR answer (s23(1)(b), covered by [ADR-402](ADR-402-ai-egress-ledger.md)), and planned transborder flows in its PAIA s51 manual. The engagement letter is also where s72(1)(b) consent can be taken as a second ground beside the binding agreement ([ADR-401](ADR-401-ai-transfer-basis-record.md)), and where the client can say "no AI" (LSSA guidance: such a mandate must be honoured; Legal basis Q4).

At HEAD none of these mention an AI provider or the US:

- **Processing register.** `ProcessingActivity` (`datarequest/ProcessingActivity.java`) has category, description, legal_basis (50), data_subjects, retention_period, recipients (255) and timestamps, **no status or key column**; rows are seeded in Java (`ProcessingActivityService.seedJurisdictionDefaults`, `:71`, seven ZA rows).
- **PAIA manual.** `PaiaManualContextBuilder` (`:48-59`) passes org, information officer, retention policies and `processingActivityRepository.findAll()` to `template-packs/compliance-za/paia-section-51-manual.json` (pack v1, Parts I–VI); no transborder part.
- **Engagement letters.** `template-packs/legal-za/engagement-letter-{general,litigation,conveyancing}.json` (pack v16) embed `clauseBlock` nodes by slug; the document JSON is the source of truth for clause associations ([ADR-123](ADR-123-template-clause-association-source-of-truth.md)), synced to `template_clauses` with a `required` flag (`clause/TemplateClauseSync.java`). Clauses ship in `clause-packs/legal-za-clauses` v3 (incl. `legal-za-data-protection`). Shipped-body edits reach pristine tenants only through pack reconcile entries ([ADR-356](ADR-356-pack-content-reconciliation.md)), guarded by `ClausePackReconcileGuardTest`.
- **Wording.** Tokenised payloads are pseudonymised PI, not de-identified (Q6, POPIA s1, s6(1)(b)). Masking is an s19 safeguard and s10 minimisation, not an exit from s72.

**Options Considered**:

1. **Static copy only: a help page and a sample clause the firm pastes in.**
   - Pros: No code; no pack bumps.
   - Cons: The register and PAIA manual stay wrong; nothing ties what the firm publishes to its actual basis and policy; design partners asked for a provable answer.
2. **An eighth static row in `seedJurisdictionDefaults` and AI text added to the existing `legal-za-data-protection` clause.**
   - Pros: Smallest change to seeding and packs.
   - Cons: The row would claim an AI transfer for firms that never enable AI, and would not exist for tenants already seeded; its recipients and basis could not follow the transfer-basis record; folding AI into the data-protection clause forces it on every letter and edits a shipped body every tenant may have customised.
3. **Render all three documents live from the ledger (actual flows).**
   - Pros: Always matches what happened.
   - Cons: The register and PAIA manual describe *planned* processing, not a log; the ledger is per subject and belongs in the DSAR answer, not in a public manual.
4. **Data-driven integration: a keyed system row in the processing register upserted from the basis and policy, a PAIA transborder part fed from the basis, two new optional clauses wired into the three engagement letters, and a wording lint (chosen).**
   - Pros: Each surface reflects the firm's recorded basis and policy; existing tenants get it through reconcile; optional clauses respect firms that do not want them.
   - Cons: Three pack bumps and their version-pin tests; attorney copy gates release.

**Decision**: Kazi keeps an `AI_ASSISTED_LEGAL_SERVICES` processing-register row keyed by a new `system_key` and synced from the transfer basis and policy, adds a "Transborder flows of personal information" part to the PAIA manual (compliance-za v2) fed from the current basis, ships optional `ai-use-disclosure` and `ai-privacy-notice` clauses (legal-za clause pack v4) referenced by the three engagement letters (legal-za v17), and fails the build on "anonymised / de-identified / POPIA-exempt" wording in AI, egress and MCP copy.

**Rationale**:

*Register.* V38 adds nullable unique `processing_activities.system_key` and `active` boolean default true (no status field exists). When AI or MCP is first enabled, or the basis is recorded, the row is upserted: recipients "<provider_legal_entity> (<provider_country>)" from the basis (A1, default "Anthropic PBC (United States)"); `legal_basis` an s11 lawful-basis code (`contractual_necessity`) — the column is `varchar(50)` holding s11 codes, and s72 is a transfer ground, not a processing basis (FD-13); `description` naming the purpose, the categories actually sent under the policy in force, and the s72 grounds from the basis. It re-syncs on policy or basis change; when AI is disabled it is marked inactive, never deleted. `PaiaManualContextBuilder` lists active rows only, so a disabled AI row stops appearing as planned processing.

*PAIA.* compliance-za v1 → v2 adds the transborder part (s51(1)(c)/(d)-style): provider, country, s72 grounds, categories, safeguards (masking/tokenising, no training under commercial terms). The builder gains `transborderFlows` from the current basis; the body change carries a reconcile entry.

*Clauses.* `ai-use-disclosure` is the engagement-letter AI clause: what is sent, to whom and where, the safeguards, s18 notice, s72(1)(b) consent, and the client's right to exclude AI (which staff record as the client's `ai_excluded`, [ADR-400](ADR-400-special-pi-children-biometric-blocking.md)). `ai-privacy-notice` is a privacy-notice paragraph. The three engagement letters gain a non-required `clauseBlock` for `ai-use-disclosure`, so the body change needs legal-za v16 → v17 plus sha256 reconcile entries; tenant-edited letters are left alone.

*Lint.* A source-scan test (pattern of `TerminologyRemnantScanTest` and `frontend/__tests__/no-terminology-remnants.test.ts`, [ADR-357](ADR-357-terminology-guard-extension.md)) forbids `/anonymi[sz]ed|de-identified|POPIA-exempt/i` in the frontend AI, egress and MCP directories and in backend egress messages; the DSAR anonymisation surfaces, where "anonymise" is the correct term ([ADR-193](ADR-193-anonymization-vs-deletion.md)), are excluded.

**Consequences**:

- Positive:
  - Register, PAIA manual, engagement letter and DSAR answer all name the same provider, country and basis, from one record.
  - Firms that never enable AI publish nothing about it; firms that do get correct documents without drafting them.
- Negative:
  - Pack bumps re-pin `TemplatePackSeederReconcileTest` (`:196`), `LegalZaTemplateReconcileIntegrationTest` (`:75`), `TemplatePackReconcileIntegrationTest`, `PackReconciliationRunnerTest`, `PackInstallBackfillTest` and the clause-pack tests; `DataProtectionWireContractTest` re-pins for `system_key`/`active` ([ADR-365](ADR-365-wire-shape-contract-pins-compliance.md)).
  - A user's manual edits to the system row are overwritten on the next sync; the UI marks it as managed by Kazi.
  - All clause, notice, PAIA and block copy is a draft until a practising attorney signs it off (RG-8; MCP acknowledgement copy RG-6). Opposing and third parties appear in payloads but never receive the firm's notice (RG-9).
  - The skill-pack docs in `../claude-for-legal-sa` are outside this repo's lint; the wording check there is part of the follow-up PR.
  - The clause is consent evidence only if the client signs the letter; Kazi does not link a signed letter to the client record automatically.
- Related: [ADR-401](ADR-401-ai-transfer-basis-record.md), [ADR-402](ADR-402-ai-egress-ledger.md), [ADR-398](ADR-398-reversible-ephemeral-tokenisation-route-a.md), [ADR-397](ADR-397-one-egress-guard-two-route-adapters.md), [ADR-104](ADR-104-clause-rendering-strategy.md), [ADR-123](ADR-123-template-clause-association-source-of-truth.md), [ADR-356](ADR-356-pack-content-reconciliation.md), [ADR-262](ADR-262-dsar-audit-trail-unsanitised.md); [architecture §19](../architecture/phase94-ai-egress-guard.md); slice 94F.
