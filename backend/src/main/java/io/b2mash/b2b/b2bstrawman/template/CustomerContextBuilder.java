package io.b2mash.b2b.b2bstrawman.template;

import io.b2mash.b2b.b2bstrawman.customer.CustomerProjectRepository;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class CustomerContextBuilder implements TemplateContextBuilder {

  private final CustomerRepository customerRepository;
  private final CustomerProjectRepository customerProjectRepository;
  private final ProjectRepository projectRepository;
  private final TemplateContextHelper contextHelper;

  public CustomerContextBuilder(
      CustomerRepository customerRepository,
      CustomerProjectRepository customerProjectRepository,
      ProjectRepository projectRepository,
      TemplateContextHelper contextHelper) {
    this.customerRepository = customerRepository;
    this.customerProjectRepository = customerProjectRepository;
    this.projectRepository = projectRepository;
    this.contextHelper = contextHelper;
  }

  @Override
  public TemplateEntityType supports() {
    return TemplateEntityType.CUSTOMER;
  }

  @Override
  public Map<String, Object> buildContext(UUID entityId, UUID memberId) {
    var customer =
        customerRepository
            .findOneById(entityId)
            .orElseThrow(() -> new ResourceNotFoundException("Customer", entityId));

    var context = new HashMap<String, Object>();

    // customer.*
    var customerMap = new LinkedHashMap<String, Object>();
    customerMap.put("id", customer.getId());
    customerMap.put("name", customer.getName());
    customerMap.put("email", customer.getEmail());
    customerMap.put("phone", customer.getPhone());
    customerMap.put("status", customer.getStatus());
    customerMap.put(
        "customFields", customer.getCustomFields() != null ? customer.getCustomFields() : Map.of());
    context.put("customer", customerMap);

    // projects[] (linked via CustomerProject)
    var customerProjects = customerProjectRepository.findByCustomerId(entityId);
    if (!customerProjects.isEmpty()) {
      var projectIds = customerProjects.stream().map(cp -> cp.getProjectId()).toList();
      var projects = projectRepository.findAllByIds(projectIds);
      var projectsList =
          projects.stream()
              .map(
                  p -> {
                    var pm = new LinkedHashMap<String, Object>();
                    pm.put("id", p.getId());
                    pm.put("name", p.getName());
                    return (Map<String, Object>) pm;
                  })
              .toList();
      context.put("projects", projectsList);
    } else {
      context.put("projects", List.of());
    }

    // org.*
    context.put("org", contextHelper.buildOrgContext());

    // tags[]
    context.put("tags", contextHelper.buildTagsList("CUSTOMER", entityId));

    // generatedAt, generatedBy
    context.put("generatedAt", Instant.now().toString());
    context.put("generatedBy", contextHelper.buildGeneratedByMap(memberId));

    return context;
  }
}
