# ADR-090: Secret Storage Strategy

**Status**: Accepted

**Context**:

Phase 21's BYOAK framework requires tenants to provide API keys for external services (e.g., Xero API key, OpenAI API key). These credentials must be stored securely -- they represent access to the tenant's own accounts with external vendors. A breach exposing these keys would compromise not our system but the tenant's external accounts, making this a trust-critical concern.

The system uses dedicated-schema-per-tenant isolation (Phase 13, [ADR-064](ADR-064-dedicated-schema-only.md)), meaning each tenant's secrets are physically separated in their own PostgreSQL schema. However, the application server has access to all schemas, so a SQL injection or application-level vulnerability could expose secrets across tenants. The storage strategy must provide defense-in-depth: even if the database is compromised (backup theft, unauthorized access), the secrets should not be usable without the encryption key. The system currently runs on ECS/Fargate with secrets injected via environment variables, and the infrastructure is managed by a small team without a dedicated secrets management platform.

**Options Considered**:

1. **AES-256-GCM in database with app-level encryption (chosen)** -- Encrypt each secret using AES-256-GCM (authenticated encryption) with a 256-bit master key sourced from the `INTEGRATION_ENCRYPTION_KEY` environment variable. Each secret gets a unique 96-bit random IV. The ciphertext (including GCM auth tag), IV, and key version are stored in an `org_secrets` table in the tenant's schema. Decryption happens in the application layer at secret retrieval time.
   - Pros:
     - Zero additional infrastructure: uses the existing PostgreSQL database and Java's built-in `javax.crypto` (AES-256-GCM is a JDK standard cipher)
     - AES-256-GCM provides both confidentiality and integrity (authenticated encryption) -- tampered ciphertext is detected and rejected
     - Per-secret random IV ensures identical plaintext values produce different ciphertext
     - `key_version` column enables future key rotation without downtime (encrypt new secrets with new key, re-encrypt old secrets in batch)
     - Schema isolation means a SQL injection targeting one tenant's schema cannot access another tenant's secrets
     - Fail-fast at startup: `@PostConstruct` validates the master key exists and is the correct length
   - Cons:
     - The application server holds the master key in memory -- if the application process is compromised, all secrets are accessible
     - Key rotation requires a custom batch process (re-encrypt all secrets with new key)
     - No hardware security module (HSM) backing -- the master key is a plain environment variable
     - No audit trail of secret access at the encryption layer (application-level audit logging covers this, but not at the crypto level)

2. **HashiCorp Vault** -- Use Vault's Transit secrets engine for encryption/decryption, or its KV secrets engine for direct secret storage. The application authenticates to Vault using an AppRole or AWS IAM auth method, and Vault handles all cryptographic operations.
   - Pros:
     - Industry-standard secrets management with battle-tested security
     - Master key never leaves Vault's HSM-backed seal -- application only sends plaintext for encryption and receives ciphertext
     - Built-in audit logging of every secret access
     - Dynamic secrets, lease management, and automatic rotation capabilities
     - Transit engine supports key versioning and rotation natively
   - Cons:
     - Requires deploying and operating Vault (or paying for HCP Vault) -- significant infrastructure cost for a v1 feature
     - Adds a runtime dependency: if Vault is unavailable, no secrets can be retrieved, blocking all integration operations
     - Latency per operation: every encrypt/decrypt is a network call to Vault (typically 2-5ms)
     - The team has no existing Vault expertise, adding operational risk
     - Over-engineered for the current scale: a handful of API keys per tenant does not justify a dedicated secrets infrastructure

3. **AWS KMS envelope encryption** -- Use AWS KMS to generate a data encryption key (DEK), encrypt secrets locally with the DEK, and store the KMS-encrypted DEK alongside the ciphertext. Decryption requires a KMS call to unwrap the DEK, then local decryption with the DEK.
   - Pros:
     - The master key (CMK) is managed by AWS and never leaves KMS hardware
     - Envelope encryption minimises KMS API calls: one call to unwrap the DEK, then local decryption for all secrets
     - AWS IAM policies provide fine-grained access control to the CMK
     - KMS audit trail via CloudTrail
   - Cons:
     - Adds AWS KMS dependency and cost (~$1/month per key + $0.03/10K API calls -- minimal, but adds vendor lock-in)
     - DEK caching strategy adds complexity: how long to cache the unwrapped DEK? Must be invalidated on key rotation.
     - LocalStack KMS emulation has known limitations -- local development may behave differently from production
     - Requires IAM role configuration for the ECS task -- adds infra complexity
     - Overkill for the current scale: the benefit over app-level encryption is the HSM-backed CMK, which matters at enterprise scale but not for a v1 SaaS starter

4. **AWS Secrets Manager (external)** -- Store each tenant's API keys directly in AWS Secrets Manager as named secrets (e.g., `docteams/{tenantId}/accounting/api_key`). The application reads secrets from Secrets Manager at runtime.
   - Pros:
     - Fully managed: no encryption code to write, no key management
     - Built-in rotation with Lambda functions
     - Native IAM access control and CloudTrail audit
   - Cons:
     - Cost: $0.40/secret/month. At 100 tenants with 4 domains each, that's $160/month for secrets alone
     - API latency on every secret retrieval (cached, but cache invalidation on rotation is complex)
     - Secrets Manager has no concept of tenant schema isolation -- access control is purely IAM-based, which doesn't map to the app's multitenancy model
     - Secret naming convention must be carefully managed to prevent cross-tenant access
     - LocalStack Secrets Manager emulation adds development complexity
     - Tightly couples the secret storage strategy to AWS, reducing portability

**Decision**: AES-256-GCM in database with app-level encryption (Option 1).

**Rationale**: For a v1 BYOAK feature storing a small number of API keys per tenant, app-level AES-256-GCM encryption in the existing database provides the best balance of security, simplicity, and operational cost. The threat model this addresses is database compromise (backup theft, unauthorized read access, SQL injection resulting in data exfiltration) -- in all these scenarios, the encrypted secrets are useless without the master key, which lives only in the application's environment variables on ECS/Fargate.

The threat this does NOT address is application-level compromise (an attacker with code execution on the application server). In that scenario, the attacker can access the master key in memory and decrypt all secrets. This is acceptable for v1 because: (a) application-level compromise is a catastrophic breach regardless of secret storage strategy (the attacker could also steal session tokens, database credentials, etc.), and (b) mitigating this threat requires HSM-backed key management (Vault or KMS), which is a disproportionate investment for the current scale.

The migration path to Vault or KMS is clean: the `SecretStore` interface abstracts the storage mechanism, and the `key_version` column supports key rotation. When the platform reaches a scale that justifies dedicated secrets infrastructure, a `VaultSecretStore` or `KmsEnvelopeSecretStore` implementation can replace `EncryptedDatabaseSecretStore` without any changes to domain services.

**Consequences**:

- Positive:
  - Zero infrastructure additions: no Vault cluster, no KMS keys, no Secrets Manager entries
  - All secrets are tenant-isolated by schema -- no cross-tenant access even at the database level
  - Fail-fast startup validation prevents running without encryption in production
  - The `SecretStore` interface provides a clean seam for future migration to Vault/KMS
  - `key_version` column enables non-disruptive key rotation when needed

- Negative:
  - Master key in environment variable is the weakest link -- must be rotated if an ECS task definition is compromised
  - No HSM backing: the key exists in plaintext in the application's memory
  - Key rotation is a custom batch operation (read all secrets, decrypt with old key, encrypt with new key, update `key_version`)
  - Operational burden: the team must manage the `INTEGRATION_ENCRYPTION_KEY` env var across environments

- Neutral:
  - The `org_secrets` table lives in each tenant's schema, alongside `org_integrations`. No global table needed.
  - The encryption key is shared across all tenants (single master key). Per-tenant keys would require a key-per-tenant management system, which is Vault territory.
