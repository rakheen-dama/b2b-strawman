# ADR-229: Content Authorship Model

**Status**: Accepted

**Context**:

HeyKazi's documentation site will contain ~30 feature guides, getting-started walkthroughs, admin references, and vertical-specific articles. The content must be accurate (reflecting actual product behavior), consistent in tone (friendly, professional, task-oriented), and kept current as the product evolves across phases.

The question is who writes and maintains this content: the platform team only (agents draft, founder reviews), individual tenants (each org customizes help content for their staff), or a hybrid model where the platform provides base content that tenants can override.

This decision affects the content storage model (static MDX files vs. database-backed CMS), the deployment pipeline (static build vs. per-tenant rendering), and the long-term maintenance burden.

**Options Considered**:

1. **Platform-authored only** — Content is written by the HeyKazi team (agent-drafted, founder-reviewed), stored as MDX files in the monorepo, and served identically to all tenants via a static Nextra site.
   - Pros:
     - Simplest architecture: static MDX files, no database, no per-tenant content storage, no CMS, no content API. The doc site is a standard static Next.js build.
     - Consistent quality: every article goes through the same review process. Tone, accuracy, and completeness are controlled by a single editorial voice.
     - Zero tenant overhead: tenants do not need to write, maintain, or update help content. They focus on their business; the platform handles documentation.
     - Content evolves with the codebase: when a phase adds a feature, the documentation article is written in the same development cycle, reviewed in the same PR, and deployed atomically. No separate CMS workflow.
     - One set of content to maintain: bug fixes, terminology updates, and feature changes are applied once and serve all tenants.
     - SEO: a single canonical doc site at `docs.heykazi.com` concentrates search authority. Per-tenant content would fragment or eliminate SEO benefit.
   - Cons:
     - No tenant customization: if an accounting firm wants to add firm-specific procedures to the "Projects" guide (e.g., "At XYZ Accounting, always tag audit projects with 'IRBA'"), they cannot. The doc site shows generic platform content only.
     - Terminology mismatch: tenants using terminology overrides (Phase 48 — e.g., "Matter" instead of "Project") will see generic terminology in the docs. The docs say "Project" but their app says "Matter." Mitigation: articles note terminology differences (e.g., "Projects — called 'Matters' in legal firms").
     - Platform maintenance burden: every content update requires a PR, review, build, and deploy. There is no self-service content editing for non-developers.

2. **Tenant-editable content** — Each tenant can customize documentation for their organization. Platform provides base content; tenants can override, extend, or add articles visible only to their members.
   - Pros:
     - Tenants can document firm-specific workflows, policies, and procedures alongside platform documentation. A firm's staff sees "How to create an invoice" (platform) next to "Our firm's invoicing policy" (firm-specific).
     - Terminology overrides can be applied to documentation content, not just UI labels. If a firm calls projects "Matters," the documentation can use "Matter" throughout.
     - Self-service: firm admins update content without waiting for a platform release.
   - Cons:
     - Requires a content management system: per-tenant content storage (database or S3), a content editor UI, content versioning, and a rendering pipeline that merges platform base content with tenant overrides.
     - Authentication: the doc site can no longer be a public static site. It must authenticate users to determine their tenant and serve tenant-specific content. This means integrating with Keycloak, adding session management, and building a content API.
     - Content quality risk: tenant-authored content has no quality control. Stale, inaccurate, or poorly written tenant articles reflect on the platform's perceived quality.
     - Development cost: building a CMS, a content editor, per-tenant content storage, and an authenticated doc site is a multi-phase effort (easily 4–6 slices). This is disproportionate to the value at 5–20 tenants.
     - Maintenance complexity: when the platform updates a feature, both the platform article and all tenant overrides may need updating. There is no automatic mechanism to flag stale tenant content.
     - Provisioning: new tenants need base content seeded. Content becomes part of the tenant provisioning pipeline alongside schema, packs, and demo data.

3. **Community/wiki model** — Users across all tenants can contribute to shared documentation (Wikipedia-style).
   - Pros:
     - Crowd-sourced content: power users write tips, workarounds, and best practices that the platform team might not think of.
     - Reduces platform maintenance burden as users self-serve content updates.
     - Community engagement: users feel invested in the product when they contribute to shared knowledge.
   - Cons:
     - Completely inappropriate for a multi-tenant B2B SaaS product. Tenants are independent businesses (accounting firms, legal firms) with no relationship to each other. There is no "community" — tenants do not collaborate, share workflows, or even know about each other.
     - Data isolation concerns: a community wiki visible across tenants conflicts with the schema-per-tenant isolation model. Even for documentation content, the optics of seeing another firm's name or workflow in shared content is unacceptable for B2B clients.
     - Moderation burden: community-contributed content requires review, approval, and cleanup. At 5–20 tenants with ~50–200 total users, the contributor base is too small to generate quality content but large enough to require moderation.
     - Quality control: user-contributed documentation is typically inconsistent in tone, accuracy, and completeness. The result is a patchwork of articles that degrades the product's professional image.
     - Implementation cost: requires user accounts on the doc site, a content editor, version history, moderation tools, notification for new contributions, and abuse prevention. This is a substantial feature set that delivers minimal value at this scale.

**Decision**: Option 1 — Platform-authored only.

**Rationale**:

The scale of the deployment drives the decision. At 5–20 tenants, one well-maintained set of documentation is categorically more valuable than a system that enables per-tenant customization. The ROI calculation is simple: building a tenant-editable CMS (Option 2) costs 4–6 development slices (~2–3 weeks of agent time). Maintaining 26 platform-authored articles costs a few hours per phase when features change. The CMS infrastructure would serve 5–20 tenants who are unlikely to invest time in writing documentation — firm staff use the product to manage their practice, not to author help content.

The terminology concern (Option 1's main weakness) is mitigated by the existing documentation convention: articles note vertical-specific terminology where relevant ("Projects — called 'Matters' in legal firms, or 'Engagements' in accounting firms"). This is a documentation pattern, not a technical limitation. If terminology overrides become pervasive enough to justify dynamic content substitution, that can be added as a lightweight enhancement to the static doc site (a client-side term replacement based on a query parameter or cookie) without building a full CMS.

The community model (Option 3) is eliminated immediately. HeyKazi is a multi-tenant B2B platform serving independent professional services firms. There is no user community to contribute content. The multi-tenant isolation model — schema-per-tenant, no cross-tenant data visibility — is philosophically incompatible with shared community content.

Option 2 (tenant-editable content) is a legitimate future enhancement. If demand materializes — if multiple tenants request the ability to add firm-specific procedures alongside platform guides — the architecture can evolve. The static MDX content can serve as the "base layer" with tenant overrides stored in the database and rendered dynamically. But building this infrastructure speculatively for 5–20 tenants who have not yet requested it violates the YAGNI principle.

**Consequences**:

- Documentation is static MDX files in the monorepo. No database, no CMS, no content API. The doc site is a standard Nextra static build deployed on Vercel.
- All content goes through the same editorial pipeline: agent drafts from codebase analysis, founder reviews and edits, merged via PR. This ensures consistent quality and accuracy.
- When a phase modifies or adds a feature, the corresponding documentation article is updated in the same development cycle. This is an explicit responsibility, not automated — the phase task file should include a documentation update task.
- Tenants cannot add firm-specific content to the doc site. If a firm needs internal procedures documented, they use their own tools (Notion, Confluence, Google Docs) — not the HeyKazi doc site.
- Terminology differences between verticals are handled by editorial convention (noting both terms in the article text), not by dynamic content substitution. This is adequate at current scale.
- If tenant-editable content is needed in the future, the migration path is: (1) keep static MDX as the base layer, (2) add a tenant content table to the database, (3) build a content editor in the admin settings, (4) render merged content (platform base + tenant override) via an authenticated doc route. The static MDX content is not throwaway — it becomes the foundation for a layered content model.
- Related: [ADR-228](ADR-228-separate-site-vs-in-app-help.md) (separate site architecture supports the platform-authored model — a public static site needs no per-tenant rendering).
