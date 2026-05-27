package io.b2mash.b2b.b2bstrawman.infrastructure.jobqueue;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Registry of {@link JobHandler} implementations discovered by Spring component scanning.
 * Fail-fast: throws {@link IllegalStateException} at startup if any {@code jobType} has duplicate
 * registrations.
 */
@Component
@ConditionalOnProperty(name = "kazi.job-queue.enabled", havingValue = "true")
public class JobHandlerRegistry {

  private static final Logger log = LoggerFactory.getLogger(JobHandlerRegistry.class);

  private final Map<String, JobHandler> handlers;

  public JobHandlerRegistry(List<JobHandler> handlerList) {
    // Check for duplicates before building the map
    var duplicates =
        handlerList.stream()
            .collect(Collectors.groupingBy(JobHandler::jobType, Collectors.counting()))
            .entrySet()
            .stream()
            .filter(e -> e.getValue() > 1)
            .map(Map.Entry::getKey)
            .toList();

    if (!duplicates.isEmpty()) {
      throw new IllegalStateException(
          "Duplicate JobHandler registrations for jobType(s): " + duplicates);
    }

    this.handlers =
        handlerList.stream().collect(Collectors.toUnmodifiableMap(JobHandler::jobType, h -> h));

    log.info("Registered {} job handler(s): {}", handlers.size(), handlers.keySet());
  }

  /**
   * Returns the handler for the given job type.
   *
   * @param jobType the job type identifier
   * @return the registered handler
   * @throws IllegalArgumentException if no handler is registered for the given type
   */
  public JobHandler getHandler(String jobType) {
    var handler = handlers.get(jobType);
    if (handler == null) {
      throw new IllegalArgumentException("No JobHandler registered for jobType: " + jobType);
    }
    return handler;
  }
}
