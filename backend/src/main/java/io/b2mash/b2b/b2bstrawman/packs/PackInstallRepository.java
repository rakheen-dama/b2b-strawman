package io.b2mash.b2b.b2bstrawman.packs;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PackInstallRepository extends JpaRepository<PackInstall, UUID> {
  Optional<PackInstall> findByPackId(String packId);
}
