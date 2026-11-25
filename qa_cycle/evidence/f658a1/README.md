# F-658A-1: Xero push never queued on fee note approve, send or void

Founder rulings FR-11 (investigate and fix), FR-12 (sync from now on only, no backfill) and FR-14 (merge the fix gated off). PR #1860, branch `fix/f658a1-accounting-push-not-queued`.

**Status: the lost enqueue is REPRODUCED and FIXED, and it ships SWITCHED OFF (`kazi.accounting.push.enabled`, false when absent). Nothing is queued, dispatched or resynced until the drain worker sends real payloads. The targeted tests are green on the rebased head. The full `./mvnw verify` (the merge bar) has not been run by this agent.**

## Hypothesis (from `qa_cycle/evidence/phase93/658A/found.md` F-658A-1)

When Xero is CONNECTED, an ordinary fee note created and approved through `POST /api/invoices` → `/lines` → `/approve` left no `accounting_sync_entry` row. Calling `AccountingSyncService.enqueueInvoicePush` directly did create one. The suspected cause: the AFTER_COMMIT listener calls a REQUIRED `@Transactional` method, that method joins the transaction that has already committed, and its insert is never committed.

## Trace

| Step | Where |
|---|---|
| Approve publishes `InvoiceApprovedEvent` inside its `@Transactional` | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/InvoiceTransitionService.java:113-114` (tx), `:218` (publish) |
| Send publishes `InvoiceSentEvent` | same file `:237-238`, `:284` |
| Void publishes `InvoiceVoidedEvent` | same file `:821-822`, `:898` |
| Client create publishes `CustomerCreatedEvent` | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customer/CustomerService.java:600` (updates at `:766`, `:876`, `:914`) |
| Listener `@TransactionalEventListener(AFTER_COMMIT)`, synchronous (no `@Async`), binds tenant via `RequestScopes.runForTenantOnShard`, catches every exception and logs WARN | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/accounting/sync/AccountingSyncEventListener.java:30-47` (approve), `:49-66` (sent), `:68-85` (voided), `:87-121` (customer), all on `origin/main` 86fbce8ec |
| Enqueue is plain `@Transactional` (REQUIRED). Guards: brought-forward → skip; no CONNECTED connection → skip; active or terminal entry → skip; then `save` | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/accounting/sync/AccountingSyncService.java:95-213` (customer `:219-243`) |

Nothing else blocks it: no feature or module flag, the tenant binding is correct (`runForTenantOnShard` with the event's tenant), the CONNECTED guard passes, and no exception is thrown. The insert is simply never committed.

## Reproduction (written first, on unchanged code)

`backend/src/test/java/io/b2mash/b2b/b2bstrawman/integration/accounting/sync/AccountingSyncEventListenerIntegrationTest.java`. The tenant is `legal-za` with a CONNECTED Xero connection, set up the `AccountingSyncServiceTest` way. Every action goes through the real HTTP path, and every assertion is by entity id:

1. approve → exactly one PENDING / EVENT / PUSH entry, `KAZI-INV-<id>`, and one row in `"<tenant schema>".accounting_sync_entry` (tenant isolation)
2. approve, mark that push COMPLETED, send → a new PENDING EVENT entry
3. approve, send, mark COMPLETED, void → a new PENDING EVENT entry
4. approve, then send while the approval push is still PENDING → still exactly one entry (no double enqueue)
5. `POST /api/clients` → one PENDING EVENT CUSTOMER entry
6. `POST /api/clients`, mark that push COMPLETED, `PUT /api/clients/{id}` → a new PENDING EVENT CUSTOMER entry. This was added for PR #1860 review: `onCustomerUpdated` had no test.

Since FR-14, this class tests the **enabled** route. It sets `kazi.accounting.push.enabled=true` at class level, and so does `AccountingSyncWorkerTest`.

**RED on unchanged code** (commit `cd32d2394`, log `red-run.txt`): `Tests run: 5, Failures: 5`, and every failure reads:

```
java.lang.AssertionError:
Expected size: 1 but was: 0 in:
[]
	at ...AccountingSyncEventListenerIntegrationTest.approvingAnOrdinaryFeeNote_queuesAPendingXeroPush_inTheFirmsSchema(AccountingSyncEventListenerIntegrationTest.java:96)
```

**Root cause, observed rather than inferred.** Test logging is ERROR-only, so the approve case was re-run with `-Dlogging.level.org.springframework.transaction=DEBUG`, `...orm.jpa.JpaTransactionManager=DEBUG` and `...integration.accounting=DEBUG` (log `red-debug-run.txt`, excerpt `red-debug-excerpt.txt`):

```
04:02:09.582 JpaTransactionManager | Committing JPA transaction on EntityManager [SessionImpl(1535150965<open>)]
04:02:09.585 JpaTransactionManager | Found thread-bound EntityManager [SessionImpl(1535150965<open>)] for JPA transaction
04:02:09.585 JpaTransactionManager | Participating in existing transaction
   ... (enqueue's repository reads, all on SessionImpl(1535150965), all "Participating") ...
04:02:09.596 AccountingSyncService | Enqueued invoice push: invoiceId=0c0f3671-..., trigger=EVENT
04:02:09.597 JpaTransactionManager | Suspending current transaction, creating new transaction with name [...NotificationService.handleInvoiceApproved]
04:02:09.597 JpaTransactionManager | Opened new EntityManager [SessionImpl(994122221<open>)] for JPA transaction
04:02:09.605 JpaTransactionManager | Committing JPA transaction on EntityManager [SessionImpl(994122221<open>)]
04:02:09.605 JpaTransactionManager | Closing JPA EntityManager after transaction
```

`enqueueInvoicePush` runs to completion: it logs "Enqueued invoice push", and no exception is swallowed. But it ran in the approve's EntityManager after that transaction had committed. No commit follows, so the new entity is discarded when that EntityManager closes. The notification handler next to it (`REQUIRES_NEW`) opens its own EntityManager and commits, which is why notifications work and the Xero queue does not. The hypothesis is confirmed.

## Fix (right layer: the listener, because it is the code that knows it runs after commit)

`AccountingSyncEventListener` now builds a `REQUIRES_NEW` `TransactionTemplate`, the same pattern `collections/CollectionsPaymentListener` uses. Each enqueue runs inside it, and the template is opened *inside* the tenant binding, so the new session resolves the event's tenant schema. The service's REQUIRED propagation is unchanged, so the force-resync controller and direct callers keep their semantics. Idempotency is unchanged: an active (PENDING / IN_FLIGHT / FAILED_RETRYING) or terminal (BLOCKED_TRUST_BOUNDARY / DEAD_LETTER) entry makes the enqueue a no-op, as proved by test 4. A failure rolls back only the inner transaction and is still logged at WARN.

- `94d752af4`: the fee-note handlers (approve, sent, voided). After this commit the reproduction was 4/5 green, with only the client case red (`step2-invoice-fix-run.txt`).
- `88eddf1bb`: the client create/update handlers. This is the same defect in the same listener, kept as its own commit so it can be dropped if the client half is ruled out of scope (root CLAUDE.md §7).

## Gated off (FR-14, PR #1860 review)

Both reviewers found (and I confirmed in the code) that the drain worker never got its real payload mapping. `AccountingSyncWorker.dispatchSync` still sends stubs: an INVOICE goes out as `"Pending"`, with no lines, currency `"NZD"` and a null issue date, which NPEs in `XeroInvoicePayloadMapper.java:45`. A CUSTOMER goes out as a contact named `"Pending"`. Wiring the real payloads (the 519A comments) never happened between epics 519/520 and the worker. Failed entries retry and then DEAD_LETTER, and a dead letter blocks every later enqueue for that entity, FORCE_RESYNC included. The listener fix is correct, but it makes that broken pipeline reachable, so it ships switched off:

| Where | While off (the default) |
|---|---|
| `AccountingPushSwitch` (new) | Reads `kazi.accounting.push.enabled` (`@Value`, default `false`). Documented in `application.yml` as `false`. Logs ENABLED/DISABLED once at INFO on `ApplicationReadyEvent`. |
| `AccountingSyncEventListener` | Every handler (approve, sent, voided, client created, client updated) returns before binding or enqueueing. The REQUIRES_NEW fix stays in place for when the switch is on. |
| `AccountingSyncWorker.drainForTenant` | Uses the new `AccountingSyncEntryRepository.findDrainableEntriesExcludingDirection(PUSH, …)`. INVOICE and CUSTOMER push entries are not selected, so they are not dispatched, retried or dead-lettered. Non-push entries (PAYMENT_PULL) drain as before. The payment poller (`AccountingPaymentPollWorker`) is a separate job and is unchanged. |
| `POST /api/integrations/sync/invoice/{id}/resync` | The controller now calls `AccountingSyncService.forceResyncInvoice`, which throws `ResourceConflictException` → **409** ProblemDetail: title "Xero sync is not available yet", detail "Kazi does not send fee notes or clients to Xero yet. Nothing was queued.", `code: XERO_SYNC_NOT_AVAILABLE`. Nothing is queued. |
| Direct `enqueueInvoicePush` / `enqueueCustomerPush` | Not gated, because tests and the brought-forward guard rely on them. Anything they queue stays untouched under the worker gate. |

**UI:** no change. The only frontend caller is `forceResyncInvoiceAction` (`frontend/app/(app)/org/[slug]/settings/connections/xero/sync-log/actions.ts:129`), used by `XeroStatusChip.handleResync`. No button renders `handleResync` (`frontend/components/invoices/XeroStatusChip.tsx:62`, never referenced in its JSX), so nothing in the UI calls the endpoint today. If a button is ever added, the action already toasts `ApiError.message`, which is the ProblemDetail `detail` (`frontend/lib/api/client.ts:147`): "Kazi does not send fee notes or clients to Xero yet. Nothing was queued."

**Tests, red first** (`AccountingPushDisabledIntegrationTest`, on the default with Xero CONNECTED, so "nothing queued" is the switch rather than the no-connection guard; all assertions by id):
1. approve, send and void queue nothing (checked after each step)
2. client create (`POST /api/clients`) and update (`PUT /api/clients/{id}`) queue nothing
3. the worker leaves pre-existing PENDING INVOICE and CUSTOMER push entries untouched (state, attempt count, error code, external id and `updated_at` all unchanged), while a PENDING PAYMENT_PULL still drains to COMPLETED
4. manual resync → 409 with the ProblemDetail above, and no entry

RED at `734f984ae` (before the gate; log `gate-red-run.txt`): `Tests run: 4, Failures: 3, Errors: 1`. Approve: `[after approve] Expecting empty but was: [AccountingSyncEntry@…]`. Client: `[after create] Expecting empty …`. Resync: `Status expected:<409> but was:<204>`. The worker case shows what the stub path does today: the worker handed the stub to the Xero provider, the provider failed with `Secret not found … :xero:access` inside a transactional bean, and that marked the drain transaction rollback-only. The worker's own catch then could not commit, and `drainForTenant` threw `UnexpectedRollbackException`. One failing push aborts that tenant's drain, and its error state is never saved. This is a further worker defect for the worker-completion work; it is not fixed here. In the same run the enabled listener class (6) and worker class (5) were green.

## Green

Final targeted run on the rebased head (after PR #1859; log `gate-green-rebased-run.txt`; counts are `<testcase>` elements in `target/surefire-reports`): **the same 119 classes, Maven exit 0, and no failure or error line in the log.** The per-testcase count was not captured, because the coordinator's full verify started in this worktree and rewrote `target/surefire-reports` before the count was taken. The run before the rebase was green too: 119 classes, 969 tests, 0 failures, 0 errors, 0 skipped (`gate-green-targeted-run.txt`).

The original fix (before FR-14) was green on 104 classes, 888 tests (`green-targeted-run.txt`).

The set: every test class under `integration/accounting/` (sync + Xero), `invoice/`, and `architecture/` (TenantScopeBindingTest, LayerDependencyRulesTest, TestConventionsTest, TerminologyRemnantScanTest, ZoneAwareTimeArchTest, PortalLinkBuilderArchTest, PortalReadModelOrgScopingGuardTest). It also includes every `*Accounting*Test`, `*Xero*Test`, `Invoice*Test`, `*TakeOn*Test` and `*BroughtForward*Test`, and every test that references the listener or the service (CollectionsPaymentCancellationTest, ClientsTakeOnIntegrationTest, and others). For PR #1860 it adds every whole-codebase scanner: the `@AnalyzeClasses` classes (incl. TakeOnEntryPointsArchTest, TrustPostingRulesTest), the `ClassFileImporter` classes (LegalOnlyProfileReplacementTest, DeadlineCopyRuleTest, the *Without* tests), CollectionsSqlSourceScanTest, WireVocabularyContractTest, McpToolContractTest, plus SchedulerMigrationBatch1ATest and the customer integration tests. The full class list is in `targeted-classes.txt` (passed as `-Dtest`).

**Not run (out of scope per brief):** the full `./mvnw verify` and E2E. The full verify is the merge bar. With the switch off by default, other suites that connect Xero and then create fee notes or clients through the API queue nothing, exactly as before this PR. Only the two classes that turn the switch on get real queue rows, and both assert by id.

## Impact on existing firms

- **Since when:** the listener and the REQUIRED service method arrived together in `5f389ff65` (Epic 518A, #1327, 2026-05-18). The Xero connect flow followed in `cbd68105c` (#1328, 2026-05-18) and `8ccc846b0` (#1335, 2026-05-20). The event route has never queued anything. No test ever covered it: 518A shipped only `AccountingSyncServiceTest` and `AccountingSyncWorkerTest`, which call the service directly.
- **What that means in practice:** nothing usable was lost. Even if an entry had been queued, the worker would have sent a placeholder and not the firm's data, so no firm has ever had correct fee notes or clients pushed to Xero by Kazi. The only push that could reach the worker was a manual fee-note resync. Its stub request has a null issue date, so it fails at `XeroInvoicePayloadMapper.java:45` before any Xero API call (`XeroAccountingProvider.syncInvoice:72` maps before `createOrUpdateInvoice` at `:74`). A resync could therefore never write to Xero. At worst it left a FAILED_RETRYING or DEAD_LETTER entry, or hit the drain abort described above. The "Pending" contact risk the reviewers raised exists only for CUSTOMER pushes, and those came only from the event route, which never committed. Payment polling matches Xero payments only through COMPLETED PUSH entries, so no Kazi fee note has had a Xero payment reconciled back either. I cannot see production, so whether any firm ever used the resync endpoint is unverified.

## Remediation (ruled)

- **FR-12: sync from now on only. No backfill.** Fee notes and clients from before the switch is turned on are not re-sent. The existing per-fee-note resync covers any firm that turns up.
- **FR-14: this PR fixes the enqueue and ships it disabled** (`kazi.accounting.push.enabled=false` everywhere; prod, dev and local all default off).
- **Before anyone turns pushes on**, three things must exist, as planned work of their own:
  1. real fee-note and client payload mapping in `AccountingSyncWorker.dispatchSync`, replacing the placeholders (`XeroInvoicePayloadMapper` / contact mapping wired to the actual invoice, lines and client);
  2. dead-letter recovery, because today a DEAD_LETTER entry blocks every later enqueue for that entity, FORCE_RESYNC included; this needs to be solved together with the `UnexpectedRollbackException` drain abort described above;
  3. an observed run against a Xero sandbox, end to end.
- Worth checking once production is visible: whether any connected firm has FAILED_RETRYING or DEAD_LETTER fee-note entries from manual resyncs. These would block that fee note's later pushes until dead-letter recovery exists.
