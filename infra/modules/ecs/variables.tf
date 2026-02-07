variable "project" {
  description = "Project name"
  type        = string
}

variable "environment" {
  description = "Environment name"
  type        = string
}

variable "aws_region" {
  description = "AWS region for CloudWatch logs"
  type        = string
}

# Networking
variable "private_subnet_ids" {
  description = "Private subnet IDs for ECS tasks"
  type        = list(string)
}

variable "frontend_sg_id" {
  description = "Security group ID for frontend ECS tasks"
  type        = string
}

variable "backend_sg_id" {
  description = "Security group ID for backend ECS tasks"
  type        = string
}

# ALB Target Groups
variable "frontend_target_group_arn" {
  description = "ARN of the frontend ALB target group"
  type        = string
}

variable "backend_target_group_arn" {
  description = "ARN of the backend ALB target group (public)"
  type        = string
}

variable "backend_internal_tg_arn" {
  description = "ARN of the backend internal ALB target group"
  type        = string
}

# IAM
variable "ecs_execution_role_arn" {
  description = "ARN of the ECS task execution role"
  type        = string
}

variable "frontend_task_role_arn" {
  description = "ARN of the frontend ECS task role"
  type        = string
}

variable "backend_task_role_arn" {
  description = "ARN of the backend ECS task role"
  type        = string
}

# Container Images
variable "frontend_image" {
  description = "Full ECR image URI with tag for frontend"
  type        = string
}

variable "backend_image" {
  description = "Full ECR image URI with tag for backend"
  type        = string
}

# Monitoring
variable "frontend_log_group_name" {
  description = "CloudWatch log group name for frontend"
  type        = string
}

variable "backend_log_group_name" {
  description = "CloudWatch log group name for backend"
  type        = string
}

# Secrets (ARNs for injection into containers)
variable "database_url_secret_arn" {
  description = "ARN of the database URL secret"
  type        = string
}

variable "database_migration_url_secret_arn" {
  description = "ARN of the database migration URL secret"
  type        = string
}

variable "clerk_secret_key_arn" {
  description = "ARN of the Clerk secret key"
  type        = string
}

variable "clerk_webhook_secret_arn" {
  description = "ARN of the Clerk webhook signing secret"
  type        = string
}

variable "clerk_publishable_key_arn" {
  description = "ARN of the Clerk publishable key"
  type        = string
}

variable "internal_api_key_arn" {
  description = "ARN of the internal API key"
  type        = string
}

# App Config (non-secret environment variables)
variable "internal_alb_dns_name" {
  description = "Internal ALB DNS name for BACKEND_URL"
  type        = string
}

variable "clerk_issuer" {
  description = "Clerk JWT issuer URL"
  type        = string
  default     = ""
}

variable "clerk_jwks_uri" {
  description = "Clerk JWKS URI for JWT validation"
  type        = string
  default     = ""
}

variable "s3_bucket_name" {
  description = "S3 bucket name for document storage"
  type        = string
}

# Service Sizing
variable "frontend_cpu" {
  description = "Frontend task CPU units"
  type        = number
  default     = 512
}

variable "frontend_memory" {
  description = "Frontend task memory in MiB"
  type        = number
  default     = 1024
}

variable "backend_cpu" {
  description = "Backend task CPU units"
  type        = number
  default     = 1024
}

variable "backend_memory" {
  description = "Backend task memory in MiB"
  type        = number
  default     = 2048
}

variable "frontend_desired_count" {
  description = "Desired number of frontend tasks"
  type        = number
  default     = 2
}

variable "backend_desired_count" {
  description = "Desired number of backend tasks"
  type        = number
  default     = 2
}
