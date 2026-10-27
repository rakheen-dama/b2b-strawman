package io.b2mash.b2b.b2bstrawman.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.audit.AuditEventFilter;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.customer.CustomerProject;
import io.b2mash.b2b.b2bstrawman.customer.CustomerProjectRepository;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.integration.storage.StorageService;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.notification.NotificationRepository;
import io.b2mash.b2b.b2bstrawman.portal.PortalContact;
import io.b2mash.b2b.b2bstrawman.portal.PortalContactRepository;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.template.DocumentTemplateRepository;
import io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Replacement-path test for Epic 597 (Phase 86 slice 3, ADR-336): with the Phase 32 proposal
 * pipeline deleted, an engagement letter is a GeneratedDocument from the legal-za {@code
 * engagement-letter-general} template, sent through Phase 28 acceptance and accepted in the portal.
 * Proves: generation → acceptance request → portal accept → certificate rendered and stored →
 * {@code acceptance.accepted} audited → {@code AcceptanceRequestAcceptedEvent} consumed ({@code
 * ACCEPTANCE_COMPLETED} notification) — with no {@code ..proposal..} package on the classpath and
 * the {@code /api/proposals} route unmapped.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EngagementLetterWithoutProposalIntegrationTest {
  private static final String ORG_ID = "org_el_without_proposal";
  private static final String OWNER = "user_elwp_owner";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private DocumentTemplateRepository documentTemplateRepository;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private ProjectRepository projectRepository;
  @Autowired private CustomerProjectRepository customerProjectRepository;
  @Autowired private PortalContactRepository portalContactRepository;
  @Autowired private AcceptanceRequestRepository acceptanceRequestRepository;
  @Autowired private AuditService auditService;
  @Autowired private NotificationRepository notificationRepository;
  @Autowired private StorageService storageService; // InMemoryStorageService (@Primary)
  @Autowired private TransactionTemplate transactionTemplate;

  private String tenantSchema;
  private UUID memberId;
  private UUID templateId;
  private UUID projectId;
  private UUID portalContactId;

  @BeforeAll
  void setup() throws Exception {
    tenantSchema =
        provisioningService
            .provisionTenant(ORG_ID, "Engagement Letter Without Proposal Org", "legal-za")
            .schemaName();
    memberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, OWNER, "elwp_owner@test.com", "ELWP Owner", "owner"));

    inTenant(
        () ->
            transactionTemplate.execute(
                tx -> {
                  templateId =
                      documentTemplateRepository
                          .findPackSeeded("legal-za", "engagement-letter-general")
                          .getFirst()
                          .getId();
                  var customer =
                      customerRepository.save(
                          TestCustomerFactory.createActiveCustomer(
                              "Sipho Dlamini", "sipho.elwp@test.com", memberId));
                  var project =
                      projectRepository.save(
                          new Project("Dlamini advisory", "Engagement letter matter", memberId));
                  projectId = project.getId();
                  customerProjectRepository.save(
                      new CustomerProject(customer.getId(), projectId, memberId));
                  portalContactId =
                      portalContactRepository
                          .save(
                              new PortalContact(
                                  ORG_ID,
                                  customer.getId(),
                                  "sipho.portal.elwp@test.com",
                                  "Sipho Dlamini",
                                  PortalContact.ContactRole.PRIMARY))
                          .getId();
                  return null;
                }));
  }

  @Test
  void engagementLetterIsGeneratedSentAcceptedCertifiedAndAudited() throws Exception {
    // 1. Generate the engagement letter from the legal-za pack template (PROJECT-typed).
    var gen =
        mockMvc
            .perform(
                post("/api/templates/" + templateId + "/generate")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"entityId": "%s", "saveToDocuments": true, "acknowledgeWarnings": true}
                        """
                            .formatted(projectId)))
            .andExpect(status().isCreated())
            .andReturn();
    String generatedDocumentId = JsonPath.read(gen.getResponse().getContentAsString(), "$.id");

    // 2. Send it for acceptance (Phase 28).
    var sent =
        mockMvc
            .perform(
                post("/api/acceptance-requests")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"generatedDocumentId": "%s", "portalContactId": "%s"}
                        """
                            .formatted(generatedDocumentId, portalContactId)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("SENT"))
            .andReturn();
    UUID requestId =
        UUID.fromString(JsonPath.read(sent.getResponse().getContentAsString(), "$.id"));
    String token =
        inTenant(() -> acceptanceRequestRepository.findById(requestId).orElseThrow())
            .getRequestToken();

    // 3. Client accepts in the portal (token auth, permitAll).
    mockMvc
        .perform(
            post("/api/portal/acceptance/" + token + "/accept")
                .contentType(MediaType.APPLICATION_JSON)
                .header("User-Agent", "ELWP-Test/1.0")
                .content("{\"name\": \"Sipho Dlamini\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ACCEPTED"))
        .andExpect(jsonPath("$.acceptorName").value("Sipho Dlamini"));

    // 4. Certificate rendered and stored (generation failure is swallowed in prod code, so assert
    //    the key AND the bytes).
    var accepted = inTenant(() -> acceptanceRequestRepository.findById(requestId).orElseThrow());
    assertThat(accepted.getStatus()).isEqualTo(AcceptanceStatus.ACCEPTED);
    assertThat(accepted.getCertificateS3Key())
        .isEqualTo(tenantSchema + "/certificates/" + requestId + "/certificate.pdf");
    byte[] certificate = storageService.download(accepted.getCertificateS3Key());
    assertThat(new String(certificate, 0, 4, StandardCharsets.US_ASCII)).isEqualTo("%PDF");

    // 5. acceptance.* audited; 6. AcceptanceRequestAcceptedEvent consumed (Option A).
    inTenant(
        () -> {
          for (String type :
              new String[] {"acceptance.accepted", "acceptance.certificate_generated"}) {
            var page =
                auditService.findEvents(
                    new AuditEventFilter(
                        "acceptance_request", requestId, null, type, null, null, null),
                    PageRequest.of(0, 10));
            assertThat(page.getTotalElements()).as(type).isGreaterThanOrEqualTo(1);
          }
          assertThat(
                  notificationRepository.existsByTypeAndReferenceEntityId(
                      "ACCEPTANCE_COMPLETED", requestId))
              .isTrue();
          return null;
        });
  }

  @Test
  void proposalPackageIsAbsentFromTheClasspath() {
    var classes =
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("io.b2mash.b2b.b2bstrawman");
    // Non-vacuous: the importer must actually see production classes (the acceptance package).
    assertThat(classes.stream().map(c -> c.getPackageName()))
        .contains("io.b2mash.b2b.b2bstrawman.acceptance")
        .noneMatch(
            p ->
                p.equals("io.b2mash.b2b.b2bstrawman.proposal")
                    || p.startsWith("io.b2mash.b2b.b2bstrawman.proposal."));
    assertThatThrownBy(() -> Class.forName("io.b2mash.b2b.b2bstrawman.proposal.Proposal"))
        .isInstanceOf(ClassNotFoundException.class);
    assertThatThrownBy(() -> Class.forName("io.b2mash.b2b.b2bstrawman.event.ProposalSentEvent"))
        .isInstanceOf(ClassNotFoundException.class);
  }

  @Test
  void proposalRouteIsGone() throws Exception {
    // Was ProposalController @GetMapping("/api/proposals"). Unmapped → 404, not 403.
    mockMvc
        .perform(get("/api/proposals").with(TestJwtFactory.ownerJwt(ORG_ID, OWNER)))
        .andExpect(status().isNotFound());
  }

  private <T> T inTenant(Supplier<T> action) {
    return ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .call(action::get);
  }
}
