# 636B.1 candidate findings (running list — NOT the final `gaps.md` register)

Walker 636B.1. Severities are **proposed** (rating precedent Phase 88 O-4: new product failure → MEDIUM; trust money path / client-data confidentiality → HIGH; copy, layout, scenario-wording drift → LOW). The orchestrator confirms every HIGH/MEDIUM.

Numbering note: ids run from G89-F1 as the 636B.1 brief instructs. The brief §5.3 also seeds the final register with the three 636A tooling LOWs (636A-c1/c2/c3); if those take G89-F1…F3 in `gaps.md`, renumber these candidates by +3 when the register is assembled.

| ID | Proposed sev | Step(s) | Class | Observed | Expected | file:line | Evidence |
|---|---|---|---|---|---|---|---|
| G89-F1 | LOW | S1-29 (L 5.3) | Scenario mismatch needing authorisation — design proven | Matter Client group menu reads `Clients, Information requests, Comments, Adverse parties`; the sub-tab is **"Comments"** (`tab-item-comments`, `?tab=comments`), which loads cleanly | 5.3 says Client › **"Client comments"** sub-tab | `frontend/lib/constants/matter-tab-groups.ts:70` (`{ id: "comments", label: "Comments" }`); PR #1738 out-of-scope item 1 (pre-known §8); scenario `qa/testplan/demos/legal-za-90day-keycloak.md:273` | `steps/days-01-07.md` S1-29, `screenshots/5.3-1440-client-comments.png` |
| G89-F2 | LOW | S1-30 side observation (L 4.4 → 5.4) | Product bug (staleness) | After Bob logged 1 h 30 m from Work › Tasks, switching to the Overview tab client-side showed **"0.0h HOURS LOGGED"**; a full reload showed "1.5h". Same family as G88-M9 (panels stale until reload) | Overview KPI reflects the logged time without a reload | not traced (matter Overview KPI data fetched at page load; tab switch does not refetch) — hypothesis, cited only | `steps/days-01-07.md` S1-30 evidence; first-pass frame was overwritten by the post-reload `wow-4-matter.png` (the 0.0h state is recorded in the batch output quoted in the step file) |
