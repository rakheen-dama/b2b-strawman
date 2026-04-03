# ADR-227: Nextra over Alternatives

**Status**: Accepted

**Context**:

HeyKazi needs a documentation site to host ~30 user-facing feature guides, getting-started walkthroughs, admin references, and vertical-specific content. The site must be deployed alongside the existing Vercel-hosted frontend and portal, match HeyKazi's design system (Sora/IBM Plex Sans fonts, slate palette, teal accents, dark mode), and support full-text search without external services. Content will be MDX files version-controlled in the monorepo so documentation evolves alongside the codebase.

The primary question is which documentation framework to use. The HeyKazi frontend stack is Next.js 16 (App Router), React 19, TypeScript 5, and Tailwind CSS v4. Ideally, the doc site uses a compatible framework to minimize context-switching for contributors and leverage existing deployment infrastructure (Vercel).

**Options Considered**:

1. **Nextra 4** — Next.js-native documentation framework with MDX, file-based routing, and a built-in docs theme.
   - Pros:
     - Built on Next.js App Router — same framework as the main app (Next.js 16, React 19). Contributors stay in the same ecosystem.
     - MDX files live in the monorepo under `docs/content/`. Content is version-controlled alongside code, so documentation updates can ship in the same PR as feature changes.
     - Deployed to Vercel as a standard Next.js project — no new platform, no new vendor, no new billing.
     - Built-in Flexsearch provides client-side full-text search with zero infrastructure. The search index is generated at build time and bundled into the client.
     - `_meta.ts` files control navigation order and labels — no separate CMS or config database.
     - Nextra 4 supports Tailwind CSS v4, custom fonts, and CSS variable theming, making it straightforward to match HeyKazi's design system.
     - Dark mode is built into the docs theme and works out of the box.
     - Zero SaaS cost — Nextra is open-source (MIT license), and Vercel's free tier or existing plan covers the deployment.
   - Cons:
     - Nextra 4 is relatively new (stable but less battle-tested than Docusaurus). Community is smaller.
     - Theming requires CSS overrides rather than a configuration API — matching HeyKazi's exact design tokens takes manual work.
     - No built-in analytics, feedback widgets, or A/B testing (not needed for v1, but noted).

2. **Docusaurus 3** — Meta's documentation framework, React-based, widely used in the open-source ecosystem.
   - Pros:
     - Extremely mature — used by thousands of projects (React, Jest, Babel, etc.). Large community, extensive plugin ecosystem.
     - Built-in versioning, i18n, and search (Algolia DocSearch integration).
     - MDX support with excellent documentation.
     - Active development with regular releases.
   - Cons:
     - Not Next.js-based — Docusaurus uses its own React framework with client-side routing. This means a different build system, different deployment patterns, and a different mental model from the main HeyKazi app.
     - Does not use Next.js App Router. Contributors must context-switch between two different React frameworks.
     - Vercel deployment works but is not the primary target — Docusaurus is optimized for GitHub Pages and Netlify. Configuration for Vercel requires manual adjustments.
     - Theming is CSS Module-based (not Tailwind). Matching HeyKazi's Tailwind-based design system requires translating design tokens into a different styling paradigm.
     - Uses Webpack/SWC, not the same bundler configuration as the main app. The monorepo gains a second distinct build pipeline.
     - Algolia DocSearch is powerful but requires an external service (or self-hosted Algolia). At ~30 articles, this is overengineered.

3. **Fumadocs** — A newer Next.js App Router documentation framework, similar to Nextra but with a different architecture.
   - Pros:
     - Built on Next.js App Router — same framework alignment as Nextra.
     - Tailwind CSS support out of the box.
     - More granular control over page rendering than Nextra.
     - Built-in search (Orama or Flexsearch).
     - Active development, good TypeScript support.
   - Cons:
     - Smaller community than both Nextra and Docusaurus. Fewer tutorials, fewer StackOverflow answers, fewer examples.
     - Less mature theming system — fewer pre-built theme components. More DIY work required for a polished docs theme.
     - Migration risk: if the project loses momentum, maintenance burden falls on HeyKazi.
     - No clear advantage over Nextra 4 for HeyKazi's requirements. Both are Next.js App Router + MDX + Flexsearch. Nextra has the larger community and more established docs theme.

4. **Mintlify** — Commercial documentation-as-a-service platform.
   - Pros:
     - Beautiful default theme with minimal configuration.
     - Hosted service — no build pipeline to maintain.
     - API reference auto-generation, analytics, feedback widgets built in.
     - Fast setup — connect to GitHub, point to MDX files, deploy.
   - Cons:
     - SaaS cost: paid plans start at $150/month (Startup tier). At the 5–20 tenant scale, this is an unnecessary recurring expense for a static documentation site.
     - Vendor lock-in: content must conform to Mintlify's MDX extensions and frontmatter schema. Migration away requires rewriting content.
     - Limited theming: custom fonts and color systems are constrained to Mintlify's configuration options. Matching HeyKazi's exact design system may not be possible.
     - Content lives in Mintlify's build pipeline, not the HeyKazi monorepo's CI/CD. PRs cannot include both code changes and documentation changes in a single atomic commit.
     - No self-hosting option. The documentation is served from Mintlify's infrastructure — a dependency on an external service for a static site.

5. **GitBook** — Wiki-style documentation platform with a web editor and Git sync.
   - Pros:
     - WYSIWYG editor for non-technical contributors.
     - Git sync keeps content in the repo.
     - Free for open-source, affordable for small teams.
     - Search, analytics, and feedback built in.
   - Cons:
     - GitBook's Git sync is one-way or conflict-prone — editing in both the web editor and Git simultaneously creates merge issues.
     - Theming is extremely limited. Custom fonts, color systems, and layout are constrained to GitBook's design options. HeyKazi's design system cannot be applied.
     - Content format is GitBook-specific Markdown, not standard MDX. Migration requires conversion.
     - SaaS dependency for what is fundamentally a static site.
     - No Next.js integration — the doc site runs on GitBook's platform, not Vercel. This adds a vendor dependency and a separate domain configuration workflow.

**Decision**: Option 1 — Nextra 4.

**Rationale**:

The decisive factors are framework alignment, monorepo integration, and cost.

Framework alignment is the strongest argument. HeyKazi's frontend is Next.js 16 (App Router), React 19, TypeScript 5, and Tailwind CSS v4. Nextra 4 is built on the same stack. Contributors working on documentation use the same tooling, the same file conventions, the same build system as the main app. Docusaurus (Option 2) introduces a second React framework with different routing, different styling, and different build tooling — an unnecessary cognitive overhead for a team already fluent in Next.js.

Monorepo integration is the second factor. MDX files under `docs/content/` are version-controlled alongside the codebase. When a phase adds a new feature, the documentation article can be written in the same branch, reviewed in the same PR, and deployed atomically. Mintlify (Option 4) and GitBook (Option 5) break this workflow by running content through external build pipelines.

Cost eliminates the SaaS options. Nextra is MIT-licensed. Deployment on Vercel uses the existing plan. There is no per-seat, per-page, or per-month fee. For ~30 articles at the 5–20 tenant scale, paying $150+/month for Mintlify's theme polish is unjustifiable when Nextra's docs theme can be customized to match HeyKazi's design system with a few hours of CSS work.

Fumadocs (Option 3) was the closest alternative. It shares the same technical profile as Nextra (Next.js App Router, MDX, Flexsearch, Tailwind). The tiebreaker is community size and theme maturity — Nextra has a larger user base, more documentation, and a more polished out-of-the-box docs theme. The risk of choosing a smaller project with less community support is not worth the marginal flexibility Fumadocs offers.

**Consequences**:

- The doc site uses the same Next.js + React + TypeScript + Tailwind stack as the main app. No new framework to learn.
- MDX content is version-controlled in the monorepo. Documentation PRs are atomic with code changes.
- Deployment is a standard Vercel Next.js project. No new vendor, no new billing, no new ops burden.
- Full-text search is client-side via Flexsearch. No external search service to configure or pay for.
- Theming requires manual CSS overrides to match HeyKazi's design tokens (Sora font, slate palette, teal accents). This is a one-time setup cost in Slice 59A.
- If Nextra 4 development stalls, migration to Fumadocs or raw Next.js + MDX is straightforward because the content is standard MDX files with minimal framework-specific extensions.
- No built-in analytics. If page view tracking becomes important post-launch, a lightweight solution (Vercel Analytics, Plausible) can be added without changing the framework.
