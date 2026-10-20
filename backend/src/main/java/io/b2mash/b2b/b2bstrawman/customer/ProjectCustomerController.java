package io.b2mash.b2b.b2bstrawman.customer;

import io.b2mash.b2b.b2bstrawman.customer.dto.ClientDtos.ClientMatterResponse;
import io.b2mash.b2b.b2bstrawman.multitenancy.ActorContext;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/matters/{matterId}/clients")
public class ProjectCustomerController {

  private final CustomerProjectService customerProjectService;

  public ProjectCustomerController(CustomerProjectService customerProjectService) {
    this.customerProjectService = customerProjectService;
  }

  @GetMapping
  public ResponseEntity<List<LinkedClientResponse>> listCustomersForProject(
      @PathVariable UUID matterId, ActorContext actor) {

    var customers = customerProjectService.listCustomersForProject(matterId, actor);
    return ResponseEntity.ok(customers.stream().map(LinkedClientResponse::from).toList());
  }

  @PostMapping("/{clientId}")
  public ResponseEntity<ClientMatterResponse> linkCustomerToProject(
      @PathVariable UUID matterId, @PathVariable UUID clientId, ActorContext actor) {

    var link =
        customerProjectService.linkCustomerToProject(clientId, matterId, actor.memberId(), actor);
    return ResponseEntity.created(URI.create("/api/matters/" + matterId + "/clients/" + clientId))
        .body(ClientMatterResponse.from(link));
  }

  @DeleteMapping("/{clientId}")
  public ResponseEntity<Void> unlinkCustomerFromProject(
      @PathVariable UUID matterId, @PathVariable UUID clientId, ActorContext actor) {

    customerProjectService.unlinkCustomerFromProject(clientId, matterId, actor);
    return ResponseEntity.noContent().build();
  }

  public record LinkedClientResponse(
      UUID id, String name, String email, String phone, String status, Instant createdAt) {

    public static LinkedClientResponse from(Customer customer) {
      return new LinkedClientResponse(
          customer.getId(),
          customer.getName(),
          customer.getEmail(),
          customer.getPhone(),
          customer.getStatus(),
          customer.getCreatedAt());
    }
  }
}
