# -----------------------------------------------------------------------------
# Secrets Manager — application secrets with placeholder values
# -----------------------------------------------------------------------------
# Secrets are created with placeholder values. Operators must update them
# manually after initial creation. The ignore_changes lifecycle rule prevents
# Terraform from reverting manually-set values on subsequent applies.
# -----------------------------------------------------------------------------

locals {
  secrets = {
    "database-url"           = "CHANGE_ME_database_url"
    "database-migration-url" = "CHANGE_ME_database_migration_url"
    "clerk-secret-key"       = "CHANGE_ME_clerk_secret_key"
    "clerk-webhook-secret"   = "CHANGE_ME_clerk_webhook_secret"
    "clerk-publishable-key"  = "CHANGE_ME_clerk_publishable_key"
    "internal-api-key"       = "CHANGE_ME_internal_api_key"
  }
}

resource "aws_secretsmanager_secret" "this" {
  for_each = local.secrets

  name                    = "${var.project}/${var.environment}/${each.key}"
  recovery_window_in_days = var.recovery_window_in_days
}

resource "aws_secretsmanager_secret_version" "this" {
  for_each = local.secrets

  secret_id     = aws_secretsmanager_secret.this[each.key].id
  secret_string = each.value

  lifecycle {
    ignore_changes = [secret_string]
  }
}
