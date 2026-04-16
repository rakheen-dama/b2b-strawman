# ADR-242: "Never Used" Uninstall Rule

**Status**: Accepted

**Context**:

Phase 65 introduces the ability to uninstall Kazi Packs — removing all content rows that a pack created. The uninstall gate determines when this operation is permitted. The design must balance user flexibility (allowing removal of unwanted content) against data safety (preventing loss of content that has been integrated into the tenant's workflow).

Pack content becomes tenant-owned data at install time. Document templates can be edited, cloned (via `source_template_id`), and used to generate documents (tracked in `generated_documents`). Automation rules can be edited, enabled/disabled, and executed (tracked in `automation_executions`). Each of these interactions creates implicit or explicit dependencies that make removal destructive.

The question is: under what conditions should uninstall be allowed, and what happens to content that does not pass the gate?

The uninstall gate applies per-pack (all-or-nothing). Partial uninstall — removing some items from a pack while keeping others — is explicitly out of scope. The gate must be deterministic: given the current database state, it always returns the same answer. There is no "override" or "force uninstall" option.

**Options Considered**:

1. **Soft delete with archive** -- Uninstall moves all pack content to an "archived" state (e.g., `active = false`, `archived = true`). Content is hidden from normal views but preserved in the database. A "restore" action re-enables it.
   - Pros:
     - Zero data loss — content can always be restored
     - No gate needed — uninstall is always allowed because nothing is deleted
     - Simple UX: "Uninstall" = "Hide from my workspace"
   - Cons:
     - Archived content still occupies database space. Over time, tenants accumulate hidden content that they never restore
     - Archived templates may still be referenced by `generated_documents` (which reference `template_id`). Archiving the template does not remove the reference — the generated document still points to a now-hidden template. This creates UI confusion: "This document was generated from a template that no longer exists" (even though it does, it is just archived)
     - `source_template_id` clone chains become confusing: a visible template cloned from an archived template shows a broken lineage
     - Soft delete is a deferred decision, not a decision. The tenant eventually wants the content truly gone, and the same gate question resurfaces at that point
     - Adds `archived` state management (filtering, UI indicators, restore flow) across templates and automation rules — cross-cutting changes to existing features

2. **Cascading hard delete with warnings** -- Uninstall hard-deletes all pack content regardless of usage. Before deletion, the UI shows warnings about what will be affected (e.g., "3 generated documents reference templates in this pack"). The user confirms and the deletion proceeds. Generated documents with dangling `template_id` references are handled with null checks.
   - Pros:
     - Simple implementation — no gate, no edit detection, no reference checks at delete time
     - Maximum user control — the tenant decides what to remove
     - Clean database state after uninstall — no orphaned or archived rows
   - Cons:
     - Data loss is irreversible. A tenant who uninstalls a pack containing an edited template they have been using loses their customizations permanently
     - Cascading effects are hard to enumerate. A template might be referenced by generated documents, cloned by other templates, referenced in project configurations, or used in proposal flows. Enumerating all possible references at warning time requires querying every referencing table — brittle and incomplete as new features add new references
     - Dangling references (null `template_id` on generated documents) corrupt the data model. Every downstream consumer must handle the null case, which is error-prone and produces confusing UI ("Generated from: Unknown template")
     - Does not match user intent. Users who want to "clean up" unwanted pack content typically mean "remove the stuff I never used." Cascade-deleting content they actively use is a misfire

3. **Hard delete gated by "never used" rule** -- Uninstall is permitted only if every content row created by the pack is in its original, unmodified, unreferenced state. Specifically: (a) content hash matches the hash captured at install time (unedited), (b) no downstream entity references the row (no generated documents for templates, no executions for automation rules), and (c) no clone references the row (no `source_template_id` pointing to it). If any condition fails, the uninstall is blocked with a specific reason. No partial uninstall, no override.
   - Pros:
     - Zero data loss by construction. If the gate passes, every content row being deleted is identical to what was installed and has never been used for any purpose. Deleting it is equivalent to never having installed the pack
     - Deterministic and auditable. The gate checks are simple database queries against indexed columns (`content_hash` comparison, FK existence checks). The blocking reason is specific: "3 of 6 templates have been edited" tells the user exactly what happened and why
     - No cascading effects. If the gate passes, there are no references to worry about — no dangling FKs, no orphaned generated documents, no broken clone chains
     - Matches user intent. Packs that were installed by mistake or explored but never adopted are cleanly removable. Packs that have been integrated into the workflow are protected
     - Simple implementation. The gate is a set of read-only queries. The delete is a simple `DELETE WHERE source_pack_install_id = ?`. No archive state, no soft delete, no restore flow
   - Cons:
     - Strict. A pack where the user edited one template out of 10 cannot be uninstalled at all, even if the other 9 are untouched. The user must manually revert the edit (or delete the template) before uninstalling. This feels inflexible but is the safe default — partial uninstall would require per-item gate logic and per-item delete, which is a different feature
     - Content hash sensitivity. Any edit — even a whitespace change — to a template's content triggers the hash mismatch. This could surprise users who made trivial edits. Mitigated by using canonical JSON serialization for the hash (sorting keys, normalizing whitespace), which eliminates format-only differences
     - No escape hatch. There is no "force uninstall" for admins. If the gate blocks, the only path is to manually remove the blocking condition (revert edits, delete generated documents, delete clones). This is by design — an escape hatch would undermine the safety guarantee — but it means support requests for "I cannot uninstall" require hands-on intervention

**Decision**: Option 3 -- Hard delete gated by "never used" rule.

**Rationale**:

The fundamental insight is that uninstall is a destructive operation on data that the tenant may have come to depend on. The safest approach is to allow destruction only when there is proof that no dependency exists. The "never used" gate provides this proof through three concrete checks:

1. **Unedited** (content hash match): If the content has not been modified, deleting it removes exactly what was installed — no customizations are lost.
2. **Not referenced** (no generated documents, no executions): If no downstream entity points to the content, deleting it creates no dangling references.
3. **Not cloned** (no `source_template_id` references for templates): If no other entity was derived from this content, deleting it breaks no lineage chains.

Together, these three checks guarantee that the delete is side-effect-free. The state after uninstall is identical to the state if the pack had never been installed. This is a strong invariant that eliminates an entire class of data-integrity bugs.

The strictness of the gate (all-or-nothing, no override) is a deliberate choice. Partial uninstall (removing some items but keeping others) breaks the pack abstraction — a pack is a coherent set of content, not a collection of independent items. Force uninstall (overriding the gate) creates data-integrity risks that the gate exists to prevent. Both can be added as future features if demand justifies the complexity, but the v1 implementation should be conservative.

Soft delete (Option 1) defers the hard question without answering it. Archived content still consumes space, still has references, and still needs eventual cleanup. The "restore" flow adds UI and state-management complexity for a scenario that most tenants will never use. Hard delete with cascade warnings (Option 2) is the opposite extreme — it provides maximum flexibility at the cost of irreversible data loss and dangling references. Neither matches the Kazi product philosophy of being simple and safe by default.

**Consequences**:

- `content_hash` column added to `document_templates` and `automation_rules` (VARCHAR(64), SHA-256 hex). Populated at pack install time. Null for user-created content
- Hash algorithm: SHA-256 over canonical JSON (keys sorted alphabetically, no formatting whitespace). Computed by `ContentHashUtil` utility class
- For document templates, canonical JSON is computed from the `content` JSONB field (Tiptap JSON)
- For automation rules, canonical JSON is computed from `trigger_config` + `conditions` JSONB fields + serialized `actions` list (child `AutomationAction` entities, sorted by action order). This ensures action edits are detected even though actions are a separate entity
- `TemplatePackInstaller.checkUninstallable()` checks: hash match, `generated_documents.template_id` existence, `document_templates.source_template_id` existence
- `AutomationPackInstaller.checkUninstallable()` checks: hash match, `automation_executions.rule_id` existence. Clone check does not apply (no clone column on `automation_rule`)
- Blocking reasons are human-readable and concatenated with `; ` if multiple conditions fail
- The uninstall API returns 409 with the blocking reason in the `ProblemDetail.detail` field
- A `pack.uninstall_blocked` audit event is emitted on every blocked attempt for admin visibility
- No "force uninstall", no "partial uninstall", no "archive" in this phase
- Users who want to uninstall a pack with edited content must manually revert their edits or delete the edited templates/rules before retrying
- Related: [ADR-240](ADR-240-unified-pack-catalog-install-pipeline.md), [ADR-241](ADR-241-add-only-pack-semantics.md)
