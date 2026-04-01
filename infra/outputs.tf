# -----------------------------------------------------------------------------
# VPC
# -----------------------------------------------------------------------------

output "vpc_id" {
  description = "ID of the VPC"
  value       = module.vpc.vpc_id
}

output "public_subnet_ids" {
  description = "IDs of the public subnets"
  value       = module.vpc.public_subnet_ids
}

output "private_subnet_ids" {
  description = "IDs of the private subnets"
  value       = module.vpc.private_subnet_ids
}

output "availability_zones" {
  description = "Availability zones used"
  value       = module.vpc.availability_zones
}

# -----------------------------------------------------------------------------
# Security Groups
# -----------------------------------------------------------------------------

output "public_alb_sg_id" {
  description = "Security group ID for the public ALB"
  value       = module.security_groups.public_alb_sg_id
}

output "internal_alb_sg_id" {
  description = "Security group ID for the internal ALB"
  value       = module.security_groups.internal_alb_sg_id
}

output "frontend_sg_id" {
  description = "Security group ID for frontend ECS tasks"
  value       = module.security_groups.frontend_sg_id
}

output "backend_sg_id" {
  description = "Security group ID for backend ECS tasks"
  value       = module.security_groups.backend_sg_id
}

# -----------------------------------------------------------------------------
# ECR
# -----------------------------------------------------------------------------

output "ecr_repository_urls" {
  description = "Map of service name to ECR repository URL"
  value       = module.ecr.ecr_repository_urls
}

# -----------------------------------------------------------------------------
# ALB
# -----------------------------------------------------------------------------

output "public_alb_dns_name" {
  description = "DNS name of the public ALB"
  value       = module.alb.public_alb_dns_name
}

output "internal_alb_dns_name" {
  description = "DNS name of the internal ALB"
  value       = module.alb.internal_alb_dns_name
}

# -----------------------------------------------------------------------------
# ECS
# -----------------------------------------------------------------------------

output "ecs_cluster_name" {
  description = "Name of the ECS cluster"
  value       = module.ecs.cluster_name
}

output "frontend_service_name" {
  description = "Name of the frontend ECS service"
  value       = module.ecs.frontend_service_name
}

output "backend_service_name" {
  description = "Name of the backend ECS service"
  value       = module.ecs.backend_service_name
}

# -----------------------------------------------------------------------------
# S3
# -----------------------------------------------------------------------------

output "s3_bucket_name" {
  description = "Name of the S3 bucket"
  value       = module.s3.bucket_name
}

# -----------------------------------------------------------------------------
# DNS
# -----------------------------------------------------------------------------

output "app_url" {
  description = "Application URL (DNS or ALB)"
  value       = var.create_dns ? "https://${module.dns.app_fqdn}" : "http://${module.alb.public_alb_dns_name}"
}

# -----------------------------------------------------------------------------
# RDS
# -----------------------------------------------------------------------------

output "rds_endpoint" {
  description = "RDS PostgreSQL endpoint"
  value       = module.data.rds_endpoint
}

output "rds_port" {
  description = "RDS PostgreSQL port"
  value       = module.data.rds_port
}

output "rds_master_credentials_secret_arn" {
  description = "ARN of the RDS master credentials secret in Secrets Manager"
  value       = module.data.rds_master_credentials_secret_arn
}

output "rds_instance_identifier" {
  description = "Identifier of the RDS instance"
  value       = module.data.rds_instance_identifier
}

# -----------------------------------------------------------------------------
# Redis
# -----------------------------------------------------------------------------

output "redis_endpoint" {
  description = "ElastiCache Redis primary endpoint"
  value       = module.data.redis_endpoint
}

output "redis_port" {
  description = "ElastiCache Redis port"
  value       = module.data.redis_port
}

output "redis_auth_token_secret_arn" {
  description = "ARN of the Redis auth token secret in Secrets Manager"
  value       = module.data.redis_auth_token_secret_arn
}
