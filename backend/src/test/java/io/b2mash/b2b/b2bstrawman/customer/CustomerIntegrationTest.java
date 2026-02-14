package io.b2mash.b2b.b2bstrawman.customer;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CustomerIntegrationTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_customer_test";
  private static final String ORG_B_ID = "org_customer_test_b";
  private static final String UUID_PATTERN =
      "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;

  @BeforeAll
  void provisionTenants() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Customer Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");
    provisioningService.provisionTenant(ORG_B_ID, "Customer Test Org B");
    planSyncService.syncPlan(ORG_B_ID, "pro-plan");

    syncMember(ORG_ID, "user_cust_owner", "cust_owner@test.com", "Owner", "owner");
    syncMember(ORG_ID, "user_cust_admin", "cust_admin@test.com", "Admin", "admin");
    syncMember(ORG_ID, "user_cust_member", "cust_member@test.com", "Member", "member");
    syncMember(ORG_B_ID, "user_cust_tenant_b", "cust_tenantb@test.com", "Tenant B User", "owner");
  }

  // --- CRUD happy path ---

  @Test
  void shouldCreateAndGetCustomer() throws Exception {
    var createResult =
        mockMvc
            .perform(
                post("/api/customers")
                    .with(ownerJwt())
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

    var id = extractIdFromLocation(createResult);

    mockMvc
        .perform(get("/api/customers/" + id).with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Acme Corp"))
        .andExpect(jsonPath("$.email").value("contact@acme.com"));
  }

  @Test
  void shouldCreateCustomerWithMinimalFields() throws Exception {
    mockMvc
        .perform(
            post("/api/customers")
                .with(adminJwt())
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
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name": "First", "email": "duplicate@test.com"}
                    """))
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            post("/api/customers")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name": "Second", "email": "duplicate@test.com"}
                    """))
        .andExpect(status().isConflict());
  }

  @Test
  void shouldListCustomers() throws Exception {
    mockMvc
        .perform(
            post("/api/customers")
                .with(adminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name": "List Test Customer", "email": "listtest@test.com"}
                    """))
        .andExpect(status().isCreated());

    mockMvc
        .perform(get("/api/customers").with(memberJwt()))
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
                    .with(adminJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "Before Update", "email": "beforeupdate@test.com"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    var id = extractIdFromLocation(createResult);

    mockMvc
        .perform(
            put("/api/customers/" + id)
                .with(adminJwt())
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
  void shouldCreateCustomerWithAddress() throws Exception {
    var createResult =
        mockMvc
            .perform(
                post("/api/customers")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "name": "Address Corp",
                          "email": "address@test.com",
                          "address": "123 Main St, Suite 100\\nNew York, NY 10001"
                        }
                        """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("Address Corp"))
            .andExpect(jsonPath("$.address").value("123 Main St, Suite 100\nNew York, NY 10001"))
            .andReturn();

    var id = extractIdFromLocation(createResult);

    // Verify it persists on GET
    mockMvc
        .perform(get("/api/customers/" + id).with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.address").value("123 Main St, Suite 100\nNew York, NY 10001"));
  }

  @Test
  void shouldUpdateCustomerWithAddress() throws Exception {
    var createResult =
        mockMvc
            .perform(
                post("/api/customers")
                    .with(adminJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "Addr Update", "email": "addrupdate@test.com"}
                        """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.address").isEmpty())
            .andReturn();

    var id = extractIdFromLocation(createResult);

    mockMvc
        .perform(
            put("/api/customers/" + id)
                .with(adminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name": "Addr Update",
                      "email": "addrupdate@test.com",
                      "address": "456 Oak Ave"
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.address").value("456 Oak Ave"));

    // Verify via GET
    mockMvc
        .perform(get("/api/customers/" + id).with(adminJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.address").value("456 Oak Ave"));
  }

  @Test
  void shouldArchiveCustomer() throws Exception {
    var createResult =
        mockMvc
            .perform(
                post("/api/customers")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "To Archive", "email": "toarchive@test.com"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    var id = extractIdFromLocation(createResult);

    mockMvc
        .perform(delete("/api/customers/" + id).with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ARCHIVED"));

    // Archived customer should still be retrievable
    mockMvc
        .perform(get("/api/customers/" + id).with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ARCHIVED"));
  }

  // --- Validation errors ---

  @Test
  void shouldReject400WhenNameIsMissing() throws Exception {
    mockMvc
        .perform(
            post("/api/customers")
                .with(adminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"email": "valid@test.com"}
                    """))
        .andExpect(status().isBadRequest());
  }

  @Test
  void shouldReject400WhenEmailIsMissing() throws Exception {
    mockMvc
        .perform(
            post("/api/customers")
                .with(adminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name": "No Email"}
                    """))
        .andExpect(status().isBadRequest());
  }

  @Test
  void shouldReject400WhenEmailIsInvalid() throws Exception {
    mockMvc
        .perform(
            post("/api/customers")
                .with(adminJwt())
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
    mockMvc
        .perform(get("/api/customers/00000000-0000-0000-0000-000000000000").with(ownerJwt()))
        .andExpect(status().isNotFound());
  }

  // --- RBAC ---

  @Test
  void memberCannotCreateCustomer() throws Exception {
    mockMvc
        .perform(
            post("/api/customers")
                .with(memberJwt())
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
    mockMvc.perform(get("/api/customers").with(memberJwt())).andExpect(status().isOk());
  }

  // --- Tenant isolation ---

  @Test
  void customersAreIsolatedBetweenTenants() throws Exception {
    var createResult =
        mockMvc
            .perform(
                post("/api/customers")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "Tenant A Customer", "email": "isolation_a@test.com"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    var customerId = extractIdFromLocation(createResult);

    // Visible from tenant A
    mockMvc
        .perform(get("/api/customers/" + customerId).with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Tenant A Customer"));

    // NOT visible from tenant B
    mockMvc
        .perform(get("/api/customers/" + customerId).with(tenantBOwnerJwt()))
        .andExpect(status().isNotFound());

    // Tenant B list doesn't include tenant A's customer
    mockMvc
        .perform(get("/api/customers").with(tenantBOwnerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[*].name", everyItem(not("Tenant A Customer"))));
  }

  // --- Helpers ---

  private String extractIdFromLocation(MvcResult result) {
    String location = result.getResponse().getHeader("Location");
    return location.substring(location.lastIndexOf('/') + 1);
  }

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

  private JwtRequestPostProcessor memberJwt() {
    return jwt()
        .jwt(j -> j.subject("user_cust_member").claim("o", Map.of("id", ORG_ID, "rol", "member")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_MEMBER")));
  }

  private JwtRequestPostProcessor adminJwt() {
    return jwt()
        .jwt(j -> j.subject("user_cust_admin").claim("o", Map.of("id", ORG_ID, "rol", "admin")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_ADMIN")));
  }

  private JwtRequestPostProcessor ownerJwt() {
    return jwt()
        .jwt(j -> j.subject("user_cust_owner").claim("o", Map.of("id", ORG_ID, "rol", "owner")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_OWNER")));
  }

  private JwtRequestPostProcessor tenantBOwnerJwt() {
    return jwt()
        .jwt(
            j -> j.subject("user_cust_tenant_b").claim("o", Map.of("id", ORG_B_ID, "rol", "owner")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_OWNER")));
  }
}
