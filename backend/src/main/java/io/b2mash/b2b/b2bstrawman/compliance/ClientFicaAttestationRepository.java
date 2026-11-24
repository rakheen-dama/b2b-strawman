package io.b2mash.b2b.b2bstrawman.compliance;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Client FICA attestations (Phase 93 655A). Tenant-scoped by the schema {@code search_path}. */
public interface ClientFicaAttestationRepository
    extends JpaRepository<ClientFicaAttestation, UUID> {

  Optional<ClientFicaAttestation> findByCustomerIdAndStatus(
      UUID customerId, FicaAttestationStatus status);

  List<ClientFicaAttestation> findByCustomerIdInAndStatus(
      Collection<UUID> customerIds, FicaAttestationStatus status);

  /** Who attested: the name/role/statement snapshot taken at sign-off (655B, C-7). */
  interface SignOffSnapshot {
    String getMemberName();

    String getRoleName();

    String getStatementVersion();
  }

  /**
   * The sign-off snapshot behind an attestation's {@code sign_off_id}. A native read of {@code
   * take_on_sign_offs} so {@code compliance} does not import {@code takeon} (D-104).
   */
  @Query(
      nativeQuery = true,
      value =
          """
      SELECT s.member_name AS memberName, s.role_name AS roleName,
             s.statement_version AS statementVersion
      FROM take_on_sign_offs s
      WHERE s.id = :signOffId
      """)
  Optional<SignOffSnapshot> findSignOffSnapshot(@Param("signOffId") UUID signOffId);
}
