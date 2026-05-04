package io.b2mash.b2b.b2bstrawman.assistant.specialist;

import static io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory.createActiveCustomerWithPrerequisiteFields;
import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.assistant.invocation.AiSpecialistInvocationRepository;
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
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.task.Task;
import io.b2mash.b2b.b2bstrawman.task.TaskRepository;
import io.b2mash.b2b.b2bstrawman.testutil.TenantTestSupport;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import io.b2mash.b2b.b2bstrawman.timeentry.TimeEntry;
import io.b2mash.b2b.b2bstrawman.timeentry.TimeEntryRepository;
import java.math.BigDecimal;
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
 * Backend integration tests for Epic 512A — Billing specialist propose tools, payload persistence,
 * and capability-gate enforcement on the assistant tool registry.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BillingSpecialistIntegrationTest {

  private static final String ORG_ID = "org_billing_512a_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private CustomerProjectRepository customerProjectRepository;
  @Autowired private ProjectRepository projectRepository;
  @Autowired private TaskRepository taskRepository;
  @Autowired private TimeEntryRepository timeEntryRepository;
  @Autowired private InvoiceService invoiceService;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private AssistantToolRegistry assistantToolRegistry;
  @Autowired private AiSpecialistInvocationRepository invocationRepository;

  @Autowired private ProposeTimeEntryPolishTool proposeTimeEntryPolishTool;
  @Autowired private ProposeInvoiceLineGroupingTool proposeInvoiceLineGroupingTool;

  private String tenantSchema;
  private UUID memberIdOwner;
  private UUID customerId;
  private UUID projectId;
  private UUID timeEntryId;
  private UUID invoiceId;

  @BeforeAll
  void provisionAndSeedData() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Billing 512A Test Org", null);

    var memberIdStr =
        TestMemberHelper.syncMember(
            mockMvc, ORG_ID, "user_b512_owner", "b512_owner@test.com", "B512 Owner", "owner");
    memberIdOwner = UUID.fromString(memberIdStr);

    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

    runInTenantScope(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var customer =
                      createActiveCustomerWithPrerequisiteFields(
                          "B512 Customer", "b512_customer@test.com", memberIdOwner);
                  customer = customerRepository.save(customer);
                  customerId = customer.getId();

                  var project = new Project("B512 Project", "Phase 70 test project", memberIdOwner);
                  project = projectRepository.save(project);
                  projectId = project.getId();

                  customerProjectRepository.save(
                      new CustomerProject(customerId, projectId, memberIdOwner));

                  var task =
                      new Task(
                          projectId,
                          "B512 Task",
                          "task for time entries",
                          "MEDIUM",
                          "TASK",
                          null,
                          memberIdOwner);
                  task = taskRepository.save(task);

                  var entry =
                      new TimeEntry(
                          task.getId(),
                          memberIdOwner,
                          LocalDate.now(),
                          120,
                          true,
                          null,
                          "call w/ J");
                  entry.snapshotBillingRate(new BigDecimal("100.00"), "ZAR");
                  entry = timeEntryRepository.save(entry);
                  timeEntryId = entry.getId();
                }));

    runInTenantScope(
        () -> {
          var request =
              new CreateInvoiceRequest(
                  customerId,
                  "ZAR",
                  List.of(timeEntryId),
                  null,
                  null,
                  null,
                  null,
                  null,
                  null,
                  null,
                  null,
                  null);
          var invoiceResponse = invoiceService.createDraft(request, memberIdOwner);
          invoiceId = invoiceResponse.id();
        });
  }

  // -------------------- 1. ProposeTimeEntryPolish records PENDING_APPROVAL --------------------

  @Test
  void proposeTimeEntryPolishRecordsPendingApprovalInvocation() {
    runInTenantScope(
        () -> {
          var ctx = buildContext(Set.of("AI_ASSISTANT_USE", "INVOICE_EDIT"));
          @SuppressWarnings("unchecked")
          var result =
              (Map<String, Object>)
                  proposeTimeEntryPolishTool.execute(
                      Map.of(
                          "invoiceId",
                          invoiceId.toString(),
                          "edits",
                          List.of(
                              Map.of(
                                  "timeEntryId",
                                  timeEntryId.toString(),
                                  "polishedDescription",
                                  "Telephone attendance"))),
                      ctx);

          assertThat(result).containsKey("invocationId");
          assertThat(result.get("editCount")).isEqualTo(1);

          var invocationId = UUID.fromString((String) result.get("invocationId"));
          var stored = invocationRepository.findById(invocationId).orElseThrow();
          assertThat(stored.getStatus()).isEqualTo(InvocationStatus.PENDING_APPROVAL);
          assertThat(stored.getSpecialistId()).isEqualTo("billing-za");
          assertThat(stored.getContextEntityType()).isEqualTo("invoice");
          assertThat(stored.getContextEntityId()).isEqualTo(invoiceId);
          assertThat(stored.getProposedOutput()).isInstanceOf(BillingPolishPayload.class);

          var payload = (BillingPolishPayload) stored.getProposedOutput();
          assertThat(payload.invoiceId()).isEqualTo(invoiceId);
          assertThat(payload.edits()).hasSize(1);
          var edit = payload.edits().get(0);
          assertThat(edit.timeEntryId()).isEqualTo(timeEntryId);
          assertThat(edit.beforeText()).isEqualTo("call w/ J");
          assertThat(edit.afterText()).isEqualTo("Telephone attendance");
        });
  }

  // -------------------- 2. ProposeInvoiceLineGrouping records grouping --------------------

  @Test
  void proposeInvoiceLineGroupingRecordsGroupingPayload() {
    runInTenantScope(
        () -> {
          var ctx = buildContext(Set.of("AI_ASSISTANT_USE", "INVOICE_EDIT"));
          @SuppressWarnings("unchecked")
          var result =
              (Map<String, Object>)
                  proposeInvoiceLineGroupingTool.execute(
                      Map.of(
                          "invoiceId",
                          invoiceId.toString(),
                          "groups",
                          List.of(
                              Map.of(
                                  "description",
                                  "Attendance upon client",
                                  "hours",
                                  2.0,
                                  "sourceTimeEntryIds",
                                  List.of(timeEntryId.toString())))),
                      ctx);

          assertThat(result).containsKey("invocationId");
          assertThat(result.get("groupCount")).isEqualTo(1);

          var invocationId = UUID.fromString((String) result.get("invocationId"));
          var stored = invocationRepository.findById(invocationId).orElseThrow();
          assertThat(stored.getStatus()).isEqualTo(InvocationStatus.PENDING_APPROVAL);
          assertThat(stored.getProposedOutput()).isInstanceOf(BillingGroupingPayload.class);

          var payload = (BillingGroupingPayload) stored.getProposedOutput();
          assertThat(payload.invoiceId()).isEqualTo(invoiceId);
          assertThat(payload.groups()).hasSize(1);
          var group = payload.groups().get(0);
          assertThat(group.description()).isEqualTo("Attendance upon client");
          assertThat(group.hours()).isEqualByComparingTo(new BigDecimal("2.0"));
          assertThat(group.sourceTimeEntryIds()).containsExactly(timeEntryId);
        });
  }

  // -------------------- 3. Capability gate — INVOICE_EDIT required --------------------

  @Test
  void proposePolishToolExcludedWithoutInvoiceEditCapability() {
    var without = assistantToolRegistry.getToolsForUser(Set.of("AI_ASSISTANT_USE"));
    assertThat(without).noneMatch(t -> "ProposeTimeEntryPolish".equals(t.name()));
    assertThat(without).noneMatch(t -> "ProposeInvoiceLineGrouping".equals(t.name()));

    var withCap = assistantToolRegistry.getToolsForUser(Set.of("AI_ASSISTANT_USE", "INVOICE_EDIT"));
    assertThat(withCap).anyMatch(t -> "ProposeTimeEntryPolish".equals(t.name()));
    assertThat(withCap).anyMatch(t -> "ProposeInvoiceLineGrouping".equals(t.name()));
  }

  // -------------------- 4. Both propose tools require confirmation --------------------

  @Test
  void proposeToolsRequireConfirmation() {
    assertThat(proposeTimeEntryPolishTool.requiresConfirmation()).isTrue();
    assertThat(proposeInvoiceLineGroupingTool.requiresConfirmation()).isTrue();
  }

  // -------------------- helpers --------------------

  private TenantToolContext buildContext(Set<String> capabilities) {
    return new TenantToolContext(tenantSchema, memberIdOwner, "owner", capabilities);
  }

  private void runInTenantScope(Runnable action) {
    TenantTestSupport.runAsActor(
        tenantSchema,
        ORG_ID,
        memberIdOwner,
        "owner",
        Set.of("AI_ASSISTANT_USE", "INVOICE_EDIT", "INVOICING"),
        action);
  }
}
