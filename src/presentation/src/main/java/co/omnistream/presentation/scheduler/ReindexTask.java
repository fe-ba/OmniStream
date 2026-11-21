package co.omnistream.presentation.scheduler;

import co.omnistream.domain.model.access.UserId;
import co.omnistream.domain.port.in.ICatalogManagement;
import java.nio.file.Path;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Scans the library folder and indexes what is new or changed. */
public final class ReindexTask implements ScheduledTask {

  private static final Logger LOG = LoggerFactory.getLogger(ReindexTask.class);

  private final ICatalogManagement catalogManagement;
  private final Path root;

  public ReindexTask(ICatalogManagement catalogManagement, Path root) {
    this.catalogManagement = Objects.requireNonNull(catalogManagement, "catalogManagement");
    this.root = Objects.requireNonNull(root, "root");
  }

  @Override
  public String name() {
    return "reindex";
  }

  @Override
  public void execute(UserId executor) {
    int indexed = catalogManagement.reindexLibrary(executor, root);
    LOG.info("Reindexed {}: {} files indexed", root, indexed);
  }
}
