package io.b2mash.b2b.b2bstrawman.report;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.TestPostgresConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.member.MemberRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.orgrole.OrgRoleRepository;
import io.b2mash.b2b.b2bstrawman.orgrole.OrgRoleService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
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
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestPostgresConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ReportCapabilityTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_report_cap_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private OrgRoleService orgRoleService;
  @Autowired private OrgRoleRepository orgRoleRepository;
  @Autowired private MemberRepository memberRepository;
  @Autowired private CustomerRepository customerRepository;

  private UUID ownerMemberId;
  private UUID customRoleMemberId;
  private UUID noCapMemberId;
  private UUID testCustomerId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Report Cap Test Org", null);

    ownerMemberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                ORG_ID,
                "user_rpt_cap_owner",
                "rpt_cap_owner@test.com",
                "RPT Owner",
                "owner"));
    customRoleMemberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                ORG_ID,
                "user_rpt_314a_custom",
                "rpt_custom@test.com",
                "RPT Custom User",
                "member"));
    noCapMemberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                ORG_ID,
                "user_rpt_314a_nocap",
                "rpt_nocap@test.com",
                "RPT NoCap User",
                "member"));

    var tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, ownerMemberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () -> {
              // Assign system owner role
              var ownerRole =
                  orgRoleRepository.findAll().stream()
                      .filter(r -> r.isSystem() && "owner".equals(r.getSlug()))
                      .findFirst()
                      .orElseThrow();
              var ownerMember = memberRepository.findById(ownerMemberId).orElseThrow();
              ownerMember.setOrgRoleEntity(ownerRole);
              memberRepository.save(ownerMember);

              var withCapRole =
                  orgRoleService.createRole(
                      new io.b2mash.b2b.b2bstrawman.orgrole.dto.OrgRoleDtos.CreateOrgRoleRequest(
                          "Financials Viewer",
                          "Can view financials",
                          Set.of("FINANCIAL_VISIBILITY")));
              var customMember = memberRepository.findById(customRoleMemberId).orElseThrow();
              customMember.setOrgRoleEntity(
                  orgRoleRepository.findById(withCapRole.id()).orElseThrow());
              memberRepository.save(customMember);

              var withoutCapRole =
                  orgRoleService.createRole(
                      new io.b2mash.b2b.b2bstrawman.orgrole.dto.OrgRoleDtos.CreateOrgRoleRequest(
                          "Team Lead", "Can manage teams", Set.of("TEAM_OVERSIGHT")));
              var noCapMember = memberRepository.findById(noCapMemberId).orElseThrow();
              noCapMember.setOrgRoleEntity(
                  orgRoleRepository.findById(withoutCapRole.id()).orElseThrow());
              memberRepository.save(noCapMember);

              // Create a customer for customer profitability tests
              var customer =
                  TestCustomerFactory.createActiveCustomer(
                      "Report Cap Test Customer", "rptcap@test.com", ownerMemberId);
              testCustomerId = customerRepository.save(customer).getId();
            });
  }

  @Test
  void customRoleWithCapability_accessesOrgProfitability_returns200() throws Exception {
    mockMvc
        .perform(
            get("/api/reports/profitability")
                .param("from", "2026-01-01")
                .param("to", "2026-12-31")
                .with(TestJwtFactory.jwtAs(ORG_ID, "user_rpt_314a_custom", "member")))
        .andExpect(status().isOk());
  }

  @Test
  void customRoleWithoutCapability_accessesOrgProfitability_returns403() throws Exception {
    mockMvc
        .perform(
            get("/api/reports/profitability")
                .param("from", "2026-01-01")
                .param("to", "2026-12-31")
                .with(TestJwtFactory.jwtAs(ORG_ID, "user_rpt_314a_nocap", "member")))
        .andExpect(status().isForbidden());
  }

  @Test
  void customRoleWithCapability_accessesCustomerProfitability_returns200() throws Exception {
    mockMvc
        .perform(
            get("/api/customers/{customerId}/profitability", testCustomerId)
                .param("from", "2026-01-01")
                .param("to", "2026-12-31")
                .with(TestJwtFactory.jwtAs(ORG_ID, "user_rpt_314a_custom", "member")))
        .andExpect(status().isOk());
  }

  @Test
  void customRoleWithoutCapability_accessesCustomerProfitability_returns403() throws Exception {
    mockMvc
        .perform(
            get("/api/customers/{customerId}/profitability", testCustomerId)
                .param("from", "2026-01-01")
                .param("to", "2026-12-31")
                .with(TestJwtFactory.jwtAs(ORG_ID, "user_rpt_314a_nocap", "member")))
        .andExpect(status().isForbidden());
  }
}
