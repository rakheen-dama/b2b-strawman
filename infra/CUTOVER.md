# Production Cutover Checklist

Use this checklist when going live for the first time or after a major infrastructure change. Each item must be verified by a human — do not skip items.

See [RUNBOOK.md](./RUNBOOK.md) for detailed operational procedures referenced below.

---

## Pre-Cutover

- [ ] Terraform apply to production succeeds without errors
- [ ] All AWS Secrets Manager values updated from `CHANGE_ME_*` placeholders to real values
- [ ] Keycloak realm imported and custom theme verified

## Service Health

- [ ] All 5 ECS services healthy and passing ALB health checks
- [ ] `https://app.heykazi.com` loads the frontend
- [ ] `https://auth.heykazi.com` loads Keycloak login with custom theme
- [ ] `https://portal.heykazi.com` loads the portal login

## Functional Verification

- [ ] Access request flow works: submit request, receive OTP email, verify
- [ ] Platform admin can approve access request and trigger org provisioning
- [ ] Owner can login via Keycloak invite link, reach dashboard
- [ ] Create project, log time, create invoice — basic smoke test

## Infrastructure Verification

- [ ] CloudWatch alarms configured and a test alarm fires successfully
- [ ] DNS propagation verified from external network (`dig app.heykazi.com`)
- [ ] RDS automated backups visible in AWS Console
- [ ] Rollback procedure tested: deploy old image, verify service recovers

---

## Verification Commands

```bash
# Run automated smoke tests
./infra/scripts/smoke-test.sh --env production

# Check all ECS services
for svc in frontend backend gateway portal keycloak; do
  aws ecs describe-services \
    --cluster kazi-production \
    --services "kazi-production-${svc}" \
    --query "services[0].{service:serviceName,status:status,running:runningCount,desired:desiredCount}" \
    --output table
done

# Check CloudWatch alarms
aws cloudwatch describe-alarms \
  --alarm-name-prefix "production-" \
  --query 'MetricAlarms[].{name:AlarmName,state:StateValue}' \
  --output table

# Verify DNS
dig app.heykazi.com +short
dig portal.heykazi.com +short
dig auth.heykazi.com +short

# Verify RDS backups
aws rds describe-db-instances \
  --query 'DBInstances[?contains(DBInstanceIdentifier, `kazi-production`)].{id:DBInstanceIdentifier,backupRetention:BackupRetentionPeriod,latestRestore:LatestRestorableTime}' \
  --output table
```
