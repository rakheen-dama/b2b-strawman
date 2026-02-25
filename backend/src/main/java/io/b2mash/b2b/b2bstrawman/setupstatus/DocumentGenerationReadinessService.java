package io.b2mash.b2b.b2bstrawman.setupstatus;

import io.b2mash.b2b.b2bstrawman.template.DocumentTemplateRepository;
import io.b2mash.b2b.b2bstrawman.template.TemplateContextBuilder;
import io.b2mash.b2b.b2bstrawman.template.TemplateEntityType;
import io.b2mash.b2b.b2bstrawman.template.TemplateValidationService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.ErrorResponseException;

@Service
public class DocumentGenerationReadinessService {

  private static final Logger log =
      LoggerFactory.getLogger(DocumentGenerationReadinessService.class);

  /**
   * Sentinel UUID used in place of a real memberId when checking readiness. memberRepository
   * .findById(null) throws IllegalArgumentException, but findById(sentinel) returns empty Optional
   * which causes buildGeneratedByMap to fall to the "Unknown" branch safely.
   */
  private static final UUID READINESS_MEMBER_SENTINEL =
      UUID.fromString("00000000-0000-0000-0000-000000000000");

  private final DocumentTemplateRepository documentTemplateRepository;
  private final List<TemplateContextBuilder> contextBuilders;
  private final TemplateValidationService templateValidationService;

  public DocumentGenerationReadinessService(
      DocumentTemplateRepository documentTemplateRepository,
      List<TemplateContextBuilder> contextBuilders,
      TemplateValidationService templateValidationService) {
    this.documentTemplateRepository = documentTemplateRepository;
    this.contextBuilders = contextBuilders;
    this.templateValidationService = templateValidationService;
  }

  @Transactional(readOnly = true)
  public List<TemplateReadiness> checkReadiness(TemplateEntityType entityType, UUID entityId) {
    var templates =
        documentTemplateRepository.findByPrimaryEntityTypeAndActiveTrueOrderBySortOrder(entityType);

    if (templates.isEmpty()) {
      return List.of();
    }

    var builder =
        contextBuilders.stream().filter(b -> b.supports() == entityType).findFirst().orElse(null);

    if (builder == null) {
      return templates.stream()
          .map(
              t ->
                  new TemplateReadiness(
                      t.getId(),
                      t.getName(),
                      t.getSlug(),
                      false,
                      List.of("No context builder available")))
          .toList();
    }

    Map<String, Object> context;
    try {
      context = builder.buildContext(entityId, READINESS_MEMBER_SENTINEL);
    } catch (ErrorResponseException e) {
      log.warn(
          "Entity not found during readiness check: entityType={}, entityId={}",
          entityType,
          entityId,
          e);
      return templates.stream()
          .map(
              t ->
                  new TemplateReadiness(
                      t.getId(), t.getName(), t.getSlug(), false, List.of("Entity not found")))
          .toList();
    } catch (IllegalArgumentException e) {
      log.warn(
          "Context builder failed during readiness check: entityType={}, entityId={}",
          entityType,
          entityId,
          e);
      return templates.stream()
          .map(
              t ->
                  new TemplateReadiness(
                      t.getId(),
                      t.getName(),
                      t.getSlug(),
                      false,
                      List.of("Entity not available for template generation")))
          .toList();
    }

    var structuralMissing = getMissingFields(context, entityType);
    return templates.stream()
        .map(
            t -> {
              var missing = new ArrayList<>(structuralMissing);
              // Check template-specific required fields
              if (t.getRequiredContextFields() != null && !t.getRequiredContextFields().isEmpty()) {
                var validationResult =
                    templateValidationService.validateRequiredFields(
                        t.getRequiredContextFields(), context);
                validationResult.fields().stream()
                    .filter(f -> !f.present())
                    .forEach(f -> missing.add(f.entity() + "." + f.field()));
              }
              return new TemplateReadiness(
                  t.getId(), t.getName(), t.getSlug(), missing.isEmpty(), missing);
            })
        .toList();
  }

  private List<String> getMissingFields(
      Map<String, Object> context, TemplateEntityType entityType) {
    List<String> missing = new ArrayList<>();
    switch (entityType) {
      case PROJECT -> {
        var project = asMap(context.get("project"));
        if (project == null || project.get("name") == null) {
          missing.add("Project Name");
        }
        if (context.get("customer") == null) {
          missing.add("Customer");
        }
        var org = asMap(context.get("org"));
        if (org == null) {
          missing.add("Org Settings");
        }
      }
      case CUSTOMER -> {
        var customer = asMap(context.get("customer"));
        if (customer == null || customer.get("name") == null) {
          missing.add("Customer Name");
        }
        if (customer == null || customer.get("email") == null) {
          missing.add("Customer Email");
        }
        if (context.get("org") == null) {
          missing.add("Org Settings");
        }
      }
      case INVOICE -> {
        var invoice = asMap(context.get("invoice"));
        if (invoice == null || invoice.get("invoiceNumber") == null) {
          missing.add("Invoice Number");
        }
        var customer = asMap(context.get("customer"));
        if (customer == null || customer.get("name") == null) {
          missing.add("Customer Name");
        }
        var lines = context.get("lines");
        if (lines == null || (lines instanceof List<?> list && list.isEmpty())) {
          missing.add("Invoice Lines");
        }
      }
      default -> {
        // New entity types will be explicitly handled when added
      }
    }
    return missing;
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> asMap(Object obj) {
    if (obj instanceof Map<?, ?> map) {
      return (Map<String, Object>) map;
    }
    return null;
  }
}
