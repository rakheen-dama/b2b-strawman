# Fix Spec: GAP-L-99 — Manual trigger for portal weekly digest

## Problem

`PortalDigestScheduler.scheduledRun()` is annotated `@Scheduled(cron = "0 0 8 ? * MON")` and is the only public path into `runWeeklyDigest()`. There is no REST endpoint, admin UI button, or dev-harness button to fire the sweep on demand. QA backends never stay up across a Monday 08:00 SAST tick (cycle 54's backend started Mon 27th 23:40 SAST, AFTER the cron window), so portal digest emails have never been delivered in any QA cycle (Mailpit subject:digest / subject:weekly / subject:week → 0 hits, all cycles).

Walk-blocker for Day 75 §75.1–75.4 (4 of 9 checkpoints BLOCKED on cycle 54). Same root cause was logged informationally as `OBS-Day75-NoManualDigestTrigger` on cycle 1 (2026-04-25) and elevated to a tracked GAP this cycle because it recurs every cycle.

## Root Cause

`backend/src/main/java/io/b2mash/b2b/b2bstrawman/portal/notification/PortalDigestScheduler.java`, lines 86-89:

```java
@Scheduled(cron = "0 0 8 ? * MON")
public void scheduledRun() {
  runWeeklyDigest();
}
```

`runWeeklyDigest()` is package-public and tests call it directly (`PortalDigestSchedulerIntegrationTest`), but no controller wires it to HTTP. The class is structurally fine — the gap is purely an exposed manual entry point.

## Fix

Backend-only. Add an internal REST controller that delegates to the existing scheduler bean. Do **not** modify the cron job — both paths must produce identical output.

### Step 1 — New controller `internal/PortalDigestInternalController.java`

Path: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/portal/notification/PortalDigestInternalController.java`

Endpoint: `POST /internal/portal/digest/run-weekly`

Auth: `ApiKeyAuthFilter` already protects every URI under `/internal/*` via the `X-API-KEY` header (see `backend/src/main/java/io/b2mash/b2b/b2bstrawman/security/ApiKeyAuthFilter.java` `shouldNotFilter` returns true unless `requestURI.startsWith("/internal/")`). The new controller inherits this gate for free — no new filter or annotation required.

Query params (all optional):
- `orgId` — Clerk org id. When present, restrict the sweep to that single tenant; resolve via `OrgSchemaMappingRepository.findByClerkOrgId()` exactly like `InternalAuditController.resolveSchema()`. When absent, sweep every tenant (mirrors `runWeeklyDigest()`).
- `targetEmail` — when present, after the per-tenant sweep selects active contacts, additionally filter to contacts whose email matches case-insensitively. Useful for QA single-recipient sends so a real cycle does not spam every portal contact.
- `dryRun` (boolean, default `false`) — when `true`, perform the same content assembly + filtering but do **not** call `PortalEmailService.sendDigestEmail`. Returns the would-have-sent count so QA can validate selection logic without inflating Mailpit.

Response shape (200 OK):
```json
{
  "tenantsProcessed": 1,
  "digestsSent": 1,
  "skipped": 0,
  "dryRun": false,
  "errors": []
}
```

Errors entry shape: `{ "schema": "tenant_xxx", "contactId": "...", "message": "..." }`. Each per-tenant or per-contact `Exception` caught inside the sweep is appended (mirroring the existing `log.warn` sites in `processTenant` and `dispatchDigestForContact`) instead of aborting the whole call. Returns 200 with errors list — caller decides how to react. Returns 404 if `orgId` is supplied and no mapping exists (matches `InternalAuditController` behaviour).

Controller body MUST follow the thin-controller rule from `backend/CLAUDE.md`: one delegation per method. Extract the new logic to a service method (next step), do not inline it.

### Step 2 — Refactor scheduler to expose a service-style method that returns a result

Modify `PortalDigestScheduler.runWeeklyDigest()` to:

1. Accept an options record:
   ```java
   public record RunOptions(String orgIdOrNull, String targetEmailOrNull, boolean dryRun) {
     public static RunOptions full() { return new RunOptions(null, null, false); }
   }
   ```
2. Return a result record:
   ```java
   public record RunResult(int tenantsProcessed, int digestsSent, int skipped, boolean dryRun, List<Error> errors) {
     public record Error(String schema, String contactId, String message) {}
   }
   ```
3. Keep an arg-less `runWeeklyDigest()` for the cron path that calls `runWeeklyDigest(RunOptions.full())` and discards the result. The cron tick must remain byte-identical in observable side effects.
4. Add `targetEmail` filter inside `processTenant` after the existing ACTIVE-contact filter.
5. Plumb `dryRun` into `dispatchDigestForContact`: when true, run `contentAssembler.assemble(...)` and `preferenceService.getOrCreate(...)` (so we still return realistic counts) but skip the `portalEmailService.sendDigestEmail(...)` call. Counts the contact as `digestsSent` to reflect "would have sent". When the bundle is null or preference is off, count as `skipped` regardless of dryRun.
6. Do **not** stamp `digestLastSentAt` on dry runs.
7. When `orgIdOrNull != null`, look up the schema via `OrgSchemaMappingRepository.findByClerkOrgId(orgId)` and only iterate that one mapping (404 from controller if not found).

Ensure the existing `PortalDigestSchedulerIntegrationTest` continues to pass without modification — keep the no-arg `runWeeklyDigest()` overload.

### Step 3 — Wire the controller

```java
@RestController
@RequestMapping("/internal/portal/digest")
public class PortalDigestInternalController {
  private final PortalDigestScheduler scheduler;
  // ctor injection

  @PostMapping("/run-weekly")
  public ResponseEntity<PortalDigestScheduler.RunResult> runWeekly(
      @RequestParam(required = false) String orgId,
      @RequestParam(required = false) String targetEmail,
      @RequestParam(defaultValue = "false") boolean dryRun) {
    return ResponseEntity.ok(
        scheduler.runWeeklyDigest(
            new PortalDigestScheduler.RunOptions(orgId, targetEmail, dryRun)));
  }
}
```

### Step 4 — Integration test `PortalDigestInternalControllerTest`

Path: `backend/src/test/java/io/b2mash/b2b/b2bstrawman/portal/notification/PortalDigestInternalControllerTest.java`

Setup mirrors `PortalDigestSchedulerIntegrationTest` (provision tenant, seed portal contacts, GreenMail singleton on :13025 — see `backend/CLAUDE.md`). Use `MockMvc` + `header("X-API-KEY", "test-api-key")` per `InternalAuditControllerTest:37`.

Cases:
1. `runWeekly_withTargetEmail_sendsToSingleContact` — provision tenant, seed 2 active portal contacts both with digest preference ON, both with content. POST `?orgId=...&targetEmail=contact-a@x.com`. Assert response `digestsSent=1`, GreenMail received exactly 1 message addressed to `contact-a@x.com`.
2. `runWeekly_fullSweep_sendsToAllEligibleContacts` — same fixtures, no `targetEmail`. Assert `digestsSent=2`, GreenMail received 2.
3. `runWeekly_dryRun_doesNotSend` — POST `?orgId=...&dryRun=true`. Assert `digestsSent=2, dryRun=true`, GreenMail received 0 messages, `org_settings.digestLastSentAt` NOT updated.
4. `runWeekly_unauthorized_returns401_withoutApiKey` — POST without header → expect 401 (handled by `ApiKeyAuthFilter`, no controller code).
5. `runWeekly_unknownOrgId_returns404` — POST `?orgId=org_does_not_exist` → expect 404 ProblemDetail (`ResourceNotFoundException`).
6. `runWeekly_idempotency_secondCallProducesSameResult` — call twice in succession with the same `targetEmail`; both calls succeed, second call sees the BIWEEKLY skip window only if cadence is BIWEEKLY (use WEEKLY in this test so both fire).

## Scope

Backend only. No frontend, no migration, no cron change.

Files to modify:
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/portal/notification/PortalDigestScheduler.java` — add `RunOptions`/`RunResult` records, new overload, plumb dryRun + targetEmail + orgId scoping.

Files to create:
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/portal/notification/PortalDigestInternalController.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/portal/notification/PortalDigestInternalControllerTest.java`

Migration needed: no.
Frontend changes: no.

## Verification

QA will, on a fresh `bugfix_cycle_2026-04-26-day75` backend (after Dev pushes the fix and `bash compose/scripts/svc.sh restart backend` lands the new controller in JVM):

1. Confirm Sipho has an active portal contact for the cycle's tenant, with `portal_notification_preferences.digest_enabled=true` (precondition — usually true via `getOrCreate`).
2. Issue a fresh magic-link to `sipho.portal@example.com` and authenticate the portal so the activity feed has at least one event in the 7-day lookback (otherwise content assembler returns null and the digest is suppressed by design).
3. Curl the new endpoint targeted at Sipho only:
   ```bash
   curl -s -X POST -H "X-API-KEY: test-api-key" \
     "http://localhost:8080/internal/portal/digest/run-weekly?orgId=mathebula-partners&targetEmail=sipho.portal@example.com" | jq
   ```
   (Use whichever orgId + API key the running profile expects — `application-test.yml` and `application-local.yml` both have `internal.key=test-api-key`.)
4. Assert response: `tenantsProcessed: 1, digestsSent: 1, errors: []`.
5. Poll Mailpit for the digest email to `sipho.portal@example.com` (template `portal-weekly-digest`, subject `<Org>: Your weekly update` per `PortalDigestScheduler:210`).
6. Click the digest CTA, confirm portal lands authenticated on `/home`.
7. Read the digest body and confirm it summarises only RAF-matter activity (Sipho's tenant). **Cross-tenant invariant**: assert NO `Moroka` text appears anywhere in the email body (the late-cycle isolation guard from §75.5/§75.6).
8. Walk Day 75 §75.1, §75.2, §75.3, §75.4 — expected 4 PASS replacing the 4 BLOCKED rows on cycle 54.
9. Re-run with `?dryRun=true` and assert Mailpit count does not increase but response still shows `digestsSent: 1, dryRun: true`.

## Estimated Effort

**S** — under 30 minutes. Single new controller (≈40 lines), scheduler refactor adds ~30 lines (new records + overload + 3 conditional branches inside existing methods), one new test class (~120 lines). No migration, no frontend, no cross-cutting concerns. The hardest part is the dryRun plumbing into `dispatchDigestForContact`, which is one extra parameter.

## Notes

- Cycle-1 informational `OBS-Day75-NoManualDigestTrigger` becomes RESOLVED-BY-DESIGN once this lands — link both records when closing.
- API key value: re-use the existing `internal.key` configured for the running profile (`test-api-key` for `test`/`local`, `${INTERNAL_API_KEY}` for `dev`/`prod`). Do NOT introduce a new key or env var.
- Header name: `X-API-KEY` (existing convention per `ApiKeyAuthFilter:21`). Spec-task instructions referenced `X-Internal-API-Key` — that header does not exist in this codebase; ignore it.
- Cron `@Scheduled` annotation MUST stay on `scheduledRun()`. Both the cron tick and the manual trigger must produce identical observable output (same email template, same `digestLastSentAt` stamp, same 12-day BIWEEKLY skip window logic).
- Thin-controller rule: the controller method is one delegation line; all logic stays in `PortalDigestScheduler`. This complies with `backend/CLAUDE.md` Controller Discipline.
- Architecture note: `PortalDigestScheduler` is a `@Component` rather than a `@Service`. Re-exposing its method to a controller via a record-style result is acceptable; if the dev agent prefers, they may rename it to `PortalDigestService` in this PR (no callers outside the scheduler itself + the new controller, plus the one integration test). Either choice is fine — flag in PR description.
