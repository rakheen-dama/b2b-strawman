# 631A — browser "after" (observed PASS)

Stack: mock-auth e2e on :3001, backend + frontend rebuilt from `worktree-epic-631A`
(`bash compose/scripts/e2e-rebuild.sh backend frontend`, then `frontend` again after the overflow-menu fix).
Observed 2026-11-04 ~18:04Z via Playwright MCP, 1440x900, Alice (owner).

| # | Step | Observed | Evidence |
|---|------|----------|----------|
| 1 | Acme Corp › overflow › Export Data › Export Now | "Export Ready — **12 files · 8.5 KB** · Download Archive" (was "files · NaN undefined") | `01-export-ready-files-size.png` |
| 2 | fetch the Download Archive `href` | **HTTP 200**, `application/zip`, 8682 bytes, `PK` magic; audit `data.subject.export.generated` fileCount 12 / totalSizeBytes 8682 | `02-archive-download-200.txt` |
| 3 | own client "POPIA Observe 631A" (created through the UI) › overflow › Anonymize | preview renders all seven counts (Portal contacts 1, Matters 0, Documents 0, Time entries 0, Fee notes 0, Comments 0, Custom field values 0) — no TypeError | `03-anonymize-preview-counts.png` |
| 3b | Acme Corp › Anonymize preview (Cancel — seed client untouched) | Portal contacts 1, Matters 1, Fee notes 2, "2 fee notes will be preserved … until 2031-11-04" | `03b-acme-preview-counts.png` |
| 4 | Continue › type the client name | Execute Anonymization enabled | `04-anonymize-confirm.png` |
| 5 | Execute Anonymization | "Anonymization Complete" with the **five** lines: Client anonymized: Yes · Documents deleted: 0 · Comments redacted: 0 · Portal contacts anonymized: 1 · Fee notes preserved: 0. Panel still open 4 s later while the page behind it had already re-rendered as "Anonymized Client 89a35e" (revalidation no longer unmounts it); Close dismissed it, badge "Anonymized" | `05-anonymization-complete-five-counts.png` |

DB audit rows for the anonymised client 89a35e59-…: `customer.created`, `data.subject.export.generated` (pre-anonymisation export), `data.subject.anonymized`.
