# ADR-167: Conditional Block Predicate Model

**Status**: Accepted
**Date**: 2026-03-09
**Phase**: UX Enhancements (Fields, Clauses, Templates)

## Context

Template authors need to conditionally show or hide blocks of content based on data values at render time. For example, a project proposal template might include a "Tax Details" section only when the customer has a tax number, or show different payment terms based on the customer type. Without conditional blocks, authors must maintain separate templates for each variant or rely on post-generation manual editing.

The question is how to model the condition predicate within the Tiptap document structure. The predicate must be stored as node attributes (serialized to JSONB), evaluated at render time by both the backend `TiptapRenderer` and the frontend `client-renderer.ts`, and configurable through a UI in the template editor.

## Options Considered

1. **Attribute-based predicates (chosen)** — Store `fieldKey`, `operator`, and `value` as Tiptap node attributes. The operator is one of a fixed set (`eq`, `neq`, `isEmpty`, `isNotEmpty`, `contains`, `in`). Evaluation is a simple switch on the operator with string comparison.
   - Pros: Simple to implement and reason about, type-safe (fixed operator set), UI-friendly (dropdown for operator, text input for field/value), covers 90% of real-world use cases (presence checks, equality, membership), easy to mirror between backend Java and frontend TypeScript, no injection surface
   - Cons: Cannot express compound conditions (AND/OR), no arithmetic comparison (gt, lt), limited to string-based evaluation

2. **Expression DSL** — Store a string expression (e.g., `customer.taxNumber != "" && customer.type == "company"`) as a single attribute. Parse and evaluate at render time.
   - Pros: Expressive, supports compound conditions, familiar syntax
   - Cons: Requires a parser on both backend and frontend, injection risk (template authors could craft malicious expressions), harder to build UI for (free-text expression input rather than structured fields), error messages harder to surface, overkill for typical template conditions

3. **No conditional blocks** — Authors maintain separate templates for each variant.
   - Pros: No implementation cost, no complexity
   - Cons: Template proliferation, maintenance burden, users already requesting this feature, common in every template engine

## Decision

Use **attribute-based predicates** (Option 1). Each `conditionalBlock` Tiptap node stores three attributes: `fieldKey` (the dot-path variable to evaluate), `operator` (one of `eq`, `neq`, `isEmpty`, `isNotEmpty`, `contains`, `in`), and `value` (the comparison value, ignored for `isEmpty`/`isNotEmpty`).

## Rationale

The attribute-based model fits naturally into Tiptap's node attribute system. Each attribute is a simple string, serialized to JSONB without any custom parsing. The fixed operator set means the UI can present a dropdown rather than a free-text input, reducing user error. The evaluation logic is a straightforward switch statement that is trivially mirrored between Java and TypeScript.

The six operators cover the dominant use cases: presence/absence checks (`isEmpty`/`isNotEmpty`), exact match (`eq`/`neq`), substring search (`contains`), and set membership (`in` with comma-separated values). Compound conditions can be achieved by nesting conditional blocks, which the wrapping-block content model supports naturally.

An expression DSL (Option 2) would introduce parsing complexity and an injection surface that is hard to justify for a template system where the primary users are non-technical staff (accountants, project managers). The attribute model is deliberately constrained to be safe and predictable.

## Consequences

- **Positive**: Zero injection risk — no expression parsing, no eval, all comparison is string-based
- **Positive**: Structured UI — field picker, operator dropdown, value input. No free-text expression authoring
- **Positive**: Backend/frontend parity is trivial — same switch/case on a fixed set of operators
- **Positive**: Nesting conditional blocks provides implicit AND semantics without compound expression support
- **Negative**: Cannot express OR conditions in a single block (workaround: duplicate the content in two conditional blocks)
- **Negative**: No numeric comparison operators (gt, lt, gte, lte) — these could be added later as the operator set is extensible
- **Neutral**: The `in` operator uses comma-separated values in a single string field, which is simple but limits values that contain commas
