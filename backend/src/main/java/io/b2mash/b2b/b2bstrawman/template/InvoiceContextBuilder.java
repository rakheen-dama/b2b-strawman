package io.b2mash.b2b.b2bstrawman.template;

import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceLineRepository;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceRepository;
import io.b2mash.b2b.b2bstrawman.member.MemberRepository;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.s3.S3PresignedUrlService;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class InvoiceContextBuilder implements TemplateContextBuilder {

  private final InvoiceRepository invoiceRepository;
  private final InvoiceLineRepository invoiceLineRepository;
  private final CustomerRepository customerRepository;
  private final ProjectRepository projectRepository;
  private final OrgSettingsRepository orgSettingsRepository;
  private final MemberRepository memberRepository;
  private final S3PresignedUrlService s3PresignedUrlService;

  public InvoiceContextBuilder(
      InvoiceRepository invoiceRepository,
      InvoiceLineRepository invoiceLineRepository,
      CustomerRepository customerRepository,
      ProjectRepository projectRepository,
      OrgSettingsRepository orgSettingsRepository,
      MemberRepository memberRepository,
      S3PresignedUrlService s3PresignedUrlService) {
    this.invoiceRepository = invoiceRepository;
    this.invoiceLineRepository = invoiceLineRepository;
    this.customerRepository = customerRepository;
    this.projectRepository = projectRepository;
    this.orgSettingsRepository = orgSettingsRepository;
    this.memberRepository = memberRepository;
    this.s3PresignedUrlService = s3PresignedUrlService;
  }

  @Override
  public TemplateEntityType supports() {
    return TemplateEntityType.INVOICE;
  }

  @Override
  public Map<String, Object> buildContext(UUID entityId, UUID memberId) {
    var invoice =
        invoiceRepository
            .findOneById(entityId)
            .orElseThrow(() -> new ResourceNotFoundException("Invoice", entityId));

    var context = new HashMap<String, Object>();

    // invoice.*
    var invoiceMap = new LinkedHashMap<String, Object>();
    invoiceMap.put("id", invoice.getId());
    invoiceMap.put("invoiceNumber", invoice.getInvoiceNumber());
    invoiceMap.put("status", invoice.getStatus() != null ? invoice.getStatus().name() : null);
    invoiceMap.put(
        "issueDate", invoice.getIssueDate() != null ? invoice.getIssueDate().toString() : null);
    invoiceMap.put(
        "dueDate", invoice.getDueDate() != null ? invoice.getDueDate().toString() : null);
    invoiceMap.put("subtotal", invoice.getSubtotal());
    invoiceMap.put("taxAmount", invoice.getTaxAmount());
    invoiceMap.put("total", invoice.getTotal());
    invoiceMap.put("currency", invoice.getCurrency());
    invoiceMap.put("notes", invoice.getNotes());
    context.put("invoice", invoiceMap);

    // lines[]
    var lines = invoiceLineRepository.findByInvoiceIdOrderBySortOrder(entityId);
    var linesList =
        lines.stream()
            .map(
                line -> {
                  var lineMap = new LinkedHashMap<String, Object>();
                  lineMap.put("description", line.getDescription());
                  lineMap.put("quantity", line.getQuantity());
                  lineMap.put("unitPrice", line.getUnitPrice());
                  lineMap.put("amount", line.getAmount());
                  return (Map<String, Object>) lineMap;
                })
            .toList();
    context.put("lines", linesList);

    // customer.* (from invoice's customerId)
    customerRepository
        .findOneById(invoice.getCustomerId())
        .ifPresentOrElse(
            customer -> {
              var customerMap = new LinkedHashMap<String, Object>();
              customerMap.put("id", customer.getId());
              customerMap.put("name", customer.getName());
              customerMap.put("email", customer.getEmail());
              customerMap.put(
                  "customFields",
                  customer.getCustomFields() != null ? customer.getCustomFields() : Map.of());
              context.put("customer", customerMap);
            },
            () -> context.put("customer", null));

    // project.* (from first invoice line with a projectId, null-safe)
    lines.stream()
        .filter(line -> line.getProjectId() != null)
        .findFirst()
        .ifPresentOrElse(
            line ->
                projectRepository
                    .findOneById(line.getProjectId())
                    .ifPresentOrElse(
                        project -> {
                          var projectMap = new LinkedHashMap<String, Object>();
                          projectMap.put("id", project.getId());
                          projectMap.put("name", project.getName());
                          projectMap.put(
                              "customFields",
                              project.getCustomFields() != null
                                  ? project.getCustomFields()
                                  : Map.of());
                          context.put("project", projectMap);
                        },
                        () -> context.put("project", null)),
            () -> context.put("project", null));

    // org.*
    context.put("org", buildOrgContext());

    // generatedAt, generatedBy
    context.put("generatedAt", Instant.now().toString());
    context.put("generatedBy", buildGeneratedByMap(memberId));

    return context;
  }

  private Map<String, Object> buildOrgContext() {
    var orgMap = new LinkedHashMap<String, Object>();
    orgSettingsRepository
        .findForCurrentTenant()
        .ifPresentOrElse(
            settings -> {
              orgMap.put("defaultCurrency", settings.getDefaultCurrency());
              orgMap.put("brandColor", settings.getBrandColor());
              orgMap.put("documentFooterText", settings.getDocumentFooterText());

              if (settings.getLogoS3Key() != null && !settings.getLogoS3Key().isBlank()) {
                try {
                  var result = s3PresignedUrlService.generateDownloadUrl(settings.getLogoS3Key());
                  orgMap.put("logoUrl", result.url());
                } catch (Exception e) {
                  orgMap.put("logoUrl", null);
                }
              } else {
                orgMap.put("logoUrl", null);
              }
            },
            () -> {
              orgMap.put("defaultCurrency", null);
              orgMap.put("brandColor", null);
              orgMap.put("documentFooterText", null);
              orgMap.put("logoUrl", null);
            });
    return orgMap;
  }

  private Map<String, Object> buildGeneratedByMap(UUID memberId) {
    var generatedBy = new LinkedHashMap<String, Object>();
    memberRepository
        .findOneById(memberId)
        .ifPresentOrElse(
            member -> {
              generatedBy.put("name", member.getName());
              generatedBy.put("email", member.getEmail());
            },
            () -> {
              generatedBy.put("name", "Unknown");
              generatedBy.put("email", null);
            });
    return generatedBy;
  }
}
