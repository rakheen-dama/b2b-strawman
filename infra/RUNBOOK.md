# Operational Runbook

This runbook covers day-to-day operations for the Kazi platform running on AWS (af-south-1). It assumes intelligence but not deep cloud expertise — commands are copy-pasteable and each section can be read independently.

**Environments**: staging (`kazi-staging`), production (`kazi-production`)
**Domain**: `heykazi.com`
**Region**: `af-south-1` (Cape Town)

---

## Table of Contents

1. [First-Time Setup](#1-first-time-setup)
2. [Deploying a New Version](#2-deploying-a-new-version)
3. [Rollback Procedure](#3-rollback-procedure)
4. [Provisioning a New Tenant](#4-provisioning-a-new-tenant)
5. [Database Operations](#5-database-operations)
6. [Viewing Logs](#6-viewing-logs)
7. [Responding to Alerts](#7-responding-to-alerts)
8. [Keycloak Operations](#8-keycloak-operations)
9. [Cost Monitoring](#9-cost-monitoring)
10. [Disaster Recovery](#10-disaster-recovery)
11. [Production Cutover Checklist](#11-production-cutover-checklist)

---

## 1. First-Time Setup

### Prerequisites

1. **AWS account** with `af-south-1` region enabled (Cape Town is an opt-in region)
2. **IAM user or role** with admin permissions (or scoped to ECS, RDS, S3, Route 53, ACM, Secrets Manager, CloudWatch, ECR, ElastiCache, VPC)
3. **AWS CLI v2** installed and configured:
   ```bash
   aws configure
   # Region: af-south-1
   # Output: json
   ```
4. **Terraform >= 1.5** installed
5. **Route 53 hosted zone** for `heykazi.com` (created manually or by Terraform)
6. **Domain NS delegation**: at your registrar, point `heykazi.com` NS records to the Route 53 hosted zone nameservers

### Step 1 — Bootstrap Terraform State Backend

This is a one-time operation that creates the S3 bucket and DynamoDB table for Terraform state.

```bash
cd infra/bootstrap
terraform init
terraform plan
terraform apply
```

This creates:
- S3 bucket: `heykazi-terraform-state` (versioned, encrypted)
- DynamoDB table: `heykazi-terraform-locks`

Verify:
```bash
aws s3api get-bucket-versioning --bucket heykazi-terraform-state
# Expected: {"Status": "Enabled"}

aws dynamodb describe-table --table-name heykazi-terraform-locks --query 'Table.TableStatus'
# Expected: "ACTIVE"
```

> **Warning**: Never delete the bootstrap state files. The S3 bucket has `prevent_destroy = true`.

### Step 2 — Deploy Staging

```bash
cd infra/
terraform init -backend-config="key=staging/terraform.tfstate"
terraform plan -var-file=environments/staging.tfvars
# Review the plan output carefully
terraform apply -var-file=environments/staging.tfvars
```

### Step 3 — Deploy Production

```bash
cd infra/
terraform init -backend-config="key=production/terraform.tfstate"
terraform plan -var-file=environments/production.tfvars
# Review the plan output carefully
terraform apply -var-file=environments/production.tfvars
```

### Step 4 — Update Secret Values

Terraform creates secrets in AWS Secrets Manager with placeholder values (`CHANGE_ME_*`). Update each one manually:

```bash
# List all secrets for an environment
aws secretsmanager list-secrets \
  --filter Key=name,Values=kazi/production \
  --query 'SecretList[].Name' --output table

# Update a specific secret
aws secretsmanager put-secret-value \
  --secret-id kazi/production/database-url \
  --secret-string 'jdbc:postgresql://HOST:5432/app?currentSchema=public'
```

Secrets to update (for each environment):

| Secret | Description |
|--------|------------|
| `kazi/{env}/database-url` | JDBC URL for backend |
| `kazi/{env}/database-migration-url` | JDBC URL for Flyway migrations |
| `kazi/{env}/internal-api-key` | Shared key for service-to-service calls |
| `kazi/{env}/keycloak-client-id` | OAuth2 client ID |
| `kazi/{env}/keycloak-client-secret` | OAuth2 client secret |
| `kazi/{env}/keycloak-admin-username` | Keycloak admin API user |
| `kazi/{env}/keycloak-admin-password` | Keycloak admin API password |
| `kazi/{env}/portal-jwt-secret` | JWT signing secret for portal |
| `kazi/{env}/portal-magic-link-secret` | Magic link signing secret |
| `kazi/{env}/integration-encryption-key` | Encryption key for integrations |
| `kazi/{env}/smtp-username` | SMTP username (email sending) |
| `kazi/{env}/smtp-password` | SMTP password |
| `kazi/{env}/email-unsubscribe-secret` | Unsubscribe link signing |
| `kazi/{env}/redis-auth-token` | Redis AUTH token |
| `kazi/{env}/keycloak-db-username` | Keycloak database user |
| `kazi/{env}/keycloak-db-password` | Keycloak database password |
| `kazi/{env}/gateway-db-username` | Gateway database user |
| `kazi/{env}/gateway-db-password` | Gateway database password |

### Step 5 — Keycloak Configuration

After Keycloak starts for the first time:

1. Login to the admin console at `https://auth.heykazi.com` (staging: `https://staging-auth.heykazi.com`)
2. Import the `kazi` realm (see [Keycloak Operations](#8-keycloak-operations) for import procedure)
3. Verify the custom theme is active on the login page
4. Create the OAuth2 client for backend/gateway and update the corresponding secrets

### Step 6 — Verify DNS Propagation

```bash
dig app.heykazi.com +short
dig portal.heykazi.com +short
dig auth.heykazi.com +short
# All should resolve to the ALB's IP addresses
```

### Step 7 — First Tenant Provisioning

See [Provisioning a New Tenant](#4-provisioning-a-new-tenant) for the walkthrough.

---

## 2. Deploying a New Version

### How Deployments Work

The CI/CD pipeline automates deployments:

1. **Merge to `main`** triggers the staging deploy workflow (`.github/workflows/deploy-staging.yml`)
2. The workflow detects which of the 5 services changed, builds Docker images, pushes to ECR, and deploys to the `kazi-staging` ECS cluster
3. After deployment, automated smoke tests run against staging URLs
4. **Production deployment** is manual: trigger `.github/workflows/deploy-prod.yml` via GitHub Actions `workflow_dispatch`

### Deploying to Staging (Automatic)

Simply merge your PR to `main`. The workflow will:

1. Detect changed services (using `dorny/paths-filter`)
2. Build Docker images for changed services, push to ECR with tags `{SHA}` and `staging`
3. Deploy all 5 services to `kazi-staging` (changed services get the new SHA image; unchanged services keep the `staging` tag)
4. Run smoke tests

Monitor the workflow in GitHub Actions. If smoke tests fail, check the workflow logs.

### Deploying to Production (Manual)

1. Go to **GitHub Actions** > **Deploy Production** workflow
2. Click **Run workflow**
3. Type `deploy-prod` in the confirmation input
4. The workflow will:
   - Re-tag 4 service images (backend, gateway, portal, keycloak) from `:staging` to `:production` — no rebuild
   - Rebuild frontend with production build args (`NEXT_PUBLIC_GATEWAY_URL=https://app.heykazi.com/bff`)
   - Deploy all 5 services to `kazi-production` using SHA-pinned images
   - A GitHub environment protection gate on `production` may require approval
5. Run smoke tests after deployment succeeds

### Verifying a Deployment

Run the smoke test script:

```bash
./infra/scripts/smoke-test.sh --env staging
./infra/scripts/smoke-test.sh --env production
```

Or check individual services via AWS CLI:

```bash
aws ecs describe-services \
  --cluster kazi-production \
  --services kazi-production-backend \
  --query 'services[0].{status:status,running:runningCount,desired:desiredCount,taskDef:taskDefinition}'
```

Check CloudWatch logs for errors after deployment:

```bash
aws logs tail /kazi/production/backend --since 15m --follow
```

### What to Do If Deployment Fails

1. Check the GitHub Actions workflow logs for the specific failure step
2. If ECS fails to stabilize (service keeps restarting):
   - Check CloudWatch logs for the failing service
   - Check ECS events: `aws ecs describe-services --cluster kazi-production --services kazi-production-backend --query 'services[0].events[:5]'`
3. If smoke tests fail after deployment:
   - Verify ALB target group health in the AWS Console
   - Check if the new container is passing health checks
4. Roll back if needed — see [Rollback Procedure](#3-rollback-procedure)

---

## 3. Rollback Procedure

### Using the Rollback Workflow (Recommended)

1. Go to **GitHub Actions** > **Rollback** workflow (`.github/workflows/rollback.yml`)
2. Click **Run workflow**
3. Fill in inputs:
   - `environment`: `staging` or `production`
   - `service`: `frontend`, `backend`, `gateway`, `portal`, `keycloak`, or `all`
   - `confirm`: type `rollback`
4. The workflow finds the current task definition revision, decrements by 1, and updates the ECS service with the previous task definition
5. Waits for service stability, then runs smoke tests

### Manual Rollback via AWS CLI

If the rollback workflow is unavailable, roll back manually:

```bash
# 1. Get the current task definition ARN
CURRENT_TD=$(aws ecs describe-services \
  --cluster kazi-production \
  --services kazi-production-backend \
  --query 'services[0].taskDefinition' --output text)

echo "Current: $CURRENT_TD"
# Example: arn:aws:ecs:af-south-1:123456:task-definition/kazi-production-backend:42

# 2. Extract the revision number and compute previous
FAMILY=$(echo "$CURRENT_TD" | cut -d: -f6 | cut -d/ -f2)
CURRENT_REV=$(echo "$CURRENT_TD" | cut -d: -f7)
PREV_REV=$((CURRENT_REV - 1))

echo "Rolling back $FAMILY from revision $CURRENT_REV to $PREV_REV"

# 3. Update the service to use the previous revision
aws ecs update-service \
  --cluster kazi-production \
  --service kazi-production-backend \
  --task-definition "${FAMILY}:${PREV_REV}"

# 4. Wait for service to stabilize
aws ecs wait services-stable \
  --cluster kazi-production \
  --services kazi-production-backend

# 5. Verify
aws ecs describe-services \
  --cluster kazi-production \
  --services kazi-production-backend \
  --query 'services[0].{status:status,running:runningCount,desired:desiredCount,taskDef:taskDefinition}'
```

### Manual Rollback via AWS Console

1. Go to **ECS** > **Clusters** > `kazi-production`
2. Click the service (e.g., `kazi-production-backend`)
3. Click **Update service**
4. Under **Task definition**, select the previous revision from the dropdown
5. Click **Update** and wait for the deployment to complete

### What Rollback Does NOT Do

- **No database schema rollback**: Flyway migrations are forward-only. If a deployment included a schema migration, rolling back the service will revert the application code but the database schema stays at the new version. In most cases this is fine because migrations are additive (add columns/tables, not remove). If a migration is incompatible with old code, you must fix-forward.
- **No secret rollback**: If you changed secret values, revert them manually in Secrets Manager.

---

## 4. Provisioning a New Tenant

### What Happens Automatically

When a platform admin approves an access request, `TenantProvisioningService` executes:

1. **Keycloak organization** — creates a new Keycloak organization for the tenant
2. **Database schema** — creates a new PostgreSQL schema (named after the tenant slug)
3. **Flyway migrations** — runs all migrations against the new schema
4. **Seed packs** — seeds default data (document templates, checklist items, etc.)

### Verifying a New Tenant

#### Check the Database Schema

```bash
# Connect to RDS (see Database Operations for SSM tunnel setup)
psql -h <rds-endpoint> -U postgres -d app

# List all schemas (each tenant has one)
\dn

# Check Flyway status for a specific tenant schema
SELECT * FROM <tenant_schema>.flyway_schema_history ORDER BY installed_rank DESC LIMIT 5;
```

#### Check Keycloak Organization

1. Login to Keycloak admin console at `https://auth.heykazi.com`
2. Navigate to **Organizations**
3. Verify the new organization exists with the correct name and domain

#### Test Login

1. Use the invite link generated during provisioning
2. Verify the user can set a password and reach the dashboard
3. Confirm the tenant slug appears correctly in the URL

### Troubleshooting Failed Provisioning

If provisioning fails partway through:

1. **Check backend logs** for the error:
   ```bash
   aws logs tail /kazi/production/backend --since 30m \
     --filter-pattern '"TenantProvisioningService"'
   ```

2. **Partial schema creation**: If the schema was created but Flyway failed, you may need to drop and recreate:
   ```sql
   -- DANGER: Only do this for a brand-new tenant that has no data
   DROP SCHEMA IF EXISTS <tenant_schema> CASCADE;
   ```
   Then retry provisioning.

3. **Keycloak org created but schema failed**: Delete the Keycloak organization manually, then retry.

4. **Check the access request status** in the platform admin UI — it may be stuck in a transitional state.

---

## 5. Database Operations

### Connecting to RDS

RDS instances are in private subnets and not directly accessible from the internet. Use SSM Session Manager to tunnel through an ECS task or bastion.

#### Option A — SSM Port Forwarding (Recommended)

```bash
# Find the RDS endpoint
aws rds describe-db-instances \
  --query 'DBInstances[?contains(DBInstanceIdentifier, `kazi-production`)].Endpoint.Address' \
  --output text

# Start a port-forwarding session through SSM
# (Requires an EC2 instance or ECS task with SSM agent in the same VPC)
aws ssm start-session \
  --target <instance-id-or-ecs-task-id> \
  --document-name AWS-StartPortForwardingSessionToRemoteHost \
  --parameters "{\"host\":[\"<rds-endpoint>\"],\"portNumber\":[\"5432\"],\"localPortNumber\":[\"15432\"]}"

# In a separate terminal, connect via the tunnel
psql -h localhost -p 15432 -U postgres -d app
```

#### Option B — ECS Exec

```bash
# Find a running backend task
TASK_ARN=$(aws ecs list-tasks \
  --cluster kazi-production \
  --service-name kazi-production-backend \
  --query 'taskArns[0]' --output text)

# Exec into the container (requires ECS Exec enabled on the service)
aws ecs execute-command \
  --cluster kazi-production \
  --task "$TASK_ARN" \
  --container backend \
  --interactive \
  --command "/bin/sh"
```

### Checking Flyway Migration Status

```sql
-- Connect to the database, then:
-- List all tenant schemas
SELECT schema_name FROM information_schema.schemata
WHERE schema_name NOT IN ('public', 'information_schema', 'pg_catalog', 'pg_toast');

-- Check migration status for a specific tenant
SELECT installed_rank, version, description, success
FROM <tenant_schema>.flyway_schema_history
ORDER BY installed_rank DESC
LIMIT 10;

-- Find failed migrations across all tenants
DO $$
DECLARE
  s TEXT;
  cnt INTEGER;
BEGIN
  FOR s IN
    SELECT schema_name FROM information_schema.schemata
    WHERE schema_name NOT IN ('public', 'information_schema', 'pg_catalog', 'pg_toast')
  LOOP
    EXECUTE format('SELECT count(*) FROM %I.flyway_schema_history WHERE success = false', s) INTO cnt;
    IF cnt > 0 THEN RAISE NOTICE 'Schema % has % failed migrations', s, cnt; END IF;
  END LOOP;
END $$;
```

### Running Ad-Hoc Queries Against a Tenant Schema

```sql
-- Set the search path to a specific tenant schema
SET search_path TO '<tenant_schema>';

-- Now queries run against that tenant's data
SELECT count(*) FROM projects;
SELECT count(*) FROM time_entries;

-- Reset when done
RESET search_path;
```

### Checking HikariCP Pool Metrics

The backend exposes connection pool metrics via Spring Boot Actuator (internal only, not exposed through ALB):

```bash
# Via ECS Exec into a backend container
curl -s http://localhost:8080/actuator/metrics/hikaricp.connections.active | jq
curl -s http://localhost:8080/actuator/metrics/hikaricp.connections.idle | jq
curl -s http://localhost:8080/actuator/metrics/hikaricp.connections.pending | jq
curl -s http://localhost:8080/actuator/metrics/hikaricp.connections.max | jq
```

Key metrics to watch:
- `hikaricp.connections.active` — should be well below `max`
- `hikaricp.connections.pending` — should be 0 under normal load; sustained pending connections indicate pool exhaustion

---

## 6. Viewing Logs

### Log Group Naming

All logs are in CloudWatch Logs, grouped by `/{project}/{env}/{service}`:

| Service | Log Group |
|---------|-----------|
| frontend | `/kazi/{env}/frontend` |
| backend | `/kazi/{env}/backend` |
| gateway | `/kazi/{env}/gateway` |
| portal | `/kazi/{env}/portal` |
| keycloak | `/kazi/{env}/keycloak` |

### Tailing Logs from the CLI

```bash
# Follow live logs
aws logs tail /kazi/production/backend --follow

# Last 30 minutes
aws logs tail /kazi/production/backend --since 30m

# Specific time range
aws logs tail /kazi/production/backend \
  --since "2026-01-15T10:00:00Z" \
  --end "2026-01-15T11:00:00Z"
```

### CloudWatch Logs Insights Queries

Open the **CloudWatch Logs Insights** console, select the log group, and run queries:

#### Errors in the Last Hour

```
fields @timestamp, @message
| filter @message like /ERROR/
| sort @timestamp desc
| limit 50
```

#### Errors by Tenant

```
fields @timestamp, @message
| parse @message '"tenantId":"*"' as tenantId
| filter @message like /ERROR/
| stats count() by tenantId
| sort count desc
```

#### Trace a Specific Request

```
fields @timestamp, @message
| parse @message '"requestId":"*"' as requestId
| filter requestId = "<REQUEST_ID>"
| sort @timestamp asc
```

#### Slow Queries (Backend)

```
fields @timestamp, @message
| filter @message like /slow query/
| sort @timestamp desc
| limit 20
```

#### 5xx Errors by Endpoint

```
fields @timestamp, @message
| parse @message '"status":*,' as statusCode
| filter statusCode >= 500
| parse @message '"path":"*"' as path
| stats count() by path
| sort count desc
```

### Filtering by MDC Fields

Backend logs include MDC (Mapped Diagnostic Context) fields in structured JSON format:

- `tenantId` — the tenant schema name
- `userId` — Keycloak user ID
- `requestId` — unique request identifier

```
fields @timestamp, @message
| parse @message '"tenantId":"*"' as tenantId
| parse @message '"userId":"*"' as userId
| filter tenantId = "acme-corp"
| sort @timestamp desc
| limit 100
```

---

## 7. Responding to Alerts

All alarms send notifications to SNS topic `kazi-{env}-alerts`, which emails `founder@heykazi.com`.

### Alarm Reference

| Alarm | What It Means | First Steps |
|-------|--------------|-------------|
| `{env}-backend-unhealthy` | Backend containers failing ALB health checks (`/actuator/health` returning non-200) | Check backend logs for startup errors or OOM. Verify RDS connectivity. Check Secrets Manager values. |
| `{env}-gateway-unhealthy` | Gateway containers failing health checks | Check gateway logs. Verify Redis connectivity (session store). Check Keycloak issuer URI reachable. |
| `{env}-keycloak-unhealthy` | Keycloak failing `/health/ready` | Check Keycloak logs. Verify its database credentials. Check if the Keycloak database has disk space. |
| `{env}-high-target-5xx` | >10 target 5xx errors in 5 minutes | Check backend/gateway logs for exceptions. Look for a pattern (specific endpoint, tenant, or user). May indicate a code bug deployed recently. |
| `{env}-high-elb-5xx` | >10 ALB-level 5xx errors (502/503/504) in 5 minutes | 502 = backend not responding (crashed or starting). 503 = no healthy targets. 504 = timeout (backend taking too long). Check ECS service events and container health. |
| `{env}-backend-cpu-high` | Backend CPU utilization alarm | Check for hot loops or expensive request handlers. Review recent deployments. Scale out ECS tasks or optimize code. |
| `{env}-rds-cpu-high` | RDS CPU >80% for 2 consecutive periods | Identify expensive queries via `pg_stat_activity`. Check if a migration is running. Consider scaling up the instance class. |
| `{env}-rds-storage-low` | Less than 5 GB free storage | Check for bloated tables, run `VACUUM FULL` on large tables. Increase allocated storage via Terraform (`allocated_storage` variable). |
| `{env}-rds-connections-high` | More than 80 active database connections | Check HikariCP metrics (Section 5). A connection leak or too many concurrent tenants may be the cause. Restart the backend service as a short-term fix. |

### Investigation Steps (General)

1. **Check the alarm in CloudWatch** — note the timestamp and which metric triggered
2. **Tail the relevant service logs**:
   ```bash
   aws logs tail /kazi/production/<service> --since 30m
   ```
3. **Check ECS service events** for deployment or scaling issues:
   ```bash
   aws ecs describe-services \
     --cluster kazi-production \
     --services kazi-production-<service> \
     --query 'services[0].events[:10]'
   ```
4. **Check ECS task status** — is the container running, restarting, or stopped?
   ```bash
   aws ecs list-tasks --cluster kazi-production --service-name kazi-production-<service>
   aws ecs describe-tasks --cluster kazi-production --tasks <task-arn>
   ```
5. **If the service was recently deployed**, consider rolling back (see [Rollback Procedure](#3-rollback-procedure))

### Escalation Path

1. **On-call developer**: Investigate using this runbook
2. **If unresolved within 30 minutes**: Check if a rollback resolves the issue
3. **If infrastructure-level** (RDS, networking, ALB): Escalate to AWS Support if on a support plan

---

## 8. Keycloak Operations

### Admin Console Access

- Production: `https://auth.heykazi.com`
- Staging: `https://staging-auth.heykazi.com`

Login with the admin credentials stored in Secrets Manager (`kazi/{env}/keycloak-admin-username` and `kazi/{env}/keycloak-admin-password`).

### Realm Export

```bash
# Exec into the Keycloak container
TASK_ARN=$(aws ecs list-tasks \
  --cluster kazi-production \
  --service-name kazi-production-keycloak \
  --query 'taskArns[0]' --output text)

aws ecs execute-command \
  --cluster kazi-production \
  --task "$TASK_ARN" \
  --container keycloak \
  --interactive \
  --command "/bin/sh"

# Inside the container:
/opt/keycloak/bin/kc.sh export --dir /tmp/export --realm kazi
# Copy export files out (or use S3)
```

Alternatively, use the admin console: **Realm settings** > **Partial export**.

### Realm Import

For initial setup or restoring from backup:

```bash
# Inside the Keycloak container:
/opt/keycloak/bin/kc.sh import --dir /tmp/import --override true
```

> **Note**: Importing overwrites existing realm configuration. Use with caution in production.

### Adding Protocol Mappers

Protocol mappers control what claims appear in JWT tokens. To add a mapper:

1. Admin console > **Clients** > select client > **Client scopes** tab
2. Click the dedicated scope > **Mappers** tab > **Add mapper** > **By configuration**
3. Common mappers: User Attribute, Group Membership, Audience

### Theme Updates

The Keycloak image includes a custom login theme. To update it:

1. Modify theme files in the repository (under the Keycloak service's Dockerfile context)
2. Rebuild the Keycloak Docker image
3. Push to ECR and deploy:
   ```bash
   # The staging deploy workflow handles this on merge to main.
   # For production, trigger the Deploy Production workflow.
   ```

### User Management

Common operations via admin console:

- **Reset password**: Users > select user > Credentials > Reset password
- **Disable user**: Users > select user > toggle Enabled off
- **View sessions**: Users > select user > Sessions tab
- **Force logout**: Sessions > select session > Logout

---

## 9. Cost Monitoring

### Expected Monthly Cost Breakdown (Production)

| Resource | Estimated Monthly Cost | Notes |
|----------|----------------------|-------|
| ECS Fargate (5 services) | $150–300 | Depends on task sizes and scaling |
| RDS db.t4g.medium (Multi-AZ) | $70–100 | Includes Multi-AZ standby |
| ALB (public) | $20–30 | Plus data processing charges |
| ElastiCache cache.t4g.micro | $15–20 | Single node |
| Route 53 | $1–2 | Hosted zone + queries |
| S3 (documents) | $1–10 | Depends on storage volume |
| CloudWatch Logs | $5–20 | 90-day retention |
| ECR | $1–5 | Image storage |
| Secrets Manager | $5–10 | 18 secrets |
| NAT Gateway | $35–45 | Per-GB data processing |
| **Total** | **$300–540** | |

Staging costs are lower (single-AZ RDS, min 1 task, 30-day log retention). Expect roughly 40-60% of production cost.

### Cost Anomaly Indicators

Watch for these in AWS Cost Explorer:

- **NAT Gateway data transfer spike** — could indicate a misconfigured service making excessive external calls
- **RDS storage growing faster than expected** — audit log tables or unvacuumed tables
- **ECS cost spike** — auto-scaling triggered frequently (check if alarm thresholds are too sensitive)
- **CloudWatch Logs ingestion spike** — a service logging at DEBUG level in production

### Setting Up Alerts

```bash
# Create a budget alert (example: $600/month cap)
aws budgets create-budget \
  --account-id <account-id> \
  --budget '{
    "BudgetName": "kazi-monthly",
    "BudgetLimit": {"Amount": "600", "Unit": "USD"},
    "TimeUnit": "MONTHLY",
    "BudgetType": "COST"
  }' \
  --notifications-with-subscribers '[{
    "Notification": {
      "NotificationType": "ACTUAL",
      "ComparisonOperator": "GREATER_THAN",
      "Threshold": 80,
      "ThresholdType": "PERCENTAGE"
    },
    "Subscribers": [{
      "SubscriptionType": "EMAIL",
      "Address": "founder@heykazi.com"
    }]
  }]'
```

---

## 10. Disaster Recovery

### RDS Point-in-Time Recovery

RDS automated backups allow restoring to any point within the retention window:
- **Staging**: 1-day retention
- **Production**: 7-day retention, Multi-AZ

```bash
# Check available restore window
aws rds describe-db-instances \
  --query 'DBInstances[?contains(DBInstanceIdentifier, `kazi-production`)].{id:DBInstanceIdentifier,earliest:EarliestRestorableTime,latest:LatestRestorableTime}'

# Restore to a specific point in time (creates a NEW instance)
aws rds restore-db-instance-to-point-in-time \
  --source-db-instance-identifier kazi-production-db \
  --target-db-instance-identifier kazi-production-db-restored \
  --restore-time "2026-01-15T10:30:00Z" \
  --db-subnet-group-name kazi-production-db-subnet \
  --vpc-security-group-ids <sg-id>
```

After restoration:
1. Verify the restored instance has the expected data
2. Update the `kazi/production/database-url` and `kazi/production/database-migration-url` secrets to point to the new endpoint
3. Restart the backend service: `aws ecs update-service --cluster kazi-production --service kazi-production-backend --force-new-deployment`
4. Update Terraform state to reflect the new instance, or rename the restored instance

> **Important**: Point-in-time recovery creates a new RDS instance. You must update connection strings and potentially Terraform state.

### S3 Versioning Recovery

The documents bucket (`kazi-{env}-documents`) has versioning enabled. To recover a deleted or overwritten file:

```bash
# List object versions
aws s3api list-object-versions \
  --bucket kazi-production-documents \
  --prefix "path/to/file"

# Restore a specific version by copying it back
aws s3api copy-object \
  --bucket kazi-production-documents \
  --copy-source "kazi-production-documents/path/to/file?versionId=<version-id>" \
  --key "path/to/file"

# If the object was deleted (has a delete marker), remove the delete marker
aws s3api delete-object \
  --bucket kazi-production-documents \
  --key "path/to/file" \
  --version-id <delete-marker-version-id>
```

### Keycloak Realm Recovery

If the Keycloak realm is corrupted or accidentally modified:

1. **From a realm export file** (if you have one):
   - Exec into the Keycloak container
   - Import the realm: `/opt/keycloak/bin/kc.sh import --dir /tmp/import --override true`

2. **From the Keycloak database** (RDS):
   - Keycloak stores all realm data in its database
   - Use RDS point-in-time recovery to restore the Keycloak database to before the issue
   - Restart the Keycloak service

3. **Prevention**: Schedule regular realm exports and store them in S3:
   ```bash
   # Add this as a periodic operation (weekly recommended)
   # Export realm, upload to S3
   aws s3 cp /tmp/export/kazi-realm.json \
     s3://kazi-production-documents/backups/keycloak/kazi-realm-$(date +%Y%m%d).json
   ```

---

## 11. Production Cutover Checklist

Use this checklist when going live for the first time or after a major infrastructure change. Each item must be verified by a human — do not skip items.

- [ ] Terraform apply to production succeeds without errors
- [ ] All 5 ECS services healthy and passing ALB health checks
- [ ] `https://app.heykazi.com` loads the frontend
- [ ] `https://auth.heykazi.com` loads Keycloak login with custom theme
- [ ] `https://portal.heykazi.com` loads the portal login
- [ ] Access request flow works: submit request, receive OTP email, verify
- [ ] Platform admin can approve access request and trigger org provisioning
- [ ] Owner can login via Keycloak invite link, reach dashboard
- [ ] Create project, log time, create invoice — basic smoke test
- [ ] CloudWatch alarms configured and a test alarm fires successfully
- [ ] DNS propagation verified from external network (`dig app.heykazi.com`)
- [ ] RDS automated backups visible in AWS Console
- [ ] Rollback procedure tested: deploy old image, verify service recovers

### How to Verify Each Item

**ECS services healthy**:
```bash
./infra/scripts/smoke-test.sh --env production

# Or check ALB target group health in AWS Console:
# EC2 > Target Groups > filter by "kazi-production"
# All targets should show "healthy"
```

**CloudWatch alarms configured**:
```bash
aws cloudwatch describe-alarms \
  --alarm-name-prefix "production-" \
  --query 'MetricAlarms[].{name:AlarmName,state:StateValue}'
```

**DNS propagation**:
```bash
# From your local machine (or any external network)
dig app.heykazi.com +short
dig portal.heykazi.com +short
dig auth.heykazi.com +short
# Should return ALB IP addresses, not NXDOMAIN
```

**RDS automated backups**:
```bash
aws rds describe-db-instances \
  --query 'DBInstances[?contains(DBInstanceIdentifier, `kazi-production`)].{id:DBInstanceIdentifier,backupRetention:BackupRetentionPeriod,latestRestore:LatestRestorableTime}'
```

**Rollback test**:
1. Note the current task definition revision
2. Deploy a known-good older image using the rollback workflow
3. Verify the service recovers and smoke tests pass
4. Re-deploy the latest version
