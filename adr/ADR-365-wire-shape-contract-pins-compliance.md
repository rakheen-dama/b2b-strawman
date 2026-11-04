# ADR-365: Wire-shape contract pins for compliance screens

**Status**: Accepted

**Context**:

The Phase 88 re-walk found the same defect class three times on compliance screens that the lifecycle suites rarely walk: the frontend reads a response shape the backend does not send. Data export: `POST /api/clients/{clientId}/data-export` returns 202 with `ExportResult(exportId, status, estimatedFiles)` (`datarequest/ExportResult.java:6`) while `data-export-dialog.tsx:131-139` renders `fileCount`, `totalSizeBytes` and `downloadUrl` from `StandaloneExportResult` (`lib/types/data-protection.ts:116-123`) — "files · NaN undefined" and a Download Archive with no href (G88-F43). Anonymise: `AnonymizationPreview` is a flat record (`AnonymizationPreview.java:11-22`) but the TS type nests `affectedEntities.*` (`data-protection.ts:100-114`), read at `anonymize-customer-dialog.tsx:148-154` → TypeError; and a second drift the gap row did not record — `AnonymizationResult(customerAnonymized, documentsDeleted, commentsRedacted, portalContactsAnonymized, invoicesPreserved)` (`DataAnonymizationService.java:405-410`) versus a TS `StandaloneAnonymizationResult` with `status`, `referenceId`, `preExportKey` and a nested `summary` (`:125-137`), read at `:284-288` after execute (G88-F44 ×2). Conflict check: `ConflictDetail.relationship` (`ConflictCheckService.java:97-107`) is null for an unlinked adverse party while the TS type said `string` (G88-F38, closed by #1719 but the same class). The requirements make it a rule: "frontend/backend shape drift is a contract bug" — align the type to the backend record **and** add a contract test that fails if they drift again.

No existing pin covers responses. The Phase 87/88 wire-bodies tests (`frontend/__tests__/customers/client-wire-bodies.test.ts` and siblings, [ADR-337](ADR-337-wire-rename-two-nouns-entities-stay.md)) pin what the frontend **sends** (request body keys Jackson would drop); `satisfies` assertions (`__tests__/lib/types/integrations.test.ts`) check a literal against a TS type with no link to the Java record; `McpToolContractTest` ([ADR-343](ADR-343-mcp-contract-pinning-test.md)) snapshots `mcp/dto` records by reflection into `src/test/resources/mcp/tool-contract.json` with `-Dmcp.contract.update=true`, but only for the MCP boundary. Nothing ties a `lib/types/*.ts` interface to the Java record it mirrors.

**Options Considered**:

1. **Runtime zod schemas in the frontend for every compliance response, parsed at the API boundary.**
   - Pros:
     - Catches drift in production, not only in tests; gives typed errors at the call site.
   - Cons:
     - Catches it as a thrown parse error on the user's screen — the same symptom as today with a better message; the schema is still hand-written from the TS side and drifts with it unless something generates it from Java.
     - Adds a runtime dependency and a parse on every response for four DTOs; the project has no zod layer and [ADR-337](ADR-337-wire-rename-two-nouns-entities-stay.md) kept the API client thin.
2. **OpenAPI codegen: generate the TS types from the backend's OpenAPI document and import them.**
   - Pros:
     - The canonical answer to the class; types cannot drift because they are not hand-written.
   - Cons:
     - The springdoc premise was found false in the 2026-06 simplification (api-types deferred: the annotations are not maintained and the generated document does not match the records), and standing it up is a phase of its own, not a Phase 89 slice; it would also regenerate every type, far outside "one fix per PR".
     - Generated types would collide with the hand-curated `lib/types/*` that the terminology guard ([ADR-357](ADR-357-terminology-guard-extension.md)) scans.
3. **Backend reflection snapshot of the record components into a committed JSON, plus a frontend test that builds a `Required<T>` literal per record and asserts its key set equals the JSON (CHOSEN).**
   - Pros:
     - One source of truth on the Java side, produced by the same mechanism `McpToolContractTest` already uses (reflection over record components, byte-for-byte compare, explicit update flag).
     - The frontend side fails twice over: at compile time (`Required<T>` rejects a literal with a missing or extra key, so `pnpm typecheck` — #1721 — is red) and at runtime (`Object.keys(literal)` compared to the JSON component list is red when the Java record changes and the literal was not updated).
     - No runtime cost, no new dependency, no codegen; the pin is four records today and grows one literal at a time.
   - Cons:
     - Field **types** are pinned on the backend side only; the frontend pin proves names, not that `fileCount` is a number — a type-level drift (string vs number) still needs the vitest that renders the dialog with a fixture.
     - The frontend test reads the JSON by relative path, which assumes the monorepo checkout (true in CI and locally; a standalone frontend checkout fails the test, by design).
4. **A single shared JSON schema per DTO hand-maintained in `packages/shared`, consumed by both sides.**
   - Pros:
     - One file both sides import.
   - Cons:
     - Hand-maintained means it drifts from Java exactly as the TS did; nothing on the backend side compares the record to it unless option 3's reflection test exists anyway.

**Decision**: Option 3. **Backend**: `datarequest/DataProtectionWireContractTest` (pattern `McpToolContractTest`) pins the component names **and** types of `ExportStatusResponse`, `AnonymizationPreview`, `AnonymizationResult` and `ConflictDetail` in declaration order into `backend/src/test/resources/wire/data-protection-contract.json`; the actual is written to `target/` and compared byte-for-byte; regenerate only with `-Ddataprotection.contract.update=true` (`WireVocabularyContractTest` keeps its own, separate flag), and the PR that does so cites the slice. **Frontend**: `frontend/__tests__/customers/data-protection-wire-shapes.test.ts` imports that JSON statically by relative path (a missing file fails the build — that is the point of the pin), builds one hand-written literal per record typed `Required<T>` (compile fails on a missing or extra key) and asserts `Object.keys(literal)` equals the JSON component list (runtime fails on a stale literal). Names are pinned on both sides; types on the backend only. **The fixes the pins protect** (R-E2a/b): `POST /api/clients/{clientId}/data-export` returns **200** with `ExportStatusResponse` (the service already builds it at `DataExportService.java:178-245`; `triggerCustomerExport` and `ExportResult` are deleted; the dialog needs no change beyond the pin; `StandaloneExportResult` gains `s3Key: string` so the pin covers all seven components; the dialog ignores it); `AnonymizationPreview` and `AnonymizationResult` TS types become the flat backend records and the dialog reads flat fields at `:148-154` and `:284-288`. The API is not extended.

**Rationale**:

1. **A drift is a contract bug and must fail a build, not a walk.** Three findings in one re-walk, on screens outside the 90-day path, show that browser observation alone does not scale to rarely-used compliance screens; a POPIA export that crashes is a go-live blocker for a design-partner firm. The pin converts "someone must click Export on the Keycloak tenant" into a failing `./mvnw verify` or `pnpm test`.
2. **Reflection, because records are the wire.** Since [ADR-337](ADR-337-wire-rename-two-nouns-entities-stay.md) the DTOs are plain Java records serialised by Jackson with no annotations to drift; their components *are* the JSON keys. A reflection snapshot is therefore exact, and `McpToolContractTest` has already proven the mechanism and the update-flag discipline on this codebase.
3. **Two failure modes on the frontend, deliberately.** `Required<T>` makes the literal a compile-time statement that the TS interface has exactly these keys; the runtime key-set comparison makes the JSON the arbiter when Java changes. Either alone has a gap (a stale literal that still compiles; a type edit with no test run); together they cover the F43 (missing fields) and F44 (nested vs flat) shapes.
4. **Scoped to the class that bit, not a programme.** Four records, one JSON, one test on each side. The pattern is reusable — `MyCapabilitiesResponse.memberId` ([ADR-362](ADR-362-interest-run-draft-approval-surface.md)) and `TrustTransactionResponse.runningBalance` ([ADR-361](ADR-361-client-ledger-running-balance-server-side.md)) are candidates — but adding records is a per-slice decision, not a blanket migration (one fix per PR; OpenAPI codegen remains the long-term answer and is not re-proposed here).
5. **Phase 88 lesson — the fix is observed.** The slice un-fixmes `customer-data-protection.spec.ts:48,76,107`; the Keycloak walk exports a client's data and downloads the archive (presigned URL from `ExportStatusResponse.downloadUrl`), then previews and completes an anonymisation.

**Consequences**:

- Positive:
  - F43 and F44 (both drifts) are closed with the smallest API change (one status code, one record deleted) and the TS types aligned; the dialogs render real counts and a working Download Archive.
  - Any later change to the four records fails `DataProtectionWireContractTest` until the JSON is regenerated with the flag, and fails the frontend typecheck/vitest until the literal is updated — the drift is caught on whichever side moved first.
  - `-Ddataprotection.contract.update=true` and `src/test/resources/wire/` become the home for response-shape pins, next to `WireVocabularyContractTest`'s `batch1-known-offenders.txt` (which keeps its own, separate flag).
- Negative:
  - Two files to touch for an intentional DTO change (JSON regeneration + literal); the PR body must say why, as for the MCP contract.
  - Types are not pinned on the frontend; a `number` → `string` change passes the key-set test and is caught only by the backend snapshot and by the dialog's rendering test with a fixture.
  - The relative-path static import couples the frontend test to the monorepo layout; a missing JSON fails the build rather than skipping — by design.
  - `ExportResult` deletion removes the 202 "accepted" semantics; the export was always synchronous, so nothing waits on it, but the controller test and any client that relied on 202 change.
- Explicitly not done: no zod, no OpenAPI codegen, no repository-wide pin of every DTO, no API extension for anonymisation counts the preview "genuinely lacks" (the flat record already carries them).

**Related**: [ADR-337](ADR-337-wire-rename-two-nouns-entities-stay.md) (request-body pins; records as the wire), [ADR-343](ADR-343-mcp-contract-pinning-test.md) (reflection snapshot + update flag precedent), [ADR-342](ADR-342-audit-and-entity-tag-strings-stable-under-renamed-wire.md) (guard-test convention), [ADR-357](ADR-357-terminology-guard-extension.md), [ADR-361](ADR-361-client-ledger-running-balance-server-side.md), [ADR-362](ADR-362-interest-run-draft-approval-surface.md); `architecture/phase89-go-live-readiness.md` §14.4, §14.8, §14.11; `qa_cycle/evidence/phase88-rewalk/full/gaps.md` G88-F43 / G88-F44 (and G88-F38 as the earlier member of the class).
