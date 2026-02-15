# ADR-063: Compliance Packs as Bundled Seed Data

**Status**: Accepted

**Context**: Phase 13 introduces jurisdiction-specific compliance content: onboarding checklists that guide staff through customer verification (e.g., FICA identity checks in South Africa), custom field definitions for compliance data (e.g., SA ID number, risk rating), and retention policy defaults. This content varies by jurisdiction — a South African law firm needs FICA-compliant checklists, a UK accounting firm needs Companies House verification steps, a US practice needs SSN/EIN fields. The question is how to package and deliver this content to tenants.

The delivery mechanism affects: (1) when content becomes available (at deploy time or runtime installation), (2) who controls content updates (platform developer or tenant admin), (3) versioning and compatibility (can old tenants receive new pack versions?), and (4) the user experience of adopting jurisdiction-specific compliance requirements.

The platform has two established patterns for delivering bundled content: **field packs** (Phase 11) and **document template packs** (Phase 12), both implemented as classpath resource JSON files seeded during tenant provisioning. The question is whether compliance content should follow this pattern, or whether a more dynamic runtime installation system (marketplace/plugin model) is needed.

**Options Considered**:

1. **Classpath resource packs seeded during provisioning (same pattern as field packs and template packs)** — Compliance packs are shipped as part of the platform codebase under `src/main/resources/compliance-packs/{pack-id}/pack.json`. During tenant provisioning (when a new org is created), the `CompliancePackSeeder` reads all pack files from the classpath, creates `ChecklistTemplate` / `ChecklistTemplateItem` / `FieldDefinition` / `RetentionPolicy` records in the tenant's schema, and records applied packs in `OrgSettings.compliance_pack_status`. Packs are versioned; the seeder is idempotent (re-running provisioning for an already-seeded pack is a no-op). Platform developers ship new packs or update existing packs by deploying a new version of the backend. The `generic-onboarding` pack is active by default; jurisdiction-specific packs (e.g., `sa-fica-individual`) are seeded but inactive — the tenant admin activates them via settings.
   - Pros:
     - **Follows established platform patterns**: Field packs (Phase 11) and document template packs (Phase 12) use this exact model. The codebase already has `FieldPackSeeder` and `TemplatePackSeeder`. Compliance packs extend the pattern, not invent a new one.
     - **Simplicity**: No runtime catalog, no download/install UI, no compatibility checks beyond version tracking. Packs are shipped with the platform and seeded atomically during provisioning.
     - **Versioning is developer-controlled**: Platform developers decide when to ship pack updates. Tenants receive updates when they upgrade to a new platform version. No risk of tenants installing incompatible packs.
     - **No external dependencies**: Packs are bundled in the JAR, available offline. No need for a pack registry service, CDN, or API key.
     - **Testable**: Packs are part of the codebase. Integration tests can provision tenants with packs and verify checklist/field creation. Pack content is code-reviewed and versioned alongside the platform.
     - **Forward-compatible with marketplace model**: The `pack.json` schema is designed to be forward-compatible. If the platform later builds a runtime marketplace, the same pack format can be reused — packs would be downloaded at runtime instead of bundled at compile time, but the seeder logic remains identical.
     - **Tenant control where it matters**: Admins can activate/deactivate packs, clone templates for customization, and create their own custom checklists. The platform ships sensible defaults, but tenants own the final configuration.
   - Cons:
     - **Pack updates require platform deployment**: If a pack's checklist items need updating (e.g., new FICA regulation requires an additional verification step), the change must be deployed as a platform update. Tenants cannot install pack updates independently.
     - **Monolithic distribution**: All tenants receive all packs, even if they only need one. A UK tenant gets the SA FICA packs bundled in their deployment (though inactive). This is a trivial storage cost (~50KB per pack) but philosophically inelegant.
     - **No pack discovery UX**: Tenants don't "install" packs — packs are seeded silently during provisioning. The admin UI shows "activate this pack" but not "install this pack from the marketplace." For early-stage product with a dozen packs, this is fine. For a mature product with 50+ jurisdiction-specific packs, discoverability becomes a UX challenge.
     - **Developer must vet all packs**: If the platform wants to support third-party compliance packs (e.g., a community-contributed Australian ASIC pack), those packs must be merged into the main codebase. No runtime plugin system for external contributions.

2. **Runtime plugin/marketplace system (admin installs packs from a catalog)** — Compliance packs are stored externally (S3, CDN, or a pack registry service). The backend exposes a "marketplace" API (`GET /api/marketplace/packs`, `POST /api/packs/install`) that lists available packs and installs them on demand. When an admin installs a pack, the backend downloads the `pack.json` from the registry, validates compatibility (pack schema version matches platform version), seeds the checklist templates and fields into the tenant schema, and records the installation. Packs can be updated independently of platform deployments — the admin clicks "Update Pack" in settings, and the backend fetches the latest pack version.
   - Pros:
     - **Independent pack updates**: Compliance content can be updated without redeploying the platform. A new FICA regulation ships as a pack update, not a backend release.
     - **Tenants install only what they need**: A UK tenant installs only UK packs. Pack distribution is on-demand, not bundled.
     - **Discovery UX**: Admins browse a pack catalog with descriptions, screenshots, and jurisdiction tags. The "install pack" flow is explicit and intuitive.
     - **Third-party packs**: External contributors (compliance consultants, industry bodies) can publish packs to the registry without platform developer involvement. The platform becomes an ecosystem.
     - **Versioning flexibility**: Tenants can choose to stay on an old pack version if a new version introduces breaking changes (e.g., removes a checklist item they depend on).
   - Cons:
     - **Significant engineering complexity**: Build a pack registry service (API, storage, versioning, CDN), implement download/install logic in the backend, add compatibility checks (platform version X supports pack schema Y), design a marketplace UI with search/browse/install flows. This is 3-5 weeks of engineering for the MVP alone.
     - **Security surface**: Packs are code-like (they execute database inserts, create templates, define validation rules). Allowing runtime installation of external packs requires: signature verification (to prevent tampering), sandboxing (to prevent malicious SQL injection via pack content), and approval workflows (to vet third-party packs). Without these safeguards, the marketplace is a security hole.
     - **Compatibility matrix nightmare**: Pack schema evolves. Platform version 1.0 supports schema v1. Version 1.5 supports schema v2. Old packs break on new platforms. New packs break on old platforms. The registry must track compatibility, the installer must enforce it, and the UI must explain it to admins. This is non-trivial.
     - **Versioning conflicts**: Tenant installs pack A v1, then installs pack B which depends on pack A v2. Do we force-upgrade pack A? Do we reject pack B? Do we allow multiple versions side-by-side? All options are complex.
     - **Offline deployment breaks**: If the platform is deployed in an air-gapped environment (e.g., financial services on-prem), the marketplace is inaccessible. Classpath bundling works offline by default.
     - **Premature optimization**: At this stage of the product, there are 3 shipped packs (`generic-onboarding`, `sa-fica-individual`, `sa-fica-company`). A marketplace is over-engineered for a dozen packs. The ROI is negative until the pack count reaches 30-50+.
     - **No established pattern in the codebase**: Field packs and template packs use classpath bundling. Compliance packs would be the first runtime-installable content type. This introduces architectural inconsistency.

3. **Database-seeded via admin UI only (no bundled packs, admin creates all checklists manually)** — The platform ships with empty checklist templates. Admins create checklists from scratch using the "New Checklist Template" UI: add items, set dependencies, configure document requirements. The platform provides UI primitives (drag-to-reorder items, dependency dropdown) but no pre-built content. Compliance consultants or the platform vendor could publish checklist recipes as documentation (e.g., "Sample FICA Individual Checklist"), but they're not shipped as code or seed data.
   - Pros:
     - **Maximum flexibility**: Every org's checklist is custom-built for their exact needs. No "platform-imposed" compliance requirements.
     - **No pack versioning**: Checklists are org-owned data, not platform-shipped content. No versioning, compatibility, or update concerns.
     - **Simplest technical implementation**: No seeder, no pack files, no marketplace. Just CRUD APIs for checklist templates.
   - Cons:
     - **Massive onboarding burden**: A new tenant must manually recreate a 10-item FICA checklist from scratch. This takes 15-30 minutes per checklist and requires compliance expertise (what items are legally required?). Most customers will get it wrong or skip it entirely.
     - **No standardization**: Every org builds their own checklist. Some will be incomplete (missing sanctions screening), some will be wrong (misinterpreting FICA requirements). The platform provides no guidance, no best practices, no defaults.
     - **Competitive disadvantage**: A competitor who ships pre-built FICA checklists provides immediate value out-of-the-box. A customer evaluating both products chooses the one that "just works" for their jurisdiction.
     - **Compliance risk for customers**: If a customer builds an incomplete checklist and fails a FICA audit, they may blame the platform for not guiding them toward compliance. Shipping jurisdiction-specific checklists as platform content transfers compliance expertise from the customer to the platform vendor.
     - **Documentation as a poor substitute**: Publishing checklist recipes as docs (PDF or Notion) doesn't help — admins must manually copy-paste item names, descriptions, and dependencies from the docs into the UI. This is error-prone and tedious. Seed data is the correct abstraction.

4. **External configuration service (packs loaded from a remote config server at startup)** — Compliance packs are stored in an external configuration service (e.g., AWS AppConfig, Spring Cloud Config). At application startup (not per-tenant provisioning), the backend fetches the latest pack definitions from the config server and caches them in memory. During tenant provisioning, the seeder reads from the cached packs and seeds the tenant schema. Pack updates are deployed by pushing a new config version to the config service; all backend instances pick up the update on restart (or via a config refresh webhook).
   - Pros:
     - **Decouples pack updates from platform deployments**: Compliance content can be updated by changing the config service, not redeploying the backend JAR.
     - **Centralized pack management**: A single config service controls packs for all deployments (dev, staging, prod). No per-environment pack files to maintain.
     - **Versioning via config service**: Config services like AppConfig support versioning, rollback, and gradual rollout. A bad pack update can be rolled back instantly.
   - Cons:
     - **External dependency**: The backend depends on the config service being available at startup. If the config service is down or misconfigured, the backend fails to start. This adds operational fragility.
     - **Network requirement**: Air-gapped deployments cannot reach the config service. Classpath bundling works offline by default.
     - **Caching complexity**: If packs are cached in memory, how are they invalidated when the config service updates? Do all backend instances restart? Do they poll for updates? Do they use a webhook? All options add complexity.
     - **Versioning mismatch**: If different backend instances have different cached pack versions (e.g., during a rolling deployment), tenants provisioned on instance A get pack v1, tenants on instance B get pack v2. This is inconsistent and confusing.
     - **Not a forward step from classpath bundling**: Classpath bundling is simpler, more reliable, and better-tested. The config service pattern trades simplicity for flexibility, but the flexibility is not needed at this stage of the product.
     - **No established pattern in the codebase**: Field packs and template packs are classpath-bundled. Compliance packs would be the first config-service-loaded content. This introduces architectural inconsistency.

**Decision**: Classpath resource packs seeded during provisioning (Option 1).

**Rationale**: Compliance packs follow the exact pattern established by field packs (Phase 11, ADR-055) and document template packs (Phase 12, ADR-059). The platform ships packs as classpath resources (`src/main/resources/compliance-packs/*/pack.json`), the `CompliancePackSeeder` reads them during tenant provisioning, and `OrgSettings.compliance_pack_status` tracks which packs have been applied. This pattern is simple, testable, offline-compatible, and proven.

At this stage of the product, there are 3 compliance packs (`generic-onboarding`, `sa-fica-individual`, `sa-fica-company`) with 5-10 more expected over the next year. A runtime marketplace (Option 2) is over-engineered for a dozen packs — the engineering cost (3-5 weeks) far exceeds the benefit. When the pack count reaches 30-50 and third-party pack contributions become a strategic goal, the marketplace can be built. The `pack.json` schema is designed to be forward-compatible — the same file format can be used for runtime installation in the future.

The database-only approach (Option 3) was rejected because it creates massive onboarding friction and compliance risk. Customers want "FICA compliance out of the box," not "build your own FICA checklist from scratch." Shipping pre-built, jurisdiction-vetted checklists is a core product differentiator.

The external configuration service approach (Option 4) was rejected because it adds operational complexity (external dependency, network requirement, caching invalidation) for negligible benefit. Pack updates are infrequent (quarterly at most) and can be deployed as platform releases. The config service pattern trades simplicity for flexibility, but the flexibility is not needed.

**Consequences**:
- Compliance packs are stored in `src/main/resources/compliance-packs/{pack-id}/pack.json` (e.g., `generic-onboarding/pack.json`, `sa-fica-individual/pack.json`).
- The `pack.json` schema matches the structure documented in 13.5.2 of the architecture doc: `{ packId, version, name, description, jurisdiction, customerType, checklistTemplate, fieldDefinitions, retentionOverrides }`.
- During tenant provisioning (after the tenant schema is created and RLS policies are applied), the `CompliancePackSeeder.seedPacksForTenant(tenantId, orgId)` method is called. It binds `RequestScopes.TENANT_ID` via `ScopedValue.where()`, loads all packs from the classpath, checks `OrgSettings.compliance_pack_status` for already-applied packs (idempotent), and seeds each pack's content:
  - `ChecklistTemplate` record created with `source = PLATFORM`, `pack_id = {packId}`, `pack_template_key = {checklistTemplate.key}`.
  - `ChecklistTemplateItem` records created for each item in `checklistTemplate.items`, with `depends_on_item_id` resolved via key lookup within the pack.
  - Field definitions (if present) delegated to `FieldPackSeeder.seedFieldsFromPack()` to avoid duplicating field seeding logic.
  - Retention policies (if present) created or updated in the `retention_policies` table.
  - `OrgSettings.compliance_pack_status` updated with `{ packId, version, appliedAt }`.
- The `generic-onboarding` pack is created with `active = true` (immediately available). Jurisdiction-specific packs are created with `active = false` (admin must explicitly activate in settings).
- Admins can activate/deactivate packs via `PATCH /api/settings/compliance`, toggle `auto_instantiate` on templates, and clone `PLATFORM` templates to create `ORG_CUSTOM` versions for customization (following ADR-059's clone-and-edit model).
- Pack updates are deployed by incrementing `version` in `pack.json` and redeploying the backend. The seeder checks `OrgSettings.compliance_pack_status` and skips already-seeded packs unless the version has incremented. Pack updates only affect new tenants; existing tenants continue using their seeded pack version unless the admin explicitly re-runs provisioning (not supported in v1).
- When/if a runtime marketplace is built in the future (Phase X), the same `pack.json` format can be reused. The seeder logic remains identical; only the pack source changes (classpath → HTTP download).
- The three shipped packs (v1) are:
  - `generic-onboarding` (jurisdiction-agnostic, 4 items, auto-instantiated for all tenants).
  - `sa-fica-individual` (South Africa FICA for individuals, 5 items, inactive by default, ships `sa_id_number`, `passport_number`, `risk_rating` field definitions).
  - `sa-fica-company` (South Africa FICA for companies/trusts, 6 items, inactive by default, ships `company_registration_number`, `entity_type` field definitions).
