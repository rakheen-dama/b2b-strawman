package io.b2mash.b2b.b2bstrawman.prerequisite;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

  /**
   * Customer missing BOTH custom fields AND structural data — produces combined MISSING_FIELD +
   * STRUCTURAL violations.
   */
  private String combinedViolationsCustomerId;

  /**
   * Customer used in the combined flow test (244.13) — starts incomplete, gets fixed during the
   * test.
   */
  private String flowCustomerId;

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

    // Customer missing BOTH custom fields AND structural data (no email, no portal contact)
    combinedViolationsCustomerId =
        createCustomer("Combined Violations Customer", "temp-combined@test.com");
    // Do NOT call fillPrerequisiteFields — custom fields remain empty
    // Blank-ish email (whitespace) + no portal contact = structural violation
    // Use single space — triggers isBlank() structural violation while avoiding duplicate key with
    // incompleteCustomerId
    jdbcTemplate.update(
        ("UPDATE \"%s\".customers SET email = ' ', custom_fields = '{}'::jsonb,"
                + " lifecycle_status = 'ACTIVE' WHERE id = ?::uuid")
            .formatted(schemaName),
        combinedViolationsCustomerId);

    // Customer for combined flow test — starts without prerequisites
    flowCustomerId = createCustomer("Flow Test Customer", "temp-flow@test.com");
    // Use double space — triggers isBlank() structural violation while remaining unique
    jdbcTemplate.update(
        ("UPDATE \"%s\".customers SET email = '  ', custom_fields = '{}'::jsonb,"
                + " lifecycle_status = 'ACTIVE' WHERE id = ?::uuid")
            .formatted(schemaName),
        flowCustomerId);
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

  // --- 244.8: Structural violation detail tests ---

  @Test
  void structuralViolation_containsResolutionText() throws Exception {
    mockMvc
        .perform(
            post("/api/invoices")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"customerId": "%s", "currency": "ZAR"}
                    """
                        .formatted(incompleteCustomerId)))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.violations").isArray())
        .andExpect(jsonPath("$.violations[?(@.code == 'STRUCTURAL')].resolution").isNotEmpty())
        .andExpect(
            jsonPath(
                "$.violations[?(@.code == 'STRUCTURAL')].resolution",
                everyItem(is(not(emptyString())))));
  }

  @Test
  void structuralViolation_fieldSlugIsEmpty() throws Exception {
    mockMvc
        .perform(
            post("/api/invoices")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"customerId": "%s", "currency": "ZAR"}
                    """
                        .formatted(incompleteCustomerId)))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.violations[?(@.code == 'STRUCTURAL')].fieldSlug").isNotEmpty())
        .andExpect(
            jsonPath(
                "$.violations[?(@.code == 'STRUCTURAL')].fieldSlug", everyItem(is(emptyString()))));
  }

  @Test
  void combinedViolations_customFieldAndStructural_bothReturned() throws Exception {
    mockMvc
        .perform(
            post("/api/invoices")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"customerId": "%s", "currency": "ZAR"}
                    """
                        .formatted(combinedViolationsCustomerId)))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.violations").isArray())
        .andExpect(jsonPath("$.violations[?(@.code == 'MISSING_FIELD')]").isNotEmpty())
        .andExpect(jsonPath("$.violations[?(@.code == 'STRUCTURAL')]").isNotEmpty());
  }

  // --- 244.9: Cross-domain 422 response format tests ---

  @Test
  void response422_containsContextAndViolationsArray() throws Exception {
    mockMvc
        .perform(
            post("/api/invoices")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"customerId": "%s", "currency": "ZAR"}
                    """
                        .formatted(incompleteCustomerId)))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.title").value("Prerequisites not met"))
        .andExpect(jsonPath("$.detail").isNotEmpty())
        .andExpect(jsonPath("$.violations").isArray())
        .andExpect(jsonPath("$.violations[0].code").exists())
        .andExpect(jsonPath("$.violations[0].message").exists())
        .andExpect(jsonPath("$.violations[0].entityType").exists())
        .andExpect(jsonPath("$.violations[0].entityId").exists())
        .andExpect(jsonPath("$.violations[0].fieldSlug").exists())
        .andExpect(jsonPath("$.violations[0].resolution").exists());
  }

  @Test
  void response422_violationCodesAreConsistent() throws Exception {
    // Invoice 422 — structural violation (incomplete customer has custom fields but no
    // email/contact)
    var invoiceResult =
        mockMvc
            .perform(
                post("/api/invoices")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"customerId": "%s", "currency": "ZAR"}
                        """
                            .formatted(incompleteCustomerId)))
            .andExpect(status().isUnprocessableEntity())
            .andReturn();

    // Proposal 422 — structural violation (incomplete customer has no portal contact)
    String proposalId =
        createProposalWithContent(incompleteCustomerId, "Code Consistency Proposal");
    var proposalResult =
        mockMvc
            .perform(
                post("/api/proposals/{id}/send", proposalId)
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"portalContactId\": \"%s\"}".formatted(UUID.randomUUID())))
            .andExpect(status().isUnprocessableEntity())
            .andReturn();

    // Both should use "STRUCTURAL" code for structural violations
    List<String> invoiceCodes =
        JsonPath.read(invoiceResult.getResponse().getContentAsString(), "$.violations[*].code");
    List<String> proposalCodes =
        JsonPath.read(proposalResult.getResponse().getContentAsString(), "$.violations[*].code");

    List<String> validCodes = List.of("MISSING_FIELD", "STRUCTURAL");
    for (String code : invoiceCodes) {
      assertThat(
          "Invoice violation code '%s' not in valid set".formatted(code),
          validCodes,
          hasItem(code));
    }
    for (String code : proposalCodes) {
      assertThat(
          "Proposal violation code '%s' not in valid set".formatted(code),
          validCodes,
          hasItem(code));
    }

    // Both domains use STRUCTURAL for structural issues
    assertTrue(
        invoiceCodes.contains("STRUCTURAL"), "Invoice 422 should contain STRUCTURAL violation");
    assertTrue(
        proposalCodes.contains("STRUCTURAL"), "Proposal 422 should contain STRUCTURAL violation");
  }

  // --- 244.13: Combined prerequisite flow test ---

  @Test
  void prerequisiteCheckThenAction_fillFieldsAndRetry_succeeds() throws Exception {
    // Step 1: Attempt invoice creation — should fail with 422
    mockMvc
        .perform(
            post("/api/invoices")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"customerId": "%s", "currency": "ZAR"}
                    """
                        .formatted(flowCustomerId)))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.violations").isArray())
        .andExpect(jsonPath("$.violations", hasSize(greaterThan(0))));

    // Step 2: Check prerequisite endpoint — should report not passed
    mockMvc
        .perform(
            get("/api/prerequisites/check")
                .with(ownerJwt())
                .param("context", "INVOICE_GENERATION")
                .param("entityType", "CUSTOMER")
                .param("entityId", flowCustomerId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.passed").value(false));

    // Step 3: Fill custom fields via SQL
    TestCustomerFactory.fillPrerequisiteFields(jdbcTemplate, schemaName, flowCustomerId);

    // Step 4: Add a portal contact (satisfies structural requirement)
    createPortalContact(flowCustomerId, "flow-contact@test.com", "Flow Contact");

    // Step 5: Check prerequisite endpoint — should now pass
    mockMvc
        .perform(
            get("/api/prerequisites/check")
                .with(ownerJwt())
                .param("context", "INVOICE_GENERATION")
                .param("entityType", "CUSTOMER")
                .param("entityId", flowCustomerId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.passed").value(true));

    // Step 6: Retry invoice creation — should succeed
    mockMvc
        .perform(
            post("/api/invoices")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"customerId": "%s", "currency": "ZAR"}
                    """
                        .formatted(flowCustomerId)))
        .andExpect(status().isCreated());
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
    assertNotNull(location, "Expected Location header");
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
