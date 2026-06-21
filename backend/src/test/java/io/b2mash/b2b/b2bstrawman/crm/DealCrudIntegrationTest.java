package io.b2mash.b2b.b2bstrawman.crm;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.Customer;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.customer.LifecycleStatus;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestEntityHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
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
class DealCrudIntegrationTest {

  private static final String ORG_ID = "org_deal_crud_test";
  private static final String OWNER_SUBJECT = "user_deal_crud_owner";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private CustomerRepository customerRepository;

  private String customerId;
  private String openStageId;
  private String tenantSchema;

  private JwtRequestPostProcessor owner() {
    return TestJwtFactory.ownerJwt(ORG_ID, OWNER_SUBJECT);
  }

  @BeforeAll
  void provisionTenantAndSeedData() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Deal CRUD Test Org", null);
    TestMemberHelper.syncMember(
        mockMvc, ORG_ID, OWNER_SUBJECT, "deal_crud_owner@test.com", "Deal CRUD Owner", "owner");
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
    customerId = TestEntityHelper.createCustomer(mockMvc, owner(), "Acme Corp", "acme@test.com");
    openStageId = firstOpenStageId();
  }

  private String firstOpenStageId() {
    return ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .call(this::lookupFirstOpenStage);
  }

  // resolved against the seeded pipeline within tenant scope
  @Autowired private PipelineStageRepository pipelineStageRepository;

  private String lookupFirstOpenStage() {
    return pipelineStageRepository
        .findFirstByStageTypeAndArchivedFalseOrderByPositionAsc(StageType.OPEN)
        .orElseThrow()
        .getId()
        .toString();
  }

  private String createDeal(String title) throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/deals")
                    .with(owner())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"customerId":"%s","title":"%s","valueAmount":1000.00}
                        """
                            .formatted(customerId, title)))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
  }

  @Test
  void createDealAgainstExistingCustomer_returns201WithDealNumberAndOpenStatus() throws Exception {
    mockMvc
        .perform(
            post("/api/deals")
                .with(owner())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"customerId":"%s","title":"Big Opportunity","valueAmount":50000.00}
                    """
                        .formatted(customerId)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").exists())
        .andExpect(jsonPath("$.dealNumber").exists())
        .andExpect(jsonPath("$.status").value("OPEN"))
        .andExpect(jsonPath("$.customerId").value(customerId))
        .andExpect(jsonPath("$.title").value("Big Opportunity"))
        .andExpect(jsonPath("$.stageName").exists());
  }

  @Test
  void intakeWithInlineCustomer_createsProspectCustomerAndOpenDeal() throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/deals/intake")
                    .with(owner())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"title":"Inbound Lead","valueAmount":7500.00,
                         "customer":{"name":"New Lead Ltd","email":"newlead@test.com",
                                     "phone":"+27110000000"}}
                        """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("OPEN"))
            .andExpect(jsonPath("$.dealNumber").exists())
            .andExpect(jsonPath("$.customerId").exists())
            .andReturn();

    String newCustomerId = JsonPath.read(result.getResponse().getContentAsString(), "$.customerId");
    String dealStageId = JsonPath.read(result.getResponse().getContentAsString(), "$.stageId");

    // Assert the created customer is a PROSPECT and the deal sits in the first OPEN stage.
    Customer created =
        ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
            .where(RequestScopes.ORG_ID, ORG_ID)
            .call(() -> customerRepository.findById(UUID.fromString(newCustomerId)).orElseThrow());
    org.junit.jupiter.api.Assertions.assertEquals(
        LifecycleStatus.PROSPECT, created.getLifecycleStatus());
    org.junit.jupiter.api.Assertions.assertEquals(openStageId, dealStageId);
  }

  @Test
  void getDeal_returnsDerivedFields() throws Exception {
    String dealId = createDeal("Get Me");
    mockMvc
        .perform(get("/api/deals/" + dealId).with(owner()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(dealId))
        .andExpect(jsonPath("$.effectiveProbabilityPct").isNumber())
        .andExpect(jsonPath("$.weightedValue").exists())
        .andExpect(jsonPath("$.stageName").exists());
  }

  @Test
  void updateDeal_changesValueAndProbabilityOverride() throws Exception {
    String dealId = createDeal("Update Me");
    mockMvc
        .perform(
            put("/api/deals/" + dealId)
                .with(owner())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"valueAmount":2000.00,"valueCurrency":"ZAR","probabilityOverride":50}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.valueAmount").value(2000.00))
        .andExpect(jsonPath("$.probabilityPct").value(50))
        .andExpect(jsonPath("$.effectiveProbabilityPct").value(50));
  }

  @Test
  void listDeals_filteredByStage_returnsPage() throws Exception {
    createDeal("Stage Filter Deal");
    mockMvc
        .perform(get("/api/deals").param("stageId", openStageId).with(owner()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isArray())
        .andExpect(jsonPath("$.page.totalElements").isNumber());
  }

  @Test
  void listDeals_filteredByStatus_returnsOnlyOpen() throws Exception {
    createDeal("Status Filter Deal");
    mockMvc
        .perform(get("/api/deals").param("status", "OPEN").with(owner()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isArray())
        .andExpect(jsonPath("$.content[0].status").value("OPEN"));
  }

  @Test
  void deleteDeal_returns204ThenGetReturns404() throws Exception {
    String dealId = createDeal("Delete Me");
    mockMvc.perform(delete("/api/deals/" + dealId).with(owner())).andExpect(status().isNoContent());
    mockMvc.perform(get("/api/deals/" + dealId).with(owner())).andExpect(status().isNotFound());
  }

  @Test
  void getMissingDeal_returns404() throws Exception {
    mockMvc
        .perform(get("/api/deals/" + UUID.randomUUID()).with(owner()))
        .andExpect(status().isNotFound());
  }

  @Test
  void listDeals_filteredByOwner_returnsPage() throws Exception {
    createDeal("Owner Filter Deal");
    // Owner defaults to the acting member (owner). Filter by a random owner → empty page.
    List<UUID> empty = List.of();
    mockMvc
        .perform(get("/api/deals").param("ownerId", UUID.randomUUID().toString()).with(owner()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isArray())
        .andExpect(jsonPath("$.page.totalElements").value(empty.size()));
  }
}
