package io.b2mash.b2b.b2bstrawman.takeon.dataset;

import io.b2mash.b2b.b2bstrawman.takeon.TakeOnDataset;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Picks the dry-run handler of a dataset: the dataset's own handler bean when a slice has added
 * one, else the generic handler (the only one in 653A).
 */
@Component
public class TakeOnDatasetHandlers {

  private final List<TakeOnDatasetHandler> handlers;

  public TakeOnDatasetHandlers(List<TakeOnDatasetHandler> handlers) {
    this.handlers = List.copyOf(handlers);
  }

  public TakeOnDatasetHandler forDataset(TakeOnDataset dataset) {
    return handlers.stream()
        .filter(h -> h.dataset() == dataset)
        .findFirst()
        .or(() -> handlers.stream().filter(h -> h.dataset() == null).findFirst())
        .orElseThrow(() -> new IllegalStateException("No take-on handler for " + dataset));
  }
}
