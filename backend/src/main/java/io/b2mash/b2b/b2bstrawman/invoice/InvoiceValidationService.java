package io.b2mash.b2b.b2bstrawman.invoice;

import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.OrganizationRepository;
import io.b2mash.b2b.b2bstrawman.setupstatus.CustomerReadinessService;
import io.b2mash.b2b.b2bstrawman.template.DocumentTemplate;
import io.b2mash.b2b.b2bstrawman.template.DocumentTemplateRepository;
import io.b2mash.b2b.b2bstrawman.template.TemplateValidationService;
import io.b2mash.b2b.b2bstrawman.timeentry.TimeEntryRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InvoiceValidationService {

  private final CustomerReadinessService customerReadinessService;
  private final OrganizationRepository organizationRepository;
  private final TimeEntryRepository timeEntryRepository;
  private final DocumentTemplateRepository documentTemplateRepository;
  private final TemplateValidationService templateValidationService;
  private final InvoiceRepository invoiceRepository;

  public InvoiceValidationService(
      CustomerReadinessService customerReadinessService,
      OrganizationRepository organizationRepository,
      TimeEntryRepository timeEntryRepository,
      DocumentTemplateRepository documentTemplateRepository,
      TemplateValidationService templateValidationService,
      InvoiceRepository invoiceRepository) {
    this.customerReadinessService = customerReadinessService;
    this.organizationRepository = organizationRepository;
    this.timeEntryRepository = timeEntryRepository;
    this.documentTemplateRepository = documentTemplateRepository;
    this.templateValidationService = templateValidationService;
    this.invoiceRepository = invoiceRepository;
  }

  @Transactional(readOnly = true)
  public List<ValidationCheck> validateInvoiceGeneration(
      UUID customerId, List<UUID> timeEntryIds, UUID templateId) {
    var checks = new ArrayList<ValidationCheck>();

    // 1. Customer required fields
    checks.add(checkCustomerRequiredFields(customerId, Severity.WARNING));

    // 2. Org branding (org name)
    checks.add(checkOrgBranding(Severity.WARNING));

    // 3. Time entry rates
    if (timeEntryIds != null && !timeEntryIds.isEmpty()) {
      checks.add(checkTimeEntryRates(timeEntryIds));
    }

    // 4. Template required fields
    if (templateId != null) {
      checks.addAll(checkTemplateRequiredFields(templateId));
    }

    return checks;
  }

  @Transactional(readOnly = true)
  public List<ValidationCheck> validateInvoiceSend(UUID invoiceId) {
    var invoice =
        invoiceRepository
            .findById(invoiceId)
            .orElseThrow(() -> new ResourceNotFoundException("Invoice", invoiceId));

    var checks = new ArrayList<ValidationCheck>();

    // For send, org name and customer fields are CRITICAL
    checks.add(checkOrgBranding(Severity.CRITICAL));
    checks.add(checkCustomerRequiredFields(invoice.getCustomerId(), Severity.CRITICAL));

    return checks;
  }

  public boolean hasCriticalFailures(List<ValidationCheck> checks) {
    return checks.stream().anyMatch(c -> c.severity() == Severity.CRITICAL && !c.passed());
  }

  private ValidationCheck checkCustomerRequiredFields(UUID customerId, Severity severity) {
    var readiness = customerReadinessService.getReadiness(customerId);
    var reqFields = readiness.requiredFields();
    boolean passed = reqFields.total() == 0 || reqFields.filled() >= reqFields.total();
    return new ValidationCheck(
        "customer_required_fields",
        severity,
        passed,
        passed
            ? "All customer required fields are filled"
            : reqFields.filled() + " of " + reqFields.total() + " required fields filled");
  }

  private ValidationCheck checkOrgBranding(Severity severity) {
    String orgId = RequestScopes.requireOrgId();
    var orgOpt = organizationRepository.findByClerkOrgId(orgId);
    boolean passed =
        orgOpt.isPresent() && orgOpt.get().getName() != null && !orgOpt.get().getName().isBlank();
    return new ValidationCheck(
        "org_name",
        severity,
        passed,
        passed ? "Organization name is set" : "Organization name is missing");
  }

  private ValidationCheck checkTimeEntryRates(List<UUID> timeEntryIds) {
    var entries = timeEntryRepository.findAllById(timeEntryIds);
    long missingRates = entries.stream().filter(e -> e.getBillingRateSnapshot() == null).count();
    boolean passed = missingRates == 0;
    return new ValidationCheck(
        "time_entry_rates",
        Severity.WARNING,
        passed,
        passed
            ? "All time entries have billing rates"
            : missingRates + " time entries without billing rates");
  }

  private List<ValidationCheck> checkTemplateRequiredFields(UUID templateId) {
    var checks = new ArrayList<ValidationCheck>();
    var templateOpt = documentTemplateRepository.findById(templateId);
    if (templateOpt.isEmpty()) {
      checks.add(
          new ValidationCheck(
              "template_required_fields", Severity.WARNING, false, "Template not found"));
      return checks;
    }

    DocumentTemplate template = templateOpt.get();
    var requiredFields = template.getRequiredContextFields();
    if (requiredFields == null || requiredFields.isEmpty()) {
      checks.add(
          new ValidationCheck(
              "template_required_fields", Severity.WARNING, true, "No required template fields"));
      return checks;
    }

    // Build a minimal context for validation - invoice not yet created,
    // so we use empty maps for entities
    Map<String, Object> context =
        Map.of("invoice", Map.of(), "customer", Map.of(), "project", Map.of(), "org", Map.of());

    var result = templateValidationService.validateRequiredFields(requiredFields, context);
    boolean passed = result.allPresent();
    long missingCount = result.fields().stream().filter(f -> !f.present()).count();
    checks.add(
        new ValidationCheck(
            "template_required_fields",
            Severity.WARNING,
            passed,
            passed
                ? "All template required fields are present"
                : missingCount + " template fields missing"));

    return checks;
  }

  public record ValidationCheck(String name, Severity severity, boolean passed, String message) {}

  public enum Severity {
    INFO,
    WARNING,
    CRITICAL
  }
}
