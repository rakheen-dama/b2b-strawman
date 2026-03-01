package io.b2mash.b2b.b2bstrawman.proposal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.audit.AuditEventFilter;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.SchemaNameGenerator;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
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
class ProposalAuditTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_proposal_audit_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private AuditService auditService;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private JdbcTemplate jdbcTemplate;

  private String schemaName;
  private String ownerMemberId;
  private String customerId;
  private UUID portalContactId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Proposal Audit Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");
    schemaName = SchemaNameGenerator.generateSchemaName(ORG_ID);

    ownerMemberId = syncMember("user_pa_owner", "pa_owner@test.com", "PA Owner", "owner");

    customerId = createCustomer("Audit Test Customer", "audit-customer@test.com");
    fillPrerequisiteFields(customerId);
    portalContactId = createPortalContact(customerId, "contact@audit-test.com", "Audit Contact");
  }

  @Test
  void createProposal_emitsAuditEvent() throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/proposals")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "title": "Audit Create Proposal",
                          "customerId": "%s",
                          "feeModel": "HOURLY"
                        }
                        """
                            .formatted(customerId)))
            .andExpect(status().isCreated())
            .andReturn();

    var proposalId = UUID.fromString(extractIdFromLocation(result));

    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .run(
            () -> {
              var page =
                  auditService.findEvents(
                      new AuditEventFilter(
                          "proposal", proposalId, null, "proposal.created", null, null),
                      PageRequest.of(0, 10));

              assertThat(page.getTotalElements()).isEqualTo(1);
              var event = page.getContent().getFirst();
              assertThat(event.getEventType()).isEqualTo("proposal.created");
              assertThat(event.getEntityType()).isEqualTo("proposal");
              assertThat(event.getEntityId()).isEqualTo(proposalId);
              assertThat(event.getDetails()).containsEntry("title", "Audit Create Proposal");
              assertThat(event.getDetails()).containsEntry("fee_model", "HOURLY");
              assertThat(event.getDetails()).containsEntry("customer_id", customerId);
              assertThat(event.getActorType()).isEqualTo("USER");
              assertThat(event.getSource()).isEqualTo("API");
            });
  }

  @Test
  void updateProposal_emitsAuditEvent() throws Exception {
    var createResult =
        mockMvc
            .perform(
                post("/api/proposals")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "title": "Audit Update Proposal",
                          "customerId": "%s",
                          "feeModel": "HOURLY"
                        }
                        """
                            .formatted(customerId)))
            .andExpect(status().isCreated())
            .andReturn();

    var proposalId = UUID.fromString(extractIdFromLocation(createResult));

    mockMvc
        .perform(
            put("/api/proposals/{id}", proposalId)
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"title": "Updated Audit Proposal"}
                    """))
        .andExpect(status().isOk());

    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .run(
            () -> {
              var page =
                  auditService.findEvents(
                      new AuditEventFilter(
                          "proposal", proposalId, null, "proposal.updated", null, null),
                      PageRequest.of(0, 10));

              assertThat(page.getTotalElements()).isEqualTo(1);
              var event = page.getContent().getFirst();
              assertThat(event.getEventType()).isEqualTo("proposal.updated");
              assertThat(event.getEntityType()).isEqualTo("proposal");
              assertThat(event.getEntityId()).isEqualTo(proposalId);
              assertThat(event.getDetails()).containsKey("proposal_number");
            });
  }

  @Test
  void deleteProposal_emitsAuditEvent() throws Exception {
    var createResult =
        mockMvc
            .perform(
                post("/api/proposals")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "title": "Delete Me Proposal",
                          "customerId": "%s",
                          "feeModel": "HOURLY"
                        }
                        """
                            .formatted(customerId)))
            .andExpect(status().isCreated())
            .andReturn();

    var proposalId = UUID.fromString(extractIdFromLocation(createResult));

    mockMvc
        .perform(delete("/api/proposals/{id}", proposalId).with(ownerJwt()))
        .andExpect(status().isNoContent());

    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .run(
            () -> {
              var page =
                  auditService.findEvents(
                      new AuditEventFilter(
                          "proposal", proposalId, null, "proposal.deleted", null, null),
                      PageRequest.of(0, 10));

              assertThat(page.getTotalElements()).isEqualTo(1);
              var event = page.getContent().getFirst();
              assertThat(event.getEventType()).isEqualTo("proposal.deleted");
              assertThat(event.getDetails()).containsEntry("title", "Delete Me Proposal");
            });
  }

  @Test
  void sendProposal_emitsAuditEvent() throws Exception {
    String proposalId = createProposalWithContent("Audit Send Proposal");

    mockMvc
        .perform(
            post("/api/proposals/{id}/send", proposalId)
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"portalContactId\": \"%s\"}".formatted(portalContactId)))
        .andExpect(status().isOk());

    var proposalUuid = UUID.fromString(proposalId);

    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .run(
            () -> {
              var page =
                  auditService.findEvents(
                      new AuditEventFilter(
                          "proposal", proposalUuid, null, "proposal.sent", null, null),
                      PageRequest.of(0, 10));

              assertThat(page.getTotalElements()).isEqualTo(1);
              var event = page.getContent().getFirst();
              assertThat(event.getEventType()).isEqualTo("proposal.sent");
              assertThat(event.getDetails()).containsKey("proposal_number");
              assertThat(event.getDetails()).containsEntry("customer_id", customerId);
              assertThat(event.getDetails())
                  .containsEntry("portal_contact_id", portalContactId.toString());
            });
  }

  // --- Helpers ---

  private String createProposalWithContent(String title) throws Exception {
    String contentJson =
        """
        {"type":"doc","content":[{"type":"paragraph","content":[{"type":"text","text":"Proposal content."}]}]}
        """;
    var result =
        mockMvc
            .perform(
                post("/api/proposals")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "title": "%s",
                          "customerId": "%s",
                          "feeModel": "HOURLY",
                          "contentJson": %s
                        }
                        """
                            .formatted(title, customerId, contentJson)))
            .andExpect(status().isCreated())
            .andReturn();
    return extractIdFromLocation(result);
  }

  private String extractIdFromLocation(MvcResult result) {
    String location = result.getResponse().getHeader("Location");
    assert location != null : "Expected Location header to be present";
    return location.substring(location.lastIndexOf('/') + 1);
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
                        {"name": "%s", "email": "%s", "type": "INDIVIDUAL"}
                        """
                            .formatted(name, email)))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.id").toString();
  }

  private UUID createPortalContact(String customerIdStr, String email, String displayName) {
    UUID contactId = UUID.randomUUID();
    jdbcTemplate.update(
        ("INSERT INTO \"%s\".portal_contacts (id, org_id, customer_id, email, display_name,"
                + " role, status, created_at, updated_at) VALUES (?::uuid, ?, ?::uuid, ?, ?,"
                + " 'PRIMARY', 'ACTIVE', now(), now())")
            .formatted(schemaName),
        contactId.toString(),
        ORG_ID,
        customerIdStr,
        email,
        displayName);
    return contactId;
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
        .jwt(j -> j.subject("user_pa_owner").claim("o", Map.of("id", ORG_ID, "rol", "owner")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_OWNER")));
  }

  private void fillPrerequisiteFields(String customerIdStr) {
    jdbcTemplate.update(
        ("UPDATE \"%s\".customers SET custom_fields ="
                + " '{\"address_line1\":\"123 Test St\",\"city\":\"Test City\","
                + "\"country\":\"ZA\",\"tax_number\":\"VAT123\"}'::jsonb WHERE id = ?::uuid")
            .formatted(schemaName),
        customerIdStr);
  }
}
