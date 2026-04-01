# -----------------------------------------------------------------------------
# Secrets Manager — application secrets with placeholder values
# -----------------------------------------------------------------------------
# Secrets are created with placeholder values. Operators must update them
# manually after initial creation. The ignore_changes lifecycle rule prevents
# Terraform from reverting manually-set values on subsequent applies.
# -----------------------------------------------------------------------------

locals {
  secrets = {
    "database-url"               = "CHANGE_ME_database_url"
    "database-migration-url"     = "CHANGE_ME_database_migration_url"
    "internal-api-key"           = "CHANGE_ME_internal_api_key"
    "keycloak-client-id"         = "CHANGE_ME_keycloak_client_id"
    "keycloak-client-secret"     = "CHANGE_ME_keycloak_client_secret"
    "keycloak-admin-username"    = "CHANGE_ME_keycloak_admin_username"
    "keycloak-admin-password"    = "CHANGE_ME_keycloak_admin_password"
    "portal-jwt-secret"          = "CHANGE_ME_portal_jwt_secret"
    "portal-magic-link-secret"   = "CHANGE_ME_portal_magic_link_secret"
    "integration-encryption-key" = "CHANGE_ME_integration_encryption_key"
    "smtp-username"              = "CHANGE_ME_smtp_username"
    "smtp-password"              = "CHANGE_ME_smtp_password"
    "email-unsubscribe-secret"   = "CHANGE_ME_email_unsubscribe_secret"
    "redis-auth-token"           = "CHANGE_ME_redis_auth_token"
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
