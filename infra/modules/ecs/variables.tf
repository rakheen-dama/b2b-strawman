variable "project" {
  description = "Project name"
  type        = string
  default     = "kazi"
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

variable "keycloak_client_secret_arn" {
  description = "ARN of the Keycloak client secret"
  type        = string
}

variable "keycloak_admin_username_arn" {
  description = "ARN of the Keycloak admin username secret (used as KC_DB_USERNAME)"
  type        = string
}

variable "keycloak_admin_password_arn" {
  description = "ARN of the Keycloak admin password secret (used as KC_DB_PASSWORD)"
  type        = string
}

variable "redis_auth_token_arn" {
  description = "ARN of the Redis auth token secret"
  type        = string
}

variable "internal_api_key_arn" {
  description = "ARN of the internal API key"
  type        = string
}

# Infrastructure references
variable "redis_host" {
  description = "ElastiCache Redis primary endpoint hostname"
  type        = string
}

variable "rds_endpoint" {
  description = "RDS PostgreSQL endpoint address (for Keycloak DB URL)"
  type        = string
}

variable "vpc_id" {
  description = "VPC ID for Cloud Map private DNS namespace"
  type        = string
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

# -----------------------------------------------------------------------------
# New Services — Networking
# -----------------------------------------------------------------------------

variable "gateway_sg_id" {
  description = "Security group ID for gateway ECS tasks"
  type        = string
}

variable "portal_sg_id" {
  description = "Security group ID for portal ECS tasks"
  type        = string
}

variable "keycloak_sg_id" {
  description = "Security group ID for Keycloak ECS tasks"
  type        = string
}

# -----------------------------------------------------------------------------
# New Services — ALB Target Groups
# -----------------------------------------------------------------------------

variable "gateway_target_group_arn" {
  description = "ARN of the gateway ALB target group"
  type        = string
  default     = ""
}

variable "portal_target_group_arn" {
  description = "ARN of the portal ALB target group"
  type        = string
  default     = ""
}

variable "keycloak_target_group_arn" {
  description = "ARN of the keycloak ALB target group"
  type        = string
  default     = ""
}

# -----------------------------------------------------------------------------
# New Services — IAM Task Roles
# -----------------------------------------------------------------------------

variable "gateway_task_role_arn" {
  description = "ARN of the gateway ECS task role"
  type        = string
}

variable "portal_task_role_arn" {
  description = "ARN of the portal ECS task role"
  type        = string
}

variable "keycloak_task_role_arn" {
  description = "ARN of the Keycloak ECS task role"
  type        = string
}

# -----------------------------------------------------------------------------
# New Services — Container Images
# -----------------------------------------------------------------------------

variable "gateway_image" {
  description = "Full ECR image URI with tag for gateway"
  type        = string
}

variable "portal_image" {
  description = "Full ECR image URI with tag for portal"
  type        = string
}

variable "keycloak_image" {
  description = "Full ECR image URI with tag for keycloak"
  type        = string
}

# -----------------------------------------------------------------------------
# New Services — Monitoring
# -----------------------------------------------------------------------------

variable "gateway_log_group_name" {
  description = "CloudWatch log group name for gateway"
  type        = string
}

variable "portal_log_group_name" {
  description = "CloudWatch log group name for portal"
  type        = string
}

variable "keycloak_log_group_name" {
  description = "CloudWatch log group name for keycloak"
  type        = string
}

# -----------------------------------------------------------------------------
# New Services — Sizing
# -----------------------------------------------------------------------------

variable "gateway_cpu" {
  description = "Gateway task CPU units"
  type        = number
  default     = 1024
}

variable "gateway_memory" {
  description = "Gateway task memory in MiB"
  type        = number
  default     = 2048
}

variable "portal_cpu" {
  description = "Portal task CPU units"
  type        = number
  default     = 512
}

variable "portal_memory" {
  description = "Portal task memory in MiB"
  type        = number
  default     = 1024
}

variable "keycloak_cpu" {
  description = "Keycloak task CPU units"
  type        = number
  default     = 1024
}

variable "keycloak_memory" {
  description = "Keycloak task memory in MiB"
  type        = number
  default     = 2048
}

variable "gateway_desired_count" {
  description = "Desired number of gateway tasks"
  type        = number
  default     = 1
}

variable "portal_desired_count" {
  description = "Desired number of portal tasks"
  type        = number
  default     = 1
}

variable "keycloak_desired_count" {
  description = "Desired number of Keycloak tasks"
  type        = number
  default     = 1
}
