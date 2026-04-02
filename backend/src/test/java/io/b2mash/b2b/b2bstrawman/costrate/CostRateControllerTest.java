package io.b2mash.b2b.b2bstrawman.costrate;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.member.MemberRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.orgrole.OrgRoleRepository;
import io.b2mash.b2b.b2bstrawman.orgrole.OrgRoleService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CostRateControllerTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_cost_ctrl_test";
  private static final String ORG_ID_B = "org_cost_ctrl_test_b";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private OrgRoleService orgRoleService;
  @Autowired private OrgRoleRepository orgRoleRepository;
  @Autowired private MemberRepository memberRepository;

  private String memberIdOwner;
  private String memberIdAdmin;
  private String memberIdMember;

  // Tenant B for isolation tests
  private String memberIdOwnerB;

  // Stored rate ID for subsequent tests
  private String createdCostRateId;

  // Custom role members for capability tests
  private UUID customRoleMemberId;
  private UUID noCapMemberId;

  @BeforeAll
  void provisionAndSeed() throws Exception {
    // Provision tenant A
    provisioningService.provisionTenant(ORG_ID, "Cost Ctrl Test Org", null);

    memberIdOwner =
        syncMember(ORG_ID, "user_crc_owner", "crc_owner@test.com", "CRC Owner", "owner");
    memberIdAdmin =
        syncMember(ORG_ID, "user_crc_admin", "crc_admin@test.com", "CRC Admin", "admin");
    memberIdMember =
        syncMember(ORG_ID, "user_crc_member", "crc_member@test.com", "CRC Member", "member");

    // Provision tenant B for isolation tests
    provisioningService.provisionTenant(ORG_ID_B, "Cost Ctrl Test Org B", null);
    memberIdOwnerB =
        syncMember(ORG_ID_B, "user_crc_owner_b", "crc_owner_b@test.com", "CRC Owner B", "owner");

    var tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

    // Assign system owner/admin roles for capability-based auth
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, UUID.fromString(memberIdOwner))
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () -> {
              var ownerRole =
                  orgRoleRepository.findAll().stream()
                      .filter(r -> r.isSystem() && "owner".equals(r.getSlug()))
                      .findFirst()
                      .orElseThrow();
              var ownerMember =
                  memberRepository.findById(UUID.fromString(memberIdOwner)).orElseThrow();
              ownerMember.setOrgRoleEntity(ownerRole);
              memberRepository.save(ownerMember);

              var adminRole =
                  orgRoleRepository.findAll().stream()
                      .filter(r -> r.isSystem() && "admin".equals(r.getSlug()))
                      .findFirst()
                      .orElseThrow();
              var adminMember =
                  memberRepository.findById(UUID.fromString(memberIdAdmin)).orElseThrow();
              adminMember.setOrgRoleEntity(adminRole);
              memberRepository.save(adminMember);
            });

    // Assign system owner role in tenant B
    var tenantSchemaB =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID_B).orElseThrow().getSchemaName();
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchemaB)
        .where(RequestScopes.ORG_ID, ORG_ID_B)
        .where(RequestScopes.MEMBER_ID, UUID.fromString(memberIdOwnerB))
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () -> {
              var ownerRole =
                  orgRoleRepository.findAll().stream()
                      .filter(r -> r.isSystem() && "owner".equals(r.getSlug()))
                      .findFirst()
                      .orElseThrow();
              var ownerMember =
                  memberRepository.findById(UUID.fromString(memberIdOwnerB)).orElseThrow();
              ownerMember.setOrgRoleEntity(ownerRole);
              memberRepository.save(ownerMember);
            });

    // Sync custom-role members for capability tests
    customRoleMemberId =
        UUID.fromString(
            syncMember(
                ORG_ID,
                "user_crc_314a_custom",
                "crc_custom@test.com",
                "CRC Custom User",
                "member"));
    noCapMemberId =
        UUID.fromString(
            syncMember(
                ORG_ID, "user_crc_314a_nocap", "crc_nocap@test.com", "CRC NoCap User", "member"));

    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, UUID.fromString(memberIdOwner))
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () -> {
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
            });
  }

  // --- CRUD Tests ---

  @Test
  @Order(1)
  void postCreatesCostRateAndReturns201() throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/cost-rates")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "memberId": "%s",
                          "currency": "USD",
                          "hourlyCost": 75.00,
                          "effectiveFrom": "2025-01-01"
                        }
                        """
                            .formatted(memberIdMember)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").exists())
            .andExpect(jsonPath("$.memberId").value(memberIdMember))
            .andExpect(jsonPath("$.memberName").value("CRC Member"))
            .andExpect(jsonPath("$.currency").value("USD"))
            .andExpect(jsonPath("$.hourlyCost").value(75.00))
            .andExpect(jsonPath("$.effectiveFrom").value("2025-01-01"))
            .andExpect(jsonPath("$.effectiveTo").isEmpty())
            .andReturn();

    createdCostRateId = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
  }

  @Test
  @Order(2)
  void getListsCostRates() throws Exception {
    mockMvc
        .perform(get("/api/cost-rates").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isArray())
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].memberName").value("CRC Member"));
  }

  @Test
  @Order(3)
  void getListsFiltersByMemberId() throws Exception {
    // Create a cost rate for the admin member
    mockMvc
        .perform(
            post("/api/cost-rates")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "memberId": "%s",
                      "currency": "EUR",
                      "hourlyCost": 90.00,
                      "effectiveFrom": "2025-01-01"
                    }
                    """
                        .formatted(memberIdAdmin)))
        .andExpect(status().isCreated());

    // Filter by the original member — should only return 1
    mockMvc
        .perform(get("/api/cost-rates").param("memberId", memberIdMember).with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].memberId").value(memberIdMember));
  }

  @Test
  @Order(4)
  void putUpdatesCostRate() throws Exception {
    mockMvc
        .perform(
            put("/api/cost-rates/" + createdCostRateId)
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "currency": "GBP",
                      "hourlyCost": 85.00,
                      "effectiveFrom": "2025-01-01",
                      "effectiveTo": "2025-12-31"
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.currency").value("GBP"))
        .andExpect(jsonPath("$.hourlyCost").value(85.00))
        .andExpect(jsonPath("$.effectiveTo").value("2025-12-31"));
  }

  @Test
  @Order(5)
  void deleteRemovesCostRate() throws Exception {
    // Create a rate to delete
    var result =
        mockMvc
            .perform(
                post("/api/cost-rates")
                    .with(adminJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "memberId": "%s",
                          "currency": "CAD",
                          "hourlyCost": 60.00,
                          "effectiveFrom": "2026-01-01"
                        }
                        """
                            .formatted(memberIdOwner)))
            .andExpect(status().isCreated())
            .andReturn();

    var rateId = JsonPath.read(result.getResponse().getContentAsString(), "$.id").toString();

    mockMvc
        .perform(delete("/api/cost-rates/" + rateId).with(adminJwt()))
        .andExpect(status().isNoContent());
  }

  // --- Permission Tests ---

  @Test
  @Order(6)
  void memberCannotCreateCostRate() throws Exception {
    mockMvc
        .perform(
            post("/api/cost-rates")
                .with(memberJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "memberId": "%s",
                      "currency": "USD",
                      "hourlyCost": 50.00,
                      "effectiveFrom": "2025-01-01"
                    }
                    """
                        .formatted(memberIdMember)))
        .andExpect(status().isForbidden());
  }

  @Test
  @Order(7)
  void memberCannotListCostRates() throws Exception {
    mockMvc.perform(get("/api/cost-rates").with(memberJwt())).andExpect(status().isForbidden());
  }

  @Test
  @Order(8)
  void memberCannotUpdateCostRate() throws Exception {
    mockMvc
        .perform(
            put("/api/cost-rates/" + createdCostRateId)
                .with(memberJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "currency": "CAD",
                      "hourlyCost": 99.00,
                      "effectiveFrom": "2025-01-01"
                    }
                    """))
        .andExpect(status().isForbidden());
  }

  @Test
  @Order(9)
  void memberCannotDeleteCostRate() throws Exception {
    mockMvc
        .perform(delete("/api/cost-rates/" + createdCostRateId).with(memberJwt()))
        .andExpect(status().isForbidden());
  }

  // --- Overlap Test ---

  @Test
  @Order(10)
  void postOverlappingCostRateReturns409() throws Exception {
    mockMvc
        .perform(
            post("/api/cost-rates")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "memberId": "%s",
                      "currency": "USD",
                      "hourlyCost": 80.00,
                      "effectiveFrom": "2025-06-01"
                    }
                    """
                        .formatted(memberIdMember)))
        .andExpect(status().isConflict());
  }

  // --- Tenant Isolation Tests ---

  @Test
  @Order(11)
  void costRateInTenantAIsInvisibleInTenantB() throws Exception {
    mockMvc
        .perform(get("/api/cost-rates").with(ownerJwtTenantB()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(0));
  }

  @Test
  @Order(12)
  void customRoleWithCapability_accessesCostRateEndpoint_returns200() throws Exception {
    mockMvc.perform(get("/api/cost-rates").with(customRoleJwt())).andExpect(status().isOk());
  }

  @Test
  @Order(13)
  void customRoleWithoutCapability_accessesCostRateEndpoint_returns403() throws Exception {
    mockMvc
        .perform(get("/api/cost-rates").with(noCapabilityJwt()))
        .andExpect(status().isForbidden());
  }

  // --- Helpers ---

  private String syncMember(
      String orgId, String clerkUserId, String email, String name, String orgRole)
      throws Exception {
    var result =
        mockMvc
            .perform(
                post("/internal/members/sync")
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
        .jwt(j -> j.subject("user_crc_owner").claim("o", Map.of("id", ORG_ID, "rol", "owner")));
  }

  private JwtRequestPostProcessor adminJwt() {
    return jwt()
        .jwt(j -> j.subject("user_crc_admin").claim("o", Map.of("id", ORG_ID, "rol", "admin")));
  }

  private JwtRequestPostProcessor memberJwt() {
    return jwt()
        .jwt(j -> j.subject("user_crc_member").claim("o", Map.of("id", ORG_ID, "rol", "member")));
  }

  private JwtRequestPostProcessor ownerJwtTenantB() {
    return jwt()
        .jwt(j -> j.subject("user_crc_owner_b").claim("o", Map.of("id", ORG_ID_B, "rol", "owner")));
  }

  private JwtRequestPostProcessor customRoleJwt() {
    return jwt()
        .jwt(
            j ->
                j.subject("user_crc_314a_custom")
                    .claim("o", Map.of("id", ORG_ID, "rol", "member")));
  }

  private JwtRequestPostProcessor noCapabilityJwt() {
    return jwt()
        .jwt(
            j ->
                j.subject("user_crc_314a_nocap").claim("o", Map.of("id", ORG_ID, "rol", "member")));
  }
}
