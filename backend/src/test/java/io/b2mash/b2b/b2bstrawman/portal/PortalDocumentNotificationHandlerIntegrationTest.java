package io.b2mash.b2b.b2bstrawman.portal;

import static io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory.createActiveCustomer;
import static org.assertj.core.api.Assertions.assertThat;

import com.icegreen.greenmail.util.GreenMail;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.event.DocumentGeneratedEvent;
import io.b2mash.b2b.b2bstrawman.member.MemberSyncService;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettings;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsService;
import io.b2mash.b2b.b2bstrawman.template.TemplateEntityType;
import jakarta.mail.internet.MimeMessage;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * GAP-L-72 (E5.1, slice 23) — covers the {@link PortalDocumentNotificationHandler} listener that
 * fires per-event portal-contact emails when matter-closure or Statement-of-Account documents are
 * generated.
 *
 * <p>Each test publishes a synthetic {@link DocumentGeneratedEvent} via {@link
 * ApplicationEventPublisher} inside a tenant-scoped transaction so the {@code AFTER_COMMIT}
 * listener fires. Assertions are made against the GreenMail JVM singleton (port 13025).
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PortalDocumentNotificationHandlerIntegrationTest {

  private static final GreenMail greenMail =
      io.b2mash.b2b.b2bstrawman.testutil.GreenMailTestSupport.getInstance();

  private static final String ORG_ID = "org_l72_doc_notif_test";
  private static final String CONTACT_EMAIL = "l72-portal-contact@test.com";

  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private MemberSyncService memberSyncService;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private ProjectRepository projectRepository;
  @Autowired private PortalContactRepository portalContactRepository;
  @Autowired private OrgSettingsRepository orgSettingsRepository;
  @Autowired private OrgSettingsService orgSettingsService;
  @Autowired private ApplicationEventPublisher eventPublisher;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private PortalDocumentNotificationHandler handler;

  private String tenantSchema;
  private UUID memberId;
  private UUID customerId;
  private UUID projectId;
  private UUID contactId;

  @BeforeAll
  void provisionTenant() {
    tenantSchema =
        provisioningService.provisionTenant(ORG_ID, "L-72 Doc Notif Test", null).schemaName();
    memberId =
        memberSyncService
            .syncMember(ORG_ID, "user_l72_owner", "l72-owner@test.com", "L72 Owner", null, "owner")
            .memberId();

    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  // Default allowlist is seeded by Flyway V117. Re-assert it explicitly so the test
                  // tenant is not at the mercy of unrelated migrations.
                  OrgSettings settings = orgSettingsService.getOrCreateForCurrentTenant();
                  settings.setPortalNotificationDocTypes(
                      List.of("matter-closure-letter", "statement-of-account"));
                  orgSettingsRepository.save(settings);

                  var customer = createActiveCustomer("L72 Test Client", CONTACT_EMAIL, memberId);
                  customer = customerRepository.save(customer);
                  customerId = customer.getId();

                  var project = new Project("L72 Test Matter", "L72 regression", memberId);
                  project.setCustomerId(customerId);
                  projectId = projectRepository.saveAndFlush(project).getId();

                  var contact =
                      new PortalContact(
                          ORG_ID,
                          customerId,
                          CONTACT_EMAIL,
                          "L72 Test Contact",
                          PortalContact.ContactRole.PRIMARY);
                  contactId = portalContactRepository.save(contact).getId();
                }));
  }

  @BeforeEach
  void resetGreenMailAndAllowlist() throws Exception {
    greenMail.purgeEmailFromAllMailboxes();
    // Caffeine dedup window is 5 minutes — without an explicit reset every test after the first
    // would silently dedup against the previous one's (customer, project) tuple.
    handler.clearDedupCacheForTesting();
    // Re-assert default allowlist between tests so the empty-allowlist case doesn't bleed into
    // subsequent tests.
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  OrgSettings settings = orgSettingsService.getOrCreateForCurrentTenant();
                  settings.setPortalNotificationDocTypes(
                      List.of("matter-closure-letter", "statement-of-account"));
                  orgSettingsRepository.save(settings);
                }));
  }

  /** Eligible event (visibility=PORTAL, scope=PROJECT, allowlisted template) → email sent. */
  @Test
  void eligibleEvent_sendsEmail() throws Exception {
    UUID generatedDocId = UUID.randomUUID();
    publishEvent(generatedDocId, projectId, "statement-of-account", "PROJECT", "PORTAL");

    MimeMessage[] received = greenMail.getReceivedMessages();
    assertThat(received).hasSize(1);
    assertThat(received[0].getAllRecipients()[0].toString()).isEqualTo(CONTACT_EMAIL);
    assertThat(received[0].getSubject()).containsIgnoringCase("document ready");
    assertThat(received[0].getSubject()).containsIgnoringCase("statement-of-account");
  }

  /** Non-portal-visible event (INTERNAL) → no email. */
  @Test
  void internalVisibility_skipsEmail() {
    UUID generatedDocId = UUID.randomUUID();
    publishEvent(generatedDocId, projectId, "statement-of-account", "PROJECT", "INTERNAL");

    MimeMessage[] received = greenMail.getReceivedMessages();
    assertThat(received).isEmpty();
  }

  /** Template name not in allowlist (e.g. invoice-delivery) → no email. */
  @Test
  void unallowedTemplate_skipsEmail() {
    UUID generatedDocId = UUID.randomUUID();
    publishEvent(generatedDocId, projectId, "invoice-delivery", "PROJECT", "PORTAL");

    MimeMessage[] received = greenMail.getReceivedMessages();
    assertThat(received).isEmpty();
  }

  /** Empty per-tenant allowlist (toggle off) → no email even for normally allowlisted templates. */
  @Test
  void emptyAllowlist_disablesAllSends() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  OrgSettings settings = orgSettingsService.getOrCreateForCurrentTenant();
                  settings.setPortalNotificationDocTypes(List.of());
                  orgSettingsRepository.save(settings);
                }));

    UUID generatedDocId = UUID.randomUUID();
    publishEvent(generatedDocId, projectId, "matter-closure-letter", "PROJECT", "PORTAL");

    MimeMessage[] received = greenMail.getReceivedMessages();
    assertThat(received).isEmpty();
  }

  /**
   * Two events within 5 minutes for the same (customer, project) → only one email. Mirrors the
   * closure-pack scenario where multiple documents are generated in seconds.
   */
  @Test
  void dedupCoalescesBatchSendsForSameCustomerAndProject() {
    UUID gd1 = UUID.randomUUID();
    UUID gd2 = UUID.randomUUID();

    publishEvent(gd1, projectId, "matter-closure-letter", "PROJECT", "PORTAL");
    publishEvent(gd2, projectId, "statement-of-account", "PROJECT", "PORTAL");

    MimeMessage[] received = greenMail.getReceivedMessages();
    assertThat(received).hasSize(1);
  }

  /** Different projects (same customer) must NOT dedup against each other. */
  @Test
  void differentProjectsDoNotDedup() {
    // Provision a second project so the dedup key (tenant:customer:project) differs.
    UUID secondProjectId =
        runInTenantReturning(
            () ->
                transactionTemplate.execute(
                    tx -> {
                      var p = new Project("L72 Second Matter", "L72 second regression", memberId);
                      p.setCustomerId(customerId);
                      return projectRepository.saveAndFlush(p).getId();
                    }));

    UUID gd1 = UUID.randomUUID();
    UUID gd2 = UUID.randomUUID();

    publishEvent(gd1, projectId, "statement-of-account", "PROJECT", "PORTAL");
    publishEvent(gd2, secondProjectId, "statement-of-account", "PROJECT", "PORTAL");

    MimeMessage[] received = greenMail.getReceivedMessages();
    assertThat(received).hasSize(2);
  }

  // ── helpers ────────────────────────────────────────────────────────────

  /**
   * Publishes a {@link DocumentGeneratedEvent} inside a tenant-scoped transaction. {@code
   * AFTER_COMMIT} listeners run when the transaction commits, so we wrap in {@code
   * transactionTemplate} to give them a chance to fire.
   */
  private void publishEvent(
      UUID generatedDocId,
      UUID targetProjectId,
      String templateName,
      String scope,
      String visibility) {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx ->
                    eventPublisher.publishEvent(
                        new DocumentGeneratedEvent(
                            "document.generated",
                            "generated_document",
                            generatedDocId,
                            targetProjectId,
                            memberId,
                            "L72 Owner",
                            tenantSchema,
                            ORG_ID,
                            Instant.now(),
                            Map.of(
                                "file_name",
                                "L72-test-" + templateName + ".pdf",
                                "template_name",
                                templateName,
                                "scope",
                                scope,
                                "visibility",
                                visibility),
                            templateName,
                            TemplateEntityType.PROJECT,
                            targetProjectId,
                            "L72-test-" + templateName + ".pdf",
                            generatedDocId))));
  }

  private void runInTenant(Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(action);
  }

  private <T> T runInTenantReturning(java.util.function.Supplier<T> action) {
    Object[] holder = new Object[1];
    runInTenant(() -> holder[0] = action.get());
    @SuppressWarnings("unchecked")
    T value = (T) holder[0];
    return value;
  }
}
