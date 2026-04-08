package io.b2mash.b2b.b2bstrawman.invoice;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InvoiceNewFieldsIntegrationTest {

  private static final String ORG_ID = "org_inv_new_fields";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private CustomerProjectRepository customerProjectRepository;
  @Autowired private ProjectRepository projectRepository;
  @Autowired private TransactionTemplate transactionTemplate;

  private String tenantSchema;
  private UUID memberIdOwner;
  private UUID customerId;

  @BeforeAll
  void setUp() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Invoice New Fields Org", null);

    memberIdOwner =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, "user_inf_owner", "inf_owner@test.com", "INF Owner", "owner"));

    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberIdOwner)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var customer =
                          TestCustomerFactory.createActiveCustomerWithPrerequisiteFields(
                              "INF Test Corp", "inftest@test.com", memberIdOwner);
                      customer = customerRepository.save(customer);
                      customerId = customer.getId();

                      var project =
                          new Project("INF Test Project", "Project for inv tests", memberIdOwner);
                      project = projectRepository.save(project);

                      customerProjectRepository.save(
                          new CustomerProject(customerId, project.getId(), memberIdOwner));
                    }));
  }

  @Test
  void createInvoiceWithTaxTypeAndPoNumberReturnsNewFields() throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/invoices")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_inf_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "customerId": "%s",
                          "currency": "ZAR",
                          "poNumber": "PO-12345",
                          "taxType": "VAT"
                        }
                        """
                            .formatted(customerId)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.poNumber").value("PO-12345"))
            .andExpect(jsonPath("$.taxType").value("VAT"))
            .andReturn();
  }

  @Test
  void createInvoiceWithBillingPeriodDatesReturnsLocalDates() throws Exception {
    mockMvc
        .perform(
            post("/api/invoices")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_inf_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "customerId": "%s",
                      "currency": "ZAR",
                      "billingPeriodStart": "2026-01-01",
                      "billingPeriodEnd": "2026-01-31"
                    }
                    """
                        .formatted(customerId)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.billingPeriodStart").value("2026-01-01"))
        .andExpect(jsonPath("$.billingPeriodEnd").value("2026-01-31"));
  }

  @Test
  void createInvoiceWithoutNewFieldsReturnsNullForNewFields() throws Exception {
    mockMvc
        .perform(
            post("/api/invoices")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_inf_owner"))
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
        .andExpect(jsonPath("$.poNumber").doesNotExist())
        .andExpect(jsonPath("$.taxType").doesNotExist())
        .andExpect(jsonPath("$.billingPeriodStart").doesNotExist())
        .andExpect(jsonPath("$.billingPeriodEnd").doesNotExist());
  }
}
