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

output "clerk_secret_key_arn" {
  description = "ARN of the Clerk secret key"
  value       = aws_secretsmanager_secret.this["clerk-secret-key"].arn
}

output "clerk_webhook_secret_arn" {
  description = "ARN of the Clerk webhook signing secret"
  value       = aws_secretsmanager_secret.this["clerk-webhook-secret"].arn
}

output "clerk_publishable_key_arn" {
  description = "ARN of the Clerk publishable key"
  value       = aws_secretsmanager_secret.this["clerk-publishable-key"].arn
}

output "internal_api_key_arn" {
  description = "ARN of the internal API key"
  value       = aws_secretsmanager_secret.this["internal-api-key"].arn
}
