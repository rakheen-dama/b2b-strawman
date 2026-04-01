# -----------------------------------------------------------------------------
# ECS Cluster with Fargate and Container Insights
# -----------------------------------------------------------------------------

resource "aws_ecs_cluster" "main" {
  name = "${var.project}-${var.environment}"

  setting {
    name  = "containerInsights"
    value = "enabled"
  }
}

resource "aws_ecs_cluster_capacity_providers" "main" {
  cluster_name = aws_ecs_cluster.main.name

  capacity_providers = ["FARGATE", "FARGATE_SPOT"]

  default_capacity_provider_strategy {
    capacity_provider = "FARGATE"
    weight            = 1
    base              = 1
  }
}

# -----------------------------------------------------------------------------
# Frontend Task Definition
# -----------------------------------------------------------------------------

resource "aws_ecs_task_definition" "frontend" {
  family                   = "${var.project}-${var.environment}-frontend"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = var.frontend_cpu
  memory                   = var.frontend_memory
  execution_role_arn       = var.ecs_execution_role_arn
  task_role_arn            = var.frontend_task_role_arn

  container_definitions = jsonencode([
    {
      name      = "frontend"
      image     = var.frontend_image
      essential = true

      portMappings = [
        {
          containerPort = 3000
          protocol      = "tcp"
        }
      ]

      environment = [
        { name = "NEXT_PUBLIC_AUTH_MODE", value = "keycloak" },
        { name = "NEXT_PUBLIC_GATEWAY_URL", value = "https://app.heykazi.com" },
        { name = "GATEWAY_URL", value = "https://app.heykazi.com" },
        { name = "BACKEND_URL", value = "http://backend.kazi.internal:8080" },
        { name = "NODE_ENV", value = "production" },
      ]

      secrets = [
        { name = "INTERNAL_API_KEY", valueFrom = var.internal_api_key_arn },
      ]

      logConfiguration = {
        logDriver = "awslogs"
        options = {
          "awslogs-group"         = var.frontend_log_group_name
          "awslogs-region"        = var.aws_region
          "awslogs-stream-prefix" = "ecs"
        }
      }
    }
  ])
}

# -----------------------------------------------------------------------------
# Backend Task Definition
# -----------------------------------------------------------------------------

resource "aws_ecs_task_definition" "backend" {
  family                   = "${var.project}-${var.environment}-backend"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = var.backend_cpu
  memory                   = var.backend_memory
  execution_role_arn       = var.ecs_execution_role_arn
  task_role_arn            = var.backend_task_role_arn

  container_definitions = jsonencode([
    {
      name      = "backend"
      image     = var.backend_image
      essential = true

      portMappings = [
        {
          containerPort = 8080
          protocol      = "tcp"
        }
      ]

      environment = [
        { name = "SPRING_PROFILES_ACTIVE", value = var.environment },
        { name = "AWS_S3_BUCKET", value = var.s3_bucket_name },
        { name = "AWS_REGION", value = var.aws_region },
        { name = "KEYCLOAK_ISSUER_URI", value = "https://auth.heykazi.com/realms/kazi" },
        { name = "KEYCLOAK_ADMIN_URL", value = "https://auth.heykazi.com/admin/realms/kazi" },
        { name = "KEYCLOAK_CLIENT_ID", value = "backend-service" },
        { name = "REDIS_HOST", value = var.redis_host },
        { name = "REDIS_PORT", value = "6379" },
        { name = "SPRING_FLYWAY_ENABLED", value = "true" },
      ]

      secrets = [
        { name = "DATABASE_URL", valueFrom = var.database_url_secret_arn },
        { name = "DATABASE_MIGRATION_URL", valueFrom = var.database_migration_url_secret_arn },
        { name = "INTERNAL_API_KEY", valueFrom = var.internal_api_key_arn },
        { name = "KEYCLOAK_CLIENT_SECRET", valueFrom = var.keycloak_client_secret_arn },
        { name = "REDIS_AUTH_TOKEN", valueFrom = var.redis_auth_token_arn },
      ]

      logConfiguration = {
        logDriver = "awslogs"
        options = {
          "awslogs-group"         = var.backend_log_group_name
          "awslogs-region"        = var.aws_region
          "awslogs-stream-prefix" = "ecs"
        }
      }
    }
  ])
}

# -----------------------------------------------------------------------------
# Frontend Service
# -----------------------------------------------------------------------------

resource "aws_ecs_service" "frontend" {
  name            = "${var.project}-${var.environment}-frontend"
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.frontend.arn
  desired_count   = var.frontend_desired_count
  launch_type     = "FARGATE"

  network_configuration {
    subnets          = var.private_subnet_ids
    security_groups  = [var.frontend_sg_id]
    assign_public_ip = false
  }

  load_balancer {
    target_group_arn = var.frontend_target_group_arn
    container_name   = "frontend"
    container_port   = 3000
  }

  deployment_circuit_breaker {
    enable   = true
    rollback = true
  }

  deployment_minimum_healthy_percent = 100
  deployment_maximum_percent         = 200

  lifecycle {
    ignore_changes = [task_definition]
  }
}

# -----------------------------------------------------------------------------
# Gateway Task Definition
# -----------------------------------------------------------------------------

resource "aws_ecs_task_definition" "gateway" {
  family                   = "${var.project}-${var.environment}-gateway"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = var.gateway_cpu
  memory                   = var.gateway_memory
  execution_role_arn       = var.ecs_execution_role_arn
  task_role_arn            = var.gateway_task_role_arn

  container_definitions = jsonencode([
    {
      name      = "gateway"
      image     = var.gateway_image
      essential = true

      portMappings = [
        {
          containerPort = 8443
          protocol      = "tcp"
        }
      ]

      environment = [
        { name = "BACKEND_URL", value = "http://backend.kazi.internal:8080" },
        { name = "KEYCLOAK_ISSUER_URI", value = "https://auth.heykazi.com/realms/kazi" },
        { name = "CORS_ALLOWED_ORIGINS", value = "https://app.heykazi.com,https://portal.heykazi.com" },
        { name = "REDIS_HOST", value = var.redis_host },
        { name = "REDIS_PORT", value = "6379" },
      ]

      secrets = [
        { name = "REDIS_AUTH_TOKEN", valueFrom = var.redis_auth_token_arn },
        { name = "INTERNAL_API_KEY", valueFrom = var.internal_api_key_arn },
      ]

      healthCheck = {
        command     = ["CMD-SHELL", "curl -f http://localhost:8443/actuator/health || exit 1"]
        interval    = 30
        timeout     = 5
        retries     = 3
        startPeriod = 60
      }

      logConfiguration = {
        logDriver = "awslogs"
        options = {
          "awslogs-group"         = var.gateway_log_group_name
          "awslogs-region"        = var.aws_region
          "awslogs-stream-prefix" = "ecs"
        }
      }
    }
  ])
}

# -----------------------------------------------------------------------------
# Portal Task Definition
# -----------------------------------------------------------------------------

resource "aws_ecs_task_definition" "portal" {
  family                   = "${var.project}-${var.environment}-portal"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = var.portal_cpu
  memory                   = var.portal_memory
  execution_role_arn       = var.ecs_execution_role_arn
  task_role_arn            = var.portal_task_role_arn

  container_definitions = jsonencode([
    {
      name      = "portal"
      image     = var.portal_image
      essential = true

      portMappings = [
        {
          containerPort = 3002
          protocol      = "tcp"
        }
      ]

      environment = [
        { name = "NEXT_PUBLIC_PORTAL_API_URL", value = "https://portal.heykazi.com/api" },
        { name = "NODE_ENV", value = "production" },
      ]

      healthCheck = {
        command     = ["CMD-SHELL", "curl -f http://localhost:3002/ || exit 1"]
        interval    = 30
        timeout     = 5
        retries     = 3
        startPeriod = 60
      }

      logConfiguration = {
        logDriver = "awslogs"
        options = {
          "awslogs-group"         = var.portal_log_group_name
          "awslogs-region"        = var.aws_region
          "awslogs-stream-prefix" = "ecs"
        }
      }
    }
  ])
}

# -----------------------------------------------------------------------------
# Keycloak Task Definition
# -----------------------------------------------------------------------------

resource "aws_ecs_task_definition" "keycloak" {
  family                   = "${var.project}-${var.environment}-keycloak"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = var.keycloak_cpu
  memory                   = var.keycloak_memory
  execution_role_arn       = var.ecs_execution_role_arn
  task_role_arn            = var.keycloak_task_role_arn

  container_definitions = jsonencode([
    {
      name      = "keycloak"
      image     = var.keycloak_image
      essential = true

      portMappings = [
        {
          containerPort = 8080
          protocol      = "tcp"
        }
      ]

      environment = [
        { name = "KC_DB", value = "postgres" },
        { name = "KC_DB_URL", value = "jdbc:postgresql://${var.rds_endpoint}:5432/keycloak" },
        { name = "KC_HOSTNAME", value = "auth.heykazi.com" },
        { name = "KC_PROXY_HEADERS", value = "xforwarded" },
        { name = "KC_HEALTH_ENABLED", value = "true" },
      ]

      secrets = [
        { name = "KC_DB_USERNAME", valueFrom = var.keycloak_admin_username_arn },
        { name = "KC_DB_PASSWORD", valueFrom = var.keycloak_admin_password_arn },
      ]

      healthCheck = {
        command     = ["CMD-SHELL", "curl -f http://localhost:8080/health/ready || exit 1"]
        interval    = 30
        timeout     = 5
        retries     = 3
        startPeriod = 120
      }

      logConfiguration = {
        logDriver = "awslogs"
        options = {
          "awslogs-group"         = var.keycloak_log_group_name
          "awslogs-region"        = var.aws_region
          "awslogs-stream-prefix" = "ecs"
        }
      }
    }
  ])
}

# -----------------------------------------------------------------------------
# Cloud Map — private DNS namespace kazi.internal
# -----------------------------------------------------------------------------

resource "aws_service_discovery_private_dns_namespace" "internal" {
  name        = "kazi.internal"
  description = "Private DNS namespace for internal service discovery"
  vpc         = var.vpc_id

  tags = {
    Project     = var.project
    Environment = var.environment
    ManagedBy   = "terraform"
  }
}

resource "aws_service_discovery_service" "backend" {
  name = "backend"

  dns_config {
    namespace_id = aws_service_discovery_private_dns_namespace.internal.id

    dns_records {
      ttl  = 10
      type = "A"
    }

    routing_policy = "MULTIVALUE"
  }

  health_check_custom_config {
    failure_threshold = 1
  }
}

# -----------------------------------------------------------------------------
# Backend Service — registers with both public and internal target groups
# -----------------------------------------------------------------------------

resource "aws_ecs_service" "backend" {
  name                              = "${var.project}-${var.environment}-backend"
  cluster                           = aws_ecs_cluster.main.id
  task_definition                   = aws_ecs_task_definition.backend.arn
  desired_count                     = var.backend_desired_count
  launch_type                       = "FARGATE"
  health_check_grace_period_seconds = 180

  network_configuration {
    subnets          = var.private_subnet_ids
    security_groups  = [var.backend_sg_id]
    assign_public_ip = false
  }

  # Public ALB target group (for /api/*)
  load_balancer {
    target_group_arn = var.backend_target_group_arn
    container_name   = "backend"
    container_port   = 8080
  }

  # Internal ALB target group (for /internal/*)
  load_balancer {
    target_group_arn = var.backend_internal_tg_arn
    container_name   = "backend"
    container_port   = 8080
  }

  service_registries {
    registry_arn = aws_service_discovery_service.backend.arn
  }

  deployment_circuit_breaker {
    enable   = true
    rollback = true
  }

  deployment_minimum_healthy_percent = 100
  deployment_maximum_percent         = 200

  lifecycle {
    ignore_changes = [task_definition]
  }
}

# -----------------------------------------------------------------------------
# Gateway Service
# -----------------------------------------------------------------------------

resource "aws_ecs_service" "gateway" {
  name            = "${var.project}-${var.environment}-gateway"
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.gateway.arn
  desired_count   = var.gateway_desired_count
  launch_type     = "FARGATE"

  network_configuration {
    subnets          = var.private_subnet_ids
    security_groups  = [var.gateway_sg_id]
    assign_public_ip = false
  }

  dynamic "load_balancer" {
    for_each = var.gateway_target_group_arn != "" ? [1] : []
    content {
      target_group_arn = var.gateway_target_group_arn
      container_name   = "gateway"
      container_port   = 8443
    }
  }

  deployment_circuit_breaker {
    enable   = true
    rollback = true
  }

  deployment_minimum_healthy_percent = 100
  deployment_maximum_percent         = 200

  lifecycle {
    ignore_changes = [task_definition]
  }
}

# -----------------------------------------------------------------------------
# Portal Service
# -----------------------------------------------------------------------------

resource "aws_ecs_service" "portal" {
  name            = "${var.project}-${var.environment}-portal"
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.portal.arn
  desired_count   = var.portal_desired_count
  launch_type     = "FARGATE"

  network_configuration {
    subnets          = var.private_subnet_ids
    security_groups  = [var.portal_sg_id]
    assign_public_ip = false
  }

  dynamic "load_balancer" {
    for_each = var.portal_target_group_arn != "" ? [1] : []
    content {
      target_group_arn = var.portal_target_group_arn
      container_name   = "portal"
      container_port   = 3002
    }
  }

  deployment_circuit_breaker {
    enable   = true
    rollback = true
  }

  deployment_minimum_healthy_percent = 100
  deployment_maximum_percent         = 200

  lifecycle {
    ignore_changes = [task_definition]
  }
}

# -----------------------------------------------------------------------------
# Keycloak Service
# -----------------------------------------------------------------------------

resource "aws_ecs_service" "keycloak" {
  name            = "${var.project}-${var.environment}-keycloak"
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.keycloak.arn
  desired_count   = var.keycloak_desired_count
  launch_type     = "FARGATE"

  network_configuration {
    subnets          = var.private_subnet_ids
    security_groups  = [var.keycloak_sg_id]
    assign_public_ip = false
  }

  dynamic "load_balancer" {
    for_each = var.keycloak_target_group_arn != "" ? [1] : []
    content {
      target_group_arn = var.keycloak_target_group_arn
      container_name   = "keycloak"
      container_port   = 8080
    }
  }

  deployment_circuit_breaker {
    enable   = true
    rollback = true
  }

  deployment_minimum_healthy_percent = 100
  deployment_maximum_percent         = 200

  lifecycle {
    ignore_changes = [task_definition]
  }
}
