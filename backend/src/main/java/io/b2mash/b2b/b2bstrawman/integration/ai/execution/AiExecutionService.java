package io.b2mash.b2b.b2bstrawman.integration.ai.execution;

import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
public class AiExecutionService {

  private final AiExecutionRepository repository;

  public AiExecutionService(AiExecutionRepository repository) {
    this.repository = repository;
  }

  public Page<AiExecution> listExecutions(String skillId, String status, Pageable pageable) {
    if (skillId != null && status != null) {
      return repository.findBySkillIdAndStatusOrderByCreatedAtDesc(skillId, status, pageable);
    } else if (skillId != null) {
      return repository.findBySkillIdOrderByCreatedAtDesc(skillId, pageable);
    } else if (status != null) {
      return repository.findByStatusOrderByCreatedAtDesc(status, pageable);
    } else {
      return repository.findAllByOrderByCreatedAtDesc(pageable);
    }
  }

  public AiExecution getExecution(UUID id) {
    return repository
        .findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("AiExecution", id.toString()));
  }
}
