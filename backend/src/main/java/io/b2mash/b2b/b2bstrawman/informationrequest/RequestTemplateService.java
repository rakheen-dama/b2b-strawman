package io.b2mash.b2b.b2bstrawman.informationrequest;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.informationrequest.dto.RequestTemplateDtos.CreateRequestTemplateRequest;
import io.b2mash.b2b.b2bstrawman.informationrequest.dto.RequestTemplateDtos.RequestTemplateItemRequest;
import io.b2mash.b2b.b2bstrawman.informationrequest.dto.RequestTemplateDtos.RequestTemplateResponse;
import io.b2mash.b2b.b2bstrawman.informationrequest.dto.RequestTemplateDtos.UpdateRequestTemplateRequest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RequestTemplateService {

  private static final Logger log = LoggerFactory.getLogger(RequestTemplateService.class);

  private final RequestTemplateRepository templateRepository;
  private final RequestTemplateItemRepository templateItemRepository;
  private final AuditService auditService;

  public RequestTemplateService(
      RequestTemplateRepository templateRepository,
      RequestTemplateItemRepository templateItemRepository,
      AuditService auditService) {
    this.templateRepository = templateRepository;
    this.templateItemRepository = templateItemRepository;
    this.auditService = auditService;
  }

  @Transactional(readOnly = true)
  public List<RequestTemplateResponse> listTemplates(Boolean active) {
    List<RequestTemplate> templates;
    if (active != null) {
      templates = templateRepository.findByActiveOrderByCreatedAtDesc(active);
    } else {
      templates = templateRepository.findByActiveOrderByCreatedAtDesc(true);
    }
    return templates.stream()
        .map(
            t -> {
              var items = templateItemRepository.findByTemplateIdOrderBySortOrder(t.getId());
              return RequestTemplateResponse.from(t, items);
            })
        .toList();
  }

  @Transactional(readOnly = true)
  public RequestTemplateResponse getById(UUID id) {
    var template =
        templateRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("RequestTemplate", id));
    var items = templateItemRepository.findByTemplateIdOrderBySortOrder(id);
    return RequestTemplateResponse.from(template, items);
  }

  @Transactional
  public RequestTemplateResponse create(CreateRequestTemplateRequest request) {
    var template =
        new RequestTemplate(request.name(), request.description(), TemplateSource.CUSTOM);
    template = templateRepository.save(template);

    List<RequestTemplateItem> savedItems = saveItems(template.getId(), request.items());

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("REQUEST_TEMPLATE_CREATED")
            .entityType("RequestTemplate")
            .entityId(template.getId())
            .details(Map.of("name", template.getName()))
            .build());

    log.info("Created request template '{}' with {} items", template.getName(), savedItems.size());
    return RequestTemplateResponse.from(template, savedItems);
  }

  @Transactional
  public RequestTemplateResponse update(UUID id, UpdateRequestTemplateRequest request) {
    var template =
        templateRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("RequestTemplate", id));
    template.update(request.name(), request.description());
    template = templateRepository.save(template);

    templateItemRepository.deleteByTemplateId(id);
    List<RequestTemplateItem> savedItems = saveItems(id, request.items());

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("REQUEST_TEMPLATE_UPDATED")
            .entityType("RequestTemplate")
            .entityId(template.getId())
            .details(Map.of("name", template.getName()))
            .build());

    return RequestTemplateResponse.from(template, savedItems);
  }

  @Transactional
  public void deactivate(UUID id) {
    var template =
        templateRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("RequestTemplate", id));
    template.deactivate();
    templateRepository.save(template);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("REQUEST_TEMPLATE_DEACTIVATED")
            .entityType("RequestTemplate")
            .entityId(template.getId())
            .details(Map.of("name", template.getName()))
            .build());

    log.info("Deactivated request template '{}'", template.getName());
  }

  @Transactional
  public RequestTemplateResponse duplicate(UUID templateId) {
    var original =
        templateRepository
            .findById(templateId)
            .orElseThrow(() -> new ResourceNotFoundException("RequestTemplate", templateId));
    var items = templateItemRepository.findByTemplateIdOrderBySortOrder(templateId);

    var clone =
        new RequestTemplate(
            original.getName() + " (Copy)", original.getDescription(), TemplateSource.CUSTOM);
    clone = templateRepository.save(clone);

    for (var item : items) {
      var clonedItem =
          new RequestTemplateItem(
              clone.getId(),
              item.getName(),
              item.getDescription(),
              item.getResponseType(),
              item.isRequired(),
              item.getFileTypeHints(),
              item.getSortOrder());
      templateItemRepository.save(clonedItem);
    }

    var clonedItems = templateItemRepository.findByTemplateIdOrderBySortOrder(clone.getId());

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("REQUEST_TEMPLATE_DUPLICATED")
            .entityType("RequestTemplate")
            .entityId(clone.getId())
            .details(Map.of("name", clone.getName(), "sourceTemplateId", templateId.toString()))
            .build());

    log.info("Duplicated request template '{}' -> '{}'", original.getName(), clone.getName());
    return RequestTemplateResponse.from(clone, clonedItems);
  }

  private List<RequestTemplateItem> saveItems(
      UUID templateId, List<RequestTemplateItemRequest> itemRequests) {
    if (itemRequests == null || itemRequests.isEmpty()) {
      return List.of();
    }
    return itemRequests.stream()
        .map(
            req -> {
              var item =
                  new RequestTemplateItem(
                      templateId,
                      req.name(),
                      req.description(),
                      req.responseType(),
                      req.required(),
                      req.fileTypeHints(),
                      req.sortOrder());
              return templateItemRepository.save(item);
            })
        .toList();
  }
}
