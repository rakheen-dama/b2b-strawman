package io.b2mash.b2b.b2bstrawman.orgrole;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrgRoleRepository extends JpaRepository<OrgRole, UUID> {

  Optional<OrgRole> findBySlug(String slug);

  List<OrgRole> findByIsSystem(boolean isSystem);

  boolean existsByNameIgnoreCase(String name);

  boolean existsBySlugAndIdNot(String slug, UUID id);

  boolean existsByNameIgnoreCaseAndIdNot(String name, UUID id);
}
