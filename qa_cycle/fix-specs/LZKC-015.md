# Fix Spec: LZKC-015 — No "Document ready" email for Statement of Account at closure

## Problem
Day 60 close: closure generates two PORTAL-visible documents; only the closure letter produced a "Document ready" email (Mailpit `TVipfc75zA7KqXbLid62Be`) — no email for the sibling SoA. Day 61 QA had to enter the portal via the closure-letter email instead.

## Root Cause (verified)
Not visibility or allowlist — both docs emit portal-visible `DocumentGeneratedEvent`s and `statement-of-account` IS in the default allowlist (`settings/PortalSettings.java:65-66`; SoA event emitted at `verticals/legal/statement/StatementService.java:217-244`; closure letter via `MatterClosureService.java:178-180` → `generateClosureLetterSafely` line 379-380, SoA second at line 192 → `generateSoaSafely` line 426).

The defect is the notification dedup key: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/portal/PortalDocumentNotificationHandler.java:222-231`:
```java
String dedupKey = event.tenantId() + ":" + ctx.customerId() + ":" + projectId;
if (dedupCache.getIfPresent(dedupKey) != null) { ... return; }   // SoA hits this
```
The key omits the template/document identity, so the closure letter (generated first) claims the key and the SoA event is silently deduped ("dedup hit" log, line 226). SoA-specific email copy already exists at lines 306-309 and is never reached.

## Fix
Include document type in the dedup key at `PortalDocumentNotificationHandler.java:224`:
`String dedupKey = event.tenantId() + ":" + ctx.customerId() + ":" + projectId + ":" + templateName;`
Closure letter and SoA each notify once; genuine duplicate re-emissions of the same doc type remain deduped.

(Alternative considered: coalesce the closure batch into ONE email listing both documents — better UX but larger change; flag for orchestrator if preferred. The one-line key fix satisfies the scenario.)

## Scope
Backend only
Files to modify: `PortalDocumentNotificationHandler.java`
Files to create: none
Migration needed: no

## Verification
Close a scratch matter with both doc flags checked → Mailpit shows TWO "Document ready" emails (closure letter + SoA). Re-emit same doc type → still one email. Extend the handler's test for the two-doc closure batch.

## Estimated Effort
S (< 30 min)
