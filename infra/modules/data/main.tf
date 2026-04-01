# -----------------------------------------------------------------------------
# DB Subnet Group — place RDS in private subnets
# -----------------------------------------------------------------------------

resource "aws_db_subnet_group" "main" {
  name       = "heykazi-${var.environment}-db-subnet-group"
  subnet_ids = var.private_subnet_ids

  tags = {
    Name        = "heykazi-${var.environment}-db-subnet-group"
    Project     = var.project
    Environment = var.environment
    ManagedBy   = "terraform"
  }
}

# -----------------------------------------------------------------------------
# DB Parameter Group — PostgreSQL 16 tuning
# -----------------------------------------------------------------------------

resource "aws_db_parameter_group" "main" {
  name   = "heykazi-${var.environment}-pg16"
  family = "postgres16"

  parameter {
    name  = "log_min_duration_statement"
    value = "1000"
  }

  parameter {
    name         = "shared_preload_libraries"
    value        = "pg_stat_statements"
    apply_method = "pending-reboot"
  }

  tags = {
    Name        = "heykazi-${var.environment}-pg16"
    Project     = var.project
    Environment = var.environment
    ManagedBy   = "terraform"
  }
}

# -----------------------------------------------------------------------------
# RDS PostgreSQL 16 Instance
# -----------------------------------------------------------------------------

resource "aws_db_instance" "main" {
  identifier = "heykazi-${var.environment}-postgres"

  engine         = "postgres"
  engine_version = "16"

  instance_class = var.rds_instance_class
  multi_az       = var.rds_multi_az

  db_name = "kazi"

  storage_type          = "gp3"
  allocated_storage     = var.rds_storage_gb
  max_allocated_storage = var.rds_max_storage_gb
  storage_encrypted     = true

  manage_master_user_password = true

  db_subnet_group_name   = aws_db_subnet_group.main.name
  parameter_group_name   = aws_db_parameter_group.main.name
  vpc_security_group_ids = [var.rds_sg_id]

  publicly_accessible = false

  backup_retention_period   = var.rds_backup_retention
  copy_tags_to_snapshot     = true
  deletion_protection       = var.rds_deletion_protection
  skip_final_snapshot       = var.rds_skip_final_snapshot
  final_snapshot_identifier = var.rds_skip_final_snapshot ? null : "heykazi-${var.environment}-postgres-final"

  performance_insights_enabled          = true
  performance_insights_retention_period = 7

  enabled_cloudwatch_logs_exports = ["postgresql", "upgrade"]

  tags = {
    Name        = "heykazi-${var.environment}-postgres"
    Project     = var.project
    Environment = var.environment
    ManagedBy   = "terraform"
  }
}

# -----------------------------------------------------------------------------
# Keycloak Database — provisioning reminder
# -----------------------------------------------------------------------------
# The RDS instance creates the 'kazi' database automatically via db_name.
# The 'kazi_keycloak' database (ADR-215) must be created as a manual
# post-provisioning step.
#
# To create it, retrieve credentials from Secrets Manager using the ARN in
# the rds_master_credentials_secret_arn output, then run:
#   psql -h <endpoint> -U <master_user> -d kazi -c 'CREATE DATABASE kazi_keycloak;'
# See infra/RUNBOOK.md for details.
# -----------------------------------------------------------------------------

resource "terraform_data" "keycloak_db_reminder" {
  input = {
    rds_endpoint    = aws_db_instance.main.address
    credentials_arn = aws_db_instance.main.master_user_secret[0].secret_arn
    message         = "Manual step: create kazi_keycloak database. Retrieve credentials from Secrets Manager ARN above, then run CREATE DATABASE kazi_keycloak. See infra/RUNBOOK.md."
  }

  depends_on = [aws_db_instance.main]
}

# -----------------------------------------------------------------------------
# Retrieve Redis auth token value from Secrets Manager
# -----------------------------------------------------------------------------

data "aws_secretsmanager_secret_version" "redis_auth_token" {
  count     = var.create_redis ? 1 : 0
  secret_id = var.redis_auth_token_secret_arn
}

# -----------------------------------------------------------------------------
# ElastiCache Subnet Group — place Redis in private subnets
# -----------------------------------------------------------------------------

resource "aws_elasticache_subnet_group" "main" {
  count = var.create_redis ? 1 : 0

  name       = "heykazi-${var.environment}-redis-subnet-group"
  subnet_ids = var.private_subnet_ids

  tags = {
    Name        = "heykazi-${var.environment}-redis-subnet-group"
    Project     = var.project
    Environment = var.environment
    ManagedBy   = "terraform"
  }
}

# -----------------------------------------------------------------------------
# ElastiCache Redis Replication Group — single-node Redis 7.1
# -----------------------------------------------------------------------------

resource "aws_elasticache_replication_group" "main" {
  count = var.create_redis ? 1 : 0

  replication_group_id = "heykazi-${var.environment}-redis"
  description          = "heykazi-${var.environment}-redis"

  engine         = "redis"
  engine_version = var.redis_engine_version
  node_type      = var.redis_node_type

  num_cache_clusters         = 1
  automatic_failover_enabled = false

  transit_encryption_enabled = true
  auth_token                 = data.aws_secretsmanager_secret_version.redis_auth_token[0].secret_string

  subnet_group_name  = aws_elasticache_subnet_group.main[0].name
  security_group_ids = [var.redis_sg_id]

  tags = {
    Name        = "heykazi-${var.environment}-redis"
    Project     = var.project
    Environment = var.environment
    ManagedBy   = "terraform"
  }
}
