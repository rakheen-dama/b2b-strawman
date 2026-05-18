package io.b2mash.b2b.b2bstrawman.integration.accounting.xero;

import io.b2mash.b2b.b2bstrawman.integration.accounting.AccountingTaxCodeMapping;
import io.b2mash.b2b.b2bstrawman.integration.accounting.InvoiceSyncRequest;
import io.b2mash.b2b.b2bstrawman.integration.accounting.LineItem;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Pure-function mapper that translates a Kazi {@link InvoiceSyncRequest} into the JSON shape
 * expected by the Xero Invoices API.
 */
@Component
public class XeroInvoicePayloadMapper {

  private static final String DEFAULT_TAX_TYPE = "NONE";

  /**
   * Builds a Xero invoice payload from the given sync request and tax code mappings.
   *
   * @param request the invoice sync request containing invoice details
   * @param taxMappings the tax code mappings for resolving Kazi tax modes to Xero tax types
   * @return a map representing the Xero invoice JSON payload
   */
  public Map<String, Object> map(
      InvoiceSyncRequest request, List<AccountingTaxCodeMapping> taxMappings) {

    var payload = new HashMap<String, Object>();

    payload.put("Type", "ACCREC");
    payload.put("Status", "AUTHORISED");

    // Contact
    var contact = new HashMap<String, Object>();
    contact.put("Name", request.customerName());
    if (request.customerEmail() != null) {
      contact.put("EmailAddress", request.customerEmail());
    }
    payload.put("Contact", contact);

    // Dates (ISO format: YYYY-MM-DD)
    payload.put("Date", request.issueDate().toString());
    payload.put("DueDate", request.dueDate().toString());

    // Idempotency reference (ADR-278)
    if (request.externalReference() != null) {
      payload.put("Reference", request.externalReference());
    }

    // Line items
    var lineItems = new ArrayList<Map<String, Object>>();
    for (LineItem lineItem : request.lineItems()) {
      var xeroLineItem = new HashMap<String, Object>();
      xeroLineItem.put("Description", lineItem.description());
      xeroLineItem.put("Quantity", lineItem.quantity());
      xeroLineItem.put("UnitAmount", lineItem.unitPrice());
      xeroLineItem.put("TaxType", resolveTaxType(lineItem.taxMode(), taxMappings));
      lineItems.add(xeroLineItem);
    }
    payload.put("LineItems", lineItems);

    return payload;
  }

  /**
   * Resolves a Kazi tax mode to the corresponding Xero tax type using the provided mappings. Falls
   * back to {@code "NONE"} if no mapping is found or the tax mode is null.
   */
  private String resolveTaxType(String kaziTaxMode, List<AccountingTaxCodeMapping> taxMappings) {
    if (kaziTaxMode == null) {
      return DEFAULT_TAX_TYPE;
    }
    return taxMappings.stream()
        .filter(m -> kaziTaxMode.equals(m.getKaziTaxMode()))
        .map(AccountingTaxCodeMapping::getExternalTaxCode)
        .findFirst()
        .orElse(DEFAULT_TAX_TYPE);
  }
}
