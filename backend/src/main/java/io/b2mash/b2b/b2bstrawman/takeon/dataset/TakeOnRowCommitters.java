package io.b2mash.b2b.b2bstrawman.takeon.dataset;

import io.b2mash.b2b.b2bstrawman.takeon.TakeOnDataset;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** The row committers by dataset (655A: clients only; each dataset slice adds its own bean). */
@Component
public class TakeOnRowCommitters {

  private final List<TakeOnRowCommitter<?>> committers;

  public TakeOnRowCommitters(List<TakeOnRowCommitter<?>> committers) {
    this.committers = List.copyOf(committers);
  }

  public Optional<TakeOnRowCommitter<?>> forDataset(TakeOnDataset dataset) {
    return committers.stream().filter(c -> c.dataset() == dataset).findFirst();
  }
}
