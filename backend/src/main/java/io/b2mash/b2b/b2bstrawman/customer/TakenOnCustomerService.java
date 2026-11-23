package io.b2mash.b2b.b2bstrawman.customer;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.fielddefinition.CustomFieldValidator;
import io.b2mash.b2b.b2bstrawman.fielddefinition.EntityType;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnAuditDetails;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The one documented path that creates a client born {@code ACTIVE} (Phase 93 655A, §17.3.3,
 * ADR-389): the take-on commit. Guarded by ArchUnit ({@code TakeOnEntryPointsArchTest}) — callers
 * live only in {@code ..takeon..}. {@link CustomerService#createCustomer} keeps its {@code
 * PROSPECT} default; nothing here weakens the UI path.
 *
 * <p>What does <b>not</b> happen, by design: no {@code CustomerCreatedEvent} (so no portal contact
 * and no accounting push — the firm's accounting system already has these clients), no lifecycle
 * transition (so no onboarding checklist, no {@code customer.lifecycle.transitioned}, no {@code
 * CustomerStatusChangedEvent} and no automation). The {@code customer.created} audit row carries
 * {@code {source: TAKE_ON, take_on_batch_id}} only — never the name or email — and nothing here
 * logs a name or email (§17.6.5).
 */
@Service
public class TakenOnCustomerService {

  private static final Logger log = LoggerFactory.getLogger(TakenOnCustomerService.class);

  /** The parked-columns note kept on the record (C-14). */
  public static final String PARKED_KEY = "takeon_parked";

  /** One client to take on, typed by the take-on dry run. Unmapped fields are null (C-20). */
  public record TakenOnClient(
      String name,
      String email,
      String phone,
      String idNumber,
      CustomerType type,
      String registrationNumber,
      String taxNumber,
      String addressLine1,
      String addressLine2,
      String city,
      String postalCode,
      String country,
      Map<String, Object> customFields,
      Map<String, Object> parkedColumns) {}

  /** Either the client created, or the id of the client that already holds the email. */
  public record TakenOnOutcome(Customer created, UUID existingClientId) {

    public boolean emailTaken() {
      return created == null;
    }
  }

  private final CustomerRepository repository;
  private final CustomFieldValidator customFieldValidator;
  private final CustomerFieldGroupAutoApply fieldGroupAutoApply;
  private final AuditService auditService;
  private final Clock clock;

  public TakenOnCustomerService(
      CustomerRepository repository,
      CustomFieldValidator customFieldValidator,
      CustomerFieldGroupAutoApply fieldGroupAutoApply,
      AuditService auditService,
      Clock clock) {
    this.repository = repository;
    this.customFieldValidator = customFieldValidator;
    this.fieldGroupAutoApply = fieldGroupAutoApply;
    this.auditService = auditService;
    this.clock = clock;
  }

  /**
   * Creates the taken-on client in the caller's transaction (the take-on row's own, {@code
   * MANDATORY}). An email another client already holds (case-insensitive) is an outcome, not an
   * exception, so the row can be marked {@code FAILED} in the same transaction. A custom-field
   * refusal throws (the row transaction rolls back and the runner marks the row {@code FAILED}).
   */
  @Transactional(propagation = Propagation.MANDATORY)
  public TakenOnOutcome createTakenOn(TakenOnClient spec, UUID takeOnBatchId, UUID actorMemberId) {
    Objects.requireNonNull(spec, "spec");
    Objects.requireNonNull(takeOnBatchId, "takeOnBatchId");
    Objects.requireNonNull(actorMemberId, "actorMemberId");
    var existing = repository.findFirstByEmailIgnoreCase(spec.email());
    if (existing.isPresent()) {
      return new TakenOnOutcome(null, existing.get().getId());
    }

    Map<String, Object> validated =
        new LinkedHashMap<>(
            customFieldValidator.validate(
                EntityType.CUSTOMER,
                spec.customFields() != null ? spec.customFields() : new HashMap<>(),
                null));
    if (spec.parkedColumns() != null && !spec.parkedColumns().isEmpty()) {
      // After validation: the validator strips slugs it does not know, and there is no field
      // definition for the parked-columns note (C-14).
      validated.put(PARKED_KEY, new LinkedHashMap<>(spec.parkedColumns()));
    }

    Instant now = clock.instant();
    var customer =
        Customer.takenOn(
            spec.name(),
            spec.email(),
            spec.phone(),
            spec.idNumber(),
            actorMemberId,
            spec.type(),
            takeOnBatchId,
            now);
    customer.setCustomFields(validated);
    customer.setRegistrationNumber(spec.registrationNumber());
    customer.setTaxNumber(spec.taxNumber());
    customer.setAddressLine1(spec.addressLine1());
    customer.setAddressLine2(spec.addressLine2());
    customer.setCity(spec.city());
    customer.setPostalCode(spec.postalCode());
    customer.setCountry(spec.country());
    fieldGroupAutoApply.apply(customer, customer.getEntityType());
    customer = repository.saveAndFlush(customer); // a uq_customers_email race surfaces here

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("customer.created")
            // ADR-342: storage key, do not rename
            .entityType("customer")
            .entityId(customer.getId())
            .details(
                TakeOnAuditDetails.builder()
                    .token("source", "TAKE_ON")
                    .put("take_on_batch_id", takeOnBatchId)
                    .build())
            .build());
    log.debug("Took on client {} in batch {}", customer.getId(), takeOnBatchId); // ids only
    return new TakenOnOutcome(customer, null);
  }
}
