package io.b2mash.b2b.b2bstrawman.portal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.integration.storage.PresignedUrl;
import io.b2mash.b2b.b2bstrawman.integration.storage.StorageService;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettings;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import java.time.Instant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PortalBrandingControllerIntegrationTest {

  private static final String ORG_ID = "org_portal_branding_test";
  private static final String ORG_WITH_LOGO_ID = "org_portal_branding_logo_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private OrgSettingsRepository orgSettingsRepository;
  @MockitoBean private StorageService storageService;

  @BeforeAll
  void setup() {
    // Org without logo
    provisioningService.provisionTenant(ORG_ID, "Branding Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");

    var schema = orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).get().getSchemaName();
    ScopedValue.where(RequestScopes.TENANT_ID, schema)
        .run(
            () -> {
              var settings =
                  orgSettingsRepository
                      .findForCurrentTenant()
                      .orElseGet(() -> new OrgSettings("ZAR"));
              settings.setBrandColor("#ff5733");
              orgSettingsRepository.save(settings);
            });

    // Org with logo
    provisioningService.provisionTenant(ORG_WITH_LOGO_ID, "Branding Logo Test Org");
    planSyncService.syncPlan(ORG_WITH_LOGO_ID, "pro-plan");

    var logoSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_WITH_LOGO_ID).get().getSchemaName();
    ScopedValue.where(RequestScopes.TENANT_ID, logoSchema)
        .run(
            () -> {
              var settings =
                  orgSettingsRepository
                      .findForCurrentTenant()
                      .orElseGet(() -> new OrgSettings("ZAR"));
              settings.setBrandColor("#0066cc");
              settings.setLogoS3Key("logos/branding-test-org/logo.png");
              orgSettingsRepository.save(settings);
            });

    when(storageService.generateDownloadUrl(any(String.class), any()))
        .thenReturn(
            new PresignedUrl("https://s3.example.com/logo.png", Instant.now().plusSeconds(3600)));
  }

  @Test
  void branding_returns_org_name_and_color() throws Exception {
    mockMvc
        .perform(get("/portal/branding").param("orgId", ORG_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.orgName").value("Branding Test Org"))
        .andExpect(jsonPath("$.brandColor").value("#ff5733"));
  }

  @Test
  void branding_without_logo_returns_null_logoUrl() throws Exception {
    mockMvc
        .perform(get("/portal/branding").param("orgId", ORG_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.logoUrl").doesNotExist());
  }

  @Test
  void branding_with_logo_returns_presigned_url() throws Exception {
    mockMvc
        .perform(get("/portal/branding").param("orgId", ORG_WITH_LOGO_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.orgName").value("Branding Logo Test Org"))
        .andExpect(jsonPath("$.logoUrl").value("https://s3.example.com/logo.png"))
        .andExpect(jsonPath("$.brandColor").value("#0066cc"));
  }

  @Test
  void branding_unknown_org_returns_404() throws Exception {
    mockMvc
        .perform(get("/portal/branding").param("orgId", "org_nonexistent"))
        .andExpect(status().isNotFound());
  }

  @Test
  void branding_has_cache_control_header() throws Exception {
    mockMvc
        .perform(get("/portal/branding").param("orgId", ORG_ID))
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "max-age=3600, public"));
  }

  @Test
  void branding_no_auth_required() throws Exception {
    // No Authorization header — should still succeed
    mockMvc
        .perform(get("/portal/branding").param("orgId", ORG_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.orgName").value("Branding Test Org"));
  }
}
