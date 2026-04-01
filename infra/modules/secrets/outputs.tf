output "secret_arns" {
  description = "Map of secret name to ARN"
  value       = { for k, v in aws_secretsmanager_secret.this : k => v.arn }
}

output "database_url_arn" {
  description = "ARN of the database URL secret"
  value       = aws_secretsmanager_secret.this["database-url"].arn
}

output "database_migration_url_arn" {
  description = "ARN of the database migration URL secret"
  value       = aws_secretsmanager_secret.this["database-migration-url"].arn
}

output "internal_api_key_arn" {
  description = "ARN of the internal API key"
  value       = aws_secretsmanager_secret.this["internal-api-key"].arn
}

output "keycloak_client_id_arn" {
  description = "ARN of the Keycloak client ID secret"
  value       = aws_secretsmanager_secret.this["keycloak-client-id"].arn
}

output "keycloak_client_secret_arn" {
  description = "ARN of the Keycloak client secret"
  value       = aws_secretsmanager_secret.this["keycloak-client-secret"].arn
}

output "keycloak_admin_username_arn" {
  description = "ARN of the Keycloak admin username secret"
  value       = aws_secretsmanager_secret.this["keycloak-admin-username"].arn
}

output "keycloak_admin_password_arn" {
  description = "ARN of the Keycloak admin password secret"
  value       = aws_secretsmanager_secret.this["keycloak-admin-password"].arn
}

output "portal_jwt_secret_arn" {
  description = "ARN of the portal JWT secret"
  value       = aws_secretsmanager_secret.this["portal-jwt-secret"].arn
}

output "portal_magic_link_secret_arn" {
  description = "ARN of the portal magic link secret"
  value       = aws_secretsmanager_secret.this["portal-magic-link-secret"].arn
}

output "integration_encryption_key_arn" {
  description = "ARN of the integration encryption key secret"
  value       = aws_secretsmanager_secret.this["integration-encryption-key"].arn
}

output "smtp_username_arn" {
  description = "ARN of the SMTP username secret"
  value       = aws_secretsmanager_secret.this["smtp-username"].arn
}

output "smtp_password_arn" {
  description = "ARN of the SMTP password secret"
  value       = aws_secretsmanager_secret.this["smtp-password"].arn
}

output "email_unsubscribe_secret_arn" {
  description = "ARN of the email unsubscribe secret"
  value       = aws_secretsmanager_secret.this["email-unsubscribe-secret"].arn
}

output "redis_auth_token_arn" {
  description = "ARN of the Redis auth token secret"
  value       = aws_secretsmanager_secret.this["redis-auth-token"].arn
}
