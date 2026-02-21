package io.b2mash.b2b.b2bstrawman.compliance;

import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import java.io.IOException;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service
public class CompliancePackService {

  private final ObjectMapper objectMapper;

  public CompliancePackService(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public CompliancePackDefinition getPackDefinition(String packId) {
    if (packId.contains("..") || packId.contains("/") || packId.contains("\\")) {
      throw new ResourceNotFoundException("CompliancePack", packId);
    }

    var resource = new ClassPathResource("compliance-packs/" + packId + "/pack.json");
    if (!resource.exists()) {
      throw new ResourceNotFoundException("CompliancePack", packId);
    }

    try {
      return objectMapper.readValue(resource.getInputStream(), CompliancePackDefinition.class);
    } catch (IOException e) {
      throw new ResourceNotFoundException("CompliancePack", packId);
    }
  }
}
