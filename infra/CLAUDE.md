# Infra CLAUDE.md

Terraform IaC for deploying the multi-tenant B2B SaaS platform to AWS. ECS Fargate for compute, Neon for Postgres (external), S3 for file storage.

## Status

All modules implemented. VPC + Security Groups (Epic 14A), ALB + ECR + ECS + Monitoring (Epic 14B), S3 + Secrets + IAM (Epic 14C), DNS + Auto-scaling (Epic 14D).

## Commands

```bash
terraform init                            # Initialize providers and modules
terraform plan -var-file=env.tfvars       # Preview changes
terraform apply -var-file=env.tfvars      # Apply changes
terraform destroy -var-file=env.tfvars    # Tear down
```

Always run `plan` before `apply`. Never apply without reviewing the plan output.

## Structure

```
infra/
├── modules/
│   ├── vpc/              # VPC, subnets, NAT gateways, route tables
│   ├── security-groups/  # SG definitions and rules
│   ├── ecr/              # Container registries for frontend + backend
│   ├── monitoring/       # CloudWatch log groups
│   ├── s3/               # S3 bucket with CORS and versioning
│   ├── secrets/          # Secrets Manager entries (placeholder values)
│   ├── iam/              # Task execution role, task roles, policies
│   ├── alb/              # Public ALB (HTTPS/HTTP) + internal ALB
│   ├── ecs/              # ECS Fargate cluster, task definitions, services
│   ├── dns/              # Route 53 + ACM certificate (conditional)
│   └── autoscaling/      # ECS service auto-scaling policies
├── environments/
│   ├── dev/
│   │   ├── main.tf
│   │   ├── variables.tf
│   │   ├── outputs.tf
│   │   ├── backend.tf          # S3 state backend config
│   │   └── terraform.tfvars.example
│   ├── staging/
│   │   └── ...
│   └── prod/
│       └── ...
├── .gitignore
└── CLAUDE.md
```

## Architecture Overview

### Compute: ECS Fargate
- **Frontend service**: Next.js containers (2 tasks, multi-AZ)
- **Backend service**: Spring Boot containers (2 tasks, multi-AZ)
- Both in private subnets, egress via NAT gateways
- Images pulled from ECR

### Networking: VPC
- CIDR: `10.0.0.0/16`
- Public subnets: `10.0.1.0/24`, `10.0.2.0/24` (ALBs, NAT gateways)
- Private subnets: `10.0.10.0/24`, `10.0.20.0/24` (ECS tasks)
- 2 AZs for redundancy

### Load Balancing
- **Public ALB** (HTTPS:443): Routes `/*` to frontend, `/api/*` to backend. Does NOT route `/internal/*`.
- **Internal ALB** (HTTP:8080): Routes `/internal/*` from frontend to backend. Not internet-accessible.

### Security Groups
| SG | Inbound | Purpose |
|----|---------|---------|
| `sg-public-alb` | 443/tcp from 0.0.0.0/0 | Public HTTPS |
| `sg-internal-alb` | 8080/tcp from `sg-frontend` | Internal service calls |
| `sg-frontend` | 3000/tcp from `sg-public-alb` | Next.js from public ALB |
| `sg-backend` | 8080/tcp from `sg-public-alb`, `sg-internal-alb` | Spring Boot from both ALBs |

### Database: Neon Postgres (external)
- Not provisioned by Terraform — managed via Neon console/API
- Connection strings stored in Secrets Manager
- Two connection strings per environment: pooled (PgBouncer) and direct (Flyway)

### Storage: S3
- Bucket naming: `docteams-{environment}` (e.g., `docteams-prod`)
- Key structure: `org/{orgId}/project/{projectId}/{documentId}`
- Backend accesses via IAM task role — no static credentials

### Secrets: AWS Secrets Manager
| Secret | Used By |
|--------|---------|
| Neon pooled connection string | Backend |
| Neon direct connection string | Backend (Flyway) |
| Clerk secret key | Frontend |
| Clerk webhook signing secret | Frontend |
| Internal API key | Frontend + Backend |

Injected into ECS tasks via `secrets` blocks in task definitions.

### DNS & TLS
- Route 53 for DNS management
- ACM certificate for HTTPS on public ALB

### Observability
- CloudWatch Log Groups: `/ecs/docteams-frontend`, `/ecs/docteams-backend`
- Fargate `awslogs` log driver
- Container Insights for CPU/memory/network metrics

## Conventions

### Module Design
- Each module is self-contained with `main.tf`, `variables.tf`, `outputs.tf`
- Modules expose outputs consumed by other modules (e.g., VPC outputs subnet IDs for ECS)
- Use `data` sources for cross-module references where possible

### Naming
- Resources prefixed with project name and environment: `docteams-{env}-{resource}`
- Tags on all resources: `Project`, `Environment`, `ManagedBy=terraform`

### State Management
- Remote state in S3 with DynamoDB locking (one state file per environment)
- State bucket and lock table bootstrapped manually or via a separate bootstrap config

### Environment Separation
- Each environment in its own directory under `environments/`
- Shared modules, environment-specific variables
- No cross-environment resource references

### Security
- Never commit `.tfvars` files with real values (gitignored)
- Provide `.tfvars.example` files with placeholder values
- Secrets referenced by ARN, not by value, in task definitions
- IAM follows least-privilege: task roles only get permissions they need
- No wildcard `*` in IAM policies — scope to specific resources

## CI/CD Integration

Terraform runs are expected to be triggered from GitHub Actions:
- `terraform plan` on PR (output in PR comment)
- `terraform apply` on merge to main (with manual approval for prod)
- State locking prevents concurrent modifications
