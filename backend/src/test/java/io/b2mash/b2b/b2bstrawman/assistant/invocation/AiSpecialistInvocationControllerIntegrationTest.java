package io.b2mash.b2b.b2bstrawman.assistant.invocation;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.assistant.invocation.applier.OutputApplier;
import io.b2mash.b2b.b2bstrawman.assistant.invocation.payload.BillingPolishPayload;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** HTTP integration tests for {@link AiSpecialistInvocationController}. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({
  TestcontainersConfiguration.class,
  AiSpecialistInvocationControllerIntegrationTest.FakeApplierConfig.class
})
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AiSpecialistInvocationControllerIntegrationTest {

  private static final String ORG_ID = "org_inv_ctl_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private AiSpecialistInvocationService service;

  private String tenantSchema;
  private UUID ownerMemberId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Inv Ctl Test Org", null);
    var ownerStr =
        TestMemberHelper.syncMember(
            mockMvc, ORG_ID, "user_invctl_owner", "invctl_owner@test.com", "Owner", "owner");
    ownerMemberId = UUID.fromString(ownerStr);
    TestMemberHelper.syncMember(
        mockMvc, ORG_ID, "user_invctl_member", "invctl_member@test.com", "Member", "member");
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
  }

  private UUID seedPending() {
    UUID[] holder = new UUID[1];
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, ownerMemberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .where(RequestScopes.CAPABILITIES, Set.of("AI_ASSISTANT_USE", "TEAM_OVERSIGHT"))
        .run(
            () -> {
              var inv =
                  service.recordRunning(
                      "billing-za",
                      InvocationSource.MEMBER,
                      ownerMemberId,
                      null,
                      "invoice",
                      UUID.randomUUID(),
                      "v1");
              service.recordProposal(inv.getId(), new BillingPolishPayload(null, List.of()));
              service.markPendingApproval(inv.getId());
              holder[0] = inv.getId();
            });
    return holder[0];
  }

  @Test
  void listInvocations_returnsPagedShapeViaDto() throws Exception {
    seedPending();
    mockMvc
        .perform(
            get("/api/assistant/invocations")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_invctl_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isArray())
        .andExpect(jsonPath("$.page").exists())
        .andExpect(jsonPath("$.page.size").exists())
        .andExpect(jsonPath("$.page.number").exists());
  }

  @Test
  void listInvocations_requiresAiAssistantUse() throws Exception {
    mockMvc
        .perform(
            get("/api/assistant/invocations")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_invctl_member")))
        .andExpect(status().isForbidden());
  }

  @Test
  void approveEndpoint_returnsAppliedShape() throws Exception {
    UUID id = seedPending();
    mockMvc
        .perform(
            post("/api/assistant/invocations/" + id + "/approve")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_invctl_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(id.toString()))
        .andExpect(jsonPath("$.status").value("APPROVED"))
        .andExpect(jsonPath("$.appliedAt").exists());
  }

  @Test
  void rejectEndpoint_returns204() throws Exception {
    UUID id = seedPending();
    mockMvc
        .perform(
            post("/api/assistant/invocations/" + id + "/reject")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_invctl_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rejectReason\":\"Bad output\"}"))
        .andExpect(status().isNoContent());
  }

  @Test
  void getById_returnsDetailDto() throws Exception {
    UUID id = seedPending();
    mockMvc
        .perform(
            get("/api/assistant/invocations/" + id)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_invctl_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(id.toString()))
        .andExpect(jsonPath("$.specialistId").value("billing-za"))
        .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"));
  }

  @Test
  void approveEndpoint_acceptsPolymorphicAppliedOutputPayload() throws Exception {
    // Verifies the OutputPayload sealed-interface polymorphic deserialization end-to-end:
    // posting {"appliedOutput":{"kind":"BillingPolishPayload"}} must round-trip to the
    // BillingPolishPayload type without 4xx, and the resulting applied_output column should
    // hold a BillingPolishPayload instance.
    UUID id = seedPending();
    mockMvc
        .perform(
            post("/api/assistant/invocations/" + id + "/approve")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_invctl_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"appliedOutput\":{\"kind\":\"BillingPolishPayload\"}}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("APPROVED"));
  }

  @Test
  void rejectEndpoint_blankReason_returns400() throws Exception {
    UUID id = seedPending();
    mockMvc
        .perform(
            post("/api/assistant/invocations/" + id + "/reject")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_invctl_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rejectReason\":\"\"}"))
        .andExpect(status().is4xxClientError());
  }

  @Test
  void getById_persistsPolymorphicProposedOutputPayload() throws Exception {
    // JSONB round-trip: ensure the proposedOutput stored as JSON deserializes back to its
    // concrete subtype after a fresh entity load (validates @JsonTypeInfo wiring).
    UUID id = seedPending();
    mockMvc
        .perform(
            get("/api/assistant/invocations/" + id)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_invctl_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.proposedOutput.kind").value("BillingPolishPayload"));
  }

  @Test
  void bulkApproveOver25_returns400() throws Exception {
    StringBuilder ids = new StringBuilder();
    for (int i = 0; i < 26; i++) {
      if (i > 0) {
        ids.append(",");
      }
      ids.append("\"").append(UUID.randomUUID()).append("\"");
    }
    mockMvc
        .perform(
            post("/api/assistant/invocations/bulk-approve")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_invctl_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"ids\":[" + ids + "]}"))
        .andExpect(status().isBadRequest());
  }

  // ----- fake applier wiring -----

  @TestConfiguration
  static class FakeApplierConfig {
    @Bean("billingPolishApplier")
    OutputApplier<BillingPolishPayload> fakeBillingPolishApplier() {
      return new OutputApplier<>() {
        @Override
        public Class<BillingPolishPayload> payloadType() {
          return BillingPolishPayload.class;
        }

        @Override
        public void apply(BillingPolishPayload payload, UUID actorId) {
          // No-op; recorded by service-level test.
        }
      };
    }
  }
}
