# Fix Spec: LZKC-020 — Portal engagement-letter acceptance attributed to "System"

## Problem
Day 88 / 88.4 (formalising a Day 10 observation): the Day-8 engagement-letter acceptance shows in firm and portal activity feeds as a Firm action by "System", and is missing from the client's portal "Your actions" trail — every other portal action (FICA submits, payment, downloads) is correctly attributed to Sipho.

## Root Cause (verified)
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/proposal/ProposalOrchestrationService.java:170-181` — the `proposal.accepted` audit event is built with no `.actorId(...)` / `.actorType(...)` / `.source(...)`, even though `acceptProposal(UUID proposalId, UUID portalContactId)` (line 131) has `portalContactId` in scope.
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/audit/AuditEventBuilder.java:259-278` — when actor is unset, builder falls back to `RequestScopes.MEMBER_ID`; portal requests bind `PORTAL_CONTACT_ID` not `MEMBER_ID`, so it resolves `actorId=null, actorType="SYSTEM"` (rendered as "System" per `audit/DatabaseAuditService.java:435-436, 479-481`).
- Portal "Your actions" tab filters `actor_type = PORTAL_CONTACT AND actor_id = contact` (`portal/PortalActivityService.java:47-57`) — hence the event is absent there.
- Correct sibling pattern: `portal/PortalQueryService.java:189-200` (document download) and `customerbackend/service/PortalInformationRequestService.java:322-350` both set `.actorId(portalContactId).actorType("PORTAL_CONTACT").source("PORTAL")`.

## Fix
In `ProposalOrchestrationService.java:170-181` add to the audit builder:
```java
.actorId(portalContactId)
.actorType("PORTAL_CONTACT")
.source("PORTAL")
```
Guard for the firm-side accept path: only set the portal-contact actor when `portalContactId != null`; otherwise leave the default so a firm-member acceptance stays a USER actor (check `ProposalController` call sites for a null/absent contact).

Forward-only: the existing Day-8 `proposal.accepted` row keeps `actor_type='SYSTEM'` — no backfill proposed (Low severity; flag to orchestrator if backfill is wanted).

## Scope
Backend only
Files to modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/proposal/ProposalOrchestrationService.java`
Files to create: none
Migration needed: no

## Verification
Accept a fresh engagement letter as a portal contact (scratch proposal): firm feed shows "Sipho Dlamini accepted …" (not System); portal `/activity` "Your actions" tab now contains the acceptance. Extend the proposal-acceptance integration test to assert `actor_type=PORTAL_CONTACT`, `actor_id=<contact>`.

## Estimated Effort
S (< 30 min)
