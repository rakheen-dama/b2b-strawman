# ADR-251: Acceptance-Eligible Template Manifest Flag

**Status**: Accepted

**Context**:

Phase 28 introduced lightweight e-signature via the `AcceptanceRequest` entity: a firm user sends a document for client acceptance, the client opens a portal page with a magic-link token, reviews the document, and clicks "Accept" (with an optional typed signature and metadata capture). Phase 28's UX assumes any document can be sent for acceptance — the "Send for Acceptance" action is available on every generated document.

In practice, only a subset of documents are natural acceptance targets. Legal engagement letters, offers to purchase, powers of attorney, and proposals are documents where client acceptance is the intended workflow. Compliance certificates, internal ledger prints, monthly retainer reports, and closure letters are informational — sending them for acceptance makes no sense and adds friction to the UI by presenting the action on every document.

Phase 67 introduces conveyancing document templates, two of which (offer to purchase, power of attorney) are natural acceptance targets — a conveyancer sends the draft offer to the buyer or the power of attorney to the transferor for signature. Phase 67 also introduces the closure letter (informational, never acceptance-eligible) and the Statement of Account (informational, never acceptance-eligible). Pack content now has a mix of acceptance-intended and non-acceptance documents.

Without signalling which templates are acceptance-eligible, the product has two bad options: show "Send for Acceptance" on every generated document (cluttering the UX with meaningless actions for 70% of cases), or hard-code acceptance eligibility into the frontend (brittle, requires code changes to flag new templates, and doesn't generalise across packs).

**Options Considered**:

1. **Frontend hard-codes a list of acceptance-eligible template slugs** — a TypeScript constant maps template slugs to acceptance-eligibility; the "Send for Acceptance" button renders conditionally.
   - Pros:
     - Works immediately, no backend changes
     - Engineering-owned list prevents accidental misconfiguration by pack authors
   - Cons:
     - Requires a frontend code change for every new acceptance-eligible template, including customer-owned templates that bypass the manifest entirely
     - Doesn't generalise — the same signal is needed on the firm-side "Send for Acceptance" button, the portal-side acceptance page eligibility check, and potentially automated rules; each would duplicate the list
     - Customer-cloned templates lose the eligibility signal when cloned
     - Poor separation of concerns — pack content decisions leaking into frontend code

2. **Add an optional `acceptanceEligible: boolean` field to template-pack manifest entries** — a new field in the manifest JSON; defaults to `false` when omitted. The `PackInstaller` propagates the flag to the `DocumentTemplate` entity as a new column. Firm-side and portal-side UI check the flag to decide whether to show the acceptance action. Cloned templates inherit the flag by default but can override it via the clone UI.
   - Pros:
     - Pack-content concern stays in pack content (where it belongs)
     - Single source of truth — manifest flag → entity column → both firm and portal UI
     - Cloned templates inherit eligibility; users can toggle it explicitly on custom templates via the template editor
     - Generalises beyond conveyancing — the legal engagement letter, proposal templates, and any future acceptance target uses the same flag
     - Backward-compatible — existing packs without the field default to `false` (no behaviour change for informational templates that were getting a button they shouldn't)
   - Cons:
     - Schema change to `DocumentTemplate` (one nullable/default-false boolean column)
     - Pack authors must remember to set the flag for new acceptance-intended templates — mitigated by code review + documentation

3. **Infer acceptance eligibility from template content** — scan the Tiptap document for signature-block nodes; presence implies acceptance-eligible.
   - Pros:
     - Zero manifest change — the signal is implicit in the template content
     - Customer-edited templates that remove the signature block correctly stop being acceptance-eligible
   - Cons:
     - Fragile — a template with a signature block for internal use (e.g., a firm-internal sign-off letter) would be mis-flagged
     - Silent failure modes — a template that should have a signature block but doesn't renders without the "Send for Acceptance" action with no user-visible error
     - Couples UX behaviour to document-content details that pack authors don't think of as semantic
     - Requires every client-side check to parse Tiptap JSON — performance and complexity cost

**Decision**: Option 2 — add an optional `acceptanceEligible: boolean` field to the template-pack manifest entry schema; propagate to a new `DocumentTemplate.acceptanceEligible` column.

**Rationale**:

**Pack-content concerns belong in pack manifests.** The template-pack manifest schema is the canonical place for pack authors to declare metadata about the templates they ship: display name, variable requirements, clause bindings, now acceptance eligibility. Extending the schema is the conventional move; inventing a new signal channel (hard-code, content scan) is overhead for a problem the manifest is designed to solve.

**Single source of truth.** The `DocumentTemplate` entity is the runtime representation read by firm-side template pickers, the portal-side acceptance page eligibility check, and future automation rules that might condition on "is this template an acceptance target?" Carrying the flag on the entity (sourced from the manifest at install) gives every consumer one column to read.

**Generalises beyond Phase 67.** The legal engagement letter (already in `legal-za` pack) has been an implicit acceptance target for some time; Phase 67 formalises it. Proposal templates (Phase 32), acceptance-driven procurement documents (future), and any future signed-off artefact benefits from the same flag. The flag is not conveyancing-specific.

**Clone semantics sensible.** When a user clones a system template, the `acceptanceEligible` flag clones with it. Users editing custom templates can toggle the flag (no-op for non-acceptance templates, meaningful for acceptance-intended ones). Removing hard-code lists as a concern removes a source of orphan flags in code.

**Minimal migration surface.** One nullable boolean column on `DocumentTemplate`, default `false`. Migration is trivial; existing templates without the flag continue to render without the "Send for Acceptance" button, which is the correct default for the vast majority of templates.

**Acceptance-UI decoupling.** The `AcceptanceRequest` path (Phase 28) is untouched. All that changes is which templates surface the "Send for Acceptance" action in the firm-side template picker and document-actions menu. The acceptance request can still be created manually against any document via API if a user has an edge case requiring it; the flag only controls UI default surfacing.

**Consequences**:

- New field in the template-pack manifest JSON schema: `acceptanceEligible: boolean` (optional, default `false`). Documented in the pack-authoring guide with the Phase 67 conveyancing pack as canonical example usage.
- New column on `DocumentTemplate`: `acceptance_eligible boolean not null default false`. Migration in Phase 67 schema work (V96 or V97 — integrated with the disbursement or closure migration rather than its own migration to keep the count down). Note: if this conflicts with migration sequencing, separate migration `V98` is acceptable.
- `PackInstaller` reads the manifest flag on install and propagates to the entity field. Existing packs without the flag install with `acceptanceEligible = false`, preserving current behaviour.
- Phase 67 `legal-za` pack updates: engagement letter template set to `acceptanceEligible: true`; offer-to-purchase and power-of-attorney templates set to `acceptanceEligible: true`; closure letter, monthly retainer report, deed of transfer, bond cancellation instruction set to `acceptanceEligible: false` or omitted.
- Proposal templates (Phase 32) are retroactively flagged — a small migration-free update to proposal template manifests + a data patch setting `acceptance_eligible = true` on existing proposal `DocumentTemplate` rows where applicable. Implementation chooses the best path (pack re-install, admin endpoint, or targeted SQL in Phase 67 migration).
- Firm-side template picker surfaces (e.g., the "Send for Acceptance" dialog template dropdown) filter to `acceptanceEligible = true` templates.
- Document-actions menu on a generated document shows "Send for Acceptance" only when `document.templateAcceptanceEligible = true` (joined from the originating template). Users can still initiate acceptance manually via API for edge cases if their capability allows.
- Portal-side Phase 28 acceptance page remains unchanged in its acceptance logic — the portal has no opinion on which templates are acceptance-eligible; it processes whatever acceptance request is sent to it.
- Template editor UI gains a toggle to set `acceptanceEligible` on custom templates (owner/admin only, under template settings).
- Cloned templates inherit the flag by default.
- Related: [ADR-244](ADR-244-pack-only-vertical-profiles.md), [ADR-247](ADR-247-legal-disbursement-sibling-entity.md), [ADR-250](ADR-250-statement-of-account-template-and-context.md), Phase 28 acceptance ADR (cross-reference at implementation).
