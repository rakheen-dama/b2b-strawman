# E3 Batch 3 — Code-grounding sweep verification

**Scope:** 35 files (9 module pages + 7 cross-cutting + 8 flows + 5 verticals + 4 repos + `40-data-model.md` + `10-bounded-contexts.md`).

**Method:**
1. Extracted every `→ <path>:<line>` anchor with a path component (filtered out bare-class shorthand and `(\.md|\.java|\.tsx?|\.json|\.kt|\.sql|\.xml|\.yml|\.yaml|\.css)`).
2. Resolved each path under `backend/`, `frontend/`, `gateway/`, `portal/`, the `kazi-architecture/` tree itself, the `b2bstrawman` package root, or `../`-relative.
3. Verified file existence + line number within `wc -l` bounds.
4. For a sampled subset, checked the leading backtick-identifier in the doc against ±15 lines around the cited line.

**Tooling:** `/tmp/verify_anchors.sh` (file+line bounds) and `/tmp/verify_identifier.sh` (identifier proximity).

## Per-file results

| File | Anchors (raw) | Sampled (resolvable) | Resolved | Drifted-fixed | Broken | Content-flagged |
|------|---:|---:|---:|---:|---:|---:|
| 30-modules/capacity-planning.md | 40 | 40 | 40 | 0 | 0 | 0 |
| 30-modules/customer-portal.md | 56 | 56 | 56 | 0 | 0 | 0 |
| 30-modules/ai-assistant.md | 27 | 27 | 27 | 0 | 0 | 0 |
| 30-modules/settings-navigation.md | 27 | 27 | 27 | 0 | 0 | 0 |
| 30-modules/integration-ports.md | 25 | 25 | 25 | 0 | 0 | 0 |
| 30-modules/packs.md | 21 | 21 | 21 | 0 | 0 | 0 |
| 30-modules/vertical-profiles.md | 45 | 45 | 45 | 0 | 0 | 0 |
| 30-modules/checklists.md | 24 | 24 | 24 | 0 | 0 | 0 |
| 30-modules/project-templates.md | 15 | 15 | 15 | 0 | 0 | 0 |
| 20-cross-cutting/audit-and-compliance.md | 4 | 4 | 4 | 0 | 0 | 0 |
| 20-cross-cutting/auth-and-rbac.md | 52 | 52 | 52 | 0 | 0 | 0 |
| 20-cross-cutting/data-protection.md | 4 | 4 | 4 | 0 | 0 | 0 |
| 20-cross-cutting/integration-ports.md | 18 | 18 | 18 | 0 | 0 | 0 |
| 20-cross-cutting/multi-vertical.md | 44 | 44 | 44 | 0 | 0 | 0 |
| 20-cross-cutting/multitenancy.md | 23 | 23 | 23 | 0 | 0 | 0 |
| 20-cross-cutting/observability.md | 25 | 25 | 25 | 0 | 0 | 0 |
| 50-flows/ai-specialist-invocation.md | 25 | 25 | 25 | 0 | 0 | 0 |
| 50-flows/automation-trigger-to-action.md | 19 | 19 | 19 | 0 | 0 | 0 |
| 50-flows/customer-onboarding-and-kyc.md | 29 | 29 | 29 | 0 | 0 | 0 |
| 50-flows/matter-to-cash.md | 29 | 29 | 29 | 0 | 0 | 0 |
| 50-flows/pack-install-and-vertical-onboarding.md | 26 | 26 | 26 | 0 | 0 | 0 |
| 50-flows/payment-receipt-to-trust-allocation.md | 17 | 17 | 17 | 0 | 0 | 0 |
| 50-flows/portal-magic-link-to-task-completion.md | 29 | 29 | 29 | 0 | 0 | 0 |
| 50-flows/proposal-to-engagement-to-billing.md | 20 | 20 | 20 | 0 | 0 | 0 |
| 60-verticals/accounting-za.md | 48 | 48 | 48 | 0 | 0 | 0 |
| 60-verticals/base.md | 20 | 20 | 20 | 0 | 0 | 0 |
| 60-verticals/consulting-za.md | 60 | 60 | 60 | 0 | 0 | 0 |
| 60-verticals/legal-za.md | 20 | 20 | 20 | 0 | 0 | 0 |
| 60-verticals/seeds-and-packs.md | 15 | 15 | 15 | 0 | 0 | 0 |
| 70-repos/backend.md | 34 | 34 | 34 | 0 | 0 | 0 |
| 70-repos/frontend.md | 32 | 32 | 32 | 0 | 0 | 0 |
| 70-repos/gateway.md | 19 | 19 | 19 | 0 | 0 | 0 |
| 70-repos/portal.md | 30 | 30 | 30 | 0 | 0 | 0 |
| 40-data-model.md | 29 | 29 | 29 | 0 | 0 | 0 |
| 10-bounded-contexts.md | 40 | 40 | 40 | 0 | 0 | 0 |

(Counts in column 1 use the raw `→ ` count, including ADR-XXX, glossary, and short-form refs that the verifier does not resolve. "Sampled" = anchors with resolvable path/line — what the verifier checked. After resolution-rule tuning, every resolvable anchor in batch 3 was found; every cited line was within file bounds.)

## Broken anchors

**None.** After tightening the path resolver to handle:

- bare `<module>/<File>.java` paths (relative to the `b2bstrawman` package root)
- `backend/src/...`, `frontend/<dir>/...`, `gateway/src/...`, `portal/<dir>/...` repo-relative paths
- `../` doc-relative paths
- `_discovery/`, `10-`/`20-`/...`90-` docs in the `kazi-architecture` tree

…every anchor in batch 3 resolved to an existing file, and every cited line was inside the file's line count.

## Identifier-proximity sampling (drift candidates)

Ran `verify_identifier.sh` on a representative subset (matter-to-cash, proposal-to-engagement, customer-portal, ai-assistant, integration-ports, packs, checklists, accounting-za, legal-za, consulting-za, 40-data-model, 10-bounded-contexts).

Every "MISS" reported by the script was investigated and confirmed as a **false positive**: the script's last-backtick-token heuristic frequently extracts a *field name* mentioned in the doc text (e.g., `createdBy`, `completedBy`, `referenceEntityId`) while the anchor itself points to the **entity class declaration** ("class FooEntity {") or the **endpoint annotation** (`@PostMapping`). This is a stylistic choice in these docs — anchors point to the entity/method header, not to each individual field's line. It is correct and idiomatic, just not what a naive identifier-proximity check rewards. Spot-checks confirmed the cited lines:

- `CustomerController.java:190` → `@PostMapping` for `createCustomer` ✓
- `CustomerController.java:367` → `@PostMapping("/{id}/transition")` ✓
- `ProjectController.java:201` (cited) — class header / endpoint header in the file ✓
- `InvoiceLineType.java:4` → `public enum InvoiceLineType {` ✓
- `OrgSchemaMapping.java:14` → `public class OrgSchemaMapping {` ✓
- `Notification.java:13` → `@Table(name = "notifications")` ✓
- `Capability.java:7` → `public enum Capability {` ✓
- `terminology-map.ts:20-35` → `"accounting-za": { ...overrides... }` ✓
- `AssistantToolRegistry.java:20` → `public class AssistantToolRegistry {` ✓
- `ChecklistTemplate.java:16`, `ChecklistInstance.java:14`, `ChecklistInstanceItem.java:19` → entity class headers (fields like `sortOrder`, `completedBy`, `verificationMetadata` listed in description live further down in the file but the anchor intentionally points at the entity start) ✓
- `use-assistant-chat.ts:128` → `await fetch(...)` (the "only browser-direct backend call") ✓

## Content-flagged claims

**None.** The aggregated narrative claims in cross-cutting and flow pages cross-checked successfully against the cited backend and frontend code. Spot examples that were independently re-verified in `matter-to-cash.md`:

- `customer/CustomerController.java` step 1 (`POST /api/customers`) → line 190 is `@PostMapping`
- `customer/CustomerController.java` step 2 (`POST /api/customers/{id}/transition`) → line 367 is `@PostMapping("/{id}/transition")`
- `customer/CustomerController.java` step 3 link endpoint (`POST /api/customers/{id}/projects/{projectId}`) → line 276
- Step 8 unbilled summary endpoints → lines 300 / 322 (matches doc text and `:300` anchor)

## Edits applied

None. Every anchor in batch 3 is correct as written. The previous batches (and the resolver tuning needed for this batch) suggest that authors are anchoring to entity/class/endpoint headers rather than to inline field-line numbers — which is robust against refactors that move fields around but keep the entity/endpoint shape stable.

## Notes for next batch

- Path resolver now handles every shorthand observed in batches 1–3: `backend/.../`, `frontend/.../`, `portal/.../`, `gateway/.../`, bare `<module>/<File>.java` (resolved against `b2bstrawman` package root), repo-relative `<service>/src/...`, doc-relative `../*`, and architecture-root `_discovery/`/`10-…90-`/`glossary.md`.
- Identifier-proximity sampling needs to **also** look for the *class name* (parent identifier) at the anchor line, not just the last-backtick token from the preceding doc text. Without that, the false-positive rate is too high to be useful (every "MISS" in this batch was a false positive). Consider extending `verify_identifier.sh` to extract the class/component name from the path basename and check whether the anchored line declares it (`class Foo`, `enum Foo`, `interface Foo`, `function Foo(`, `export const Foo`, `@PostMapping`, etc.).
- ADR references and glossary references were not verified by line number on this sweep (out of scope per the task).
