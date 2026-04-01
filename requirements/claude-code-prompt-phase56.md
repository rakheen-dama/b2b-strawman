You are a senior infrastructure/DevOps architect working on an existing multi-tenant "HeyKazi" platform (codebase internally named "Kazi").

The current system already has:

- **Multi-tenant architecture**: Schema-per-tenant PostgreSQL isolation. Each tenant gets a dedicated schema with Flyway-managed migrations (V7–V83+). Tenants are provisioned via `TenantProvisioningService` which creates Keycloak org + database schema + runs migrations + seeds packs.
- **5 deployable services**:
    - **Backend** (Spring Boot 4, Java 25, port 8080): REST API, virtual threads, S3 integration, Flyway migrations.
    - **Gateway** (Spring Cloud Gateway BFF, port 8443): Session-based auth, Keycloak OIDC, proxies `/api/*` to backend, serves `/bff/*` endpoints.
    - **Frontend** (Next.js 16, port 3000): App Router, BFF auth mode via `NEXT_PUBLIC_AUTH_MODE=keycloak`.
    - **Portal** (Next.js, port 3002): Customer-facing portal, magic link JWT auth.
    - **Keycloak 26.5** (port 8180 locally): Custom `docteams` realm, Keycloakify theme, protocol mappers, org membership.
- **Infrastructure dependencies**: PostgreSQL 16 (schema-per-tenant), Redis/session storage (gateway sessions), S3-compatible storage (document uploads, generated PDFs), SMTP relay (SendGrid BYOAK or org-provided).
- **Containerization** (Phase 13): Dockerfiles exist for all services. Multi-stage builds.
- **Local dev**: Docker Compose (`compose/docker-compose.yml`) runs all infrastructure + services. Scripts in `compose/scripts/`.
- **Auth**: Keycloak with `gateway-bff` client, `platform-admins` group, JIT member sync. Admin-approved org provisioning (Phase 39).
- **RBAC**: Application-managed `OrgRole` entity with capability-based `@RequiresCapability` authorization (Phase 41/46).
- **Vertical profiles**: Module guard architecture, `accounting-za` and `legal` profiles with pack seeding.
- **850+ backend tests, 280+ frontend tests**: Spring Boot integration tests, Vitest frontend tests, Playwright E2E (currently against mock IDP stack).
- **GitHub repository**: Private repo with basic GitHub Actions (builds exist but are incomplete/outdated — no deployment pipeline).
- **Existing Terraform infrastructure** (stale, from ~6 months ago):
    - 11 Terraform modules: `vpc`, `security-groups`, `ecr`, `ecs`, `alb`, `iam`, `s3`, `secrets`, `dns`, `monitoring`, `autoscaling`.
    - 3 environments: `dev`, `staging`, `prod` — each with own `main.tf`, `variables.tf`, `backend.tf`.
    - S3 state backend: `docteams-terraform-state` bucket with DynamoDB locking.
    - **Critical staleness**: Only provisions 2 services (frontend + backend). All secrets/env-vars reference Clerk (removed). No gateway, portal, or Keycloak infrastructure. ALB routes directly to frontend/backend instead of through Gateway BFF.
    - **What's solid**: VPC module (2-AZ, NAT gateways, proper CIDRs), S3 module (versioning, encryption, CORS), IAM patterns (least-privilege, scoped ARNs), ECS patterns (circuit breakers, deployment settings).
- **Existing GitHub Actions** (stale):
    - 7 workflows: `ci.yml`, `build-and-push.yml`, `deploy-dev.yml`, `deploy-staging.yml`, `deploy-prod.yml`, `rollback.yml`, `qodana_code_quality.yml`.
    - **Good patterns**: Change detection (`dorny/paths-filter`), composite `ecs-deploy` action, rollback with image diff, smoke tests.
    - **Critical staleness**: Only builds/deploys frontend + backend. CI uses Clerk build args. All workflows use long-lived IAM keys (OIDC declared but unused). No image promotion (each env rebuilds from source). No Terraform plan/apply workflow.
- **Existing Dockerfiles** (mostly production-ready):
    - All 4 services have multi-stage builds, non-root users, correct base images.
    - **Issues**: No `HEALTHCHECK` in any Dockerfile. Backend/gateway hardcode JAR filenames. Frontend Dockerfile still has Clerk build args. Portal missing `public/` copy.

***

## Objective of Phase 56

**Update and extend the existing (stale) AWS infrastructure and CI/CD pipeline** to match the current 5-service architecture, enabling HeyKazi to serve 5–20 paying tenants reliably. The approach is **update-in-place** — the existing Terraform modules are well-structured; the gaps are scope (only 2 of 5 services) and staleness (Clerk references, no Keycloak/Gateway/Portal support). This phase:

1. **Extends Terraform modules for 5 services** — Add gateway, portal, and Keycloak to existing ECR, ECS, security-groups, ALB, IAM, monitoring, and autoscaling modules. Replace Clerk secrets with Keycloak secrets.
2. **Adds missing infrastructure** — ElastiCache Redis (gateway sessions), RDS PostgreSQL (Keycloak database), Keycloak ECS task definition.
3. **Fixes ALB routing architecture** — Restructure public ALB to route through Gateway BFF (not directly to frontend/backend).
4. **Updates CI/CD pipeline for 5 services** — Extend build, push, deploy, and rollback workflows. Switch from IAM keys to GitHub OIDC. Add image promotion (build once, promote to staging/prod). Add Terraform plan/apply workflow.
5. **Renames from `docteams` to `heykazi`** — S3 state bucket, ECR repo prefixes, ECS cluster names, CloudWatch log groups, resource tags.
6. **Establishes observability** — Extend monitoring module with CloudWatch alarms, SNS alerting, dashboards.
7. **Fixes Dockerfiles** — Add `HEALTHCHECK` instructions, fix hardcoded JAR names, update build args, fix portal `public/` copy.
8. **Solves multi-tenant migration operations** — Flyway migration strategy across N tenant schemas during ECS deployments (currently disabled in prod profile).
9. **Delivers production DNS and SSL** — ACM certificate for `*.heykazi.com`, Route 53 records for app/portal/auth subdomains.
10. **Produces operational runbooks** — for tenant provisioning, rollback, migration, and incident response.

***

## Constraints and assumptions

1. **Architecture/stack constraints**

- All infrastructure defined in **Terraform** (HCL). State stored in S3 with DynamoDB locking.
- Compute via **AWS ECS/Fargate** — no EC2 instances to manage (except NAT Gateway).
- Database via **Amazon RDS PostgreSQL 16** — Multi-AZ for production, single instance for staging.
- Session storage via **Amazon ElastiCache Redis** (Valkey-compatible) — single node for gateway sessions.
- Object storage via **Amazon S3** — existing S3 integration in backend, just needs a real bucket.
- Container registry via **Amazon ECR** — one repo per service.
- Load balancing via **Application Load Balancer (ALB)** — path-based and host-based routing, HTTPS termination.
- DNS via **Amazon Route 53** — hosted zone for `heykazi.com` (or chosen domain).
- Secrets via **AWS Secrets Manager** — database credentials, Keycloak admin password, SendGrid API key. Non-secret config via SSM Parameter Store.
- CI/CD via **GitHub Actions** — existing repo, extend with deployment workflows.
- Do not introduce Kubernetes, Helm, or any K8s tooling.
- Do not use AWS CDK, Pulumi, or CloudFormation directly.
- Do not use Terraform Cloud — use S3 backend for state.

2. **Environment strategy**

- **Two environments**: `staging` and `production`.
- Terraform uses **workspaces** or **separate variable files** to differentiate environments. Prefer separate `.tfvars` files (e.g., `staging.tfvars`, `production.tfvars`) over workspaces for clarity.
- Staging auto-deploys on merge to `main`. Production deploys on Git tag (e.g., `v1.0.0`) or manual GitHub Actions dispatch.
- Both environments share the same Terraform modules — only instance sizes, replica counts, and domain names differ.

3. **Domain mapping**

```
app.heykazi.com      → ALB → Frontend (port 3000)
app.heykazi.com/bff/* → ALB → Gateway (port 8443)  [path-based routing, higher priority]
app.heykazi.com/api/* → ALB → Gateway (port 8443)  [path-based routing, higher priority]
portal.heykazi.com   → ALB → Portal (port 3002)
auth.heykazi.com     → ALB → Keycloak (port 8080 inside container)
```

Staging uses a prefix: `staging-app.heykazi.com`, `staging-portal.heykazi.com`, `staging-auth.heykazi.com`.

4. **Cost constraints (5–20 tenants)**

Target monthly AWS cost: **$200–$400/month** for production. Key sizing decisions:
- Fargate tasks: `0.5 vCPU / 1 GB` for frontend, portal, gateway. `1 vCPU / 2 GB` for backend, Keycloak.
- RDS: `db.t4g.medium` (2 vCPU, 4 GB) Multi-AZ for production. `db.t4g.micro` single-AZ for staging.
- ElastiCache: `cache.t4g.micro` single node (both environments).
- NAT Gateway: Single AZ (staging). Dual AZ (production).
- S3: Standard storage class, lifecycle rule to move old generated documents to Infrequent Access after 90 days.

5. **Security baseline**

- No public subnets for application services — all ECS tasks run in private subnets.
- ALB is the only public-facing resource (in public subnets).
- RDS and ElastiCache in private subnets, security group restricted to ECS tasks only.
- All inter-service communication over private network (service discovery or internal ALB).
- ECS task roles follow least-privilege: backend gets S3 read/write + SES send, frontend/portal get no AWS permissions, Keycloak gets none.
- Secrets Manager values referenced by ARN in ECS task definitions — never in plain text.
- ECR image scanning enabled.

***

## Section 1 — Terraform Foundation & Networking

### 1.1 Repository structure

```
infra/
├── modules/
│   ├── networking/          # VPC, subnets, NAT, IGW, security groups
│   ├── data/                # RDS, ElastiCache, S3
│   ├── ecr/                 # ECR repositories
│   ├── ecs/                 # ECS cluster, services, task definitions, ALB
│   ├── dns/                 # Route 53, ACM certificates
│   ├── secrets/             # Secrets Manager, SSM Parameter Store
│   └── monitoring/          # CloudWatch log groups, alarms, SNS topics
├── environments/
│   ├── staging.tfvars
│   └── production.tfvars
├── main.tf                  # Root module, calls all child modules
├── variables.tf             # Input variables
├── outputs.tf               # Output values (ALB DNS, RDS endpoint, etc.)
├── providers.tf             # AWS provider, S3 backend config
├── versions.tf              # Terraform and provider version constraints
└── README.md                # Setup instructions, prereqs, first-run guide
```

### 1.2 S3 state backend

- Bucket: `heykazi-terraform-state` (versioning enabled, server-side encryption).
- DynamoDB table: `heykazi-terraform-locks` (partition key: `LockID`).
- These resources must be created manually (or via a one-time bootstrap script) before Terraform can init.
- Provide a `bootstrap/` directory with a minimal Terraform config (local state) that creates the S3 bucket and DynamoDB table.

### 1.3 VPC and networking

- VPC CIDR: `10.0.0.0/16`.
- 2 Availability Zones (e.g., `af-south-1a`, `af-south-1b` for Cape Town region — if available. Otherwise `eu-west-1a`, `eu-west-1b`).
- Public subnets: `10.0.1.0/24`, `10.0.2.0/24` — for ALB and NAT Gateway.
- Private subnets: `10.0.10.0/24`, `10.0.11.0/24` — for ECS tasks, RDS, ElastiCache.
- Internet Gateway attached to VPC.
- NAT Gateway in one public subnet (staging) or both (production) — configurable via variable.
- Route tables: public subnets route `0.0.0.0/0` → IGW, private subnets route `0.0.0.0/0` → NAT Gateway.

### 1.4 Security groups

| Name | Inbound | Outbound | Used by |
|------|---------|----------|---------|
| `alb-sg` | 80, 443 from `0.0.0.0/0` | All traffic | ALB |
| `ecs-sg` | Container ports from `alb-sg` | All traffic | All ECS tasks |
| `rds-sg` | 5432 from `ecs-sg` | None | RDS |
| `redis-sg` | 6379 from `ecs-sg` | None | ElastiCache |

***

## Section 2 — Data Layer

### 2.1 RDS PostgreSQL

- Engine: PostgreSQL 16.
- Instance class: variable (`db.t4g.medium` production, `db.t4g.micro` staging).
- Multi-AZ: variable (`true` production, `false` staging).
- Storage: 20 GB gp3, auto-scaling up to 100 GB.
- Database name: `kazi`.
- Master credentials stored in Secrets Manager (auto-generated, auto-rotated).
- Automated backups: 7-day retention (production), 1-day (staging).
- Point-in-time recovery enabled (production).
- Parameter group: `log_min_duration_statement = 1000` (log slow queries > 1s), `shared_preload_libraries = pg_stat_statements`.
- Deletion protection enabled (production only).
- Subnet group using private subnets.

### 2.2 ElastiCache Redis

- Engine: Redis 7 (Valkey-compatible).
- Node type: `cache.t4g.micro` (both environments).
- Single node (no cluster mode).
- Subnet group using private subnets.
- Auth token stored in Secrets Manager.
- Used by: Gateway (Spring Session storage).

### 2.3 S3 bucket

- Bucket name: `heykazi-{environment}-documents` (e.g., `heykazi-production-documents`).
- Versioning enabled.
- Server-side encryption (SSE-S3).
- Lifecycle rule: objects in `generated/` prefix move to S3 Infrequent Access after 90 days.
- Block public access (all four settings enabled).
- CORS configuration for presigned URL uploads from frontend.
- IAM policy granting backend ECS task role `s3:GetObject`, `s3:PutObject`, `s3:DeleteObject`.

***

## Section 3 — ECR & Container Images

### 3.1 ECR repositories

Create one ECR repo per service:

| Repository | Source | Build context |
|------------|--------|---------------|
| `kazi/backend` | `backend/` | `backend/Dockerfile` |
| `kazi/gateway` | `backend/` | `backend/Dockerfile.gateway` (or multi-stage with profile) |
| `kazi/frontend` | `frontend/` | `frontend/Dockerfile` |
| `kazi/portal` | `frontend/` | `frontend/Dockerfile.portal` (or separate if exists) |
| `kazi/keycloak` | `compose/keycloak/` | Custom Keycloak image with theme + realm config |

- Image scanning on push enabled.
- Lifecycle policy: keep last 10 tagged images, expire untagged after 7 days.
- Image tag strategy: Git SHA for CI builds, semantic version for releases (e.g., `v1.0.0`).

### 3.2 Dockerfile updates

Review and update existing Dockerfiles for production readiness:

- **Backend**: Multi-stage build. Build with Maven, run with `eclipse-temurin:25-jre-alpine`. Set `JAVA_OPTS` for container memory limits. `SPRING_PROFILES_ACTIVE=production,keycloak`. Health check: `curl -f http://localhost:8080/actuator/health`.
- **Gateway**: Similar to backend. Separate Dockerfile or build profile. `SPRING_PROFILES_ACTIVE=production`. Health check: `curl -f http://localhost:8443/actuator/health`.
- **Frontend**: Multi-stage build. Build with Node, serve with `node:22-alpine`. Build args: `NEXT_PUBLIC_AUTH_MODE=keycloak`. Health check: `curl -f http://localhost:3000/`.
- **Portal**: Same pattern as frontend. Build args: portal-specific env vars. Health check: `curl -f http://localhost:3002/`.
- **Keycloak**: Based on `quay.io/keycloak/keycloak:26.5`. Copy custom theme JAR, realm import JSON. Set `KC_DB=postgres`, `KC_HOSTNAME`, etc. Health check: Keycloak's built-in `/health/ready`.

All Dockerfiles must:
- Use non-root users.
- Include `HEALTHCHECK` instructions.
- Set appropriate `EXPOSE` ports.
- Accept configuration via environment variables (12-factor).

***

## Section 4 — ECS Cluster & Service Definitions

### 4.1 ECS cluster

- Cluster name: `heykazi-{environment}`.
- Capacity provider: Fargate (default), Fargate Spot (optional for staging to reduce cost).
- Container Insights enabled (CloudWatch metrics).

### 4.2 Service definitions

Each service has: task definition, ECS service, target group, ALB listener rule.

| Service | CPU | Memory | Desired count | Min/Max (auto-scale) | Health check path |
|---------|-----|--------|---------------|----------------------|-------------------|
| backend | 1024 (1 vCPU) | 2048 MB | 1 | 1/3 | `/actuator/health` |
| gateway | 512 (0.5 vCPU) | 1024 MB | 1 | 1/2 | `/actuator/health` |
| frontend | 512 (0.5 vCPU) | 1024 MB | 1 | 1/2 | `/` |
| portal | 512 (0.5 vCPU) | 1024 MB | 1 | 1/2 | `/` |
| keycloak | 1024 (1 vCPU) | 2048 MB | 1 | 1/1 | `/health/ready` |

Auto-scaling policy: scale on CPU utilization > 70%. Scale-in cooldown: 300s. Scale-out cooldown: 60s.

### 4.3 Task definitions

Each task definition specifies:
- Container image from ECR (with tag).
- Port mappings.
- Environment variables from SSM Parameter Store.
- Secrets from Secrets Manager (DB credentials, Redis auth, Keycloak admin password).
- Log configuration: `awslogs` driver → CloudWatch Log Group.
- Task execution role (ECR pull + Secrets Manager read + CloudWatch Logs write).
- Task role (service-specific AWS permissions — e.g., backend gets S3 access).

**Backend environment variables:**
```
SPRING_PROFILES_ACTIVE=production,keycloak
SPRING_DATASOURCE_URL=jdbc:postgresql://${rds_endpoint}:5432/kazi
SPRING_DATASOURCE_USERNAME=${from_secrets_manager}
SPRING_DATASOURCE_PASSWORD=${from_secrets_manager}
SPRING_DATA_REDIS_HOST=${redis_endpoint}
SPRING_DATA_REDIS_PORT=6379
SPRING_THREADS_VIRTUAL_ENABLED=true
AWS_S3_BUCKET=${s3_bucket_name}
AWS_REGION=${aws_region}
KEYCLOAK_BASE_URL=https://auth.heykazi.com
KEYCLOAK_REALM=docteams
APP_BASE_URL=https://app.heykazi.com
PORTAL_BASE_URL=https://portal.heykazi.com
```

**Gateway environment variables:**
```
SPRING_PROFILES_ACTIVE=production
SPRING_DATA_REDIS_HOST=${redis_endpoint}
SPRING_SECURITY_OAUTH2_CLIENT_PROVIDER_KEYCLOAK_ISSUER_URI=https://auth.heykazi.com/realms/docteams
SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_KEYCLOAK_CLIENT_ID=gateway-bff
SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_KEYCLOAK_CLIENT_SECRET=${from_secrets_manager}
BACKEND_URL=http://backend.kazi.internal:8080
```

**Frontend environment variables:**
```
NEXT_PUBLIC_AUTH_MODE=keycloak
NEXT_PUBLIC_GATEWAY_URL=https://app.heykazi.com
```

**Keycloak environment variables:**
```
KC_DB=postgres
KC_DB_URL=jdbc:postgresql://${rds_endpoint}:5432/kazi_keycloak
KC_DB_USERNAME=${from_secrets_manager}
KC_DB_PASSWORD=${from_secrets_manager}
KC_HOSTNAME=auth.heykazi.com
KC_HTTP_ENABLED=true
KC_PROXY_HEADERS=xforwarded
KC_HEALTH_ENABLED=true
```

### 4.4 Service discovery

Use **AWS Cloud Map** (Service Connect) for internal service-to-service communication:
- `backend.kazi.internal` → Backend ECS service.
- Gateway references backend via this internal hostname.
- No internal ALB needed — Cloud Map + Fargate handles it.

### 4.5 ALB routing rules

Single ALB with HTTPS listener (port 443). HTTP listener (port 80) redirects to HTTPS.

| Priority | Condition | Target group |
|----------|-----------|-------------|
| 10 | Host: `auth.heykazi.com` | keycloak-tg |
| 20 | Host: `portal.heykazi.com` | portal-tg |
| 30 | Host: `app.heykazi.com`, Path: `/bff/*` | gateway-tg |
| 40 | Host: `app.heykazi.com`, Path: `/api/*` | gateway-tg |
| 50 | Host: `app.heykazi.com` | frontend-tg |
| 99 | Default | 404 fixed response |

Staging uses the same rules with `staging-` prefix on hostnames.

### 4.6 Database migration strategy

**Critical concern**: Flyway runs on backend startup and migrates schemas. In production with N tenant schemas, this means:

1. Backend starts → Flyway runs against the `public` schema (global tables: `access_request`, `org_secret`, etc.).
2. Backend's `TenantProvisioningService` runs Flyway against each tenant schema on first access (or a startup hook iterates all known schemas).

**Production strategy:**
- Add a backend startup task (or ECS `initContainer` pattern) that runs Flyway migrations against all existing tenant schemas before the service registers as healthy.
- ALB health check grace period: 120s (allow time for migrations on deploy).
- **Zero-downtime deploys**: ECS rolling update with `minimumHealthyPercent=100`, `maximumPercent=200`. New task starts, runs migrations, becomes healthy, old task drains. Since migrations are forward-only and additive, old code + new schema is safe during the transition window.
- **Rollback**: Revert to previous task definition revision (previous Docker image). Flyway migrations are not rolled back — they are designed to be forward-compatible.

***

## Section 5 — CI/CD Pipeline (GitHub Actions)

### 5.1 Workflow: PR checks (`.github/workflows/ci.yml`)

Triggers: `pull_request` to `main`.

Jobs:
1. **backend-test**: Checkout → setup Java 25 → `./mvnw verify` (runs all tests including Testcontainers).
2. **frontend-test**: Checkout → setup Node 22 → `pnpm install` → `pnpm test` (Vitest) → `pnpm lint` → `pnpm build`.
3. **portal-test**: Same as frontend, in portal directory.
4. **terraform-validate**: Checkout → setup Terraform → `terraform init -backend=false` → `terraform validate` → `terraform fmt -check`.

All jobs run in parallel. PR cannot merge unless all pass.

### 5.2 Workflow: Build and deploy to staging (`.github/workflows/deploy-staging.yml`)

Triggers: `push` to `main`.

Jobs:
1. **build-and-push** (per service, in parallel):
    - Checkout.
    - Configure AWS credentials (OIDC — use GitHub's OIDC provider, no long-lived access keys).
    - Login to ECR.
    - Docker build with cache (`--cache-from` previous image).
    - Tag: `${GITHUB_SHA}` + `latest`.
    - Push to ECR.
2. **deploy-staging** (depends on build-and-push):
    - Configure AWS credentials.
    - Update ECS service task definitions with new image tags.
    - Use `aws ecs update-service --force-new-deployment` for each service.
    - Wait for services to stabilize: `aws ecs wait services-stable`.
    - Run smoke test: `curl -f https://staging-app.heykazi.com/` and `curl -f https://staging-app.heykazi.com/bff/me` (expect 401).
3. **notify** (depends on deploy-staging):
    - Post deploy status to a Slack webhook or GitHub commit status.

### 5.3 Workflow: Deploy to production (`.github/workflows/deploy-production.yml`)

Triggers: `workflow_dispatch` (manual) with input for image tag. Or triggered by Git tag matching `v*`.

Jobs:
1. **deploy-production**:
    - Same as staging deploy but targets production ECS cluster.
    - Uses production `.tfvars` if Terraform changes are included.
    - Requires GitHub environment protection rules (manual approval).
2. **smoke-test**:
    - `curl -f https://app.heykazi.com/`.
    - `curl -f https://app.heykazi.com/actuator/health` (via gateway or direct if exposed).
3. **notify**.

### 5.4 Workflow: Terraform plan/apply (`.github/workflows/terraform.yml`)

Triggers: `pull_request` (plan only, comment on PR), `push` to `main` with changes in `infra/` (apply to staging), `workflow_dispatch` for production apply.

Jobs:
1. **plan**: `terraform plan -var-file=staging.tfvars` → output as PR comment.
2. **apply-staging**: `terraform apply -auto-approve -var-file=staging.tfvars` (only on main merge).
3. **apply-production**: `terraform apply -auto-approve -var-file=production.tfvars` (manual dispatch only, with environment protection).

### 5.5 GitHub OIDC for AWS

Use GitHub's OIDC identity provider to authenticate with AWS — no long-lived `AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY` stored in GitHub Secrets.

- Create IAM OIDC provider for `token.actions.githubusercontent.com`.
- Create IAM role `heykazi-github-actions` with trust policy scoped to the repo.
- Role permissions: ECR push, ECS update, S3 state access, Secrets Manager read, CloudWatch write.

***

## Section 6 — Observability & Operations

### 6.1 CloudWatch Log Groups

One log group per service per environment:

- `/kazi/{environment}/backend`
- `/kazi/{environment}/gateway`
- `/kazi/{environment}/frontend`
- `/kazi/{environment}/portal`
- `/kazi/{environment}/keycloak`

Retention: 30 days (staging), 90 days (production).

ECS tasks use `awslogs` log driver → these log groups.

### 6.2 CloudWatch Alarms

| Alarm | Metric | Threshold | Action |
|-------|--------|-----------|--------|
| Backend unhealthy | ALB `UnHealthyHostCount` (backend-tg) | > 0 for 2 minutes | SNS → email |
| Gateway unhealthy | ALB `UnHealthyHostCount` (gateway-tg) | > 0 for 2 minutes | SNS → email |
| Keycloak unhealthy | ALB `UnHealthyHostCount` (keycloak-tg) | > 0 for 2 minutes | SNS → email |
| High 5xx rate | ALB `HTTPCode_Target_5XX_Count` | > 10 in 5 minutes | SNS → email |
| RDS CPU high | RDS `CPUUtilization` | > 80% for 10 minutes | SNS → email |
| RDS storage low | RDS `FreeStorageSpace` | < 5 GB | SNS → email |
| RDS connections high | RDS `DatabaseConnections` | > 80 (out of ~100 for t4g.medium) | SNS → email |
| ECS backend CPU | ECS `CPUUtilization` (backend service) | > 80% for 5 minutes | SNS → email |

### 6.3 SNS alerting

- SNS topic: `heykazi-{environment}-alerts`.
- Email subscription (founder's email for now).
- Future: Slack webhook subscription.

### 6.4 Spring Boot Actuator production configuration

Create a `application-production.yml` profile for the backend:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    health:
      show-details: when-authorized
  health:
    db:
      enabled: true
    redis:
      enabled: true

logging:
  level:
    root: WARN
    com.kazi: INFO
    org.springframework.security: WARN
    org.hibernate.SQL: WARN
  pattern:
    console: '{"timestamp":"%d","level":"%p","logger":"%c","message":"%m","thread":"%t"}%n'
```

JSON-structured logging for CloudWatch Logs Insights querying.

### 6.5 Operational runbook

Create `infra/RUNBOOK.md` covering:

1. **First-time setup**: AWS account prerequisites, Terraform bootstrap, domain delegation, initial deploy.
2. **Deploying a new version**: How the CI/CD pipeline works, manual deploy steps, rollback procedure.
3. **Provisioning a new tenant**: What happens (Keycloak org + DB schema + Flyway + packs), how to verify, how to troubleshoot.
4. **Database operations**: Connecting to RDS via bastion/SSM, running ad-hoc queries, checking Flyway migration status, manual migration.
5. **Viewing logs**: CloudWatch Logs console, Logs Insights query examples (e.g., find errors for a tenant, trace a request).
6. **Responding to alerts**: What each alarm means, investigation steps, escalation.
7. **Rollback procedure**: How to revert an ECS deployment (previous task definition revision).
8. **Keycloak operations**: Realm export/import, adding protocol mappers, theme updates.
9. **Cost monitoring**: Where to find the AWS bill, what to watch for cost spikes.
10. **Disaster recovery**: RDS point-in-time recovery procedure, S3 versioning recovery.

***

## Section 7 — DNS, SSL & Production Cutover

### 7.1 Route 53

- Hosted zone: `heykazi.com` (assumes domain is registered and NS records delegated to Route 53).
- A records (alias) pointing to ALB:
    - `app.heykazi.com` → ALB
    - `portal.heykazi.com` → ALB
    - `auth.heykazi.com` → ALB
    - `staging-app.heykazi.com` → Staging ALB
    - `staging-portal.heykazi.com` → Staging ALB
    - `staging-auth.heykazi.com` → Staging ALB

### 7.2 ACM certificates

- Wildcard certificate: `*.heykazi.com` (covers all subdomains).
- DNS validation via Route 53 (Terraform can automate the CNAME records).
- Separate certificate for staging if using separate subdomain pattern.

### 7.3 ALB HTTPS configuration

- HTTPS listener (443) with ACM certificate.
- HTTP listener (80) with redirect action → HTTPS.
- Security policy: `ELBSecurityPolicy-TLS13-1-2-2021-06` (TLS 1.3 preferred, TLS 1.2 minimum).
- Sticky sessions: disabled (stateless frontend, Redis-backed gateway sessions).

### 7.4 Keycloak production configuration

Keycloak in production needs specific configuration:

- `KC_HOSTNAME=auth.heykazi.com` — Keycloak generates URLs with this hostname.
- `KC_PROXY_HEADERS=xforwarded` — trust ALB's `X-Forwarded-*` headers.
- `KC_HTTP_ENABLED=true` — ALB terminates SSL, Keycloak runs HTTP internally.
- `KC_HOSTNAME_STRICT=false` — allow admin console access (can be tightened later).
- Separate Keycloak database: `kazi_keycloak` schema or separate RDS instance. Recommend separate schema in the same RDS instance for cost efficiency.
- Realm import on first boot: mount the realm JSON as a volume or bake into the Docker image.
- Theme: Keycloakify theme JAR baked into the Docker image.

### 7.5 Production cutover checklist

Before going live:

1. [ ] Terraform apply to production succeeds without errors.
2. [ ] All 5 ECS services healthy and passing ALB health checks.
3. [ ] `https://app.heykazi.com` loads the HeyKazi landing page.
4. [ ] `https://auth.heykazi.com` loads the Keycloak login page with custom theme.
5. [ ] `https://portal.heykazi.com` loads the portal login page.
6. [ ] Access request form works: submit → OTP email → verify → pending.
7. [ ] Platform admin can approve access request → org provisioned.
8. [ ] Owner can register via Keycloak invite → login → dashboard loads.
9. [ ] Create a project, log time, create an invoice — basic smoke test.
10. [ ] CloudWatch alarms configured and tested (trigger a test alarm).
11. [ ] DNS propagation verified (dig/nslookup from external network).
12. [ ] Backups verified: RDS automated backup visible in console.
13. [ ] Rollback procedure tested: deploy old image, verify service recovers.

***

## Out of scope

- **Multi-region / global CDN**: Not needed at 5–20 tenants. CloudFront can be added later.
- **WAF rules**: Security groups + ALB + application-level auth suffice for now.
- **Blue/green deployments**: ECS rolling update is sufficient. Blue/green adds complexity without proportional benefit at this scale.
- **Auto-scaling beyond basic**: CPU-based scaling with modest limits (1–3 instances). No custom metrics scaling.
- **CI/CD for Playwright E2E**: E2E tests run locally for now. CI integration is a future phase.
- **Cost optimization**: No Reserved Instances, Savings Plans, or Spot for production. Staging can use Fargate Spot.
- **Custom domain per tenant**: All tenants share `app.heykazi.com`. Vanity domains are a future feature.
- **Rate limiting / DDoS protection**: Application-level auth provides basic protection. AWS Shield Standard is included free. Dedicated rate limiting (API Gateway, WAF) deferred.
- **Centralized logging beyond CloudWatch**: No ELK/Datadog/Grafana stack. CloudWatch Logs Insights is sufficient for 5–20 tenants.
- **Database read replicas**: Single primary is sufficient at this scale.
- **Container orchestration tooling**: No ECS CLI, Copilot, or similar. Raw Terraform + GitHub Actions.

***

## ADR topics to address

1. **ECS Fargate over EKS** — Why Fargate for a 5-service architecture at 5–20 tenant scale. Trade-offs: less flexibility, no DaemonSets, but zero cluster management overhead.
2. **Terraform over CDK/Pulumi** — Why HCL Terraform with S3 backend. Trade-offs: less type safety than CDK, but industry standard, larger community, and any DevOps hire can contribute.
3. **Multi-schema migration on deploy** — How Flyway migrations run across N tenant schemas during rolling ECS deployments. Forward-compatible migration design. Why rollback means "deploy previous image" not "rollback schema."
4. **GitHub OIDC over IAM keys** — Security benefits of short-lived credentials. No secrets rotation burden.
5. **Single ALB with path/host routing** — Why one ALB for all services (cost) vs. separate ALBs (isolation). At 5–20 tenants, cost wins.
6. **Staging as production mirror** — Why staging mirrors production topology (same modules, different sizes) rather than a simplified single-instance setup.

***

## Style and boundaries

- All Terraform resources must be tagged with `Environment`, `Service`, `Project=kazi`, and `ManagedBy=terraform`.
- Use Terraform modules for reusability — do not put all resources in a single flat file.
- Pin Terraform provider versions explicitly. Pin Terraform version in `versions.tf`.
- GitHub Actions workflows should use pinned action versions (e.g., `actions/checkout@v4`, not `@latest`).
- All secrets are referenced by Secrets Manager ARN — never hardcoded, never in `.tfvars` files.
- Infrastructure changes go through PR review (Terraform plan as PR comment) before apply.
- Production deploys require explicit manual approval via GitHub environment protection.
- The runbook should be written for a developer who has never used AWS — assume intelligence but not AWS experience.
- Dockerfiles must produce images under 500 MB (Java images are the risk — use Alpine + JRE-only).
