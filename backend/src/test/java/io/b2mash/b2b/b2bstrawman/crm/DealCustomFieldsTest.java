package io.b2mash.b2b.b2bstrawman.crm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.activity.ActivityMessageFormatter;
import io.b2mash.b2b.b2bstrawman.audit.AuditEvent;
import io.b2mash.b2b.b2bstrawman.audit.AuditEventGroup;
import io.b2mash.b2b.b2bstrawman.audit.AuditEventRecord;
import io.b2mash.b2b.b2bstrawman.audit.AuditEventTypeRegistry;
import io.b2mash.b2b.b2bstrawman.member.Member;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.orgrole.OrgRole;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestEntityHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
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
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Epic 577 (577A.5) — proves {@code Deal} is a first-class field-able entity by registering it with
 * the existing custom-field / field-group machinery via {@link
 * io.b2mash.b2b.b2bstrawman.fielddefinition.EntityType#DEAL}.
 *
 * <p>Covers: custom-field write+read round-trip (PUT /api/deals/{id}), invalid custom-field value →
 * 400, auto-apply field-group attached at create, and that deal audit events resolve to the SALES
 * group and render through {@link ActivityMessageFormatter}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DealCustomFieldsTest {

  private static final String ORG_ID = "org_deal_cf_test";
  private static final String OWNER_SUBJECT = "user_deal_cf_owner";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private DealRepository dealRepository;
  @Autowired private AuditEventTypeRegistry auditEventTypeRegistry;

  private String customerId;
  private String tenantSchema;

  private JwtRequestPostProcessor owner() {
    return TestJwtFactory.ownerJwt(ORG_ID, OWNER_SUBJECT);
  }

  @BeforeAll
  void provisionTenantAndSeedData() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Deal Custom Fields Test Org", null);
    TestMemberHelper.syncMember(
        mockMvc, ORG_ID, OWNER_SUBJECT, "deal_cf_owner@test.com", "Deal CF Owner", "owner");
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
    customerId =
        TestEntityHelper.createCustomer(mockMvc, owner(), "CF Acme Corp", "cf_acme@test.com");

    // Slug is derived from the name server-side: "Reference Number" -> reference_number,
    // "External URL" -> external_url (mirrors InvoiceCustomFieldIntegrationTest).
    createFieldDefinition("Reference Number", "TEXT");
    createFieldDefinition("External URL", "URL");
  }

  private void createFieldDefinition(String name, String fieldType) throws Exception {
    mockMvc
        .perform(
            post("/api/field-definitions")
                .with(owner())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"entityType":"DEAL","name":"%s","fieldType":"%s","required":false,"sortOrder":0}
                    """
                        .formatted(name, fieldType)))
        .andExpect(status().isCreated());
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
  void customFieldWriteAndReadRoundTripsOnDeal() throws Exception {
    String dealId = createDeal("CF Round-Trip Deal");

    mockMvc
        .perform(
            put("/api/deals/" + dealId)
                .with(owner())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"customFields":{"reference_number":"REF-001"}}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.customFields.reference_number").value("REF-001"));

    mockMvc
        .perform(get("/api/deals/" + dealId).with(owner()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.customFields.reference_number").value("REF-001"));
  }

  @Test
  void invalidCustomFieldValueIsRejectedWith400() throws Exception {
    String dealId = createDeal("CF Invalid Deal");

    mockMvc
        .perform(
            put("/api/deals/" + dealId)
                .with(owner())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"customFields":{"external_url":"not-a-url"}}
                    """))
        .andExpect(status().isBadRequest());
  }

  @Test
  void autoApplyFieldGroupIsAttachedAtCreate() throws Exception {
    var groupResult =
        mockMvc
            .perform(
                post("/api/field-groups")
                    .with(owner())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"entityType":"DEAL","name":"Deal Auto-Apply Group","sortOrder":0,"autoApply":true}
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    UUID groupId =
        UUID.fromString(JsonPath.read(groupResult.getResponse().getContentAsString(), "$.id"));

    String dealId = createDeal("Auto-Apply Deal");
    UUID dealUuid = UUID.fromString(dealId);

    // DealResponse does not expose appliedFieldGroups (matches Customer/Invoice response
    // contracts), so assert via the repository inside tenant scope.
    boolean attached =
        ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
            .where(RequestScopes.ORG_ID, ORG_ID)
            .call(
                () ->
                    dealRepository.findOneById(dealUuid).getAppliedFieldGroups().contains(groupId));
    assertThat(attached).isTrue();
  }

  @Test
  void dealAuditEventsResolveToSalesGroup() {
    assertThat(auditEventTypeRegistry.resolve("deal.created").group())
        .isEqualTo(AuditEventGroup.SALES);
    assertThat(auditEventTypeRegistry.resolve("deal.stage_changed").group())
        .isEqualTo(AuditEventGroup.SALES);
    assertThat(auditEventTypeRegistry.resolve("deal.won").group()).isEqualTo(AuditEventGroup.SALES);
    assertThat(auditEventTypeRegistry.resolve("deal.lost").group())
        .isEqualTo(AuditEventGroup.SALES);
    assertThat(auditEventTypeRegistry.resolve("deal.reopened").group())
        .isEqualTo(AuditEventGroup.SALES);
  }

  @Test
  void activityFeedRendersDealMessages() {
    var formatter = new ActivityMessageFormatter();
    var role = new OrgRole("Member", "member", "Default member role", true);
    UUID actorId = UUID.randomUUID();
    var actor = new Member("clerk_deal_actor", "actor@test.com", "Alice", null, role);
    Map<UUID, Member> actorMap = Map.of(actorId, actor);

    assertThat(renderMessage(formatter, "deal.created", actorId, actorMap))
        .isEqualTo("Alice created a deal");
    assertThat(renderMessage(formatter, "deal.stage_changed", actorId, actorMap))
        .isEqualTo("Alice moved a deal to a new stage");
    assertThat(renderMessage(formatter, "deal.won", actorId, actorMap))
        .isEqualTo("Alice won a deal");
    assertThat(renderMessage(formatter, "deal.lost", actorId, actorMap))
        .isEqualTo("Alice marked a deal as lost");
    assertThat(renderMessage(formatter, "deal.reopened", actorId, actorMap))
        .isEqualTo("Alice re-opened a deal");
  }

  private String renderMessage(
      ActivityMessageFormatter formatter,
      String eventType,
      UUID actorId,
      Map<UUID, Member> actorMap) {
    var record =
        new AuditEventRecord(
            eventType, "deal", UUID.randomUUID(), actorId, "USER", "API", null, null, Map.of());
    return formatter.format(new AuditEvent(record), actorMap, Map.of()).message();
  }
}
