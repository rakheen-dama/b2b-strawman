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
