output "public_alb_sg_id" {
  description = "Security group ID for the public ALB"
  value       = aws_security_group.public_alb.id
}

output "internal_alb_sg_id" {
  description = "Security group ID for the internal ALB"
  value       = aws_security_group.internal_alb.id
}

output "frontend_sg_id" {
  description = "Security group ID for the frontend ECS tasks"
  value       = aws_security_group.frontend.id
}

output "backend_sg_id" {
  description = "Security group ID for the backend ECS tasks"
  value       = aws_security_group.backend.id
}

output "gateway_sg_id" {
  description = "Security group ID for the gateway ECS tasks"
  value       = aws_security_group.gateway.id
}

output "portal_sg_id" {
  description = "Security group ID for the portal ECS tasks"
  value       = aws_security_group.portal.id
}

output "keycloak_sg_id" {
  description = "Security group ID for the Keycloak ECS tasks"
  value       = aws_security_group.keycloak.id
}

output "rds_sg_id" {
  description = "Security group ID for RDS PostgreSQL"
  value       = aws_security_group.rds.id
}

output "redis_sg_id" {
  description = "Security group ID for ElastiCache Redis"
  value       = aws_security_group.redis.id
}
