package io.b2mash.b2b.b2bstrawman.orgrole;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrgRoleRepository extends JpaRepository<OrgRole, UUID> {

  Optional<OrgRole> findBySlug(String slug);

  List<OrgRole> findByIsSystem(boolean isSystem);

  boolean existsByNameIgnoreCase(String name);

  boolean existsBySlugAndIdNot(String slug, UUID id);

  boolean existsByNameIgnoreCaseAndIdNot(String name, UUID id);

  /**
   * The role row {@code FOR UPDATE}: a capability update reads what the role holds and writes the
   * whole list back, so concurrent updates are serialised (FR-4 — an owner's removal must not be
   * undone by an admin's update that read the role before it).
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select r from OrgRole r where r.id = :id")
  Optional<OrgRole> findByIdForUpdate(@Param("id") UUID id);
}
