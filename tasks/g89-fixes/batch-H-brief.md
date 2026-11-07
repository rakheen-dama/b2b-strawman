# Batch H brief: document access control (G89-F52, F53, F54, F56: all HIGH; plus F55)

**Branch:** `fix/g89-h-document-access`, cut from a fresh `origin/main` (includes C2 `c38616dd5`).
**PR title:** `fix(security): trust gate on generated-document downloads, matter access on generate/preview/statements, contract-review document access (G89-F52/F53/F54/F56)`
**Authorisation:** founder D18 and D19 (2026-11-07). This is one access-control PR. F55 is the dead statements `pdfUrl`, included because it is in the same files.

**Gates:**
- backend full verify (the orchestrator runs it)
- frontend gate
- two bound reviews
- CodeRabbit
- **pre-merge legal e2e** (lifecycle + depth-ii + default on fresh stacks), because download behaviour changes
- KC browser check with a **custom role without VIEW_TRUST**. That check also closes the C2 deferral.

Scout report (verified against main `c38616dd5`): the design below. Paths are relative to `backend/src/main/java/io/b2mash/b2b/b2bstrawman/`.

## Access model (keep it)

- **Matters:** `member/ProjectAccessService`. Owner/admin see every matter; others see a matter only through `project_members`.
  - `requireViewAccess` → **404** (`ResourceNotFoundException`, security-by-obscurity).
  - `requireEditAccess` → **403**.
- **Clients:** there is no client-level ACL. Every member sees every client. **Do not invent one.**
- **Capabilities:** `RequestScopes.hasCapability`; owner/admin hold every capability. The system "member" role is seeded with VIEW_TRUST (`V1__baseline.sql:2333-2337`), so the trust leak applies only to **custom roles without VIEW_TRUST**. It is still HIGH (D18).
- **Trust visibility:** `verticals/legal/trustaccounting/TrustDataVisibility`, with `visibleToCurrentMember()` and `withheldFromCurrentMember()`.
- **Status rule:** run the existing matter check first (404). The new trust check comes after it (**403**, `ForbiddenException`). It matches `@RequiresCapability("VIEW_TRUST")` trust endpoints, and it is consistent because the member already sees the row in the list.

## G89-F52: trust gate on downloads

### 1. Flag at generation time: tenant `V14__g89_contains_trust_data.sql`

Confirm V14 is the next free number.

Add `contains_trust_data boolean NOT NULL DEFAULT false` to **`generated_documents` and `documents`** with `ADD COLUMN IF NOT EXISTS`.

When the flag is set to true:
- **Generic path** (`GeneratedDocumentService.generateDocument`, around `:256-301`): `contextMap.get("trust")` is a Map with `status == "available"` **and** the template body uses trust data. The body uses trust data when `TemplateVariableAnalyzer.extractVariableKeys(template.getContent())` returns any key starting `trust.`, or `summary.trust_balance_held`. This avoids over-flagging Power of Attorney, because `CustomerContextBuilder` adds `trust` to every client context.
- **Statement of Account** (`StatementService.generate`, around `:133-165`): `trustDataVisibility.visibleToCurrentMember()` and the summary is not `trustWithheld()`.
- **DOCX** (`generateDocx`, around `:708`, `:793`): the trust block is `available`. This is conservative by design; the body can't be scanned cheaply.
- **Receipts, closure letters, PAIA manual:** stay false.

Copy the flag onto the linked `documents` row in `createLinkedDocument` (around `:965-1036`) and on the SoA `pairedDocument` (`StatementService` around `:175-181`).

**Backfill in V14, idempotent:**
- Set `generated_documents.contains_trust_data = true` when the joined `document_templates` row has `pack_template_key IN ('statement-of-account','client-trust-statement','trust-receipt')`, or `slug = 'statement-of-account'`, or `slug LIKE 'statement-of-account-%'`.
- In both cases also require `COALESCE(context_snapshot->'summary'->>'trust_withheld','false') <> 'true'`.
- Then update `documents` from the `generated_documents.document_id` link.
- Over-flagging pre-#1749 statements is the safe default.

### 2. One rule

Add to `TrustDataVisibility`: `requireMayRead(boolean containsTrustData)`. It throws `ForbiddenException` with title "Trust details are not available" when `containsTrustData && !RequestScopes.hasCapability("VIEW_TRUST")`.

- **Capability only, not the module flag:** a stored file still holds trust data after the module is switched off.
- **Owner/admin** pass, because they hold every capability.

### 3. Apply the rule

- **`GeneratedDocumentService.getDownloadUrl(id)`:** move the logic out of `GeneratedDocumentController` (around `:53-61`) so the controller is thin. Order: getById → matter view check (404) → trust check (403) → presign. Do the same in `getDocxDownloadUrl` (around `:853-867`).
- **`DocumentService`:** add a read-path check, `requireDocumentAccess` plus `requireMayRead(document.isContainsTrustData())`. Call it from `getPresignedDownloadUrl` (around `:550-593`) and `getDocumentBytes` (around `:628-636`). Do **not** apply it to confirm/cancel (upload flows).
- **MCP `DocumentTools.getDocumentUrl`** (around `:143-179`): catch `ForbiddenException` and return `McpError.forbidden()`. Call `McpToolAudit.emitDenied` with gate `"VIEW_TRUST"`.
- **Portal:** leave untouched. The portal path is `PortalQueryService` (`/portal/documents/{id}/presign-download`). Portal requests bind no capabilities, so the trust check must **not** go into `PortalQueryService`. Clients must still get their own shared statements.

### 4. Lists (D19)

- Keep the rows. Add `containsTrustData` to `GeneratedDocumentListResponse` (around `:1105`) and `DocumentResponse`.
- Frontend:
  - In `GeneratedDocumentsList.tsx`, the matter/client Documents lists and `project-statements-tab.tsx`, disable Download (with a tooltip "Trust details are not available") when `containsTrustData && !canViewTrust`.
  - Get `canViewTrust` from the existing capability context the frontend uses for trust nav. Find it; do not invent a new fetch.
  - The server-side 403 stays the real guard.

## G89-F53: matter access on generate / preview / generate-docx

- `template/DocumentTemplateController` (around `:153-180`): `POST /api/templates/{id}/generate`, `/generate-docx` and `/preview` take an entity id and do not check matter access when the entity is a matter.
- Fix: in the service layer, for PROJECT entities, call `projectAccessService.requireViewAccess(entityId, actor)` before building the context (404).
- For INVOICE entities, check access to the invoice's matter(s) if the invoice service already has a rule. Otherwise leave invoices as they are and say so.
- CUSTOMER: no client ACL exists, so no change.
- Covers generic generate, preview and docx.

## G89-F54: statements list/get skip matter membership

- `verticals/legal/statement/StatementController` (around `:38-59`): list, get and generate are gated by `VIEW_LEGAL` / `GENERATE_STATEMENT_OF_ACCOUNT` only.
- Fix: `projectAccessService.requireViewAccess(matterId, actor)` in `StatementService` for list, get and generate (404).

## G89-F55: dead `pdfUrl`

- `StatementService` (around `:272`, `:353`, `:507`) returns `/api/documents/{id}/pdf`, which does not exist.
- Point it at the real download route, `/api/generated-documents/{id}/download`, or drop the field if no consumer reads it. Grep the frontend.
- Test: the returned URL resolves (302) for an authorised member.

## G89-F56: AI contract review reads any document

- `integration/ai/skill/contractreview/ContractReviewSkill.java` (around `:93-99`) calls `documentRepository.findById` with no access check, and the text goes to the LLM.
- Fix: route the read through `DocumentService.getDocumentBytes`, or an equivalent read-checked method, so the matter (404) and trust (403) rules apply.
- Map a denial to the skill's existing error contract; check how other skill errors surface.

## Reproduce-first tests

Commit each red test before its fix.

Fixture setup:
- trust module on;
- a client with a trust deposit;
- a matter;
- member **M** on the matter, with a **custom role** holding VIEW_LEGAL but **not** VIEW_TRUST. Use the pattern in `test:project/MatterListRowIntegrationTest.java:597-620`: `POST /api/org-roles`, then `PUT /api/members/{id}/role`;
- member **N** not on the matter.

New class `GeneratedDocumentTrustAccessIntegrationTest`:
1. Owner generates an SoA. M downloads → **403** (today 302).
2. The same SoA's linked document: M presigns → 403.
3. Owner generates a Client Trust Statement (`saveToDocuments=true`). M downloads → 403; M presigns the client document → 403.
4. Owner generates a Power of Attorney for the same client. M downloads → **302**. This guards against over-flagging.
5. An SoA generated *by M* (trust withheld) has the flag false, so M gets 302.
6. A VIEW_TRUST member gets 302 for case 1. N still gets **404**, so the existing rule stays first.
7. MCP `get_document_url` as M on a flagged document → forbidden, with no URL.
8. Portal safety: share the SoA (PORTAL). The portal contact's presign → 200.

Lists:
- `containsTrustData` is true on the flagged rows and false on Power of Attorney.

**F53:**
- N calls generate, preview and generate-docx on M's matter → 404 (today 200).
- M → 200.

**F54:**
- N lists or gets statements on the matter → 404 (today 200).

**F56:**
- Contract review on a document of a matter N is not on → denied; the document text is never sent. Assert on the AI provider mock or stub that the skill tests already use.

**V14 migration test,** in the style of `test:project/MatterTypeSuffixMigrationTest.java`:
- SoA with `trust_balance_held` → true;
- SoA with `trust_withheld` → false;
- Client Trust Statement → true;
- Power of Attorney → false;
- the linked `documents` rows match.

**Frontend vitest:**
- Download is disabled with a tooltip for a flagged row when `canViewTrust` is false.
- Download is enabled otherwise.

**Tests to update** (from the scout):
- `GeneratedDocumentControllerTest`
- `DocumentIntegrationTest` (presign)
- `DocumentScopeIntegrationTest`
- `SecurityAuditTest`
- the MCP `CatalogueBatch1ToolsTest` / `McpToolContractTest`
- the statement tests
- `ClientTrustStatementContextIntegrationTest`

List every updated test in the PR body.

## Out of scope (record only)

- A client-level ACL. None exists, by design.
- Acceptance-certificate download without a capability. It holds only a hash; low.

## Process

As in the `batch-B-brief.md` "Process" section:
- check `pwd` first;
- commit each red test before its fix;
- run the targeted set:
  - `template/**`
  - `document/**`
  - `verticals/legal/statement/**`
  - `verticals/legal/trustaccounting/**`
  - `mcp/**`
  - `integration/ai/**`
  - `portal/**`
  - `customerbackend/**`
  - `audit/**`
  - `member/**`
  - `architecture/**`
  - `project/WireVocabularyContractTest`
  - plus importers of changed classes;
- run the frontend gate;
- open the PR; no full verify, no merge;
- report back: PR number, head sha, test counts, changed tests, judgment calls.
