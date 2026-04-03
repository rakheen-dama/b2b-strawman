---
name: Test plan format
description: Test plans must be step-by-step execution scripts with checkable items, concrete test data, and specific expected values — not gap analyses or developer specs
type: feedback
---

Test plans in `qa/testplan/` must follow the format of `phase49-document-content-verification.md` — not a gap analysis or developer spec.

**Why:** The founder rejected a gap-analysis-style test plan as "not a test plan." QA agents need step-by-step scripts they can follow mechanically.

**How to apply:** Test plans must include:
1. Purpose, scope (in/out), prerequisites with specific seed data values
2. Test tracks organized by domain, each with checkable `- [ ] **T{track}.{section}.{step}**` items
3. Concrete test data (customer names, field values, expected amounts)
4. Actor identification (who performs each action)
5. Stop gates at readiness checkpoints
6. Gap reporting format template
7. Success criteria table with targets
8. Execution order with "when to stop" guidance
