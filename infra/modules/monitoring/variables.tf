variable "project" {
  description = "Project name"
  type        = string
  default     = "kazi"
}

variable "environment" {
  description = "Environment name"
  type        = string
}

variable "log_retention_days" {
  description = "CloudWatch log retention in days"
  type        = number
  default     = 30
}

variable "alert_email" {
  description = "Email address for CloudWatch alarm SNS notifications"
  type        = string
}

variable "alb_arn_suffix" {
  description = "ARN suffix of the public ALB (used as CloudWatch dimension)"
  type        = string
}

variable "backend_tg_arn_suffix" {
  description = "ARN suffix of the backend target group (used as CloudWatch dimension)"
  type        = string
}

variable "gateway_tg_arn_suffix" {
  description = "ARN suffix of the gateway target group (used as CloudWatch dimension)"
  type        = string
}

variable "keycloak_tg_arn_suffix" {
  description = "ARN suffix of the keycloak target group (used as CloudWatch dimension)"
  type        = string
}

variable "rds_instance_identifier" {
  description = "RDS DB instance identifier (used as CloudWatch dimension)"
  type        = string
}

variable "ecs_cluster_name" {
  description = "ECS cluster name (used as CloudWatch dimension)"
  type        = string
}

variable "backend_service_name" {
  description = "Name of the backend ECS service (used as CloudWatch dimension)"
  type        = string
}

variable "alarm_cpu_threshold" {
  description = "CPU utilization threshold (%) for ECS CPU alarm"
  type        = number
  default     = 80
}

variable "alarm_evaluation_periods" {
  description = "Number of evaluation periods for ECS CPU alarm"
  type        = number
  default     = 1
}
