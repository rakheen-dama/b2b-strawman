# E2E Seed Library Design

**Date**: 2026-03-01
**Purpose**: Modular shell script library for creating rich test data in the E2E environment via REST APIs.
**Use cases**: Agent-driven Playwright testing + manual demo/exploration.

## Architecture

**Approach**: Modular Script Library (Option B) — per-entity shell scripts orchestrated by a top-level runner.

All data is created via REST API calls (not raw SQL), ensuring business rules, lifecycle transitions, and tenant isolation are respected.

## File Structure

```
compose/seed/
├── seed.sh                   # Existing boot seed (unchanged)
├── wait-for-backend.sh       # Existing (unchanged)
├── rich-seed.sh              # Orchestrator
├── lib/
│   ├── common.sh             # Shared helpers (auth, check_status, api wrappers)
│   ├── customers.sh          # Create customers + lifecycle transitions
│   ├── projects.sh           # Create projects
│   ├── tasks.sh              # Create tasks with assignments
│   ├── time-entries.sh       # Log time entries
│   ├── invoices.sh           # Create invoices with lines
│   ├── retainers.sh          # Create retainer agreements
│   ├── documents.sh          # Upload documents (presigned URL flow)
│   ├── comments.sh           # Create comments
│   ├── proposals.sh          # Create proposals
│   ├── rates-budgets.sh      # Billing rates + project budgets
│   └── reset.sh              # Wipe via e2e-reseed + re-provision
└── Dockerfile                # Existing (add lib/ COPY if needed)
```

## Orchestrator (`rich-seed.sh`)

```
Usage:
  bash compose/seed/rich-seed.sh              # Idempotent — adds missing data
  bash compose/seed/rich-seed.sh --reset      # Wipe + recreate everything
  bash compose/seed/rich-seed.sh --only customers,projects  # Specific modules
```

**Execution order** (dependency graph):

```
1. reset          (only if --reset)
2. customers      → 4 customers across lifecycle stages
3. projects       → 6 projects across customers
4. tasks          → ~20 tasks across projects
5. time-entries   → ~15 entries over last 30 days
6. rates-budgets  → billing rates, project budgets
7. invoices       → 2 invoices with lines
8. retainers      → 1 retainer agreement
9. documents      → 3 documents via presigned URL flow
10. comments      → 4-5 comments on tasks
11. proposals     → 1 proposal with milestones
```

**Environment detection**: Auto-detects Docker (`backend:8080`) vs host (`localhost:8081`).

## Shared Library (`lib/common.sh`)

Key functions:
- `get_jwt <userId> <orgRole>` — cached JWT from mock IDP
- `api_post <path> <json_body> [jwt]` — curl wrapper, handles 409 as idempotent
- `api_get <path> [jwt]` — GET wrapper
- `api_put <path> <json_body> [jwt]` — PUT wrapper
- `check_or_create <name> <get_path> <jq_filter> <create_fn>` — idempotency: GET-first, create if missing

## Data Scenario (Medium)

### Customers (4)

| Name | Status | Type |
|------|--------|------|
| Acme Corp | ACTIVE | COMPANY |
| Bright Solutions | ACTIVE | COMPANY |
| Carlos Mendez | ONBOARDING | INDIVIDUAL |
| Dormant Industries | CHURNED | COMPANY |

### Projects (6)

| Project | Customer |
|---------|----------|
| Website Redesign | Acme Corp |
| Brand Guidelines | Acme Corp |
| Mobile App MVP | Bright Solutions |
| SEO Audit | Bright Solutions |
| Annual Report | Carlos Mendez |
| Legacy Migration | Dormant Industries |

### Tasks (~20)

Spread across projects with varied statuses (OPEN/IN_PROGRESS/DONE), assigned to Alice/Bob/Carol.

### Time Entries (~15)

Logged against completed/in-progress tasks, varied dates over last 30 days, mix of billable/non-billable.

### Rates & Budgets

- Org-level: Alice $150/hr, Bob $120/hr
- Budget: Brand Guidelines ($5,000 / 40hrs), Mobile App MVP ($15,000 / 120hrs)

### Invoices (2)

- Acme Corp: 1 invoice, 3 manual lines
- Bright Solutions: 1 invoice pulling unbilled time

### Retainer (1)

- Acme Corp: Monthly hours-based, 20hrs/month, $3,000/month

### Documents (3)

- Project-scoped: "design-mockup.pdf" on Website Redesign
- Customer-scoped: "service-agreement.pdf" on Acme Corp
- Org-scoped: "company-policies.pdf"

### Comments (4-5)

On various tasks, mix of INTERNAL and PORTAL visibility.

### Proposals (1)

- Bright Solutions: Fixed-fee $8,500, 2 milestones

## Idempotency Strategy

Each module uses `check_or_create`: GET the list, filter by name/unique field via jq, create only if not found. This makes `rich-seed.sh` safe to run repeatedly.

## Reset Strategy

`--reset` flag calls `e2e-reseed.sh` (tears down and re-provisions the E2E stack), then runs the full seed. This gives a clean slate.

## API Authentication

All API calls use JWTs from the mock IDP (`POST /token`). Alice (owner) is the default actor. Bob/Carol used for specific assignments to demonstrate multi-user data.
