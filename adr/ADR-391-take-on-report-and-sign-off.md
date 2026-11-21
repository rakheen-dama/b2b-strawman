# ADR-391: The trust take-on is signed off by a partner or director and reported in an immutable snapshot

**Status**: Accepted

**Context**:

The requirements (E5) gate the trust commit on "a partner or director has signed off the take-on (Rule 54.19 places responsibility on them)" and require a take-on report generated at commit and stored immutably — the cutover-date Rule 54.15.1-style creditor list by group, control totals, the investments register, the unidentified-deposit items, the sign-off and the source file references — exportable as PDF and CSV. It is the artefact the firm hands its trust auditor (under the IRBA Guide, auditors test opening balances) and Kazi's introduction to auditors.

At HEAD Kazi has no notion of a partner or director: members have an org role (owner, admin, custom) and capabilities; owner and admin receive every capability in code, admin excluding `OWNER_ONLY` (`OrgRoleService.java:68-75`); custom roles and member overrides may grant any capability. `GeneratedDocument` needs a `DocumentTemplate` id and a PROJECT / CUSTOMER / INVOICE / ORGANIZATION scope and is not immutable (setters, `linkToDocument`). Report exports render via Thymeleaf and `htmlToPdf` and are streamed, not stored. `TrustMonthlyListService.liveList(accountId, asAt)` produces the Rule 54.15 list content at any date; `lock()` requires a completed reconciliation. Phase 92's R-2 lesson: trust reports had been gated by `FINANCIAL_VISIBILITY` instead of `VIEW_TRUST` until FU-2.

**Options Considered**:

1. **Sign-off by the owner role only.**
   - Pros: No new capability; owner is the firm's principal in Keycloak.
   - Cons: Many firms have several partners or directors but one Keycloak owner; a partner who is an admin could not sign; conflates "who administers the Kazi tenant" with "who is responsible in law".
2. **A member attribute "partner/director" managed by the owner.**
   - Pros: Models the concept directly.
   - Cons: A new member field and admin UI that only take-on reads; Kazi still cannot verify it; duplicates what the capability system already does.
3. **Any holder of `APPROVE_TRUST_PAYMENT` or `MANAGE_TRUST` signs.**
   - Pros: Reuses existing capabilities.
   - Cons: Those are delegated to bookkeepers routinely; signing the opening position is a different responsibility.
4. **A new owner-only, grantable capability `SIGN_OFF_TRUST_TAKE_ON` plus a typed, versioned Rule 54.19 declaration bound to the validated batch hash (chosen).**
   - Pros: Owner holds it by default; admin does not inherit it, so delegation is a deliberate act (a custom "Partner" role or a member override); the declaration records what the signer affirmed; binding to the hash means a changed batch needs a new sign-off.
   - Cons: Kazi still trusts the firm's assignment; one more capability in the role editor.

For storage of the report:

5. **A `GeneratedDocument` with a new entity type and template.**
   - Pros: Reuses downloads and S3 handling.
   - Cons: Needs a firm-editable `DocumentTemplate` for a statutory-style report the firm must not edit; a new `TemplateEntityType`; the row is mutable.
6. **Regenerate on demand from the books (like other reports).**
   - Pros: Nothing stored.
   - Cons: The report would change when the books change (a later posting dated at the cutover is refused, but reallocations, anonymisation and code changes are not); the auditor's artefact must not move.
7. **An `@Immutable` snapshot row written in the commit transaction, with PDF and CSV rendered from it and stored with SHA-256 (chosen).**
   - Pros: The content is fixed at the moment the position is posted and provably unchanged (hash); files can be re-rendered byte-stable if lost; no firm-editable template.
   - Cons: A new table pair and a render job; the PDF arrives a few seconds after the commit; a fixed layout the firm cannot brand beyond the header.

**Decision**: The trust position commit requires a live `TRUST_SIGN_OFF` by a member holding the new owner-only, grantable `SIGN_OFF_TRUST_TAKE_ON` (plus `VIEW_TRUST`), recording the declaration `TAKEON-SIGNOFF-v1` with the member's name and role snapshot and the batch hash; and the take-on report is an `@Immutable` `take_on_reports` snapshot built inside the commit transaction from the posted rows (`liveList` at the cutover for each account, the investments, unidentified items, outstanding items, both sides of the reconciled position, control totals, flags, sign-off and source files), rendered to PDF (classpath Thymeleaf → `htmlToPdf`) and CSV after the commit by the take-on drain job (and retried by its sweep) into `take_on_report_files` with SHA-256, and readable only with `VIEW_TRUST`.

**Rationale**:

Kazi cannot know who is a partner or director, so the design makes the firm decide explicitly and records what the signer affirmed: "I am a partner or director … responsible for its trust accounting records (Rule 54.19) … the trust creditors listed agree, to the cent, with the reconciled trust position in the previous system … and no trust creditor is in debit." The capability sits in `OWNER_ONLY` beside `FINANCIAL_RECONCILE` and `OVERRIDE_MATTER_CLOSURE` — admin does not inherit it — and is grantable like `MCP_WRITE` (ADR-321). A sole practitioner who is the owner can upload, sign and commit alone, and both the uploader and the signer are printed on the report; when they are the same member the report says "Prepared and signed off by the same person" (FD-31). The signer must be able to read what they sign without being given the take-on screens: `GET /api/take-on/sign-off-queue` and `GET /api/take-on/batches/{id}/sign-off-review` (position inputs, equation summary, blocks, flags, paged rows) are gated by `SIGN_OFF_TRUST_TAKE_ON`, with `VIEW_TRUST` checked in the service because `@RequiresCapability` takes a single value (`RequiresCapability.java:21`), so a custom "Partner" role holding only those two capabilities can review and sign. Such a signer never enters the Take-on tab, which stays gated by `MANAGE_TAKE_ON` alone (the settings nav takes one capability per tab, `frontend/lib/settings-nav.ts:27`): a home widget "Trust take-on awaiting your sign-off" lists the queue and opens a sign-off sheet that uses only the four signer endpoints — the queue, the review, `POST …/sign-off` and the signer's own `DELETE …/trust-sign-off` (FD-36). The sign-off binds to the validation hash and is revoked by a re-validation that changes it, under the same batch lock, so what is committed is what was signed. Who the firm may designate is founder decision FD-4.

The report is built in the commit transaction, after the self-check and with the accounts locked, so it describes exactly the posted position and cannot include a concurrent posting. Its creditor list *is* `liveList(G, C)` — the same code a month-end lock freezes — so the take-on report and the first Rule 54.15 list are comparable line for line; it is not a `TrustMonthlyList` because those require a completed reconciliation and there is none at cutover. The snapshot is immutable and hashed; the files are rendered after commit (a PDF render inside the locked transaction would hold off postings for seconds), are re-renderable from the snapshot if lost, are never overwritten, and are stamped from `generated_at` so a re-render is byte-stable. It is gated by `VIEW_TRUST`, the gate of the monthly lists, and deliberately not served through `ReportingController` (`FINANCIAL_VISIBILITY`), the Phase 92 R-2 defect. Rendering uses the trust-report route (Thymeleaf template on the classpath) rather than a Tiptap document template because the report is fixed, not firm-editable.

**Consequences**:

- Positive:
  - A signed, hashed, immutable artefact for the auditor, comparable with the first monthly list.
  - Responsibility is recorded per Rule 54.19 without Kazi pretending to know firm structure.
  - Correct gate from day one.
- Negative:
  - Firms with partners who are admins must grant the capability; until they do, only the owner can sign.
  - A new capability in the role editor and its owner-only test; two sign-off read endpoints, a signer withdrawal endpoint, a home widget and a sign-off sheet, each endpoint with a 403 test for a member holding the sign-off capability without `VIEW_TRUST`.
  - The report reflects the books at commit; later corrections (transfers between clients) appear only in later lists, by design.
  - The declaration's wording awaits attorney sign-off (FD-4); a change is a new statement version.

**Related**: [ADR-382](ADR-382-monthly-trust-creditor-list-locking.md) (the monthly list and `liveList`), [ADR-321](ADR-321-mcp-write-tool-category.md) (capability grants), [ADR-386](ADR-386-trust-opening-entries-in-the-registry.md) (what is signed), [ADR-385](ADR-385-take-on-architecture.md) (sign-off rows and hash); [architecture §17.2.4, §17.2.7, §17.3.9, §17.3.12, §17.9](../architecture/phase93-client-trust-take-on.md); founder decisions FD-4, FD-15, FD-31, FD-36; slices 657B, 657C.
