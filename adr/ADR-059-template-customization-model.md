# ADR-059: Template Customization Model

**Status**: Accepted

**Context**: The document template system ships platform-standard templates via template packs (seeded per-tenant at provisioning, following the field pack pattern from Phase 11). Orgs will want to customize these templates — changing the wording, adjusting the layout, adding org-specific sections. The question is how org customizations interact with platform templates: can orgs edit platform templates directly, or must they create a separate copy?

The answer affects platform update safety (can a platform update overwrite org customizations?), data provenance (is a template platform-standard or org-modified?), and the "Reset to Default" experience (how does an org undo their customizations and return to the platform version?). The field pack system from Phase 11 faces a similar question — field definitions are per-tenant copies that can be independently modified — and its design informs this decision.

**Options Considered**:

1. **Clone-and-edit (separate ORG_CUSTOM record)** — When an org wants to customize a platform template, they "clone" it: a new `ORG_CUSTOM` record is created with `source_template_id` pointing to the original `PLATFORM` record. The org edits the clone freely. The platform original is hidden (not shown in the active template list) when a clone exists for the same `pack_template_key`. "Reset to Default" deletes the clone, restoring the platform original.
   - Pros:
     - Platform templates are immutable per-tenant. Platform updates (new packs, updated template content) can be applied without risk of overwriting org customizations.
     - Clear provenance: every template is either `PLATFORM` (unmodified) or `ORG_CUSTOM` (org-created or cloned). The `source_template_id` FK traces the lineage.
     - "Reset to Default" is a simple operation: delete the `ORG_CUSTOM` record. The `PLATFORM` record is always present and unmodified. No need to store "original content" separately.
     - Audit trail is clean: the audit log shows "template cloned" and "template updated" as distinct events. An auditor can see exactly when the org diverged from the platform standard.
     - Works identically to the field pack customization model (Phase 11) — consistent pattern across the platform.
     - Safe for concurrent edits: admins edit the clone, the platform original is never locked or contended.
   - Cons:
     - Data duplication: the clone contains a full copy of the template content (~5-50KB). At 1,000 tenants with 3 cloned templates each, this is ~150MB — trivial.
     - The listing logic must filter out platform templates that have been cloned (precedence logic). This adds a small amount of service-layer complexity.
     - Renaming a platform template doesn't automatically rename org clones. The clone preserves the name at clone time (which can be independently changed by the org).

2. **In-place edit of PLATFORM records** — Allow orgs to directly modify `PLATFORM` template records. The original content is stored in a separate column (e.g., `original_content`) or a shadow table to support "Reset to Default."
   - Pros:
     - Simpler data model: one record per template per tenant, no clone chain.
     - No list-filtering logic — every template is a single record.
     - Less data duplication — only modified templates store extra content (the original).
   - Cons:
     - Platform updates are dangerous: if a platform update changes the content of a `PLATFORM` template, it could overwrite an org's modifications. The update process must detect whether the template has been modified before applying updates — comparing `content` to `original_content`.
     - "Reset to Default" requires maintaining the original content somewhere — either a shadow column (`original_content`) or a separate table. This is additional storage and complexity.
     - Data provenance is ambiguous: a `PLATFORM` template that has been modified is neither truly "platform" nor "org-custom." A boolean `modified` flag helps, but the semantics are muddier than two distinct records with explicit sources.
     - The audit trail is less clear: "template updated" on a `PLATFORM` record could mean a platform update or an org edit. Additional metadata is needed to distinguish.
     - If an org modifies a platform template and a platform update ships new content, the merge strategy is unclear. Do we skip the update? Force-overwrite with a warning? Present a diff? All options are complex.
     - Doesn't follow the field pack pattern from Phase 11, which uses per-tenant copies.

3. **Layer/overlay model (platform base + org patch)** — Store org customizations as diffs/patches against the platform template. At render time, apply the patch to produce the final content.
   - Pros:
     - Minimal data duplication: only the diff is stored.
     - Platform updates to unchanged sections propagate automatically — only the patched sections are preserved.
     - Intellectual appeal: "the org only overrides what they changed."
   - Cons:
     - HTML is not diff-friendly. A small structural change (wrapping a section in a new `<div>`) can produce a large, fragile diff that breaks when the platform template is updated.
     - Merge conflicts: when a platform update touches the same section that the org patched, the overlay breaks silently (incorrect merge) or noisily (render error). Both are bad UX.
     - Complexity is disproportionate to the benefit. Templates are 5-50KB of HTML — full copies are cheap. The engineering effort to build and maintain a reliable HTML diff/patch system far exceeds the storage cost of full copies.
     - Template authors cannot see their final output without applying the patch — the editing experience is confusing.
     - No precedent in the codebase — field packs use full copies, not patches.

**Decision**: Clone-and-edit with separate ORG_CUSTOM records (Option 1).

**Rationale**: Clone-and-edit provides the clearest separation between platform and org content. The `PLATFORM` record is immutable once seeded — it serves as the "factory default" that is always available for reset. The `ORG_CUSTOM` clone is the org's space to customize freely. This model is simple to implement, simple to explain to users ("Clone this template to customize it, Reset to restore the original"), and consistent with the field pack pattern from Phase 11.

The data duplication concern is negligible. Templates are small text documents. Even at aggressive scale, the storage overhead of full copies is measured in megabytes — invisible against PostgreSQL's storage capacity.

In-place editing (Option 2) was rejected because it creates an ambiguous state: a `PLATFORM` record that has been modified is neither platform-standard nor org-custom. Platform update safety requires comparing content to detect modifications, and "Reset to Default" requires maintaining original content — both of which are inherent in the clone model without extra mechanism.

The overlay model (Option 3) was rejected because HTML is a poor target for diff/patch. The engineering complexity of reliable HTML merging is not justified by the negligible storage savings. The user experience of editing a diff rather than a complete document is significantly worse.

**Consequences**:
- `DocumentTemplate.source` is `PLATFORM` or `ORG_CUSTOM`. Platform records are immutable per-tenant (created by seeder, not editable via API).
- `DocumentTemplate.sourceTemplateId` (nullable FK) links an `ORG_CUSTOM` clone to its `PLATFORM` original.
- Clone operation: `POST /api/templates/{id}/clone` creates a new `ORG_CUSTOM` record copying content, CSS, and metadata from the platform original.
- Reset operation: `POST /api/templates/{cloneId}/reset` hard-deletes the `ORG_CUSTOM` record. The `PLATFORM` original becomes visible again in listings.
- Template listing logic: when both a `PLATFORM` template and an `ORG_CUSTOM` clone exist for the same `pack_template_key`, only the clone is shown. This filtering happens in the service layer, not in SQL (to keep the query simple and the logic testable).
- Future platform template updates: the `TemplatePackSeeder` can update `PLATFORM` records (content, CSS) without affecting `ORG_CUSTOM` clones. Orgs that have cloned and customized are unaffected. Orgs that have not cloned see the updated platform version immediately.
- The template management UI clearly distinguishes `PLATFORM` (with "Clone" action) from `ORG_CUSTOM` (with "Edit" and optionally "Reset to Default" actions) using source badges.
