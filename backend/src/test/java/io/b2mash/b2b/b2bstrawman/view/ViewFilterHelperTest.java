package io.b2mash.b2b.b2bstrawman.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ViewFilterHelperTest {

  @Mock private SavedViewRepository savedViewRepository;
  @Mock private ViewFilterService viewFilterService;
  @InjectMocks private ViewFilterHelper helper;

  private static final UUID VIEW_ID = UUID.randomUUID();

  // --- applyViewFilter tests ---

  @Test
  void applyViewFilter_throwsWhenViewNotFound() {
    when(savedViewRepository.findById(VIEW_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () -> helper.applyViewFilter(VIEW_ID, "PROJECT", "projects", String.class, null, null))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  void applyViewFilter_throwsOnEntityTypeMismatch() {
    var view = savedView("TASK");
    when(savedViewRepository.findById(VIEW_ID)).thenReturn(Optional.of(view));

    assertThatThrownBy(
            () -> helper.applyViewFilter(VIEW_ID, "PROJECT", "projects", String.class, null, null))
        .isInstanceOf(InvalidStateException.class)
        .hasMessageContaining("Expected PROJECT view but got TASK");
  }

  @Test
  void applyViewFilter_returnsNullWhenFilterServiceReturnsNull() {
    var view = savedView("PROJECT");
    when(savedViewRepository.findById(VIEW_ID)).thenReturn(Optional.of(view));
    when(viewFilterService.executeFilterQuery(any(), any(), any(), any())).thenReturn(null);

    var result = helper.applyViewFilter(VIEW_ID, "PROJECT", "projects", String.class, null, null);

    assertThat(result).isNull();
  }

  @Test
  void applyViewFilter_returnsAllResultsWhenAccessIdsNull() {
    var view = savedView("PROJECT");
    when(savedViewRepository.findById(VIEW_ID)).thenReturn(Optional.of(view));
    when(viewFilterService.executeFilterQuery(
            eq("projects"), eq(String.class), any(), eq("PROJECT")))
        .thenReturn(List.of("a", "b", "c"));

    var result = helper.applyViewFilter(VIEW_ID, "PROJECT", "projects", String.class, null, null);

    assertThat(result).containsExactly("a", "b", "c");
  }

  @Test
  void applyViewFilter_filtersResultsByAccessibleIds() {
    var id1 = UUID.randomUUID();
    var id2 = UUID.randomUUID();
    var id3 = UUID.randomUUID();

    var view = savedView("PROJECT");
    when(savedViewRepository.findById(VIEW_ID)).thenReturn(Optional.of(view));
    when(viewFilterService.executeFilterQuery(any(), any(), any(), any()))
        .thenReturn(List.of(id1, id2, id3));

    var result =
        helper.applyViewFilter(
            VIEW_ID, "PROJECT", "projects", UUID.class, Set.of(id1, id3), id -> id);

    assertThat(result).containsExactly(id1, id3);
  }

  // --- applyViewFilterForProject tests ---

  @Test
  void applyViewFilterForProject_throwsWhenViewNotFound() {
    when(savedViewRepository.findById(VIEW_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                helper.applyViewFilterForProject(
                    VIEW_ID, "TASK", "tasks", String.class, UUID.randomUUID()))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  void applyViewFilterForProject_throwsOnEntityTypeMismatch() {
    var view = savedView("PROJECT");
    when(savedViewRepository.findById(VIEW_ID)).thenReturn(Optional.of(view));

    assertThatThrownBy(
            () ->
                helper.applyViewFilterForProject(
                    VIEW_ID, "TASK", "tasks", String.class, UUID.randomUUID()))
        .isInstanceOf(InvalidStateException.class)
        .hasMessageContaining("Expected TASK view but got PROJECT");
  }

  @Test
  void applyViewFilterForProject_delegatesToFilterService() {
    var projectId = UUID.randomUUID();
    var view = savedView("TASK");
    when(savedViewRepository.findById(VIEW_ID)).thenReturn(Optional.of(view));
    when(viewFilterService.executeFilterQueryForProject(
            eq("tasks"), eq(String.class), any(), eq("TASK"), eq(projectId)))
        .thenReturn(List.of("task1", "task2"));

    var result =
        helper.applyViewFilterForProject(VIEW_ID, "TASK", "tasks", String.class, projectId);

    assertThat(result).containsExactly("task1", "task2");
  }

  private SavedView savedView(String entityType) {
    return new SavedView(entityType, "Test View", Map.of(), null, false, UUID.randomUUID(), 0);
  }
}
