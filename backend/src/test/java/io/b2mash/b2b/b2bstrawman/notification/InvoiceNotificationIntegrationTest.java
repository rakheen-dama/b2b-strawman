package io.b2mash.b2b.b2bstrawman.notification;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.CustomerProject;
import io.b2mash.b2b.b2bstrawman.customer.CustomerProjectRepository;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.event.InvoiceApprovedEvent;
import io.b2mash.b2b.b2bstrawman.event.InvoicePaidEvent;
import io.b2mash.b2b.b2bstrawman.event.InvoiceVoidedEvent;
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
import io.b2mash.b2b.b2bstrawman.timeentry.TimeEntry;
import io.b2mash.b2b.b2bstrawman.timeentry.TimeEntryRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
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
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Integration tests for invoice domain events and notification delivery. Verifies that invoice
 * lifecycle transitions publish the correct domain events and create notifications for the
 * appropriate recipients.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@RecordApplicationEvents
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InvoiceNotificationIntegrationTest {
  private static final String ORG_ID = "org_inv_notif_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private CustomerProjectRepository customerProjectRepository;
  @Autowired private ProjectRepository projectRepository;
  @Autowired private TaskRepository taskRepository;
  @Autowired private TimeEntryRepository timeEntryRepository;
  @Autowired private NotificationRepository notificationRepository;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private ApplicationEvents events;

  @Autowired
  private io.b2mash.b2b.b2bstrawman.compliance.CustomerLifecycleService customerLifecycleService;

  private String tenantSchema;
  private UUID memberIdOwner;
  private UUID memberIdAdmin;
  private UUID customerId;
  private UUID projectId;
  private UUID taskId;

  @BeforeAll
  void setUp() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Invoice Notif Test Org", null);

    memberIdOwner =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                ORG_ID,
                "user_inv_notif_owner",
                "inv_notif_owner@test.com",
                "Notif Owner",
                "owner"));
    memberIdAdmin =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                ORG_ID,
                "user_inv_notif_admin",
                "inv_notif_admin@test.com",
                "Notif Admin",
                "admin"));

    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

    // Create test data within tenant scope
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
                              "Notif Test Corp", "notif@test.com", memberIdOwner);
                      customer = customerRepository.save(customer);
                      customerId = customer.getId();

                      var project =
                          new Project(
                              "Notif Test Project",
                              "Project for notification tests",
                              memberIdOwner);
                      project = projectRepository.save(project);
                      projectId = project.getId();

                      customerProjectRepository.save(
                          new CustomerProject(customerId, projectId, memberIdOwner));

                      var task =
                          new Task(
                              projectId,
                              "Notif Work",
                              "Notification test task",
                              "MEDIUM",
                              "TASK",
                              null,
                              memberIdOwner);
                      task = taskRepository.save(task);
                      taskId = task.getId();
                    }));
  }

  @Test
  void approveInvoice_publishesInvoiceApprovedEvent() throws Exception {
    String invoiceId = createInvoiceAs(TestJwtFactory.ownerJwt(ORG_ID, "user_inv_notif_owner"));

    events.clear();

    mockMvc
        .perform(
            post("/api/invoices/" + invoiceId + "/approve")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_inv_notif_owner")))
        .andExpect(status().isOk());

    var approvedEvents = events.stream(InvoiceApprovedEvent.class).toList();
    assertThat(approvedEvents).hasSize(1);

    var event = approvedEvents.getFirst();
    assertThat(event.invoiceNumber()).isNotNull();
    assertThat(event.customerName()).isEqualTo("Notif Test Corp");
    assertThat(event.orgId()).isEqualTo(ORG_ID);
    assertThat(event.tenantId()).isNotNull();
  }

  @Test
  void approveInvoice_createsNotificationForCreator() throws Exception {
    // Create as admin, approve as owner
    String invoiceId = createInvoiceAs(TestJwtFactory.adminJwt(ORG_ID, "user_inv_notif_admin"));

    mockMvc
        .perform(
            post("/api/invoices/" + invoiceId + "/approve")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_inv_notif_owner")))
        .andExpect(status().isOk());

    // Admin (creator) should receive INVOICE_APPROVED notification
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () -> {
              var adminNotifs =
                  notificationRepository.findByRecipientMemberId(
                      memberIdAdmin, PageRequest.of(0, 100));
              assertThat(adminNotifs.getContent())
                  .anyMatch(
                      n ->
                          "INVOICE_APPROVED".equals(n.getType())
                              && n.getTitle().contains("has been approved")
                              && n.getTitle().contains("Notif Test Corp"));
            });
  }

  @Test
  void sendInvoice_publishesEvent_andNotifiesAdminsOwners() throws Exception {
    // Create and approve as admin
    String invoiceId = createInvoiceAs(TestJwtFactory.adminJwt(ORG_ID, "user_inv_notif_admin"));

    mockMvc
        .perform(
            post("/api/invoices/" + invoiceId + "/approve")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_inv_notif_owner")))
        .andExpect(status().isOk());

    // Send as admin -- owner should get notification
    mockMvc
        .perform(
            post("/api/invoices/" + invoiceId + "/send")
                .with(TestJwtFactory.adminJwt(ORG_ID, "user_inv_notif_admin")))
        .andExpect(status().isOk());

    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () -> {
              var ownerNotifs =
                  notificationRepository.findByRecipientMemberId(
                      memberIdOwner, PageRequest.of(0, 100));
              assertThat(ownerNotifs.getContent())
                  .anyMatch(
                      n ->
                          "INVOICE_SENT".equals(n.getType())
                              && n.getTitle().contains("has been sent"));
            });
  }

  @Test
  void recordPayment_publishesPaidEvent() throws Exception {
    String invoiceId = createInvoiceAs(TestJwtFactory.ownerJwt(ORG_ID, "user_inv_notif_owner"));

    // Approve -> Send -> Pay
    mockMvc
        .perform(
            post("/api/invoices/" + invoiceId + "/approve")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_inv_notif_owner")))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            post("/api/invoices/" + invoiceId + "/send")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_inv_notif_owner")))
        .andExpect(status().isOk());

    events.clear();

    mockMvc
        .perform(
            post("/api/invoices/" + invoiceId + "/payment")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_inv_notif_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"paymentReference": "EFT-NOTIF-001"}
                    """))
        .andExpect(status().isOk());

    var paidEvents = events.stream(InvoicePaidEvent.class).toList();
    assertThat(paidEvents).hasSize(1);
    assertThat(paidEvents.getFirst().paymentReference()).isEqualTo("EFT-NOTIF-001");
  }

  @Test
  void voidInvoice_publishesVoidedEvent_andNotifiesCreator() throws Exception {
    // Create as admin, approve+void as owner
    String invoiceId = createInvoiceAs(TestJwtFactory.adminJwt(ORG_ID, "user_inv_notif_admin"));

    mockMvc
        .perform(
            post("/api/invoices/" + invoiceId + "/approve")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_inv_notif_owner")))
        .andExpect(status().isOk());

    events.clear();

    mockMvc
        .perform(
            post("/api/invoices/" + invoiceId + "/void")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_inv_notif_owner")))
        .andExpect(status().isOk());

    var voidedEvents = events.stream(InvoiceVoidedEvent.class).toList();
    assertThat(voidedEvents).hasSize(1);

    // Admin (creator) should receive INVOICE_VOIDED notification
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () -> {
              var adminNotifs =
                  notificationRepository.findByRecipientMemberId(
                      memberIdAdmin, PageRequest.of(0, 100));
              assertThat(adminNotifs.getContent())
                  .anyMatch(
                      n ->
                          "INVOICE_VOIDED".equals(n.getType())
                              && n.getTitle().contains("has been voided"));
            });
  }

  // --- Helpers ---

  private String createInvoiceAs(JwtRequestPostProcessor jwt) throws Exception {
    // Each test gets a fresh time entry to avoid double-billing conflicts
    UUID teId = createBillableTimeEntry();

    var result =
        mockMvc
            .perform(
                post("/api/invoices")
                    .with(jwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "customerId": "%s",
                          "currency": "ZAR",
                          "timeEntryIds": ["%s"]
                        }
                        """
                            .formatted(customerId, teId)))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
  }

  private UUID createBillableTimeEntry() {
    var holder = new UUID[1];
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberIdOwner)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var te =
                          new TimeEntry(
                              taskId,
                              memberIdOwner,
                              LocalDate.of(2025, 3, 1),
                              60,
                              true,
                              null,
                              "Notification test entry");
                      te.snapshotBillingRate(new BigDecimal("1500.00"), "ZAR");
                      te = timeEntryRepository.save(te);
                      holder[0] = te.getId();
                    }));
    return holder[0];
  }
}
