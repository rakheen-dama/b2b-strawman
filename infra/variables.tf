# -----------------------------------------------------------------------------
# Common
# -----------------------------------------------------------------------------

variable "project" {
  description = "Project name used for resource naming and tagging"
  type        = string
  default     = "kazi"
}

variable "environment" {
  description = "Environment name (staging, production)"
  type        = string
}

variable "aws_region" {
  description = "AWS region for all resources"
  type        = string
  default     = "af-south-1"
}

# -----------------------------------------------------------------------------
# VPC
# -----------------------------------------------------------------------------

variable "vpc_cidr" {
  description = "CIDR block for the VPC"
  type        = string
  default     = "10.0.0.0/16"
}

variable "public_subnet_cidrs" {
  description = "CIDR blocks for public subnets (one per AZ)"
  type        = list(string)
  default     = ["10.0.1.0/24", "10.0.2.0/24"]
}

variable "private_subnet_cidrs" {
  description = "CIDR blocks for private subnets (one per AZ)"
  type        = list(string)
  default     = ["10.0.10.0/24", "10.0.20.0/24"]
}

# -----------------------------------------------------------------------------
# RDS
# -----------------------------------------------------------------------------

variable "rds_instance_class" {
  description = "RDS instance class"
  type        = string
  default     = "db.t4g.micro"
}

variable "rds_multi_az" {
  description = "Enable Multi-AZ for RDS"
  type        = bool
  default     = false
}

variable "rds_storage_gb" {
  description = "Initial allocated storage for RDS (GB)"
  type        = number
  default     = 20
}

variable "rds_max_storage_gb" {
  description = "Maximum storage for RDS autoscaling (GB)"
  type        = number
  default     = 100
}

variable "rds_backup_retention" {
  description = "RDS automated backup retention days"
  type        = number
  default     = 1
}

variable "rds_deletion_protection" {
  description = "Enable deletion protection for RDS"
  type        = bool
  default     = false
}

variable "rds_skip_final_snapshot" {
  description = "Skip final snapshot on RDS deletion (true for staging, false for production)"
  type        = bool
  default     = true
}

# -----------------------------------------------------------------------------
# Redis
# -----------------------------------------------------------------------------

variable "redis_node_type" {
  description = "ElastiCache node type"
  type        = string
  default     = "cache.t4g.micro"
}

variable "redis_engine_version" {
  description = "Redis engine version"
  type        = string
  default     = "7.1"
}

variable "create_redis" {
  description = "Whether to create ElastiCache Redis resources"
  type        = bool
  default     = true
}

# -----------------------------------------------------------------------------
# Container Images
# -----------------------------------------------------------------------------

variable "frontend_image" {
  description = "Frontend ECR image URI with tag"
  type        = string
  default     = "public.ecr.aws/nginx/nginx:latest"
}

variable "backend_image" {
  description = "Backend ECR image URI with tag"
  type        = string
  default     = "public.ecr.aws/nginx/nginx:latest"
}

variable "gateway_image" {
  description = "Gateway ECR image URI with tag"
  type        = string
  default     = "public.ecr.aws/nginx/nginx:latest"
}

variable "portal_image" {
  description = "Portal ECR image URI with tag"
  type        = string
  default     = "public.ecr.aws/nginx/nginx:latest"
}

variable "keycloak_image" {
  description = "Keycloak ECR image URI with tag"
  type        = string
  default     = "public.ecr.aws/nginx/nginx:latest"
}

# -----------------------------------------------------------------------------
# DNS (optional)
# -----------------------------------------------------------------------------

variable "create_dns" {
  description = "Whether to create DNS and ACM resources"
  type        = bool
  default     = false
}

variable "domain_name" {
  description = "Domain name for the application"
  type        = string
  default     = ""
}

variable "hosted_zone_id" {
  description = "Route 53 hosted zone ID"
  type        = string
  default     = ""
}

# -----------------------------------------------------------------------------
# ALB Domains + Protection
# -----------------------------------------------------------------------------

variable "app_domain" {
  description = "Domain name for the main application (e.g., app.heykazi.com)"
  type        = string
  default     = ""
}

variable "portal_domain" {
  description = "Domain name for the customer portal (e.g., portal.heykazi.com)"
  type        = string
  default     = ""
}

variable "auth_domain" {
  description = "Domain name for the auth server / Keycloak (e.g., auth.heykazi.com)"
  type        = string
  default     = ""
}

variable "alb_deletion_protection" {
  description = "Enable deletion protection for the public ALB"
  type        = bool
  default     = false
}

# -----------------------------------------------------------------------------
# Monitoring
# -----------------------------------------------------------------------------

variable "log_retention_days" {
  description = "CloudWatch log retention in days"
  type        = number
  default     = 30
}

# -----------------------------------------------------------------------------
# Secrets
# -----------------------------------------------------------------------------

variable "secrets_recovery_window" {
  description = "Secrets Manager recovery window in days (0 for immediate, 7-30 for production)"
  type        = number
  default     = 7
}

# -----------------------------------------------------------------------------
# Auto Scaling
# -----------------------------------------------------------------------------

variable "autoscaling_min_capacity" {
  description = "Minimum number of tasks per ECS service"
  type        = number
  default     = 2
}

variable "autoscaling_max_capacity" {
  description = "Maximum number of tasks per ECS service"
  type        = number
  default     = 10
}

# -----------------------------------------------------------------------------
# GitHub OIDC
# -----------------------------------------------------------------------------

variable "github_repo" {
  description = "GitHub repository in OWNER/REPO format for OIDC trust policy"
  type        = string
  default     = "heykazi/kazi"
}

# -----------------------------------------------------------------------------
# Terraform State (for GitHub Actions IAM policy)
# -----------------------------------------------------------------------------

variable "terraform_state_bucket_name" {
  description = "Name of the S3 bucket used for Terraform state"
  type        = string
  default     = "heykazi-terraform-state"
}

variable "terraform_lock_table_name" {
  description = "Name of the DynamoDB table used for Terraform state locking"
  type        = string
  default     = "heykazi-terraform-locks"
}
