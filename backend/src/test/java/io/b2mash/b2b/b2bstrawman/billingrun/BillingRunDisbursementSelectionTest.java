package io.b2mash.b2b.b2bstrawman.billingrun;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.CustomerProject;
import io.b2mash.b2b.b2bstrawman.customer.CustomerProjectRepository;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.task.Task;
import io.b2mash.b2b.b2bstrawman.task.TaskRepository;
import io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TestModuleHelper;
import io.b2mash.b2b.b2bstrawman.timeentry.TimeEntry;
import io.b2mash.b2b.b2bstrawman.timeentry.TimeEntryRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.DisbursementCategory;
import io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.DisbursementPaymentSource;
import io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.DisbursementRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.LegalDisbursement;
import io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.VatTreatment;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * OBS-2104c — verifies that approved-and-unbilled legal disbursements surface as cherry-pickable
 * lines in wizard step 3 via {@code GET
 * /api/billing-runs/{id}/items/{itemId}/unbilled-disbursements}, persist as {@code
 * BillingRunEntrySelection} rows of type {@code LEGAL_DISBURSEMENT}, and have their amounts roll
 * into the recalculated unbilledExpenseAmount on toggle. Pre-fix the wizard silently dropped them
 * at step 3 — the user had to use the "Add Disbursements" modal in step 4.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class BillingRunDisbursementSelectionTest {

  private static final String ORG_ID = "org_billing_disbursement_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private CustomerProjectRepository customerProjectRepository;
  @Autowired private ProjectRepository projectRepository;
  @Autowired private TaskRepository taskRepository;
  @Autowired private TimeEntryRepository timeEntryRepository;
  @Autowired private DisbursementRepository disbursementRepository;
  @Autowired private TransactionTemplate transactionTemplate;

  private String tenantSchema;
  private UUID memberIdOwner;
  private UUID customerId;
  private UUID disbursementId;
  private String billingRunId;
  private String itemId;

  @BeforeAll
  void setUp() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Disbursement Selection Test Org", null);

    memberIdOwner =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, "user_disb_owner", "disb_owner@test.com", "Disb Owner", "owner"));

    TestModuleHelper.enableModules(mockMvc, ORG_ID, "user_disb_owner", "bulk_billing");

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
                              "Sipho RAF Matter", "sipho-raf@test.com", memberIdOwner);
                      customer = customerRepository.save(customer);
                      customerId = customer.getId();

                      var project = new Project("RAF-2026-001", "Sipho's RAF claim", memberIdOwner);
                      project.setCustomerId(customerId);
                      project = projectRepository.save(project);

                      customerProjectRepository.save(
                          new CustomerProject(customerId, project.getId(), memberIdOwner));

                      // Single time entry so step 2 discovery picks the customer up via the
                      // time-agg CTE; the fix-spec is about the disbursement column alongside
                      // it, not about isolated disbursement-only customers.
                      var task =
                          new Task(
                              project.getId(),
                              "Initial RAF assessment",
                              null,
                              "MEDIUM",
                              "TASK",
                              null,
                              memberIdOwner);
                      task = taskRepository.save(task);

                      var te =
                          new TimeEntry(
                              task.getId(),
                              memberIdOwner,
                              LocalDate.of(2026, 4, 10),
                              120,
                              true,
                              null,
                              "Assessment");
                      te.snapshotBillingRate(new BigDecimal("1800.00"), "ZAR");
                      timeEntryRepository.save(te);

                      // Approved-and-unbilled sheriff fee disbursement R 1,250 (no VAT — zero-
                      // rated pass-through).
                      var d =
                          new LegalDisbursement(
                              project.getId(),
                              customerId,
                              DisbursementCategory.SHERIFF_FEES.name(),
                              "Sheriff service of summons on RAF",
                              new BigDecimal("1250.00"),
                              VatTreatment.ZERO_RATED_PASS_THROUGH.name(),
                              BigDecimal.ZERO,
                              DisbursementPaymentSource.OFFICE_ACCOUNT.name(),
                              null,
                              LocalDate.of(2026, 4, 15),
                              "Sheriff Sandton",
                              "REF-SAND-001",
                              null,
                              memberIdOwner);
                      d.submitForApproval();
                      d.approve(memberIdOwner, "ok");
                      d = disbursementRepository.saveAndFlush(d);
                      disbursementId = d.getId();
                    }));

    billingRunId = createBillingRun("Disbursement Run", "2026-04-01", "2026-04-30", "ZAR", true);

    var previewResult =
        mockMvc
            .perform(
                post("/api/billing-runs/" + billingRunId + "/preview")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_disb_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}"))
            .andExpect(status().isOk())
            .andReturn();

    itemId = JsonPath.read(previewResult.getResponse().getContentAsString(), "$.items[0].id");
  }

  /**
   * GET /unbilled-disbursements returns the approved sheriff fee with the right shape — proves the
   * disbursement was persisted as a {@code BillingRunEntrySelection} of type {@code
   * LEGAL_DISBURSEMENT} during preview load and that the new endpoint resolves it back via the
   * legal-vertical {@code DisbursementRepository}.
   */
  @Test
  @Order(1)
  void getUnbilledDisbursements_returnsApprovedDisbursement() throws Exception {
    mockMvc
        .perform(
            get("/api/billing-runs/"
                    + billingRunId
                    + "/items/"
                    + itemId
                    + "/unbilled-disbursements")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_disb_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].id").value(disbursementId.toString()))
        .andExpect(jsonPath("$[0].description").value("Sheriff service of summons on RAF"))
        .andExpect(jsonPath("$[0].category").value(DisbursementCategory.SHERIFF_FEES.name()))
        .andExpect(jsonPath("$[0].supplierName").value("Sheriff Sandton"))
        .andExpect(jsonPath("$[0].amount").value(1250.00))
        .andExpect(jsonPath("$[0].vatAmount").value(0))
        .andExpect(jsonPath("$[0].billableAmount").value(1250.00));
  }

  /**
   * Toggling the disbursement off via {@code PUT /selections} with {@code
   * entryType=LEGAL_DISBURSEMENT} recalculates the item totals and removes the disbursement amount
   * from {@code unbilledExpenseAmount}. Pre-fix this code path didn't exist (no enum value, no
   * persistence path).
   */
  @Test
  @Order(2)
  void updateSelection_excludeDisbursement_recalculatesTotals() throws Exception {
    // Sanity check: before toggling, the item carries the disbursement amount under the expense
    // aggregate (per the OBS-2104b CTE projection). Verify via the read endpoint to avoid
    // depending on the preview body shape directly.
    var itemResult =
        mockMvc
            .perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                        "/api/billing-runs/" + billingRunId + "/items/" + itemId)
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_disb_owner")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.unbilledExpenseCount").value(1))
            .andExpect(jsonPath("$.unbilledExpenseAmount").value(1250.00))
            .andReturn();
    // (itemResult kept for readability; not asserted further here.)
    org.junit.jupiter.api.Assertions.assertNotNull(itemResult);

    mockMvc
        .perform(
            put("/api/billing-runs/" + billingRunId + "/items/" + itemId + "/selections")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_disb_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "selections": [
                        {"entryType": "LEGAL_DISBURSEMENT", "entryId": "%s", "included": false}
                      ]
                    }
                    """
                        .formatted(disbursementId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.unbilledExpenseCount").value(0))
        .andExpect(jsonPath("$.unbilledExpenseAmount").value(0));
  }

  // --- Helpers ---

  private String createBillingRun(
      String name, String periodFrom, String periodTo, String currency, boolean includeExpenses)
      throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/billing-runs")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_disb_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "name": "%s",
                          "periodFrom": "%s",
                          "periodTo": "%s",
                          "currency": "%s",
                          "includeExpenses": %s,
                          "includeRetainers": false
                        }
                        """
                            .formatted(name, periodFrom, periodTo, currency, includeExpenses)))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
  }
}
