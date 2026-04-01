# Java 25 + Spring Boot 4: What's Actually Different for SaaS Developers

*This is Part 1 of "Modern Java for SaaS" — practical patterns from building production B2B software with Java 25, Spring Boot 4, and Hibernate 7.*

---

When I started DocTeams, I picked the latest stack: Java 25, Spring Boot 4.0.2, Hibernate 7. Not because I like living on the edge — because I didn't want to upgrade later. Migration tax compounds. Better to pay it once at the start.

Eight weeks and 240,000 lines of Java later, here's what actually matters in the new stack. Not everything that's new — just what changed how I write SaaS code.

## ScopedValue is Final (JEP 506)

This is the headliner for multi-tenant applications. `ScopedValue` replaces `ThreadLocal` for request-scoped context.

I covered this in depth in [the multi-tenancy series](../series-2-multi-tenant-from-scratch/02-scopedvalue-over-threadlocal.md), but the summary: immutable bindings with scope-based cleanup. No `ThreadLocal.remove()` in a `finally` block. No leak risk. Virtual-thread safe.

```java
// Old: ThreadLocal (mutable, manual cleanup, leaks on error)
TenantContext.setTenantId(schema);
try { filterChain.doFilter(req, res); }
finally { TenantContext.clear(); }

// New: ScopedValue (immutable, auto-cleanup, virtual-thread safe)
ScopedValue.where(RequestScopes.TENANT_ID, schema)
    .run(() -> filterChain.doFilter(req, res));
```

If you're building multi-tenant software, this is the single best reason to be on Java 25.

## Records for DTOs

Java records have been around since Java 14 (preview), but they're now the idiomatic way to write DTOs, request/response objects, and domain events:

```java
// Request DTO — immutable, compact, auto-generates equals/hashCode/toString
public record CreateInvoiceRequest(
    UUID customerId,
    LocalDate issueDate,
    LocalDate dueDate,
    String notes) {}

// Response DTO
public record InvoiceResponse(
    UUID id,
    String invoiceNumber,
    String status,
    BigDecimal total,
    LocalDate dueDate) {

    public static InvoiceResponse from(Invoice invoice) {
        return new InvoiceResponse(
            invoice.getId(),
            invoice.getInvoiceNumber(),
            invoice.getStatus().name(),
            invoice.getTotal(),
            invoice.getDueDate());
    }
}

// Domain event — immutable record implementing marker interface
public record InvoiceSentEvent(
    String eventType,
    UUID entityId,
    UUID actorMemberId,
    String tenantId,
    Instant occurredAt,
    Map<String, Object> details) implements DomainEvent {}
```

In DocTeams, every DTO, every event, and every projection is a record. Entities remain classes (Hibernate needs mutable state for dirty checking), but everything else is a record. It's less code, fewer bugs (immutability by default), and better readability.

## Pattern Matching in Switch

Java 21 finalized pattern matching for switch, and it's become my default for type-based dispatch:

```java
// Test factory: generate appropriate test values by field type
private static Object testValueFor(FieldType fieldType) {
    return switch (fieldType) {
        case TEXT -> "test_value";
        case NUMBER -> 42;
        case DATE -> "2026-01-01";
        case DROPDOWN -> "option_1";
        case BOOLEAN -> true;
        case CURRENCY -> Map.of("amount", 100, "currency", "ZAR");
        case URL -> "https://example.com";
        case EMAIL -> "test@example.com";
        case PHONE -> "+27123456789";
    };
}
```

The compiler enforces exhaustiveness — if I add a new `FieldType` enum value, every switch expression that handles it must be updated. No silent fallthrough.

## Spring Boot 4 Breaking Changes That Matter

### Starter Renames

```xml
<!-- Old (Spring Boot 3.x) -->
<artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
<artifactId>spring-boot-starter-aop</artifactId>

<!-- New (Spring Boot 4.x) -->
<artifactId>spring-boot-starter-security-oauth2-resource-server</artifactId>
<artifactId>spring-boot-starter-aspectj</artifactId>
```

The renames are cosmetic but will break your build if you upgrade without checking. The `spring-boot-starter-aop` → `spring-boot-starter-aspectj` rename caught me because the old name gives no deprecation warning — it just doesn't exist.

### AutoConfigureMockMvc Moved

```java
// Old (Spring Boot 3.x)
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;

// New (Spring Boot 4.x)
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
```

Every test file that uses `@AutoConfigureMockMvc` needs this import change. With 455 test files, this was a significant find-and-replace.

### DataSourceProperties Removed

```java
// Old: use DataSourceProperties to configure
@Bean
@ConfigurationProperties("spring.datasource")
public DataSourceProperties dataSourceProperties() { ... }

// New: configure HikariDataSource directly
@Bean
@ConfigurationProperties("spring.datasource.app")
public HikariDataSource appDataSource() {
    return new HikariDataSource();
}
```

Note: the property name is `jdbc-url` (with hyphen), not `url`. HikariCP uses `jdbc-url` directly, and Spring Boot 4 no longer translates `url` → `jdbc-url` for you. This one took an hour to debug — the error message just says "no JDBC URL specified."

### OSIV Disabled by Default

Spring Boot 4 sets `spring.jpa.open-in-view: false` by default (it was `true` in Boot 3). For multi-tenant applications, this is correct — OSIV creates an EntityManager at request start that pins to whatever tenant was active at that moment. For internal endpoints that skip the TenantFilter, the EntityManager pins to "public" and all queries hit the wrong schema.

If you relied on OSIV for lazy loading in controllers, you'll need to either add explicit fetching in services or re-enable it. I recommend keeping it off and using DTOs.

## Hibernate 7 Gotchas

### Spring Security Test JWT Mock

`SecurityMockMvcRequestPostProcessors.jwt()` does NOT invoke your `JwtAuthenticationConverter`. In Spring Boot 3, this was already true, but it's more noticeable in Boot 4 because the converter is where org claims get extracted.

```java
// This does NOT run ClerkJwtAuthenticationConverter:
mockMvc.perform(get("/api/projects").with(jwt()));

// You must set authorities explicitly:
mockMvc.perform(get("/api/projects")
    .with(jwt().jwt(j -> j
        .subject("user_123")
        .claim("o", Map.of("id", "org_abc", "rol", "owner"))
    )));
```

The claim is present in the JWT, so `TenantFilter` can extract the org ID. But Spring Security authorities aren't set by the converter — they're empty. Since DocTeams uses `@RequiresCapability` (which reads from `RequestScopes`, not authorities), this is fine. But if you use `@PreAuthorize("hasRole('ADMIN')")`, you need to add `.authorities()` to the mock.

### Spotless + Google Java Format Versions

Java 25 requires specific versions of the formatting tools:

```xml
<plugin>
    <groupId>com.diffplug.spotless</groupId>
    <artifactId>spotless-maven-plugin</artifactId>
    <version>3.2.1</version>  <!-- Must be 3.2.1+ for Java 25 -->
    <configuration>
        <java>
            <googleJavaFormat>
                <version>1.28.0</version>  <!-- Must be 1.28.0+ -->
            </googleJavaFormat>
        </java>
    </configuration>
</plugin>
```

Spotless 2.x fails with `NoSuchMethodError` on Java 25. Google Java Format below 1.28.0 fails with internal AST errors. These version requirements aren't well-documented — I found them through trial and error.

## What I Wish I'd Known

**Spring Retry replaces Resilience4j.** Resilience4j doesn't have Spring Boot 4 support yet. Use `spring-retry` 2.0.12 with `@Retryable` for retry logic. The API is simpler anyway.

**Flyway must be disabled for manual control.** Set `spring.flyway.enabled: false` and manage migrations through your own `FlywayConfig` bean. The auto-configuration doesn't support dual-path migrations (global + tenant).

**`@ConfigurationProperties` binding changed subtly.** Property names are now stricter about kebab-case. `jdbc-url` works, `jdbcUrl` does not. `connection-init-sql` works, `connectionInitSql` does not. This is technically documented but catches everyone on upgrade.

**Test `@ServiceConnection` only auto-configures the default datasource.** If you have custom datasource beans (like dual app + migration datasources), use `DynamicPropertyRegistrar` to bridge Testcontainers properties to your custom beans. `@ServiceConnection` won't find them.

The stack is solid. The edge cases are real. And the benefits — ScopedValue, records everywhere, pattern matching, and a cleaner Spring Boot API — are worth the upgrade tax.

---

*Next in this series: [Integration Testing with Testcontainers: No Mocks, No Mercy](02-testcontainers-no-mocks.md)*
