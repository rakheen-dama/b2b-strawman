package io.b2mash.b2b.b2bstrawman.invoice;

import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.customer.CustomerType;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.notification.template.EmailTerminology;
import io.b2mash.b2b.b2bstrawman.prerequisite.StructuralPrerequisiteCheck;
import io.b2mash.b2b.b2bstrawman.provisioning.OrganizationRepository;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettings;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.setupstatus.CustomerReadinessService;
import io.b2mash.b2b.b2bstrawman.template.DocumentTemplate;
import io.b2mash.b2b.b2bstrawman.template.DocumentTemplateRepository;
import io.b2mash.b2b.b2bstrawman.timeentry.TimeEntryRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InvoiceValidationService {

  private final CustomerReadinessService customerReadinessService;
  private final CustomerRepository customerRepository;
  private final OrganizationRepository organizationRepository;
  private final TimeEntryRepository timeEntryRepository;
  private final DocumentTemplateRepository documentTemplateRepository;
  private final OrgSettingsRepository orgSettingsRepository;
  private final EmailTerminology emailTerminology;

  public InvoiceValidationService(
      CustomerReadinessService customerReadinessService,
      CustomerRepository customerRepository,
      OrganizationRepository organizationRepository,
      TimeEntryRepository timeEntryRepository,
      DocumentTemplateRepository documentTemplateRepository,
      OrgSettingsRepository orgSettingsRepository,
      EmailTerminology emailTerminology) {
    this.customerReadinessService = customerReadinessService;
    this.customerRepository = customerRepository;
    this.organizationRepository = organizationRepository;
    this.timeEntryRepository = timeEntryRepository;
    this.documentTemplateRepository = documentTemplateRepository;
    this.orgSettingsRepository = orgSettingsRepository;
    this.emailTerminology = emailTerminology;
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

    // 4. Customer tax number — WARNING at draft (soft-warn surfaced inline in the Pre-generation
    //    checks checklist), escalates to CRITICAL at send. See GAP-L-62.
    checks.add(checkCustomerTaxNumber(customerId, Severity.WARNING));

    // 5. Template required fields
    if (templateId != null) {
      checks.addAll(checkTemplateRequiredFields(templateId));
    }

    return checks;
  }

  @Transactional(readOnly = true)
  public List<ValidationCheck> validateInvoiceSend(Invoice invoice) {
    var checks = new ArrayList<ValidationCheck>();

    // For send, org name and customer fields are CRITICAL
    checks.add(checkOrgBranding(Severity.CRITICAL));
    checks.add(checkCustomerRequiredFields(invoice.getCustomerId(), Severity.CRITICAL));
    // GAP-L-62: tax number is soft-warned at draft but CRITICAL at send for COMPANY/TRUST (SARS
    // requires it on issued invoices for registered entities). LZKC-008: INDIVIDUAL customers are
    // downgraded to WARNING inside checkCustomerTaxNumber — natural persons don't always have one.
    checks.add(checkCustomerTaxNumber(invoice.getCustomerId(), Severity.CRITICAL));

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
            ? "All client required fields are filled"
            : reqFields.filled() + " of " + reqFields.total() + " required fields filled");
  }

  /**
   * Tax-number guard for invoice send: the customer must have a non-blank tax number on either the
   * promoted entity column or the JSONB custom fields map. Soft-warned at draft, critical at send
   * for COMPANY/TRUST customers (GAP-L-62 hybrid). For INDIVIDUAL customers the send-time check is
   * downgraded to a visible WARNING (LZKC-008) — SARS does not issue tax numbers to every natural
   * person (e.g. RAF claimants), mirroring the OBS-2102 activation exemption — so any role can send
   * without an override while the gap stays visible. Returns a PASSED check when the customer
   * cannot be found — the customer-required-fields check already covers that case with a clearer
   * message.
   *
   * <p>G-C6 (Phase 88 U15, architecture §13.3.8): the failure copy says what send will do for this
   * client — an INDIVIDUAL is never blocked (warning copy), a COMPANY/TRUST is blocked at send
   * (blocking copy). The copy is keyed on the client type rather than on the check's severity
   * because the draft-time check is WARNING for every type and must not tell a company's fee note
   * that it "will be sent".
   */
  private ValidationCheck checkCustomerTaxNumber(UUID customerId, Severity severity) {
    var customerOpt = customerRepository.findById(customerId);
    if (customerOpt.isEmpty()) {
      return new ValidationCheck("customer_tax_number", severity, true, "Client not found");
    }
    var customer = customerOpt.get();
    // LZKC-008: INDIVIDUAL customers are never hard-blocked on tax number — cap at WARNING.
    boolean individual = customer.getCustomerType() == CustomerType.INDIVIDUAL;
    var effectiveSeverity = individual ? Severity.WARNING : severity;
    boolean missing = StructuralPrerequisiteCheck.isTaxNumberMissing(customer);
    return new ValidationCheck(
        "customer_tax_number",
        effectiveSeverity,
        !missing,
        missing ? missingTaxNumberMessage(individual) : "Client tax number is set");
  }

  private String missingTaxNumberMessage(boolean individual) {
    String noun = invoiceNoun();
    return individual
        ? "Client has no tax number \u2014 the "
            + noun
            + " will be sent without a client VAT number"
        : "A tax number is required before "
            + EmailTerminology.withIndefiniteArticle(noun)
            + " can be sent to a company or trust";
  }

  /**
   * LZKC-009 (site 2): resolves the tenant's lowercase invoice noun — "invoice" by default, "fee
   * note" on legal-za — so validation copy matches the vocabulary shown across the rest of the firm
   * UI.
   */
  private String invoiceNoun() {
    String profile =
        orgSettingsRepository
            .findForCurrentTenant()
            .map(OrgSettings::getVerticalProfile)
            .orElse(null);
    return emailTerminology.resolve(profile).getOrDefault("invoice", "invoice");
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

    // Advisory check: the template declares required fields that will be validated at render time.
    // We cannot validate field presence here because the invoice has not been created yet.
    checks.add(
        new ValidationCheck(
            "template_required_fields",
            Severity.WARNING,
            true,
            "Template has "
                + requiredFields.size()
                + " required fields (validated at render time)"));

    return checks;
  }

  public record ValidationCheck(String name, Severity severity, boolean passed, String message) {}

  public enum Severity {
    WARNING,
    CRITICAL
  }
}
