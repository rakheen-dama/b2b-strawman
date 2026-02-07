terraform {
  required_version = ">= 1.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}

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

# -----------------------------------------------------------------------------
# VPC
# -----------------------------------------------------------------------------

module "vpc" {
  source = "../../modules/vpc"

  project              = var.project
  environment          = var.environment
  vpc_cidr             = var.vpc_cidr
  public_subnet_cidrs  = var.public_subnet_cidrs
  private_subnet_cidrs = var.private_subnet_cidrs
}

# -----------------------------------------------------------------------------
# Security Groups
# -----------------------------------------------------------------------------

module "security_groups" {
  source = "../../modules/security-groups"

  project     = var.project
  environment = var.environment
  vpc_id      = module.vpc.vpc_id
}

# -----------------------------------------------------------------------------
# ECR Repositories
# -----------------------------------------------------------------------------

module "ecr" {
  source = "../../modules/ecr"

  project     = var.project
  environment = var.environment
}

# -----------------------------------------------------------------------------
# Monitoring (CloudWatch Log Groups)
# -----------------------------------------------------------------------------

module "monitoring" {
  source = "../../modules/monitoring"

  project            = var.project
  environment        = var.environment
  log_retention_days = var.log_retention_days
}

# -----------------------------------------------------------------------------
# S3 Bucket
# -----------------------------------------------------------------------------

module "s3" {
  source = "../../modules/s3"

  project     = var.project
  environment = var.environment
}

# -----------------------------------------------------------------------------
# Secrets Manager
# -----------------------------------------------------------------------------

module "secrets" {
  source = "../../modules/secrets"

  project                 = var.project
  environment             = var.environment
  recovery_window_in_days = var.secrets_recovery_window
}

# -----------------------------------------------------------------------------
# IAM Roles
# -----------------------------------------------------------------------------

module "iam" {
  source = "../../modules/iam"

  project                = var.project
  environment            = var.environment
  frontend_ecr_repo_arn  = module.ecr.frontend_repository_arn
  backend_ecr_repo_arn   = module.ecr.backend_repository_arn
  s3_bucket_arn          = module.s3.bucket_arn
  secret_arns            = values(module.secrets.secret_arns)
  frontend_log_group_arn = module.monitoring.frontend_log_group_arn
  backend_log_group_arn  = module.monitoring.backend_log_group_arn
}

# -----------------------------------------------------------------------------
# DNS + ACM (conditional)
# -----------------------------------------------------------------------------

module "dns" {
  source = "../../modules/dns"

  project        = var.project
  environment    = var.environment
  create_dns     = var.create_dns
  domain_name    = var.domain_name
  hosted_zone_id = var.hosted_zone_id
  alb_dns_name   = module.alb.public_alb_dns_name
  alb_zone_id    = module.alb.public_alb_zone_id
}

# -----------------------------------------------------------------------------
# Application Load Balancers
# -----------------------------------------------------------------------------

module "alb" {
  source = "../../modules/alb"

  project            = var.project
  environment        = var.environment
  vpc_id             = module.vpc.vpc_id
  public_subnet_ids  = module.vpc.public_subnet_ids
  private_subnet_ids = module.vpc.private_subnet_ids
  public_alb_sg_id   = module.security_groups.public_alb_sg_id
  internal_alb_sg_id = module.security_groups.internal_alb_sg_id
  certificate_arn    = module.dns.certificate_arn
}

# -----------------------------------------------------------------------------
# ECS Cluster + Services
# -----------------------------------------------------------------------------

module "ecs" {
  source = "../../modules/ecs"

  project     = var.project
  environment = var.environment
  aws_region  = var.aws_region

  # Networking
  private_subnet_ids = module.vpc.private_subnet_ids
  frontend_sg_id     = module.security_groups.frontend_sg_id
  backend_sg_id      = module.security_groups.backend_sg_id

  # ALB Target Groups
  frontend_target_group_arn = module.alb.frontend_target_group_arn
  backend_target_group_arn  = module.alb.backend_target_group_arn
  backend_internal_tg_arn   = module.alb.backend_internal_target_group_arn

  # IAM
  ecs_execution_role_arn = module.iam.ecs_execution_role_arn
  frontend_task_role_arn = module.iam.frontend_task_role_arn
  backend_task_role_arn  = module.iam.backend_task_role_arn

  # Container Images
  frontend_image = var.frontend_image
  backend_image  = var.backend_image

  # Monitoring
  frontend_log_group_name = module.monitoring.frontend_log_group_name
  backend_log_group_name  = module.monitoring.backend_log_group_name

  # Secrets
  database_url_secret_arn           = module.secrets.database_url_arn
  database_migration_url_secret_arn = module.secrets.database_migration_url_arn
  clerk_secret_key_arn              = module.secrets.clerk_secret_key_arn
  clerk_webhook_secret_arn          = module.secrets.clerk_webhook_secret_arn
  clerk_publishable_key_arn         = module.secrets.clerk_publishable_key_arn
  internal_api_key_arn              = module.secrets.internal_api_key_arn

  # App Config
  internal_alb_dns_name = module.alb.internal_alb_dns_name
  clerk_issuer          = var.clerk_issuer
  clerk_jwks_uri        = var.clerk_jwks_uri
  s3_bucket_name        = module.s3.bucket_name
}

# -----------------------------------------------------------------------------
# Auto Scaling
# -----------------------------------------------------------------------------

module "autoscaling" {
  source = "../../modules/autoscaling"

  project               = var.project
  environment           = var.environment
  ecs_cluster_name      = module.ecs.cluster_name
  frontend_service_name = module.ecs.frontend_service_name
  backend_service_name  = module.ecs.backend_service_name
  min_capacity          = var.autoscaling_min_capacity
  max_capacity          = var.autoscaling_max_capacity
}
