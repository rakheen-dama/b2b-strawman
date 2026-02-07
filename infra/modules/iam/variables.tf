variable "project" {
  description = "Project name"
  type        = string
}

variable "environment" {
  description = "Environment name"
  type        = string
}

variable "frontend_ecr_repo_arn" {
  description = "ARN of the frontend ECR repository"
  type        = string
}

variable "backend_ecr_repo_arn" {
  description = "ARN of the backend ECR repository"
  type        = string
}

variable "s3_bucket_arn" {
  description = "ARN of the S3 bucket for document storage"
  type        = string
}

variable "secret_arns" {
  description = "List of Secrets Manager secret ARNs"
  type        = list(string)
}

variable "frontend_log_group_arn" {
  description = "ARN of the frontend CloudWatch log group"
  type        = string
}

variable "backend_log_group_arn" {
  description = "ARN of the backend CloudWatch log group"
  type        = string
}
