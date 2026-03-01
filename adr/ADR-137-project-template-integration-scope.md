# ADR-137: Project Template Integration Scope — Draft on Creation

**Status**: Accepted

**Context**:

Phase 34 allows project templates to reference a request template, so that when a project is created from the template (manually, via proposal acceptance, or via recurring schedule), an information request is automatically prepared. The key decision is whether the system should auto-send the request immediately to the client, or create it as a DRAFT for the firm to review before sending.

This integration point connects three systems: project templates (defining the engagement structure), information requests (collecting documents from clients), and the portal (where clients receive and respond to requests). The behavior at this intersection affects both firm workflow and client experience.

**Options Considered**:

1. **Auto-send immediately** -- When a project is created from a template with a request template reference, the system creates the information request and immediately sends it to the client's primary portal contact.
   - Pros:
     - Zero friction — no manual step required from the firm
     - Client receives the request as soon as the engagement begins
     - Fully automated end-to-end flow (especially powerful with proposal acceptance)
   - Cons:
     - No opportunity to customize items for the specific engagement before sending
     - Wrong portal contact may be selected (primary contact is not always the right person for document collection)
     - If project creation has errors (wrong template, test creation, etc.), the client receives an unintended request
     - Cannot add engagement-specific ad-hoc items before sending
     - No human review of what the client will see
     - Risk of sending requests to clients who are not yet ready (e.g., project created before client relationship is fully established)

2. **Draft for review** -- Create the information request in DRAFT status. Firm member reviews, optionally customizes items and selects the correct portal contact, then manually sends.
   - Pros:
     - Firm retains control over what the client sees and when
     - Portal contact can be changed from the default (primary) to the correct person
     - Ad-hoc items can be added for engagement-specific needs
     - Items can be removed if not applicable to this engagement
     - Reminder interval can be adjusted before sending
     - Safe by default — no unintended client communication
     - In-app notification alerts the firm that a draft needs attention
   - Cons:
     - Requires a manual step (firm must review and send the draft)
     - Draft may be forgotten if firm doesn't act on the notification
     - Slightly slower time-to-client for the information request

3. **Configurable per template** -- Project template has a flag: "auto-send" or "create as draft." Each template can choose its behavior.
   - Pros:
     - Maximum flexibility per engagement type
     - Firms that trust their templates can auto-send; others can review
   - Cons:
     - More complex configuration UI (another toggle to explain)
     - Auto-send still carries the risks from Option 1
     - Firms may set auto-send without understanding the implications
     - Testing matrix doubles (must test both paths)

**Decision**: Option 2 -- Draft for review.

**Rationale**:

The draft approach is the correct default because information requests are client-facing communications. Unlike internal project setup (tasks, documents), an information request directly impacts the client experience. Sending the wrong items, to the wrong contact, at the wrong time creates a poor first impression and generates unnecessary support overhead.

The common case in professional services is that each engagement has nuances: an annual audit for Client A may need different supporting documents than the same engagement type for Client B. A request template provides a starting point, but the firm typically needs to adjust items, add engagement-specific requests, or select a different portal contact (e.g., the client's bookkeeper rather than the director). The draft gives them this adjustment window.

The risk of drafts being forgotten is mitigated by the in-app notification ("A draft information request has been created for {customer}") and the dashboard widget showing outstanding drafts. If firms later request auto-send capability, Option 3 can be implemented as an additive change: add an `autoSendRequest` boolean to `ProjectTemplate` with a default of `false`, preserving backward compatibility.

Related: [ADR-068](ADR-068-snapshot-based-templates.md) (snapshot-based templates), [ADR-134](ADR-134-dedicated-entity-vs-checklist-extension.md) (dedicated information request entities).

**Consequences**:

- `ProjectTemplate.requestTemplateId` triggers DRAFT creation only — never auto-sends
- `ProjectInstantiationService` creates the information request via `InformationRequestService.createFromTemplate()` which defaults to DRAFT status
- Primary portal contact is pre-selected but can be changed before sending
- In-app notification created for project members: "Draft information request created for {customer}"
- Dashboard shows DRAFT requests in the "Requests" summary (firms can see pending drafts)
- Firm must explicitly call `POST /api/information-requests/{id}/send` to deliver the request to the client
- Future enhancement: add `autoSendRequest` flag to `ProjectTemplate` for firms that want immediate sending (additive, backward-compatible)
