output "public_alb_arn" {
  description = "ARN of the public ALB"
  value       = aws_lb.public.arn
}

output "public_alb_dns_name" {
  description = "DNS name of the public ALB"
  value       = aws_lb.public.dns_name
}

output "public_alb_zone_id" {
  description = "Zone ID of the public ALB (for Route 53 alias)"
  value       = aws_lb.public.zone_id
}

output "internal_alb_arn" {
  description = "ARN of the internal ALB"
  value       = aws_lb.internal.arn
}

output "internal_alb_dns_name" {
  description = "DNS name of the internal ALB"
  value       = aws_lb.internal.dns_name
}

output "frontend_target_group_arn" {
  description = "ARN of the frontend target group"
  value       = aws_lb_target_group.frontend.arn
}

output "backend_target_group_arn" {
  description = "ARN of the backend target group (public)"
  value       = aws_lb_target_group.backend.arn
}

output "backend_internal_target_group_arn" {
  description = "ARN of the backend internal target group"
  value       = aws_lb_target_group.backend_internal.arn
}

output "gateway_target_group_arn" {
  description = "ARN of the gateway target group"
  value       = aws_lb_target_group.gateway.arn
}

output "portal_target_group_arn" {
  description = "ARN of the portal target group"
  value       = aws_lb_target_group.portal.arn
}

output "keycloak_target_group_arn" {
  description = "ARN of the keycloak target group"
  value       = aws_lb_target_group.keycloak.arn
}

output "public_alb_arn_suffix" {
  description = "ARN suffix of the public ALB (for CloudWatch alarm dimensions)"
  value       = aws_lb.public.arn_suffix
}

output "backend_tg_arn_suffix" {
  description = "ARN suffix of the backend target group (for CloudWatch alarm dimensions)"
  value       = aws_lb_target_group.backend.arn_suffix
}

output "gateway_tg_arn_suffix" {
  description = "ARN suffix of the gateway target group (for CloudWatch alarm dimensions)"
  value       = aws_lb_target_group.gateway.arn_suffix
}

output "keycloak_tg_arn_suffix" {
  description = "ARN suffix of the keycloak target group (for CloudWatch alarm dimensions)"
  value       = aws_lb_target_group.keycloak.arn_suffix
}
