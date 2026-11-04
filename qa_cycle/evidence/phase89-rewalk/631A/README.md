# 631A evidence — POPIA export 200 + anonymise flat shapes + wire-shape contract pins

- Red (reproduce-first, HEAD 60978d96b): `631A-red.txt` (also `before/red-tests.md`)
- Browser before: `before/README.md` — `before/01-export-ready-nan-undefined.png`, `before/02-anonymize-preview-crash.png`, `before/03-e2e-completion-panel-unmounted.png`
- Browser after: `after/README.md`

## G88-F43 — PASS (observed)
:3001 Acme Corp › Export Data → "12 files · 8.5 KB · Download Archive"; the href GETs **200** `application/zip` (8682 bytes, `PK`), matching the `data.subject.export.generated` audit row (fileCount 12, totalSizeBytes 8682).
Evidence: `after/01-export-ready-files-size.png`, `after/02-archive-download-200.txt`.

## G88-F44 — PASS (observed)
:3001 own client › Anonymize → preview renders the seven counts (no TypeError); Acme Corp preview renders Matters 1 / Fee notes 2 + retention note; Execute → completion panel renders the five counts and stays open across the server revalidation until Close.
Evidence: `after/03-anonymize-preview-counts.png`, `after/03b-acme-preview-counts.png`, `after/04-anonymize-confirm.png`, `after/05-anonymization-complete-five-counts.png`.

## E2E
`frontend/e2e/tests/customers/customer-data-protection.spec.ts` (3 fixmes removed): 5 passed, two independent runs on the rebuilt :3001 stack.
