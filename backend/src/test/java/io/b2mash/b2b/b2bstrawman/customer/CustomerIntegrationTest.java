package io.b2mash.b2b.b2bstrawman.customer;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.member.MemberRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.orgrole.OrgRoleRepository;
import io.b2mash.b2b.b2bstrawman.orgrole.OrgRoleService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.ProblemDetailAssertions;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CustomerIntegrationTest {
  private static final String ORG_ID = "org_customer_test";
  private static final String ORG_B_ID = "org_customer_test_b";
  private static final String UUID_PATTERN =
      "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private OrgRoleService orgRoleService;
  @Autowired private MemberRepository memberRepository;
  @Autowired private OrgRoleRepository orgRoleRepository;

  private String tenantSchema;
  private UUID memberIdOwner;
  private UUID customRoleMemberId;
  private UUID noCapMemberId;

  @BeforeAll
  void provisionTenants() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Customer Test Org", null);
    provisioningService.provisionTenant(ORG_B_ID, "Customer Test Org B", null);

    memberIdOwner =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, "user_cust_owner", "cust_owner@test.com", "Owner", "owner"));
    TestMemberHelper.syncMember(
        mockMvc, ORG_ID, "user_cust_admin", "cust_admin@test.com", "Admin", "admin");
    TestMemberHelper.syncMember(
        mockMvc, ORG_ID, "user_cust_member", "cust_member@test.com", "Member", "member");
    TestMemberHelper.syncMember(
        mockMvc, ORG_B_ID, "user_cust_tenant_b", "cust_tenantb@test.com", "Tenant B User", "owner");

    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

    customRoleMemberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                ORG_ID,
                "user_cust_315a_custom",
                "cust_custom@test.com",
                "Cust Custom User",
                "member"));
    noCapMemberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                ORG_ID,
                "user_cust_315a_nocap",
                "cust_nocap@test.com",
                "Cust NoCap User",
                "member"));

    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberIdOwner)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () -> {
              var withCapRole =
                  orgRoleService.createRole(
                      new io.b2mash.b2b.b2bstrawman.orgrole.dto.OrgRoleDtos.CreateOrgRoleRequest(
                          "Customer Manager",
                          "Can manage customers",
                          Set.of("CUSTOMER_MANAGEMENT")));
              var customMember = memberRepository.findById(customRoleMemberId).orElseThrow();
              customMember.setOrgRoleEntity(
                  orgRoleRepository.findById(withCapRole.id()).orElseThrow());
              memberRepository.save(customMember);

              var withoutCapRole =
                  orgRoleService.createRole(
                      new io.b2mash.b2b.b2bstrawman.orgrole.dto.OrgRoleDtos.CreateOrgRoleRequest(
                          "Team Lead Cust", "Can manage teams", Set.of("TEAM_OVERSIGHT")));
              var noCapMember = memberRepository.findById(noCapMemberId).orElseThrow();
              noCapMember.setOrgRoleEntity(
                  orgRoleRepository.findById(withoutCapRole.id()).orElseThrow());
              memberRepository.save(noCapMember);
            });
  }

  // --- CRUD happy path ---

  @Test
  void shouldCreateAndGetCustomer() throws Exception {
    var createResult =
        mockMvc
            .perform(
                post("/api/customers")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_cust_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "name": "Acme Corp",
                          "email": "contact@acme.com",
                          "phone": "+1-555-0100",
                          "idNumber": "ACM-001",
                          "notes": "Important client"
                        }
                        """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("Acme Corp"))
            .andExpect(jsonPath("$.email").value("contact@acme.com"))
            .andExpect(jsonPath("$.phone").value("+1-555-0100"))
            .andExpect(jsonPath("$.idNumber").value("ACM-001"))
            .andExpect(jsonPath("$.status").value("ACTIVE"))
            .andExpect(jsonPath("$.notes").value("Important client"))
            .andExpect(jsonPath("$.createdBy").value(matchesPattern(UUID_PATTERN)))
            .andExpect(jsonPath("$.id").exists())
            .andExpect(jsonPath("$.createdAt").exists())
            .andExpect(jsonPath("$.updatedAt").exists())
            .andReturn();

    var id = TestEntityHelper.extractIdFromLocation(createResult);

    mockMvc
        .perform(
            get("/api/customers/" + id).with(TestJwtFactory.ownerJwt(ORG_ID, "user_cust_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Acme Corp"))
        .andExpect(jsonPath("$.email").value("contact@acme.com"));
  }

  @Test
  void shouldCreateCustomerWithMinimalFields() throws Exception {
    mockMvc
        .perform(
            post("/api/customers")
                .with(TestJwtFactory.adminJwt(ORG_ID, "user_cust_admin"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name": "Minimal Customer", "email": "minimal@test.com"}
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.name").value("Minimal Customer"))
        .andExpect(jsonPath("$.phone").isEmpty())
        .andExpect(jsonPath("$.idNumber").isEmpty())
        .andExpect(jsonPath("$.notes").isEmpty());
  }

  @Test
  void shouldRejectDuplicateEmail() throws Exception {
    mockMvc
        .perform(
            post("/api/customers")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_cust_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name": "First", "email": "duplicate@test.com"}
                    """))
        .andExpect(status().isCreated());

    var result =
        mockMvc.perform(
            post("/api/customers")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_cust_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name": "Second", "email": "duplicate@test.com"}
                    """));
    ProblemDetailAssertions.assertProblemWithDetail(
        result, HttpStatus.CONFLICT, "Customer email conflict", "duplicate@test.com");
  }

  @Test
  void shouldListCustomers() throws Exception {
    mockMvc
        .perform(
            post("/api/customers")
                .with(TestJwtFactory.adminJwt(ORG_ID, "user_cust_admin"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name": "List Test Customer", "email": "listtest@test.com"}
                    """))
        .andExpect(status().isCreated());

    mockMvc
        .perform(get("/api/customers").with(TestJwtFactory.memberJwt(ORG_ID, "user_cust_member")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$.length()").value(greaterThanOrEqualTo(1)));
  }

  @Test
  void shouldUpdateCustomer() throws Exception {
    var createResult =
        mockMvc
            .perform(
                post("/api/customers")
                    .with(TestJwtFactory.adminJwt(ORG_ID, "user_cust_admin"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "Before Update", "email": "beforeupdate@test.com"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    var id = TestEntityHelper.extractIdFromLocation(createResult);

    mockMvc
        .perform(
            put("/api/customers/" + id)
                .with(TestJwtFactory.adminJwt(ORG_ID, "user_cust_admin"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name": "After Update",
                      "email": "afterupdate@test.com",
                      "phone": "+1-555-9999",
                      "idNumber": "UPD-001",
                      "notes": "Updated notes"
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("After Update"))
        .andExpect(jsonPath("$.email").value("afterupdate@test.com"))
        .andExpect(jsonPath("$.phone").value("+1-555-9999"))
        .andExpect(jsonPath("$.idNumber").value("UPD-001"))
        .andExpect(jsonPath("$.notes").value("Updated notes"));
  }

  @Test
  void shouldArchiveCustomer() throws Exception {
    var createResult =
        mockMvc
            .perform(
                post("/api/customers")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_cust_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "To Archive", "email": "toarchive@test.com"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    var id = TestEntityHelper.extractIdFromLocation(createResult);

    mockMvc
        .perform(
            delete("/api/customers/" + id).with(TestJwtFactory.ownerJwt(ORG_ID, "user_cust_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ARCHIVED"));

    // Archived customer should still be retrievable
    mockMvc
        .perform(
            get("/api/customers/" + id).with(TestJwtFactory.ownerJwt(ORG_ID, "user_cust_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ARCHIVED"));
  }

  // --- Validation errors ---

  @Test
  void shouldReject400WhenNameIsMissing() throws Exception {
    var result =
        mockMvc.perform(
            post("/api/customers")
                .with(TestJwtFactory.adminJwt(ORG_ID, "user_cust_admin"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"email": "valid@test.com"}
                    """));
    ProblemDetailAssertions.assertValidationErrors(result, "name");
  }

  @Test
  void shouldReject400WhenEmailIsMissing() throws Exception {
    var result =
        mockMvc.perform(
            post("/api/customers")
                .with(TestJwtFactory.adminJwt(ORG_ID, "user_cust_admin"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name": "No Email"}
                    """));
    ProblemDetailAssertions.assertValidationErrors(result, "email");
  }

  @Test
  void shouldReject400WhenEmailIsInvalid() throws Exception {
    mockMvc
        .perform(
            post("/api/customers")
                .with(TestJwtFactory.adminJwt(ORG_ID, "user_cust_admin"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name": "Bad Email", "email": "not-an-email"}
                    """))
        .andExpect(status().isBadRequest());
  }

  // --- Not found ---

  @Test
  void shouldReturn404ForNonexistentCustomer() throws Exception {
    var result =
        mockMvc.perform(
            get("/api/customers/00000000-0000-0000-0000-000000000000")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_cust_owner")));
    ProblemDetailAssertions.assertProblem(result, HttpStatus.NOT_FOUND, "Customer not found");
  }

  // --- Capability Tests (added in Epic 315A) ---

  @Test
  void customRoleWithCapability_accessesCustomerEndpoint_returns201() throws Exception {
    mockMvc
        .perform(
            post("/api/customers")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_cust_315a_custom"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name": "Capability Test Customer", "email": "cap315a@test.com"}
                    """))
        .andExpect(status().isCreated());
  }

  @Test
  void customRoleWithoutCapability_accessesCustomerEndpoint_returns403() throws Exception {
    mockMvc
        .perform(
            post("/api/customers")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_cust_315a_nocap"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name": "NoCap Customer", "email": "nocap315a@test.com"}
                    """))
        .andExpect(status().isForbidden());
  }

  // --- RBAC ---

  @Test
  void memberCannotCreateCustomer() throws Exception {
    mockMvc
        .perform(
            post("/api/customers")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_cust_member"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name": "RBAC Test", "email": "rbac_create@test.com"}
                    """))
        .andExpect(status().isForbidden());
  }

  @Test
  void memberCanListAndGetCustomers() throws Exception {
    // Member can list
    mockMvc
        .perform(get("/api/customers").with(TestJwtFactory.memberJwt(ORG_ID, "user_cust_member")))
        .andExpect(status().isOk());
  }

  // --- Tenant isolation ---

  @Test
  void customersAreIsolatedBetweenTenants() throws Exception {
    var createResult =
        mockMvc
            .perform(
                post("/api/customers")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_cust_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "Tenant A Customer", "email": "isolation_a@test.com"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    var customerId = TestEntityHelper.extractIdFromLocation(createResult);

    // Visible from tenant A
    mockMvc
        .perform(
            get("/api/customers/" + customerId)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_cust_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Tenant A Customer"));

    // NOT visible from tenant B
    mockMvc
        .perform(
            get("/api/customers/" + customerId)
                .with(TestJwtFactory.ownerJwt(ORG_B_ID, "user_cust_tenant_b")))
        .andExpect(status().isNotFound());

    // Tenant B list doesn't include tenant A's customer
    mockMvc
        .perform(
            get("/api/customers").with(TestJwtFactory.ownerJwt(ORG_B_ID, "user_cust_tenant_b")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[*].name", everyItem(not("Tenant A Customer"))));
  }

  // --- Helpers ---

}
