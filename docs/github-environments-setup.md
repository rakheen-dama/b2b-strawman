# GitHub Environments & Secrets Setup

This guide documents the GitHub repository configuration required for the CI/CD deployment pipeline.

## Repository Secrets

Configure these at **Settings → Secrets and variables → Actions → Repository secrets**:

| Secret | Description | Example |
|--------|-------------|---------|
| `AWS_ACCESS_KEY_ID` | IAM credentials for CI/CD | `AKIA...` |
| `AWS_SECRET_ACCESS_KEY` | IAM credentials for CI/CD | `wJalr...` |
| `AWS_ACCOUNT_ID` | AWS account number (used in ECR URIs) | `123456789012` |
| `NEXT_PUBLIC_CLERK_PUBLISHABLE_KEY` | Clerk publishable key (needed at Docker build time) | `pk_live_...` or `pk_test_...` |

> **Note:** `NEXT_PUBLIC_CLERK_PUBLISHABLE_KEY` is not technically a secret (it's embedded in client-side code), but storing it as a GitHub secret keeps the workflow clean and avoids hardcoding environment-specific values.

## Repository Variables

Configure these at **Settings → Secrets and variables → Actions → Repository variables**:

| Variable | Description | Default |
|----------|-------------|---------|
| `AWS_REGION` | AWS region for all resources | `us-east-1` |

## GitHub Environments

Configure at **Settings → Environments**:

### `dev`

- **Protection rules:** None
- **Deployment trigger:** Automatic on merge to `main`
- **Environment secrets:** None (uses repository-level secrets)

### `staging`

- **Protection rules:** Required reviewers (1 approval)
- **Wait timer:** None
- **Environment secrets:** Override `NEXT_PUBLIC_CLERK_PUBLISHABLE_KEY` if staging uses a different Clerk instance

### `prod`

- **Protection rules:** Required reviewers (2 approvals)
- **Wait timer:** 5 minutes (allows cancellation window)
- **Environment secrets:** Override `NEXT_PUBLIC_CLERK_PUBLISHABLE_KEY` with production Clerk key

## IAM Permissions for CI/CD

The IAM user/role used by GitHub Actions needs these permissions:

### ECR (image push)

```json
{
  "Effect": "Allow",
  "Action": [
    "ecr:GetAuthorizationToken"
  ],
  "Resource": "*"
},
{
  "Effect": "Allow",
  "Action": [
    "ecr:BatchCheckLayerAvailability",
    "ecr:GetDownloadUrlForLayer",
    "ecr:BatchGetImage",
    "ecr:PutImage",
    "ecr:InitiateLayerUpload",
    "ecr:UploadLayerPart",
    "ecr:CompleteLayerUpload"
  ],
  "Resource": [
    "arn:aws:ecr:us-east-1:*:repository/docteams-*"
  ]
}
```

### ECS (deploy)

```json
{
  "Effect": "Allow",
  "Action": [
    "ecs:DescribeTaskDefinition",
    "ecs:RegisterTaskDefinition"
  ],
  "Resource": "*"
},
{
  "Effect": "Allow",
  "Action": [
    "ecs:UpdateService",
    "ecs:DescribeServices"
  ],
  "Resource": [
    "arn:aws:ecs:us-east-1:*:service/docteams-*/docteams-*"
  ]
}
```

### ELB (smoke tests)

```json
{
  "Effect": "Allow",
  "Action": [
    "elasticloadbalancing:DescribeLoadBalancers"
  ],
  "Resource": "*"
}
```

### IAM (pass role to ECS tasks)

```json
{
  "Effect": "Allow",
  "Action": [
    "iam:PassRole"
  ],
  "Resource": [
    "arn:aws:iam::*:role/docteams-*"
  ]
}
```

## Recommended: OIDC Authentication

For production environments, replace static IAM credentials with OIDC federation. This eliminates long-lived secrets:

1. **Create an OIDC Identity Provider** in IAM for `token.actions.githubusercontent.com`
2. **Create an IAM Role** with a trust policy scoped to your repository:

```json
{
  "Effect": "Allow",
  "Principal": {
    "Federated": "arn:aws:iam::ACCOUNT_ID:oidc-provider/token.actions.githubusercontent.com"
  },
  "Action": "sts:AssumeRoleWithWebIdentity",
  "Condition": {
    "StringEquals": {
      "token.actions.githubusercontent.com:aud": "sts.amazonaws.com"
    },
    "StringLike": {
      "token.actions.githubusercontent.com:sub": "repo:YOUR_ORG/YOUR_REPO:*"
    }
  }
}
```

3. **Update workflows** to use role assumption instead of static keys:

```yaml
- uses: aws-actions/configure-aws-credentials@v4
  with:
    role-to-assume: arn:aws:iam::ACCOUNT_ID:role/github-actions-deploy
    aws-region: us-east-1
```

This is the recommended approach for production. Static keys are acceptable for initial setup and development.

## Workflow Overview

```
Push to main
  ├─→ CI (ci.yml)
  │     └─ lint, test, build (pass/fail gate)
  │
  └─→ Build & Push (build-and-push.yml)
        ├─ Detect changes (path filter)
        ├─ Build frontend → Push to ECR (tagged with SHA)
        └─ Build backend  → Push to ECR (tagged with SHA)
              │
              └─→ Deploy Dev (deploy-dev.yml, automatic)
                    ├─ Deploy frontend to ECS
                    ├─ Deploy backend to ECS
                    └─ Smoke tests (health endpoints)
```

Staging and production deployment workflows are added in Epic 15B.
