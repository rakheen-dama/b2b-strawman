package io.b2mash.b2b.b2bstrawman.verticals.legal.closure;

import static io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory.createActiveCustomer;
import static org.assertj.core.api.Assertions.assertThat;

import com.icegreen.greenmail.util.GreenMail;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
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
import io.b2mash.b2b.b2bstrawman.testutil.GreenMailTestSupport;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import io.b2mash.b2b.b2bstrawman.verticals.legal.closure.dto.ClosureReason;
import io.b2mash.b2b.b2bstrawman.verticals.legal.closure.dto.ClosureRequest;
import jakarta.mail.internet.MimeMessage;
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
 *       DocumentGeneratedEvent}s with explicit {@code visibility=PORTAL} (SoA via {@code
 *       StatementService}, closure-letter via the OBS-2106 Part 2 follow-up event in {@code
 *       MatterClosureService#publishPortalReadyFollowUp}).
 *   <li>Assert the GreenMail JVM singleton receives exactly ONE portal-document-ready email (dedup
 *       coalesces the two events into a single send per the 5-minute Caffeine window in {@code
 *       PortalDocumentNotificationHandler}).
 * </ol>
 *
 * <p>Without this test, OBS-2106 would re-regress silently: the existing closure-letter test
 * (`close_withGenerateClosureLetterTrue_flipsLinkedDocumentVisibility_toPortal`) only checks the
 * persisted Document visibility, not whether the AFTER_COMMIT listener actually fired and sent the
 * email.
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
                  // Re-assert default allowlist explicitly — V117 seeds it but a future migration
                  // could change defaults.
                  settings
                      .getPortal()
                      .setPortalNotificationDocTypes(
                          List.of("matter-closure-letter", "statement-of-account"));
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
    // (tenant:customer:project) differs across tests — no need to invalidate the Caffeine cache,
    // and the clearDedupCacheForTesting() hook is package-private to portal/ anyway.
  }

  /**
   * OBS-2106 regression guard: closing a matter with both closure-letter and SoA enabled MUST land
   * exactly one portal-document-ready email at the PortalContact. Both documents publish
   * DocumentGeneratedEvent with visibility=PORTAL; dedup coalesces them into a single send.
   */
  @Test
  void close_withClosureLetterAndSoa_dispatchesExactlyOnePortalEmail() throws Exception {
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
                        VALID_JUSTIFICATION),
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
    boolean delivered = greenMail.waitForIncomingEmail(5_000L, 1);
    assertThat(delivered)
        .as("OBS-2106: at least one portal-document-ready email must arrive within 5s of close")
        .isTrue();

    MimeMessage[] received = greenMail.getReceivedMessages();
    assertThat(received)
        .as(
            "OBS-2106 dedup invariant: closure-letter + SoA inside the same 5-min window must"
                + " coalesce to a single portal email keyed on (tenant, customer, project)")
        .hasSize(1);
    assertThat(received[0].getAllRecipients()[0].toString()).isEqualTo(CONTACT_EMAIL);
    assertThat(received[0].getSubject()).containsIgnoringCase("document ready");
  }

  /**
   * OBS-2106 Part 2 verification: when ONLY the closure-letter is generated (SoA suppressed), the
   * portal email STILL dispatches. This is the exact regression scenario from Day 60 — the
   * canonical {@code GeneratedDocumentService.generateDocument} event carries no {@code visibility}
   * key in details, so prior to Part 2 the listener relied on a DB fallback that races the
   * visibility flip's commit. The follow-up event from {@link
   * MatterClosureService#publishPortalReadyFollowUp} eliminates the race.
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
                        VALID_JUSTIFICATION),
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
                    VALID_JUSTIFICATION),
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
        .containsEntry("visibility", "PORTAL");
  }

  // ── helpers ────────────────────────────────────────────────────────────

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
