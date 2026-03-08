package io.b2mash.b2b.b2bstrawman.template;

import io.b2mash.b2b.b2bstrawman.fielddefinition.EntityType;
import io.b2mash.b2b.b2bstrawman.fielddefinition.FieldDefinition;
import io.b2mash.b2b.b2bstrawman.fielddefinition.FieldDefinitionRepository;
import io.b2mash.b2b.b2bstrawman.fielddefinition.FieldType;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Registry of available template variables per entity type. Powers the variable metadata endpoint
 * and the frontend variable picker. Static groups are manually curated; custom field groups are
 * dynamically loaded from the tenant's FieldDefinition records.
 */
@Component
public class VariableMetadataRegistry {

  static final Map<FieldType, String> FIELD_TYPE_TO_VARIABLE_TYPE =
      Map.of(
          FieldType.TEXT, "string",
          FieldType.NUMBER, "number",
          FieldType.DATE, "date",
          FieldType.CURRENCY, "currency",
          FieldType.BOOLEAN, "boolean",
          FieldType.DROPDOWN, "string",
          FieldType.URL, "string",
          FieldType.EMAIL, "string",
          FieldType.PHONE, "string");

  private final Map<TemplateEntityType, List<VariableGroup>> staticGroupsByType;
  private final Map<TemplateEntityType, List<LoopSource>> loopSourcesByType;
  private final FieldDefinitionRepository fieldDefinitionRepository;

  public VariableMetadataRegistry(FieldDefinitionRepository fieldDefinitionRepository) {
    this.fieldDefinitionRepository = fieldDefinitionRepository;
    staticGroupsByType = new EnumMap<>(TemplateEntityType.class);
    loopSourcesByType = new EnumMap<>(TemplateEntityType.class);

    registerProjectVariables();
    registerCustomerVariables();
    registerInvoiceVariables();
  }

  public VariableMetadataResponse getVariables(TemplateEntityType entityType) {
    var groups = new ArrayList<>(staticGroupsByType.getOrDefault(entityType, List.of()));
    appendCustomFieldGroups(groups, entityType);
    return new VariableMetadataResponse(
        List.copyOf(groups), loopSourcesByType.getOrDefault(entityType, List.of()));
  }

  // TODO: Batch DB queries — replace per-EntityType findByEntityTypeAndActiveTrueOrderBySortOrder()
  // calls with a single findByEntityTypeInAndActiveTrueOrderBySortOrder(List<EntityType>) for
  // performance (currently up to 3 separate queries for INVOICE templates).
  private void appendCustomFieldGroups(
      List<VariableGroup> groups, TemplateEntityType templateEntityType) {
    switch (templateEntityType) {
      case PROJECT -> {
        addCustomFieldGroup(groups, EntityType.PROJECT, "project", "Custom Fields");
        addCustomFieldGroup(groups, EntityType.CUSTOMER, "customer", "Customer Custom Fields");
      }
      case CUSTOMER -> {
        addCustomFieldGroup(groups, EntityType.CUSTOMER, "customer", "Custom Fields");
      }
      case INVOICE -> {
        addCustomFieldGroup(groups, EntityType.INVOICE, "invoice", "Custom Fields");
        addCustomFieldGroup(groups, EntityType.CUSTOMER, "customer", "Customer Custom Fields");
        addCustomFieldGroup(groups, EntityType.PROJECT, "project", "Project Custom Fields");
      }
    }
  }

  private void addCustomFieldGroup(
      List<VariableGroup> groups, EntityType entityType, String prefix, String label) {
    List<FieldDefinition> fields =
        fieldDefinitionRepository.findByEntityTypeAndActiveTrueOrderBySortOrder(entityType);
    if (fields.isEmpty()) {
      return;
    }
    var variables =
        fields.stream()
            .map(
                fd ->
                    new VariableInfo(
                        prefix + ".customFields." + fd.getSlug(),
                        fd.getName(),
                        FIELD_TYPE_TO_VARIABLE_TYPE.getOrDefault(fd.getFieldType(), "string")))
            .toList();
    groups.add(new VariableGroup(label, prefix + ".customFields", variables));
  }

  private void registerProjectVariables() {
    var groups = new ArrayList<VariableGroup>();

    groups.add(
        new VariableGroup(
            "Project",
            "project",
            List.of(
                new VariableInfo("project.id", "Project ID", "string"),
                new VariableInfo("project.name", "Project Name", "string"),
                new VariableInfo("project.description", "Description", "string"),
                new VariableInfo("project.createdAt", "Created At", "date"))));

    groups.add(
        new VariableGroup(
            "Customer",
            "customer",
            List.of(
                new VariableInfo("customer.id", "Customer ID", "string"),
                new VariableInfo("customer.name", "Customer Name", "string"),
                new VariableInfo("customer.email", "Customer Email", "string"))));

    groups.add(
        new VariableGroup(
            "Lead",
            "lead",
            List.of(
                new VariableInfo("lead.id", "Lead ID", "string"),
                new VariableInfo("lead.name", "Lead Name", "string"),
                new VariableInfo("lead.email", "Lead Email", "string"))));

    groups.add(
        new VariableGroup(
            "Budget",
            "budget",
            List.of(
                new VariableInfo("budget.hours", "Budget Hours", "number"),
                new VariableInfo("budget.amount", "Budget Amount", "currency"),
                new VariableInfo("budget.currency", "Budget Currency", "string"))));

    groups.add(buildOrgGroup());
    groups.add(buildGeneratedGroup());

    staticGroupsByType.put(TemplateEntityType.PROJECT, List.copyOf(groups));

    loopSourcesByType.put(
        TemplateEntityType.PROJECT,
        List.of(
            new LoopSource(
                "members",
                "Project Members",
                List.of("PROJECT"),
                List.of("id", "name", "email", "role")),
            new LoopSource(
                "tags", "Tags", List.of("PROJECT", "CUSTOMER"), List.of("name", "color"))));
  }

  private void registerCustomerVariables() {
    var groups = new ArrayList<VariableGroup>();

    groups.add(
        new VariableGroup(
            "Customer",
            "customer",
            List.of(
                new VariableInfo("customer.id", "Customer ID", "string"),
                new VariableInfo("customer.name", "Customer Name", "string"),
                new VariableInfo("customer.email", "Customer Email", "string"),
                new VariableInfo("customer.phone", "Customer Phone", "string"),
                new VariableInfo("customer.status", "Customer Status", "string"))));

    groups.add(buildOrgGroup());
    groups.add(buildGeneratedGroup());

    staticGroupsByType.put(TemplateEntityType.CUSTOMER, List.copyOf(groups));

    loopSourcesByType.put(
        TemplateEntityType.CUSTOMER,
        List.of(
            new LoopSource("projects", "Projects", List.of("CUSTOMER"), List.of("id", "name")),
            new LoopSource(
                "tags", "Tags", List.of("PROJECT", "CUSTOMER"), List.of("name", "color"))));
  }

  private void registerInvoiceVariables() {
    var groups = new ArrayList<VariableGroup>();

    groups.add(
        new VariableGroup(
            "Invoice",
            "invoice",
            List.of(
                new VariableInfo("invoice.id", "Invoice ID", "string"),
                new VariableInfo("invoice.invoiceNumber", "Invoice Number", "string"),
                new VariableInfo("invoice.status", "Invoice Status", "string"),
                new VariableInfo("invoice.issueDate", "Issue Date", "date"),
                new VariableInfo("invoice.dueDate", "Due Date", "date"),
                new VariableInfo("invoice.subtotal", "Subtotal", "currency"),
                new VariableInfo("invoice.taxAmount", "Tax Amount", "currency"),
                new VariableInfo("invoice.total", "Total", "currency"),
                new VariableInfo("invoice.currency", "Currency", "string"),
                new VariableInfo("invoice.notes", "Notes", "string"))));

    groups.add(
        new VariableGroup(
            "Customer",
            "customer",
            List.of(
                new VariableInfo("customer.id", "Customer ID", "string"),
                new VariableInfo("customer.name", "Customer Name", "string"),
                new VariableInfo("customer.email", "Customer Email", "string"))));

    groups.add(
        new VariableGroup(
            "Project",
            "project",
            List.of(
                new VariableInfo("project.id", "Project ID", "string"),
                new VariableInfo("project.name", "Project Name", "string"))));

    groups.add(buildOrgGroup());
    groups.add(buildGeneratedGroup());

    staticGroupsByType.put(TemplateEntityType.INVOICE, List.copyOf(groups));

    loopSourcesByType.put(
        TemplateEntityType.INVOICE,
        List.of(
            new LoopSource(
                "lines",
                "Invoice Lines",
                List.of("INVOICE"),
                List.of("description", "quantity", "unitPrice", "amount"))));
  }

  private VariableGroup buildOrgGroup() {
    return new VariableGroup(
        "Organization",
        "org",
        List.of(
            new VariableInfo("org.name", "Organization Name", "string"),
            new VariableInfo("org.defaultCurrency", "Default Currency", "string"),
            new VariableInfo("org.brandColor", "Brand Color", "string"),
            new VariableInfo("org.documentFooterText", "Document Footer", "string"),
            new VariableInfo("org.logoUrl", "Logo URL", "string")));
  }

  private VariableGroup buildGeneratedGroup() {
    return new VariableGroup(
        "Generated",
        "generated",
        List.of(
            new VariableInfo("generatedAt", "Generated At", "date"),
            new VariableInfo("generatedBy.name", "Generated By Name", "string"),
            new VariableInfo("generatedBy.email", "Generated By Email", "string")));
  }

  // --- DTOs ---

  public record VariableMetadataResponse(
      List<VariableGroup> groups, List<LoopSource> loopSources) {}

  public record VariableGroup(String label, String prefix, List<VariableInfo> variables) {}

  public record VariableInfo(String key, String label, String type) {}

  public record LoopSource(
      String key, String label, List<String> entityTypes, List<String> fields) {}
}
