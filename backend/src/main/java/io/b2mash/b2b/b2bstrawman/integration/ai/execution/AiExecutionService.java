package io.b2mash.b2b.b2bstrawman.integration.ai.execution;

import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

  /**
   * Records a synthetic, zero-cost {@link AiExecution} for a Bring-Your-Own-Claude MCP proposal
   * (Epic 585, ADR-322). Used by the {@code propose_task} write tool: the firm's own Claude did the
   * reasoning over the MCP server, so Kazi made no provider call and there is nothing to meter. The
   * persisted row exists solely to satisfy the {@code execution_id NOT NULL} foreign key on the
   * gate it backs; its {@code cost_cents = 0} is the BYOC cost-model signal. Status is the terminal
   * {@code EXTERNALLY_EXECUTED}.
   *
   * @param memberId the authenticated MCP member who invoked the proposal
   * @param correspondenceId the filed email the task is proposed from (the execution's {@code
   *     entity_id})
   */
  @Transactional
  public AiExecution recordSyntheticMcpExecution(UUID memberId, UUID correspondenceId) {
    return repository.save(AiExecution.syntheticMcpProposal(memberId, correspondenceId));
  }
}
