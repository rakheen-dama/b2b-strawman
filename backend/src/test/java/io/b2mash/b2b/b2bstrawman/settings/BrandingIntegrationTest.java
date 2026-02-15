package io.b2mash.b2b.b2bstrawman.settings;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
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
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Testcontainers
class BrandingIntegrationTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_branding_test";
  private static final String TEST_BUCKET = "test-bucket";

  @Container
  static LocalStackContainer localstack =
      new LocalStackContainer(DockerImageName.parse("localstack/localstack:latest"))
          .withServices(LocalStackContainer.Service.S3);

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

  private static boolean provisioned = false;

  @BeforeAll
  static void createBucket() {
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
  }

  void ensureProvisioned() throws Exception {
    if (!provisioned) {
      provisioningService.provisionTenant(ORG_ID, "Branding Test Org");
      planSyncService.syncPlan(ORG_ID, "pro-plan");
      syncMember(ORG_ID, "user_brand_owner", "brand_owner@test.com", "Brand Owner", "owner");
      syncMember(ORG_ID, "user_brand_member", "brand_member@test.com", "Brand Member", "member");
      provisioned = true;
    }
  }

  @Test
  @Order(1)
  void uploadLogo() throws Exception {
    ensureProvisioned();
    var logoFile =
        new MockMultipartFile(
            "file", "logo.png", "image/png", new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47});

    mockMvc
        .perform(multipart("/api/settings/logo").file(logoFile).with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.logoUrl").isNotEmpty());
  }

  @Test
  @Order(2)
  void getSettingsIncludesBranding() throws Exception {
    ensureProvisioned();
    mockMvc
        .perform(get("/api/settings").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.defaultCurrency").isNotEmpty())
        .andExpect(jsonPath("$.logoUrl").isNotEmpty());
  }

  @Test
  @Order(3)
  void deleteLogo() throws Exception {
    ensureProvisioned();
    mockMvc
        .perform(delete("/api/settings/logo").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.logoUrl").doesNotExist());
  }

  @Test
  @Order(4)
  void updateBrandColor() throws Exception {
    ensureProvisioned();
    mockMvc
        .perform(
            put("/api/settings")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"defaultCurrency": "USD", "brandColor": "#FF5733", "documentFooterText": "All rights reserved"}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.brandColor").value("#FF5733"))
        .andExpect(jsonPath("$.documentFooterText").value("All rights reserved"));
  }

  @Test
  @Order(5)
  void invalidHexColor() throws Exception {
    ensureProvisioned();
    mockMvc
        .perform(
            put("/api/settings")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"defaultCurrency": "USD", "brandColor": "notahex"}
                    """))
        .andExpect(status().isBadRequest());
  }

  @Test
  void fileTooLarge() throws Exception {
    ensureProvisioned();
    byte[] largeFile = new byte[3 * 1024 * 1024]; // 3MB
    var logoFile = new MockMultipartFile("file", "logo.png", "image/png", largeFile);

    mockMvc
        .perform(multipart("/api/settings/logo").file(logoFile).with(ownerJwt()))
        .andExpect(status().isBadRequest());
  }

  @Test
  void memberForbidden() throws Exception {
    ensureProvisioned();
    var logoFile =
        new MockMultipartFile(
            "file", "logo.png", "image/png", new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47});

    mockMvc
        .perform(multipart("/api/settings/logo").file(logoFile).with(memberJwt()))
        .andExpect(status().isForbidden());
  }

  // --- Helpers ---

  private String syncMember(
      String orgId, String clerkUserId, String email, String name, String orgRole)
      throws Exception {
    var result =
        mockMvc
            .perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                        "/internal/members/sync")
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

  private JwtRequestPostProcessor ownerJwt() {
    return jwt()
        .jwt(j -> j.subject("user_brand_owner").claim("o", Map.of("id", ORG_ID, "rol", "owner")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_OWNER")));
  }

  private JwtRequestPostProcessor memberJwt() {
    return jwt()
        .jwt(j -> j.subject("user_brand_member").claim("o", Map.of("id", ORG_ID, "rol", "member")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_MEMBER")));
  }
}
