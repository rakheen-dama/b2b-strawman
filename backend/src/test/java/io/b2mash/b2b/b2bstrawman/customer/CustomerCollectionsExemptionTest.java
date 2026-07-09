package io.b2mash.b2b.b2bstrawman.customer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestEntityHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
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
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Integration tests for the per-customer collections-exemption endpoint (Phase 83, Epic 588B):
 * {@code PUT /api/customers/{id}/collections-exemption}. Covers owner-set, admin-clear (with a
 * repository-level persistence check under tenant scope), member-forbidden (403), and unknown-id
 * (404 ProblemDetail). No audit type exists for exemption — the flag lives on the customer row.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CustomerCollectionsExemptionTest {

  private static final String ORG_ID = "org_collections_exemption";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private CustomerRepository customerRepository;

  private String tenantSchema;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Collections Exemption Org", null);
    TestMemberHelper.syncMember(
        mockMvc, ORG_ID, "user_exempt_owner", "exempt_owner@test.com", "Owner", "owner");
    TestMemberHelper.syncMember(
        mockMvc, ORG_ID, "user_exempt_admin", "exempt_admin@test.com", "Admin", "admin");
    TestMemberHelper.syncMember(
        mockMvc, ORG_ID, "user_exempt_member", "exempt_member@test.com", "Member", "member");
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
  }

  @Test
  void ownerSetsExemptionAndItPersists() throws Exception {
    var customerId =
        TestEntityHelper.createCustomer(
            mockMvc,
            TestJwtFactory.ownerJwt(ORG_ID, "user_exempt_owner"),
            "Exempt Co",
            "exempt@test.com");

    mockMvc
        .perform(
            put("/api/customers/{id}/collections-exemption", customerId)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_exempt_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"collectionsExempt": true}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(customerId))
        .andExpect(jsonPath("$.collectionsExempt").value(true));

    assertThat(readExemptFlag(UUID.fromString(customerId))).isTrue();
  }

  @Test
  void adminClearsExemptionAndItPersists() throws Exception {
    var customerId =
        TestEntityHelper.createCustomer(
            mockMvc,
            TestJwtFactory.ownerJwt(ORG_ID, "user_exempt_owner"),
            "Clearable Co",
            "clearable@test.com");

    // Set first (owner), then clear (admin).
    mockMvc
        .perform(
            put("/api/customers/{id}/collections-exemption", customerId)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_exempt_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"collectionsExempt": true}
                    """))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            put("/api/customers/{id}/collections-exemption", customerId)
                .with(TestJwtFactory.adminJwt(ORG_ID, "user_exempt_admin"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"collectionsExempt": false}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.collectionsExempt").value(false));

    assertThat(readExemptFlag(UUID.fromString(customerId))).isFalse();
  }

  @Test
  void memberForbidden() throws Exception {
    var customerId =
        TestEntityHelper.createCustomer(
            mockMvc,
            TestJwtFactory.ownerJwt(ORG_ID, "user_exempt_owner"),
            "Member Blocked Co",
            "blocked@test.com");

    mockMvc
        .perform(
            put("/api/customers/{id}/collections-exemption", customerId)
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_exempt_member"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"collectionsExempt": true}
                    """))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.status").value(403))
        .andExpect(jsonPath("$.title").value("Access denied"))
        .andExpect(jsonPath("$.detail").value("Insufficient permissions for this operation"));
  }

  @Test
  void unknownCustomerReturns404ProblemDetail() throws Exception {
    mockMvc
        .perform(
            put("/api/customers/{id}/collections-exemption", UUID.randomUUID())
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_exempt_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"collectionsExempt": true}
                    """))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(jsonPath("$.title").exists());
  }

  private boolean readExemptFlag(UUID customerId) {
    boolean[] holder = new boolean[1];
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .run(
            () ->
                holder[0] =
                    Boolean.TRUE.equals(
                        transactionTemplate.execute(
                            tx ->
                                customerRepository
                                    .findById(customerId)
                                    .orElseThrow()
                                    .isCollectionsExempt())));
    return holder[0];
  }
}
