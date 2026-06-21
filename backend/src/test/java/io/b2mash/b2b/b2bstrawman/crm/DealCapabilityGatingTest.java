package io.b2mash.b2b.b2bstrawman.crm;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.member.MemberRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.orgrole.OrgRoleRepository;
import io.b2mash.b2b.b2bstrawman.orgrole.OrgRoleService;
import io.b2mash.b2b.b2bstrawman.orgrole.dto.OrgRoleDtos.CreateOrgRoleRequest;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestEntityHelper;
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

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DealCapabilityGatingTest {

  private static final String ORG_ID = "org_deal_capgate_test";
  private static final String OWNER_SUBJECT = "user_deal_cap_owner";
  private static final String VIEWER_SUBJECT = "user_deal_cap_viewer"; // VIEW_DEALS only
  private static final String NOCAP_SUBJECT = "user_deal_cap_nocap"; // no deal caps

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private OrgRoleService orgRoleService;
  @Autowired private OrgRoleRepository orgRoleRepository;
  @Autowired private MemberRepository memberRepository;

  private String customerId;
  private String dealId;
  private String tenantSchema;

  @BeforeAll
  void provisionTenantAndSeedData() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Deal Cap Gate Test Org", null);
    String ownerMemberId =
        TestMemberHelper.syncMember(
            mockMvc, ORG_ID, OWNER_SUBJECT, "deal_cap_owner@test.com", "Owner", "owner");
    UUID viewerMemberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, VIEWER_SUBJECT, "deal_cap_viewer@test.com", "Viewer", "member"));
    UUID noCapMemberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, NOCAP_SUBJECT, "deal_cap_nocap@test.com", "NoCap", "member"));

    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

    customerId =
        TestEntityHelper.createCustomer(
            mockMvc, TestJwtFactory.ownerJwt(ORG_ID, OWNER_SUBJECT), "CapCo", "capco@test.com");
    dealId = createDealAsOwner();

    UUID ownerUuid = UUID.fromString(ownerMemberId);
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, ownerUuid)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () -> {
              var viewerRole =
                  orgRoleService.createRole(
                      new CreateOrgRoleRequest(
                          "Deal Viewer", "Can view deals", Set.of("VIEW_DEALS")));
              var viewer = memberRepository.findById(viewerMemberId).orElseThrow();
              viewer.setOrgRoleEntity(orgRoleRepository.findById(viewerRole.id()).orElseThrow());
              memberRepository.save(viewer);

              var noCapRole =
                  orgRoleService.createRole(
                      new CreateOrgRoleRequest(
                          "Team Lead", "No deal caps", Set.of("TEAM_OVERSIGHT")));
              var noCap = memberRepository.findById(noCapMemberId).orElseThrow();
              noCap.setOrgRoleEntity(orgRoleRepository.findById(noCapRole.id()).orElseThrow());
              memberRepository.save(noCap);
            });
  }

  private String createDealAsOwner() throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/deals")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER_SUBJECT))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"customerId":"%s","title":"Cap Gate Deal"}
                        """
                            .formatted(customerId)))
            .andExpect(status().isCreated())
            .andReturn();
    return com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.id");
  }

  // --- VIEW_DEALS gating ---

  @Test
  void viewerWithViewDeals_canList() throws Exception {
    mockMvc
        .perform(get("/api/deals").with(TestJwtFactory.memberJwt(ORG_ID, VIEWER_SUBJECT)))
        .andExpect(status().isOk());
  }

  @Test
  void memberWithoutViewDeals_cannotList() throws Exception {
    mockMvc
        .perform(get("/api/deals").with(TestJwtFactory.memberJwt(ORG_ID, NOCAP_SUBJECT)))
        .andExpect(status().isForbidden());
  }

  // --- MANAGE_DEALS gating (viewer has VIEW only, so write paths are forbidden) ---

  @Test
  void viewerWithoutManageDeals_cannotCreate() throws Exception {
    mockMvc
        .perform(
            post("/api/deals")
                .with(TestJwtFactory.memberJwt(ORG_ID, VIEWER_SUBJECT))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"customerId":"%s","title":"Should Be Forbidden"}
                    """
                        .formatted(customerId)))
        .andExpect(status().isForbidden());
  }

  @Test
  void viewerWithoutManageDeals_cannotIntake() throws Exception {
    mockMvc
        .perform(
            post("/api/deals/intake")
                .with(TestJwtFactory.memberJwt(ORG_ID, VIEWER_SUBJECT))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"title":"X","customer":{"name":"Y","email":"y@test.com","phone":null}}
                    """))
        .andExpect(status().isForbidden());
  }

  @Test
  void viewerWithoutManageDeals_cannotUpdate() throws Exception {
    mockMvc
        .perform(
            put("/api/deals/" + dealId)
                .with(TestJwtFactory.memberJwt(ORG_ID, VIEWER_SUBJECT))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"title":"Nope"}
                    """))
        .andExpect(status().isForbidden());
  }

  @Test
  void viewerWithoutManageDeals_cannotDelete() throws Exception {
    mockMvc
        .perform(
            delete("/api/deals/" + dealId).with(TestJwtFactory.memberJwt(ORG_ID, VIEWER_SUBJECT)))
        .andExpect(status().isForbidden());
  }
}
