# 631A — browser "before" (reproduce-first)

Stack: mock-auth e2e on :3001, built from pre-631A code (630C worktree = main @ 60978d96b equivalent).
Observed 2026-11-04 ~17:49Z via Playwright MCP, 1440x900, Alice (owner), client "Acme Corp"
(`/org/e2e-test-org/clients/edd17447-4eee-43a8-82fe-55e1de804f2e`).

| # | Step | Observed | Screenshot |
|---|------|----------|------------|
| 1 | overflow › Export Data › Export Now | "Export Ready" shows **"files · NaN undefined"**; the "Download Archive" `<a>` has **`href=null`** (read via `browser_evaluate`: `{tag:"A", href:null}`) — G88-F43 reproduced | `01-export-ready-nan-undefined.png` |
| 2 | overflow › Anonymize | Page falls into the error boundary "Something went wrong — Unable to load client data"; console: **`TypeError: Cannot read properties of undefined (reading 'portalContacts')`** — G88-F44 reproduced (preview only; nothing was anonymised) | `02-anonymize-preview-crash.png` |
| 3 | e2e "Execute anonymization" on the stack rebuilt with the flat-shape fix (overflow menu still unchanged) | the completion panel rendered four lines and then **unmounted**: `executeAnonymization` revalidates the client page → `isAnonymized` flips → `client-overflow-menu.tsx` only mounted `<AnonymizeCustomerDialog>` while `!isAnonymized`. Second half of G88-F44 (completion panel), masked until the preview crash was fixed | `03-e2e-completion-panel-unmounted.png` (Playwright failure screenshot: page already "Anonymized Client a1b980", no dialog) |

Red tests (unit/integration): `red-tests.md` (copy of `../631A-red.txt`).
