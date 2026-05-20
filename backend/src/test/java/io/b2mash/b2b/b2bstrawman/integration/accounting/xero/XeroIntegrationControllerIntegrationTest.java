package io.b2mash.b2b.b2bstrawman.integration.accounting.xero;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.member.MemberRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.orgrole.OrgRoleRepository;
import io.b2mash.b2b.b2bstrawman.orgrole.OrgRoleService;
import io.b2mash.b2b.b2bstrawman.orgrole.dto.OrgRoleDtos;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.util.Optional;
import java.util.Set;
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
class XeroIntegrationControllerIntegrationTest {

  private static final String ORG_ID = "org_xero_ctrl_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private OrgRoleService orgRoleService;
  @Autowired private OrgRoleRepository orgRoleRepository;
  @Autowired private MemberRepository memberRepository;

  @MockitoBean private XeroOAuthService xeroOAuthService;
  @MockitoBean private XeroCustomerImportService xeroCustomerImportService;

  private String tenantSchema;
  private UUID ownerMemberId;
  private UUID noCapMemberId;

  @BeforeAll
  void setUp() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Xero Ctrl Test Org", null);

    ownerMemberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                ORG_ID,
                "user_xero_ctrl_owner",
                "xero_ctrl_owner@test.com",
                "Xero Ctrl Owner",
                "owner"));

    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

    // Assign system owner role
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, ownerMemberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () -> {
              var ownerRole =
                  orgRoleRepository.findAll().stream()
                      .filter(r -> r.isSystem() && "owner".equals(r.getSlug()))
                      .findFirst()
                      .orElseThrow();
              var ownerMember = memberRepository.findById(ownerMemberId).orElseThrow();
              ownerMember.setOrgRoleEntity(ownerRole);
              memberRepository.save(ownerMember);
            });

    // Member without INTEGRATION_MANAGE capability
    noCapMemberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                ORG_ID,
                "user_xero_ctrl_nocap",
                "xero_ctrl_nocap@test.com",
                "Xero NoCap",
                "member"));

    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, ownerMemberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () -> {
              var viewerRole =
                  orgRoleService.createRole(
                      new OrgRoleDtos.CreateOrgRoleRequest(
                          "Viewer", "Can only view", Set.of("TEAM_OVERSIGHT")));
              var noCapMember = memberRepository.findById(noCapMemberId).orElseThrow();
              noCapMember.setOrgRoleEntity(
                  orgRoleRepository.findById(viewerRole.id()).orElseThrow());
              memberRepository.save(noCapMember);
            });
  }

  @Test
  void connect_requiresIntegrationManage_returns403ForNoCap() throws Exception {
    mockMvc
        .perform(
            get("/api/integrations/xero/connect")
                .with(TestJwtFactory.jwtAs(ORG_ID, "user_xero_ctrl_nocap", "member")))
        .andExpect(status().isForbidden());
  }

  @Test
  void connect_ownerCanAccess() throws Exception {
    when(xeroOAuthService.initiateConnect(any(UUID.class)))
        .thenReturn(
            new XeroOAuthService.XeroConnectResult("https://xero.com/authorize?...", "test-state"));

    mockMvc
        .perform(
            get("/api/integrations/xero/connect")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_xero_ctrl_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.authorizationUrl").exists())
        .andExpect(jsonPath("$.state").value("test-state"));
  }

  @Test
  void getConnection_returns404WhenNotConnected() throws Exception {
    when(xeroOAuthService.getActiveConnection()).thenReturn(Optional.empty());

    mockMvc
        .perform(
            get("/api/integrations/xero/connection")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_xero_ctrl_owner")))
        .andExpect(status().isNotFound());
  }

  @Test
  void disconnect_requiresIntegrationManage_returns403ForNoCap() throws Exception {
    mockMvc
        .perform(
            delete("/api/integrations/xero/connection")
                .with(TestJwtFactory.jwtAs(ORG_ID, "user_xero_ctrl_nocap", "member")))
        .andExpect(status().isForbidden());
  }

  @Test
  void importCustomers_requiresIntegrationManage_returns403ForNoCap() throws Exception {
    mockMvc
        .perform(
            post("/api/integrations/xero/import-customers")
                .with(TestJwtFactory.jwtAs(ORG_ID, "user_xero_ctrl_nocap", "member")))
        .andExpect(status().isForbidden());
  }

  @Test
  void importCustomers_ownerCanAccess() throws Exception {
    when(xeroCustomerImportService.importCustomersFromConnectedOrg(any(UUID.class)))
        .thenReturn(new CustomerImportSummary(10, 2, 1, 13));

    mockMvc
        .perform(
            post("/api/integrations/xero/import-customers")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_xero_ctrl_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.created").value(10))
        .andExpect(jsonPath("$.skippedDuplicate").value(2))
        .andExpect(jsonPath("$.skippedNoEmail").value(1))
        .andExpect(jsonPath("$.total").value(13));
  }

  @Test
  void getSettings_ownerCanAccess() throws Exception {
    when(xeroOAuthService.getSettings())
        .thenReturn(
            new io.b2mash.b2b.b2bstrawman.integration.accounting.xero.dto.XeroSyncSettings(
                15, "APPROVED", true));

    mockMvc
        .perform(
            get("/api/integrations/xero/settings")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_xero_ctrl_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.paymentPollIntervalMinutes").value(15))
        .andExpect(jsonPath("$.pushTrigger").value("APPROVED"))
        .andExpect(jsonPath("$.autoSyncEnabled").value(true));
  }

  @Test
  void getTaxRates_requiresIntegrationManage_returns403ForNoCap() throws Exception {
    mockMvc
        .perform(
            get("/api/integrations/xero/tax-rates")
                .with(TestJwtFactory.jwtAs(ORG_ID, "user_xero_ctrl_nocap", "member")))
        .andExpect(status().isForbidden());
  }
}
