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
