# ADR-177: API Key Encryption Approach

**Status**: Accepted
**Date**: 2026-03-12
**Phase**: Phase 45 — In-App AI Assistant (BYOAK)

## Context

Tenants store their own LLM API keys in OrgSettings (BYOAK model). These keys grant access to the tenant's Anthropic/OpenAI account and can incur significant costs if leaked. The keys must be encrypted at rest in the database and decrypted only when making LLM calls. The question is where and how to perform the encryption: at the application level, at the database level, or via an external secrets manager.

## Options Considered

### 1. **Application-level AES-256-GCM (chosen)** — encrypt/decrypt in Java with a config-managed key

An `AiKeyEncryptionService` uses `javax.crypto.Cipher` with AES/GCM/NoPadding. The encryption key is loaded from application configuration (`app.ai.encryption-key`, set via environment variable in production). Each encryption operation generates a random 12-byte IV, prepended to the ciphertext. Decryption extracts the IV from the stored value.

- Pros:
  - Portable — works with any database, no database-specific extensions required
  - Encryption key in app config (environment variable in production) — standard secrets management
  - AES-256-GCM provides authenticated encryption (confidentiality + integrity)
  - Decrypted only at call time — key material exists in memory briefly
  - Simple implementation (~50 lines of Java)
  - Easy to test — encrypt/decrypt round-trip in unit tests
- Cons:
  - Encryption key must be managed outside the application (env var, secrets manager)
  - Key rotation requires re-encrypting all stored keys (a batch operation)
  - If the encryption key is compromised, all tenant API keys are exposed

### 2. **Database-level pgcrypto** — encrypt/decrypt in PostgreSQL

Use PostgreSQL's `pgcrypto` extension with `pgp_sym_encrypt()` / `pgp_sym_decrypt()`. The encryption key is passed as a parameter in SQL queries.

- Pros:
  - Encryption happens at the database layer — no application code needed
  - PostgreSQL handles IV generation and key derivation
  - Can be used in raw SQL queries and migrations
- Cons:
  - Ties encryption to PostgreSQL — not portable to other databases
  - The encryption key must be passed in every SQL query that reads/writes the column — visible in query logs unless logging is carefully configured
  - Hibernate/JPA integration is awkward — requires native queries or custom types
  - `pgcrypto` extension must be enabled per schema (tenant schemas would each need it)
  - Harder to unit test without a PostgreSQL instance

### 3. **External secrets manager** — HashiCorp Vault or AWS Secrets Manager

Store API keys in an external secrets manager. The backend retrieves the key at call time via the secrets manager API. The database stores only a reference (key ID), not the encrypted key.

- Pros:
  - Best-in-class security — keys never touch the database
  - Built-in key rotation, access logging, and lease management
  - Separation of concerns — the application never handles raw encryption
- Cons:
  - Requires additional infrastructure (Vault server or AWS Secrets Manager)
  - Adds network latency to every LLM call (secrets manager lookup)
  - Increased operational complexity — another service to deploy, monitor, and maintain
  - Cost: AWS Secrets Manager charges per secret per month + per API call
  - Over-engineered for v1 with a small number of tenants
  - Local development requires running Vault or mocking the secrets manager

## Decision

Use application-level AES-256-GCM encryption. The `AiKeyEncryptionService` handles all encrypt/decrypt operations using a platform encryption key from application configuration.

## Rationale

For a v1 BYOAK feature with a manageable number of tenants, application-level encryption provides the right balance of security and simplicity. AES-256-GCM is the industry standard for authenticated encryption — it provides both confidentiality and integrity, preventing both reading and tampering with the ciphertext. The implementation is ~50 lines of well-understood Java cryptography.

The encryption key lives in application configuration (`app.ai.encryption-key`), which maps to an environment variable in production. This follows the same pattern used for other secrets in the application (database credentials, S3 keys). No new infrastructure is required.

pgcrypto ties the implementation to PostgreSQL and creates awkward JPA integration patterns. The multi-tenant schema-per-tenant architecture would require enabling the extension in each tenant schema. The external secrets manager is the right long-term solution for a production system at scale, but adds infrastructure complexity that is not justified for v1. It can be adopted later by replacing `AiKeyEncryptionService` with a Vault/AWS Secrets Manager client — the interface is the same (encrypt/decrypt), only the implementation changes.

Key rotation (re-encrypting all tenant keys with a new encryption key) is a future concern. When needed, it is a straightforward batch operation: decrypt with old key, encrypt with new key, update each row. This can be implemented as a management endpoint or a migration script.

## Consequences

- **Positive**: Simple implementation, portable across databases, standard Java cryptography, easy to test, no new infrastructure.
- **Negative**: Encryption key must be managed as an environment variable. Key rotation requires a batch re-encryption operation. Single encryption key protects all tenant API keys (compromise of the key exposes all).
- **Neutral**: The `AiKeyEncryptionService` interface is implementation-agnostic — swapping to Vault or AWS Secrets Manager later requires changing only the service implementation, not the callers.
