package io.b2mash.b2b.b2bstrawman.document;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentRepository extends JpaRepository<Document, UUID> {

  List<Document> findByProjectId(UUID projectId);
}
