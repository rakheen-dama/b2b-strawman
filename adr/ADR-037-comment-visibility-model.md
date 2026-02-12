# ADR-037: Comment Visibility Model

**Status**: Accepted

**Context**: Phase 6.5 introduces comments on tasks and documents. The product roadmap includes a customer portal (Phase 7) where external customers can view selected project information. Some comments should be visible to customers via the portal, while others should remain internal to the staff team. This "visibility" concept must be designed into the comment system from the start so that Phase 7 can project visible comments into the portal read-model schema without schema changes.

The visibility model must balance simplicity (Phase 6.5 has no portal) with forward compatibility (Phase 7 needs to filter comments by visibility). It must also include appropriate permission controls -- not every team member should be able to make a comment customer-visible, since that could inadvertently expose sensitive internal discussions.

**Options Considered**:

1. **Simple enum: `INTERNAL` / `SHARED`**
   - Pros:
     - Two-state model is easy to understand, implement, and test
     - Maps directly to Phase 7 portal projection: `WHERE visibility = 'SHARED'` produces portal-visible comments
     - Permission model is straightforward: any member can create INTERNAL comments; only leads/admins/owners can set SHARED
     - Default is INTERNAL -- safe by default, no accidental customer exposure
     - Single column, single index filter, minimal storage overhead
   - Cons:
     - No fine-grained control (e.g., "visible to this specific customer but not others")
     - Binary model may not cover future requirements (e.g., "visible to partner orgs")
     - Cannot un-share a comment from a specific customer while keeping it shared for others

2. **Per-user visibility lists (many-to-many: `comment_visibility` join table)**
   - Pros:
     - Fine-grained control: each comment can be visible to specific users or groups
     - Supports complex sharing rules (e.g., "visible to Customer A but not Customer B")
   - Cons:
     - Significant schema complexity (join table, additional queries for every comment list)
     - Overkill for the current use case (internal vs. customer-visible)
     - Phase 7 portal projection becomes a complex join instead of a simple WHERE clause
     - Permission model is harder to define (who can add/remove individual visibility entries?)
     - No current requirement for per-user visibility

3. **Per-role visibility matrix (visibility levels: INTERNAL, CUSTOMER, PARTNER, PUBLIC)**
   - Pros:
     - Extensible to multiple audience types
     - Still a single column (enum), simple to query
   - Cons:
     - Premature generalization -- only INTERNAL and CUSTOMER are foreseeable
     - Additional enum values require code changes in both visibility checks and portal projections
     - "CUSTOMER" and "SHARED" are semantically equivalent for the current use case
     - Introduces complexity without a concrete requirement for PARTNER or PUBLIC

4. **Separate `portal_comments` table (dual-write)**
   - Pros:
     - Complete separation between internal and portal data
     - Portal can have different schema (e.g., stripped of internal metadata)
   - Cons:
     - Data duplication -- shared comments exist in two tables
     - Sync complexity -- editing a shared comment must update both tables
     - Violates single-source-of-truth principle
     - Phase 7's `PortalProjectionHandler` is specifically designed to project FROM core entities TO read-model entities; dual-write bypasses this pattern

**Decision**: Simple enum: `INTERNAL` / `SHARED` (Option 1).

**Rationale**: The two-state enum is the simplest model that satisfies both Phase 6.5 requirements (internal team comments) and Phase 7 forward compatibility (portal-visible comments). The mapping to Phase 7 is direct and requires no schema changes:

```
Phase 6.5: Comment(visibility = 'SHARED')
    |
    v  (Phase 7 PortalProjectionHandler)
Phase 7:  PortalComment(projected from Comment WHERE visibility = 'SHARED')
```

The permission model naturally aligns with organizational authority:
- **Any project member** can create comments (defaults to INTERNAL)
- **Project leads, org admins, and org owners** can set visibility to SHARED
- This reflects the real-world pattern: individual contributors write comments, but team leads decide what customers see

The "safe by default" behavior (INTERNAL) prevents accidental exposure of internal discussions. Setting a comment to SHARED is an explicit, permission-gated action -- not something that happens by accident.

Per-user visibility (Option 2) was rejected because there is no requirement for sharing a comment with specific customers. The portal serves all linked customers of a project equally -- if a comment is shared, all customers with portal access to that project can see it. Per-role visibility (Option 3) was rejected as premature generalization. A separate portal table (Option 4) contradicts the event-driven projection architecture designed in [ADR-032](ADR-032-spring-application-events-for-portal.md).

If fine-grained per-customer visibility is needed in the future, the migration path is to add a `visible_to_customer_ids UUID[]` column (nullable) alongside the existing `visibility` enum. SHARED comments with a non-null `visible_to_customer_ids` would be filtered in the portal projection. This is an additive change that does not break the existing model.

**Consequences**:
- `Comment.visibility` is a `VARCHAR(20)` column with values `INTERNAL` (default) and `SHARED`
- `CommentService.createComment()` defaults to `INTERNAL`; the request DTO accepts an optional `visibility` field
- `CommentService.updateComment()` allows changing `visibility` only if the caller is a lead/admin/owner (via `ProjectAccess.canEdit()`)
- Changing visibility from INTERNAL to SHARED is audited as `comment.visibility_changed` (important for compliance)
- Phase 7's `PortalProjectionHandler` subscribes to `CommentCreatedEvent` and `CommentVisibilityChangedEvent`, projecting only SHARED comments
- The frontend shows a visibility badge (e.g., "Customer visible" tag) on SHARED comments and a toggle for authorized users
- No per-customer or per-role visibility in Phase 6.5 -- the model is strictly binary
