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
