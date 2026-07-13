# Fix Spec: LZKC-025 — Portal proposal declines have no actor attribution (LZKC-020 class, decline verb)

## Problem
Carried forward: `proposal.declined` audit events carry no actor. On portal requests `RequestScopes.MEMBER_ID` is unbound, so the event renders as "System" in firm feeds and is dropped from the portal "Your actions" trail (which filters `actor_type = PORTAL_CONTACT`). Same defect class as LZKC-020 (accept verb), which was fixed last cycle; the decline verb was left behind.

## Root Cause (confirmed)
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/proposal/ProposalService.java:808` — `declineProposal(UUID proposalId, String reason)` builds the `proposal.declined` audit event (`:820-831`) with **no** `.actorId()/.actorType()/.source()` → builder falls back to the unbound member scope.
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/proposal/PortalProposalService.java:153-177` — `declineProposal(proposalId, customerId, portalContactId, reason)` HAS the `portalContactId` (it validates it at `:159`) but drops it at `:177`: `proposalService.declineProposal(proposalId, reason)`.
- The fixed accept-path pattern to mirror: `backend/.../proposal/ProposalOrchestrationService.java:170-187` — guarded actor attribution:

```java
if (portalContactId != null) {
  acceptedAudit.actorId(portalContactId).actorType("PORTAL_CONTACT").source("PORTAL");
}
```

- Caller survey (grep-verified): the ONLY production caller of `ProposalService.declineProposal` is `PortalProposalService:177` (HTTP entry `PortalProposalController:58-62`). No firm-side decline endpoint exists, so a signature change is safe.

## Fix
1. Change `ProposalService.declineProposal(UUID proposalId, String reason)` → `declineProposal(UUID proposalId, String reason, UUID portalContactId)` (nullable; future firm-side declines pass null).
2. In the audit build at `ProposalService.java:820-831`, convert to the builder-variable shape used by the accept fix and apply the same guard: when `portalContactId != null`, add `.actorId(portalContactId).actorType("PORTAL_CONTACT").source("PORTAL")`.
3. `PortalProposalService.java:177` → `proposalService.declineProposal(proposalId, reason, portalContactId)`.
4. Leave the creator notification (`:837-851`) untouched — it already targets `proposal.getCreatedById()` correctly.

## Scope
Backend only.
Files to modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/proposal/ProposalService.java`, `backend/src/main/java/io/b2mash/b2b/b2bstrawman/proposal/PortalProposalService.java`.
Test: extend `backend/src/test/java/io/b2mash/b2b/b2bstrawman/proposal/PortalProposalControllerTest.java` (decline tests exist at `:536-587`) — after a portal decline, assert the `proposal.declined` audit row has `actor_type = PORTAL_CONTACT` and `actor_id = {portalContactId}` (red-first on current main).
Migration needed: no.

## Verification
- New assertion red-first, green after.
- Full `bash scripts/verify.sh` (targeted scope must include `proposal` package + anything importing `ProposalService` — but merge bar is the full verify).
- Live: decline a SENT engagement letter from the portal as Sipho → firm proposal feed shows "Sipho Dlamini declined…" (not "System"); portal /activity "Your actions" shows the decline. (Related known observation e — `proposal.accepted` payload `actor_name` "System" — is a separate display-payload nit, NOT in scope; don't bundle.)

## Estimated Effort
S–M (~45 min including test)
