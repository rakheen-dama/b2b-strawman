# Phase 56 Ideation — Production Infrastructure & Deployment Pipeline
**Date**: 2026-04-01

## Decision
Update and extend the existing (stale) AWS infrastructure to support all 5 services, replace Clerk references with Keycloak, fix ALB routing for Gateway BFF, and build a production-ready CI/CD pipeline.

## Rationale
55 phases built but everything runs on Docker Compose locally. Existing Terraform infra (11 modules) is well-structured but 6 months stale — only provisions 2 services (frontend + backend) with Clerk auth. Approach: **update in place** because module quality is high, only scope and staleness need fixing.

### Key Decisions
1. **Update in place** (not rewrite) — Terraform module structure is solid (ADR-213)
2. **Gateway BFF ALB routing** — split routing: frontend direct for SSR, /bff/* and /api/* through gateway (ADR-214)
3. **Keycloak on ECS Fargate** — separate database in shared RDS instance for cost efficiency (ADR-215)
4. **Flyway re-enabled on startup** — with 180s health check grace period for multi-schema migration (ADR-216)
5. **Image promotion** — build once, tag-promote for backend/gateway/keycloak; frontend rebuilt per env due to NEXT_PUBLIC_* (ADR-217)
6. **Naming migration** — customer-facing uses heykazi.com; internal resources transition from docteams to kazi (ADR-218)

### Critical Findings from Infra Audit
- All secrets/env-vars reference Clerk (removed)
- Only 2 of 5 services provisioned
- ALB routes directly to frontend/backend (should go through Gateway BFF)
- CI/CD uses long-lived IAM keys (OIDC declared but unused)
- Flyway disabled in prod profile
- No CloudWatch alarms or dashboards

## Founder Preferences
- AWS ECS/Fargate hosting (confirmed)
- Terraform for IaC (confirmed)
- Self-managed Keycloak on Fargate (confirmed)
- 5-20 tenant scale for first 6-12 months
- Product name: HeyKazi, domain: heykazi.com

## Phase Roadmap (Updated)
- Phase 55: Legal Foundations (specced, not yet built)
- **Phase 56: Production Infrastructure & Deployment Pipeline** (architecture + ADRs complete)
- Next: Security hardening, go-to-market readiness
