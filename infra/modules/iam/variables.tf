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

variable "gateway_log_group_arn" {
  description = "ARN of the gateway CloudWatch log group"
  type        = string
}

variable "portal_log_group_arn" {
  description = "ARN of the portal CloudWatch log group"
  type        = string
}

variable "keycloak_log_group_arn" {
  description = "ARN of the keycloak CloudWatch log group"
  type        = string
}

variable "github_repo" {
  description = "GitHub repository in OWNER/REPO format for OIDC trust policy (e.g. heykazi/kazi)"
  type        = string
}

variable "terraform_state_bucket_name" {
  description = "Name of the S3 bucket used for Terraform state (for GitHub Actions policy)"
  type        = string
  default     = "heykazi-terraform-state"
}

variable "terraform_lock_table_name" {
  description = "Name of the DynamoDB table used for Terraform state locking (ARN constructed from data sources)"
  type        = string
  default     = "heykazi-terraform-locks"
}
