package io.b2mash.b2b.b2bstrawman.project;

import io.b2mash.b2b.b2bstrawman.compliance.CustomerLifecycleGuard;
import io.b2mash.b2b.b2bstrawman.compliance.LifecycleAction;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.fielddefinition.CustomFieldValidator;
import io.b2mash.b2b.b2bstrawman.fielddefinition.EntityType;
import io.b2mash.b2b.b2bstrawman.fielddefinition.FieldGroupResolver;
import io.b2mash.b2b.b2bstrawman.fielddefinition.FieldGroupService;
import io.b2mash.b2b.b2bstrawman.fielddefinition.dto.FieldDefinitionResponse;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettings;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Handles custom field validation, field group resolution, naming pattern application, and customer
 * link validation. Extracted from ProjectService to reduce constructor bloat.
 */
@Service
class ProjectFieldService {

  private final CustomFieldValidator customFieldValidator;
  private final FieldGroupResolver fieldGroupResolver;
  private final FieldGroupService fieldGroupService;
  private final OrgSettingsRepository orgSettingsRepository;
  private final ProjectNameResolver projectNameResolver;
  private final CustomerRepository customerRepository;
  private final CustomerLifecycleGuard customerLifecycleGuard;

  ProjectFieldService(
      CustomFieldValidator customFieldValidator,
      FieldGroupResolver fieldGroupResolver,
      FieldGroupService fieldGroupService,
      OrgSettingsRepository orgSettingsRepository,
      ProjectNameResolver projectNameResolver,
      CustomerRepository customerRepository,
      CustomerLifecycleGuard customerLifecycleGuard) {
    this.customFieldValidator = customFieldValidator;
    this.fieldGroupResolver = fieldGroupResolver;
    this.fieldGroupService = fieldGroupService;
    this.orgSettingsRepository = orgSettingsRepository;
    this.projectNameResolver = projectNameResolver;
    this.customerRepository = customerRepository;
    this.customerLifecycleGuard = customerLifecycleGuard;
  }

  record CreateFieldResult(
      Map<String, Object> validatedFields, String resolvedName, List<UUID> mergedFieldGroups) {}

  /**
   * Prepares field-related data for project creation: validates custom fields, applies naming
   * pattern, and resolves auto-apply field groups.
   */
  CreateFieldResult prepareForCreate(
      String name,
      Map<String, Object> customFields,
      List<UUID> appliedFieldGroups,
      String customerName) {
    // Validate custom fields
    Map<String, Object> validatedFields =
        customFieldValidator.validate(
            EntityType.PROJECT,
            customFields != null ? customFields : new HashMap<>(),
            appliedFieldGroups);

    // Apply naming pattern if configured
    String resolvedName = name;
    var namingPattern =
        orgSettingsRepository
            .findForCurrentTenant()
            .map(OrgSettings::getProjectNamingPattern)
            .orElse(null);
    if (namingPattern != null && !namingPattern.isBlank()) {
      resolvedName =
          projectNameResolver.resolve(namingPattern, name, validatedFields, customerName);
    }

    // Resolve auto-apply field groups and merge with explicitly applied groups
    var autoApplyIds = fieldGroupService.resolveAutoApplyGroupIds(EntityType.PROJECT);
    List<UUID> mergedFieldGroups =
        appliedFieldGroups != null ? new ArrayList<>(appliedFieldGroups) : new ArrayList<>();
    if (!autoApplyIds.isEmpty()) {
      for (UUID id : autoApplyIds) {
        if (!mergedFieldGroups.contains(id)) {
          mergedFieldGroups.add(id);
        }
      }
    }

    return new CreateFieldResult(validatedFields, resolvedName, mergedFieldGroups);
  }

  /** Validates custom fields for project updates. */
  Map<String, Object> validateFields(Map<String, Object> customFields, List<UUID> effectiveGroups) {
    return customFieldValidator.validate(EntityType.PROJECT, customFields, effectiveGroups);
  }

  /** Resolves and validates field groups, updating the project's applied groups. */
  List<FieldDefinitionResponse> setFieldGroups(Project project, List<UUID> appliedFieldGroups) {
    var resolved = fieldGroupResolver.resolveAndValidate(appliedFieldGroups, EntityType.PROJECT);
    project.setAppliedFieldGroups(resolved);
    return fieldGroupResolver.collectFieldDefinitions(resolved);
  }

  /**
   * Validates the customer link and returns the customer name. Checks that the customer exists and
   * that its lifecycle permits project creation.
   */
  String resolveCustomerName(UUID customerId) {
    var customer =
        customerRepository
            .findById(customerId)
            .orElseThrow(() -> new ResourceNotFoundException("Customer", customerId));
    customerLifecycleGuard.requireActionPermitted(customer, LifecycleAction.CREATE_PROJECT);
    return customer.getName();
  }

  /**
   * Validates that a customer link is permitted (for project updates). GAP-L-35: uses the
   * UPDATE_PROJECT action — routine edits (custom-field saves, due-date tweaks) must not be gated
   * by the stricter CREATE_PROJECT rule that blocks PROSPECT / OFFBOARDING. Only OFFBOARDED
   * (terminal) blocks updates.
   */
  void validateCustomerLink(UUID customerId) {
    var customer =
        customerRepository
            .findById(customerId)
            .orElseThrow(() -> new ResourceNotFoundException("Customer", customerId));
    customerLifecycleGuard.requireActionPermitted(customer, LifecycleAction.UPDATE_PROJECT);
  }
}
