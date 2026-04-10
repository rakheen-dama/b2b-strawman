package io.b2mash.b2b.b2bstrawman.template;

import io.b2mash.b2b.b2bstrawman.customer.Customer;
import io.b2mash.b2b.b2bstrawman.customer.CustomerProjectRepository;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.fielddefinition.EntityType;
import io.b2mash.b2b.b2bstrawman.fielddefinition.FieldDefinition;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceRepository;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceStatus;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class CustomerContextBuilder implements TemplateContextBuilder {

  private static final Logger log = LoggerFactory.getLogger(CustomerContextBuilder.class);

  private final CustomerRepository customerRepository;
  private final CustomerProjectRepository customerProjectRepository;
  private final ProjectRepository projectRepository;
  private final InvoiceRepository invoiceRepository;
  private final TemplateContextHelper contextHelper;

  public CustomerContextBuilder(
      CustomerRepository customerRepository,
      CustomerProjectRepository customerProjectRepository,
      ProjectRepository projectRepository,
      InvoiceRepository invoiceRepository,
      TemplateContextHelper contextHelper) {
    this.customerRepository = customerRepository;
    this.customerProjectRepository = customerProjectRepository;
    this.projectRepository = projectRepository;
    this.invoiceRepository = invoiceRepository;
    this.contextHelper = contextHelper;
  }

  @Override
  public TemplateEntityType supports() {
    return TemplateEntityType.CUSTOMER;
  }

  @Override
  public Map<String, Object> buildContext(UUID entityId, UUID memberId) {
    var customer =
        customerRepository
            .findById(entityId)
            .orElseThrow(() -> new ResourceNotFoundException("Customer", entityId));

    var context = new HashMap<String, Object>();
    contextHelper.populateLocale(context);
    var fieldDefCache = new EnumMap<EntityType, List<FieldDefinition>>(EntityType.class);

    // customer.*
    var customerMap = new LinkedHashMap<String, Object>();
    customerMap.put("id", customer.getId());
    customerMap.put("name", customer.getName());
    customerMap.put("email", customer.getEmail());
    customerMap.put("phone", customer.getPhone());
    customerMap.put("status", customer.getStatus());
    // Promoted structural fields (Epic 459) — exposed as direct template variables.
    populatePromotedCustomerFields(customerMap, customer);
    Map<String, Object> rawCustomFields =
        customer.getCustomFields() != null ? customer.getCustomFields() : Map.of();
    Map<String, Object> resolvedCustomFields =
        contextHelper.resolveDropdownLabels(rawCustomFields, EntityType.CUSTOMER, fieldDefCache);
    // resolveDropdownLabels may return an immutable Map.of() for empty input — wrap before
    // mutating.
    var mutableCustomFields = new LinkedHashMap<String, Object>(resolvedCustomFields);
    injectPromotedCustomerAliases(mutableCustomFields, customer);
    customerMap.put("customFields", mutableCustomFields);
    log.debug(
        "Customer {} customFields: raw keys={}, resolved keys={}",
        entityId,
        rawCustomFields.keySet(),
        mutableCustomFields.keySet());
    context.put("customer", customerMap);

    // projects[] (linked via CustomerProject)
    var customerProjects = customerProjectRepository.findByCustomerId(entityId);
    if (!customerProjects.isEmpty()) {
      var projectIds = customerProjects.stream().map(cp -> cp.getProjectId()).toList();
      var projects = projectRepository.findAllById(projectIds);
      var projectsList =
          projects.stream()
              .map(
                  p -> {
                    var pm = new LinkedHashMap<String, Object>();
                    pm.put("id", p.getId());
                    pm.put("name", p.getName());
                    return (Map<String, Object>) pm;
                  })
              .toList();
      context.put("projects", projectsList);
    } else {
      context.put("projects", List.of());
    }

    // invoices[] and totalOutstanding
    var invoices = invoiceRepository.findByCustomerId(entityId);
    var invoicesList = new ArrayList<Map<String, Object>>();
    var runningBalance = BigDecimal.ZERO;
    var totalOutstanding = BigDecimal.ZERO;

    for (var invoice : invoices) {
      var im = new LinkedHashMap<String, Object>();
      im.put("invoiceNumber", invoice.getInvoiceNumber());
      im.put(
          "issueDate", invoice.getIssueDate() != null ? invoice.getIssueDate().toString() : null);
      im.put("dueDate", invoice.getDueDate() != null ? invoice.getDueDate().toString() : null);
      im.put("total", invoice.getTotal());
      im.put("currency", invoice.getCurrency());
      im.put("status", invoice.getStatus() != null ? invoice.getStatus().name() : null);

      boolean isPaid = invoice.getStatus() == InvoiceStatus.PAID;
      boolean isVoid = invoice.getStatus() == InvoiceStatus.VOID;
      boolean isDraft = invoice.getStatus() == InvoiceStatus.DRAFT;

      if (!isVoid && !isDraft) {
        runningBalance = runningBalance.add(invoice.getTotal());
        if (isPaid) {
          runningBalance = runningBalance.subtract(invoice.getTotal());
        } else {
          totalOutstanding = totalOutstanding.add(invoice.getTotal());
        }
      }
      im.put("runningBalance", runningBalance);
      invoicesList.add(im);
    }

    context.put("invoices", invoicesList);
    context.put("totalOutstanding", totalOutstanding);
    log.debug(
        "Customer {} invoice context: query returned {} invoices, context list size={}, totalOutstanding={}",
        entityId,
        invoices.size(),
        invoicesList.size(),
        totalOutstanding);

    // org.*
    context.put("org", contextHelper.buildOrgContext());

    // tags[]
    context.put("tags", contextHelper.buildTagsList("CUSTOMER", entityId));

    // generatedAt, generatedBy
    context.put("generatedAt", Instant.now().toString());
    context.put("generatedBy", contextHelper.buildGeneratedByMap(memberId));

    return context;
  }

  /**
   * Exposes the 13 structural customer fields promoted in Epic 459 as direct template variables
   * (e.g. {@code ${customer.taxNumber}}). Package-private so {@link ProjectContextBuilder} and
   * {@link InvoiceContextBuilder} can reuse it when building their nested customer blocks.
   */
  static void populatePromotedCustomerFields(Map<String, Object> customerMap, Customer customer) {
    customerMap.put("taxNumber", customer.getTaxNumber());
    customerMap.put("addressLine1", customer.getAddressLine1());
    customerMap.put("addressLine2", customer.getAddressLine2());
    customerMap.put("city", customer.getCity());
    customerMap.put("stateProvince", customer.getStateProvince());
    customerMap.put("postalCode", customer.getPostalCode());
    customerMap.put("country", customer.getCountry());
    customerMap.put("contactName", customer.getContactName());
    customerMap.put("contactEmail", customer.getContactEmail());
    customerMap.put("contactPhone", customer.getContactPhone());
    customerMap.put("entityType", customer.getEntityType());
    customerMap.put(
        "financialYearEnd",
        customer.getFinancialYearEnd() != null ? customer.getFinancialYearEnd().toString() : null);
    customerMap.put("registrationNumber", customer.getRegistrationNumber());
  }

  /**
   * Injects backward-compatible {@code customFields.<slug>} aliases into the given customFields
   * map, pointing at the promoted structural getters. Only overwrites when the structural getter is
   * non-null — pre-Phase-63 entities that still carry the value in JSONB are left alone.
   *
   * <p>Shared with {@link ProjectContextBuilder} and {@link InvoiceContextBuilder} so nested
   * customer blocks in project/invoice templates also see the aliases.
   */
  static void injectPromotedCustomerAliases(Map<String, Object> customFields, Customer customer) {
    putIfNotNull(customFields, "tax_number", customer.getTaxNumber());
    putIfNotNull(customFields, "vat_number", customer.getTaxNumber());
    putIfNotNull(customFields, "phone", customer.getContactPhone());
    putIfNotNull(customFields, "primary_contact_name", customer.getContactName());
    putIfNotNull(customFields, "primary_contact_email", customer.getContactEmail());
    putIfNotNull(customFields, "primary_contact_phone", customer.getContactPhone());
    putIfNotNull(
        customFields, "acct_company_registration_number", customer.getRegistrationNumber());
    putIfNotNull(customFields, "registration_number", customer.getRegistrationNumber());
    putIfNotNull(customFields, "client_type", customer.getEntityType());
    putIfNotNull(customFields, "acct_entity_type", customer.getEntityType());
    putIfNotNull(customFields, "address_line1", customer.getAddressLine1());
    putIfNotNull(customFields, "address_line2", customer.getAddressLine2());
    putIfNotNull(customFields, "city", customer.getCity());
    putIfNotNull(customFields, "state_province", customer.getStateProvince());
    putIfNotNull(customFields, "postal_code", customer.getPostalCode());
    putIfNotNull(customFields, "country", customer.getCountry());
    putIfNotNull(customFields, "registered_address", customer.getAddressLine1());
    putIfNotNull(customFields, "physical_address", customer.getAddressLine1());
    if (customer.getFinancialYearEnd() != null) {
      customFields.put("financial_year_end", customer.getFinancialYearEnd().toString());
    }
  }

  private static void putIfNotNull(Map<String, Object> map, String key, Object value) {
    if (value != null) {
      map.put(key, value);
    }
  }
}
