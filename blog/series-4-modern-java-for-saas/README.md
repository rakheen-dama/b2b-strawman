# Series 4: "Modern Java for SaaS"

Practical patterns from building production B2B software with Java 25, Spring Boot 4, and Hibernate 7.

## Posts

| # | Title | Status | Words |
|---|-------|--------|-------|
| 01 | [Java 25 + Spring Boot 4](01-java-25-spring-boot-4.md) | Draft | ~2,400 |
| 02 | [Integration Testing with Testcontainers](02-testcontainers-no-mocks.md) | Draft | ~2,200 |
| 03 | [The One-Service-Call Controller](03-one-service-call-controller.md) | Draft | ~2,300 |
| 04 | [Domain Events Without a Message Broker](04-domain-events-without-broker.md) | Draft | ~2,400 |
| 05 | [Presigned URLs and S3](05-presigned-urls-s3.md) | Draft | ~2,200 |

**Total: ~11,500 words across 5 posts**

## Series Arc

- **01**: What's actually different in the new stack. ScopedValue, records, starter renames, HikariCP gotchas, Spotless versions.
- **02**: Real PostgreSQL in every test. Testcontainers setup, test factories, ScopedValue in tests, two-pass builds.
- **03**: The controller convention. One service call, declarative auth, centralized error handling. Why it scales to 104 controllers.
- **04**: In-process events via ApplicationEventPublisher. AFTER_COMMIT listeners, ScopedValue rebinding, when to upgrade to Kafka.
- **05**: Presigned URLs for direct S3 access. Key validation, org-scoped paths, LocalStack for dev, tenant isolation.

## Code Examples Source

All code examples are from the actual codebase:
- `AcceptanceController.java` — one-service-call pattern
- `GlobalExceptionHandler.java` — RFC 9457 ProblemDetail
- `NotificationEventHandler.java` — @TransactionalEventListener + ScopedValue
- `S3StorageAdapter.java` — presigned URLs
- `TestCustomerFactory.java` — test factories
- `TestcontainersConfiguration.java` — test setup
- `pom.xml` — dependency versions

## Publishing Plan

- Frequency: Monthly
- Launch after Series 1-3 establish audience (~Month 5)
- Strong SEO potential: "Spring Boot 4 ScopedValue", "Testcontainers integration test"

## Before Publishing Checklist

- [ ] Verify all code examples match current codebase
- [ ] Verify Spring Boot 4 / Hibernate 7 version-specific claims
- [ ] Verify starter rename details against Spring Boot 4 release notes
- [ ] Add links to relevant Spring documentation
- [ ] Check Spotless/google-java-format version requirements are still current
