variable "aws_region" {
  description = "AWS region for the state bucket and lock table"
  type        = string
  default     = "af-south-1"
}

variable "state_bucket_name" {
  description = "Name of the S3 bucket for Terraform state"
  type        = string
  default     = "heykazi-terraform-state"
}

variable "lock_table_name" {
  description = "Name of the DynamoDB table for Terraform state locking"
  type        = string
  default     = "heykazi-terraform-locks"
}
