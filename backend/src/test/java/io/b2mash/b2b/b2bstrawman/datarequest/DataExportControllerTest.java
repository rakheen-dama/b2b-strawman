package io.b2mash.b2b.b2bstrawman.datarequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.audit.AuditEventRepository;
import io.b2mash.b2b.b2bstrawman.integration.storage.PresignedUrl;
import io.b2mash.b2b.b2bstrawman.integration.storage.StorageService;
import io.b2mash.b2b.b2bstrawman.member.MemberRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestEntityHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
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
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DataExportControllerTest {
  private static final String ORG_ID = "org_data_export_ctrl_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private MemberRepository memberRepository;
  @Autowired private AuditEventRepository auditEventRepository;
  @Autowired private TransactionTemplate transactionTemplate;

  @MockitoBean private StorageService storageService;

  private String customerId;
  private String tenantSchema;

  @BeforeAll
  void setup() throws Exception {
    setupMocks();

    provisioningService.provisionTenant(ORG_ID, "Data Export Controller Test Org", null);
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
    TestMemberHelper.syncMember(
        mockMvc, ORG_ID, "user_dex_owner", "dex_owner@test.com", "DEX Owner", "owner");
    TestMemberHelper.syncMember(
        mockMvc, ORG_ID, "user_dex_member", "dex_member@test.com", "DEX Member", "member");

    var result =
        mockMvc
            .perform(
                post("/api/clients")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_dex_owner"))
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
  void postExport_returns200WithTheCompletedExportStatus() throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/clients/" + customerId + "/data-export")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_dex_owner")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.exportId").isNotEmpty())
            .andExpect(jsonPath("$.status").value("COMPLETED"))
            .andExpect(jsonPath("$.downloadUrl").value("https://example.com/download"))
            .andExpect(jsonPath("$.expiresAt").isNotEmpty())
            .andExpect(jsonPath("$.fileCount").value(greaterThan(0)))
            .andExpect(jsonPath("$.totalSizeBytes").isNumber())
            .andExpect(
                jsonPath("$.s3Key").value(containsString("/exports/compliance-" + customerId)))
            .andExpect(jsonPath("$.estimatedFiles").doesNotExist())
            .andReturn();
    Map<String, Object> body = JsonPath.read(result.getResponse().getContentAsString(), "$");
    assertThat(body.keySet())
        .containsExactlyInAnyOrder(
            "exportId",
            "status",
            "downloadUrl",
            "expiresAt",
            "fileCount",
            "totalSizeBytes",
            "s3Key");
    assertThat(((Number) body.get("totalSizeBytes")).longValue()).isPositive();
  }

  @Test
  void postExport_storageUploadFails_returns500ProblemDetail_andWritesNoAuditRow()
      throws Exception {
    String failingClientId =
        TestEntityHelper.createCustomer(
            mockMvc,
            TestJwtFactory.ownerJwt(ORG_ID, "user_dex_owner"),
            "Export Upload Failure Client",
            "dex-fail@test.com");
    // RuntimeException -> GlobalExceptionHandler catch-all -> 500. IllegalArgumentException would
    // map to 400 (handleIllegalArgument), so it is deliberately not used here.
    Mockito.when(storageService.upload(Mockito.any(), Mockito.any(byte[].class), Mockito.any()))
        .thenThrow(new RuntimeException("simulated S3 outage"));

    mockMvc
        .perform(
            post("/api/clients/" + failingClientId + "/data-export")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_dex_owner")))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.status").value(500))
        .andExpect(jsonPath("$.title").value("Internal server error"))
        .andExpect(jsonPath("$.detail").value("An unexpected error occurred"));

    Mockito.verify(storageService, Mockito.never())
        .generateDownloadUrl(Mockito.any(), Mockito.any());
    // Id-scoped to the client created in this test (the shared client is exported by siblings).
    long auditRows =
        ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
            .call(
                () ->
                    transactionTemplate.execute(
                        tx ->
                            auditEventRepository
                                .findByFilter(
                                    "customer",
                                    UUID.fromString(failingClientId),
                                    null,
                                    "data.subject.export.generated",
                                    null,
                                    null,
                                    Pageable.ofSize(10))
                                .getTotalElements()));
    assertThat(auditRows).isZero();
  }

  @Test
  void getExportStatus_returns200() throws Exception {
    // Trigger an export first
    var postResult =
        mockMvc
            .perform(
                post("/api/clients/" + customerId + "/data-export")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_dex_owner")))
            .andExpect(status().isOk())
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
        .perform(
            get("/api/data-exports/" + exportId)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_dex_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.exportId").value(exportId))
        .andExpect(jsonPath("$.status").value("COMPLETED"));
  }

  @Test
  void listExports_returns200() throws Exception {
    mockMvc
        .perform(get("/api/data-exports").with(TestJwtFactory.ownerJwt(ORG_ID, "user_dex_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray());
  }

  @Test
  void postExport_memberRole_returns403() throws Exception {
    mockMvc
        .perform(
            post("/api/clients/" + customerId + "/data-export")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_dex_member")))
        .andExpect(status().isForbidden());
  }

  @Test
  void postExport_nonExistentCustomer_returns404() throws Exception {
    mockMvc
        .perform(
            post("/api/clients/" + UUID.randomUUID() + "/data-export")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_dex_owner")))
        .andExpect(status().isNotFound());
  }

  @Test
  void listExports_memberRole_returns403() throws Exception {
    // Verifies MANAGE_COMPLIANCE capability enforcement on data export endpoints
    mockMvc
        .perform(get("/api/data-exports").with(TestJwtFactory.memberJwt(ORG_ID, "user_dex_member")))
        .andExpect(status().isForbidden());
  }

  @Test
  void getExportStatus_memberRole_returns403() throws Exception {
    mockMvc
        .perform(
            get("/api/data-exports/" + UUID.randomUUID())
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_dex_member")))
        .andExpect(status().isForbidden());
  }
}
