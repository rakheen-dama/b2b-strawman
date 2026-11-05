package io.b2mash.b2b.b2bstrawman.collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.CustomerProject;
import io.b2mash.b2b.b2bstrawman.customer.CustomerProjectRepository;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.math.BigDecimal;
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
 * G88-M13 (Phase 89 §14.3.18): {@code GET /api/invoices/outstanding-summary?matterId=} narrows the
 * debtor-book summary to fee notes with a line on the matter ({@code invoices} has no {@code
 * project_id}); without the parameter the summary stays firm-wide. Real write path (create → line →
 * approve → send through the API) in a dedicated tenant so the tenant-wide totals are exact.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OutstandingSummaryMatterFilterTest {

  private static final String ORG_ID = "org_outstanding_matter_filter";
  private static final String OWNER = "user_osmf_owner";
  private static final String MEMBER = "user_osmf_member";

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private ProjectRepository projectRepository;
  @Autowired private CustomerProjectRepository customerProjectRepository;

  private UUID matterA;
  private UUID matterB;
  private UUID matterEmpty;
  private BigDecimal totalA;
  private BigDecimal totalB;

  @BeforeAll
  void setup() throws Exception {
    // "ZA" → ZAR default currency (the summary is computed in the org default currency).
    provisioningService.provisionTenant(ORG_ID, "Outstanding Matter Filter Org", null, "ZA");
    UUID ownerId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, OWNER, "osmf_owner@test.com", "OSMF Owner", "owner"));
    TestMemberHelper.syncMember(
        mockMvc, ORG_ID, MEMBER, "osmf_member@test.com", "OSMF Member", "member");
    String schema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

    UUID[] client = new UUID[1];
    ScopedValue.where(RequestScopes.TENANT_ID, schema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, ownerId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () -> {
              client[0] =
                  customerRepository
                      .save(
                          TestCustomerFactory.createActiveCustomerWithPrerequisiteFields(
                              "Filter Client", "filter_client@test.com", ownerId))
                      .getId();
              matterA = saveLinkedMatter("Matter A", client[0], ownerId);
              matterB = saveLinkedMatter("Matter B", client[0], ownerId);
              matterEmpty = saveLinkedMatter("Matter Empty", client[0], ownerId);
            });

    totalA = sendFeeNote(client[0], matterA, "5000.00");
    totalB = sendFeeNote(client[0], matterB, "1200.00");
  }

  @Test
  void matterFilter_returnsOnlyThatMattersFeeNotes() throws Exception {
    JsonNode a = summary("?matterId=" + matterA);
    assertThat(a.get("currency").asText()).isEqualTo("ZAR");
    assertThat(a.get("outstandingCount").asInt()).isEqualTo(1);
    assertThat(a.get("outstandingTotal").decimalValue()).isEqualByComparingTo(totalA);

    JsonNode b = summary("?matterId=" + matterB);
    assertThat(b.get("outstandingCount").asInt()).isEqualTo(1);
    assertThat(b.get("outstandingTotal").decimalValue()).isEqualByComparingTo(totalB);
  }

  @Test
  void noParam_staysFirmWide() throws Exception {
    JsonNode all = summary("");
    assertThat(all.get("outstandingCount").asInt()).isEqualTo(2);
    assertThat(all.get("outstandingTotal").decimalValue()).isEqualByComparingTo(totalA.add(totalB));
  }

  @Test
  void matterWithNoLines_returnsZeros() throws Exception {
    JsonNode e = summary("?matterId=" + matterEmpty);
    assertThat(e.get("currency").asText()).isEqualTo("ZAR");
    assertThat(e.get("outstandingCount").asInt()).isZero();
    assertThat(e.get("outstandingTotal").decimalValue()).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(e.get("overdueCount").asInt()).isZero();
    assertThat(e.get("approvedUnsentCount").asInt()).isZero();
  }

  @Test
  void matterFilter_stillRequiresFinancialVisibility() throws Exception {
    mockMvc
        .perform(
            get("/api/invoices/outstanding-summary?matterId=" + matterA)
                .with(TestJwtFactory.memberJwt(ORG_ID, MEMBER)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.status").value(403))
        .andExpect(jsonPath("$.title").value("Access denied"))
        .andExpect(jsonPath("$.detail").value("Insufficient permissions for this operation"));
  }

  // --- helpers ---

  private UUID saveLinkedMatter(String name, UUID clientId, UUID ownerId) {
    UUID matterId =
        projectRepository.save(new Project(name, "634B outstanding filter", ownerId)).getId();
    customerProjectRepository.save(new CustomerProject(clientId, matterId, ownerId));
    return matterId;
  }

  /** Real write path: create → one line on the matter → approve → send; returns the total. */
  private BigDecimal sendFeeNote(UUID clientId, UUID matterId, String unitPrice) throws Exception {
    String id =
        objectMapper
            .readTree(
                mockMvc
                    .perform(
                        post("/api/invoices")
                            .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(
                                "{\"clientId\": \"%s\", \"currency\": \"ZAR\"}"
                                    .formatted(clientId)))
                    .andExpect(status().isCreated())
                    .andReturn()
                    .getResponse()
                    .getContentAsString())
            .get("id")
            .asText();
    mockMvc
        .perform(
            post("/api/invoices/" + id + "/lines")
                .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"matterId": "%s", "description": "Consultation", "quantity": 1.0000,
                     "unitPrice": %s, "sortOrder": 0}
                    """
                        .formatted(matterId, unitPrice)))
        .andExpect(status().isCreated());
    postOk("/api/invoices/" + id + "/approve");
    JsonNode sent = postOk("/api/invoices/" + id + "/send");
    assertThat(sent.get("status").asText()).isEqualTo("SENT");
    return sent.get("total").decimalValue();
  }

  private JsonNode postOk(String path) throws Exception {
    return objectMapper.readTree(
        mockMvc
            .perform(post(path).with(TestJwtFactory.ownerJwt(ORG_ID, OWNER)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  private JsonNode summary(String queryString) throws Exception {
    return objectMapper.readTree(
        mockMvc
            .perform(
                get("/api/invoices/outstanding-summary" + queryString)
                    .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }
}
