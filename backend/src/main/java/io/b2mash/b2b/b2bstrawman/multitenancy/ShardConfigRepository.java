package io.b2mash.b2b.b2bstrawman.multitenancy;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShardConfigRepository extends JpaRepository<ShardConfig, String> {

  List<ShardConfig> findByActiveTrue();
}
