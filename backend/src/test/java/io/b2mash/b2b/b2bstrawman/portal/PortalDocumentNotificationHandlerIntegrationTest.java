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
import io.b2mash.b2b.b2bstrawman.template.GeneratedDocumentSharedEvent;
import io.b2mash.b2b.b2bstrawman.template.TemplateEntityType;
import jakarta.mail.internet.MimeMessage;
import java.time.Instant;
import java.util.HashMap;
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
 * fires per-event portal-contact "Document ready" emails.
 *
 * <p>Phase 88 U13 / ADR-350: the handler emails only on explicit client-notification intent — a
 * {@link DocumentGeneratedEvent} whose {@code details.notify_client} is {@code true}, or a {@link
 * GeneratedDocumentSharedEvent} with {@code notifyClient=true} — and only for portal-visible
 * documents. The per-tenant template allowlist is gone: intent, not template type, decides.
 *
 * <p>Each test publishes a synthetic event via {@link ApplicationEventPublisher} inside a
 * tenant-scoped transaction so the {@code AFTER_COMMIT} listener fires. Assertions are made against
 * the GreenMail JVM singleton (dynamic port).
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
  @Autowired private ApplicationEventPublisher eventPublisher;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private PortalDocumentNotificationHandler handler;

  private String tenantSchema;
  private UUID memberId;
  private UUID customerId;
  private UUID projectId;

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
                  portalContactRepository.save(contact);
                }));
  }

  @BeforeEach
  void resetGreenMailAndDedup() throws Exception {
    greenMail.purgeEmailFromAllMailboxes();
    // Caffeine dedup window is 5 minutes — without an explicit reset every test after the first
    // would silently dedup against the previous one's (customer, project, template) tuple.
    handler.clearDedupCacheForTesting();
  }

  /**
   * Portal-visible event with explicit notify_client=true → email sent, deep-linked to /matters.
   */
  @Test
  void eligibleEvent_withNotifyClient_sendsEmail() throws Exception {
    UUID generatedDocId = UUID.randomUUID();
    publishEvent(generatedDocId, projectId, "statement-of-account", "PROJECT", "PORTAL", true);

    MimeMessage[] received = greenMail.getReceivedMessages();
    assertThat(received).hasSize(1);
    assertThat(received[0].getAllRecipients()[0].toString()).isEqualTo(CONTACT_EMAIL);
    assertThat(received[0].getSubject()).containsIgnoringCase("document ready");
    assertThat(received[0].getSubject()).containsIgnoringCase("statement-of-account");

    // Phase 88 U11 / ADR-350 / ADR-351: the "View document" link is the matter page (the portal
    // has no /projects route) and carries orgId so a fresh browser lands branded.
    String html = extractHtmlBody(received[0]);
    assertThat(html)
        .as("document-ready CTA must deep-link to /matters/{id} with orgId")
        .contains("href=\"http://localhost:3002/matters/" + projectId + "?orgId=" + ORG_ID + "\"");
    assertThat(html).as("legacy /projects/ link must be gone").doesNotContain("/projects/");
  }

  /**
   * ADR-350 (G-C5): a portal-visible statement of account WITHOUT a notify_client key — what every
   * "Preview & Save" produced before the intent flags — must NOT email the client.
   */
  @Test
  void portalVisibleEvent_withoutNotifyClient_skipsEmail() {
    UUID generatedDocId = UUID.randomUUID();
    publishEvent(generatedDocId, projectId, "statement-of-account", "PROJECT", "PORTAL", null);

    assertThat(greenMail.getReceivedMessages()).isEmpty();
  }

  /** ADR-350: explicit notify_client=false → no email even when portal-visible. */
  @Test
  void notifyClientFalse_skipsEmail() {
    UUID generatedDocId = UUID.randomUUID();
    publishEvent(generatedDocId, projectId, "matter-closure-letter", "PROJECT", "PORTAL", false);

    assertThat(greenMail.getReceivedMessages()).isEmpty();
  }

  /** Intent without portal visibility (INTERNAL) → no email: the client could not open it. */
  @Test
  void notifyClientTrue_butInternal_skipsEmail() {
    UUID generatedDocId = UUID.randomUUID();
    publishEvent(generatedDocId, projectId, "statement-of-account", "PROJECT", "INTERNAL", true);

    assertThat(greenMail.getReceivedMessages()).isEmpty();
  }

  /**
   * ADR-350: the allowlist is gone — any template slug emails on explicit intent, with the generic
   * "A new document is ready for you" copy.
   */
  @Test
  void anyTemplate_withNotifyClient_sendsGenericDocumentReadyEmail() throws Exception {
    UUID generatedDocId = UUID.randomUUID();
    publishEvent(generatedDocId, projectId, "engagement-letter", "PROJECT", "PORTAL", true);

    MimeMessage[] received = greenMail.getReceivedMessages();
    assertThat(received).hasSize(1);
    assertThat(received[0].getAllRecipients()[0].toString()).isEqualTo(CONTACT_EMAIL);
    String html = extractHtmlBody(received[0]);
    assertThat(html).contains("A new document is ready for you");
    assertThat(html)
        .contains("href=\"http://localhost:3002/matters/" + projectId + "?orgId=" + ORG_ID + "\"");
  }

  /**
   * LZKC-015: two DIFFERENT document types within 5 minutes for the same (customer, project) → one
   * email per document. Mirrors the closure-pack scenario (closure letter + SoA generated in
   * seconds): pre-fix the dedup key omitted the template name, so the closure letter claimed the
   * key and the SoA "Document ready" email was silently swallowed.
   */
  @Test
  void distinctDocumentTypesInSameBatchEachSendEmail() {
    UUID gd1 = UUID.randomUUID();
    UUID gd2 = UUID.randomUUID();

    publishEvent(gd1, projectId, "matter-closure-letter", "PROJECT", "PORTAL", true);
    publishEvent(gd2, projectId, "statement-of-account", "PROJECT", "PORTAL", true);

    MimeMessage[] received = greenMail.getReceivedMessages();
    assertThat(received)
        .as("LZKC-015: closure letter + SoA are distinct documents — one email each")
        .hasSize(2);
  }

  /**
   * Re-emission of the SAME document type within 5 minutes for the same (customer, project) → still
   * only one email. Guards the residual dedup semantics after LZKC-015 widened the key to (tenant,
   * customer, project, template). ADR-350 keeps the dedup gate unchanged.
   */
  @Test
  void sameDocumentTypeReEmissionStillDedups() {
    UUID gd1 = UUID.randomUUID();
    UUID gd2 = UUID.randomUUID();

    publishEvent(gd1, projectId, "statement-of-account", "PROJECT", "PORTAL", true);
    publishEvent(gd2, projectId, "statement-of-account", "PROJECT", "PORTAL", true);

    MimeMessage[] received = greenMail.getReceivedMessages();
    assertThat(received)
        .as("same doc type re-emitted within the window must remain deduped")
        .hasSize(1);
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

    publishEvent(gd1, projectId, "statement-of-account", "PROJECT", "PORTAL", true);
    publishEvent(gd2, secondProjectId, "statement-of-account", "PROJECT", "PORTAL", true);

    MimeMessage[] received = greenMail.getReceivedMessages();
    assertThat(received).hasSize(2);
  }

  /** ADR-350 "Share with client" with notifyClient=true → one document-ready email. */
  @Test
  void sharedEvent_withNotifyClient_sendsEmail() throws Exception {
    publishSharedEvent(projectId, "statement-of-account", "PORTAL", true);

    MimeMessage[] received = greenMail.getReceivedMessages();
    assertThat(received).hasSize(1);
    assertThat(received[0].getAllRecipients()[0].toString()).isEqualTo(CONTACT_EMAIL);
    assertThat(extractHtmlBody(received[0]))
        .contains("href=\"http://localhost:3002/matters/" + projectId + "?orgId=" + ORG_ID + "\"");
  }

  /** ADR-350 "Share with client" without notifyClient → shared silently, no email. */
  @Test
  void sharedEvent_withoutNotifyClient_skipsEmail() {
    publishSharedEvent(projectId, "statement-of-account", "PORTAL", false);

    assertThat(greenMail.getReceivedMessages()).isEmpty();
  }

  /**
   * D-16: the blank-template early return existed only for the allowlist. Sharing a document whose
   * template has since been deleted (null slug) must still email on explicit intent.
   */
  @Test
  void sharedEvent_withNullTemplateSlug_stillSendsEmail() throws Exception {
    publishSharedEvent(projectId, null, "PORTAL", true);

    MimeMessage[] received = greenMail.getReceivedMessages();
    assertThat(received).hasSize(1);
    assertThat(received[0].getAllRecipients()[0]).hasToString(CONTACT_EMAIL);
  }

  // ── helpers ────────────────────────────────────────────────────────────

  /**
   * Publishes a {@link DocumentGeneratedEvent} inside a tenant-scoped transaction. {@code
   * AFTER_COMMIT} listeners run when the transaction commits, so we wrap in {@code
   * transactionTemplate} to give them a chance to fire. A {@code null} {@code notifyClient} omits
   * the {@code notify_client} key entirely (the pre-ADR-350 event shape).
   */
  private void publishEvent(
      UUID generatedDocId,
      UUID targetProjectId,
      String templateName,
      String scope,
      String visibility,
      Boolean notifyClient) {
    Map<String, Object> details = new HashMap<>();
    details.put("file_name", "L72-test-" + templateName + ".pdf");
    details.put("template_name", templateName);
    details.put("scope", scope);
    details.put("visibility", visibility);
    if (notifyClient != null) {
      details.put("notify_client", notifyClient);
    }
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
                            Map.copyOf(details),
                            templateName,
                            TemplateEntityType.PROJECT,
                            targetProjectId,
                            "L72-test-" + templateName + ".pdf",
                            generatedDocId))));
  }

  /** Publishes a {@link GeneratedDocumentSharedEvent} inside a tenant-scoped transaction. */
  private void publishSharedEvent(
      UUID targetProjectId, String templateSlug, String visibility, boolean notifyClient) {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx ->
                    eventPublisher.publishEvent(
                        new GeneratedDocumentSharedEvent(
                            tenantSchema,
                            ORG_ID,
                            RequestScopes.getShardIdOrDefault(),
                            targetProjectId,
                            UUID.randomUUID(),
                            templateSlug,
                            "L72-shared.pdf",
                            visibility,
                            notifyClient))));
  }

  private static String extractHtmlBody(MimeMessage msg) throws Exception {
    StringBuilder out = new StringBuilder();
    collectHtmlParts(msg.getContent(), out);
    return out.toString();
  }

  private static void collectHtmlParts(Object content, StringBuilder out) throws Exception {
    if (content instanceof jakarta.mail.Multipart multipart) {
      for (int i = 0; i < multipart.getCount(); i++) {
        var part = multipart.getBodyPart(i);
        String ct = part.getContentType() == null ? "" : part.getContentType().toLowerCase();
        if (ct.contains("text/html")) {
          out.append(part.getContent().toString());
        } else if (ct.contains("multipart/")) {
          collectHtmlParts(part.getContent(), out);
        }
      }
    } else if (content != null) {
      out.append(content.toString());
    }
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
