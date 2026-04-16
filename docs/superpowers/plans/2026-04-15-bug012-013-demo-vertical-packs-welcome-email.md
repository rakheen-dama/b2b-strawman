# BUG-012 + BUG-013: Demo Org Vertical Packs & Welcome Email

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix demo org provisioning so vertical-specific packs (legal/accounting) are installed correctly, and add a welcome email with temp credentials to the demo user.

**Architecture:** Two independent bugs sharing the same provisioning flow. BUG-012 is a value mismatch: the frontend sends `"LEGAL"` but the backend registry keys are `"legal-za"`. Fix by aligning frontend values to backend profile IDs and adding a validation guard. BUG-013 adds a `DemoWelcomeEmailService` using `Optional<JavaMailSender>` (same pattern as `AccessRequestService`) to send a Thymeleaf-rendered welcome email after provisioning.

**Tech Stack:** Spring Boot 4 (Java 25), Next.js 16 (TypeScript), Thymeleaf (email templates), Zod (form validation), Vitest (frontend tests), JUnit 5 + MockMvc (backend tests)

**Bug references:** `documentation/bugs.md` — BUG-012, BUG-013

---

## File Structure

### BUG-012 (Vertical Profile Mismatch)

| Action | File | Responsibility |
|--------|------|----------------|
| Modify | `frontend/lib/schemas/demo-provision.ts` | Change Zod enum from `["GENERIC","ACCOUNTING","LEGAL"]` to `["consulting-generic","accounting-za","legal-za"]` |
| Modify | `frontend/app/(app)/platform-admin/demo/demo-provision-form.tsx` | Update `VERTICAL_OPTIONS` values to match backend profile IDs |
| Modify | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/demo/seed/DemoDataSeeder.java` | Update switch to match on full profile IDs (`"legal-za"`, `"accounting-za"`) |
| Modify | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/demo/DemoProvisionService.java` | Add validation guard: reject unknown vertical profiles before provisioning |
| Modify | `backend/src/test/java/io/b2mash/b2b/b2bstrawman/demo/DemoProvisionServiceTest.java` | Update test payloads to use correct profile IDs; add profile validation test |
| Modify | `backend/src/test/java/io/b2mash/b2b/b2bstrawman/demo/seed/DemoDataSeederIntegrationTest.java` | Update dispatcher test to use full profile IDs |

### BUG-013 (Welcome Email)

| Action | File | Responsibility |
|--------|------|----------------|
| Create | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/demo/DemoWelcomeEmailService.java` | Send welcome email via platform SMTP (no tenant context needed) |
| Create | `backend/src/main/resources/templates/email/demo-welcome.html` | Thymeleaf content template for welcome email |
| Modify | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/demo/DemoProvisionService.java` | Call DemoWelcomeEmailService after provisioning (non-fatal) |
| Create | `backend/src/test/java/io/b2mash/b2b/b2bstrawman/demo/DemoWelcomeEmailServiceTest.java` | Verify email rendering and send; verify non-fatal on failure |

---

## Task 1: Fix frontend vertical profile values (BUG-012)

**Files:**
- Modify: `frontend/lib/schemas/demo-provision.ts`
- Modify: `frontend/app/(app)/platform-admin/demo/demo-provision-form.tsx`

- [ ] **Step 1: Update the Zod schema to accept backend profile IDs**

In `frontend/lib/schemas/demo-provision.ts`, change the enum values:

```typescript
import { z } from "zod";

export const demoProvisionSchema = z.object({
  organizationName: z
    .string()
    .trim()
    .min(1, "Organization name is required")
    .max(255, "Organization name must be 255 characters or fewer"),
  verticalProfile: z.enum(["consulting-generic", "accounting-za", "legal-za"], {
    message: "Please select a vertical profile",
  }),
  adminEmail: z.string().trim().min(1, "Email is required").email("Invalid email address"),
  seedDemoData: z.boolean(),
});

export type DemoProvisionFormData = z.infer<typeof demoProvisionSchema>;
```

- [ ] **Step 2: Update the form radio options to use backend profile IDs**

In `frontend/app/(app)/platform-admin/demo/demo-provision-form.tsx`, update `VERTICAL_OPTIONS` and the default value:

```typescript
const VERTICAL_OPTIONS = [
  {
    value: "consulting-generic" as const,
    label: "Generic",
    description: "Marketing agency / consultancy",
  },
  {
    value: "accounting-za" as const,
    label: "Accounting",
    description: "South African accounting firm",
  },
  {
    value: "legal-za" as const,
    label: "Legal",
    description: "South African law firm",
  },
];
```

And update the `defaultValues` in `useForm`:

```typescript
  const form = useForm<DemoProvisionFormData>({
    resolver: zodResolver(demoProvisionSchema),
    defaultValues: {
      organizationName: "",
      verticalProfile: "consulting-generic",
      adminEmail: "",
      seedDemoData: true,
    },
  });
```

- [ ] **Step 3: Verify no other frontend files reference the old enum values**

Run:
```bash
cd /Users/rakheendama/Projects/2026/b2b-strawman/frontend && grep -rn '"GENERIC"\|"ACCOUNTING"\|"LEGAL"' --include="*.ts" --include="*.tsx" | grep -v node_modules | grep -v .next
```

Expected: Only the two files we just changed should appear. If others reference these values, update them too.

- [ ] **Step 4: Run frontend lint**

Run:
```bash
cd /Users/rakheendama/Projects/2026/b2b-strawman/frontend && pnpm run lint
```

Expected: PASS, no errors related to our changes.

- [ ] **Step 5: Commit frontend fix**

```bash
git add frontend/lib/schemas/demo-provision.ts frontend/app/\(app\)/platform-admin/demo/demo-provision-form.tsx
git commit -m "fix(BUG-012): align frontend vertical profile values with backend profile IDs

Frontend was sending LEGAL/ACCOUNTING/GENERIC but backend registry
keys are legal-za/accounting-za/consulting-generic. This caused
VerticalProfileRegistry.getProfile() to return empty, skipping all
vertical-specific packs, modules, and terminology."
```

---

## Task 2: Fix backend DemoDataSeeder dispatch (BUG-012)

**Files:**
- Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/demo/seed/DemoDataSeeder.java`

- [ ] **Step 1: Write the failing test**

The existing test in `DemoDataSeederIntegrationTest.java` (`dispatcher_routesToCorrectSeeder_accounting`) uses `"accounting"` as the profile. We need to verify that the full profile IDs work. But first, let's update the `DemoDataSeeder` switch statement and then verify existing tests still pass.

Update `backend/src/main/java/io/b2mash/b2b/b2bstrawman/demo/seed/DemoDataSeeder.java`:

```java
  public void seed(String schemaName, UUID orgId, String verticalProfile) {
    log.info(
        "Dispatching demo data seeding for profile '{}' in schema {}", verticalProfile, schemaName);

    BaseDemoDataSeeder seeder =
        switch (verticalProfile == null ? "" : verticalProfile.toLowerCase()) {
          case "accounting-za" -> accountingSeeder;
          case "legal-za" -> legalSeeder;
          default -> genericSeeder;
        };
    seeder.seed(schemaName, orgId);
  }
```

- [ ] **Step 2: Update the existing integration test to use full profile IDs**

In `backend/src/test/java/io/b2mash/b2b/b2bstrawman/demo/seed/DemoDataSeederIntegrationTest.java`, update the `dispatcher_routesToCorrectSeeder_accounting` test:

Change line 306:
```java
    tenantProvisioningService.provisionTenant(slug, "Accounting Dispatch Test Org", "accounting-za");
```

And change line 311:
```java
    demoDataSeeder.seed(acctSchema, acctOrgId, "accounting-za");
```

- [ ] **Step 3: Run the demo seeder tests**

Run:
```bash
cd /Users/rakheendama/Projects/2026/b2b-strawman/backend && ./mvnw test -pl . -Dtest="DemoDataSeederIntegrationTest" -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: All tests PASS. The existing `@BeforeAll` uses `"generic"` which falls through to the `default` case — still works.

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/io/b2mash/b2b/b2bstrawman/demo/seed/DemoDataSeeder.java backend/src/test/java/io/b2mash/b2b/b2bstrawman/demo/seed/DemoDataSeederIntegrationTest.java
git commit -m "fix(BUG-012): update DemoDataSeeder dispatch to match full profile IDs

Switch cases changed from stem matches (accounting, legal) to full
profile IDs (accounting-za, legal-za). Tests updated accordingly."
```

---

## Task 3: Add backend validation guard for unknown profiles (BUG-012)

**Files:**
- Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/demo/DemoProvisionService.java`
- Modify: `backend/src/test/java/io/b2mash/b2b/b2bstrawman/demo/DemoProvisionServiceTest.java`

- [ ] **Step 1: Write the failing test — unknown profile rejected**

Add a new test to `DemoProvisionServiceTest.java`:

```java
  @Test
  void provision_unknownVerticalProfile_returns400() throws Exception {
    mockMvc
        .perform(
            post(BASE_PATH + "/provision")
                .with(adminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "organizationName": "Unknown Profile Org",
                      "verticalProfile": "BOGUS",
                      "adminEmail": "bogus@example.com",
                      "seedDemoData": false
                    }
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail", containsString("Unknown vertical profile")));
  }
```

- [ ] **Step 2: Run the test to verify it fails**

Run:
```bash
cd /Users/rakheendama/Projects/2026/b2b-strawman/backend && ./mvnw test -pl . -Dtest="DemoProvisionServiceTest#provision_unknownVerticalProfile_returns400" -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: FAIL — currently the backend accepts any profile string without validation.

- [ ] **Step 3: Add the validation guard**

In `DemoProvisionService.java`, inject `VerticalProfileRegistry` and add validation at the top of `provisionDemo()`:

Add to constructor parameters and field:
```java
  private final VerticalProfileRegistry verticalProfileRegistry;
```

Add to constructor body:
```java
    this.verticalProfileRegistry = verticalProfileRegistry;
```

Add import:
```java
import io.b2mash.b2b.b2bstrawman.verticals.VerticalProfileRegistry;
```

Add constructor parameter (after `JdbcTemplate jdbcTemplate`):
```java
      VerticalProfileRegistry verticalProfileRegistry,
```

Add validation after the slug check (after line 96, before the log statement):

```java
    // Validate vertical profile exists in registry
    if (!verticalProfileRegistry.exists(verticalProfile)) {
      throw new InvalidStateException(
          "Unknown vertical profile",
          "Vertical profile '%s' is not registered. Available profiles: %s"
              .formatted(
                  verticalProfile,
                  verticalProfileRegistry.getAllProfiles().stream()
                      .map(p -> p.profileId())
                      .toList()));
    }
```

- [ ] **Step 4: Run the new test to verify it passes**

Run:
```bash
cd /Users/rakheendama/Projects/2026/b2b-strawman/backend && ./mvnw test -pl . -Dtest="DemoProvisionServiceTest#provision_unknownVerticalProfile_returns400" -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: PASS

- [ ] **Step 5: Update existing test payloads to use correct profile IDs**

In `DemoProvisionServiceTest.java`, update ALL test methods that send `"verticalProfile": "accounting"` or `"verticalProfile": "legal"`:

- `provision_createsKeycloakOrgAndUserAndSchema`: `"accounting"` → `"accounting-za"`, also update the `jsonPath` assertion: `.andExpect(jsonPath("$.verticalProfile").value("accounting-za"))`
- `provision_setsSubscriptionToActivePilot`: `"legal"` → `"legal-za"`
- `provision_withExistingKeycloakUser_reusesUser`: `"accounting"` → `"accounting-za"`
- `provision_withSeedDemoDataFalse_returnsNotSeeded`: `"accounting"` → `"accounting-za"`
- `provision_returnsCorrectLoginUrl`: `"legal"` → `"legal-za"`
- `provision_emitsAuditLog_noException`: `"accounting"` → `"accounting-za"`

- [ ] **Step 6: Run all demo provisioning tests**

Run:
```bash
cd /Users/rakheendama/Projects/2026/b2b-strawman/backend && ./mvnw test -pl . -Dtest="DemoProvisionServiceTest" -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: ALL tests PASS.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/io/b2mash/b2b/b2bstrawman/demo/DemoProvisionService.java backend/src/test/java/io/b2mash/b2b/b2bstrawman/demo/DemoProvisionServiceTest.java
git commit -m "fix(BUG-012): validate vertical profile exists before provisioning

Rejects unknown profile IDs with 400 and lists available profiles.
Prevents silent fallback to generic packs when profile ID is wrong.
Updated all test payloads to use correct profile IDs (accounting-za,
legal-za)."
```

---

## Task 4: Add vertical pack installation integration test (BUG-012)

**Files:**
- Modify: `backend/src/test/java/io/b2mash/b2b/b2bstrawman/demo/seed/DemoDataSeederIntegrationTest.java`

- [ ] **Step 1: Add a test that verifies legal packs are installed when profile is set**

Add to `DemoDataSeederIntegrationTest.java`:

```java
  @Test
  void legalProfile_installsVerticalSpecificPacks() {
    String slug = "legal-pack-test-" + UUID.randomUUID().toString().substring(0, 8);
    tenantProvisioningService.provisionTenant(slug, "Legal Pack Test Org", "legal-za");
    String legalSchema = mappingRepository.findByExternalOrgId(slug).orElseThrow().getSchemaName();
    UUID legalOrgId = organizationRepository.findByExternalOrgId(slug).orElseThrow().getId();

    var result = new AtomicReference<io.b2mash.b2b.b2bstrawman.settings.OrgSettings>();
    tenantTransactionHelper.executeInTenantTransaction(
        legalSchema,
        legalOrgId.toString(),
        t -> {
          result.set(
              orgSettingsRepository.findForCurrentTenant().orElseThrow());
        });

    var settings = result.get();
    assertEquals("legal-za", settings.getVerticalProfile());
    assertNotNull(settings.getEnabledModules(), "Enabled modules should be set");
    assertTrue(
        settings.getEnabledModules().contains("trust_accounting"),
        "Legal profile should enable trust_accounting module");
    assertTrue(
        settings.getEnabledModules().contains("court_calendar"),
        "Legal profile should enable court_calendar module");
    assertEquals("en-ZA-legal", settings.getTerminologyNamespace());
    assertEquals("ZAR", settings.getDefaultCurrency());
  }
```

Add the required field injection — add `OrgSettingsRepository` as a constructor parameter and field:

```java
  private final OrgSettingsRepository orgSettingsRepository;
```

And the import:
```java
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
```

- [ ] **Step 2: Run the test**

Run:
```bash
cd /Users/rakheendama/Projects/2026/b2b-strawman/backend && ./mvnw test -pl . -Dtest="DemoDataSeederIntegrationTest#legalProfile_installsVerticalSpecificPacks" -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: PASS — verifies that `TenantProvisioningService.provisionTenant("legal-za")` correctly sets the profile, modules, terminology, and currency.

- [ ] **Step 3: Commit**

```bash
git add backend/src/test/java/io/b2mash/b2b/b2bstrawman/demo/seed/DemoDataSeederIntegrationTest.java
git commit -m "test(BUG-012): verify vertical packs installed for legal-za profile

Asserts OrgSettings has correct verticalProfile, enabledModules,
terminologyNamespace, and currency after provisioning with legal-za."
```

---

## Task 5: Create DemoWelcomeEmailService (BUG-013)

**Files:**
- Create: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/demo/DemoWelcomeEmailService.java`

- [ ] **Step 1: Write the failing test first**

Create `backend/src/test/java/io/b2mash/b2b/b2bstrawman/demo/DemoWelcomeEmailServiceTest.java`:

```java
package io.b2mash.b2b.b2bstrawman.demo;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import jakarta.mail.internet.MimeMessage;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DemoWelcomeEmailServiceTest {

  @Autowired private DemoWelcomeEmailService welcomeEmailService;

  @Test
  void sendWelcomeEmail_doesNotThrow() {
    // In test profile, JavaMailSender may not be configured (Optional.empty()),
    // so this verifies the non-fatal behavior
    assertDoesNotThrow(
        () ->
            welcomeEmailService.sendWelcomeEmail(
                "test@example.com",
                "Test Org",
                "test-org",
                "legal-za",
                "http://localhost:3000/org/test-org",
                "TempPass123!"));
  }
}
```

- [ ] **Step 2: Run test to verify it fails (class doesn't exist yet)**

Run:
```bash
cd /Users/rakheendama/Projects/2026/b2b-strawman/backend && ./mvnw test -pl . -Dtest="DemoWelcomeEmailServiceTest" -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: FAIL — compilation error, `DemoWelcomeEmailService` not found.

- [ ] **Step 3: Create the email service**

Create `backend/src/main/java/io/b2mash/b2b/b2bstrawman/demo/DemoWelcomeEmailService.java`:

```java
package io.b2mash.b2b.b2bstrawman.demo;

import io.b2mash.b2b.b2bstrawman.notification.template.EmailTemplateRenderer;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/**
 * Sends a welcome email to newly provisioned demo org admins. Uses platform-level SMTP (not
 * tenant-scoped email provider) since the email is sent before the tenant has any integrations
 * configured. Fire-and-forget: exceptions are caught and logged, never propagated.
 */
@Service
public class DemoWelcomeEmailService {

  private static final Logger log = LoggerFactory.getLogger(DemoWelcomeEmailService.class);
  private static final String TEMPLATE_NAME = "demo-welcome";

  private final Optional<JavaMailSender> mailSender;
  private final EmailTemplateRenderer emailTemplateRenderer;
  private final String senderAddress;
  private final String productName;

  public DemoWelcomeEmailService(
      Optional<JavaMailSender> mailSender,
      EmailTemplateRenderer emailTemplateRenderer,
      @Value("${docteams.email.sender-address:noreply@kazi.app}") String senderAddress,
      @Value("${docteams.app.product-name:Kazi}") String productName) {
    this.mailSender = mailSender;
    this.emailTemplateRenderer = emailTemplateRenderer;
    this.senderAddress = senderAddress;
    this.productName = productName;
  }

  /**
   * Sends a welcome email with login credentials. Non-fatal: catches and logs all exceptions.
   *
   * @param adminEmail recipient email address
   * @param orgName display name of the provisioned organization
   * @param orgSlug organization slug (used in login URL)
   * @param verticalProfile the vertical profile (e.g., "legal-za")
   * @param loginUrl full login URL for the demo org
   * @param tempPassword temporary password for first login
   */
  public void sendWelcomeEmail(
      String adminEmail,
      String orgName,
      String orgSlug,
      String verticalProfile,
      String loginUrl,
      String tempPassword) {
    if (mailSender.isEmpty()) {
      log.info(
          "Skipping demo welcome email to {} — no mail sender configured (test/dev environment)",
          adminEmail);
      return;
    }

    try {
      Map<String, Object> context = new HashMap<>();
      context.put("subject", "Welcome to " + productName + " — Your demo is ready");
      context.put("orgName", orgName);
      context.put("orgSlug", orgSlug);
      context.put("verticalProfile", verticalProfile);
      context.put("loginUrl", loginUrl);
      context.put("adminEmail", adminEmail);
      context.put("tempPassword", tempPassword);
      context.put("productName", productName);
      context.put("recipientName", null);
      context.put("brandColor", "#0D9488");
      context.put("orgLogoUrl", null);
      context.put("footerText", null);
      context.put("unsubscribeUrl", null);
      context.put("appUrl", loginUrl);

      var rendered = emailTemplateRenderer.render(TEMPLATE_NAME, context);

      var mimeMessage = mailSender.get().createMimeMessage();
      var helper = new MimeMessageHelper(mimeMessage, true);
      helper.setFrom(senderAddress);
      helper.setTo(adminEmail);
      helper.setSubject(rendered.subject());
      if (rendered.htmlBody() != null && rendered.plainTextBody() != null) {
        helper.setText(rendered.plainTextBody(), rendered.htmlBody());
      } else if (rendered.htmlBody() != null) {
        helper.setText(rendered.htmlBody(), true);
      }
      mailSender.get().send(mimeMessage);

      log.info("Demo welcome email sent to {} for org '{}'", adminEmail, orgName);
    } catch (Exception e) {
      log.error("Failed to send demo welcome email to {} for org '{}': {}", adminEmail, orgName, e.getMessage());
    }
  }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run:
```bash
cd /Users/rakheendama/Projects/2026/b2b-strawman/backend && ./mvnw test -pl . -Dtest="DemoWelcomeEmailServiceTest" -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: PASS — `Optional<JavaMailSender>` is empty in test profile, so the service logs "Skipping" and returns without error.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/io/b2mash/b2b/b2bstrawman/demo/DemoWelcomeEmailService.java backend/src/test/java/io/b2mash/b2b/b2bstrawman/demo/DemoWelcomeEmailServiceTest.java
git commit -m "feat(BUG-013): add DemoWelcomeEmailService for demo org provisioning

Uses Optional<JavaMailSender> (platform SMTP) — no tenant context
needed. Fire-and-forget: exceptions logged, never propagated.
Gracefully skips in test/dev environments without mail sender."
```

---

## Task 6: Create welcome email Thymeleaf template (BUG-013)

**Files:**
- Create: `backend/src/main/resources/templates/email/demo-welcome.html`

- [ ] **Step 1: Create the template**

Create `backend/src/main/resources/templates/email/demo-welcome.html`:

```html
<div xmlns:th="http://www.thymeleaf.org">
  <h2 style="margin: 0 0 16px 0; font-size: 20px; font-weight: 600; color: #111827;">
    Your Demo is Ready
  </h2>
  <p style="margin: 0 0 16px 0; font-size: 14px; line-height: 1.6; color: #374151;">
    Hi,
  </p>
  <p style="margin: 0 0 16px 0; font-size: 14px; line-height: 1.6; color: #374151;">
    Your <strong th:text="${productName}">Kazi</strong> demo organization
    <strong th:text="${orgName}">Demo Org</strong> has been provisioned and is ready to use.
  </p>

  <!-- Credentials box -->
  <table role="presentation" cellpadding="0" cellspacing="0" width="100%"
         style="margin: 24px 0; background-color: #f9fafb; border: 1px solid #e5e7eb; border-radius: 8px;">
    <tr>
      <td style="padding: 20px;">
        <p style="margin: 0 0 12px 0; font-size: 13px; font-weight: 600; color: #6b7280; text-transform: uppercase; letter-spacing: 0.05em;">
          Your Login Credentials
        </p>
        <table role="presentation" cellpadding="0" cellspacing="0">
          <tr>
            <td style="padding: 4px 0; font-size: 14px; color: #6b7280; width: 100px;">Email:</td>
            <td style="padding: 4px 0; font-size: 14px; color: #111827; font-weight: 500;" th:text="${adminEmail}">admin@example.com</td>
          </tr>
          <tr>
            <td style="padding: 4px 0; font-size: 14px; color: #6b7280; width: 100px;">Password:</td>
            <td style="padding: 4px 0; font-size: 14px; color: #111827; font-family: monospace; font-weight: 500;" th:text="${tempPassword}">********</td>
          </tr>
        </table>
      </td>
    </tr>
  </table>

  <p style="margin: 0 0 8px 0; font-size: 13px; color: #DC2626; font-weight: 500;">
    You will be asked to change your password on first login.
  </p>

  <!-- CTA button -->
  <table role="presentation" cellpadding="0" cellspacing="0" style="margin: 24px 0;">
    <tr>
      <td align="center" style="border-radius: 6px; background-color: #0D9488;">
        <a th:href="${loginUrl}"
           style="display: inline-block; padding: 12px 24px; color: #ffffff; text-decoration: none; font-size: 14px; font-weight: 600; border-radius: 6px; background-color: #0D9488;">
          Sign In to Your Demo
        </a>
      </td>
    </tr>
  </table>

  <p style="margin: 0 0 8px 0; font-size: 13px; color: #6b7280;">
    If you did not request this demo, you can safely ignore this email.
  </p>
  <p style="margin: 0; font-size: 14px; line-height: 1.6; color: #374151;">
    Best,<br/>
    The <span th:text="${productName}">Kazi</span> Team
  </p>
</div>
```

- [ ] **Step 2: Commit**

```bash
git add backend/src/main/resources/templates/email/demo-welcome.html
git commit -m "feat(BUG-013): add Thymeleaf template for demo welcome email

Renders into the existing base.html layout. Shows login credentials,
first-login password-change notice, and CTA button."
```

---

## Task 7: Wire welcome email into DemoProvisionService (BUG-013)

**Files:**
- Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/demo/DemoProvisionService.java`

- [ ] **Step 1: Add DemoWelcomeEmailService as a dependency**

In `DemoProvisionService.java`, add the field and constructor parameter:

Field:
```java
  private final DemoWelcomeEmailService demoWelcomeEmailService;
```

Add to constructor signature (after `JdbcTemplate jdbcTemplate`... after the `VerticalProfileRegistry` we added in Task 3):
```java
      DemoWelcomeEmailService demoWelcomeEmailService,
```

Add to constructor body:
```java
    this.demoWelcomeEmailService = demoWelcomeEmailService;
```

- [ ] **Step 2: Call the welcome email service after subscription override**

Add after Step 5 (subscription override, after line 186) and before Step 6 (demo data seeding):

```java
    // Step 5b: Send welcome email (non-fatal — provisioning succeeds even if email fails)
    demoWelcomeEmailService.sendWelcomeEmail(
        adminEmail, name, slug, verticalProfile, loginUrl, tempPassword);
```

Note: `loginUrl` is already computed on line 217. Move `loginUrl` computation before this call — move `String loginUrl = baseUrl + "/org/" + slug;` to just before this new step (before Step 5b).

The reordered section should look like:

```java
    // Compute login URL (needed for welcome email and response)
    String loginUrl = baseUrl + "/org/" + slug;

    // Step 5b: Send welcome email (non-fatal — provisioning succeeds even if email fails)
    demoWelcomeEmailService.sendWelcomeEmail(
        adminEmail, name, slug, verticalProfile, loginUrl, tempPassword);

    // Step 6: Demo data seeding (non-fatal — tenant is usable without demo data)
    ...
```

And remove the duplicate `String loginUrl = baseUrl + "/org/" + slug;` from later in the method (was line 217).

- [ ] **Step 3: Run existing provisioning tests**

Run:
```bash
cd /Users/rakheendama/Projects/2026/b2b-strawman/backend && ./mvnw test -pl . -Dtest="DemoProvisionServiceTest" -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: ALL PASS — welcome email service gracefully skips in test (no `JavaMailSender` configured).

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/io/b2mash/b2b/b2bstrawman/demo/DemoProvisionService.java
git commit -m "feat(BUG-013): wire welcome email into demo provisioning flow

Sends credentials email after subscription override. Non-fatal:
provisioning succeeds even if email delivery fails. Gracefully
skips in environments without mail sender configured."
```

---

## Task 8: Run full test suite and verify

- [ ] **Step 1: Run all demo-related tests**

Run:
```bash
cd /Users/rakheendama/Projects/2026/b2b-strawman/backend && ./mvnw test -pl . -Dtest="DemoProvisionServiceTest,DemoWelcomeEmailServiceTest,DemoDataSeederIntegrationTest,DemoReseedTest,DemoCleanupServiceTest" -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: ALL PASS.

- [ ] **Step 2: Run the full backend test suite to check for regressions**

Run:
```bash
cd /Users/rakheendama/Projects/2026/b2b-strawman/backend && ./mvnw test
```

Expected: ALL PASS. No regressions from our changes.

- [ ] **Step 3: Run frontend lint and tests**

Run:
```bash
cd /Users/rakheendama/Projects/2026/b2b-strawman/frontend && pnpm run lint && pnpm test
```

Expected: ALL PASS.

- [ ] **Step 4: Verify fix by tracing the code path manually**

Confirm the fix by reading through the flow:
1. Frontend sends `"legal-za"` → backend receives `"legal-za"`
2. `DemoProvisionService` validates `verticalProfileRegistry.exists("legal-za")` → true
3. `TenantProvisioningService.provisionTenant()` calls `setVerticalProfile()` with `"legal-za"` → `VerticalProfileRegistry.getProfile("legal-za")` returns the legal profile → modules, terminology, currency set
4. Pack seeders run → `tenantProfile = "legal-za"`, `packProfile = "legal-za"` → MATCH → vertical packs installed
5. `DemoDataSeeder.seed("legal-za")` → `.toLowerCase()` = `"legal-za"` → matches `case "legal-za"` → `legalSeeder` dispatched
6. Welcome email sent with login URL and temp password

- [ ] **Step 5: Final commit — update bug documentation**

Update `documentation/bugs.md` to mark both bugs with fix status:

For BUG-012, append to the Impact section:
```
**Status: Fixed** — Frontend aligned to backend profile IDs (consulting-generic, accounting-za, legal-za). Backend validation guard rejects unknown profiles. DemoDataSeeder dispatch updated. All tests updated.
```

For BUG-013, append to the Impact section:
```
**Status: Fixed** — DemoWelcomeEmailService sends Thymeleaf-rendered welcome email via platform SMTP after provisioning. Fire-and-forget (non-fatal). Gracefully skips in environments without mail sender.
```

```bash
git add documentation/bugs.md
git commit -m "docs: mark BUG-012 and BUG-013 as fixed"
```
