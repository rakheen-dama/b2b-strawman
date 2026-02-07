variable "project" {
  description = "Project name"
  type        = string
  default     = "docteams"
}

variable "environment" {
  description = "Environment name"
  type        = string
  default     = "staging"
}

variable "aws_region" {
  description = "AWS region"
  type        = string
  default     = "us-east-1"
}

# VPC
variable "vpc_cidr" {
  description = "CIDR block for the VPC"
  type        = string
  default     = "10.1.0.0/16"
}

variable "public_subnet_cidrs" {
  description = "CIDR blocks for public subnets"
  type        = list(string)
  default     = ["10.1.1.0/24", "10.1.2.0/24"]
}

variable "private_subnet_cidrs" {
  description = "CIDR blocks for private subnets"
  type        = list(string)
  default     = ["10.1.10.0/24", "10.1.20.0/24"]
}

# Container Images
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

# Clerk
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

# DNS (optional)
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

# Monitoring
variable "log_retention_days" {
  description = "CloudWatch log retention in days"
  type        = number
  default     = 14
}

# Secrets
variable "secrets_recovery_window" {
  description = "Secrets Manager recovery window in days"
  type        = number
  default     = 7
}

# Auto Scaling
variable "autoscaling_min_capacity" {
  description = "Minimum number of tasks per service"
  type        = number
  default     = 2
}

variable "autoscaling_max_capacity" {
  description = "Maximum number of tasks per service"
  type        = number
  default     = 6
}
