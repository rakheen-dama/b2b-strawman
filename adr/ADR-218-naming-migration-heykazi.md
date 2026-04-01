# ADR-218: Naming Migration (docteams to heykazi)

**Status**: Proposed
**Date**: 2026-04-01
**Phase**: 56

## Context

The infrastructure was originally built under the name "docteams" (the internal codebase name). All AWS resources, Terraform state, ECR repositories, ECS clusters, CloudWatch log groups, S3 buckets, and resource tags use the `docteams` prefix. The product has since been branded as "HeyKazi" with the domain `heykazi.com`. The internal codebase uses `kazi` as the short name (Java package `io.b2mash.b2b.b2bstrawman`, but service names use `kazi`).

AWS resource naming during Phase 56 needs to decide how to handle the transition from `docteams` to `heykazi`/`kazi`.

**Scope of `docteams` references:**
- S3 state bucket: `docteams-terraform-state`
- S3 document buckets: `docteams-{env}` (e.g., `docteams-prod`)
- ECR repositories: `docteams-{env}-frontend`, `docteams-{env}-backend`
- ECS cluster: `docteams-{env}`
- ECS services: `docteams-{env}-frontend`, `docteams-{env}-backend`
- ECS task families: `docteams-{env}-frontend`, `docteams-{env}-backend`
- ALBs: `docteams-{env}-public`, `docteams-{env}-internal`
- Target groups: `docteams-{env}-fe`, `docteams-{env}-be`, `docteams-{env}-be-int`
- Security groups: `docteams-{env}-sg-*`
- IAM roles: `docteams-{env}-ecs-execution`, `docteams-{env}-backend-task`, etc.
- CloudWatch log groups: `/ecs/docteams-{env}-frontend`, `/ecs/docteams-{env}-backend`
- DynamoDB lock table: `docteams-terraform-locks`
- Resource tags: `Project = docteams`

## Options Considered

### Option 1: Rename Everything at Once

Change the `project` variable from `docteams` to `heykazi` across all Terraform modules and environments. Run `terraform apply` -- Terraform destroys old-named resources and creates new-named ones.

- **Pros:** Clean, consistent naming throughout. No legacy names to confuse new contributors. Single migration event.
- **Cons:** Destructive -- Terraform cannot rename most AWS resources. It will destroy and recreate ECS services (downtime), ALBs (DNS disruption), security groups (breaks ECS task networking), and IAM roles (breaks running tasks). S3 buckets cannot be renamed at all (must create new, copy data, delete old). ECR repositories would lose all existing images. State bucket rename requires manual state migration. High risk of extended outage during the transition.

### Option 2: Create New Resources Alongside Old

Create all new resources with `heykazi` prefix while keeping old `docteams` resources. Run both in parallel, then decommission old resources.

- **Pros:** Zero-downtime migration. Can be done incrementally. Rollback is possible (switch back to old resources).
- **Cons:** Double the AWS cost during migration (two ALBs, two sets of ECS services, two sets of target groups). Complex Terraform state management (importing old resources, managing two sets). Significantly more effort than the value of having correct names. At 5-20 tenants, the operational complexity is not justified.

### Option 3: Rename Customer-Facing + New Resources Only (Selected)

Keep internal resource names as-is (`docteams-{env}-*` for existing ECS services, security groups, IAM roles). Use the new name (`heykazi`/`kazi`) only for:
- New resources added in Phase 56 (gateway, portal, keycloak services, data stores, monitoring)
- Customer-facing resources (DNS records, ACM certificates, S3 document bucket)
- Terraform state infrastructure (new state bucket)
- Resource tags (`Project = kazi`)

- **Pros:** No destructive changes to existing resources. New resources start with the correct name. Customer-facing names are correct from day one. Existing internal names are invisible to users. Gradual migration -- old names get replaced naturally when resources are recreated for other reasons.
- **Cons:** Mixed naming: existing services are `docteams-{env}-frontend` while new services are `heykazi-{env}-gateway`. Slightly confusing for operators who see both names in the AWS console.

## Decision

**Option 3 -- Rename customer-facing and new resources, keep internal names until natural replacement.**

## Rationale

1. **AWS resources are not renameable.** Most AWS resources (S3 buckets, security groups, IAM roles, ECS services) cannot be renamed. The only way to "rename" them is destroy and recreate, which causes downtime and risk. This is not worth doing for cosmetic reasons.
2. **Internal names are invisible to customers.** Users see `app.heykazi.com`, not `docteams-prod-frontend`. The ECS service name, security group name, and IAM role name are operational details that only appear in the AWS Console and Terraform state.
3. **New resources get the right name.** All new resources created in Phase 56 (gateway service, portal service, keycloak service, RDS, ElastiCache, CloudWatch alarms, SNS topics) use `heykazi`/`kazi` naming. Over time, as Phase 56 adds more resources than the original 2-service deployment, the majority of resources will have the new name.
4. **Natural replacement handles the rest.** When existing resources need to be recreated for other reasons (e.g., changing ECS task definition family, upgrading to a new ALB, or migrating to a different VPC), they get the new name at that point. No dedicated migration effort needed.

## Naming Convention Going Forward

| Resource Type | Existing Resources | New Resources |
|--------------|-------------------|---------------|
| ECS cluster | `docteams-{env}` | `heykazi-{env}` (if recreated) |
| ECS services | `docteams-{env}-frontend`, `docteams-{env}-backend` | `heykazi-{env}-gateway`, `heykazi-{env}-portal`, `heykazi-{env}-keycloak` |
| ECR repos | `docteams-{env}-frontend`, `docteams-{env}-backend` | `kazi/frontend`, `kazi/backend`, `kazi/gateway`, `kazi/portal`, `kazi/keycloak` |
| S3 buckets | `docteams-{env}` | `heykazi-{env}-documents` |
| Log groups | `/ecs/docteams-{env}-*` | `/kazi/{env}/*` |
| State bucket | `docteams-terraform-state` | `heykazi-terraform-state` |
| Tags | `Project = docteams` | `Project = kazi` |
| Cloud Map | -- | `kazi.internal` |

**Note on ECR**: New ECR repositories use the `kazi/{service}` format (without environment prefix) because images are promoted across environments (ADR-217). Existing ECR repos (`docteams-{env}-frontend`, `docteams-{env}-backend`) are replaced by `kazi/frontend` and `kazi/backend` as part of the image promotion migration.

## Consequences

- **Positive:** No downtime or destructive changes. Customer-facing names are correct immediately. Gradual, risk-free migration. New resources start with the right name.
- **Negative:** Mixed naming in the AWS Console for 2-4 of the existing resources. Terraform variable `project` may need to be split into `project_legacy` and `project` for modules that manage both old and new resources.
- **Mitigations:** Document the naming convention in `infra/CLAUDE.md` so all contributors understand the mixed naming is intentional. Use resource tags (`Project = kazi`) consistently on all resources (old and new) for cost allocation and filtering. The AWS Console "tag filter" can show all `kazi`-tagged resources regardless of resource name.
