# ADR-338: Terminology Layer Deleted — Literal Legal Copy, a Single-Vocabulary Rule, Lint + Test Enforcement; "Tasks" Not "Action Items", "Mandate" Only on the Engagement-Letter Flow

**Status**: Accepted

**Context**:

Phase 55 built the terminology layer so one skeleton could serve three verticals: `packages/shared/src/terminology-map.ts` (`TERMINOLOGY_BASE`, [ADR-182](ADR-182-terminology-override-mechanism.md) / [ADR-185](ADR-185-terminology-switching-approach.md) — static map in the frontend bundle), `frontend/lib/terminology.tsx` (`TerminologyProvider`, `useTerminology`, `t()`, `withIndefiniteArticle` — LZKC-003), `components/terminology-heading.tsx`, `components/terminology-text.tsx` (`{Key}` / `{a Key}` templates), `auditTabLabel()`, and `portal/lib/terminology.tsx` with `PORTAL_TERMINOLOGY`. Phase 86 trimmed the map to one profile (**92 lines**, `PORTAL_TERMINOLOGY === TERMINOLOGY`) and kept the mechanism for Phase 87 ([ADR-332](ADR-332-single-vertical-profile-mechanism-retained.md)). The census at HEAD `41cb211fb` (`.arch-context-frontend.md` §3): **262 real usages in 82 files** (frontend 72, portal 10) — `t("Word")` ×133 + placeholder/`audit.tab` keys ×8, `useTerminology(` ×55, `<TerminologyText` ×48, `<TerminologyHeading` ×12, `withIndefiniteArticle(` ×3, `auditTabLabel(` ×3; 55 files import `@/lib/terminology`. A raw grep says 315/94 because **53 `createMessages().t("ns.key")` calls** in `lib/messages/index.ts` consumers share the name `t` — a different, dotted-key i18n loader with five JSON namespaces (`help`, `empty-states`, `errors`, `getting-started`, `common`). The map still carries `Task → Action Item`, dead `Proposal → Engagement Letter` and `Retainer → Mandate` entries (features deleted in 86), and `TerminologyProvider` is mounted at `frontend/app/(app)/org/[slug]/layout.tsx:119` and `portal/app/(authenticated)/layout.tsx:38`. Six test files exist only to exercise the mechanism; 17 more assert legal words through it.

The founder decision: *delete the terminology layer; legal words are written directly into copy; no runtime substitution remains* (`requirements/claude-code-prompt-phase87.md`). Two label decisions ride with it: **Tasks are "Tasks"** (Clio, LEAP and AJS all say Tasks; the `Action Item` override is dropped) and **"Mandate"** attaches to the engagement-letter acceptance flow, where [ADR-336](ADR-336-engagement-letter-document-plus-acceptance.md) B1 left it for this phase. The ADR-332 deviation — the one-option vertical-profile picker in `components/settings/vertical-profile-section.tsx` on `settings/general` (closure F-B3) — is removed here too; the profile mechanism itself stays.

**Options Considered**:

1. **Keep the map, collapsed to legal-only** (the post-86 status quo).
   - Pros:
     - Zero edits in 82 files; no test churn; `TerminologyProvider` keeps working.
     - A future second vertical could be re-added by adding a map entry.
   - Cons:
     - Every page keeps translating: `t("Project")` in a product that has no projects is indirection with one branch.
     - The `t` name collision with `createMessages().t` (53 false positives) already confuses greps and reviewers.
     - `verticalProfile` prop threads, `TerminologyProvider` mounts and six mechanism-only test files survive for nothing.
     - The founder decision is explicit that none remains.
     - ADR-330's archive tag already preserves the multi-vertical mechanism.
     - Keeping it live is a second copy.
2. **Delete the layer; literal legal copy; lint + test enforcement (CHOSEN)** — delete `terminology-map.ts`, `lib/terminology.tsx`, both components, `withIndefiniteArticle`, `auditTabLabel`, `PORTAL_TERMINOLOGY`, `portal/lib/terminology.tsx`, the two provider mounts and every real call site (262/82); drop `verticalProfile` threads that only fed terminology (`portal/app/(authenticated)/layout.tsx`); rewrite `empty-states.json` templates and `help.json` generic copy; delete the six mechanism-only test files and turn the 17 legal-word assertions into plain assertions. Enforce with ESLint `no-restricted-imports` for `@/lib/terminology`, `@/components/terminology-heading`, `@/components/terminology-text`, `@b2mash/shared/terminology-map` (and the portal equivalents), plus a vitest "no terminology remnant" test that greps `frontend/` + `portal/` source for `useTerminology|TerminologyText|TerminologyHeading|terminology-map|withIndefiniteArticle|auditTabLabel`, plus a one-paragraph rule in `frontend/CLAUDE.md`.
   - Pros:
     - Copy says what the product is; reviewers read "Matters" in the source, not `t("Projects")`.
     - Removes 55 imports, two providers, one shared module and six test files.
     - `@b2mash/shared` shrinks to formatting.
     - Enforcement is cheap and mechanical: the lint rule fails the import, the test fails the string, `format:check` is unaffected.
     - `createMessages()` is left alone, so no i18n regression and no confusion between the two `t`s remains once the terminology `t` is gone.
   - Cons:
     - 82-file edit with judgement per site (plural, article, casing — e.g. `{a Key}` templates must become the right article by hand).
     - The grep test is a blunt instrument: a comment containing `useTerminology` would fail it (acceptable; the rule is "the word does not appear").
     - A second vertical would need a real i18n layer later (option 3) — this ADR does not provide one.
3. **Replace with a proper i18n message catalogue** — extend `createMessages()` (dotted keys, `{{var}}` interpolation, five namespaces) into a full catalogue and route every label through it.
   - Pros:
     - One mechanism for all copy; locale-ready (`// TODO: add locale parameter` already sits in `lib/messages/index.ts`).
     - Would resolve the `t` naming collision by making it the only `t`.
   - Cons:
     - Phase 87 is an IA phase for a single-locale, single-vertical product.
     - A catalogue migration of every heading, dialog and empty state is a larger edit than the deletion and reintroduces indirection (`t("matters.heading")`) the founder just asked to remove.
     - No design partner is non-English.
     - `en-ZA` is the only locale in `legal-za.json`.
     - Would have to be done in the same PRs as the terminology removal to avoid a double edit — scope creep across all 82 files (CLAUDE.md §7).
     - `createMessages()` stays as it is for the long-form copy it already serves.
     - Promoting it is a separate decision when a second locale exists.
4. **Keep `t()` as a thin identity shim** — `t = (s) => s`, delete the map and components, keep call sites.
   - Pros:
     - Smallest diff; call sites untouched; tests keep passing.
   - Cons:
     - A no-op function at 141 sites is the definition of a runtime substitution layer the decision forbids.
     - Keeps the `t` collision and the `useTerminology` hook.
     - Invites the next contributor to put a map behind it again.
     - `<TerminologyText template="…{projects}…">` cannot be a no-op — the template tokens still need rewriting, so most of the work is done anyway.

**Decision**: Option 2 — delete the terminology layer in full and write legal copy literally, enforced by `no-restricted-imports`, a vitest remnant test and a `frontend/CLAUDE.md` rule; "Tasks" replaces "Action Items"; "Mandate" is used only as copy on the engagement-letter acceptance surfaces; the single-option vertical-profile picker is removed with it.

**Rationale**:

1. **One vertical, one vocabulary.** After [ADR-332](ADR-332-single-vertical-profile-mechanism-retained.md) there is one profile and the map has one key. The founder's judgement — "we can design a better UI without having a generic structure that fits everything" — applies to copy as much as to navigation; a map with one branch is indirection without a decision behind it. The multi-vertical mechanism is preserved at `horizontal-platform-final` ([ADR-330](ADR-330-archive-tag-not-fork.md)) if ever needed.
2. **The wire rename makes the layer redundant for the two nouns and the labels are leaf copy.** With `/api/matters` and `Matter` types ([ADR-337](ADR-337-wire-rename-two-nouns-entities-stay.md)), `t("Project")` has nothing left to translate; `Invoice → Fee Note`, `Expense → Disbursement`, `Budget → Fee Estimate`, `Time Entry → Time Recording`, `Rate Card → Tariff Schedule` are a few dozen headings and dialog titles — cheaper to write than to keep mapping.
3. **Tasks are Tasks.** "Action Item" was an override no legal PM tool uses; every attorney calls them tasks, and the MCP tool is `propose_task`. Dropping the override also removes the only non-noun override that leaked into test names.
4. **Mandate is a flow word, not a nav word.** ADR-336 B1 reserved it for the engagement-letter/acceptance path. The rule here: "Mandate" may appear as copy on the acceptance surfaces of an engagement-letter `GeneratedDocument` (document-row status, acceptance request, certificate and portal acceptance copy — exact strings fixed in the slice with the `frontend-design` skill) and nowhere else — never a nav label, route segment, entity, type or settings family. It does not label `AcceptanceRequest` generically, because acceptance also serves non-engagement documents.
5. **Enforcement must be mechanical.** CLAUDE.md quality gates require observed green; a lint rule and a vitest test are the only things that survive the next 50 PRs:
   - ESLint `no-restricted-imports` for `@/lib/terminology`, `@/components/terminology-heading`, `@/components/terminology-text`, `@b2mash/shared/terminology-map` and the portal equivalents (fails at `pnpm lint`);.
   - `frontend/__tests__/no-terminology-remnants.test.ts` (and its portal twin `portal/__tests__/no-terminology-remnants.test.ts`) greps `frontend/` + `portal/` source for `useTerminology|TerminologyText|TerminologyHeading|terminology-map|withIndefiniteArticle|auditTabLabel` and fails on any hit (fails at `pnpm test`);.
   - the `frontend/CLAUDE.md` rule below, so the next agent reads the rule before writing a heading.

   The `frontend/CLAUDE.md` paragraph (verbatim, replacing the current "Terminology and date/currency formatting come from `@b2mash/shared`" line):
   > **Single vocabulary.** Kazi is legal-only. Write the legal word in the source — Matter, Client, Fee note, Disbursement, Tariff, Time recording, Fee estimate, Task, Diary. There is no runtime substitution layer: no `useTerminology`, no `t("Project")`, no `<TerminologyText>`, no terminology map in `@b2mash/shared`. ESLint `no-restricted-imports` and `__tests__/no-terminology-remnant.test.ts` enforce this. Long-form help/empty-state copy still comes from `lib/messages` (`createMessages(ns).t("dotted.key")`), which is an i18n loader, not a terminology layer. "Mandate" is used only on engagement-letter acceptance surfaces.

**Consequences**:

- Positive:
  - 262 call sites become plain strings.
  - `@b2mash/shared` loses `terminology-map.ts`.
  - `frontend/__tests__/{terminology,terminology-integration,lzkc-terminology-cluster,lzkc-031-terminology-residuals}.test.tsx`, `portal/lib/__tests__/terminology-provider.test.tsx` and `portal/components/__tests__/portal-sidebar.test.tsx` (legal-only form) are deleted or reduced to plain assertions.
  - `OrgProfileProvider` / `useOrgProfile` keep `enabledModules` + `isModuleEnabled` (module gating is independent of terminology).
  - `verticalProfile` threads that feed packs, platform-admin billing/demo and `lib/schemas/demo-provision.ts` stay.
  - `components/settings/vertical-profile-section.tsx` and the "Apply Profile" control on `settings/general` go (closure F-B3).
  - `settings/general/profile-actions.ts` is deleted if the picker was its only caller (confirm at build time).
  - `GET /api/profiles` stays for `useOrgProfile`.
- Negative:
  - An 82-file hand edit with per-site judgement on plurals and articles.
  - The slice (S5) must run full vitest for frontend and portal plus `format:check`, and the legal Playwright configs because headings are locators.
  - `empty-states.json` `{projects}`/`{customers}` tokens and `help.json`'s generic copy ("project overrides", "charge your clients", "INV-001") are rewritten in the same slice — otherwise the help panel reads agency while the headings read legal.
  - Portal `labelKey` placeholders (`portal.nav.*`, no `next-intl`) remain a fake i18n indirection.
  - `portal-sidebar.tsx:70` `t(PORTAL_NAV_LABELS[labelKey] ?? labelKey)` becomes a direct label lookup — the `labelKey` scheme is kept (not this ADR's scope) but no longer passes through `t`.
  - A future second locale or vertical starts from literal English copy and must adopt option 3 at that time; this is the accepted trade.
- Follow-ups:
  - `portal/lib/nav-items.ts` single-entry `profiles: ["legal-za"]` and `filterNavItems`'s profile branch are dead after this (closure F-E) — remove in the portal vocabulary slice (S10) if no other consumer exists.
- Related:
  - [ADR-182](ADR-182-terminology-override-mechanism.md) / [ADR-185](ADR-185-terminology-switching-approach.md) (superseded by this ADR), [ADR-332](ADR-332-single-vertical-profile-mechanism-retained.md) (mechanism kept, picker now removed), [ADR-336](ADR-336-engagement-letter-document-plus-acceptance.md) (where "Mandate" was parked), [ADR-337](ADR-337-wire-rename-two-nouns-entities-stay.md) (the nouns the map no longer needs to translate), [ADR-339](ADR-339-attorney-week-navigation-and-one-diary.md) (nav labels written literally), [ADR-330](ADR-330-archive-tag-not-fork.md).
