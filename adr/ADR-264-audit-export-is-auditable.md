# ADR-264: Audit Export is Itself an Audited Action

**Status**: Accepted

**Context**:

Phase 69 introduces CSV and PDF export of the audit log to firm administrators with the `TEAM_OVERSIGHT` capability. Exports are streamed; CSV is unbounded, PDF is capped at 10 000 rows ([ADR-263](ADR-263-audit-pdf-via-tiptap-pipeline.md)). Both are filtered the same way the on-screen list is filtered — date range, actor, event type, entity, severity.

An audit-log export is a significant action. It produces a portable artefact containing the firm's audit trail — potentially with internal notes, IP addresses, and operator justifications visible. The recipient could be a regulator, a subpoena responder, a compliance auditor, opposing counsel, the firm's own legal counsel, or simply an admin pulling a report for a board meeting. In rare cases, an export could be the precursor to a data-disclosure incident (a malicious insider exporting before exfiltrating).

The audit log records who did what across every other domain in the codebase. The question is whether it should record itself — specifically, whether running an export should emit an audit event of its own, completing the recursive loop ("did anyone export the audit log? when? with what filter?").

**Options Considered**:

1. **Silent exports.** Exports happen, no audit event is written. The act of exporting is not itself a recorded action.
   - Pros:
     - Marginally simpler implementation. No reflexive write step in the export controller.
     - Avoids the recursive feel of "the audit log audits itself."
     - The tenant's CloudWatch logs might capture the request anyway (HTTP access log) for incident-investigation purposes.
   - Cons:
     - An auditor or regulator who asks "has anyone extracted the firm's audit data, when, and with what scope?" cannot answer that question from the firm's primary audit trail. The HTTP access log is not the audit log; it lives in a different system, with a different retention policy, and is not surfaced through the audit UI.
     - Compliance posture is weak. Most compliance frameworks (SOC 2, ISO 27001, POPIA) expect the audit log to record privileged actions on the audit log itself.
     - A malicious or curious admin can extract the firm's audit history with no in-system trace. The act would be invisible to the next admin reviewing the audit log.

2. **Emit a reflexive audit event with filter / rowCount / format (CHOSEN).** On every successful export (CSV or PDF), the controller writes an `audit.export.generated` event with `details.{filter, rowCount, format}`. The event is emitted *after* the export completes (so failed exports don't pollute the log). Capability gate is unchanged — `TEAM_OVERSIGHT` is required for both the export and the read of the export event.
   - Pros:
     - Compliance posture is strong. The audit log records its own disclosures. An auditor investigating "did anyone extract our data?" can answer it from the log itself.
     - Matches industry expectation for privileged actions on audit infrastructure.
     - Implementation is minimal: one `AuditEventBuilder.builder()...build()` call after the export completes successfully.
     - The reflexive event is itself auditable. An admin running an export, then later running another export, sees both — and notices if a colleague ran one in between.
     - The event integrates cleanly with existing surfaces — it appears in the global audit-log page filtered as Compliance / NOTICE, contributes to the dashboard widget if it crosses to WARNING territory in some future classification, and shows up in DSAR exports for any subject whose data was exported.
   - Cons:
     - The audit log grows by one row per export. At the rates Kazi tenants would plausibly export (a handful per month per tenant), this is invisible.
     - The reflexive nature can feel recursive — "the audit log records the audit-log export, which is also exported, which is also recorded…" — but that recursion terminates: the second export's event is captured by the third export, etc.
     - The `details.filter` field captures the filter as a structured map. If a future filter expansion adds new fields, the captured shape evolves; readers must tolerate variation.

3. **Require step-up auth before export AND emit the event.** Combine Option 2 with a re-authentication challenge before the export proceeds. The user must re-enter their password (or pass an MFA challenge) to export.
   - Pros:
     - Strongest possible posture — combines audit recording with friction that discourages casual or accidental exports.
     - Matches some financial-services and healthcare frameworks' expectation for privileged data exports.
   - Cons:
     - Significant UX friction for what is, for most firms, a routine action. Compliance officers running monthly reports would re-auth every time.
     - Step-up auth is not currently part of Kazi's auth model. Introducing it is a phase-sized addition.
     - The friction does not actually prevent malicious exports — a malicious insider has already authenticated and has the capability; re-entering their password is not a meaningful barrier.
     - Phase 69 is a read-side phase; introducing step-up auth here is scope-expansion that conflicts with [ADR-259](ADR-259-audit-ui-read-only-no-write-changes.md).
     - Step-up auth is the right primitive for a *future* phase (e.g. Phase 71 — sensitive-action confirmation), where it can be designed coherently and applied to multiple high-risk operations (export, role escalation, data anonymisation, etc.). Bundling it here forecloses that phase's design space.

**Decision**: Option 2 — emit a reflexive audit event with filter / rowCount / format.

**Rationale**:

The audit log's purpose is to record privileged actions on tenant data. Exporting the audit log is itself a privileged action on tenant data — arguably one of the most privileged, since it produces a portable artefact that travels outside the system. Recording this action in the log is consistent with the log's own design intent.

The compliance argument is decisive: an auditor asking "has anyone extracted our audit data, and with what scope?" must be answerable from the audit log itself. Otherwise the log has a blind spot precisely where it should be most observant. SOC 2 CC7.2 (event logging), ISO 27001 A.12.4.1, and POPIA Condition 7 (Security Safeguards) all expect privileged-action logging on audit infrastructure; emitting `audit.export.generated` satisfies that expectation cleanly.

Step-up auth (Option 3) is the right primitive for a *future* phase that designs sensitive-action confirmation across the codebase. Bundling it into Phase 69 forecloses that future phase's design space and adds friction without adding much real protection — a malicious insider with `TEAM_OVERSIGHT` has already cleared whatever bar matters. The audit *recording* is the deterrent and the forensic mechanism; step-up auth is a separate concern.

The reflexive nature is occasionally pointed at as awkward, but it is just the natural result of treating the audit log as data that the system also records actions on. It terminates: the export's event is captured; if that export is itself later exported, *that* export emits a new event; and so on. The recursion is finite at every step.

Note that this ADR is the *one* write-path change in Phase 69. [ADR-259](ADR-259-audit-ui-read-only-no-write-changes.md) frames the phase as read-only, with the gap report as the mechanism for surfacing write-side gaps in *adjacent* domains. The `audit.export.generated` event is not a gap-fix in an adjacent domain — it is the new domain itself ("audit-log export"), introduced by this phase, and as such is in scope for being audited from day one. The discipline of [ADR-259](ADR-259-audit-ui-read-only-no-write-changes.md) is "don't fix existing write gaps;" it is not "ship new behaviour without auditing it."

**Consequences**:

- Positive:
  - Audit log records its own disclosures. The "did anyone export our audit data?" question is answerable from the audit UI itself.
  - Compliance posture aligns with SOC 2 / ISO 27001 / POPIA expectations for audit-on-audit-infrastructure.
  - Implementation is minimal — one `AuditEventBuilder.builder()...build()` call in each export endpoint, after the export completes.
  - The reflexive event integrates with all the existing Phase 69 surfaces: it's filterable by the Compliance preset (`group=COMPLIANCE`), it can show up in the dashboard widget if classification crosses to WARNING, and it appears in DSAR exports for affected subjects.
  - An admin reviewing the log sees their own past exports — useful for "what did I export last quarter and with what filter?" recall.

- Negative:
  - One extra row in the audit log per successful export. At realistic export volumes (low single digits per month per tenant), this is invisible.
  - The `details.filter` shape captures the request filter as a structured map; future filter expansions evolve that shape. Readers must tolerate variation. The event-type metadata registry classification (NOTICE / Compliance) is stable.
  - The export endpoint is the one new write path introduced by Phase 69. This is a deliberate exception to [ADR-259](ADR-259-audit-ui-read-only-no-write-changes.md)'s "no new write paths" framing — see Rationale.

- Neutral:
  - The reflexive event is captured by `audit.export.generated` (singular event type). Both CSV and PDF exports emit the same event type; `details.format` distinguishes.
  - The event carries `details.rowCount` so a reviewer can spot anomalously large exports without re-running the filter.
  - Failed exports (e.g. 413 PDF cap exceeded) do not emit the event — the rationale being that no artefact left the system, and the failed-attempt is captured in the HTTP access log if needed for incident investigation. This is a deliberate boundary: only successful disclosures are recorded as disclosures.

- Related: [ADR-029](ADR-029-audit-logging-abstraction.md) (`AuditService` + `AuditEventBuilder` — the abstraction this ADR builds on), [ADR-259](ADR-259-audit-ui-read-only-no-write-changes.md) (the read-only-scope discipline this ADR carves a deliberate exception to), [ADR-262](ADR-262-dsar-audit-trail-unsanitised.md) (the DSAR pack — also a privileged disclosure that emits an audit event via the existing Phase 50 mechanism), [ADR-263](ADR-263-audit-pdf-via-tiptap-pipeline.md) (the PDF pipeline this ADR composes with — the export-emission step runs after the PDF is fully streamed, not at start).
