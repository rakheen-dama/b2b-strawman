output "cluster_name" {
  description = "Name of the ECS cluster"
  value       = aws_ecs_cluster.main.name
}

output "cluster_arn" {
  description = "ARN of the ECS cluster"
  value       = aws_ecs_cluster.main.arn
}

output "frontend_service_name" {
  description = "Name of the frontend ECS service"
  value       = aws_ecs_service.frontend.name
}

output "backend_service_name" {
  description = "Name of the backend ECS service"
  value       = aws_ecs_service.backend.name
}

output "frontend_service_id" {
  description = "ID of the frontend ECS service"
  value       = aws_ecs_service.frontend.id
}

output "backend_service_id" {
  description = "ID of the backend ECS service"
  value       = aws_ecs_service.backend.id
}

output "gateway_service_name" {
  description = "Name of the gateway ECS service"
  value       = aws_ecs_service.gateway.name
}

output "portal_service_name" {
  description = "Name of the portal ECS service"
  value       = aws_ecs_service.portal.name
}

output "keycloak_service_name" {
  description = "Name of the keycloak ECS service"
  value       = aws_ecs_service.keycloak.name
}

output "gateway_service_id" {
  description = "ID of the gateway ECS service"
  value       = aws_ecs_service.gateway.id
}

output "portal_service_id" {
  description = "ID of the portal ECS service"
  value       = aws_ecs_service.portal.id
}

output "keycloak_service_id" {
  description = "ID of the keycloak ECS service"
  value       = aws_ecs_service.keycloak.id
}

output "cloud_map_namespace_id" {
  description = "ID of the kazi.internal Cloud Map namespace"
  value       = aws_service_discovery_private_dns_namespace.internal.id
}

output "cloud_map_backend_service_arn" {
  description = "ARN of the backend Cloud Map service"
  value       = aws_service_discovery_service.backend.arn
}
