# 625F (G-B13 / U7 / D4): reproduction before the fix

**Verdict: REPRODUCED** (2026-10-27 ~17:09 UTC, e2e mock-auth stack, backend built from main ≈ e7bf0e386 with 625E merged)

## Environment
- Org `e2e-test-org` → tenant schema **`tenant_7d218705360b`** (`public.org_schema_mapping`)
- Users: Alice Owner `bf835b97-…` (owner), Bob Admin `e3166722-…` (admin), Carol Member `9756676c-…` (member)
- Upload target: matter **"Ndlovu v RAF op0p"** `02496c55-b778-4da3-8902-f1b779aab555`. Alice is `lead` (lower-case, so U1 casing does not apply here) and Carol is `member`.

## Current DB state of the rule (these are the reconcile `from` values)
| field | value |
|---|---|
| rule id | `19dc292f-6044-4001-97c1-b15844b15a78` |
| template_slug | `document-review-notification` |
| name | `Document Review Notification` |
| description | `Notifies project owner when a document is accepted` |
| enabled | **true** |
| trigger_type | `DOCUMENT_ACCEPTED` (trigger_config `{}`, conditions `[]`) |
| source | `TEMPLATE` |
| source_pack_install_id | `71d882c5-3f35-4ab3-bc4a-d351bc7c13f2` (automation-common) |
| content_hash | `bfbe0ab9d5ea2957ad81657e082286ab593cca2e4f612294cc73d30f893b4b29` |
| action id | `37e71985-8ad3-480d-b513-8711d5e6c0da`, sort 0, `SEND_NOTIFICATION` |
| action title | `Document accepted: {{document.name}}` |
| action message | `A document has been accepted in project {{project.name}}.` |
| action recipientType | `PROJECT_OWNER` |

Pack versions: DB `pack_install` has `automation-common` at **pack_version `2`** (item_count 6, installed 15:10:18), and `org_settings.automation_pack_status` records automation-common v2 (appliedAt 16:44:38, from 625E's gate re-run). The classpath file `backend/src/main/resources/automation-templates/common.json` has `packId=automation-common`, **`version: 2`**. DB and classpath versions are equal, so the fix needs a bump to v3 to make the reconcile run. `automation-legal-za` is also at v2 and does not contain this rule.

UI label: under Settings › Connections › Automations (`/settings/connections?tab=automations`) the row reads "Document Review Notification", described as "Notifies project owner when a document is accepted". The trigger badge says **"Document Accepted"** (`frontend/components/automations/trigger-type-badge.tsx:14`) and the toggle is **ON**.

## Steps (driven in the browser with Playwright MCP)
1. Logged in as Alice and opened Settings › Connections › Automations. The rule is enabled and its trigger is "Document Accepted" (`01-settings-automations-rule-enabled.png`). Alice had 7 unread notifications.
2. Logged in as Carol, opened the matter's Work · Documents tab and uploaded `625F-repro-upload.pdf` with the drop zone. The row shows status **"Uploaded"** (`02-carol-upload-matter-documents.png`). DB: document `82f0d000-…` has `status=UPLOADED`, visibility INTERNAL. Nobody accepted, reviewed or approved it.
3. Logged in as Alice. The bell went from 7 to **9 unread**, and the top two entries are "Carol Member uploaded "625F-repro-upload.pdf"" and **"Document accepted: 625F-repro-upload.pdf"** (`03-alice-bell-document-accepted.png`, `04-alice-notifications-page.png`).
4. The Execution Log shows "Document Review Notification", trigger `DocumentUploadedEvent`, status Completed, 1 action (`05-execution-log.png`).

## Rule-firing evidence (`after-upload-db.txt`)
- `automation_executions` `6742c4fd-609b-4fbb-b2fe-bd9f7631ac7c`: rule `19dc292f-…`, trigger_event_type `DocumentUploadedEvent` (`eventType: document.uploaded`, actor Carol Member), conditions_met true, status `ACTIONS_COMPLETED`.
- `action_executions` `bb5c5fa6-…`: action `37e71985-…`, COMPLETED, result `{"notificationsSent": 1}`.
- `notifications` `36e1e393-c2ae-43e8-9815-c399d6d221a5`: recipient **Alice Owner** (the matter lead), type `AUTOMATION`, title **`Document accepted: 625F-repro-upload.pdf`**, body `A document has been accepted in project {{project.name}}.`
- The cause is the mapping `TriggerTypeMapping.java:35-37`, `DocumentUploadedEvent → DOCUMENT_ACCEPTED` ("upload is treated as acceptance").

## Surprises / side findings (outside 625F scope unless the orchestrator decides otherwise)
1. **The body placeholder is not resolved.** `{{project.name}}` reaches the notification body as literal text. `AutomationContext.buildDocumentAccepted` (`AutomationContext.java:278-289`) only puts `project.id` into the context, never `project.name`. A renamed rule that keeps `{{project.name}}` in its message will still show the raw placeholder. The builder should drop it from the new message or note this as a separate gap.
2. **The lead gets the same upload twice.** The native `DOCUMENT_SHARED` notification "Carol Member uploaded "625F-repro-upload.pdf"" (`77a15082-…`) already tells Alice about the upload, so the automation duplicates it. This supports D4's choice to seed the rule disabled.
3. **No email.** Mailpit total stayed at 22, so both notifications were in-app only.
4. **`last_run_at` stays NULL** on the rule after a successful execution, so the "Last run" column never updates from executions. This is pre-existing and unrelated.
5. The automation notification has `reference_entity_type=AutomationRule` but `reference_entity_id` holds the **project** id. This is pre-existing and cosmetic.
6. On this matter the recipient resolved to exactly one user (the lead), because the lead role is stored in lower case. The register's "double recipient" half of G-B13 (U1 casing → admin/owner fallback) did **not** show up here.

## Files
- `before-db.txt`: rule, action and pack_install rows, org_settings.automation_pack_status, all rules, baseline counts, Mailpit baseline
- `after-upload-db.txt`: document, execution, action-execution and notification rows after the upload, plus counts after
- `01-settings-automations-rule-enabled.png`, `02-carol-upload-matter-documents.png`, `03-alice-bell-document-accepted.png`, `04-alice-notifications-page.png`, `05-execution-log.png`
