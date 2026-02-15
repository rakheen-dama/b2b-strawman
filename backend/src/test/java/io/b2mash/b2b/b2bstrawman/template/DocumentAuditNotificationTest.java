package io.b2mash.b2b.b2bstrawman.template;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.audit.AuditEventRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DocumentAuditNotificationTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_audit_notif_test";
  private static final String TEST_BUCKET = "test-bucket";

  static LocalStackContainer localstack =
      new LocalStackContainer(DockerImageName.parse("localstack/localstack:latest"))
          .withServices(LocalStackContainer.Service.S3);

  static {
    localstack.start();
  }

  @DynamicPropertySource
  static void overrideS3Properties(
      org.springframework.test.context.DynamicPropertyRegistry registry) {
    registry.add(
        "aws.s3.endpoint",
        () -> localstack.getEndpointOverride(LocalStackContainer.Service.S3).toString());
    registry.add("aws.s3.region", localstack::getRegion);
    registry.add("aws.s3.bucket-name", () -> TEST_BUCKET);
    registry.add("aws.credentials.access-key-id", localstack::getAccessKey);
    registry.add("aws.credentials.secret-access-key", localstack::getSecretKey);
  }

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private DocumentTemplateRepository documentTemplateRepository;
  @Autowired private AuditEventRepository auditEventRepository;
  @Autowired private ProjectRepository projectRepository;
  @Autowired private TransactionTemplate transactionTemplate;

  private String tenantSchema;
  private String memberIdOwner;
  private UUID testProjectId;
  private UUID testTemplateId;

  @BeforeAll
  void setup() throws Exception {
    try (var s3 =
        S3Client.builder()
            .endpointOverride(localstack.getEndpointOverride(LocalStackContainer.Service.S3))
            .region(Region.of(localstack.getRegion()))
            .credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(
                        localstack.getAccessKey(), localstack.getSecretKey())))
            .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
            .build()) {
      s3.createBucket(CreateBucketRequest.builder().bucket(TEST_BUCKET).build());
    }

    provisioningService.provisionTenant(ORG_ID, "Audit Notif Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");

    memberIdOwner =
        syncMember(ORG_ID, "user_audit_owner", "audit_owner@test.com", "Audit Owner", "owner");

    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var project =
                      new Project(
                          "Audit Test Project", "For audit tests", UUID.fromString(memberIdOwner));
                  project = projectRepository.save(project);
                  testProjectId = project.getId();

                  var template =
                      new DocumentTemplate(
                          TemplateEntityType.PROJECT,
                          "Audit Template",
                          "audit-template",
                          TemplateCategory.ENGAGEMENT_LETTER,
                          "<h1 th:text=\"${project.name}\">Name</h1>");
                  template = documentTemplateRepository.save(template);
                  testTemplateId = template.getId();
                }));
  }

  @Test
  void shouldCreateAuditEventOnTemplateCreation() throws Exception {
    mockMvc
        .perform(
            post("/api/templates")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name": "Audit Created Template",
                      "category": "REPORT",
                      "primaryEntityType": "PROJECT",
                      "content": "<p>Test</p>"
                    }
                    """))
        .andExpect(status().isCreated());

    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var page =
                      auditEventRepository.findByFilter(
                          "document_template",
                          null,
                          null,
                          "template.created",
                          null,
                          null,
                          PageRequest.of(0, 10));
                  assertThat(page.getContent()).isNotEmpty();
                  var latestEvent = page.getContent().getFirst();
                  assertThat(latestEvent.getEntityType()).isEqualTo("document_template");
                  assertThat(latestEvent.getEventType()).isEqualTo("template.created");
                }));
  }

  @Test
  void shouldCreateAuditEventOnTemplateClone() throws Exception {
    final UUID[] platformTemplateId = new UUID[1];
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var template =
                      new DocumentTemplate(
                          TemplateEntityType.PROJECT,
                          "Clone Source Template",
                          "clone-source-audit",
                          TemplateCategory.PROPOSAL,
                          "<p>Source</p>");
                  template.setSource(TemplateSource.PLATFORM);
                  template = documentTemplateRepository.save(template);
                  platformTemplateId[0] = template.getId();
                }));

    mockMvc
        .perform(post("/api/templates/" + platformTemplateId[0] + "/clone").with(ownerJwt()))
        .andExpect(status().isCreated());

    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var page =
                      auditEventRepository.findByFilter(
                          "document_template",
                          null,
                          null,
                          "template.cloned",
                          null,
                          null,
                          PageRequest.of(0, 10));
                  assertThat(page.getContent()).isNotEmpty();
                  var latestEvent = page.getContent().getFirst();
                  assertThat(latestEvent.getEventType()).isEqualTo("template.cloned");
                }));
  }

  @Test
  void shouldCreateAuditEventOnDocumentGeneration() throws Exception {
    mockMvc
        .perform(
            post("/api/templates/" + testTemplateId + "/generate")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"entityId": "%s", "saveToDocuments": false}
                    """
                        .formatted(testProjectId)))
        .andExpect(status().isOk());

    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var page =
                      auditEventRepository.findByFilter(
                          "generated_document",
                          null,
                          null,
                          "document.generated",
                          null,
                          null,
                          PageRequest.of(0, 10));
                  assertThat(page.getContent()).isNotEmpty();
                  var latestEvent = page.getContent().getFirst();
                  assertThat(latestEvent.getEventType()).isEqualTo("document.generated");
                  assertThat(latestEvent.getEntityType()).isEqualTo("generated_document");
                }));
  }

  // --- JWT Helpers ---

  private JwtRequestPostProcessor ownerJwt() {
    return jwt()
        .jwt(j -> j.subject("user_audit_owner").claim("o", Map.of("id", ORG_ID, "rol", "owner")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_OWNER")));
  }

  // --- Helpers ---

  private void runInTenant(Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, UUID.fromString(memberIdOwner))
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(action);
  }

  private String syncMember(
      String orgId, String clerkUserId, String email, String name, String orgRole)
      throws Exception {
    var result =
        mockMvc
            .perform(
                post("/internal/members/sync")
                    .header("X-API-KEY", API_KEY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "clerkOrgId": "%s",
                          "clerkUserId": "%s",
                          "email": "%s",
                          "name": "%s",
                          "avatarUrl": null,
                          "orgRole": "%s"
                        }
                        """
                            .formatted(orgId, clerkUserId, email, name, orgRole)))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.memberId");
  }
}
