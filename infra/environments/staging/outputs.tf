# VPC outputs
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

# Security group outputs
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

# ECR outputs
output "frontend_ecr_url" {
  description = "URL of the frontend ECR repository"
  value       = module.ecr.frontend_repository_url
}

output "backend_ecr_url" {
  description = "URL of the backend ECR repository"
  value       = module.ecr.backend_repository_url
}

# ALB outputs
output "public_alb_dns_name" {
  description = "DNS name of the public ALB"
  value       = module.alb.public_alb_dns_name
}

output "internal_alb_dns_name" {
  description = "DNS name of the internal ALB"
  value       = module.alb.internal_alb_dns_name
}

# ECS outputs
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

# S3 outputs
output "s3_bucket_name" {
  description = "Name of the S3 bucket"
  value       = module.s3.bucket_name
}

# DNS outputs
output "app_url" {
  description = "Application URL (DNS or ALB)"
  value       = var.create_dns ? "https://${module.dns.app_fqdn}" : "http://${module.alb.public_alb_dns_name}"
}
