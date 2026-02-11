package io.b2mash.b2b.b2bstrawman.customer;

import io.b2mash.b2b.b2bstrawman.exception.ResourceConflictException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerService {

  private static final Logger log = LoggerFactory.getLogger(CustomerService.class);

  private final CustomerRepository repository;

  public CustomerService(CustomerRepository repository) {
    this.repository = repository;
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

    customer.update(name, email, phone, idNumber, notes);
    return repository.save(customer);
  }

  @Transactional
  public Customer archiveCustomer(UUID id) {
    var customer =
        repository.findOneById(id).orElseThrow(() -> new ResourceNotFoundException("Customer", id));
    customer.archive();
    return repository.save(customer);
  }
}
