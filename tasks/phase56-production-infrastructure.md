# Phase 56 -- Production Infrastructure & Deployment Pipeline

Phase 56 updates and extends the existing (stale) AWS infrastructure and CI/CD pipeline to match the current 5-service architecture (frontend, backend, gateway, portal, Keycloak), enabling HeyKazi to serve 5--20 paying tenants reliably. The approach is **update-in-place** (ADR-213) -- the existing Terraform modules are well-structured; the gaps are scope (only 2 of 5 services) and staleness (Clerk references, no Keycloak/Gateway/Portal support). This phase touches Terraform (HCL), GitHub Actions (YAML), Dockerfiles, and Spring Boot config (YAML) -- no Java or TypeScript domain code.

**Architecture doc**: `architecture/phase56-production-infrastructure.md`

**ADRs**:
- [ADR-213](adr/ADR-213-update-in-place-infrastructure.md) -- Update-in-place vs. rewrite infrastructure
- [ADR-214](adr/ADR-214-gateway-bff-alb-routing.md) -- Gateway BFF ALB routing (split routing with path-based + host-based rules)
- [ADR-215](adr/ADR-215-keycloak-deployment-strategy.md) -- Keycloak deployment on ECS Fargate with shared RDS instance, separate database
- [ADR-216](adr/ADR-216-flyway-migration-production.md) -- Re-enable Flyway on app startup with extended health check grace period
- [ADR-217](adr/ADR-217-cicd-image-promotion.md) -- Single ECR with environment tags, build once, tag-promote
- [ADR-218](adr/ADR-218-naming-migration-heykazi.md) -- Rename customer-facing + new resources only, keep internal names

**Dependencies on prior phases**:
- Phase 13: Dockerfiles for all 4 application services (backend, gateway, frontend, portal)
- Phase 39: Keycloak realm configuration (`compose/keycloak/realm-export.json`, theme JARs)
- All phases: Current application architecture with 5 deployable services

**Audit reference**: `.infra-audit.md` -- detailed findings on what's stale, what works, what's missing

---

## Epic Overview

| Epic | Name | Scope | Deps | Effort | Slices | Status |
|------|------|-------|------|--------|--------|--------|
| 410 | Terraform Foundation: Naming, Secrets, State Bucket & Bootstrap | Infra | -- | M | 410A, 410B | **Done** (PR #854) |
| 411 | Data Layer: RDS PostgreSQL + ElastiCache Redis | Infra | 410 | M | 411A, 411B | **Done** (PR #855, #856) |
| 412 | Service Extension: ECR, Security Groups, IAM for 5 Services | Infra | 410 | L | 412A, 412B, 412C | **Done** (PRs #857, #858, #859) |
| 413 | ECS Services + ALB Routing Restructure | Infra | 411, 412 | L | 413A, 413B | **Done** (PRs #860, #861) |
| 414 | Keycloak Deployment: ECS Task, Database, Realm Import | Infra + Config | 411, 413 | M | 414A, 414B | **Done** (PRs #862, #863) |
| 415 | Dockerfile Hardening: Health Checks, JAR Fixes, Build Args | Docker | -- | S | 415A | **Done** (PR #864) |
| 416 | CI/CD Pipeline: OIDC, Image Promotion, Terraform Workflow | CI/CD | 412 | L | 416A, 416B, 416C | **Done** (PRs #865, #866, #867) |
| 417 | Observability: Alarms, SNS, Dashboards, Structured Logging | Infra + Config | 413 | M | 417A, 417B | **Done** (PRs #868, #869) |
| 418 | DNS, SSL & Production Cutover | Infra | 413, 416, 417 | M | 418A, 418B | |

---

## Dependency Graph

```
TERRAFORM FOUNDATION                    DOCKERFILES (parallel, independent)
────────────────────                    ──────────────────────────────────
[E410A Naming, secrets,                 [E415A Health checks, JAR fixes,
 state bucket rename,                    build args, portal public/ fix]
 bootstrap config]
        |
[E410B Flatten env structure,
 providers.tf, versions.tf,
 root module setup]
        |
        +──────────────────────────────────+
        |                                  |
DATA LAYER                          SERVICE EXTENSION (ECR, SG, IAM)
────────────                        ───────────────────────────────
        |                                  |
[E411A RDS PostgreSQL module        [E412A ECR: extend to 5 repos
 (app + keycloak databases)]         using for_each]
        |                                  |
[E411B ElastiCache Redis            [E412B Security groups: add
 module (gateway sessions)]          gateway, portal, keycloak,
        |                            RDS, Redis SGs]
        |                                  |
        |                           [E412C IAM: extend task roles
        |                            for 5 services + OIDC provider]
        |                                  |
        +──────────────────────────────────+
                        |
            ECS + ALB ROUTING
            ─────────────────
                        |
            [E413A ECS: 5 task definitions,
             5 services, Cloud Map namespace,
             autoscaling for all services]
                        |
            [E413B ALB: restructure routing
             for Gateway BFF pattern with
             host-based + path-based rules,
             5 target groups]
                        |
        +───────────────+──────────────────+
        |               |                  |
KEYCLOAK           CI/CD PIPELINE     OBSERVABILITY
─────────          ──────────────     ─────────────
        |               |                  |
[E414A Keycloak    [E416A OIDC +      [E417A CloudWatch alarms
 ECS task def,      ECR/ECS deploy     + SNS topics + log
 Dockerfile,        workflow update    group extension]
 realm config]      for 5 services]        |
        |               |             [E417B Structured logging
[E414B Gateway     [E416B Image        config + dashboard]
 production         promotion +
 config: Redis      Terraform
 session store,     plan/apply
 cookie.secure]     workflow]
                        |
                   [E416C Rollback
                    workflow update
                    for 5 services]
                        |
        +───────────────+──────────────────+
                        |
              DNS, SSL & CUTOVER
              ──────────────────
                        |
              [E418A ACM wildcard cert,
               Route 53 records for
               3 subdomains + staging]
                        |
              [E418B Runbook + production
               cutover checklist +
               smoke test script]
```

**Parallel opportunities**:
- E415 (Dockerfiles) can run in parallel with all Terraform epics -- Dockerfiles are independent of infrastructure.
- E416 (CI/CD) can start after E412 (needs ECR repo names and IAM OIDC outputs), but can run in parallel with E413/E414.
- E417 (Observability) can run in parallel with E416.
- E411A (RDS) and E412A (ECR) can run in parallel after E410.
- E411B (Redis) is sequential after E411A (same module directory).
- E412A, E412B, E412C are sequential (security groups reference ECR outputs, IAM references security group outputs).

---

## Implementation Order

### Stage 0: Terraform Foundation (sequential)

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 0a | 410 | 410A | Rename state bucket from `docteams-terraform-state` to `heykazi-terraform-state`. Create `infra/bootstrap/` directory with standalone Terraform config (local state) for S3 bucket + DynamoDB lock table. Replace 3 stale Clerk secrets with 10 Keycloak-era secrets in secrets module. Update resource tags to `Project = kazi`. Infra only. | **Done** (PR #854) |
| 0b | 410 | 410B | Flatten environment structure: collapse `environments/dev/`, `environments/staging/`, `environments/prod/` into root-level `main.tf`, `variables.tf`, `outputs.tf`, `providers.tf`, `versions.tf` with `environments/staging.tfvars` and `environments/production.tfvars`. Pin provider versions. Remove `dev` environment (only staging + production per requirements). Infra only. | **Done** (PR #854) |

### Stage 1: Data Layer + Service Extension -- ECR (parallel tracks)

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 1a (parallel) | 411 | 411A | Create new `infra/modules/data/` module with RDS PostgreSQL 16 (Multi-AZ variable, `db.t4g.medium`/`db.t4g.micro`, 20GB gp3, auto-scaling to 100GB, automated backups, parameter group, two databases: `kazi` + `kazi_keycloak`). Outputs: RDS endpoint, RDS port, security group ID. Infra only. | **Done** (PR #855) |
| 1b (parallel) | 412 | 412A | Extend ECR module: refactor from 2 hardcoded repos to `for_each` over service list `["backend", "gateway", "frontend", "portal", "keycloak"]`. Use `kazi/{service}` naming per ADR-218. Add image scanning on push, lifecycle policy (keep 10 tagged, expire untagged after 7 days). Infra only. | **Done** (PR #857) |

### Stage 2: Data Layer -- Redis + Service Extension -- SGs, IAM (parallel tracks)

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 2a (parallel) | 411 | 411B | Add ElastiCache Redis to `infra/modules/data/` module. Redis 7, `cache.t4g.micro`, single node, private subnet group, auth token in Secrets Manager. Outputs: Redis endpoint, Redis port, Redis auth token ARN. Infra only. | **Done** (PR #856) |
| 2b (parallel) | 412 | 412B | Extend security-groups module: add 5 new security groups (gateway: port 8443, portal: port 3002, keycloak: port 8080, RDS: port 5432 from `ecs-sg` only, Redis: port 6379 from `ecs-sg` only). Refactor existing SGs to use `for_each` where possible. Infra only. | **Done** (PR #858) |

### Stage 3: Service Extension -- IAM (sequential after SGs)

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 3a | 412 | 412C | Extend IAM module: add task roles for gateway (no extra AWS perms), portal (no extra AWS perms), keycloak (no extra AWS perms). Add GitHub OIDC provider (`aws_iam_openid_connect_provider` for `token.actions.githubusercontent.com`). Create `heykazi-github-actions` IAM role with trust policy scoped to repo. Role permissions: ECR push, ECS update, S3 state access, Secrets Manager read, CloudWatch Logs write. Infra only. | **Done** (PR #859) |

### Stage 4: ECS + ALB (sequential, depends on data + service extension)

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 4a | 413 | 413A | Extend ECS module: add 3 new task definitions (gateway, portal, keycloak) following existing frontend/backend pattern. Add 3 new ECS services. Replace Clerk env vars with Keycloak env vars in frontend/backend task definitions. Add Cloud Map namespace (`kazi.internal`) for service discovery (`backend.kazi.internal`). Extend autoscaling module for gateway, portal. Set `health_check_grace_period_seconds = 180` on backend service (ADR-216). Infra only. | **Done** (PR #860) |
| 4b | 413 | 413B | Restructure ALB module per ADR-214: add 3 new target groups (gateway on 8443, portal on 3002, keycloak on 8080). Replace existing listener rules with priority-ordered rules (auth.heykazi.com -> keycloak-tg at priority 10, portal.heykazi.com -> portal-tg at 20, app.heykazi.com/bff/* -> gateway-tg at 30, app.heykazi.com/api/* -> gateway-tg at 40, app.heykazi.com -> frontend-tg at 50, default -> 404). Add `enable_deletion_protection = true` for production. Infra only. | **Done** (PR #861) |

### Stage 5: Keycloak Deployment + Dockerfile Hardening (parallel tracks)

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 5a (parallel) | 414 | 414A | Create Keycloak production Dockerfile at `compose/keycloak/Dockerfile.production`. Based on `quay.io/keycloak/keycloak:26.5`. Copy custom theme JAR + realm import JSON. Set `KC_DB=postgres`, `KC_HEALTH_ENABLED=true`. Add `HEALTHCHECK` instruction. Create Keycloak-specific ECS configuration in task definition (env vars: `KC_DB_URL`, `KC_HOSTNAME`, `KC_PROXY_HEADERS=xforwarded`, `KC_HTTP_ENABLED=true`). Docker + Infra. | **Done** (PR #862) |
| 5b (parallel) | 415 | 415A | Add `HEALTHCHECK` to all 4 Dockerfiles (backend: `curl -f http://localhost:8080/actuator/health`, gateway: `curl -f http://localhost:8443/actuator/health`, frontend: `curl -f http://localhost:3000/`, portal: `curl -f http://localhost:3002/`). Fix hardcoded JAR names in backend + gateway Dockerfiles (use wildcard or build arg). Fix portal Dockerfile missing `public/` copy. Update frontend Dockerfile: remove Clerk build args, add `NEXT_PUBLIC_AUTH_MODE` and `NEXT_PUBLIC_GATEWAY_URL`. Docker only. | **Done** (PR #864) |
| 5c (parallel) | 414 | 414B | Create `gateway/src/main/resources/application-production.yml` for gateway: switch session store from JDBC to Redis (`spring.session.store-type=redis`), set `cookie.secure=true`, configure Redis connection via env vars. Create `backend/src/main/resources/application-production.yml` update: re-enable Flyway (`spring.flyway.enabled: true` per ADR-216), add structured JSON logging pattern, set `SPRING_PROFILES_ACTIVE=production,keycloak`. Config only. | **Done** (PR #863) |

### Stage 6: CI/CD Pipeline (sequential slices, parallel with Stage 5)

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 6a | 416 | 416A | Update `.github/workflows/ci.yml`: add gateway test job, portal test job, terraform-validate job. Replace Clerk build args with Keycloak build args in frontend test job. Switch all jobs from IAM keys to OIDC (`aws-actions/configure-aws-credentials` with `role-to-assume`). Add change detection for `gateway/` and `portal/` paths. CI/CD only. | **Done** (PR #865) |
| 6b | 416 | 416B | Create `.github/workflows/terraform.yml`: plan on PR (comment on PR), apply to staging on merge to main (only when `infra/` changes), apply to production on `workflow_dispatch` with environment protection. Create `.github/workflows/deploy-staging.yml` rewrite: build 5 services (with change detection), tag with Git SHA, deploy to staging ECS, smoke test. Implement image promotion per ADR-217 (build once, tag-promote for backend/gateway/portal/keycloak; environment-specific build for frontend). CI/CD only. | **Done** (PR #866) |
| 6c | 416 | 416C | Update `.github/workflows/deploy-prod.yml`: promote images from staging (re-tag, no rebuild) except frontend (env-specific build). Add environment protection rules. Update `.github/workflows/rollback.yml`: extend to support all 5 services. Update `.github/actions/ecs-deploy/action.yml` if needed. CI/CD only. | **Done** (PR #867) |

### Stage 7: Observability (parallel with Stage 6)

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 7a (parallel) | 417 | 417A | Extend monitoring module: add 3 new CloudWatch log groups (gateway, portal, keycloak) with `/kazi/{env}/{service}` naming. Add SNS topic `heykazi-{env}-alerts` with email subscription variable. Add 8 CloudWatch alarms (backend/gateway/keycloak unhealthy, high 5xx rate, RDS CPU high, RDS storage low, RDS connections high, ECS backend CPU). Infra only. | **Done** (PR #868) |
| 7b (parallel) | 417 | 417B | Create `backend/src/main/resources/application-production.yml` structured logging section: JSON pattern for CloudWatch Logs Insights. Configure actuator endpoints for production (health, info, metrics). Add health indicator configuration for DB and Redis. Config only. | **Done** (PR #869) |

### Stage 8: DNS, SSL & Production Cutover (final)

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 8a | 418 | 418A | Extend DNS module: wildcard ACM certificate for `*.heykazi.com` with DNS validation. Route 53 A-record aliases for `app.heykazi.com`, `portal.heykazi.com`, `auth.heykazi.com` pointing to ALB. Staging variants (`staging-app.heykazi.com`, etc.). ALB HTTPS listener with ACM cert, HTTP-to-HTTPS redirect, TLS 1.3 security policy. Infra only. | **Done** (PR #870) |
| 8b | 418 | 418B | Create `infra/RUNBOOK.md` covering: first-time setup, deploying a new version, provisioning a new tenant, database operations, viewing logs, responding to alerts, rollback procedure, Keycloak operations, cost monitoring, disaster recovery. Create production cutover checklist script (`infra/scripts/smoke-test.sh`) that verifies all 5 services are healthy. Docs only. | |

### Timeline

```
Stage 0:  [410A] -> [410B]                                              <- foundation (sequential)
Stage 1:  [411A]  //  [412A]                                            <- data + ECR (parallel)
Stage 2:  [411B]  //  [412B]                                            <- Redis + SGs (parallel)
Stage 3:  [412C]                                                        <- IAM + OIDC (sequential)
Stage 4:  [413A] -> [413B]                                              <- ECS + ALB (sequential)
Stage 5:  [414A]  //  [415A]  //  [414B]                                <- Keycloak + Docker + config (parallel)
Stage 6:  [416A] -> [416B] -> [416C]                                    <- CI/CD (sequential)
Stage 7:  [417A]  //  [417B]                                            <- observability (parallel)
Stage 8:  [418A] -> [418B]                                              <- DNS + runbook (sequential)

Note: Stage 5 can run in parallel with Stages 1-4 (Dockerfiles are independent).
      Stage 6 can start after Stage 3 (needs OIDC IAM role).
      Stage 7 can run in parallel with Stage 6.
      Stage 8 is the final stage — requires Stages 4, 6, and 7.
```

---

## Epic 410: Terraform Foundation -- Naming, Secrets, State Bucket & Bootstrap

**Goal**: Establish the Terraform foundation for Phase 56. Create the bootstrap configuration for the new `heykazi-terraform-state` S3 bucket and DynamoDB lock table. Replace stale Clerk secrets with Keycloak-era secrets. Flatten the environment directory structure from 3 per-env directories to root-level modules with `.tfvars` files. Pin provider versions. Update resource tags to `Project = kazi`.

**References**: Architecture doc Sections 1.1 (repo structure), 1.2 (state backend); ADR-213 (update-in-place), ADR-218 (naming migration).

**Dependencies**: None (first epic).

**Scope**: Infra

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **410A** | 410.1--410.5 | Create `infra/bootstrap/` directory with standalone Terraform config (local state) for S3 state bucket (`heykazi-terraform-state`) + DynamoDB lock table (`heykazi-terraform-locks`). Replace 3 stale Clerk secrets with 10 Keycloak/portal/SMTP secrets in secrets module. Update `project` variable default to `kazi`. Update resource tags to `Project = kazi`. Infra only. | **Done** (PR #854) |
| **410B** | 410.6--410.10 | Flatten environment structure: create root-level `infra/main.tf`, `infra/variables.tf`, `infra/outputs.tf`, `infra/providers.tf`, `infra/versions.tf` that compose all modules. Create `infra/environments/staging.tfvars` and `infra/environments/production.tfvars`. Remove `dev` environment. Pin Terraform >= 1.9 and AWS provider >= 5.0. Infra only. | **Done** (PR #854) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 410.1 | Create bootstrap Terraform config for state bucket | 410A | -- | New files: `infra/bootstrap/main.tf`, `infra/bootstrap/variables.tf`, `infra/bootstrap/outputs.tf`. Resources: `aws_s3_bucket` (`heykazi-terraform-state`, versioning enabled, SSE-S3), `aws_dynamodb_table` (`heykazi-terraform-locks`, partition key `LockID`). Uses local state (no backend block). Include a `README.md` with usage instructions. Verify: `cd infra/bootstrap && terraform init && terraform validate`. Pattern: architecture doc Section 1.2. |
| 410.2 | Replace Clerk secrets with Keycloak secrets in secrets module | 410A | -- | Modify: `infra/modules/secrets/main.tf`, `infra/modules/secrets/variables.tf`, `infra/modules/secrets/outputs.tf`. Remove 3 Clerk secrets (`clerk-secret-key`, `clerk-webhook-secret`, `clerk-publishable-key`). Add 10 new secrets: `keycloak-client-id`, `keycloak-client-secret`, `keycloak-admin-username`, `keycloak-admin-password`, `portal-jwt-secret`, `portal-magic-link-secret`, `integration-encryption-key`, `smtp-username`, `smtp-password`, `email-unsubscribe-secret`. Keep `database-url`, `database-migration-url`, `internal-api-key`. All with `CHANGE_ME` placeholder + `lifecycle { ignore_changes = [secret_string] }`. Pattern: existing secrets in same file. |
| 410.3 | Add Redis auth token secret | 410A | 410.2 | Add `redis-auth-token` to secrets module. This will be consumed by the ElastiCache module (E411B) and the gateway task definition (E413A). |
| 410.4 | Update project variable and tags | 410A | -- | Modify: `infra/modules/*/variables.tf` -- update `project` variable default from `docteams` to `kazi`. Search for `Project = var.project` in all module `main.tf` files and ensure tag maps use the new default. All new resources will be tagged `Project = kazi`. Existing resources retain `docteams` names but get `kazi` tags per ADR-218. Verify: `grep -r 'docteams' infra/modules/` should only appear in resource name interpolations for existing resources, not in tags. |
| 410.5 | Write bootstrap README | 410A | 410.1 | New file: `infra/bootstrap/README.md`. Document: (1) prerequisites (AWS CLI configured, correct region), (2) first-time setup steps (`terraform init`, `terraform apply`), (3) state migration from `docteams-terraform-state` to `heykazi-terraform-state` (manual S3 copy + update backend.tf), (4) verification steps. |
| 410.6 | Create root-level Terraform configuration | 410B | 410A | New files: `infra/main.tf` (composes all child modules: networking, data, ecr, ecs, dns, secrets, monitoring), `infra/variables.tf` (all input variables with descriptions and type constraints), `infra/outputs.tf` (ALB DNS name, RDS endpoint, ECR repo URLs, etc.). Pattern: existing `infra/environments/prod/main.tf` but extended for all modules. |
| 410.7 | Create providers.tf and versions.tf | 410B | -- | New files: `infra/providers.tf` (AWS provider with region variable, S3 backend config referencing `heykazi-terraform-state`), `infra/versions.tf` (pin `terraform >= 1.9.0`, `hashicorp/aws >= 5.0`). Remove hardcoded `us-east-1` -- make region a variable (default `af-south-1` for Cape Town, fallback `eu-west-1`). |
| 410.8 | Create environment tfvars files | 410B | 410.6 | New files: `infra/environments/staging.tfvars`, `infra/environments/production.tfvars`. Staging: smaller instance sizes (`db.t4g.micro`, single NAT, Fargate Spot for non-critical), `staging-` domain prefix. Production: full sizes (`db.t4g.medium`, Multi-AZ, dual NAT). Pattern: existing `terraform.tfvars.example` files in environment dirs. |
| 410.9 | Remove dev environment and old per-env structure | 410B | 410.6 | Remove `infra/environments/dev/` directory entirely (only staging + production per requirements). The `infra/environments/staging/` and `infra/environments/prod/` directories can be removed once root-level config is verified. Keep the new `infra/environments/staging.tfvars` and `infra/environments/production.tfvars`. |
| 410.10 | Update infra CLAUDE.md | 410B | 410.6 | Modify: `infra/CLAUDE.md`. Update structure diagram, commands (now `terraform plan -var-file=environments/staging.tfvars`), architecture overview (5 services, RDS instead of Neon, Gateway BFF routing, Redis sessions), naming convention notes (ADR-218), secrets table (Keycloak-era). |

### Key Files

**Slice 410A -- Create:**
- `infra/bootstrap/main.tf` -- S3 state bucket + DynamoDB lock table
- `infra/bootstrap/variables.tf` -- region, bucket name variables
- `infra/bootstrap/outputs.tf` -- bucket ARN, table name
- `infra/bootstrap/README.md` -- setup instructions

**Slice 410A -- Modify:**
- `infra/modules/secrets/main.tf` -- Replace Clerk secrets with Keycloak secrets
- `infra/modules/secrets/variables.tf` -- New secret name variables
- `infra/modules/secrets/outputs.tf` -- New secret ARN outputs

**Slice 410B -- Create:**
- `infra/main.tf` -- Root module composing all child modules
- `infra/variables.tf` -- All input variables
- `infra/outputs.tf` -- All output values
- `infra/providers.tf` -- AWS provider + S3 backend
- `infra/versions.tf` -- Terraform + provider version pins
- `infra/environments/staging.tfvars` -- Staging variable values
- `infra/environments/production.tfvars` -- Production variable values

**Slice 410B -- Modify:**
- `infra/CLAUDE.md` -- Updated structure and conventions

**Slice 410A/410B -- Read for context:**
- `infra/environments/prod/main.tf` -- Existing module composition pattern
- `infra/environments/prod/backend.tf` -- Existing S3 backend config
- `infra/modules/secrets/main.tf` -- Existing secret patterns

### Architecture Decisions

- **State bucket rename**: The new bootstrap creates `heykazi-terraform-state`. Existing state in `docteams-terraform-state` must be manually copied (S3 sync) before switching backend config. This is a one-time manual operation documented in the bootstrap README.
- **Flatten to root-level config**: The 3-environment directory structure (dev/staging/prod) with duplicated `main.tf` files is replaced by a single root-level config with `.tfvars` files. This eliminates module composition drift between environments and simplifies `terraform plan` commands.
- **Remove dev environment**: Per requirements, only `staging` and `production` are provisioned. Local development uses Docker Compose, not a cloud dev environment.

---

## Epic 411: Data Layer -- RDS PostgreSQL + ElastiCache Redis

**Goal**: Create the new `data` Terraform module provisioning RDS PostgreSQL 16 and ElastiCache Redis 7. RDS hosts both the application database (`kazi`) and Keycloak's database (`kazi_keycloak`) in the same instance (ADR-215). ElastiCache provides Redis for gateway session storage.

**References**: Architecture doc Sections 2.1 (RDS), 2.2 (ElastiCache); ADR-215 (Keycloak deployment strategy -- shared RDS).

**Dependencies**: Epic 410 (foundation -- needs secrets module for DB credentials, provider config).

**Scope**: Infra

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **411A** | 411.1--411.5 | Create `infra/modules/data/` module with RDS PostgreSQL 16. Resources: `aws_db_subnet_group`, `aws_db_parameter_group`, `aws_db_instance`, `aws_secretsmanager_secret` (auto-generated master credentials). Configurable: instance class, Multi-AZ, storage, backup retention, deletion protection. Outputs: endpoint, port, credentials secret ARN. Infra only. | **Done** (PR #855) |
| **411B** | 411.6--411.9 | Add ElastiCache Redis to `infra/modules/data/` module. Resources: `aws_elasticache_subnet_group`, `aws_elasticache_replication_group` (single-node, Redis 7). Outputs: primary endpoint, port, auth token ARN. Infra only. | **Done** (PR #856) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 411.1 | Create data module structure | 411A | -- | New files: `infra/modules/data/main.tf`, `infra/modules/data/variables.tf`, `infra/modules/data/outputs.tf`. Variables: `environment`, `project`, `vpc_id`, `private_subnet_ids`, `rds_sg_id`, `redis_sg_id`, `rds_instance_class` (default `db.t4g.micro`), `rds_multi_az` (default `false`), `rds_storage_gb` (default `20`), `rds_max_storage_gb` (default `100`), `rds_backup_retention` (default `1`), `rds_deletion_protection` (default `false`). Pattern: existing `infra/modules/s3/` for module structure. |
| 411.2 | Add RDS PostgreSQL instance | 411A | 411.1 | In `infra/modules/data/main.tf`. Resources: `aws_db_subnet_group` (private subnets), `aws_db_parameter_group` (family `postgres16`, params: `log_min_duration_statement = 1000`, `shared_preload_libraries = pg_stat_statements`), `aws_db_instance` (engine `postgres`, engine_version `16`, identifier `heykazi-{env}-postgres`, db_name `kazi`, storage type `gp3`, auto-scaling, `manage_master_user_password = true`). Tags: `Project = kazi`, `Environment`, `ManagedBy = terraform`. |
| 411.3 | Add RDS master credentials output | 411A | 411.2 | In `outputs.tf`: `rds_endpoint`, `rds_port`, `rds_master_credentials_secret_arn` (from `aws_db_instance.master_user_secret[0].secret_arn`), `rds_database_name`. These outputs feed into ECS task definitions for backend and Keycloak. |
| 411.4 | Add Keycloak database provisioning note | 411A | 411.2 | RDS creates the `kazi` database automatically via `db_name`. The `kazi_keycloak` database must be created as a post-provisioning step (either manually or via a provisioner). Add a `null_resource` with `local-exec` provisioner that runs `createdb kazi_keycloak` via `psql`, or document this as a manual step in the runbook. The Keycloak ECS task definition connects to `jdbc:postgresql://{endpoint}:5432/kazi_keycloak`. |
| 411.5 | Add staging/production tfvars for RDS | 411A | 411.1 | Update `infra/environments/staging.tfvars`: `rds_instance_class = "db.t4g.micro"`, `rds_multi_az = false`, `rds_backup_retention = 1`, `rds_deletion_protection = false`. Update `infra/environments/production.tfvars`: `rds_instance_class = "db.t4g.medium"`, `rds_multi_az = true`, `rds_backup_retention = 7`, `rds_deletion_protection = true`. Verify: `terraform validate`. |
| 411.6 | Add ElastiCache Redis | 411B | 411.1 | In `infra/modules/data/main.tf`. Resources: `aws_elasticache_subnet_group` (private subnets), `aws_elasticache_replication_group` (description `heykazi-{env}-redis`, engine `redis`, engine_version `7.1`, node_type `cache.t4g.micro`, num_cache_clusters `1`, automatic_failover_enabled `false`, transit_encryption_enabled `true`, auth_token from Secrets Manager). |
| 411.7 | Add Redis outputs | 411B | 411.6 | In `outputs.tf`: `redis_endpoint` (primary endpoint address), `redis_port`, `redis_auth_token_secret_arn`. These feed into gateway ECS task definition. |
| 411.8 | Add Redis variables | 411B | 411.6 | In `variables.tf`: `redis_node_type` (default `cache.t4g.micro`), `redis_engine_version` (default `7.1`), `create_redis` (default `true`). In tfvars: both environments use `cache.t4g.micro`. |
| 411.9 | Validate data module | 411B | 411.6 | Verify: `cd infra && terraform init -backend=false && terraform validate`. Check `terraform plan -var-file=environments/staging.tfvars` produces expected resources (1 RDS instance, 1 Redis replication group, 2 subnet groups, 1 parameter group). |

### Key Files

**Slice 411A -- Create:**
- `infra/modules/data/main.tf` -- RDS resources
- `infra/modules/data/variables.tf` -- Input variables
- `infra/modules/data/outputs.tf` -- RDS outputs

**Slice 411A -- Modify:**
- `infra/environments/staging.tfvars` -- RDS staging values
- `infra/environments/production.tfvars` -- RDS production values

**Slice 411B -- Modify:**
- `infra/modules/data/main.tf` -- Add ElastiCache resources
- `infra/modules/data/variables.tf` -- Add Redis variables
- `infra/modules/data/outputs.tf` -- Add Redis outputs

**Read for context:**
- `infra/modules/s3/main.tf` -- Module structure pattern
- `infra/modules/vpc/main.tf` -- Subnet ID output pattern

### Architecture Decisions

- **Single `data` module for RDS + Redis**: Rather than separate `rds` and `elasticache` modules, both are grouped in a `data` module. This simplifies the root module composition and reflects that both resources share the same networking inputs (private subnets, security groups).
- **Managed master credentials**: Using `manage_master_user_password = true` lets RDS auto-generate and store the master password in Secrets Manager with automatic rotation. No manual password management needed.
- **Keycloak database in same RDS**: Per ADR-215, `kazi_keycloak` is a separate database (not schema) in the same RDS instance. This provides logical isolation while sharing the instance cost.

---

## Epic 412: Service Extension -- ECR, Security Groups, IAM for 5 Services

**Goal**: Extend the existing ECR, security-groups, and IAM modules to support all 5 services (frontend, backend, gateway, portal, keycloak). Add the GitHub OIDC identity provider for CI/CD authentication.

**References**: Architecture doc Sections 1.4 (security groups), 3.1 (ECR repos); ADR-217 (image promotion -- single ECR per service), ADR-218 (naming -- `kazi/{service}` format).

**Dependencies**: Epic 410 (foundation -- provider config, variable updates).

**Scope**: Infra

**Estimated Effort**: L

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **412A** | 412.1--412.3 | Refactor ECR module from 2 hardcoded repos to `for_each` over 5-service list. Use `kazi/{service}` naming per ADR-218. Image scanning, lifecycle policies. Infra only. | **Done** (PR #857) |
| **412B** | 412.4--412.7 | Extend security-groups module: add gateway (8443), portal (3002), keycloak (8080), RDS (5432), Redis (6379) security groups. Update internal ALB SG to allow gateway ingress. Infra only. | **Done** (PR #858) |
| **412C** | 412.8--412.12 | Extend IAM module: add task roles for gateway, portal, keycloak. Add GitHub OIDC provider + `heykazi-github-actions` role. Extend ECS execution role to read new secrets. Infra only. | **Done** (PR #859) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 412.1 | Refactor ECR to use for_each | 412A | -- | Modify: `infra/modules/ecr/main.tf`. Replace 2 hardcoded `aws_ecr_repository` resources with a single resource using `for_each = toset(var.services)`. Default `services = ["backend", "gateway", "frontend", "portal", "keycloak"]`. Repo naming: `kazi/${each.key}` (per ADR-218, no environment prefix since images are promoted). Pattern: existing ECR module but refactored. |
| 412.2 | Update ECR lifecycle and scanning | 412A | 412.1 | In same file: `aws_ecr_lifecycle_policy` using `for_each` matching repos. Keep last 10 tagged images, expire untagged after 7 days. `aws_ecr_repository` with `image_scanning_configuration { scan_on_push = true }`. Image tag mutability: `MUTABLE` (needed for tag promotion per ADR-217). |
| 412.3 | Update ECR outputs | 412A | 412.1 | Modify: `infra/modules/ecr/outputs.tf`. Output a map of repo URLs: `ecr_repository_urls = { for k, v in aws_ecr_repository.repos : k => v.repository_url }`. Remove old hardcoded `frontend_repository_url` / `backend_repository_url` outputs. Update consumers (ECS module, CI/CD) to use the new map. |
| 412.4 | Add gateway security group | 412B | -- | Modify: `infra/modules/security-groups/main.tf`. Add `aws_security_group` for gateway (name: `heykazi-{env}-sg-gateway`). Ingress: port 8443 from `sg-public-alb`. Egress: all traffic. Uses `aws_vpc_security_group_ingress_rule` / `egress_rule` pattern matching existing SGs. |
| 412.5 | Add portal, keycloak security groups | 412B | 412.4 | Same file. Portal SG: ingress port 3002 from `sg-public-alb`. Keycloak SG: ingress port 8080 from `sg-public-alb`. Both follow same pattern as gateway SG. |
| 412.6 | Add RDS and Redis security groups | 412B | 412.4 | Same file. RDS SG: ingress port 5432 from `sg-ecs` (a general ECS SG that all ECS tasks share). Redis SG: ingress port 6379 from `sg-ecs`. No egress rules (response traffic is stateful). These restrict database/cache access to ECS tasks only. |
| 412.7 | Update security group outputs | 412B | 412.4 | Modify: `infra/modules/security-groups/outputs.tf`. Add outputs: `gateway_sg_id`, `portal_sg_id`, `keycloak_sg_id`, `rds_sg_id`, `redis_sg_id`. These feed into ECS task definitions and data module. |
| 412.8 | Add gateway and portal task roles | 412C | -- | Modify: `infra/modules/iam/main.tf`. Add `aws_iam_role` for gateway task (no additional AWS permissions -- gateway only needs network access to Keycloak and Redis, handled by SGs). Add portal task role (no additional AWS permissions). Follow existing `backend_task_role` pattern with empty inline policy. |
| 412.9 | Add keycloak task role | 412C | 412.8 | Same file. Keycloak task role: no additional AWS permissions (Keycloak only needs database access via SGs and network access to send emails via SMTP relay). |
| 412.10 | Add GitHub OIDC provider | 412C | -- | Modify: `infra/modules/iam/main.tf`. Add `aws_iam_openid_connect_provider` for `token.actions.githubusercontent.com`. Thumbprint list from GitHub's OIDC cert. Add `aws_iam_role` named `heykazi-github-actions` with trust policy: `Principal: { Federated: oidc_provider_arn }`, `Condition: { StringLike: { "token.actions.githubusercontent.com:sub": "repo:OWNER/REPO:*" } }`. Variable for repo identifier. |
| 412.11 | Add GitHub Actions role permissions | 412C | 412.10 | Attach inline policy to `heykazi-github-actions` role. Permissions: `ecr:GetAuthorizationToken`, `ecr:BatchCheckLayerAvailability`, `ecr:PutImage`, `ecr:InitiateLayerUpload`, `ecr:UploadLayerPart`, `ecr:CompleteLayerUpload`, `ecr:BatchGetImage`, `ecr:GetDownloadUrlForLayer`, `ecs:UpdateService`, `ecs:DescribeServices`, `ecs:DescribeTaskDefinition`, `ecs:RegisterTaskDefinition`, `ecs:DeregisterTaskDefinition`, `s3:GetObject`/`PutObject` on state bucket, `iam:PassRole` on execution + task roles. Scope all ARNs to specific resources (no wildcards per convention). |
| 412.12 | Update IAM outputs | 412C | 412.8 | Modify: `infra/modules/iam/outputs.tf`. Add outputs: `gateway_task_role_arn`, `portal_task_role_arn`, `keycloak_task_role_arn`, `github_actions_role_arn`. These feed into ECS task definitions and CI/CD workflows. |

### Key Files

**Slice 412A -- Modify:**
- `infra/modules/ecr/main.tf` -- Refactor to `for_each`
- `infra/modules/ecr/variables.tf` -- Add `services` variable
- `infra/modules/ecr/outputs.tf` -- Map output

**Slice 412B -- Modify:**
- `infra/modules/security-groups/main.tf` -- Add 5 new SGs
- `infra/modules/security-groups/variables.tf` -- No changes expected
- `infra/modules/security-groups/outputs.tf` -- Add 5 new outputs

**Slice 412C -- Modify:**
- `infra/modules/iam/main.tf` -- Add 3 task roles + OIDC provider + GitHub role
- `infra/modules/iam/variables.tf` -- Add `github_repo` variable
- `infra/modules/iam/outputs.tf` -- Add 4 new outputs

**Read for context:**
- `infra/modules/ecr/main.tf` -- Existing 2-repo pattern
- `infra/modules/security-groups/main.tf` -- Existing SG rule pattern
- `infra/modules/iam/main.tf` -- Existing task role pattern

### Architecture Decisions

- **ECR `for_each` refactor**: Converting from 2 hardcoded repos to a `for_each` over a service list reduces duplication and makes adding/removing services a single variable change. Note: this will cause Terraform to destroy the old `docteams-{env}-frontend` and `docteams-{env}-backend` repos and create `kazi/frontend` and `kazi/backend`. Images must be migrated or re-pushed. Plan this carefully -- perhaps `terraform state mv` the existing repos first.
- **GitHub OIDC over IAM keys**: Per architecture doc Section 5.5 and audit finding #6. Short-lived credentials with no rotation burden. The `id-token: write` permission is already declared in existing workflows.
- **Shared ECS security group**: All 5 ECS services share a single SG for database/Redis access rules. Individual service SGs control ALB ingress per port.

---

## Epic 413: ECS Services + ALB Routing Restructure

**Goal**: Extend the ECS module with task definitions and services for all 5 services. Restructure ALB routing to implement the Gateway BFF pattern (ADR-214). Add Cloud Map service discovery for internal communication. Extend autoscaling for new services.

**References**: Architecture doc Sections 4.1--4.5 (ECS, services, task definitions, service discovery, ALB routing); ADR-214 (split routing), ADR-216 (health check grace period).

**Dependencies**: Epic 411 (RDS/Redis endpoints for env vars), Epic 412 (ECR repos, SGs, IAM roles).

**Scope**: Infra

**Estimated Effort**: L

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **413A** | 413.1--413.8 | Extend ECS module: add 3 new task definitions (gateway, portal, keycloak) with correct env vars and secrets. Replace Clerk env vars in frontend/backend task defs. Add 3 new ECS services. Add Cloud Map namespace `kazi.internal` with `backend.kazi.internal` service discovery. Extend autoscaling for gateway + portal. Set backend health check grace period to 180s. Infra only. | **Done** (PR #860) |
| **413B** | 413.9--413.14 | Restructure ALB module: add 3 new target groups (gateway-tg on 8443, portal-tg on 3002, keycloak-tg on 8080). Replace existing listener rules with priority-ordered host-based + path-based rules per ADR-214 (keycloak at priority 10, portal at 20, /bff/* at 30, /api/* at 40, frontend at 50, default 404 at 99). Enable deletion protection for production. Infra only. | **Done** (PR #861) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 413.1 | Add gateway task definition | 413A | -- | Modify: `infra/modules/ecs/main.tf`. New `aws_ecs_task_definition` for gateway. Container: image from ECR `kazi/gateway`, port 8443, CPU 512, memory 1024. Environment: `SPRING_PROFILES_ACTIVE=production`, `SPRING_DATA_REDIS_HOST=${redis_endpoint}`, `BACKEND_URL=http://backend.kazi.internal:8080`. Secrets from Secrets Manager: `SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_KEYCLOAK_CLIENT_SECRET`. Log config: `awslogs` driver to `/kazi/{env}/gateway`. Health check: `curl -f http://localhost:8443/actuator/health`. Pattern: existing backend task definition. |
| 413.2 | Add portal task definition | 413A | -- | Same file. Portal container: image from ECR `kazi/portal`, port 3002, CPU 512, memory 1024. Environment: `NEXT_PUBLIC_PORTAL_API_URL=https://{domain}/api`. Minimal env vars (portal uses magic link JWT, not Keycloak sessions). Health check: `curl -f http://localhost:3002/`. |
| 413.3 | Add keycloak task definition | 413A | -- | Same file. Keycloak container: image from ECR `kazi/keycloak`, port 8080, CPU 1024, memory 2048. Environment: `KC_DB=postgres`, `KC_DB_URL=jdbc:postgresql://${rds_endpoint}:5432/kazi_keycloak`, `KC_HOSTNAME=auth.heykazi.com`, `KC_PROXY_HEADERS=xforwarded`, `KC_HTTP_ENABLED=true`, `KC_HEALTH_ENABLED=true`, `KC_HOSTNAME_STRICT=false`, `KC_DB_POOL_MAX_SIZE=5`. Secrets: `KC_DB_USERNAME`, `KC_DB_PASSWORD` from RDS master credentials. Health check: `curl -f http://localhost:8080/health/ready`. |
| 413.4 | Update frontend task definition | 413A | -- | Modify existing frontend task def in `infra/modules/ecs/main.tf`. Remove: `CLERK_SECRET_KEY`, `CLERK_WEBHOOK_SIGNING_SECRET`, `NEXT_PUBLIC_CLERK_PUBLISHABLE_KEY`. Add: `NEXT_PUBLIC_AUTH_MODE=keycloak`, `NEXT_PUBLIC_GATEWAY_URL=https://app.heykazi.com`, `GATEWAY_URL=https://app.heykazi.com`, `BACKEND_URL=http://backend.kazi.internal:8080`, `INTERNAL_API_KEY` from secrets. |
| 413.5 | Update backend task definition | 413A | -- | Modify existing backend task def. Remove: `CLERK_ISSUER`, `CLERK_JWKS_URI`. Add: `SPRING_PROFILES_ACTIVE=production,keycloak`, `SPRING_DATASOURCE_URL=jdbc:postgresql://${rds_endpoint}:5432/kazi`, `KEYCLOAK_BASE_URL=https://auth.heykazi.com`, `KEYCLOAK_REALM=docteams`, `APP_BASE_URL=https://app.heykazi.com`, `PORTAL_BASE_URL=https://portal.heykazi.com`, `SPRING_DATA_REDIS_HOST=${redis_endpoint}`. Secrets: DB username/password from RDS master credentials, `PORTAL_JWT_SECRET`, `INTEGRATION_ENCRYPTION_KEY`. |
| 413.6 | Add Cloud Map namespace and service discovery | 413A | -- | Add `aws_service_discovery_private_dns_namespace` (name `kazi.internal`, VPC). Add `aws_service_discovery_service` for backend (name `backend`). Register backend ECS service with Cloud Map via `service_registries` block. Gateway references `backend.kazi.internal:8080` in its `BACKEND_URL` env var. |
| 413.7 | Add 3 new ECS services | 413A | 413.1, 413.2, 413.3 | Add `aws_ecs_service` for gateway, portal, keycloak. Each: `launch_type = "FARGATE"`, `desired_count = 1`, `deployment_circuit_breaker { enable = true, rollback = true }`, `deployment_minimum_healthy_percent = 100`, `deployment_maximum_percent = 200`. Backend service: set `health_check_grace_period_seconds = 180` (ADR-216). |
| 413.8 | Extend autoscaling for new services | 413A | 413.7 | Modify: `infra/modules/autoscaling/main.tf`. Add scalable targets + CPU target tracking for gateway (min 1, max 2) and portal (min 1, max 2). Keycloak: min 1, max 1 (no autoscaling). Use `for_each` or replicate existing pattern. Variables for min/max per service. |
| 413.9 | Add gateway target group | 413B | -- | Modify: `infra/modules/alb/main.tf`. New `aws_lb_target_group` for gateway: port 8443, protocol HTTP, health check path `/actuator/health`, healthy threshold 3, interval 30s. Target type: `ip` (Fargate). |
| 413.10 | Add portal and keycloak target groups | 413B | 413.9 | Same file. Portal target group: port 3002, health check `/`. Keycloak target group: port 8080, health check `/health/ready`. Both target type `ip`. |
| 413.11 | Restructure HTTPS listener rules | 413B | 413.9, 413.10 | Replace existing listener rules with priority-ordered rules per ADR-214. Priority 10: host `auth.heykazi.com` -> keycloak-tg. Priority 20: host `portal.heykazi.com` -> portal-tg. Priority 30: host `app.heykazi.com` + path `/bff/*` -> gateway-tg. Priority 40: host `app.heykazi.com` + path `/api/*` -> gateway-tg. Priority 50: host `app.heykazi.com` -> frontend-tg. Default action: fixed response 404. Use variables for domain names (staging uses `staging-` prefix). |
| 413.12 | Add HTTP-to-HTTPS redirect | 413B | 413.11 | Ensure HTTP listener (port 80) redirects to HTTPS (443). This may already exist -- verify and update if needed. |
| 413.13 | Enable ALB deletion protection for production | 413B | -- | Add `enable_deletion_protection = var.alb_deletion_protection` to `aws_lb`. Set `true` in `production.tfvars`, `false` in `staging.tfvars`. |
| 413.14 | Update ALB outputs | 413B | 413.9 | Modify: `infra/modules/alb/outputs.tf`. Add target group ARNs for gateway, portal, keycloak (consumed by ECS service registration). |

### Key Files

**Slice 413A -- Modify:**
- `infra/modules/ecs/main.tf` -- Add 3 task defs + 3 services, update 2 existing task defs
- `infra/modules/ecs/variables.tf` -- Add variables for new services (image tags, env vars, secrets ARNs)
- `infra/modules/ecs/outputs.tf` -- Add service/task def outputs
- `infra/modules/autoscaling/main.tf` -- Extend for gateway + portal
- `infra/modules/autoscaling/variables.tf` -- New service variables

**Slice 413B -- Modify:**
- `infra/modules/alb/main.tf` -- Add 3 target groups, restructure listener rules
- `infra/modules/alb/variables.tf` -- Add domain name variables, deletion protection variable
- `infra/modules/alb/outputs.tf` -- Add target group ARN outputs

**Read for context:**
- `infra/modules/ecs/main.tf` -- Existing frontend/backend task def patterns
- `infra/modules/alb/main.tf` -- Existing target group and listener rule patterns
- `infra/modules/autoscaling/main.tf` -- Existing autoscaling pattern

### Architecture Decisions

- **Gateway BFF split routing**: Per ADR-214, the ALB routes `/bff/*` and `/api/*` to the gateway at higher priority, and `/*` to the frontend as catch-all. This avoids routing all traffic through the gateway (which would bottleneck on static assets and SSR pages).
- **Cloud Map service discovery**: Gateway reaches backend via `backend.kazi.internal:8080`. This eliminates the internal ALB for backend access. The internal ALB can be deprecated.
- **Health check grace period 180s**: Per ADR-216, the backend task needs time for Flyway migrations across tenant schemas before becoming healthy. The 180s grace period accommodates up to 20 tenants with 84 migrations each.
- **Keycloak no autoscaling**: Keycloak at min 1 / max 1. At 5-20 tenants, a single Keycloak instance handles the load. Autoscaling Keycloak requires session replication (Infinispan) which adds complexity.

---

## Epic 414: Keycloak Deployment -- ECS Task, Dockerfile, Production Config

**Goal**: Create the Keycloak production Dockerfile with custom theme and realm import. Configure the gateway for Redis-backed sessions in production. Update the backend production profile to re-enable Flyway.

**References**: Architecture doc Sections 7.4 (Keycloak production config), 4.6 (database migration strategy), 6.4 (actuator production config); ADR-215 (Keycloak deployment), ADR-216 (Flyway on startup).

**Dependencies**: Epic 411 (RDS endpoint, Redis endpoint), Epic 413 (ECS task definition references).

**Scope**: Infra + Config

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **414A** | 414.1--414.4 | Create Keycloak production Dockerfile (`compose/keycloak/Dockerfile.production`). Multi-stage build: base Keycloak 26.5 image + custom theme JAR + realm import JSON. Add `HEALTHCHECK`. Configure for production mode (optimized, HTTP enabled for ALB termination). Docker + Infra. | **Done** (PR #862) |
| **414B** | 414.5--414.8 | Create gateway `application-production.yml`: switch session store from JDBC to Redis, set `cookie.secure=true`. Update backend `application-prod.yml`: re-enable Flyway (`spring.flyway.enabled: true`), add `keycloak` to active profiles default. Config only. | **Done** (PR #863) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 414.1 | Create Keycloak production Dockerfile | 414A | -- | New file: `compose/keycloak/Dockerfile.production`. Base: `quay.io/keycloak/keycloak:26.5`. Copy theme JAR from `compose/keycloak/theme/` or `compose/keycloak/themes/`. Copy realm import from `compose/keycloak/realm-export.json` to `/opt/keycloak/data/import/`. Set environment: `KC_HEALTH_ENABLED=true`, `KC_METRICS_ENABLED=true`. Run `build` command for optimized production image: `RUN /opt/keycloak/bin/kc.sh build`. Entrypoint: `start --import-realm`. Add `HEALTHCHECK --interval=30s --timeout=5s --start-period=60s CMD curl -f http://localhost:8080/health/ready || exit 1`. Non-root user (Keycloak image uses `keycloak` user by default). Expose port 8080. |
| 414.2 | Verify Keycloak Dockerfile builds | 414A | 414.1 | Verify: `docker build -f compose/keycloak/Dockerfile.production -t kazi/keycloak:test compose/keycloak/`. Ensure image size < 500 MB. Ensure health check endpoint works: `docker run -d --name kc-test kazi/keycloak:test && sleep 30 && docker exec kc-test curl -f http://localhost:8080/health/ready`. |
| 414.3 | Document Keycloak theme update process | 414A | 414.1 | The Keycloakify theme JAR is baked into the Docker image. To update the theme: rebuild the Keycloakify project, copy the new JAR to `compose/keycloak/theme/`, rebuild the Docker image. Document this in the runbook (E418B). |
| 414.4 | Document realm import strategy | 414A | 414.1 | Keycloak imports `realm-export.json` on first boot (`--import-realm`). Subsequent boots skip import if the realm exists. To update realm config: either use the Keycloak Admin API or update `realm-export.json` and redeploy with the realm deleted. Document in runbook. |
| 414.5 | Create gateway production profile | 414B | -- | New file: `gateway/src/main/resources/application-production.yml`. Content: `spring.session.store-type: redis` (override JDBC default), `spring.data.redis.host: ${SPRING_DATA_REDIS_HOST}`, `spring.data.redis.port: 6379`, `spring.data.redis.password: ${SPRING_DATA_REDIS_PASSWORD}`, `server.servlet.session.cookie.secure: true` (HTTPS behind ALB), `server.servlet.session.cookie.same-site: lax`. Pattern: existing `backend/src/main/resources/application-prod.yml`. |
| 414.6 | Update backend production profile -- re-enable Flyway | 414B | -- | Modify: `backend/src/main/resources/application-prod.yml`. Change `spring.flyway.enabled` from `false` to `true` (per ADR-216). Flyway runs on startup, migrating all tenant schemas before the health check passes. The ECS health check grace period (180s, set in E413A) accommodates this. |
| 414.7 | Update backend production profile -- structured logging | 414B | 414.6 | Modify: `backend/src/main/resources/application-prod.yml`. Add JSON structured logging pattern per architecture doc Section 6.4: `logging.pattern.console: '{"timestamp":"%d","level":"%p","logger":"%c","message":"%m","thread":"%t"}%n'`. Set `logging.level.root: WARN`, `logging.level.com.kazi: INFO`. This enables CloudWatch Logs Insights querying. |
| 414.8 | Update backend production profile -- actuator config | 414B | 414.6 | Modify: `backend/src/main/resources/application-prod.yml`. Add actuator production config: `management.endpoints.web.exposure.include: health,info,metrics`, `management.endpoint.health.show-details: when-authorized`, `management.health.db.enabled: true`, `management.health.redis.enabled: true`. |

### Key Files

**Slice 414A -- Create:**
- `compose/keycloak/Dockerfile.production` -- Keycloak production image

**Slice 414A -- Read for context:**
- `compose/keycloak/realm-export.json` -- Realm configuration to bake in
- `compose/keycloak/theme/` or `compose/keycloak/themes/` -- Custom theme JAR

**Slice 414B -- Create:**
- `gateway/src/main/resources/application-production.yml` -- Gateway production profile

**Slice 414B -- Modify:**
- `backend/src/main/resources/application-prod.yml` -- Re-enable Flyway, add structured logging, actuator config

**Read for context:**
- `gateway/src/main/resources/application.yml` -- Gateway base config (JDBC sessions, OAuth2)
- `backend/src/main/resources/application-prod.yml` -- Existing production profile

### Architecture Decisions

- **Redis sessions for gateway**: The gateway currently uses JDBC sessions (Postgres). For multi-instance ECS deployments, Redis is the correct choice -- no sticky sessions needed, sessions are shared across instances. ElastiCache Redis (E411B) provides the backing store.
- **Flyway re-enabled per ADR-216**: The `enabled: false` setting was a holdover from before multi-tenant Flyway was implemented. Re-enabling with the 180s health check grace period is the simplest approach at 5-20 tenant scale.
- **Realm import on first boot only**: Keycloak's `--import-realm` flag imports the realm if it doesn't already exist. This is safe for rolling updates (existing realm data is preserved).

---

## Epic 415: Dockerfile Hardening -- Health Checks, JAR Fixes, Build Args

**Goal**: Fix production-readiness issues in all 4 application Dockerfiles: add `HEALTHCHECK` instructions, fix hardcoded JAR names, update build args for Keycloak auth, fix portal missing `public/` copy.

**References**: Architecture doc Section 3.2 (Dockerfile updates); `.infra-audit.md` Section 4 (Dockerfile findings).

**Dependencies**: None (Dockerfiles are independent of Terraform).

**Scope**: Docker

**Estimated Effort**: S

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **415A** | 415.1--415.6 | Add `HEALTHCHECK` to all 4 Dockerfiles. Fix hardcoded JAR names in backend + gateway (use wildcard glob or build arg). Update frontend Dockerfile: remove Clerk build args. Fix portal Dockerfile: add `public/` copy. Docker only. | **Done** (PR #864) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 415.1 | Add HEALTHCHECK to backend Dockerfile | 415A | -- | Modify: `backend/Dockerfile`. Add: `HEALTHCHECK --interval=30s --timeout=5s --retries=3 --start-period=60s CMD curl -f http://localhost:8080/actuator/health \|\| exit 1`. Install `curl` in the JRE stage if not present (`apk add --no-cache curl`). Verify: `docker build -t kazi/backend:test backend/ && docker inspect kazi/backend:test \| jq '.[0].Config.Healthcheck'`. |
| 415.2 | Fix backend Dockerfile hardcoded JAR name | 415A | -- | Modify: `backend/Dockerfile`. Replace hardcoded `b2b-strawman-backend-0.0.1-SNAPSHOT.jar` with a wildcard or build arg. Option A (recommended): in the extract stage, use `java -Djarmode=tools -jar *.jar extract --layers --destination extracted`. Option B: add `ARG JAR_FILE=*.jar` and use `${JAR_FILE}`. Pattern: Spring Boot layered extraction with wildcard. Verify: `docker build -t kazi/backend:test backend/`. |
| 415.3 | Add HEALTHCHECK to gateway Dockerfile | 415A | -- | Modify: `gateway/Dockerfile`. Add: `HEALTHCHECK --interval=30s --timeout=5s --retries=3 --start-period=60s CMD curl -f http://localhost:8443/actuator/health \|\| exit 1`. Install `curl` if needed. Fix hardcoded `b2b-strawman-gateway-0.0.1-SNAPSHOT.jar` (same approach as 415.2). Verify: `docker build -t kazi/gateway:test gateway/`. |
| 415.4 | Add HEALTHCHECK to frontend Dockerfile | 415A | -- | Modify: `frontend/Dockerfile`. Add: `HEALTHCHECK --interval=30s --timeout=5s --retries=3 --start-period=30s CMD curl -f http://localhost:3000/ \|\| exit 1`. Install `curl` in runner stage (`apk add --no-cache curl`). Remove stale build args: `NEXT_PUBLIC_CLERK_PUBLISHABLE_KEY`. Keep: `NEXT_PUBLIC_AUTH_MODE`, `NEXT_PUBLIC_GATEWAY_URL`. Remove: `NEXT_PUBLIC_MOCK_IDP_URL`, `NEXT_PUBLIC_BACKEND_URL` (not needed in production Dockerfile). Verify: `docker build --build-arg NEXT_PUBLIC_AUTH_MODE=keycloak -t kazi/frontend:test frontend/`. |
| 415.5 | Fix portal Dockerfile missing public/ copy and add HEALTHCHECK | 415A | -- | Modify: `portal/Dockerfile`. Add `COPY --from=builder /app/public ./public` in the runner stage (matching frontend Dockerfile pattern). Add: `HEALTHCHECK --interval=30s --timeout=5s --retries=3 --start-period=30s CMD curl -f http://localhost:3002/ \|\| exit 1`. Update port if needed (audit says 3001, architecture says 3002 -- verify actual `EXPOSE` in Dockerfile). Verify: `docker build -t kazi/portal:test portal/`. |
| 415.6 | Verify all Dockerfiles build successfully | 415A | 415.1--415.5 | Run all 4 builds: `docker build -t kazi/backend:test backend/ && docker build -t kazi/gateway:test gateway/ && docker build -t kazi/frontend:test --build-arg NEXT_PUBLIC_AUTH_MODE=keycloak frontend/ && docker build -t kazi/portal:test portal/`. Verify all images are under 500 MB: `docker images kazi/*`. Verify health checks are present: `docker inspect kazi/backend:test \| jq '.[0].Config.Healthcheck'`. |

### Key Files

**Slice 415A -- Modify:**
- `backend/Dockerfile` -- Add HEALTHCHECK, fix JAR name
- `gateway/Dockerfile` -- Add HEALTHCHECK, fix JAR name
- `frontend/Dockerfile` -- Add HEALTHCHECK, remove Clerk build args
- `portal/Dockerfile` -- Add HEALTHCHECK, add public/ copy

**Read for context:**
- `frontend/Dockerfile` -- Reference pattern for portal public/ copy

### Architecture Decisions

- **HEALTHCHECK in Dockerfiles**: ECS uses both ALB health checks and container-level health checks. Container-level health checks detect issues faster (before ALB notices the target is unhealthy). The `--start-period` accounts for Java startup time (60s for backend/gateway) and Next.js startup (30s for frontend/portal).
- **Wildcard JAR matching**: Using `*.jar` in the extract step avoids hardcoding the artifact version. This is resilient to version bumps (e.g., from `0.0.1-SNAPSHOT` to `1.0.0`).

---

## Epic 416: CI/CD Pipeline -- OIDC, Image Promotion, Terraform Workflow

**Goal**: Update all GitHub Actions workflows for the 5-service architecture. Switch from IAM keys to GitHub OIDC. Implement image promotion (build once, tag-promote per ADR-217). Add Terraform plan/apply workflow.

**References**: Architecture doc Sections 5.1--5.5 (CI/CD pipeline); ADR-217 (image promotion); `.infra-audit.md` Section 3 (GitHub Actions findings).

**Dependencies**: Epic 412 (GitHub OIDC IAM role ARN, ECR repo names).

**Scope**: CI/CD

**Estimated Effort**: L

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **416A** | 416.1--416.5 | Update `.github/workflows/ci.yml`: add gateway, portal, and terraform-validate jobs. Replace Clerk build args. Switch to OIDC auth. Add change detection for new service paths. CI/CD only. | **Done** (PR #865) |
| **416B** | 416.6--416.11 | Create `.github/workflows/terraform.yml` (plan on PR, apply on merge). Rewrite `.github/workflows/deploy-staging.yml` (build 5 services, image promotion, deploy to staging ECS, smoke tests). CI/CD only. | **Done** (PR #866) |
| **416C** | 416.12--416.16 | Rewrite `.github/workflows/deploy-prod.yml` (promote images, environment protection). Update `.github/workflows/rollback.yml` (5 services). Update composite action if needed. CI/CD only. | **Done** (PR #867) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 416.1 | Add gateway and portal CI jobs | 416A | -- | Modify: `.github/workflows/ci.yml`. Add `gateway-test` job: checkout -> setup Java 25 -> `cd gateway && ../mvnw verify` (or appropriate Maven invocation for gateway submodule). Add `portal-test` job: checkout -> setup Node 22 -> `cd portal && pnpm install && pnpm test && pnpm lint && pnpm build`. Add change detection paths: `gateway/**`, `portal/**`. Pattern: existing `backend-test` and `frontend-test` jobs. |
| 416.2 | Add terraform-validate CI job | 416A | -- | Same file. Add `terraform-validate` job: checkout -> `hashicorp/setup-terraform@v3` -> `cd infra && terraform init -backend=false && terraform validate && terraform fmt -check`. Trigger on changes to `infra/**`. Pin Terraform version to match `versions.tf`. |
| 416.3 | Update frontend CI job -- remove Clerk | 416A | -- | Modify frontend-test job in `ci.yml`. Remove `CLERK_SECRET_KEY` and `NEXT_PUBLIC_CLERK_PUBLISHABLE_KEY` from env/build. Add `NEXT_PUBLIC_AUTH_MODE=keycloak` as build arg. |
| 416.4 | Switch CI to OIDC auth | 416A | -- | Modify all jobs in `ci.yml` that need AWS access (if any -- CI may not need AWS). If future CI needs ECR access for caching, use `aws-actions/configure-aws-credentials@v4` with `role-to-assume` and `role-session-name`. Remove `AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY` references. Ensure `permissions: id-token: write` is set at workflow level. |
| 416.5 | Update change detection paths | 416A | -- | Update `dorny/paths-filter` (or equivalent) to include: `backend/**` -> backend-test, `frontend/**` -> frontend-test, `gateway/**` -> gateway-test, `portal/**` -> portal-test, `infra/**` -> terraform-validate. Shared paths (e.g., `compose/**`) can trigger all. |
| 416.6 | Create Terraform plan/apply workflow | 416B | -- | New file: `.github/workflows/terraform.yml`. Triggers: `pull_request` (plan), `push` to `main` with `paths: [infra/**]` (apply staging), `workflow_dispatch` for production. Job `plan`: OIDC auth, `terraform init`, `terraform plan -var-file=environments/staging.tfvars -no-color`, post plan output as PR comment (`actions/github-script` or `peter-evans/create-or-update-comment`). Job `apply-staging`: `terraform apply -auto-approve -var-file=environments/staging.tfvars`. Job `apply-production`: `workflow_dispatch`, environment `production` (requires approval), `terraform apply -auto-approve -var-file=environments/production.tfvars`. |
| 416.7 | Rewrite deploy-staging workflow -- build phase | 416B | -- | Rewrite: `.github/workflows/deploy-staging.yml`. Trigger: `push` to `main`. Change detection: only build services with changes. Build job per service (parallel): OIDC auth -> ECR login -> Docker build with cache -> tag `${GITHUB_SHA}` + `staging` -> push to `kazi/{service}`. Frontend: build with `--build-arg NEXT_PUBLIC_AUTH_MODE=keycloak --build-arg NEXT_PUBLIC_GATEWAY_URL=https://staging-app.heykazi.com`. Backend/gateway/portal/keycloak: environment-agnostic builds. |
| 416.8 | Rewrite deploy-staging workflow -- deploy phase | 416B | 416.7 | Deploy job (depends on build): OIDC auth. For each service: use `ecs-deploy` composite action (update task def image to `${GITHUB_SHA}`, update service, wait for stability). Run in parallel for independent services, or sequential if preferred for safety. |
| 416.9 | Add staging smoke tests | 416B | 416.8 | Smoke test job (depends on deploy): `curl -f https://staging-app.heykazi.com/` (frontend), `curl -f https://staging-app.heykazi.com/bff/me` (expect 401 -- gateway is working), `curl -f https://staging-auth.heykazi.com/health/ready` (keycloak), `curl -f https://staging-portal.heykazi.com/` (portal). Failure triggers notification. |
| 416.10 | Build-and-push workflow deprecation | 416B | 416.7 | The old `build-and-push.yml` is superseded by the new `deploy-staging.yml`. Either remove `build-and-push.yml` or rename to `build-and-push.yml.deprecated`. Similarly, `deploy-dev.yml` is no longer needed (no dev environment). |
| 416.11 | Remove old deploy-dev workflow | 416B | -- | Remove or disable `.github/workflows/deploy-dev.yml`. The dev environment is removed -- only staging and production exist. |
| 416.12 | Rewrite deploy-prod workflow -- image promotion | 416C | -- | Rewrite: `.github/workflows/deploy-prod.yml`. Trigger: `workflow_dispatch` with input `image_tag` (Git SHA), or Git tag matching `v*`. Promotion job: OIDC auth -> for each service (backend, gateway, portal, keycloak): pull image by SHA tag from ECR, add `production` tag, push (re-tagging, no rebuild). Frontend: rebuild with `--build-arg NEXT_PUBLIC_GATEWAY_URL=https://app.heykazi.com` (env-specific per ADR-217). Deploy: same as staging but targeting production ECS cluster. Environment protection: `production` environment with required reviewers. |
| 416.13 | Add production smoke tests | 416C | 416.12 | Same pattern as staging smoke tests but against production URLs: `app.heykazi.com`, `auth.heykazi.com`, `portal.heykazi.com`. |
| 416.14 | Update rollback workflow for 5 services | 416C | -- | Modify: `.github/workflows/rollback.yml`. Extend service selection from `[frontend, backend]` to `[frontend, backend, gateway, portal, keycloak]`. Keep the existing image-diff display and confirmation logic. Update the `ecs-deploy` action calls for new service names. Add `all` option to rollback all 5 services. |
| 416.15 | Update ecs-deploy composite action | 416C | -- | Review: `.github/actions/ecs-deploy/action.yml`. The action is service-agnostic (takes cluster, service, image as inputs) so it may not need changes. Verify it works with the new `kazi/{service}` ECR naming and `heykazi-{env}` cluster naming. Update if any hardcoded `docteams` references exist. |
| 416.16 | Remove Clerk references from all workflows | 416C | -- | Search all workflow files for `CLERK`, `clerk`. Remove any remaining references to Clerk secrets, build args, or environment variables. Verify: `grep -ri clerk .github/`. |

### Key Files

**Slice 416A -- Modify:**
- `.github/workflows/ci.yml` -- Add 3 jobs, update auth, update build args

**Slice 416B -- Create:**
- `.github/workflows/terraform.yml` -- Plan/apply workflow

**Slice 416B -- Modify:**
- `.github/workflows/deploy-staging.yml` -- Full rewrite for 5 services + image promotion

**Slice 416B -- Remove/Deprecate:**
- `.github/workflows/build-and-push.yml` -- Superseded by deploy-staging
- `.github/workflows/deploy-dev.yml` -- No dev environment

**Slice 416C -- Modify:**
- `.github/workflows/deploy-prod.yml` -- Image promotion, 5 services
- `.github/workflows/rollback.yml` -- Extend to 5 services
- `.github/actions/ecs-deploy/action.yml` -- Verify/update for new naming

**Read for context:**
- `.github/workflows/deploy-staging.yml` -- Existing deploy pattern
- `.github/workflows/rollback.yml` -- Existing rollback pattern
- `.github/actions/ecs-deploy/action.yml` -- Composite action (service-agnostic)

### Architecture Decisions

- **Image promotion per ADR-217**: Backend, gateway, portal, and keycloak images are built once (on merge to main), tagged with Git SHA, and promoted to staging/production by re-tagging. Frontend is the exception -- it requires environment-specific builds due to `NEXT_PUBLIC_*` inlining.
- **Terraform in CI/CD**: Plan output as PR comment gives reviewers visibility into infrastructure changes before merge. Auto-apply to staging on merge provides fast feedback. Production apply requires manual dispatch with environment protection.
- **Workflow consolidation**: The old `build-and-push.yml` + `deploy-dev.yml` pattern (separate workflows with `workflow_run` trigger) is replaced by a single `deploy-staging.yml` that builds and deploys. This simplifies the pipeline and reduces execution time.

---

## Epic 417: Observability -- Alarms, SNS, Dashboards, Structured Logging

**Goal**: Extend the monitoring module with CloudWatch alarms, SNS alerting, and log groups for all 5 services. Configure structured JSON logging in the backend for CloudWatch Logs Insights querying.

**References**: Architecture doc Sections 6.1--6.4 (observability); `.infra-audit.md` findings #12 (monitoring gaps).

**Dependencies**: Epic 413 (ECS services must exist for alarm metrics to reference).

**Scope**: Infra + Config

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **417A** | 417.1--417.6 | Extend monitoring module: add 3 new CloudWatch log groups (gateway, portal, keycloak) with `/kazi/{env}/{service}` naming. Create SNS topic `heykazi-{env}-alerts` with email subscription. Add 8 CloudWatch alarms (ALB unhealthy hosts, 5xx rate, RDS CPU/storage/connections, ECS CPU). Infra only. | **Done** (PR #868) |
| **417B** | 417.7--417.8 | Consolidate structured logging and actuator production config in backend `application-prod.yml`. This overlaps with E414B tasks 414.7 and 414.8 -- if those were completed, this slice verifies and extends. If not, this slice performs the config. Config only. | **Done** (PR #869) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 417.1 | Add 3 new CloudWatch log groups | 417A | -- | Modify: `infra/modules/monitoring/main.tf`. Add log groups: `/kazi/{env}/gateway`, `/kazi/{env}/portal`, `/kazi/{env}/keycloak` (per ADR-218 naming). Retention: `var.log_retention_days` (30 staging, 90 production). Keep existing `/ecs/docteams-{env}-frontend` and `/ecs/docteams-{env}-backend` log groups for backward compatibility, or migrate to new naming. Pattern: existing log group resources in same file. |
| 417.2 | Create SNS topic for alerts | 417A | -- | Same file. Add `aws_sns_topic` named `heykazi-{env}-alerts`. Add `aws_sns_topic_subscription` for email (variable `alert_email`). Add `aws_sns_topic_policy` allowing CloudWatch to publish. Output: `sns_topic_arn`. |
| 417.3 | Add ALB unhealthy host alarms | 417A | 417.2 | Add 3 `aws_cloudwatch_metric_alarm` resources: backend unhealthy hosts, gateway unhealthy hosts, keycloak unhealthy hosts. Metric: `AWS/ApplicationELB` namespace, `UnHealthyHostCount`, `TargetGroup` dimension. Threshold: > 0 for 2 minutes. Action: SNS topic. Pattern: standard CloudWatch alarm Terraform resource. |
| 417.4 | Add ALB 5xx rate alarm | 417A | 417.2 | Alarm: `HTTPCode_Target_5XX_Count` on the ALB. Threshold: > 10 in 5 minutes (Sum statistic, period 300s). Action: SNS topic. |
| 417.5 | Add RDS alarms | 417A | 417.2 | 3 alarms: (1) RDS CPU > 80% for 10 minutes, (2) RDS FreeStorageSpace < 5 GB, (3) RDS DatabaseConnections > 80. All use `AWS/RDS` namespace with `DBInstanceIdentifier` dimension. Actions: SNS topic. |
| 417.6 | Add ECS CPU alarm | 417A | 417.2 | Alarm: backend ECS service CPU > 80% for 5 minutes. Namespace `AWS/ECS`, dimensions `ClusterName` + `ServiceName`. Action: SNS topic. Variables: `alarm_cpu_threshold`, `alarm_evaluation_periods`. |
| 417.7 | Verify structured logging config | 417B | -- | Verify or create: `backend/src/main/resources/application-prod.yml` contains JSON structured logging pattern (from E414B task 414.7). If E414B was completed, verify the pattern works with CloudWatch Logs Insights. If not, add: `logging.pattern.console: '{"timestamp":"%d","level":"%p","logger":"%c","message":"%m","thread":"%t"}%n'`. Test: run backend locally with `prod` profile and verify JSON output. |
| 417.8 | Verify actuator production config | 417B | -- | Verify or create: `application-prod.yml` contains actuator configuration (from E414B task 414.8). Health endpoints enabled for DB and Redis. Metrics endpoint exposed for potential future Prometheus scraping. |

### Key Files

**Slice 417A -- Modify:**
- `infra/modules/monitoring/main.tf` -- Add log groups, SNS, alarms
- `infra/modules/monitoring/variables.tf` -- Add alarm thresholds, email, retention vars
- `infra/modules/monitoring/outputs.tf` -- Add SNS topic ARN

**Slice 417A -- Modify (tfvars):**
- `infra/environments/staging.tfvars` -- `log_retention_days = 30`, `alert_email`
- `infra/environments/production.tfvars` -- `log_retention_days = 90`, `alert_email`

**Slice 417B -- Modify (if not done in E414B):**
- `backend/src/main/resources/application-prod.yml` -- Structured logging, actuator

**Read for context:**
- `infra/modules/monitoring/main.tf` -- Existing log group pattern

### Architecture Decisions

- **SNS email for MVP**: Email alerting is sufficient for a founder-operated platform with 5-20 tenants. Slack webhook can be added later as an additional SNS subscription.
- **8 alarms for key metrics**: Focused on availability (unhealthy hosts), reliability (5xx rate), and capacity (RDS CPU/storage/connections, ECS CPU). More granular alarms (per-service 5xx, latency percentiles) can be added when traffic patterns are established.

---

## Epic 418: DNS, SSL & Production Cutover

**Goal**: Configure ACM wildcard certificate, Route 53 DNS records for all subdomains, and ALB HTTPS listener. Create the operational runbook and production cutover checklist with smoke test script.

**References**: Architecture doc Sections 7.1--7.5 (DNS, SSL, Keycloak prod config, cutover checklist); Section 6.5 (runbook).

**Dependencies**: Epic 413 (ALB exists), Epic 416 (CI/CD pipeline works), Epic 417 (monitoring configured).

**Scope**: Infra

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **418A** | 418.1--418.5 | Extend DNS module: wildcard ACM certificate for `*.heykazi.com` with DNS validation. Route 53 A-record aliases for `app.heykazi.com`, `portal.heykazi.com`, `auth.heykazi.com` (and staging variants). Update ALB HTTPS listener with ACM cert ARN. TLS 1.3 security policy. Infra only. | **Done** (PR #870) |
| **418B** | 418.6--418.8 | Create `infra/RUNBOOK.md` (10-section operational runbook). Create `infra/scripts/smoke-test.sh` (automated smoke test for all 5 services). Create production cutover checklist. Docs only. | |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 418.1 | Configure wildcard ACM certificate | 418A | -- | Modify: `infra/modules/dns/main.tf`. Update or add `aws_acm_certificate` for `*.heykazi.com` with DNS validation. Add `aws_route53_record` for CNAME validation records. Add `aws_acm_certificate_validation` to wait for validation. Pattern: existing conditional DNS module. Variables: `domain_name` (default `heykazi.com`), `create_dns` (default `true`). |
| 418.2 | Add Route 53 A-record aliases | 418A | 418.1 | Same file. Add 3 `aws_route53_record` resources (type A, alias to ALB): `app.heykazi.com`, `portal.heykazi.com`, `auth.heykazi.com`. Add 3 staging records: `staging-app.heykazi.com`, `staging-portal.heykazi.com`, `staging-auth.heykazi.com`. Use variables for environment prefix. The hosted zone `heykazi.com` is assumed to exist (NS records delegated). |
| 418.3 | Update ALB HTTPS listener with ACM cert | 418A | 418.1 | Modify: `infra/modules/alb/main.tf`. Update HTTPS listener (port 443) to reference ACM certificate ARN from DNS module output. Set `ssl_policy = "ELBSecurityPolicy-TLS13-1-2-2021-06"`. Ensure HTTP listener (port 80) redirects to HTTPS. |
| 418.4 | Add DNS outputs | 418A | 418.2 | Modify: `infra/modules/dns/outputs.tf`. Add: `certificate_arn`, `app_domain`, `portal_domain`, `auth_domain`. These are informational (for runbook and verification). |
| 418.5 | Validate DNS module | 418A | 418.2 | Verify: `terraform validate`. Check `terraform plan` shows expected resources (1 ACM cert, 6-12 Route 53 records depending on validation + alias records, 0 destructive changes to existing DNS module resources). |
| 418.6 | Create operational runbook | 418B | -- | New file: `infra/RUNBOOK.md`. 10 sections per architecture doc Section 6.5: (1) First-time setup (AWS prereqs, bootstrap, domain delegation, initial deploy), (2) Deploying a new version (CI/CD pipeline, manual deploy, rollback), (3) Provisioning a new tenant (Keycloak org + DB schema + Flyway + packs), (4) Database operations (RDS connect via SSM, ad-hoc queries, Flyway status), (5) Viewing logs (CloudWatch console, Logs Insights queries), (6) Responding to alerts (each alarm meaning, investigation steps), (7) Rollback procedure (previous task def revision), (8) Keycloak operations (realm export/import, theme updates), (9) Cost monitoring (AWS bill, cost spike watch), (10) Disaster recovery (RDS PITR, S3 versioning). Written for a developer without AWS experience. |
| 418.7 | Create smoke test script | 418B | -- | New file: `infra/scripts/smoke-test.sh`. Arguments: `--env staging\|production`. Checks: `curl -sf https://{prefix}app.heykazi.com/` (frontend), `curl -sf https://{prefix}app.heykazi.com/bff/me` (expect 401 -- gateway), `curl -sf https://{prefix}auth.heykazi.com/health/ready` (keycloak), `curl -sf https://{prefix}portal.heykazi.com/` (portal). Exit 0 on all pass, exit 1 on any failure with descriptive error. Make executable. |
| 418.8 | Create production cutover checklist | 418B | -- | Add to `infra/RUNBOOK.md` as final section (or separate file `infra/CUTOVER.md`). 13-item checklist per architecture doc Section 7.5: Terraform apply succeeds, all 5 services healthy, HTTPS working for all 3 subdomains, access request flow works, org provisioning works, basic smoke test (create project, log time, create invoice), CloudWatch alarms configured, DNS propagation verified, backups verified, rollback procedure tested. |

### Key Files

**Slice 418A -- Modify:**
- `infra/modules/dns/main.tf` -- ACM wildcard cert, Route 53 records
- `infra/modules/dns/variables.tf` -- Domain name, create_dns variables
- `infra/modules/dns/outputs.tf` -- Certificate ARN, domain outputs
- `infra/modules/alb/main.tf` -- HTTPS listener SSL policy + cert

**Slice 418B -- Create:**
- `infra/RUNBOOK.md` -- 10-section operational runbook
- `infra/scripts/smoke-test.sh` -- Automated smoke test
- `infra/CUTOVER.md` (or section in RUNBOOK) -- Production cutover checklist

**Read for context:**
- `infra/modules/dns/main.tf` -- Existing conditional DNS pattern
- `infra/modules/alb/main.tf` -- Existing HTTPS listener

### Architecture Decisions

- **Wildcard certificate**: `*.heykazi.com` covers `app`, `portal`, `auth`, and any future subdomains (e.g., `api.heykazi.com` if the gateway gets its own subdomain). Single certificate simplifies renewal and ALB configuration.
- **Staging domain prefix**: `staging-app.heykazi.com` rather than `app.staging.heykazi.com` avoids a second-level subdomain wildcard and keeps a single `*.heykazi.com` cert for both environments.
- **Runbook for non-AWS developers**: The founding team may onboard developers without AWS experience. The runbook assumes intelligence but not cloud expertise, with explicit step-by-step instructions for common operations.

---

### Critical Files for Implementation
- `/Users/rakheendama/Projects/2026/b2b-strawman/infra/modules/ecs/main.tf`
- `/Users/rakheendama/Projects/2026/b2b-strawman/infra/modules/alb/main.tf`
- `/Users/rakheendama/Projects/2026/b2b-strawman/.github/workflows/deploy-staging.yml`
- `/Users/rakheendama/Projects/2026/b2b-strawman/infra/modules/secrets/main.tf`
- `/Users/rakheendama/Projects/2026/b2b-strawman/backend/src/main/resources/application-prod.yml`
