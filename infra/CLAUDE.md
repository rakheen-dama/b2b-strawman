# Infra CLAUDE.md

Terraform IaC for deploying the Kazi multi-tenant B2B SaaS platform to AWS.
5 services on ECS Fargate, RDS PostgreSQL, ElastiCache Redis, S3 for file storage.

## Status

Foundation modules implemented: VPC, Security Groups, ALB, ECR, ECS, Monitoring, S3, Secrets, IAM, DNS, Autoscaling.
Environment structure flattened to root-level config with per-environment `.tfvars` files.
Bootstrap config for S3 state bucket and DynamoDB lock table in `bootstrap/`.

## Commands

```bash
# Initialize with environment-specific state key
terraform init -backend-config="key=staging/terraform.tfstate"
terraform init -backend-config="key=production/terraform.tfstate"

# Plan and apply
terraform plan -var-file=environments/staging.tfvars
terraform apply -var-file=environments/staging.tfvars

# Destroy
terraform destroy -var-file=environments/staging.tfvars

# Format check
terraform fmt -check -recursive

# Validate
terraform validate
```

Always run `plan` before `apply`. Never apply without reviewing the plan output.

## Structure

```
infra/
├── main.tf                    # Root module composing all child modules
├── variables.tf               # All input variables with types and defaults
├── outputs.tf                 # All output values from child modules
├── providers.tf               # AWS provider + S3 backend config
├── versions.tf                # Terraform + provider version pins
├── bootstrap/                 # Standalone config for state bucket + lock table
│   ├── main.tf
│   ├── variables.tf
│   ├── outputs.tf
│   └── README.md
├── modules/
│   ├── vpc/                   # VPC, subnets, NAT gateways, route tables
│   ├── security-groups/       # SG definitions and rules
│   ├── ecr/                   # Container registries
│   ├── monitoring/            # CloudWatch log groups
│   ├── s3/                    # S3 bucket with CORS and versioning
│   ├── secrets/               # Secrets Manager entries (Keycloak-era)
│   ├── iam/                   # Task execution role, task roles, policies
│   ├── alb/                   # Public ALB (HTTPS/HTTP) + internal ALB
│   ├── ecs/                   # ECS Fargate cluster, task definitions, services
│   ├── dns/                   # Route 53 + ACM certificate (conditional)
│   └── autoscaling/           # ECS service auto-scaling policies
├── environments/
│   ├── staging.tfvars         # Staging variable values
│   └── production.tfvars      # Production variable values
├── .gitignore
└── CLAUDE.md
```

## Architecture Overview

### Services (5 on ECS Fargate)
- **Frontend**: Next.js (App Router) — public-facing UI
- **Backend**: Spring Boot — API server, multitenancy, business logic
- **Gateway**: Spring Cloud Gateway BFF — routes frontend-to-backend, session management
- **Portal**: Next.js — customer-facing portal
- **Keycloak**: Identity provider — orgs, RBAC, SSO

### Networking: VPC
- Staging CIDR: `10.1.0.0/16`, Production CIDR: `10.2.0.0/16`
- Public subnets: ALBs, NAT gateways
- Private subnets: ECS tasks, RDS, ElastiCache
- 2 AZs for redundancy

### Load Balancing
- **Public ALB** (HTTPS:443): Routes to frontend, backend, gateway, portal, Keycloak
- **Internal ALB** (HTTP:8080): Internal service-to-service calls

### Database: RDS PostgreSQL
- Schema-per-tenant multitenancy (Hibernate + Flyway)
- Connection strings stored in Secrets Manager

### Cache: ElastiCache Redis
- Gateway session storage
- Auth token stored in Secrets Manager

### Storage: S3
- Document storage with org/project scoped key structure
- Backend accesses via IAM task role

### Secrets: AWS Secrets Manager

| Secret | Used By |
|--------|---------|
| `database-url` | Backend |
| `database-migration-url` | Backend (Flyway) |
| `internal-api-key` | Frontend + Backend |
| `keycloak-client-id` | Backend, Gateway |
| `keycloak-client-secret` | Backend, Gateway |
| `keycloak-admin-username` | Backend (admin API) |
| `keycloak-admin-password` | Backend (admin API) |
| `portal-jwt-secret` | Portal, Backend |
| `portal-magic-link-secret` | Portal |
| `integration-encryption-key` | Backend |
| `smtp-username` | Backend (email) |
| `smtp-password` | Backend (email) |
| `email-unsubscribe-secret` | Backend |
| `redis-auth-token` | Gateway, ElastiCache |

Injected into ECS tasks via `secrets` blocks in task definitions.

### DNS & TLS
- Route 53 for DNS management
- ACM certificate for HTTPS on public ALB

### Observability
- CloudWatch Log Groups per service
- Fargate `awslogs` log driver
- Container Insights for CPU/memory/network metrics

## Conventions

### Naming (ADR-218)
- **Tags**: `Project = kazi`, `Environment = {env}`, `ManagedBy = terraform`
- **Customer-facing resources**: `heykazi-` prefix (e.g., `heykazi-terraform-state`)
- **Internal resources**: `kazi-{env}-{resource}` (via `${var.project}-${var.environment}` interpolation)
- Some legacy resources retain `docteams` in names until natural replacement

### Module Design
- Each module is self-contained with `main.tf`, `variables.tf`, `outputs.tf`
- All modules have `default = "kazi"` for the `project` variable
- Modules expose outputs consumed by other modules
- Use `data` sources for cross-module references where possible

### State Management
- Remote state in S3 (`heykazi-terraform-state`) with DynamoDB locking (`heykazi-terraform-locks`)
- One state file per environment: `staging/terraform.tfstate`, `production/terraform.tfstate`
- Bootstrap config in `infra/bootstrap/` (uses local state)
- State key passed via CLI: `-backend-config="key=staging/terraform.tfstate"`

### Environment Separation
- Single root configuration with environment-specific `.tfvars` files
- Staging and production only (no dev environment)
- No cross-environment resource references

### Security
- Never commit `.tfvars` files with real secrets (gitignored except committed env files)
- Secrets referenced by ARN, not by value, in task definitions
- IAM follows least-privilege: task roles only get permissions they need
- No wildcard `*` in IAM policies — scope to specific resources

### Version Constraints
- Terraform >= 1.9.0
- AWS provider >= 5.0

## CI/CD Integration

Terraform runs are expected to be triggered from GitHub Actions:
- `terraform plan` on PR (output in PR comment)
- `terraform apply` on merge to main (with manual approval for production)
- State locking prevents concurrent modifications
