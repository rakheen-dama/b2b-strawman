# ADR-217: CI/CD Image Promotion

**Status**: Proposed
**Date**: 2026-04-01
**Phase**: 56

## Context

The current CI/CD pipeline rebuilds Docker images from source for each environment. When deploying to staging, the `deploy-staging.yml` workflow checks out the code, builds frontend and backend images, pushes to ECR, then deploys. When deploying to production, the `deploy-prod.yml` workflow does the same -- a completely independent build from source.

This means the binary deployed to production is not the same binary that was tested in staging. Source code is identical (same Git SHA), but the build process is non-deterministic: different layer caching, different npm/Maven resolution timing, potentially different transitive dependency versions. This undermines confidence that "what we tested is what we deploy."

With 5 services (frontend, backend, gateway, portal, keycloak), the problem multiplies -- each environment rebuild is 5 parallel Docker builds, each with its own build cache and timing.

## Options Considered

### Option 1: Rebuild Per Environment (Current)

Each environment triggers its own Docker build from source. Images are tagged with the Git SHA but built independently.

- **Pros:** Simple to understand. Each environment's ECR has its own images. No cross-environment concerns.
- **Cons:** Different binaries in each environment. Longer deployment times (rebuild on every deploy, ~5-10 min per service). Wasted CI compute (building the same code 2-3 times). Risk of "works in staging, breaks in prod" due to non-deterministic builds. With 5 services, each deploy triggers 5 Docker builds.

### Option 2: Single ECR with Environment Tags (Selected)

One ECR repository per service (e.g., `kazi/backend`). Images are built once on merge to `main`, tagged with the Git SHA. Promotion to staging or production adds an environment tag to the existing image -- no rebuild.

```
Build (merge to main):
  kazi/backend:abc123       (Git SHA)
  kazi/backend:staging      (promoted to staging)

Promote to production:
  kazi/backend:abc123       (same image, already exists)
  kazi/backend:production   (new tag on same manifest)
```

- **Pros:** Same binary tested in staging is deployed to production (build once, deploy everywhere). Faster promotions (~30 seconds to re-tag vs. ~5-10 minutes to rebuild). Less CI compute cost. ECR image scanning runs once per image. Clear audit trail (Git SHA tag traces back to exact commit).
- **Cons:** Single ECR repository per service means both environments share access. Requires ECR tag mutation (or immutable tags with SHA + env suffix). Environment-specific build args (e.g., `NEXT_PUBLIC_GATEWAY_URL`) must be handled differently -- either at build time with the production URL or at runtime.

### Option 3: Cross-Account ECR Promotion

Separate AWS accounts for staging and production. Images built in staging account, promoted to production account via `ecr:ReplicateImage` or manual pull/push.

- **Pros:** Strongest isolation between environments. Production account has independent IAM, billing, and access controls. Industry best practice for regulated environments.
- **Cons:** Requires two AWS accounts (HeyKazi currently uses one). Cross-account IAM trust policies add complexity. ECR cross-account replication has additional cost. Over-engineering for a 5-20 tenant SaaS with a single-person operations team.

## Decision

**Option 2 -- Single ECR repository per service with environment-specific tags.**

## Rationale

1. **Build determinism matters.** Non-deterministic builds are a known source of production incidents. Even with the same Git SHA, Docker layer caching, package registry state, and build timing can produce different binaries. Building once eliminates this entire class of risk.
2. **Promotion is fast.** Tagging an existing ECR image takes ~2 seconds (API call). Rebuilding a Java service from source takes 5-10 minutes (Maven dependency resolution, compilation, Spring Boot extract, Docker layer push). With 5 services, that's 25-50 minutes of CI time saved per promotion.
3. **Single AWS account is appropriate at this scale.** Cross-account separation (Option 3) is an industry best practice for large organizations but adds significant complexity for a startup with 5-20 tenants and a founder who is also the sole operator. Environment separation via tags and ECS cluster names is sufficient.
4. **Build args for Next.js are environment-independent.** The frontend's `NEXT_PUBLIC_AUTH_MODE=keycloak` is the same across environments. The `NEXT_PUBLIC_GATEWAY_URL` can be set to the production URL at build time (staging uses the same value since it's baked into the client JavaScript and the staging frontend makes client-side requests to its own domain, not the production gateway).

**Correction on build args**: `NEXT_PUBLIC_GATEWAY_URL` is tricky because it's baked at build time. For staging, it should be `https://staging-app.heykazi.com`. For production, `https://app.heykazi.com`. This means the frontend image IS environment-specific. **Resolution**: Build two frontend images (one per environment) or use a runtime env injection approach. Given the cost is only one extra Docker build for the frontend (not all 5 services), build two frontend images. Backend, gateway, portal, and keycloak images are environment-agnostic (configuration via runtime env vars).

## Consequences

- **Positive:** Same binary in staging and production (except frontend, which has env-specific build args). Faster promotions. Less CI compute. Clear audit trail via Git SHA tags. ECR lifecycle policies apply uniformly.
- **Negative:** Frontend requires per-environment builds due to Next.js `NEXT_PUBLIC_*` inlining. Single ECR repo means staging and production share the same image repository (mitigated by tag discipline). Requires updating all deploy workflows to use tag-promotion instead of rebuild.
- **Mitigations:** Frontend is the only service with environment-specific build args -- the other 4 services build once and promote. ECR lifecycle policy keeps the last 10 tagged images and expires untagged after 7 days, preventing unbounded storage growth. Tag naming convention (`{sha}`, `staging`, `production`) is enforced by the CI/CD workflows.
