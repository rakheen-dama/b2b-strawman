package io.b2mash.b2b.b2bstrawman.portal;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
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
import java.util.ArrayList;
import java.util.List;
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

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PortalContextControllerIntegrationTest {

  private static final String LEGAL_ORG_ID = "org_portal_ctx_legal";
  private static final String LEGAL_ORG_NAME = "Test Law Firm";
  private static final String NO_PROFILE_ORG_ID = "org_portal_ctx_no_profile";
  private static final String NO_PROFILE_ORG_NAME = "Test No-Profile Firm";

  @Autowired private MockMvc mockMvc;
  @Autowired private PortalJwtService portalJwtService;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private CustomerService customerService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private OrgSettingsRepository orgSettingsRepository;
  @MockitoBean private StorageService storageService;

  private UUID legalCustomerId;
  private UUID noProfileCustomerId;

  @BeforeAll
  void setup() throws Exception {
    when(storageService.generateDownloadUrl(anyString(), any()))
        .thenReturn(
            new PresignedUrl("https://s3.example.com/logo.png", Instant.now().plusSeconds(3600)));

    // --- legal-za tenant ---
    provisioningService.provisionTenant(LEGAL_ORG_ID, LEGAL_ORG_NAME, "legal-za");
    UUID legalMemberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                LEGAL_ORG_ID,
                "user_ctx_legal_owner",
                "legal_owner@test.com",
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
                      "Legal Client", "legal-client@test.com", null, null, null, legalMemberId);
              legalCustomerId = customer.getId();
              // GAP-L-34: the PortalContactAutoProvisioner listener already created a GENERAL
              // portal contact for this customer's email when CustomerCreatedEvent fired. A
              // manual PRIMARY create with the same email would 409, and this test only asserts
              // session-context resolution (role-agnostic), so we rely on the auto-provisioned
              // contact.
              // legal-za.json seeds a subset of modules; tests assert Phase 68 module IDs that
              // live outside the seed (document_acceptance). Set them
              // explicitly on OrgSettings.
              var settings = orgSettingsRepository.findForCurrentTenant().orElseThrow();
              List<String> modules = new ArrayList<>(settings.getEnabledModules());
              // trust_accounting is already seeded by legal-za.json; no guard needed.
              if (!modules.contains("document_acceptance")) {
                modules.add("document_acceptance");
              }
              settings.setEnabledModules(modules);
              orgSettingsRepository.save(settings);
            });

    // --- no-profile tenant (legal-za is the only registered profile since Phase 86 / Epic 604;
    // the control tenant has no profile and no modules, so trust must not leak into it) ---
    provisioningService.provisionTenant(NO_PROFILE_ORG_ID, NO_PROFILE_ORG_NAME, null);
    UUID noProfileMemberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                NO_PROFILE_ORG_ID,
                "user_ctx_noprof_owner",
                "noprof_owner@test.com",
                "NoProfile Owner",
                "owner"));
    String noProfileSchema =
        orgSchemaMappingRepository.findByClerkOrgId(NO_PROFILE_ORG_ID).get().getSchemaName();

    ScopedValue.where(RequestScopes.TENANT_ID, noProfileSchema)
        .where(RequestScopes.ORG_ID, NO_PROFILE_ORG_ID)
        .run(
            () -> {
              var customer =
                  customerService.createCustomer(
                      "NoProfile Client",
                      "noprof-client@test.com",
                      null,
                      null,
                      null,
                      noProfileMemberId);
              noProfileCustomerId = customer.getId();
              // GAP-L-34: auto-provisioner already created the portal contact for this email.
              // Pin enabledModules to empty so the assertions below are deterministic.
              var settings = orgSettingsRepository.findForCurrentTenant().orElseThrow();
              settings.setEnabledModules(List.of());
              orgSettingsRepository.save(settings);
            });
  }

  @Test
  void legalZaContact_returnsLegalProfileWithModules() throws Exception {
    String token = portalJwtService.issueToken(legalCustomerId, LEGAL_ORG_ID);
    mockMvc
        .perform(get("/portal/session/context").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.tenantProfile").value("legal-za"))
        .andExpect(jsonPath("$.terminologyKey").value("en-ZA-legal"))
        .andExpect(jsonPath("$.enabledModules", hasItem("trust_accounting")))
        .andExpect(jsonPath("$.enabledModules", hasItem("document_acceptance")))
        .andExpect(jsonPath("$.orgName").value(LEGAL_ORG_NAME));
  }

  @Test
  void noProfileContact_returnsNoProfile_withoutTrust() throws Exception {
    String token = portalJwtService.issueToken(noProfileCustomerId, NO_PROFILE_ORG_ID);
    mockMvc
        .perform(get("/portal/session/context").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.tenantProfile").doesNotExist())
        .andExpect(jsonPath("$.terminologyKey").value(""))
        .andExpect(jsonPath("$.enabledModules").isEmpty())
        .andExpect(jsonPath("$.enabledModules", not(hasItem("trust_accounting"))))
        .andExpect(jsonPath("$.orgName").value(NO_PROFILE_ORG_NAME));
  }

  @Test
  void unauthenticated_returns401() throws Exception {
    mockMvc.perform(get("/portal/session/context")).andExpect(status().isUnauthorized());
  }
}
