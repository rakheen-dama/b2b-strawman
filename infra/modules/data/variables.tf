variable "project" {
  description = "Project name"
  type        = string
  default     = "kazi"
}

variable "environment" {
  description = "Environment name"
  type        = string
}

variable "vpc_id" {
  description = "VPC ID for DB subnet group"
  type        = string
}

variable "private_subnet_ids" {
  description = "Private subnet IDs for DB subnet group"
  type        = list(string)
}

variable "rds_sg_id" {
  description = "Security group ID for RDS instance"
  type        = string
}

variable "redis_sg_id" {
  description = "Security group ID for Redis (used in 411B)"
  type        = string
}

variable "rds_instance_class" {
  description = "RDS instance type"
  type        = string
  default     = "db.t4g.micro"
}

variable "rds_multi_az" {
  description = "Enable Multi-AZ deployment"
  type        = bool
  default     = false
}

variable "rds_storage_gb" {
  description = "Initial allocated storage (GB)"
  type        = number
  default     = 20
}

variable "rds_max_storage_gb" {
  description = "Maximum storage for autoscaling (GB)"
  type        = number
  default     = 100
}

variable "rds_backup_retention" {
  description = "Automated backup retention days"
  type        = number
  default     = 1
}

variable "rds_deletion_protection" {
  description = "Enable deletion protection"
  type        = bool
  default     = false
}
