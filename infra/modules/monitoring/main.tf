# -----------------------------------------------------------------------------
# CloudWatch Log Groups for ECS services
# -----------------------------------------------------------------------------

resource "aws_cloudwatch_log_group" "frontend" {
  name              = "/ecs/${var.project}-${var.environment}-frontend"
  retention_in_days = var.log_retention_days
}

resource "aws_cloudwatch_log_group" "backend" {
  name              = "/ecs/${var.project}-${var.environment}-backend"
  retention_in_days = var.log_retention_days
}

resource "aws_cloudwatch_log_group" "gateway" {
  name              = "/ecs/${var.project}-${var.environment}-gateway"
  retention_in_days = var.log_retention_days
}

resource "aws_cloudwatch_log_group" "portal" {
  name              = "/ecs/${var.project}-${var.environment}-portal"
  retention_in_days = var.log_retention_days
}

resource "aws_cloudwatch_log_group" "keycloak" {
  name              = "/ecs/${var.project}-${var.environment}-keycloak"
  retention_in_days = var.log_retention_days
}
