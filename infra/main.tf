# -----------------------------------------------------------------------------
# Root Module — composes all child modules
# -----------------------------------------------------------------------------
# Usage:
#   terraform init -backend-config="key=staging/terraform.tfstate"
#   terraform plan -var-file=environments/staging.tfvars
#   terraform apply -var-file=environments/staging.tfvars
# -----------------------------------------------------------------------------

# -----------------------------------------------------------------------------
# VPC
# -----------------------------------------------------------------------------

module "vpc" {
  source = "./modules/vpc"

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
  source = "./modules/security-groups"

  project     = var.project
  environment = var.environment
  vpc_id      = module.vpc.vpc_id
}

# -----------------------------------------------------------------------------
# Data (RDS PostgreSQL + ElastiCache Redis)
# -----------------------------------------------------------------------------

module "data" {
  source = "./modules/data"

  project     = var.project
  environment = var.environment

  # Networking
  vpc_id             = module.vpc.vpc_id
  private_subnet_ids = module.vpc.private_subnet_ids

  # Security Groups
  rds_sg_id = module.security_groups.rds_sg_id

  # RDS Configuration
  rds_instance_class      = var.rds_instance_class
  rds_multi_az            = var.rds_multi_az
  rds_storage_gb          = var.rds_storage_gb
  rds_max_storage_gb      = var.rds_max_storage_gb
  rds_backup_retention    = var.rds_backup_retention
  rds_deletion_protection = var.rds_deletion_protection
  rds_skip_final_snapshot = var.rds_skip_final_snapshot

  # Redis Configuration
  redis_sg_id                 = module.security_groups.redis_sg_id
  redis_node_type             = var.redis_node_type
  redis_engine_version        = var.redis_engine_version
  create_redis                = var.create_redis
  redis_auth_token_secret_arn = module.secrets.redis_auth_token_arn
}

# -----------------------------------------------------------------------------
# ECR Repositories
# -----------------------------------------------------------------------------

module "ecr" {
  source = "./modules/ecr"

  project     = var.project
  environment = var.environment
}

# -----------------------------------------------------------------------------
# Monitoring (CloudWatch Log Groups)
# -----------------------------------------------------------------------------

module "monitoring" {
  source = "./modules/monitoring"

  project            = var.project
  environment        = var.environment
  log_retention_days = var.log_retention_days
}

# -----------------------------------------------------------------------------
# S3 Bucket
# -----------------------------------------------------------------------------

module "s3" {
  source = "./modules/s3"

  project     = var.project
  environment = var.environment
}

# -----------------------------------------------------------------------------
# Secrets Manager
# -----------------------------------------------------------------------------

module "secrets" {
  source = "./modules/secrets"

  project                 = var.project
  environment             = var.environment
  recovery_window_in_days = var.secrets_recovery_window
}

# -----------------------------------------------------------------------------
# IAM Roles
# -----------------------------------------------------------------------------

module "iam" {
  source = "./modules/iam"

  project                     = var.project
  environment                 = var.environment
  ecr_repo_arns               = values(module.ecr.ecr_repository_arns)
  s3_bucket_arn               = module.s3.bucket_arn
  secret_arns                 = values(module.secrets.secret_arns)
  frontend_log_group_arn      = module.monitoring.frontend_log_group_arn
  backend_log_group_arn       = module.monitoring.backend_log_group_arn
  gateway_log_group_arn       = module.monitoring.gateway_log_group_arn
  portal_log_group_arn        = module.monitoring.portal_log_group_arn
  keycloak_log_group_arn      = module.monitoring.keycloak_log_group_arn
  github_repo                 = var.github_repo
  terraform_state_bucket_name = var.terraform_state_bucket_name
  terraform_lock_table_arn    = var.terraform_lock_table_arn
}

# -----------------------------------------------------------------------------
# DNS + ACM (conditional)
# -----------------------------------------------------------------------------

module "dns" {
  source = "./modules/dns"

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
  source = "./modules/alb"

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
# NOTE: The ECS module still expects Clerk-era variable names
# (clerk_secret_key_arn, clerk_webhook_secret_arn, etc.) which will be
# updated in Epic 412/413 when task definitions are rewritten for the
# 5-service architecture. For now, pass empty strings for the deprecated
# Clerk variables to allow validation to pass.
# -----------------------------------------------------------------------------

module "ecs" {
  source = "./modules/ecs"

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

  # Secrets — current secrets
  database_url_secret_arn           = module.secrets.database_url_arn
  database_migration_url_secret_arn = module.secrets.database_migration_url_arn
  internal_api_key_arn              = module.secrets.internal_api_key_arn

  # Secrets — deprecated Clerk variables (will be removed in E412/E413)
  clerk_secret_key_arn      = ""
  clerk_webhook_secret_arn  = ""
  clerk_publishable_key_arn = ""

  # App Config
  internal_alb_dns_name = module.alb.internal_alb_dns_name
  clerk_issuer          = ""
  clerk_jwks_uri        = ""
  s3_bucket_name        = module.s3.bucket_name
}

# -----------------------------------------------------------------------------
# Auto Scaling
# -----------------------------------------------------------------------------

module "autoscaling" {
  source = "./modules/autoscaling"

  project               = var.project
  environment           = var.environment
  ecs_cluster_name      = module.ecs.cluster_name
  frontend_service_name = module.ecs.frontend_service_name
  backend_service_name  = module.ecs.backend_service_name
  min_capacity          = var.autoscaling_min_capacity
  max_capacity          = var.autoscaling_max_capacity
}
