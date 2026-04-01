# -----------------------------------------------------------------------------
# Public ALB Security Group
# Allows HTTPS (443) from the internet
# -----------------------------------------------------------------------------

resource "aws_security_group" "public_alb" {
  name        = "${var.project}-${var.environment}-sg-public-alb"
  description = "Public ALB - allows HTTPS from internet"
  vpc_id      = var.vpc_id

  tags = {
    Name        = "${var.project}-${var.environment}-sg-public-alb"
    Project     = var.project
    Environment = var.environment
    ManagedBy   = "terraform"
  }
}

resource "aws_vpc_security_group_ingress_rule" "public_alb_https" {
  security_group_id = aws_security_group.public_alb.id
  description       = "HTTPS from internet"
  from_port         = 443
  to_port           = 443
  ip_protocol       = "tcp"
  cidr_ipv4         = "0.0.0.0/0"
}

resource "aws_vpc_security_group_ingress_rule" "public_alb_http" {
  security_group_id = aws_security_group.public_alb.id
  description       = "HTTP from internet (redirect to HTTPS)"
  from_port         = 80
  to_port           = 80
  ip_protocol       = "tcp"
  cidr_ipv4         = "0.0.0.0/0"
}

resource "aws_vpc_security_group_egress_rule" "public_alb_all" {
  security_group_id = aws_security_group.public_alb.id
  description       = "Allow all outbound"
  ip_protocol       = "-1"
  cidr_ipv4         = "0.0.0.0/0"
}

# -----------------------------------------------------------------------------
# Frontend Security Group
# Allows port 3000 from public ALB only
# -----------------------------------------------------------------------------

resource "aws_security_group" "frontend" {
  name        = "${var.project}-${var.environment}-sg-frontend"
  description = "Frontend (Next.js) - allows traffic from public ALB"
  vpc_id      = var.vpc_id

  tags = {
    Name        = "${var.project}-${var.environment}-sg-frontend"
    Project     = var.project
    Environment = var.environment
    ManagedBy   = "terraform"
  }
}

resource "aws_vpc_security_group_ingress_rule" "frontend_from_public_alb" {
  security_group_id            = aws_security_group.frontend.id
  description                  = "HTTP from public ALB"
  from_port                    = 3000
  to_port                      = 3000
  ip_protocol                  = "tcp"
  referenced_security_group_id = aws_security_group.public_alb.id
}

resource "aws_vpc_security_group_egress_rule" "frontend_all" {
  security_group_id = aws_security_group.frontend.id
  description       = "Allow all outbound"
  ip_protocol       = "-1"
  cidr_ipv4         = "0.0.0.0/0"
}

# -----------------------------------------------------------------------------
# Internal ALB Security Group
# Allows port 8080 from frontend only
# -----------------------------------------------------------------------------

resource "aws_security_group" "internal_alb" {
  name        = "${var.project}-${var.environment}-sg-internal-alb"
  description = "Internal ALB - allows traffic from frontend"
  vpc_id      = var.vpc_id

  tags = {
    Name        = "${var.project}-${var.environment}-sg-internal-alb"
    Project     = var.project
    Environment = var.environment
    ManagedBy   = "terraform"
  }
}

resource "aws_vpc_security_group_ingress_rule" "internal_alb_from_frontend" {
  security_group_id            = aws_security_group.internal_alb.id
  description                  = "HTTP from frontend"
  from_port                    = 8080
  to_port                      = 8080
  ip_protocol                  = "tcp"
  referenced_security_group_id = aws_security_group.frontend.id
}

resource "aws_vpc_security_group_egress_rule" "internal_alb_all" {
  security_group_id = aws_security_group.internal_alb.id
  description       = "Allow all outbound"
  ip_protocol       = "-1"
  cidr_ipv4         = "0.0.0.0/0"
}

# -----------------------------------------------------------------------------
# Backend Security Group
# Allows port 8080 from gateway and internal ALB
# -----------------------------------------------------------------------------

resource "aws_security_group" "backend" {
  name        = "${var.project}-${var.environment}-sg-backend"
  description = "Backend (Spring Boot) - allows traffic from gateway and internal ALB"
  vpc_id      = var.vpc_id

  tags = {
    Name        = "${var.project}-${var.environment}-sg-backend"
    Project     = var.project
    Environment = var.environment
    ManagedBy   = "terraform"
  }
}

resource "aws_vpc_security_group_ingress_rule" "backend_from_gateway" {
  security_group_id            = aws_security_group.backend.id
  description                  = "HTTP from gateway (for /api/* routes via BFF)"
  from_port                    = 8080
  to_port                      = 8080
  ip_protocol                  = "tcp"
  referenced_security_group_id = aws_security_group.gateway.id
}

resource "aws_vpc_security_group_ingress_rule" "backend_from_internal_alb" {
  security_group_id            = aws_security_group.backend.id
  description                  = "HTTP from internal ALB (for /internal/* routes)"
  from_port                    = 8080
  to_port                      = 8080
  ip_protocol                  = "tcp"
  referenced_security_group_id = aws_security_group.internal_alb.id
}

resource "aws_vpc_security_group_egress_rule" "backend_all" {
  security_group_id = aws_security_group.backend.id
  description       = "Allow all outbound"
  ip_protocol       = "-1"
  cidr_ipv4         = "0.0.0.0/0"
}

# -----------------------------------------------------------------------------
# Gateway Security Group
# Allows port 8443 from public ALB only
# -----------------------------------------------------------------------------

resource "aws_security_group" "gateway" {
  name        = "${var.project}-${var.environment}-sg-gateway"
  description = "Gateway (Spring Cloud Gateway BFF) - allows traffic from public ALB"
  vpc_id      = var.vpc_id

  tags = {
    Name        = "${var.project}-${var.environment}-sg-gateway"
    Project     = var.project
    Environment = var.environment
    ManagedBy   = "terraform"
  }
}

resource "aws_vpc_security_group_ingress_rule" "gateway_from_public_alb" {
  security_group_id            = aws_security_group.gateway.id
  description                  = "HTTPS from public ALB"
  from_port                    = 8443
  to_port                      = 8443
  ip_protocol                  = "tcp"
  referenced_security_group_id = aws_security_group.public_alb.id
}

resource "aws_vpc_security_group_egress_rule" "gateway_all" {
  security_group_id = aws_security_group.gateway.id
  description       = "Allow all outbound"
  ip_protocol       = "-1"
  cidr_ipv4         = "0.0.0.0/0"
}

# -----------------------------------------------------------------------------
# Portal Security Group
# Allows port 3002 from public ALB only
# -----------------------------------------------------------------------------

resource "aws_security_group" "portal" {
  name        = "${var.project}-${var.environment}-sg-portal"
  description = "Portal (Next.js) - allows traffic from public ALB"
  vpc_id      = var.vpc_id

  tags = {
    Name        = "${var.project}-${var.environment}-sg-portal"
    Project     = var.project
    Environment = var.environment
    ManagedBy   = "terraform"
  }
}

resource "aws_vpc_security_group_ingress_rule" "portal_from_public_alb" {
  security_group_id            = aws_security_group.portal.id
  description                  = "HTTP from public ALB"
  from_port                    = 3002
  to_port                      = 3002
  ip_protocol                  = "tcp"
  referenced_security_group_id = aws_security_group.public_alb.id
}

resource "aws_vpc_security_group_egress_rule" "portal_all" {
  security_group_id = aws_security_group.portal.id
  description       = "Allow all outbound"
  ip_protocol       = "-1"
  cidr_ipv4         = "0.0.0.0/0"
}

# -----------------------------------------------------------------------------
# Keycloak Security Group
# Allows port 8080 from public ALB only
# -----------------------------------------------------------------------------

resource "aws_security_group" "keycloak" {
  name        = "${var.project}-${var.environment}-sg-keycloak"
  description = "Keycloak (Identity Provider) - allows traffic from public ALB"
  vpc_id      = var.vpc_id

  tags = {
    Name        = "${var.project}-${var.environment}-sg-keycloak"
    Project     = var.project
    Environment = var.environment
    ManagedBy   = "terraform"
  }
}

resource "aws_vpc_security_group_ingress_rule" "keycloak_from_public_alb" {
  security_group_id            = aws_security_group.keycloak.id
  description                  = "HTTP from public ALB"
  from_port                    = 8080
  to_port                      = 8080
  ip_protocol                  = "tcp"
  referenced_security_group_id = aws_security_group.public_alb.id
}

resource "aws_vpc_security_group_egress_rule" "keycloak_all" {
  security_group_id = aws_security_group.keycloak.id
  description       = "Allow all outbound"
  ip_protocol       = "-1"
  cidr_ipv4         = "0.0.0.0/0"
}

# -----------------------------------------------------------------------------
# RDS Security Group
# Allows port 5432 from backend and keycloak only (stateful — no egress needed)
# -----------------------------------------------------------------------------

resource "aws_security_group" "rds" {
  name        = "${var.project}-${var.environment}-sg-rds"
  description = "RDS PostgreSQL - allows traffic from backend and keycloak"
  vpc_id      = var.vpc_id

  tags = {
    Name        = "${var.project}-${var.environment}-sg-rds"
    Project     = var.project
    Environment = var.environment
    ManagedBy   = "terraform"
  }
}

resource "aws_vpc_security_group_ingress_rule" "rds_from_backend" {
  security_group_id            = aws_security_group.rds.id
  description                  = "PostgreSQL from backend"
  from_port                    = 5432
  to_port                      = 5432
  ip_protocol                  = "tcp"
  referenced_security_group_id = aws_security_group.backend.id
}

resource "aws_vpc_security_group_ingress_rule" "rds_from_keycloak" {
  security_group_id            = aws_security_group.rds.id
  description                  = "PostgreSQL from keycloak"
  from_port                    = 5432
  to_port                      = 5432
  ip_protocol                  = "tcp"
  referenced_security_group_id = aws_security_group.keycloak.id
}

# -----------------------------------------------------------------------------
# Redis Security Group
# Allows port 6379 from gateway only (stateful — no egress needed)
# -----------------------------------------------------------------------------

resource "aws_security_group" "redis" {
  name        = "${var.project}-${var.environment}-sg-redis"
  description = "ElastiCache Redis - allows traffic from gateway"
  vpc_id      = var.vpc_id

  tags = {
    Name        = "${var.project}-${var.environment}-sg-redis"
    Project     = var.project
    Environment = var.environment
    ManagedBy   = "terraform"
  }
}

resource "aws_vpc_security_group_ingress_rule" "redis_from_gateway" {
  security_group_id            = aws_security_group.redis.id
  description                  = "Redis from gateway"
  from_port                    = 6379
  to_port                      = 6379
  ip_protocol                  = "tcp"
  referenced_security_group_id = aws_security_group.gateway.id
}
