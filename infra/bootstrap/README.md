# Terraform State Bootstrap

This directory contains the Terraform configuration for bootstrapping the remote
state backend (S3 bucket + DynamoDB lock table). It uses **local state** because
it cannot reference itself.

## Prerequisites

1. AWS CLI configured with credentials for the target account
2. Correct region set (default: `af-south-1` / Cape Town)
3. Terraform >= 1.9 installed

## First-Time Setup

```bash
cd infra/bootstrap
terraform init
terraform plan
terraform apply
```

This creates:
- **S3 bucket**: `heykazi-terraform-state` with versioning and SSE-KMS encryption
- **DynamoDB table**: `heykazi-terraform-locks` with `LockID` partition key

## State Migration (from docteams-terraform-state)

If migrating from the previous `docteams-terraform-state` bucket:

1. Copy existing state files to the new bucket:
   ```bash
   aws s3 sync s3://docteams-terraform-state s3://heykazi-terraform-state
   ```

2. Update backend configuration in `infra/providers.tf` (already done in this repo).

3. Re-initialize each environment:
   ```bash
   cd infra
   terraform init -backend-config="key=staging/terraform.tfstate" -migrate-state
   terraform init -backend-config="key=production/terraform.tfstate" -migrate-state
   ```

4. Verify state is intact:
   ```bash
   terraform plan -var-file=environments/staging.tfvars
   # Should show no changes if migration was successful
   ```

## Verification

```bash
# Check bucket exists and has versioning
aws s3api get-bucket-versioning --bucket heykazi-terraform-state

# Check DynamoDB table exists
aws dynamodb describe-table --table-name heykazi-terraform-locks --query 'Table.TableStatus'
```

## Important Notes

- **Never delete** the bootstrap state files (`terraform.tfstate` in this directory)
- The S3 bucket has `prevent_destroy = true` as a safety measure
- Bootstrap state is local — back it up or store securely
