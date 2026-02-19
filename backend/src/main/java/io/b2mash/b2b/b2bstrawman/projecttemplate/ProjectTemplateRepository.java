package io.b2mash.b2b.b2bstrawman.projecttemplate;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectTemplateRepository extends JpaRepository<ProjectTemplate, UUID> {
  List<ProjectTemplate> findByActiveOrderByNameAsc(boolean active);

  List<ProjectTemplate> findAllByOrderByNameAsc();
}
