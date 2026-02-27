package io.b2mash.b2b.b2bstrawman.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
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
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PortalAcceptanceControllerIntegrationTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_portal_accept_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private StorageService storageService;
  @Autowired private AcceptanceExpiryProcessor acceptanceExpiryProcessor;
  @Autowired private AcceptanceRequestRepository acceptanceRequestRepository;
  @Autowired private TransactionTemplate transactionTemplate;

  private String schema;
  private String ownerMemberId;
  private final UUID customerId = UUID.randomUUID();
  private final UUID portalContactId = UUID.randomUUID();
  private final UUID templateId = UUID.randomUUID();
  private final UUID generatedDocumentId = UUID.randomUUID();

  private String requestToken;
  private String expiredRequestToken;
  private String revokedRequestToken;
  private String acceptedRequestToken;

  @BeforeAll
  void provisionAndSeed() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Portal Accept Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");

    ownerMemberId =
        syncMember(
            ORG_ID, "user_portal_acc_owner", "portal_acc_owner@test.com", "Portal Owner", "owner");

    schema = orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

    // Seed customer
    jdbcTemplate.update(
        """
        INSERT INTO "%s".customers (id, name, email, created_by, lifecycle_status, created_at, updated_at)
        VALUES (?::uuid, ?, ?, ?::uuid, 'ACTIVE', NOW(), NOW())
        """
            .formatted(schema),
        customerId.toString(),
        "Portal Test Customer",
        "portal-customer@test.com",
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
        "portal-contact@test.com",
        "Portal Contact");

    // Seed document template
    jdbcTemplate.update(
        """
        INSERT INTO "%s".document_templates (id, name, slug, category, primary_entity_type, content, source, active, created_at, updated_at)
        VALUES (?::uuid, ?, ?, 'ENGAGEMENT_LETTER', 'CUSTOMER', '<p>Test</p>', 'ORG_CUSTOM', true, NOW(), NOW())
        """
            .formatted(schema),
        templateId.toString(),
        "Portal Test Template",
        "portal-test-template");

    // Upload test PDF to S3
    String s3Key = "test/portal-acceptance-doc.pdf";
    byte[] pdfBytes = "%PDF-1.4 test portal acceptance document content".getBytes();
    storageService.upload(s3Key, pdfBytes, "application/pdf");

    // Seed generated document with the uploaded PDF
    jdbcTemplate.update(
        """
        INSERT INTO "%s".generated_documents (id, template_id, primary_entity_type, primary_entity_id, file_name, s3_key, file_size, generated_by, generated_at)
        VALUES (?::uuid, ?::uuid, 'CUSTOMER', ?::uuid, ?, ?, 1024, ?::uuid, NOW())
        """
            .formatted(schema),
        generatedDocumentId.toString(),
        templateId.toString(),
        customerId.toString(),
        "engagement-letter.pdf",
        s3Key,
        ownerMemberId);

    // Create a SENT acceptance request via the firm API
    var result =
        mockMvc
            .perform(
                post("/api/acceptance-requests")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"generatedDocumentId":"%s","portalContactId":"%s","expiryDays":30}
                        """
                            .formatted(generatedDocumentId, portalContactId)))
            .andExpect(status().isCreated())
            .andReturn();

    String requestId = JsonPath.read(result.getResponse().getContentAsString(), "$.id");

    // Get the token from the database
    requestToken =
        jdbcTemplate.queryForObject(
            """
            SELECT request_token FROM "%s".acceptance_requests WHERE id = ?::uuid
            """
                .formatted(schema),
            String.class,
            requestId);

    // The portal read-model is auto-synced by the event handler when the acceptance request is
    // created via the firm API, so no manual insert needed for this request.

    // Create an EXPIRED acceptance request
    UUID expiredRequestId = UUID.randomUUID();
    expiredRequestToken = "expired-token-" + UUID.randomUUID();
    jdbcTemplate.update(
        """
        INSERT INTO "%s".acceptance_requests
          (id, generated_document_id, portal_contact_id, customer_id, status,
           request_token, sent_at, expires_at,
           sent_by_member_id, reminder_count, created_at, updated_at)
        VALUES (?::uuid, ?::uuid, ?::uuid, ?::uuid, 'EXPIRED',
                ?, NOW(), NOW() - INTERVAL '1 day',
                ?::uuid, 0, NOW(), NOW())
        """
            .formatted(schema),
        expiredRequestId.toString(),
        generatedDocumentId.toString(),
        portalContactId.toString(),
        customerId.toString(),
        expiredRequestToken,
        ownerMemberId);

    // Seed portal read-model for expired request
    jdbcTemplate.update(
        """
        INSERT INTO portal.portal_acceptance_requests
          (id, portal_contact_id, generated_document_id, document_title,
           document_file_name, status, request_token, sent_at, expires_at,
           org_name, org_logo)
        VALUES (?::uuid, ?::uuid, ?::uuid, ?, ?, 'EXPIRED', ?, NOW(), NOW() - INTERVAL '1 day',
                'Portal Accept Test Org', null)
        """,
        expiredRequestId.toString(),
        portalContactId.toString(),
        generatedDocumentId.toString(),
        "engagement-letter.pdf",
        "engagement-letter.pdf",
        expiredRequestToken);

    // Create a REVOKED acceptance request
    UUID revokedRequestId = UUID.randomUUID();
    revokedRequestToken = "revoked-token-" + UUID.randomUUID();
    jdbcTemplate.update(
        """
        INSERT INTO "%s".acceptance_requests
          (id, generated_document_id, portal_contact_id, customer_id, status,
           request_token, sent_at, expires_at, revoked_by_member_id,
           sent_by_member_id, reminder_count, created_at, updated_at)
        VALUES (?::uuid, ?::uuid, ?::uuid, ?::uuid, 'REVOKED',
                ?, NOW(), NOW() + INTERVAL '30 days', ?::uuid,
                ?::uuid, 0, NOW(), NOW())
        """
            .formatted(schema),
        revokedRequestId.toString(),
        generatedDocumentId.toString(),
        portalContactId.toString(),
        customerId.toString(),
        revokedRequestToken,
        ownerMemberId,
        ownerMemberId);

    // Seed portal read-model for revoked request
    jdbcTemplate.update(
        """
        INSERT INTO portal.portal_acceptance_requests
          (id, portal_contact_id, generated_document_id, document_title,
           document_file_name, status, request_token, sent_at, expires_at,
           org_name, org_logo)
        VALUES (?::uuid, ?::uuid, ?::uuid, ?, ?, 'REVOKED', ?, NOW(), NOW() + INTERVAL '30 days',
                'Portal Accept Test Org', null)
        """,
        revokedRequestId.toString(),
        portalContactId.toString(),
        generatedDocumentId.toString(),
        "engagement-letter.pdf",
        "engagement-letter.pdf",
        revokedRequestToken);

    // Create an ACCEPTED acceptance request for idempotency test
    UUID acceptedRequestId = UUID.randomUUID();
    acceptedRequestToken = "accepted-token-" + UUID.randomUUID();
    jdbcTemplate.update(
        """
        INSERT INTO "%s".acceptance_requests
          (id, generated_document_id, portal_contact_id, customer_id, status,
           request_token, sent_at, accepted_at, expires_at, acceptor_name,
           acceptor_ip_address, acceptor_user_agent,
           sent_by_member_id, reminder_count, created_at, updated_at)
        VALUES (?::uuid, ?::uuid, ?::uuid, ?::uuid, 'ACCEPTED',
                ?, NOW(), NOW(), NOW() + INTERVAL '30 days', 'Already Accepted',
                '127.0.0.1', 'Test Agent',
                ?::uuid, 0, NOW(), NOW())
        """
            .formatted(schema),
        acceptedRequestId.toString(),
        generatedDocumentId.toString(),
        portalContactId.toString(),
        customerId.toString(),
        acceptedRequestToken,
        ownerMemberId);

    // Seed portal read-model for accepted request
    jdbcTemplate.update(
        """
        INSERT INTO portal.portal_acceptance_requests
          (id, portal_contact_id, generated_document_id, document_title,
           document_file_name, status, request_token, sent_at, expires_at,
           org_name, org_logo)
        VALUES (?::uuid, ?::uuid, ?::uuid, ?, ?, 'ACCEPTED', ?, NOW(), NOW() + INTERVAL '30 days',
                'Portal Accept Test Org', null)
        """,
        acceptedRequestId.toString(),
        portalContactId.toString(),
        generatedDocumentId.toString(),
        "engagement-letter.pdf",
        "engagement-letter.pdf",
        acceptedRequestToken);
  }

  // --- Portal controller tests (no JWT required) ---

  @Test
  @Order(1)
  void get_byToken_returnsPageData() throws Exception {
    mockMvc
        .perform(get("/api/portal/acceptance/" + requestToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.requestId").exists())
        .andExpect(jsonPath("$.documentTitle").value("engagement-letter.pdf"))
        .andExpect(jsonPath("$.documentFileName").value("engagement-letter.pdf"))
        .andExpect(jsonPath("$.orgName").value("Portal Accept Test Org"))
        .andExpect(jsonPath("$.expiresAt").exists());
  }

  @Test
  @Order(2)
  void get_byToken_marksViewed() throws Exception {
    // First call already happened in test 1, check the status is VIEWED now
    mockMvc
        .perform(get("/api/portal/acceptance/" + requestToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("VIEWED"));
  }

  @Test
  @Order(3)
  void get_byToken_returnsExpiredStatusForExpiredRequest() throws Exception {
    mockMvc
        .perform(get("/api/portal/acceptance/" + expiredRequestToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("EXPIRED"));
  }

  @Test
  @Order(4)
  void get_byToken_returns404ForInvalidToken() throws Exception {
    mockMvc
        .perform(get("/api/portal/acceptance/invalid-token-that-does-not-exist"))
        .andExpect(status().isNotFound());
  }

  @Test
  @Order(5)
  void get_pdf_streamsDocument() throws Exception {
    mockMvc
        .perform(get("/api/portal/acceptance/" + requestToken + "/pdf"))
        .andExpect(status().isOk())
        .andExpect(header().string("Content-Type", "application/pdf"))
        .andExpect(
            result -> {
              String contentDisposition = result.getResponse().getHeader("Content-Disposition");
              assertThat(contentDisposition).startsWith("inline;");
              assertThat(contentDisposition).contains("engagement-letter.pdf");
            });
  }

  @Test
  @Order(6)
  void post_accept_recordsAcceptanceWithIpAndUa() throws Exception {
    mockMvc
        .perform(
            post("/api/portal/acceptance/" + requestToken + "/accept")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Forwarded-For", "203.0.113.50, 70.41.3.18")
                .header("User-Agent", "TestBrowser/1.0")
                .content(
                    """
                    {"name": "Jane Smith"}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ACCEPTED"))
        .andExpect(jsonPath("$.acceptedAt").exists())
        .andExpect(jsonPath("$.acceptorName").value("Jane Smith"));

    // Verify IP and user agent were recorded in the database
    var row =
        jdbcTemplate.queryForMap(
            """
            SELECT acceptor_ip_address, acceptor_user_agent FROM "%s".acceptance_requests
            WHERE request_token = ?
            """
                .formatted(schema),
            requestToken);
    assertThat(row.get("acceptor_ip_address")).isEqualTo("203.0.113.50");
    assertThat(row.get("acceptor_user_agent")).isEqualTo("TestBrowser/1.0");
  }

  @Test
  @Order(7)
  void post_accept_rejectsExpired() throws Exception {
    // Expired requests should return an error (400 Bad Request per InvalidStateException)
    mockMvc
        .perform(
            post("/api/portal/acceptance/" + expiredRequestToken + "/accept")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name": "Jane Smith"}
                    """))
        .andExpect(status().isBadRequest());
  }

  @Test
  @Order(8)
  void post_accept_rejectsRevoked() throws Exception {
    // Revoked requests should return an error (400 Bad Request per InvalidStateException)
    mockMvc
        .perform(
            post("/api/portal/acceptance/" + revokedRequestToken + "/accept")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name": "Jane Smith"}
                    """))
        .andExpect(status().isBadRequest());
  }

  @Test
  @Order(9)
  void post_accept_idempotentForAlreadyAccepted() throws Exception {
    // The request was accepted in test 6, so accepting again should still return ACCEPTED
    mockMvc
        .perform(
            post("/api/portal/acceptance/" + requestToken + "/accept")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name": "Jane Smith"}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ACCEPTED"));
  }

  // --- Expiry processor tests ---

  @Test
  @Order(10)
  void processExpired_transitionsExpiredRequests() throws Exception {
    // Create a new request that should expire
    UUID expiringRequestId = UUID.randomUUID();
    String expiringToken = "expiring-token-" + UUID.randomUUID();
    jdbcTemplate.update(
        """
        INSERT INTO "%s".acceptance_requests
          (id, generated_document_id, portal_contact_id, customer_id, status,
           request_token, sent_at, expires_at,
           sent_by_member_id, reminder_count, created_at, updated_at)
        VALUES (?::uuid, ?::uuid, ?::uuid, ?::uuid, 'SENT',
                ?, NOW(), ?::timestamptz,
                ?::uuid, 0, NOW(), NOW())
        """
            .formatted(schema),
        expiringRequestId.toString(),
        generatedDocumentId.toString(),
        portalContactId.toString(),
        customerId.toString(),
        expiringToken,
        Instant.now().minus(1, ChronoUnit.HOURS).toString(),
        ownerMemberId);

    // Run the expiry processor
    acceptanceExpiryProcessor.processExpired();

    // Verify it was transitioned to EXPIRED
    String newStatus =
        jdbcTemplate.queryForObject(
            """
            SELECT status FROM "%s".acceptance_requests WHERE id = ?::uuid
            """
                .formatted(schema),
            String.class,
            expiringRequestId.toString());
    assertThat(newStatus).isEqualTo("EXPIRED");
  }

  @Test
  @Order(11)
  void processExpired_ignoresTerminalStatuses() throws Exception {
    // The ACCEPTED and REVOKED requests should not be changed
    UUID terminalRequestId = UUID.randomUUID();
    String terminalToken = "terminal-token-" + UUID.randomUUID();
    jdbcTemplate.update(
        """
        INSERT INTO "%s".acceptance_requests
          (id, generated_document_id, portal_contact_id, customer_id, status,
           request_token, sent_at, accepted_at, expires_at, acceptor_name,
           sent_by_member_id, reminder_count, created_at, updated_at)
        VALUES (?::uuid, ?::uuid, ?::uuid, ?::uuid, 'ACCEPTED',
                ?, NOW(), NOW(), ?::timestamptz, 'Already Done',
                ?::uuid, 0, NOW(), NOW())
        """
            .formatted(schema),
        terminalRequestId.toString(),
        generatedDocumentId.toString(),
        portalContactId.toString(),
        customerId.toString(),
        terminalToken,
        Instant.now().minus(1, ChronoUnit.HOURS).toString(),
        ownerMemberId);

    // Run the expiry processor
    acceptanceExpiryProcessor.processExpired();

    // Verify ACCEPTED was not changed
    String status =
        jdbcTemplate.queryForObject(
            """
            SELECT status FROM "%s".acceptance_requests WHERE id = ?::uuid
            """
                .formatted(schema),
            String.class,
            terminalRequestId.toString());
    assertThat(status).isEqualTo("ACCEPTED");
  }

  @Test
  @Order(12)
  void portalEndpoints_bypassJwtAuth() throws Exception {
    // Verify that portal acceptance endpoints work without any auth headers
    // (no JWT, no API key, nothing)
    mockMvc.perform(get("/api/portal/acceptance/" + requestToken)).andExpect(status().isOk());
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
        .jwt(
            j ->
                j.subject("user_portal_acc_owner").claim("o", Map.of("id", ORG_ID, "rol", "owner")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_OWNER")));
  }
}
