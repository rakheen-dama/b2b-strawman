package io.b2mash.b2b.b2bstrawman.settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.audit.AuditEvent;
import io.b2mash.b2b.b2bstrawman.audit.AuditEventRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.util.List;
import java.util.Map;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Integration tests for the collections / dunning policy settings API (Phase 83, Epic 588B): {@code
 * GET/PUT /api/settings/collections}. Covers fresh-tenant defaults, PUT round-trip + persistence,
 * threshold validation (non-increasing and &lt; 1 → 400), member-forbidden (403), and the {@code
 * collections.policy.updated} audit row (asserted via the audit repository, not logs).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
// Methods share one tenant, so the fresh-state defaults read must run before any mutating PUT.
// Order guarantees that. (The audit test captures its own pre-PUT baseline and is order-robust.)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CollectionsSettingsApiTest {

  private static final String ORG_ID = "org_collections_settings";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private AuditEventRepository auditEventRepository;

  private String tenantSchema;

  @BeforeAll
  void provisionTenant() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Collections Settings Org", null);
    TestMemberHelper.syncMember(
        mockMvc, ORG_ID, "user_coll_owner", "coll_owner@test.com", "Owner", "owner");
    TestMemberHelper.syncMember(
        mockMvc, ORG_ID, "user_coll_member", "coll_member@test.com", "Member", "member");
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
  }

  @Test
  @Order(1)
  void getReturnsBakedInDefaultsForFreshTenant() throws Exception {
    mockMvc
        .perform(
            get("/api/settings/collections")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_coll_member")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.collectionsEnabled").value(false))
        .andExpect(jsonPath("$.stage1DaysOverdue").value(7))
        .andExpect(jsonPath("$.stage2DaysOverdue").value(21))
        .andExpect(jsonPath("$.stage3DaysOverdue").value(45))
        .andExpect(jsonPath("$.escalateDaysOverdue").value(60));
  }

  @Test
  @Order(3)
  void putRoundTripPersistsAllFields() throws Exception {
    mockMvc
        .perform(
            put("/api/settings/collections")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_coll_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "collectionsEnabled": true,
                      "stage1DaysOverdue": 5,
                      "stage2DaysOverdue": 15,
                      "stage3DaysOverdue": 30,
                      "escalateDaysOverdue": 50
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.collectionsEnabled").value(true))
        .andExpect(jsonPath("$.stage1DaysOverdue").value(5))
        .andExpect(jsonPath("$.escalateDaysOverdue").value(50));

    // Subsequent GET reflects persistence.
    mockMvc
        .perform(
            get("/api/settings/collections")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_coll_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.collectionsEnabled").value(true))
        .andExpect(jsonPath("$.stage1DaysOverdue").value(5))
        .andExpect(jsonPath("$.stage2DaysOverdue").value(15))
        .andExpect(jsonPath("$.stage3DaysOverdue").value(30))
        .andExpect(jsonPath("$.escalateDaysOverdue").value(50));
  }

  @Test
  @Order(4)
  void putRejectsNonIncreasingThresholds() throws Exception {
    mockMvc
        .perform(
            put("/api/settings/collections")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_coll_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "collectionsEnabled": true,
                      "stage1DaysOverdue": 7,
                      "stage2DaysOverdue": 21,
                      "stage3DaysOverdue": 21,
                      "escalateDaysOverdue": 60
                    }
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.title").value("Invalid collections thresholds"))
        .andExpect(
            jsonPath("$.detail")
                .value(
                    "Thresholds must be strictly increasing: stage1 < stage2 < stage3 <"
                        + " escalate"));
  }

  @Test
  @Order(5)
  void putRejectsThresholdBelowOne() throws Exception {
    mockMvc
        .perform(
            put("/api/settings/collections")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_coll_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "collectionsEnabled": true,
                      "stage1DaysOverdue": 0,
                      "stage2DaysOverdue": 21,
                      "stage3DaysOverdue": 45,
                      "escalateDaysOverdue": 60
                    }
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.title").value("Validation failed"))
        .andExpect(jsonPath("$.detail").value("1 field(s) have validation errors"))
        .andExpect(jsonPath("$.fieldErrors[0].field").value("stage1DaysOverdue"))
        .andExpect(
            jsonPath("$.fieldErrors[0].message").value("stage1DaysOverdue must be at least 1"));
  }

  @Test
  @Order(6)
  void putForbiddenForMember() throws Exception {
    mockMvc
        .perform(
            put("/api/settings/collections")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_coll_member"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "collectionsEnabled": true,
                      "stage1DaysOverdue": 5,
                      "stage2DaysOverdue": 15,
                      "stage3DaysOverdue": 30,
                      "escalateDaysOverdue": 50
                    }
                    """))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.status").value(403))
        .andExpect(jsonPath("$.title").value("Access denied"))
        .andExpect(jsonPath("$.detail").value("Insufficient permissions for this operation"));
  }

  @Test
  @Order(2)
  void putWritesPolicyUpdatedAuditRowWithOldAndNewValues() throws Exception {
    // Capture the actual pre-PUT value so the old-value assertion does not depend on run order
    // (a prior mutating PUT against the shared tenant would otherwise break a hardcoded default).
    String preBody =
        mockMvc
            .perform(
                get("/api/settings/collections")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_coll_owner")))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    int preStage1 = JsonPath.read(preBody, "$.stage1DaysOverdue");

    mockMvc
        .perform(
            put("/api/settings/collections")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_coll_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "collectionsEnabled": true,
                      "stage1DaysOverdue": 3,
                      "stage2DaysOverdue": 9,
                      "stage3DaysOverdue": 27,
                      "escalateDaysOverdue": 40
                    }
                    """))
        .andExpect(status().isOk());

    // findByFilter orders by occurredAt DESC, so getFirst() is the event this PUT just wrote.
    var events = readEvents("collections.policy.updated");
    assertThat(events).isNotEmpty();
    var details = events.getFirst().getDetails();
    assertThat(details).containsKeys("stage1_days", "escalate_days", "collections_enabled");
    // The delta carries both the old and new values (numbers only — no PII).
    Object stage1Delta = details.get("stage1_days");
    assertThat(stage1Delta).isInstanceOf(Map.class);
    @SuppressWarnings("unchecked")
    Map<String, Object> stage1Map = (Map<String, Object>) stage1Delta;
    assertThat(stage1Map).containsKeys("from", "to");
    // The delta carries both the captured pre-PUT value and the new value.
    assertThat(String.valueOf(stage1Map.get("from"))).isEqualTo(String.valueOf(preStage1));
    assertThat(String.valueOf(stage1Map.get("to"))).isEqualTo("3");
  }

  private List<AuditEvent> readEvents(String prefix) {
    @SuppressWarnings("unchecked")
    List<AuditEvent>[] holder = new List[1];
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .run(
            () ->
                holder[0] =
                    transactionTemplate.execute(
                        tx ->
                            auditEventRepository
                                .findByFilter(
                                    null, null, null, prefix, null, null, PageRequest.of(0, 200))
                                .getContent()));
    return holder[0];
  }
}
