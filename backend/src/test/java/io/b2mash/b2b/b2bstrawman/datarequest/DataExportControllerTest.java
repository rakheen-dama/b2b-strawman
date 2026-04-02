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
import io.b2mash.b2b.b2bstrawman.member.MemberRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
class DataExportControllerTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_data_export_ctrl_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private MemberRepository memberRepository;

  @MockitoBean private StorageService storageService;

  private String customerId;

  @BeforeAll
  void setup() throws Exception {
    setupMocks();

    provisioningService.provisionTenant(ORG_ID, "Data Export Controller Test Org", null);
    syncMember(ORG_ID, "user_dex_owner", "dex_owner@test.com", "DEX Owner", "owner");
    syncMember(ORG_ID, "user_dex_member", "dex_member@test.com", "DEX Member", "member");

    var result =
        mockMvc
            .perform(
                post("/api/customers")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name":"Export Ctrl Customer","email":"dex-ctrl@test.com","phone":"+1-555-9000","idNumber":"DEX-C01","notes":"Test"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    customerId = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
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
  void postExport_returns202WithExportId() throws Exception {
    mockMvc
        .perform(post("/api/customers/" + customerId + "/data-export").with(ownerJwt()))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.exportId").exists())
        .andExpect(jsonPath("$.status").exists());
  }

  @Test
  void getExportStatus_returns200() throws Exception {
    // Trigger an export first
    var postResult =
        mockMvc
            .perform(post("/api/customers/" + customerId + "/data-export").with(ownerJwt()))
            .andExpect(status().isAccepted())
            .andReturn();
    String exportId = JsonPath.read(postResult.getResponse().getContentAsString(), "$.exportId");

    // Set up listKeys to return a key matching this customer's compliance export (includes
    // exportId)
    String fakeS3Key =
        "org/tenant_placeholder/exports/compliance-"
            + customerId
            + "-"
            + exportId
            + "-1234567890.zip";
    Mockito.when(storageService.listKeys(Mockito.any())).thenReturn(List.of(fakeS3Key));

    mockMvc
        .perform(get("/api/data-exports/" + exportId).with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.exportId").value(exportId))
        .andExpect(jsonPath("$.status").value("COMPLETED"));
  }

  @Test
  void listExports_returns200() throws Exception {
    mockMvc
        .perform(get("/api/data-exports").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray());
  }

  @Test
  void postExport_memberRole_returns403() throws Exception {
    mockMvc
        .perform(post("/api/customers/" + customerId + "/data-export").with(memberJwt()))
        .andExpect(status().isForbidden());
  }

  @Test
  void postExport_nonExistentCustomer_returns404() throws Exception {
    mockMvc
        .perform(post("/api/customers/" + UUID.randomUUID() + "/data-export").with(ownerJwt()))
        .andExpect(status().isNotFound());
  }

  @Test
  void listExports_memberRole_returns403() throws Exception {
    // Verifies MANAGE_COMPLIANCE capability enforcement on data export endpoints
    mockMvc.perform(get("/api/data-exports").with(memberJwt())).andExpect(status().isForbidden());
  }

  @Test
  void getExportStatus_memberRole_returns403() throws Exception {
    mockMvc
        .perform(get("/api/data-exports/" + UUID.randomUUID()).with(memberJwt()))
        .andExpect(status().isForbidden());
  }

  private JwtRequestPostProcessor ownerJwt() {
    return jwt()
        .jwt(j -> j.subject("user_dex_owner").claim("o", Map.of("id", ORG_ID, "rol", "owner")));
  }

  private JwtRequestPostProcessor memberJwt() {
    return jwt()
        .jwt(j -> j.subject("user_dex_member").claim("o", Map.of("id", ORG_ID, "rol", "member")));
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
