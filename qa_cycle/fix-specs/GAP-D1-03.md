# Fix Spec: GAP-D1-03 -- Onboarding checklist "Upload signed engagement letter" blocks ACTIVE transition

## Problem

The generic-onboarding compliance pack seeds a checklist item "Upload signed engagement letter" with `requiresDocument: true`. When a new client transitions from PROSPECT to ONBOARDING, this checklist auto-instantiates. The item cannot be completed because:

1. The client has no documents yet (just created).
2. The frontend shows a "Select a document..." dropdown with no options.
3. The backend rejects completion without a documentId (`ChecklistInstanceService.completeItem()` line 222).
4. All 4 items are marked `required: true`, so the checklist never completes.
5. The manual "Activate" button also fails because `CustomerLifecycleService` checks all checklists are complete.

This cascades to block matter creation (CustomerLifecycleGuard blocks CREATE_PROJECT for non-ACTIVE customers) and all Day 1+ test steps.

## Root Cause (confirmed)

Two issues combine:

**Root cause A (primary blocker):** `backend/src/main/resources/compliance-packs/generic-onboarding/pack.json` item 4 has `requiresDocument: true` but there is a chicken-and-egg problem -- a freshly created client in ONBOARDING status has no documents, so the requirement can never be satisfied without first uploading a document through a separate flow.

**Root cause B (contributing):** `backend/src/main/java/io/b2mash/b2b/b2bstrawman/checklist/ChecklistInstanceService.java` line 222 enforces the document requirement with no escape hatch. The backend correctly validates this, so the fix must be in the pack definition, not the validation logic.

## Fix

Change the generic-onboarding pack's "Upload signed engagement letter" item to `requiresDocument: false`. The item description already says "Obtain and upload" -- making it a manual confirmation step is consistent with the other 3 items in the generic pack (all are confirmation-style, none require documents).

The FICA-specific legal-za-onboarding pack correctly has document requirements (ID copy, proof of address, etc.) because those are regulatory requirements. The generic pack should not impose document requirements -- it is a lightweight onboarding flow.

### File Changes

**File:** `backend/src/main/resources/compliance-packs/generic-onboarding/pack.json`

Change item 4:
```json
{
  "name": "Upload signed engagement letter",
  "description": "Obtain and upload the signed engagement letter from the client.",
  "sortOrder": 4,
  "required": true,
  "requiresDocument": false,
  "requiredDocumentLabel": null,
  "dependsOnItemKey": null
}
```

### Database cleanup

Existing checklist instances created from the generic-onboarding template will still have the old `requiresDocument=true` on their instance items. Since the E2E stack is rebuilt between cycles, this is not an issue for QA. For production (if this pack were ever deployed), a data migration would be needed -- but this pack has never been deployed to production, so no migration is required.

**Post-fix:** After rebuilding the E2E stack, the newly seeded generic-onboarding checklist will have 4 confirmation-only items. All can be completed without documents.

## Scope

- 1 file: `backend/src/main/resources/compliance-packs/generic-onboarding/pack.json`
- 2 lines changed (requiresDocument: true -> false, requiredDocumentLabel: "Signed engagement letter" -> null)
- No Java code changes
- No frontend changes
- No migration needed (E2E stack rebuilt)

## Verification

1. Rebuild E2E stack: `bash compose/scripts/e2e-rebuild.sh backend`
2. Login as Alice, create a new client, transition to ONBOARDING
3. Generic checklist instantiates with 4 items
4. Complete all 4 items -- item 4 should NOT show document dropdown
5. Checklist completes, client auto-transitions to ACTIVE
6. Verify matter creation works on the now-ACTIVE client

## Estimated Effort

15 minutes (pack JSON edit + E2E rebuild + verification)
