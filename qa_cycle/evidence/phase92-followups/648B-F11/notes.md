# 648B F-11 — every trust write carries an Idempotency-Key

Ruling (`tasks/phase92-decisions.md` row 648B F-11, founder 2026-11-22, option A): every trust write
carries a client-generated `Idempotency-Key`. A retry with the same key and the same request returns
the original result without a second posting. The same key with a different request is refused (409).

Defect (`qa_cycle/evidence/phase92/648B/found.md` F-11): no trust write carried a key, so if a
response was lost after commit and the firm resubmitted, the entry posted twice. Reproduced first:
`TrustIdempotencyRedTest` posted two deposits for one key on unmodified main (`red-run.txt`).

## 1. Endpoints

All 28 mutating mappings under `verticals/legal/trustaccounting/**` are `@IdempotentTrustWrite`.
There are no exclusions: every POST/PUT under the package is a trust write (a posting, an approval
decision, a reconciliation state change or a trust-account / memo record). There are no PATCH or
DELETE mappings. GETs are excluded (read-only), and the guard test refuses the annotation on them.

| # | Controller.method | Mapping |
|---|---|---|
| 1 | TrustAccountingController.create | POST /api/trust-accounts |
| 2 | TrustAccountingController.update | PUT /api/trust-accounts/{id} |
| 3 | TrustAccountingController.close | POST /api/trust-accounts/{id}/close |
| 4 | TrustTransactionController.recordDeposit | POST /api/trust-accounts/{accountId}/transactions/deposit |
| 5 | TrustTransactionController.recordPayment | POST …/transactions/payment |
| 6 | TrustTransactionController.recordTransfer | POST …/transactions/transfer |
| 7 | TrustTransactionController.recordFeeTransfer | POST …/transactions/fee-transfer |
| 8 | TrustTransactionController.recordRefund | POST …/transactions/refund |
| 9 | TrustTransactionController.approveTransaction | POST /api/trust-transactions/{id}/approve |
| 10 | TrustTransactionController.rejectTransaction | POST /api/trust-transactions/{id}/reject |
| 11 | TrustTransactionController.reverseTransaction | POST /api/trust-transactions/{id}/reverse |
| 12 | TrustInvestmentController.placeInvestment | POST /api/trust-accounts/{accountId}/investments |
| 13 | TrustInvestmentController.recordInterest | POST /api/trust-investments/{investmentId}/interest |
| 14 | TrustInvestmentController.reverseInterest | POST /api/trust-investment-interest/{interestId}/reverse |
| 15 | TrustInvestmentController.withdrawInvestment | POST /api/trust-investments/{investmentId}/withdraw (the "return") |
| 16 | TrustReconciliationController.importBankStatement | POST /api/trust-accounts/{accountId}/bank-statements (multipart; `replacesStatementId` = replace) |
| 17 | TrustReconciliationController.discardBankStatement | POST /api/bank-statements/{statementId}/discard |
| 18 | TrustReconciliationController.autoMatchStatement | POST /api/bank-statements/{statementId}/auto-match |
| 19 | TrustReconciliationController.manualMatch | POST /api/bank-statement-lines/{lineId}/match |
| 20 | TrustReconciliationController.recordFromLine | POST /api/bank-statement-lines/{lineId}/record |
| 21 | TrustReconciliationController.unmatch | POST /api/bank-statement-lines/{lineId}/unmatch |
| 22 | TrustReconciliationController.excludeLine | POST /api/bank-statement-lines/{lineId}/exclude |
| 23 | TrustReconciliationController.createReconciliation | POST /api/trust-accounts/{accountId}/reconciliations |
| 24 | TrustReconciliationController.calculateReconciliation | POST /api/trust-reconciliations/{id}/calculate |
| 25 | TrustReconciliationController.completeReconciliation | POST /api/trust-reconciliations/{id}/complete |
| 26 | UnidentifiedDepositController.allocate | POST /api/unidentified-deposits/{depositId}/allocate |
| 27 | UnidentifiedDepositController.payToFund | POST /api/unidentified-deposits/{depositId}/pay-to-fund |
| 28 | TrustInterestMemoController.save | PUT /api/trust-accounts/{accountId}/interest-memos/{month} |

Notes: `update` (2), `close` (3) and the memo `save` (28) are trust records, not postings. They are
included because the ruling says "every trust write", and an annotation the guard can check is
simpler than a per-endpoint judgement. `PUT` is idempotent anyway, so the key costs nothing there.
`TrustIdempotencyCoverageTest` scans every `@Controller` in the package. It requires the annotation
on each POST/PUT/PATCH/DELETE (or method-less `@RequestMapping`) and refuses it on a GET. It also
asserts at least 28 writes, so it is never vacuous. A mutation check (one annotation removed) failed
the test as expected.

## 2. Protocol (`verticals/legal/trustaccounting/idempotency/`)

- `@IdempotentTrustWrite` goes on the controller method. Controllers stay one-liners.
- `TrustIdempotencyFilter` (servlet filter, order 0, after the Spring Security chain at -100) handles
  buffering only. It acts on a mutating request that carries the header, has a resolved member
  (`RequestScopes.MEMBER_ID` bound), and whose handler (looked up the way the DispatcherServlet does,
  with the lookup's request attributes removed afterwards) is annotated. For that request it buffers
  the JSON body up to a bound (see §8) and wraps the response in a `ContentCachingResponseWrapper`.
  Every other request passes through untouched.
- `TrustIdempotencyInterceptor` (`HandlerInterceptor` on `/api/**`) runs the protocol inside the
  tenant and member scopes bound by `TenantFilter` / `MemberFilter`. It binds no `ScopedValue` and
  uses no `ThreadLocal`. Its steps:
  - Key validation. A missing key is a 400 `Idempotency-Key header required`. A key that is not a
    canonical UUID is a 400 `Invalid Idempotency-Key`. A JSON body over the limit is a 413
    `Request body too large`.
  - **(a) Claim.** `TrustIdempotencyService.claim` runs in its own `REQUIRES_NEW`
    `TransactionTemplate`. It does `INSERT … ON CONFLICT (idempotency_key) DO NOTHING`. A concurrent
    claim of the same key waits on the primary key until the first claim's short transaction
    commits, then sees the row. When the key already exists:
    - a different `member_id` or `request_hash` → 409 `Idempotency key reused`,
      `code: idempotency_key_reused`;
    - COMPLETED → the stored status, body, Content-Type and Location are replayed verbatim (plus
      `Idempotency-Replayed: true`), and the handler never runs;
    - IN_PROGRESS → 409 `Request still in progress`, `code: idempotency_in_progress`, never
      re-executed.

    The claim transaction commits and returns its connection before the write starts. No lock and no
    second connection is held while (b) runs (`open-in-view` is false).
  - **(b)** The handler runs exactly as today, in its own transaction.
  - **(c) Complete.** `afterCompletion` stores the status, the body and the headers, and marks the
    key COMPLETED, in its own transaction. A failure there is logged at ERROR and the key stays
    IN_PROGRESS, which is safe.
- `request_hash` is a SHA-256 over:
  - the method, the route template and the sorted path variables;
  - for JSON: the query string and the body normalised (members sorted, whitespace dropped). A body
    that is not JSON is hashed as its bytes;
  - for multipart: every parameter and non-file part, sorted, and per file the name, content type,
    size and a streamed SHA-256 of its bytes.
- Retention: `TrustIdempotencyPurgeJob` runs daily at 03:15 SA (`@SchedulerLock`,
  `TenantScopedRunner.forEachTenant`). It deletes keys older than 7 days, whatever their state.

## 3. 4xx / 5xx policy

- **2xx and 4xx are stored and replayed.** A 4xx is a refusal: the write's transaction rolled back
  and nothing posted. It is deterministic for the same request, so the same key replays the same
  refusal, even after its cause has gone (tested). This is the conservative option the brief prefers.
  The client takes a new key after any definite answer (§5), so a stale refusal is replayed only for
  a lost-response retry.
- **A 5xx or an unresolved exception leaves the key IN_PROGRESS.** The outcome is unknown, so a retry
  is refused with 409 and never re-executed. The firm checks the ledger. The row is purged after 7
  days.
- A crash between (b) and (c) leaves IN_PROGRESS. That is safe: no double posting.
- A capability refusal (403, method security inside the handler) is a 4xx. It is stored and replayed
  for the same member and key.

## 4. Non-frontend HTTP callers

| Caller | Change |
|---|---|
| `compose/seed/lib/trust.sh` (boot seed, creates the trust account) | New `api_post_trust` in `compose/seed/lib/common.sh` adds a fresh key. Alpine has no `uuidgen`, so it reads `/proc/sys/kernel/random/uuid` and falls back to `uuidgen`. |
| `frontend/e2e/helpers/api.ts` `api()` (used by `apiOk` / `apiStatus`, e.g. `trust-balanced-month.spec.ts` POST …/complete) | A fresh key on every non-GET call (one setup call is one intent). |
| `portal/e2e/helpers/firm-api.ts` `firmApiOk` (`recordAcmeDeposit`) | A fresh key on every non-GET call. |
| `frontend/e2e/tests/admin-audit-30day/fixtures.ts` | Not changed. It POSTs `/api/trust-transactions`, a route that does not exist (pre-existing, stale), so its `/approve` call is never reached. |
| MCP (`mcp/tool/TrustTools`) | Read-only, and it calls services in Java, so it is unaffected. |
| Internal Java callers (demo seeders, services, tests calling services) | They bypass HTTP, so they are unaffected. |
| Backend MockMvc tests | `TestcontainersConfiguration` declares one `MockMvcBuilderCustomizer`: `defaultRequest(get("/").with(TestIdempotency.freshKeyIfAbsent()))`. It adds a fresh key to every mutating request without one. It is declared inside the existing shared config, so it adds no context-cache key. Protocol tests set the header, or strip it with `TestIdempotency.withoutIdempotencyKey()`. |

## 5. Frontend

- `lib/idempotency.ts` (isomorphic) holds the header, `idempotent(key)` (the `api.post/put` options),
  `isOutcomeUnknown`, `outcomeUnknownOf` and `unknownOutcome`. An outcome is unknown when there was
  no response, on a 5xx, or on the `idempotency_in_progress` 409.
- `hooks/use-idempotency-key.ts`: `useIdempotencyKey().run(request, key => action(…, key))`.
  - A fresh UUID per submit intent.
  - The same key is reused only when the same request's last submit ended with its outcome unknown:
    the action threw (the browser→Next leg failed) or it returned `outcomeUnknown`.
  - After a success or a definite refusal, the next submit (even an identical one) is a new intent
    with a new key. A changed request is also a new intent.
  - Concurrent identical submits get their own keys.
  - This refines the brief's "reset on open / after success". The key is not reset when a dialog
    reopens, because a reopen after a lost response is the likeliest retry. It is reset after any
    definite answer, so a corrected resubmit after a refusal never hits "reused with a different
    request" and never replays a stale refusal.
- Server actions: 26 trust-write actions (every frontend caller of the 28 endpoints; account
  update/close have no UI caller). Each takes `idempotencyKey` last, sends it as the header, and
  returns `...unknownOutcome(error)` on failure:
  - `trust-accounting/actions.ts`: createTrustAccount;
  - `transactions/actions.ts`: recordDeposit, recordPayment, recordTransfer, recordFeeTransfer,
    recordRefund, approveTransaction, rejectTransaction, reverseTransaction;
  - `investments/actions.ts`: placeInvestment, recordInvestmentInterest, reverseInvestmentInterest,
    withdrawInvestment;
  - `other-creditors/actions.ts`: allocateUnidentifiedDeposit, payUnidentifiedDepositToFund,
    saveInterestMemo;
  - `reconciliation/actions.ts`: uploadBankStatement (multipart `fetch`, header added; the outcome is
    computed from the status and `code`), discardBankStatement, autoMatch, manualMatch, unmatch,
    excludeLine, recordFromStatement, createReconciliation, calculateReconciliation,
    completeReconciliation.
- Call sites (one hook per component; the action name is part of the intent):
  - `components/trust/`: CreateTrustAccountDialog, RecordDeposit / Payment / Transfer / FeeTransfer /
    Refund dialogs, ReversalButton, ApprovalBadge (approve) and its RejectDialog, PlaceInvestment,
    RecordInvestmentInterest, InvestmentInterestEntries (reverse), WithdrawInvestment,
    AllocateUnidentified, PayToFund, InterestMemo, DiscardStatement;
  - the reconciliation wizard `reconciliation/new/page.tsx`: upload, auto-match, create, every
    calculate, match, exclude, unmatch, record, complete. Its `idempotency` is added to each
    `useCallback`'s deps.

  There are no `legal/**` trust writes: `legal/disbursements/actions.ts` only reads trust
  transactions.

## 6. Migration

Tenant `V31__phase92_trust_idempotency_keys.sql` (V30 was the latest on main). It creates
`trust_idempotency_keys`:
- columns: `idempotency_key` uuid PK (the unique index; per tenant schema, so per tenant),
  `member_id`, `http_method`, `route_template`, `request_hash`, `state` (CHECK IN_PROGRESS /
  COMPLETED), `response_status`, `response_body` text, `response_headers` jsonb (Content-Type,
  Location), `created_at`, `completed_at`;
- a CHECK tying the response columns to the state;
- an index on `created_at` for the purge.

It is idempotent: `CREATE … IF NOT EXISTS`, constraints inline, no catalog lookups. No
`customer*` / `project*` names.

## 7. Tests

- Red: `TrustIdempotencyRedTest` on unmodified main: 2 postings (`red-run.txt`). Green: 1.
- `TrustIdempotencyIntegrationTest` (16, MockMvc + embedded Postgres):
  - same key + same request → one posting and an identical replayed response (status, body,
    Content-Type, Location; `Idempotency-Replayed`);
  - JSON layout does not matter;
  - a stored Location is replayed;
  - same key + different body → 409; same key on another endpoint → 409; same key + different
    member → 409;
  - a seeded IN_PROGRESS key → 409 and not executed;
  - a missing key → 400; malformed keys (`not-a-uuid`, `1-1-1-1-1`, empty) → 400;
  - a 4xx refusal (insufficient balance) is replayed after the balance is funded; a new key posts;
  - concurrency: a gate connection holds the trust account row lock. The first request claims and
    blocks in its posting lock (observed in `pg_stat_activity`, no sleeps). The second request with
    the key gets 409 in-progress. Released, exactly one deposit posts and a later retry replays it;
  - multipart import: a replay posts one statement; a different file under the key → 409;
  - purge: a key older than 7 days is deleted and a newer one is kept;
  - bounded buffering (§8).
- `TrustIdempotencyInterceptorTest` (6, no Spring context): 2xx/4xx stored, 5xx and unresolved
  exceptions not stored, replay writes the stored response and skips the handler, unannotated
  handlers ignored.
- `TrustIdempotencyFilterTest` (6): buffering bounds, multipart never buffered, nothing read
  without a member or for another endpoint.
- `TrustIdempotencyCoverageTest`: the guard.
- Frontend `__tests__/trust/trust-idempotency-key.test.tsx` (10):
  - the key is stable across a network-failure retry and across unknown outcomes;
  - a new key per intent, after a success, after a refusal and for a changed request;
  - concurrent submits get their own keys; keys are UUIDs;
  - outcome classification;
  - the action sends the header and flags unknown outcomes;
  - a hook resubmit after a lost response reuses the key on the wire.

  The existing trust action and dialog tests were updated to expect the header / the key argument.
  A "server refusal" mock now carries a `status`, as a real `ApiError` does.

## 8. Security review finding (MEDIUM, raised on the first push) and fix

Finding: `BufferedBodyRequest` read `request.getInputStream().readAllBytes()` with no cap, so an
authenticated caller could make the server hold an arbitrarily large body.

Fix:
- **JSON bodies** are buffered only up to `TrustIdempotencyFilter.MAX_JSON_BODY_BYTES` = 256 KiB.
  The largest legitimate trust JSON body is under 2 KiB: a transfer, or an interest entry with a
  500-character note or reason. A declared `Content-Length` above the limit is never read. A body
  without one, or with a false one, is read through `readNBytes(limit + 1)`, at most one byte past
  the limit. Either way the request is marked and the interceptor refuses it with a 413
  ProblemDetail (`PayloadTooLargeException`, new in `exception/`, registered with the ADR-357
  scanner) before the key is claimed or the handler runs.
- **Multipart** (the statement import) is never buffered by the filter. The container's limits apply
  exactly as before (`spring.servlet.multipart.max-file-size: 10MB`, `max-request-size: 15MB`):
  Tomcat refuses an oversized upload at multipart resolution, before the interceptor, as today. The
  hasher now digests each file part through a `DigestInputStream`, so it makes no in-memory copy.
  MockMvc pre-parses multipart, so the container limit itself is not exercised by these tests.
- **Order.** The filter runs after the Spring Security chain, which contains `TenantFilter` and
  `MemberFilter`, and it also requires `RequestScopes.MEMBER_ID` before reading. An unauthenticated
  request is never buffered: it gets a 401 first (tested with a 512 KiB body). The capability check
  (`@RequiresCapability`, method security) runs later, inside the handler, so an authenticated member
  without `MANAGE_TRUST` can still cause one bounded (≤256 KiB) buffer and one key row (purged after
  7 days).
- If a body was somehow not buffered, the hasher throws instead of hashing it as empty, so two
  different requests can never share a hash.
- Tests:
  - an oversized JSON body → 413 with no key claimed and nothing posted;
  - a body of exactly 256 KiB → accepted and replayed;
  - an unauthenticated oversized request → 401;
  - filter unit tests: a declared oversize is not read at all; a missing or false length is read at
    most limit + 1 bytes; multipart is never buffered.

## 9. Residual risk

- **Keys only protect retries inside one page session.** The key lives in the component. A full page
  reload, or a different tab, after a lost response gets a new key and could post again. The
  server-side duplicate heuristic (option C) was not chosen.
- **A stuck key.** A key left IN_PROGRESS by a 5xx or a crash refuses that intent's retries until it
  is purged (7 days). The UI shows the "check the ledger" refusal. This is by design.
- **A changed request after an unknown outcome is a new intent.** The first submit may have posted,
  and the firm sees both in the ledger.
- **Response buffering.** The response is buffered in memory and stored in `response_body`. It is
  bounded by what the server generates. The largest is a statement import, which returns its lines,
  and that is bounded by the 10 MB upload limit.
- **Repeated handler lookup.** The filter's handler lookup runs once more per mutating request that
  carries the header, and only those.
- **Pre-existing frontend failures, unrelated.** 27 tests in 4 files fail identically on unmodified
  main 34f45c071: `comments/comment-item`, `legal/statement-of-account-dialog`,
  `matters/matter-search`, `components/legal/__tests__/retention-card` (hook time-outs). See
  `frontend-gate.txt`. They are not triaged here (scope discipline). They are raised for the
  orchestrator.
- **Full verify not run here.** The full `./mvnw verify` is the orchestrator's (brief). Tier 2 counts
  are in `tier2.txt`.
