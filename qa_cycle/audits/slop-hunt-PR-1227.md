# Slop hunt — PR #1227: fix(OBS-301): align matter description maxLength + show field-level errors

**Batch**: C — customer UX
**Reviewed**: 2026-05-01
**Verdict**: NIT

## PR description vs diff

Description honest. Two-part fix matches the diff exactly:
1. Backend `@Size(max=255)` → `@Size(max=2000)` on `InstantiateTemplateRequest.description` to align with `CreateProjectRequest`/`UpdateProjectRequest`.
2. Frontend `instantiateTemplateAction` threads `fieldErrors` through; `NewFromTemplateDialog` renders inline per-field errors.

Verification mentions "spotless clean", "31 ProjectTemplateControllerTest", "13 InstantiateTemplateIntegrationTest", and a new `shouldInstantiateTemplateWithLongDescription` (1500 chars). All cite specific numbers — that's the right shape.

## Findings

| # | Severity | Category | File:line | Finding | Suggested action |
|---|----|---|---|---|---|
| 1 | LOW | Test scope — full verify not cited | PR #1227 description | Backend verification cites two targeted classes (`ProjectTemplateControllerTest`, `InstantiateTemplateIntegrationTest`). The PR predates the PR #1251 lockdown, so this was permissible at the time, but the `@Size` change to a request DTO touches every caller of the endpoint — the targeted scope happened to cover the right surface here (the existing 7+ `InstantiateTemplateIntegrationTest` cases all construct the record), but the convention is the gap. Per Quality Gate rule #5, a `@Size` constraint relaxation that affects bean validation should run full `./mvnw verify`. | No follow-up — change is benign (relaxation, not tightening, so old callers can't break). Note for the bug-class tracker: any DTO `@Size`/`@NotNull`/`@Pattern` edit is a candidate for full verify. |
| 2 | LOW | AI smell — verbose comment | `backend/.../ProjectTemplateControllerTest.java:710-713` | Three comment lines for a 7-line test, two of them duplicating the `@Order` Javadoc and the test name. | Trim opportunistically. |
| 3 | LOW | AI smell — verbose comment | `frontend/.../NewFromTemplateDialog.tsx:71-75, 192-196` | Two 4-line "OBS-301: surface per-field validation errors inline…" blocks repeat the same explanation in adjacent lines. The state declaration and the populate loop don't both need it. | Trim on next touch. |
| 4 | LOW | Type cast assumption | `frontend/app/(app)/org/[slug]/settings/project-templates/actions.ts:198-200` | `error.detail?.fieldErrors as Array<{ field; message }> \| undefined` — bare `as` cast with no runtime validation. If the backend ever changes the ProblemDetail shape, the dialog renders nothing useful but no console error fires either. Low risk because GlobalExceptionHandler is stable, but the more defensive pattern (Zod parse, or `Array.isArray + every(item => 'field' in item)`) costs ~3 lines. | Note for bug-class tracker — same shape-of-error-trust pattern surfaces in many actions.ts files. Worth a one-time hardening pass rather than a per-PR fix. |

## Test scope check

- Targeted: `./mvnw test -Dtest='ProjectTemplateControllerTest'` + `InstantiateTemplateIntegrationTest`. PR description does not claim full `./mvnw verify`.
- Surface area of the change: `InstantiateTemplateRequest.description` constraint. Callers grep'd: `ProjectTemplateControllerTest`, `InstantiateTemplateIntegrationTest`, `PortalProjectSyncFromTemplateIntegrationTest` — all in the same `projecttemplate` package, all should be covered by `'*ProjectTemplate*'` glob.
- Did targeted scope cover surface area? Probably yes. The constraint is a relaxation (255 → 2000), so any test that previously passed a `<= 255`-char description still passes; any test that asserted a 400 on a 256–2000-char description (none exist) would now break. The risk surface was small. **However the convention is the gap** — a clean targeted run should not be cited as the merge gate.
- New `shouldInstantiateTemplateWithLongDescription` test posts 1500 chars and asserts 201 + payload echo — this exercises the new behaviour directly. Good.
- Frontend: full vitest cited (2119 passed). `NewFromTemplateDialog.test.tsx` exists but the diff doesn't show new assertions for the inline `fieldErrors` rendering. Existing tests will not regress, but the new behaviour is uncovered in tests. Nit only.

## Notes

- The `description` column on `Project` is `TEXT` — backend storage is unaffected. Confirmed in PR description and matches schema convention.
- No migration. No frontend lib changes. No scope creep.
- The fix correctly aligns the instantiate path with `CreateProjectRequest`/`UpdateProjectRequest` rather than diverging — that's good cross-API consistency.
- No swallowed exceptions, no status-conditional render hiding a real bug. The 400 branch correctly threads `fieldErrors` through; the 401/403 branches are untouched.
