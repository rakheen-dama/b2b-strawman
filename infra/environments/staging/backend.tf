terraform {
  backend "s3" {
    bucket         = "docteams-terraform-state"
    key            = "staging/terraform.tfstate"
    region         = "us-east-1"
    dynamodb_table = "docteams-terraform-locks"
    encrypt        = true
  }
}
