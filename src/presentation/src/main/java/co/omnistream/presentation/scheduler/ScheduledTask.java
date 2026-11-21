package co.omnistream.presentation.scheduler;

import co.omnistream.domain.model.access.UserId;

/**
 * A job the scheduler runs every so often (Command pattern): each task wraps one call to a driving
 * port, so the scheduler can run any of them the same way.
 */
public interface ScheduledTask {

  /** A unique name, used in the logs and to register the task once. */
  String name();

  /**
   * Does the job.
   *
   * @param executor the identity the task acts as: the system identity, an administrator
   */
  void execute(UserId executor);
}
