# ADR-166: Template Format Coexistence Model

**Status**: Proposed
**Date**: 2026-03-08
**Phase**: 42 (Word Template Pipeline)

## Context

Phase 42 introduces Word (`.docx`) templates alongside the existing Tiptap (JSONB) templates. Both template types share significant metadata (name, slug, description, category, entity type, source, active status) and both produce documents via the same `TemplateContextBuilder` pipeline. The question is how to model this in the database and domain layer.

The existing `DocumentTemplate` entity has 15+ fields. Word templates add 4 new fields (S3 key, file name, file size, discovered fields). Both types appear in the same template list, use the same CRUD patterns, and are selected from the same generation dropdown on entity pages.

## Options Considered

1. **Single entity with format discriminator (chosen)** — Add a `format` column (TIPTAP/DOCX) to `DocumentTemplate`. Format-specific fields are nullable. Validation enforced in the domain layer based on format.
   - Pros: Single table, single repository, single list endpoint, minimal API surface change, shared metadata handled naturally, no join overhead, format filter is a simple WHERE clause, existing CRUD endpoints work for both (with format-aware validation)
   - Cons: Nullable columns for format-specific fields (content is null for DOCX, docx_s3_key is null for TIPTAP), domain validation must enforce format-specific constraints, entity class grows with both sets of fields

2. **Separate entities with shared base (JPA inheritance)** — `TiptapTemplate` and `DocxTemplate` extend a common `DocumentTemplate` base class. Use `SINGLE_TABLE` or `JOINED` inheritance strategy.
   - Pros: Type-safe fields per format, no nullable format-specific columns, each subclass has exactly the fields it needs, clean OOP model
   - Cons: JPA inheritance adds complexity (discriminator columns, Hibernate quirks), `SINGLE_TABLE` still has nullable columns, `JOINED` adds join overhead, polymorphic queries are more complex, existing code must be refactored to use the base type, two repositories or a polymorphic repository pattern, breaks the simple CRUD that exists today

3. **Separate tables, separate entities** — `document_templates` stays as-is for Tiptap, new `docx_templates` table for Word templates.
   - Pros: Complete separation, no impact on existing Tiptap code, each table has exactly the right columns
   - Cons: Duplicate metadata columns (name, slug, category, etc.), separate list endpoints or UNION queries, separate CRUD services, template selection dropdown needs two data sources, slug uniqueness harder to enforce across tables, generated documents need to reference either table, significant API surface increase

## Decision

Use **single entity with format discriminator** (Option 1). The `DocumentTemplate` entity gains a `format` column with enum values `TIPTAP` and `DOCX`. Format-specific fields are nullable with domain-layer validation.

## Rationale

The shared metadata surface (name, slug, category, entity type, source, permissions) is much larger than the format-specific surface (4 new fields for DOCX). A single entity keeps the template list simple — one query, one endpoint, optional format filter. The generation dropdown on entity pages shows all templates regardless of format, distinguished by a badge. This is the natural UX: users don't think in terms of "Tiptap templates" vs "Word templates" — they think "templates" and want to see them all in one place.

JPA inheritance (Option 2) is theoretically cleaner but introduces Hibernate complexity that this project has deliberately avoided. The codebase uses plain `@Entity` classes with no inheritance hierarchies — introducing one for a simple format discriminator would be architecturally inconsistent. The nullable fields are a minor trade-off; the `TemplateFormat` enum makes the intent clear, and domain validation in `DocumentTemplate` methods ensures consistency.

Separate tables (Option 3) would fracture the template experience. Slug uniqueness across tables, merged listings, and cross-table foreign keys from `GeneratedDocument` all add unnecessary complexity.

## Consequences

- **Positive**: Minimal API change — existing list/detail endpoints gain a `format` field, no new template-list endpoints needed
- **Positive**: Single `DocumentTemplateRepository` handles both formats — no repository proliferation
- **Positive**: Format filter (`?format=DOCX`) is trivially added to existing list queries
- **Positive**: `GeneratedDocument.templateId` continues to reference a single table
- **Negative**: `DocumentTemplate` entity grows from ~15 to ~19 fields with 4 nullable DOCX-specific columns
- **Negative**: Domain validation must check format consistency (e.g., DOCX template must have `docx_s3_key`, TIPTAP template must have `content`)
- **Neutral**: The `format` column defaults to `TIPTAP`, so existing data and code paths are unaffected by the migration
