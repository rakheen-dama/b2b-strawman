package io.b2mash.b2b.b2bstrawman.portal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.CustomerService;
import io.b2mash.b2b.b2bstrawman.integration.storage.PresignedUrl;
import io.b2mash.b2b.b2bstrawman.integration.storage.StorageService;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.time.Instant;
import java.util.UUID;
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

/**
 * Integration tests for {@link PortalTerminologyController} (GAP-L-65, slice 19).
 *
 * <p>Exercises {@code GET /portal/terminology} for two tenants: a legal-za firm (returns the
 * vertical-profile id) and a generic firm with {@code verticalProfile = null} (returns null
 * namespace). Authentication uses real portal JWTs issued via {@link PortalJwtService}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PortalTerminologyControllerIntegrationTest {

  private static final String LEGAL_ORG_ID = "org_portal_term_legal";
  private static final String LEGAL_ORG_NAME = "Test Law Firm";
  private static final String GENERIC_ORG_ID = "org_portal_term_generic";
  private static final String GENERIC_ORG_NAME = "Test Generic Firm";

  @Autowired private MockMvc mockMvc;
  @Autowired private PortalJwtService portalJwtService;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private CustomerService customerService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private OrgSettingsRepository orgSettingsRepository;
  @MockitoBean private StorageService storageService;

  private UUID legalCustomerId;
  private UUID genericCustomerId;

  @BeforeAll
  void setup() throws Exception {
    when(storageService.generateDownloadUrl(anyString(), any()))
        .thenReturn(
            new PresignedUrl("https://s3.example.com/logo.png", Instant.now().plusSeconds(3600)));

    // --- legal-za tenant: provisioned with verticalProfile = "legal-za" ---
    provisioningService.provisionTenant(LEGAL_ORG_ID, LEGAL_ORG_NAME, "legal-za");
    UUID legalMemberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                LEGAL_ORG_ID,
                "user_term_legal_owner",
                "term_legal_owner@test.com",
                "Legal Owner",
                "owner"));
    String legalSchema =
        orgSchemaMappingRepository.findByClerkOrgId(LEGAL_ORG_ID).get().getSchemaName();
    ScopedValue.where(RequestScopes.TENANT_ID, legalSchema)
        .where(RequestScopes.ORG_ID, LEGAL_ORG_ID)
        .run(
            () -> {
              var customer =
                  customerService.createCustomer(
                      "Legal Client",
                      "term-legal-client@test.com",
                      null,
                      null,
                      null,
                      legalMemberId);
              legalCustomerId = customer.getId();
            });

    // --- generic tenant: provisioned without a verticalProfile ---
    provisioningService.provisionTenant(GENERIC_ORG_ID, GENERIC_ORG_NAME, null);
    UUID genericMemberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                GENERIC_ORG_ID,
                "user_term_generic_owner",
                "term_generic_owner@test.com",
                "Generic Owner",
                "owner"));
    String genericSchema =
        orgSchemaMappingRepository.findByClerkOrgId(GENERIC_ORG_ID).get().getSchemaName();
    ScopedValue.where(RequestScopes.TENANT_ID, genericSchema)
        .where(RequestScopes.ORG_ID, GENERIC_ORG_ID)
        .run(
            () -> {
              var customer =
                  customerService.createCustomer(
                      "Generic Client",
                      "term-generic-client@test.com",
                      null,
                      null,
                      null,
                      genericMemberId);
              genericCustomerId = customer.getId();
              // Defensive: ensure verticalProfile is null for this tenant (provisioner left it
              // null because we passed null above, but assert via reset for clarity).
              var settings = orgSettingsRepository.findForCurrentTenant().orElseThrow();
              settings.setVerticalProfile(null);
              orgSettingsRepository.save(settings);
            });
  }

  @Test
  void legalZaContact_returnsLegalNamespace() throws Exception {
    String token = portalJwtService.issueToken(legalCustomerId, LEGAL_ORG_ID);
    mockMvc
        .perform(get("/portal/terminology").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.namespace").value("legal-za"));
  }

  @Test
  void genericContact_returnsNullNamespace() throws Exception {
    String token = portalJwtService.issueToken(genericCustomerId, GENERIC_ORG_ID);
    mockMvc
        .perform(get("/portal/terminology").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.namespace").doesNotExist());
  }

  @Test
  void unauthenticated_returns401() throws Exception {
    mockMvc.perform(get("/portal/terminology")).andExpect(status().isUnauthorized());
  }
}
