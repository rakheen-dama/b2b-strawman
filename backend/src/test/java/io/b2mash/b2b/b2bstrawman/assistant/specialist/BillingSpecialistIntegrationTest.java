package io.b2mash.b2b.b2bstrawman.assistant.specialist;

import static io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory.createActiveCustomerWithPrerequisiteFields;
import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.assistant.invocation.AiSpecialistInvocation;
import io.b2mash.b2b.b2bstrawman.assistant.invocation.AiSpecialistInvocationRepository;
import io.b2mash.b2b.b2bstrawman.assistant.invocation.AiSpecialistInvocationService;
import io.b2mash.b2b.b2bstrawman.assistant.invocation.InvocationSource;
import io.b2mash.b2b.b2bstrawman.assistant.invocation.InvocationStatus;
import io.b2mash.b2b.b2bstrawman.assistant.invocation.payload.BillingGroupingPayload;
import io.b2mash.b2b.b2bstrawman.assistant.invocation.payload.BillingPolishPayload;
import io.b2mash.b2b.b2bstrawman.assistant.tool.AssistantToolRegistry;
import io.b2mash.b2b.b2bstrawman.assistant.tool.TenantToolContext;
import io.b2mash.b2b.b2bstrawman.assistant.tool.write.ProposeInvoiceLineGroupingTool;
import io.b2mash.b2b.b2bstrawman.assistant.tool.write.ProposeTimeEntryPolishTool;
import io.b2mash.b2b.b2bstrawman.customer.CustomerProject;
import io.b2mash.b2b.b2bstrawman.customer.CustomerProjectRepository;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceService;
import io.b2mash.b2b.b2bstrawman.invoice.dto.CreateInvoiceRequest;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.task.Task;
import io.b2mash.b2b.b2bstrawman.task.TaskRepository;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import io.b2mash.b2b.b2bstrawman.timeentry.TimeEntry;
import io.b2mash.b2b.b2bstrawman.timeentry.TimeEntryRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Integration tests for the Billing specialist's propose tools. Verifies that invocations are
 * recorded correctly and capability gates work.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BillingSpecialistIntegrationTest {

  private static final String ORG_ID = "org_billing_spec_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private AiSpecialistInvocationService invocationService;
  @Autowired private AiSpecialistInvocationRepository invocationRepository;
  @Autowired private ProposeTimeEntryPolishTool polishTool;
  @Autowired private ProposeInvoiceLineGroupingTool groupingTool;
  @Autowired private AssistantToolRegistry toolRegistry;
  @Autowired private ProjectRepository projectRepository;
  @Autowired private TaskRepository taskRepository;
  @Autowired private TimeEntryRepository timeEntryRepository;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private CustomerProjectRepository customerProjectRepository;
  @Autowired private InvoiceService invoiceService;
  @Autowired private TransactionTemplate transactionTemplate;

  private String tenantSchema;
  private UUID memberId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Billing Spec Test Org", null);
    var memberStr =
        TestMemberHelper.syncMember(
            mockMvc, ORG_ID, "user_billing_owner", "billing@test.com", "Billing Owner", "owner");
    memberId = UUID.fromString(memberStr);
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
  }

  private void runWithCaps(Set<String> caps, Runnable body) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .where(RequestScopes.CAPABILITIES, caps)
        .run(body);
  }

  @Test
  void proposeTimeEntryPolish_recordsInvocationWithPendingStatus() {
    var invoiceId = UUID.randomUUID();
    var timeEntryId = UUID.randomUUID();

    runWithCaps(
        Set.of("AI_ASSISTANT_USE", "INVOICING"),
        () -> {
          var ctx = new TenantToolContext(tenantSchema, memberId, "owner", Set.of("INVOICING"));
          var input =
              Map.<String, Object>of(
                  "invoiceId",
                  invoiceId.toString(),
                  "edits",
                  List.of(
                      Map.of(
                          "timeEntryId",
                          timeEntryId.toString(),
                          "polishedDescription",
                          "Telephone attendance upon client J")));

          @SuppressWarnings("unchecked")
          var result = (Map<String, Object>) polishTool.execute(input, ctx);

          assertThat(result).containsKey("invocationId");
          assertThat(result.get("editCount")).isEqualTo(1);

          var invId = UUID.fromString((String) result.get("invocationId"));
          var inv = invocationRepository.findById(invId).orElseThrow();
          assertThat(inv.getStatus()).isEqualTo(InvocationStatus.PENDING_APPROVAL);
          assertThat(inv.getProposedOutput()).isInstanceOf(BillingPolishPayload.class);

          var payload = (BillingPolishPayload) inv.getProposedOutput();
          assertThat(payload.invoiceId()).isEqualTo(invoiceId);
          assertThat(payload.edits()).hasSize(1);
          assertThat(payload.edits().getFirst().afterText())
              .isEqualTo("Telephone attendance upon client J");
        });
  }

  @Test
  void proposeInvoiceLineGrouping_recordsGroupingPayload() {
    var invoiceId = UUID.randomUUID();
    var te1 = UUID.randomUUID();
    var te2 = UUID.randomUUID();

    runWithCaps(
        Set.of("AI_ASSISTANT_USE", "INVOICING"),
        () -> {
          var ctx = new TenantToolContext(tenantSchema, memberId, "owner", Set.of("INVOICING"));
          var input =
              Map.<String, Object>of(
                  "invoiceId",
                  invoiceId.toString(),
                  "groups",
                  List.of(
                      Map.of(
                          "description",
                          "Preparation for trial",
                          "hours",
                          3.5,
                          "sourceTimeEntryIds",
                          List.of(te1.toString(), te2.toString()))));

          @SuppressWarnings("unchecked")
          var result = (Map<String, Object>) groupingTool.execute(input, ctx);

          assertThat(result).containsKey("invocationId");
          assertThat(result.get("groupCount")).isEqualTo(1);

          var invId = UUID.fromString((String) result.get("invocationId"));
          var inv = invocationRepository.findById(invId).orElseThrow();
          assertThat(inv.getStatus()).isEqualTo(InvocationStatus.PENDING_APPROVAL);
          assertThat(inv.getProposedOutput()).isInstanceOf(BillingGroupingPayload.class);

          var payload = (BillingGroupingPayload) inv.getProposedOutput();
          assertThat(payload.invoiceId()).isEqualTo(invoiceId);
          assertThat(payload.groups()).hasSize(1);
          assertThat(payload.groups().getFirst().sourceTimeEntryIds()).containsExactly(te1, te2);
        });
  }

  @Test
  void approvePolish_appliesDescriptionUpdate_toBilledTimeEntry() {
    // This test exercises the CRITICAL apply path: time entries with invoiceId set
    // (i.e. already on a draft invoice) must have their descriptions updated successfully.
    // Before the fix, TimeEntryService.updateTimeEntry would throw ResourceConflictException.

    // Holder arrays for IDs that cross transaction boundaries
    final UUID[] timeEntryIdHolder = new UUID[1];
    final UUID[] invoiceIdHolder = new UUID[1];
    final UUID[] invocationIdHolder = new UUID[1];

    // Step 1: Create customer, project, task, time entry, and invoice (in a transaction)
    runWithCaps(
        Set.of("AI_ASSISTANT_USE", "INVOICING"),
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var customer =
                      createActiveCustomerWithPrerequisiteFields(
                          "Polish Test Customer", "polish_customer@test.com", memberId);
                  customer = customerRepository.save(customer);

                  var project = new Project("Polish Test Project", "Test", memberId);
                  project = projectRepository.save(project);

                  customerProjectRepository.save(
                      new CustomerProject(customer.getId(), project.getId(), memberId));

                  var task =
                      new Task(
                          project.getId(),
                          "Polish Test Task",
                          "A task",
                          "MEDIUM",
                          "TASK",
                          null,
                          memberId);
                  task = taskRepository.save(task);

                  var timeEntry =
                      new TimeEntry(
                          task.getId(),
                          memberId,
                          LocalDate.now(),
                          60,
                          true,
                          null,
                          "Attendnce upn clint re: mtter 123");
                  timeEntry = timeEntryRepository.save(timeEntry);
                  timeEntryIdHolder[0] = timeEntry.getId();

                  // Create a draft invoice that links this time entry (sets invoiceId on the entry)
                  var invoiceResponse =
                      invoiceService.createDraft(
                          new CreateInvoiceRequest(
                              customer.getId(),
                              "USD",
                              List.of(timeEntry.getId()),
                              null,
                              null,
                              null,
                              null,
                              null,
                              null,
                              null,
                              null,
                              null),
                          memberId);
                  invoiceIdHolder[0] = invoiceResponse.id();
                }));

    // Step 2: Verify the time entry now has an invoiceId (the billed guard condition)
    runWithCaps(
        Set.of("AI_ASSISTANT_USE", "INVOICING"),
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var entry = timeEntryRepository.findById(timeEntryIdHolder[0]).orElseThrow();
                  assertThat(entry.getInvoiceId())
                      .as("Time entry must be linked to invoice (billed guard condition)")
                      .isEqualTo(invoiceIdHolder[0]);

                  // Create an invocation in PENDING_APPROVAL state with polish payload
                  var inv =
                      new AiSpecialistInvocation(
                          "billing",
                          InvocationSource.MEMBER,
                          memberId,
                          null,
                          "invoice",
                          invoiceIdHolder[0],
                          "v1");
                  var payload =
                      new BillingPolishPayload(
                          invoiceIdHolder[0],
                          List.of(
                              new BillingPolishPayload.PolishEdit(
                                  timeEntryIdHolder[0],
                                  "Attendnce upn clint re: mtter 123",
                                  "Attendance upon client regarding matter 123")));
                  inv.recordProposal(payload);
                  inv.markPendingApproval();
                  inv = invocationRepository.save(inv);
                  invocationIdHolder[0] = inv.getId();
                }));

    // Step 3: Approve — this calls the applier which must NOT throw despite invoiceId being set
    runWithCaps(
        Set.of("AI_ASSISTANT_USE", "INVOICING"),
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var result = invocationService.approve(invocationIdHolder[0], null);
                  assertThat(result.status()).isEqualTo(InvocationStatus.APPROVED);

                  // Verify the description was actually updated
                  var updated = timeEntryRepository.findById(timeEntryIdHolder[0]).orElseThrow();
                  assertThat(updated.getDescription())
                      .isEqualTo("Attendance upon client regarding matter 123");
                  // Verify the invoiceId is still set (not cleared)
                  assertThat(updated.getInvoiceId()).isEqualTo(invoiceIdHolder[0]);
                }));
  }

  @Test
  void capabilityGate_memberWithoutInvoicing_cannotUsePolishTool() {
    // Verify the tools declare INVOICING as a required capability
    assertThat(polishTool.requiredCapabilities()).contains("INVOICING");
    assertThat(groupingTool.requiredCapabilities()).contains("INVOICING");

    // Exercise the actual registry filtering: a member with AI_ASSISTANT_USE but WITHOUT
    // INVOICING should not see billing tools in the filtered tool list.
    var billingToolIds =
        List.of("ProposeTimeEntryPolish", "ProposeInvoiceLineGrouping", "create_invoice_draft");
    var capsWithoutInvoicing = Set.of("AI_ASSISTANT_USE");

    var filtered = toolRegistry.filterBy(billingToolIds, capsWithoutInvoicing);
    var filteredNames = filtered.stream().map(t -> t.name()).toList();
    assertThat(filteredNames).doesNotContain("ProposeTimeEntryPolish");
    assertThat(filteredNames).doesNotContain("ProposeInvoiceLineGrouping");

    // Verify WITH INVOICING they ARE included
    var capsWithInvoicing = Set.of("AI_ASSISTANT_USE", "INVOICING");
    var filteredWithCap = toolRegistry.filterBy(billingToolIds, capsWithInvoicing);
    var filteredWithCapNames = filteredWithCap.stream().map(t -> t.name()).toList();
    assertThat(filteredWithCapNames).contains("ProposeTimeEntryPolish");
    assertThat(filteredWithCapNames).contains("ProposeInvoiceLineGrouping");
  }
}
