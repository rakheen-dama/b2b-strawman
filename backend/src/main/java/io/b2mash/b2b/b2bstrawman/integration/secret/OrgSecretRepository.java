package io.b2mash.b2b.b2bstrawman.integration.secret;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrgSecretRepository extends JpaRepository<OrgSecret, UUID> {

  Optional<OrgSecret> findBySecretKey(String secretKey);

  void deleteBySecretKey(String secretKey);

  boolean existsBySecretKey(String secretKey);
}
