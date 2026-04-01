# Presigned URLs and S3 in Multi-Tenant SaaS

*Part 5 of "Modern Java for SaaS" — practical patterns from building production B2B software with Java 25, Spring Boot 4, and Hibernate 7. This is the final post.*

---

DocTeams stores documents in S3 — engagement letters, compliance documents, invoices, client uploads. In a multi-tenant system, file storage has a unique challenge: every tenant's files must be strictly isolated, and no tenant should ever be able to access another's documents.

The solution is presigned URLs with org-scoped key paths. The backend never proxies file content — it generates time-limited URLs that the frontend uses to upload and download directly from S3. Here's how it works.

## The Key Structure

Every S3 object has a key (path) that encodes the ownership hierarchy:

```
org/{orgId}/project/{projectId}/Q1-audit-report.pdf
org/{orgId}/customer/{customerId}/fica-id-copy.pdf
org/{orgId}/branding/logo.png
org/{orgId}/generated/invoice-INV-2026-042.pdf
org/{orgId}/org-docs/engagement-template.docx
```

The `org/{orgId}/` prefix is the isolation boundary. A presigned URL for Tenant A's file can only access keys under `org/org_abc123/`. Tenant B's files exist under `org/org_xyz789/` — structurally unreachable.

The backend validates keys with a regex before generating presigned URLs:

```java
private static final Pattern S3_KEY_PATTERN = Pattern.compile(
    "^org/[^/]+/(project/[^/]+|org-docs|customer/[^/]+|branding|generated|exports|templates/[^/]+)/[^/]+$");

private static void validateKey(String key) {
    if (key == null || !S3_KEY_PATTERN.matcher(key).matches()) {
        throw new IllegalArgumentException("Invalid storage key format");
    }
}
```

This prevents path traversal attacks. A malicious request can't ask for a presigned URL to `../other-org/secrets.pdf` — the regex rejects anything that doesn't match the expected structure.

## Presigned Upload URLs

When the frontend needs to upload a file, it asks the backend for a presigned upload URL:

```
POST /api/documents/upload-url
{ "projectId": "...", "fileName": "report.pdf", "contentType": "application/pdf" }

Response:
{ "uploadUrl": "https://s3.amazonaws.com/bucket/org/org_abc/project/proj_123/report.pdf?X-Amz-...",
  "key": "org/org_abc/project/proj_123/report.pdf",
  "expiresAt": "2026-03-15T10:05:00Z" }
```

The backend generates the key (ensuring the correct org prefix), creates a presigned PUT URL with a 5-minute expiry, and returns it. The frontend `PUT`s the file content directly to S3:

```typescript
const response = await api.post('/api/documents/upload-url', {
    projectId, fileName, contentType
});

// Direct upload to S3 — no backend proxy
await fetch(response.uploadUrl, {
    method: 'PUT',
    body: file,
    headers: { 'Content-Type': contentType }
});
```

The backend implementation:

```java
@Override
public PresignedUrl generateUploadUrl(String key, String contentType, Duration expiry) {
    validateKey(key);
    var putRequest = PutObjectRequest.builder()
        .bucket(bucketName)
        .key(key)
        .contentType(contentType)
        .build();

    var presignRequest = PutObjectPresignRequest.builder()
        .signatureDuration(expiry)
        .putObjectRequest(putRequest)
        .build();

    var presigned = s3Presigner.presignPutObject(presignRequest);
    return new PresignedUrl(presigned.url().toExternalForm(), Instant.now().plus(expiry));
}
```

## Presigned Download URLs

Downloads work the same way — the backend generates a presigned GET URL:

```java
@Override
public PresignedUrl generateDownloadUrl(String key, Duration expiry) {
    validateKey(key);
    var getRequest = GetObjectRequest.builder()
        .bucket(bucketName)
        .key(key)
        .build();

    var presignRequest = GetObjectPresignRequest.builder()
        .signatureDuration(expiry)
        .getObjectRequest(getRequest)
        .build();

    var presigned = s3Presigner.presignGetObject(presignRequest);
    return new PresignedUrl(presigned.url().toExternalForm(), Instant.now().plus(expiry));
}
```

The frontend receives the URL and opens it in a new tab or uses it as an `<a href>` download link. The file streams directly from S3 to the user's browser. The backend never touches the file content.

## Why Not Proxy?

Proxying file content through the backend is simpler to implement:

```java
// DON'T DO THIS for multi-tenant SaaS
@GetMapping("/api/documents/{id}/download")
public ResponseEntity<Resource> download(@PathVariable UUID id) {
    byte[] content = s3Client.getObject(...).readAllBytes();
    return ResponseEntity.ok()
        .header("Content-Disposition", "attachment; filename=" + doc.getFileName())
        .body(new ByteArrayResource(content));
}
```

Problems:
- **Memory.** Large files (50MB+ PDFs, client document bundles) are loaded entirely into JVM heap. With 20 concurrent downloads, that's 1GB of heap used for proxying.
- **Latency.** The file downloads from S3 to your server, then from your server to the client. Double the network time.
- **Connection holding.** Each download holds a servlet thread for the duration of the transfer. With Fluid Compute or traditional serverless, this wastes compute time on I/O waiting.
- **URL leakage.** The Phase 12 review caught a bug where the proxy endpoint leaked the internal S3 URL (with bucket name and region) in a response header. With presigned URLs, the client only sees a time-limited URL that expires.

Presigned URLs eliminate all of these: the file goes directly from S3 to the client, the backend only generates the URL (milliseconds, not minutes), and the URL expires automatically.

## LocalStack for Local Development

In development, we use LocalStack (an S3-compatible local service) instead of real AWS:

```yaml
# docker-compose.yml
localstack:
  image: localstack/localstack
  ports:
    - "4566:4566"
  environment:
    - SERVICES=s3
    - DEFAULT_REGION=us-east-1
```

The `S3StorageAdapter` connects to LocalStack using the same AWS SDK — the endpoint URL changes, but the code is identical:

```yaml
# application-local.yml
storage:
  provider: s3
  s3:
    bucket-name: docteams-local
    region: us-east-1
    endpoint: http://b2mash.local:4566  # LocalStack
    path-style-access: true
```

The `@ConditionalOnProperty` annotation on `S3StorageAdapter` means the same code runs against LocalStack locally and real S3 in production. No mock. No in-memory substitute. A real S3-compatible service.

## Tenant Isolation in Practice

The isolation model for S3 relies on two things:

**1. Key prefix enforcement.** The backend constructs the key using the current tenant's org ID from `RequestScopes.ORG_ID`. A tenant can never request a key for another org — the key is built server-side:

```java
String key = String.format("org/%s/project/%s/%s",
    RequestScopes.requireOrgId(),
    projectId,
    sanitizeFileName(fileName));
```

The `requireOrgId()` call reads from the `ScopedValue` bound by `TenantFilter`. It's the same org ID that was extracted from the JWT. A tenant can't override it.

**2. Presigned URL expiry.** Even if a presigned URL leaked (via browser history, shared bookmark, etc.), it expires in 5-15 minutes. The window of vulnerability is small and time-bounded.

For additional security in a compliance-sensitive environment, you could add S3 bucket policies that restrict access to specific IAM roles, or use S3 Object Lock for documents that must not be modified after creation. DocTeams doesn't need these yet, but the architecture supports them.

## The Storage Interface

The `StorageService` interface abstracts away the S3 implementation:

```java
public interface StorageService {
    String upload(String key, byte[] content, String contentType);
    String upload(String key, InputStream content, long contentLength, String contentType);
    byte[] download(String key);
    void delete(String key);
    PresignedUrl generateUploadUrl(String key, String contentType, Duration expiry);
    PresignedUrl generateDownloadUrl(String key, Duration expiry);
    List<String> listKeys(String prefix);
}
```

`S3StorageAdapter` is the production implementation. In tests, Testcontainers starts a LocalStack container and injects the same adapter with a different endpoint URL. No mock — real S3-compatible storage.

If you needed to support a different storage backend (Azure Blob Storage, Google Cloud Storage, or even a local filesystem for on-premise deployments), you'd implement `StorageService` and swap via `@ConditionalOnProperty`. The rest of the application — services, controllers, domain events — doesn't change.

---

*This is the final post in "Modern Java for SaaS." The series covered:*

1. *[Java 25 + Spring Boot 4](01-java-25-spring-boot-4.md) — what's actually different*
2. *[Integration Testing with Testcontainers](02-testcontainers-no-mocks.md) — no mocks, no mercy*
3. *[The One-Service-Call Controller](03-one-service-call-controller.md) — a convention that scales*
4. *[Domain Events Without a Message Broker](04-domain-events-without-broker.md) — in-process event bus*
5. *[Presigned URLs and S3](05-presigned-urls-s3.md) — file storage in multi-tenant SaaS*

*The entire series draws from [DocTeams](https://github.com/...) — a production B2B SaaS platform with 83 entities, 240K lines of Java, and schema-per-tenant multitenancy. The foundation is being extracted into an open-source template. [Subscribe](#) for updates.*
