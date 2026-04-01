# -----------------------------------------------------------------------------
# Staging Environment
# -----------------------------------------------------------------------------

project     = "kazi"
environment = "staging"
aws_region  = "af-south-1"

# VPC
vpc_cidr             = "10.1.0.0/16"
public_subnet_cidrs  = ["10.1.1.0/24", "10.1.2.0/24"]
private_subnet_cidrs = ["10.1.10.0/24", "10.1.20.0/24"]

# Container images — updated by CI/CD pipeline
frontend_image = "public.ecr.aws/nginx/nginx:latest"
backend_image  = "public.ecr.aws/nginx/nginx:latest"

# DNS (set to true and fill in values when domain is available)
create_dns     = false
domain_name    = ""
hosted_zone_id = ""

# Monitoring
log_retention_days = 14

# Secrets
secrets_recovery_window = 7

# Auto Scaling
autoscaling_min_capacity = 1
autoscaling_max_capacity = 4

# RDS
rds_instance_class      = "db.t4g.micro"
rds_multi_az            = false
rds_backup_retention    = 1
rds_deletion_protection = false
rds_skip_final_snapshot = true

# Redis
redis_node_type = "cache.t4g.micro"

# GitHub OIDC
github_repo               = "heykazi/kazi"
terraform_lock_table_name = "heykazi-terraform-locks"
