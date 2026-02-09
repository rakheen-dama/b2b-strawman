## Prompt for Architect Agent (Phase 2: Billing & Tiered Tenancy)

You are an experienced SaaS architect.  
I have an existing multi‑tenant SaaS “strawman” implementation built with the following stack:

- Frontend: Next.js (app router), TypeScript, Tailwind, Shadcn UI, Clerk Organizations for auth and multi‑tenant org management.
- Backend: Spring Boot 4 (Java 25) REST API with schema‑per‑tenant multitenancy on Neon Postgres, RBAC using Clerk org roles.
- Storage: Neon Postgres (single DB), schema‑per‑tenant model, Flyway for migrations and dynamic schema provisioning, AWS S3 for file storage.
- Runtime: Containerized services, deployable to AWS ECS/Fargate (or similar).

The current architecture and decisions are documented in `ARCHITECTURE.md`. This is the baseline you should work from.

## Critical Constraints

### Context Management
- **Your context window shoulf not exceed 75% utilization**
- **Use sub-agents for ALL research tasks** and code exploration - do not attempt to research directly
- When you need to research best practices for any technology (Clerk, Neon, Spring Boot, Next.js, Flyway, AWS, etc.), spawn a sub-agent with a focused research query
- Summarize sub-agent findings concisely before incorporating into your architecture

### Sub-Agent Usage Pattern
When you need to research a topic, use this pattern:

```
Task for sub-agent: Research [specific topic]
- Question 1: [specific question]
- Question 2: [specific question]
- Return: Summary of findings with sources/references
```

### New Requirements (Phase 2 Evolution)

I want to evolve this reusable SaaS starter to introduce **Clerk Billing** for subscription management and to support **tiered multitenancy**:

1. **Clerk Billing integration**
    - Use Clerk Billing (with Stripe under the hood) to manage B2B subscriptions at the organization level.[1][2][3][4][5]
    - Plans/tiers must be defined and managed in Clerk Billing (e.g., “Starter” and “Pro” plans).
    - I want to avoid building custom billing UIs and webhooks where possible; leverage Clerk’s billing components and session‑aware billing flows.[2][3][4][5]
    - The application must be able to read subscription state and plan/feature entitlements from Clerk to enforce limits in the backend and frontend.

2. **Two subscription tiers with different tenancy models**
    - **Tier 1 – “Starter” (cheapest tier)**:
        - All organizations on this tier share a **single shared schema** in Postgres (e.g. `tenant_shared`), not schema‑per‑tenant.
        - Data isolation is at **row level**, enforced via:
            - A `tenant_id` / `org_id` column on all shared tables.
            - Application‑enforced filtering and/or Postgres Row Level Security (RLS), if you recommend it.[6][7][8]
        - Hard limits:
            - Maximum of **2 members** per organization.
    - **Tier 2 – “Pro”**:
        - Organizations have **schema‑level isolation** (same as current implementation).
        - Each Pro org gets its own schema provisioned via the existing Flyway‑based tenant provisioning flow.
        - Hard limits:
            - Maximum of **10 members** per organization.
    - The system must support:
        - Correct provisioning flow depending on tier (shared schema vs dedicated schema).
        - Possible future migration path (e.g., upgrading a Starter org from shared schema to its own schema) – you do not have to fully design the migration process now, but call out implications and a plausible high‑level approach.[7][6]

3. **Enforcement of plan limits and entitlements**
    - Member limits:
        - Enforced via Clerk Billing plan configuration where possible (features/entitlements), and backed up by server‑side checks.
        - Backend must reject operations that violate member count limits.
    - Data model and tenancy:
        - Backend must enforce:
            - Correct schema selection for Pro orgs.
            - Correct row‑level filtering (`tenant_id`) or RLS for Starter orgs.
        - The architecture must clearly separate the paths for:
            - Shared‑schema tenants vs schema‑per‑tenant tenants.
    - The frontend must:
        - Reflect plan information (e.g. show which plan the org is on).
        - Surface appropriate UX (e.g. “Upgrade” prompts when a Starter org hits member or feature limits), using Clerk Billing components where possible.[3][4][5][2]

4. **Clerk Billing data flow**
    - Use Clerk Billing to:
        - Define plans and features (e.g., “Starter: shared schema, 2 members; Pro: dedicated schema, 10 members”).[4][5][1]
        - Manage subscription lifecycle (trial, active, canceled).
    - The app must:
        - Read plan/subscription information for the active organization (e.g. via Clerk’s SDK/APIs) and use that to:
            - Decide whether an org should be in shared schema or dedicated schema.
            - Enforce plan‑based access checks on backend endpoints and UI.
    - You may assume Clerk Billing is already wired to Stripe and handles payments. Focus on how we use Clerk Billing’s state to control application behavior.

### What I Want You To Produce

Using `ARCHITECTURE.md` and the existing code as your baseline, **analyze the current architecture and implementation**, then produce an **amended architecture and decision record set** for Phase 2:

1. **Updated architecture description**
    - Extend or revise the existing architecture to account for:
        - Integration of Clerk Billing (plans, subscription state, feature entitlements) into both frontend and backend.[5][1][2][3][4]
        - The coexistence of:
            - Starter orgs in a **shared schema** with row‑level isolation.
            - Pro orgs in **per‑schema** isolation using the existing multitenant approach.
        - How the org’s plan (from Clerk Billing) determines:
            - Which tenancy model to use (shared vs per‑schema).
            - Member limits and any other feature flags.
    - Explicitly describe:
        - Data flow from Clerk Billing → application (how we read subscription and plan info).
        - Provisioning flows for:
            - New Starter orgs: register org, map to shared schema, no new schema creation.
            - New Pro orgs: register org, create dedicated schema, run Flyway tenant migrations.
        - How upgrades/downgrades between tiers **should** be handled conceptually, including constraints and risks (you can stay at a conceptual level here).
    - Provide updated component boundaries and responsibilities, including where billing‑related logic will live (frontend vs backend) and how it is tested.

2. **Mermaid diagrams**
    - Provide **Mermaid diagrams** embedded in markdown, for example:
        - A high‑level component diagram showing:
            - Next.js + Clerk (auth + billing).
            - Spring Boot backend.
            - Neon Postgres (public schema, shared tenant schema, dedicated tenant schemas).
            - AWS S3.
        - Sequence diagrams for:
            1. New Starter org signup:
                - User signs up and selects Starter plan via Clerk Billing.
                - Org is created in Clerk Organizations.
                - Org is registered in our system, mapped to shared schema.
                - Data access path for Starter org (shared schema with row‑level isolation).
            2. New Pro org signup:
                - Similar to above, but resulting in dedicated schema provisioning via Flyway.
            3. Plan upgrade Starter → Pro (conceptual):
                - Where we’d hook into billing events.
                - How/when we’d provision a new schema and migrate data (conceptually, acknowledging that the detailed migration implementation is future work).[7]
        - Any additional diagrams you think are useful (e.g. request‑time tenant resolution including plan checks).

3. **Architecture Decision Records (ADRs)**

The existing spec had several decision points (Section 9). For those decisions, plus any new decisions that Phase 2 introduces, produce ADRs with this structure:

```markdown
### ADR-{number}: {Decision Title}

**Status**: Accepted

**Context**: {Why this decision is needed}

**Options Considered**:
1. {Option A} - {Pros/Cons}
2. {Option B} - {Pros/Cons}
3. {Option C} - {Pros/Cons}

**Decision**: {Chosen option}

**Rationale**: {Why this option was chosen, referencing research findings}

**Consequences**: {Impact of this decision}
```

For example, at minimum I expect ADRs for the following decision points (extend or refactor existing ADRs as needed):

1. **Billing integration approach**
    - E.g., “Use Clerk Billing (with Stripe) as the source of truth for plans and subscription state, rather than integrating Stripe directly.”[1][2][3][4][5]

2. **Tier‑dependent tenancy model**
    - E.g., “Starter uses shared schema with row‑level isolation; Pro uses schema‑per‑tenant.”

3. **Row‑level isolation mechanism for Starter tier**
    - E.g., “Use Postgres RLS + `tenant_id` on shared tables vs only app‑level filtering vs additional logical sharding.”[8][6][7]

4. **Plan enforcement strategy**
    - Where and how we enforce member limits and feature entitlements:
        - Clerk Billing features.
        - Backend checks.
        - Frontend checks.

5. **Org provisioning flow per tier**
    - How the provisioning pipeline branches:
        - Shared schema mapping for Starter orgs.
        - Dedicated schema provisioning with Flyway for Pro orgs.

6. **Optional**: Any other major decisions you think we should explicitly capture in ADRs for this phase (e.g., how to store a local cache of plan/entitlement data, how to structure database tables for shared vs dedicated schemas, etc.).

For each ADR, please:

- Reference relevant trade‑offs and best‑practice guidance (for example, shared schema vs schema‑per‑tenant isolation trade‑offs, or pros/cons of relying on Clerk Billing vs Stripe directly).[6][8][7]
- Make the decisions consistent with the existing Phase 1 architecture and codebase where possible.
- Clearly describe the impact on future evolution (e.g., adding a third tier, moving some tenants to DB‑per‑tenant later).

4. **Implementation guidance (high level)**

You do not need to write full production code, but please outline:

- Where in the **frontend** we should:
    - Integrate Clerk Billing components (pricing tables, checkout).
    - Read subscription/plan and expose it to the app (e.g., via React context or server actions).
- Where in the **backend** we should:
    - Read and interpret plan information (e.g., from Clerk session/JWT or via an API call).
    - Enforce tier limits and select tenancy model per request (shared vs dedicated schema).
    - Extend Flyway provisioning logic for Pro orgs while leaving Starter orgs in the shared schema.

Assume the reader has access to `ARCHITECTURE.md` and the existing codebase. Your output should be a **self‑contained markdown document** that includes the updated architecture narrative, Mermaid diagrams, and ADRs, ready to commit into the repo as the new version of `ARCHITECTURE.md` and a new `/adr` folder.

***

You can paste this entire prompt into Claude and point it at your repo so it can inspect `ARCHITECTURE.md` and the code, then generate the amended architecture, diagrams, and ADRs.

Sources
[1] Clerk Billing https://clerk.com/billing
[2] Using Clerk Billing to add Subscriptions to your app! Clerk + Stripe! https://www.youtube.com/watch?v=Ncqs2PK2aCo
[3] How to add subscription billing to your SaaS app (Clerk Billing + Next.js) https://www.youtube.com/watch?v=m0hmvwaVacw
[4] Clerk Billing - Guides | Clerk Docs https://clerk.com/docs/guides/billing/overview
[5] Getting started with Clerk Billing https://clerk.com/blog/intro-to-clerk-billing
[6] Schema Migration Strategy https://asadali.dev/blog/multi-tenant-saas-practical-comparison-database-per-tenant-vs-shared-schema/
[7] Postgres Multitenancy in 2025: RLS vs Schemas vs Separate ... https://debugg.ai/resources/postgres-multitenancy-rls-vs-schemas-vs-separate-dbs-performance-isolation-migration-playbook-2025
[8] PostgreSQL's schemas for multi-tenant applications - Stack Overflow https://stackoverflow.com/questions/44524364/postgresqls-schemas-for-multi-tenant-applications
[9] Pricing - Clerk https://clerk.com/pricing
[10] Real-Time Pricing Calculator for Every Business - Clerk.io https://www.clerk.io/pricing
[11] Clerk — Document AI. https://clerk-app.com/en/pricing/
[12] Clerk Pricing - The Complete Guide https://supertokens.com/blog/clerk-pricing-the-complete-guide
[13] Integrate the Clerk API with the Stripe API https://pipedream.com/apps/clerk/integrations/stripe
[14] Clerk pricing: How it works and compares to WorkOS https://workos.com/blog/clerk-pricing
[15] Is Clerk‘s pricing really that insane or am I missing something? https://www.reddit.com/r/nextjs/comments/167pj2d/is_clerks_pricing_really_that_insane_or_am_i/
