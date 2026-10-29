# 628.19 repo-wide grep — `grep -rnE '\b(Projects?|Customers?|Invoices?)\b'` @ cb10c5d63

| Tree | Raw hits | Classification |
|---|---|---|
| frontend/app + components + lib | 38 (16 files) | 2 production lines, both comments (`CustomFieldSection.tsx:457`, `lib/constants/entity-types.ts:2`); 36 in `__tests__`/`*.test.*` — fixtures or negative assertions (`queryByText("Invoices")).not…`). **User-visible: 0.** |
| portal/app + components + lib | 13 (8 files) | 4 production lines, all comments (`profile/page.tsx:32-33`, `lib/auth.ts:6`, `lib/types.ts:91`); 9 test fixtures/negative assertions. **User-visible: 0.** |
| backend/src/main | 632 (182 files) | 413 Java identifiers (types, JPQL entity names), 122 Java comments, 80 Java string literals, 17 resource files. Literal/resource breakdown below. |

Guard allow-lists: frontend 9 entries, portal 0, backend 2 (ADR-357 REASONS set).

## Backend string literals + resources (97) — by kind
- **Reconcile `from` values (old text being replaced, not rendered):** `automation-templates/common.json:44,58,72,79,107`, `template-packs/common/pack.json:9,16,23`, `field-packs/common-project.json:9,23`, `StandardReportPackSeeder.java:68,92` — 12.
- **Log lines (`log.*` / SLF4J format strings):** 33 (AccountingSyncService ×7, ProjectService ×3, CustomerContextBuilder ×2, ProjectContextBuilder ×3, ChecklistInstanceService ×2, InvoiceEmailService ×2, PortalContactAutoProvisioner ×2, ReportService ×2, DataAnonymizationService, CustomerLifecycleService, PortalEventHandler, PortalResyncService, InvoiceEmailEventListener, PaymentReconciliationService, MatterClosureNotificationHandler, StatementOfAccountContextBuilder, TrustTransactionService).
- **JPQL entity names:** 14 (ProjectRepository ×5, InvoiceRepository ×6, CustomerRepository ×2, CalendarService, InvoiceLineRepository, TaxRateRepository).
- **External-API / AI-only text:** Xero `/Invoices` paths + response keys ×4; AI prompt lines (CollectionReminderSkill ×3, ComplianceDataCollectorService ×2, `ai/skills/*` ×3); MCP descriptions ×3 (R8 note 6: deliberate, excluded).
- **Statutory / comments in resources:** `compliance-audit/system.txt:10` "Customer Due Diligence" (FICA s21 statutory term); SQL comments ×2; yml comment ×1.
- **Internal programming guards (`IllegalArgument/IllegalState` on null ids; not reachable from the API):** `Expense.java:219`, `LegalDisbursement.java:255`, `RequestScopes.java:117`, `LegalDemoDataSeeder.java:611`.
- **Dead fallbacks:** `NotificationService.java:100`, `EmailContextBuilder.java:93,95` `getOrDefault("Invoice","Invoice")` — `EmailTerminology.resolve()` returns legal nouns for every namespace (628D follow-up, already registered). `EmailTerminology.java:23-24` are the from→to keys.
- **Payments mandate exemption:** `StripePaymentGateway.java:82`, `PayFastPaymentGateway.java:81` ("Invoice N" item name on the PSP checkout), `templates/portal/mock-payment.html:30` (mock PSP page) — CLAUDE.md §6 exemption.

## Residual user-visible sites NOT in the R8 census (routed, not fixed in the gate slice — CLAUDE.md §7)
1. **`datarequest/ProcessingActivityService.java:84,97`** — `seedJurisdictionDefaults("ZA")` seeds the POPIA processing register rows "Financial Records — *Invoices*, payment records, billing information" and category "*Project* Documentation — Documents and correspondence related to engagements". Rendered on Settings › Firm › Data protection (`components/data-protection/processing-register-table.tsx`). Rows are keyed by `category` (`existsByCategory`), so a rename on existing tenants needs a reconcile (628C/ADR-356 shape) — new scope.
2. **`integration/accounting/sync/TrustBoundaryGuard.java:61`** — "Invoice is flagged as trust-related (is_trust_invoice=true)" → `markBlockedTrustBoundary(reason)` → `SyncLogTable.tsx:139-144` `lastErrorDetail` (Integrations › sync log). R8 row 98 covered `:125` in the same class, not `:61`.
3. **`member/ProjectMemberService.java:109`, `budget/BudgetCheckService.java:129`** — `orElse("Unknown Project")` fallback project name feeding notification text; only reachable if the project row vanished inside the same transaction (effectively dead).
4. **`automation/executor/UpdateStatusActionExecutor.java:72`** — ActionFailure "Customer status update not yet supported" (automation run log) — reachable only for an UPDATE_STATUS action targeting CUSTOMER.
