terraform {
  backend "s3" {
    bucket         = "docteams-terraform-state"
    key            = "dev/terraform.tfstate"
    region         = "us-east-1"
    dynamodb_table = "docteams-terraform-locks"
    encrypt        = true
  }
}
