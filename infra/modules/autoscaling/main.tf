# -----------------------------------------------------------------------------
# ECS Service Auto Scaling — CPU and Memory target tracking
# -----------------------------------------------------------------------------

locals {
  services = {
    frontend = var.frontend_service_name
    backend  = var.backend_service_name
  }
}

# -----------------------------------------------------------------------------
# Scalable Targets
# -----------------------------------------------------------------------------

resource "aws_appautoscaling_target" "ecs" {
  for_each = local.services

  max_capacity       = var.max_capacity
  min_capacity       = var.min_capacity
  resource_id        = "service/${var.ecs_cluster_name}/${each.value}"
  scalable_dimension = "ecs:service:DesiredCount"
  service_namespace  = "ecs"
}

# -----------------------------------------------------------------------------
# CPU Target Tracking
# -----------------------------------------------------------------------------

resource "aws_appautoscaling_policy" "cpu" {
  for_each = local.services

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
  for_each = local.services

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
