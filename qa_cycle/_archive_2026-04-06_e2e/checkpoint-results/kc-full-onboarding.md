# Full Customer Onboarding QA — Document Upload Verified

**Date**: 2026-03-24
**Branch**: `bugfix_cycle_kc_2026-03-23` (merged to main)
**Stack**: Keycloak dev (Frontend:3000, Backend:8080, Gateway:8443, Keycloak:8180)
**Auth**: Thandi Thornton (owner) via Keycloak org-scoped JWT + Playwright UI
**Customer**: Naledi Corp QA (id=4160e3cb-73cc-4d83-8bf2-84eade31b85d)

## Summary

Complete customer onboarding flow tested end-to-end with **real document upload** for the engagement letter checklist item. This was the one flow that was previously skipped (C4 used `skip` for the doc-required item).

## Results

| Step | Test | Result | Evidence |
|------|------|--------|----------|
| 1 | Customer starts in PROSPECT | **PASS** | API: `lifecycleStatus=PROSPECT`. UI: "Prospect" badge shown. Screenshot: `step1-naledi-prospect.png` |
| 2 | Transition PROSPECT -> ONBOARDING | **PASS** | API: POST `/api/customers/{id}/transition` with `targetStatus=ONBOARDING`. Response: `lifecycleStatus=ONBOARDING`. UI: "Onboarding" badge appeared, Onboarding tab visible, checklist 0/4. Screenshot: `step2-onboarding-checklist.png` |
| 3 | Complete first 3 checklist items | **PASS** | API: PUT `/api/checklist-items/{id}/complete` for items 1-3 (Confirm client engagement, Verify contact details, Confirm billing arrangements). All returned `status=COMPLETED`. Customer still ONBOARDING (4th item pending). |
| 4a | Document guard blocks completion without doc | **PASS** | API: PUT complete without documentId -> HTTP 400 "This item requires a document upload. Please upload: Signed engagement letter". Guard works correctly. |
| 4b | Initiate customer-scoped document upload | **PASS** | API: POST `/api/customers/{id}/documents/upload-init` with `fileName=Naledi_Corp_QA_Engagement_Letter_Signed.pdf`. Returned presigned S3 URL (LocalStack). documentId=89852791-67d2-444f-b38b-53cf2b4bf391. |
| 4c | Upload file content to S3 | **PASS** | PUT to presigned URL with PDF content -> HTTP 200. File stored in LocalStack S3 bucket `docteams-dev`. |
| 4d | Confirm document upload | **PASS** | API: POST `/api/documents/{id}/confirm` -> `status=UPLOADED`, `scope=CUSTOMER`, `customerId` correct, `uploadedBy=Thandi Thornton`. |
| 5 | Complete engagement letter item WITH documentId | **PASS** | API: PUT `/api/checklist-items/{id}/complete` with `documentId`. Response: `status=COMPLETED`, `documentId=89852791-...`. Item now links to the uploaded document. |
| 6 | Auto-transition ONBOARDING -> ACTIVE | **PASS** | After all 4/4 checklist items completed, customer auto-transitioned to `lifecycleStatus=ACTIVE`. Checklist instance `status=COMPLETED`. No manual transition needed. UI shows dual "Active"/"Active" badges. Screenshot: `step6-active-status.png` |
| 7 | Create project for ACTIVE customer | **PASS** | API: POST `/api/projects` with customerId -> HTTP 201. Project "QA Onboarding Verified Project" created and auto-linked. Lifecycle guard lifted (PROSPECT previously blocked this). |
| 7b | Document visible in UI Documents tab | **PASS** | UI: Documents tab shows 1 document: `Naledi_Corp_QA_Engagement_Letter_Signed.pdf` (1.0 KB, Uploaded, Internal). Screenshot: `step7-documents-tab.png` |

## Key Findings

1. **Document-required guard works correctly**: Attempting to complete a `requiresDocument=true` checklist item without a `documentId` returns HTTP 400 with clear error message including the required document label.

2. **Customer-scoped document upload flow is fully functional**: The three-step flow (upload-init -> PUT to presigned URL -> confirm) works correctly for CUSTOMER-scoped documents. S3 key pattern: `org/{orgAlias}/customer/{customerId}/{docId}`.

3. **Auto-transition cascade works**: Completing the 4th checklist item triggers: item marked COMPLETED -> checklist instance marked COMPLETED -> lifecycle auto-transition ONBOARDING -> ACTIVE. No manual intervention needed.

4. **Lifecycle guard lifted on ACTIVE**: PROSPECT customers cannot create projects (verified in C4). After full onboarding to ACTIVE, project creation succeeds immediately.

5. **"Start Onboarding" UI button is an anchor link**: The button at `#lifecycle-transition` only scrolls the page; it doesn't trigger the transition. The actual transition must be done via the "Change Status" dropdown or API. This is minor UX — the button's purpose is to draw attention to the lifecycle section.

## Screenshots

- `step1-naledi-prospect.png` — Customer detail page showing PROSPECT status
- `step2-onboarding-checklist.png` — After ONBOARDING transition, showing 0/4 checklist and Onboarding tab
- `step6-active-status.png` — Customer showing dual Active/Active badges after auto-transition
- `step7-documents-tab.png` — Documents tab showing uploaded engagement letter PDF, 1 project linked

## API Calls Made

```
POST /api/customers/{id}/transition        {"targetStatus":"ONBOARDING"}
PUT  /api/checklist-items/{id}/complete     {"notes":"..."} x3 (items 1-3)
PUT  /api/checklist-items/{id}/complete     {"notes":"..."} (without documentId — 400 expected)
POST /api/customers/{id}/documents/upload-init  {"fileName":"...","contentType":"application/pdf","size":1024}
PUT  {presignedUrl}                         (file content to S3/LocalStack)
POST /api/documents/{id}/confirm
PUT  /api/checklist-items/{id}/complete     {"notes":"...","documentId":"89852791-..."} (item 4)
GET  /api/customers/{id}                    (verify ACTIVE)
GET  /api/customers/{id}/checklists         (verify 4/4 COMPLETED)
POST /api/projects                          {"name":"...","customerId":"..."} (verify guard lifted)
GET  /api/documents?scope=CUSTOMER&customerId={id}  (verify document listed)
```

## Conclusion

The full customer onboarding flow with document upload is **fully functional** on the Keycloak dev stack. All guards, auto-transitions, and document linking work as designed. This closes the gap from the previous QA cycle where the document-required checklist item was skipped.
