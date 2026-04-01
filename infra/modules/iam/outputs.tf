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

output "gateway_task_role_arn" {
  description = "ARN of the gateway ECS task role"
  value       = aws_iam_role.gateway_task.arn
}

output "portal_task_role_arn" {
  description = "ARN of the portal ECS task role"
  value       = aws_iam_role.portal_task.arn
}

output "keycloak_task_role_arn" {
  description = "ARN of the keycloak ECS task role"
  value       = aws_iam_role.keycloak_task.arn
}

output "github_actions_role_arn" {
  description = "ARN of the GitHub Actions IAM role (used in CI/CD workflows)"
  value       = aws_iam_role.github_actions.arn
}
