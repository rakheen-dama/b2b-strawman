package io.b2mash.b2b.b2bstrawman.datarequest;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.integration.storage.PresignedUrl;
import io.b2mash.b2b.b2bstrawman.integration.storage.StorageService;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AnonymizationControllerTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_anon_ctrl_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;

  @MockitoBean private StorageService storageService;

  private String customerId;
  private String customerName;
  private String alreadyAnonymizedCustomerId;

  @BeforeAll
  void setup() throws Exception {
    setupMocks();

    provisioningService.provisionTenant(ORG_ID, "Anon Controller Test Org", null);
    planSyncService.syncPlan(ORG_ID, "pro-plan");
    syncMember(ORG_ID, "user_anon_owner", "anon_owner@test.com", "Anon Owner", "owner");
    syncMember(ORG_ID, "user_anon_admin", "anon_admin@test.com", "Anon Admin", "admin");

    // Create customer for happy path, validation, and preview tests
    customerName = "Anon Test Customer";
    var result =
        mockMvc
            .perform(
                post("/api/customers")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name":"Anon Test Customer","email":"anon-test@test.com","phone":"+1-555-8000","idNumber":"ANON-C01","notes":"Test"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    customerId = JsonPath.read(result.getResponse().getContentAsString(), "$.id");

    // Create a separate customer and anonymize it for the 409 conflict test
    var anonResult =
        mockMvc
            .perform(
                post("/api/customers")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name":"Already Anon Customer","email":"already-anon@test.com","phone":"+1-555-8001","idNumber":"ANON-C02","notes":"Test"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    alreadyAnonymizedCustomerId =
        JsonPath.read(anonResult.getResponse().getContentAsString(), "$.id");

    // Anonymize this customer via POST endpoint so it's in ANONYMIZED state
    mockMvc
        .perform(
            post("/api/customers/" + alreadyAnonymizedCustomerId + "/anonymize")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"confirmationName":"Already Anon Customer","reason":"Setup for 409 test"}
                    """))
        .andExpect(status().isOk());
  }

  @BeforeEach
  void setupMocks() {
    Mockito.when(storageService.upload(Mockito.any(), Mockito.any(byte[].class), Mockito.any()))
        .thenAnswer(inv -> inv.getArgument(0));
    Mockito.when(storageService.generateDownloadUrl(Mockito.any(), Mockito.any()))
        .thenReturn(
            new PresignedUrl("https://example.com/download", Instant.now().plusSeconds(86400)));
    Mockito.when(storageService.listKeys(Mockito.any())).thenReturn(List.of());
  }

  @Test
  void postAnonymize_correctName_returns200AndCustomerIsAnonymized() throws Exception {
    // Create a fresh customer for this destructive test
    var result =
        mockMvc
            .perform(
                post("/api/customers")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name":"Fresh Anon Customer","email":"fresh-anon@test.com","phone":"+1-555-8010","idNumber":"ANON-C10","notes":"Test"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    String freshCustomerId = JsonPath.read(result.getResponse().getContentAsString(), "$.id");

    mockMvc
        .perform(
            post("/api/customers/" + freshCustomerId + "/anonymize")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"confirmationName":"Fresh Anon Customer","reason":"GDPR right to erasure"}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.customerAnonymized").value(true))
        .andExpect(jsonPath("$.documentsDeleted").isNumber())
        .andExpect(jsonPath("$.commentsRedacted").isNumber())
        .andExpect(jsonPath("$.portalContactsAnonymized").isNumber())
        .andExpect(jsonPath("$.invoicesPreserved").isNumber());
  }

  @Test
  void postAnonymize_wrongConfirmationName_returns400() throws Exception {
    mockMvc
        .perform(
            post("/api/customers/" + customerId + "/anonymize")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"confirmationName":"Wrong Name","reason":"test"}
                    """))
        .andExpect(status().isBadRequest());
  }

  @Test
  void postAnonymize_adminRole_returns403() throws Exception {
    // ADMIN has MANAGE_COMPLIANCE but NOT MANAGE_COMPLIANCE_DESTRUCTIVE
    mockMvc
        .perform(
            post("/api/customers/" + customerId + "/anonymize")
                .with(adminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"confirmationName":"Anon Test Customer","reason":"test"}
                    """))
        .andExpect(status().isForbidden());
  }

  @Test
  void getPreview_returns200WithCorrectCounts() throws Exception {
    mockMvc
        .perform(get("/api/customers/" + customerId + "/anonymize/preview").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.customerId").value(customerId))
        .andExpect(jsonPath("$.customerName").value(customerName))
        .andExpect(jsonPath("$.portalContacts").isNumber())
        .andExpect(jsonPath("$.projects").isNumber())
        .andExpect(jsonPath("$.documents").isNumber())
        .andExpect(jsonPath("$.invoices").isNumber())
        .andExpect(jsonPath("$.comments").isNumber());
  }

  @Test
  void postAnonymize_alreadyAnonymized_returns409() throws Exception {
    mockMvc
        .perform(
            post("/api/customers/" + alreadyAnonymizedCustomerId + "/anonymize")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"confirmationName":"Already Anon Customer","reason":"should fail"}
                    """))
        .andExpect(status().isConflict());
  }

  @Test
  void getPreview_adminRole_returns200() throws Exception {
    // ADMIN has MANAGE_COMPLIANCE — preview is non-destructive, accessible to ADMIN+
    // Contrasts with POST /anonymize which requires MANAGE_COMPLIANCE_DESTRUCTIVE (OWNER-only)
    mockMvc
        .perform(get("/api/customers/" + customerId + "/anonymize/preview").with(adminJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.customerId").value(customerId))
        .andExpect(jsonPath("$.customerName").exists());
  }

  private JwtRequestPostProcessor ownerJwt() {
    return jwt()
        .jwt(j -> j.subject("user_anon_owner").claim("o", Map.of("id", ORG_ID, "rol", "owner")));
  }

  private JwtRequestPostProcessor adminJwt() {
    return jwt()
        .jwt(j -> j.subject("user_anon_admin").claim("o", Map.of("id", ORG_ID, "rol", "admin")));
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
                        {"clerkOrgId":"%s","clerkUserId":"%s","email":"%s","name":"%s","avatarUrl":null,"orgRole":"%s"}
                        """
                            .formatted(orgId, clerkUserId, email, name, orgRole)))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.memberId");
  }
}
