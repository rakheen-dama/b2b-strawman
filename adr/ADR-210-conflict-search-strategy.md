# ADR-210: Conflict Search Strategy -- PostgreSQL pg_trgm with GIN Index

**Status**: Proposed
**Date**: 2026-03-31
**Phase**: 55 (Legal Foundations: Court Calendar, Conflict Check & LSSA Tariff)

## Context

Phase 55 introduces conflict-of-interest checking for legal tenants. When a firm considers taking on a new client or matter, it must search its adverse party registry and client list for potential conflicts. The search must handle name variations common in South African practice: "Ndlovu Trading (Pty) Ltd" vs. "Ndlovu Trading," "Van der Merwe, Johannes Petrus" vs. "JP van der Merwe," company names with and without "Pty Ltd" suffixes.

The search has two components: exact matching on ID numbers and registration numbers (deterministic), and fuzzy matching on names (probabilistic). The exact matching is straightforward (simple equality queries). The fuzzy matching requires a strategy decision.

Performance target: the search must complete in under 500ms at 10,000+ adverse parties, which represents a large firm with 10+ years of matter history.

## Options Considered

### Option A: PostgreSQL pg_trgm Trigram Similarity (Selected)

Use the `pg_trgm` extension with a GIN index on the `name` column. Query with `similarity(lower(name), lower(:input)) > threshold` and rank by similarity score. Threshold at 0.3 for broad matching, classify results at 0.6+ as CONFLICT_FOUND and 0.3-0.6 as POTENTIAL_CONFLICT.

- **Pros:** Built into PostgreSQL (no external dependency). GIN index makes similarity queries fast even at scale. Handles character transpositions, missing characters, abbreviations naturally. Score-based result ranking is intuitive. `pg_trgm` is a lightweight extension already commonly enabled. No application-level computation needed -- the database does the heavy lifting.
- **Cons:** Trigram similarity is character-based, not semantic -- it does not understand that "Pty Ltd" and "Proprietary Limited" are equivalent. Threshold tuning may require adjustment in practice. Not effective for very short names (trigrams need at least 3 characters).

### Option B: Full-Text Search (tsvector)

Use PostgreSQL's built-in full-text search with `tsvector`/`tsquery`. Tokenize names into lexemes and match using the text search engine.

- **Pros:** Handles stemming and stop words. Good for phrase matching. Built into PostgreSQL.
- **Cons:** Designed for document search, not name matching. Tokenization splits on word boundaries -- "Van der Merwe" becomes three tokens, and a search for "Vandermerwe" (one word) would miss it. Does not provide similarity scores -- results are binary (match/no match). Poor at handling character-level variations (typos, transpositions). Would require custom text search configurations for name matching patterns.

### Option C: Application-Level Levenshtein Distance

Compute Levenshtein edit distance in Java for each candidate name. Load all adverse party names into memory and compute pairwise distances.

- **Pros:** Full control over the matching algorithm. Can implement custom rules (e.g., ignore "Pty Ltd" suffix). No database extension needed.
- **Cons:** O(n) comparison against every adverse party on every search. At 10,000 parties, this is 10,000 Levenshtein computations per search -- feasible but wasteful. Cannot use database indexes for acceleration. Memory overhead of loading all names. Does not scale beyond a single-digit-thousands registry without pagination tricks that reduce match quality.

### Option D: Exact Match Only

Only match on exact name equality (case-insensitive) plus exact ID/registration number matching. No fuzzy search.

- **Pros:** Simplest implementation. No false positives. No extension needed. Fastest queries.
- **Cons:** Misses the primary use case. Name variations are the norm in SA legal practice. A firm that only does exact matching would miss obvious conflicts (same person, slightly different name spelling). This would not satisfy regulatory expectations for reasonable conflict checking.

## Decision

**Option A -- PostgreSQL pg_trgm with GIN index for fuzzy name matching.**

## Rationale

1. **Right tool for the job:** Trigram similarity is specifically designed for fuzzy string matching. It handles the common name variation patterns in SA practice: abbreviations ("JP" vs. "Johannes Petrus"), suffix variations ("Pty Ltd" vs. "Proprietary Limited" -- caught indirectly via the non-suffix portion), character transpositions, and minor spelling differences.

2. **Database-level performance:** The GIN index on trigrams allows PostgreSQL to efficiently identify candidate matches without scanning the entire table. At 10,000 adverse parties, a trigram similarity query with a GIN index runs in single-digit milliseconds. This scales comfortably to the expected data volumes.

3. **Score-based classification:** The similarity score (0.0 to 1.0) maps naturally to the three-tier result classification: NO_CONFLICT (no match above 0.3), POTENTIAL_CONFLICT (0.3-0.6), CONFLICT_FOUND (above 0.6 or exact ID match). This gives the firm useful information about match confidence.

4. **No external dependencies:** `pg_trgm` is a core PostgreSQL extension that ships with every PostgreSQL installation. It requires only `CREATE EXTENSION IF NOT EXISTS pg_trgm` -- no additional infrastructure.

5. **Exact match priority:** The algorithm checks exact ID/registration number matches first. If found, the result is immediately CONFLICT_FOUND regardless of name similarity. Fuzzy matching only runs for name-based searches, reducing unnecessary computation.

## Consequences

- **Positive:** Fuzzy name matching catches common SA name variations without manual synonym configuration
- **Positive:** GIN index provides sub-10ms query performance at expected scale (10K adverse parties)
- **Positive:** Similarity scores provide graduated conflict classification (found/potential/none)
- **Positive:** No external service dependencies -- everything runs within PostgreSQL
- **Negative:** Trigram similarity is character-based -- does not understand semantic equivalences (e.g., "Johannes" and "Jan" are semantically related but trigram-distant)
- **Negative:** Threshold values (0.3 and 0.6) may need per-firm tuning based on name patterns
- **Mitigations:** Alias support on adverse parties allows firms to register known name variations. The 0.3 threshold is intentionally broad to minimize false negatives. Exact ID matching bypasses fuzzy search entirely for deterministic matches.
