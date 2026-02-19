package io.b2mash.b2b.b2bstrawman.projecttemplate;

import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/**
 * Repository for the template_tags join table. Uses EntityManager native queries instead of
 * JdbcClient so that queries go through Hibernate's multitenancy connection provider (which sets
 * search_path to the correct tenant schema).
 */
@Repository
@Transactional
public class TemplateTagRepository {

  private final EntityManager entityManager;

  public TemplateTagRepository(EntityManager entityManager) {
    this.entityManager = entityManager;
  }

  public void save(UUID templateId, UUID tagId) {
    entityManager
        .createNativeQuery(
            "INSERT INTO template_tags (template_id, tag_id) VALUES (?1, ?2) ON CONFLICT DO NOTHING")
        .setParameter(1, templateId)
        .setParameter(2, tagId)
        .executeUpdate();
  }

  @SuppressWarnings("unchecked")
  public List<UUID> findTagIdsByTemplateId(UUID templateId) {
    return entityManager
        .createNativeQuery("SELECT tag_id FROM template_tags WHERE template_id = ?1", UUID.class)
        .setParameter(1, templateId)
        .getResultList();
  }

  public void deleteByTemplateId(UUID templateId) {
    entityManager
        .createNativeQuery("DELETE FROM template_tags WHERE template_id = ?1")
        .setParameter(1, templateId)
        .executeUpdate();
  }

  public void deleteByTemplateIdAndTagId(UUID templateId, UUID tagId) {
    entityManager
        .createNativeQuery("DELETE FROM template_tags WHERE template_id = ?1 AND tag_id = ?2")
        .setParameter(1, templateId)
        .setParameter(2, tagId)
        .executeUpdate();
  }
}
