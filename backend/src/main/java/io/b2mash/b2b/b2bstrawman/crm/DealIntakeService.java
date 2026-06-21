package io.b2mash.b2b.b2bstrawman.crm;

import io.b2mash.b2b.b2bstrawman.crm.dto.DealResponse;
import io.b2mash.b2b.b2bstrawman.crm.dto.IntakeRequest;
import io.b2mash.b2b.b2bstrawman.customer.Customer;
import io.b2mash.b2b.b2bstrawman.customer.CustomerService;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Atomic deal intake (Phase 80, slice 574A; ADR-313). In a single transaction it either attaches a
 * deal to an existing customer or creates a PROSPECT customer (reusing {@link CustomerService} — no
 * duplicated validation) and then opens a deal at the first OPEN stage.
 *
 * <p>The {@link IntakeRequest} is deliberately UI-decoupled so the {@code intake-triage} AI seam
 * can call this same path programmatically.
 */
@Service
public class DealIntakeService {

  private final CustomerService customerService;
  private final DealService dealService;

  public DealIntakeService(CustomerService customerService, DealService dealService) {
    this.customerService = customerService;
    this.dealService = dealService;
  }

  @Transactional
  public DealResponse intake(IntakeRequest request, UUID actingMemberId) {
    UUID customerId = resolveCustomer(request, actingMemberId);

    var stage = dealService.resolveStageForIntake(request.stageId());
    var currency = dealService.resolveCurrencyForIntake();
    UUID ownerId = request.ownerId() != null ? request.ownerId() : actingMemberId;

    return dealService.persistIntakeDeal(
        customerId,
        request.title(),
        stage,
        request.valueAmount(),
        currency,
        ownerId,
        request.source(),
        request.expectedCloseDate(),
        actingMemberId);
  }

  private UUID resolveCustomer(IntakeRequest request, UUID actingMemberId) {
    boolean hasExisting = request.customerId() != null;
    boolean hasInline = request.customer() != null;
    if (hasExisting == hasInline) {
      throw new InvalidStateException(
          "Invalid intake request",
          "Exactly one of customerId or customer (inline) must be supplied");
    }
    if (hasExisting) {
      // 404 if the customer does not exist in this tenant.
      return customerService.getCustomer(request.customerId()).getId();
    }
    var newCustomer = request.customer();
    Customer created =
        customerService.createCustomer(
            newCustomer.name(),
            newCustomer.email(),
            newCustomer.phone(),
            null,
            null,
            actingMemberId);
    return created.getId();
  }
}
