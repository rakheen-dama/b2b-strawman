# -----------------------------------------------------------------------------
# Backend — S3 remote state with DynamoDB locking
# -----------------------------------------------------------------------------
# The state key is environment-specific and must be passed via CLI:
#   terraform init -backend-config="key=staging/terraform.tfstate"
#   terraform init -backend-config="key=production/terraform.tfstate"
# -----------------------------------------------------------------------------

terraform {
  backend "s3" {
    bucket         = "heykazi-terraform-state"
    dynamodb_table = "heykazi-terraform-locks"
    encrypt        = true
    region         = "af-south-1"
  }
}

# -----------------------------------------------------------------------------
# AWS Provider
# -----------------------------------------------------------------------------

provider "aws" {
  region = var.aws_region

  default_tags {
    tags = {
      Project     = var.project
      Environment = var.environment
      ManagedBy   = "terraform"
    }
  }
}
