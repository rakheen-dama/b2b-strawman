package io.b2mash.b2b.b2bstrawman.invoice;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
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
class InvoiceCustomFieldIntegrationTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_inv_cf_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private TransactionTemplate transactionTemplate;

  private String tenantSchema;
  private UUID memberIdOwner;
  private UUID customerId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Invoice CF Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");

    memberIdOwner =
        UUID.fromString(
            syncMember("user_inv_cf_owner", "inv_cf_owner@test.com", "CF Owner", "owner"));

    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

    // Create an ACTIVE customer in tenant scope
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberIdOwner)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var customer =
                          TestCustomerFactory.createActiveCustomer(
                              "CF Invoice Corp", "cf_inv_corp@test.com", memberIdOwner);
                      customer = customerRepository.save(customer);
                      customerId = customer.getId();
                    }));

    // Create field definitions for INVOICE entity type
    createFieldDefinition("Reference Number", "reference_number", "TEXT", "INVOICE");
    createFieldDefinition("Priority", "priority", "BOOLEAN", "INVOICE");
    createFieldDefinition("External URL", "external_url", "URL", "INVOICE");
  }

  @Test
  void shouldUpdateInvoiceCustomFields() throws Exception {
    String invoiceId = createDraftInvoice();

    // Update custom fields
    mockMvc
        .perform(
            put("/api/invoices/" + invoiceId + "/custom-fields")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "customFields": {
                        "reference_number": "REF-001",
                        "priority": true
                      }
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.customFields.reference_number").value("REF-001"))
        .andExpect(jsonPath("$.customFields.priority").value(true));

    // Verify GET returns custom fields
    mockMvc
        .perform(get("/api/invoices/" + invoiceId).with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.customFields.reference_number").value("REF-001"))
        .andExpect(jsonPath("$.customFields.priority").value(true));
  }

  @Test
  void shouldReturn400ForInvalidCustomFieldValue() throws Exception {
    String invoiceId = createDraftInvoice();

    mockMvc
        .perform(
            put("/api/invoices/" + invoiceId + "/custom-fields")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "customFields": {
                        "external_url": "not-a-url"
                      }
                    }
                    """))
        .andExpect(status().isBadRequest());
  }

  @Test
  void shouldApplyFieldGroupsToInvoice() throws Exception {
    // Create a field group for INVOICE entity type
    var groupResult =
        mockMvc
            .perform(
                post("/api/field-groups")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "entityType": "INVOICE",
                          "name": "Invoice CF Test Group",
                          "sortOrder": 0
                        }
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    String groupId = JsonPath.read(groupResult.getResponse().getContentAsString(), "$.id");

    String invoiceId = createDraftInvoice();

    mockMvc
        .perform(
            put("/api/invoices/" + invoiceId + "/field-groups")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"appliedFieldGroups": ["%s"]}
                    """
                        .formatted(groupId)))
        .andExpect(status().isOk());

    // Verify GET shows appliedFieldGroups
    mockMvc
        .perform(get("/api/invoices/" + invoiceId).with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.appliedFieldGroups[0]").value(groupId));
  }

  @Test
  void shouldAutoApplyFieldGroupsOnInvoiceCreation() throws Exception {
    // Create an auto-apply field group for INVOICE entity type
    var groupResult =
        mockMvc
            .perform(
                post("/api/field-groups")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "entityType": "INVOICE",
                          "name": "Invoice Auto-Apply Group",
                          "sortOrder": 0,
                          "autoApply": true
                        }
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    String groupId = JsonPath.read(groupResult.getResponse().getContentAsString(), "$.id");

    // Create invoice -- should auto-apply
    String invoiceId = createDraftInvoice();

    mockMvc
        .perform(get("/api/invoices/" + invoiceId).with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.appliedFieldGroups").isArray())
        .andExpect(jsonPath("$.appliedFieldGroups[?(@=='" + groupId + "')]").exists());
  }

  @Test
  void shouldRejectCustomFieldUpdateOnNonDraftInvoice() throws Exception {
    String invoiceId = createDraftInvoice();

    // Add a manual line item so invoice can be approved
    mockMvc
        .perform(
            post("/api/invoices/" + invoiceId + "/lines")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "description": "Test line item",
                      "quantity": 1,
                      "unitPrice": 100.00,
                      "sortOrder": 0
                    }
                    """))
        .andExpect(status().isCreated());

    // Approve the invoice
    mockMvc
        .perform(post("/api/invoices/" + invoiceId + "/approve").with(ownerJwt()))
        .andExpect(status().isOk());

    // Try to update custom fields on approved invoice
    mockMvc
        .perform(
            put("/api/invoices/" + invoiceId + "/custom-fields")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "customFields": {
                        "reference_number": "REF-002"
                      }
                    }
                    """))
        .andExpect(status().isConflict());
  }

  @Test
  void shouldRejectFieldGroupUpdateOnNonDraftInvoice() throws Exception {
    String invoiceId = createDraftInvoice();

    // Add a manual line item so invoice can be approved
    mockMvc
        .perform(
            post("/api/invoices/" + invoiceId + "/lines")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "description": "Test line item",
                      "quantity": 1,
                      "unitPrice": 100.00,
                      "sortOrder": 0
                    }
                    """))
        .andExpect(status().isCreated());

    // Approve the invoice
    mockMvc
        .perform(post("/api/invoices/" + invoiceId + "/approve").with(ownerJwt()))
        .andExpect(status().isOk());

    // Create a field group
    var groupResult =
        mockMvc
            .perform(
                post("/api/field-groups")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "entityType": "INVOICE",
                          "name": "Non-Draft Group",
                          "sortOrder": 0
                        }
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    String groupId = JsonPath.read(groupResult.getResponse().getContentAsString(), "$.id");

    // Try to apply field groups on approved invoice
    mockMvc
        .perform(
            put("/api/invoices/" + invoiceId + "/field-groups")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"appliedFieldGroups": ["%s"]}
                    """
                        .formatted(groupId)))
        .andExpect(status().isConflict());
  }

  // --- Helpers ---

  private String createDraftInvoice() throws Exception {
    var result =
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
                            .formatted(customerId)))
            .andExpect(status().isCreated())
            .andReturn();

    return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
  }

  private JwtRequestPostProcessor ownerJwt() {
    return jwt()
        .jwt(j -> j.subject("user_inv_cf_owner").claim("o", Map.of("id", ORG_ID, "rol", "owner")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_OWNER")));
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
                          "clerkOrgId": "%s",
                          "clerkUserId": "%s",
                          "email": "%s",
                          "name": "%s",
                          "avatarUrl": null,
                          "orgRole": "%s"
                        }
                        """
                            .formatted(ORG_ID, clerkUserId, email, name, orgRole)))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.memberId");
  }

  private void createFieldDefinition(String name, String slug, String fieldType, String entityType)
      throws Exception {
    mockMvc
        .perform(
            post("/api/field-definitions")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "entityType": "%s",
                      "name": "%s",
                      "fieldType": "%s",
                      "required": false,
                      "sortOrder": 0
                    }
                    """
                        .formatted(entityType, name, fieldType)))
        .andExpect(status().isCreated());
  }
}
