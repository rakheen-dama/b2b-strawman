package io.b2mash.b2b.b2bstrawman.customer;

import io.b2mash.b2b.b2bstrawman.fielddefinition.EntityType;
import io.b2mash.b2b.b2bstrawman.fielddefinition.FieldGroupService;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Auto-applies the client field groups marked auto-apply to a new client, before it is saved so the
 * audit events capture the final state. Shared by the two client creation paths — {@link
 * CustomerService#createCustomer} and the take-on path {@code TakenOnCustomerService} (Phase 93
 * 655A extracted it unchanged from {@code createCustomer}).
 */
@Component
class CustomerFieldGroupAutoApply {

  private final FieldGroupService fieldGroupService;

  CustomerFieldGroupAutoApply(FieldGroupService fieldGroupService) {
    this.fieldGroupService = fieldGroupService;
  }

  /**
   * Merges the auto-apply group ids into {@code customer}'s applied groups (keeping any already
   * applied, in order). OBS-5004: the entity-type value scopes groups (e.g. TRUST) to clients of
   * that entity type.
   */
  void apply(Customer customer, String entityType) {
    var autoApplyIds =
        fieldGroupService.resolveAutoApplyGroupIds(EntityType.CUSTOMER, null, entityType);
    if (autoApplyIds.isEmpty()) {
      return;
    }
    var merged =
        new ArrayList<>(
            customer.getAppliedFieldGroups() != null
                ? customer.getAppliedFieldGroups()
                : List.<UUID>of());
    for (UUID id : autoApplyIds) {
      if (!merged.contains(id)) {
        merged.add(id);
      }
    }
    customer.setAppliedFieldGroups(merged);
  }
}
