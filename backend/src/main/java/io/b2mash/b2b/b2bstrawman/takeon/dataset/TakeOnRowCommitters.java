package io.b2mash.b2b.b2bstrawman.takeon.dataset;

import io.b2mash.b2b.b2bstrawman.takeon.TakeOnBatchCommitter;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnDataset;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnSignOffKind;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * The commit paths by dataset: the per-row committers (655A clients, 656A matters) and the
 * whole-batch committers (657C trust position, R-2). The claim and the views ask only the
 * dataset-level questions ({@link #canCommit}, {@link #requiredSignOff}); the drain routes a batch
 * to {@link #batchCommitterFor} when there is one, else to the row runner ({@link #forDataset}).
 */
@Component
public class TakeOnRowCommitters {

  private final List<TakeOnRowCommitter<?>> committers;
  private final List<TakeOnBatchCommitter> batchCommitters;

  public TakeOnRowCommitters(
      List<TakeOnRowCommitter<?>> committers, List<TakeOnBatchCommitter> batchCommitters) {
    this.committers = List.copyOf(committers);
    this.batchCommitters = List.copyOf(batchCommitters);
  }

  public Optional<TakeOnRowCommitter<?>> forDataset(TakeOnDataset dataset) {
    return committers.stream().filter(c -> c.dataset() == dataset).findFirst();
  }

  public Optional<TakeOnBatchCommitter> batchCommitterFor(TakeOnDataset dataset) {
    return batchCommitters.stream().filter(c -> c.dataset() == dataset).findFirst();
  }

  /** Whether the dataset's slice has shipped a commit path (row or whole-batch). */
  public boolean canCommit(TakeOnDataset dataset) {
    return batchCommitterFor(dataset).isPresent() || forDataset(dataset).isPresent();
  }

  /** The live sign-off a commit of the dataset requires; empty when none (or no commit path). */
  public Optional<TakeOnSignOffKind> requiredSignOff(TakeOnDataset dataset) {
    var batchCommitter = batchCommitterFor(dataset);
    if (batchCommitter.isPresent()) {
      return Optional.ofNullable(batchCommitter.get().requiredSignOff());
    }
    return forDataset(dataset).map(TakeOnRowCommitter::requiredSignOff);
  }
}
