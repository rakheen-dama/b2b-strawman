# ADR-088: Integration Port Package Structure

**Status**: Accepted

**Context**:

Phase 21 introduces four new integration port interfaces (StorageService, AccountingProvider, AiProvider, DocumentSigningProvider) plus a SecretStore infrastructure port and an IntegrationRegistry component. These ports define clean boundaries between domain logic and external vendor dependencies. The codebase already has two integration-like interfaces -- `PaymentProvider` in the `invoice/` package and `NotificationChannel` in the `notification/channel/` package -- but these were created ad-hoc in their respective feature phases without a unifying organisational principle.

With five new ports and their respective adapter implementations, domain records, and NoOp stubs, the question of package structure becomes critical. The wrong choice leads to either a sprawling flat package that's hard to navigate, or a deep nesting that obscures discoverability. The codebase convention is feature-per-package at the top level (e.g., `invoice/`, `template/`, `customer/`), with sub-packages for sub-concerns (e.g., `notification/channel/`, `notification/template/`). The integration ports are cross-cutting -- they serve multiple domain features -- so they don't fit neatly into any existing domain package.

**Options Considered**:

1. **Per-domain packages under `integration/` (chosen)** -- Create a top-level `integration/` package with sub-packages for each integration domain: `integration/storage/`, `integration/accounting/`, `integration/ai/`, `integration/signing/`, `integration/secret/`. The port interface, domain records, and NoOp stub live in the domain sub-package. Vendor-specific adapter implementations go one level deeper (e.g., `integration/storage/s3/S3StorageAdapter.java`). The `IntegrationRegistry` and `@IntegrationAdapter` annotation live in `integration/` itself.
   - Pros:
     - Matches the existing feature-per-package convention -- each integration domain is self-contained
     - Clear import boundaries: domain services import `integration.accounting.AccountingProvider`, never `integration.accounting.xero.*`
     - Adding a new vendor adapter means adding one sub-package -- no changes to existing structure
     - All integration concerns are discoverable under a single top-level package
     - The registry, annotation, and shared types (`IntegrationDomain`, `ConnectionTestResult`) live at the `integration/` root, naturally adjacent to all domains
   - Cons:
     - Creates a new top-level package that breaks the "one package per business domain" pattern slightly (integration is infrastructure, not business domain)
     - Existing `PaymentProvider` and `NotificationChannel` would ideally move here for consistency, but moving them is a breaking change best deferred

2. **Ports in domain packages, adapters in `infrastructure/`** -- Place each port interface alongside the domain it serves (e.g., `StorageService` in a new `storage/` package, `AccountingProvider` in `invoice/`). Adapter implementations live in a separate `infrastructure/` package tree (e.g., `infrastructure/s3/S3StorageAdapter.java`, `infrastructure/xero/XeroAccountingAdapter.java`).
   - Pros:
     - Clean hexagonal architecture: domain layer has zero knowledge of adapters
     - Port interfaces live close to where they're used
   - Cons:
     - Creates a new `infrastructure/` top-level package that has no precedent in this codebase
     - Splits related code across two distant package trees -- the port interface and its NoOp stub are in different packages
     - `AccountingProvider` in `invoice/` is misleading: accounting sync serves customers too, not just invoices
     - `AiProvider` has no natural "home" domain package -- it's used across multiple features
     - Spring component scanning would need to cover both `integration/` and `infrastructure/`, adding configuration complexity

3. **Flat `integration/` package** -- All port interfaces, domain records, annotations, registry, and NoOp stubs in a single `integration/` package. Vendor adapters get sub-packages (e.g., `integration/s3/`).
   - Pros:
     - Simplest structure: everything integration-related in one place
     - No deep nesting -- easy to find any integration type
   - Cons:
     - A single package with 20+ files (5 ports, 5 NoOp stubs, 10+ domain records, registry, annotation, enum) becomes unwieldy
     - No separation between unrelated domains -- `AiTextRequest` sits next to `SigningRequest` with no grouping
     - Violates the codebase convention of sub-packaging for sub-concerns (see `notification/channel/`, `notification/template/`)
     - As real adapters are added (Xero, Stripe, OpenAI), the flat package explodes in size

**Decision**: Per-domain packages under `integration/` (Option 1).

**Rationale**: The per-domain structure under a single `integration/` top-level package provides the best balance of discoverability and separation of concerns. Each integration domain (storage, accounting, AI, signing, secret) is self-contained with its port interface, domain records, and NoOp stub. Vendor-specific adapters nest one level deeper, keeping SDK dependencies clearly isolated.

This mirrors how the codebase already organises sub-concerns: `notification/channel/` contains delivery implementations, `notification/template/` contains template rendering. Similarly, `integration/accounting/` will contain the port and records, while `integration/accounting/xero/` will contain the Xero-specific adapter when built.

The existing `PaymentProvider` and `NotificationChannel` interfaces remain in their current packages. The `IntegrationRegistry` can discover and wrap them via their `@IntegrationAdapter` annotations (or equivalent marker) without requiring a package move. A future cleanup phase could relocate them, but it is not required for the registry to function.

**Consequences**:

- Positive:
  - All new integration code lives under `integration/` -- easy to find, easy to audit for vendor leaks
  - Adding a new integration domain (e.g., `integration/crm/`) is a self-contained package addition
  - Import discipline is enforced by package structure: `import io.b2mash.b2b.b2bstrawman.integration.accounting.AccountingProvider` is clearly a port, while `import io.b2mash.b2b.b2bstrawman.integration.accounting.xero.*` is clearly an adapter
  - Spring `@ComponentScan` naturally covers all adapters under `integration/`

- Negative:
  - `PaymentProvider` and `NotificationChannel` remain in their original packages rather than co-locating with other ports -- some inconsistency
  - Developers must remember that `integration/` is for cross-cutting vendor ports, not for domain-specific service code

- Neutral:
  - The `integration/` package will contain ~6 sub-packages (storage, accounting, ai, signing, secret, plus the root for registry/annotation/enum). This is comparable to other top-level packages like `notification/` (3 sub-packages)
