# ADR-213: Update-in-place vs. Rewrite Infrastructure

**Status**: Proposed
**Date**: 2026-04-01
**Phase**: 56

## Context

The existing Terraform infrastructure was built approximately 6 months ago for a 2-service architecture (frontend + backend) using Clerk for authentication. The application has since grown to 5 deployable services (frontend, backend, gateway, portal, Keycloak) with Keycloak replacing Clerk. The infrastructure modules reference Clerk secrets, only provision 2 ECR repositories, only define 2 ECS task definitions, and route the ALB directly to frontend and backend instead of through the Gateway BFF.

The question is whether to update the existing Terraform modules to support the current architecture or rewrite them from scratch.

## Options Considered

### Option 1: Full Rewrite

Start fresh with new Terraform modules structured for the 5-service architecture.

- **Pros:** Clean slate, no legacy naming or structure constraints, can adopt newer Terraform patterns, opportunity to consolidate modules
- **Cons:** Discards 6 months of proven, well-structured infrastructure code. Requires re-testing every module (VPC, security groups, IAM policies). Higher risk of introducing bugs in networking/security configuration. Significantly more effort (~3-4 weeks vs. ~1.5 weeks). Loses battle-tested patterns (circuit breakers, deployment settings, least-privilege IAM)

### Option 2: Update in Place (Selected)

Extend existing modules to support 5 services. Replace stale references (Clerk to Keycloak). Add missing modules (data stores, OIDC).

- **Pros:** Preserves proven VPC, IAM, and ECS patterns. Lower risk -- each module update is an incremental, testable change. Faster time to production (~8-10 days). Module structure is already well-designed (proper separation, parameterization, tagging). Changes are scoped and reviewable.
- **Cons:** Some naming inconsistencies may persist (internal `docteams` references vs. external `heykazi`). Modules may carry dead code paths during transition. Requires understanding of existing module structure before making changes.

### Option 3: Hybrid (Rewrite Some, Extend Others)

Rewrite modules that need fundamental structural changes (ECS, ALB), extend modules that just need more entries (ECR, monitoring).

- **Pros:** Gets the best of both approaches for heavily-changed modules
- **Cons:** Inconsistent patterns between rewritten and extended modules. Requires judgment calls on which modules to rewrite. Risk of breaking inter-module output references during partial rewrite. More complex migration path.

## Decision

**Option 2 -- Update in place.**

## Rationale

1. **Module quality is high.** The infrastructure audit (`.infra-audit.md`) found "the Terraform module structure is well-designed -- proper separation, parameterization, tagging, and least-privilege IAM." The problem is scope and staleness, not quality.
2. **Risk minimization.** VPC, security group, and IAM configurations are the highest-risk infrastructure components. The existing modules have correct CIDR ranges, security group references, and scoped IAM policies. Rewriting them introduces risk of subtle networking or permission bugs that are difficult to detect until production.
3. **Effort efficiency.** Extending an ECR module from 2 repositories to 5 is a ~10 line change. Extending an ECS module with 3 new task definitions follows an established pattern (copy, modify env vars). A full rewrite would re-derive these patterns from scratch.
4. **Incremental testing.** Each module update can be tested in isolation via `terraform plan`. A full rewrite requires testing the entire stack simultaneously.
5. **The composite `ecs-deploy` action works for any service.** The CI/CD deployment action is service-agnostic -- it just needs a cluster, service name, and image URI. Extending it to 5 services requires no changes to the action itself.

## Consequences

- **Positive:** Faster time to production. Lower risk. Preserves battle-tested infrastructure patterns. Each slice is independently deployable and testable.
- **Negative:** Some internal resource names retain `docteams` prefix (see ADR-218 for the naming migration strategy). Module variable lists grow longer with 5 services. Code reviewers need to understand existing module structure.
- **Mitigations:** ADR-218 defines a gradual naming migration strategy. Module variable lists are managed via `locals` blocks and `for_each` where appropriate (e.g., refactoring ECR from 2 hardcoded repos to a `for_each` over a service list). CLAUDE.md for `infra/` will be updated with the current module structure.
