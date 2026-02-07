# -----------------------------------------------------------------------------
# ACM Certificate with DNS validation — conditional on var.create_dns
# -----------------------------------------------------------------------------

resource "aws_acm_certificate" "main" {
  count = var.create_dns ? 1 : 0

  domain_name       = var.domain_name
  validation_method = "DNS"

  lifecycle {
    create_before_destroy = true
  }
}

# -----------------------------------------------------------------------------
# Route 53 DNS validation records
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
# Route 53 alias record pointing to public ALB
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
