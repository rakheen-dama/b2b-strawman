# ADR-228: Separate Site vs In-App Help

**Status**: Accepted

**Context**:

HeyKazi has three existing help mechanisms: (1) `HelpTip` popover tooltips that provide brief, contextual explanations at the field level (Phase 43), (2) `EmptyState` components on empty list pages that explain what a feature does and prompt the user to take action (Phase 43), and (3) an in-app AI assistant (Phase 52) that answers questions conversationally using tool-calling against the user's data. These mechanisms serve different purposes: tooltips answer "what does this field mean?", empty states answer "what is this feature?", and the AI assistant answers open-ended questions.

What is missing is a browsable, searchable reference layer — a place where users can read a complete guide on invoicing, learn the end-to-end workflow for onboarding a new customer, or discover features they haven't tried yet. The question is where this reference material should live: as a separate documentation site (a standalone web property), or embedded within the main application as an in-app help panel or drawer.

**Options Considered**:

1. **Separate documentation site at `docs.heykazi.com`** — A standalone Nextra site deployed as its own Vercel project, linked from the main app via contextual deep-links.
   - Pros:
     - Zero impact on the main app's bundle size. Documentation content (text, search index) is never loaded by the main app — it lives on a separate domain with its own build.
     - Independent deployment: doc site can be updated, redeployed, and rolled back without touching the main app. Content fixes ship in minutes without a full frontend redeploy.
     - Full-page reading experience: articles get a dedicated layout with a table of contents sidebar, breadcrumbs, search, and proper typographic hierarchy. In-app panels are constrained to a drawer or modal — insufficient for 800-word feature guides.
     - SEO benefit: documentation pages at `docs.heykazi.com` are indexable by search engines. Users searching "HeyKazi invoicing" or "HeyKazi time tracking" find the doc site. In-app help is behind authentication and invisible to search engines.
     - Complementary to existing help: tooltips provide micro-context, the AI assistant provides conversational answers, and the doc site provides structured reference. Each mechanism serves a different user intent. No overlap, no redundancy.
     - Standard pattern: Stripe, Linear, Vercel, Notion, and virtually every B2B SaaS product uses a separate documentation site. Users expect this pattern.
   - Cons:
     - Context switch: clicking "Learn more" opens a new tab, taking the user out of the app. They must navigate back to the app to continue their task.
     - Two deployments to maintain: the doc site is a separate Vercel project with its own build pipeline, environment variables, and domain configuration.
     - Link integrity: if a doc page is renamed or deleted, in-app links break silently (no build-time validation across the two projects).

2. **In-app help panel (drawer/sidebar)** — A slide-out panel within the main app that loads documentation content, either from embedded MDX or fetched from an API.
   - Pros:
     - No context switch: the user reads help content without leaving the current page. The panel overlays or sits beside the feature they need help with.
     - Single deployment: help content is part of the main frontend build. No separate Vercel project, no separate domain.
     - Potential for contextual awareness: the panel could auto-suggest articles based on the current route (e.g., on the invoices page, show the invoicing guide).
   - Cons:
     - Bundle size impact: embedding ~26 articles worth of MDX content (~18,000 words) into the main app increases the JavaScript bundle. Even with lazy loading, the MDX runtime, search index, and content chunks add to the app's footprint.
     - Constrained reading experience: a 300-400px wide drawer is too narrow for comfortable long-form reading. Tables, code blocks, and step-by-step instructions render poorly in a side panel. Expanding to full-width defeats the "no context switch" benefit.
     - Build coupling: every content edit requires a full frontend build and deployment. The main app's CI/CD pipeline runs tests, type-checks, and builds — adding 26 MDX files to this pipeline increases build time and couples documentation changes to application deployment.
     - Duplicates the AI assistant: the in-app AI assistant (Phase 52) already provides contextual, conversational help within the app. An in-app help panel that also shows content within the app competes for the same user attention and UI real estate. The assistant can answer "how do I create an invoice?" better than a static panel because it has access to the user's actual data and can walk them through it step by step.
     - No SEO: content behind authentication is invisible to search engines. Users cannot discover HeyKazi features via Google.
     - Implementation complexity: requires building a content loading system, a panel component, navigation within the panel, search within the panel — essentially building a mini documentation site inside the app.

3. **Embedded iframe help widget** — A third-party or self-hosted widget (e.g., Intercom Articles, HelpScout Beacon, or a custom iframe) that loads documentation content in a floating widget.
   - Pros:
     - Minimal frontend code: the widget is a third-party script or an iframe pointing to the doc site. No MDX embedding, no content loading logic.
     - In-context: the widget floats over the current page, providing help without a full tab switch.
     - Some widgets include search, article suggestion, and contact-support escalation in a unified interface.
   - Cons:
     - Third-party dependency and cost: Intercom ($74+/month), HelpScout ($20+/month). At the 5–20 tenant scale, this is an unnecessary expense when the content is static MDX.
     - Limited rendering: widgets have constrained viewport (typically 400x600px). Long articles with tables, steps, and code blocks render poorly.
     - Iframe isolation: an iframe loading `docs.heykazi.com` cannot interact with the parent app. No contextual awareness, no route-based article suggestion without custom postMessage plumbing.
     - Duplicates the AI assistant: same issue as Option 2 — two in-context help mechanisms competing for user attention.
     - Performance: third-party widgets load external scripts, add network requests, and can impact page load time. The main app already loads Keycloak, Tiptap, Recharts, and other heavy dependencies.
     - Branding constraints: third-party widgets have their own design language. Matching HeyKazi's design system inside an Intercom widget is limited to color and logo customization.

**Decision**: Option 1 — Separate documentation site at `docs.heykazi.com`.

**Rationale**:

The decision hinges on the relationship between the doc site and the existing in-app help mechanisms. HeyKazi already has two in-context help systems: HelpTip tooltips for micro-context and the AI assistant for conversational help. Adding a third in-context mechanism (an embedded panel or widget) creates redundancy. The doc site serves a fundamentally different purpose: it is a reference manual that users browse proactively, not a reactive help system triggered during a task.

The reading experience is the second factor. Feature guides are 500–1,000 words with tables, step lists, and cross-links to related features. This content needs a full-page layout with a sidebar table of contents, proper heading hierarchy, and readable line lengths. A 300px drawer or a 400x600px widget cannot render this content well. Users deserve the same reading quality they get from Stripe's docs or Linear's changelog — and that requires a dedicated page.

The bundle size and build coupling arguments favor separation. The main app is already a substantial Next.js application with ~280+ frontend tests, Tiptap rich text editing, Recharts dashboards, and complex form logic. Adding 18,000 words of MDX content, a search index, and a rendering pipeline to this build adds complexity and build time for no benefit. The doc site is better served by its own lightweight build that deploys in seconds.

The SEO benefit is a bonus, not the primary driver. But it matters: at the 5–20 tenant scale, users searching for "HeyKazi how to create invoice" should land on the doc site, not find nothing.

Options 2 and 3 are eliminated because they duplicate the AI assistant's role (in-context help), render long-form content poorly, and add complexity or cost without proportional benefit.

**Consequences**:

- Users clicking "Learn more" from a HelpTip or "Read the guide" from an EmptyState open a new tab to `docs.heykazi.com`. This is a deliberate context switch — the user has signaled intent to learn in depth, not perform a quick task.
- The doc site is a separate Vercel project. It has its own build pipeline, its own domain, and its own deployment schedule. This is additional infrastructure to maintain, but it is a static Next.js site with no backend — the ops burden is minimal.
- Link integrity between the main app and the doc site is not validated at build time. If a doc page is renamed, the in-app link will 404. Mitigation: use stable URL paths that map to feature domains (e.g., `/features/invoicing`) and avoid renaming. A future Playwright E2E test could verify link integrity.
- The in-app AI assistant remains the primary in-context help mechanism. The doc site is the reference layer. Users who need quick answers use the assistant. Users who want to learn a feature end-to-end read the doc site. These are complementary, not competing.
- The sidebar "Help" link opens the doc site in a new tab. This is consistent with how Linear, Vercel, and other B2B SaaS products handle their help link.
