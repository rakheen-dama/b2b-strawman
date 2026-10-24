package io.b2mash.b2b.b2bstrawman.verticals.legal.closure;

import static io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory.createActiveCustomer;
import static org.assertj.core.api.Assertions.assertThat;

import com.icegreen.greenmail.util.GreenMail;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.document.Document;
import io.b2mash.b2b.b2bstrawman.document.DocumentRepository;
import io.b2mash.b2b.b2bstrawman.event.DocumentGeneratedEvent;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.portal.PortalContact;
import io.b2mash.b2b.b2bstrawman.portal.PortalContactRepository;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsService;
import io.b2mash.b2b.b2bstrawman.template.GeneratedDocumentRepository;
import io.b2mash.b2b.b2bstrawman.testutil.GreenMailTestSupport;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import io.b2mash.b2b.b2bstrawman.verticals.legal.closure.dto.ClosureReason;
import io.b2mash.b2b.b2bstrawman.verticals.legal.closure.dto.ClosureRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.statement.StatementService;
import io.b2mash.b2b.b2bstrawman.verticals.legal.statement.dto.GenerateStatementRequest;
import jakarta.mail.internet.MimeMessage;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * OBS-2106 — closes the regression-test gap that allowed the closure-pack portal email to silently
 * stop dispatching after matter closure (Day 60, cycle 47). Drives the full close flow end-to-end:
 *
 * <ol>
 *   <li>Provision a {@code legal-za} tenant (registers the {@code matter-closure-letter} template).
 *   <li>Seed an ACTIVE customer with a {@link PortalContact} so the portal-notification listener
 *       can resolve a recipient.
 *   <li>Invoke {@link MatterClosureService#close} with {@code generateClosureLetter=true} and
 *       {@code generateStatementOfAccount=true} — both PDFs render, both publish {@code
 *       DocumentGeneratedEvent}s with explicit {@code scope}/{@code visibility} in details (SoA via
 *       {@code StatementService}, closure-letter via {@code GeneratedDocumentService
 *       .generateForProject}).
 *   <li>Assert the GreenMail JVM singleton receives one portal-document-ready email PER document
 *       (LZKC-015): the 5-minute Caffeine dedup in {@code PortalDocumentNotificationHandler} keys
 *       on (tenant, customer, project, template), so distinct doc types each notify while genuine
 *       re-emissions of the same doc type stay deduped.
 * </ol>
 *
 * <p>Phase 88 U13 / ADR-350 (G-C5, G-D14 emails): the client is emailed ONLY on explicit intent
 * ({@code notifyClient}, which implies {@code shareWithClient}). Without flags the closure letter,
 * the closure SoA and a standalone "Preview &amp; Save" SoA are saved {@code INTERNAL} and no email
 * is sent; {@code shareWithClient} alone shares silently.
 *
 * <p>Without this test, OBS-2106 would re-regress silently: the closure-letter visibility test in
 * {@code MatterClosureServiceIntegrationTest} only checks the persisted Document visibility, not
 * whether the AFTER_COMMIT listener actually fired and sent the email.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@RecordApplicationEvents
class MatterClosureEmailIntegrationTest {

  private static final GreenMail greenMail = GreenMailTestSupport.getInstance();

  private static final String ORG_ID = "org_obs2106_closure_email";
  private static final String CONTACT_EMAIL = "obs2106-portal-contact@test.com";

  private static final String VALID_JUSTIFICATION =
      "Client withdrew mid-matter; all trust funds disbursed and no court dates remain outstanding.";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private OrgSettingsRepository orgSettingsRepository;
  @Autowired private OrgSettingsService orgSettingsService;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private MatterClosureService matterClosureService;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private ProjectRepository projectRepository;
  @Autowired private PortalContactRepository portalContactRepository;
  @Autowired private StatementService statementService;
  @Autowired private GeneratedDocumentRepository generatedDocumentRepository;
  @Autowired private DocumentRepository documentRepository;

  private String tenantSchema;
  private UUID memberId;
  private UUID customerId;

  @BeforeAll
  void setup() throws Exception {
    // legal-za vertical profile registers the matter-closure-letter template at provisioning time.
    provisioningService.provisionTenant(ORG_ID, "OBS-2106 Email Firm", "legal-za");

    memberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                ORG_ID,
                "user_obs2106_owner",
                "obs2106_owner@test.com",
                "OBS-2106 Owner",
                "owner"));

    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

    runInTenantAsOwner(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  // matter_closure module + disbursements (SoA pre-req) must be enabled.
                  var settings = orgSettingsService.getOrCreateForCurrentTenant();
                  settings.setEnabledModules(List.of("matter_closure", "disbursements"));
                  orgSettingsRepository.save(settings);

                  var customer =
                      createActiveCustomer("OBS-2106 Test Client", CONTACT_EMAIL, memberId);
                  customer = customerRepository.saveAndFlush(customer);
                  customerId = customer.getId();

                  // PortalContact is the recipient PortalDocumentNotificationHandler resolves via
                  // findPreferredByCustomerIdAndOrgId(customerId, orgId).
                  var contact =
                      new PortalContact(
                          ORG_ID,
                          customerId,
                          CONTACT_EMAIL,
                          "OBS-2106 Test Contact",
                          PortalContact.ContactRole.PRIMARY);
                  portalContactRepository.saveAndFlush(contact);
                }));
  }

  @BeforeEach
  void resetGreenMail() throws Exception {
    greenMail.purgeEmailFromAllMailboxes();
    // Each @Test creates its OWN projectId via createProject(...), so the dedup key
    // (tenant:customer:project:template) differs across tests — no need to invalidate the Caffeine
    // cache, and the clearDedupCacheForTesting() hook is package-private to portal/ anyway.
  }

  /**
   * OBS-2106 regression guard, amended by LZKC-015: closing a matter with both closure-letter and
   * SoA enabled MUST land one portal-document-ready email PER document (two total) at the
   * PortalContact. Both documents publish DocumentGeneratedEvent with visibility=PORTAL; the dedup
   * key includes the template name, so distinct doc types each notify while genuine re-emissions of
   * the same doc type stay deduped. (Pre-LZKC-015 the key omitted the template name, so the closure
   * letter claimed the key and the SoA email was silently swallowed — Day 60/61, cycle 2026-07-06.)
   * ADR-350: the firm explicitly asks for the email ({@code notifyClient=true}, implies share).
   */
  @Test
  void close_withClosureLetterAndSoa_dispatchesOnePortalEmailPerDocument() throws Exception {
    UUID projectId = createProject("OBS-2106 Closure Pack Matter");

    var response =
        runInTenantReturning(
            () ->
                matterClosureService.close(
                    projectId,
                    new ClosureRequest(
                        ClosureReason.CONCLUDED,
                        "OBS-2106 closure-pack regression",
                        /* generateClosureLetter */ true,
                        /* generateStatementOfAccount */ true,
                        /* override */ true,
                        VALID_JUSTIFICATION,
                        /* shareWithClient */ null,
                        /* notifyClient */ true),
                    memberId));

    // Sanity: both PDFs were produced + linked.
    assertThat(response.closureLetterDocumentId())
        .as("closure-letter doc id present when generateClosureLetter=true")
        .isNotNull();
    assertThat(response.statementOfAccountDocumentId())
        .as("SoA doc id present when generateStatementOfAccount=true")
        .isNotNull();

    // Allow AFTER_COMMIT listeners + GreenMail SMTP delivery to settle. GreenMail's
    // waitForIncomingEmail handles both the listener queue drain and the SMTP receive.
    boolean delivered = greenMail.waitForIncomingEmail(5_000L, 2);
    assertThat(delivered)
        .as(
            "LZKC-015: TWO portal-document-ready emails (closure letter + SoA) must arrive within"
                + " 5s of close")
        .isTrue();

    MimeMessage[] received = greenMail.getReceivedMessages();
    assertThat(received)
        .as(
            "LZKC-015: closure-letter + SoA are distinct documents — the client must be notified"
                + " once per document, not once per (tenant, customer, project)")
        .hasSize(2);
    for (MimeMessage message : received) {
      assertThat(message.getAllRecipients()[0].toString()).isEqualTo(CONTACT_EMAIL);
      assertThat(message.getSubject()).containsIgnoringCase("document ready");
    }
    // One email per document type: subjects carry the generated file names, which embed the
    // template slugs ("Closure Letter ..." pdf vs "Statement of Account ..." pdf).
    assertThat(received)
        .extracting(MimeMessage::getSubject)
        .as("one email references the closure letter, the other the statement of account")
        .anySatisfy(subject -> assertThat(subject).containsIgnoringCase("closure"))
        .anySatisfy(subject -> assertThat(subject).containsIgnoringCase("statement"));
  }

  /**
   * OBS-2106 Part 2 verification: when ONLY the closure-letter is generated (SoA suppressed), the
   * portal email STILL dispatches. This is the exact regression scenario from Day 60 — the
   * canonical {@code GeneratedDocumentService.generateDocument} event carries no {@code visibility}
   * key in details, so prior to Part 2 the listener relied on a DB fallback that races the
   * visibility flip's commit. The explicit visibility hint carried by {@code generateForProject}
   * eliminates the race. ADR-350: requires {@code notifyClient=true}.
   */
  @Test
  void close_withClosureLetterOnly_dispatchesPortalEmail() throws Exception {
    UUID projectId = createProject("OBS-2106 Closure Letter Only Matter");

    var response =
        runInTenantReturning(
            () ->
                matterClosureService.close(
                    projectId,
                    new ClosureRequest(
                        ClosureReason.CONCLUDED,
                        "OBS-2106 letter-only regression",
                        /* generateClosureLetter */ true,
                        /* generateStatementOfAccount */ false,
                        /* override */ true,
                        VALID_JUSTIFICATION,
                        /* shareWithClient */ null,
                        /* notifyClient */ true),
                    memberId));

    assertThat(response.closureLetterDocumentId()).isNotNull();
    assertThat(response.statementOfAccountDocumentId()).isNull();

    boolean delivered = greenMail.waitForIncomingEmail(5_000L, 1);
    assertThat(delivered)
        .as("OBS-2106 Part 2: closure-letter-only close must still dispatch the portal email")
        .isTrue();

    MimeMessage[] received = greenMail.getReceivedMessages();
    assertThat(received).hasSize(1);
    assertThat(received[0].getAllRecipients()[0].toString()).isEqualTo(CONTACT_EMAIL);
  }

  /**
   * OBS-2106 follow-up (slop-hunt PR-1246 finding 1): closing a matter must publish exactly ONE
   * canonical {@link DocumentGeneratedEvent} for the closure letter, and that event must carry
   * explicit {@code scope=PROJECT, visibility=PORTAL} in its {@code details} map. The original
   * #1246 fix worked around a race in the canonical emitter by publishing a SECOND compensating
   * event from {@code MatterClosureService.publishPortalReadyFollowUp} and relying on the 5-minute
   * Caffeine dedup in {@code PortalDocumentNotificationHandler} to coalesce the two into one email.
   * That dedup is best-effort (single-JVM, evicted on listener restart, cold-tenant empty) — under
   * unfortunate timing the listener could observe the canonical event first, fall through to a DB
   * read of the still-INTERNAL Document row, and silently skip the email. The structural fix routes
   * the closure-letter through {@code generateForProject} with explicit {@code
   * intendedVisibility=PORTAL}, so the linked Document is born PORTAL and the canonical event
   * carries the visibility hint atomically. The compensating second event is then unnecessary.
   * ADR-350: with {@code notifyClient=true} the event also carries {@code notify_client=true}.
   */
  @Test
  void close_publishesSingleCanonicalEventWithExplicitPortalVisibility(ApplicationEvents events)
      throws Exception {
    UUID projectId = createProject("OBS-2106 followup canonical event shape");

    runInTenantAsOwner(
        () ->
            matterClosureService.close(
                projectId,
                new ClosureRequest(
                    ClosureReason.CONCLUDED,
                    "OBS-2106 follow-up: canonical-event-shape regression",
                    /* generateClosureLetter */ true,
                    /* generateStatementOfAccount */ false,
                    /* override */ true,
                    VALID_JUSTIFICATION,
                    /* shareWithClient */ null,
                    /* notifyClient */ true),
                memberId));

    var closureLetterEvents =
        events.stream(DocumentGeneratedEvent.class)
            .filter(e -> "matter-closure-letter".equals(e.templateName()))
            .toList();

    assertThat(closureLetterEvents)
        .as(
            "OBS-2106 follow-up: a single canonical DocumentGeneratedEvent must be published per"
                + " closure letter — the second compensating event from publishPortalReadyFollowUp"
                + " is no longer needed once generateForProject carries the visibility hint")
        .hasSize(1);

    var details = closureLetterEvents.get(0).details();
    assertThat(details)
        .as(
            "OBS-2106 follow-up: canonical DocumentGeneratedEvent for closure letter must carry"
                + " scope=PROJECT and visibility=PORTAL in details (mirrors the SoA pattern in"
                + " StatementService:228-236), eliminating the DB-fallback race in"
                + " PortalDocumentNotificationHandler.isPortalVisible")
        .containsEntry("scope", "PROJECT")
        .containsEntry("visibility", "PORTAL")
        .containsEntry("notify_client", true);
  }

  /**
   * ADR-350 default counterpart: without intent flags the closure letter's canonical event carries
   * {@code visibility=INTERNAL} and {@code notify_client=false}.
   */
  @Test
  void close_withoutIntentFlags_canonicalEventCarriesInternalAndNotifyClientFalse(
      ApplicationEvents events) {
    UUID projectId = createProject("ADR-350 default canonical event shape");

    runInTenantAsOwner(
        () ->
            matterClosureService.close(
                projectId,
                closureRequest(
                    "ADR-350 default event shape",
                    true,
                    false, /* share */
                    null, /* notify */
                    null),
                memberId));

    var closureLetterEvents =
        events.stream(DocumentGeneratedEvent.class)
            .filter(e -> projectId.equals(e.projectId()))
            .filter(e -> "matter-closure-letter".equals(e.templateName()))
            .toList();
    assertThat(closureLetterEvents).hasSize(1);
    assertThat(closureLetterEvents.get(0).details())
        .containsEntry("scope", "PROJECT")
        .containsEntry("visibility", "INTERNAL")
        .containsEntry("notify_client", false);
  }

  /**
   * G-C5 reproduction: the firm's SoA "Preview &amp; Save" sends {@code {periodStart, periodEnd,
   * templateId}} only. Before ADR-350 the SoA was saved PORTAL and the client was emailed. Now it
   * must be INTERNAL and nothing is sent.
   */
  @Test
  void statementGenerate_withoutIntentFlags_sendsNoClientEmail_andIsInternal() {
    UUID projectId = createProject("G-C5 Preview and Save Matter");

    var response =
        runInTenantReturning(
            () ->
                statementService.generate(
                    projectId,
                    new GenerateStatementRequest(
                        LocalDate.now().minusMonths(1), LocalDate.now(), null, null, null),
                    memberId));

    greenMail.waitForIncomingEmail(2_000L, 1);
    assertThat(mailsToContact()).as("G-C5: Preview & Save must not email the client").isEmpty();
    assertThat(linkedDocumentVisibility(response.id())).isEqualTo(Document.Visibility.INTERNAL);
  }

  /**
   * G-D14 (emails) reproduction: closing with letter + SoA and no intent flags used to email the
   * client twice and publish both documents. Now both stay INTERNAL and nothing is sent.
   */
  @Test
  void close_withoutIntentFlags_sendsNoClientEmail_andDocumentsStayInternal() {
    UUID projectId = createProject("G-D14 Silent Close Matter");

    var response =
        runInTenantReturning(
            () ->
                matterClosureService.close(
                    projectId,
                    closureRequest("G-D14 silent close", true, true, null, null),
                    memberId));

    assertThat(response.closureLetterDocumentId()).isNotNull();
    assertThat(response.statementOfAccountDocumentId()).isNotNull();

    greenMail.waitForIncomingEmail(2_000L, 1);
    assertThat(mailsToContact()).as("G-D14: closing must not email the client").isEmpty();
    assertThat(linkedDocumentVisibility(response.closureLetterDocumentId()))
        .isEqualTo(Document.Visibility.INTERNAL);
    assertThat(linkedDocumentVisibility(response.statementOfAccountDocumentId()))
        .isEqualTo(Document.Visibility.INTERNAL);
  }

  /**
   * ADR-350: "Email the client" on the SoA dialog → exactly one email, and notify implies share.
   */
  @Test
  void statementGenerate_withNotifyClient_sendsOneEmailAndImpliesShare() throws Exception {
    UUID projectId = createProject("ADR-350 SoA Notify Matter");

    var response =
        runInTenantReturning(
            () ->
                statementService.generate(
                    projectId,
                    new GenerateStatementRequest(
                        LocalDate.now().minusMonths(1),
                        LocalDate.now(),
                        null,
                        /* shareWithClient */ null,
                        /* notifyClient */ true),
                    memberId));

    assertThat(greenMail.waitForIncomingEmail(5_000L, 1)).isTrue();
    var mails = mailsToContact();
    assertThat(mails).hasSize(1);
    assertThat(mails.get(0).getSubject()).containsIgnoringCase("statement");
    assertThat(linkedDocumentVisibility(response.id())).isEqualTo(Document.Visibility.PORTAL);
  }

  /** ADR-350: {@code shareWithClient} alone shares letter + SoA silently — no email. */
  @Test
  void close_withShareOnly_sendsNoEmail_documentsPortal() {
    UUID projectId = createProject("ADR-350 Share Only Close Matter");

    var response =
        runInTenantReturning(
            () ->
                matterClosureService.close(
                    projectId,
                    closureRequest("ADR-350 share-only close", true, true, true, null),
                    memberId));

    greenMail.waitForIncomingEmail(2_000L, 1);
    assertThat(mailsToContact()).as("share without notify must not email").isEmpty();
    assertThat(linkedDocumentVisibility(response.closureLetterDocumentId()))
        .isEqualTo(Document.Visibility.PORTAL);
    assertThat(linkedDocumentVisibility(response.statementOfAccountDocumentId()))
        .isEqualTo(Document.Visibility.PORTAL);
  }

  // ── helpers ────────────────────────────────────────────────────────────

  private ClosureRequest closureRequest(
      String notes,
      boolean generateClosureLetter,
      boolean generateStatementOfAccount,
      Boolean shareWithClient,
      Boolean notifyClient) {
    return new ClosureRequest(
        ClosureReason.CONCLUDED,
        notes,
        generateClosureLetter,
        generateStatementOfAccount,
        /* override */ true,
        VALID_JUSTIFICATION,
        shareWithClient,
        notifyClient);
  }

  private List<MimeMessage> mailsToContact() {
    return Arrays.stream(greenMail.getReceivedMessages())
        .filter(
            m -> {
              try {
                return CONTACT_EMAIL.equals(m.getAllRecipients()[0].toString());
              } catch (jakarta.mail.MessagingException e) {
                throw new IllegalStateException(e);
              }
            })
        .toList();
  }

  private String linkedDocumentVisibility(UUID generatedDocumentId) {
    return runInTenantReturning(
        () ->
            transactionTemplate.execute(
                tx -> {
                  var generated =
                      generatedDocumentRepository.findById(generatedDocumentId).orElseThrow();
                  return documentRepository
                      .findById(generated.getDocumentId())
                      .orElseThrow()
                      .getVisibility();
                }));
  }

  private UUID createProject(String name) {
    final UUID[] id = new UUID[1];
    runInTenantAsOwner(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var project = new Project(name, "OBS-2106 closure-email regression", memberId);
                  project.setCustomerId(customerId);
                  id[0] = projectRepository.saveAndFlush(project).getId();
                }));
    return id[0];
  }

  private void runInTenantAsOwner(Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .where(
            RequestScopes.CAPABILITIES,
            Set.copyOf(io.b2mash.b2b.b2bstrawman.orgrole.Capability.ALL_NAMES))
        .run(action);
  }

  private <T> T runInTenantReturning(java.util.function.Supplier<T> action) {
    Object[] holder = new Object[1];
    runInTenantAsOwner(() -> holder[0] = action.get());
    @SuppressWarnings("unchecked")
    T value = (T) holder[0];
    return value;
  }
}
