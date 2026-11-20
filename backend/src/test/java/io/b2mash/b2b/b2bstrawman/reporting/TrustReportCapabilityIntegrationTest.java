package io.b2mash.b2b.b2bstrawman.reporting;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
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
import io.b2mash.b2b.b2bstrawman.orgrole.dto.OrgRoleDtos.CreateOrgRoleRequest;
import io.b2mash.b2b.b2bstrawman.orgrole.dto.OrgRoleDtos.OrgRoleResponse;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Phase 92 R-2 / FU-2: reports that expose trust figures require VIEW_TRUST in addition to
 * FINANCIAL_VISIBILITY.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TrustReportCapabilityIntegrationTest {

  private static final String ORG_ID = "org_trust_rpt_cap";
  private static final String FIN_ONLY = "user_trc_fin_only";
  private static final String FIN_AND_TRUST = "user_trc_fin_trust";
  private static final String AS_OF_BODY =
      "{\"parameters\":{\"asOfDate\":\"2026-01-31\"},\"page\":0,\"size\":50}";
  private static final String SLUGS = "$.categories[*].reports[*].slug";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private OrgRoleService orgRoleService;
  @Autowired private OrgRoleRepository orgRoleRepository;
  @Autowired private MemberRepository memberRepository;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Trust Report Capability Org", "legal-za");
    var ownerId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, "user_trc_owner", "trc_owner@test.com", "Owner", "owner"));
    var finOnlyId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, FIN_ONLY, "trc_fin@test.com", "Fin Only", "member"));
    var finTrustId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, FIN_AND_TRUST, "trc_both@test.com", "Fin Trust", "member"));
    var schema = orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
    ScopedValue.where(RequestScopes.TENANT_ID, schema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, ownerId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () -> {
              assign(
                  finOnlyId,
                  orgRoleService.createRole(
                      new CreateOrgRoleRequest(
                          "Fin Only", "financials", Set.of("FINANCIAL_VISIBILITY"))));
              assign(
                  finTrustId,
                  orgRoleService.createRole(
                      new CreateOrgRoleRequest(
                          "Fin Trust",
                          "financials + trust",
                          Set.of("FINANCIAL_VISIBILITY", "VIEW_TRUST"))));
            });
  }

  private void assign(UUID memberId, OrgRoleResponse role) {
    var member = memberRepository.findById(memberId).orElseThrow();
    member.setOrgRoleEntity(orgRoleRepository.findById(role.id()).orElseThrow());
    memberRepository.save(member);
  }

  @Test
  void executeClientLedgerStatement_withoutViewTrust_is403() throws Exception {
    mockMvc
        .perform(
            post("/api/report-definitions/client-ledger-statement/execute")
                .with(TestJwtFactory.customRoleJwt(ORG_ID, FIN_ONLY))
                .contentType(MediaType.APPLICATION_JSON)
                .content(AS_OF_BODY))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.status").value(403))
        .andExpect(jsonPath("$.title").value("Insufficient permissions"))
        .andExpect(jsonPath("$.detail").value(containsString("VIEW_TRUST")));
  }

  @Test
  void exportsPreviewAndDefinition_withoutViewTrust_are403() throws Exception {
    var jwt = TestJwtFactory.customRoleJwt(ORG_ID, FIN_ONLY);
    mockMvc
        .perform(
            get("/api/report-definitions/trust-position/export/pdf")
                .param("asOfDate", "2026-01-31")
                .with(jwt))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(
            get("/api/report-definitions/trust-position/export/csv")
                .param("asOfDate", "2026-01-31")
                .with(jwt))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(
            get("/api/report-definitions/trust-position/preview")
                .param("asOfDate", "2026-01-31")
                .with(jwt))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(get("/api/report-definitions/section-35-data-pack").with(jwt))
        .andExpect(status().isForbidden());
  }

  @Test
  void list_withoutViewTrust_omitsTrustReports_butKeepsOthers() throws Exception {
    mockMvc
        .perform(
            get("/api/report-definitions").with(TestJwtFactory.customRoleJwt(ORG_ID, FIN_ONLY)))
        .andExpect(status().isOk())
        .andExpect(jsonPath(SLUGS, not(hasItem("client-ledger-statement"))))
        .andExpect(jsonPath(SLUGS, not(hasItem("trust-position"))))
        .andExpect(jsonPath(SLUGS, hasItem("invoice-aging")));
    mockMvc
        .perform(
            get("/api/report-definitions")
                .with(TestJwtFactory.customRoleJwt(ORG_ID, FIN_AND_TRUST)))
        .andExpect(status().isOk())
        .andExpect(jsonPath(SLUGS, hasItem("trust-position")));
  }

  @Test
  void nonTrustReport_stillWorksWithoutViewTrust() throws Exception {
    mockMvc
        .perform(
            post("/api/report-definitions/invoice-aging/execute")
                .with(TestJwtFactory.customRoleJwt(ORG_ID, FIN_ONLY))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"parameters\":{},\"page\":0,\"size\":50}"))
        .andExpect(status().isOk());
  }

  @Test
  void trustReport_withViewTrust_is200() throws Exception {
    mockMvc
        .perform(
            post("/api/report-definitions/trust-position/execute")
                .with(TestJwtFactory.customRoleJwt(ORG_ID, FIN_AND_TRUST))
                .contentType(MediaType.APPLICATION_JSON)
                .content(AS_OF_BODY))
        .andExpect(status().isOk());
  }
}
