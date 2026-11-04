# ADR-360: Contract parties are explicit matter fields, never `org.*` / `customer.*`

**Status**: Accepted

**Context**:

This ADR records a decision shipped in PR #1718 (merge `ec1ff1d246f277519ff1e37589554963cf7a303e`, 2026-11-04) before Phase 89 started; it is written here so the decision is discoverable. The conveyancing clause pack bound the contractual parties of an Offer to Purchase to the two entities a template always has in context: `clause-packs/conveyancing-za-clauses/pack.json:34` rendered `customer.name` as the party who "accepts the property voetstoots" and `:36` rendered **`org.name`** — the law firm — as the party who "shall not be liable" (G88-F12, HIGH). The OTP template already referenced `project.customFields.seller_name` (`template-packs/legal-za/offer-to-purchase.json:55,179`) but no field pack defined `seller_name` or any purchaser field, so `CustomFieldValidator` stripped the value on save and "Seller:" printed blank. The same-class audit found the Letter of Demand addressed "Dear `customer.name`" — the firm's own client — and the Bond Cancellation Instruction naming the client as mortgagor. The founder decided (requirements decision 5) that OTP parties are explicit matter fields, not inferred from the client, and authorised the same-bug-class cluster and the scenario amendments (14.4 / 14.5 / 14.13).

The constraint is the pack model: pack-seeded content reaches existing tenants only through the [ADR-356](ADR-356-pack-content-reconciliation.md) reconcile, and a firm's edited or cloned rows must survive.

**Options Considered**:

1. **Infer the parties from the client record: purchaser = `customer.*`, seller = the first adverse party, firm = `org.*` only for firm-as-party clauses.**
   - Pros:
     - No new fields; a conveyancing matter already has a client.
   - Cons:
     - Wrong in every direction the walk exercised: the firm can act for the seller, the purchaser, the bank or the estate agent, and "the client" names a different party in each; an adverse party is a conflict-check concept, not a contracting role.
     - Encodes a hidden assumption into legal text; no attorney can see or correct it from the matter form.
2. **A party-role picker on the matter (one dropdown: "our client is the Seller / Purchaser / Mortgagee…") with templates branching on it.**
   - Pros:
     - One field instead of six; the firm states its role once.
   - Cons:
     - The counter-party still has no name anywhere, so the other side of every clause stays blank or inferred.
     - Every clause needs a `conditionalBlock` per role (the renderer supports `eq`/`neq`, `TiptapRenderer.java:445-452`), doubling the clause-pack surface and the attorney-review burden; a wrong pick silently swaps both parties.
     - A new matter-level concept for one pack, in a legal-za-only product ([ADR-330](ADR-330-archive-tag-not-fork.md)).
3. **Explicit, optional matter custom fields for each contracting party, bound by name in clauses and templates; `org.*` kept only where the firm genuinely is the party; delivered to existing tenants by the pack reconcile (CHOSEN).**
   - Pros:
     - The attorney sees and types exactly what the document will print; blank means blank, never "the firm".
     - Uses the field-pack mechanism that already exists: `field-packs/conveyancing-za-project.json` v2 → v3 appends `seller_name`, `seller_id_number`, `purchaser_name`, `purchaser_id_number`, `occupation_date`, `bond_number` to group `conveyancing_za_matter` at sort 11–16 (`:147-187`), all `required: false`.
     - Reconcile is additive: a new `FieldPackSeeder.applyReconcileEntry` step (`FieldPackSeeder.java:57` `MEMBERSHIP_FIELD`, `:170` `createDefinitionInGroup`) creates a missing definition exactly as a fresh install would and never writes, re-groups or deletes an existing row, pack-owned or firm-created (`:255`, `:279` skip logs).
   - Cons:
     - Six more fields on the conveyancing matter form; nothing warns at generation time when a party field is empty (packs cannot declare `requiredContextFields` — follow-up, now R-E3c).
     - A standalone bond cancellation (no sale) has no seller and renders a blank mortgagor (follow-up).
4. **Separate per-role templates (seller-side OTP, purchaser-side OTP, respondent-side notice of motion) instead of fields.**
   - Pros:
     - No conditional logic in clauses.
   - Cons:
     - Multiplies the legal-za template count for every role combination and still needs the counter-party's name from somewhere — it does not remove the need for the fields, only adds templates on top. Retained as the product call for respondent-side litigation (`notice-of-motion`, `founding-affidavit`), where the applicant/respondent split is structural rather than a name swap.

**Decision**: Option 3 — contract parties are explicit matter custom fields; no clause or template infers a contracting party from `org.*` (the firm) or `customer.*` (the firm's client). Concretely: the Voetstoots clause binds `project.customFields.purchaser_name` (`pack.json:39,49`) and `seller_name` (`:41`); occupation-date, suspensive-bond, transfer-duty and sectional-title clauses bind `purchaser_name`; OTP and Deed of Transfer bind purchaser/seller name and ID fields; Bond Cancellation's mortgagor is the seller (`seller_name` / `seller_id_number`, neutral sentence at `bond-cancellation-instruction.json:46`, heading "Mortgagor (Seller)" at `:54`); the Letter of Demand is addressed to the matter's existing `opposing_party` and becomes a matter template (`pack.json:186` `primaryEntityType: PROJECT`). `org.name` stays where the firm is the party: `fica-compliance`, `body-corporate-clearance`, `rates-clearance` (accountable institution / lodging conveyancer), general power of attorney, engagement letters, fee note, SoA, closure letter, trust statement/receipt, Section 35 documents.

**Rationale**:

1. **Legal text must say what the attorney typed.** A clause that names the firm as "not liable" is not a rendering bug; it is a wrong contract. Explicit fields make the binding inspectable in the matter form and in the generated PDF, and put the wording — not the inference — in front of the attorney for review.
2. **Delivery through the reconcile framework, not a migration.** Pack versions bumped conveyancing-za-project 2→3, conveyancing-za-clauses 2→3, legal-za 9→10 (`legal-za-project` unchanged at 3 — the Letter of Demand reuses `opposing_party`; the `debtor_name` field proposed in round 0 was dropped). Clause bodies carry five `from = sha256(v2)` entries; templates carry v9→head content entries with Deed and Bond Cancellation v7 entries re-pointed to the new head (cumulative chain). A new template reconcile field `primaryEntityType` (`TemplatePackSeeder.java:40`, `:209`: `to` must equal the pack's `primaryEntityType`; moves only while the row body is the shipped body, tenant-edited → LEFT_ALONE) moved the Letter of Demand. Clones and tenant-edited rows keep old bindings by design ([ADR-356](ADR-356-pack-content-reconciliation.md)'s one rule). No stored literal changed ([ADR-342](ADR-342-audit-and-entity-tag-strings-stable-under-renamed-wire.md) / [ADR-357](ADR-357-terminology-guard-extension.md)).
3. **Phase 88 lesson — real-write-path tests and browser observation.** `ContractPartyBindingIntegrationTest` renders OTP, Deed, Letter of Demand ("Dear <opposing party>,") and `bondCancellationInstruction_namesTheSellerAsMortgagor_notTheClient` through the generation service; `FieldPackSeederReconcileTest` installs at v2, reconciles to v3 (adds 6), proves a firm-created `bond_number` survives and an equal version is a no-op; `TemplatePackSeederReconcileTest`, `LegalZaTemplateReconcileIntegrationTest` (`V10_EDITED_KEYS`), `ClauseBodyReconcileTest` and `PackInstallBackfillTest` cover the chain. Observed on an existing e2e tenant: six fields at sort 11–16, packs at 3/10/3, Letter of Demand `primary_entity_type = PROJECT`. Playwright `day-14-conveyancing.spec.ts` asserts 16 field labels, fills seller/purchaser at 14.5, "Seller: Johannes van der Merwe" at 14.7 and the Voetstoots purchaser/seller lines at 14.8 — depth-ii 39/0/4 on a fresh stack. Full verify on `0fbc7a1f`: 6371/0/0/26.
4. **The clause text is legal content.** The PR requested attorney review of the 12 re-bound sentences and listed four wording points that are rulings, not key swaps: (a) occupation-date and levies sentences say "the seller" as literal text beside a bound purchaser; (b) the CPA paragraph assumes consumer-buyer / supplier-seller; (c) FICA/clearance clauses keep `org.name`; (d) the Letter of Demand never names the creditor, says "the amount as set out below" but sets nothing out, and renders no opposing-party address. The founder verified the wording on the attorney's behalf on 2026-11-04; the gap is recorded closed on that basis.

**Consequences**:

- Positive:
  - A conveyancing matter with both parties filled renders an OTP whose Seller line and Voetstoots clause name the seller and whose purchaser clauses name the purchaser (G88-F12 closed); scenario 14.4 / 14.5 / 14.13 amended under founder authorisation.
  - Two reusable reconcile capabilities now exist for every later pack edit: additive field-definition membership and `primaryEntityType` moves. Documented in `backend/CLAUDE.md` ("Field additions").
  - Fixture packs `clause-packs/conveyancing-za-clauses-v2/pack.json` and `template-packs/legal-za-v9/*` pin the "from" state for future reconcile tests.
- Negative:
  - No generation-time warning when a party field is empty; `requiredContextFields` exists on `DocumentTemplate` (`:84,320-324`) and is enforced by `GeneratedDocumentService.java:229-234`, but no pack can declare it and the validator does not resolve `project.customFields.<key>` — Phase 89 R-E3c (CodeRabbit Major on #1718).
  - Standalone bond cancellation (no sale) renders a blank mortgagor; a `mortgagor_name` fallback is a product call (backlog C8).
  - Applicant-side `notice-of-motion.json:44,83` and `founding-affidavit.json:44,88` still bind `customer.name` as Applicant and `opposing_party` as Respondent — correct for applicant-side firms; respondent-side variants are a product call (backlog C7).
  - Letter of Demand content gaps (creditor name, amount, opposing-party address) need attorney wording and fields.
  - Firms' clones of the rebound SYSTEM clauses and tenant-edited OTP / Deed / Letter of Demand rows keep the old bindings — intended, and the reason the reconcile logs LEFT_ALONE rather than overwriting.
- Explicitly not done: no party-role picker, no inference fallback from `customer.name` when a field is empty, no new `TemplateEntityType`, no required-field hard block.

**Related**: [ADR-356](ADR-356-pack-content-reconciliation.md) (the delivery mechanism; this PR adds two reconcile capabilities to it), [ADR-342](ADR-342-audit-and-entity-tag-strings-stable-under-renamed-wire.md) / [ADR-357](ADR-357-terminology-guard-extension.md) (stored literals and pack text unchanged in kind), [ADR-353](ADR-353-firm-letterhead-settings.md) (`org.*` keys remain the letterhead, not a party), [ADR-330](ADR-330-archive-tag-not-fork.md), [ADR-344](ADR-344-qa-scenario-amendment-authorisation-phase87.md) (scenario amendment authorisation pattern); `architecture/phase89-go-live-readiness.md` §14.2, §14.3, §14.8, §14.11, §14.12; `qa_cycle/evidence/phase88-rewalk/full/gaps.md` G88-F12.
