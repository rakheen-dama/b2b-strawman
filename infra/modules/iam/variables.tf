variable "project" {
  description = "Project name"
  type        = string
  default     = "kazi"
}

variable "environment" {
  description = "Environment name"
  type        = string
}

variable "ecr_repo_arns" {
  description = "List of ECR repository ARNs that the execution role can pull from"
  type        = list(string)
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
