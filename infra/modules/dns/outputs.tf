output "certificate_arn" {
  description = "ARN of the validated ACM certificate (empty if DNS is disabled)"
  value       = var.create_dns ? aws_acm_certificate_validation.main[0].certificate_arn : ""
}

output "app_fqdn" {
  description = "FQDN of the application (empty if DNS is disabled)"
  value       = var.create_dns ? aws_route53_record.app[0].fqdn : ""
}
