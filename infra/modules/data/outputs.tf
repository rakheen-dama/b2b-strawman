output "rds_endpoint" {
  description = "RDS PostgreSQL endpoint address"
  value       = aws_db_instance.main.address
}

output "rds_port" {
  description = "RDS PostgreSQL port"
  value       = aws_db_instance.main.port
}

output "rds_master_credentials_secret_arn" {
  description = "ARN of the RDS master credentials secret in Secrets Manager"
  value       = aws_db_instance.main.master_user_secret[0].secret_arn
}

output "rds_database_name" {
  description = "Name of the application database"
  value       = aws_db_instance.main.db_name
}

output "rds_instance_identifier" {
  description = "Identifier of the RDS instance"
  value       = aws_db_instance.main.identifier
}

# -----------------------------------------------------------------------------
# Redis
# -----------------------------------------------------------------------------

output "redis_endpoint" {
  description = "ElastiCache Redis primary endpoint address"
  value       = var.create_redis ? aws_elasticache_replication_group.main[0].primary_endpoint_address : null
}

output "redis_port" {
  description = "ElastiCache Redis port"
  value       = var.create_redis ? aws_elasticache_replication_group.main[0].port : null
}

output "redis_auth_token_secret_arn" {
  description = "ARN of the Redis auth token secret in Secrets Manager"
  value       = var.redis_auth_token_secret_arn
}
