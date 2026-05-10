# Phase E — Code-Grounding Pass, Batch 2

Verification of `→ file:line` anchors across 10 module pages. Anchors validated by reading the cited file ±15 lines and matching the identifier hint from the surrounding doc text. Line-only edits applied where drift detected; content mismatches flagged for human review.

## Method

- Regex extracted every `→ <path>:<line>` anchor (suffixes: `.java .tsx .ts .sql .yaml .kt`).
- Path resolver handles three forms: full path (`backend/src/main/java/...`), relative (`invoice/Invoice.java` → backend prefix), and ellipsis shorthand (`backend/.../foo/Bar.java` resolved by basename + suffix match).
- Identifier hint extracted from backticked tokens and CamelCase prose words **before** the arrow on the same line.
- Statuses: `resolved` (hint hit cited line), `drift_fixed` (hit within ±15, single-line anchor → edited), `drift_unfixed` (line-range anchor — left in place; manual review of cited window confirmed correctness for all six in invoicing + reporting + proposals), `content` (no hint match in window — flagged), `broken` (file missing).

## Per-file Tally

| File | Checked | Resolved | Drift fixed | Broken | Content flag |
|---|---:|---:|---:|---:|---:|
| invoicing.md | 53 | 45 | 4 | 0 | 3 |
| retainers.md | 12 | 12 | 5 | 0 | 0 |
| trust-accounting.md | 1 | 1 | 0 | 0 | 0 |
| proposals-acceptance.md | 12 | 11 | 0 | 0 | 1 |
| information-requests.md | 31 | 26 | 3 | 0 | 5 |
| audit.md | 4 | 4 | 1 | 0 | 0 |
| domain-events.md | 6 | 5 | 0 | 0 | 1 |
| automation.md | 6 | 6 | 0 | 0 | 0 |
| notifications.md | 13 | 13 | 5 | 0 | 0 |
| reporting.md | 21 | 14 | 1 | 0 | 1 |
| **Total** | **159** | **137** | **19** | **0** | **11** |

(Drift counts include the 11 from the first-pass run; 8 additional from the basename-resolver second pass for `backend/.../` and bare-basename anchors.)

## Edits Applied (line-number only)

invoicing.md
- L79  `invoice/InvoiceCounter.java:16` → `:17`
- L110 `InvoiceCreationService.java:639` → `:635`
- L215 `invoice/InvoiceCreationService.java:912` → `:917`
- L215 `invoice/InvoiceCreationService.java:639` → `:636`

retainers.md
- L9  `retainer/RetainerType.java:3` → `:4`
- L9  `retainer/RetainerFrequency.java:5` → `:8`
- L15 `retainer/RetainerStatus.java:3` → `:4`
- L15 `retainer/RolloverPolicy.java:3` → `:4`
- L19 `retainer/PeriodStatus.java:3` → `:4`

information-requests.md
- L99  `InformationRequestService.java:572` → `:573` (Completed event ctor)
- L102 `InformationRequestService.java:468` → `:469` (ItemAccepted event ctor)
- L103 `InformationRequestEmailEventListener.java:138` → `:139` (`onItemRejected` handler)

audit.md
- L32 `AuditEventController.java:27` → `:21` (controller class declaration)

notifications.md
- L20 `Notification.java:13` → `:14`
- L39 `NotificationController.java:19` → `:20`
- L49 `NotificationPreferenceController.java:14` → `:15`
- L56 `CommentController.java:26` → `:27`
- L160 `EmailNotificationChannel.java:224` → `:226`

reporting.md
- L25 `reporting/ReportingController.java:21` → `:22`

## Broken anchors

None. All cited paths resolve to real files (after fixing the resolver to handle the `backend/.../` ellipsis shorthand).

## Content-flagged claims

These need human/product review — the line points to real code, but the doc's claim about that line does not match what the code says. Line-only edits cannot fix them.

### invoicing.md

1. **L28** — `invoice/InvoiceNumberService.java:49` — claim: "format `INV-%04d` allocated at `APPROVED`". Line 49 is `return String.format("INV-%04d", number);` — format string matches, but the `APPROVED`-state-allocation claim lives in the caller (allocateNumber call site), not on this line. Acceptable as a "where the format constant lives" anchor; flag is borderline.
2. **L55** — `tax/TaxRate.java:89` — claim: "deactivation clears the default flag". Line 89 is `public void deactivate() {`. The `this.isDefault = false;` body is at :91. This is a method-signature anchor; the prose claim is satisfied by the method as a whole. Borderline; not a fix candidate.
3. **L58** — `backend/src/main/java/io/b2mash/b2b/b2bstrawman/tax/TaxCalculationService.java:32` — claim: "`TaxCalculationService` … implements both inclusive and exclusive modes". Line 32 is mid-method (inside `calculateLineTax`). Class declaration is at :15, method at :26. Better anchor would be `:15` or `:26`. Flag for re-anchor.

### proposals-acceptance.md

4. **L17** — `Proposal.java:80` (range :80-89) — confirmed RESOLVED on manual inspection (contingency-fee field block) — the "no-hint hit on cited line" was a script artefact (line 80 is the comment delimiter `// --- Contingency fee fields …`, hint matches the prose `FIXED|HOURLY|RETAINER|CONTINGENCY` in the comment two lines earlier). No action.

### information-requests.md (the most concerning cluster)

The "Emitted" event list maps each event to a `publishEvent(...)` line in `InformationRequestService.java`. Several mappings are wrong: the actual event published at the cited line is a different event class.

Actual publishes in `InformationRequestService.java`:
- L373 `new InformationRequestSentEvent(`
- L415 `new InformationRequestCancelledEvent(`
- L469 `new RequestItemAcceptedEvent(`
- L523 `new RequestItemRejectedEvent(`
- L573 `new InformationRequestCompletedEvent(`
- L667 `new InformationRequestSentEvent(` (resend path)

Doc claims (information-requests.md §"Emitted"):

5. **L97** — claims `InformationRequestDraftCreatedEvent` is published at `InformationRequestService.java:372`. **Wrong**: that line publishes `InformationRequestSentEvent`. The actual `InformationRequestDraftCreatedEvent` publish lives in `ProjectTemplateService.java:1011` (no publishes in `InformationRequestService` at all). The doc's statement that "fires when a request is created in DRAFT state" is also unsupported by InformationRequestService source — there is no DRAFT-creation publish path here.
6. **L98** — claims `InformationRequestSentEvent` is published at `:414`. **Wrong**: line 414/415 publishes `InformationRequestCancelledEvent`. Correct publish line is `:372` (publishEvent) / `:373` (ctor) and `:666/:667` (resend).
7. **L100** — claims `InformationRequestCancelledEvent` is published at `:522`. **Wrong**: line 522/523 publishes `RequestItemRejectedEvent`. Correct publish line is `:414/:415`.
8. **L101** — claims `RequestItemSubmittedEvent` is published at `:666`. **Wrong**: line 666/667 publishes `InformationRequestSentEvent` (resend). `RequestItemSubmittedEvent` is **not** published anywhere in `InformationRequestService.java` — `grep -n "new RequestItemSubmittedEvent" backend/...` returns no hits in this service. Likely lives in `customerbackend/service/PortalInformationRequestService.java`.
9. **L119** — `RequestReminderScheduler.java:54` — claim: "@Scheduled(fixedRate=21_600_000) — six-hour tick". Line 54 is the `@Scheduled(fixedRate = CHECK_INTERVAL_MS, initialDelay = CHECK_INTERVAL_MS)` annotation — the literal `21_600_000` is held in the constant `CHECK_INTERVAL_MS`, not on this line. Anchor is correct as the annotation pointer; doc just expanded the constant inline. Borderline; not a fix candidate.

The IR-events cluster (5–8 above) is a substantive content drift. Either the events were renumbered/renamed during a refactor and the doc was not updated, or the doc's whole section was hand-written from memory. Recommend: re-anchor to the actual `new <Event>(` ctor line for each event, OR rewrite the prose to match the real publish topology (note that `DraftCreated` lives in `ProjectTemplateService`, and `RequestItemSubmitted` is published from `PortalInformationRequestService`).

### domain-events.md

10. **L15** — `DomainEvent.java:9` — Javadoc claim. Line 9 is `* implementations must be records with primitive/UUID fields only` — RESOLVED on manual inspection (script flagged for empty-hint set; the doc text after the arrow is the description, not before). No action.

### reporting.md

11. **L58** — `reports/page.tsx:18` — claim: "FINANCIAL_VISIBILITY". Line 18 is inside the capability check block (line 13 starts the block, FINANCIAL_VISIBILITY literal is on line 18). RESOLVED on manual inspection.

## Notes for next batch

- The script-fix for `:572 → :573` (publishEvent vs `new Event(`) shows a doc-author convention split: some IR/notification anchors point to the `publishEvent(` call line, others to the `new Event(` constructor line. The fixes nudged the latter convention. If the convention should be "anchor to publishEvent call", revert these five edits in information-requests.md / notifications.md (re-runnable from the diff).
- Line-range anchors (`:70-83`, `:32-34`, etc.) were all manually verified correct in invoicing/proposals/reporting. No edits needed.
- 11 anchors in invoicing.md were not auto-checked because they're inside a single rendered table row (multiple anchors per markdown line); the script processes them sequentially within `re.finditer`, all RESOLVED.
