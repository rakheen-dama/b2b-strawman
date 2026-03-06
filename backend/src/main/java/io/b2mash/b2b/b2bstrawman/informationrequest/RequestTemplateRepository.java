package io.b2mash.b2b.b2bstrawman.informationrequest;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RequestTemplateRepository extends JpaRepository<RequestTemplate, UUID> {

  List<RequestTemplate> findByActiveOrderByCreatedAtDesc(boolean active);

  List<RequestTemplate> findByPackId(String packId);
}
