output "ecs_execution_role_arn" {
  description = "ARN of the ECS task execution role"
  value       = aws_iam_role.execution.arn
}

output "backend_task_role_arn" {
  description = "ARN of the backend ECS task role"
  value       = aws_iam_role.backend_task.arn
}

output "frontend_task_role_arn" {
  description = "ARN of the frontend ECS task role"
  value       = aws_iam_role.frontend_task.arn
}
