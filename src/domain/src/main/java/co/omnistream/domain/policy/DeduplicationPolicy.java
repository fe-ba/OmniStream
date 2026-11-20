package co.omnistream.domain.policy;

import co.omnistream.domain.model.catalog.CatalogIndex;
import co.omnistream.domain.model.catalog.DuplicateAction;
import co.omnistream.domain.model.catalog.IndexingStatus;
import co.omnistream.domain.model.catalog.MediaFile;
import co.omnistream.domain.snapshot.MediaFileSnapshot;
import java.util.Objects;

/**
 * Decides what happens when an incoming file has the same content as one already in the catalog.
 * Stateless; it sees both files at once, so it lives outside the entities.
 *
 * <p>The rules, in order:
 *
 * <ol>
 *   <li>The existing file failed to index ({@link IndexingStatus#ERROR}): the incoming one replaces
 *       it ({@link DuplicateAction#REPLACE}).
 *   <li>Both are at the same path, so it is the same file seen again, for example when reindexing:
 *       the incoming one is rejected ({@link DuplicateAction#REJECT}).
 *   <li>Otherwise it is a copy in another place: both are kept ({@link DuplicateAction#KEEP_BOTH}),
 *       and the "detect duplicates" use case lists them so a user can decide.
 * </ol>
 */
public final class DeduplicationPolicy {

  public DeduplicationPolicy() {}

  /** Whether the catalog already has another file with the same content as {@code a}. */
  public boolean isDuplicate(MediaFile a, CatalogIndex c) {
    Objects.requireNonNull(a, "a");
    Objects.requireNonNull(c, "c");
    return c.findByFingerprint(a.toSnapshot().fingerprint()).filter(a::isDuplicateOf).isPresent();
  }

  /** What to do with {@code incoming}, given that {@code existing} has the same content. */
  public DuplicateAction resolve(MediaFile incoming, MediaFile existing) {
    MediaFileSnapshot in = Objects.requireNonNull(incoming, "incoming").toSnapshot();
    MediaFileSnapshot current = Objects.requireNonNull(existing, "existing").toSnapshot();
    if (current.status() == IndexingStatus.ERROR) {
      return DuplicateAction.REPLACE;
    }
    if (current.path().normalize().equals(in.path().normalize())) {
      return DuplicateAction.REJECT;
    }
    return DuplicateAction.KEEP_BOTH;
  }
}
