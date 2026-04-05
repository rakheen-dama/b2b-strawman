# ADR-236: KYC Provider Adapter Strategy

**Status**: Accepted

**Context**:

Law firms are "accountable institutions" under the Financial Intelligence Centre Act (FICA) and must verify the identity of every client during onboarding. Phase 14 built a checklist engine with FICA compliance packs that include identity verification items. Currently, identity verification is entirely manual — the firm checks the client's ID document, confirms the details, and marks the checklist item as complete. There is no automated verification against government databases.

South Africa has several KYC verification providers with different capabilities, pricing, and API designs. VerifyNow connects to the Home Affairs HANIS database and provides definitive identity verification (verified/not verified). Check ID SA validates ID number format and checksum (structural validity) but does not verify against Home Affairs — it is a pre-check, not a verification. Other providers may emerge. The question is how to integrate with these providers in a multi-tenant platform where each firm chooses (and pays for) their own provider.

The platform already has a well-established BYOAK (Bring Your Own API Key) integration pattern from Phase 21. Payment providers (PayFast, Stripe), email providers (SMTP, SendGrid), and accounting providers are all integrated via this pattern: a port interface defines the contract, adapters implement it per provider, firms bring their own API keys stored in the encrypted `SecretStore`, and the `IntegrationRegistry` resolves the active adapter at runtime based on the tenant's `OrgIntegration` configuration. The KYC integration could follow this pattern or deviate from it.

**Options Considered**:

1. **BYOAK with KycVerificationPort abstraction** — Add `KYC_VERIFICATION` to the `IntegrationDomain` enum. Define a `KycVerificationPort` interface with a `verify(request) → result` method. Implement one adapter per provider (`VerifyNowKycAdapter`, `CheckIdKycAdapter`), each annotated with `@IntegrationAdapter`. Firms configure their preferred provider and enter their API key in Settings → Integrations. The `IntegrationRegistry` resolves the active adapter at runtime. A `NoOpKycAdapter` serves as the default when no integration is configured.
   - Pros:
     - Follows the established pattern exactly. No new infrastructure, no new abstractions. The `IntegrationRegistry`, `SecretStore`, `@IntegrationAdapter`, `IntegrationGuardService`, and integration settings UI patterns are all reusable without modification.
     - No credential liability for the platform. Each firm holds its own API key and pays the provider directly. DocTeams does not process KYC data on behalf of firms — the firm's backend sends the request directly to the provider using the firm's credentials.
     - Firms choose their provider based on their own criteria: pricing, capability level (format check vs Home Affairs verification), existing commercial relationship. A firm already using VerifyNow for other purposes can reuse their existing account.
     - Adding a new provider is a single adapter class annotated with `@IntegrationAdapter`. No schema changes, no configuration changes, no deployment coordination. The adapter is picked up by the `IntegrationRegistry` at startup via annotation scanning.
     - Clean separation of concerns: the checklist service knows nothing about KYC providers. It calls `KycVerificationPort.verify()` and handles the result. The adapter handles provider-specific API calls, authentication, and response mapping.
   - Cons:
     - Each firm must obtain their own API key from the provider. This is an onboarding friction point — the firm admin must sign up with VerifyNow or Check ID SA, get API credentials, and enter them in DocTeams.
     - The platform cannot negotiate volume discounts on behalf of its tenants. Each firm gets individual pricing.
     - Support burden: when a firm's API key expires or their credits run out, they contact DocTeams support, even though the issue is with the provider. The platform must guide them to the provider.

2. **Platform-managed credentials** — DocTeams holds a single account with each KYC provider. Verification requests from all tenants route through the platform's account. Firms do not need their own API keys.
   - Pros:
     - Zero onboarding friction for firms. KYC verification "just works" from the first day. No API keys to manage, no provider accounts to create.
     - Platform can negotiate volume discounts based on aggregate usage across all tenants.
     - Single point of configuration and monitoring. The platform operations team manages API keys, monitors credit balances, and handles provider issues.
   - Cons:
     - DocTeams becomes a data processor for KYC data. Every verification request passes through the platform's account, making DocTeams responsible for the personal data under POPIA. This creates significant compliance obligations: data processing agreements with each provider, impact assessments, breach notification responsibilities.
     - Credential liability: if the platform's API key is compromised, all tenants are affected. A single key compromise exposes the verification capability of every firm.
     - Cost allocation complexity: the platform must track per-tenant usage, invoice firms for verifications, handle billing disputes about verification counts. This is a billing system within a billing system.
     - Provider dependency: if the platform's account is suspended (credit exhaustion, abuse detection, contractual dispute), all tenants lose verification capability simultaneously.
     - POPIA consent chain becomes longer: the data subject consents to the firm, the firm authorizes DocTeams, DocTeams sends to the provider. Each link in the chain must be documented and defensible.

3. **Reseller / white-label model** — DocTeams resells provider credits. Firms purchase verification credits through the DocTeams billing system. The platform holds provider credentials but charges firms a per-verification fee.
   - Pros:
     - Revenue opportunity for the platform. A markup on verification credits creates a recurring revenue stream tied to usage.
     - Simpler for firms than BYOAK: they buy credits in DocTeams, no external provider account needed.
     - Platform can switch underlying providers without affecting firms (as long as the capability is equivalent).
   - Cons:
     - All the downsides of Option 2 (data processor obligations, credential liability, provider dependency), plus:
     - Regulatory complexity: reselling KYC services may require specific registrations or authorizations depending on the provider's terms and South African financial services regulations.
     - Billing infrastructure: credit purchases, balance tracking, low-balance alerts, credit expiry, invoicing for credits — this is a significant product surface area for what is an optional integration.
     - Misaligned incentives: the platform profits from verifications, creating a subtle pressure to make automated verification the default or to discourage manual verification. This is uncomfortable in a compliance context where the firm should exercise professional judgment.
     - Provider lock-in: if the platform switches providers, the credit system and pricing must be reworked. Firms cannot independently choose providers.

**Decision**: Option 1 — BYOAK with KycVerificationPort abstraction.

**Rationale**:

The BYOAK pattern is proven in this codebase. Phase 21 established it for payments, email, and accounting — the pattern is well-understood by the development team, well-tested in production, and well-documented in architecture docs. KYC verification is structurally identical to payment processing from an integration perspective: a port interface defines the contract, adapters implement it per provider, firms bring credentials, and the registry resolves at runtime. There is no architectural reason to deviate from this pattern.

The credential liability argument is decisive. KYC verification involves personal data protected under POPIA — ID numbers, names, dates of birth. Under Options 2 and 3, DocTeams would process this data on behalf of firms, creating data processing obligations that are disproportionate to the value provided. Under BYOAK, the firm's backend sends the verification request directly to the provider using the firm's own credentials. DocTeams facilitates the integration but does not hold or process the KYC data centrally. The `SecretStore` encrypts the API key (AES-256-GCM), and the verification request flows through the firm's tenant schema — not through a shared platform account.

The onboarding friction of BYOAK (firm must obtain API key) is acceptable because KYC integration is optional. Firms that do not configure a provider continue with manual verification — the Phase 14 checklist flow is unchanged. Firms that do configure a provider are already sophisticated enough to manage an API key. The Settings → Integrations UI makes configuration straightforward: select provider, paste API key, test connection.

**Check ID SA returns NEEDS_REVIEW, not VERIFIED**: Check ID SA validates ID number format (Luhn checksum, birth date encoding, citizenship digit) but does not verify the person's identity against the Home Affairs database. Format validation confirms that an ID number is structurally valid — it does not confirm that the ID number belongs to the person presenting it. A stolen or fabricated ID with a valid checksum would pass format validation. For this reason, the `CheckIdKycAdapter` always returns `NEEDS_REVIEW` rather than `VERIFIED`. The pre-check result is recorded on the checklist item, but the firm must still perform manual verification (examining the physical ID document, comparing the photo). This distinction is important: returning `VERIFIED` for format-only validation would create a false sense of compliance and expose the firm to FICA findings.

Adding a new provider requires only a new adapter class:

```java
@Component
@IntegrationAdapter(domain = IntegrationDomain.KYC_VERIFICATION, slug = "newprovider")
public class NewProviderKycAdapter implements KycVerificationPort {
    private final SecretStore secretStore;
    // Implementation
}
```

The adapter is discovered at startup by the `IntegrationRegistry` via annotation scanning. No schema changes, no configuration changes, no migration. The integration settings UI dynamically lists available providers from `IntegrationRegistry.availableProviders(KYC_VERIFICATION)`.

**Consequences**:

- `IntegrationDomain` enum gains `KYC_VERIFICATION("noop")` as a new value. This is a backward-compatible enum extension — existing tenants are unaffected.
- New package `integration/kyc/` contains the port interface, adapters, request/response records, and the `KycVerificationService` orchestrator.
- The `ChecklistInstanceItem` entity gains five nullable verification columns. These are populated only when automated verification is used — manual verification remains the default path.
- POPIA consent is tracked per verification attempt in the `verification_metadata` JSONB column. The system records that the firm user acknowledged consent, not the consent itself.
- Firms without a configured KYC integration see no UI changes — the "Verify Now" button is conditionally rendered based on `IntegrationGuardService.isConfigured("KYC_VERIFICATION")`.
- Future providers (e.g., XDS, TransUnion SA, iIdentifii) can be added as adapter classes without architectural changes. The `KycVerificationPort` interface is provider-agnostic.
- Related: [ADR-085](ADR-085-auth-provider-abstraction.md) (provider abstraction pattern), [ADR-051](ADR-051-psp-adapter-design.md) (payment adapter pattern — structurally identical BYOAK approach)
