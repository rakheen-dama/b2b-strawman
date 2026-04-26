package io.b2mash.b2b.b2bstrawman.verticals.legal.statement;

import static io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory.createActiveCustomer;
import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.customerbackend.event.DocumentCreatedEvent;
import io.b2mash.b2b.b2bstrawman.document.Document;
import io.b2mash.b2b.b2bstrawman.document.DocumentRepository;
import io.b2mash.b2b.b2bstrawman.event.DocumentGeneratedEvent;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsService;
import io.b2mash.b2b.b2bstrawman.template.GeneratedDocumentRepository;
import io.b2mash.b2b.b2bstrawman.template.TemplateEntityType;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import io.b2mash.b2b.b2bstrawman.verticals.legal.statement.dto.GenerateStatementRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.statement.dto.StatementResponse;
import io.b2mash.b2b.b2bstrawman.verticals.legal.statement.event.StatementOfAccountGeneratedEvent;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
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
 * GAP-L-74 part A regression: {@link StatementService#generate} must persist a paired {@link
 * Document} row (with {@code visibility=SHARED}, {@code status=UPLOADED}, the same {@code s3Key} as
 * the {@code GeneratedDocument}) and link it back via {@code generatedDoc.linkToDocument(...)} so
 * the SoA appears on the standard documents pipeline that the portal queries.
 *
 * <p>Pre-fix evidence (Day 61 verify): {@code generated_documents.document_id} was {@code NULL} for
 * SoA outputs and no row existed in {@code documents}, so the Statement of Account never showed up
 * on either firm-side or portal-side Documents tabs.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@RecordApplicationEvents
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StatementServicePortalDocumentTest {

  private static final String ORG_ID = "org_stmt_portal_doc";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private OrgSettingsRepository orgSettingsRepository;
  @Autowired private OrgSettingsService orgSettingsService;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private ProjectRepository projectRepository;
  @Autowired private StatementService statementService;
  @Autowired private GeneratedDocumentRepository generatedDocumentRepository;
  @Autowired private DocumentRepository documentRepository;
  @Autowired private ApplicationEvents events;

  private String tenantSchema;
  private UUID memberId;
  private UUID customerId;
  private UUID projectId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Stmt Portal Doc Firm", "legal-za");

    memberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                ORG_ID,
                "user_stmt_portal_owner",
                "stmt_portal_owner@test.com",
                "Stmt Portal Owner",
                "owner"));

    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var settings = orgSettingsService.getOrCreateForCurrentTenant();
                  settings.setEnabledModules(List.of("disbursements"));
                  orgSettingsRepository.save(settings);

                  var customer =
                      createActiveCustomer("Stmt Portal Client", "stmt_portal@test.com", memberId);
                  customer = customerRepository.saveAndFlush(customer);
                  customerId = customer.getId();

                  var project = new Project("Stmt Portal Matter", "L-74a regression", memberId);
                  project.setCustomerId(customerId);
                  projectId = projectRepository.saveAndFlush(project).getId();
                }));
  }

  @Test
  void generate_persistsPairedDocumentRow_withSharedVisibility_andLinksGeneratedDocument() {
    StatementResponse response =
        runInTenantReturning(
            () ->
                statementService.generate(
                    projectId,
                    new GenerateStatementRequest(
                        LocalDate.parse("2026-04-01"),
                        LocalDate.parse("2026-04-30"),
                        /* templateId */ null),
                    memberId));

    assertThat(response).isNotNull();
    assertThat(response.id()).isNotNull();

    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  // generated_documents.document_id is populated.
                  var generated = generatedDocumentRepository.findById(response.id()).orElseThrow();
                  assertThat(generated.getDocumentId())
                      .as(
                          "GAP-L-74 part A: SoA must persist a paired Document and link"
                              + " back via document_id")
                      .isNotNull();

                  // GAP-L-74-followup: the paired Document is PORTAL (system-auto-shared) +
                  // UPLOADED + same S3 key as the GeneratedDocument so the portal Documents tab
                  // can list + presign-download it. PORTAL distinguishes system-auto-share from
                  // a firm user manually clicking "share" (Visibility.SHARED).
                  var paired = documentRepository.findById(generated.getDocumentId()).orElseThrow();
                  assertThat(paired.getVisibility()).isEqualTo(Document.Visibility.PORTAL);
                  assertThat(paired.getStatus()).isEqualTo(Document.Status.UPLOADED);
                  assertThat(paired.getS3Key()).isEqualTo(generated.getS3Key());
                  assertThat(paired.getProjectId()).isEqualTo(projectId);
                  assertThat(paired.getScope()).isEqualTo(Document.Scope.PROJECT);
                  assertThat(paired.getFileName()).isEqualTo(generated.getFileName());
                  assertThat(paired.getContentType()).isEqualTo("application/pdf");
                  assertThat(paired.getUploadedBy()).isEqualTo(memberId);
                }));
  }

  /**
   * GAP-OBS-Day61 / E2.5 regression: SoA generation must publish BOTH {@link
   * StatementOfAccountGeneratedEvent} (the SoA-specific domain event) AND {@link
   * DocumentGeneratedEvent} (the canonical event consumed by NotificationEventHandler /
   * activity-feed listeners). Pre-fix evidence: {@code StatementService.generate} only published
   * StatementOfAccountGeneratedEvent, so SoA outputs never showed up on the activity feed or
   * triggered notifications — unlike standard generated documents going through {@code
   * GeneratedDocumentService.generateDocument}.
   */
  @Test
  void generate_publishesBothStatementAndDocumentGeneratedEvents() {
    StatementResponse response =
        runInTenantReturning(
            () ->
                statementService.generate(
                    projectId,
                    new GenerateStatementRequest(
                        LocalDate.parse("2026-05-01"),
                        LocalDate.parse("2026-05-31"),
                        /* templateId */ null),
                    memberId));

    assertThat(response).isNotNull();

    // StatementOfAccountGeneratedEvent — preserved (existing behaviour).
    var statementEvents =
        events.stream(StatementOfAccountGeneratedEvent.class)
            .filter(e -> e.generatedDocumentId().equals(response.id()))
            .toList();
    assertThat(statementEvents)
        .as("StatementOfAccountGeneratedEvent must still publish")
        .hasSize(1);

    // DocumentGeneratedEvent — new (E2.5 fix). Must mirror GeneratedDocumentService:209 shape so
    // notification + activity-feed listeners pick up SoA artefacts the same way as canonical
    // generated documents.
    var documentEvents =
        events.stream(DocumentGeneratedEvent.class)
            .filter(
                e ->
                    e.generatedDocumentId() != null
                        && e.generatedDocumentId().equals(response.id()))
            .toList();
    assertThat(documentEvents)
        .as("DocumentGeneratedEvent must be published from SoA generate")
        .hasSize(1);

    var docEvent = documentEvents.get(0);
    assertThat(docEvent.eventType()).isEqualTo("document.generated");
    assertThat(docEvent.entityType()).isEqualTo("generated_document");
    assertThat(docEvent.entityId()).isEqualTo(response.id());
    assertThat(docEvent.projectId()).isEqualTo(projectId);
    assertThat(docEvent.actorMemberId()).isEqualTo(memberId);
    assertThat(docEvent.tenantId()).isEqualTo(tenantSchema);
    assertThat(docEvent.orgId()).isEqualTo(ORG_ID);
    assertThat(docEvent.occurredAt()).isNotNull();
    assertThat(docEvent.primaryEntityType()).isEqualTo(TemplateEntityType.PROJECT);
    assertThat(docEvent.primaryEntityId()).isEqualTo(projectId);
    assertThat(docEvent.fileName()).isNotBlank();
    assertThat(docEvent.templateName()).isNotBlank();
    assertThat(docEvent.details())
        .containsEntry("scope", "PROJECT")
        .containsEntry("visibility", "PORTAL")
        .containsKey("file_name")
        .containsKey("template_name");

    // DocumentCreatedEvent — new (E2.5 fix). Drives the portal.portal_documents projection via
    // PortalEventHandler.onDocumentCreated. Without this, SoA documents never surface in the
    // portal projection (the gap the slice is named after).
    var createdEvents =
        events.stream(DocumentCreatedEvent.class)
            .filter(e -> response.id() != null && e.getDocumentId() != null)
            .filter(e -> e.getProjectId() != null && e.getProjectId().equals(projectId))
            .toList();
    assertThat(createdEvents)
        .as("DocumentCreatedEvent must be published so the portal projection upserts SoA rows")
        .hasSize(1);
    var createdEvent = createdEvents.get(0);
    assertThat(createdEvent.getVisibility()).isEqualTo("PORTAL");
    assertThat(createdEvent.getFileName()).isNotBlank();
    assertThat(createdEvent.getOrgId()).isEqualTo(ORG_ID);
    assertThat(createdEvent.getTenantId()).isEqualTo(tenantSchema);
  }

  // ── helpers ────────────────────────────────────────────────────────────

  private void runInTenant(Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(action);
  }

  private <T> T runInTenantReturning(java.util.function.Supplier<T> action) {
    @SuppressWarnings("unchecked")
    Object[] holder = new Object[1];
    runInTenant(() -> holder[0] = action.get());
    @SuppressWarnings("unchecked")
    T value = (T) holder[0];
    return value;
  }
}
