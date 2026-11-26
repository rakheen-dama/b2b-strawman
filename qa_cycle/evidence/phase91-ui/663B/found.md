# 663B — found outside scope (reported, not fixed; root CLAUDE.md §7)

| Id | Found by | Site | Finding | Suggested owner |
|---|---|---|---|---|
| F-663B-1 | Review B (architecture), PR #1866 | `frontend/components/audit/audit-timeline.tsx:174` | §18.3.3 lists this as a page-level tab panel, which holds for the matter and client Audit tabs. The same component also renders inside the fee-note detail's "Audit trail" section (`components/invoices/invoice-audit-section.tsx:21-23`, which the JSDoc rule says should be `compact`) and in a small `p-3` box in `components/projects/closure-history-section.tsx:143-146` (`compact` or `inline`). One literal variant cannot be right in every container. Fix: pass a `variant` (`emptyVariant`) prop down through `AuditTimelineTab`/`AuditTimeline`. 663B keeps `default` as specified, so nothing visible changes. Whether the fee-note timeline is ever empty in practice is unverified. | 666A (fee-note detail) or 669B; kinks register follow-up list |
