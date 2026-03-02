package io.b2mash.b2b.b2bstrawman.prerequisite;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.fielddefinition.EntityType;
import io.b2mash.b2b.b2bstrawman.fielddefinition.FieldDefinition;
import io.b2mash.b2b.b2bstrawman.fielddefinition.FieldDefinitionRepository;
import io.b2mash.b2b.b2bstrawman.fielddefinition.FieldType;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CompletenessQueryTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_completeness_query_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private FieldDefinitionRepository fieldDefinitionRepository;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private TransactionTemplate transactionTemplate;

  private String tenantSchema;
  private UUID memberIdOwner;
  private String customerId1;
  private String customerId2;
  private String customerId3;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Completeness Query Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");

    syncMember("user_cqt_owner", "cqt_owner@test.com", "CQT Owner", "owner");

    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
    memberIdOwner =
        UUID.fromString(
            syncMember("user_cqt_owner2", "cqt_owner2@test.com", "CQT Owner2", "owner"));

    // Create 3 customers via API
    customerId1 = createCustomer("Completeness Customer 1", "cq1@test.com");
    customerId2 = createCustomer("Completeness Customer 2", "cq2@test.com");
    customerId3 = createCustomer("Completeness Customer 3", "cq3@test.com");

    // Create field definitions with requiredForContexts
    runInTenant(
        tenantSchema,
        ORG_ID,
        memberIdOwner,
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var fd1 =
                      new FieldDefinition(
                          EntityType.CUSTOMER, "CQ Tax Number", "cq_tax_number", FieldType.TEXT);
                  fd1.setRequiredForContexts(
                      new ArrayList<>(List.of("INVOICE_GENERATION", "LIFECYCLE_ACTIVATION")));
                  fieldDefinitionRepository.save(fd1);

                  var fd2 =
                      new FieldDefinition(
                          EntityType.CUSTOMER, "CQ Address", "cq_address", FieldType.TEXT);
                  fd2.setRequiredForContexts(new ArrayList<>(List.of("INVOICE_GENERATION")));
                  fieldDefinitionRepository.save(fd2);
                }));
  }

  @Test
  void computeReadinessByContext_returnsPerContextBreakdown() throws Exception {
    // totalRequired includes seeded fields from common-customer pack + test fields
    mockMvc
        .perform(
            get("/api/customers/completeness-summary")
                .with(ownerJwt())
                .param("customerIds", customerId1))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$." + customerId1 + ".totalRequired").isNumber())
        .andExpect(jsonPath("$." + customerId1 + ".filled").value(0))
        .andExpect(jsonPath("$." + customerId1 + ".percentage").value(0));
  }

  @Test
  void batchCompute_multipleCustomers_returnsScores() throws Exception {
    mockMvc
        .perform(
            get("/api/customers/completeness-summary")
                .with(ownerJwt())
                .param("customerIds", customerId1 + "," + customerId2))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$." + customerId1).exists())
        .andExpect(jsonPath("$." + customerId2).exists());
  }

  @Test
  void batchCompute_avoidNPlusOne_singleQuery() throws Exception {
    mockMvc
        .perform(
            get("/api/customers/completeness-summary")
                .with(ownerJwt())
                .param("customerIds", customerId1 + "," + customerId2 + "," + customerId3))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$." + customerId1).exists())
        .andExpect(jsonPath("$." + customerId2).exists())
        .andExpect(jsonPath("$." + customerId3).exists());
  }

  @Test
  void completenessEndpoint_returnsScoresForRequestedIds() throws Exception {
    mockMvc
        .perform(
            get("/api/customers/completeness-summary")
                .with(ownerJwt())
                .param("customerIds", customerId1 + "," + customerId2))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$." + customerId1 + ".totalRequired").isNumber())
        .andExpect(jsonPath("$." + customerId1 + ".filled").isNumber())
        .andExpect(jsonPath("$." + customerId1 + ".percentage").isNumber());
  }

  @Test
  void aggregatedQuery_returnsTopMissingFields() throws Exception {
    mockMvc
        .perform(
            get("/api/customers/completeness-summary").with(ownerJwt()).param("aggregated", "true"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.topMissingFields").isArray())
        .andExpect(jsonPath("$.totalCount").isNumber())
        .andExpect(jsonPath("$.incompleteCount").isNumber());
  }

  @Test
  void completenessScore_allFieldsFilled_returns100Percent() throws Exception {
    // Fill ALL required fields (seeded common-customer pack + test fields) via API update
    mockMvc
        .perform(
            put("/api/customers/" + customerId3)
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name": "Completeness Customer 3",
                      "email": "cq3@test.com",
                      "customFields": {
                        "cq_tax_number": "TAX123",
                        "cq_address": "123 Test St",
                        "address_line1": "456 Main St",
                        "city": "Cape Town",
                        "country": "ZA",
                        "tax_number": "TAX456"
                      }
                    }
                    """))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            get("/api/customers/completeness-summary")
                .with(ownerJwt())
                .param("customerIds", customerId3))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$." + customerId3 + ".percentage").value(100));
  }

  private void runInTenant(String schema, String orgId, UUID memberId, Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, schema)
        .where(RequestScopes.ORG_ID, orgId)
        .where(RequestScopes.MEMBER_ID, memberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(action);
  }

  private String createCustomer(String name, String email) throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/customers")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "%s", "email": "%s"}
                        """
                            .formatted(name, email)))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
  }

  private String syncMember(String clerkUserId, String email, String name, String orgRole)
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
                          "clerkOrgId": "%s", "clerkUserId": "%s", "email": "%s",
                          "name": "%s", "avatarUrl": null, "orgRole": "%s"
                        }
                        """
                            .formatted(ORG_ID, clerkUserId, email, name, orgRole)))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.memberId");
  }

  private JwtRequestPostProcessor ownerJwt() {
    return jwt()
        .jwt(j -> j.subject("user_cqt_owner").claim("o", Map.of("id", ORG_ID, "rol", "owner")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_OWNER")));
  }

  private JwtRequestPostProcessor memberJwt() {
    return jwt()
        .jwt(j -> j.subject("user_cqt_member").claim("o", Map.of("id", ORG_ID, "rol", "member")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_MEMBER")));
  }
}
