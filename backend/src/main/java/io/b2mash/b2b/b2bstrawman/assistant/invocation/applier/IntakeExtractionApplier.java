package io.b2mash.b2b.b2bstrawman.assistant.invocation.applier;

import io.b2mash.b2b.b2bstrawman.assistant.invocation.payload.IntakeExtractionPayload;
import io.b2mash.b2b.b2bstrawman.customer.CustomerService;
import java.time.LocalDate;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Applies an approved intake extraction to the customer record. Delegates to {@link
 * CustomerService#updateCustomer} with the proposed fields.
 */
@Component("intakeExtractionApplier")
public class IntakeExtractionApplier implements OutputApplier<IntakeExtractionPayload> {

  private static final Logger log = LoggerFactory.getLogger(IntakeExtractionApplier.class);

  private final CustomerService customerService;

  public IntakeExtractionApplier(CustomerService customerService) {
    this.customerService = customerService;
  }

  @Override
  public Class<IntakeExtractionPayload> payloadType() {
    return IntakeExtractionPayload.class;
  }

  @Override
  public void apply(IntakeExtractionPayload payload, UUID actorId) {
    if (!"customer".equals(payload.contextEntityType())) {
      throw new IllegalArgumentException(
          "IntakeExtractionApplier only supports customer entities, got: "
              + payload.contextEntityType());
    }

    var fields = payload.proposedFields();
    var customerId = payload.contextEntityId();

    // Fetch current customer to fill in unchanged fields
    var customer = customerService.getCustomer(customerId);

    customerService.updateCustomer(
        customerId,
        getStringOrDefault(fields, "name", customer.getName()),
        getStringOrDefault(fields, "email", customer.getEmail()),
        getStringOrDefault(fields, "phone", customer.getPhone()),
        getStringOrDefault(fields, "idNumber", customer.getIdNumber()),
        getStringOrDefault(fields, "notes", customer.getNotes()),
        null, // customFields — not modified by intake extraction
        null, // appliedFieldGroups — not modified by intake extraction
        getStringOrDefault(fields, "registrationNumber", customer.getRegistrationNumber()),
        getStringOrDefault(fields, "addressLine1", customer.getAddressLine1()),
        getStringOrDefault(fields, "addressLine2", customer.getAddressLine2()),
        getStringOrDefault(fields, "city", customer.getCity()),
        getStringOrDefault(fields, "stateProvince", customer.getStateProvince()),
        getStringOrDefault(fields, "postalCode", customer.getPostalCode()),
        getStringOrDefault(fields, "country", customer.getCountry()),
        getStringOrDefault(fields, "taxNumber", customer.getTaxNumber()),
        getStringOrDefault(fields, "contactName", customer.getContactName()),
        getStringOrDefault(fields, "contactEmail", customer.getContactEmail()),
        getStringOrDefault(fields, "contactPhone", customer.getContactPhone()),
        getStringOrDefault(fields, "entityType", customer.getEntityType()),
        getLocalDateOrDefault(fields, "financialYearEnd", customer.getFinancialYearEnd()));
  }

  private static String getStringOrDefault(
      java.util.Map<String, Object> fields, String key, String defaultValue) {
    var value = fields.get(key);
    return value != null ? value.toString() : defaultValue;
  }

  private static LocalDate getLocalDateOrDefault(
      java.util.Map<String, Object> fields, String key, LocalDate defaultValue) {
    var value = fields.get(key);
    if (value == null) return defaultValue;
    if (value instanceof LocalDate ld) return ld;
    try {
      return LocalDate.parse(value.toString());
    } catch (Exception e) {
      log.warn(
          "Failed to parse date for field '{}': value='{}' — falling back to default", key, value);
      return defaultValue;
    }
  }
}
