# -----------------------------------------------------------------------------
# ACM Wildcard Certificate with DNS validation — conditional on var.create_dns
# Covers: *.heykazi.com (wildcard) + heykazi.com (apex SAN)
# Both staging subdomains (staging-app.heykazi.com) and production subdomains
# (app.heykazi.com) are covered by the single wildcard.
# -----------------------------------------------------------------------------

resource "aws_acm_certificate" "main" {
  count = var.create_dns ? 1 : 0

  domain_name               = "*.${var.domain_name}"
  subject_alternative_names = [var.domain_name]
  validation_method         = "DNS"

  lifecycle {
    create_before_destroy = true
  }
}

# -----------------------------------------------------------------------------
# Route 53 DNS validation records (CNAME)
# ACM requires CNAME records in the hosted zone to validate domain ownership.
# for_each iterates over domain_validation_options which includes all SANs.
# allow_overwrite = true so staging + production cert validations don't conflict.
# -----------------------------------------------------------------------------

resource "aws_route53_record" "validation" {
  for_each = var.create_dns ? {
    for dvo in aws_acm_certificate.main[0].domain_validation_options : dvo.domain_name => {
      name   = dvo.resource_record_name
      record = dvo.resource_record_value
      type   = dvo.resource_record_type
    }
  } : {}

  zone_id         = var.hosted_zone_id
  name            = each.value.name
  type            = each.value.type
  records         = [each.value.record]
  ttl             = 60
  allow_overwrite = true
}

resource "aws_acm_certificate_validation" "main" {
  count = var.create_dns ? 1 : 0

  certificate_arn         = aws_acm_certificate.main[0].arn
  validation_record_fqdns = [for record in aws_route53_record.validation : record.fqdn]
}

# -----------------------------------------------------------------------------
# Route 53 alias record for apex domain (heykazi.com -> ALB)
# Kept for completeness; no ECS service uses apex directly.
# -----------------------------------------------------------------------------

resource "aws_route53_record" "app" {
  count = var.create_dns ? 1 : 0

  zone_id = var.hosted_zone_id
  name    = var.domain_name
  type    = "A"

  alias {
    name                   = var.alb_dns_name
    zone_id                = var.alb_zone_id
    evaluate_target_health = true
  }
}

# -----------------------------------------------------------------------------
# Route 53 A-record aliases for all subdomains
# Production: app, portal, auth
# Staging: staging-app, staging-portal, staging-auth
# All point to the environment's public ALB (each environment has its own ALB).
# Staging subdomains use flat prefix (staging-app) not nested (app.staging)
# so a single *.heykazi.com wildcard cert covers both environments.
# -----------------------------------------------------------------------------

locals {
  dns_records = var.create_dns ? {
    "app"            = "app.${var.domain_name}"
    "portal"         = "portal.${var.domain_name}"
    "auth"           = "auth.${var.domain_name}"
    "staging-app"    = "staging-app.${var.domain_name}"
    "staging-portal" = "staging-portal.${var.domain_name}"
    "staging-auth"   = "staging-auth.${var.domain_name}"
  } : {}
}

resource "aws_route53_record" "subdomains" {
  for_each = local.dns_records

  zone_id = var.hosted_zone_id
  name    = each.value
  type    = "A"

  alias {
    name                   = var.alb_dns_name
    zone_id                = var.alb_zone_id
    evaluate_target_health = true
  }
}
