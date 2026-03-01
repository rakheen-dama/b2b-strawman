# Phase 34 Ideation — Client Information Requests
**Date**: 2026-03-01

## Lighthouse Domain
Accounting/bookkeeping firms (primary). Document collection is the #1 daily friction — "please send me your bank statements" happens via email chains today. Every accounting firm, audit firm, and tax practice needs this. Also critical for legal (court bundles, affidavits) and consultancies (project briefs, contracts).

## Decision Rationale
Accounting sync (Xero/Sage) was previously queued as next after data completeness. Founder questioned whether it's really the highest value — concluded it's not. Accounting sync is an integration (moves data elsewhere), not a capability (changes what the product does). High maintenance, narrow audience. Client Information Requests is used **every engagement**, drives portal adoption, and eliminates the #1 email friction.

Accounting sync pushed to near-market phase (late in roadmap).

### Key Design Choices
1. **Dedicated entity, not checklist extension** — checklists are internal (firm verifies), requests are external (client provides). Different lifecycle, audience, portal experience.
2. **Review cycle** — client uploads → firm accepts/rejects → re-upload if rejected. Ensures quality, catches wrong/incomplete documents.
3. **Customer OR project scoped** — covers both onboarding docs (customer-level) and engagement-specific docs (project-level).
4. **Interval-based reminders** — every N days while items outstanding. No due date required. Simpler, works universally.
5. **Draft on project creation** — project template references request template → draft auto-created. Firm reviews/customizes before sending. Automation without losing control.
6. **Two response types** — FILE_UPLOAD + TEXT_RESPONSE. Covers document collection and simple information gathering. No confirmation type (acceptance handles that).

## Founder Preferences (Confirmed)
- Review cycle (not upload-equals-done) — quality matters for professional documents
- Draft-on-creation (not auto-send) — firm retains control per engagement
- Files + text responses — eliminates email for simple questions alongside document requests
- Accounting sync explicitly deprioritized — "probably to the end"

## Phase Roadmap (Updated)
- Phase 33: Data Completeness & Prerequisite Enforcement (spec written)
- **Phase 34: Client Information Requests** (spec written)
- Phase 35: Resource Planning & Capacity
- Phase 36: Bulk Billing & Batch Operations
- Phase 37: Workflow Automations v1
- Phase N (late): Accounting Sync (Xero + Sage)

## Estimated Scope
~5-6 epics, ~12-16 slices. Builds heavily on existing infrastructure (portal, S3 uploads, email delivery, scheduled jobs, project templates, notifications). New entities: RequestTemplate, InformationRequest, RequestItem. Portal gets its first interactive page (upload + text submission).
