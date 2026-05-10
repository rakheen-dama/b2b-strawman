# E4 — ADR Index Verification

**Date:** 2026-05-10
**Scope:** Cross-check `kazi-architecture/90-adr-index.md` against `adr/`.

## Top-level numbers

| Metric | Value |
|---|---|
| Total ADR files in `adr/` | **278** |
| Distinct ADR IDs referenced in `90-adr-index.md` (any context) | **278** |
| ADRs in `adr/` not referenced in index | **0** |
| ADRs referenced in index without a backing file | **0** |
| ID format mismatches (e.g. `ADR-13` vs `ADR-013`) | **0** |
| Net change to the index from this E-pass | **0 lines** (no edits required) |

## Method

1. `ls adr/` → 278 files. Extracted IDs with `grep -oE 'ADR-[0-9]{3}|ADR-T00[0-9]'` → 278 unique IDs.
2. Extracted IDs from `90-adr-index.md` the same way → 278 unique IDs.
3. `comm -23` and `comm -13` to diff the two sets → empty on both sides.
4. Spot-checked supersedor IDs (ADR-064, ADR-098, ADR-123, ADR-185, ADR-240, ADR-260) all exist in `adr/`.
5. T-series ADRs T001–T008: all present in both `adr/` and the index (Tenancy, Identity, Portal, Notification clusters).
6. ID format: every reference in the index is `ADR-XXX` zero-padded (010–279) or `ADR-T00X`. Files in `adr/` use the identical format. No mismatches.

## Section-level findings (informational, no fixes applied)

| Section | Header claim | Distinct first-column IDs measured |
|---|---|---|
| Active | 258 | 259 (260 row-entries; ADR-085 cross-listed in Identity + Operational, intentional) |
| Superseded | 9 | 9 |
| Stale | 8 | 8 |
| Informational | 3 | 3 |

The Active count in the header (258) is one below the measured 259. Per task scope ("Don't re-evaluate Active vs Superseded status. Triage from A4 stands; only fill gaps."), this is **noted, not corrected** — likely a small bookkeeping drift from the A4 triage and out of scope for E4.

## ADRs in `adr/` not in index (added)

**None.** No edits made to `90-adr-index.md`.

## ADRs in index not in `adr/` (flagged)

**None.** Every cited ID has a backing file.

## ID format mismatches fixed

**None.** Format is uniform across both surfaces.

## Superseded ADRs — supersedor existence check

All 9 Superseded entries point at supersedors that exist in `adr/`:

| Superseded | → Supersedor | Supersedor file present |
|---|---|---|
| ADR-011 | ADR-064 | yes |
| ADR-012 | ADR-064 | yes |
| ADR-015 | ADR-064 | yes |
| ADR-016 | ADR-064 | yes |
| ADR-026 | ADR-260 | yes |
| ADR-051 | ADR-098 | yes |
| ADR-063 | ADR-240 | yes |
| ADR-106 | ADR-123 | yes |
| ADR-182 | ADR-185 | yes |

## Net change

`90-adr-index.md` was **not modified**. The file is internally consistent with `adr/` at the ID-existence level.
