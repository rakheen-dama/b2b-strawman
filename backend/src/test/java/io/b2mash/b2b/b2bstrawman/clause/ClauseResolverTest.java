package io.b2mash.b2b.b2bstrawman.clause;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.template.DocumentTemplateController.ClauseSelection;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.ErrorResponseException;

@ExtendWith(MockitoExtension.class)
class ClauseResolverTest {

  @Mock private TemplateClauseRepository templateClauseRepository;
  @Mock private ClauseRepository clauseRepository;
  @InjectMocks private ClauseResolver clauseResolver;

  private static final UUID TEMPLATE_ID = UUID.randomUUID();

  @Test
  void resolveClauses_nullSelections_loadsTemplateDefaults() {
    var clauseId1 = UUID.randomUUID();
    var clauseId2 = UUID.randomUUID();

    var assoc1 = new TemplateClause(TEMPLATE_ID, clauseId1, 0, false);
    var assoc2 = new TemplateClause(TEMPLATE_ID, clauseId2, 1, false);

    when(templateClauseRepository.findByTemplateIdOrderBySortOrderAsc(TEMPLATE_ID))
        .thenReturn(List.of(assoc1, assoc2));

    var c1 = createClauseWithId(clauseId1, "Confidentiality", "confidentiality");
    var c2 = createClauseWithId(clauseId2, "Liability", "liability");
    when(clauseRepository.findAllById(List.of(clauseId1, clauseId2))).thenReturn(List.of(c1, c2));

    var result = clauseResolver.resolveClauses(TEMPLATE_ID, null);

    assertThat(result).hasSize(2);
    assertThat(result.get(0).getSlug()).isEqualTo("confidentiality");
    assertThat(result.get(1).getSlug()).isEqualTo("liability");
  }

  @Test
  void resolveClauses_emptySelectionsAndNoAssociations_returnsEmptyList() {
    when(templateClauseRepository.findByTemplateIdOrderBySortOrderAsc(TEMPLATE_ID))
        .thenReturn(List.of());

    var result = clauseResolver.resolveClauses(TEMPLATE_ID, List.of());

    assertThat(result).isEmpty();
  }

  @Test
  void resolveClauses_providedSelections_returnsInOrder() {
    var clauseId1 = UUID.randomUUID();
    var clauseId2 = UUID.randomUUID();

    when(templateClauseRepository.findByTemplateIdOrderBySortOrderAsc(TEMPLATE_ID))
        .thenReturn(List.of()); // No required clauses

    var c1 = createClauseWithId(clauseId1, "Confidentiality", "confidentiality");
    var c2 = createClauseWithId(clauseId2, "Liability", "liability");
    when(clauseRepository.findAllById(List.of(clauseId2, clauseId1))).thenReturn(List.of(c1, c2));

    // Specify reverse order — clause2 first (sortOrder=0), clause1 second (sortOrder=1)
    var selections = List.of(new ClauseSelection(clauseId2, 0), new ClauseSelection(clauseId1, 1));

    var result = clauseResolver.resolveClauses(TEMPLATE_ID, selections);

    assertThat(result).hasSize(2);
    assertThat(result.get(0).getSlug()).isEqualTo("liability");
    assertThat(result.get(1).getSlug()).isEqualTo("confidentiality");
  }

  @Test
  void resolveClauses_missingRequiredClause_throws422() {
    var requiredClauseId = UUID.randomUUID();
    var optionalClauseId = UUID.randomUUID();

    var requiredAssoc = new TemplateClause(TEMPLATE_ID, requiredClauseId, 0, true);
    var optionalAssoc = new TemplateClause(TEMPLATE_ID, optionalClauseId, 1, false);

    when(templateClauseRepository.findByTemplateIdOrderBySortOrderAsc(TEMPLATE_ID))
        .thenReturn(List.of(requiredAssoc, optionalAssoc));

    // Only select the optional clause, omit the required one
    var selections = List.of(new ClauseSelection(optionalClauseId, 0));

    assertThatThrownBy(() -> clauseResolver.resolveClauses(TEMPLATE_ID, selections))
        .isInstanceOf(ErrorResponseException.class)
        .satisfies(
            ex -> {
              var ere = (ErrorResponseException) ex;
              assertThat(ere.getStatusCode().value()).isEqualTo(422);
              assertThat(ere.getBody().getDetail()).contains(requiredClauseId.toString());
            });
  }

  @Test
  void resolveClauses_invalidClauseId_throws400() {
    var invalidId = UUID.randomUUID();

    when(templateClauseRepository.findByTemplateIdOrderBySortOrderAsc(TEMPLATE_ID))
        .thenReturn(List.of()); // No required clauses

    when(clauseRepository.findAllById(List.of(invalidId))).thenReturn(List.of()); // Not found

    var selections = List.of(new ClauseSelection(invalidId, 0));

    assertThatThrownBy(() -> clauseResolver.resolveClauses(TEMPLATE_ID, selections))
        .isInstanceOf(InvalidStateException.class)
        .satisfies(
            ex -> {
              var ise = (InvalidStateException) ex;
              assertThat(ise.getStatusCode().value()).isEqualTo(400);
              assertThat(ise.getBody().getDetail()).contains(invalidId.toString());
            });
  }

  @Test
  void resolveClauses_nullSelectionsNoAssociations_returnsEmptyList() {
    when(templateClauseRepository.findByTemplateIdOrderBySortOrderAsc(TEMPLATE_ID))
        .thenReturn(List.of());

    var result = clauseResolver.resolveClauses(TEMPLATE_ID, null);

    assertThat(result).isEmpty();
  }

  // --- Helper ---

  private static Clause createClauseWithId(UUID id, String title, String slug) {
    var clause = new Clause(title, slug, "<p>" + title + "</p>", "General");
    try {
      var idField = Clause.class.getDeclaredField("id");
      idField.setAccessible(true);
      idField.set(clause, id);
    } catch (Exception e) {
      throw new RuntimeException("Failed to set clause ID", e);
    }
    return clause;
  }
}
