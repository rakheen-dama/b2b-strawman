# ADR-122: Content Migration Strategy

**Status**: Accepted

**Context**:

Phase 31 changes document template content from Thymeleaf HTML (TEXT) to Tiptap JSON (JSONB), and clause bodies from HTML (TEXT) to Tiptap JSON (JSONB). Existing content must be migrated. There are three categories of content:

1. **Platform content** (3 template packs, 12 system clauses) — we control the exact content, can hand-convert to JSON.
2. **Org-custom content** (user-created or cloned templates/clauses with potentially arbitrary HTML + Thymeleaf expressions) — we don't control the content.
3. **Generated documents** (already-generated PDFs with context/clause snapshots) — these are historical records.

The system is multi-tenant (schema-per-tenant, Flyway tenant migrations run per schema). Each tenant may have different amounts of custom content.

**Options Considered**:

1. **Clean cut with legacy fallback** — Single migration (V48): platform content re-seeded from new JSON pack files, org-custom content best-effort converted via PL/pgSQL, unconvertible content stored in `legacy_content`/`legacy_body` columns, UI shows "migration needed" badge for unconvertible items. No dual rendering paths. Old Thymeleaf code deleted entirely.
   - Pros:
     - Clean codebase (one renderer, one format), no maintenance burden of dual paths
     - Honest UX — users know when content did not convert, rather than silently receiving degraded output
     - Legacy columns preserve original HTML for manual recovery; original is never deleted
     - Platform content is zero-risk (hand-converted and verified via visual regression tests)
     - Eliminates the Thymeleaf SSTI attack surface entirely
   - Cons:
     - Org-custom HTML may not convert perfectly for every edge case
     - Users must manually re-author unconvertible content in the new editor
     - No way to preview the old rendering after migration completes

2. **Dual-support period (feature flag)** — Maintain both Thymeleaf and TiptapRenderer for N months. Feature flag per template: `renderEngine: "thymeleaf" | "tiptap"`. New templates default to tiptap. Existing templates stay on thymeleaf until manually migrated. Eventually deprecate and remove the thymeleaf path.
   - Pros:
     - Zero risk of content loss; users can migrate at their own pace
     - Can A/B compare rendering output between the two engines
   - Cons:
     - Double the rendering code (both engines maintained simultaneously)
     - Double the test surface
     - Feature flag complexity — which templates use which engine must be tracked and queried
     - Migration never actually completes in practice; users procrastinate on working content
     - Thymeleaf SSTI attack surface persists indefinitely
     - Defeats the purpose of Phase 31, which is to eliminate Thymeleaf from document rendering entirely

3. **Forced re-authoring (no auto-conversion)** — Migrate platform content only. All org-custom templates and clauses are reset to an empty editor with a "Re-author your content" message. Original HTML is viewable in a read-only panel for reference but not preserved in the primary columns.
   - Pros:
     - Cleanest migration with no conversion bugs possible
     - Users become familiar with the new editor by re-authoring
     - No legacy column needed
   - Cons:
     - Destroys user work (even if viewable), constituting a breaking change for active users
     - Terrible UX for tenants with many custom templates
     - High support volume expected; punishes the most engaged users who customized the most content

4. **Pre-migration validation + manual intervention queue** — Run a pre-migration analysis that categorizes all org-custom content by conversion difficulty. Simple content auto-converts; complex content is queued for manual review. An admin dashboard shows migration status per template.
   - Pros:
     - Granular control and admin visibility into migration health
     - Complex content receives human attention before cutover
   - Cons:
     - Over-engineered for the actual content volume (most tenants have 0-3 custom templates)
     - Requires building a one-time migration dashboard UI
     - Delays the cutover; queue management adds coordination complexity

**Decision**: Option 1 — Clean cut with legacy fallback.

**Rationale**:

The dual-support period (Option 2) defeats the purpose of Phase 31. The entire goal is eliminating Thymeleaf from document rendering. Maintaining two renderers doubles the test surface, keeps the SSTI attack surface alive, and experience consistently shows that feature-flag migrations never complete — users do not voluntarily migrate content that is working today.

The clean cut works because the actual risk is smaller than it appears:

- **Platform content (3 templates, 12 clauses)** is zero-risk. We hand-convert it and verify via visual regression tests before the migration ships.
- **Org-custom content** is typically simple. Users clone platform templates and change a few words, or write short custom clauses. The two-phase converter (SQL classification + Jsoup-based application-layer import) handles all standard HTML block and inline elements plus the common Thymeleaf variable pattern (`th:text="${key}"` → variable node). The realistic conversion success rate for "simple" content is 80–90%.
- **Unconvertible content** is never deleted. The original HTML is preserved in `legacy_content` / `legacy_body` columns. Users see a "migration needed" badge and can re-author in the new editor with the original visible alongside it.
- **Volume is low.** Most tenants have zero to five custom templates. The support burden for manual re-authoring of the remainder is manageable.

Forced re-authoring (Option 3) is unnecessarily destructive given that a best-effort converter will handle the majority of custom content correctly. The pre-migration queue (Option 4) is over-engineered for the actual content volumes involved and delays the cutover without proportionate benefit.

**Consequences**:

- V48 migration adds `content_json JSONB`, `body_json JSONB`, `legacy_content TEXT`, and `legacy_body TEXT` columns to the relevant tables
- Migration is two-phase: (A) PL/pgSQL function in V48 wraps org-custom HTML in a `legacyHtml` node, classifying content as "simple" or "complex" based on structural analysis; (B) `LegacyContentImporter` service runs at application startup, using Jsoup to parse "simple" HTML and convert it to proper Tiptap JSON nodes (`p` → paragraph, `h1`–`h3` → heading, `strong`/`em`/`u` → text marks, `a` → link mark, lists, tables, `hr`, `br`, `th:text="${key}"` → variable nodes)
- Platform content: `content_json` / `body_json` set to NULL initially, then re-seeded by updated `TemplatePackSeeder` and `ClausePackSeeder` using the new JSON format
- Org-custom content: "simple" HTML (no structural elements, no Thymeleaf loops/conditionals, no inline styles) is converted by the application-layer importer; "complex" HTML remains as `legacyHtml` node for manual re-authoring
- Original TEXT columns (`content`, `body`) are dropped after conversion; legacy columns are preserved permanently as the safety net
- Frontend shows a "migration needed" badge whenever a template or clause JSON contains a `legacyHtml` node type; clicking the badge opens the original HTML in a read-only reference panel
- Generated documents (historical records) are unchanged — they store rendered HTML and PDF output, not template source, so no migration of generated document rows is required
- No rollback path exists for the column type change; the legacy columns are the safety net and must not be dropped
- Thymeleaf rendering code and all associated tests are deleted after the migration is verified; no dual rendering path is introduced or maintained
- Related: [ADR-120](ADR-120-document-storage-format.md), [ADR-121](ADR-121-rendering-pipeline-architecture.md)
