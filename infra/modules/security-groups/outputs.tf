output "public_alb_sg_id" {
  description = "Security group ID for the public ALB"
  value       = aws_security_group.public_alb.id
}

output "internal_alb_sg_id" {
  description = "Security group ID for the internal ALB"
  value       = aws_security_group.internal_alb.id
}

output "frontend_sg_id" {
  description = "Security group ID for the frontend ECS tasks"
  value       = aws_security_group.frontend.id
}

output "backend_sg_id" {
  description = "Security group ID for the backend ECS tasks"
  value       = aws_security_group.backend.id
}
