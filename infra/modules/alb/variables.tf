variable "project" {
  description = "Project name"
  type        = string
}

variable "environment" {
  description = "Environment name"
  type        = string
}

variable "vpc_id" {
  description = "VPC ID for target groups"
  type        = string
}

variable "public_subnet_ids" {
  description = "Public subnet IDs for the public ALB"
  type        = list(string)
}

variable "private_subnet_ids" {
  description = "Private subnet IDs for the internal ALB"
  type        = list(string)
}

variable "public_alb_sg_id" {
  description = "Security group ID for the public ALB"
  type        = string
}

variable "internal_alb_sg_id" {
  description = "Security group ID for the internal ALB"
  type        = string
}

variable "certificate_arn" {
  description = "ACM certificate ARN for HTTPS. If empty, only HTTP listener is created."
  type        = string
  default     = ""
}

variable "frontend_health_check_path" {
  description = "Health check path for the frontend target group"
  type        = string
  default     = "/"
}

variable "backend_health_check_path" {
  description = "Health check path for the backend target group"
  type        = string
  default     = "/actuator/health"
}

variable "deregistration_delay" {
  description = "Target group deregistration delay in seconds"
  type        = number
  default     = 30
}
