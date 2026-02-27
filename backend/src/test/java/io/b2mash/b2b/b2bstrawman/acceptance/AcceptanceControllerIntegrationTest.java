package io.b2mash.b2b.b2bstrawman.acceptance;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.integration.storage.StorageService;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AcceptanceControllerIntegrationTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_acceptance_ctrl_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private StorageService storageService;

  private String schema;
  private String ownerMemberId;
  private final UUID customerId = UUID.randomUUID();
  private final UUID portalContactId = UUID.randomUUID();
  private final UUID templateId = UUID.randomUUID();
  private final UUID generatedDocumentId = UUID.randomUUID();

  private String createdRequestId;

  @BeforeAll
  void provisionAndSeed() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Acceptance Ctrl Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");

    ownerMemberId =
        syncMember(ORG_ID, "user_acc_ctrl_owner", "acc_ctrl_owner@test.com", "Acc Owner", "owner");
    syncMember(ORG_ID, "user_acc_ctrl_admin", "acc_ctrl_admin@test.com", "Acc Admin", "admin");
    syncMember(ORG_ID, "user_acc_ctrl_member", "acc_ctrl_member@test.com", "Acc Member", "member");

    schema = orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

    // Seed customer
    jdbcTemplate.update(
        """
        INSERT INTO "%s".customers (id, name, email, created_by, lifecycle_status, created_at, updated_at)
        VALUES (?::uuid, ?, ?, ?::uuid, 'ACTIVE', NOW(), NOW())
        """
            .formatted(schema),
        customerId.toString(),
        "Test Customer",
        "customer@test.com",
        ownerMemberId);

    // Seed portal contact
    jdbcTemplate.update(
        """
        INSERT INTO "%s".portal_contacts (id, org_id, customer_id, email, display_name, role, status, created_at, updated_at)
        VALUES (?::uuid, ?, ?::uuid, ?, ?, 'GENERAL', 'ACTIVE', NOW(), NOW())
        """
            .formatted(schema),
        portalContactId.toString(),
        ORG_ID,
        customerId.toString(),
        "contact@test.com",
        "Test Contact");

    // Seed document template
    jdbcTemplate.update(
        """
        INSERT INTO "%s".document_templates (id, name, slug, category, primary_entity_type, content, source, active, created_at, updated_at)
        VALUES (?::uuid, ?, ?, 'ENGAGEMENT_LETTER', 'CUSTOMER', '<p>Test</p>', 'ORG_CUSTOM', true, NOW(), NOW())
        """
            .formatted(schema),
        templateId.toString(),
        "Test Template",
        "test-template-ctrl");

    // Seed generated document
    jdbcTemplate.update(
        """
        INSERT INTO "%s".generated_documents (id, template_id, primary_entity_type, primary_entity_id, file_name, s3_key, file_size, generated_by, generated_at)
        VALUES (?::uuid, ?::uuid, 'CUSTOMER', ?::uuid, ?, ?, 1024, ?::uuid, NOW())
        """
            .formatted(schema),
        generatedDocumentId.toString(),
        templateId.toString(),
        customerId.toString(),
        "test-doc.pdf",
        "test/acceptance-ctrl-doc.pdf",
        ownerMemberId);
  }

  @Test
  @Order(1)
  void post_create_returns201_asOwner() throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/acceptance-requests")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"generatedDocumentId":"%s","portalContactId":"%s","expiryDays":14}
                        """
                            .formatted(generatedDocumentId, portalContactId)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").exists())
            .andExpect(jsonPath("$.status").value("SENT"))
            .andExpect(jsonPath("$.generatedDocumentId").value(generatedDocumentId.toString()))
            .andExpect(jsonPath("$.portalContactId").value(portalContactId.toString()))
            .andExpect(jsonPath("$.customerId").value(customerId.toString()))
            .andExpect(jsonPath("$.contact.displayName").value("Test Contact"))
            .andExpect(jsonPath("$.contact.email").value("contact@test.com"))
            .andExpect(jsonPath("$.document.fileName").value("test-doc.pdf"))
            .andReturn();
    createdRequestId = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
  }

  @Test
  @Order(2)
  void post_create_returns403_asMember() throws Exception {
    mockMvc
        .perform(
            post("/api/acceptance-requests")
                .with(memberJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"generatedDocumentId":"%s","portalContactId":"%s"}
                    """
                        .formatted(generatedDocumentId, portalContactId)))
        .andExpect(status().isForbidden());
  }

  @Test
  @Order(3)
  void get_listByDocument_returns200() throws Exception {
    mockMvc
        .perform(
            get("/api/acceptance-requests")
                .param("documentId", generatedDocumentId.toString())
                .with(memberJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$[0].id").value(createdRequestId));
  }

  @Test
  @Order(4)
  void get_listByCustomer_returns200() throws Exception {
    mockMvc
        .perform(
            get("/api/acceptance-requests")
                .param("customerId", customerId.toString())
                .with(memberJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$[0].id").value(createdRequestId));
  }

  @Test
  @Order(5)
  void get_detail_returns200() throws Exception {
    mockMvc
        .perform(get("/api/acceptance-requests/" + createdRequestId).with(memberJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(createdRequestId))
        .andExpect(jsonPath("$.status").value("SENT"))
        .andExpect(jsonPath("$.contact").exists())
        .andExpect(jsonPath("$.document").exists());
  }

  @Test
  @Order(6)
  void post_remind_returns200_asAdmin() throws Exception {
    mockMvc
        .perform(post("/api/acceptance-requests/" + createdRequestId + "/remind").with(adminJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(createdRequestId))
        .andExpect(jsonPath("$.reminderCount").value(1));
  }

  @Test
  @Order(7)
  void post_remind_returns403_asMember() throws Exception {
    mockMvc
        .perform(post("/api/acceptance-requests/" + createdRequestId + "/remind").with(memberJwt()))
        .andExpect(status().isForbidden());
  }

  @Test
  @Order(8)
  void post_revoke_returns200_asOwner() throws Exception {
    mockMvc
        .perform(post("/api/acceptance-requests/" + createdRequestId + "/revoke").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(createdRequestId))
        .andExpect(jsonPath("$.status").value("REVOKED"));
  }

  @Test
  @Order(9)
  void post_revoke_returns403_asMember() throws Exception {
    mockMvc
        .perform(post("/api/acceptance-requests/" + createdRequestId + "/revoke").with(memberJwt()))
        .andExpect(status().isForbidden());
  }

  @Test
  @Order(10)
  void get_certificate_returns404_whenNoCertificate() throws Exception {
    // Create a new request (the revoked one won't work for certificate)
    var result =
        mockMvc
            .perform(
                post("/api/acceptance-requests")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"generatedDocumentId":"%s","portalContactId":"%s"}
                        """
                            .formatted(generatedDocumentId, portalContactId)))
            .andExpect(status().isCreated())
            .andReturn();
    String newRequestId = JsonPath.read(result.getResponse().getContentAsString(), "$.id");

    // Certificate not generated yet => 404
    mockMvc
        .perform(get("/api/acceptance-requests/" + newRequestId + "/certificate").with(memberJwt()))
        .andExpect(status().isNotFound());
  }

  @Test
  @Order(11)
  void get_certificate_returns200_whenCertificateExists() throws Exception {
    // Upload a test PDF to S3
    String s3Key = "certificates/test-cert.pdf";
    byte[] pdfBytes = "%PDF-1.4 test certificate content".getBytes();
    storageService.upload(s3Key, pdfBytes, "application/pdf");

    // Insert a completed acceptance request with certificate via SQL
    UUID certRequestId = UUID.randomUUID();
    jdbcTemplate.update(
        """
        INSERT INTO "%s".acceptance_requests
          (id, generated_document_id, portal_contact_id, customer_id, status,
           request_token, sent_at, accepted_at, expires_at, acceptor_name,
           certificate_s3_key, certificate_file_name,
           sent_by_member_id, reminder_count, created_at, updated_at)
        VALUES (?::uuid, ?::uuid, ?::uuid, ?::uuid, 'ACCEPTED',
                ?, NOW(), NOW(), NOW() + INTERVAL '30 days', 'Jane Doe',
                ?, ?,
                ?::uuid, 0, NOW(), NOW())
        """
            .formatted(schema),
        certRequestId.toString(),
        generatedDocumentId.toString(),
        portalContactId.toString(),
        customerId.toString(),
        "cert-test-token-" + UUID.randomUUID(),
        s3Key,
        "certificate-of-acceptance.pdf",
        ownerMemberId);

    mockMvc
        .perform(
            get("/api/acceptance-requests/" + certRequestId + "/certificate").with(memberJwt()))
        .andExpect(status().isOk())
        .andExpect(header().string("Content-Type", "application/pdf"))
        .andExpect(
            header()
                .string(
                    "Content-Disposition",
                    "attachment; filename=\"certificate-of-acceptance.pdf\""));
  }

  @Test
  @Order(12)
  void get_detail_returns404_forDifferentTenant() throws Exception {
    // Provision a second, completely separate tenant
    String otherOrgId = "org_acceptance_other_tenant";
    provisioningService.provisionTenant(otherOrgId, "Other Tenant Org");
    planSyncService.syncPlan(otherOrgId, "pro-plan");

    syncMember(otherOrgId, "user_other_owner", "other_owner@test.com", "Other Owner", "owner");

    // Authenticate as the other tenant's owner and try to access the first tenant's request
    JwtRequestPostProcessor otherTenantJwt =
        jwt()
            .jwt(
                j ->
                    j.subject("user_other_owner")
                        .claim("o", Map.of("id", otherOrgId, "rol", "owner")))
            .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_OWNER")));

    mockMvc
        .perform(get("/api/acceptance-requests/" + createdRequestId).with(otherTenantJwt))
        .andExpect(status().isNotFound());
  }

  // --- Helper: sync member ---
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
                        {"clerkOrgId":"%s","clerkUserId":"%s","email":"%s","name":"%s","avatarUrl":null,"orgRole":"%s"}
                        """
                            .formatted(orgId, clerkUserId, email, name, orgRole)))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.memberId");
  }

  // --- JWT helpers ---
  private JwtRequestPostProcessor ownerJwt() {
    return jwt()
        .jwt(j -> j.subject("user_acc_ctrl_owner").claim("o", Map.of("id", ORG_ID, "rol", "owner")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_OWNER")));
  }

  private JwtRequestPostProcessor adminJwt() {
    return jwt()
        .jwt(j -> j.subject("user_acc_ctrl_admin").claim("o", Map.of("id", ORG_ID, "rol", "admin")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_ADMIN")));
  }

  private JwtRequestPostProcessor memberJwt() {
    return jwt()
        .jwt(
            j ->
                j.subject("user_acc_ctrl_member").claim("o", Map.of("id", ORG_ID, "rol", "member")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_MEMBER")));
  }
}
