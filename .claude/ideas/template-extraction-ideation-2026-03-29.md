# Template Extraction Ideation — 2026-03-29

## What
Extract a standalone multitenant SaaS starter template from DocTeams into a new repo: `java-keycloak-multitenant-saas`.

## Why
- 50+ phases of battle-tested multitenancy infra is reusable across future products
- Blog series ("Zero to Prod") builds personal brand + demonstrates architecture
- Template becomes the foundation for all future SaaS projects (not just DocTeams forks)

## Key Decisions

1. **Gateway IN** — Spring Cloud Gateway BFF. No secrets in frontend, session-based auth.
2. **Blog on Medium.com** — source drafts in `docs/blog/` within the repo.
3. **Opinionated template** — includes Project + Customer + Comment as demo domain. Not just auth scaffolding.
4. **Virtual threads from day one** — `spring.threads.virtual.enabled=true`, ScopedValues throughout.
5. **No LocalStack/S3** — lean template, no file storage.
6. **No seeding/verticals** — empty schemas with migrations only.
7. **Owner/member roles only** — stored in product DB. Keycloak org roles were problematic (learned from DocTeams experience).
8. **Customer portal with magic links** — the key differentiator. Demonstrates dual-auth (Keycloak session + portal JWT) in one codebase.
9. **Dual-author comments** — `author_type` discriminator (MEMBER vs CUSTOMER) on same table. Strong blog content.
10. **Separate repo** — clean git history, independent of DocTeams.

## Blog Series (10 posts, "Zero to Prod")
1. Architecture & why schema-per-tenant
2. One-command dev environment
3. The multitenancy core (ScopedValues)
4. Gateway as BFF
5. Tenant registration pipeline
6. Members, roles & profile sync
7. First domain entity (Project + Customer)
8. Security hardening
9. Magic link portal
10. Portal comments — dual-auth writes

## Domain Model
Organization → Member (owner|member), Customer → Project → Comment (author_type: MEMBER|CUSTOMER)

## What's OUT
Billing, audit, notifications (beyond email), S3, verticals, custom fields, reporting, automation.

## Deliverables Completed (2026-03-29)
- **Requirements**: `requirements/claude-code-prompt-template-extraction.md` (16 sections)
- **Architecture**: `architecture/template-multitenant-saas-starter.md` (~2,200 lines, 16 sections)
- **ADRs**: `adr/ADR-T001` through `ADR-T007` (7 standalone files)
- **Task Breakdown**: `tasks/template-multitenant-saas-starter.md` (7 epics, 19 slices, 72 tasks)
- Quality review completed — 4 critical issues found and fixed

## Implementation Stats
| Metric | Count |
|--------|-------|
| Epics | 7 (T1–T7) |
| Slices | 19 |
| Tasks | 72 |
| New files (estimated) | ~135 |
| Blog posts | 10 |

## Next Steps
- Scaffold new repo at `~/Projects/2026/java-keycloak-multitenant-saas/`
- Copy architecture doc, ADRs, requirements, and task breakdown to new repo
- Begin implementation with T1A (Docker Compose + repo scaffold)
