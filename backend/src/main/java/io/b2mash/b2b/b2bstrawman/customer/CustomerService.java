package io.b2mash.b2b.b2bstrawman.customer;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.exception.ResourceConflictException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerService {

  private static final Logger log = LoggerFactory.getLogger(CustomerService.class);

  private final CustomerRepository repository;
  private final AuditService auditService;

  public CustomerService(CustomerRepository repository, AuditService auditService) {
    this.repository = repository;
    this.auditService = auditService;
  }

  @Transactional(readOnly = true)
  public List<Customer> listCustomers() {
    return repository.findAll();
  }

  @Transactional(readOnly = true)
  public Customer getCustomer(UUID id) {
    return repository
        .findOneById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Customer", id));
  }

  @Transactional
  public Customer createCustomer(
      String name, String email, String phone, String idNumber, String notes, UUID createdBy) {
    if (repository.existsByEmail(email)) {
      throw new ResourceConflictException(
          "Customer email conflict", "A customer with email " + email + " already exists");
    }
    var customer = repository.save(new Customer(name, email, phone, idNumber, notes, createdBy));
    log.info("Created customer {} with email {}", customer.getId(), email);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("customer.created")
            .entityType("customer")
            .entityId(customer.getId())
            .details(Map.of("name", customer.getName(), "email", customer.getEmail()))
            .build());

    return customer;
  }

  @Transactional
  public Customer updateCustomer(
      UUID id, String name, String email, String phone, String idNumber, String notes) {
    var customer =
        repository.findOneById(id).orElseThrow(() -> new ResourceNotFoundException("Customer", id));

    // Check email uniqueness if changed
    if (!customer.getEmail().equals(email) && repository.existsByEmail(email)) {
      throw new ResourceConflictException(
          "Customer email conflict", "A customer with email " + email + " already exists");
    }

    // Capture old values before mutation
    String oldName = customer.getName();
    String oldEmail = customer.getEmail();
    String oldPhone = customer.getPhone();
    String oldIdNumber = customer.getIdNumber();
    String oldNotes = customer.getNotes();

    customer.update(name, email, phone, idNumber, notes);
    var saved = repository.save(customer);

    // Build delta map -- only include changed fields
    var details = new LinkedHashMap<String, Object>();
    if (!Objects.equals(oldName, name)) {
      details.put("name", Map.of("from", oldName, "to", name));
    }
    if (!Objects.equals(oldEmail, email)) {
      details.put("email", Map.of("from", oldEmail, "to", email));
    }
    if (!Objects.equals(oldPhone, phone)) {
      details.put("phone", Map.of("from", String.valueOf(oldPhone), "to", String.valueOf(phone)));
    }
    if (!Objects.equals(oldIdNumber, idNumber)) {
      details.put(
          "id_number", Map.of("from", String.valueOf(oldIdNumber), "to", String.valueOf(idNumber)));
    }
    if (!Objects.equals(oldNotes, notes)) {
      details.put("notes", Map.of("from", String.valueOf(oldNotes), "to", String.valueOf(notes)));
    }

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("customer.updated")
            .entityType("customer")
            .entityId(saved.getId())
            .details(details.isEmpty() ? null : details)
            .build());

    return saved;
  }

  @Transactional
  public Customer archiveCustomer(UUID id) {
    var customer =
        repository.findOneById(id).orElseThrow(() -> new ResourceNotFoundException("Customer", id));
    customer.archive();
    var saved = repository.save(customer);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("customer.archived")
            .entityType("customer")
            .entityId(saved.getId())
            .build());

    return saved;
  }
}
