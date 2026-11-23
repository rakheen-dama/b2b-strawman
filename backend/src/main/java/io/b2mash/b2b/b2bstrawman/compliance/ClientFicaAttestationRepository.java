package io.b2mash.b2b.b2bstrawman.compliance;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Client FICA attestations (Phase 93 655A). Tenant-scoped by the schema {@code search_path}. */
public interface ClientFicaAttestationRepository
    extends JpaRepository<ClientFicaAttestation, UUID> {

  Optional<ClientFicaAttestation> findByCustomerIdAndStatus(
      UUID customerId, FicaAttestationStatus status);

  List<ClientFicaAttestation> findByCustomerIdInAndStatus(
      Collection<UUID> customerIds, FicaAttestationStatus status);
}
