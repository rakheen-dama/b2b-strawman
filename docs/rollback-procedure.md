# Rollback Procedure

## Overview

Rollback reverts an ECS service to its previous task definition revision. This is fast (~2-3 minutes) because it reuses an existing Docker image — no build step required.

## When to Rollback

- Deployment caused errors (5xx responses, health check failures)
- Application crash loops (ECS circuit breaker may auto-rollback before you act)
- Performance degradation after deploy
- Feature regression discovered post-deploy

## Using the GitHub Actions Workflow

### Quick Rollback

1. Go to **Actions** → **Rollback** → **Run workflow**
2. Select the **environment** (`dev`, `staging`, `prod`)
3. Select the **service** (`frontend`, `backend`, or `both`)
4. Type `rollback` in the confirmation field
5. Click **Run workflow**

The workflow will:
- Look up the current task definition revision
- Find the previous revision (N-1)
- Show the image diff (current vs rollback target)
- Update the ECS service to use the previous revision
- Wait for the service to stabilize

### What Happens

```
Current:  docteams-prod-frontend:12  →  image:abc123
Rollback: docteams-prod-frontend:11  →  image:def456

ECS update-service → rolling deployment → old tasks drain → new tasks healthy
```

ECS performs a rolling update: new tasks (running the old image) start while current tasks drain. The service's deployment configuration (`minimumHealthyPercent: 100`, `maximumPercent: 200`) ensures zero downtime.

## Manual Rollback via AWS CLI

If GitHub Actions is unavailable, use the AWS CLI directly:

```bash
# 1. Find current task definition
aws ecs describe-services \
  --cluster docteams-prod \
  --services docteams-prod-backend \
  --query 'services[0].taskDefinition' \
  --output text
# → arn:aws:ecs:us-east-1:123456789012:task-definition/docteams-prod-backend:15

# 2. Verify previous revision exists
aws ecs describe-task-definition \
  --task-definition docteams-prod-backend:14 \
  --query 'taskDefinition.containerDefinitions[0].image' \
  --output text
# → 123456789012.dkr.ecr.us-east-1.amazonaws.com/docteams-prod-backend:abc123

# 3. Rollback
aws ecs update-service \
  --cluster docteams-prod \
  --service docteams-prod-backend \
  --task-definition docteams-prod-backend:14

# 4. Wait for stability
aws ecs wait services-stable \
  --cluster docteams-prod \
  --services docteams-prod-backend
```

## Rolling Back Multiple Revisions

The automated workflow rolls back exactly one revision (N → N-1). To roll back further:

```bash
# Roll back to a specific revision
aws ecs update-service \
  --cluster docteams-prod \
  --service docteams-prod-backend \
  --task-definition docteams-prod-backend:10
```

Or run the rollback workflow multiple times (each run goes back one more revision).

## ECS Circuit Breaker (Automatic Rollback)

ECS services are configured with deployment circuit breaker (`rollback: true`). If a deployment fails (tasks can't reach healthy state), ECS automatically rolls back to the last stable deployment without manual intervention.

The circuit breaker triggers when:
- Tasks fail health checks repeatedly
- Tasks crash during startup
- Container image pull fails

You can check if a circuit breaker rollback occurred:

```bash
aws ecs describe-services \
  --cluster docteams-prod \
  --services docteams-prod-backend \
  --query 'services[0].deployments'
```

## Limitations

- **Task definition history**: ECS keeps all revisions unless explicitly deregistered. If someone deregistered old revisions, rollback to those revisions will fail.
- **Database migrations**: Rollback only affects the application code (Docker image). If a deployment included database migrations, rolling back the application may cause issues if the new schema is incompatible with the old code. Always ensure migrations are backward-compatible.
- **Environment variables**: If a deployment changed environment variables or secrets in the task definition, rolling back restores the previous environment variables too.

## Post-Rollback Checklist

1. Verify service health via smoke tests (the workflow runs these automatically)
2. Check application logs for errors: `aws logs tail /ecs/docteams-prod-backend --follow`
3. Notify the team about the rollback and the reason
4. Create a ticket to investigate the root cause
5. Fix the issue and deploy a new version (don't leave the rollback as permanent state)
