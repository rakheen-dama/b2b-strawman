package io.b2mash.b2b.b2bstrawman.integration.accounting.xero;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.customer.Customer;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.customer.CustomerType;
import io.b2mash.b2b.b2bstrawman.customer.LifecycleStatus;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceConflictException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.integration.OrgIntegration;
import io.b2mash.b2b.b2bstrawman.integration.OrgIntegrationRepository;
import io.b2mash.b2b.b2bstrawman.integration.secret.SecretStore;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * One-time bulk import of Xero contacts as Kazi customers. Paginates through the Xero Contacts API,
 * deduplicates against existing customers (by email, then by name+taxNumber), and creates new
 * customers with {@link LifecycleStatus#PROSPECT} status.
 *
 * <p>The import is guarded by a flag in {@link OrgIntegration#getConfigJson()} — once completed, a
 * second attempt throws {@link ResourceConflictException}.
 */
@Service
public class XeroCustomerImportService {

  private static final Logger log = LoggerFactory.getLogger(XeroCustomerImportService.class);
  private static final int PAGE_SIZE = 100;
  private static final int MAX_CUSTOMER_COUNT_FOR_IMPORT = 10_000;
  private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

  private final AccountingXeroConnectionRepository connectionRepository;
  private final OrgIntegrationRepository orgIntegrationRepository;
  private final XeroApiClient xeroApiClient;
  private final SecretStore secretStore;
  private final CustomerRepository customerRepository;
  private final AuditService auditService;
  private final ObjectMapper objectMapper;
  private final TransactionTemplate txTemplate;

  public XeroCustomerImportService(
      AccountingXeroConnectionRepository connectionRepository,
      OrgIntegrationRepository orgIntegrationRepository,
      XeroApiClient xeroApiClient,
      SecretStore secretStore,
      CustomerRepository customerRepository,
      AuditService auditService,
      ObjectMapper objectMapper,
      TransactionTemplate txTemplate) {
    this.connectionRepository = connectionRepository;
    this.orgIntegrationRepository = orgIntegrationRepository;
    this.xeroApiClient = xeroApiClient;
    this.secretStore = secretStore;
    this.customerRepository = customerRepository;
    this.auditService = auditService;
    this.objectMapper = objectMapper;
    this.txTemplate = txTemplate;
  }

  /**
   * Imports customers from the currently connected Xero org. Resolves the active connection
   * internally so the controller does not need to orchestrate multiple service calls.
   *
   * @param actorMemberId the member performing the import
   * @return summary of the import outcome
   * @throws ResourceNotFoundException if no active Xero connection exists
   */
  public CustomerImportSummary importCustomersFromConnectedOrg(UUID actorMemberId) {
    var connection =
        connectionRepository.findByStatus(XeroConnectionStatus.CONNECTED).stream()
            .findFirst()
            .or(
                () ->
                    connectionRepository.findByStatus(XeroConnectionStatus.REFRESH_FAILED).stream()
                        .findFirst())
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "AccountingXeroConnection", "No active Xero connection found"));
    return importCustomersFromXero(connection.getId(), actorMemberId);
  }

  /**
   * Imports contacts from the Xero organisation linked by the given connection.
   *
   * <p>The import is split into two phases to avoid holding a database connection during HTTP
   * calls:
   *
   * <ol>
   *   <li><strong>Phase 1 (non-transactional):</strong> Validate connection, check guard, fetch all
   *       Xero contacts via paginated HTTP calls.
   *   <li><strong>Phase 2 (transactional):</strong> Dedup against existing customers, create new
   *       ones, mark import completed, and emit audit event.
   * </ol>
   *
   * @param connectionId the {@link AccountingXeroConnection} ID
   * @param actorMemberId the member performing the import (used as {@code createdBy} on new
   *     customers)
   * @return summary of the import outcome
   * @throws ResourceNotFoundException if the connection or org integration does not exist
   * @throws ResourceConflictException if customers have already been imported for this connection
   */
  public CustomerImportSummary importCustomersFromXero(UUID connectionId, UUID actorMemberId) {
    // Phase 1: Validate + fetch (no long-lived transaction)
    var fetchContext = validateAndFetchContacts(connectionId);

    // Phase 2: Dedup + persist (transactional via TransactionTemplate to avoid self-invocation
    // proxy bypass)
    return txTemplate.execute(
        status ->
            dedupAndPersist(
                fetchContext.orgIntegrationId(), fetchContext.allContacts(), actorMemberId));
  }

  /**
   * Phase 1 — validates the connection and guard, then paginates through Xero to collect all
   * contacts. Runs without a transaction so the DB connection is returned between HTTP calls.
   */
  private FetchContext validateAndFetchContacts(UUID connectionId) {
    // 1. Load and validate connection
    var connection =
        connectionRepository
            .findOneById(connectionId)
            .orElseThrow(() -> new ResourceNotFoundException("XeroConnection", connectionId));

    if (connection.getStatus() != XeroConnectionStatus.CONNECTED) {
      throw new ResourceConflictException(
          "Xero connection not active",
          "Connection " + connectionId + " has status " + connection.getStatus());
    }

    // 2. One-time guard via OrgIntegration.configJson
    var orgIntegration =
        orgIntegrationRepository
            .findById(connection.getOrgIntegrationId())
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "OrgIntegration", connection.getOrgIntegrationId()));

    checkImportGuard(orgIntegration);

    // 3. Get access token
    String accessToken =
        secretStore.retrieve(connection.getOrgIntegrationId().toString() + ":xero:access");

    // 4. Paginate Xero contacts (all HTTP calls happen here, outside a transaction)
    var allContacts = new java.util.ArrayList<Map<String, Object>>();
    int page = 1;
    while (true) {
      Map<String, Object> response =
          xeroApiClient.getContacts(connection.getXeroTenantId(), page, accessToken);

      @SuppressWarnings("unchecked")
      List<Map<String, Object>> contacts = (List<Map<String, Object>>) response.get("Contacts");

      if (contacts == null || contacts.isEmpty()) {
        break;
      }
      allContacts.addAll(contacts);

      if (contacts.size() < PAGE_SIZE) {
        break;
      }
      page++;
    }

    return new FetchContext(orgIntegration.getId(), allContacts);
  }

  /**
   * Phase 2 — deduplicates fetched Xero contacts against existing customers, creates new ones,
   * marks the import as completed, and emits an audit event. Called within a {@link
   * TransactionTemplate} transaction.
   */
  private CustomerImportSummary dedupAndPersist(
      UUID orgIntegrationId, List<Map<String, Object>> allContacts, UUID actorMemberId) {

    var orgIntegration =
        orgIntegrationRepository
            .findById(orgIntegrationId)
            .orElseThrow(() -> new ResourceNotFoundException("OrgIntegration", orgIntegrationId));

    // Re-check guard inside the transaction (race-condition protection)
    checkImportGuard(orgIntegration);

    // Safety valve: refuse import if existing customer table is too large for in-memory dedup
    long customerCount = customerRepository.count();
    if (customerCount > MAX_CUSTOMER_COUNT_FOR_IMPORT) {
      throw new InvalidStateException(
          "Customer table too large for import",
          "Customer table has "
              + customerCount
              + " rows — too large for in-memory dedup (limit "
              + MAX_CUSTOMER_COUNT_FOR_IMPORT
              + "). Use a different import strategy.");
    }

    // Build dedup indexes from existing customers
    List<Customer> existingCustomers = customerRepository.findAll();
    Map<String, Customer> emailIndex =
        existingCustomers.stream()
            .filter(c -> c.getEmail() != null)
            .collect(
                Collectors.toMap(
                    c -> c.getEmail().toLowerCase(), c -> c, (a, b) -> a // keep first if duplicates
                    ));

    int created = 0;
    int skippedDuplicate = 0;
    int skippedNoEmail = 0;

    for (Map<String, Object> contact : allContacts) {
      String contactId = (String) contact.get("ContactID");
      String name = (String) contact.get("Name");
      String emailAddress = (String) contact.get("EmailAddress");
      String taxNumber = (String) contact.get("TaxNumber");

      // Skip contacts without email
      if (emailAddress == null || emailAddress.isBlank()) {
        skippedNoEmail++;
        continue;
      }

      // Dedup by email (case-insensitive)
      Customer existingByEmail = emailIndex.get(emailAddress.toLowerCase());
      if (existingByEmail != null) {
        storeXeroContactId(existingByEmail, contactId);
        customerRepository.save(existingByEmail);
        skippedDuplicate++;
        continue;
      }

      // Dedup by name + taxNumber
      Customer existingByNameTax = findByNameAndTaxNumber(existingCustomers, name, taxNumber);
      if (existingByNameTax != null) {
        storeXeroContactId(existingByNameTax, contactId);
        customerRepository.save(existingByNameTax);
        skippedDuplicate++;
        continue;
      }

      // Create new customer
      var customer =
          new Customer(
              name,
              emailAddress,
              extractPhone(contact),
              null,
              "Imported from Xero",
              actorMemberId,
              CustomerType.INDIVIDUAL,
              LifecycleStatus.PROSPECT);

      if (taxNumber != null && !taxNumber.isBlank()) {
        customer.setTaxNumber(taxNumber);
      }

      storeXeroContactId(customer, contactId);
      populateAddress(customer, contact);

      var saved = customerRepository.save(customer);
      // Add the new customer to the dedup indexes so later contacts don't create duplicates
      emailIndex.put(emailAddress.toLowerCase(), saved);
      existingCustomers.add(saved);
      created++;
    }

    int total = created + skippedDuplicate + skippedNoEmail;
    var summary = new CustomerImportSummary(created, skippedDuplicate, skippedNoEmail, total);

    // Mark import as done
    markImportCompleted(orgIntegration);

    // Emit audit event
    auditService.log(
        AuditEventBuilder.builder()
            .eventType("integration.xero.customers_imported")
            .entityType("org_integration")
            .entityId(orgIntegration.getId())
            .actorId(actorMemberId)
            .actorType("USER")
            .details(
                Map.of(
                    "created", summary.created(),
                    "skippedDuplicate", summary.skippedDuplicate(),
                    "skippedNoEmail", summary.skippedNoEmail(),
                    "total", summary.total()))
            .build());

    log.info(
        "Xero customer import completed: created={}, skippedDuplicate={}, skippedNoEmail={}, total={}",
        created,
        skippedDuplicate,
        skippedNoEmail,
        total);

    return summary;
  }

  private record FetchContext(UUID orgIntegrationId, List<Map<String, Object>> allContacts) {}

  private void checkImportGuard(OrgIntegration orgIntegration) {
    String configJson = orgIntegration.getConfigJson();
    if (configJson != null) {
      try {
        Map<String, Object> config = objectMapper.readValue(configJson, MAP_TYPE);
        if (Boolean.TRUE.equals(config.get("customersImported"))) {
          throw new ResourceConflictException(
              "Customer import already completed",
              "Customers have already been imported from Xero for this connection");
        }
      } catch (JacksonException e) {
        throw new InvalidStateException(
            "Import guard check failed",
            "OrgIntegration configJson is malformed — cannot verify import guard");
      }
    }
  }

  private void markImportCompleted(OrgIntegration orgIntegration) {
    try {
      String configJson = orgIntegration.getConfigJson();
      Map<String, Object> config =
          configJson != null
              ? new HashMap<>(objectMapper.readValue(configJson, MAP_TYPE))
              : new HashMap<>();
      config.put("customersImported", true);
      orgIntegration.setConfigJson(objectMapper.writeValueAsString(config));
      orgIntegrationRepository.save(orgIntegration);
    } catch (JacksonException e) {
      log.error("Failed to update OrgIntegration configJson after import", e);
      throw new InvalidStateException(
          "Failed to mark import as completed",
          "Could not update OrgIntegration configJson after Xero customer import");
    }
  }

  private void storeXeroContactId(Customer customer, String xeroContactId) {
    var fields =
        new HashMap<>(customer.getCustomFields() != null ? customer.getCustomFields() : Map.of());
    fields.put("xero_contact_id", xeroContactId);
    customer.setCustomFields(fields);
  }

  private Customer findByNameAndTaxNumber(
      List<Customer> customers, String xeroName, String xeroTaxNumber) {
    if (xeroName == null || xeroTaxNumber == null || xeroTaxNumber.isBlank()) {
      return null;
    }
    return customers.stream()
        .filter(
            c ->
                xeroName.equalsIgnoreCase(c.getName())
                    && c.getTaxNumber() != null
                    && c.getTaxNumber().equalsIgnoreCase(xeroTaxNumber))
        .findFirst()
        .orElse(null);
  }

  @SuppressWarnings("unchecked")
  private String extractPhone(Map<String, Object> contact) {
    var phones = (List<Map<String, Object>>) contact.get("Phones");
    if (phones == null || phones.isEmpty()) {
      return null;
    }
    // Prefer DEFAULT phone type, fall back to first available
    return phones.stream()
        .filter(p -> "DEFAULT".equals(p.get("PhoneType")))
        .map(p -> (String) p.get("PhoneNumber"))
        .filter(n -> n != null && !n.isBlank())
        .findFirst()
        .orElseGet(
            () ->
                phones.stream()
                    .map(p -> (String) p.get("PhoneNumber"))
                    .filter(n -> n != null && !n.isBlank())
                    .findFirst()
                    .orElse(null));
  }

  @SuppressWarnings("unchecked")
  private void populateAddress(Customer customer, Map<String, Object> contact) {
    var addresses = (List<Map<String, Object>>) contact.get("Addresses");
    if (addresses == null || addresses.isEmpty()) {
      return;
    }
    // Prefer POBOX address type, fall back to first
    var address =
        addresses.stream()
            .filter(a -> "POBOX".equals(a.get("AddressType")))
            .findFirst()
            .orElse(addresses.getFirst());

    String line1 = (String) address.get("AddressLine1");
    if (line1 != null && !line1.isBlank()) {
      customer.setAddressLine1(line1);
    }
    String city = (String) address.get("City");
    if (city != null && !city.isBlank()) {
      customer.setCity(city);
    }
    String postalCode = (String) address.get("PostalCode");
    if (postalCode != null && !postalCode.isBlank()) {
      customer.setPostalCode(postalCode);
    }
    // Xero returns full country names (e.g. "South Africa") which exceed the VARCHAR(2)
    // ISO-3166 alpha-2 column. Store the raw value in customFields for future mapping.
    String country = (String) address.get("Country");
    if (country != null && !country.isBlank()) {
      var fields =
          new HashMap<>(customer.getCustomFields() != null ? customer.getCustomFields() : Map.of());
      fields.put("xero_country", country);
      customer.setCustomFields(fields);
    }
  }
}
