output "certificate_arn" {
  description = "ARN of the validated ACM wildcard certificate (empty if DNS is disabled)"
  value       = var.create_dns ? aws_acm_certificate_validation.main[0].certificate_arn : ""
}

output "app_fqdn" {
  description = "FQDN of the apex domain (empty if DNS is disabled)"
  value       = var.create_dns ? aws_route53_record.app[0].fqdn : ""
}

output "app_domain" {
  description = "FQDN of the app subdomain (empty if DNS is disabled)"
  value       = var.create_dns ? aws_route53_record.subdomains["app"].fqdn : ""
}

output "portal_domain" {
  description = "FQDN of the portal subdomain (empty if DNS is disabled)"
  value       = var.create_dns ? aws_route53_record.subdomains["portal"].fqdn : ""
}

output "auth_domain" {
  description = "FQDN of the auth subdomain (empty if DNS is disabled)"
  value       = var.create_dns ? aws_route53_record.subdomains["auth"].fqdn : ""
}
