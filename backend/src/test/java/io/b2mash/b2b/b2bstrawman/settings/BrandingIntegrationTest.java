package io.b2mash.b2b.b2bstrawman.settings;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class BrandingIntegrationTest {
  private static final String ORG_ID = "org_branding_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;

  private static boolean provisioned = false;

  void ensureProvisioned() throws Exception {
    if (!provisioned) {
      provisioningService.provisionTenant(ORG_ID, "Branding Test Org", null);
      TestMemberHelper.syncMember(
          mockMvc, ORG_ID, "user_brand_owner", "brand_owner@test.com", "Brand Owner", "owner");
      TestMemberHelper.syncMember(
          mockMvc, ORG_ID, "user_brand_member", "brand_member@test.com", "Brand Member", "member");
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
        .perform(
            multipart("/api/settings/logo")
                .file(logoFile)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_brand_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.logoUrl").isNotEmpty());
  }

  @Test
  @Order(2)
  void getSettingsIncludesBranding() throws Exception {
    ensureProvisioned();
    mockMvc
        .perform(get("/api/settings").with(TestJwtFactory.ownerJwt(ORG_ID, "user_brand_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.defaultCurrency").isNotEmpty())
        .andExpect(jsonPath("$.logoUrl").isNotEmpty());
  }

  @Test
  @Order(3)
  void deleteLogo() throws Exception {
    ensureProvisioned();
    mockMvc
        .perform(
            delete("/api/settings/logo").with(TestJwtFactory.ownerJwt(ORG_ID, "user_brand_owner")))
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
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_brand_owner"))
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
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_brand_owner"))
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
        .perform(
            multipart("/api/settings/logo")
                .file(logoFile)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_brand_owner")))
        .andExpect(status().isBadRequest());
  }

  @Test
  void memberForbidden() throws Exception {
    ensureProvisioned();
    var logoFile =
        new MockMultipartFile(
            "file", "logo.png", "image/png", new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47});

    mockMvc
        .perform(
            multipart("/api/settings/logo")
                .file(logoFile)
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_brand_member")))
        .andExpect(status().isForbidden());
  }
}
