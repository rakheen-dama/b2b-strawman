# -----------------------------------------------------------------------------
# ECS Service Auto Scaling — CPU and Memory target tracking
# -----------------------------------------------------------------------------

locals {
  # Services with shared min/max capacity
  base_services = {
    frontend = {
      name         = var.frontend_service_name
      min_capacity = var.min_capacity
      max_capacity = var.max_capacity
    }
    backend = {
      name         = var.backend_service_name
      min_capacity = var.min_capacity
      max_capacity = var.max_capacity
    }
  }

  # Gateway and portal have independent min/max bounds
  extra_services = {
    gateway = {
      name         = var.gateway_service_name
      min_capacity = var.gateway_min_capacity
      max_capacity = var.gateway_max_capacity
    }
    portal = {
      name         = var.portal_service_name
      min_capacity = var.portal_min_capacity
      max_capacity = var.portal_max_capacity
    }
  }

  # Merge all scalable services (keycloak excluded — fixed at 1)
  all_services = merge(local.base_services, local.extra_services)
}

# -----------------------------------------------------------------------------
# Scalable Targets
# -----------------------------------------------------------------------------

resource "aws_appautoscaling_target" "ecs" {
  for_each = local.all_services

  max_capacity       = each.value.max_capacity
  min_capacity       = each.value.min_capacity
  resource_id        = "service/${var.ecs_cluster_name}/${each.value.name}"
  scalable_dimension = "ecs:service:DesiredCount"
  service_namespace  = "ecs"
}

# -----------------------------------------------------------------------------
# CPU Target Tracking
# -----------------------------------------------------------------------------

resource "aws_appautoscaling_policy" "cpu" {
  for_each = local.all_services

  name               = "${var.project}-${var.environment}-${each.key}-cpu"
  policy_type        = "TargetTrackingScaling"
  resource_id        = aws_appautoscaling_target.ecs[each.key].resource_id
  scalable_dimension = aws_appautoscaling_target.ecs[each.key].scalable_dimension
  service_namespace  = aws_appautoscaling_target.ecs[each.key].service_namespace

  target_tracking_scaling_policy_configuration {
    predefined_metric_specification {
      predefined_metric_type = "ECSServiceAverageCPUUtilization"
    }

    target_value       = var.cpu_target_value
    scale_in_cooldown  = var.scale_in_cooldown
    scale_out_cooldown = var.scale_out_cooldown
  }
}

# -----------------------------------------------------------------------------
# Memory Target Tracking
# -----------------------------------------------------------------------------

resource "aws_appautoscaling_policy" "memory" {
  for_each = local.all_services

  name               = "${var.project}-${var.environment}-${each.key}-memory"
  policy_type        = "TargetTrackingScaling"
  resource_id        = aws_appautoscaling_target.ecs[each.key].resource_id
  scalable_dimension = aws_appautoscaling_target.ecs[each.key].scalable_dimension
  service_namespace  = aws_appautoscaling_target.ecs[each.key].service_namespace

  target_tracking_scaling_policy_configuration {
    predefined_metric_specification {
      predefined_metric_type = "ECSServiceAverageMemoryUtilization"
    }

    target_value       = var.memory_target_value
    scale_in_cooldown  = var.scale_in_cooldown
    scale_out_cooldown = var.scale_out_cooldown
  }
}
