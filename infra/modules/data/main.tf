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
    name  = "shared_preload_libraries"
    value = "pg_stat_statements"
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

  manage_master_user_password = true

  db_subnet_group_name   = aws_db_subnet_group.main.name
  parameter_group_name   = aws_db_parameter_group.main.name
  vpc_security_group_ids = [var.rds_sg_id]

  backup_retention_period = var.rds_backup_retention
  deletion_protection     = var.rds_deletion_protection
  skip_final_snapshot     = true

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
# post-provisioning step. This null_resource prints a reminder on apply.
# -----------------------------------------------------------------------------

resource "null_resource" "keycloak_db" {
  triggers = {
    rds_endpoint = aws_db_instance.main.address
  }

  provisioner "local-exec" {
    command = <<-EOT
      echo "Manual step: Create kazi_keycloak database"
      echo "Run: psql postgresql://MASTER_USER:MASTER_PASS@${aws_db_instance.main.address}:5432/kazi -c 'CREATE DATABASE kazi_keycloak;'"
      echo "See infra/RUNBOOK.md for details"
    EOT
  }

  depends_on = [aws_db_instance.main]
}
