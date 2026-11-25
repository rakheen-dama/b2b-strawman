# F-658A-1: Xero push never queued on fee note approve, send or void

Founder ruling FR-11: investigate and fix. Branch `fix/f658a1-accounting-push-not-queued` (local commits only: no push, no PR, no full verify, no E2E).

**Status: REPRODUCED and FIXED; the fix passes the targeted tests. MERGED-AWAITING-VERIFY does not apply yet, because the branch is not merged. A full `./mvnw verify` is still needed before merge.**

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
5. `POST /api/customers` → one PENDING EVENT CUSTOMER entry

**RED on unchanged code** (commit `cd32d2394`, log `red-run.log`): `Tests run: 5, Failures: 5`, and every failure reads:

```
java.lang.AssertionError:
Expected size: 1 but was: 0 in:
[]
	at ...AccountingSyncEventListenerIntegrationTest.approvingAnOrdinaryFeeNote_queuesAPendingXeroPush_inTheFirmsSchema(AccountingSyncEventListenerIntegrationTest.java:96)
```

**Root cause, observed rather than inferred.** Test logging is ERROR-only, so the approve case was re-run with `-Dlogging.level.org.springframework.transaction=DEBUG`, `...orm.jpa.JpaTransactionManager=DEBUG` and `...integration.accounting=DEBUG` (log `red-debug-run.log`, excerpt `red-debug-excerpt.txt`):

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

- `94d752af4`: the fee-note handlers (approve, sent, voided). After this commit the reproduction was 4/5 green, with only the client case red (`step2-invoice-fix-run.log`).
- `88eddf1bb`: the client create/update handlers. This is the same defect in the same listener, kept as its own commit so it can be dropped if the client half is ruled out of scope (root CLAUDE.md §7).

## Green

Targeted set (log `green-targeted-run.log`; counts are `<testcase>` elements in `target/surefire-reports`): **104 classes, 888 tests, 0 failures, 0 errors, 0 skipped.** The new class is 5/5.

The set: every test class under `integration/accounting/` (sync + Xero), `invoice/`, and `architecture/` (TenantScopeBindingTest, LayerDependencyRulesTest, TestConventionsTest, TerminologyRemnantScanTest, ZoneAwareTimeArchTest, PortalLinkBuilderArchTest, PortalReadModelOrgScopingGuardTest). It also includes every `*Accounting*Test`, `*Xero*Test`, `Invoice*Test`, `*TakeOn*Test` and `*BroughtForward*Test`, and every test that references the listener or the service (CollectionsPaymentCancellationTest, ClientsTakeOnIntegrationTest, and others). The full class list is in `targeted-classes.txt` (passed as `-Dtest`).

**Not run (out of scope per brief):** the full `./mvnw verify` and E2E. The full verify is the merge bar. Other tests that connect Xero and then create fee notes or clients through the API will now get real queue rows. I grepped every test that creates an `AccountingXeroConnection`, and all of them are in the targeted set. A global-count assertion elsewhere could still surface only in the full verify.

## Impact on existing firms

- **Since when:** the listener and the REQUIRED service method arrived together in `5f389ff65` (Epic 518A, #1327, 2026-05-18). The Xero connect flow followed in `cbd68105c` (#1328, 2026-05-18) and `8ccc846b0` (#1335, 2026-05-20). The event route has never worked. No test ever covered it: 518A shipped only `AccountingSyncServiceTest` and `AccountingSyncWorkerTest`, which call the service directly.
- **Who:** any tenant that has had a CONNECTED Xero connection, from the time that build was deployed. For those tenants, no fee note approved, sent or voided in Kazi was ever queued for Xero, and no client created or updated in Kazi was ever pushed. The only entries that could exist are FORCE_RESYNC ones from `POST /api/integrations/sync/invoice/{id}/resync`. A knock-on effect: payment polling matches Xero payments only through COMPLETED PUSH entries (`AccountingSyncService.pollPaymentsForConnection`), so payments were never reconciled back for those fee notes either.
- **Not checked:** I cannot see production, so I do not know whether any live firm has connected Xero. Detection query per tenant schema: `SELECT i.id, i.invoice_number, i.status FROM <schema>.invoices i JOIN <schema>.accounting_xero_connection c ON c.status = 'CONNECTED' WHERE i.status <> 'DRAFT' AND i.take_on_batch_id IS NULL AND i.updated_at >= c.connected_at AND NOT EXISTS (SELECT 1 FROM <schema>.accounting_sync_entry e WHERE e.entity_type = 'INVOICE' AND e.entity_id = i.id)`. The column names were checked against the tenant schema (V1 baseline + V37), but the query has never been run. A firm that disconnected and reconnected has a later `connected_at`, so widen the window for that case.

## Remediation proposal (founder to rule; nothing was backfilled)

Option A, the default proposal: after this fix is deployed, run the detection query per tenant and report counts. For each affected firm, re-queue the missed fee notes with the existing force-resync endpoint (or a one-off admin job calling `enqueueInvoicePush(id, FORCE_RESYNC)`; it is idempotent and skips brought-forward notes), and the missed clients with `enqueueCustomerPush`.

Open questions for the ruling:
1. Should VOID fee notes that Xero never saw be pushed at all, or skipped?
2. Should a firm that has since keyed these fee notes into Xero by hand be excluded, to avoid duplicates in Xero? This probably needs a check with the firm first.

Option B: do nothing retroactively. Fee notes are queued from the next approve, send or void onwards.
