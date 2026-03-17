package io.b2mash.b2b.b2bstrawman.template;

import io.b2mash.b2b.b2bstrawman.customer.CustomerProjectRepository;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.fielddefinition.EntityType;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceRepository;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceStatus;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class CustomerContextBuilder implements TemplateContextBuilder {

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

    // customer.*
    var customerMap = new LinkedHashMap<String, Object>();
    customerMap.put("id", customer.getId());
    customerMap.put("name", customer.getName());
    customerMap.put("email", customer.getEmail());
    customerMap.put("phone", customer.getPhone());
    customerMap.put("status", customer.getStatus());
    customerMap.put(
        "customFields",
        contextHelper.resolveDropdownLabels(
            customer.getCustomFields() != null ? customer.getCustomFields() : Map.of(),
            EntityType.CUSTOMER));
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

    // org.*
    context.put("org", contextHelper.buildOrgContext());

    // tags[]
    context.put("tags", contextHelper.buildTagsList("CUSTOMER", entityId));

    // generatedAt, generatedBy
    context.put("generatedAt", Instant.now().toString());
    context.put("generatedBy", contextHelper.buildGeneratedByMap(memberId));

    return context;
  }
}
