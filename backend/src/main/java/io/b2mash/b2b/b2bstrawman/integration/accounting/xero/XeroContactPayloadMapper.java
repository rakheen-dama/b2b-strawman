package io.b2mash.b2b.b2bstrawman.integration.accounting.xero;

import io.b2mash.b2b.b2bstrawman.integration.accounting.CustomerSyncRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Pure-function mapper that translates a Kazi {@link CustomerSyncRequest} into the JSON shape
 * expected by the Xero Contacts API.
 */
@Component
public class XeroContactPayloadMapper {

  /**
   * Builds a Xero contact payload from the given customer sync request.
   *
   * @param request the customer sync request containing customer details
   * @return a map representing the Xero contact JSON payload
   */
  public Map<String, Object> map(CustomerSyncRequest request) {
    var contact = new HashMap<String, Object>();
    contact.put("Name", request.customerName());

    if (request.email() != null) {
      contact.put("EmailAddress", request.email());
    }

    if (request.externalReference() != null) {
      contact.put("AccountNumber", request.externalReference());
    }

    // Build address if any address fields are present
    if (request.addressLine1() != null || request.city() != null || request.postalCode() != null) {
      var address = new HashMap<String, Object>();
      address.put("AddressType", "POBOX");
      if (request.addressLine1() != null) {
        address.put("AddressLine1", request.addressLine1());
      }
      if (request.addressLine2() != null) {
        address.put("AddressLine2", request.addressLine2());
      }
      if (request.city() != null) {
        address.put("City", request.city());
      }
      if (request.postalCode() != null) {
        address.put("PostalCode", request.postalCode());
      }
      if (request.country() != null) {
        address.put("Country", request.country());
      }
      contact.put("Addresses", List.of(address));
    }

    return contact;
  }
}
