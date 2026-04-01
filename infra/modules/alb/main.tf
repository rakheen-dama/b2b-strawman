# -----------------------------------------------------------------------------
# Public ALB — internet-facing, routes to frontend, gateway, portal, keycloak
# -----------------------------------------------------------------------------

resource "aws_lb" "public" {
  name               = "${var.project}-${var.environment}-public"
  internal           = false
  load_balancer_type = "application"
  security_groups    = [var.public_alb_sg_id]
  subnets            = var.public_subnet_ids

  enable_deletion_protection = var.alb_deletion_protection
}

# -----------------------------------------------------------------------------
# Target Groups
# -----------------------------------------------------------------------------

resource "aws_lb_target_group" "frontend" {
  name        = "${var.project}-${var.environment}-fe"
  port        = 3000
  protocol    = "HTTP"
  vpc_id      = var.vpc_id
  target_type = "ip"

  deregistration_delay = var.deregistration_delay

  health_check {
    enabled             = true
    path                = var.frontend_health_check_path
    port                = "traffic-port"
    protocol            = "HTTP"
    healthy_threshold   = 2
    unhealthy_threshold = 3
    interval            = 30
    timeout             = 5
    matcher             = "200"
  }
}

resource "aws_lb_target_group" "backend" {
  name        = "${var.project}-${var.environment}-be"
  port        = 8080
  protocol    = "HTTP"
  vpc_id      = var.vpc_id
  target_type = "ip"

  deregistration_delay = var.deregistration_delay

  health_check {
    enabled             = true
    path                = var.backend_health_check_path
    port                = "traffic-port"
    protocol            = "HTTP"
    healthy_threshold   = 2
    unhealthy_threshold = 3
    interval            = 30
    timeout             = 5
    matcher             = "200"
  }
}

resource "aws_lb_target_group" "gateway" {
  name        = "${var.project}-${var.environment}-gw"
  port        = 8443
  protocol    = "HTTP"
  vpc_id      = var.vpc_id
  target_type = "ip"

  deregistration_delay = var.deregistration_delay

  health_check {
    enabled             = true
    path                = "/actuator/health"
    port                = "traffic-port"
    protocol            = "HTTP"
    healthy_threshold   = 3
    unhealthy_threshold = 3
    interval            = 30
    timeout             = 5
    matcher             = "200"
  }
}

resource "aws_lb_target_group" "portal" {
  name        = "${var.project}-${var.environment}-portal"
  port        = 3002
  protocol    = "HTTP"
  vpc_id      = var.vpc_id
  target_type = "ip"

  deregistration_delay = var.deregistration_delay

  health_check {
    enabled             = true
    path                = "/"
    port                = "traffic-port"
    protocol            = "HTTP"
    healthy_threshold   = 2
    unhealthy_threshold = 3
    interval            = 30
    timeout             = 5
    matcher             = "200"
  }
}

resource "aws_lb_target_group" "keycloak" {
  name        = "${var.project}-${var.environment}-kc"
  port        = 8080
  protocol    = "HTTP"
  vpc_id      = var.vpc_id
  target_type = "ip"

  deregistration_delay = var.deregistration_delay

  health_check {
    enabled             = true
    path                = "/health/ready"
    port                = "traffic-port"
    protocol            = "HTTP"
    healthy_threshold   = 2
    unhealthy_threshold = 3
    interval            = 30
    timeout             = 5
    matcher             = "200"
  }
}

# -----------------------------------------------------------------------------
# HTTPS Listener (port 443) — conditional on certificate_arn
# Default action: fixed 404 for unknown hosts
# -----------------------------------------------------------------------------

resource "aws_lb_listener" "https" {
  count = var.certificate_arn != "" ? 1 : 0

  load_balancer_arn = aws_lb.public.arn
  port              = 443
  protocol          = "HTTPS"
  ssl_policy        = "ELBSecurityPolicy-TLS13-1-2-2021-06"
  certificate_arn   = var.certificate_arn

  default_action {
    type = "fixed-response"

    fixed_response {
      content_type = "text/plain"
      message_body = "Not Found"
      status_code  = "404"
    }
  }
}

# -----------------------------------------------------------------------------
# HTTPS Listener Rules — priority-ordered per ADR-214
# -----------------------------------------------------------------------------

# Priority 10: auth.{domain} -> keycloak
resource "aws_lb_listener_rule" "https_keycloak" {
  count = var.certificate_arn != "" ? 1 : 0

  listener_arn = aws_lb_listener.https[0].arn
  priority     = 10

  action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.keycloak.arn
  }

  condition {
    host_header {
      values = [var.auth_domain]
    }
  }
}

# Priority 20: portal.{domain} -> portal
resource "aws_lb_listener_rule" "https_portal" {
  count = var.certificate_arn != "" ? 1 : 0

  listener_arn = aws_lb_listener.https[0].arn
  priority     = 20

  action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.portal.arn
  }

  condition {
    host_header {
      values = [var.portal_domain]
    }
  }
}

# Priority 30: app.{domain}/bff/* -> gateway
resource "aws_lb_listener_rule" "https_bff" {
  count = var.certificate_arn != "" ? 1 : 0

  listener_arn = aws_lb_listener.https[0].arn
  priority     = 30

  action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.gateway.arn
  }

  condition {
    host_header {
      values = [var.app_domain]
    }
  }

  condition {
    path_pattern {
      values = ["/bff/*"]
    }
  }
}

# Priority 40: app.{domain}/api/* -> gateway
resource "aws_lb_listener_rule" "https_api" {
  count = var.certificate_arn != "" ? 1 : 0

  listener_arn = aws_lb_listener.https[0].arn
  priority     = 40

  action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.gateway.arn
  }

  condition {
    host_header {
      values = [var.app_domain]
    }
  }

  condition {
    path_pattern {
      values = ["/api/*"]
    }
  }
}

# Priority 50: app.{domain}/* -> frontend (catch-all for app host)
resource "aws_lb_listener_rule" "https_frontend" {
  count = var.certificate_arn != "" ? 1 : 0

  listener_arn = aws_lb_listener.https[0].arn
  priority     = 50

  action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.frontend.arn
  }

  condition {
    host_header {
      values = [var.app_domain]
    }
  }
}

# -----------------------------------------------------------------------------
# HTTP Listener (port 80) — redirects to HTTPS when cert exists, else forwards
# -----------------------------------------------------------------------------

# When HTTPS is enabled: HTTP redirects to HTTPS
resource "aws_lb_listener" "http_redirect" {
  count = var.certificate_arn != "" ? 1 : 0

  load_balancer_arn = aws_lb.public.arn
  port              = 80
  protocol          = "HTTP"

  default_action {
    type = "redirect"

    redirect {
      port        = "443"
      protocol    = "HTTPS"
      status_code = "HTTP_301"
    }
  }
}

# When no HTTPS: HTTP forwards directly to frontend (pre-DNS development path)
resource "aws_lb_listener" "http_forward" {
  count = var.certificate_arn == "" ? 1 : 0

  load_balancer_arn = aws_lb.public.arn
  port              = 80
  protocol          = "HTTP"

  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.frontend.arn
  }
}

# Path rule: /bff/* -> gateway (on HTTP forward listener)
resource "aws_lb_listener_rule" "http_bff" {
  count = var.certificate_arn == "" ? 1 : 0

  listener_arn = aws_lb_listener.http_forward[0].arn
  priority     = 90

  action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.gateway.arn
  }

  condition {
    path_pattern {
      values = ["/bff/*"]
    }
  }
}

# Path rule: /api/* -> gateway (on HTTP forward listener)
resource "aws_lb_listener_rule" "http_api" {
  count = var.certificate_arn == "" ? 1 : 0

  listener_arn = aws_lb_listener.http_forward[0].arn
  priority     = 100

  action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.gateway.arn
  }

  condition {
    path_pattern {
      values = ["/api/*"]
    }
  }
}

# -----------------------------------------------------------------------------
# Internal ALB — private, routes /internal/* to backend
# -----------------------------------------------------------------------------

resource "aws_lb" "internal" {
  name               = "${var.project}-${var.environment}-internal"
  internal           = true
  load_balancer_type = "application"
  security_groups    = [var.internal_alb_sg_id]
  subnets            = var.private_subnet_ids

  enable_deletion_protection = false
}

resource "aws_lb_target_group" "backend_internal" {
  name        = "${var.project}-${var.environment}-be-int"
  port        = 8080
  protocol    = "HTTP"
  vpc_id      = var.vpc_id
  target_type = "ip"

  deregistration_delay = var.deregistration_delay

  health_check {
    enabled             = true
    path                = var.backend_health_check_path
    port                = "traffic-port"
    protocol            = "HTTP"
    healthy_threshold   = 2
    unhealthy_threshold = 3
    interval            = 30
    timeout             = 5
    matcher             = "200"
  }
}

resource "aws_lb_listener" "internal" {
  load_balancer_arn = aws_lb.internal.arn
  port              = 8080
  protocol          = "HTTP"

  default_action {
    type = "fixed-response"

    fixed_response {
      content_type = "application/json"
      message_body = "{\"error\": \"not found\"}"
      status_code  = "404"
    }
  }
}

resource "aws_lb_listener_rule" "internal_api" {
  listener_arn = aws_lb_listener.internal.arn
  priority     = 100

  action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.backend_internal.arn
  }

  condition {
    path_pattern {
      values = ["/internal/*"]
    }
  }
}
