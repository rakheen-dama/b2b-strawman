output "frontend_log_group_name" {
  description = "Name of the frontend CloudWatch log group"
  value       = aws_cloudwatch_log_group.frontend.name
}

output "backend_log_group_name" {
  description = "Name of the backend CloudWatch log group"
  value       = aws_cloudwatch_log_group.backend.name
}

output "frontend_log_group_arn" {
  description = "ARN of the frontend CloudWatch log group"
  value       = aws_cloudwatch_log_group.frontend.arn
}

output "backend_log_group_arn" {
  description = "ARN of the backend CloudWatch log group"
  value       = aws_cloudwatch_log_group.backend.arn
}

output "gateway_log_group_name" {
  description = "Name of the gateway CloudWatch log group"
  value       = aws_cloudwatch_log_group.gateway.name
}

output "gateway_log_group_arn" {
  description = "ARN of the gateway CloudWatch log group"
  value       = aws_cloudwatch_log_group.gateway.arn
}

output "portal_log_group_name" {
  description = "Name of the portal CloudWatch log group"
  value       = aws_cloudwatch_log_group.portal.name
}

output "portal_log_group_arn" {
  description = "ARN of the portal CloudWatch log group"
  value       = aws_cloudwatch_log_group.portal.arn
}

output "keycloak_log_group_name" {
  description = "Name of the keycloak CloudWatch log group"
  value       = aws_cloudwatch_log_group.keycloak.name
}

output "keycloak_log_group_arn" {
  description = "ARN of the keycloak CloudWatch log group"
  value       = aws_cloudwatch_log_group.keycloak.arn
}

output "sns_topic_arn" {
  description = "ARN of the alerts SNS topic"
  value       = aws_sns_topic.alerts.arn
}
