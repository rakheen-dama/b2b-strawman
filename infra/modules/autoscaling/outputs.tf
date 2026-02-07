output "frontend_scaling_target_id" {
  description = "ID of the frontend auto-scaling target"
  value       = aws_appautoscaling_target.ecs["frontend"].id
}

output "backend_scaling_target_id" {
  description = "ID of the backend auto-scaling target"
  value       = aws_appautoscaling_target.ecs["backend"].id
}
