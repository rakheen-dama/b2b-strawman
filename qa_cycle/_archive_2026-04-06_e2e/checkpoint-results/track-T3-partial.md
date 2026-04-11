# Track 3 — Adverse Party Registry Results (Partial)

**Executed**: 2026-04-04
**Actor**: QA Agent (Cycle 1, continued)
**Stack**: Keycloak dev (3000/8080/8443/8180)

---

## T3.1 — Adverse Party List

| Step | Result | Evidence |
|------|--------|----------|
| T3.1.1 | PASS | Navigate to Clients > Adverse Parties. Page loads at `/org/moyo-dlamini-attorneys/legal/adverse-parties`. |
| T3.1.2 | PASS | All 4 seeded adverse parties visible: BHP Minerals SA (Pty) Ltd, James Nkosi, Road Accident Fund, Thandi Modise. Counter shows "4 parties". |
| T3.1.3 | PARTIAL | Columns: Name, ID Number, Reg. Number, Type, Links (count), Actions. "Linked Matters" column shows as "Links" with a numeric count (1 for each party). Column names close but not exact match to test plan ("Linked Matters" expected). |

**Observations**:
- Type column shows inconsistent formatting: "Company" (formatted) vs "INDIVIDUAL" and "GOVERNMENT" (raw enums). Same class of issue as GAP-P55-005.
- Type filter dropdown options: "Natural Person", "Company", "Trust", "Close Corporation", "Partnership", "Other" — missing "Estate" and "Government" from the filter, and "Individual" is labeled "Natural Person". The data uses INDIVIDUAL/GOVERNMENT which don't map to filter labels.

---

## T3.2 — Search Adverse Parties

| Step | Result | Evidence |
|------|--------|----------|
| T3.2.1 | FAIL | Typed "BHP" in search → 0 results. Expected: "BHP Minerals SA (Pty) Ltd". API also returns empty for `?search=BHP`. |
| T3.2.2 | N/A | Not tested (search broken). |
| T3.2.3 | N/A | Not tested. |
| T3.2.4 | N/A | Not tested. |
| T3.2.5 | N/A | Not tested. |

### GAP-P55-010: Adverse party search does not match on partial name tokens

**Track**: T3.2 — Search Adverse Parties
**Step**: T3.2.1
**Category**: bug
**Severity**: major
**Description**: The adverse party search does not find results for partial name matches. Searching "BHP" returns 0 results despite "BHP Minerals SA (Pty) Ltd" existing. Searching "Road" returns 0 results despite "Road Accident Fund" existing. However, searching "Minerals" correctly returns "BHP Minerals SA (Pty) Ltd". The search appears to only match on certain substrings (possibly ILIKE on a specific field rather than the full name, or the search implementation has a minimum match threshold issue).
**Evidence**:
- `GET /api/adverse-parties?search=BHP` → 0 results
- `GET /api/adverse-parties?search=Road` → 0 results
- `GET /api/adverse-parties?search=Minerals` → 1 result (BHP Minerals SA)
- `GET /api/adverse-parties` (no search) → 4 results
**Impact**: Blocks all search-dependent tests (T3.2, T3.3 verification, T4.3 fuzzy match, T4.4 alias match).
**Suggested fix**: Review the backend search query — likely needs `ILIKE '%' || :search || '%'` on the name field (and alias array). Current implementation may be using exact prefix match or pg_trgm with too-high similarity threshold.

---

## T3.3–T3.8 — Not Executed

Remaining Track 3 checkpoints deferred. The search bug (GAP-P55-010) is the most critical finding. CRUD operations (Add Party, Edit, Link, Delete) were not tested in this pass.
