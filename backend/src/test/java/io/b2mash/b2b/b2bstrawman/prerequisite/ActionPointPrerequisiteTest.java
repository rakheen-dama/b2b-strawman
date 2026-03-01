package io.b2mash.b2b.b2bstrawman.prerequisite;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.SchemaNameGenerator;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Integration tests verifying that action-point prerequisite checks are correctly wired into
 * InvoiceService (createDraft) and ProposalService (sendProposal). Uses a real Spring context with
 * MockMvc to exercise the full request path including PrerequisiteService structural checks.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ActionPointPrerequisiteTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_action_prereq_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private JdbcTemplate jdbcTemplate;

  private String schemaName;
  private String ownerMemberId;

  /** Customer WITHOUT portal contact or email — structural prerequisites will fail. */
  private String incompleteCustomerId;

  /** Customer WITH portal contact and prerequisite fields — prerequisites will pass. */
  private String completeCustomerId;

  private UUID completeCustomerContactId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Action Prereq Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");
    schemaName = SchemaNameGenerator.generateSchemaName(ORG_ID);

    ownerMemberId = syncMember("user_ap_owner", "ap_owner@test.com", "AP Owner", "owner");

    // Customer missing portal contact AND with blank email — structural prereqs will fail
    incompleteCustomerId = createCustomer("Incomplete Customer", "temp@test.com");
    TestCustomerFactory.fillPrerequisiteFields(jdbcTemplate, schemaName, incompleteCustomerId);
    // Blank email + no portal contact + ACTIVE lifecycle = structural check fails
    jdbcTemplate.update(
        ("UPDATE \"%s\".customers SET email = '', lifecycle_status = 'ACTIVE'"
                + " WHERE id = ?::uuid")
            .formatted(schemaName),
        incompleteCustomerId);

    // Customer with portal contact and email — passes prerequisites
    completeCustomerId = createCustomer("Complete Customer", "complete@test.com");
    TestCustomerFactory.fillPrerequisiteFields(jdbcTemplate, schemaName, completeCustomerId);
    // Set ACTIVE so lifecycle guard passes
    jdbcTemplate.update(
        "UPDATE \"%s\".customers SET lifecycle_status = 'ACTIVE' WHERE id = ?::uuid"
            .formatted(schemaName),
        completeCustomerId);
    completeCustomerContactId =
        createPortalContact(completeCustomerId, "contact@complete-test.com", "Complete Contact");
  }

  // --- Invoice creation prerequisite tests ---

  @Test
  void createInvoiceDraft_customerMissingPrerequisites_returns422() throws Exception {
    mockMvc
        .perform(
            post("/api/invoices")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "customerId": "%s",
                      "currency": "ZAR"
                    }
                    """
                        .formatted(incompleteCustomerId)))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.detail").exists());
  }

  @Test
  void createInvoiceDraft_customerWithPrerequisites_succeeds() throws Exception {
    mockMvc
        .perform(
            post("/api/invoices")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "customerId": "%s",
                      "currency": "ZAR"
                    }
                    """
                        .formatted(completeCustomerId)))
        .andExpect(status().isCreated());
  }

  // --- Proposal send prerequisite tests ---

  @Test
  void sendProposal_customerMissingPrerequisites_returns422() throws Exception {
    // Create a proposal for the incomplete customer (creation doesn't check prerequisites)
    String proposalId = createProposalWithContent(incompleteCustomerId, "Prereq Fail Proposal");

    mockMvc
        .perform(
            post("/api/proposals/{id}/send", proposalId)
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"portalContactId\": \"%s\"}".formatted(UUID.randomUUID())))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.detail").exists());
  }

  @Test
  void sendProposal_customerWithPrerequisites_succeeds() throws Exception {
    String proposalId = createProposalWithContent(completeCustomerId, "Prereq Pass Proposal");

    mockMvc
        .perform(
            post("/api/proposals/{id}/send", proposalId)
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"portalContactId\": \"%s\"}".formatted(completeCustomerContactId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SENT"));
  }

  // --- Helpers ---

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

  private String createProposalWithContent(String customerIdStr, String title) throws Exception {
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
                            .formatted(title, customerIdStr, contentJson)))
            .andExpect(status().isCreated())
            .andReturn();
    String location = result.getResponse().getHeader("Location");
    assert location != null : "Expected Location header";
    return location.substring(location.lastIndexOf('/') + 1);
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
        .jwt(j -> j.subject("user_ap_owner").claim("o", Map.of("id", ORG_ID, "rol", "owner")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_OWNER")));
  }
}
