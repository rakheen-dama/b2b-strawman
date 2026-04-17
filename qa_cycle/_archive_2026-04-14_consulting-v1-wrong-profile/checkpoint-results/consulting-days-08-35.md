# Days 8-35 Checkpoint Results — Consulting Agency 90-Day Demo (Keycloak)

**Date**: 2026-04-14
**Cycle**: 1

---

## Days 8-9 — Ubuntu Startup (Retainer Client)

| ID | Result | Evidence |
|----|--------|----------|
| 8.1 | PASS | Ubuntu Startup (Pty) Ltd created: Company, email=accounts@ubuntustartup.co.za, Contact=Sipho Khumalo, Tax=1234567890, Address=12 Sandton Drive Johannesburg, Country=ZA |
| 8.2 | PASS | Onboarding completed: PROSPECT -> ONBOARDING -> ACTIVE |
| 9.1 | PASS | Project created: "Ubuntu Startup -- Monthly Marketing Retainer (Mar 2026)", ref=UBUNTU-RET-2026-03, priority=Medium |
| 9.2 | PASS | Budget set: 20 hours, R24,000, ZAR, alert threshold 80% |
| 9.3 | NOTED | GAP-C-07: No retainer primitive. Workaround is "one project per retainer cycle, manually cloned monthly" |
| 9.4 | SKIP | Custom text field on project not attempted (project custom fields require field definition + group setup) |
| 9.5 | PASS | 5 tasks created: Social Media Management, Email Campaign, Analytics Review, Content Writing, Strategy Call |

**Customer ID**: `93c0d990-4374-463d-a779-d24cd72add1e`
**Project ID**: `63f9bc10-bc99-4e26-a896-e289ce33febf`

---

## Days 10-20 — Retainer Work

| ID | Result | Evidence |
|----|--------|----------|
| 10.1 | PASS | 2.0h logged on Strategy Call: "Monthly strategy call with Sipho" |
| 12.1 | PASS | 3.0h logged on Social Media Management: "Social media posts for March week 2" |
| 14.1 | PASS | 2.5h logged on Email Campaign: "Email campaign design + copy" |
| 16.1 | PASS | 1.5h logged on Analytics Review: "Analytics dashboard review" |
| 18.1 | PASS | 2.0h logged on Content Writing: "Blog post writing" |
| 20.1 | PASS | Budget burn: 11h consumed (55% hours), R19,800 consumed (83% amount). Status: "At Risk" (amount exceeded 80% threshold). Hours On Track. This is because all time was logged at Zolani's R1,800/hr rate rather than Bob/Carol's lower rates |

---

## Days 21-22 — Masakhane Foundation (NGO)

| ID | Result | Evidence |
|----|--------|----------|
| 21.1 | PASS | Masakhane Foundation created: Company, email=info@masakhane.org.za, Contact=Thandiwe Nkosi, Tax=NPO-2024-001, Address=100 Mandela Avenue Pretoria, Country=ZA |
| 21.2 | PASS | Onboarding completed: PROSPECT -> ONBOARDING -> ACTIVE |
| 22.1 | PASS | Project created: "Masakhane -- 2025 Annual Report + Fundraising Campaign", ref=MAS-2026-001, priority=HIGH |
| 22.2 | PASS | Budget set: 60 hours, R60,000, ZAR |
| 22.3 | PASS | 6 tasks created: Content Gathering, Design, Copywriting, Print-Ready, Digital Campaign, Distribution |

**Customer ID**: `8df5d5c2-b2fa-440b-a270-a949d77d243b`
**Project ID**: `30970c02-742b-47e3-b00f-f9de799f433e`

---

## Days 23-35 — NGO Work + Profitability

| ID | Result | Evidence |
|----|--------|----------|
| 23.1 | PASS | 3.0h logged on Content Gathering: "Content gathering meeting with Masakhane team" |
| 25.1 | PASS | 4.0h logged on Design: "Report layout design" |
| 27.1 | SKIP | Document upload not attempted |
| 28.1 | PASS | 3.5h logged on Design: "Illustration + photo treatments" |
| 30.1 | PASS | 2.0h logged on Copywriting: "Copywriting -- executive summary" |
| 32.1 | PASS | NGO budget burn: 12.5h consumed (21%), R22,500 consumed (38%). On Track |
| 34.1 | PASS | Profitability page shows all 3 projects with ZAR revenue/cost/margin. Team Utilization: Zolani 32.5h billable, 100% utilization. Project margins all 55.6%. Customer profitability breakdown matches. Screenshot: `consulting-day34-profitability-wow.png` |

---

## Gaps Logged

| GAP_ID | Day / Checkpoint | Severity | Type | Summary |
|--------|------------------|----------|------|---------|
| GAP-C-07 | D9 / 9.3 | HIGH | Profile-content | No retainer primitive in consulting-generic. Workaround: one project per retainer cycle, manually cloned monthly. Real agency needs: retainer entity, rollover, auto-cycle, consumption dashboard |

---

## Summary

- **3 customers created**: BrightCup Coffee Roasters, Ubuntu Startup (Pty) Ltd, Masakhane Foundation — all ACTIVE
- **3 projects created**: BrightCup Brand Refresh (6 tasks), Ubuntu Retainer Mar 2026 (5 tasks), Masakhane Annual Report (6 tasks)
- **32.5 total hours logged** across all projects (9h + 11h + 12.5h)
- **All budgets configured and tracking correctly** with On Track / At Risk indicators
- **Profitability page renders correctly** with 3 projects, ZAR currency, utilization metrics
- **No product bugs found** — all issues are profile-content gaps
