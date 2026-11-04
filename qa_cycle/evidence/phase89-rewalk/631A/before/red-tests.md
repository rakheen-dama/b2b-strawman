# 631A red tests (reproduce-first, HEAD 60978d96b)

Raw output: ../631A-red.txt (copied below).

```
## 631A backend red @ 60978d96b 2026-11-04T17:47:16Z (production code untouched; red tests only)
$ ./mvnw test -Dtest="DataExportControllerTest"
[ERROR] Tests run: 8, Failures: 2, Errors: 0, Skipped: 0, Time elapsed: 11.52 s <<< FAILURE! -- in io.b2mash.b2b.b2bstrawman.datarequest.DataExportControllerTest
[ERROR]   DataExportControllerTest.getExportStatus_returns200:184 Status expected:<200> but was:<202>
[ERROR]   DataExportControllerTest.postExport_returns200WithTheCompletedExportStatus:107 Status expected:<200> but was:<202>
[ERROR] Tests run: 8, Failures: 2, Errors: 0, Skipped: 0
(postExport_storageUploadFails_returns500ProblemDetail_andWritesNoAuditRow passes at HEAD: the 500/no-audit semantics already hold; it is a new regression guard, not a red case.)

## 631A frontend red @ 60978d96b 2026-11-04T17:48:15Z (production code untouched; red tests only)
$ NODE_OPTIONS="" pnpm vitest run __tests__/customers/anonymize-customer-dialog.test.tsx __tests__/customers/data-export-dialog.test.tsx
     × renders every preview count from the flat backend record (G88-F44) 1099ms
     × confirm button disabled when input is empty 9ms
     × confirm button disabled when input does not match customer name 9ms
     × executes anonymization and renders the five completion counts from the flat result (G88-F44) 8ms
 Test Files  1 failed | 1 passed (2)
      Tests  4 failed | 3 passed (7)
TypeError: Cannot read properties of undefined (reading 'portalContacts')
    at AnonymizeCustomerDialog (components/customers/anonymize-customer-dialog.tsx:148:68)
(data-export-dialog.test.tsx is green at HEAD: the dialog already reads the 200 shape; G88-F43 is a backend 202/ExportResult defect, red in DataExportControllerTest above.)

## 631A frontend red — completion panel (scratch copy: HEAD-nested preview fixture so the dialog reaches the completion step, flat AnonymizationResult)
$ NODE_OPTIONS="" pnpm vitest run __tests__/customers/zz-scratch-completion-red.test.tsx -t "five completion"   # scratch file deleted after the run
TypeError: Cannot read properties of undefined (reading 'customerAnonymized')
     × executes anonymization and renders the five completion counts from the flat result (G88-F44) 1139ms
    at AnonymizeCustomerDialog (components/customers/anonymize-customer-dialog.tsx:284:56)

## 631A typecheck red @ 60978d96b (the satisfies-typed flat fixtures do not fit the HEAD TS types)
$ pnpm typecheck
__tests__/customers/anonymize-customer-dialog.test.tsx(35,3): error TS2353: Object literal may only specify known properties, and 'portalContacts' does not exist in type 'AnonymizationPreview'.
__tests__/customers/anonymize-customer-dialog.test.tsx(48,3): error TS2353: Object literal may only specify known properties, and 'customerAnonymized' does not exist in type 'StandaloneAnonymizationResult'.
__tests__/customers/data-export-dialog.test.tsx(35,3): error TS2353: Object literal may only specify known properties, and 's3Key' does not exist in type 'StandaloneExportResult'.

## 631A frontend red 2 — completion panel unmounted by revalidation (found by the un-fixme'd e2e) @ 60978d96b+631A-wip 2026-11-04T17:59:20Z
e2e (rebuilt :3001 stack, flat-shape fix in, overflow menu unchanged): 'Execute anonymization' saw 'Client anonymized: Yes', 'Documents deleted', 'Comments redacted', 'Portal contacts anonymized' and then the alertdialog vanished:
    Expected pattern: /Fee notes preserved: \d+/
    Error: element(s) not found
Cause: executeAnonymization revalidatePath(/org/{slug}/clients/{id}) -> page re-renders with isAnonymized=true -> client-overflow-menu.tsx renders <AnonymizeCustomerDialog> only when showAnonymize (isOwner && !isAnonymized) -> dialog unmounted mid-completion.
$ NODE_OPTIONS="" pnpm vitest run __tests__/components/customers/client-overflow-menu.test.tsx   # overflow menu unchanged
     × keeps an open Anonymize dialog mounted when the client becomes ANONYMIZED 45ms
TestingLibraryElementError: Unable to find an element by: [data-testid="anonymize-customer-dialog"]
      Tests  1 failed | 7 passed (8)
```
