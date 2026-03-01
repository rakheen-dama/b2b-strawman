package io.b2mash.b2b.b2bstrawman.proposal.dto;

import io.b2mash.b2b.b2bstrawman.proposal.FeeModel;
import io.b2mash.b2b.b2bstrawman.proposal.ProposalStatus;
import java.util.UUID;

public record ProposalFilterCriteria(
    UUID customerId, ProposalStatus status, FeeModel feeModel, UUID createdById) {}
