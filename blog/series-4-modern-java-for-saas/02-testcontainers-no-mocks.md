# Integration Testing with Testcontainers: No Mocks, No Mercy

*Part 2 of "Modern Java for SaaS" — practical patterns from building production B2B software with Java 25, Spring Boot 4, and Hibernate 7.*

---

Early in the project, I made a decision that shaped everything: no mocking the database. Not in integration tests. Not in service tests. Not ever.

The reason is a lesson from Phase 2. I built a shared-schema tenancy model with RLS policies. The mocked tests passed — H2 doesn't support RLS, so the mocks just verified that the right SQL was generated. The production migration failed because the RLS policies had syntax errors that H2 couldn't catch.

From that point forward: real PostgreSQL in every test. Testcontainers spins up a PostgreSQL container, the test runs against it, the container is destroyed. The test proves the code works against the same database engine that runs in production.

## The Test Setup

```java
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProjectIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private TenantProvisioningService provisioningService;
    @Autowired private ProjectRepository projectRepository;

    private static final String ORG_ID = "org_project_test";

    @BeforeAll
    void provisionTenant() {
        provisioningService.provisionTenant(ORG_ID, "Test Org", null);
    }

    @Test
    void createProject_returnsCreated() throws Exception {
        mockMvc.perform(post("/api/projects")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name": "Q1 Audit", "description": "Annual audit"}
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("Q1 Audit"))
            .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    private JwtRequestPostProcessor ownerJwt() {
        return jwt().jwt(j -> j
            .subject("user_owner")
            .claim("o", Map.of("id", ORG_ID, "rol", "owner")));
    }
}
```

Key elements:

**`@Import(TestcontainersConfiguration.class)`** — a shared configuration class that starts one PostgreSQL container and reuses it across all test classes in the suite:

```java
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgresContainer() {
        return new PostgreSQLContainer<>("postgres:16-alpine")
            .withReuse(true);
    }
}
```

`withReuse(true)` keeps the container alive across test classes. Without it, every `@SpringBootTest` class spins up a new container — adding 3-5 seconds per class. With 455 test files, that's 20+ minutes of just container starts.

**`@TestInstance(PER_CLASS)`** — allows `@BeforeAll` to be non-static, so it can use injected beans. The tenant is provisioned once per test class, not once per test method.

**`provisioningService.provisionTenant()`** — creates a real tenant schema with real Flyway migrations. The test operates in the same schema-per-tenant model as production.

**`ownerJwt()`** — creates a mock JWT with Clerk v2 nested claims. `TenantFilter` extracts the org ID and binds the tenant. `MemberFilter` lazy-creates the member. The full auth flow runs.

## The Test Factories

Creating test data directly via constructors is tedious and fragile. Test factories centralize object creation:

```java
public final class TestCustomerFactory {

    /** Creates a customer already in ACTIVE state — safe for guarded operations. */
    public static Customer createActiveCustomer(
            String name, String email, UUID createdBy) {
        return new Customer(name, email, null, null, null,
            createdBy, CustomerType.INDIVIDUAL, LifecycleStatus.ACTIVE);
    }

    /** Creates a customer with prerequisite custom fields pre-filled. */
    public static Customer createActiveCustomerWithPrerequisiteFields(
            String name, String email, UUID createdBy) {
        var customer = createActiveCustomer(name, email, createdBy);
        customer.setCustomFields(new HashMap<>(Map.of(
            "address_line1", "123 Test Street",
            "city", "Test City",
            "country", "ZA",
            "tax_number", "VAT123456")));
        return customer;
    }
}
```

The `createActiveCustomer()` method exists because of the lifecycle guard. A customer in PROSPECT status can't have projects, invoices, or time entries. Every test that creates related entities needs an ACTIVE customer — and building one from PROSPECT requires completing a full compliance checklist. The factory shortcut saves dozens of lines per test.

This was a hard-won lesson. When I added the lifecycle guard in Phase 14, about 60 existing tests broke because they created customers without specifying a status (defaulting to PROSPECT) and then tried to create projects for them.

## ScopedValue in Tests

Tests that need tenant context use `ScopedValue.where().run()`:

```java
@Test
void profitabilityReport_calculatesMarginCorrectly() {
    ScopedValue.where(RequestScopes.TENANT_ID, testSchema)
        .where(RequestScopes.MEMBER_ID, testMemberId)
        .run(() -> {
            // Create test data
            var customer = TestCustomerFactory.createActiveCustomer(
                "Acme", "acme@test.com", testMemberId);
            customerRepository.save(customer);

            var project = new Project("Audit Q1", "Desc", testMemberId);
            project.setCustomerId(customer.getId());
            projectRepository.save(project);

            // Create time entry with rate snapshot
            var entry = new TimeEntry(project.getId(), testMemberId,
                LocalDate.now(), BigDecimal.valueOf(8));
            entry.setBillingRate(BigDecimal.valueOf(1500));
            entry.setCostRate(BigDecimal.valueOf(450));
            timeEntryRepository.save(entry);

            // Assert
            var report = reportService.getProjectProfitability();
            assertThat(report).hasSize(1);
            assertThat(report.getFirst().getMargin())
                .isEqualByComparingTo(BigDecimal.valueOf(8400)); // (1500-450) * 8
        });
    // ScopedValue auto-cleaned up. No @AfterEach needed.
}
```

No `@BeforeEach` to set up tenant context. No `@AfterEach` to clean up. The `run()` lambda scopes everything. If the assertion throws, the bindings still clean up — it's structural, not procedural.

## The Two-Pass Build Strategy

Running 455 test files produces verbose output. Maven's Surefire reports are helpful for machines but overwhelming for humans (or AI agents). I use a two-pass approach:

**Pass 1: Silent full build**

```bash
./mvnw clean verify -q 2>&1 > /tmp/mvn-build.log
```

The `-q` flag suppresses everything except errors. If the build passes, the log is nearly empty. If it fails, the exit code is non-zero.

**Pass 2: Targeted re-run of failures**

```bash
# Find failed test classes from Surefire XML
grep -rl 'failures="[1-9]' target/surefire-reports/TEST-*.xml \
    | sed 's/.*TEST-//' | sed 's/\.xml//'

# Re-run only those classes with full output
./mvnw test -Dtest=ProjectIntegrationTest,InvoiceIntegrationTest -pl backend
```

This keeps context usage minimal when working with AI agents (see [Post 2 of Series 1](../series-1-one-dev-843-prs/02-scout-builder-pattern.md)). A full Maven build produces 500-1,100 lines of output. The two-pass approach reduces it to ~10 lines per pass.

## What We Test (and What We Don't)

**We test:**
- API endpoint contracts (request → response shape, status codes)
- Authorization (capability checks, 403 for unauthorized)
- Tenant isolation (queries scoped to correct schema)
- Domain logic (lifecycle transitions, rate resolution, invoice generation)
- Database constraints (unique indexes, foreign keys, check constraints)
- Migration correctness (DDL runs without errors on real PostgreSQL)

**We don't test:**
- Repository method signatures (Spring Data generates these — testing them is testing Spring)
- Controller request parsing (Spring MVC handles this — covered by MockMvc tests implicitly)
- Third-party library behavior (Hibernate, Flyway, S3 client)

**We mock:**
- External services (S3 — using LocalStack via Testcontainers)
- Email sending (mock SMTP server)
- Nothing else. No mocked repositories. No mocked services. No in-memory database substitutes.

## The Confidence Factor

455 test files. Real PostgreSQL. Real Flyway migrations. Real tenant schemas. Real JWT authentication.

When a test passes, it means: this code works against the same database, with the same isolation model, using the same auth flow, as production. There's no gap between "it passes in tests" and "it works in production" — at least not for the database layer.

The Phase 2 RLS incident taught me this: **mocks tell you your code does what you think it does. Integration tests tell you your code does what it actually does.** Those are different things.

---

*Next in this series: [The One-Service-Call Controller: A Convention That Scales](03-one-service-call-controller.md)*

*Previous: [Java 25 + Spring Boot 4](01-java-25-spring-boot-4.md)*
