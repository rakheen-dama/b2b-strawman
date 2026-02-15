package io.b2mash.b2b.b2bstrawman.template;

import io.b2mash.b2b.b2bstrawman.exception.ForbiddenException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.member.MemberRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GeneratedDocumentService {

  private static final Logger log = LoggerFactory.getLogger(GeneratedDocumentService.class);

  private final GeneratedDocumentRepository generatedDocumentRepository;
  private final DocumentTemplateRepository documentTemplateRepository;
  private final MemberRepository memberRepository;

  public GeneratedDocumentService(
      GeneratedDocumentRepository generatedDocumentRepository,
      DocumentTemplateRepository documentTemplateRepository,
      MemberRepository memberRepository) {
    this.generatedDocumentRepository = generatedDocumentRepository;
    this.documentTemplateRepository = documentTemplateRepository;
    this.memberRepository = memberRepository;
  }

  @Transactional
  public GeneratedDocument create(
      UUID templateId,
      TemplateEntityType entityType,
      UUID entityId,
      String fileName,
      String s3Key,
      long fileSize,
      UUID generatedBy,
      Map<String, Object> contextSnapshot) {
    var generatedDocument =
        new GeneratedDocument(
            templateId, entityType, entityId, fileName, s3Key, fileSize, generatedBy);
    generatedDocument.setContextSnapshot(contextSnapshot);
    generatedDocument = generatedDocumentRepository.save(generatedDocument);
    log.info(
        "Created generated document: id={}, template={}, entity={}/{}",
        generatedDocument.getId(),
        templateId,
        entityType,
        entityId);
    return generatedDocument;
  }

  @Transactional(readOnly = true)
  public List<GeneratedDocumentListResponse> listByEntity(
      TemplateEntityType entityType, UUID entityId) {
    var documents =
        generatedDocumentRepository.findByPrimaryEntityTypeAndPrimaryEntityIdOrderByGeneratedAtDesc(
            entityType, entityId);
    return documents.stream().map(this::toListResponse).toList();
  }

  @Transactional(readOnly = true)
  public GeneratedDocument getById(UUID id) {
    return generatedDocumentRepository
        .findOneById(id)
        .orElseThrow(() -> new ResourceNotFoundException("GeneratedDocument", id));
  }

  @Transactional
  public void delete(UUID id, String orgRole) {
    if (!"admin".equalsIgnoreCase(orgRole) && !"owner".equalsIgnoreCase(orgRole)) {
      throw new ForbiddenException(
          "Insufficient permissions", "Only admins and owners can delete generated documents");
    }
    var doc =
        generatedDocumentRepository
            .findOneById(id)
            .orElseThrow(() -> new ResourceNotFoundException("GeneratedDocument", id));
    generatedDocumentRepository.delete(doc);
    log.info("Deleted generated document: id={}", id);
  }

  private GeneratedDocumentListResponse toListResponse(GeneratedDocument gd) {
    String templateName = resolveTemplateName(gd.getTemplateId());
    String generatedByName = resolveGeneratedByName(gd.getGeneratedBy());
    return new GeneratedDocumentListResponse(
        gd.getId(),
        templateName,
        gd.getPrimaryEntityType(),
        gd.getPrimaryEntityId(),
        gd.getFileName(),
        gd.getFileSize(),
        generatedByName,
        gd.getGeneratedAt(),
        gd.getDocumentId());
  }

  private String resolveTemplateName(UUID templateId) {
    return documentTemplateRepository
        .findOneById(templateId)
        .map(DocumentTemplate::getName)
        .orElse("Deleted Template");
  }

  private String resolveGeneratedByName(UUID memberId) {
    return memberRepository.findOneById(memberId).map(m -> m.getName()).orElse("Unknown");
  }

  /** DTO for generated document list responses. */
  public record GeneratedDocumentListResponse(
      UUID id,
      String templateName,
      TemplateEntityType primaryEntityType,
      UUID primaryEntityId,
      String fileName,
      long fileSize,
      String generatedByName,
      Instant generatedAt,
      UUID documentId) {}
}
