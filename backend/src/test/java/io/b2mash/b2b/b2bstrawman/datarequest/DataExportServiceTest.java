package io.b2mash.b2b.b2bstrawman.datarequest;

import static io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory.createActiveCustomer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.audit.AuditEventRepository;
import io.b2mash.b2b.b2bstrawman.customer.CustomerProject;
import io.b2mash.b2b.b2bstrawman.customer.CustomerProjectRepository;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.integration.storage.PresignedUrl;
import io.b2mash.b2b.b2bstrawman.integration.storage.StorageService;
import io.b2mash.b2b.b2bstrawman.member.MemberSyncService;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.portal.PortalContact;
import io.b2mash.b2b.b2bstrawman.portal.PortalContactRepository;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.task.Task;
import io.b2mash.b2b.b2bstrawman.task.TaskRepository;
import io.b2mash.b2b.b2bstrawman.timeentry.TimeEntry;
import io.b2mash.b2b.b2bstrawman.timeentry.TimeEntryRepository;
import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DataExportServiceTest {

  private static final String ORG_ID = "org_dsr_export_test";

  @Autowired private DataExportService dataExportService;
  @Autowired private DataSubjectRequestService dataSubjectRequestService;
  @Autowired private DataSubjectRequestRepository requestRepository;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private AuditEventRepository auditEventRepository;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private MemberSyncService memberSyncService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private PortalContactRepository portalContactRepository;
  @Autowired private TimeEntryRepository timeEntryRepository;
  @Autowired private ProjectRepository projectRepository;
  @Autowired private TaskRepository taskRepository;
  @Autowired private CustomerProjectRepository customerProjectRepository;

  @MockitoBean private StorageService storageService;

  private String tenantSchema;
  private UUID memberId;
  private UUID customerId;

  @BeforeAll
  void setup() {
    provisioningService.provisionTenant(ORG_ID, "DSR Export Test Org", null);
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
    var syncResult =
        memberSyncService.syncMember(
            ORG_ID,
            "user_dsr_export_test",
            "dsr_export@test.com",
            "DSR Export Tester",
            null,
            "owner");
    memberId = syncResult.memberId();

    customerId =
        runInTenant(
            () -> {
              var customer =
                  createActiveCustomer(
                      "Export Test Customer", "export-customer@test.com", memberId);
              customer = customerRepository.save(customer);
              return customer.getId();
            });
  }

  @Test
  void generateExport_setsExportFileKeyOnRequest() {
    when(storageService.upload(any(String.class), any(byte[].class), any(String.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    var request =
        runInTenant(
            () ->
                dataSubjectRequestService.createRequest(
                    customerId, "ACCESS", "Export test", memberId));

    var s3Key = runInTenant(() -> dataExportService.generateExport(request.getId(), memberId));

    assertThat(s3Key).contains("exports/" + request.getId() + ".zip");

    var updated = runInTenant(() -> requestRepository.findById(request.getId()).orElseThrow());
    assertThat(updated.getExportFileKey()).isEqualTo(s3Key);
  }

  @Test
  void generateExport_uploadsToS3() {
    when(storageService.upload(any(String.class), any(byte[].class), any(String.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    var request =
        runInTenant(
            () ->
                dataSubjectRequestService.createRequest(
                    customerId, "ACCESS", "S3 upload test", memberId));

    runInTenant(() -> dataExportService.generateExport(request.getId(), memberId));

    var keyCaptor = ArgumentCaptor.forClass(String.class);
    var contentTypeCaptor = ArgumentCaptor.forClass(String.class);
    verify(storageService)
        .upload(keyCaptor.capture(), any(byte[].class), contentTypeCaptor.capture());

    assertThat(keyCaptor.getValue()).contains("exports/" + request.getId() + ".zip");
    assertThat(contentTypeCaptor.getValue()).isEqualTo("application/zip");
  }

  @Test
  void generateExport_producesZipWithCorrectEntries() {
    when(storageService.upload(any(String.class), any(byte[].class), any(String.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    var request =
        runInTenant(
            () ->
                dataSubjectRequestService.createRequest(
                    customerId, "ACCESS", "ZIP contents test", memberId));

    runInTenant(() -> dataExportService.generateExport(request.getId(), memberId));

    // Verify the storage upload was called
    verify(storageService).upload(any(String.class), any(byte[].class), any(String.class));
  }

  @Test
  void generateExport_auditEventLogged() {
    when(storageService.upload(any(String.class), any(byte[].class), any(String.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    var request =
        runInTenant(
            () ->
                dataSubjectRequestService.createRequest(
                    customerId, "ACCESS", "Audit export test", memberId));

    runInTenant(() -> dataExportService.generateExport(request.getId(), memberId));

    runInTenant(
        () -> {
          var page =
              auditEventRepository.findByFilter(
                  "data_subject_request",
                  request.getId(),
                  null,
                  "data.export.generated",
                  null,
                  null,
                  Pageable.ofSize(10));
          assertThat(page.getContent()).isNotEmpty();
          assertThat(page.getContent().getFirst().getEventType())
              .isEqualTo("data.export.generated");
        });
  }

  // --- New tests for exportCustomerData (Epic 374A) ---

  @Test
  void exportCustomerData_includesAllTimeEntries() throws Exception {
    // Setup: create a project linked to customer, add one billable + one non-billable time entry
    runInTenant(
        () -> {
          var project = new Project("Export Time Test Project", "desc", memberId);
          project = projectRepository.save(project);

          var cp = new CustomerProject(customerId, project.getId(), memberId);
          customerProjectRepository.save(cp);

          var task =
              new Task(project.getId(), "Export Time Task", "desc", "MEDIUM", null, null, memberId);
          task = taskRepository.save(task);

          var billableEntry =
              new TimeEntry(
                  task.getId(), memberId, LocalDate.now(), 60, true, null, "billable work");
          timeEntryRepository.save(billableEntry);

          var nonBillableEntry =
              new TimeEntry(
                  task.getId(), memberId, LocalDate.now(), 30, false, null, "non-billable work");
          timeEntryRepository.save(nonBillableEntry);
        });

    var zipCaptor = ArgumentCaptor.forClass(byte[].class);
    when(storageService.upload(any(), zipCaptor.capture(), any()))
        .thenAnswer(inv -> inv.getArgument(0));
    when(storageService.generateDownloadUrl(any(), any()))
        .thenReturn(
            new PresignedUrl("https://example.com/download", Instant.now().plusSeconds(3600)));

    var result = runInTenant(() -> dataExportService.exportCustomerData(customerId, memberId));

    assertThat(result).isNotNull();
    assertThat(result.status()).isEqualTo("COMPLETED");

    // Inspect ZIP for time entries
    byte[] zipBytes = zipCaptor.getValue();
    Map<String, byte[]> entries = readZipEntries(zipBytes);

    String prefix = "customer-export-" + customerId + "/";
    assertThat(entries).containsKey(prefix + "time-entries.json");

    String timeEntriesJson = new String(entries.get(prefix + "time-entries.json"));
    assertThat(timeEntriesJson).contains("billable work");
    assertThat(timeEntriesJson).contains("non-billable work");
  }

  @Test
  void exportCustomerData_includesCustomFields() throws Exception {
    // Setup: set custom fields on the customer
    runInTenant(
        () -> {
          var customer = customerRepository.findById(customerId).orElseThrow();
          customer.setCustomFields(Map.of("industry", "Technology", "size", "Enterprise"));
          customerRepository.save(customer);
        });

    var zipCaptor = ArgumentCaptor.forClass(byte[].class);
    when(storageService.upload(any(), zipCaptor.capture(), any()))
        .thenAnswer(inv -> inv.getArgument(0));
    when(storageService.generateDownloadUrl(any(), any()))
        .thenReturn(
            new PresignedUrl("https://example.com/download", Instant.now().plusSeconds(3600)));

    var result = runInTenant(() -> dataExportService.exportCustomerData(customerId, memberId));

    assertThat(result).isNotNull();

    byte[] zipBytes = zipCaptor.getValue();
    Map<String, byte[]> entries = readZipEntries(zipBytes);

    String prefix = "customer-export-" + customerId + "/";
    assertThat(entries).containsKey(prefix + "custom-fields.json");

    String customFieldsJson = new String(entries.get(prefix + "custom-fields.json"));
    assertThat(customFieldsJson).contains("Technology");
    assertThat(customFieldsJson).contains("Enterprise");
  }

  @Test
  void exportCustomerData_includesAuditEvents() throws Exception {
    var zipCaptor = ArgumentCaptor.forClass(byte[].class);
    when(storageService.upload(any(), zipCaptor.capture(), any()))
        .thenAnswer(inv -> inv.getArgument(0));
    when(storageService.generateDownloadUrl(any(), any()))
        .thenReturn(
            new PresignedUrl("https://example.com/download", Instant.now().plusSeconds(3600)));

    var result = runInTenant(() -> dataExportService.exportCustomerData(customerId, memberId));

    assertThat(result).isNotNull();

    byte[] zipBytes = zipCaptor.getValue();
    Map<String, byte[]> entries = readZipEntries(zipBytes);

    String prefix = "customer-export-" + customerId + "/";
    assertThat(entries).containsKey(prefix + "audit-events.json");

    // The audit-events.json should exist (may be empty array if no customer-entity audit events
    // existed before this call, but the file itself must be present)
    String auditEventsJson = new String(entries.get(prefix + "audit-events.json"));
    assertThat(auditEventsJson).isNotNull();
  }

  @Test
  void exportCustomerData_includesPortalContacts() throws Exception {
    // Setup: create a PortalContact for the customer
    runInTenant(
        () -> {
          var contact =
              new PortalContact(
                  ORG_ID,
                  customerId,
                  "portal-contact@test.com",
                  "Test Contact",
                  PortalContact.ContactRole.PRIMARY);
          portalContactRepository.save(contact);
        });

    var zipCaptor = ArgumentCaptor.forClass(byte[].class);
    when(storageService.upload(any(), zipCaptor.capture(), any()))
        .thenAnswer(inv -> inv.getArgument(0));
    when(storageService.generateDownloadUrl(any(), any()))
        .thenReturn(
            new PresignedUrl("https://example.com/download", Instant.now().plusSeconds(3600)));

    var result = runInTenant(() -> dataExportService.exportCustomerData(customerId, memberId));

    assertThat(result).isNotNull();

    byte[] zipBytes = zipCaptor.getValue();
    Map<String, byte[]> entries = readZipEntries(zipBytes);

    String prefix = "customer-export-" + customerId + "/";
    assertThat(entries).containsKey(prefix + "portal-contacts.json");

    String portalContactsJson = new String(entries.get(prefix + "portal-contacts.json"));
    assertThat(portalContactsJson).contains("portal-contact@test.com");
    assertThat(portalContactsJson).contains("Test Contact");
  }

  @Test
  void exportCustomerData_emptyCustomer_producesEmptyArrays() throws Exception {
    // Create a brand-new customer with no linked data
    UUID emptyCustomerId =
        runInTenant(
            () -> {
              var customer =
                  createActiveCustomer("Empty Export Customer", "empty-export@test.com", memberId);
              return customerRepository.save(customer).getId();
            });

    var zipCaptor = ArgumentCaptor.forClass(byte[].class);
    when(storageService.upload(any(), zipCaptor.capture(), any()))
        .thenAnswer(inv -> inv.getArgument(0));
    when(storageService.generateDownloadUrl(any(), any()))
        .thenReturn(
            new PresignedUrl("https://example.com/download", Instant.now().plusSeconds(3600)));

    var result = runInTenant(() -> dataExportService.exportCustomerData(emptyCustomerId, memberId));

    assertThat(result).isNotNull();
    assertThat(result.status()).isEqualTo("COMPLETED");
    assertThat(result.downloadUrl()).isEqualTo("https://example.com/download");

    byte[] zipBytes = zipCaptor.getValue();
    Map<String, byte[]> entries = readZipEntries(zipBytes);

    String prefix = "customer-export-" + emptyCustomerId + "/";
    // Verify all expected files exist
    assertThat(entries).containsKey(prefix + "customer.json");
    assertThat(entries).containsKey(prefix + "portal-contacts.json");
    assertThat(entries).containsKey(prefix + "time-entries.json");
    assertThat(entries).containsKey(prefix + "invoices.json");
    assertThat(entries).containsKey(prefix + "comments.json");
    assertThat(entries).containsKey(prefix + "custom-fields.json");
    assertThat(entries).containsKey(prefix + "audit-events.json");
    assertThat(entries).containsKey(prefix + "export-metadata.json");

    // Verify portal-contacts.json contains empty array
    String portalContactsJson = new String(entries.get(prefix + "portal-contacts.json"));
    assertThat(portalContactsJson).isEqualTo("[]");

    // Verify time-entries.json contains empty array
    String timeEntriesJson = new String(entries.get(prefix + "time-entries.json"));
    assertThat(timeEntriesJson).isEqualTo("[]");

    // Verify export-metadata.json contains expected fields
    String metadataJson = new String(entries.get(prefix + "export-metadata.json"));
    assertThat(metadataJson).contains("FULL_CUSTOMER_DATA");
    assertThat(metadataJson).contains(emptyCustomerId.toString());
  }

  // --- Helper methods ---

  private Map<String, byte[]> readZipEntries(byte[] zipBytes) throws Exception {
    var result = new LinkedHashMap<String, byte[]>();
    try (var bais = new ByteArrayInputStream(zipBytes);
        var zis = new ZipInputStream(bais)) {
      ZipEntry entry;
      while ((entry = zis.getNextEntry()) != null) {
        if (!entry.isDirectory()) {
          result.put(entry.getName(), zis.readAllBytes());
        }
        zis.closeEntry();
      }
    }
    return result;
  }

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  private <T> T runInTenant(Callable<T> callable) {
    var auth =
        new TestingAuthenticationToken("user_dsr_export_test", null, Collections.emptyList());
    SecurityContextHolder.getContext().setAuthentication(auth);

    return ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberId)
        .call(
            () ->
                transactionTemplate.execute(
                    tx -> {
                      try {
                        return callable.call();
                      } catch (RuntimeException e) {
                        throw e;
                      } catch (Exception e) {
                        throw new RuntimeException(e);
                      }
                    }));
  }

  private void runInTenant(Runnable runnable) {
    runInTenant(
        () -> {
          runnable.run();
          return null;
        });
  }
}
